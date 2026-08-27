package com.lagradost.cloudstream3.shared.viewmodels.library

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
import com.lagradost.cloudstream3.shared.persistence.repository.AppPreferenceManager
import com.lagradost.cloudstream3.shared.persistence.repository.BookmarkRepository
import com.lagradost.cloudstream3.shared.persistence.repository.FavoriteRepository
import com.lagradost.cloudstream3.shared.persistence.repository.WatchProgressRepository
import com.lagradost.cloudstream3.shared.syncproviders.AccountManager
import com.lagradost.cloudstream3.shared.syncproviders.SyncAPI
import com.lagradost.cloudstream3.utils.asStringSuspend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import org.jetbrains.compose.resources.getString
import kotlin.coroutines.CoroutineContext

/**
 * Watch status category for bookmark items.
 * Maps to integer watch types:
 * 0 = ALL (or None)
 * 1 = WATCHING
 * 2 = COMPLETED
 * 3 = ON_HOLD
 * 4 = DROPPED
 * 5 = PLANNED
 */
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

/**
 * Sort options for media items in the Library.
 */
enum class SortOrder {
    LAST_WATCHED,      // Most recently watched first
    RECENTLY_ADDED,    // Most recently bookmarked/saved first
    ALPHABETICAL       // A to Z title sorting
}

/**
 * Unified representation of a sync provider or local bookmark source.
 */
data class LibraryProvider(
    val id: String,
    val name: String,
    val isLocal: Boolean
)

/**
 * Unified representation of a tab / category header.
 */
data class LibraryTab(
    val name: String,
    val count: Int
)

/**
 * Unified representation of an item across local bookmarks and remote sync providers (AniList, MAL, Simkl).
 */
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

/**
 * Model representing a bookmarked media item in the Library with merged watch progress.
 */
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
    val watchState: Int = 0, // 0=None, 1=Watching, 2=Watched
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

    /**
     * Converts to a [SearchResponse] instance for standard MediaCard interop.
     */
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

/**
 * Converts a [SyncAPI.LibraryItem] from a remote sync provider to [UnifiedLibraryItem].
 */
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

/**
 * Immutable State for the Library screen in KMP MVI architecture.
 */
data class LibraryState(
    val allItems: List<LibraryItem> = emptyList(),
    val filteredItems: List<LibraryItem> = emptyList(),
    val selectedTab: WatchStatus = WatchStatus.ALL,
    val tabCounts: Map<WatchStatus, Int> = emptyMap(),
    val searchQuery: String = "",
    val selectedType: TvType? = null,
    val sortOrder: SortOrder = SortOrder.LAST_WATCHED,
    val isLoading: Boolean = true,
    val error: String? = null,
    val availableProviders: List<LibraryProvider> = listOf(LibraryProvider("local", "Bookmarks", true)),
    val selectedProviderId: String = "local",
    val remoteLists: List<SyncAPI.LibraryList> = emptyList(),
    val currentTabs: List<LibraryTab> = emptyList(),
    val selectedTabIndex: Int = 0,
    val displayedItems: List<UnifiedLibraryItem> = emptyList(),
    val isRefreshing: Boolean = false,
    val syncSortingMethods: List<ListSorting> = emptyList()
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

/**
 * User Intents / Events for [LibraryViewModel].
 */
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

/**
 * Side effects emitted by [LibraryViewModel].
 */
sealed interface LibraryEffect : UiEffect {
    data class NavigateToDetails(val url: String, val apiName: String) : LibraryEffect
    data object NavigateToHome : LibraryEffect
    data class ShowToast(val message: String) : LibraryEffect
    data class SearchMedia(val query: String) : LibraryEffect
}

/**
 * Pure Kotlin Multiplatform ViewModel managing the Library & Bookmarks state.
 * Reactively combines BookmarkRepository, WatchProgressRepository, FavoriteRepository, and AccountManager sync APIs.
 *
 * @param bookmarkRepository Repository managing user bookmarks and watch types.
 * @param watchProgressRepository Repository managing video playback progress and watched status.
 * @param favoriteRepository Repository managing user favorites.
 * @param accountId Active account identifier. Defaults to 0.
 * @param initialState Initial UI state.
 * @param coroutineContext Optional custom coroutine context for viewModelScope.
 */
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

    /**
     * Reactively observes Room persistence flows and automatically computes
     * merged library items, tab counters, and applied filters.
     */
    private fun observeRepositories() {
        launchSafeJob(key = "observation") {
            combine(
                bookmarkRepository.getAllBookmarksFlow(accountId),
                watchProgressRepository.getAllProgressFlow(accountId),
                favoriteRepository.getAllFavoritesFlow(accountId)
            ) { bookmarks, progresses, favorites ->
                val progressMap = progresses.associateBy { it.mediaId }
                val favoriteIds = favorites.map { it.id }.toSet()
                val bookmarkIds = bookmarks.map { it.id }.toSet()

                // Map Bookmarks to LibraryItems
                val bookmarkItems = bookmarks.map { bookmark ->
                    val progress = progressMap[bookmark.id]
                    val isFav = favoriteIds.contains(bookmark.id)
                    val status = WatchStatus.fromId(bookmark.watchType)
                    LibraryItem(
                        id = bookmark.id,
                        name = bookmark.name,
                        url = bookmark.url,
                        apiName = bookmark.apiName,
                        type = bookmark.type,
                        posterUrl = bookmark.posterUrl,
                        year = bookmark.year,
                        watchStatus = status,
                        watchType = bookmark.watchType,
                        bookmarkedTime = bookmark.bookmarkedTime,
                        latestUpdatedTime = bookmark.latestUpdatedTime,
                        quality = bookmark.quality,
                        plot = bookmark.plot,
                        score = bookmark.score,
                        isFavorite = isFav,
                        position = progress?.position ?: 0L,
                        duration = progress?.duration ?: 0L,
                        watchState = progress?.watchState ?: 0,
                        lastWatchedTime = progress?.lastUpdated ?: 0L
                    )
                }

                // Add standalone favorites (not already in bookmarks)
                val standaloneFavorites = favorites.filter { !bookmarkIds.contains(it.id) }.map { fav ->
                    val progress = progressMap[fav.id]
                    LibraryItem(
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

                val allItems = bookmarkItems + standaloneFavorites

                // Calculate tab counts
                val counts = mutableMapOf<WatchStatus, Int>()
                counts[WatchStatus.ALL] = allItems.size
                counts[WatchStatus.WATCHING] = allItems.count { it.watchStatus == WatchStatus.WATCHING }
                counts[WatchStatus.COMPLETED] = allItems.count { it.watchStatus == WatchStatus.COMPLETED }
                counts[WatchStatus.PLANNED] = allItems.count { it.watchStatus == WatchStatus.PLANNED }
                counts[WatchStatus.ON_HOLD] = allItems.count { it.watchStatus == WatchStatus.ON_HOLD }
                counts[WatchStatus.DROPPED] = allItems.count { it.watchStatus == WatchStatus.DROPPED }

                val filtered = computeFilteredItems(
                    items = allItems,
                    tab = currentState.selectedTab,
                    query = currentState.searchQuery,
                    type = currentState.selectedType,
                    sort = currentState.sortOrder
                )

                val localTabs = computeLocalTabs(counts)

                updateState {
                    copy(
                        allItems = allItems,
                        filteredItems = filtered,
                        tabCounts = counts,
                        currentTabs = if (selectedProviderId == "local") localTabs else currentTabs,
                        displayedItems = if (selectedProviderId == "local") filtered.map { it.toUnifiedItem() } else displayedItems,
                        isLoading = false,
                        error = null
                    )
                }
            }.collect {}
        }
    }

    /**
     * Reactively observes sync account state and dynamically updates available providers.
     */
    private fun observeAccounts() {
        launchSafeJob(key = "accounts_observation") {
            AccountManager.accountsState.collect { accountsMap ->
                val localProvider = LibraryProvider("local", "Bookmarks", true)
                val remoteProviders = AccountManager.syncApis
                    .filter { it.idPrefix != "local" && accountsMap[it.idPrefix] != null }
                    .map { LibraryProvider(id = it.idPrefix, name = it.name, isLocal = false) }
                val providers = listOf(localProvider) + remoteProviders

                val savedProvider = AppPreferenceManager.getStringSync(AppPreferenceManager.getLastSyncApiKey(accountId), "local") ?: "local"
                val isSavedValid = providers.any { it.id == savedProvider }
                val targetProviderId = if (isSavedValid) savedProvider else "local"

                if (!isSavedValid) {
                    AppPreferenceManager.setStringSync(AppPreferenceManager.getLastSyncApiKey(accountId), "local")
                }

                val isCurrentValid = providers.any { it.id == currentState.selectedProviderId }

                if (!isCurrentValid || (targetProviderId == "local" && currentState.selectedProviderId != "local")) {
                    val localTabs = computeLocalTabs(currentState.tabCounts)
                    updateState {
                        copy(
                            availableProviders = providers,
                            selectedProviderId = "local",
                            selectedTabIndex = 0,
                            currentTabs = localTabs,
                            displayedItems = filteredItems.map { it.toUnifiedItem() },
                            remoteLists = emptyList(),
                            syncSortingMethods = emptyList()
                        )
                    }
                } else if (targetProviderId != "local" && (currentState.selectedProviderId != targetProviderId || (currentState.remoteLists.isEmpty() && !currentState.isLoading))) {
                    updateState {
                        copy(
                            availableProviders = providers,
                            selectedProviderId = targetProviderId,
                            selectedTabIndex = 0,
                            remoteLists = emptyList(),
                            currentTabs = emptyList(),
                            displayedItems = emptyList(),
                            syncSortingMethods = emptyList(),
                            error = null
                        )
                    }
                    fetchRemoteLibrary(targetProviderId, force = false)
                } else {
                    updateState {
                        copy(availableProviders = providers)
                    }
                }
            }
        }
    }

    private fun computeLocalTabs(counts: Map<WatchStatus, Int>): List<LibraryTab> {
        return listOf(
            LibraryTab("All", counts[WatchStatus.ALL] ?: 0),
            LibraryTab("Watching", counts[WatchStatus.WATCHING] ?: 0),
            LibraryTab("Completed", counts[WatchStatus.COMPLETED] ?: 0),
            LibraryTab("Plan to Watch", counts[WatchStatus.PLANNED] ?: 0),
            LibraryTab("On Hold", counts[WatchStatus.ON_HOLD] ?: 0),
            LibraryTab("Dropped", counts[WatchStatus.DROPPED] ?: 0),
        )
    }

    /**
     * Fetches remote library metadata and lists from a sync provider (AniList, MAL, Simkl).
     */
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
                val lists = metadata?.allLibraryLists ?: emptyList()
                val sortingMethods = metadata?.supportedListSorting?.toList() ?: emptyList()
                val tabs = lists.map { LibraryTab(name = it.name.asStringSuspend(), count = it.items.size) }
                val safeIndex = currentState.selectedTabIndex.coerceIn(0, (lists.size - 1).coerceAtLeast(0))
                val currentListItems = lists.getOrNull(safeIndex)?.items?.map { it.toUnifiedItem() } ?: emptyList()
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
        private val LOCAL_WATCH_STATUSES = listOf(
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
    ): Pair<List<LibraryItem>, List<UnifiedLibraryItem>> {
        val filtered = computeFilteredItems(currentState.allItems, tab, query, type, sort)
        val displayed = if (currentState.selectedProviderId == "local") {
            filtered.map { it.toUnifiedItem() }
        } else {
            val rawItems = currentState.remoteLists.getOrNull(tabIndex)?.items?.map { it.toUnifiedItem() } ?: emptyList()
            computeFilteredRemoteItems(rawItems, query, type, sort)
        }
        return filtered to displayed
    }

    private fun selectTab(status: WatchStatus? = null, index: Int? = null) {
        if (currentState.selectedProviderId == "local") {
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
                    displayedItems = filtered.map { it.toUnifiedItem() }
                )
            }
        } else {
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
    }

    override fun handleEvent(event: LibraryEvent) {
        when (event) {
            is LibraryEvent.SelectProvider -> {
                AppPreferenceManager.setStringSync(AppPreferenceManager.getLastSyncApiKey(accountId), event.providerId)
                if (event.providerId == currentState.selectedProviderId) return
                if (event.providerId == "local") {
                    val localTabs = computeLocalTabs(currentState.tabCounts)
                    val (filtered, displayed) = computeDisplayedAndFiltered(tab = currentState.selectedTab, tabIndex = 0)
                    updateState {
                        copy(
                            selectedProviderId = "local",
                            selectedTabIndex = 0,
                            currentTabs = localTabs,
                            displayedItems = displayed,
                            remoteLists = emptyList(),
                            syncSortingMethods = emptyList(),
                            error = null
                        )
                    }
                } else {
                    updateState {
                        copy(
                            selectedProviderId = event.providerId,
                            selectedTabIndex = 0,
                            remoteLists = emptyList(),
                            currentTabs = emptyList(),
                            displayedItems = emptyList(),
                            syncSortingMethods = emptyList(),
                            error = null
                        )
                    }
                    fetchRemoteLibrary(event.providerId, force = false)
                }
            }

            is LibraryEvent.SelectTab -> selectTab(status = event.status)

            is LibraryEvent.SelectTabIndex -> selectTab(index = event.index)

            is LibraryEvent.RefreshLibrary -> {
                if (currentState.selectedProviderId != "local") {
                    fetchRemoteLibrary(currentState.selectedProviderId, force = true)
                }
            }

            is LibraryEvent.Search -> {
                val (filtered, displayed) = computeDisplayedAndFiltered(query = event.query)
                updateState {
                    copy(
                        searchQuery = event.query,
                        filteredItems = filtered,
                        displayedItems = displayed
                    )
                }
            }

            is LibraryEvent.SetFilterType -> {
                val (filtered, displayed) = computeDisplayedAndFiltered(type = event.type)
                updateState {
                    copy(
                        selectedType = event.type,
                        filteredItems = filtered,
                        displayedItems = displayed
                    )
                }
            }

            is LibraryEvent.SetSortOrder -> {
                val (filtered, displayed) = computeDisplayedAndFiltered(sort = event.sortOrder)
                updateState {
                    copy(
                        sortOrder = event.sortOrder,
                        filteredItems = filtered,
                        displayedItems = displayed
                    )
                }
            }

            is LibraryEvent.RemoveBookmark -> {
                launch {
                    bookmarkRepository.deleteBookmark(accountId, event.id)
                }
            }

            is LibraryEvent.BatchRemoveBookmarks -> {
                launch {
                    event.ids.forEach { id ->
                        bookmarkRepository.deleteBookmark(accountId, id)
                    }
                }
            }

            is LibraryEvent.BatchSetWatchStatus -> {
                launch {
                    event.ids.forEach { id ->
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
                                    watchType = event.status.id,
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

            is LibraryEvent.ToggleFavorite -> {
                launch {
                    val item = currentState.allItems.find { it.id == event.id }
                    if (item != null) {
                        if (item.isFavorite) {
                            favoriteRepository.deleteFavorite(accountId, event.id)
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

            is LibraryEvent.ClearSearch -> {
                val (filtered, displayed) = computeDisplayedAndFiltered(query = "")
                updateState {
                    copy(
                        searchQuery = "",
                        filteredItems = filtered,
                        displayedItems = displayed
                    )
                }
            }

            is LibraryEvent.ClearFilters -> {
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

            is LibraryEvent.SelectItem -> {
                if (event.item.originalItem is LibraryItem || (event.item.url.isNotBlank() && event.item.apiName.isNotBlank() && event.item.originalItem !is SyncAPI.LibraryItem)) {
                    emitEffect(LibraryEffect.NavigateToDetails(url = event.item.url, apiName = event.item.apiName))
                } else {
                    emitEffect(LibraryEffect.SearchMedia(query = event.item.name))
                }
            }

            is LibraryEvent.DismissError -> {
                updateState { copy(error = null) }
            }
        }
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

    /**
     * Pure filtering and sorting pipeline for library items.
     */
    private fun computeFilteredItems(
        items: List<LibraryItem>,
        tab: WatchStatus,
        query: String,
        type: TvType?,
        sort: SortOrder
    ): List<LibraryItem> {
        val filtered = items.filter { item ->
            (tab == WatchStatus.ALL || item.watchStatus == tab) &&
                matchesQuery(item.name, item.apiName, query) &&
                matchesType(item.type, type)
        }

        return when (sort) {
            SortOrder.LAST_WATCHED -> filtered.sortedWith(
                compareByDescending<LibraryItem> {
                    maxOf(it.lastWatchedTime, it.latestUpdatedTime, it.bookmarkedTime)
                }.thenBy { it.name.lowercase() }
            )
            SortOrder.RECENTLY_ADDED -> filtered.sortedWith(
                compareByDescending<LibraryItem> {
                    maxOf(it.bookmarkedTime, it.latestUpdatedTime)
                }.thenBy { it.name.lowercase() }
            )
            SortOrder.ALPHABETICAL -> filtered.sortedBy { it.name.lowercase() }
        }
    }

    /**
     * Pure filtering and sorting pipeline for remote sync items.
     */
    private fun computeFilteredRemoteItems(
        items: List<UnifiedLibraryItem>,
        query: String,
        type: TvType?,
        sort: SortOrder
    ): List<UnifiedLibraryItem> {
        val filtered = items.filter { item ->
            matchesQuery(item.name, item.apiName, query) &&
                matchesType(item.type, type)
        }

        return when (sort) {
            SortOrder.ALPHABETICAL -> filtered.sortedBy { it.name.lowercase() }
            else -> filtered
        }
    }
}

