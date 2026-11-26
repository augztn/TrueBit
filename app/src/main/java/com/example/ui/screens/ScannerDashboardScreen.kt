package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.AudioTrack
import com.example.domain.model.AuthenticityVerdict
import com.example.ui.components.VerdictBadge
import com.example.ui.components.formatFileSize
import com.example.ui.theme.LocalTrueBitColors
import com.example.ui.viewmodel.TrueBitTab
import com.example.ui.viewmodel.TrueBitUiState
import java.util.Locale

enum class MetadataSortColumn(val label: String) {
    TITLE("Track"),
    CODEC("Codec"),
    SAMPLE_RATE("Sample Rate"),
    BIT_DEPTH("Bit Depth")
}

@Composable
fun ScannerDashboardScreen(
    uiState: TrueBitUiState,
    listState: LazyListState,
    onPickSafFolder: () -> Unit,
    onPickSingleAudioFile: () -> Unit,
    onRunScan: (Boolean) -> Unit,
    onToggleDeepSpectral: (Boolean) -> Unit,
    onNavigateToTab: (TrueBitTab) -> Unit,
    onInspectTrack: (AudioTrack) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTrueBitColors.current
    val progress = uiState.scanProgress
    val metrics = uiState.metrics

    var showTableMode by remember { mutableStateOf(true) }
    var sortColumn by remember { mutableStateOf(MetadataSortColumn.BIT_DEPTH) }
    var sortAscending by remember { mutableStateOf(false) }

    val sortedTracks by remember(uiState.activeTracks, sortColumn, sortAscending) {
        derivedStateOf {
            val base = when (sortColumn) {
                MetadataSortColumn.TITLE -> uiState.activeTracks.sortedBy { it.title.lowercase(Locale.ROOT) }
                MetadataSortColumn.CODEC -> uiState.activeTracks.sortedWith(
                    compareBy<AudioTrack> { it.codec.label }.thenByDescending { it.effectiveBitDepth }
                )
                MetadataSortColumn.SAMPLE_RATE -> uiState.activeTracks.sortedWith(
                    compareBy<AudioTrack> { it.sampleRateHz }.thenBy { it.effectiveBitDepth }
                )
                MetadataSortColumn.BIT_DEPTH -> uiState.activeTracks.sortedWith(
                    compareBy<AudioTrack> { it.effectiveBitDepth }
                        .thenBy { it.containerBitDepth }
                        .thenBy { it.sampleRateHz }
                )
            }
            if (sortAscending) base else base.reversed()
        }
    }

    val onHeaderClick: (MetadataSortColumn) -> Unit = { clickedColumn ->
        if (sortColumn == clickedColumn) {
            sortAscending = !sortAscending
        } else {
            sortColumn = clickedColumn
            sortAscending = clickedColumn == MetadataSortColumn.TITLE || clickedColumn == MetadataSortColumn.CODEC
        }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier
            .fillMaxSize()
            .testTag("scanner_dashboard_list")
    ) {
        // 1. Clean Scanner Card
        item {
            Surface(
                color = colors.cardElevated,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, colors.glassBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Lossless Audio Scanner",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Scan FLAC, ALAC, WAV, AIFF, APE & WavPack files to find duplicates and fake transcodes.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onRunScan(true) },
                            enabled = !progress.isScanning,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.cyanAccent,
                                contentColor = Color(0xFF090A0F)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("run_spectral_audit_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.GraphicEq,
                                contentDescription = "Scan Audio",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (progress.isScanning) "Scanning..." else "Scan Now",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = onPickSafFolder,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, colors.glassBorder),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("pick_saf_folder_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CreateNewFolder,
                                contentDescription = "Select Folder",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Select Folder",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = colors.glassBorder)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Deep Spectral & Bit-Depth Check",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Switch(
                            checked = uiState.isDeepSpectralScan,
                            onCheckedChange = onToggleDeepSpectral,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF090A0F),
                                checkedTrackColor = colors.cyanAccent
                            ),
                            modifier = Modifier.testTag("deep_spectral_scan_toggle")
                        )
                    }

                    // Progress indicator only while scanning
                    AnimatedVisibility(visible = progress.isScanning) {
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            val fraction = if (progress.totalCount > 0) {
                                (progress.processedCount.toFloat() / progress.totalCount.toFloat()).coerceIn(0f, 1f)
                            } else 0.5f
                            LinearProgressIndicator(
                                progress = { fraction },
                                color = colors.cyanAccent,
                                trackColor = colors.glassBorder,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Checking ${progress.currentFileName} (${progress.processedCount}/${progress.totalCount})",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // 2. Simple 4-Stat Overview Grid
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SimpleStatBox(
                    label = "Total Files",
                    value = "${metrics.totalTracks}",
                    accentColor = MaterialTheme.colorScheme.onSurface,
                    onClick = { onNavigateToTab(TrueBitTab.INSPECTOR) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("metric_total_tracks")
                )
                SimpleStatBox(
                    label = "True Lossless",
                    value = "${metrics.trueLosslessCount}",
                    accentColor = colors.emeraldVerdict,
                    onClick = { onNavigateToTab(TrueBitTab.INSPECTOR) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("metric_true_lossless")
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SimpleStatBox(
                    label = "Fake / Padded",
                    value = "${metrics.fakeOrUpsampledCount}",
                    accentColor = colors.crimsonVerdict,
                    onClick = { onNavigateToTab(TrueBitTab.DUPLICATES) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("metric_fake_lossless")
                )
                SimpleStatBox(
                    label = "Reclaimable",
                    value = formatFileSize(metrics.reclaimableBytes),
                    accentColor = colors.amberVerdict,
                    onClick = { onNavigateToTab(TrueBitTab.DUPLICATES) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("metric_reclaimable")
                )
            }
        }

        // 3. Clean Duplicates Call-to-Action Card
        if (uiState.allDuplicateGroups.isNotEmpty()) {
            item {
                Surface(
                    color = colors.cardElevated,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, colors.cyanAccent.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToTab(TrueBitTab.DUPLICATES) }
                        .testTag("open_duplicates_review_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${uiState.allDuplicateGroups.size} Duplicate Songs Found",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Review and clean lower-quality copies (${formatFileSize(metrics.reclaimableBytes)})",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Open Duplicates",
                            tint = colors.cyanAccent
                        )
                    }
                }
            }
        }

        // 3B. Albums Folder Organizer Quick Card
        if (uiState.activeTracks.isNotEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToTab(TrueBitTab.ORGANIZER) }
                        .testTag("open_organizer_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Albums (${uiState.metrics.albumBucketCount + uiState.metrics.movieBucketCount})",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Create Album folders and move ${uiState.metrics.unorganizedTrackCount} song(s) automatically",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Open Albums Organizer",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // 4. Metadata Summary Header + Table/Card View Switch + Single File Picker link
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Metadata Summary (${uiState.activeTracks.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    TextButton(
                        onClick = onPickSingleAudioFile,
                        modifier = Modifier.testTag("inspect_single_file_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AudioFile,
                            contentDescription = null,
                            tint = colors.cyanAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Check Single File",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.cyanAccent
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = showTableMode,
                        onClick = { showTableMode = true },
                        label = { Text("Summary Table") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.TableChart,
                                contentDescription = "Table View",
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("view_mode_table_chip")
                    )

                    FilterChip(
                        selected = !showTableMode,
                        onClick = { showTableMode = false },
                        label = { Text("Cards") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.ViewAgenda,
                                contentDescription = "Card View",
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("view_mode_cards_chip")
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Text(
                        text = "Sorted by ${sortColumn.label} ${if (sortAscending) "↑" else "↓"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 5. Sortable Metadata Summary Table OR Card List
        if (showTableMode) {
            item {
                MetadataSummaryTable(
                    tracks = sortedTracks,
                    sortColumn = sortColumn,
                    sortAscending = sortAscending,
                    onSortByColumn = onHeaderClick,
                    onInspectTrack = onInspectTrack
                )
            }
        } else {
            items(sortedTracks, key = { it.fileUri }) { track ->
                SimpleTrackCard(
                    track = track,
                    onClick = { onInspectTrack(track) }
                )
            }
        }
    }
}

@Composable
fun MetadataSummaryTable(
    tracks: List<AudioTrack>,
    sortColumn: MetadataSortColumn,
    sortAscending: Boolean,
    onSortByColumn: (MetadataSortColumn) -> Unit,
    onInspectTrack: (AudioTrack) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTrueBitColors.current

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
            .fillMaxWidth()
            .testTag("metadata_summary_table")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Sortable Table Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SortableColumnHeader(
                    title = "Track",
                    column = MetadataSortColumn.TITLE,
                    activeColumn = sortColumn,
                    sortAscending = sortAscending,
                    onClick = { onSortByColumn(MetadataSortColumn.TITLE) },
                    textAlign = TextAlign.Start,
                    modifier = Modifier
                        .weight(1.9f)
                        .testTag("sort_header_title")
                )
                SortableColumnHeader(
                    title = "Codec",
                    column = MetadataSortColumn.CODEC,
                    activeColumn = sortColumn,
                    sortAscending = sortAscending,
                    onClick = { onSortByColumn(MetadataSortColumn.CODEC) },
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(0.9f)
                        .testTag("sort_header_codec")
                )
                SortableColumnHeader(
                    title = "Rate",
                    column = MetadataSortColumn.SAMPLE_RATE,
                    activeColumn = sortColumn,
                    sortAscending = sortAscending,
                    onClick = { onSortByColumn(MetadataSortColumn.SAMPLE_RATE) },
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .weight(1.1f)
                        .testTag("sort_header_sample_rate")
                )
                SortableColumnHeader(
                    title = "Bit Depth",
                    column = MetadataSortColumn.BIT_DEPTH,
                    activeColumn = sortColumn,
                    sortAscending = sortAscending,
                    onClick = { onSortByColumn(MetadataSortColumn.BIT_DEPTH) },
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .weight(1.2f)
                        .testTag("sort_header_bit_depth")
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            // Data Rows
            tracks.forEachIndexed { index, track ->
                val rowBackground = if (index % 2 == 0) {
                    MaterialTheme.colorScheme.surfaceContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                }

                val bitDepthColor = when {
                    track.verdict == AuthenticityVerdict.FAKE_TRANSCODE -> colors.crimsonVerdict
                    track.isZeroPadded || track.verdict == AuthenticityVerdict.UPSAMPLED -> colors.amberVerdict
                    track.effectiveBitDepth >= 24 -> colors.emeraldVerdict
                    else -> MaterialTheme.colorScheme.onSurface
                }

                val bitDepthText = if (track.isZeroPadded && track.containerBitDepth != track.effectiveBitDepth) {
                    "${track.effectiveBitDepth}→${track.containerBitDepth}b"
                } else {
                    "${track.effectiveBitDepth}-bit"
                }

                val sampleRateText = if (track.sampleRateHz % 1000 == 0) {
                    "${track.sampleRateHz / 1000} kHz"
                } else {
                    String.format(Locale.US, "%.1f kHz", track.sampleRateHz / 1000.0)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(rowBackground)
                        .clickable { onInspectTrack(track) }
                        .padding(horizontal = 12.dp, vertical = 11.dp)
                        .testTag("table_row_${track.fileName}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Column 1: Track Title & Artist
                    Column(
                        modifier = Modifier
                            .weight(1.9f)
                            .padding(end = 6.dp)
                    ) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Column 2: Codec Pill
                    Row(
                        modifier = Modifier.weight(0.9f),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Text(
                                text = track.codec.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // Column 3: Sample Rate
                    Text(
                        text = sampleRateText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        modifier = Modifier.weight(1.1f)
                    )

                    // Column 4: Bit Depth (Effective / Container indicator)
                    Column(
                        modifier = Modifier.weight(1.2f),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = bitDepthText,
                            style = MaterialTheme.typography.labelMedium,
                            color = bitDepthColor,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End,
                            maxLines = 1
                        )
                        Text(
                            text = when (track.verdict) {
                                AuthenticityVerdict.TRUE_LOSSLESS -> "True"
                                AuthenticityVerdict.UPSAMPLED -> if (track.isZeroPadded) "Padded" else "Upsampled"
                                AuthenticityVerdict.FAKE_TRANSCODE -> "Fake"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = bitDepthColor.copy(alpha = 0.85f),
                            textAlign = TextAlign.End,
                            maxLines = 1
                        )
                    }
                }

                if (index < tracks.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
private fun SortableColumnHeader(
    title: String,
    column: MetadataSortColumn,
    activeColumn: MetadataSortColumn,
    sortAscending: Boolean,
    onClick: () -> Unit,
    textAlign: TextAlign,
    modifier: Modifier = Modifier
) {
    val isSelected = column == activeColumn
    val textColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val horizontalArrangement = when (textAlign) {
        TextAlign.Start -> Arrangement.Start
        TextAlign.Center -> Arrangement.Center
        else -> Arrangement.End
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 2.dp),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1
        )
        if (isSelected) {
            Spacer(modifier = Modifier.width(2.dp))
            Icon(
                imageVector = if (sortAscending) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                contentDescription = if (sortAscending) "Sorted Ascending" else "Sorted Descending",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

@Composable
private fun SimpleStatBox(
    label: String,
    value: String,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTrueBitColors.current
    Surface(
        color = colors.cardElevated,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, colors.glassBorder),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = accentColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SimpleTrackCard(
    track: AudioTrack,
    onClick: () -> Unit
) {
    val colors = LocalTrueBitColors.current
    Surface(
        color = colors.cardElevated,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, colors.glassBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("catalog_track_${track.fileName}")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${track.artist} • ${track.codec.label} • ${track.effectiveBitDepth}-bit / ${track.sampleRateHz / 1000}kHz",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            VerdictBadge(verdict = track.verdict)
        }
    }
}
