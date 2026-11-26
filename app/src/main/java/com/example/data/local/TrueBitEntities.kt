package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.AudioCodec
import com.example.domain.model.AudioTrack
import com.example.domain.model.AuthenticityVerdict

@Entity(
    tableName = "cached_tracks",
    indices = [
        Index(value = ["fileUri", "lastModified", "fileSize"]),
        Index(value = ["fullSha256"])
    ]
)
data class CachedTrackEntity(
    @PrimaryKey val fileUri: String,
    val fileName: String,
    val folderName: String,
    val lastModified: Long,
    val fileSize: Long,
    val codecName: String,
    val containerBitDepth: Int,
    val effectiveBitDepth: Int,
    val isFloatingPoint: Boolean,
    val isZeroPadded: Boolean,
    val sampleRateHz: Int,
    val effectiveSampleRateHz: Int,
    val hasUltrasonicEnergy: Boolean,
    val channels: Int,
    val durationMs: Long,
    val bitrateKbps: Int,
    val title: String,
    val artist: String,
    val album: String,
    val trackNumber: Int,
    val isrc: String,
    val hasEmbeddedArtwork: Boolean,
    val partialHash: String,
    val fullSha256: String,
    val acousticFingerprintCsv: String,
    val spectralCutoffHz: Int,
    val authenticityScore: Int,
    val verdictName: String,
    val transcodeProfile: String,
    val dynamicRangeDb: Float,
    val spectrumBinsCsv: String,
    val bitPlaneActivityCsv: String,
    val crcVerified: Boolean,
    val isQuarantined: Boolean
) {
    fun toDomain(): AudioTrack {
        return AudioTrack(
            fileUri = fileUri,
            fileName = fileName,
            folderName = folderName,
            lastModified = lastModified,
            fileSize = fileSize,
            codec = runCatching { AudioCodec.valueOf(codecName) }.getOrDefault(AudioCodec.FLAC),
            containerBitDepth = containerBitDepth,
            effectiveBitDepth = effectiveBitDepth,
            isFloatingPoint = isFloatingPoint,
            isZeroPadded = isZeroPadded,
            sampleRateHz = sampleRateHz,
            effectiveSampleRateHz = effectiveSampleRateHz,
            hasUltrasonicEnergy = hasUltrasonicEnergy,
            channels = channels,
            durationMs = durationMs,
            bitrateKbps = bitrateKbps,
            title = title,
            artist = artist,
            album = album,
            trackNumber = trackNumber,
            isrc = isrc,
            hasEmbeddedArtwork = hasEmbeddedArtwork,
            partialHash = partialHash,
            fullSha256 = fullSha256,
            acousticFingerprint = parseFloatCsv(acousticFingerprintCsv),
            spectralCutoffHz = spectralCutoffHz,
            authenticityScore = authenticityScore,
            verdict = runCatching { AuthenticityVerdict.valueOf(verdictName) }.getOrDefault(AuthenticityVerdict.TRUE_LOSSLESS),
            transcodeProfile = transcodeProfile,
            dynamicRangeDb = dynamicRangeDb,
            spectrumBinsDb = parseFloatCsv(spectrumBinsCsv),
            bitPlaneActivity = parseFloatCsv(bitPlaneActivityCsv),
            crcVerified = crcVerified,
            isQuarantined = isQuarantined
        )
    }

    companion object {
        fun fromDomain(track: AudioTrack): CachedTrackEntity {
            return CachedTrackEntity(
                fileUri = track.fileUri,
                fileName = track.fileName,
                folderName = track.folderName,
                lastModified = track.lastModified,
                fileSize = track.fileSize,
                codecName = track.codec.name,
                containerBitDepth = track.containerBitDepth,
                effectiveBitDepth = track.effectiveBitDepth,
                isFloatingPoint = track.isFloatingPoint,
                isZeroPadded = track.isZeroPadded,
                sampleRateHz = track.sampleRateHz,
                effectiveSampleRateHz = track.effectiveSampleRateHz,
                hasUltrasonicEnergy = track.hasUltrasonicEnergy,
                channels = track.channels,
                durationMs = track.durationMs,
                bitrateKbps = track.bitrateKbps,
                title = track.title,
                artist = track.artist,
                album = track.album,
                trackNumber = track.trackNumber,
                isrc = track.isrc,
                hasEmbeddedArtwork = track.hasEmbeddedArtwork,
                partialHash = track.partialHash,
                fullSha256 = track.fullSha256,
                acousticFingerprintCsv = encodeFloatCsv(track.acousticFingerprint),
                spectralCutoffHz = track.spectralCutoffHz,
                authenticityScore = track.authenticityScore,
                verdictName = track.verdict.name,
                transcodeProfile = track.transcodeProfile,
                dynamicRangeDb = track.dynamicRangeDb,
                spectrumBinsCsv = encodeFloatCsv(track.spectrumBinsDb),
                bitPlaneActivityCsv = encodeFloatCsv(track.bitPlaneActivity),
                crcVerified = track.crcVerified,
                isQuarantined = track.isQuarantined
            )
        }

        private fun encodeFloatCsv(values: List<Float>): String {
            return values.joinToString(",") { "%.4f".format(java.util.Locale.US, it) }
        }

        private fun parseFloatCsv(csv: String): List<Float> {
            if (csv.isBlank()) return emptyList()
            return csv.split(",").mapNotNull { it.trim().toFloatOrNull() }
        }
    }
}

@Entity(tableName = "trash_audit_entries")
data class TrashAuditEntity(
    @PrimaryKey val trashId: String,
    val originalUri: String,
    val trashFilePath: String,
    val fileName: String,
    val title: String,
    val artist: String,
    val codecLabel: String,
    val fileSize: Long,
    val downgradeReason: String,
    val comparisonSummary: String,
    val quarantinedAtMs: Long,
    val isPermanentlyDeleted: Boolean
)
