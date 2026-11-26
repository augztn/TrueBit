package com.example.dsp

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Generates a compact 32-band logarithmic chromagram / energy-band acoustic fingerprint
 * from decoded PCM frames (sampling normalized middle 60 seconds of audio).
 * Used alongside normalized metadata matching to group the same song across different
 * bit depths (16-bit vs 24-bit vs 32-bit), sample rates (44.1kHz vs 96kHz), or containers.
 */
class AcousticFingerprinter(
    private val fftAnalyzer: FftSpectralAnalyzer = FftSpectralAnalyzer()
) {
    companion object {
        const val FINGERPRINT_BANDS = 32
    }

    fun generateChromagramFingerprint(
        pcmWindows: List<FloatArray>,
        sampleRateHz: Int
    ): List<Float> {
        if (pcmWindows.isEmpty()) {
            return List(FINGERPRINT_BANDS) { 0.5f }
        }

        val fftSize = FftSpectralAnalyzer.FFT_SIZE
        val halfBins = fftSize / 2
        val hzPerBin = (sampleRateHz / 2f) / halfBins.toFloat()

        // Focus purely on 80 Hz .. 12,000 Hz so that 16-bit, 24-bit, 96kHz, and 128kbps transcodes
        // of the exact same musical recording produce matching acoustic fingerprints!
        val bandEdgesHz = FloatArray(FINGERPRINT_BANDS + 1)
        val minHz = 80.0
        val maxHz = 12000.0
        val logMin = ln(minHz)
        val logMax = ln(maxHz)

        for (i in 0..FINGERPRINT_BANDS) {
            val frac = i.toDouble() / FINGERPRINT_BANDS.toDouble()
            bandEdgesHz[i] = kotlin.math.exp(logMin + frac * (logMax - logMin)).toFloat()
        }

        val bandEnergy = DoubleArray(FINGERPRINT_BANDS)
        val real = FloatArray(fftSize)
        val imag = FloatArray(fftSize)

        for (window in pcmWindows) {
            if (window.size < fftSize) continue
            for (i in 0 until fftSize) {
                real[i] = window[i]
                imag[i] = 0f
            }
            fftAnalyzer.computeRadix2Fft(real, imag)

            for (band in 0 until FINGERPRINT_BANDS) {
                val startBin = (bandEdgesHz[band] / hzPerBin).toInt().coerceIn(1, halfBins - 2)
                val endBin = (bandEdgesHz[band + 1] / hzPerBin).toInt().coerceIn(startBin + 1, halfBins - 1)
                var sum = 0.0
                for (b in startBin..endBin) {
                    val mag = sqrt((real[b] * real[b] + imag[b] * imag[b]).toDouble())
                    sum += mag
                }
                bandEnergy[band] += sum / (endBin - startBin + 1)
            }
        }

        // L2-normalize the 32-band vector for scale-invariant cosine similarity
        var normSq = 0.0
        for (e in bandEnergy) {
            normSq += e * e
        }
        val norm = max(sqrt(normSq), 1e-9)

        return List(FINGERPRINT_BANDS) { i ->
            (bandEnergy[i] / norm).toFloat()
        }
    }

    /**
     * Fallback deterministic waveform envelope fingerprint when raw PCM byte buffer is analyzed directly.
     */
    fun generateEnvelopeFromBytes(rawBytes: ByteArray): List<Float> {
        if (rawBytes.isEmpty()) return List(FINGERPRINT_BANDS) { 0.5f }
        val buckets = DoubleArray(FINGERPRINT_BANDS)
        val step = (rawBytes.size / FINGERPRINT_BANDS).coerceAtLeast(1)
        for (b in 0 until FINGERPRINT_BANDS) {
            val start = (b * step).coerceAtMost(rawBytes.lastIndex)
            val end = ((b + 1) * step).coerceAtMost(rawBytes.size)
            var sum = 0.0
            for (i in start until end) {
                sum += abs(rawBytes[i].toInt())
            }
            buckets[b] = sum / (end - start).coerceAtLeast(1)
        }
        var normSq = 0.0
        for (v in buckets) normSq += v * v
        val norm = max(sqrt(normSq), 1e-9)
        return List(FINGERPRINT_BANDS) { i -> (buckets[i] / norm).toFloat() }
    }
}
