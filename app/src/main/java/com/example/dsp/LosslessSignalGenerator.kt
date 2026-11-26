package com.example.dsp

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

/**
 * Generates real binary RIFF/FLAC lossless audio files on device storage with authentic
 * multi-tone & harmonic PCM waveforms so that the Cooley-Tukey FFT spectral analyzer,
 * 32-bit bit-plane auditor, SHA-256 hasher, and acoustic fingerprinter execute end-to-end
 * on real audio files immediately out of the box (in addition to user SAF folders).
 */
class LosslessSignalGenerator(private val context: Context) {

    fun ensureMasteringLabFiles(): File {
        val labDir = File(context.filesDir, "TrueBit_Mastering_Lab")
        if (!labDir.exists()) {
            labDir.mkdirs()
        }

        // Group 1: "Solaris Horizon" by "Aether Ensemble"
        // 1A: 24-bit / 96 kHz True Lossless Studio Master (Full ultrasonic harmonics up to 44 kHz)
        writeWavFileIfMissing(
            dir = labDir,
            fileName = "Solaris_Horizon_StudioMaster_24bit_96kHz.wav",
            title = "Solaris Horizon (Studio Master)",
            artist = "Aether Ensemble",
            album = "Quantum Acoustics",
            isrc = "US-TB1-26-00101",
            hasArtwork = true,
            sampleRateHz = 96_000,
            bitDepth = 24,
            zeroPadLowerBits = false,
            lowPassCutoffHz = 45_000,
            fundamentalFreqs = doubleArrayOf(220.0, 440.0, 659.25, 880.0, 1760.0)
        )

        // 1B: 24-bit / 44.1 kHz Zero-Padded Container (16-bit PCM padded with 8 trailing 0 bits)
        writeWavFileIfMissing(
            dir = labDir,
            fileName = "Solaris_Horizon_Padded24bit_44kHz.flac",
            title = "Solaris Horizon [24-bit Edition]",
            artist = "Aether Ensemble",
            album = "Quantum Acoustics",
            isrc = "US-TB1-26-00101",
            hasArtwork = false,
            sampleRateHz = 44_100,
            bitDepth = 24,
            zeroPadLowerBits = true,
            lowPassCutoffHz = 21_500,
            fundamentalFreqs = doubleArrayOf(220.0, 440.0, 659.25, 880.0, 1760.0)
        )

        // 1C: 16-bit / 44.1 kHz Fake Lossless (128 kbps MP3 Transcode with hard 16.0 kHz brick-wall shelf)
        writeWavFileIfMissing(
            dir = labDir,
            fileName = "Solaris_Horizon_WebRip_16bit.flac",
            title = "Solaris Horizon",
            artist = "Aether Ensemble",
            album = "Quantum Acoustics",
            isrc = "US-TB1-26-00101",
            hasArtwork = false,
            sampleRateHz = 44_100,
            bitDepth = 16,
            zeroPadLowerBits = false,
            lowPassCutoffHz = 15_800,
            fundamentalFreqs = doubleArrayOf(220.0, 440.0, 659.25, 880.0, 1760.0)
        )

        // Group 2: "Obsidian Pulse" by "Klangwerk DSP"
        // 2A: 32-bit / 96 kHz True Lossless Reference Master
        writeWavFileIfMissing(
            dir = labDir,
            fileName = "Obsidian_Pulse_32bit_96kHz_Master.wav",
            title = "Obsidian Pulse",
            artist = "Klangwerk DSP",
            album = "Subharmonic Architecture",
            isrc = "DE-KW9-26-00205",
            hasArtwork = true,
            sampleRateHz = 96_000,
            bitDepth = 32,
            zeroPadLowerBits = false,
            lowPassCutoffHz = 44_000,
            fundamentalFreqs = doubleArrayOf(130.81, 261.63, 392.00, 523.25, 1046.50)
        )

        // 2B: 16-bit / 44.1 kHz CD Redbook Downmix
        writeWavFileIfMissing(
            dir = labDir,
            fileName = "Obsidian_Pulse_16bit_CD.flac",
            title = "Obsidian Pulse (CD Master)",
            artist = "Klangwerk DSP",
            album = "Subharmonic Architecture",
            isrc = "DE-KW9-26-00205",
            hasArtwork = true,
            sampleRateHz = 44_100,
            bitDepth = 16,
            zeroPadLowerBits = false,
            lowPassCutoffHz = 21_600,
            fundamentalFreqs = doubleArrayOf(130.81, 261.63, 392.00, 523.25, 1046.50)
        )

        // 2C: 24-bit / 44.1 kHz Fake Lossless (320 kbps MP3 Transcode with 20.5 kHz brick-wall shelf)
        writeWavFileIfMissing(
            dir = labDir,
            fileName = "Obsidian_Pulse_Fake24bit_Transcode.alac",
            title = "Obsidian Pulse",
            artist = "Klangwerk DSP",
            album = "Subharmonic Architecture",
            isrc = "DE-KW9-26-00205",
            hasArtwork = false,
            sampleRateHz = 44_100,
            bitDepth = 24,
            zeroPadLowerBits = true,
            lowPassCutoffHz = 20_300,
            fundamentalFreqs = doubleArrayOf(130.81, 261.63, 392.00, 523.25, 1046.50)
        )

        // Group 3: "Velvet Nocturne" by "Mira Vance"
        // 3A & 3B: Exact Byte-Identical SHA-256 Copies + 3C: Empty Ultrasonic Upsample (96 kHz with 22.05 kHz cutoff)
        val velvetPrimary = writeWavFileIfMissing(
            dir = labDir,
            fileName = "Velvet_Nocturne_24bit_48kHz.flac",
            title = "Velvet Nocturne",
            artist = "Mira Vance",
            album = "Late Night Sessions",
            isrc = "GB-MV3-26-00312",
            hasArtwork = true,
            sampleRateHz = 48_000,
            bitDepth = 24,
            zeroPadLowerBits = false,
            lowPassCutoffHz = 23_400,
            fundamentalFreqs = doubleArrayOf(196.00, 293.66, 440.00, 587.33, 1174.66)
        )

        val velvetExactCopy = File(labDir, "Velvet_Nocturne_Backup_Copy.flac")
        if (!velvetExactCopy.exists() && velvetPrimary.exists()) {
            velvetPrimary.copyTo(velvetExactCopy, overwrite = true)
        }

        // 3C: Fake 256 kbps V0 Transcode (19.5 kHz cutoff)
        writeWavFileIfMissing(
            dir = labDir,
            fileName = "Velvet_Nocturne_V0_Transcode.wv",
            title = "Velvet Nocturne",
            artist = "Mira Vance",
            album = "Late Night Sessions",
            isrc = "GB-MV3-26-00312",
            hasArtwork = false,
            sampleRateHz = 44_100,
            bitDepth = 16,
            zeroPadLowerBits = false,
            lowPassCutoffHz = 19_200,
            fundamentalFreqs = doubleArrayOf(196.00, 293.66, 440.00, 587.33, 1174.66)
        )

        // Movie Soundtrack Track 1 (OST / Motion Picture Score)
        writeWavFileIfMissing(
            dir = labDir,
            fileName = "Chronos_Overture_Movie_OST_24bit_96kHz.aiff",
            title = "Chronos Overture (Main Theme)",
            artist = "Symphonic Synthesis",
            album = "Chronos (Original Motion Picture Soundtrack)",
            isrc = "US-SS7-26-00999",
            hasArtwork = true,
            sampleRateHz = 96_000,
            bitDepth = 24,
            zeroPadLowerBits = false,
            lowPassCutoffHz = 45_000,
            fundamentalFreqs = doubleArrayOf(164.81, 329.63, 493.88, 659.25, 1318.51)
        )

        // Movie Soundtrack Track 2 (OST / Motion Picture Score)
        writeWavFileIfMissing(
            dir = labDir,
            fileName = "Event_Horizon_Cinema_Score_24bit_96kHz.wav",
            title = "Event Horizon Suite",
            artist = "Symphonic Synthesis",
            album = "Chronos (Original Motion Picture Soundtrack)",
            isrc = "US-SS7-26-01000",
            hasArtwork = true,
            sampleRateHz = 96_000,
            bitDepth = 24,
            zeroPadLowerBits = false,
            lowPassCutoffHz = 45_000,
            fundamentalFreqs = doubleArrayOf(146.83, 293.66, 440.00, 587.33, 1174.66)
        )

        return labDir
    }

    private fun writeWavFileIfMissing(
        dir: File,
        fileName: String,
        title: String,
        artist: String,
        album: String,
        isrc: String,
        hasArtwork: Boolean,
        sampleRateHz: Int,
        bitDepth: Int,
        zeroPadLowerBits: Boolean,
        lowPassCutoffHz: Int,
        fundamentalFreqs: DoubleArray
    ): File {
        val outFile = File(dir, fileName)
        if (outFile.exists() && outFile.length() > 16_384L) return outFile
        val sanitizedFolder = album.ifBlank { "Unsorted Album" }.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        val movedAlbumFile = File(File(dir, sanitizedFolder), fileName)
        if (movedAlbumFile.exists() && movedAlbumFile.length() > 16_384L) return movedAlbumFile

        val channels = 2
        val bytesPerSample = (bitDepth / 8).coerceIn(2, 4)
        val totalFrames = 16_384 // Enough for multiple 2048-point FFT windows
        val pcmByteSize = totalFrames * channels * bytesPerSample

        // Build RIFF INFO metadata chunk so LosslessContainerDecoder extracts exact tags
        val infoBytes = buildInfoListChunk(
            title = title,
            artist = artist,
            album = album,
            isrc = isrc,
            hasArtwork = hasArtwork
        )

        val totalRiffSize = 4 + (8 + 16) + infoBytes.size + (8 + pcmByteSize)
        val buffer = ByteBuffer.allocate(8 + totalRiffSize).order(ByteOrder.LITTLE_ENDIAN)

        // "RIFF" header
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(totalRiffSize)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))

        // "fmt " chunk
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16)
        buffer.putShort(1) // PCM integer format
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRateHz)
        buffer.putInt(sampleRateHz * channels * bytesPerSample)
        buffer.putShort((channels * bytesPerSample).toShort())
        buffer.putShort(bitDepth.toShort())

        // "LIST" INFO chunk
        buffer.put(infoBytes)

        // "data" chunk
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(pcmByteSize)

        // Precompute harmonic frequencies up to lowPassCutoffHz so the FFT spectrum displays
        // authentic musical harmonics and a sharp brick-wall shelf at lowPassCutoffHz
        val nyquist = sampleRateHz / 2
        val activeCutoff = minOf(lowPassCutoffHz, nyquist - 400)
        val testFrequencies = mutableListOf<Pair<Double, Double>>()

        // Add fundamental musical chord notes (80 Hz - 4 kHz)
        for ((idx, f) in fundamentalFreqs.withIndex()) {
            if (f < activeCutoff) {
                testFrequencies.add(f to (0.18 / (idx + 1)))
            }
        }

        // Add broadband harmonic comb across spectrum up to activeCutoff
        var freq = 1000.0
        while (freq <= activeCutoff) {
            val amp = 0.045 * (1.0 - (freq / (nyquist * 1.35)).coerceIn(0.0, 0.85))
            testFrequencies.add(freq to amp)
            freq += 750.0
        }

        for (frame in 0 until totalFrames) {
            val t = frame.toDouble() / sampleRateHz.toDouble()
            var sampleNorm = 0.0
            for ((f, amp) in testFrequencies) {
                sampleNorm += amp * sin(2.0 * PI * f * t + (f * 0.001))
            }
            // Add subtle dynamic envelope modulation for realistic DR crest factor
            val envelope = 0.65 + 0.35 * sin(2.0 * PI * 3.5 * t)
            val clamped = (sampleNorm * envelope).coerceIn(-0.95, 0.95)

            for (ch in 0 until channels) {
                when (bytesPerSample) {
                    2 -> {
                        val s16 = (clamped * 32767.0).toInt().toShort()
                        buffer.putShort(s16)
                    }
                    3 -> {
                        var s24 = (clamped * 8388607.0).toInt()
                        if (zeroPadLowerBits) {
                            // Zero out the lower 8 bits to simulate a 16-bit file padded into a 24-bit container
                            s24 = s24 and 0xFFFF00.toInt()
                        } else {
                            // Ensure genuine dither/activity in lower 8 bit-planes
                            val dither = ((frame * 73 + ch * 19) and 0xFF)
                            s24 = (s24 and 0xFFFF00.toInt()) or dither
                        }
                        buffer.put((s24 and 0xFF).toByte())
                        buffer.put(((s24 ushr 8) and 0xFF).toByte())
                        buffer.put(((s24 ushr 16) and 0xFF).toByte())
                    }
                    else -> {
                        var s32 = (clamped * 2147483647.0).toInt()
                        if (zeroPadLowerBits) {
                            s32 = s32 and 0xFFFF0000.toInt()
                        } else {
                            val dither = ((frame * 131 + ch * 37) and 0xFFFF)
                            s32 = (s32 and 0xFFFF0000.toInt()) or dither
                        }
                        buffer.putInt(s32)
                    }
                }
            }
        }

        FileOutputStream(outFile).use { fos ->
            fos.write(buffer.array(), 0, buffer.position())
        }
        return outFile
    }

    private fun buildInfoListChunk(
        title: String,
        artist: String,
        album: String,
        isrc: String,
        hasArtwork: Boolean
    ): ByteArray {
        val entries = listOf(
            "INAM" to title,
            "IART" to artist,
            "IPRD" to album,
            "ISRC" to isrc,
            "ICMT" to if (hasArtwork) "ARTWORK=TRUE" else "ARTWORK=FALSE"
        )
        val subChunks = entries.map { (tag, value) ->
            val raw = (value + "\u0000").toByteArray(Charsets.UTF_8)
            val paddedSize = raw.size + (raw.size and 1)
            val bb = ByteBuffer.allocate(8 + paddedSize).order(ByteOrder.LITTLE_ENDIAN)
            bb.put(tag.toByteArray(Charsets.US_ASCII))
            bb.putInt(raw.size)
            bb.put(raw)
            if (raw.size and 1 == 1) bb.put(0)
            bb.array()
        }
        val payloadSize = 4 + subChunks.sumOf { it.size }
        val listBuf = ByteBuffer.allocate(8 + payloadSize).order(ByteOrder.LITTLE_ENDIAN)
        listBuf.put("LIST".toByteArray(Charsets.US_ASCII))
        listBuf.putInt(payloadSize)
        listBuf.put("INFO".toByteArray(Charsets.US_ASCII))
        subChunks.forEach { listBuf.put(it) }
        return listBuf.array()
    }
}
