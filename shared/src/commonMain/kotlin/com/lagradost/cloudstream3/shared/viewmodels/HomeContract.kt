package com.lagradost.cloudstream3.shared.viewmodels

import androidx.compose.runtime.Immutable
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.shared.mvi.UiEffect
import com.lagradost.cloudstream3.shared.mvi.UiEvent
import com.lagradost.cloudstream3.shared.mvi.UiState
import com.lagradost.cloudstream3.utils.UiText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

@Immutable
data class HomeCarousel(
    val name: String,
    val items: ImmutableList<SearchResponse> = persistentListOf(),
    val isHorizontalImages: Boolean = false,
    val currentPage: Int = 1,
    val hasNext: Boolean = false,
    val isLoadingMore: Boolean = false,
    val data: String = ""
)

@Immutable
data class HomeState(
    val carousels: ImmutableList<HomeCarousel> = persistentListOf(),
    val resumeWatching: ImmutableList<SearchResponse> = persistentListOf(),
    val resumeWatchingProgress: ImmutableMap<String, Float> = persistentMapOf(),
    val availableProviders: ImmutableList<MainAPI> = persistentListOf(),
    val selectedProvider: MainAPI? = null,
    val featuredItems: ImmutableList<SearchResponse> = persistentListOf(),
    val selectedItem: SearchResponse? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: UiText? = null
) : UiState

sealed interface HomeEvent : UiEvent {
    data class LoadHome(
        val providerName: String? = null,
        val forceReload: Boolean = false
    ) : HomeEvent

    data class SelectProvider(val provider: MainAPI) : HomeEvent
    data class SelectProviderByName(val providerName: String) : HomeEvent
    object RefreshHome : HomeEvent
    data class SelectItem(val item: SearchResponse?) : HomeEvent
    data class ResumeItem(val item: SearchResponse) : HomeEvent
    data class ExpandCarousel(val carouselName: String) : HomeEvent
    data class RemoveFromResumeWatching(val item: SearchResponse) : HomeEvent
    object DismissError : HomeEvent
}

sealed interface HomeEffect : UiEffect {
    data class NavigateToDetails(val item: SearchResponse, val autoResume: Boolean = false) : HomeEffect
    data class ShowToast(val message: String) : HomeEffect
}
