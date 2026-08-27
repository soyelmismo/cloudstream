package com.lagradost.cloudstream3.shared.ui.search

import com.lagradost.cloudstream3.utils.asString
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.shared.viewmodels.SearchEffect
import com.lagradost.cloudstream3.shared.viewmodels.SearchEvent
import com.lagradost.cloudstream3.shared.viewmodels.SearchState
import com.lagradost.cloudstream3.shared.viewmodels.SearchViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Stateful reactive Search Screen connected directly to [SearchViewModel].
 *
 * @param viewModel The SearchViewModel handling business logic and MVI state.
 * @param onNavigateToDetails Callback when a search result item is selected.
 * @param modifier Modifier applied to the screen root.
 */
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    modifier: Modifier = Modifier,
    onNavigateToDetails: (SearchResponse) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val scaffoldState = rememberScaffoldState()
    val scope = rememberCoroutineScope()

    // Handle side effects emitted by the ViewModel
    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is SearchEffect.NavigateToDetails -> {
                    onNavigateToDetails(effect.item)
                }

                is SearchEffect.ShowToast -> {
                    scope.launch {
                        scaffoldState.snackbarHostState.showSnackbar(effect.message)
                    }
                }
            }
        }
    }

    SearchScreenContent(
        state = state,
        onEvent = viewModel::handleEvent,
        onNavigateToDetails = onNavigateToDetails,
        modifier = modifier
    )
}

/**
 * Stateless Search Screen content composable for testing, previews, and decoupled UI composition.
 *
 * @param state Immutable UI state for search.
 * @param onEvent Callback for dispatching MVI [SearchEvent] intents.
 * @param onNavigateToDetails Callback when a search response item is clicked.
 * @param modifier Modifier applied to the root container.
 */
@Composable
fun SearchScreenContent(
    state: SearchState,
    onEvent: (SearchEvent) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToDetails: (SearchResponse) -> Unit = {}
) {
    Scaffold(
        backgroundColor = MaterialTheme.colors.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Top Controls Section (Search Bar & Filters Bar)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colors.surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // 1. Search Bar View
                SearchBarView(
                    query = state.query,
                    onQueryChange = { /* debounced auto search handles querying */ },
                    onSearch = { queryText, isQuick ->
                        onEvent(SearchEvent.Search(query = queryText, isQuickSearch = isQuick))
                    },
                    onClear = {
                        onEvent(SearchEvent.ClearSearch)
                    },
                    displayMode = state.displayMode,
                    onDisplayModeChange = { newMode ->
                        onEvent(SearchEvent.SetDisplayMode(newMode))
                    },
                    isLoading = state.isLoading
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 2. Search Filters Bar
                SearchFiltersBar(
                    filters = state.activeFilters,
                    availableProviders = state.availableProviders,
                    availableTypes = state.availableTypes,
                    availableQualities = state.availableQualities,
                    onToggleProvider = { providerName ->
                        onEvent(SearchEvent.ToggleProviderFilter(providerName))
                    },
                    onToggleType = { tvType ->
                        onEvent(SearchEvent.ToggleTypeFilter(tvType))
                    },
                    onToggleQuality = { quality ->
                        onEvent(SearchEvent.ToggleQualityFilter(quality))
                    },
                    onToggleNsfw = { hideNsfw ->
                        onEvent(SearchEvent.SetFilter(state.activeFilters.copy(hideNsfw = hideNsfw)))
                    },
                    onClearFilters = {
                        onEvent(SearchEvent.ClearFilters)
                    }
                )
            }

            // 3. Search Results Presentation Grid / Grouped List
            SearchResultsGrid(
                results = state.results,
                groupedResults = state.groupedResults,
                displayMode = state.displayMode,
                isLoading = state.isLoading,
                isPaginating = state.isPaginating,
                hasNextPage = state.hasNextPage,
                error = state.error?.asString(),
                query = state.query,
                onLoadNextPage = {
                    onEvent(SearchEvent.LoadNextPage)
                },
                onExpandProvider = { providerName ->
                    onEvent(SearchEvent.ExpandProviderSearch(providerName))
                },
                onItemClick = { item ->
                    onEvent(SearchEvent.SelectItem(item))
                    onNavigateToDetails(item)
                },
                onRetry = {
                    if (state.query.isNotBlank()) {
                        onEvent(SearchEvent.Search(query = state.query, isQuickSearch = false))
                    }
                },
                onDismissError = {
                    onEvent(SearchEvent.DismissError)
                },
                searchHistory = state.searchHistory,
                onSelectHistoryQuery = { historyQuery ->
                    onEvent(SearchEvent.Search(query = historyQuery, isQuickSearch = false))
                },
                onRemoveHistoryQuery = { historyQuery ->
                    onEvent(SearchEvent.RemoveHistoryItem(historyQuery))
                },
                onClearHistory = {
                    onEvent(SearchEvent.ClearHistory)
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
