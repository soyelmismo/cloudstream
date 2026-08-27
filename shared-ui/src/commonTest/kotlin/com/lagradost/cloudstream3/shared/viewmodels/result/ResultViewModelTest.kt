package com.lagradost.cloudstream3.shared.viewmodels.result

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TrailerData
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.newAnimeLoadResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.shared.persistence.entity.BookmarkEntity
import com.lagradost.cloudstream3.shared.persistence.entity.FavoriteEntity
import com.lagradost.cloudstream3.shared.persistence.entity.ResumeWatchingEntity
import com.lagradost.cloudstream3.shared.persistence.entity.SubscriptionEntity
import com.lagradost.cloudstream3.shared.persistence.entity.SyncMappingEntity
import com.lagradost.cloudstream3.shared.persistence.entity.WatchProgressEntity
import com.lagradost.cloudstream3.shared.persistence.repository.BookmarkRepository
import com.lagradost.cloudstream3.shared.persistence.repository.FavoriteRepository
import com.lagradost.cloudstream3.shared.persistence.repository.ResumeWatchingRepository
import com.lagradost.cloudstream3.shared.persistence.repository.SubscriptionRepository
import com.lagradost.cloudstream3.shared.persistence.repository.SyncMappingRepository
import com.lagradost.cloudstream3.shared.persistence.repository.WatchProgressRepository
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// -----------------------------------------------------------------------------
// Test Fakes
// -----------------------------------------------------------------------------

class FakeSyncMappingRepository : SyncMappingRepository {
    private val data = mutableMapOf<Triple<Int, Int, String>, SyncMappingEntity>()
    private val flows = mutableMapOf<Pair<Int, Int>, MutableStateFlow<List<SyncMappingEntity>>>()

    override suspend fun getSyncMappings(accountId: Int, mediaId: Int): List<SyncMappingEntity> {
        return data.filterKeys { it.first == accountId && it.second == mediaId }.values.toList()
    }

    override fun getSyncMappingsFlow(accountId: Int, mediaId: Int): Flow<List<SyncMappingEntity>> {
        return flows.getOrPut(accountId to mediaId) {
            MutableStateFlow(getSyncMappingsDirect(accountId, mediaId))
        }
    }

    private fun getSyncMappingsDirect(accountId: Int, mediaId: Int): List<SyncMappingEntity> {
        return data.filterKeys { it.first == accountId && it.second == mediaId }.values.toList()
    }

    override suspend fun getSyncMapping(accountId: Int, mediaId: Int, syncPrefix: String): SyncMappingEntity? {
        return data[Triple(accountId, mediaId, syncPrefix)]
    }

    override suspend fun saveSyncMapping(mapping: SyncMappingEntity) {
        val key = Triple(mapping.accountId, mapping.mediaId, mapping.syncPrefix)
        data[key] = mapping
        flows[mapping.accountId to mapping.mediaId]?.value = getSyncMappingsDirect(mapping.accountId, mapping.mediaId)
    }

    override suspend fun deleteSyncMapping(accountId: Int, mediaId: Int, syncPrefix: String) {
        val key = Triple(accountId, mediaId, syncPrefix)
        data.remove(key)
        flows[accountId to mediaId]?.value = getSyncMappingsDirect(accountId, mediaId)
    }

    override suspend fun clearSyncMappings(accountId: Int, mediaId: Int) {
        data.keys.filter { it.first == accountId && it.second == mediaId }.forEach { data.remove(it) }
        flows[accountId to mediaId]?.value = emptyList()
    }
}

class FakeBookmarkRepository : BookmarkRepository {
    private val data = mutableMapOf<Pair<Int, Int>, BookmarkEntity>()
    private val flows = mutableMapOf<Pair<Int, Int>, MutableStateFlow<BookmarkEntity?>>()
    private val allFlow = MutableStateFlow<List<BookmarkEntity>>(emptyList())

    override suspend fun getBookmark(accountId: Int, id: Int): BookmarkEntity? = data[accountId to id]

    override fun getBookmarkFlow(accountId: Int, id: Int): Flow<BookmarkEntity?> {
        return flows.getOrPut(accountId to id) { MutableStateFlow(data[accountId to id]) }
    }

    override suspend fun getAllBookmarks(accountId: Int): List<BookmarkEntity> {
        return data.filterKeys { it.first == accountId }.values.toList()
    }

    override fun getAllBookmarksFlow(accountId: Int): Flow<List<BookmarkEntity>> = allFlow

    override suspend fun getBookmarksByWatchType(accountId: Int, watchType: Int): List<BookmarkEntity> {
        return data.filterKeys { it.first == accountId }.values.filter { it.watchType == watchType }
    }

    override fun getBookmarksByWatchTypeFlow(accountId: Int, watchType: Int): Flow<List<BookmarkEntity>> {
        return allFlow.map { list -> list.filter { it.accountId == accountId && it.watchType == watchType } }
    }

    override suspend fun saveBookmark(bookmark: BookmarkEntity) {
        val key = bookmark.accountId to bookmark.id
        data[key] = bookmark
        flows.getOrPut(key) { MutableStateFlow(null) }.value = bookmark
        allFlow.value = data.values.toList()
    }

    override suspend fun deleteBookmark(accountId: Int, id: Int) {
        val key = accountId to id
        data.remove(key)
        flows.getOrPut(key) { MutableStateFlow(null) }.value = null
        allFlow.value = data.values.toList()
    }

    override suspend fun clearAll(accountId: Int) {
        data.clear()
        flows.values.forEach { it.value = null }
        allFlow.value = emptyList()
    }
}

class FakeFavoriteRepository : FavoriteRepository {
    private val data = mutableMapOf<Pair<Int, Int>, FavoriteEntity>()
    private val flows = mutableMapOf<Pair<Int, Int>, MutableStateFlow<FavoriteEntity?>>()
    private val allFlow = MutableStateFlow<List<FavoriteEntity>>(emptyList())

    override suspend fun getFavorite(accountId: Int, id: Int): FavoriteEntity? = data[accountId to id]

    override fun getFavoriteFlow(accountId: Int, id: Int): Flow<FavoriteEntity?> {
        return flows.getOrPut(accountId to id) { MutableStateFlow(data[accountId to id]) }
    }

    override suspend fun getAllFavorites(accountId: Int): List<FavoriteEntity> {
        return data.filterKeys { it.first == accountId }.values.toList()
    }

    override fun getAllFavoritesFlow(accountId: Int): Flow<List<FavoriteEntity>> = allFlow

    override suspend fun saveFavorite(favorite: FavoriteEntity) {
        val key = favorite.accountId to favorite.id
        data[key] = favorite
        flows.getOrPut(key) { MutableStateFlow(null) }.value = favorite
        allFlow.value = data.values.toList()
    }

    override suspend fun deleteFavorite(accountId: Int, id: Int) {
        val key = accountId to id
        data.remove(key)
        flows.getOrPut(key) { MutableStateFlow(null) }.value = null
        allFlow.value = data.values.toList()
    }

    override suspend fun clearAll(accountId: Int) {
        data.clear()
        flows.values.forEach { it.value = null }
        allFlow.value = emptyList()
    }
}

class FakeSubscriptionRepository : SubscriptionRepository {
    private val data = mutableMapOf<Pair<Int, Int>, SubscriptionEntity>()
    private val flows = mutableMapOf<Pair<Int, Int>, MutableStateFlow<SubscriptionEntity?>>()
    private val allFlow = MutableStateFlow<List<SubscriptionEntity>>(emptyList())

    override suspend fun getSubscription(accountId: Int, id: Int): SubscriptionEntity? = data[accountId to id]

    override fun getSubscriptionFlow(accountId: Int, id: Int): Flow<SubscriptionEntity?> {
        return flows.getOrPut(accountId to id) { MutableStateFlow(data[accountId to id]) }
    }

    override suspend fun getAllSubscriptions(accountId: Int): List<SubscriptionEntity> {
        return data.filterKeys { it.first == accountId }.values.toList()
    }

    override fun getAllSubscriptionsFlow(accountId: Int): Flow<List<SubscriptionEntity>> = allFlow

    override suspend fun saveSubscription(subscription: SubscriptionEntity) {
        val key = subscription.accountId to subscription.id
        data[key] = subscription
        flows.getOrPut(key) { MutableStateFlow(null) }.value = subscription
        allFlow.value = data.values.toList()
    }

    override suspend fun deleteSubscription(accountId: Int, id: Int) {
        val key = accountId to id
        data.remove(key)
        flows.getOrPut(key) { MutableStateFlow(null) }.value = null
        allFlow.value = data.values.toList()
    }

    override suspend fun clearAll(accountId: Int) {
        data.clear()
        flows.values.forEach { it.value = null }
        allFlow.value = emptyList()
    }
}

class FakeWatchProgressRepository : WatchProgressRepository {
    private val data = mutableMapOf<Pair<Int, Int>, WatchProgressEntity>()
    private val flows = mutableMapOf<Pair<Int, Int>, MutableStateFlow<WatchProgressEntity?>>()

    private val allFlow = MutableStateFlow<List<WatchProgressEntity>>(emptyList())

    override suspend fun getProgress(accountId: Int, mediaId: Int): WatchProgressEntity? = data[accountId to mediaId]

    override fun getProgressFlow(accountId: Int, mediaId: Int): Flow<WatchProgressEntity?> {
        return flows.getOrPut(accountId to mediaId) { MutableStateFlow(data[accountId to mediaId]) }
    }

    override suspend fun getAllProgress(accountId: Int): List<WatchProgressEntity> {
        return data.filterKeys { it.first == accountId }.values.toList()
    }

    override fun getAllProgressFlow(accountId: Int): Flow<List<WatchProgressEntity>> = allFlow

    override suspend fun setProgress(
        accountId: Int,
        mediaId: Int,
        position: Long,
        duration: Long,
        watchState: Int
    ) {
        val entity = WatchProgressEntity(
            accountId = accountId,
            mediaId = mediaId,
            position = position,
            duration = duration,
            watchState = watchState,
            lastUpdated = 1000L
        )
        val key = accountId to mediaId
        data[key] = entity
        flows.getOrPut(key) { MutableStateFlow(null) }.value = entity
    }

    override suspend fun deleteProgress(accountId: Int, mediaId: Int) {
        val key = accountId to mediaId
        data.remove(key)
        flows.getOrPut(key) { MutableStateFlow(null) }.value = null
    }

    override suspend fun clearProgress(accountId: Int) {
        data.clear()
        flows.values.forEach { it.value = null }
    }
}

class FakeResumeWatchingRepository : ResumeWatchingRepository {
    private val data = mutableMapOf<Pair<Int, Int>, ResumeWatchingEntity>()
    private val flows = mutableMapOf<Pair<Int, Int>, MutableStateFlow<ResumeWatchingEntity?>>()
    private val allFlow = MutableStateFlow<List<ResumeWatchingEntity>>(emptyList())

    override suspend fun getResumeWatching(accountId: Int, parentId: Int): ResumeWatchingEntity? = data[accountId to parentId]

    override fun getResumeWatchingFlow(accountId: Int, parentId: Int): Flow<ResumeWatchingEntity?> {
        return flows.getOrPut(accountId to parentId) { MutableStateFlow(data[accountId to parentId]) }
    }

    override suspend fun getAllResumeWatching(accountId: Int): List<ResumeWatchingEntity> {
        return data.filterKeys { it.first == accountId }.values.toList()
    }

    override fun getAllResumeWatchingFlow(accountId: Int): Flow<List<ResumeWatchingEntity>> = allFlow

    override suspend fun setResumeWatching(
        accountId: Int,
        parentId: Int,
        episodeId: Int?,
        episode: Int?,
        season: Int?,
        isFromDownload: Boolean,
        updateTime: Long?
    ) {
        val entity = ResumeWatchingEntity(
            accountId = accountId,
            parentId = parentId,
            episodeId = episodeId,
            episode = episode,
            season = season,
            isFromDownload = isFromDownload,
            updateTime = updateTime ?: 1000L
        )
        val key = accountId to parentId
        data[key] = entity
        flows.getOrPut(key) { MutableStateFlow(null) }.value = entity
        allFlow.value = data.values.toList()
    }

    override suspend fun saveResumeWatching(resumeWatching: ResumeWatchingEntity) {
        val key = resumeWatching.accountId to resumeWatching.parentId
        data[key] = resumeWatching
        flows.getOrPut(key) { MutableStateFlow(null) }.value = resumeWatching
        allFlow.value = data.values.toList()
    }

    override suspend fun deleteResumeWatching(accountId: Int, parentId: Int) {
        val key = accountId to parentId
        data.remove(key)
        flows.getOrPut(key) { MutableStateFlow(null) }.value = null
        allFlow.value = data.values.toList()
    }

    override suspend fun clearAll(accountId: Int) {
        data.clear()
        flows.values.forEach { it.value = null }
        allFlow.value = emptyList()
    }
}

// -----------------------------------------------------------------------------
// Test Provider API
// -----------------------------------------------------------------------------

class TestMediaProvider : MainAPI() {
    override var name = "TestProvider"
    override var mainUrl = "https://test.provider.com"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    override suspend fun load(url: String) = when {
        url.contains("movie") -> {
            newMovieLoadResponse(
                name = "Test Movie",
                url = url,
                type = TvType.Movie,
                dataUrl = "https://stream.provider.com/movie.m3u8"
            ) {
                this.plot = "A thrilling test movie synopsis."
                this.posterUrl = "https://img.provider.com/poster.jpg"
                this.year = 2026
                this.score = Score.from("8.5", 10)
                this.trailers = mutableListOf(
                    TrailerData(extractorUrl = "https://stream.provider.com/trailer1.mp4", referer = "https://test.provider.com", raw = true),
                    TrailerData(extractorUrl = "https://stream.provider.com/trailer2.mp4", referer = "https://test.provider.com", raw = true)
                )
            }
        }
        url.contains("series") -> {
            newTvSeriesLoadResponse(
                name = "Test Series",
                url = url,
                type = TvType.TvSeries,
                episodes = listOf(
                    newEpisode("https://stream.provider.com/s1e1") {
                        name = "Pilot"
                        season = 1
                        episode = 1
                    },
                    newEpisode("https://stream.provider.com/s1e2") {
                        name = "Episode 2"
                        season = 1
                        episode = 2
                    },
                    newEpisode("https://stream.provider.com/s2e1") {
                        name = "Season 2 Opener"
                        season = 2
                        episode = 1
                    }
                )
            ) {
                this.plot = "A great TV series."
                this.year = 2024
            }
        }
        url.contains("anime") -> {
            newAnimeLoadResponse(
                name = "Test Anime",
                url = url,
                type = TvType.Anime
            ) {
                this.plot = "Anime plot."
                this.episodes[DubStatus.Subbed] = listOf(
                    newEpisode("https://stream.provider.com/sub_ep1") { name = "Sub 1"; season = 1; episode = 1 },
                    newEpisode("https://stream.provider.com/sub_ep2") { name = "Sub 2"; season = 1; episode = 2 }
                )
                this.episodes[DubStatus.Dubbed] = listOf(
                    newEpisode("https://stream.provider.com/dub_ep1") { name = "Dub 1"; season = 1; episode = 1 }
                )
            }
        }
        else -> null
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        newExtractorLink(
            source = name,
            name = "1080p Stream",
            url = "$data/master.m3u8"
        ) {
            this.referer = mainUrl
            this.quality = Qualities.P1080.value
        }.also { callback(it) }
        return true
    }
}

// -----------------------------------------------------------------------------
// Test Suite
// -----------------------------------------------------------------------------

@OptIn(ExperimentalCoroutinesApi::class)
class ResultViewModelTest {

    private val provider = TestMediaProvider()

    @BeforeTest
    fun setUp() {
        APIHolder.allProviders.withLock {
            if (!APIHolder.allProviders.contains(provider)) {
                APIHolder.allProviders.add(provider)
            }
        }
        APIHolder.addPluginMapping(provider)
    }

    @Test
    fun testInitialState() {
        val bookmarkRepo = FakeBookmarkRepository()
        val watchProgressRepo = FakeWatchProgressRepository()
        val favoriteRepo = FakeFavoriteRepository()

        val viewModel = ResultViewModel(
            bookmarkRepository = bookmarkRepo,
            watchProgressRepository = watchProgressRepo,
            favoriteRepository = favoriteRepo
        )

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals("", state.title)
        assertFalse(state.isBookmarked)
        assertFalse(state.isFavorite)
        assertTrue(state.episodes.isEmpty())
        assertFalse(state.hasLinks)
    }

    @Test
    fun testLoadMovieResultSuccess() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val bookmarkRepo = FakeBookmarkRepository()
        val watchProgressRepo = FakeWatchProgressRepository()
        val favoriteRepo = FakeFavoriteRepository()
        val resumeRepo = FakeResumeWatchingRepository()

        val viewModel = ResultViewModel(
            bookmarkRepository = bookmarkRepo,
            watchProgressRepository = watchProgressRepo,
            favoriteRepository = favoriteRepo,
            resumeWatchingRepository = resumeRepo,
            coroutineContext = testDispatcher
        )

        viewModel.onEvent(ResultEvent.LoadResult("https://test.provider.com/movie/123", "TestProvider"))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals("Test Movie", state.title)
        assertEquals(2026, state.year)
        assertTrue(state.isMovie)
        assertFalse(state.isAnime)
        assertEquals(1, state.episodes.size)
        assertEquals(0, state.episodes.first().episode)
        assertNotNull(state.mediaId)
    }

    @Test
    fun testLoadTvSeriesWithSeasons() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val bookmarkRepo = FakeBookmarkRepository()
        val watchProgressRepo = FakeWatchProgressRepository()
        val favoriteRepo = FakeFavoriteRepository()

        val viewModel = ResultViewModel(
            bookmarkRepository = bookmarkRepo,
            watchProgressRepository = watchProgressRepo,
            favoriteRepository = favoriteRepo,
            coroutineContext = testDispatcher
        )

        viewModel.onEvent(ResultEvent.LoadResult("https://test.provider.com/series/456", "TestProvider"))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals("Test Series", state.title)
        assertTrue(state.isEpisodeBased)
        assertEquals(2, state.availableSeasons.size) // Season 1 and Season 2
        assertEquals(1, state.selectedSeason)
        assertEquals(2, state.episodes.size) // Season 1 has 2 episodes

        // Switch to Season 2
        viewModel.onEvent(ResultEvent.SelectSeason(2))
        advanceUntilIdle()

        val season2State = viewModel.state.value
        assertEquals(2, season2State.selectedSeason)
        assertEquals(1, season2State.episodes.size) // Season 2 has 1 episode
        assertEquals("Season 2 Opener", season2State.episodes.first().name)
    }

    @Test
    fun testLoadAnimeWithSubAndDub() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val bookmarkRepo = FakeBookmarkRepository()
        val watchProgressRepo = FakeWatchProgressRepository()
        val favoriteRepo = FakeFavoriteRepository()

        val viewModel = ResultViewModel(
            bookmarkRepository = bookmarkRepo,
            watchProgressRepository = watchProgressRepo,
            favoriteRepository = favoriteRepo,
            coroutineContext = testDispatcher
        )

        viewModel.onEvent(ResultEvent.LoadResult("https://test.provider.com/anime/789", "TestProvider"))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.isAnime)
        assertTrue(state.availableDubStatuses.contains(DubStatus.Subbed))
        assertTrue(state.availableDubStatuses.contains(DubStatus.Dubbed))
        assertEquals(DubStatus.Subbed, state.selectedDubStatus)
        assertEquals(2, state.episodes.size)

        // Switch to Dubbed
        viewModel.onEvent(ResultEvent.SelectDubStatus(DubStatus.Dubbed))
        advanceUntilIdle()

        val dubState = viewModel.state.value
        assertEquals(DubStatus.Dubbed, dubState.selectedDubStatus)
        assertEquals(1, dubState.episodes.size)
        assertEquals("Dub 1", dubState.episodes.first().name)
    }

    @Test
    fun testToggleAndSetBookmark() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val bookmarkRepo = FakeBookmarkRepository()
        val watchProgressRepo = FakeWatchProgressRepository()
        val favoriteRepo = FakeFavoriteRepository()

        val viewModel = ResultViewModel(
            bookmarkRepository = bookmarkRepo,
            watchProgressRepository = watchProgressRepo,
            favoriteRepository = favoriteRepo,
            coroutineContext = testDispatcher
        )

        viewModel.onEvent(ResultEvent.LoadResult("https://test.provider.com/movie/123", "TestProvider"))
        advanceUntilIdle()

        // Toggle bookmark (sets to Watching = 1)
        viewModel.onEvent(ResultEvent.ToggleBookmark())
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isBookmarked)
        assertEquals(1, viewModel.state.value.bookmarkWatchType)
        assertNotNull(bookmarkRepo.getBookmark(0, viewModel.state.value.mediaId!!))

        // Set to Completed = 2
        viewModel.onEvent(ResultEvent.SetBookmark(2))
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isBookmarked)
        assertEquals(2, viewModel.state.value.bookmarkWatchType)

        // Remove bookmark (sets watchType = 0 while preserving metadata)
        viewModel.onEvent(ResultEvent.SetBookmark(0))
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isBookmarked)
        assertEquals(0, viewModel.state.value.bookmarkWatchType)
        val b = bookmarkRepo.getBookmark(0, viewModel.state.value.mediaId!!)
        assertNotNull(b)
        assertEquals(0, b.watchType)
    }

    @Test
    fun testToggleFavoriteAndSubscription() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val bookmarkRepo = FakeBookmarkRepository()
        val watchProgressRepo = FakeWatchProgressRepository()
        val favoriteRepo = FakeFavoriteRepository()
        val subscriptionRepo = FakeSubscriptionRepository()

        val viewModel = ResultViewModel(
            bookmarkRepository = bookmarkRepo,
            watchProgressRepository = watchProgressRepo,
            favoriteRepository = favoriteRepo,
            subscriptionRepository = subscriptionRepo,
            coroutineContext = testDispatcher
        )

        viewModel.onEvent(ResultEvent.LoadResult("https://test.provider.com/series/456", "TestProvider"))
        advanceUntilIdle()

        // Toggle Favorite
        viewModel.onEvent(ResultEvent.ToggleFavorite)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isFavorite)
        assertNotNull(favoriteRepo.getFavorite(0, viewModel.state.value.mediaId!!))

        // Toggle Subscription
        viewModel.onEvent(ResultEvent.ToggleSubscription)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isSubscribed)
        assertNotNull(subscriptionRepo.getSubscription(0, viewModel.state.value.mediaId!!))
    }

    @Test
    fun testWatchProgressAndWatchedState() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val bookmarkRepo = FakeBookmarkRepository()
        val watchProgressRepo = FakeWatchProgressRepository()
        val favoriteRepo = FakeFavoriteRepository()
        val resumeRepo = FakeResumeWatchingRepository()

        val viewModel = ResultViewModel(
            bookmarkRepository = bookmarkRepo,
            watchProgressRepository = watchProgressRepo,
            favoriteRepository = favoriteRepo,
            resumeWatchingRepository = resumeRepo,
            coroutineContext = testDispatcher
        )

        viewModel.onEvent(ResultEvent.LoadResult("https://test.provider.com/series/456", "TestProvider"))
        advanceUntilIdle()

        val firstEp = viewModel.state.value.episodes.first()

        // Update Progress: 50%
        viewModel.onEvent(
            ResultEvent.UpdateWatchProgress(
                episodeId = firstEp.id,
                position = 60_000L,
                duration = 120_000L,
                watchState = 1
            )
        )
        advanceUntilIdle()

        val stateAfterProgress = viewModel.state.value
        val updatedEp = stateAfterProgress.episodes.first()
        assertEquals(60_000L, updatedEp.position)
        assertEquals(120_000L, updatedEp.duration)
        assertEquals(1, updatedEp.videoWatchState)
        assertEquals(0.5f, updatedEp.getWatchProgress())

        // Verify resume watching repository was updated
        val mediaId = viewModel.state.value.mediaId!!
        val resumeItem = resumeRepo.getResumeWatching(0, mediaId)
        assertNotNull(resumeItem)
        assertEquals(firstEp.id, resumeItem.episodeId)

        // Verify bookmark was auto-created for continue watching
        val autoBookmark = bookmarkRepo.getBookmark(0, mediaId)
        assertNotNull(autoBookmark)
        assertEquals(0, autoBookmark.watchType)

        // Mark Watched
        viewModel.onEvent(ResultEvent.SetWatchState(firstEp.id, 2))
        advanceUntilIdle()

        val stateAfterWatched = viewModel.state.value
        val watchedEp = stateAfterWatched.episodes.first()
        assertEquals(2, watchedEp.videoWatchState)
        assertTrue(watchedEp.isWatched)
    }

    @Test
    fun testLoadResultAutoPersistsBookmarkMetadataImmediately() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val bookmarkRepo = FakeBookmarkRepository()
        val watchProgressRepo = FakeWatchProgressRepository()
        val favoriteRepo = FakeFavoriteRepository()
        val resumeRepo = FakeResumeWatchingRepository()

        val mediaId = "series456".hashCode()

        val viewModel = ResultViewModel(
            bookmarkRepository = bookmarkRepo,
            watchProgressRepository = watchProgressRepo,
            favoriteRepository = favoriteRepo,
            resumeWatchingRepository = resumeRepo,
            coroutineContext = testDispatcher
        )

        assertNull(bookmarkRepo.getBookmark(0, mediaId))

        viewModel.onEvent(ResultEvent.LoadResult("https://test.provider.com/series/456", "TestProvider"))
        advanceUntilIdle()

        // Bookmark should now be persisted automatically immediately with watchType = 0 and full metadata
        val loadedBookmark = bookmarkRepo.getBookmark(0, viewModel.state.value.mediaId!!)
        assertNotNull(loadedBookmark)
        assertEquals(0, loadedBookmark.watchType)
        assertFalse(viewModel.state.value.isBookmarked)
        assertEquals("Test Series", loadedBookmark.name)
        assertEquals("https://test.provider.com/series/456", loadedBookmark.url)
        assertEquals("TestProvider", loadedBookmark.apiName)
        assertEquals(TvType.TvSeries, loadedBookmark.type)
        assertNull(loadedBookmark.posterUrl)
        assertEquals(2024, loadedBookmark.year)
        assertEquals("A great TV series.", loadedBookmark.plot)
        assertNull(loadedBookmark.score)
    }

    @Test
    fun testReloadLinksExtraction() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val bookmarkRepo = FakeBookmarkRepository()
        val watchProgressRepo = FakeWatchProgressRepository()
        val favoriteRepo = FakeFavoriteRepository()

        val viewModel = ResultViewModel(
            bookmarkRepository = bookmarkRepo,
            watchProgressRepository = watchProgressRepo,
            favoriteRepository = favoriteRepo,
            coroutineContext = testDispatcher
        )

        viewModel.onEvent(ResultEvent.LoadResult("https://test.provider.com/movie/123", "TestProvider"))
        advanceUntilIdle()

        viewModel.onEvent(ResultEvent.ReloadLinks())
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isExtractingLinks)
        assertTrue(state.hasLinks)
        assertEquals(1, state.extractedLinks.size)
        assertEquals("1080p Stream", state.extractedLinks.first().name)

        // Clear Links
        viewModel.onEvent(ResultEvent.ClearLinks)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.hasLinks)
        assertTrue(viewModel.state.value.extractedLinks.isEmpty())
    }

    @Test
    fun testSyncInitialStateAndServiceSelection() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val bookmarkRepo = FakeBookmarkRepository()
        val watchProgressRepo = FakeWatchProgressRepository()
        val favoriteRepo = FakeFavoriteRepository()
        val syncRepo = FakeSyncMappingRepository()

        val viewModel = ResultViewModel(
            bookmarkRepository = bookmarkRepo,
            watchProgressRepository = watchProgressRepo,
            favoriteRepository = favoriteRepo,
            syncMappingRepository = syncRepo,
            coroutineContext = testDispatcher
        )

        viewModel.onEvent(ResultEvent.LoadResult("https://test.provider.com/series/456", "TestProvider"))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(SyncService.AniList, state.selectedSyncService)
        assertEquals(SyncService.entries.size, state.externalSyncStates.size)
        assertFalse(state.isSyncLinked)
        assertNull(state.primaryLinkedSync)

        // Switch active service
        viewModel.onEvent(ResultEvent.SelectSyncService(SyncService.MyAnimeList))
        advanceUntilIdle()
        assertEquals(SyncService.MyAnimeList, viewModel.state.value.selectedSyncService)
    }

    @Test
    fun testUpdateSyncStatusAndScore() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val bookmarkRepo = FakeBookmarkRepository()
        val watchProgressRepo = FakeWatchProgressRepository()
        val favoriteRepo = FakeFavoriteRepository()
        val syncRepo = FakeSyncMappingRepository()

        val viewModel = ResultViewModel(
            bookmarkRepository = bookmarkRepo,
            watchProgressRepository = watchProgressRepo,
            favoriteRepository = favoriteRepo,
            syncMappingRepository = syncRepo,
            coroutineContext = testDispatcher
        )

        viewModel.onEvent(ResultEvent.LoadResult("https://test.provider.com/series/456", "TestProvider"))
        advanceUntilIdle()

        // Update AniList sync status to Watching
        viewModel.onEvent(ResultEvent.UpdateSyncStatus(SyncService.AniList, ExternalSyncStatus.Watching))
        advanceUntilIdle()

        var state = viewModel.state.value
        assertTrue(state.isSyncLinked)
        assertEquals(ExternalSyncStatus.Watching, state.externalSyncStates[SyncService.AniList]?.status)
        assertNotNull(state.primaryLinkedSync)
        assertEquals(SyncService.AniList, state.primaryLinkedSync?.service)

        // Update score to 9/10
        viewModel.onEvent(ResultEvent.UpdateSyncScore(SyncService.AniList, 9))
        advanceUntilIdle()

        state = viewModel.state.value
        assertEquals(9, state.externalSyncStates[SyncService.AniList]?.score)

        // Verify entity persisted in FakeSyncMappingRepository
        val mediaId = state.mediaId!!
        val mappings = syncRepo.getSyncMappings(0, mediaId)
        assertTrue(mappings.any { it.syncPrefix == "anilist" })
    }

    @Test
    fun testUpdateSyncEpisodeCounter() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val bookmarkRepo = FakeBookmarkRepository()
        val watchProgressRepo = FakeWatchProgressRepository()
        val favoriteRepo = FakeFavoriteRepository()
        val syncRepo = FakeSyncMappingRepository()

        val viewModel = ResultViewModel(
            bookmarkRepository = bookmarkRepo,
            watchProgressRepository = watchProgressRepo,
            favoriteRepository = favoriteRepo,
            syncMappingRepository = syncRepo,
            coroutineContext = testDispatcher
        )

        viewModel.onEvent(ResultEvent.LoadResult("https://test.provider.com/series/456", "TestProvider"))
        advanceUntilIdle()

        // Update episode count
        viewModel.onEvent(ResultEvent.UpdateSyncEpisode(SyncService.Simkl, 2))
        advanceUntilIdle()

        val simklState = viewModel.state.value.externalSyncStates[SyncService.Simkl]
        assertNotNull(simklState)
        assertEquals(2, simklState.watchedEpisodes)

        // If updated to max episodes (3), should auto-complete
        val maxEps = viewModel.state.value.externalSyncStates[SyncService.Simkl]?.maxEpisodes ?: 3
        viewModel.onEvent(ResultEvent.UpdateSyncEpisode(SyncService.Simkl, maxEps))
        advanceUntilIdle()

        val completedState = viewModel.state.value.externalSyncStates[SyncService.Simkl]
        assertEquals(maxEps, completedState?.watchedEpisodes)
        assertEquals(ExternalSyncStatus.Completed, completedState?.status)
    }

    @Test
    fun testSaveSyncDataAndUnlink() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val bookmarkRepo = FakeBookmarkRepository()
        val watchProgressRepo = FakeWatchProgressRepository()
        val favoriteRepo = FakeFavoriteRepository()
        val syncRepo = FakeSyncMappingRepository()

        val viewModel = ResultViewModel(
            bookmarkRepository = bookmarkRepo,
            watchProgressRepository = watchProgressRepo,
            favoriteRepository = favoriteRepo,
            syncMappingRepository = syncRepo,
            coroutineContext = testDispatcher
        )

        viewModel.onEvent(ResultEvent.LoadResult("https://test.provider.com/series/456", "TestProvider"))
        advanceUntilIdle()

        val mediaId = viewModel.state.value.mediaId!!

        // Save complete sync data
        viewModel.onEvent(
            ResultEvent.SaveSyncData(
                service = SyncService.Kitsu,
                syncId = "kitsu-anime-9999",
                status = ExternalSyncStatus.Watching,
                score = 8,
                watchedEpisodes = 12,
                maxEpisodes = 24
            )
        )
        advanceUntilIdle()

        var state = viewModel.state.value
        val kitsuEntry = state.externalSyncStates[SyncService.Kitsu]
        assertNotNull(kitsuEntry)
        assertTrue(kitsuEntry.isLinked)
        assertEquals("kitsu-anime-9999", kitsuEntry.syncId)
        assertEquals(ExternalSyncStatus.Watching, kitsuEntry.status)
        assertEquals(8, kitsuEntry.score)
        assertEquals(12, kitsuEntry.watchedEpisodes)
        assertEquals(24, kitsuEntry.maxEpisodes)

        // Verify mapping in repository
        var mappings = syncRepo.getSyncMappings(0, mediaId)
        assertEquals(1, mappings.size)
        assertEquals("kitsu-anime-9999", mappings.first().remoteUrl)

        // Unlink service
        viewModel.onEvent(ResultEvent.UnlinkSyncService(SyncService.Kitsu))
        advanceUntilIdle()

        state = viewModel.state.value
        val unlinkedKitsu = state.externalSyncStates[SyncService.Kitsu]
        assertNotNull(unlinkedKitsu)
        assertFalse(unlinkedKitsu.isLinked)
        assertEquals(ExternalSyncStatus.None, unlinkedKitsu.status)
        assertNull(unlinkedKitsu.syncId)

        // Verify removed from repository
        mappings = syncRepo.getSyncMappings(0, mediaId)
        assertTrue(mappings.isEmpty())
    }

    @Test
    fun testMultiScaleScoringFormats() {
        // 1. 10-Point Decimal Scale
        val score10Decimal = Score.from("8.5", 10)
        assertEquals(8.5, TrackerScoreScale.Point10Decimal.toDisplayValue(score10Decimal))
        assertEquals("8.5", TrackerScoreScale.Point10Decimal.formatScore(score10Decimal))

        // 2. 100-Point Integer Scale
        val score100 = Score.from100(85)
        assertEquals(85.0, TrackerScoreScale.Point100.toDisplayValue(score100))
        assertEquals("85", TrackerScoreScale.Point100.formatScore(score100))

        // 3. 5-Star Rating Scale
        val score5Star = Score.from5(4)
        assertEquals(4.0, TrackerScoreScale.Point5Star.toDisplayValue(score5Star))
        assertEquals("4", TrackerScoreScale.Point5Star.formatScore(score5Star))

        // 4. 3-Point Smiley Rating Scale
        val sadScore = Score.from(1, 3)
        assertEquals(1.0, TrackerScoreScale.Point3Smiley.toDisplayValue(sadScore))
        assertEquals(SmileyRating.Sad, SmileyRating.fromScore(sadScore))

        val neutralScore = Score.from(2, 3)
        assertEquals(2.0, TrackerScoreScale.Point3Smiley.toDisplayValue(neutralScore))
        assertEquals(SmileyRating.Neutral, SmileyRating.fromScore(neutralScore))

        val happyScore = Score.from(3, 3)
        assertEquals(3.0, TrackerScoreScale.Point3Smiley.toDisplayValue(happyScore))
        assertEquals(SmileyRating.Happy, SmileyRating.fromScore(happyScore))
    }

    @Test
    fun testSetSyncScoreScaleAndMultiScaleUpdates() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val bookmarkRepo = FakeBookmarkRepository()
        val watchProgressRepo = FakeWatchProgressRepository()
        val favoriteRepo = FakeFavoriteRepository()
        val syncRepo = FakeSyncMappingRepository()

        val viewModel = ResultViewModel(
            bookmarkRepository = bookmarkRepo,
            watchProgressRepository = watchProgressRepo,
            favoriteRepository = favoriteRepo,
            syncMappingRepository = syncRepo,
            coroutineContext = testDispatcher
        )

        viewModel.onEvent(ResultEvent.LoadResult("https://test.provider.com/series/456", "TestProvider"))
        advanceUntilIdle()

        // 1. Update score using 10-point decimal
        val score85 = Score.from("8.5", 10)
        viewModel.onEvent(ResultEvent.UpdateSyncScore(SyncService.AniList, score85, TrackerScoreScale.Point10Decimal))
        advanceUntilIdle()

        val aniListEntry = viewModel.state.value.externalSyncStates[SyncService.AniList]
        assertNotNull(aniListEntry)
        assertEquals(TrackerScoreScale.Point10Decimal, aniListEntry.scoreScale)
        assertEquals(8.5, aniListEntry.displayScoreValue)
        assertEquals("8.5", aniListEntry.formattedScore())

        // 2. Change active scale to 100-Point
        viewModel.onEvent(ResultEvent.SetSyncScoreScale(SyncService.AniList, TrackerScoreScale.Point100))
        advanceUntilIdle()

        val entry100 = viewModel.state.value.externalSyncStates[SyncService.AniList]
        assertNotNull(entry100)
        assertEquals(TrackerScoreScale.Point100, entry100.scoreScale)
        assertEquals(85.0, entry100.displayScoreValue)
        assertEquals("85", entry100.formattedScore())

        // 3. Change active scale to 5-Star
        viewModel.onEvent(ResultEvent.SetSyncScoreScale(SyncService.AniList, TrackerScoreScale.Point5Star))
        advanceUntilIdle()

        val entry5Star = viewModel.state.value.externalSyncStates[SyncService.AniList]
        assertNotNull(entry5Star)
        assertEquals(TrackerScoreScale.Point5Star, entry5Star.scoreScale)
        assertEquals(4.0, entry5Star.displayScoreValue)

        // 4. Change active scale to 3-Point Smiley
        viewModel.onEvent(ResultEvent.SetSyncScoreScale(SyncService.AniList, TrackerScoreScale.Point3Smiley))
        advanceUntilIdle()

        val entrySmiley = viewModel.state.value.externalSyncStates[SyncService.AniList]
        assertNotNull(entrySmiley)
        assertEquals(TrackerScoreScale.Point3Smiley, entrySmiley.scoreScale)
        assertEquals(3.0, entrySmiley.displayScoreValue)
        assertEquals(SmileyRating.Happy, SmileyRating.fromScore(entrySmiley.effectiveScore))

        // 5. Update directly with Smiley Rating Sad (1)
        val sad = Score.from(1, 3)
        viewModel.onEvent(ResultEvent.UpdateSyncScore(SyncService.AniList, sad, TrackerScoreScale.Point3Smiley))
        advanceUntilIdle()

        val entrySad = viewModel.state.value.externalSyncStates[SyncService.AniList]
        assertNotNull(entrySad)
        assertEquals(1.0, entrySad.displayScoreValue)
        assertEquals(SmileyRating.Sad, SmileyRating.fromScore(entrySad.effectiveScore))
    }

    @Test
    fun testSaveSyncDataWithMultiScaleScore() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val bookmarkRepo = FakeBookmarkRepository()
        val watchProgressRepo = FakeWatchProgressRepository()
        val favoriteRepo = FakeFavoriteRepository()
        val syncRepo = FakeSyncMappingRepository()

        val viewModel = ResultViewModel(
            bookmarkRepository = bookmarkRepo,
            watchProgressRepository = watchProgressRepo,
            favoriteRepository = favoriteRepo,
            syncMappingRepository = syncRepo,
            coroutineContext = testDispatcher
        )

        viewModel.onEvent(ResultEvent.LoadResult("https://test.provider.com/series/456", "TestProvider"))
        advanceUntilIdle()

        val score10Decimal = Score.from("9.2", 10)
        viewModel.onEvent(
            ResultEvent.SaveSyncData(
                service = SyncService.AniList,
                syncId = "anilist-anime-5678",
                status = ExternalSyncStatus.Completed,
                rawScore = score10Decimal,
                scoreScale = TrackerScoreScale.Point10Decimal,
                watchedEpisodes = 24,
                maxEpisodes = 24
            )
        )
        advanceUntilIdle()

        val state = viewModel.state.value
        val aniListEntry = state.externalSyncStates[SyncService.AniList]
        assertNotNull(aniListEntry)
        assertTrue(aniListEntry.isLinked)
        assertEquals("anilist-anime-5678", aniListEntry.syncId)
        assertEquals(ExternalSyncStatus.Completed, aniListEntry.status)
        assertEquals(9.2, aniListEntry.displayScoreValue)
        assertEquals(TrackerScoreScale.Point10Decimal, aniListEntry.scoreScale)
        assertEquals(24, aniListEntry.watchedEpisodes)
    }

    @Test
    fun testAutoSyncEpisodeProgressOnWatch() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val bookmarkRepo = FakeBookmarkRepository()
        val watchProgressRepo = FakeWatchProgressRepository()
        val favoriteRepo = FakeFavoriteRepository()
        val syncRepo = FakeSyncMappingRepository()

        val viewModel = ResultViewModel(
            bookmarkRepository = bookmarkRepo,
            watchProgressRepository = watchProgressRepo,
            favoriteRepository = favoriteRepo,
            syncMappingRepository = syncRepo,
            coroutineContext = testDispatcher
        )

        viewModel.onEvent(ResultEvent.LoadResult("https://test.provider.com/series/456", "TestProvider"))
        advanceUntilIdle()

        // Link MAL
        viewModel.onEvent(
            ResultEvent.SaveSyncData(
                service = SyncService.MyAnimeList,
                syncId = "mal-12345",
                status = ExternalSyncStatus.Watching,
                score = null,
                watchedEpisodes = 0,
                maxEpisodes = 24
            )
        )
        advanceUntilIdle()

        // Watch first episode
        val firstEp = viewModel.state.value.episodes.first()
        viewModel.onEvent(
            ResultEvent.UpdateWatchProgress(
                episodeId = firstEp.id,
                position = 120_000L,
                duration = 120_000L,
                watchState = 2
            )
        )
        advanceUntilIdle()

        val malState = viewModel.state.value.externalSyncStates[SyncService.MyAnimeList]
        assertNotNull(malState)
        assertEquals(1, malState.watchedEpisodes)
    }

    @Test
    fun testTrailersStateAfterLoad() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val bookmarkRepo = FakeBookmarkRepository()
        val watchProgressRepo = FakeWatchProgressRepository()
        val favoriteRepo = FakeFavoriteRepository()

        val viewModel = ResultViewModel(
            bookmarkRepository = bookmarkRepo,
            watchProgressRepository = watchProgressRepo,
            favoriteRepository = favoriteRepo,
            coroutineContext = testDispatcher
        )

        viewModel.onEvent(ResultEvent.LoadResult("https://test.provider.com/movie/123", "TestProvider"))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.hasTrailers)
        assertEquals(2, state.trailers.size)
        assertNotNull(state.currentTrailerData)
        assertEquals("https://stream.provider.com/trailer1.mp4", state.currentTrailerData?.extractorUrl)
        assertFalse(state.isTrailerDialogOpen)
    }

    @Test
    fun testOpenTrailerAndLoadTrailer() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val bookmarkRepo = FakeBookmarkRepository()
        val watchProgressRepo = FakeWatchProgressRepository()
        val favoriteRepo = FakeFavoriteRepository()

        val viewModel = ResultViewModel(
            bookmarkRepository = bookmarkRepo,
            watchProgressRepository = watchProgressRepo,
            favoriteRepository = favoriteRepo,
            coroutineContext = testDispatcher
        )

        viewModel.onEvent(ResultEvent.LoadResult("https://test.provider.com/movie/123", "TestProvider"))
        advanceUntilIdle()

        // Open first trailer
        viewModel.onEvent(ResultEvent.OpenTrailer(0))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.isTrailerDialogOpen)
        assertEquals(0, state.selectedTrailerIndex)
        assertFalse(state.isExtractingTrailer)
        assertNull(state.trailerExtractionError)
        assertEquals(1, state.extractedTrailerLinks.size)
        assertNotNull(state.selectedTrailerQuality)
        assertEquals("https://stream.provider.com/trailer1.mp4", state.selectedTrailerQuality.url)
    }

    @Test
    fun testSwitchTrailerIndex() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val bookmarkRepo = FakeBookmarkRepository()
        val watchProgressRepo = FakeWatchProgressRepository()
        val favoriteRepo = FakeFavoriteRepository()

        val viewModel = ResultViewModel(
            bookmarkRepository = bookmarkRepo,
            watchProgressRepository = watchProgressRepo,
            favoriteRepository = favoriteRepo,
            coroutineContext = testDispatcher
        )

        viewModel.onEvent(ResultEvent.LoadResult("https://test.provider.com/movie/123", "TestProvider"))
        advanceUntilIdle()

        // Switch to second trailer
        viewModel.onEvent(ResultEvent.LoadTrailer(1))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(1, state.selectedTrailerIndex)
        assertEquals(1, state.extractedTrailerLinks.size)
        assertEquals("https://stream.provider.com/trailer2.mp4", state.selectedTrailerQuality?.url)
    }

    @Test
    fun testSelectTrailerQuality() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val bookmarkRepo = FakeBookmarkRepository()
        val watchProgressRepo = FakeWatchProgressRepository()
        val favoriteRepo = FakeFavoriteRepository()

        val viewModel = ResultViewModel(
            bookmarkRepository = bookmarkRepo,
            watchProgressRepository = watchProgressRepo,
            favoriteRepository = favoriteRepo,
            coroutineContext = testDispatcher
        )

        val customLink = newExtractorLink(
            source = "Test",
            name = "720p",
            url = "https://stream.provider.com/trailer_720.mp4"
        )

        viewModel.onEvent(ResultEvent.SelectTrailerQuality(customLink))
        advanceUntilIdle()

        assertEquals(customLink, viewModel.state.value.selectedTrailerQuality)
    }

    @Test
    fun testCloseTrailer() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val bookmarkRepo = FakeBookmarkRepository()
        val watchProgressRepo = FakeWatchProgressRepository()
        val favoriteRepo = FakeFavoriteRepository()

        val viewModel = ResultViewModel(
            bookmarkRepository = bookmarkRepo,
            watchProgressRepository = watchProgressRepo,
            favoriteRepository = favoriteRepo,
            coroutineContext = testDispatcher
        )

        viewModel.onEvent(ResultEvent.LoadResult("https://test.provider.com/movie/123", "TestProvider"))
        advanceUntilIdle()

        viewModel.onEvent(ResultEvent.OpenTrailer(0))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.isTrailerDialogOpen)

        viewModel.onEvent(ResultEvent.CloseTrailer)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isTrailerDialogOpen)
        assertFalse(state.isExtractingTrailer)
        assertTrue(state.extractedTrailerLinks.isEmpty())
        assertNull(state.selectedTrailerQuality)
    }

    @Test
    fun testSelectEpisodeRestoresExactPositionAndDuration() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val bookmarkRepo = FakeBookmarkRepository()
        val watchProgressRepo = FakeWatchProgressRepository()
        val favoriteRepo = FakeFavoriteRepository()
        val resumeRepo = FakeResumeWatchingRepository()

        val viewModel = ResultViewModel(
            bookmarkRepository = bookmarkRepo,
            watchProgressRepository = watchProgressRepo,
            favoriteRepository = favoriteRepo,
            resumeWatchingRepository = resumeRepo,
            coroutineContext = testDispatcher
        )

        viewModel.onEvent(ResultEvent.LoadResult("https://test.provider.com/series/456", "TestProvider"))
        advanceUntilIdle()

        val episodes = viewModel.state.value.episodes
        val targetEp = episodes.first()

        // Set progress for target episode
        watchProgressRepo.setProgress(
            accountId = 0,
            mediaId = targetEp.id,
            position = 45_230L,
            duration = 140_000L,
            watchState = 1
        )

        // Select the episode
        viewModel.onEvent(ResultEvent.SelectEpisode(targetEp))
        advanceUntilIdle()

        val selected = viewModel.state.value.selectedEpisode
        assertNotNull(selected)
        assertEquals(targetEp.id, selected.id)
        assertEquals(45_230L, selected.position)
        assertEquals(140_000L, selected.duration)
        assertEquals(1, selected.videoWatchState)
    }

    @Test
    fun testMovieWatchProgressIntegration() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val bookmarkRepo = FakeBookmarkRepository()
        val watchProgressRepo = FakeWatchProgressRepository()
        val favoriteRepo = FakeFavoriteRepository()
        val resumeRepo = FakeResumeWatchingRepository()

        val viewModel = ResultViewModel(
            bookmarkRepository = bookmarkRepo,
            watchProgressRepository = watchProgressRepo,
            favoriteRepository = favoriteRepo,
            resumeWatchingRepository = resumeRepo,
            coroutineContext = testDispatcher
        )

        viewModel.onEvent(ResultEvent.LoadResult("https://test.provider.com/movie/123", "TestProvider"))
        advanceUntilIdle()

        val mediaId = viewModel.state.value.mediaId!!
        val movieEp = viewModel.state.value.selectedEpisode!!
        assertEquals(mediaId, movieEp.id)

        // Update progress for movie
        viewModel.onEvent(
            ResultEvent.UpdateWatchProgress(
                episodeId = movieEp.id,
                position = 3_600_000L,
                duration = 7_200_000L,
                watchState = 1
            )
        )
        advanceUntilIdle()

        // Verify state
        val state = viewModel.state.value
        assertEquals(3_600_000L, state.selectedEpisode?.position)
        assertEquals(7_200_000L, state.selectedEpisode?.duration)
        assertEquals(0.5f, state.selectedEpisode?.getWatchProgress())

        // Verify bookmark and resume repositories
        val bookmark = bookmarkRepo.getBookmark(0, mediaId)
        assertNotNull(bookmark)
        assertEquals(0, bookmark.watchType)

        val resume = resumeRepo.getResumeWatching(0, mediaId)
        assertNotNull(resume)
        assertEquals(mediaId, resume.parentId)
    }

    @Test
    fun testEpisodeMenuOpenClose() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val bookmarkRepo = FakeBookmarkRepository()
        val watchProgressRepo = FakeWatchProgressRepository()
        val favoriteRepo = FakeFavoriteRepository()

        val viewModel = ResultViewModel(
            bookmarkRepository = bookmarkRepo,
            watchProgressRepository = watchProgressRepo,
            favoriteRepository = favoriteRepo,
            coroutineContext = testDispatcher
        )

        viewModel.onEvent(ResultEvent.LoadResult("https://test.provider.com/series/456", "TestProvider"))
        advanceUntilIdle()

        val ep = viewModel.state.value.episodes.first()
        assertFalse(viewModel.state.value.isEpisodeMenuOpen)
        assertNull(viewModel.state.value.selectedMenuEpisode)

        // Open menu
        viewModel.onEvent(ResultEvent.OpenEpisodeMenu(ep))
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isEpisodeMenuOpen)
        assertEquals(ep.id, viewModel.state.value.selectedMenuEpisode?.id)

        // Close menu
        viewModel.onEvent(ResultEvent.CloseEpisodeMenu)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isEpisodeMenuOpen)
        assertNull(viewModel.state.value.selectedMenuEpisode)
    }

    @Test
    fun testMarkEpisodesUpTo() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val bookmarkRepo = FakeBookmarkRepository()
        val watchProgressRepo = FakeWatchProgressRepository()
        val favoriteRepo = FakeFavoriteRepository()

        val viewModel = ResultViewModel(
            bookmarkRepository = bookmarkRepo,
            watchProgressRepository = watchProgressRepo,
            favoriteRepository = favoriteRepo,
            coroutineContext = testDispatcher
        )

        viewModel.onEvent(ResultEvent.LoadResult("https://test.provider.com/series/456", "TestProvider"))
        advanceUntilIdle()

        val season1Eps = viewModel.state.value.episodes
        assertEquals(2, season1Eps.size)
        val s1e1 = season1Eps[0]
        val s1e2 = season1Eps[1]

        // Mark episodes up to S1E2
        viewModel.onEvent(ResultEvent.MarkEpisodesUpTo(episodeId = s1e2.id, season = 1))
        advanceUntilIdle()

        val stateAfterS1 = viewModel.state.value
        val updatedS1E1 = stateAfterS1.episodes[0]
        val updatedS1E2 = stateAfterS1.episodes[1]

        assertEquals(2, updatedS1E1.videoWatchState)
        assertTrue(updatedS1E1.isWatched)
        assertEquals(2, updatedS1E2.videoWatchState)
        assertTrue(updatedS1E2.isWatched)

        // Verify repository
        assertEquals(2, watchProgressRepo.getProgress(0, s1e1.id)?.watchState)
        assertEquals(2, watchProgressRepo.getProgress(0, s1e2.id)?.watchState)

        // Switch to Season 2 and verify S2E1 is not watched
        viewModel.onEvent(ResultEvent.SelectSeason(2))
        advanceUntilIdle()

        val s2e1 = viewModel.state.value.episodes.first()
        assertEquals(0, s2e1.videoWatchState)
        assertFalse(s2e1.isWatched)

        // Mark up to S2E1
        viewModel.onEvent(ResultEvent.MarkEpisodesUpTo(episodeId = s2e1.id, season = 2))
        advanceUntilIdle()

        val stateAfterS2 = viewModel.state.value
        val updatedS2E1 = stateAfterS2.episodes.first()
        assertEquals(2, updatedS2E1.videoWatchState)
        assertTrue(updatedS2E1.isWatched)
        assertEquals(2, watchProgressRepo.getProgress(0, s2e1.id)?.watchState)
    }

    @Test
    fun testCopyEpisodeLink() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val bookmarkRepo = FakeBookmarkRepository()
        val watchProgressRepo = FakeWatchProgressRepository()
        val favoriteRepo = FakeFavoriteRepository()

        val viewModel = ResultViewModel(
            bookmarkRepository = bookmarkRepo,
            watchProgressRepository = watchProgressRepo,
            favoriteRepository = favoriteRepo,
            coroutineContext = testDispatcher
        )

        viewModel.onEvent(ResultEvent.LoadResult("https://test.provider.com/series/456", "TestProvider"))
        advanceUntilIdle()

        val firstEp = viewModel.state.value.episodes.first()

        var effect: com.lagradost.cloudstream3.shared.mvi.UiEffect? = null
        val effectJob = launch {
            viewModel.effects.collect { effect = it }
        }

        viewModel.onEvent(ResultEvent.CopyEpisodeLink(firstEp))
        advanceUntilIdle()

        assertNotNull(effect)
        assertTrue(effect is ResultEffect.CopyToClipboard)
        val copyEffect = effect as ResultEffect.CopyToClipboard
        assertEquals("https://stream.provider.com/s1e1", copyEffect.text)
        assertTrue(copyEffect.toastMessage.isNotBlank())

        effectJob.cancel()
    }

    @Test
    fun testReloadLinksWithClearCache() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val bookmarkRepo = FakeBookmarkRepository()
        val watchProgressRepo = FakeWatchProgressRepository()
        val favoriteRepo = FakeFavoriteRepository()

        val viewModel = ResultViewModel(
            bookmarkRepository = bookmarkRepo,
            watchProgressRepository = watchProgressRepo,
            favoriteRepository = favoriteRepo,
            coroutineContext = testDispatcher
        )

        viewModel.onEvent(ResultEvent.LoadResult("https://test.provider.com/movie/123", "TestProvider"))
        advanceUntilIdle()

        // Extract links first
        viewModel.onEvent(ResultEvent.ReloadLinks())
        advanceUntilIdle()

        assertTrue(viewModel.state.value.hasLinks)
        assertEquals(1, viewModel.state.value.extractedLinks.size)

        // Reload with clearCache = true
        viewModel.onEvent(ResultEvent.ReloadLinks(clearCache = true))
        advanceUntilIdle()

        assertTrue(viewModel.state.value.hasLinks)
        assertEquals(1, viewModel.state.value.extractedLinks.size)
    }
}
