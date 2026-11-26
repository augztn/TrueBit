package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.preferences.ThemePreferencesRepository
import com.example.data.repository.AudioScannerRepository
import com.example.domain.model.AudioTrack
import com.example.domain.model.AuthenticityVerdict
import com.example.domain.model.DuplicateFilter
import com.example.domain.model.DuplicateGroup
import com.example.domain.model.DuplicateMatchType
import com.example.domain.model.MediaCategoryBucket
import com.example.domain.model.MediaCategoryType
import com.example.domain.model.ScanProgressState
import com.example.domain.model.TrashEntry
import com.example.domain.usecase.DuplicateRankerUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TrueBitTab(val route: String, val label: String) {
    SCANNER("scanner_dashboard", "Scanner"),
    ORGANIZER("album_movie_organizer", "Albums"),
    DUPLICATES("duplicate_groups", "Duplicates"),
    INSPECTOR("file_inspector", "Inspector"),
    TRASH("trash_audit", "Trash")
}

data class DashboardSummaryMetrics(
    val totalTracks: Int = 0,
    val trueLosslessCount: Int = 0,
    val fakeOrUpsampledCount: Int = 0,
    val duplicateGroupCount: Int = 0,
    val selectedDuplicateCount: Int = 0,
    val reclaimableBytes: Long = 0L,
    val albumBucketCount: Int = 0,
    val movieBucketCount: Int = 0,
    val unorganizedTrackCount: Int = 0
)

data class TrueBitUiState(
    val currentTab: TrueBitTab = TrueBitTab.SCANNER,
    val isObsidianDarkTheme: Boolean = true,
    val isDeepSpectralScan: Boolean = true,
    val savedFolderUris: Set<String> = emptySet(),
    val scanProgress: ScanProgressState = ScanProgressState(),
    val activeTracks: List<AudioTrack> = emptyList(),
    val allDuplicateGroups: List<DuplicateGroup> = emptyList(),
    val filteredDuplicateGroups: List<DuplicateGroup> = emptyList(),
    val activeFilter: DuplicateFilter = DuplicateFilter.ALL,
    val activeCategoryFilter: MediaCategoryType = MediaCategoryType.ALL,
    val categoryBuckets: List<MediaCategoryBucket> = emptyList(),
    val inspectedTrack: AudioTrack? = null,
    val trashEntries: List<TrashEntry> = emptyList(),
    val metrics: DashboardSummaryMetrics = DashboardSummaryMetrics(),
    val statusBannerMessage: String? = null
)

class TrueBitViewModel(
    private val scannerRepository: AudioScannerRepository,
    private val preferencesRepository: ThemePreferencesRepository,
    private val duplicateRanker: DuplicateRankerUseCase
) : ViewModel() {

    private val _currentTab = MutableStateFlow(TrueBitTab.SCANNER)
    private val _activeFilter = MutableStateFlow(DuplicateFilter.ALL)
    private val _activeCategoryFilter = MutableStateFlow(MediaCategoryType.ALL)
    private val _inspectedTrackUri = MutableStateFlow<String?>(null)
    private val _manualSelectionOverrides = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private val _statusBannerMessage = MutableStateFlow<String?>(null)

    private val navAndFilterFlow = combine(
        _currentTab,
        _activeFilter,
        _activeCategoryFilter,
        _inspectedTrackUri,
        _manualSelectionOverrides
    ) { tab, filter, catFilter, inspectedUri, overrides ->
        NavFilterCore(tab, filter, catFilter, inspectedUri, overrides)
    }.combine(_statusBannerMessage) { core, banner ->
        NavFilterBundle(
            tab = core.tab,
            filter = core.filter,
            categoryFilter = core.categoryFilter,
            inspectedUri = core.inspectedUri,
            overrides = core.overrides,
            banner = banner
        )
    }

    val uiState: StateFlow<TrueBitUiState> = combine(
        preferencesRepository.userPreferencesFlow,
        scannerRepository.scanProgress,
        scannerRepository.allTracksFlow,
        scannerRepository.allTrashEntriesFlow,
        navAndFilterFlow
    ) { prefs, progress, allTracks, trashEntries, navBundle ->
        val activeTracks = allTracks.filter { !it.isQuarantined }
        val allGroups = duplicateRanker.buildDuplicateGroups(
            tracks = activeTracks,
            manualSelectionOverrides = navBundle.overrides
        )

        val filteredGroups = when (navBundle.filter) {
            DuplicateFilter.ALL -> allGroups
            DuplicateFilter.BIT_DEPTH -> allGroups.filter { it.hasBitDepthDifference }
            DuplicateFilter.FAKE_LOSSLESS -> allGroups.filter { it.hasFakeLossless }
            DuplicateFilter.EXACT_COPIES -> allGroups.filter { it.matchType == DuplicateMatchType.EXACT_SHA256 }
        }

        // Build Album & Movie Category Buckets
        val allBuckets = buildCategoryBuckets(activeTracks)
        val filteredBuckets = when (navBundle.categoryFilter) {
            MediaCategoryType.ALL -> allBuckets
            MediaCategoryType.ALBUM -> allBuckets.filter { it.categoryType == MediaCategoryType.ALBUM }
            MediaCategoryType.MOVIE -> allBuckets.filter { it.categoryType == MediaCategoryType.MOVIE }
        }

        val trueLosslessCount = activeTracks.count {
            it.verdict == AuthenticityVerdict.TRUE_LOSSLESS && !it.isZeroPadded
        }
        val fakeOrUpsampledCount = activeTracks.count {
            it.verdict != AuthenticityVerdict.TRUE_LOSSLESS || it.isZeroPadded
        }
        val selectedDuplicates = allGroups.sumOf { g ->
            g.redundantItems.count { it.selectedForDeletion }
        }
        val totalReclaimable = allGroups.sumOf { it.reclaimableBytes }

        val inspected = activeTracks.firstOrNull { it.fileUri == navBundle.inspectedUri }
            ?: allTracks.firstOrNull { it.fileUri == navBundle.inspectedUri }
            ?: activeTracks.firstOrNull()

        TrueBitUiState(
            currentTab = navBundle.tab,
            isObsidianDarkTheme = prefs.isObsidianDarkTheme,
            isDeepSpectralScan = prefs.isDeepSpectralScan,
            savedFolderUris = prefs.savedFolderUris,
            scanProgress = progress,
            activeTracks = activeTracks,
            allDuplicateGroups = allGroups,
            filteredDuplicateGroups = filteredGroups,
            activeFilter = navBundle.filter,
            activeCategoryFilter = navBundle.categoryFilter,
            categoryBuckets = filteredBuckets,
            inspectedTrack = inspected,
            trashEntries = trashEntries,
            metrics = DashboardSummaryMetrics(
                totalTracks = activeTracks.size,
                trueLosslessCount = trueLosslessCount,
                fakeOrUpsampledCount = fakeOrUpsampledCount,
                duplicateGroupCount = allGroups.size,
                selectedDuplicateCount = selectedDuplicates,
                reclaimableBytes = totalReclaimable,
                albumBucketCount = allBuckets.count { it.categoryType == MediaCategoryType.ALBUM },
                movieBucketCount = allBuckets.count { it.categoryType == MediaCategoryType.MOVIE },
                unorganizedTrackCount = allBuckets.sumOf { it.unorganizedCount }
            ),
            statusBannerMessage = navBundle.banner
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TrueBitUiState()
    )

    init {
        viewModelScope.launch {
            val existing = scannerRepository.allTracksFlow.first()
            if (existing.isEmpty()) {
                val prefs = preferencesRepository.userPreferencesFlow.first()
                scannerRepository.scanDirectories(
                    safTreeUris = prefs.savedFolderUris,
                    deepSpectralScan = prefs.isDeepSpectralScan,
                    includeMasteringLab = true
                )
            }
        }
    }

    private fun buildCategoryBuckets(tracks: List<AudioTrack>): List<MediaCategoryBucket> {
        return tracks
            .groupBy { it.album.ifBlank { "Unsorted Album" }.trim() }
            .map { (albumName, groupTracks) ->
                val isMovie = groupTracks.any { it.isMovieSoundtrack }
                val type = if (isMovie) MediaCategoryType.MOVIE else MediaCategoryType.ALBUM
                val primaryArtist = groupTracks.firstOrNull()?.artist ?: "Unknown Artist"
                val targetFolder = groupTracks.firstOrNull()?.sanitizedTargetFolderName ?: albumName
                MediaCategoryBucket(
                    bucketId = "${type.name}_$albumName",
                    name = albumName,
                    subtitleArtist = primaryArtist,
                    categoryType = type,
                    targetFolderName = targetFolder,
                    tracks = groupTracks.sortedBy { it.title }
                )
            }
            .sortedWith(
                compareByDescending<MediaCategoryBucket> { it.unorganizedCount > 0 }
                    .thenBy { it.categoryType.ordinal }
                    .thenBy { it.name }
            )
    }

    fun selectTab(tab: TrueBitTab) {
        _currentTab.value = tab
    }

    fun toggleTheme() {
        viewModelScope.launch {
            val current = uiState.value.isObsidianDarkTheme
            preferencesRepository.setObsidianDarkTheme(!current)
        }
    }

    fun setDeepSpectralScan(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setDeepSpectralScan(enabled)
        }
    }

    fun setDuplicateFilter(filter: DuplicateFilter) {
        _activeFilter.value = filter
    }

    fun setCategoryFilter(categoryType: MediaCategoryType) {
        _activeCategoryFilter.value = categoryType
    }

    fun inspectTrack(track: AudioTrack) {
        _inspectedTrackUri.value = track.fileUri
        _currentTab.value = TrueBitTab.INSPECTOR
    }

    fun organizeBucketIntoFolder(bucket: MediaCategoryBucket, customFolderName: String? = null) {
        viewModelScope.launch {
            val folderName = customFolderName?.takeIf { it.isNotBlank() } ?: bucket.targetFolderName
            val prefs = preferencesRepository.userPreferencesFlow.first()
            val moved = scannerRepository.organizeTracksIntoFolder(
                tracks = bucket.tracks,
                targetFolderName = folderName,
                safTreeUris = prefs.savedFolderUris
            )
            _statusBannerMessage.value = if (moved > 0) {
                "Created folder '$folderName' and moved $moved song(s)."
            } else {
                "All songs in '${bucket.name}' are already inside '$folderName'."
            }
        }
    }

    fun organizeAllBucketsIntoFolders() {
        viewModelScope.launch {
            val prefs = preferencesRepository.userPreferencesFlow.first()
            val buckets = uiState.value.categoryBuckets.filter { !it.isAllOrganized }
            if (buckets.isEmpty()) {
                _statusBannerMessage.value = "All albums and movies are already organized into folders."
                return@launch
            }
            var totalMoved = 0
            for (bucket in buckets) {
                totalMoved += scannerRepository.organizeTracksIntoFolder(
                    tracks = bucket.tracks,
                    targetFolderName = bucket.targetFolderName,
                    safTreeUris = prefs.savedFolderUris
                )
            }
            _statusBannerMessage.value = "Created ${buckets.size} Album/Movie folder(s) and moved $totalMoved song(s)."
        }
    }

    fun createCustomFolderAndMoveTracks(folderName: String, selectedTrackUris: Set<String>) {
        viewModelScope.launch {
            val cleanName = folderName.trim()
            if (cleanName.isBlank() || selectedTrackUris.isEmpty()) return@launch
            val tracksToMove = uiState.value.activeTracks.filter { it.fileUri in selectedTrackUris }
            val prefs = preferencesRepository.userPreferencesFlow.first()
            val moved = scannerRepository.organizeTracksIntoFolder(
                tracks = tracksToMove,
                targetFolderName = cleanName,
                safTreeUris = prefs.savedFolderUris
            )
            _statusBannerMessage.value = "Created folder '$cleanName' and moved $moved song(s)."
        }
    }

    fun onSafFolderPicked(treeUri: Uri) {
        viewModelScope.launch {
            scannerRepository.persistSafFolderPermission(treeUri)
            preferencesRepository.addSavedFolderUri(treeUri.toString())
            val prefs = preferencesRepository.userPreferencesFlow.first()
            scannerRepository.scanDirectories(
                safTreeUris = prefs.savedFolderUris + treeUri.toString(),
                deepSpectralScan = prefs.isDeepSpectralScan,
                includeMasteringLab = true
            )
            _statusBannerMessage.value = "Scanned folder & updated incremental spectral cache."
        }
    }

    fun onSingleFilePickedForInspection(fileUri: Uri) {
        viewModelScope.launch {
            val track = scannerRepository.inspectSingleDocumentUri(fileUri)
            _inspectedTrackUri.value = track.fileUri
            _currentTab.value = TrueBitTab.INSPECTOR
            _statusBannerMessage.value = "Completed FFT & 32-bit PCM bit-plane audit on ${track.fileName}."
        }
    }

    fun runScan(forceFullReaudit: Boolean = false) {
        viewModelScope.launch {
            val prefs = preferencesRepository.userPreferencesFlow.first()
            if (forceFullReaudit) {
                scannerRepository.forceClearCacheAndRescan(
                    safTreeUris = prefs.savedFolderUris,
                    deepSpectralScan = prefs.isDeepSpectralScan
                )
                _statusBannerMessage.value = "Re-audited all PCM bit-planes and FFT spectrograms."
            } else {
                scannerRepository.scanDirectories(
                    safTreeUris = prefs.savedFolderUris,
                    deepSpectralScan = prefs.isDeepSpectralScan,
                    includeMasteringLab = true
                )
            }
        }
    }

    fun toggleTrackDeletionSelection(fileUri: String, currentlySelected: Boolean) {
        _manualSelectionOverrides.update { current ->
            current + (fileUri to !currentlySelected)
        }
    }

    fun quarantineSelectedDuplicatesToTrash() {
        viewModelScope.launch {
            val pairs = collectSelectedRedundantTracks()
            if (pairs.isEmpty()) return@launch
            val count = scannerRepository.quarantineTracksToTrash(pairs)
            _statusBannerMessage.value = "Moved $count lower-tier duplicate(s) to .TrueBit_Trash."
        }
    }

    fun permanentlyDeleteSelectedDuplicates() {
        viewModelScope.launch {
            val pairs = collectSelectedRedundantTracks()
            if (pairs.isEmpty()) return@launch
            val count = scannerRepository.permanentlyDeleteTracks(pairs)
            _statusBannerMessage.value = "Permanently deleted $count lower-tier duplicate(s)."
        }
    }

    fun restoreTrashEntry(entry: TrashEntry) {
        viewModelScope.launch {
            scannerRepository.restoreFromTrash(entry)
            _statusBannerMessage.value = "Restored ${entry.fileName} to library."
        }
    }

    fun purgeTrashEntryPermanently(entry: TrashEntry) {
        viewModelScope.launch {
            scannerRepository.purgeTrashEntryPermanently(entry)
            _statusBannerMessage.value = "Permanently purged ${entry.fileName}."
        }
    }

    fun emptyAllTrash() {
        viewModelScope.launch {
            val activeTrash = uiState.value.trashEntries.filter { !it.isPermanentlyDeleted }
            if (activeTrash.isEmpty()) return@launch
            scannerRepository.emptyAllTrashPermanently(activeTrash)
            _statusBannerMessage.value = "Purged ${activeTrash.size} quarantined file(s) from .TrueBit_Trash."
        }
    }

    fun clearDeletedHistoryLog() {
        viewModelScope.launch {
            scannerRepository.clearAuditHistory()
            _statusBannerMessage.value = "Cleared permanent deletion audit history."
        }
    }

    fun dismissStatusBanner() {
        _statusBannerMessage.value = null
    }

    private fun collectSelectedRedundantTracks(): List<Pair<AudioTrack, String>> {
        return uiState.value.allDuplicateGroups.flatMap { group ->
            group.redundantItems
                .filter { it.selectedForDeletion }
                .map { it.track to it.downgradeReason }
        }
    }

    private data class NavFilterCore(
        val tab: TrueBitTab,
        val filter: DuplicateFilter,
        val categoryFilter: MediaCategoryType,
        val inspectedUri: String?,
        val overrides: Map<String, Boolean>
    )

    private data class NavFilterBundle(
        val tab: TrueBitTab,
        val filter: DuplicateFilter,
        val categoryFilter: MediaCategoryType,
        val inspectedUri: String?,
        val overrides: Map<String, Boolean>,
        val banner: String?
    )

    companion object {
        fun provideFactory(container: AppContainer): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return TrueBitViewModel(
                        scannerRepository = container.audioScannerRepository,
                        preferencesRepository = container.themePreferencesRepository,
                        duplicateRanker = container.duplicateRankerUseCase
                    ) as T
                }
            }
        }
    }
}
