package com.lagradost.cloudstream3.shared.viewmodels.downloads

import cloudstream.shared_ui.generated.resources.*
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.shared.downloads.DefaultDownloadDirectoryProvider
import com.lagradost.cloudstream3.shared.downloads.DownloadEngine
import com.lagradost.cloudstream3.shared.downloads.DownloadProgress
import com.lagradost.cloudstream3.shared.downloads.DownloadQueueItem
import com.lagradost.cloudstream3.shared.downloads.DownloadStatus
import com.lagradost.cloudstream3.shared.downloads.KmpDownloadEngineImpl
import com.lagradost.cloudstream3.shared.persistence.dao.DownloadCacheDao
import com.lagradost.cloudstream3.shared.persistence.entity.DownloadEpisodeEntity
import com.lagradost.cloudstream3.shared.persistence.entity.DownloadHeaderEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private class TestDownloadCacheDao : DownloadCacheDao {
    val headersMap = mutableMapOf<Int, DownloadHeaderEntity>()
    val episodesMap = mutableMapOf<Int, DownloadEpisodeEntity>()
    val headersFlow = MutableStateFlow<List<DownloadHeaderEntity>>(emptyList())
    val episodesFlow = MutableStateFlow<List<DownloadEpisodeEntity>>(emptyList())

    override suspend fun getHeader(id: Int): DownloadHeaderEntity? = headersMap[id]

    override suspend fun getAllHeaders(): List<DownloadHeaderEntity> = headersMap.values.toList()

    override fun getAllHeadersFlow(): Flow<List<DownloadHeaderEntity>> = headersFlow

    override suspend fun upsertHeader(header: DownloadHeaderEntity) {
        headersMap[header.id] = header
        headersFlow.value = headersMap.values.toList()
    }

    override suspend fun insertHeaders(headers: List<DownloadHeaderEntity>) {
        headers.forEach { headersMap[it.id] = it }
        headersFlow.value = headersMap.values.toList()
    }

    override suspend fun deleteHeader(id: Int) {
        headersMap.remove(id)
        headersFlow.value = headersMap.values.toList()
    }

    override suspend fun getEpisode(id: Int): DownloadEpisodeEntity? = episodesMap[id]

    override suspend fun getEpisodesForParent(parentId: Int): List<DownloadEpisodeEntity> {
        return episodesMap.values.filter { it.parentId == parentId }
    }

    override fun getEpisodesForParentFlow(parentId: Int): Flow<List<DownloadEpisodeEntity>> {
        return episodesFlow.map { list -> list.filter { it.parentId == parentId } }
    }

    override suspend fun upsertEpisode(episode: DownloadEpisodeEntity) {
        episodesMap[episode.id] = episode
        episodesFlow.value = episodesMap.values.toList()
    }

    override suspend fun insertEpisodes(episodes: List<DownloadEpisodeEntity>) {
        episodes.forEach { episodesMap[it.id] = it }
        episodesFlow.value = episodesMap.values.toList()
    }

    override suspend fun deleteEpisode(id: Int) {
        episodesMap.remove(id)
        episodesFlow.value = episodesMap.values.toList()
    }

    override suspend fun deleteEpisodesForParent(parentId: Int) {
        val keysToRemove = episodesMap.filterValues { it.parentId == parentId }.keys
        keysToRemove.forEach { episodesMap.remove(it) }
        episodesFlow.value = episodesMap.values.toList()
    }

    override suspend fun clearAllHeaders() {
        headersMap.clear()
        headersFlow.value = emptyList()
    }

    override suspend fun clearAllEpisodes() {
        episodesMap.clear()
        episodesFlow.value = emptyList()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModelTest {

    @Test
    fun testInitialState() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val dao = TestDownloadCacheDao()
        val downloadService = DefaultDownloadService()

        val viewModel = DownloadsViewModel(
            downloadCacheDao = dao,
            downloadService = downloadService,
            coroutineContext = testDispatcher
        )
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(DownloadsTab.DOWNLOADING, state.selectedTab)
        assertTrue(state.activeDownloads.isEmpty())
        assertTrue(state.completedGroups.isEmpty())
        assertEquals(0, state.totalActiveDownloadsCount)
        assertFalse(state.isLoading)
    }

    @Test
    fun testTabSwitching() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val dao = TestDownloadCacheDao()
        val downloadService = DefaultDownloadService()

        val viewModel = DownloadsViewModel(
            downloadCacheDao = dao,
            downloadService = downloadService,
            coroutineContext = testDispatcher
        )
        advanceUntilIdle()

        viewModel.handleEvent(DownloadsEvent.SwitchTab(DownloadsTab.COMPLETED))
        assertEquals(DownloadsTab.COMPLETED, viewModel.state.value.selectedTab)

        viewModel.handleEvent(DownloadsEvent.SwitchTab(DownloadsTab.DOWNLOADING))
        assertEquals(DownloadsTab.DOWNLOADING, viewModel.state.value.selectedTab)
    }

    @Test
    fun testActiveDownloadLifecycle() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val dao = TestDownloadCacheDao()
        val downloadService = DefaultDownloadService()

        val viewModel = DownloadsViewModel(
            downloadCacheDao = dao,
            downloadService = downloadService,
            coroutineContext = testDispatcher
        )
        advanceUntilIdle()

        val item1 = ActiveDownloadItem(
            id = 101,
            title = "Episode 1",
            headerName = "Cyberpunk: Edgerunners",
            status = DownloadItemStatus.DOWNLOADING,
            speedBytesPerSec = 1024L * 1024L * 5L, // 5 MB/s
            bytesDownloaded = 100L * 1024L * 1024L,
            totalBytes = 500L * 1024L * 1024L
        )
        downloadService.enqueueDownload(item1)
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.activeDownloads.size)
        assertEquals(1, viewModel.state.value.totalActiveDownloadsCount)

        // Pause individual download
        viewModel.handleEvent(DownloadsEvent.PauseDownload(101))
        advanceUntilIdle()
        val pausedItem = viewModel.state.value.activeDownloads.first()
        assertEquals(DownloadItemStatus.PAUSED, pausedItem.status)

        // Resume download
        viewModel.handleEvent(DownloadsEvent.ResumeDownload(101))
        advanceUntilIdle()
        val resumedItem = viewModel.state.value.activeDownloads.first()
        assertEquals(DownloadItemStatus.DOWNLOADING, resumedItem.status)

        // Cancel individual download
        viewModel.handleEvent(DownloadsEvent.CancelDownload(101))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.activeDownloads.isEmpty())
    }

    @Test
    fun testQueueGlobalActions() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val dao = TestDownloadCacheDao()
        val downloadService = DefaultDownloadService()

        val viewModel = DownloadsViewModel(
            downloadCacheDao = dao,
            downloadService = downloadService,
            coroutineContext = testDispatcher
        )
        advanceUntilIdle()

        val item1 = ActiveDownloadItem(
            id = 1,
            title = "Ep 1",
            headerName = "Arcane",
            status = DownloadItemStatus.DOWNLOADING
        )
        val item2 = ActiveDownloadItem(
            id = 2,
            title = "Ep 2",
            headerName = "Arcane",
            status = DownloadItemStatus.DOWNLOADING
        )
        downloadService.enqueueDownload(item1)
        downloadService.enqueueDownload(item2)
        advanceUntilIdle()

        assertEquals(2, viewModel.state.value.activeDownloads.size)

        // Pause All
        viewModel.handleEvent(DownloadsEvent.PauseAll)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.activeDownloads.all { it.status == DownloadItemStatus.PAUSED })

        // Resume All
        viewModel.handleEvent(DownloadsEvent.ResumeAll)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.activeDownloads.all { it.status == DownloadItemStatus.DOWNLOADING })

        // Cancel All
        viewModel.handleEvent(DownloadsEvent.CancelAll)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.activeDownloads.isEmpty())
    }

    @Test
    fun testCompletedDownloadsObservationAndDeletion() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val dao = TestDownloadCacheDao()
        val downloadService = DefaultDownloadService()

        // Insert header and episode in fake Room
        dao.upsertHeader(
            DownloadHeaderEntity(
                id = 50,
                apiName = "ProviderA",
                url = "https://show.com/50",
                type = TvType.TvSeries,
                name = "Steins;Gate",
                poster = "https://show.com/poster.jpg",
                cacheTime = 1000L
            )
        )
        dao.upsertEpisode(
            DownloadEpisodeEntity(
                id = 501,
                parentId = 50,
                name = "Prologue of the Beginning and the End",
                poster = null,
                episode = 1,
                season = 1,
                description = "Episode 1 summary",
                cacheTime = 1000L
            )
        )

        val viewModel = DownloadsViewModel(
            downloadCacheDao = dao,
            downloadService = downloadService,
            coroutineContext = testDispatcher
        )
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(1, state.completedGroups.size)
        val group = state.completedGroups.first()
        assertEquals("Steins;Gate", group.header.name)
        assertEquals(1, group.episodeCount)

        // Search filtering
        viewModel.handleEvent(DownloadsEvent.SearchQueryChanged("steins"))
        assertEquals(1, viewModel.state.value.filteredCompletedGroups.size)

        viewModel.handleEvent(DownloadsEvent.SearchQueryChanged("nonexistent"))
        assertEquals(0, viewModel.state.value.filteredCompletedGroups.size)

        viewModel.handleEvent(DownloadsEvent.SearchQueryChanged(""))
        assertEquals(1, viewModel.state.value.filteredCompletedGroups.size)

        // Delete Episode
        viewModel.handleEvent(DownloadsEvent.DeleteCompletedEpisode(501, 50))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.completedGroups.isEmpty())
    }

    @Test
    fun testByteFormattingUtilities() {
        assertEquals("0 B", formatBytes(0L))
        assertEquals("500 B", formatBytes(500L))
        assertEquals("1 KB", formatBytes(1024L))
        assertEquals("1.5 MB", formatBytes((1.5 * 1024 * 1024).toLong()))
        assertEquals("2 GB", formatBytes(2L * 1024 * 1024 * 1024))
        assertEquals("1.5 MB/s", formatByteRate((1.5 * 1024 * 1024).toLong()))
    }

    @Test
    fun testKmpDownloadEngineLifecycle() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = CoroutineScope(testDispatcher)
        val tempDir = File(System.getProperty("java.io.tmpdir") ?: "/tmp", "cs_download_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        val dao = TestDownloadCacheDao()
        val directoryProvider = DefaultDownloadDirectoryProvider(customBaseDir = tempDir)
        val engine = KmpDownloadEngineImpl(
            downloadCacheDao = dao,
            directoryProvider = directoryProvider,
            scope = testScope
        )

        val item = DownloadQueueItem(
            id = "1001",
            parentId = 100,
            episodeId = 1001,
            url = "http://127.0.0.1:9999/test.mp4",
            headerName = "Frieren",
            episodeName = "The Journey's End",
            episodeIndex = 1,
            seasonIndex = 1,
            posterUrl = "https://example.com/poster.jpg"
        )

        // Start download
        engine.startDownload(item)
        advanceUntilIdle()

        val queue = engine.queueFlow.value
        assertEquals(1, queue.size)
        assertEquals("1001", queue.first().id)

        val progress = engine.progressFlow.value["1001"]
        assertNotNull(progress)

        // Pause download
        engine.pauseDownload("1001")
        advanceUntilIdle()
        val pausedProgress = engine.progressFlow.value["1001"]
        assertEquals(DownloadStatus.PAUSED, pausedProgress?.status)

        // Resume download
        engine.resumeDownload("1001")
        advanceUntilIdle()
        val resumedProgress = engine.progressFlow.value["1001"]
        assertTrue(resumedProgress?.status == DownloadStatus.QUEUED || resumedProgress?.status == DownloadStatus.DOWNLOADING || resumedProgress?.status == DownloadStatus.FAILED)

        // Progress Flow check
        val progressFlowItem = engine.getDownloadProgressFlow("1001").first()
        assertEquals("1001", progressFlowItem.id)

        // Cancel download
        engine.cancelDownload("1001")
        advanceUntilIdle()
        assertTrue(engine.queueFlow.value.isEmpty())
        assertFalse(engine.progressFlow.value.containsKey("1001"))

        // Cleanup temp dir
        tempDir.deleteRecursively()
    }

    @Test
    fun testDownloadsViewModelWithKmpDownloadEngine() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = CoroutineScope(testDispatcher)
        val dao = TestDownloadCacheDao()
        val tempDir = File(System.getProperty("java.io.tmpdir") ?: "/tmp", "cs_vm_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        val directoryProvider = DefaultDownloadDirectoryProvider(customBaseDir = tempDir)
        val engine = KmpDownloadEngineImpl(
            downloadCacheDao = dao,
            directoryProvider = directoryProvider,
            scope = testScope
        )

        val viewModel = DownloadsViewModel(
            downloadCacheDao = dao,
            downloadEngine = engine,
            directoryProvider = directoryProvider,
            coroutineContext = testDispatcher
        )
        advanceUntilIdle()

        val item = DownloadQueueItem(
            id = "2001",
            parentId = 200,
            episodeId = 2001,
            url = "http://127.0.0.1:9999/video.mp4",
            headerName = "Vinland Saga",
            episodeName = "Somewhere Not Here",
            episodeIndex = 1,
            seasonIndex = 1
        )

        viewModel.enqueueDownload(item)
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.activeDownloads.size)
        val activeItem = viewModel.state.value.activeDownloads.first()
        assertEquals(2001, activeItem.id)
        assertEquals("Vinland Saga", activeItem.headerName)

        // Pause via ViewModel
        viewModel.handleEvent(DownloadsEvent.PauseDownload(2001))
        advanceUntilIdle()
        assertEquals(DownloadItemStatus.PAUSED, viewModel.state.value.activeDownloads.first().status)

        // Resume via ViewModel
        viewModel.handleEvent(DownloadsEvent.ResumeDownload(2001))
        advanceUntilIdle()

        // Cancel via ViewModel
        viewModel.handleEvent(DownloadsEvent.CancelDownload(2001))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.activeDownloads.isEmpty())

        tempDir.deleteRecursively()
    }

    @Test
    fun testRealStorageMetricsCalculation() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val dao = TestDownloadCacheDao()
        val tempDir = File(System.getProperty("java.io.tmpdir") ?: "/tmp", "cs_storage_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        val directoryProvider = DefaultDownloadDirectoryProvider(customBaseDir = tempDir)
        val downloadService = DefaultDownloadService()

        val epFile = directoryProvider.getEpisodeFile(300, 3001)
        epFile.writeBytes(ByteArray(1024 * 100)) // 100 KB file

        dao.upsertHeader(
            DownloadHeaderEntity(
                id = 300,
                apiName = "ProviderTest",
                url = "https://show.com/300",
                type = TvType.TvSeries,
                name = "Test Series",
                poster = null,
                cacheTime = 1000L
            )
        )
        dao.upsertEpisode(
            DownloadEpisodeEntity(
                id = 3001,
                parentId = 300,
                name = "Episode 1",
                poster = null,
                episode = 1,
                season = 1,
                description = "Desc",
                cacheTime = 1000L
            )
        )

        val viewModel = DownloadsViewModel(
            downloadCacheDao = dao,
            downloadService = downloadService,
            directoryProvider = directoryProvider,
            coroutineContext = testDispatcher
        )
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(1, state.completedGroups.size)
        val group = state.completedGroups.first()
        assertEquals(1024L * 100L, group.totalEstimatedSizeBytes)
        assertEquals(1024L * 100L, state.storageUsage.appBytes)
        assertTrue(state.storageUsage.totalBytes > 0L)
        assertTrue(state.storageUsage.freeBytes > 0L)

        // Delete episode and verify storage updates
        viewModel.handleEvent(DownloadsEvent.DeleteCompletedEpisode(3001, 300))
        advanceUntilIdle()

        val updatedState = viewModel.state.value
        assertTrue(updatedState.completedGroups.isEmpty())
        assertEquals(0L, updatedState.storageUsage.appBytes)

        tempDir.deleteRecursively()
    }

    @Test
    fun testPlayOfflineEffect() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val dao = TestDownloadCacheDao()
        val downloadService = DefaultDownloadService()
        val viewModel = DownloadsViewModel(
            downloadCacheDao = dao,
            downloadService = downloadService,
            coroutineContext = testDispatcher
        )
        advanceUntilIdle()

        val episode = DownloadEpisodeEntity(
            id = 4001,
            parentId = 400,
            name = null,
            poster = null,
            episode = 1,
            season = 1,
            description = null,
            cacheTime = 1000L
        )

        var effect: com.lagradost.cloudstream3.shared.mvi.UiEffect? = null
        val effectJob = launch {
            viewModel.effects.collect { effect = it }
        }

        viewModel.handleEvent(DownloadsEvent.PlayOffline(episode, null))
        advanceUntilIdle()

        assertNotNull(effect)
        assertTrue(effect is DownloadsEffect.NavigateToPlayer)
        val nav = effect as DownloadsEffect.NavigateToPlayer
        assertEquals(Res.string.offline_playback, nav.titleRes)
        effectJob.cancel()
    }
}
