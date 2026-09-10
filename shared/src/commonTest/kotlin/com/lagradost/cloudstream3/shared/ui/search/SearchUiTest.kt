package com.lagradost.cloudstream3.shared.ui.search

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newSearchResponseList
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.shared.persistence.repository.AppPreferenceManager
import com.lagradost.cloudstream3.shared.viewmodels.SearchEvent
import com.lagradost.cloudstream3.shared.viewmodels.SearchFilters
import com.lagradost.cloudstream3.shared.viewmodels.SearchState
import com.lagradost.cloudstream3.shared.viewmodels.SearchViewModel
import com.lagradost.cloudstream3.shared.viewmodels.settings.FakeAppPreferenceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakeSearchApi(
    override var name: String = "FakeProvider",
    override var mainUrl: String = "https://fake.provider",
    private val resultsToReturn: List<SearchResponse> = emptyList(),
    override var hasQuickSearch: Boolean = true
) : MainAPI() {
    var searchCallCount = 0
    var quickSearchCallCount = 0

    override suspend fun search(query: String, page: Int): SearchResponseList {
        searchCallCount++
        return newSearchResponseList(resultsToReturn, false)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> {
        quickSearchCallCount++
        return resultsToReturn
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SearchUiTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        AppPreferenceManager.init(FakeAppPreferenceRepository())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testSearchDisplayModes() {
        val unified = SearchDisplayMode.Unified
        val grouped = SearchDisplayMode.Grouped
        assertEquals("Unified", unified.name)
        assertEquals("Grouped", grouped.name)
    }

    @Test
    fun testSearchViewModelInitialState() {
        val provider1 = FakeSearchApi("Provider1")
        val provider2 = FakeSearchApi("Provider2")

        val viewModel = SearchViewModel(
            providersProvider = { listOf(provider1, provider2) },
            coroutineContext = testDispatcher
        )

        val state = viewModel.currentState
        assertEquals("", state.query)
        assertTrue(state.results.isEmpty())
        assertTrue(state.groupedResults.isEmpty())
        assertFalse(state.isLoading)
        assertFalse(state.isPaginating)
        assertFalse(state.hasNextPage)
        assertEquals(2, state.availableProviders.size)
        assertTrue(state.availableTypes.contains(TvType.Movie))
        assertTrue(state.availableQualities.contains(SearchQuality.HD))
    }

    @Test
    fun testSearchExecutionAndResultsInterleaving() = runTest(testDispatcher) {
        val provider1 = FakeSearchApi("Provider1")
        val provider2 = FakeSearchApi("Provider2")

        val item1 = provider1.newMovieSearchResponse("Batman Begins", "https://prov1.com/batman1") {
            this.quality = SearchQuality.HD
        }
        val item2 = provider1.newMovieSearchResponse("The Dark Knight", "https://prov1.com/batman2") {
            this.quality = SearchQuality.FourK
        }
        val item3 = provider2.newTvSeriesSearchResponse("Batman TAS", "https://prov2.com/batman-tas") {
            this.quality = SearchQuality.HQ
        }

        val p1 = FakeSearchApi("Provider1", resultsToReturn = listOf(item1, item2))
        val p2 = FakeSearchApi("Provider2", resultsToReturn = listOf(item3))

        val viewModel = SearchViewModel(
            providersProvider = { listOf(p1, p2) },
            coroutineContext = testDispatcher
        )

        viewModel.handleEvent(SearchEvent.Search("Batman"))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.currentState
        assertEquals("Batman", state.query)
        assertEquals(3, state.results.size)
        // Check interleaving: first item from p1, then first item from p2, then second item from p1
        assertEquals("Batman Begins", state.results[0].name)
        assertEquals("Batman TAS", state.results[1].name)
        assertEquals("The Dark Knight", state.results[2].name)

        assertEquals(2, state.groupedResults.size)
        assertEquals(2, state.groupedResults["Provider1"]?.size)
        assertEquals(1, state.groupedResults["Provider2"]?.size)
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun testFilterTogglingAndApplication() = runTest(testDispatcher) {
        val provider = FakeSearchApi("TestProvider")
        val movie = provider.newMovieSearchResponse("Movie A", "https://p.com/a") {
            type = TvType.Movie
            quality = SearchQuality.FourK
        }
        val series = provider.newTvSeriesSearchResponse("Series B", "https://p.com/b") {
            type = TvType.TvSeries
            quality = SearchQuality.HD
        }

        val testApi = FakeSearchApi("TestProvider", resultsToReturn = listOf(movie, series))
        val viewModel = SearchViewModel(
            providersProvider = { listOf(testApi) },
            coroutineContext = testDispatcher
        )

        viewModel.handleEvent(SearchEvent.Search("test"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, viewModel.currentState.results.size)

        // Filter to Movie only
        viewModel.handleEvent(SearchEvent.ToggleTypeFilter(TvType.Movie))
        assertEquals(1, viewModel.currentState.results.size)
        assertEquals("Movie A", viewModel.currentState.results.first().name)

        // Filter to HD only (Movie A is 4K, so should be empty)
        viewModel.handleEvent(SearchEvent.ToggleQualityFilter(SearchQuality.HD))
        assertEquals(0, viewModel.currentState.results.size)

        // Clear all filters
        viewModel.handleEvent(SearchEvent.ClearFilters)
        assertEquals(2, viewModel.currentState.results.size)
    }

    @Test
    fun testClearSearchEvent() = runTest(testDispatcher) {
        val provider = FakeSearchApi("Provider")
        val item = provider.newMovieSearchResponse("Movie", "https://p.com/1")
        val testApi = FakeSearchApi("Provider", resultsToReturn = listOf(item))

        val viewModel = SearchViewModel(
            providersProvider = { listOf(testApi) },
            coroutineContext = testDispatcher
        )

        viewModel.handleEvent(SearchEvent.Search("query"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.currentState.results.size)

        viewModel.handleEvent(SearchEvent.ClearSearch)
        val state = viewModel.currentState
        assertEquals("", state.query)
        assertTrue(state.results.isEmpty())
        assertTrue(state.groupedResults.isEmpty())
        assertFalse(state.isLoading)
    }
}
