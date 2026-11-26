package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.AudioTrack
import com.example.domain.model.AuthenticityVerdict
import com.example.ui.components.BitPlaneMatrixCanvas
import com.example.ui.components.InteractiveSpectrogramCanvas
import com.example.ui.components.VerdictBadge
import com.example.ui.components.formatDuration
import com.example.ui.components.formatFileSize
import com.example.ui.theme.LocalTrueBitColors
import com.example.ui.viewmodel.TrueBitUiState
import java.util.Locale

@Composable
fun SingleFileInspectorScreen(
    uiState: TrueBitUiState,
    listState: LazyListState,
    onPickFileToInspect: () -> Unit,
    onSelectTrack: (AudioTrack) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTrueBitColors.current
    val track = uiState.inspectedTrack

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier
            .fillMaxSize()
            .testTag("single_file_inspector_list")
    ) {
        // 1. Simple File Selector & Open Button
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Audio Authenticity Inspector",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = onPickFileToInspect,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.cyanAccent,
                        contentColor = Color(0xFF090A0F)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .height(42.dp)
                        .testTag("inspector_pick_file_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.FolderOpen,
                        contentDescription = "Open Audio File",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Open File",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Horizontal Track Pills
        if (uiState.activeTracks.isNotEmpty()) {
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    uiState.activeTracks.forEach { candidate ->
                        val isCurrent = candidate.fileUri == track?.fileUri
                        Surface(
                            color = if (isCurrent) colors.cyanAccent.copy(alpha = 0.18f) else colors.cardElevated,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isCurrent) colors.cyanAccent else colors.glassBorder
                            ),
                            modifier = Modifier
                                .clickable { onSelectTrack(candidate) }
                                .testTag("inspector_switch_${candidate.fileName}")
                        ) {
                            Text(
                                text = "${candidate.title} (${candidate.containerBitDepth}-bit ${candidate.codec.label})",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isCurrent) colors.cyanAccent else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        if (track != null) {
            // 2. Clean Verdict Summary Card
            item {
                Surface(
                    color = colors.cardElevated,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, colors.glassBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${track.artist} • ${track.album}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textSecondary
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            VerdictBadge(
                                verdict = track.verdict,
                                authenticityScore = track.authenticityScore
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = colors.glassBorder)
                        Spacer(modifier = Modifier.height(12.dp))

                        val summaryMessage = when {
                            track.verdict == AuthenticityVerdict.FAKE_TRANSCODE ->
                                "${track.transcodeProfile}: High frequencies cut off sharply at ${String.format(Locale.US, "%.1f kHz", track.spectralCutoffHz / 1000f)}."
                            track.isZeroPadded ->
                                "Header claims ${track.containerBitDepth}-bit, but actual audio is ${track.effectiveBitDepth}-bit padded with empty zeroes."
                            track.verdict == AuthenticityVerdict.UPSAMPLED ->
                                "Upsampled container (${track.sampleRateSummary}) with no real ultrasonic energy above 22 kHz."
                            else ->
                                "Genuine ${track.effectiveBitDepth}-bit lossless audio with full frequency response up to ${String.format(Locale.US, "%.1f kHz", track.spectralCutoffHz / 1000f)}."
                        }

                        Text(
                            text = summaryMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // 3. Key Audio Specs (Simple 2x2 Grid)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SimpleSpecTile(
                        label = "Bit Depth",
                        value = "${track.effectiveBitDepth}-bit Active",
                        subValue = "Header: ${track.containerBitDepth}-bit",
                        modifier = Modifier.weight(1f)
                    )
                    SimpleSpecTile(
                        label = "Frequency Cutoff",
                        value = String.format(Locale.US, "%.1f kHz", track.spectralCutoffHz / 1000f),
                        subValue = "Sample Rate: ${track.sampleRateHz / 1000} kHz",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SimpleSpecTile(
                        label = "Dynamic Range",
                        value = String.format(Locale.US, "DR %.1f dB", track.dynamicRangeDb),
                        subValue = "${track.codec.label} • ${track.bitrateKbps} kbps",
                        modifier = Modifier.weight(1f)
                    )
                    SimpleSpecTile(
                        label = "File Size",
                        value = formatFileSize(track.fileSize),
                        subValue = "Length: ${formatDuration(track.durationMs)}",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 4. Frequency Spectrum Card
            item {
                Surface(
                    color = colors.cardElevated,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, colors.glassBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Frequency Spectrum (FFT)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        InteractiveSpectrogramCanvas(
                            spectrumBinsDb = track.spectrumBinsDb,
                            sampleRateHz = track.sampleRateHz,
                            spectralCutoffHz = track.spectralCutoffHz,
                            verdict = track.verdict,
                            isCompact = false
                        )
                    }
                }
            }

            // 5. Bit-Depth Activity Card
            item {
                Surface(
                    color = colors.cardElevated,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, colors.glassBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Bit-Depth Verification",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        BitPlaneMatrixCanvas(
                            bitPlaneActivity = track.bitPlaneActivity,
                            containerBitDepth = track.containerBitDepth,
                            effectiveBitDepth = track.effectiveBitDepth,
                            isZeroPadded = track.isZeroPadded
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SimpleSpecTile(
    label: String,
    value: String,
    subValue: String,
    modifier: Modifier = Modifier
) {
    val colors = LocalTrueBitColors.current
    Surface(
        color = colors.cardElevated,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, colors.glassBorder),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subValue,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
