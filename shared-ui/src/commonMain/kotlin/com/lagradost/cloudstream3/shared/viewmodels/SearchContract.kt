package com.lagradost.cloudstream3.shared.viewmodels

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.shared.mvi.UiEffect
import com.lagradost.cloudstream3.shared.mvi.UiEvent
import com.lagradost.cloudstream3.shared.mvi.UiState
import com.lagradost.cloudstream3.shared.ui.search.SearchDisplayMode
import com.lagradost.cloudstream3.utils.UiText

/**
 * Filter configuration for search queries.
 *
 * @property selectedProviders Set of provider names to restrict searches to. If empty, all available providers are searched.
 * @property selectedTypes Set of [TvType] media categories to include (e.g. Movies, Series, Anime). If empty, all types are allowed.
 * @property selectedTags Set of genre/category tags to filter by.
 * @property selectedQualities Set of media qualities (e.g. HD, 4K) to filter by.
 * @property hideNsfw True to automatically exclude adult/NSFW content.
 */
data class SearchFilters(
    val selectedProviders: Set<String> = emptySet(),
    val selectedTypes: Set<TvType> = emptySet(),
    val selectedTags: Set<String> = emptySet(),
    val selectedQualities: Set<SearchQuality> = emptySet(),
    val hideNsfw: Boolean = false
)

/**
 * Tracks pagination progress per provider.
 */
data class ProviderSearchPagination(
    val providerName: String,
    val currentPage: Int = 1,
    val hasNext: Boolean = false
)

/**
 * Pure Kotlin Multiplatform UI State for Search.
 *
 * @property query The current search text.
 * @property results Interleaved/bundled consolidated list of [SearchResponse] results matching active filters.
 * @property groupedResults Search results grouped by provider name.
 * @property activeFilters Active filter criteria.
 * @property availableProviders All registered [MainAPI] providers available for searching.
 * @property availableTypes All available [TvType] options for filter UI chips.
 * @property availableQualities All available [SearchQuality] options.
 * @property isLoading True when initial search is executing.
 * @property isPaginating True when fetching the next page of results.
 * @property hasNextPage True if at least one provider has more results to fetch.
 * @property currentPage The maximum page reached across providers.
 * @property error Error message if search failed, null otherwise.
 * @property selectedItem The currently selected search response item.
 * @property searchHistory Local history of recent searches.
 * @property displayMode Visual layout mode for presenting search results (Unified vs Grouped).
 */
data class SearchState(
    val query: String = "",
    val results: List<SearchResponse> = emptyList(),
    val groupedResults: Map<String, List<SearchResponse>> = emptyMap(),
    val activeFilters: SearchFilters = SearchFilters(),
    val availableProviders: List<MainAPI> = emptyList(),
    val availableTypes: Set<TvType> = emptySet(),
    val availableQualities: Set<SearchQuality> = emptySet(),
    val isLoading: Boolean = false,
    val isPaginating: Boolean = false,
    val hasNextPage: Boolean = false,
    val currentPage: Int = 1,
    val error: UiText? = null,
    val selectedItem: SearchResponse? = null,
    val searchHistory: List<String> = emptyList(),
    val displayMode: SearchDisplayMode = SearchDisplayMode.Unified
) : UiState

/**
 * UI Events / Intents handled by [SearchViewModel].
 */
sealed interface SearchEvent : UiEvent {
    /**
     * Executes a search with the given query text.
     * @property query Search term.
     * @property isQuickSearch True to use quick search if supported by the provider.
     */
    data class Search(
        val query: String,
        val isQuickSearch: Boolean = false
    ) : SearchEvent

    /**
     * Updates the search display presentation mode (Unified vs Grouped).
     */
    data class SetDisplayMode(val mode: SearchDisplayMode) : SearchEvent

    /**
     * Sets or updates the active search filters.
     */
    data class SetFilter(val filter: SearchFilters) : SearchEvent

    /**
     * Toggles inclusion of a provider in the search filters.
     */
    data class ToggleProviderFilter(val providerName: String) : SearchEvent

    /**
     * Toggles inclusion of a media type filter (e.g. Movie, Anime).
     */
    data class ToggleTypeFilter(val type: TvType) : SearchEvent

    /**
     * Toggles inclusion of a quality filter (e.g. HD, 4K).
     */
    data class ToggleQualityFilter(val quality: SearchQuality) : SearchEvent

    /**
     * Clears all active filters.
     */
    object ClearFilters : SearchEvent

    /**
     * Clears current query, results, and resets pagination.
     */
    object ClearSearch : SearchEvent

    /**
     * Removes a single query term from search history.
     */
    data class RemoveHistoryItem(val query: String) : SearchEvent

    /**
     * Clears entire local search history.
     */
    object ClearHistory : SearchEvent

    /**
     * Fetches the next page of results across providers that have more data.
     */
    object LoadNextPage : SearchEvent

    /**
     * Expands/paginates search results for a specific provider.
     */
    data class ExpandProviderSearch(val providerName: String) : SearchEvent

    /**
     * Selects a search response item.
     */
    data class SelectItem(val item: SearchResponse?) : SearchEvent

    /**
     * Dismisses the current error banner.
     */
    object DismissError : SearchEvent
}

/**
 * Side effects emitted by [SearchViewModel].
 */
sealed interface SearchEffect : UiEffect {
    data class NavigateToDetails(val item: SearchResponse) : SearchEffect
    data class ShowToast(val message: String) : SearchEffect
}
