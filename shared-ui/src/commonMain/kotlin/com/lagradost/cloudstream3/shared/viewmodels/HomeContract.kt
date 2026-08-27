package com.lagradost.cloudstream3.shared.viewmodels

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.shared.mvi.UiEffect
import com.lagradost.cloudstream3.shared.mvi.UiEvent
import com.lagradost.cloudstream3.shared.mvi.UiState
import com.lagradost.cloudstream3.utils.UiText

/**
 * Represents a content carousel (row/category) displayed on the home screen.
 *
 * @property name Title of the category/carousel.
 * @property items List of [SearchResponse] media cards in this category.
 * @property isHorizontalImages True for 16:9 banner cards, false for 2:3 poster cards.
 * @property currentPage The current loaded page index for pagination.
 * @property hasNext Whether additional items can be loaded for this category.
 * @property isLoadingMore True if a pagination request is currently loading.
 * @property data Optional endpoint/category URL parameter.
 */
data class HomeCarousel(
    val name: String,
    val items: List<SearchResponse> = emptyList(),
    val isHorizontalImages: Boolean = false,
    val currentPage: Int = 1,
    val hasNext: Boolean = false,
    val isLoadingMore: Boolean = false,
    val data: String = ""
)

/**
 * Pure Kotlin Multiplatform UI State for the Home screen.
 *
 * @property carousels Content carousels loaded from the selected provider.
 * @property availableProviders List of registered providers supporting a main page.
 * @property selectedProvider The currently selected provider.
 * @property featuredItems Highlighted/featured items for the top hero banner.
 * @property selectedItem The currently selected media item.
 * @property isLoading True when initial load or provider switch is occurring.
 * @property isRefreshing True when pull-to-refresh is in progress.
 * @property error Error message if loading failed, null otherwise.
 */
data class HomeState(
    val carousels: List<HomeCarousel> = emptyList(),
    val resumeWatching: List<SearchResponse> = emptyList(),
    val resumeWatchingProgress: Map<String, Float> = emptyMap(),
    val availableProviders: List<MainAPI> = emptyList(),
    val selectedProvider: MainAPI? = null,
    val featuredItems: List<SearchResponse> = emptyList(),
    val selectedItem: SearchResponse? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: UiText? = null
) : UiState

/**
 * UI Events / Intents for [HomeViewModel].
 */
sealed interface HomeEvent : UiEvent {
    /**
     * Load or reload home data.
     * @property providerName Optional provider name to switch to.
     * @property forceReload True to ignore any cached results.
     */
    data class LoadHome(
        val providerName: String? = null,
        val forceReload: Boolean = false
    ) : HomeEvent

    /**
     * Select a specific [MainAPI] provider and reload the home screen.
     */
    data class SelectProvider(val provider: MainAPI) : HomeEvent

    /**
     * Select a provider by its name identifier.
     */
    data class SelectProviderByName(val providerName: String) : HomeEvent

    /**
     * Trigger a refresh of the current home content.
     */
    object RefreshHome : HomeEvent

    /**
     * Select a media item to view details.
     */
    data class SelectItem(val item: SearchResponse?) : HomeEvent

    /**
     * Resume watching a media item (opens details and auto-resumes playback).
     */
    data class ResumeItem(val item: SearchResponse) : HomeEvent

    /**
     * Expand / paginate a carousel to load more items.
     */
    data class ExpandCarousel(val carouselName: String) : HomeEvent

    /**
     * Remove an item from resume watching.
     */
    data class RemoveFromResumeWatching(val item: SearchResponse) : HomeEvent

    /**
     * Dismiss current error state.
     */
    object DismissError : HomeEvent
}

/**
 * Side effects emitted by [HomeViewModel].
 */
sealed interface HomeEffect : UiEffect {
    data class NavigateToDetails(val item: SearchResponse, val autoResume: Boolean = false) : HomeEffect
    data class ShowToast(val message: String) : HomeEffect
}
