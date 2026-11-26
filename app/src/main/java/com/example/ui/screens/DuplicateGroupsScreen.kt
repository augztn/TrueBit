package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.AudioTrack
import com.example.domain.model.DuplicateFilter
import com.example.domain.model.DuplicateGroup
import com.example.domain.model.RankedTrackItem
import com.example.ui.components.InteractiveSpectrogramCanvas
import com.example.ui.components.MasterLockBadge
import com.example.ui.components.VerdictBadge
import com.example.ui.components.formatFileSize
import com.example.ui.theme.LocalTrueBitColors
import com.example.ui.viewmodel.TrueBitUiState

@Composable
fun DuplicateGroupsScreen(
    uiState: TrueBitUiState,
    listState: LazyListState,
    onSelectFilter: (DuplicateFilter) -> Unit,
    onToggleTrackSelection: (String, Boolean) -> Unit,
    onInspectTrack: (AudioTrack) -> Unit,
    onQuarantineSelectedToTrash: () -> Unit,
    onPermanentDeleteSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTrueBitColors.current
    var showPermanentDeleteConfirm by remember { mutableStateOf(false) }
    var showQuarantineConfirm by remember { mutableStateOf(false) }

    val selectedCount = uiState.metrics.selectedDuplicateCount
    val reclaimableText = formatFileSize(uiState.metrics.reclaimableBytes)

    if (showQuarantineConfirm) {
        AlertDialog(
            onDismissRequest = { showQuarantineConfirm = false },
            containerColor = colors.cardElevated,
            title = { Text("Move $selectedCount File(s) to Trash?") },
            text = {
                Text(
                    text = "Moves $selectedCount lower-quality duplicate(s) ($reclaimableText) to Trash while keeping your highest-quality Master copies safe.",
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showQuarantineConfirm = false
                        onQuarantineSelectedToTrash()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.cyanAccent,
                        contentColor = Color(0xFF090A0F)
                    ),
                    modifier = Modifier.testTag("confirm_quarantine_button")
                ) {
                    Text("Move to Trash", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuarantineConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showPermanentDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showPermanentDeleteConfirm = false },
            containerColor = colors.cardElevated,
            title = {
                Text(
                    text = "Delete $selectedCount Duplicate(s) Forever?",
                    color = colors.crimsonVerdict
                )
            },
            text = {
                Text(
                    text = "Permanently deletes $selectedCount lower-quality file(s) ($reclaimableText). Master copies will not be touched.",
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermanentDeleteConfirm = false
                        onPermanentDeleteSelected()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.crimsonVerdict,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("confirm_permanent_delete_button")
                ) {
                    Text("Delete Forever", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermanentDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("duplicate_groups_list")
        ) {
            // Simple Filter Chips
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    DuplicateFilter.entries.forEach { filter ->
                        val selected = uiState.activeFilter == filter
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectFilter(filter) },
                            label = { Text(filter.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.cyanAccent.copy(alpha = 0.18f),
                                selectedLabelColor = colors.cyanAccent
                            ),
                            modifier = Modifier.testTag("filter_chip_${filter.name.lowercase()}")
                        )
                    }
                }
            }

            if (uiState.filteredDuplicateGroups.isEmpty()) {
                item {
                    Surface(
                        color = colors.cardElevated,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, colors.glassBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = "No Duplicates",
                                tint = colors.emeraldVerdict,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No Duplicates Found",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Your library only has unique master tracks in this category.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                        }
                    }
                }
            } else {
                items(uiState.filteredDuplicateGroups, key = { it.groupId }) { group ->
                    SimpleDuplicateGroupCard(
                        group = group,
                        onToggleTrackSelection = onToggleTrackSelection,
                        onInspectTrack = onInspectTrack
                    )
                }
            }
        }

        // Clean Bottom Action Bar
        if (selectedCount > 0) {
            Surface(
                color = colors.cardElevated,
                border = BorderStroke(1.dp, colors.glassBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$selectedCount duplicate(s) selected",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary
                        )
                        Text(
                            text = "Save $reclaimableText",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.emeraldVerdict,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { showQuarantineConfirm = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.cyanAccent,
                                contentColor = Color(0xFF090A0F)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1.2f)
                                .height(48.dp)
                                .testTag("quarantine_to_trash_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteSweep,
                                contentDescription = "Move to Trash",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Move to Trash",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = { showPermanentDeleteConfirm = true },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, colors.crimsonVerdict.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .weight(0.8f)
                                .height(48.dp)
                                .testTag("permanent_delete_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteForever,
                                contentDescription = "Delete",
                                tint = colors.crimsonVerdict,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Delete",
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.crimsonVerdict,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SimpleDuplicateGroupCard(
    group: DuplicateGroup,
    onToggleTrackSelection: (String, Boolean) -> Unit,
    onInspectTrack: (AudioTrack) -> Unit
) {
    val colors = LocalTrueBitColors.current
    var showSpectrum by remember { mutableStateOf(false) }

    Surface(
        color = colors.cardElevated,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, colors.glassBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("duplicate_group_${group.canonicalTitle}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.canonicalTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${group.canonicalArtist} • ${group.matchType.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }

                TextButton(onClick = { showSpectrum = !showSpectrum }) {
                    Icon(
                        imageVector = Icons.Filled.GraphicEq,
                        contentDescription = null,
                        tint = colors.cyanAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (showSpectrum) "Hide Graph" else "Spectrum",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.cyanAccent
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Master Item (Always kept)
            SimpleRankedTrackRow(
                item = group.masterItem,
                showSpectrum = showSpectrum,
                onToggleSelection = {},
                onInspect = { onInspectTrack(group.masterItem.track) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Redundant Items
            group.redundantItems.forEach { redundant ->
                SimpleRankedTrackRow(
                    item = redundant,
                    showSpectrum = showSpectrum,
                    onToggleSelection = {
                        onToggleTrackSelection(redundant.track.fileUri, redundant.selectedForDeletion)
                    },
                    onInspect = { onInspectTrack(redundant.track) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SimpleRankedTrackRow(
    item: RankedTrackItem,
    showSpectrum: Boolean,
    onToggleSelection: () -> Unit,
    onInspect: () -> Unit
) {
    val colors = LocalTrueBitColors.current
    val track = item.track
    val borderColor = when {
        item.isMaster -> colors.emeraldVerdict.copy(alpha = 0.5f)
        item.selectedForDeletion -> colors.crimsonVerdict.copy(alpha = 0.45f)
        else -> colors.glassBorder
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.cardHigh)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onInspect)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (item.isMaster) {
                MasterLockBadge()
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(onClick = onToggleSelection)
                ) {
                    Checkbox(
                        checked = item.selectedForDeletion,
                        onCheckedChange = { onToggleSelection() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = colors.crimsonVerdict,
                            checkmarkColor = Color.White
                        ),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (item.selectedForDeletion) "Marked to Clean" else "Keep Copy",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (item.selectedForDeletion) colors.crimsonVerdict else colors.textSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            VerdictBadge(verdict = track.verdict)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = track.comparisonPillText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(2.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = item.downgradeReason,
                style = MaterialTheme.typography.bodySmall,
                color = if (item.isMaster) colors.emeraldVerdict else colors.amberVerdict,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = formatFileSize(track.fileSize),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )
        }

        AnimatedVisibility(visible = showSpectrum) {
            Column {
                Spacer(modifier = Modifier.height(10.dp))
                InteractiveSpectrogramCanvas(
                    spectrumBinsDb = track.spectrumBinsDb,
                    sampleRateHz = track.sampleRateHz,
                    spectralCutoffHz = track.spectralCutoffHz,
                    verdict = track.verdict,
                    isCompact = true
                )
            }
        }
    }
}
