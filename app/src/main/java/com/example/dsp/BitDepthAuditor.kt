package com.example.dsp

data class BitDepthAuditResult(
    val containerBitDepth: Int,
    val effectiveBitDepth: Int,
    val isFloatingPoint: Boolean,
    val isZeroPadded: Boolean,
    val bitPlaneActivity: List<Float>
)

/**
 * Inspects raw 32-bit left-aligned PCM sample integers across non-silent audio frames
 * to determine true active bit depth vs. zero-padded container headers (e.g. 16-bit padded to 24-bit).
 */
class BitDepthAuditor {

    /**
     * @param leftAlignedSamples32 PCM samples normalized to 32-bit signed integers where
     *   - 16-bit audio occupies bits [31..16] (lower 16 bits [15..0] are zero)
     *   - 24-bit audio occupies bits [31..8]  (lower 8 bits [7..0] are zero)
     *   - 32-bit audio occupies bits [31..0]
     */
    fun auditBitPlanes(
        leftAlignedSamples32: IntArray,
        declaredContainerBitDepth: Int,
        isFloatingPoint: Boolean = false
    ): BitDepthAuditResult {
        val bitHits = IntArray(32)
        var nonSilentSamples = 0

        for (sample in leftAlignedSamples32) {
            if (sample == 0 || sample == Int.MIN_VALUE) continue
            nonSilentSamples++

            for (bit in 0 until 32) {
                // Bit 0 is MSB (bit 31 of Int), Bit 31 is LSB (bit 0 of Int)
                val shift = 31 - bit
                if (((sample ushr shift) and 1) != 0) {
                    bitHits[bit]++
                }
            }
        }

        if (nonSilentSamples == 0) {
            val defaultActivity = List(32) { idx ->
                if (idx < declaredContainerBitDepth) 0.5f else 0f
            }
            return BitDepthAuditResult(
                containerBitDepth = declaredContainerBitDepth,
                effectiveBitDepth = declaredContainerBitDepth,
                isFloatingPoint = isFloatingPoint,
                isZeroPadded = false,
                bitPlaneActivity = defaultActivity
            )
        }

        // Compute activity ratio [0.0 .. 1.0] for each bit plane from MSB (0) to LSB (31)
        val activity = List(32) { bit ->
            val ratio = bitHits[bit].toFloat() / nonSilentSamples.toFloat()
            (ratio * 1.85f).coerceIn(0f, 1f)
        }

        // Check active bit planes in lower 16..23 (24-bit zone) and 24..31 (32-bit zone)
        val noiseGateThreshold = 0.015f
        val has24BitZoneActivity = (16..23).count { activity[it] > noiseGateThreshold } >= 3
        val has32BitZoneActivity = (24..31).count { activity[it] > noiseGateThreshold } >= 3

        val effectiveBitDepth = when {
            declaredContainerBitDepth >= 32 && has32BitZoneActivity -> 32
            declaredContainerBitDepth >= 24 && has24BitZoneActivity -> 24
            else -> 16
        }

        val isZeroPadded = effectiveBitDepth < declaredContainerBitDepth

        return BitDepthAuditResult(
            containerBitDepth = declaredContainerBitDepth,
            effectiveBitDepth = effectiveBitDepth,
            isFloatingPoint = isFloatingPoint,
            isZeroPadded = isZeroPadded,
            bitPlaneActivity = activity
        )
    }
}
