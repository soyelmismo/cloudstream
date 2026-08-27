package com.lagradost.cloudstream3.shared.viewmodels.library

import androidx.compose.runtime.Immutable
import cloudstream.shared_ui.generated.resources.Res
import cloudstream.shared_ui.generated.resources.result_error_provider_not_found
import com.lagradost.cloudstream3.APIHolder.unixTimeMS
import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.LiveSearchResponse
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvSeriesSearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.models.ListSorting
import com.lagradost.cloudstream3.shared.mvi.MviViewModel
import com.lagradost.cloudstream3.shared.mvi.UiEffect
import com.lagradost.cloudstream3.shared.mvi.UiEvent
import com.lagradost.cloudstream3.shared.mvi.UiState
import com.lagradost.cloudstream3.shared.persistence.entity.BookmarkEntity
import com.lagradost.cloudstream3.shared.persistence.entity.FavoriteEntity
import com.lagradost.cloudstream3.shared.persistence.entity.WatchProgressEntity
import com.lagradost.cloudstream3.shared.persistence.repository.AppPreferenceManager
import com.lagradost.cloudstream3.shared.persistence.repository.BookmarkRepository
import com.lagradost.cloudstream3.shared.persistence.repository.FavoriteRepository
import com.lagradost.cloudstream3.shared.persistence.repository.WatchProgressRepository
import com.lagradost.cloudstream3.shared.syncproviders.AccountManager
import com.lagradost.cloudstream3.shared.syncproviders.SyncAPI
import com.lagradost.cloudstream3.utils.asStringSuspend
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import org.jetbrains.compose.resources.getString
import kotlin.coroutines.CoroutineContext

enum class WatchStatus(val id: Int) {
    ALL(0),
    WATCHING(1),
    COMPLETED(2),
    PLANNED(5),
    ON_HOLD(3),
    DROPPED(4);

    companion object {
        fun fromId(id: Int): WatchStatus = entries.find { it.id == id } ?: ALL
    }
}

enum class SortOrder {
    LAST_WATCHED,
    RECENTLY_ADDED,
    ALPHABETICAL
}

@Immutable
data class LibraryProvider(
    val id: String,
    val name: String,
    val isLocal: Boolean
)

@Immutable
data class LibraryTab(
    val name: String,
    val count: Int
)

@Immutable
data class UnifiedLibraryItem(
    val id: String,
    val name: String,
    val posterUrl: String?,
    val url: String,
    val apiName: String,
    val progressPercentage: Float = 0f,
    val isWatched: Boolean = false,
    val episodesText: String? = null,
    val score: Double? = null,
    val year: Int? = null,
    val type: TvType? = null,
    val isFavorite: Boolean = false,
    val originalItem: Any
)

@Immutable
data class LibraryItem(
    val id: Int,
    val name: String,
    val url: String,
    val apiName: String,
    val type: TvType? = null,
    val posterUrl: String? = null,
    val year: Int? = null,
    val watchStatus: WatchStatus = WatchStatus.ALL,
    val watchType: Int = 0,
    val bookmarkedTime: Long = 0L,
    val latestUpdatedTime: Long = 0L,
    val quality: SearchQuality? = null,
    val plot: String? = null,
    val score: Double? = null,
    val isFavorite: Boolean = false,
    val position: Long = 0L,
    val duration: Long = 0L,
    val watchState: Int = 0,
    val lastWatchedTime: Long = 0L
) {
    val progressPercentage: Float
        get() = if (duration > 0) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

    val isWatched: Boolean
        get() = watchState == 2 || (duration > 0 && position * 100 / duration >= 90)

    fun toUnifiedItem(): UnifiedLibraryItem {
        return UnifiedLibraryItem(
            id = id.toString(),
            name = name,
            posterUrl = posterUrl,
            url = url,
            apiName = apiName,
            progressPercentage = progressPercentage,
            isWatched = isWatched,
            episodesText = null,
            score = score,
            year = year,
            type = type,
            isFavorite = isFavorite,
            originalItem = this
        )
    }

    @Suppress("DEPRECATION_ERROR")
    fun toSearchResponse(): SearchResponse {
        val targetType = type ?: TvType.Movie
        return when (targetType) {
            TvType.Anime, TvType.AnimeMovie, TvType.OVA -> AnimeSearchResponse(
                name = name,
                url = url,
                apiName = apiName,
                type = targetType,
                posterUrl = posterUrl,
                year = year,
                dubStatus = null,
                otherName = null,
                episodes = mutableMapOf(),
                id = id,
                quality = quality,
                posterHeaders = null,
                score = null
            )
            TvType.TvSeries -> TvSeriesSearchResponse(
                name = name,
                url = url,
                apiName = apiName,
                type = targetType,
                posterUrl = posterUrl,
                year = year,
                episodes = null,
                id = id,
                quality = quality,
                posterHeaders = null,
                score = null
            )
            TvType.Live -> LiveSearchResponse(
                name = name,
                url = url,
                apiName = apiName,
                type = targetType,
                posterUrl = posterUrl,
                id = id,
                quality = quality,
                posterHeaders = null,
                lang = null,
                score = null
            )
            else -> MovieSearchResponse(
                name = name,
                url = url,
                apiName = apiName,
                type = targetType,
                posterUrl = posterUrl,
                year = year,
                id = id,
                quality = quality,
                posterHeaders = null,
                score = null
            )
        }
    }
}

fun SyncAPI.LibraryItem.toUnifiedItem(): UnifiedLibraryItem {
    val totalEp = episodesTotal
    val completedEp = episodesCompleted
    val progress = if (totalEp != null && totalEp > 0 && completedEp != null) {
        (completedEp.toFloat() / totalEp.toFloat()).coerceIn(0f, 1f)
    } else if (completedEp != null && completedEp > 0) {
        1f
    } else {
        0f
    }

    val watched = if (totalEp != null && totalEp > 0 && completedEp != null) {
        completedEp >= totalEp
    } else {
        false
    }

    val epText = if (completedEp != null || totalEp != null) {
        val completed = completedEp ?: 0
        val total = totalEp?.toString() ?: "??"
        "$completed / $total ep"
    } else {
        null
    }

    @Suppress("DEPRECATION")
    val itemYear = releaseDate?.year?.let { it + 1900 }

    val itemScore = personalRating?.toDouble(10) ?: score?.toDouble(10)

    return UnifiedLibraryItem(
        id = syncId,
        name = name,
        posterUrl = posterUrl,
        url = url,
        apiName = apiName,
        progressPercentage = progress,
        isWatched = watched,
        episodesText = epText,
        score = itemScore,
        year = itemYear,
        type = type,
        isFavorite = false,
        originalItem = this
    )
}

@Immutable
data class LibraryState(
    val allItems: ImmutableList<LibraryItem> = persistentListOf(),
    val filteredItems: ImmutableList<LibraryItem> = persistentListOf(),
    val selectedTab: WatchStatus = WatchStatus.ALL,
    val tabCounts: ImmutableMap<WatchStatus, Int> = persistentMapOf(),
    val searchQuery: String = "",
    val selectedType: TvType? = null,
    val sortOrder: SortOrder = SortOrder.LAST_WATCHED,
    val isLoading: Boolean = true,
    val error: String? = null,
    val availableProviders: ImmutableList<LibraryProvider> = persistentListOf(LibraryProvider("local", "Bookmarks", true)),
    val selectedProviderId: String = "local",
    val remoteLists: ImmutableList<SyncAPI.LibraryList> = persistentListOf(),
    val currentTabs: ImmutableList<LibraryTab> = persistentListOf(),
    val selectedTabIndex: Int = 0,
    val displayedItems: ImmutableList<UnifiedLibraryItem> = persistentListOf(),
    val isRefreshing: Boolean = false,
    val syncSortingMethods: ImmutableList<ListSorting> = persistentListOf()
) : UiState {
    val isLibraryEmpty: Boolean
        get() = if (selectedProviderId == "local") {
            allItems.isEmpty() && !isLoading
        } else {
            displayedItems.isEmpty() && !isLoading && !isRefreshing
        }

    val isFilteredEmpty: Boolean
        get() = if (selectedProviderId == "local") {
            filteredItems.isEmpty() && !isLoading
        } else {
            displayedItems.isEmpty() && !isLoading && !isRefreshing
        }
}

sealed interface LibraryEvent : UiEvent {
    data class SelectTab(val status: WatchStatus) : LibraryEvent
    data class SelectTabIndex(val index: Int) : LibraryEvent
    data class SelectProvider(val providerId: String) : LibraryEvent
    data object RefreshLibrary : LibraryEvent
    data class Search(val query: String) : LibraryEvent
    data class SetFilterType(val type: TvType?) : LibraryEvent
    data class SetSortOrder(val sortOrder: SortOrder) : LibraryEvent
    data class RemoveBookmark(val id: Int) : LibraryEvent
    data class ToggleFavorite(val id: Int) : LibraryEvent
    data class BatchRemoveBookmarks(val ids: Set<Int>) : LibraryEvent
    data class BatchSetWatchStatus(val ids: Set<Int>, val status: WatchStatus) : LibraryEvent
    data object ClearSearch : LibraryEvent
    data object ClearFilters : LibraryEvent
    data class SelectItem(val item: UnifiedLibraryItem) : LibraryEvent {
        constructor(localItem: LibraryItem) : this(localItem.toUnifiedItem())
    }
    data object DismissError : LibraryEvent
}

sealed interface LibraryEffect : UiEffect {
    data class NavigateToDetails(val url: String, val apiName: String) : LibraryEffect
    data object NavigateToHome : LibraryEffect
    data class ShowToast(val message: String) : LibraryEffect
    data class SearchMedia(val query: String) : LibraryEffect
}

class LibraryViewModel(
    private val bookmarkRepository: BookmarkRepository,
    private val watchProgressRepository: WatchProgressRepository,
    private val favoriteRepository: FavoriteRepository,
    private val accountId: Int = 0,
    initialState: LibraryState = LibraryState(),
    coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.Default
) : MviViewModel<LibraryState, LibraryEvent>(initialState, coroutineContext) {

    init {
        observeRepositories()
        observeAccounts()
    }

    private fun observeRepositories() {
        launchSafeJob(key = "observation") {
            combine(
                bookmarkRepository.getAllBookmarksFlow(accountId),
                watchProgressRepository.getAllProgressFlow(accountId),
                favoriteRepository.getAllFavoritesFlow(accountId)
            ) { bookmarks, progresses, favorites ->
                val allItems = buildLibraryItems(bookmarks, progresses, favorites)
                val immutableCounts = computeWatchStatusCounts(allItems)
                val filtered = computeFilteredItems(
                    items = allItems,
                    tab = currentState.selectedTab,
                    query = currentState.searchQuery,
                    type = currentState.selectedType,
                    sort = currentState.sortOrder
                )
                val localTabs = computeLocalTabs(immutableCounts)

                updateState {
                    copy(
                        allItems = allItems,
                        filteredItems = filtered,
                        tabCounts = immutableCounts,
                        currentTabs = if (selectedProviderId == "local") localTabs else currentTabs,
                        displayedItems = if (selectedProviderId == "local") filtered.map { it.toUnifiedItem() }.toImmutableList() else displayedItems,
                        isLoading = false,
                        error = null
                    )
                }
            }.collect {}
        }
    }

    private fun resolveAvailableProviders(accountsMap: Map<String, *>): ImmutableList<LibraryProvider> {
        val localProvider = LibraryProvider("local", "Bookmarks", true)
        val remoteProviders = AccountManager.syncApis
            .filter { it.idPrefix != "local" && accountsMap.containsKey(it.idPrefix) }
            .map { LibraryProvider(id = it.idPrefix, name = it.name, isLocal = false) }
        return (listOf(localProvider) + remoteProviders).toImmutableList()
    }

    private fun resolveTargetProviderId(providers: List<LibraryProvider>): String {
        val savedProvider = AppPreferenceManager.getStringSync(
            AppPreferenceManager.getLastSyncApiKey(accountId),
            "local"
        ) ?: "local"
        val isSavedValid = providers.any { it.id == savedProvider }
        if (!isSavedValid) {
            AppPreferenceManager.setStringSync(AppPreferenceManager.getLastSyncApiKey(accountId), "local")
            return "local"
        }
        return savedProvider
    }

    private fun shouldSwitchToLocal(providers: List<LibraryProvider>, targetProviderId: String): Boolean {
        val isCurrentValid = providers.any { it.id == currentState.selectedProviderId }
        val isTargetLocal = targetProviderId == "local"
        val isCurrentNotLocal = currentState.selectedProviderId != "local"
        return !isCurrentValid || (isTargetLocal && isCurrentNotLocal)
    }

    private fun shouldFetchRemote(targetProviderId: String): Boolean {
        if (targetProviderId == "local") return false
        val isDifferentProvider = currentState.selectedProviderId != targetProviderId
        val needsInitialLoad = currentState.remoteLists.isEmpty() && !currentState.isLoading
        return isDifferentProvider || needsInitialLoad
    }

    private fun switchToLocalProvider(providers: ImmutableList<LibraryProvider>) {
        val localTabs = computeLocalTabs(currentState.tabCounts)
        updateState {
            copy(
                availableProviders = providers,
                selectedProviderId = "local",
                selectedTabIndex = 0,
                currentTabs = localTabs,
                displayedItems = filteredItems.map { it.toUnifiedItem() }.toImmutableList(),
                remoteLists = persistentListOf(),
                syncSortingMethods = persistentListOf()
            )
        }
    }

    private fun switchToRemoteProvider(providers: ImmutableList<LibraryProvider>, targetProviderId: String) {
        updateState {
            copy(
                availableProviders = providers,
                selectedProviderId = targetProviderId,
                selectedTabIndex = 0,
                remoteLists = persistentListOf(),
                currentTabs = persistentListOf(),
                displayedItems = persistentListOf(),
                syncSortingMethods = persistentListOf(),
                error = null
            )
        }
        fetchRemoteLibrary(targetProviderId, force = false)
    }

    private fun syncAccountProviders(accountsMap: Map<String, *>) {
        val providers = resolveAvailableProviders(accountsMap)
        val targetProviderId = resolveTargetProviderId(providers)

        when {
            shouldSwitchToLocal(providers, targetProviderId) -> switchToLocalProvider(providers)
            shouldFetchRemote(targetProviderId) -> switchToRemoteProvider(providers, targetProviderId)
            else -> updateState { copy(availableProviders = providers) }
        }
    }

    private fun observeAccounts() {
        launchSafeJob(key = "accounts_observation") {
            AccountManager.accountsState.collect { accountsMap ->
                syncAccountProviders(accountsMap)
            }
        }
    }

    private fun computeLocalTabs(counts: Map<WatchStatus, Int>): ImmutableList<LibraryTab> {
        return persistentListOf(
            LibraryTab("All", counts[WatchStatus.ALL] ?: 0),
            LibraryTab("Watching", counts[WatchStatus.WATCHING] ?: 0),
            LibraryTab("Completed", counts[WatchStatus.COMPLETED] ?: 0),
            LibraryTab("Plan to Watch", counts[WatchStatus.PLANNED] ?: 0),
            LibraryTab("On Hold", counts[WatchStatus.ON_HOLD] ?: 0),
            LibraryTab("Dropped", counts[WatchStatus.DROPPED] ?: 0),
        )
    }

    private fun fetchRemoteLibrary(providerId: String, force: Boolean = false) {
        val syncRepo = AccountManager.syncApis.find { it.idPrefix == providerId }
        if (syncRepo == null) {
            launch {
                val errorMessage = getString(Res.string.result_error_provider_not_found, providerId)
                updateState {
                    copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = errorMessage
                    )
                }
            }
            return
        }

        updateState {
            copy(
                isRefreshing = force || remoteLists.isNotEmpty(),
                isLoading = remoteLists.isEmpty() && !force,
                error = null
            )
        }

        launchSafeJob(key = "fetch_remote_library") {
            if (force) {
                syncRepo.requireLibraryRefresh = true
            }
            val result = syncRepo.library()
            result.onSuccess { metadata ->
                val lists = (metadata?.allLibraryLists ?: emptyList()).toImmutableList()
                val sortingMethods = (metadata?.supportedListSorting?.toList() ?: emptyList()).toImmutableList()
                val tabs = lists.map { LibraryTab(name = it.name.asStringSuspend(), count = it.items.size) }.toImmutableList()
                val safeIndex = currentState.selectedTabIndex.coerceIn(0, (lists.size - 1).coerceAtLeast(0))
                val currentListItems = lists.getOrNull(safeIndex)?.items?.map { it.toUnifiedItem() }?.toImmutableList() ?: persistentListOf()
                val filtered = computeFilteredRemoteItems(
                    items = currentListItems,
                    query = currentState.searchQuery,
                    type = currentState.selectedType,
                    sort = currentState.sortOrder
                )

                updateState {
                    copy(
                        remoteLists = lists,
                        currentTabs = tabs,
                        selectedTabIndex = safeIndex,
                        displayedItems = filtered,
                        syncSortingMethods = sortingMethods,
                        isLoading = false,
                        isRefreshing = false,
                        error = null
                    )
                }
            }.onFailure { throwable ->
                updateState {
                    copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = throwable.message ?: "Failed to load library"
                    )
                }
            }
        }
    }

    companion object {
        private val LOCAL_WATCH_STATUSES = persistentListOf(
            WatchStatus.ALL,
            WatchStatus.WATCHING,
            WatchStatus.COMPLETED,
            WatchStatus.PLANNED,
            WatchStatus.ON_HOLD,
            WatchStatus.DROPPED
        )
    }

    private fun computeDisplayedAndFiltered(
        tab: WatchStatus = currentState.selectedTab,
        tabIndex: Int = currentState.selectedTabIndex,
        query: String = currentState.searchQuery,
        type: TvType? = currentState.selectedType,
        sort: SortOrder = currentState.sortOrder
    ): Pair<ImmutableList<LibraryItem>, ImmutableList<UnifiedLibraryItem>> {
        val filtered = computeFilteredItems(currentState.allItems, tab, query, type, sort)
        val displayed = if (currentState.selectedProviderId == "local") {
            filtered.map { it.toUnifiedItem() }.toImmutableList()
        } else {
            val rawItems = currentState.remoteLists.getOrNull(tabIndex)?.items?.map { it.toUnifiedItem() } ?: emptyList()
            computeFilteredRemoteItems(rawItems, query, type, sort)
        }
        return filtered to displayed
    }

    private fun selectLocalTab(status: WatchStatus? = null, index: Int? = null) {
        val targetStatus = status ?: index?.let { LOCAL_WATCH_STATUSES.getOrElse(it) { WatchStatus.ALL } } ?: WatchStatus.ALL
        val targetIndex = index ?: LOCAL_WATCH_STATUSES.indexOf(targetStatus).coerceAtLeast(0)
        val filtered = computeFilteredItems(
            currentState.allItems,
            targetStatus,
            currentState.searchQuery,
            currentState.selectedType,
            currentState.sortOrder
        )
        updateState {
            copy(
                selectedTab = targetStatus,
                selectedTabIndex = targetIndex,
                filteredItems = filtered,
                displayedItems = filtered.map { it.toUnifiedItem() }.toImmutableList()
            )
        }
    }

    private fun selectRemoteTab(index: Int?) {
        val safeIndex = (index ?: 0).coerceIn(0, (currentState.remoteLists.size - 1).coerceAtLeast(0))
        val rawItems = currentState.remoteLists.getOrNull(safeIndex)?.items?.map { it.toUnifiedItem() } ?: emptyList()
        val filtered = computeFilteredRemoteItems(
            rawItems,
            currentState.searchQuery,
            currentState.selectedType,
            currentState.sortOrder
        )
        updateState {
            copy(
                selectedTabIndex = safeIndex,
                displayedItems = filtered
            )
        }
    }

    fun selectTab(status: WatchStatus? = null, index: Int? = null) {
        if (currentState.selectedProviderId == "local") {
            selectLocalTab(status, index)
        } else {
            selectRemoteTab(index)
        }
    }

    fun selectProvider(providerId: String) {
        AppPreferenceManager.setStringSync(AppPreferenceManager.getLastSyncApiKey(accountId), providerId)
        if (providerId == currentState.selectedProviderId) return
        if (providerId == "local") {
            val localTabs = computeLocalTabs(currentState.tabCounts)
            val (filtered, displayed) = computeDisplayedAndFiltered(tab = currentState.selectedTab, tabIndex = 0)
            updateState {
                copy(
                    selectedProviderId = "local",
                    selectedTabIndex = 0,
                    currentTabs = localTabs,
                    displayedItems = displayed,
                    remoteLists = persistentListOf(),
                    syncSortingMethods = persistentListOf(),
                    error = null
                )
            }
        } else {
            updateState {
                copy(
                    selectedProviderId = providerId,
                    selectedTabIndex = 0,
                    remoteLists = persistentListOf(),
                    currentTabs = persistentListOf(),
                    displayedItems = persistentListOf(),
                    syncSortingMethods = persistentListOf(),
                    error = null
                )
            }
            fetchRemoteLibrary(providerId, force = false)
        }
    }

    fun search(query: String) {
        val (filtered, displayed) = computeDisplayedAndFiltered(query = query)
        updateState {
            copy(
                searchQuery = query,
                filteredItems = filtered,
                displayedItems = displayed
            )
        }
    }

    fun setFilterType(type: TvType?) {
        val (filtered, displayed) = computeDisplayedAndFiltered(type = type)
        updateState {
            copy(
                selectedType = type,
                filteredItems = filtered,
                displayedItems = displayed
            )
        }
    }

    fun setSortOrder(sortOrder: SortOrder) {
        val (filtered, displayed) = computeDisplayedAndFiltered(sort = sortOrder)
        updateState {
            copy(
                sortOrder = sortOrder,
                filteredItems = filtered,
                displayedItems = displayed
            )
        }
    }

    fun clearSearch() {
        val (filtered, displayed) = computeDisplayedAndFiltered(query = "")
        updateState {
            copy(
                searchQuery = "",
                filteredItems = filtered,
                displayedItems = displayed
            )
        }
    }

    fun clearFilters() {
        val (filtered, displayed) = computeDisplayedAndFiltered(tab = WatchStatus.ALL, tabIndex = 0, query = "", type = null)
        updateState {
            copy(
                selectedTab = WatchStatus.ALL,
                selectedTabIndex = 0,
                selectedType = null,
                searchQuery = "",
                filteredItems = filtered,
                displayedItems = displayed
            )
        }
    }

    fun refreshLibrary() {
        if (currentState.selectedProviderId != "local") {
            fetchRemoteLibrary(currentState.selectedProviderId, force = true)
        }
    }

    fun removeBookmark(id: Int) {
        launch {
            bookmarkRepository.deleteBookmark(accountId, id)
        }
    }

    fun batchRemoveBookmarks(ids: Collection<Int>) {
        launch {
            ids.forEach { id ->
                bookmarkRepository.deleteBookmark(accountId, id)
            }
        }
    }

    fun batchSetWatchStatus(ids: Collection<Int>, status: WatchStatus) {
        launch {
            ids.forEach { id ->
                val item = currentState.allItems.find { it.id == id }
                if (item != null) {
                    bookmarkRepository.saveBookmark(
                        BookmarkEntity(
                            accountId = accountId,
                            id = item.id,
                            name = item.name,
                            url = item.url,
                            apiName = item.apiName,
                            type = item.type,
                            posterUrl = item.posterUrl,
                            year = item.year,
                            watchType = status.id,
                            bookmarkedTime = if (item.bookmarkedTime > 0) item.bookmarkedTime else unixTimeMS,
                            latestUpdatedTime = unixTimeMS,
                            quality = item.quality,
                            plot = item.plot,
                            score = item.score
                        )
                    )
                }
            }
        }
    }

    fun toggleFavorite(id: Int) {
        launch {
            val item = currentState.allItems.find { it.id == id }
            if (item != null) {
                if (item.isFavorite) {
                    favoriteRepository.deleteFavorite(accountId, id)
                } else {
                    favoriteRepository.saveFavorite(
                        FavoriteEntity(
                            accountId = accountId,
                            id = item.id,
                            name = item.name,
                            url = item.url,
                            apiName = item.apiName,
                            type = item.type,
                            posterUrl = item.posterUrl,
                            favoritesTime = unixTimeMS
                        )
                    )
                }
            }
        }
    }

    fun selectItem(item: UnifiedLibraryItem) {
        if (item.originalItem is LibraryItem || (item.url.isNotBlank() && item.apiName.isNotBlank() && item.originalItem !is SyncAPI.LibraryItem)) {
            emitEffect(LibraryEffect.NavigateToDetails(url = item.url, apiName = item.apiName))
        } else {
            emitEffect(LibraryEffect.SearchMedia(query = item.name))
        }
    }

    fun dismissError() {
        updateState { copy(error = null) }
    }

    private fun handleNavigationAndTabEvent(event: LibraryEvent): Boolean = when (event) {
        is LibraryEvent.SelectProvider -> { selectProvider(event.providerId); true }
        is LibraryEvent.SelectTab -> { selectTab(status = event.status); true }
        is LibraryEvent.SelectTabIndex -> { selectTab(index = event.index); true }
        is LibraryEvent.RefreshLibrary -> { refreshLibrary(); true }
        else -> false
    }

    private fun handleFilterAndSearchEvent(event: LibraryEvent): Boolean = when (event) {
        is LibraryEvent.Search -> { search(event.query); true }
        is LibraryEvent.SetFilterType -> { setFilterType(event.type); true }
        is LibraryEvent.SetSortOrder -> { setSortOrder(event.sortOrder); true }
        is LibraryEvent.ClearSearch -> { clearSearch(); true }
        is LibraryEvent.ClearFilters -> { clearFilters(); true }
        else -> false
    }

    private fun handleDataMutationEvent(event: LibraryEvent): Boolean = when (event) {
        is LibraryEvent.RemoveBookmark -> { removeBookmark(event.id); true }
        is LibraryEvent.BatchRemoveBookmarks -> { batchRemoveBookmarks(event.ids); true }
        is LibraryEvent.BatchSetWatchStatus -> { batchSetWatchStatus(event.ids, event.status); true }
        is LibraryEvent.ToggleFavorite -> { toggleFavorite(event.id); true }
        is LibraryEvent.SelectItem -> { selectItem(event.item); true }
        is LibraryEvent.DismissError -> { dismissError(); true }
        else -> false
    }

    override fun handleEvent(event: LibraryEvent) {
        if (handleNavigationAndTabEvent(event)) return
        if (handleFilterAndSearchEvent(event)) return
        handleDataMutationEvent(event)
    }

    private fun matchesQuery(name: String, apiName: String, query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim().lowercase()
        return name.lowercase().contains(q) || apiName.lowercase().contains(q)
    }

    private fun matchesType(itemType: TvType?, filterType: TvType?): Boolean {
        if (filterType == null) return true
        return when (filterType) {
            TvType.Anime -> itemType in listOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)
            TvType.TvSeries -> itemType == TvType.TvSeries
            TvType.Movie -> itemType in listOf(TvType.Movie, TvType.Torrent)
            else -> itemType == filterType
        }
    }

    private fun matchesFilter(
        item: LibraryItem,
        tab: WatchStatus,
        query: String,
        type: TvType?
    ): Boolean {
        val matchesTab = tab == WatchStatus.ALL || item.watchStatus == tab
        return matchesTab && matchesQuery(item.name, item.apiName, query) && matchesType(item.type, type)
    }

    private fun applySortOrder(items: List<LibraryItem>, sort: SortOrder): List<LibraryItem> = when (sort) {
        SortOrder.LAST_WATCHED -> items.sortedWith(
            compareByDescending<LibraryItem> {
                maxOf(it.lastWatchedTime, it.latestUpdatedTime, it.bookmarkedTime)
            }.thenBy { it.name.lowercase() }
        )
        SortOrder.RECENTLY_ADDED -> items.sortedWith(
            compareByDescending<LibraryItem> {
                maxOf(it.bookmarkedTime, it.latestUpdatedTime)
            }.thenBy { it.name.lowercase() }
        )
        SortOrder.ALPHABETICAL -> items.sortedBy { it.name.lowercase() }
    }

    private fun computeFilteredItems(
        items: List<LibraryItem>,
        tab: WatchStatus,
        query: String,
        type: TvType?,
        sort: SortOrder
    ): ImmutableList<LibraryItem> {
        val filtered = items.filter { matchesFilter(it, tab, query, type) }
        return applySortOrder(filtered, sort).toImmutableList()
    }

    private fun computeFilteredRemoteItems(
        items: List<UnifiedLibraryItem>,
        query: String,
        type: TvType?,
        sort: SortOrder
    ): ImmutableList<UnifiedLibraryItem> {
        val filtered = items.filter { item ->
            matchesQuery(item.name, item.apiName, query) &&
                matchesType(item.type, type)
        }

        val sorted = when (sort) {
            SortOrder.ALPHABETICAL -> filtered.sortedBy { it.name.lowercase() }
            else -> filtered
        }
        return sorted.toImmutableList()
    }

    private fun mapBookmarkToLibraryItem(
        bookmark: BookmarkEntity,
        progress: WatchProgressEntity?,
        isFavorite: Boolean
    ): LibraryItem {
        return LibraryItem(
            id = bookmark.id,
            name = bookmark.name,
            url = bookmark.url,
            apiName = bookmark.apiName,
            type = bookmark.type,
            posterUrl = bookmark.posterUrl,
            year = bookmark.year,
            watchStatus = WatchStatus.fromId(bookmark.watchType),
            watchType = bookmark.watchType,
            bookmarkedTime = bookmark.bookmarkedTime,
            latestUpdatedTime = bookmark.latestUpdatedTime,
            quality = bookmark.quality,
            plot = bookmark.plot,
            score = bookmark.score,
            isFavorite = isFavorite,
            position = progress?.position ?: 0L,
            duration = progress?.duration ?: 0L,
            watchState = progress?.watchState ?: 0,
            lastWatchedTime = progress?.lastUpdated ?: 0L
        )
    }

    private fun mapFavoriteToLibraryItem(
        fav: FavoriteEntity,
        progress: WatchProgressEntity?
    ): LibraryItem {
        return LibraryItem(
            id = fav.id,
            name = fav.name,
            url = fav.url,
            apiName = fav.apiName,
            type = fav.type,
            posterUrl = fav.posterUrl,
            year = null,
            watchStatus = WatchStatus.ALL,
            watchType = 0,
            bookmarkedTime = fav.favoritesTime,
            latestUpdatedTime = fav.favoritesTime,
            quality = null,
            plot = null,
            score = null,
            isFavorite = true,
            position = progress?.position ?: 0L,
            duration = progress?.duration ?: 0L,
            watchState = progress?.watchState ?: 0,
            lastWatchedTime = progress?.lastUpdated ?: 0L
        )
    }

    private fun buildLibraryItems(
        bookmarks: List<BookmarkEntity>,
        progresses: List<WatchProgressEntity>,
        favorites: List<FavoriteEntity>
    ): ImmutableList<LibraryItem> {
        val progressMap = progresses.associateBy { it.mediaId }
        val favoriteIds = favorites.map { it.id }.toSet()
        val bookmarkIds = bookmarks.map { it.id }.toSet()

        val bookmarkItems = bookmarks.map { bookmark ->
            mapBookmarkToLibraryItem(
                bookmark = bookmark,
                progress = progressMap[bookmark.id],
                isFavorite = favoriteIds.contains(bookmark.id)
            )
        }

        val standaloneFavorites = favorites
            .filter { !bookmarkIds.contains(it.id) }
            .map { fav ->
                mapFavoriteToLibraryItem(
                    fav = fav,
                    progress = progressMap[fav.id]
                )
            }

        return (bookmarkItems + standaloneFavorites).toImmutableList()
    }

    private fun computeWatchStatusCounts(items: List<LibraryItem>): ImmutableMap<WatchStatus, Int> {
        val counts = mutableMapOf<WatchStatus, Int>()
        counts[WatchStatus.ALL] = items.size
        counts[WatchStatus.WATCHING] = items.count { it.watchStatus == WatchStatus.WATCHING }
        counts[WatchStatus.COMPLETED] = items.count { it.watchStatus == WatchStatus.COMPLETED }
        counts[WatchStatus.PLANNED] = items.count { it.watchStatus == WatchStatus.PLANNED }
        counts[WatchStatus.ON_HOLD] = items.count { it.watchStatus == WatchStatus.ON_HOLD }
        counts[WatchStatus.DROPPED] = items.count { it.watchStatus == WatchStatus.DROPPED }
        return counts.toImmutableMap()
    }
}
