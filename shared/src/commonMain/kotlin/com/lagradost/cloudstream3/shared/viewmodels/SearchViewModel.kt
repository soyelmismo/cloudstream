package com.lagradost.cloudstream3.shared.viewmodels

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.newSearchResponseList
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.shared.mvi.MviViewModel
import com.lagradost.cloudstream3.shared.persistence.repository.AppPreferenceManager
import com.lagradost.cloudstream3.shared.persistence.repository.AppPreferenceRepository
import com.lagradost.cloudstream4.generated.resources.Res
import com.lagradost.cloudstream4.generated.resources.search_error_failed
import com.lagradost.cloudstream4.generated.resources.search_error_no_providers
import com.lagradost.cloudstream3.shared.ui.search.SearchDisplayMode
import com.lagradost.cloudstream3.utils.txt
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class SearchViewModel(
    private val providersProvider: () -> List<MainAPI> = {
        val apisList = APIHolder.apis.withLock { APIHolder.apis.toList() }
        val allList = APIHolder.allProviders.withLock { APIHolder.allProviders.toList() }
        (apisList.ifEmpty { allList }).distinctBy { it.name }
    },
    private val preferenceRepository: AppPreferenceRepository = AppPreferenceManager.currentRepository,
    initialState: SearchState = SearchState(),
    coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.Default
) : MviViewModel<SearchState, SearchEvent>(initialState, coroutineContext) {

    private val rawProviderResults = mutableMapOf<String, List<SearchResponse>>()
    private val providerPagination = mutableMapOf<String, ProviderSearchPagination>()

    init {
        initialize()
        APIHolder.onProvidersChanged.add {
            initialize()
        }
    }

    fun initialize() {
        val providers = providersProvider().toImmutableList()
        val allTypes = TvType.entries.toImmutableSet()
        val allQualities = SearchQuality.entries.toImmutableSet()

        val savedProviders = preferenceRepository.getStringSetSync(AppPreferenceManager.KEY_SEARCH_SELECTED_PROVIDERS, emptySet())?.toImmutableSet() ?: persistentSetOf()
        val savedTypes = preferenceRepository.getStringSetSync(AppPreferenceManager.KEY_SEARCH_SELECTED_TYPES, emptySet())?.mapNotNull { runCatching { TvType.valueOf(it) }.getOrNull() }?.toImmutableSet() ?: persistentSetOf()
        val savedQualities = preferenceRepository.getStringSetSync(AppPreferenceManager.KEY_SEARCH_SELECTED_QUALITIES, emptySet())?.mapNotNull { runCatching { SearchQuality.valueOf(it) }.getOrNull() }?.toImmutableSet() ?: persistentSetOf()
        val savedModeStr = preferenceRepository.getStringSync(AppPreferenceManager.KEY_SEARCH_DISPLAY_MODE, SearchDisplayMode.Unified.name) ?: SearchDisplayMode.Unified.name
        val savedMode = runCatching { SearchDisplayMode.valueOf(savedModeStr) }.getOrDefault(SearchDisplayMode.Unified)

        updateState {
            copy(
                availableProviders = providers,
                availableTypes = allTypes,
                availableQualities = allQualities,
                displayMode = savedMode,
                activeFilters = activeFilters.copy(
                    selectedProviders = savedProviders,
                    selectedTypes = savedTypes,
                    selectedQualities = savedQualities
                )
            )
        }
    }

    private fun persistFilters(newFilters: SearchFilters) {
        preferenceRepository.setStringSetSync(AppPreferenceManager.KEY_SEARCH_SELECTED_PROVIDERS, newFilters.selectedProviders)
        preferenceRepository.setStringSetSync(AppPreferenceManager.KEY_SEARCH_SELECTED_TYPES, newFilters.selectedTypes.map { it.name }.toSet())
        preferenceRepository.setStringSetSync(AppPreferenceManager.KEY_SEARCH_SELECTED_QUALITIES, newFilters.selectedQualities.map { it.name }.toSet())
    }

    private fun <T> toggleFilter(currentSet: ImmutableSet<T>, item: T): ImmutableSet<T> =
        if (currentSet.contains(item)) (currentSet - item).toImmutableSet() else (currentSet + item).toImmutableSet()

    override fun handleEvent(event: SearchEvent) {
        when (event) {
            is SearchEvent.SetFilter,
            is SearchEvent.ToggleProviderFilter,
            is SearchEvent.ToggleTypeFilter,
            is SearchEvent.ToggleQualityFilter,
            is SearchEvent.ClearFilters -> handleFilterEvent(event)

            is SearchEvent.RemoveHistoryItem,
            is SearchEvent.ClearHistory -> handleHistoryEvent(event)

            is SearchEvent.SetDisplayMode,
            is SearchEvent.Search,
            is SearchEvent.ClearSearch,
            is SearchEvent.LoadNextPage,
            is SearchEvent.ExpandProviderSearch,
            is SearchEvent.SelectItem,
            is SearchEvent.DismissError -> handleQueryEvent(event)
        }
    }

    private fun handleFilterEvent(event: SearchEvent) {
        val newFilters = when (event) {
            is SearchEvent.SetFilter -> event.filter
            is SearchEvent.ToggleProviderFilter -> currentState.activeFilters.copy(
                selectedProviders = toggleFilter(currentState.activeFilters.selectedProviders, event.providerName)
            )
            is SearchEvent.ToggleTypeFilter -> currentState.activeFilters.copy(
                selectedTypes = toggleFilter(currentState.activeFilters.selectedTypes, event.type)
            )
            is SearchEvent.ToggleQualityFilter -> currentState.activeFilters.copy(
                selectedQualities = toggleFilter(currentState.activeFilters.selectedQualities, event.quality)
            )
            is SearchEvent.ClearFilters -> SearchFilters()
            else -> return
        }
        updateState { copy(activeFilters = newFilters) }
        persistFilters(newFilters)
        applyCurrentFilters()
    }

    private fun handleHistoryEvent(event: SearchEvent) {
        when (event) {
            is SearchEvent.RemoveHistoryItem -> updateState {
                copy(searchHistory = (searchHistory - event.query).toImmutableList())
            }
            is SearchEvent.ClearHistory -> updateState {
                copy(searchHistory = persistentListOf())
            }
            else -> Unit
        }
    }

    private fun handleQueryEvent(event: SearchEvent) {
        when (event) {
            is SearchEvent.SetDisplayMode -> {
                updateState { copy(displayMode = event.mode) }
                preferenceRepository.setStringSync(AppPreferenceManager.KEY_SEARCH_DISPLAY_MODE, event.mode.name)
            }
            is SearchEvent.Search -> executeSearch(query = event.query, isQuickSearch = event.isQuickSearch)
            is SearchEvent.ClearSearch -> resetSearchSession()
            is SearchEvent.LoadNextPage -> loadNextPage()
            is SearchEvent.ExpandProviderSearch -> expandProviderSearch(event.providerName)
            is SearchEvent.SelectItem -> {
                updateState { copy(selectedItem = event.item) }
                if (event.item != null) emitEffect(SearchEffect.NavigateToDetails(event.item))
            }
            is SearchEvent.DismissError -> updateState { copy(error = null) }
            else -> Unit
        }
    }

    private fun resetSearchSession() {
        cancelJob("search")
        cancelJob("pagination")
        rawProviderResults.clear()
        providerPagination.clear()
        updateState {
            copy(
                query = "",
                results = persistentListOf(),
                groupedResults = persistentMapOf(),
                isLoading = false,
                isPaginating = false,
                hasNextPage = false,
                currentPage = 1,
                error = null
            )
        }
    }

    private fun resolveCandidateProviders(isQuickSearch: Boolean): List<MainAPI> {
        val allProviders = currentState.availableProviders.ifEmpty { providersProvider().toImmutableList() }
        val selectedNames = currentState.activeFilters.selectedProviders
        return allProviders.filter { provider ->
            val matchesSelection = selectedNames.isEmpty() || provider.name in selectedNames
            val matchesQuickSearch = !isQuickSearch || provider.hasQuickSearch
            matchesSelection && matchesQuickSearch
        }
    }

    private suspend fun fetchSingleProviderSearch(
        provider: MainAPI,
        query: String,
        isQuickSearch: Boolean
    ): Triple<String, List<SearchResponse>, Boolean> = try {
        val response: SearchResponseList? = if (isQuickSearch) {
            val list = provider.quickSearch(query)
            list?.let { newSearchResponseList(it, false) }
        } else {
            provider.search(query, 1)
        }
        Triple(provider.name, response?.items ?: emptyList(), response?.hasNext ?: false)
    } catch (e: Throwable) {
        Triple(provider.name, emptyList(), false)
    }

    private fun executeSearch(query: String, isQuickSearch: Boolean) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) {
            handleEvent(SearchEvent.ClearSearch)
            return
        }

        val newHistory = (listOf(trimmedQuery) + currentState.searchHistory).distinct().take(20).toImmutableList()
        updateState { copy(searchHistory = newHistory) }

        cancelJob("pagination")

        launchSafeJob(
            key = "search",
            onError = { e ->
                updateState {
                    copy(
                        isLoading = false,
                        error = e.message?.let { txt(it) } ?: txt(Res.string.search_error_failed)
                    )
                }
            }
        ) job@{
            updateState {
                copy(
                    query = trimmedQuery,
                    isLoading = true,
                    isPaginating = false,
                    error = null,
                    currentPage = 1
                )
            }

            val candidateProviders = resolveCandidateProviders(isQuickSearch)
            if (candidateProviders.isEmpty()) {
                rawProviderResults.clear()
                providerPagination.clear()
                updateState {
                    copy(
                        results = persistentListOf(),
                        groupedResults = persistentMapOf(),
                        isLoading = false,
                        hasNextPage = false,
                        error = txt(Res.string.search_error_no_providers)
                    )
                }
                return@job
            }

            rawProviderResults.clear()
            providerPagination.clear()

            val fetched = coroutineScope {
                candidateProviders.map { provider ->
                    async { fetchSingleProviderSearch(provider, trimmedQuery, isQuickSearch) }
                }.awaitAll()
            }

            for ((providerName, items, hasNext) in fetched) {
                if (items.isNotEmpty()) {
                    rawProviderResults[providerName] = items
                }
                providerPagination[providerName] = ProviderSearchPagination(
                    providerName = providerName,
                    currentPage = 1,
                    hasNext = hasNext
                )
            }

            val hasMore = providerPagination.values.any { it.hasNext }
            val filteredGrouped = filterResults(rawProviderResults, currentState.activeFilters)
            val bundled = bundleSearchResults(filteredGrouped)

            updateState {
                copy(
                    results = bundled,
                    groupedResults = filteredGrouped,
                    isLoading = false,
                    hasNextPage = hasMore,
                    currentPage = 1,
                    error = if (bundled.isEmpty()) txt("No results found for '$trimmedQuery'") else null
                )
            }
        }
    }

    private fun loadNextPage() {
        if (currentState.isLoading || currentState.isPaginating || !currentState.hasNextPage) return

        val query = currentState.query
        if (query.isBlank()) return

        launchSafeJob(
            key = "pagination",
            onError = {
                updateState { copy(isPaginating = false) }
            }
        ) job@{
            updateState { copy(isPaginating = true) }

            val providersToPaginate = currentState.availableProviders.filter { provider ->
                providerPagination[provider.name]?.hasNext == true
            }

            if (providersToPaginate.isEmpty()) {
                updateState { copy(isPaginating = false, hasNextPage = false) }
                return@job
            }

            val nextPage = currentState.currentPage + 1

            val fetched = coroutineScope {
                providersToPaginate.map { provider ->
                    val currentPagination = providerPagination[provider.name] ?: ProviderSearchPagination(provider.name)
                    val targetPage = currentPagination.currentPage + 1
                    async {
                        try {
                            val response = provider.search(query, targetPage)
                            Triple(provider.name, targetPage, response)
                        } catch (e: Throwable) {
                            Triple(provider.name, targetPage, null)
                        }
                    }
                }.awaitAll()
            }

            for ((providerName, targetPage, response) in fetched) {
                val newItems = response?.items ?: emptyList()
                val hasNext = response?.hasNext ?: false
                val existing = rawProviderResults[providerName] ?: emptyList()
                rawProviderResults[providerName] = (existing + newItems).distinctBy { it.url }
                providerPagination[providerName] = ProviderSearchPagination(
                    providerName = providerName,
                    currentPage = targetPage,
                    hasNext = hasNext
                )
            }

            val hasMore = providerPagination.values.any { it.hasNext }
            val filteredGrouped = filterResults(rawProviderResults, currentState.activeFilters)
            val bundled = bundleSearchResults(filteredGrouped)

            updateState {
                copy(
                    results = bundled,
                    groupedResults = filteredGrouped,
                    isPaginating = false,
                    hasNextPage = hasMore,
                    currentPage = nextPage
                )
            }
        }
    }

    private fun expandProviderSearch(providerName: String) {
        val currentPagination = providerPagination[providerName] ?: return
        if (!currentPagination.hasNext) return

        val provider = currentState.availableProviders.find { it.name == providerName } ?: return
        val query = currentState.query
        if (query.isBlank()) return

        launchSafeJob(
            key = "expand_provider_$providerName"
        ) {
            val nextPage = currentPagination.currentPage + 1
            try {
                val response = provider.search(query, nextPage)
                val newItems = response?.items ?: emptyList()
                val hasNext = response?.hasNext ?: false
                val existing = rawProviderResults[providerName] ?: emptyList()

                rawProviderResults[providerName] = (existing + newItems).distinctBy { it.url }
                providerPagination[providerName] = ProviderSearchPagination(
                    providerName = providerName,
                    currentPage = nextPage,
                    hasNext = hasNext
                )

                applyCurrentFilters()
            } catch (_: Throwable) {
                // Keep existing results intact on single provider pagination failure
            }
        }
    }

    private fun applyCurrentFilters() {
        val filteredGrouped = filterResults(rawProviderResults, currentState.activeFilters)
        val bundled = bundleSearchResults(filteredGrouped)
        val hasMore = providerPagination.values.any { it.hasNext }

        updateState {
            copy(
                results = bundled,
                groupedResults = filteredGrouped,
                hasNextPage = hasMore,
                error = if (bundled.isEmpty() && query.isNotBlank()) txt("No results matching current filters") else null
            )
        }
    }

    private fun SearchFilters.matches(item: SearchResponse): Boolean {
        if (hideNsfw && item.type == TvType.NSFW) return false
        if (selectedTypes.isNotEmpty() && item.type != null && item.type !in selectedTypes) return false
        if (selectedQualities.isNotEmpty() && item.quality != null && item.quality !in selectedQualities) return false
        return true
    }

    private fun filterResults(
        raw: Map<String, List<SearchResponse>>,
        filters: SearchFilters
    ): ImmutableMap<String, ImmutableList<SearchResponse>> {
        val filtered = mutableMapOf<String, ImmutableList<SearchResponse>>()

        for ((providerName, items) in raw) {
            if (filters.selectedProviders.isNotEmpty() && providerName !in filters.selectedProviders) {
                continue
            }

            val matchingItems = items.filter { filters.matches(it) }
            if (matchingItems.isNotEmpty()) {
                filtered[providerName] = matchingItems.toImmutableList()
            }
        }

        return filtered.toImmutableMap()
    }

    private fun bundleSearchResults(grouped: Map<String, List<SearchResponse>>): ImmutableList<SearchResponse> {
        val lists = grouped.values.toList()
        if (lists.isEmpty()) return persistentListOf()
        if (lists.size == 1) return lists.first().toImmutableList()

        val bundled = mutableListOf<SearchResponse>()
        val seenUrls = mutableSetOf<String>()
        var index = 0
        var addedAny: Boolean

        do {
            addedAny = false
            for (list in lists) {
                if (index < list.size) {
                    val item = list[index]
                    if (seenUrls.add(item.url)) {
                        bundled.add(item)
                    }
                    addedAny = true
                }
            }
            index++
        } while (addedAny)

        return bundled.toImmutableList()
    }
}
