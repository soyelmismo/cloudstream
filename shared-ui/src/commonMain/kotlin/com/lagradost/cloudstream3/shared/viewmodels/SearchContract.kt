package com.lagradost.cloudstream3.shared.viewmodels

import androidx.compose.runtime.Immutable
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.shared.mvi.UiEffect
import com.lagradost.cloudstream3.shared.mvi.UiEvent
import com.lagradost.cloudstream3.shared.mvi.UiState
import com.lagradost.cloudstream3.shared.ui.search.SearchDisplayMode
import com.lagradost.cloudstream3.utils.UiText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf

@Immutable
data class SearchFilters(
    val selectedProviders: ImmutableSet<String> = persistentSetOf(),
    val selectedTypes: ImmutableSet<TvType> = persistentSetOf(),
    val selectedTags: ImmutableSet<String> = persistentSetOf(),
    val selectedQualities: ImmutableSet<SearchQuality> = persistentSetOf(),
    val hideNsfw: Boolean = false
)

@Immutable
data class ProviderSearchPagination(
    val providerName: String,
    val currentPage: Int = 1,
    val hasNext: Boolean = false
)

@Immutable
data class SearchState(
    val query: String = "",
    val results: ImmutableList<SearchResponse> = persistentListOf(),
    val groupedResults: ImmutableMap<String, ImmutableList<SearchResponse>> = persistentMapOf(),
    val activeFilters: SearchFilters = SearchFilters(),
    val availableProviders: ImmutableList<MainAPI> = persistentListOf(),
    val availableTypes: ImmutableSet<TvType> = persistentSetOf(),
    val availableQualities: ImmutableSet<SearchQuality> = persistentSetOf(),
    val isLoading: Boolean = false,
    val isPaginating: Boolean = false,
    val hasNextPage: Boolean = false,
    val currentPage: Int = 1,
    val error: UiText? = null,
    val selectedItem: SearchResponse? = null,
    val searchHistory: ImmutableList<String> = persistentListOf(),
    val displayMode: SearchDisplayMode = SearchDisplayMode.Unified
) : UiState

sealed interface SearchEvent : UiEvent {
    data class Search(
        val query: String,
        val isQuickSearch: Boolean = false
    ) : SearchEvent

    data class SetDisplayMode(val mode: SearchDisplayMode) : SearchEvent
    data class SetFilter(val filter: SearchFilters) : SearchEvent
    data class ToggleProviderFilter(val providerName: String) : SearchEvent
    data class ToggleTypeFilter(val type: TvType) : SearchEvent
    data class ToggleQualityFilter(val quality: SearchQuality) : SearchEvent
    object ClearFilters : SearchEvent
    object ClearSearch : SearchEvent
    data class RemoveHistoryItem(val query: String) : SearchEvent
    object ClearHistory : SearchEvent
    object LoadNextPage : SearchEvent
    data class ExpandProviderSearch(val providerName: String) : SearchEvent
    data class SelectItem(val item: SearchResponse?) : SearchEvent
    object DismissError : SearchEvent
}

sealed interface SearchEffect : UiEffect {
    data class NavigateToDetails(val item: SearchResponse) : SearchEffect
    data class ShowToast(val message: String) : SearchEffect
}
