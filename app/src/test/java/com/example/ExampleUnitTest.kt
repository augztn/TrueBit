package com.example

import com.example.domain.model.AudioCodec
import com.example.domain.model.AudioTrack
import com.example.domain.model.AuthenticityVerdict
import com.example.domain.usecase.DuplicateRankerUseCase
import com.example.dsp.BitDepthAuditor
import com.example.dsp.FftSpectralAnalyzer
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun bitDepthAuditor_detects16BitZeroPaddedInside24BitContainer() {
        val auditor = BitDepthAuditor()
        // Generate 24-bit samples where lower 8 bits [15..0 of 32-bit left-aligned] are strictly 0
        val paddedSamples = IntArray(1024) { idx ->
            val s16 = ((sin(2.0 * PI * idx / 32.0) * 32000).toInt())
            s16 shl 16 // Only top 16 bits populated
        }
        val paddedResult = auditor.auditBitPlanes(
            leftAlignedSamples32 = paddedSamples,
            declaredContainerBitDepth = 24
        )
        assertEquals(24, paddedResult.containerBitDepth)
        assertEquals(16, paddedResult.effectiveBitDepth)
        assertTrue(paddedResult.isZeroPadded)

        // Now generate genuine 24-bit samples with active lower bits [23..8]
        val true24Samples = IntArray(1024) { idx ->
            val s24 = ((sin(2.0 * PI * idx / 32.0) * 8000000).toInt()) or ((idx * 73) and 0xFF)
            s24 shl 8
        }
        val true24Result = auditor.auditBitPlanes(
            leftAlignedSamples32 = true24Samples,
            declaredContainerBitDepth = 24
        )
        assertEquals(24, true24Result.effectiveBitDepth)
        assertFalse(true24Result.isZeroPadded)
    }

    @Test
    fun fftSpectralAnalyzer_detects16kHzLossyTranscodeCutoff() {
        val analyzer = FftSpectralAnalyzer()
        val sampleRate = 44_100
        // Synthesize window with harmonics only up to 15.2 kHz (simulating 128 kbps MP3 brick-wall shelf)
        val window = FloatArray(FftSpectralAnalyzer.FFT_SIZE) { i ->
            val t = i.toDouble() / sampleRate.toDouble()
            var s = 0.0
            var freq = 1000.0
            while (freq <= 15_000.0) {
                s += 0.08 * sin(2.0 * PI * freq * t)
                freq += 1000.0
            }
            s.toFloat()
        }

        val result = analyzer.analyzePcmWindows(
            pcmWindows = listOf(window),
            sampleRateHz = sampleRate,
            containerBitDepth = 16,
            effectiveBitDepth = 16,
            isZeroPadded = false
        )

        assertEquals(AuthenticityVerdict.FAKE_TRANSCODE, result.verdict)
        assertTrue(result.spectralCutoffHz <= 16_600)
    }

    @Test
    fun duplicateRanker_preservesTrue24BitMasterOverZeroPaddedAndTranscodedCopies() {
        val ranker = DuplicateRankerUseCase()
        val fingerprint = List(32) { 0.25f }

        val trueMaster24 = createTestTrack(
            uri = "file:///master24.wav",
            bitDepth = 24,
            effectiveBitDepth = 24,
            isZeroPadded = false,
            sampleRate = 96_000,
            verdict = AuthenticityVerdict.TRUE_LOSSLESS,
            fingerprint = fingerprint
        )
        val padded24 = createTestTrack(
            uri = "file:///padded24.flac",
            bitDepth = 24,
            effectiveBitDepth = 16,
            isZeroPadded = true,
            sampleRate = 44_100,
            verdict = AuthenticityVerdict.UPSAMPLED,
            fingerprint = fingerprint
        )
        val fakeTranscode = createTestTrack(
            uri = "file:///fake128.flac",
            bitDepth = 16,
            effectiveBitDepth = 16,
            isZeroPadded = false,
            sampleRate = 44_100,
            verdict = AuthenticityVerdict.FAKE_TRANSCODE,
            fingerprint = fingerprint
        )

        val groups = ranker.buildDuplicateGroups(listOf(padded24, fakeTranscode, trueMaster24))
        assertEquals(1, groups.size)
        val group = groups.first()
        assertEquals("file:///master24.wav", group.masterItem.track.fileUri)
        assertFalse(group.masterItem.selectedForDeletion)
        assertEquals(2, group.redundantItems.size)
        assertTrue(group.redundantItems.all { it.selectedForDeletion })
    }

    private fun createTestTrack(
        uri: String,
        bitDepth: Int,
        effectiveBitDepth: Int,
        isZeroPadded: Boolean,
        sampleRate: Int,
        verdict: AuthenticityVerdict,
        fingerprint: List<Float>
    ): AudioTrack {
        return AudioTrack(
            fileUri = uri,
            fileName = uri.substringAfterLast('/'),
            folderName = "Test",
            lastModified = 1000L,
            fileSize = 25_000_000L,
            codec = AudioCodec.FLAC,
            containerBitDepth = bitDepth,
            effectiveBitDepth = effectiveBitDepth,
            isFloatingPoint = false,
            isZeroPadded = isZeroPadded,
            sampleRateHz = sampleRate,
            effectiveSampleRateHz = sampleRate,
            hasUltrasonicEnergy = true,
            channels = 2,
            durationMs = 210_000L,
            bitrateKbps = 1411,
            title = "Solaris Horizon",
            artist = "Aether Ensemble",
            album = "Quantum Acoustics",
            trackNumber = 1,
            isrc = "US-TB1-26-00101",
            hasEmbeddedArtwork = true,
            partialHash = "abc",
            fullSha256 = "sha_$uri",
            acousticFingerprint = fingerprint,
            spectralCutoffHz = if (verdict == AuthenticityVerdict.FAKE_TRANSCODE) 16_000 else sampleRate / 2,
            authenticityScore = if (verdict == AuthenticityVerdict.TRUE_LOSSLESS) 98 else 30,
            verdict = verdict,
            transcodeProfile = verdict.badgeLabel,
            dynamicRangeDb = 14.2f,
            spectrumBinsDb = List(64) { -20f },
            bitPlaneActivity = List(32) { 0.8f },
            crcVerified = true
        )
    }
}
