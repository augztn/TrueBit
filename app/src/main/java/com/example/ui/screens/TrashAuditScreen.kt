package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.TrashEntry
import com.example.ui.components.formatFileSize
import com.example.ui.theme.LocalTrueBitColors
import com.example.ui.viewmodel.TrueBitUiState

@Composable
fun TrashAuditScreen(
    uiState: TrueBitUiState,
    listState: LazyListState,
    onRestoreEntry: (TrashEntry) -> Unit,
    onPurgeEntryPermanently: (TrashEntry) -> Unit,
    onEmptyAllTrash: () -> Unit,
    onClearDeletedHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTrueBitColors.current
    val quarantinedEntries = uiState.trashEntries.filter { !it.isPermanentlyDeleted }
    val deletedAuditHistory = uiState.trashEntries.filter { it.isPermanentlyDeleted }
    val quarantinedBytes = quarantinedEntries.sumOf { it.fileSize }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxSize()
            .testTag("trash_audit_list")
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Trash & History",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${quarantinedEntries.size} file(s) in Trash (${formatFileSize(quarantinedBytes)})",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }

                if (quarantinedEntries.isNotEmpty()) {
                    Button(
                        onClick = onEmptyAllTrash,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.crimsonVerdict,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .height(42.dp)
                            .testTag("empty_all_trash_button")
                    ) {
                        Text("Empty Trash", fontWeight = FontWeight.Bold)
                    }
                } else if (deletedAuditHistory.isNotEmpty()) {
                    TextButton(
                        onClick = onClearDeletedHistory,
                        modifier = Modifier.testTag("clear_audit_history_button")
                    ) {
                        Text("Clear History", color = colors.textSecondary)
                    }
                }
            }
        }

        if (uiState.trashEntries.isEmpty()) {
            item {
                Surface(
                    color = colors.cardElevated,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, colors.glassBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VerifiedUser,
                            contentDescription = "Trash Empty",
                            tint = colors.emeraldVerdict,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Trash is Empty",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Moved duplicate files can be restored or deleted from here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary
                        )
                    }
                }
            }
        } else {
            items(uiState.trashEntries, key = { it.trashId }) { entry ->
                SimpleTrashCard(
                    entry = entry,
                    onRestore = { onRestoreEntry(entry) },
                    onPurge = { onPurgeEntryPermanently(entry) }
                )
            }
        }
    }
}

@Composable
private fun SimpleTrashCard(
    entry: TrashEntry,
    onRestore: () -> Unit,
    onPurge: () -> Unit
) {
    val colors = LocalTrueBitColors.current

    Surface(
        color = colors.cardElevated,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, colors.glassBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("trash_entry_${entry.fileName}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatFileSize(entry.fileSize),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
            }

            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${entry.comparisonSummary} • ${entry.downgradeReason}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )

            if (!entry.isPermanentlyDeleted) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onRestore,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, colors.glassBorder),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .testTag("restore_btn_${entry.fileName}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.RestoreFromTrash,
                            contentDescription = "Restore",
                            tint = colors.emeraldVerdict,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Restore", color = colors.emeraldVerdict)
                    }

                    OutlinedButton(
                        onClick = onPurge,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, colors.glassBorder),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .testTag("purge_btn_${entry.fileName}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteForever,
                            contentDescription = "Delete",
                            tint = colors.crimsonVerdict,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete", color = colors.crimsonVerdict)
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Permanently Deleted",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.crimsonVerdict
                )
            }
        }
    }
}
