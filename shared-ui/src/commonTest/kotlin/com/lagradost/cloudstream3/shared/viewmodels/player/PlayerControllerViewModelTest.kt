package com.lagradost.cloudstream3.shared.viewmodels.player

import com.lagradost.cloudstream3.shared.persistence.entity.BookmarkEntity
import com.lagradost.cloudstream3.shared.persistence.entity.ResumeWatchingEntity
import com.lagradost.cloudstream3.shared.persistence.entity.WatchProgressEntity
import com.lagradost.cloudstream3.shared.persistence.repository.BookmarkRepository
import com.lagradost.cloudstream3.shared.persistence.repository.ResumeWatchingRepository
import com.lagradost.cloudstream3.shared.persistence.repository.WatchProgressRepository
import com.lagradost.cloudstream3.shared.player.PlayerEvent
import com.lagradost.cloudstream3.shared.player.PlayerState
import com.lagradost.cloudstream3.shared.player.VideoPlayer
import com.lagradost.cloudstream3.utils.Qualities
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakeVideoPlayer : VideoPlayer {
    private val _stateFlow = MutableStateFlow(PlayerState())
    override val stateFlow: StateFlow<PlayerState> = _stateFlow.asStateFlow()
    override val state: PlayerState get() = _stateFlow.value

    private val _events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<PlayerEvent> = _events.asSharedFlow()

    var lastPlayedUrl: String? = null
    var lastQuality: PlayerQuality? = null
    var lastSubtitles: List<PlayerSubtitleTrack> = emptyList()
    var isPlaying: Boolean = false
    var isPaused: Boolean = false
    var isStopped: Boolean = false
    var lastSeekPos: Long = -1L
    var lastSpeed: Float = 1.0f
    var lastSubtitleUrl: String? = null

    override fun play(quality: PlayerQuality, subtitles: List<PlayerSubtitleTrack>) {
        lastQuality = quality
        lastSubtitles = subtitles
        super.play(quality, subtitles)
    }

    override fun play(url: String) {
        lastPlayedUrl = url
        isPlaying = true
        isPaused = false
        isStopped = false
        _stateFlow.value = _stateFlow.value.copy(isPlaying = true, currentUrl = url)
        _events.tryEmit(PlayerEvent.OnPlay)
    }

    override fun pause() {
        isPlaying = false
        isPaused = true
        _stateFlow.value = _stateFlow.value.copy(isPlaying = false)
        _events.tryEmit(PlayerEvent.OnPause)
    }

    override fun resume() {
        isPlaying = true
        isPaused = false
        _stateFlow.value = _stateFlow.value.copy(isPlaying = true)
        _events.tryEmit(PlayerEvent.OnPlay)
    }

    override fun stop() {
        isPlaying = false
        isStopped = true
        _stateFlow.value = _stateFlow.value.copy(isPlaying = false, currentUrl = null)
        _events.tryEmit(PlayerEvent.OnStop)
    }

    override fun seekTo(positionMs: Long) {
        lastSeekPos = positionMs
        _stateFlow.value = _stateFlow.value.copy(positionMs = positionMs)
        _events.tryEmit(PlayerEvent.OnPositionChanged(positionMs, _stateFlow.value.durationMs))
    }

    override fun setPlaybackSpeed(speed: Float) {
        lastSpeed = speed
    }

    override fun loadSubtitle(url: String, headers: Map<String, String>?) {
        lastSubtitleUrl = url
    }

    fun updatePlayerState(newState: PlayerState) {
        _stateFlow.value = newState
    }

    fun emitPlayerEvent(event: PlayerEvent) {
        _events.tryEmit(event)
    }
}

class FakeWatchProgressRepository : WatchProgressRepository {
    private val progressMap = mutableMapOf<Pair<Int, Int>, WatchProgressEntity>()

    override suspend fun getProgress(accountId: Int, mediaId: Int): WatchProgressEntity? {
        return progressMap[accountId to mediaId]
    }

    override fun getProgressFlow(accountId: Int, mediaId: Int): Flow<WatchProgressEntity?> {
        return MutableStateFlow(progressMap[accountId to mediaId])
    }

    override suspend fun getAllProgress(accountId: Int): List<WatchProgressEntity> {
        return progressMap.filterKeys { it.first == accountId }.values.toList()
    }

    override fun getAllProgressFlow(accountId: Int): Flow<List<WatchProgressEntity>> {
        return MutableStateFlow(progressMap.filterKeys { it.first == accountId }.values.toList())
    }

    override suspend fun setProgress(
        accountId: Int,
        mediaId: Int,
        position: Long,
        duration: Long,
        watchState: Int
    ) {
        progressMap[accountId to mediaId] = WatchProgressEntity(
            accountId = accountId,
            mediaId = mediaId,
            position = position,
            duration = duration,
            watchState = watchState,
            lastUpdated = 1000L
        )
    }

    override suspend fun deleteProgress(accountId: Int, mediaId: Int) {
        progressMap.remove(accountId to mediaId)
    }

    override suspend fun clearProgress(accountId: Int) {
        progressMap.keys.removeAll { it.first == accountId }
    }
}

class FakeResumeWatchingRepository : ResumeWatchingRepository {
    private val resumeMap = mutableMapOf<Pair<Int, Int>, ResumeWatchingEntity>()

    override suspend fun getResumeWatching(accountId: Int, parentId: Int): ResumeWatchingEntity? {
        return resumeMap[accountId to parentId]
    }

    override fun getResumeWatchingFlow(accountId: Int, parentId: Int): Flow<ResumeWatchingEntity?> {
        return MutableStateFlow(resumeMap[accountId to parentId])
    }

    override suspend fun getAllResumeWatching(accountId: Int): List<ResumeWatchingEntity> {
        return resumeMap.filterKeys { it.first == accountId }.values.toList()
    }

    override fun getAllResumeWatchingFlow(accountId: Int): Flow<List<ResumeWatchingEntity>> {
        return MutableStateFlow(resumeMap.filterKeys { it.first == accountId }.values.toList())
    }

    override suspend fun setResumeWatching(
        accountId: Int,
        parentId: Int,
        episodeId: Int?,
        episode: Int?,
        season: Int?,
        isFromDownload: Boolean,
        updateTime: Long?
    ) {
        resumeMap[accountId to parentId] = ResumeWatchingEntity(
            accountId = accountId,
            parentId = parentId,
            episodeId = episodeId,
            episode = episode,
            season = season,
            isFromDownload = isFromDownload,
            updateTime = updateTime ?: 1000L
        )
    }

    override suspend fun saveResumeWatching(resumeWatching: ResumeWatchingEntity) {
        resumeMap[resumeWatching.accountId to resumeWatching.parentId] = resumeWatching
    }

    override suspend fun deleteResumeWatching(accountId: Int, parentId: Int) {
        resumeMap.remove(accountId to parentId)
    }

    override suspend fun clearAll(accountId: Int) {
        resumeMap.keys.removeAll { it.first == accountId }
    }
}

class FakeBookmarkRepository : BookmarkRepository {
    private val bookmarkMap = mutableMapOf<Pair<Int, Int>, BookmarkEntity>()

    override suspend fun getBookmark(accountId: Int, id: Int): BookmarkEntity? {
        return bookmarkMap[accountId to id]
    }

    override fun getBookmarkFlow(accountId: Int, id: Int): Flow<BookmarkEntity?> {
        return MutableStateFlow(bookmarkMap[accountId to id])
    }

    override suspend fun getAllBookmarks(accountId: Int): List<BookmarkEntity> {
        return bookmarkMap.filterKeys { it.first == accountId }.values.toList()
    }

    override fun getAllBookmarksFlow(accountId: Int): Flow<List<BookmarkEntity>> {
        return MutableStateFlow(bookmarkMap.filterKeys { it.first == accountId }.values.toList())
    }

    override suspend fun getBookmarksByWatchType(accountId: Int, watchType: Int): List<BookmarkEntity> {
        return bookmarkMap.filterKeys { it.first == accountId }.values.filter { it.watchType == watchType }
    }

    override fun getBookmarksByWatchTypeFlow(accountId: Int, watchType: Int): Flow<List<BookmarkEntity>> {
        return MutableStateFlow(bookmarkMap.filterKeys { it.first == accountId }.values.filter { it.watchType == watchType })
    }

    override suspend fun saveBookmark(bookmark: BookmarkEntity) {
        bookmarkMap[bookmark.accountId to bookmark.id] = bookmark
    }

    override suspend fun deleteBookmark(accountId: Int, id: Int) {
        bookmarkMap.remove(accountId to id)
    }

    override suspend fun clearAll(accountId: Int) {
        bookmarkMap.keys.removeAll { it.first == accountId }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerControllerViewModelTest {

    @Test
    fun testInitialState() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val player = FakeVideoPlayer()
        val repository = FakeWatchProgressRepository()

        val viewModel = PlayerControllerViewModel(
            player = player,
            watchProgressRepository = repository,
            coroutineContext = testDispatcher,
            progressSaveIntervalMs = 0L
        )

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.currentState
        assertFalse(state.isPlaying)
        assertFalse(state.isBuffering)
        assertTrue(state.isStopped)
        assertEquals(0L, state.positionMs)
        assertEquals(0L, state.durationMs)
        assertEquals(1.0f, state.playbackSpeed)
        assertTrue(state.areControlsVisible)
        assertFalse(state.isControlsLocked)
        assertNull(state.activeSkipTimestamp)

        viewModel.close()
    }

    @Test
    fun testPlaybackControls() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val player = FakeVideoPlayer()
        val repository = FakeWatchProgressRepository()

        val viewModel = PlayerControllerViewModel(
            player = player,
            watchProgressRepository = repository,
            coroutineContext = testDispatcher,
            progressSaveIntervalMs = 0L
        )

        // Load media
        viewModel.onEvent(
            PlayerUiEvent.LoadMedia(
                url = "https://example.com/video.mp4",
                mediaId = 101,
                accountId = 1,
                autoPlay = true
            )
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(player.isPlaying)
        assertEquals("https://example.com/video.mp4", player.lastPlayedUrl)

        // Pause
        viewModel.onEvent(PlayerUiEvent.Pause)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(player.isPaused)
        assertFalse(viewModel.currentState.isPlaying)

        // TogglePlayPause to Play
        viewModel.onEvent(PlayerUiEvent.TogglePlayPause)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(player.isPlaying)
        assertTrue(viewModel.currentState.isPlaying)

        // Stop
        viewModel.onEvent(PlayerUiEvent.Stop)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(player.isStopped)
        assertTrue(viewModel.currentState.isStopped)

        viewModel.close()
    }

    @Test
    fun testSeekAndSpeedControl() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val player = FakeVideoPlayer()
        val repository = FakeWatchProgressRepository()

        val viewModel = PlayerControllerViewModel(
            player = player,
            watchProgressRepository = repository,
            coroutineContext = testDispatcher,
            progressSaveIntervalMs = 0L
        )

        // Set player state with duration
        player.updatePlayerState(
            PlayerState(
                isPlaying = true,
                positionMs = 10_000L,
                durationMs = 100_000L,
                currentUrl = "https://example.com/video.mp4"
            )
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // SeekTo
        viewModel.onEvent(PlayerUiEvent.SeekTo(50_000L))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(50_000L, player.lastSeekPos)
        assertEquals(50_000L, viewModel.currentState.positionMs)

        // SeekBy forward
        viewModel.onEvent(PlayerUiEvent.SeekBy(10_000L))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(60_000L, viewModel.currentState.positionMs)

        // SeekBy backward
        viewModel.onEvent(PlayerUiEvent.SeekBy(-20_000L))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(40_000L, viewModel.currentState.positionMs)

        // Speed control
        viewModel.onEvent(PlayerUiEvent.SetSpeed(1.5f))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1.5f, player.lastSpeed)
        assertEquals(1.5f, viewModel.currentState.playbackSpeed)

        // Speed control clamping
        viewModel.onEvent(PlayerUiEvent.SetSpeed(10.0f))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(4.0f, viewModel.currentState.playbackSpeed)

        viewModel.close()
    }

    @Test
    fun testSkipIntroAndOutro() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val player = FakeVideoPlayer()
        val repository = FakeWatchProgressRepository()

        val viewModel = PlayerControllerViewModel(
            player = player,
            watchProgressRepository = repository,
            coroutineContext = testDispatcher,
            progressSaveIntervalMs = 0L
        )

        val intro = PlayerSkipTimestamp(
            type = PlayerSkipType.Intro,
            startMs = 5000L,
            endMs = 85_000L,
            label = "Opening"
        )
        val outro = PlayerSkipTimestamp(
            type = PlayerSkipType.Ending,
            startMs = 900_000L,
            endMs = 950_000L,
            label = "Ending"
        )

        viewModel.onEvent(
            PlayerUiEvent.LoadMedia(
                url = "https://example.com/video.mp4",
                mediaId = 1,
                skipTimestamps = listOf(intro, outro)
            )
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // Simulate position inside intro
        player.updatePlayerState(PlayerState(isPlaying = true, positionMs = 10_000L, durationMs = 1_000_000L))
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.currentState.activeSkipTimestamp)
        assertEquals(intro, viewModel.currentState.activeSkipTimestamp)

        // Trigger SkipIntro
        viewModel.onEvent(PlayerUiEvent.SkipIntro)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(85_000L, player.lastSeekPos)

        // Simulate position inside outro
        player.updatePlayerState(PlayerState(isPlaying = true, positionMs = 910_000L, durationMs = 1_000_000L))
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.currentState.activeSkipTimestamp)
        assertEquals(outro, viewModel.currentState.activeSkipTimestamp)

        // Trigger SkipOutro
        viewModel.onEvent(PlayerUiEvent.SkipOutro)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(950_000L, player.lastSeekPos)

        viewModel.close()
    }

    @Test
    fun testPlaylistNavigation() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val player = FakeVideoPlayer()
        val repository = FakeWatchProgressRepository()

        val viewModel = PlayerControllerViewModel(
            player = player,
            watchProgressRepository = repository,
            coroutineContext = testDispatcher,
            progressSaveIntervalMs = 0L
        )

        val ep1 = PlayerEpisode(id = 101, name = "Episode 1", qualities = listOf(PlayerQuality(url = "https://example.com/1.mp4", quality = 1080)))
        val ep2 = PlayerEpisode(id = 102, name = "Episode 2", qualities = listOf(PlayerQuality(url = "https://example.com/2.mp4", quality = 1080)))
        val ep3 = PlayerEpisode(id = 103, name = "Episode 3", qualities = listOf(PlayerQuality(url = "https://example.com/3.mp4", quality = 1080)))

        viewModel.onEvent(PlayerUiEvent.LoadPlaylist(playlist = listOf(ep1, ep2, ep3), startIndex = 0))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(101, viewModel.currentState.currentEpisodeId)
        assertEquals(0, viewModel.currentState.currentEpisodeIndex)
        assertTrue(viewModel.currentState.hasNextEpisode)
        assertFalse(viewModel.currentState.hasPreviousEpisode)
        assertEquals("https://example.com/1.mp4", player.lastPlayedUrl)

        // Next Episode -> ep2
        viewModel.onEvent(PlayerUiEvent.NextEpisode)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(102, viewModel.currentState.currentEpisodeId)
        assertEquals(1, viewModel.currentState.currentEpisodeIndex)
        assertTrue(viewModel.currentState.hasNextEpisode)
        assertTrue(viewModel.currentState.hasPreviousEpisode)
        assertEquals("https://example.com/2.mp4", player.lastPlayedUrl)

        // Next Episode -> ep3
        viewModel.onEvent(PlayerUiEvent.NextEpisode)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(103, viewModel.currentState.currentEpisodeId)
        assertEquals(2, viewModel.currentState.currentEpisodeIndex)
        assertFalse(viewModel.currentState.hasNextEpisode)
        assertTrue(viewModel.currentState.hasPreviousEpisode)

        // Previous Episode -> ep2
        viewModel.onEvent(PlayerUiEvent.PreviousEpisode)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(102, viewModel.currentState.currentEpisodeId)

        viewModel.close()
    }

    @Test
    fun testControlsLockAndVisibility() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val player = FakeVideoPlayer()
        val repository = FakeWatchProgressRepository()

        val viewModel = PlayerControllerViewModel(
            player = player,
            watchProgressRepository = repository,
            coroutineContext = testDispatcher,
            progressSaveIntervalMs = 0L
        )

        // Controls visibility
        viewModel.onEvent(PlayerUiEvent.VisibilityChanged(false))
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.currentState.areControlsVisible)

        viewModel.onEvent(PlayerUiEvent.ToggleControlsVisibility)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.currentState.areControlsVisible)

        // Controls lock
        viewModel.onEvent(PlayerUiEvent.ToggleControlsLock(true))
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.currentState.isControlsLocked)

        viewModel.onEvent(PlayerUiEvent.ToggleControlsLock())
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.currentState.isControlsLocked)

        viewModel.close()
    }

    @Test
    fun testWatchProgressPersistenceAndAutoResume() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val player = FakeVideoPlayer()
        val repository = FakeWatchProgressRepository()

        val viewModel = PlayerControllerViewModel(
            player = player,
            watchProgressRepository = repository,
            coroutineContext = testDispatcher,
            progressSaveIntervalMs = 0L
        )

        val ep = PlayerEpisode(
            id = 555,
            name = "Test Movie",
            qualities = listOf(PlayerQuality(url = "https://example.com/movie.mp4", quality = 1080))
        )

        viewModel.onEvent(PlayerUiEvent.LoadEpisode(episode = ep, accountId = 1))
        testDispatcher.scheduler.advanceUntilIdle()

        // Set playback position to 50% (watching)
        player.updatePlayerState(
            PlayerState(
                isPlaying = true,
                positionMs = 50_000L,
                durationMs = 100_000L,
                currentUrl = "https://example.com/movie.mp4"
            )
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // Pause to trigger immediate progress save
        viewModel.onEvent(PlayerUiEvent.Pause)
        testDispatcher.scheduler.advanceUntilIdle()

        val savedWatching = repository.getProgress(1, 555)
        assertNotNull(savedWatching)
        assertEquals(50_000L, savedWatching.position)
        assertEquals(100_000L, savedWatching.duration)
        assertEquals(1, savedWatching.watchState) // 1 = Watching

        // Set playback position to 95% (watched)
        player.updatePlayerState(
            PlayerState(
                isPlaying = true,
                positionMs = 95_000L,
                durationMs = 100_000L,
                currentUrl = "https://example.com/movie.mp4"
            )
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(PlayerUiEvent.SaveProgressNow)
        testDispatcher.scheduler.advanceUntilIdle()

        val savedWatched = repository.getProgress(1, 555)
        assertNotNull(savedWatched)
        assertEquals(95_000L, savedWatched.position)
        assertEquals(2, savedWatched.watchState) // 2 = Watched

        viewModel.close()

        // Test auto resume in new instance
        val newPlayer = FakeVideoPlayer()
        // Put progress in valid resume range (30%)
        repository.setProgress(accountId = 2, mediaId = 777, position = 30_000L, duration = 100_000L, watchState = 1)

        val newViewModel = PlayerControllerViewModel(
            player = newPlayer,
            watchProgressRepository = repository,
            coroutineContext = testDispatcher,
            progressSaveIntervalMs = 0L
        )

        val epResume = PlayerEpisode(
            id = 777,
            name = "Resume Ep",
            qualities = listOf(PlayerQuality(url = "https://example.com/resumable.mp4", quality = 1080))
        )

        newViewModel.onEvent(PlayerUiEvent.LoadEpisode(episode = epResume, accountId = 2))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(30_000L, newPlayer.lastSeekPos)
        assertEquals(30_000L, newViewModel.currentState.positionMs)

        newViewModel.close()
    }

    @Test
    fun testPeriodicWatchProgressSave() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val player = FakeVideoPlayer()
        val repository = FakeWatchProgressRepository()

        val viewModel = PlayerControllerViewModel(
            player = player,
            watchProgressRepository = repository,
            coroutineContext = testDispatcher,
            progressSaveIntervalMs = 5000L
        )

        val ep = PlayerEpisode(
            id = 888,
            name = "Periodic Test Ep",
            qualities = listOf(PlayerQuality(url = "https://example.com/video.mp4", quality = 1080))
        )

        viewModel.onEvent(PlayerUiEvent.LoadEpisode(episode = ep, accountId = 1))
        testDispatcher.scheduler.runCurrent()

        player.updatePlayerState(
            PlayerState(
                isPlaying = true,
                positionMs = 25_000L,
                durationMs = 100_000L,
                currentUrl = "https://example.com/video.mp4"
            )
        )
        testDispatcher.scheduler.runCurrent()

        // Advance by 5000ms to trigger periodic saver ticker
        testDispatcher.scheduler.advanceTimeBy(5000L)
        testDispatcher.scheduler.runCurrent()

        val saved = repository.getProgress(1, 888)
        assertNotNull(saved)
        assertEquals(25_000L, saved.position)

        viewModel.close()
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun testLosslessPlayerQualityAndSubtitlesPassing() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val player = FakeVideoPlayer()
        val viewModel = PlayerControllerViewModel(
            player = player,
            coroutineContext = testDispatcher,
            progressSaveIntervalMs = 0L
        )

        val q1 = PlayerQuality(id = "1080p", name = "1080p", quality = 1080, url = "https://example.com/1080.mp4")
        val q2 = PlayerQuality(id = "720p", name = "720p", quality = 720, url = "https://example.com/720.mp4")
        val sub1 = PlayerSubtitleTrack(id = "en", name = "English", url = "https://example.com/en.srt", isDefault = true)
        val sub2 = PlayerSubtitleTrack(id = "es", name = "Spanish", url = "https://example.com/es.srt")

        val ep = PlayerEpisode(
            id = 999,
            name = "Test Ep",
            qualities = listOf(q1, q2),
            subtitles = listOf(sub1, sub2)
        )

        viewModel.onEvent(PlayerUiEvent.LoadEpisode(episode = ep, accountId = 1))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(q1, player.lastQuality)
        assertEquals(listOf(sub1, sub2), player.lastSubtitles)

        viewModel.onEvent(PlayerUiEvent.SelectQuality(q2))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(q2, player.lastQuality)
        assertEquals(listOf(sub1, sub2), player.lastSubtitles)

        viewModel.close()
    }

    @Test
    fun testScreenLockAndPinProtection() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val player = FakeVideoPlayer()
        val viewModel = PlayerControllerViewModel(
            player = player,
            coroutineContext = testDispatcher,
            progressSaveIntervalMs = 0L
        )

        // Lock controls without PIN
        viewModel.onEvent(PlayerUiEvent.ToggleControlsLock(true))
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.currentState.isControlsLocked)
        assertFalse(viewModel.currentState.isPinLocked)

        // Unlock controls
        viewModel.onEvent(PlayerUiEvent.ToggleControlsLock(false))
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.currentState.isControlsLocked)

        // Set 4-digit PIN
        viewModel.onEvent(PlayerUiEvent.SetLockPin("1234"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("1234", viewModel.currentState.lockPin)
        assertTrue(viewModel.currentState.isPinLocked)

        // Lock controls with PIN
        viewModel.onEvent(PlayerUiEvent.ToggleControlsLock(true))
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.currentState.isControlsLocked)

        // Show PIN unlock dialog
        viewModel.onEvent(PlayerUiEvent.ShowLockPinDialog(true, LockPinDialogMode.Unlock))
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.currentState.showLockPinDialog)
        assertEquals(LockPinDialogMode.Unlock, viewModel.currentState.lockPinDialogMode)

        // Wrong PIN should not unlock
        viewModel.onEvent(PlayerUiEvent.UnlockWithPin("9999"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.currentState.isControlsLocked)

        // Correct PIN unlocks successfully
        viewModel.onEvent(PlayerUiEvent.UnlockWithPin("1234"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.currentState.isControlsLocked)
        assertFalse(viewModel.currentState.showLockPinDialog)
        assertTrue(viewModel.currentState.areControlsVisible)

        // Clear PIN
        viewModel.onEvent(PlayerUiEvent.ClearLockPin)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.currentState.lockPin)
        assertFalse(viewModel.currentState.isPinLocked)

        viewModel.close()
    }

    @Test
    fun testAudioTrackSelection() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val player = FakeVideoPlayer()
        val viewModel = PlayerControllerViewModel(
            player = player,
            coroutineContext = testDispatcher,
            progressSaveIntervalMs = 0L
        )

        val track1 = PlayerAudioTrack(id = "a1", name = "Japanese Original", languageCode = "ja", isDefault = true)
        val track2 = PlayerAudioTrack(id = "a2", name = "English Dub", languageCode = "en")

        viewModel.onEvent(PlayerUiEvent.SelectAudioTrack(track2))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(track2, viewModel.currentState.selectedAudioTrack)

        viewModel.onEvent(PlayerUiEvent.SelectAudioTrack(null))
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.currentState.selectedAudioTrack)

        viewModel.close()
    }

    @Test
    fun testResumeWatchingRepositorySaveAndLoadMediaParentId() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val player = FakeVideoPlayer()
        val watchRepo = FakeWatchProgressRepository()
        val resumeRepo = FakeResumeWatchingRepository()

        val viewModel = PlayerControllerViewModel(
            player = player,
            watchProgressRepository = watchRepo,
            resumeWatchingRepository = resumeRepo,
            coroutineContext = testDispatcher,
            progressSaveIntervalMs = 0L
        )

        // Load media with parentId and mediaId
        viewModel.onEvent(
            PlayerUiEvent.LoadMedia(
                url = "https://example.com/stream.mp4",
                mediaId = 200,
                parentId = 100,
                accountId = 1,
                autoPlay = true
            )
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(100, viewModel.currentState.parentId)
        assertEquals(200, viewModel.currentState.currentEpisodeId)

        // Set playback state and trigger progress save
        player.updatePlayerState(
            PlayerState(
                isPlaying = true,
                positionMs = 45_000L,
                durationMs = 90_000L,
                currentUrl = "https://example.com/stream.mp4"
            )
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(PlayerUiEvent.Pause)
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify WatchProgressRepository saved correctly
        val progress = watchRepo.getProgress(1, 200)
        assertNotNull(progress)
        assertEquals(45_000L, progress.position)
        assertEquals(90_000L, progress.duration)

        // Verify ResumeWatchingRepository saved correctly with parentId and episodeId
        val resume = resumeRepo.getResumeWatching(1, 100)
        assertNotNull(resume)
        assertEquals(100, resume.parentId)
        assertEquals(200, resume.episodeId)

        viewModel.close()
    }

    @Test
    fun testAutoResumeSavedProgressInLoadMedia() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val player = FakeVideoPlayer()
        val watchRepo = FakeWatchProgressRepository()

        // Pre-populate progress
        watchRepo.setProgress(
            accountId = 1,
            mediaId = 500,
            position = 25_000L,
            duration = 100_000L,
            watchState = 1
        )

        val viewModel = PlayerControllerViewModel(
            player = player,
            watchProgressRepository = watchRepo,
            coroutineContext = testDispatcher,
            progressSaveIntervalMs = 0L
        )

        // Load media without explicit resumePosition
        viewModel.onEvent(
            PlayerUiEvent.LoadMedia(
                url = "https://example.com/movie.mp4",
                mediaId = 500,
                parentId = 500,
                accountId = 1,
                autoPlay = true
            )
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(25_000L, viewModel.currentState.positionMs)
        assertEquals(25_000L, player.lastSeekPos)

        viewModel.close()
    }

    @Test
    fun testBookmarkRepositoryUpdatedOnSaveProgress() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val player = FakeVideoPlayer()
        val watchRepo = FakeWatchProgressRepository()
        val resumeRepo = FakeResumeWatchingRepository()
        val bookmarkRepo = FakeBookmarkRepository()

        // Seed initial bookmark
        bookmarkRepo.saveBookmark(
            BookmarkEntity(
                accountId = 1,
                id = 100,
                name = "Test Series",
                url = "https://example.com/series",
                apiName = "TestProvider",
                latestUpdatedTime = 100L
            )
        )

        val viewModel = PlayerControllerViewModel(
            player = player,
            watchProgressRepository = watchRepo,
            resumeWatchingRepository = resumeRepo,
            bookmarkRepository = bookmarkRepo,
            coroutineContext = testDispatcher,
            progressSaveIntervalMs = 0L
        )

        viewModel.onEvent(
            PlayerUiEvent.LoadMedia(
                url = "https://example.com/video.mp4",
                mediaId = 200,
                parentId = 100,
                accountId = 1,
                autoPlay = true
            )
        )
        testDispatcher.scheduler.advanceUntilIdle()

        player.updatePlayerState(
            PlayerState(
                isPlaying = true,
                positionMs = 50_000L,
                durationMs = 100_000L,
                currentUrl = "https://example.com/video.mp4"
            )
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(PlayerUiEvent.SaveProgressNow)
        testDispatcher.scheduler.advanceUntilIdle()

        val updatedBookmark = bookmarkRepo.getBookmark(1, 100)
        assertNotNull(updatedBookmark)
        assertTrue(updatedBookmark.latestUpdatedTime > 100L)

        viewModel.close()
    }

    @Test
    fun testAutoQualitySelectionPrioritizesHighestResolutionOnUpdateQualities() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val player = FakeVideoPlayer()
        val repository = FakeWatchProgressRepository()

        // Seed watch progress to verify seeking to resume position
        repository.setProgress(accountId = 1, mediaId = 999, position = 42_000L, duration = 100_000L, watchState = 1)

        val viewModel = PlayerControllerViewModel(
            player = player,
            watchProgressRepository = repository,
            coroutineContext = testDispatcher,
            progressSaveIntervalMs = 0L
        )

        // Episode loaded without initial qualities (as happens during Quick Play from Home / Continue Watching)
        val ep = PlayerEpisode(id = 999, name = "Test Ep", qualities = emptyList())
        viewModel.onEvent(PlayerUiEvent.LoadEpisode(episode = ep, accountId = 1))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(42_000L, viewModel.currentState.positionMs)
        assertFalse(player.isPlaying)

        // Extractor finishes extracting links in unsorted order (e.g. 480p, 4K, 720p, 1080p, Auto)
        val q480 = PlayerQuality(id = "480", name = "SD Server", quality = 480, url = "https://example.com/480.mp4")
        val q2160 = PlayerQuality(id = "2160", name = "4K Ultra Server", quality = 2160, url = "https://example.com/2160.mp4")
        val q720 = PlayerQuality(id = "720", name = "HD 720 Server", quality = 720, url = "https://example.com/720.mp4")
        val q1080 = PlayerQuality(id = "1080", name = "FHD Server", quality = 1080, url = "https://example.com/1080.mp4")
        val qAuto = PlayerQuality(id = "auto", name = "Auto Stream", quality = 0, url = "https://example.com/auto.m3u8", isAuto = true)

        viewModel.onEvent(
            PlayerUiEvent.UpdateQualitiesAndSubtitles(
                qualities = listOf(q480, q2160, q720, q1080, qAuto),
                subtitles = emptyList()
            )
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // 4K (2160) should be automatically selected and played
        assertEquals(q2160, viewModel.currentState.selectedQuality)
        assertEquals("https://example.com/2160.mp4", viewModel.currentState.currentUrl)
        assertEquals(q2160, player.lastQuality)
        assertTrue(player.isPlaying)
        assertEquals(42_000L, player.lastSeekPos)

        // Available qualities should be sorted descending by resolution: 2160 > 1080 > 720 > 480 > 0
        assertEquals(listOf(q2160, q1080, q720, q480, qAuto), viewModel.currentState.availableQualities)

        viewModel.close()
    }

    @Test
    fun testAutoQualitySelectionParsesResolutionFromNameWhenQualityIsUnknown() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val player = FakeVideoPlayer()

        val viewModel = PlayerControllerViewModel(
            player = player,
            coroutineContext = testDispatcher,
            progressSaveIntervalMs = 0L
        )

        val ep = PlayerEpisode(id = 1001, name = "Test Ep 2", qualities = emptyList())
        viewModel.onEvent(PlayerUiEvent.LoadEpisode(episode = ep, accountId = 1))
        testDispatcher.scheduler.advanceUntilIdle()

        val qUnknownSd = PlayerQuality(id = "s1", name = "Source 1 - SD 480p", quality = Qualities.Unknown.value, url = "https://example.com/s1.mp4")
        val qUnknown4k = PlayerQuality(id = "s2", name = "Source 2 - 4K UHD", quality = 0, url = "https://example.com/s2.mp4")
        val qUnknownFhd = PlayerQuality(id = "s3", name = "Source 3 - 1080p FHD", quality = Qualities.Unknown.value, url = "https://example.com/s3.mp4")
        val qUnknownHd = PlayerQuality(id = "s4", name = "Source 4 - 720p HD", quality = 0, url = "https://example.com/s4.mp4")

        viewModel.onEvent(
            PlayerUiEvent.UpdateQualitiesAndSubtitles(
                qualities = listOf(qUnknownSd, qUnknown4k, qUnknownFhd, qUnknownHd),
                subtitles = emptyList()
            )
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // 4K UHD should be parsed and played first
        assertEquals(qUnknown4k, viewModel.currentState.selectedQuality)
        assertEquals("https://example.com/s2.mp4", viewModel.currentState.currentUrl)
        assertEquals(listOf(qUnknown4k, qUnknownFhd, qUnknownHd, qUnknownSd), viewModel.currentState.availableQualities)

        viewModel.close()
    }

    @Test
    fun testQualitySortingInLoadEpisode() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val player = FakeVideoPlayer()

        val viewModel = PlayerControllerViewModel(
            player = player,
            coroutineContext = testDispatcher,
            progressSaveIntervalMs = 0L
        )

        val q360 = PlayerQuality(id = "360", name = "360p", quality = 360, url = "https://example.com/360.mp4")
        val q720 = PlayerQuality(id = "720", name = "720p", quality = 720, url = "https://example.com/720.mp4")
        val q1080 = PlayerQuality(id = "1080", name = "1080p", quality = 1080, url = "https://example.com/1080.mp4")

        val ep = PlayerEpisode(
            id = 1002,
            name = "Test Ep 3",
            qualities = listOf(q360, q720, q1080)
        )

        viewModel.onEvent(PlayerUiEvent.LoadEpisode(episode = ep, accountId = 1))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(q1080, viewModel.currentState.selectedQuality)
        assertEquals(q1080, player.lastQuality)
        assertEquals(listOf(q1080, q720, q360), viewModel.currentState.availableQualities)

        viewModel.close()
    }
}

