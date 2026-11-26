package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.example.data.local.CachedTrackEntity
import com.example.data.local.TrackCacheDao
import com.example.data.local.TrashAuditDao
import com.example.data.local.TrashAuditEntity
import com.example.domain.model.AudioCodec
import com.example.domain.model.AudioTrack
import com.example.domain.model.ScanProgressState
import com.example.domain.model.TrashEntry
import com.example.dsp.LosslessContainerDecoder
import com.example.dsp.LosslessSignalGenerator
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

class AudioScannerRepository(
    private val context: Context,
    private val trackCacheDao: TrackCacheDao,
    private val trashAuditDao: TrashAuditDao,
    private val decoder: LosslessContainerDecoder,
    private val signalGenerator: LosslessSignalGenerator
) {
    private val _scanProgress = MutableStateFlow(ScanProgressState())
    val scanProgress: StateFlow<ScanProgressState> = _scanProgress.asStateFlow()

    private val supportedExtensions = AudioCodec.entries.flatMap { it.extensions }.toSet()

    val allTracksFlow: Flow<List<AudioTrack>> = trackCacheDao.observeAllTracks().map { list ->
        list.map { it.toDomain() }
    }

    val allTrashEntriesFlow: Flow<List<TrashEntry>> = trashAuditDao.observeAllTrashEntries().map { list ->
        list.map { entity ->
            TrashEntry(
                trashId = entity.trashId,
                originalUri = entity.originalUri,
                trashFilePath = entity.trashFilePath,
                fileName = entity.fileName,
                title = entity.title,
                artist = entity.artist,
                codecLabel = entity.codecLabel,
                fileSize = entity.fileSize,
                downgradeReason = entity.downgradeReason,
                comparisonSummary = entity.comparisonSummary,
                quarantinedAtMs = entity.quarantinedAtMs,
                isPermanentlyDeleted = entity.isPermanentlyDeleted
            )
        }
    }

    /**
     * Ensures the built-in TrueBit Mastering Lab directory has real multi-format lossless test files
     * and scans them together with any user-selected SAF directories.
     */
    suspend fun scanDirectories(
        safTreeUris: Set<String>,
        deepSpectralScan: Boolean,
        includeMasteringLab: Boolean = true
    ) = withContext(Dispatchers.IO) {
        if (_scanProgress.value.isScanning) return@withContext

        val discoveredFiles = mutableListOf<DiscoveredAudioSource>()
        val folderLabels = mutableListOf<String>()

        _scanProgress.update {
            it.copy(
                isScanning = true,
                isDeepSpectralScan = deepSpectralScan,
                currentStage = "Enumerating Lossless Containers...",
                processedCount = 0,
                totalCount = 0,
                cacheHitCount = 0,
                throughputMbPerSec = 0f
            )
        }

        // 1. Discover files in TrueBit Mastering Lab (including any organized Album / Movie subfolders)
        if (includeMasteringLab) {
            val labDir = signalGenerator.ensureMasteringLabFiles()
            folderLabels.add("Mastering Lab (${labDir.name})")
            collectLocalAudioFiles(labDir, "TrueBit Mastering Lab", discoveredFiles)
        }

        // 2. Recursively traverse user-selected Storage Access Framework (SAF) directory trees
        for (treeUriStr in safTreeUris) {
            val treeUri = runCatching { Uri.parse(treeUriStr) }.getOrNull() ?: continue
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: continue
            val folderLabel = rootDoc.name ?: "SAF Volume"
            folderLabels.add(folderLabel)
            collectSafAudioFiles(rootDoc, folderLabel, discoveredFiles)
        }

        val totalFiles = discoveredFiles.size
        _scanProgress.update {
            it.copy(
                totalCount = totalFiles,
                selectedFolders = folderLabels.distinct(),
                currentStage = if (deepSpectralScan) "Running FFT & Bit-Plane Audit..." else "Checking Incremental Cache & Headers..."
            )
        }

        val startTimeNs = System.nanoTime()
        var totalBytesProcessed = 0L
        var cacheHits = 0

        for ((index, source) in discoveredFiles.withIndex()) {
            _scanProgress.update {
                it.copy(
                    currentFileName = source.fileName,
                    processedCount = index
                )
            }

            // Incremental Room Cache Lookup keyed by (fileUri, lastModified, fileSize)
            val cached = trackCacheDao.findCachedTrack(
                uri = source.uri.toString(),
                lastModified = source.lastModified,
                fileSize = source.fileSize
            )

            if (cached != null) {
                cacheHits++
                totalBytesProcessed += source.fileSize
            } else {
                val analyzedTrack = decoder.analyzeAudioUri(
                    uri = source.uri,
                    fileName = source.fileName,
                    folderName = source.folderName,
                    lastModified = source.lastModified,
                    fileSize = source.fileSize,
                    deepSpectralScan = deepSpectralScan
                )
                trackCacheDao.upsertTrack(CachedTrackEntity.fromDomain(analyzedTrack))
                totalBytesProcessed += source.fileSize
            }

            val elapsedSec = ((System.nanoTime() - startTimeNs) / 1_000_000_000.0).coerceAtLeast(0.05)
            val mbPerSec = ((totalBytesProcessed / (1024.0 * 1024.0)) / elapsedSec).toFloat()

            _scanProgress.update {
                it.copy(
                    processedCount = index + 1,
                    cacheHitCount = cacheHits,
                    throughputMbPerSec = mbPerSec
                )
            }
        }

        _scanProgress.update {
            it.copy(
                isScanning = false,
                currentFileName = "",
                currentStage = "Audit Complete",
                lastScanTimestampMs = System.currentTimeMillis()
            )
        }
    }

    /**
     * Single-file on-demand Lossless Inspector via SAF OpenDocument picker.
     */
    suspend fun inspectSingleDocumentUri(uri: Uri): AudioTrack = withContext(Dispatchers.IO) {
        val docFile = DocumentFile.fromSingleUri(context, uri)
        val fileName = docFile?.name ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Inspected_Track.flac"
        val fileSize = docFile?.length()?.takeIf { it > 0 } ?: queryStreamSize(uri)
        val lastModified = docFile?.lastModified()?.takeIf { it > 0 } ?: System.currentTimeMillis()

        val analyzed = decoder.analyzeAudioUri(
            uri = uri,
            fileName = fileName,
            folderName = "Direct File Inspector",
            lastModified = lastModified,
            fileSize = fileSize,
            deepSpectralScan = true
        )
        trackCacheDao.upsertTrack(CachedTrackEntity.fromDomain(analyzed))
        analyzed
    }

    /**
     * Quarantines selected duplicate tracks into `.TrueBit_Trash` inside app-managed quarantine
     * storage so they can be safely restored or permanently purged later.
     */
    suspend fun quarantineTracksToTrash(
        itemsToQuarantine: List<Pair<AudioTrack, String>>
    ): Int = withContext(Dispatchers.IO) {
        val trashDir = File(context.filesDir, ".TrueBit_Trash")
        if (!trashDir.exists()) trashDir.mkdirs()

        var count = 0
        for ((track, downgradeReason) in itemsToQuarantine) {
            val trashId = UUID.randomUUID().toString()
            val safeFileName = "${trashId.take(8)}_${track.fileName}"
            val trashFile = File(trashDir, safeFileName)

            val uri = Uri.parse(track.fileUri)
            // Copy file bytes into .TrueBit_Trash quarantine before marking quarantined
            runCatching {
                if (uri.scheme == "file") {
                    val srcFile = File(uri.path ?: "")
                    if (srcFile.exists()) {
                        srcFile.copyTo(trashFile, overwrite = true)
                    }
                } else {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(trashFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }

            trackCacheDao.setQuarantinedState(track.fileUri, true)
            trashAuditDao.insertTrashEntry(
                TrashAuditEntity(
                    trashId = trashId,
                    originalUri = track.fileUri,
                    trashFilePath = trashFile.absolutePath,
                    fileName = track.fileName,
                    title = track.title,
                    artist = track.artist,
                    codecLabel = track.codec.label,
                    fileSize = track.fileSize,
                    downgradeReason = downgradeReason,
                    comparisonSummary = track.comparisonPillText,
                    quarantinedAtMs = System.currentTimeMillis(),
                    isPermanentlyDeleted = false
                )
            )
            count++
        }
        count
    }

    /**
     * Permanently deletes selected duplicate tracks via SAF DocumentsContract.deleteDocument
     * or direct File deletion, and logs the action in the Trash Audit Log.
     */
    suspend fun permanentlyDeleteTracks(
        itemsToDelete: List<Pair<AudioTrack, String>>
    ): Int = withContext(Dispatchers.IO) {
        var deletedCount = 0
        for ((track, downgradeReason) in itemsToDelete) {
            val uri = Uri.parse(track.fileUri)
            if (uri.scheme == "file") {
                val f = File(uri.path ?: "")
                if (f.exists()) f.delete()
            } else {
                runCatching {
                    DocumentsContract.deleteDocument(context.contentResolver, uri)
                }
            }

            trackCacheDao.deleteTrackByUri(track.fileUri)
            trashAuditDao.insertTrashEntry(
                TrashAuditEntity(
                    trashId = UUID.randomUUID().toString(),
                    originalUri = track.fileUri,
                    trashFilePath = "",
                    fileName = track.fileName,
                    title = track.title,
                    artist = track.artist,
                    codecLabel = track.codec.label,
                    fileSize = track.fileSize,
                    downgradeReason = downgradeReason,
                    comparisonSummary = track.comparisonPillText,
                    quarantinedAtMs = System.currentTimeMillis(),
                    isPermanentlyDeleted = true
                )
            )
            deletedCount++
        }
        deletedCount
    }

    /**
     * Restores a quarantined track from `.TrueBit_Trash` back into the active library.
     */
    suspend fun restoreFromTrash(entry: TrashEntry) = withContext(Dispatchers.IO) {
        if (entry.isPermanentlyDeleted) return@withContext
        val uri = Uri.parse(entry.originalUri)
        val trashFile = File(entry.trashFilePath)

        if (uri.scheme == "file" && trashFile.exists()) {
            val targetFile = File(uri.path ?: "")
            if (!targetFile.exists()) {
                trashFile.copyTo(targetFile, overwrite = true)
            }
            trashFile.delete()
        } else if (trashFile.exists()) {
            trashFile.delete()
        }

        trackCacheDao.setQuarantinedState(entry.originalUri, false)
        trashAuditDao.removeTrashEntry(entry.trashId)
    }

    /**
     * Permanently purges a quarantined track from `.TrueBit_Trash` and SAF storage.
     */
    suspend fun purgeTrashEntryPermanently(entry: TrashEntry) = withContext(Dispatchers.IO) {
        if (entry.trashFilePath.isNotBlank()) {
            val trashFile = File(entry.trashFilePath)
            if (trashFile.exists()) trashFile.delete()
        }

        val uri = Uri.parse(entry.originalUri)
        if (uri.scheme == "file") {
            val f = File(uri.path ?: "")
            if (f.exists()) f.delete()
        } else {
            runCatching {
                DocumentsContract.deleteDocument(context.contentResolver, uri)
            }
        }

        trackCacheDao.deleteTrackByUri(entry.originalUri)
        trashAuditDao.markPermanentlyDeleted(entry.trashId)
    }

    suspend fun emptyAllTrashPermanently(entries: List<TrashEntry>) = withContext(Dispatchers.IO) {
        for (entry in entries) {
            if (!entry.isPermanentlyDeleted) {
                purgeTrashEntryPermanently(entry)
            }
        }
    }

    suspend fun clearAuditHistory() = withContext(Dispatchers.IO) {
        trashAuditDao.clearDeletedHistory()
    }

    suspend fun forceClearCacheAndRescan(
        safTreeUris: Set<String>,
        deepSpectralScan: Boolean
    ) = withContext(Dispatchers.IO) {
        trackCacheDao.clearAllTracks()
        scanDirectories(safTreeUris, deepSpectralScan, includeMasteringLab = true)
    }

    /**
     * Creates an Album or Movie folder (or custom-named folder) and moves all songs in the bucket
     * into that folder on local storage or SAF storage, updating the Room cache immediately.
     */
    suspend fun organizeTracksIntoFolder(
        tracks: List<AudioTrack>,
        targetFolderName: String,
        safTreeUris: Set<String>
    ): Int = withContext(Dispatchers.IO) {
        val cleanFolder = targetFolderName
            .ifBlank { "Unsorted Album" }
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()

        var movedCount = 0
        for (track in tracks) {
            val currentSubfolder = track.folderName.substringAfterLast('/')
            if (currentSubfolder.equals(cleanFolder, ignoreCase = true)) continue

            val srcUri = Uri.parse(track.fileUri)
            if (srcUri.scheme == "file") {
                val srcFile = File(srcUri.path ?: "")
                if (!srcFile.exists()) continue

                val baseDir = File(context.filesDir, "TrueBit_Mastering_Lab")
                val destDir = File(baseDir, cleanFolder)
                if (!destDir.exists()) destDir.mkdirs()

                val destFile = File(destDir, srcFile.name)
                if (srcFile.absolutePath != destFile.absolutePath) {
                    val moved = srcFile.renameTo(destFile)
                    if (!moved) {
                        srcFile.copyTo(destFile, overwrite = true)
                        srcFile.delete()
                    }
                }

                if (destFile.exists()) {
                    val newUriStr = Uri.fromFile(destFile).toString()
                    val updatedTrack = track.copy(
                        fileUri = newUriStr,
                        folderName = "TrueBit Mastering Lab/$cleanFolder",
                        lastModified = destFile.lastModified()
                    )
                    trackCacheDao.deleteTrackByUri(track.fileUri)
                    trackCacheDao.upsertTrack(CachedTrackEntity.fromDomain(updatedTrack))
                    movedCount++
                }
            } else {
                // SAF DocumentFile move/copy into target album directory inside the selected SAF tree
                val rootTreeUri = safTreeUris.firstNotNullOfOrNull { treeStr ->
                    runCatching { Uri.parse(treeStr) }.getOrNull()?.takeIf {
                        track.fileUri.startsWith(treeStr.substringBefore("/tree/"))
                    }
                } ?: safTreeUris.firstOrNull()?.let { runCatching { Uri.parse(it) }.getOrNull() }

                if (rootTreeUri != null) {
                    val rootDoc = DocumentFile.fromTreeUri(context, rootTreeUri)
                    if (rootDoc != null) {
                        val albumDirDoc = rootDoc.findFile(cleanFolder)?.takeIf { it.isDirectory }
                            ?: rootDoc.createDirectory(cleanFolder)

                        if (albumDirDoc != null) {
                            val existingDest = albumDirDoc.findFile(track.fileName)
                            val destDoc = existingDest ?: albumDirDoc.createFile("audio/*", track.fileName)
                            if (destDoc != null) {
                                val copied = runCatching {
                                    context.contentResolver.openInputStream(srcUri)?.use { input ->
                                        context.contentResolver.openOutputStream(destDoc.uri)?.use { output ->
                                            input.copyTo(output)
                                        }
                                    }
                                    true
                                }.getOrDefault(false)

                                if (copied) {
                                    runCatching {
                                        DocumentsContract.deleteDocument(context.contentResolver, srcUri)
                                    }
                                    val rootLabel = rootDoc.name ?: "SAF Volume"
                                    val updatedTrack = track.copy(
                                        fileUri = destDoc.uri.toString(),
                                        folderName = "$rootLabel/$cleanFolder",
                                        lastModified = System.currentTimeMillis()
                                    )
                                    trackCacheDao.deleteTrackByUri(track.fileUri)
                                    trackCacheDao.upsertTrack(CachedTrackEntity.fromDomain(updatedTrack))
                                    movedCount++
                                }
                            }
                        }
                    }
                }
            }
        }
        movedCount
    }

    private fun collectLocalAudioFiles(
        dir: File,
        folderLabel: String,
        outList: MutableList<DiscoveredAudioSource>
    ) {
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (child.name.startsWith(".TrueBit_Trash")) continue
            if (child.isDirectory) {
                collectLocalAudioFiles(child, "$folderLabel/${child.name}", outList)
            } else if (child.isFile) {
                val ext = child.extension.lowercase()
                if (ext in supportedExtensions) {
                    outList.add(
                        DiscoveredAudioSource(
                            uri = Uri.fromFile(child),
                            fileName = child.name,
                            folderName = folderLabel,
                            lastModified = child.lastModified(),
                            fileSize = child.length()
                        )
                    )
                }
            }
        }
    }

    fun persistSafFolderPermission(uri: Uri) {
        runCatching {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
        }
    }

    private fun collectSafAudioFiles(
        dir: DocumentFile,
        folderLabel: String,
        outList: MutableList<DiscoveredAudioSource>
    ) {
        val children = dir.listFiles()
        for (child in children) {
            val name = child.name ?: continue
            if (name.startsWith(".TrueBit_Trash")) continue
            if (child.isDirectory) {
                collectSafAudioFiles(child, "$folderLabel/$name", outList)
            } else if (child.isFile) {
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext in supportedExtensions) {
                    outList.add(
                        DiscoveredAudioSource(
                            uri = child.uri,
                            fileName = name,
                            folderName = folderLabel,
                            lastModified = child.lastModified(),
                            fileSize = child.length()
                        )
                    )
                }
            }
        }
    }

    private fun queryStreamSize(uri: Uri): Long {
        return runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                afd.length.takeIf { it > 0 }
            } ?: context.contentResolver.openInputStream(uri)?.use { stream ->
                var total = 0L
                val buf = ByteArray(16384)
                while (true) {
                    val r = stream.read(buf)
                    if (r <= 0) break
                    total += r
                }
                total
            } ?: 0L
        }.getOrDefault(0L)
    }

    private data class DiscoveredAudioSource(
        val uri: Uri,
        val fileName: String,
        val folderName: String,
        val lastModified: Long,
        val fileSize: Long
    )
}
