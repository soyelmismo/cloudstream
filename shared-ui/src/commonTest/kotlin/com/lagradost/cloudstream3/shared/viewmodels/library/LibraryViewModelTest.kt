package com.lagradost.cloudstream3.shared.viewmodels.library

import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.shared.persistence.entity.BookmarkEntity
import com.lagradost.cloudstream3.shared.persistence.entity.FavoriteEntity
import com.lagradost.cloudstream3.shared.persistence.entity.WatchProgressEntity
import com.lagradost.cloudstream3.shared.persistence.repository.AppPreferenceManager
import com.lagradost.cloudstream3.shared.persistence.repository.BookmarkRepository
import com.lagradost.cloudstream3.shared.persistence.repository.FavoriteRepository
import com.lagradost.cloudstream3.shared.persistence.repository.WatchProgressRepository
import com.lagradost.cloudstream3.shared.viewmodels.settings.FakeAppPreferenceRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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

private class TestBookmarkRepo : BookmarkRepository {
    val data = mutableMapOf<Pair<Int, Int>, BookmarkEntity>()
    val allFlow = MutableStateFlow<List<BookmarkEntity>>(emptyList())

    override suspend fun getBookmark(accountId: Int, id: Int): BookmarkEntity? = data[accountId to id]

    override fun getBookmarkFlow(accountId: Int, id: Int): Flow<BookmarkEntity?> {
        return allFlow.map { list -> list.find { it.accountId == accountId && it.id == id } }
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
        allFlow.value = data.values.toList()
    }

    override suspend fun deleteBookmark(accountId: Int, id: Int) {
        val key = accountId to id
        data.remove(key)
        allFlow.value = data.values.toList()
    }

    override suspend fun clearAll(accountId: Int) {
        data.clear()
        allFlow.value = emptyList()
    }
}

private class TestFavoriteRepo : FavoriteRepository {
    val data = mutableMapOf<Pair<Int, Int>, FavoriteEntity>()
    val allFlow = MutableStateFlow<List<FavoriteEntity>>(emptyList())

    override suspend fun getFavorite(accountId: Int, id: Int): FavoriteEntity? = data[accountId to id]

    override fun getFavoriteFlow(accountId: Int, id: Int): Flow<FavoriteEntity?> {
        return allFlow.map { list -> list.find { it.accountId == accountId && it.id == id } }
    }

    override suspend fun getAllFavorites(accountId: Int): List<FavoriteEntity> {
        return data.filterKeys { it.first == accountId }.values.toList()
    }

    override fun getAllFavoritesFlow(accountId: Int): Flow<List<FavoriteEntity>> = allFlow

    override suspend fun saveFavorite(favorite: FavoriteEntity) {
        val key = favorite.accountId to favorite.id
        data[key] = favorite
        allFlow.value = data.values.toList()
    }

    override suspend fun deleteFavorite(accountId: Int, id: Int) {
        val key = accountId to id
        data.remove(key)
        allFlow.value = data.values.toList()
    }

    override suspend fun clearAll(accountId: Int) {
        data.clear()
        allFlow.value = emptyList()
    }
}

private class TestWatchProgressRepo : WatchProgressRepository {
    val data = mutableMapOf<Pair<Int, Int>, WatchProgressEntity>()
    val allFlow = MutableStateFlow<List<WatchProgressEntity>>(emptyList())

    override suspend fun getProgress(accountId: Int, mediaId: Int): WatchProgressEntity? = data[accountId to mediaId]

    override fun getProgressFlow(accountId: Int, mediaId: Int): Flow<WatchProgressEntity?> {
        return allFlow.map { list -> list.find { it.accountId == accountId && it.mediaId == mediaId } }
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
        allFlow.value = data.values.toList()
    }

    override suspend fun deleteProgress(accountId: Int, mediaId: Int) {
        val key = accountId to mediaId
        data.remove(key)
        allFlow.value = data.values.toList()
    }

    override suspend fun clearProgress(accountId: Int) {
        data.clear()
        allFlow.value = emptyList()
    }
}

// -----------------------------------------------------------------------------
// Test Suite
// -----------------------------------------------------------------------------

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    @BeforeTest
    fun setUp() {
        AppPreferenceManager.init(FakeAppPreferenceRepository())
    }

    @Test
    fun testInitialEmptyState() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val bookmarkRepo = TestBookmarkRepo()
        val watchProgressRepo = TestWatchProgressRepo()
        val favoriteRepo = TestFavoriteRepo()

        val viewModel = LibraryViewModel(
            bookmarkRepository = bookmarkRepo,
            watchProgressRepository = watchProgressRepo,
            favoriteRepository = favoriteRepo,
            coroutineContext = testDispatcher
        )
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertTrue(state.isLibraryEmpty)
        assertTrue(state.allItems.isEmpty())
        assertTrue(state.filteredItems.isEmpty())
        assertEquals(WatchStatus.ALL, state.selectedTab)
        assertEquals(0, state.tabCounts[WatchStatus.ALL] ?: 0)
    }

    @Test
    fun testObservingBookmarksAndStatusTabs() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val bookmarkRepo = TestBookmarkRepo()
        val watchProgressRepo = TestWatchProgressRepo()
        val favoriteRepo = TestFavoriteRepo()

        // Prepopulate bookmarks
        bookmarkRepo.saveBookmark(
            BookmarkEntity(
                accountId = 0,
                id = 1,
                name = "Breaking Bad",
                url = "https://provider.com/bb",
                apiName = "TestAPI",
                type = TvType.TvSeries,
                watchType = 1, // Watching
                bookmarkedTime = 100L
            )
        )
        bookmarkRepo.saveBookmark(
            BookmarkEntity(
                accountId = 0,
                id = 2,
                name = "Inception",
                url = "https://provider.com/inception",
                apiName = "TestAPI",
                type = TvType.Movie,
                watchType = 2, // Completed
                bookmarkedTime = 200L
            )
        )
        bookmarkRepo.saveBookmark(
            BookmarkEntity(
                accountId = 0,
                id = 3,
                name = "Attack on Titan",
                url = "https://provider.com/aot",
                apiName = "TestAPI",
                type = TvType.Anime,
                watchType = 5, // Planned
                bookmarkedTime = 300L
            )
        )
        bookmarkRepo.saveBookmark(
            BookmarkEntity(
                accountId = 0,
                id = 4,
                name = "Death Note",
                url = "https://provider.com/dn",
                apiName = "TestAPI",
                type = TvType.Anime,
                watchType = 3, // On Hold
                bookmarkedTime = 400L
            )
        )
        bookmarkRepo.saveBookmark(
            BookmarkEntity(
                accountId = 0,
                id = 5,
                name = "Random Show",
                url = "https://provider.com/random",
                apiName = "TestAPI",
                type = TvType.TvSeries,
                watchType = 4, // Dropped
                bookmarkedTime = 500L
            )
        )

        val viewModel = LibraryViewModel(
            bookmarkRepository = bookmarkRepo,
            watchProgressRepository = watchProgressRepo,
            favoriteRepository = favoriteRepo,
            coroutineContext = testDispatcher
        )
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(5, state.allItems.size)
        assertEquals(5, state.filteredItems.size)

        // Verify Tab Counts
        assertEquals(5, state.tabCounts[WatchStatus.ALL])
        assertEquals(1, state.tabCounts[WatchStatus.WATCHING])
        assertEquals(1, state.tabCounts[WatchStatus.COMPLETED])
        assertEquals(1, state.tabCounts[WatchStatus.PLANNED])
        assertEquals(1, state.tabCounts[WatchStatus.ON_HOLD])
        assertEquals(1, state.tabCounts[WatchStatus.DROPPED])

        // Select "Watching" Tab
        viewModel.handleEvent(LibraryEvent.SelectTab(WatchStatus.WATCHING))
        advanceUntilIdle()

        assertEquals(WatchStatus.WATCHING, viewModel.state.value.selectedTab)
        assertEquals(1, viewModel.state.value.filteredItems.size)
        assertEquals("Breaking Bad", viewModel.state.value.filteredItems.first().name)

        // Select "Completed" Tab
        viewModel.handleEvent(LibraryEvent.SelectTab(WatchStatus.COMPLETED))
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.filteredItems.size)
        assertEquals("Inception", viewModel.state.value.filteredItems.first().name)

        // Select "Planned" Tab
        viewModel.handleEvent(LibraryEvent.SelectTab(WatchStatus.PLANNED))
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.filteredItems.size)
        assertEquals("Attack on Titan", viewModel.state.value.filteredItems.first().name)
    }

    @Test
    fun testSearchFiltering() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val bookmarkRepo = TestBookmarkRepo()
        val watchProgressRepo = TestWatchProgressRepo()
        val favoriteRepo = TestFavoriteRepo()

        bookmarkRepo.saveBookmark(
            BookmarkEntity(
                accountId = 0,
                id = 1,
                name = "Spiderman: No Way Home",
                url = "https://provider.com/spiderman",
                apiName = "FlixAPI",
                type = TvType.Movie,
                watchType = 1
            )
        )
        bookmarkRepo.saveBookmark(
            BookmarkEntity(
                accountId = 0,
                id = 2,
                name = "Batman Begins",
                url = "https://provider.com/batman",
                apiName = "FlixAPI",
                type = TvType.Movie,
                watchType = 2
            )
        )

        val viewModel = LibraryViewModel(
            bookmarkRepository = bookmarkRepo,
            watchProgressRepository = watchProgressRepo,
            favoriteRepository = favoriteRepo,
            coroutineContext = testDispatcher
        )
        advanceUntilIdle()

        // Search for "Spider"
        viewModel.handleEvent(LibraryEvent.Search("Spider"))
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.filteredItems.size)
        assertEquals("Spiderman: No Way Home", viewModel.state.value.filteredItems.first().name)

        // Clear Search
        viewModel.handleEvent(LibraryEvent.ClearSearch)
        advanceUntilIdle()

        assertEquals(2, viewModel.state.value.filteredItems.size)
    }

    @Test
    fun testMediaTypeFiltering() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val bookmarkRepo = TestBookmarkRepo()
        val watchProgressRepo = TestWatchProgressRepo()
        val favoriteRepo = TestFavoriteRepo()

        bookmarkRepo.saveBookmark(
            BookmarkEntity(
                accountId = 0,
                id = 1,
                name = "Interstellar",
                url = "https://provider.com/interstellar",
                apiName = "FlixAPI",
                type = TvType.Movie,
                watchType = 1
            )
        )
        bookmarkRepo.saveBookmark(
            BookmarkEntity(
                accountId = 0,
                id = 2,
                name = "Stranger Things",
                url = "https://provider.com/stranger",
                apiName = "FlixAPI",
                type = TvType.TvSeries,
                watchType = 1
            )
        )
        bookmarkRepo.saveBookmark(
            BookmarkEntity(
                accountId = 0,
                id = 3,
                name = "Jujutsu Kaisen",
                url = "https://provider.com/jjk",
                apiName = "AnimeAPI",
                type = TvType.Anime,
                watchType = 1
            )
        )

        val viewModel = LibraryViewModel(
            bookmarkRepository = bookmarkRepo,
            watchProgressRepository = watchProgressRepo,
            favoriteRepository = favoriteRepo,
            coroutineContext = testDispatcher
        )
        advanceUntilIdle()

        // Filter Movies
        viewModel.handleEvent(LibraryEvent.SetFilterType(TvType.Movie))
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.filteredItems.size)
        assertEquals("Interstellar", viewModel.state.value.filteredItems.first().name)

        // Filter Series
        viewModel.handleEvent(LibraryEvent.SetFilterType(TvType.TvSeries))
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.filteredItems.size)
        assertEquals("Stranger Things", viewModel.state.value.filteredItems.first().name)

        // Filter Anime
        viewModel.handleEvent(LibraryEvent.SetFilterType(TvType.Anime))
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.filteredItems.size)
        assertEquals("Jujutsu Kaisen", viewModel.state.value.filteredItems.first().name)
    }

    @Test
    fun testSortingOrders() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val bookmarkRepo = TestBookmarkRepo()
        val watchProgressRepo = TestWatchProgressRepo()
        val favoriteRepo = TestFavoriteRepo()

        bookmarkRepo.saveBookmark(
            BookmarkEntity(
                accountId = 0,
                id = 1,
                name = "Charlie Movie",
                url = "https://provider.com/c",
                apiName = "API",
                bookmarkedTime = 1000L,
                latestUpdatedTime = 1000L,
                watchType = 1
            )
        )
        bookmarkRepo.saveBookmark(
            BookmarkEntity(
                accountId = 0,
                id = 2,
                name = "Alpha Movie",
                url = "https://provider.com/a",
                apiName = "API",
                bookmarkedTime = 3000L,
                latestUpdatedTime = 3000L,
                watchType = 1
            )
        )
        bookmarkRepo.saveBookmark(
            BookmarkEntity(
                accountId = 0,
                id = 3,
                name = "Bravo Movie",
                url = "https://provider.com/b",
                apiName = "API",
                bookmarkedTime = 2000L,
                latestUpdatedTime = 2000L,
                watchType = 1
            )
        )

        // Give Charlie recent watch progress
        watchProgressRepo.setProgress(
            accountId = 0,
            mediaId = 1,
            position = 500L,
            duration = 1000L
        )

        val viewModel = LibraryViewModel(
            bookmarkRepository = bookmarkRepo,
            watchProgressRepository = watchProgressRepo,
            favoriteRepository = favoriteRepo,
            coroutineContext = testDispatcher
        )
        advanceUntilIdle()

        // 1. Sort Alphabetical
        viewModel.handleEvent(LibraryEvent.SetSortOrder(SortOrder.ALPHABETICAL))
        advanceUntilIdle()
        val alphaNames = viewModel.state.value.filteredItems.map { it.name }
        assertEquals(listOf("Alpha Movie", "Bravo Movie", "Charlie Movie"), alphaNames)

        // 2. Sort Recently Added
        viewModel.handleEvent(LibraryEvent.SetSortOrder(SortOrder.RECENTLY_ADDED))
        advanceUntilIdle()
        val recentNames = viewModel.state.value.filteredItems.map { it.name }
        assertEquals(listOf("Alpha Movie", "Bravo Movie", "Charlie Movie"), recentNames)
    }

    @Test
    fun testMergedWatchProgressAndPercentage() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val bookmarkRepo = TestBookmarkRepo()
        val watchProgressRepo = TestWatchProgressRepo()
        val favoriteRepo = TestFavoriteRepo()

        bookmarkRepo.saveBookmark(
            BookmarkEntity(
                accountId = 0,
                id = 10,
                name = "Avatar",
                url = "https://provider.com/avatar",
                apiName = "API",
                type = TvType.Movie,
                watchType = 1
            )
        )

        watchProgressRepo.setProgress(
            accountId = 0,
            mediaId = 10,
            position = 3000L,
            duration = 6000L,
            watchState = 1
        )

        val viewModel = LibraryViewModel(
            bookmarkRepository = bookmarkRepo,
            watchProgressRepository = watchProgressRepo,
            favoriteRepository = favoriteRepo,
            coroutineContext = testDispatcher
        )
        advanceUntilIdle()

        val item = viewModel.state.value.allItems.first()
        assertEquals(10, item.id)
        assertEquals(3000L, item.position)
        assertEquals(6000L, item.duration)
        assertEquals(0.5f, item.progressPercentage)
        assertFalse(item.isWatched)
    }

    @Test
    fun testRemoveBookmark() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val bookmarkRepo = TestBookmarkRepo()
        val watchProgressRepo = TestWatchProgressRepo()
        val favoriteRepo = TestFavoriteRepo()

        bookmarkRepo.saveBookmark(
            BookmarkEntity(
                accountId = 0,
                id = 99,
                name = "To Be Deleted",
                url = "https://provider.com/del",
                apiName = "API",
                watchType = 1
            )
        )

        val viewModel = LibraryViewModel(
            bookmarkRepository = bookmarkRepo,
            watchProgressRepository = watchProgressRepo,
            favoriteRepository = favoriteRepo,
            coroutineContext = testDispatcher
        )
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.allItems.size)

        // Remove bookmark
        viewModel.handleEvent(LibraryEvent.RemoveBookmark(99))
        advanceUntilIdle()

        assertEquals(0, viewModel.state.value.allItems.size)
        assertNull(bookmarkRepo.getBookmark(0, 99))
    }

    @Test
    fun testUnifiedLibraryItemsAndLocalTabs() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val bookmarkRepo = TestBookmarkRepo()
        val watchProgressRepo = TestWatchProgressRepo()
        val favoriteRepo = TestFavoriteRepo()

        bookmarkRepo.saveBookmark(
            BookmarkEntity(
                accountId = 0,
                id = 42,
                name = "Steins;Gate",
                url = "https://provider.com/sg",
                apiName = "AnimeAPI",
                type = TvType.Anime,
                year = 2011,
                score = 9.1,
                watchType = 2 // Completed
            )
        )

        val viewModel = LibraryViewModel(
            bookmarkRepository = bookmarkRepo,
            watchProgressRepository = watchProgressRepo,
            favoriteRepository = favoriteRepo,
            coroutineContext = testDispatcher
        )
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("local", state.selectedProviderId)
        assertTrue(state.availableProviders.isNotEmpty())
        val localProvider = state.availableProviders.first { it.id == "local" }
        assertEquals("Bookmarks", localProvider.name)
        assertTrue(localProvider.isLocal)

        // Verify currentTabs
        assertEquals(6, state.currentTabs.size)
        assertEquals("All", state.currentTabs[0].name)
        assertEquals(1, state.currentTabs[0].count)
        assertEquals("Completed", state.currentTabs[2].name)
        assertEquals(1, state.currentTabs[2].count)

        // Verify displayedItems
        assertEquals(1, state.displayedItems.size)
        val item = state.displayedItems.first()
        assertEquals("42", item.id)
        assertEquals("Steins;Gate", item.name)
        assertEquals("AnimeAPI", item.apiName)
        assertEquals(2011, item.year)
        assertEquals(9.1, item.score)
    }

    @Test
    fun testSelectTabIndexForLocal() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val bookmarkRepo = TestBookmarkRepo()
        val watchProgressRepo = TestWatchProgressRepo()
        val favoriteRepo = TestFavoriteRepo()

        bookmarkRepo.saveBookmark(
            BookmarkEntity(
                accountId = 0,
                id = 1,
                name = "Item 1",
                url = "https://provider.com/1",
                apiName = "API",
                watchType = 1 // Watching
            )
        )
        bookmarkRepo.saveBookmark(
            BookmarkEntity(
                accountId = 0,
                id = 2,
                name = "Item 2",
                url = "https://provider.com/2",
                apiName = "API",
                watchType = 2 // Completed
            )
        )

        val viewModel = LibraryViewModel(
            bookmarkRepository = bookmarkRepo,
            watchProgressRepository = watchProgressRepo,
            favoriteRepository = favoriteRepo,
            coroutineContext = testDispatcher
        )
        advanceUntilIdle()

        assertEquals(2, viewModel.state.value.displayedItems.size)

        // Select Tab Index 1 (Watching)
        viewModel.handleEvent(LibraryEvent.SelectTabIndex(1))
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.selectedTabIndex)
        assertEquals(WatchStatus.WATCHING, viewModel.state.value.selectedTab)
        assertEquals(1, viewModel.state.value.displayedItems.size)
        assertEquals("Item 1", viewModel.state.value.displayedItems.first().name)
    }

    @Test
    fun testProviderPersistenceOnSelectProvider() = runTest {
        val prefRepo = FakeAppPreferenceRepository()
        AppPreferenceManager.init(prefRepo)
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val bookmarkRepo = TestBookmarkRepo()
        val watchProgressRepo = TestWatchProgressRepo()
        val favoriteRepo = TestFavoriteRepo()

        val viewModel = LibraryViewModel(
            bookmarkRepository = bookmarkRepo,
            watchProgressRepository = watchProgressRepo,
            favoriteRepository = favoriteRepo,
            accountId = 0,
            coroutineContext = testDispatcher
        )
        advanceUntilIdle()

        viewModel.handleEvent(LibraryEvent.SelectProvider("local"))
        advanceUntilIdle()
        assertEquals("local", prefRepo.getStringSync(AppPreferenceManager.getLastSyncApiKey(0)))

        viewModel.handleEvent(LibraryEvent.SelectProvider("anilist"))
        advanceUntilIdle()
        assertEquals("anilist", prefRepo.getStringSync(AppPreferenceManager.getLastSyncApiKey(0)))
    }

    @Test
    fun testSavedProviderRestorationFallbackToLocalWhenLoggedOut() = runTest {
        val prefRepo = FakeAppPreferenceRepository()
        AppPreferenceManager.init(prefRepo)
        prefRepo.setStringSync(AppPreferenceManager.getLastSyncApiKey(0), "invalid_provider")

        val testDispatcher = StandardTestDispatcher(testScheduler)
        val bookmarkRepo = TestBookmarkRepo()
        val watchProgressRepo = TestWatchProgressRepo()
        val favoriteRepo = TestFavoriteRepo()

        val viewModel = LibraryViewModel(
            bookmarkRepository = bookmarkRepo,
            watchProgressRepository = watchProgressRepo,
            favoriteRepository = favoriteRepo,
            accountId = 0,
            coroutineContext = testDispatcher
        )
        advanceUntilIdle()

        assertEquals("local", viewModel.state.value.selectedProviderId)
        assertEquals("local", prefRepo.getStringSync(AppPreferenceManager.getLastSyncApiKey(0)))
    }
}

