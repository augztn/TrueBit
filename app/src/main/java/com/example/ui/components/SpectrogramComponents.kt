package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.AuthenticityVerdict
import com.example.ui.theme.LocalTrueBitColors
import java.util.Locale

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return if (mb >= 1024.0) {
        String.format(Locale.US, "%.2f GB", mb / 1024.0)
    } else if (mb >= 1.0) {
        String.format(Locale.US, "%.1f MB", mb)
    } else {
        String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    }
}

fun formatDuration(durationMs: Long): String {
    val totalSec = (durationMs / 1000L).coerceAtLeast(1L)
    val mins = totalSec / 60
    val secs = totalSec % 60
    return String.format(Locale.US, "%d:%02d", mins, secs)
}

@Composable
fun VerdictBadge(
    verdict: AuthenticityVerdict,
    authenticityScore: Int? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalTrueBitColors.current
    val (bgColor, fgColor, label) = when (verdict) {
        AuthenticityVerdict.TRUE_LOSSLESS -> Triple(colors.emeraldVerdictBg, colors.emeraldVerdict, "True Lossless")
        AuthenticityVerdict.UPSAMPLED -> Triple(colors.amberVerdictBg, colors.amberVerdict, "Padded / Upsampled")
        AuthenticityVerdict.FAKE_TRANSCODE -> Triple(colors.crimsonVerdictBg, colors.crimsonVerdict, "Fake Transcode")
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            val icon = when (verdict) {
                AuthenticityVerdict.TRUE_LOSSLESS -> Icons.Filled.CheckCircle
                AuthenticityVerdict.UPSAMPLED -> Icons.Filled.Warning
                AuthenticityVerdict.FAKE_TRANSCODE -> Icons.Filled.Error
            }
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = fgColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = if (authenticityScore != null) "$label • $authenticityScore%" else label,
                style = MaterialTheme.typography.labelMedium,
                color = fgColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun MasterLockBadge(modifier: Modifier = Modifier) {
    val colors = LocalTrueBitColors.current
    Surface(
        color = colors.emeraldVerdictBg,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = "Keep Master",
                tint = colors.emeraldVerdict,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = "Keep Master",
                style = MaterialTheme.typography.labelMedium,
                color = colors.emeraldVerdict,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun InteractiveSpectrogramCanvas(
    spectrumBinsDb: List<Float>,
    sampleRateHz: Int,
    spectralCutoffHz: Int,
    verdict: AuthenticityVerdict,
    isCompact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = LocalTrueBitColors.current
    val bins = if (spectrumBinsDb.isEmpty()) List(48) { -24f - it * 0.8f } else spectrumBinsDb
    var selectedBinIndex by remember { mutableIntStateOf(-1) }

    val nyquistHz = (sampleRateHz / 2).coerceAtLeast(22_050)
    val cutoffRatio = (spectralCutoffHz.toFloat() / nyquistHz.toFloat()).coerceIn(0.15f, 1.0f)

    val barAccent = when (verdict) {
        AuthenticityVerdict.TRUE_LOSSLESS -> colors.emeraldVerdict
        AuthenticityVerdict.UPSAMPLED -> colors.amberVerdict
        AuthenticityVerdict.FAKE_TRANSCODE -> colors.crimsonVerdict
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.cardHigh)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val cutoffKhz = String.format(Locale.US, "%.1f kHz", spectralCutoffHz / 1000f)
            val nyquistKhz = String.format(Locale.US, "%.1f kHz", nyquistHz / 1000f)
            Text(
                text = if (selectedBinIndex in bins.indices) {
                    val tappedFreqKhz = ((selectedBinIndex + 1).toFloat() / bins.size.toFloat()) * (nyquistHz / 1000f)
                    String.format(Locale.US, "%.1f kHz (%.0f dB)", tappedFreqKhz, bins[selectedBinIndex])
                } else {
                    "Cutoff: $cutoffKhz / Max: $nyquistKhz"
                },
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isCompact) 56.dp else 120.dp)
                .pointerInput(bins) {
                    detectTapGestures { offset ->
                        val ratio = (offset.x / size.width.toFloat()).coerceIn(0f, 0.999f)
                        selectedBinIndex = (ratio * bins.size).toInt()
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val barCount = bins.size
                val slotWidth = w / barCount.toFloat()
                val barWidth = (slotWidth * 0.65f).coerceAtLeast(2f)

                for (i in 0 until barCount) {
                    val db = bins[i].coerceIn(-110f, 0f)
                    val norm = ((db + 110f) / 110f).coerceIn(0.08f, 0.95f)
                    val barHeight = h * norm
                    val x = i * slotWidth
                    val topY = h - barHeight

                    val binRatio = i.toFloat() / barCount.toFloat()
                    val isDepletedZone = binRatio > cutoffRatio && verdict != AuthenticityVerdict.TRUE_LOSSLESS
                    val color = when {
                        i == selectedBinIndex -> colors.cyanAccent
                        isDepletedZone -> colors.crimsonVerdict.copy(alpha = 0.25f)
                        else -> barAccent.copy(alpha = 0.8f)
                    }

                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x, topY),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(3f, 3f)
                    )
                }

                // Cutoff threshold line
                val cutoffX = (w * cutoffRatio).coerceIn(4f, w - 4f)
                drawLine(
                    color = if (verdict == AuthenticityVerdict.TRUE_LOSSLESS) colors.emeraldVerdict else colors.crimsonVerdict,
                    start = Offset(cutoffX, 0f),
                    end = Offset(cutoffX, h),
                    strokeWidth = 2.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                )
            }
        }
    }
}

@Composable
fun BitPlaneMatrixCanvas(
    bitPlaneActivity: List<Float>,
    containerBitDepth: Int,
    effectiveBitDepth: Int,
    isZeroPadded: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = LocalTrueBitColors.current
    val activity = if (bitPlaneActivity.size == 32) {
        bitPlaneActivity
    } else {
        List(32) { idx -> if (idx < effectiveBitDepth) 0.75f else 0f }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.cardHigh)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Active Bit Depth: ${effectiveBitDepth}-bit (Container: ${containerBitDepth}-bit)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (isZeroPadded) "Zero-Padded" else "Verified",
                style = MaterialTheme.typography.labelMedium,
                color = if (isZeroPadded) colors.amberVerdict else colors.emeraldVerdict,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
        ) {
            val w = size.width
            val h = size.height
            val slotW = w / 32f
            val barW = (slotW * 0.7f).coerceAtLeast(2.5f)

            for (bit in 0 until 32) {
                val act = activity[bit].coerceIn(0f, 1f)
                val x = bit * slotW
                val isWithinContainer = bit < containerBitDepth
                val isWithinEffective = bit < effectiveBitDepth

                drawRoundRect(
                    color = Color.Gray.copy(alpha = 0.15f),
                    topLeft = Offset(x, 0f),
                    size = Size(barW, h),
                    cornerRadius = CornerRadius(2f, 2f)
                )

                val barH = if (act > 0.015f) (h * act.coerceIn(0.2f, 1f)) else 4f
                val barColor = when {
                    !isWithinContainer -> Color.Gray.copy(alpha = 0.25f)
                    isWithinContainer && !isWithinEffective -> colors.amberVerdict
                    else -> colors.emeraldVerdict
                }

                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(x, h - barH),
                    size = Size(barW, barH),
                    cornerRadius = CornerRadius(2f, 2f)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("1–16 bit", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
            Text("17–24 bit", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
            Text("25–32 bit", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
        }
    }
}
