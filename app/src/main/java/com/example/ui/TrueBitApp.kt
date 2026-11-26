package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.screens.AlbumMovieOrganizerScreen
import com.example.ui.screens.DuplicateGroupsScreen
import com.example.ui.screens.ScannerDashboardScreen
import com.example.ui.screens.SingleFileInspectorScreen
import com.example.ui.screens.TrashAuditScreen
import com.example.ui.theme.LocalTrueBitColors
import com.example.ui.theme.TrueBitTheme
import com.example.ui.viewmodel.TrueBitTab
import com.example.ui.viewmodel.TrueBitUiState
import com.example.ui.viewmodel.TrueBitViewModel

@Composable
fun TrueBitApp(viewModel: TrueBitViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    if (uiState.currentTab != TrueBitTab.SCANNER) {
        BackHandler {
            viewModel.selectTab(TrueBitTab.SCANNER)
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            viewModel.onSafFolderPicked(treeUri)
        }
    }

    val singleAudioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { docUri ->
        if (docUri != null) {
            viewModel.onSingleFilePickedForInspection(docUri)
        }
    }

    val scannerListState = rememberLazyListState()
    val organizerListState = rememberLazyListState()
    val duplicatesListState = rememberLazyListState()
    val inspectorListState = rememberLazyListState()
    val trashListState = rememberLazyListState()

    TrueBitTheme(isObsidianDark = uiState.isObsidianDarkTheme) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isExpandedScreen = maxWidth >= 640.dp

            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    SimpleTopBar(
                        uiState = uiState,
                        onToggleTheme = { viewModel.toggleTheme() }
                    )
                },
                bottomBar = {
                    if (!isExpandedScreen) {
                        TrueBitBottomNavBar(
                            currentTab = uiState.currentTab,
                            unorganizedCount = uiState.metrics.unorganizedTrackCount,
                            duplicateGroupCount = uiState.metrics.duplicateGroupCount,
                            quarantinedCount = uiState.trashEntries.count { !it.isPermanentlyDeleted },
                            onSelectTab = { viewModel.selectTab(it) }
                        )
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (isExpandedScreen) {
                        TrueBitNavigationRail(
                            currentTab = uiState.currentTab,
                            unorganizedCount = uiState.metrics.unorganizedTrackCount,
                            duplicateGroupCount = uiState.metrics.duplicateGroupCount,
                            quarantinedCount = uiState.trashEntries.count { !it.isPermanentlyDeleted },
                            onSelectTab = { viewModel.selectTab(it) }
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        AnimatedVisibility(visible = uiState.statusBannerMessage != null) {
                            uiState.statusBannerMessage?.let { message ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = { viewModel.dismissStatusBanner() },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Close,
                                                contentDescription = "Dismiss",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        AnimatedContent(
                            targetState = uiState.currentTab,
                            transitionSpec = {
                                fadeIn(animationSpec = spring()) togetherWith fadeOut(animationSpec = spring())
                            },
                            label = "truebit_tab_transition",
                            modifier = Modifier.fillMaxSize()
                        ) { targetTab ->
                            when (targetTab) {
                                TrueBitTab.SCANNER -> ScannerDashboardScreen(
                                    uiState = uiState,
                                    listState = scannerListState,
                                    onPickSafFolder = { folderPickerLauncher.launch(null) },
                                    onPickSingleAudioFile = {
                                        singleAudioPickerLauncher.launch(arrayOf("audio/*", "*/*"))
                                    },
                                    onRunScan = { forceFull -> viewModel.runScan(forceFull) },
                                    onToggleDeepSpectral = { viewModel.setDeepSpectralScan(it) },
                                    onNavigateToTab = { viewModel.selectTab(it) },
                                    onInspectTrack = { viewModel.inspectTrack(it) }
                                )

                                TrueBitTab.ORGANIZER -> AlbumMovieOrganizerScreen(
                                    uiState = uiState,
                                    listState = organizerListState,
                                    onSelectCategoryFilter = { viewModel.setCategoryFilter(it) },
                                    onOrganizeBucket = { bucket, customName ->
                                        viewModel.organizeBucketIntoFolder(bucket, customName)
                                    },
                                    onOrganizeAllBuckets = { viewModel.organizeAllBucketsIntoFolders() },
                                    onCreateCustomFolderAndMove = { folderName, uris ->
                                        viewModel.createCustomFolderAndMoveTracks(folderName, uris)
                                    },
                                    onInspectTrack = { viewModel.inspectTrack(it) }
                                )

                                TrueBitTab.DUPLICATES -> DuplicateGroupsScreen(
                                    uiState = uiState,
                                    listState = duplicatesListState,
                                    onSelectFilter = { viewModel.setDuplicateFilter(it) },
                                    onToggleTrackSelection = { uri, selected ->
                                        viewModel.toggleTrackDeletionSelection(uri, selected)
                                    },
                                    onInspectTrack = { viewModel.inspectTrack(it) },
                                    onQuarantineSelectedToTrash = {
                                        viewModel.quarantineSelectedDuplicatesToTrash()
                                    },
                                    onPermanentDeleteSelected = {
                                        viewModel.permanentlyDeleteSelectedDuplicates()
                                    }
                                )

                                TrueBitTab.INSPECTOR -> SingleFileInspectorScreen(
                                    uiState = uiState,
                                    listState = inspectorListState,
                                    onPickFileToInspect = {
                                        singleAudioPickerLauncher.launch(arrayOf("audio/*", "*/*"))
                                    },
                                    onSelectTrack = { viewModel.inspectTrack(it) }
                                )

                                TrueBitTab.TRASH -> TrashAuditScreen(
                                    uiState = uiState,
                                    listState = trashListState,
                                    onRestoreEntry = { viewModel.restoreTrashEntry(it) },
                                    onPurgeEntryPermanently = { viewModel.purgeTrashEntryPermanently(it) },
                                    onEmptyAllTrash = { viewModel.emptyAllTrash() },
                                    onClearDeletedHistory = { viewModel.clearDeletedHistoryLog() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SimpleTopBar(
    uiState: TrueBitUiState,
    onToggleTheme: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "TrueBit",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            }

            IconButton(
                onClick = onToggleTheme,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("theme_toggle_button")
            ) {
                Icon(
                    imageVector = if (uiState.isObsidianDarkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                    contentDescription = "Toggle Theme",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun TrueBitBottomNavBar(
    currentTab: TrueBitTab,
    unorganizedCount: Int,
    duplicateGroupCount: Int,
    quarantinedCount: Int,
    onSelectTab: (TrueBitTab) -> Unit
) {
    val colors = LocalTrueBitColors.current

    Column {
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp
        ) {
            TrueBitTab.entries.forEach { tab ->
                val selected = currentTab == tab
                val badgeCount = when (tab) {
                    TrueBitTab.ORGANIZER -> unorganizedCount
                    TrueBitTab.DUPLICATES -> duplicateGroupCount
                    TrueBitTab.TRASH -> quarantinedCount
                    else -> 0
                }
                NavigationBarItem(
                    selected = selected,
                    onClick = { onSelectTab(tab) },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (badgeCount > 0) {
                                    Badge(
                                        containerColor = if (tab == TrueBitTab.TRASH) colors.amberVerdict else MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ) {
                                        Text("$badgeCount")
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = tab.iconVector(),
                                contentDescription = tab.label
                            )
                        }
                    },
                    label = {
                        Text(
                            text = tab.shortNavLabel(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.testTag("nav_tab_${tab.route}")
                )
            }
        }
    }
}

@Composable
private fun TrueBitNavigationRail(
    currentTab: TrueBitTab,
    unorganizedCount: Int,
    duplicateGroupCount: Int,
    quarantinedCount: Int,
    onSelectTab: (TrueBitTab) -> Unit
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        TrueBitTab.entries.forEach { tab ->
            val selected = currentTab == tab
            val badgeCount = when (tab) {
                TrueBitTab.ORGANIZER -> unorganizedCount
                TrueBitTab.DUPLICATES -> duplicateGroupCount
                TrueBitTab.TRASH -> quarantinedCount
                else -> 0
            }
            NavigationRailItem(
                selected = selected,
                onClick = { onSelectTab(tab) },
                icon = {
                    BadgedBox(
                        badge = {
                            if (badgeCount > 0) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ) {
                                    Text("$badgeCount")
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = tab.iconVector(),
                            contentDescription = tab.label
                        )
                    }
                },
                label = {
                    Text(
                        text = tab.shortNavLabel(),
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.testTag("rail_tab_${tab.route}")
            )
        }
    }
}

private fun TrueBitTab.iconVector(): ImageVector {
    return when (this) {
        TrueBitTab.SCANNER -> Icons.Filled.GraphicEq
        TrueBitTab.ORGANIZER -> Icons.Filled.DriveFileMove
        TrueBitTab.DUPLICATES -> Icons.Filled.LibraryMusic
        TrueBitTab.INSPECTOR -> Icons.Filled.Analytics
        TrueBitTab.TRASH -> Icons.Filled.DeleteSweep
    }
}

private fun TrueBitTab.shortNavLabel(): String {
    return when (this) {
        TrueBitTab.SCANNER -> "Scanner"
        TrueBitTab.ORGANIZER -> "Albums"
        TrueBitTab.DUPLICATES -> "Duplicates"
        TrueBitTab.INSPECTOR -> "Inspector"
        TrueBitTab.TRASH -> "Trash"
    }
}
