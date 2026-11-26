package com.example.domain.model

enum class AudioCodec(val label: String, val extensions: List<String>) {
    FLAC("FLAC", listOf("flac")),
    ALAC("ALAC", listOf("alac", "m4a")),
    WAV("WAV", listOf("wav")),
    AIFF("AIFF", listOf("aiff", "aif")),
    APE("APE", listOf("ape")),
    WAVPACK("WavPack", listOf("wv"));

    companion object {
        fun fromExtension(ext: String): AudioCodec? {
            val clean = ext.lowercase().trimStart('.')
            return entries.firstOrNull { clean in it.extensions }
        }
    }
}

enum class AuthenticityVerdict(val badgeLabel: String, val tierRank: Int) {
    TRUE_LOSSLESS("TRUE LOSSLESS", 3),
    UPSAMPLED("UPSAMPLED / PADDED", 2),
    FAKE_TRANSCODE("FAKE / TRANSCODE", 1)
}

enum class DuplicateMatchType(val label: String) {
    EXACT_SHA256("Exact Byte Copy (SHA-256)"),
    ACOUSTIC_BIT_DEPTH("Multi-Bit-Depth Acoustic Match"),
    ACOUSTIC_TRANSCODE("Lossless vs. Fake Transcode Match"),
    ACOUSTIC_CONTAINER("Cross-Container Acoustic Match")
}

enum class DuplicateFilter(val label: String) {
    ALL("All Duplicates"),
    BIT_DEPTH("16-bit vs 24/32-bit"),
    FAKE_LOSSLESS("Fake Lossless"),
    EXACT_COPIES("Exact Copies")
}

enum class MediaCategoryType(val label: String) {
    ALL("All Categories"),
    ALBUM("Albums"),
    MOVIE("Movies / OST")
}

data class AudioTrack(
    val fileUri: String,
    val fileName: String,
    val folderName: String,
    val lastModified: Long,
    val fileSize: Long,
    val codec: AudioCodec,
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
    val acousticFingerprint: List<Float>,
    val spectralCutoffHz: Int,
    val authenticityScore: Int,
    val verdict: AuthenticityVerdict,
    val transcodeProfile: String,
    val dynamicRangeDb: Float,
    val spectrumBinsDb: List<Float>,
    val bitPlaneActivity: List<Float>,
    val crcVerified: Boolean,
    val isQuarantined: Boolean = false
) {
    val isMovieSoundtrack: Boolean
        get() {
            val combined = "$album $title $artist $fileName".lowercase()
            val movieKeywords = listOf(
                "ost", "soundtrack", "motion picture", "movie", "film",
                "score", "cinema", "theme", "overture", "interstellar", "dune", "inception"
            )
            return movieKeywords.any { combined.contains(it) }
        }

    val categoryType: MediaCategoryType
        get() = if (isMovieSoundtrack) MediaCategoryType.MOVIE else MediaCategoryType.ALBUM

    val sanitizedTargetFolderName: String
        get() = album.ifBlank { "Unsorted Album" }
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()

    val isOrganizedInAlbumFolder: Boolean
        get() {
            val cleanTarget = sanitizedTargetFolderName.lowercase()
            val currentFolder = folderName.substringAfterLast('/').lowercase()
            return currentFolder == cleanTarget || folderName.lowercase().endsWith("/$cleanTarget")
        }

    val containerVsEffectiveSummary: String
        get() = if (isZeroPadded) {
            "${effectiveBitDepth}-bit effective (padded to ${containerBitDepth}-bit)"
        } else if (isFloatingPoint) {
            "${containerBitDepth}-bit Float (${effectiveBitDepth}-bit active)"
        } else {
            "${effectiveBitDepth}-bit True PCM"
        }

    val sampleRateSummary: String
        get() {
            val khz = sampleRateHz / 1000f
            val formatted = if (sampleRateHz % 1000 == 0) "${sampleRateHz / 1000} kHz" else String.format("%.1f kHz", khz)
            return if (sampleRateHz > 48000 && !hasUltrasonicEnergy) {
                "$formatted (Empty >22kHz)"
            } else {
                formatted
            }
        }

    val comparisonPillText: String
        get() {
            val khz = if (sampleRateHz % 1000 == 0) "${sampleRateHz / 1000}kHz" else String.format("%.1fkHz", sampleRateHz / 1000f)
            val status = when (verdict) {
                AuthenticityVerdict.TRUE_LOSSLESS -> if (isZeroPadded) "${effectiveBitDepth}-bit Padded" else "True Lossless"
                AuthenticityVerdict.UPSAMPLED -> if (isZeroPadded) "${effectiveBitDepth}-bit Padded" else "Upsampled"
                AuthenticityVerdict.FAKE_TRANSCODE -> transcodeProfile
            }
            return "${containerBitDepth}-bit / $khz • ${codec.label} • $status"
        }
}

data class MediaCategoryBucket(
    val bucketId: String,
    val name: String,
    val subtitleArtist: String,
    val categoryType: MediaCategoryType,
    val targetFolderName: String,
    val tracks: List<AudioTrack>
) {
    val totalSizeBytes: Long
        get() = tracks.sumOf { it.fileSize }

    val unorganizedCount: Int
        get() = tracks.count { !it.isOrganizedInAlbumFolder }

    val isAllOrganized: Boolean
        get() = tracks.isNotEmpty() && unorganizedCount == 0
}

data class RankedTrackItem(
    val track: AudioTrack,
    val isMaster: Boolean,
    val masterScore: Int,
    val downgradeReason: String,
    val selectedForDeletion: Boolean
)

data class DuplicateGroup(
    val groupId: String,
    val canonicalTitle: String,
    val canonicalArtist: String,
    val matchType: DuplicateMatchType,
    val similarityPercent: Int,
    val masterItem: RankedTrackItem,
    val redundantItems: List<RankedTrackItem>
) {
    val reclaimableBytes: Long
        get() = redundantItems.filter { it.selectedForDeletion }.sumOf { it.track.fileSize }

    val hasBitDepthDifference: Boolean
        get() {
            val allDepths = (listOf(masterItem.track) + redundantItems.map { it.track })
                .map { it.containerBitDepth }
                .distinct()
            return allDepths.size > 1 || redundantItems.any { it.track.isZeroPadded }
        }

    val hasFakeLossless: Boolean
        get() = redundantItems.any {
            it.track.verdict == AuthenticityVerdict.FAKE_TRANSCODE ||
                it.track.verdict == AuthenticityVerdict.UPSAMPLED
        }
}

data class TrashEntry(
    val trashId: String,
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
    val isPermanentlyDeleted: Boolean = false
)

data class ScanProgressState(
    val isScanning: Boolean = false,
    val isDeepSpectralScan: Boolean = true,
    val currentFileName: String = "",
    val currentStage: String = "Idle",
    val processedCount: Int = 0,
    val totalCount: Int = 0,
    val cacheHitCount: Int = 0,
    val throughputMbPerSec: Float = 0f,
    val selectedFolders: List<String> = emptyList(),
    val lastScanTimestampMs: Long = 0L
)
