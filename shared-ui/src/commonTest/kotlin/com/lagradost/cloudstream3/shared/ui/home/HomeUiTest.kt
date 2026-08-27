package com.lagradost.cloudstream3.shared.ui.home

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvSeriesSearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.shared.persistence.entity.BookmarkEntity
import com.lagradost.cloudstream3.shared.persistence.entity.FavoriteEntity
import com.lagradost.cloudstream3.shared.persistence.entity.ResumeWatchingEntity
import com.lagradost.cloudstream3.shared.persistence.entity.WatchProgressEntity
import com.lagradost.cloudstream3.shared.persistence.repository.AppPreferenceManager
import com.lagradost.cloudstream3.shared.persistence.repository.AppPreferenceRepository
import com.lagradost.cloudstream3.shared.persistence.repository.BookmarkRepository
import com.lagradost.cloudstream3.shared.persistence.repository.FavoriteRepository
import com.lagradost.cloudstream3.shared.persistence.repository.ProviderRepository
import com.lagradost.cloudstream3.shared.persistence.repository.ResumeWatchingRepository
import com.lagradost.cloudstream3.shared.persistence.repository.WatchProgressRepository
import com.lagradost.cloudstream3.shared.ui.components.MediaCardDefaults
import com.lagradost.cloudstream3.shared.viewmodels.HomeEffect
import com.lagradost.cloudstream3.shared.viewmodels.HomeEvent
import com.lagradost.cloudstream3.shared.viewmodels.HomeViewModel
import com.lagradost.cloudstream3.shared.viewmodels.result.ResultEvent
import com.lagradost.cloudstream3.shared.viewmodels.result.ResultViewModel
import com.lagradost.cloudstream3.shared.viewmodels.settings.FakeAppPreferenceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
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

class FakeHomeApi(
    override var name: String = "FakeHomeProvider",
    override var mainUrl: String = "https://fakehome.provider",
    override var hasMainPage: Boolean = true,
    override var lang: String = "es",
    override val mainPage: List<MainPageData> = listOf(
        MainPageData(name = "Tendencias", data = "trending", horizontalImages = false),
        MainPageData(name = "Estrenos", data = "recent", horizontalImages = true)
    ),
    private val pagesToReturn: Map<Int, HomePageResponse> = emptyMap()
) : MainAPI() {

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val direct = pagesToReturn[page]
        if (direct != null) {
            val matchingItems = direct.items.filter { it.name.equals(request.name, ignoreCase = true) }
            return if (matchingItems.isNotEmpty()) {
                newHomePageResponse(matchingItems, direct.hasNext)
            } else if (direct.items.size == 1 && direct.items.first().name.isBlank()) {
                direct
            } else {
                direct
            }
        }
        return newHomePageResponse(
            name = request.name,
            list = emptyList(),
            hasNext = false
        )
    }
}

class FakeProviderRepository(
    private var initialProviders: List<MainAPI> = emptyList()
) : ProviderRepository {
    private val providers = initialProviders.toMutableList()
    private val listeners = mutableListOf<() -> Unit>()

    override fun getAllProviders(): List<MainAPI> = providers.toList()

    override fun getApiByName(name: String?): MainAPI? {
        if (name == null) return null
        return providers.firstOrNull { it.name == name }
    }

    override fun getApiByUrl(url: String?): MainAPI? {
        if (url == null) return null
        return providers.firstOrNull { url.startsWith(it.mainUrl) }
    }

    override fun addOnProvidersChangedListener(listener: () -> Unit): () -> Unit {
        listeners.add(listener)
        return {
            listeners.remove(listener)
        }
    }

    override fun getProvidersFlow(): Flow<List<MainAPI>> = flowOf(providers.toList())

    fun updateProviders(newProviders: List<MainAPI>) {
        providers.clear()
        providers.addAll(newProviders)
        listeners.toList().forEach { it.invoke() }
    }
}

class FakeBookmarkRepo : BookmarkRepository {
    private val flow = MutableStateFlow<List<BookmarkEntity>>(emptyList())
    fun setBookmarks(items: List<BookmarkEntity>) { flow.value = items }
    override fun getAllBookmarksFlow(accountId: Int): Flow<List<BookmarkEntity>> = flow
    override suspend fun getAllBookmarks(accountId: Int): List<BookmarkEntity> = flow.value
    override suspend fun getBookmark(accountId: Int, id: Int): BookmarkEntity? = flow.value.find { it.id == id }
    override fun getBookmarkFlow(accountId: Int, id: Int): Flow<BookmarkEntity?> = flowOf(flow.value.find { it.id == id })
    override suspend fun getBookmarksByWatchType(accountId: Int, watchType: Int): List<BookmarkEntity> = flow.value.filter { it.watchType == watchType }
    override fun getBookmarksByWatchTypeFlow(accountId: Int, watchType: Int): Flow<List<BookmarkEntity>> = flowOf(emptyList())
    override suspend fun saveBookmark(bookmark: BookmarkEntity) {
        flow.value = flow.value.filterNot { it.id == bookmark.id } + bookmark
    }
    override suspend fun deleteBookmark(accountId: Int, id: Int) {
        flow.value = flow.value.filterNot { it.id == id }
    }
    override suspend fun clearAll(accountId: Int) { flow.value = emptyList() }
}

class FakeWatchProgressRepo : WatchProgressRepository {
    private val flow = MutableStateFlow<List<WatchProgressEntity>>(emptyList())
    fun setProgresses(items: List<WatchProgressEntity>) { flow.value = items }
    override fun getAllProgressFlow(accountId: Int): Flow<List<WatchProgressEntity>> = flow
    override suspend fun getAllProgress(accountId: Int): List<WatchProgressEntity> = flow.value
    override suspend fun getProgress(accountId: Int, mediaId: Int): WatchProgressEntity? = flow.value.find { it.mediaId == mediaId }
    override fun getProgressFlow(accountId: Int, mediaId: Int): Flow<WatchProgressEntity?> = flowOf(flow.value.find { it.mediaId == mediaId })
    override suspend fun setProgress(accountId: Int, mediaId: Int, position: Long, duration: Long, watchState: Int) {
        val entity = WatchProgressEntity(
            accountId = accountId,
            mediaId = mediaId,
            position = position,
            duration = duration,
            watchState = watchState,
            lastUpdated = 1000L
        )
        flow.value = flow.value.filterNot { it.mediaId == mediaId } + entity
    }
    override suspend fun deleteProgress(accountId: Int, mediaId: Int) {
        flow.value = flow.value.filterNot { it.mediaId == mediaId }
    }
    override suspend fun clearProgress(accountId: Int) { flow.value = emptyList() }
}

class FakeResumeWatchingRepo : ResumeWatchingRepository {
    private val flow = MutableStateFlow<List<ResumeWatchingEntity>>(emptyList())
    fun setResumeWatchings(items: List<ResumeWatchingEntity>) { flow.value = items }
    override fun getAllResumeWatchingFlow(accountId: Int): Flow<List<ResumeWatchingEntity>> = flow
    override suspend fun getAllResumeWatching(accountId: Int): List<ResumeWatchingEntity> = flow.value
    override suspend fun getResumeWatching(accountId: Int, parentId: Int): ResumeWatchingEntity? = flow.value.find { it.parentId == parentId }
    override fun getResumeWatchingFlow(accountId: Int, parentId: Int): Flow<ResumeWatchingEntity?> = flowOf(flow.value.find { it.parentId == parentId })
    override suspend fun setResumeWatching(accountId: Int, parentId: Int, episodeId: Int?, episode: Int?, season: Int?, isFromDownload: Boolean, updateTime: Long?) {
        val entity = ResumeWatchingEntity(accountId, parentId, episodeId, episode, season, isFromDownload, updateTime ?: 1000L)
        flow.value = flow.value.filterNot { it.parentId == parentId } + entity
    }
    override suspend fun saveResumeWatching(resumeWatching: ResumeWatchingEntity) {
        flow.value = flow.value.filterNot { it.parentId == resumeWatching.parentId } + resumeWatching
    }
    override suspend fun deleteResumeWatching(accountId: Int, parentId: Int) {
        flow.value = flow.value.filterNot { it.parentId == parentId }
    }
    override suspend fun clearAll(accountId: Int) { flow.value = emptyList() }
}

class FakeFavoriteRepo : FavoriteRepository {
    private val flow = MutableStateFlow<List<FavoriteEntity>>(emptyList())
    override fun getAllFavoritesFlow(accountId: Int): Flow<List<FavoriteEntity>> = flow
    override suspend fun getAllFavorites(accountId: Int): List<FavoriteEntity> = flow.value
    override suspend fun getFavorite(accountId: Int, id: Int): FavoriteEntity? = flow.value.find { it.id == id }
    override fun getFavoriteFlow(accountId: Int, id: Int): Flow<FavoriteEntity?> = flowOf(flow.value.find { it.id == id })
    override suspend fun saveFavorite(favorite: FavoriteEntity) {
        flow.value = flow.value.filterNot { it.id == favorite.id } + favorite
    }
    override suspend fun deleteFavorite(accountId: Int, id: Int) {
        flow.value = flow.value.filterNot { it.id == id }
    }
    override suspend fun clearAll(accountId: Int) { flow.value = emptyList() }
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeUiTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakePreferenceRepo: FakeAppPreferenceRepository

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakePreferenceRepo = FakeAppPreferenceRepository()
        AppPreferenceManager.init(fakePreferenceRepo)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testHomeViewModelInitialization() = runTest(testDispatcher) {
        val provider = FakeHomeApi("TestProvider")
        val item1 = provider.newMovieSearchResponse("Movie 1", "https://p.com/1") {
            quality = SearchQuality.HD
        }
        val item2 = provider.newTvSeriesSearchResponse("Series 2", "https://p.com/2") {
            quality = SearchQuality.FourK
        }

        val page1Response = newHomePageResponse(
            list = listOf(
                HomePageList(name = "Tendencias", list = listOf(item1), isHorizontalImages = false),
                HomePageList(name = "Estrenos", list = listOf(item2), isHorizontalImages = true)
            ),
            hasNext = true
        )

        val fakeApi = FakeHomeApi(
            name = "TestProvider",
            pagesToReturn = mapOf(1 to page1Response)
        )

        val viewModel = HomeViewModel(
            providersProvider = { listOf(fakeApi) },
            autoLoad = true,
            coroutineContext = testDispatcher
        )

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.currentState
        assertEquals("TestProvider", state.selectedProvider?.name)
        assertEquals(1, state.availableProviders.size)
        assertEquals(2, state.carousels.size)
        assertEquals("Tendencias", state.carousels[0].name)
        assertFalse(state.carousels[0].isHorizontalImages)
        assertEquals("Estrenos", state.carousels[1].name)
        assertTrue(state.carousels[1].isHorizontalImages)
        assertTrue(state.featuredItems.isNotEmpty())
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun testProviderSwitching() = runTest(testDispatcher) {
        val p1 = FakeHomeApi("Provider1")
        val p2 = FakeHomeApi("Provider2")

        val viewModel = HomeViewModel(
            providersProvider = { listOf(p1, p2) },
            autoLoad = false,
            coroutineContext = testDispatcher
        )

        viewModel.handleEvent(HomeEvent.SelectProvider(p2))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Provider2", viewModel.currentState.selectedProvider?.name)

        viewModel.handleEvent(HomeEvent.SelectProviderByName("Provider1"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Provider1", viewModel.currentState.selectedProvider?.name)
    }

    @Test
    fun testRefreshHome() = runTest(testDispatcher) {
        val provider = FakeHomeApi("Provider")
        val item = provider.newMovieSearchResponse("Inception", "https://p.com/inc")
        val pageResponse = newHomePageResponse(
            list = listOf(HomePageList(name = "Populares", list = listOf(item))),
            hasNext = false
        )

        val fakeApi = FakeHomeApi(
            name = "Provider",
            mainPage = listOf(MainPageData(name = "Populares", data = "pop")),
            pagesToReturn = mapOf(1 to pageResponse)
        )
        val viewModel = HomeViewModel(
            providersProvider = { listOf(fakeApi) },
            autoLoad = true,
            coroutineContext = testDispatcher
        )

        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.currentState.carousels.size)

        viewModel.handleEvent(HomeEvent.RefreshHome)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.currentState.isRefreshing)
        assertEquals(1, viewModel.currentState.carousels.size)
        assertEquals("Inception", viewModel.currentState.carousels.first().items.first().name)
    }

    @Test
    fun testCarouselPagination() = runTest(testDispatcher) {
        val provider = FakeHomeApi("Provider")
        val itemPage1 = provider.newMovieSearchResponse("Page 1 Item", "https://p.com/1")
        val itemPage2 = provider.newMovieSearchResponse("Page 2 Item", "https://p.com/2")

        val page1 = newHomePageResponse(
            list = listOf(HomePageList(name = "Tendencias", list = listOf(itemPage1))),
            hasNext = true
        )
        val page2 = newHomePageResponse(
            list = listOf(HomePageList(name = "Tendencias", list = listOf(itemPage2))),
            hasNext = false
        )

        val fakeApi = FakeHomeApi(
            name = "Provider",
            mainPage = listOf(MainPageData(name = "Tendencias", data = "trending")),
            pagesToReturn = mapOf(1 to page1, 2 to page2)
        )

        val viewModel = HomeViewModel(
            providersProvider = { listOf(fakeApi) },
            autoLoad = true,
            coroutineContext = testDispatcher
        )

        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.currentState.carousels.first().items.size)
        assertTrue(viewModel.currentState.carousels.first().hasNext)

        viewModel.handleEvent(HomeEvent.ExpandCarousel("Tendencias"))
        testDispatcher.scheduler.advanceUntilIdle()

        val carousel = viewModel.currentState.carousels.first()
        assertEquals(2, carousel.items.size)
        assertEquals("Page 1 Item", carousel.items[0].name)
        assertEquals("Page 2 Item", carousel.items[1].name)
        assertFalse(carousel.hasNext)
        assertFalse(carousel.isLoadingMore)
    }

    @Test
    fun testSelectItemEmitsEffect() = runTest(testDispatcher) {
        val provider = FakeHomeApi("Provider")
        val item = provider.newAnimeSearchResponse("Attack on Titan", "https://p.com/aot")

        val viewModel = HomeViewModel(
            providersProvider = { listOf(provider) },
            autoLoad = false,
            coroutineContext = testDispatcher
        )

        var receivedEffect: HomeEffect? = null
        val job = launch {
            viewModel.effects.collect { effect ->
                receivedEffect = effect as? HomeEffect.NavigateToDetails
            }
        }

        viewModel.handleEvent(HomeEvent.SelectItem(item))
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(receivedEffect)
        val detailsEffect = receivedEffect as HomeEffect.NavigateToDetails
        assertEquals("Attack on Titan", detailsEffect.item.name)
        assertEquals(false, detailsEffect.autoResume)
        assertEquals(item, viewModel.currentState.selectedItem)

        job.cancel()
    }

    @Test
    fun testResumeItemEmitsEffectWithAutoResume() = runTest(testDispatcher) {
        val provider = FakeHomeApi("Provider")
        val item = provider.newAnimeSearchResponse("Attack on Titan", "https://p.com/aot")

        val viewModel = HomeViewModel(
            providersProvider = { listOf(provider) },
            autoLoad = false,
            coroutineContext = testDispatcher
        )

        var receivedEffect: HomeEffect.NavigateToDetails? = null
        val job = launch {
            viewModel.effects.collect { effect ->
                if (effect is HomeEffect.NavigateToDetails) {
                    receivedEffect = effect
                }
            }
        }

        viewModel.handleEvent(HomeEvent.ResumeItem(item))
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(receivedEffect)
        assertEquals("Attack on Titan", receivedEffect.item.name)
        assertEquals(true, receivedEffect.autoResume)
        assertEquals(item, viewModel.currentState.selectedItem)

        job.cancel()
    }

    @Test
    fun testDismissError() = runTest(testDispatcher) {
        val viewModel = HomeViewModel(
            providersProvider = { emptyList() },
            autoLoad = true,
            coroutineContext = testDispatcher
        )

        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.currentState.error)

        viewModel.handleEvent(HomeEvent.DismissError)
        assertNull(viewModel.currentState.error)
    }

    @Test
    fun testProviderRepositoryIntegrationAndReactiveUpdates() = runTest(testDispatcher) {
        val p1 = FakeHomeApi("ProviderOne")
        val p2 = FakeHomeApi("ProviderTwo")
        val fakeRepo = FakeProviderRepository(listOf(p1))

        val viewModel = HomeViewModel(
            providerRepository = fakeRepo,
            autoLoad = false,
            coroutineContext = testDispatcher
        )

        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.currentState.availableProviders.size)
        assertEquals("ProviderOne", viewModel.currentState.availableProviders.first().name)

        // Reactive update when providers change
        fakeRepo.updateProviders(listOf(p1, p2))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, viewModel.currentState.availableProviders.size)
        assertEquals(listOf("ProviderOne", "ProviderTwo"), viewModel.currentState.availableProviders.map { it.name })
    }

    @Test
    fun testResumeWatchingGeneratesLegitimateSearchResponses() = runTest(testDispatcher) {
        val bookmarkRepo = FakeBookmarkRepo()
        val watchProgressRepo = FakeWatchProgressRepo()

        val bookmarkMovie = BookmarkEntity(
            accountId = 0,
            id = 101,
            name = "Interstellar",
            url = "https://prov.com/interstellar",
            apiName = "TestApi",
            type = TvType.Movie,
            posterUrl = "https://poster.com/interstellar.jpg",
            year = 2014,
            quality = SearchQuality.HD
        )

        val bookmarkSeries = BookmarkEntity(
            accountId = 0,
            id = 102,
            name = "Breaking Bad",
            url = "https://prov.com/bb",
            apiName = "TestApi",
            type = TvType.TvSeries,
            posterUrl = "https://poster.com/bb.jpg",
            year = 2008,
            quality = SearchQuality.FourK
        )

        val bookmarkAnime = BookmarkEntity(
            accountId = 0,
            id = 103,
            name = "Frieren",
            url = "https://prov.com/frieren",
            apiName = "TestApi",
            type = TvType.Anime,
            posterUrl = "https://poster.com/frieren.jpg",
            year = 2023,
            quality = SearchQuality.HD
        )

        val progress1 = WatchProgressEntity(
            accountId = 0,
            mediaId = 101,
            position = 3000L,
            duration = 6000L,
            watchState = 1,
            lastUpdated = 1000L
        )

        val progress2 = WatchProgressEntity(
            accountId = 0,
            mediaId = 102,
            position = 2000L,
            duration = 4000L,
            watchState = 1,
            lastUpdated = 2000L
        )

        val progress3 = WatchProgressEntity(
            accountId = 0,
            mediaId = 103,
            position = 1000L,
            duration = 5000L,
            watchState = 1,
            lastUpdated = 3000L
        )

        bookmarkRepo.setBookmarks(listOf(bookmarkMovie, bookmarkSeries, bookmarkAnime))
        watchProgressRepo.setProgresses(listOf(progress1, progress2, progress3))

        val viewModel = HomeViewModel(
            providersProvider = { emptyList() },
            bookmarkRepository = bookmarkRepo,
            watchProgressRepository = watchProgressRepo,
            autoLoad = false,
            coroutineContext = testDispatcher
        )

        testDispatcher.scheduler.advanceUntilIdle()

        val resumeItems = viewModel.currentState.resumeWatching
        assertEquals(3, resumeItems.size)

        // Sorted by latest updated descending: 103 (Frieren), 102 (Breaking Bad), 101 (Interstellar)
        val animeItem = resumeItems[0]
        assertTrue(animeItem is AnimeSearchResponse)
        assertEquals("Frieren", animeItem.name)
        assertEquals("TestApi", animeItem.apiName)
        assertEquals(103, animeItem.id)
        assertEquals(TvType.Anime, animeItem.type)

        val seriesItem = resumeItems[1]
        assertTrue(seriesItem is TvSeriesSearchResponse)
        assertEquals("Breaking Bad", seriesItem.name)
        assertEquals("TestApi", seriesItem.apiName)
        assertEquals(102, seriesItem.id)
        assertEquals(TvType.TvSeries, seriesItem.type)

        val movieItem = resumeItems[2]
        assertTrue(movieItem is MovieSearchResponse)
        assertEquals("Interstellar", movieItem.name)
        assertEquals("TestApi", movieItem.apiName)
        assertEquals(101, movieItem.id)
        assertEquals(TvType.Movie, movieItem.type)

        // Verify progress percentage mapping
        val progressMap = viewModel.currentState.resumeWatchingProgress
        assertEquals(0.5f, progressMap["https://prov.com/interstellar"])
        assertEquals(0.5f, progressMap["https://prov.com/bb"])
        assertEquals(0.2f, progressMap["https://prov.com/frieren"])
    }

    @Test
    fun testSavedProviderRestoredOnInitialization() = runTest(testDispatcher) {
        val p1 = FakeHomeApi("ProviderOne")
        val p2 = FakeHomeApi("ProviderTwo")

        fakePreferenceRepo.setStringSync(AppPreferenceManager.KEY_HOME_API_USED, "ProviderTwo")

        val viewModel = HomeViewModel(
            providersProvider = { listOf(p1, p2) },
            preferenceRepository = fakePreferenceRepo,
            autoLoad = false,
            coroutineContext = testDispatcher
        )

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("ProviderTwo", viewModel.currentState.selectedProvider?.name)
    }

    @Test
    fun testProviderSelectionPersistsToPreferences() = runTest(testDispatcher) {
        val p1 = FakeHomeApi("ProviderOne")
        val p2 = FakeHomeApi("ProviderTwo")

        val viewModel = HomeViewModel(
            providersProvider = { listOf(p1, p2) },
            preferenceRepository = fakePreferenceRepo,
            autoLoad = false,
            coroutineContext = testDispatcher
        )

        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("ProviderOne", viewModel.currentState.selectedProvider?.name)

        viewModel.handleEvent(HomeEvent.SelectProvider(p2))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("ProviderTwo", viewModel.currentState.selectedProvider?.name)
        assertEquals("ProviderTwo", fakePreferenceRepo.getStringSync(AppPreferenceManager.KEY_HOME_API_USED))

        viewModel.handleEvent(HomeEvent.SelectProviderByName("ProviderOne"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("ProviderOne", viewModel.currentState.selectedProvider?.name)
        assertEquals("ProviderOne", fakePreferenceRepo.getStringSync(AppPreferenceManager.KEY_HOME_API_USED))
    }

    @Test
    fun testResumeWatchingPreservedAcrossProviderSwitchAndErrors() = runTest(testDispatcher) {
        val p1 = FakeHomeApi("ProviderOne")
        val p2 = FakeHomeApi("ProviderTwo", hasMainPage = false)
        val bookmarkRepo = FakeBookmarkRepo()
        val watchProgressRepo = FakeWatchProgressRepo()

        val bookmark = BookmarkEntity(
            accountId = 0,
            id = 201,
            name = "Stranger Things",
            url = "https://prov.com/st",
            apiName = "TestApi",
            type = TvType.TvSeries,
            posterUrl = "https://poster.com/st.jpg",
            year = 2016,
            quality = SearchQuality.FourK
        )
        val progress = WatchProgressEntity(
            accountId = 0,
            mediaId = 201,
            position = 1800L,
            duration = 3600L,
            watchState = 1,
            lastUpdated = 5000L
        )

        bookmarkRepo.setBookmarks(listOf(bookmark))
        watchProgressRepo.setProgresses(listOf(progress))

        val viewModel = HomeViewModel(
            providersProvider = { listOf(p1, p2) },
            bookmarkRepository = bookmarkRepo,
            watchProgressRepository = watchProgressRepo,
            autoLoad = true,
            coroutineContext = testDispatcher
        )

        testDispatcher.scheduler.advanceUntilIdle()

        // Resume watching is populated
        assertEquals(1, viewModel.currentState.resumeWatching.size)
        assertEquals("Stranger Things", viewModel.currentState.resumeWatching.first().name)

        // Switch to provider 2 that has error (no main page)
        viewModel.handleEvent(HomeEvent.SelectProvider(p2))
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.currentState.error)
        assertTrue(viewModel.currentState.carousels.isEmpty())
        // Resume watching MUST remain intact
        assertEquals(1, viewModel.currentState.resumeWatching.size)
        assertEquals("Stranger Things", viewModel.currentState.resumeWatching.first().name)
        assertEquals(0.5f, viewModel.currentState.resumeWatchingProgress["https://prov.com/st"])
    }

    @Test
    fun testRemoveFromResumeWatchingEvent() = runTest(testDispatcher) {
        val bookmarkRepo = FakeBookmarkRepo()
        val watchProgressRepo = FakeWatchProgressRepo()

        val bookmark = BookmarkEntity(
            accountId = 0,
            id = 301,
            name = "The Matrix",
            url = "https://prov.com/matrix",
            apiName = "TestApi",
            type = TvType.Movie,
            posterUrl = "https://poster.com/matrix.jpg",
            year = 1999,
            quality = SearchQuality.HD
        )
        val progress = WatchProgressEntity(
            accountId = 0,
            mediaId = 301,
            position = 2000L,
            duration = 4000L,
            watchState = 1,
            lastUpdated = 6000L
        )

        bookmarkRepo.setBookmarks(listOf(bookmark))
        watchProgressRepo.setProgresses(listOf(progress))

        val viewModel = HomeViewModel(
            providersProvider = { emptyList() },
            bookmarkRepository = bookmarkRepo,
            watchProgressRepository = watchProgressRepo,
            autoLoad = false,
            coroutineContext = testDispatcher
        )

        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.currentState.resumeWatching.size)

        val itemToRemove = viewModel.currentState.resumeWatching.first()
        viewModel.handleEvent(HomeEvent.RemoveFromResumeWatching(itemToRemove))
        testDispatcher.scheduler.advanceUntilIdle()

        // Progress deleted -> resumeWatching is updated to empty reactively
        assertEquals(0, viewModel.currentState.resumeWatching.size)
    }

    @Test
    fun testResumeWatchingWithSeriesEpisodeFromResumeWatchingRepo() = runTest(testDispatcher) {
        val bookmarkRepo = FakeBookmarkRepo()
        val watchProgressRepo = FakeWatchProgressRepo()
        val resumeRepo = FakeResumeWatchingRepo()

        val seriesBookmark = BookmarkEntity(
            accountId = 0,
            id = 500,
            name = "One Piece",
            url = "https://prov.com/op",
            apiName = "TestApi",
            type = TvType.Anime,
            posterUrl = "https://poster.com/op.jpg",
            year = 1999,
            quality = SearchQuality.HD
        )

        // Episode ID is 500_001
        val episodeProgress = WatchProgressEntity(
            accountId = 0,
            mediaId = 500_001,
            position = 1200L,
            duration = 2400L,
            watchState = 1,
            lastUpdated = 9000L
        )

        val resumeEntity = ResumeWatchingEntity(
            accountId = 0,
            parentId = 500,
            episodeId = 500_001,
            episode = 1000,
            season = 1,
            updateTime = 9000L
        )

        bookmarkRepo.setBookmarks(listOf(seriesBookmark))
        watchProgressRepo.setProgresses(listOf(episodeProgress))
        resumeRepo.setResumeWatchings(listOf(resumeEntity))

        val viewModel = HomeViewModel(
            providersProvider = { emptyList() },
            bookmarkRepository = bookmarkRepo,
            watchProgressRepository = watchProgressRepo,
            resumeWatchingRepository = resumeRepo,
            autoLoad = false,
            coroutineContext = testDispatcher
        )

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.currentState.resumeWatching.size)
        assertEquals("One Piece", viewModel.currentState.resumeWatching.first().name)
        assertEquals(0.5f, viewModel.currentState.resumeWatchingProgress["https://prov.com/op"])

        // Remove from resume watching
        viewModel.handleEvent(HomeEvent.RemoveFromResumeWatching(viewModel.currentState.resumeWatching.first()))
        testDispatcher.scheduler.advanceUntilIdle()

        // Both resume watching and episode progress cleared
        assertNull(resumeRepo.getResumeWatching(0, 500))
        assertNull(watchProgressRepo.getProgress(0, 500_001))
        assertEquals(0, viewModel.currentState.resumeWatching.size)
    }

    @Test
    fun testResumeWatchingExactProgressSortingAndNavigation() = runTest(testDispatcher) {
        val bookmarkRepo = FakeBookmarkRepo()
        val watchProgressRepo = FakeWatchProgressRepo()
        val resumeRepo = FakeResumeWatchingRepo()

        val movie1 = BookmarkEntity(
            accountId = 0,
            id = 1001,
            name = "Movie Alpha",
            url = "https://prov.com/alpha",
            apiName = "TestApi",
            type = TvType.Movie,
            bookmarkedTime = 1000L,
            latestUpdatedTime = 1500L
        )
        val movie2 = BookmarkEntity(
            accountId = 0,
            id = 1002,
            name = "Movie Beta",
            url = "https://prov.com/beta",
            apiName = "TestApi",
            type = TvType.Movie,
            bookmarkedTime = 2000L,
            latestUpdatedTime = 2500L
        )

        // Alpha: 120s / 240s = 0.5f, lastUpdated = 1500L
        val progress1 = WatchProgressEntity(
            accountId = 0,
            mediaId = 1001,
            position = 120_000L,
            duration = 240_000L,
            watchState = 1,
            lastUpdated = 1500L
        )

        // Beta: 90s / 120s = 0.75f, lastUpdated = 5000L
        val progress2 = WatchProgressEntity(
            accountId = 0,
            mediaId = 1002,
            position = 90_000L,
            duration = 120_000L,
            watchState = 1,
            lastUpdated = 5000L
        )

        bookmarkRepo.setBookmarks(listOf(movie1, movie2))
        watchProgressRepo.setProgresses(listOf(progress1, progress2))

        val viewModel = HomeViewModel(
            providersProvider = { emptyList() },
            bookmarkRepository = bookmarkRepo,
            watchProgressRepository = watchProgressRepo,
            resumeWatchingRepository = resumeRepo,
            autoLoad = false,
            coroutineContext = testDispatcher
        )

        testDispatcher.scheduler.advanceUntilIdle()

        val resumeItems = viewModel.currentState.resumeWatching
        assertEquals(2, resumeItems.size)

        // Most recently updated should be first (Movie Beta at 5000L > Movie Alpha at 1500L)
        assertEquals("Movie Beta", resumeItems[0].name)
        assertEquals("Movie Alpha", resumeItems[1].name)

        // Exact progress ratios
        val progressMap = viewModel.currentState.resumeWatchingProgress
        assertEquals(0.75f, progressMap["https://prov.com/beta"])
        assertEquals(0.5f, progressMap["https://prov.com/alpha"])

        // Clicking an item emits NavigateToDetails effect
        var navigatedItem: SearchResponse? = null
        val job = launch {
            viewModel.effects.collect { effect ->
                if (effect is HomeEffect.NavigateToDetails) {
                    navigatedItem = effect.item
                }
            }
        }

        viewModel.handleEvent(HomeEvent.SelectItem(resumeItems[0]))
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(navigatedItem)
        assertEquals("Movie Beta", navigatedItem.name)
        assertEquals("https://prov.com/beta", navigatedItem.url)

        job.cancel()
    }

    @Test
    fun testContinueWatchingAppearsImmediatelyWhenContentIsLoadedAndWatched() = runTest(testDispatcher) {
        val bookmarkRepo = FakeBookmarkRepo()
        val watchProgressRepo = FakeWatchProgressRepo()
        val resumeRepo = FakeResumeWatchingRepo()
        val favoriteRepo = FakeFavoriteRepo()

        val homeProvider = FakeHomeApi("TestHomeProvider")
        val mediaProvider = object : MainAPI() {
            override var name = "TestMediaProvider"
            override var mainUrl = "https://media.test"
            override suspend fun load(url: String) = newTvSeriesLoadResponse(
                name = "Cyberpunk Series",
                url = url,
                type = TvType.TvSeries,
                episodes = listOf(
                    newEpisode("https://media.test/e1") {
                        name = "Episode 1"
                        season = 1
                        episode = 1
                    }
                )
            ) {
                posterUrl = "https://poster.test/cyberpunk.jpg"
                year = 2024
            }
        }

        APIHolder.allProviders.withLock {
            if (!APIHolder.allProviders.contains(mediaProvider)) {
                APIHolder.allProviders.add(mediaProvider)
            }
        }
        APIHolder.addPluginMapping(mediaProvider)

        val homeViewModel = HomeViewModel(
            providersProvider = { listOf(homeProvider) },
            bookmarkRepository = bookmarkRepo,
            watchProgressRepository = watchProgressRepo,
            resumeWatchingRepository = resumeRepo,
            autoLoad = false,
            coroutineContext = testDispatcher
        )

        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, homeViewModel.currentState.resumeWatching.size)

        val resultViewModel = ResultViewModel(
            bookmarkRepository = bookmarkRepo,
            watchProgressRepository = watchProgressRepo,
            favoriteRepository = favoriteRepo,
            resumeWatchingRepository = resumeRepo,
            coroutineContext = testDispatcher
        )

        // 1. Load details - BookmarkEntity is immediately persisted with watchType = 0 and full metadata
        resultViewModel.onEvent(ResultEvent.LoadResult("https://media.test/series/1", "TestMediaProvider"))
        testDispatcher.scheduler.advanceUntilIdle()

        val mediaId = resultViewModel.state.value.mediaId!!
        val persistedBookmark = bookmarkRepo.getBookmark(0, mediaId)
        assertNotNull(persistedBookmark)
        assertEquals(0, persistedBookmark.watchType)
        assertEquals("Cyberpunk Series", persistedBookmark.name)
        assertEquals("https://poster.test/cyberpunk.jpg", persistedBookmark.posterUrl)

        // 2. Play episode 1 to 40%
        val episode = resultViewModel.state.value.episodes.first()
        resultViewModel.onEvent(
            ResultEvent.UpdateWatchProgress(
                episodeId = episode.id,
                position = 40_000L,
                duration = 100_000L,
                watchState = 1
            )
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // 3. Verify Continue Watching in Home immediately displays the item and progress!
        val resumeItems = homeViewModel.currentState.resumeWatching
        assertEquals(1, resumeItems.size)
        val resumeItem = resumeItems.first()
        assertEquals("Cyberpunk Series", resumeItem.name)
        assertEquals("https://media.test/series/1", resumeItem.url)
        assertEquals("https://poster.test/cyberpunk.jpg", resumeItem.posterUrl)
        assertEquals(0.4f, homeViewModel.currentState.resumeWatchingProgress["https://media.test/series/1"])

        // 4. Mark episode 1 as completed (watchState = 2) -> Row immediately removes item
        resultViewModel.onEvent(ResultEvent.SetWatchState(episode.id, 2))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, homeViewModel.currentState.resumeWatching.size)
    }

    @Test
    fun testResumeWatchingFiltersOutThresholdsAndCompleted() = runTest(testDispatcher) {
        val bookmarkRepo = FakeBookmarkRepo()
        val watchProgressRepo = FakeWatchProgressRepo()
        val resumeRepo = FakeResumeWatchingRepo()

        val itemBelowThreshold = BookmarkEntity(
            accountId = 0,
            id = 701,
            name = "Below Threshold",
            url = "https://test.com/below",
            apiName = "TestApi",
            type = TvType.Movie,
            latestUpdatedTime = 1000L
        )
        val itemAboveThreshold = BookmarkEntity(
            accountId = 0,
            id = 702,
            name = "Above Threshold",
            url = "https://test.com/above",
            apiName = "TestApi",
            type = TvType.Movie,
            latestUpdatedTime = 2000L
        )
        val itemCompleted = BookmarkEntity(
            accountId = 0,
            id = 703,
            name = "Completed",
            url = "https://test.com/completed",
            apiName = "TestApi",
            type = TvType.Movie,
            latestUpdatedTime = 3000L
        )
        val itemValid = BookmarkEntity(
            accountId = 0,
            id = 704,
            name = "Valid Watch",
            url = "https://test.com/valid",
            apiName = "TestApi",
            type = TvType.Movie,
            latestUpdatedTime = 4000L
        )

        // Below threshold: 2s / 1000s = 0.002f (< 0.005f)
        val progressBelow = WatchProgressEntity(0, 701, position = 2L, duration = 1000L, watchState = 1, lastUpdated = 1000L)
        // Above threshold: 960s / 1000s = 0.96f (> 0.95f)
        val progressAbove = WatchProgressEntity(0, 702, position = 960L, duration = 1000L, watchState = 1, lastUpdated = 2000L)
        // Completed: watchState = 2
        val progressCompleted = WatchProgressEntity(0, 703, position = 500L, duration = 1000L, watchState = 2, lastUpdated = 3000L)
        // Valid: 500s / 1000s = 0.5f
        val progressValid = WatchProgressEntity(0, 704, position = 500L, duration = 1000L, watchState = 1, lastUpdated = 4000L)

        bookmarkRepo.setBookmarks(listOf(itemBelowThreshold, itemAboveThreshold, itemCompleted, itemValid))
        watchProgressRepo.setProgresses(listOf(progressBelow, progressAbove, progressCompleted, progressValid))

        val homeViewModel = HomeViewModel(
            providersProvider = { emptyList() },
            bookmarkRepository = bookmarkRepo,
            watchProgressRepository = watchProgressRepo,
            resumeWatchingRepository = resumeRepo,
            autoLoad = false,
            coroutineContext = testDispatcher
        )

        testDispatcher.scheduler.advanceUntilIdle()

        val resumeItems = homeViewModel.currentState.resumeWatching
        assertEquals(1, resumeItems.size)
        assertEquals("Valid Watch", resumeItems.first().name)
        assertEquals(0.5f, homeViewModel.currentState.resumeWatchingProgress["https://test.com/valid"])
    }
}
