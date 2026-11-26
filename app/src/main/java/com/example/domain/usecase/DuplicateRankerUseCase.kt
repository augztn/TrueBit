package com.example.domain.usecase

import com.example.domain.model.AudioCodec
import com.example.domain.model.AudioTrack
import com.example.domain.model.AuthenticityVerdict
import com.example.domain.model.DuplicateGroup
import com.example.domain.model.DuplicateMatchType
import com.example.domain.model.RankedTrackItem
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class DuplicateRankerUseCase {

    fun buildDuplicateGroups(
        tracks: List<AudioTrack>,
        manualSelectionOverrides: Map<String, Boolean> = emptyMap()
    ): List<DuplicateGroup> {
        val activeTracks = tracks.filter { !it.isQuarantined }
        if (activeTracks.size < 2) return emptyList()

        val assignedUris = mutableSetOf<String>()
        val rawClusters = mutableListOf<Pair<List<AudioTrack>, DuplicateMatchType>>()

        // Stage 1: Exact SHA-256 Clusters (or identical partialHash + size + acoustic similarity > 99%)
        val byHash = activeTracks.groupBy { "${it.fullSha256}_${it.fileSize}" }
        for ((_, sameHashTracks) in byHash) {
            if (sameHashTracks.size > 1) {
                // Check if any remaining tracks also acoustically match this song across bit depths
                val seed = sameHashTracks.first()
                val acousticRelatives = activeTracks.filter { candidate ->
                    candidate.fileUri !in sameHashTracks.map { it.fileUri } &&
                        candidate.fileUri !in assignedUris &&
                        areSameSongAcousticallyAndByTag(seed, candidate)
                }
                val combined = sameHashTracks + acousticRelatives
                combined.forEach { assignedUris.add(it.fileUri) }
                val matchType = if (acousticRelatives.isEmpty()) {
                    DuplicateMatchType.EXACT_SHA256
                } else {
                    classifyAcousticMatchType(combined)
                }
                rawClusters.add(combined to matchType)
            }
        }

        // Stage 2: Acoustic Waveform Fingerprint + Normalized Metadata Levenshtein + Duration (±2.0s)
        val remaining = activeTracks.filter { it.fileUri !in assignedUris }
        val visited = mutableSetOf<String>()

        for (i in remaining.indices) {
            val anchor = remaining[i]
            if (anchor.fileUri in visited) continue

            val cluster = mutableListOf(anchor)
            visited.add(anchor.fileUri)

            for (j in i + 1 until remaining.size) {
                val candidate = remaining[j]
                if (candidate.fileUri in visited) continue

                if (areSameSongAcousticallyAndByTag(anchor, candidate)) {
                    cluster.add(candidate)
                    visited.add(candidate.fileUri)
                }
            }

            if (cluster.size > 1) {
                rawClusters.add(cluster to classifyAcousticMatchType(cluster))
            }
        }

        return rawClusters.mapIndexed { index, (clusterTracks, matchType) ->
            rankCluster(
                groupId = "dup_group_${index}_${clusterTracks.first().title.hashCode()}",
                clusterTracks = clusterTracks,
                matchType = matchType,
                manualOverrides = manualSelectionOverrides
            )
        }.sortedByDescending { it.reclaimableBytes }
    }

    private fun classifyAcousticMatchType(cluster: List<AudioTrack>): DuplicateMatchType {
        val hasFake = cluster.any { it.verdict == AuthenticityVerdict.FAKE_TRANSCODE }
        if (hasFake) return DuplicateMatchType.ACOUSTIC_TRANSCODE

        val bitDepths = cluster.map { it.effectiveBitDepth }.distinct()
        val containerDepths = cluster.map { it.containerBitDepth }.distinct()
        if (bitDepths.size > 1 || containerDepths.size > 1 || cluster.any { it.isZeroPadded }) {
            return DuplicateMatchType.ACOUSTIC_BIT_DEPTH
        }
        return DuplicateMatchType.ACOUSTIC_CONTAINER
    }

    private fun rankCluster(
        groupId: String,
        clusterTracks: List<AudioTrack>,
        matchType: DuplicateMatchType,
        manualOverrides: Map<String, Boolean>
    ): DuplicateGroup {
        val scored = clusterTracks.map { track ->
            track to computeMasterScore(track)
        }.sortedByDescending { it.second }

        val masterPair = scored.first()
        val masterTrack = masterPair.first
        val masterScore = masterPair.second

        val masterRanked = RankedTrackItem(
            track = masterTrack,
            isMaster = true,
            masterScore = masterScore,
            downgradeReason = "Preserved Master (${masterTrack.effectiveBitDepth}-bit / ${masterTrack.sampleRateSummary} • ${masterTrack.verdict.badgeLabel})",
            selectedForDeletion = false
        )

        val redundantItems = scored.drop(1).map { (track, score) ->
            val reason = explainDowngradeReason(master = masterTrack, candidate = track)
            val isSelected = manualOverrides[track.fileUri] ?: true
            RankedTrackItem(
                track = track,
                isMaster = false,
                masterScore = score,
                downgradeReason = reason,
                selectedForDeletion = isSelected
            )
        }

        val avgSimilarity = redundantItems.map { item ->
            (cosineSimilarity(masterTrack.acousticFingerprint, item.track.acousticFingerprint) * 100f)
                .toInt()
                .coerceIn(85, 100)
        }.average().toInt().coerceIn(88, 100)

        return DuplicateGroup(
            groupId = groupId,
            canonicalTitle = masterTrack.title,
            canonicalArtist = masterTrack.artist,
            matchType = matchType,
            similarityPercent = if (matchType == DuplicateMatchType.EXACT_SHA256) 100 else avgSimilarity,
            masterItem = masterRanked,
            redundantItems = redundantItems
        )
    }

    /**
     * Master Selection Hierarchy:
     * 1. Authenticity Tier: TRUE_LOSSLESS > UPSAMPLED / ZERO-PADDED > FAKE_TRANSCODE
     * 2. Verified Effective Bit Depth: 32-bit > 24-bit > 16-bit (active PCM bit-planes, not padded header claims)
     * 3. Verified Sample Rate: 192 kHz > 96 kHz > 88.2 kHz > 48 kHz > 44.1 kHz (credited if ultrasonic energy verified)
     * 4. Integrity & Bitrate: CRC verified, lossless compressed container efficiency, complete tags & cover art
     */
    fun computeMasterScore(track: AudioTrack): Int {
        var score = 0

        // 1. Authenticity Tier (up to 30,000 pts)
        score += when (track.verdict) {
            AuthenticityVerdict.TRUE_LOSSLESS -> 30_000
            AuthenticityVerdict.UPSAMPLED -> 18_000
            AuthenticityVerdict.FAKE_TRANSCODE -> 5_000
        }

        // Penalty for zero-padded container deception
        if (track.isZeroPadded) {
            score -= 2_500
        }

        // 2. Verified Effective Bit Depth (up to 9,600 pts)
        score += (track.effectiveBitDepth * 300)

        // 3. Verified Effective Sample Rate (up to 3,840 pts)
        val creditedSampleRate = if (track.sampleRateHz > 48_000 && !track.hasUltrasonicEnergy) {
            44_100
        } else {
            track.effectiveSampleRateHz
        }
        score += (creditedSampleRate / 50)

        // 4. Integrity, Authenticity Score, Metadata & Codec Tagging Support
        if (track.crcVerified) score += 800
        score += track.authenticityScore * 5
        if (track.hasEmbeddedArtwork) score += 250
        if (track.isrc.isNotBlank()) score += 150
        score += (track.bitrateKbps / 20).coerceAtMost(400)

        // Prefer open tagged lossless containers (FLAC / ALAC / WavPack) when raw PCM quality is identical
        score += when (track.codec) {
            AudioCodec.FLAC -> 120
            AudioCodec.WAVPACK -> 100
            AudioCodec.ALAC -> 95
            AudioCodec.AIFF -> 80
            AudioCodec.WAV -> 75
            AudioCodec.APE -> 70
        }

        return score
    }

    private fun explainDowngradeReason(master: AudioTrack, candidate: AudioTrack): String {
        if (candidate.fullSha256 == master.fullSha256) {
            return "Byte-Identical Duplicate (SHA-256 Match)"
        }
        if (candidate.verdict == AuthenticityVerdict.FAKE_TRANSCODE) {
            return "Fake Lossless (${candidate.transcodeProfile} • ${candidate.spectralCutoffHz / 1000f}kHz cutoff)"
        }
        if (candidate.isZeroPadded && candidate.effectiveBitDepth < master.effectiveBitDepth) {
            return "Zero-Padded Container (${candidate.effectiveBitDepth}-bit padded to ${candidate.containerBitDepth}-bit)"
        }
        if (candidate.effectiveBitDepth < master.effectiveBitDepth) {
            return "Lower Verified Bit Depth (${candidate.effectiveBitDepth}-bit vs ${master.effectiveBitDepth}-bit Master)"
        }
        if (candidate.verdict == AuthenticityVerdict.UPSAMPLED && !candidate.hasUltrasonicEnergy) {
            return "Empty Ultrasonic Upsample (${candidate.sampleRateHz / 1000}kHz with no >22kHz energy)"
        }
        if (candidate.effectiveSampleRateHz < master.effectiveSampleRateHz) {
            return "Lower Sample Rate (${candidate.sampleRateSummary} vs ${master.sampleRateSummary})"
        }
        if (!candidate.hasEmbeddedArtwork && master.hasEmbeddedArtwork) {
            return "Redundant Copy (Missing Embedded Cover Art & ISRC)"
        }
        return "Redundant ${candidate.codec.label} Copy (Lower Compression / Tag Completeness)"
    }

    fun areSameSongAcousticallyAndByTag(a: AudioTrack, b: AudioTrack): Boolean {
        if (a.fullSha256.isNotBlank() && a.fullSha256 == b.fullSha256) return true

        // Check duration within ±2.0s (2000ms)
        val durationDiffMs = abs(a.durationMs - b.durationMs)
        if (durationDiffMs > 2000L) return false

        // Normalized title & artist similarity
        val normTitleA = normalizeTag(a.title)
        val normTitleB = normalizeTag(b.title)
        val normArtistA = normalizeTag(a.artist)
        val normArtistB = normalizeTag(b.artist)

        val titleSim = levenshteinSimilarity(normTitleA, normTitleB)
        val artistSim = levenshteinSimilarity(normArtistA, normArtistB)

        // Acoustic chromagram cosine similarity
        val acousticSim = cosineSimilarity(a.acousticFingerprint, b.acousticFingerprint)

        // Match if both acoustic waveform and tags strongly align, OR if ISRC matches and acoustic > 0.85
        val isrcMatches = a.isrc.isNotBlank() && a.isrc.equals(b.isrc, ignoreCase = true)
        if (isrcMatches && acousticSim >= 0.84f) return true

        return (titleSim >= 0.78f && artistSim >= 0.75f && acousticSim >= 0.86f) ||
            (acousticSim >= 0.96f && titleSim >= 0.60f)
    }

    fun normalizeTag(raw: String): String {
        return raw.lowercase()
            .replace(Regex("\\(.*?\\)|\\[.*?\\]"), " ") // strip (Remastered), [24-bit], (Master), etc.
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\b(remaster|remastered|master|24bit|16bit|32bit|96khz|192khz|flac|wav|hi-res)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun levenshteinSimilarity(s1: String, s2: String): Float {
        if (s1 == s2) return 1.0f
        if (s1.isEmpty() || s2.isEmpty()) return 0.0f

        val maxLen = max(s1.length, s2.length)
        val dp = IntArray(s2.length + 1) { it }
        for (i in 1..s1.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..s2.length) {
                val temp = dp[j]
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[j] = min(min(dp[j] + 1, dp[j - 1] + 1), prev + cost)
                prev = temp
            }
        }
        return (1.0f - dp[s2.length].toFloat() / maxLen.toFloat()).coerceIn(0f, 1f)
    }

    fun cosineSimilarity(vecA: List<Float>, vecB: List<Float>): Float {
        if (vecA.isEmpty() || vecB.isEmpty()) return 0f
        val size = min(vecA.size, vecB.size)
        var dot = 0.0
        var magA = 0.0
        var magB = 0.0
        for (i in 0 until size) {
            val a = vecA[i].toDouble()
            val b = vecB[i].toDouble()
            dot += a * b
            magA += a * a
            magB += b * b
        }
        val denom = sqrt(magA) * sqrt(magB)
        return if (denom <= 1e-9) 0f else (dot / denom).toFloat().coerceIn(0f, 1f)
    }
}
