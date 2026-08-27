package com.lagradost.cloudstream3.shared.viewmodels.player

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.shared.mvi.MviViewModel
import com.lagradost.cloudstream3.shared.persistence.entity.ResumeWatchingEntity
import com.lagradost.cloudstream3.shared.persistence.repository.BookmarkRepository
import com.lagradost.cloudstream3.shared.persistence.repository.ResumeWatchingRepository
import com.lagradost.cloudstream3.shared.persistence.repository.WatchProgressRepository
import com.lagradost.cloudstream3.shared.player.PlayerEvent
import com.lagradost.cloudstream3.shared.player.PlayerState
import com.lagradost.cloudstream3.shared.player.VideoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * Agnostic, decoupled ViewModel / Controller for video playback in Compose Multiplatform.
 *
 * Implements MVI architecture:
 * - Emits [PlayerUiState] via [state] / [uiState].
 * - Consumes [PlayerUiEvent] via [handleEvent] / [onEvent].
 * - Synchronizes with [VideoPlayer] reactive state.
 * - Periodically persists watch progress into Room KMP via [WatchProgressRepository] and [ResumeWatchingRepository].
 *
 * @param player The platform-agnostic video player interface.
 * @param watchProgressRepository The Room KMP repository for tracking watch history.
 * @param resumeWatchingRepository The Room KMP repository for tracking resume watching state.
 * @param bookmarkRepository The Room KMP repository for tracking library bookmarks.
 * @param coroutineContext Optional custom CoroutineContext for the ViewModel scope.
 * @param progressSaveIntervalMs Interval in milliseconds between periodic watch progress saves. Set <= 0 to disable periodic ticker.
 */
class PlayerControllerViewModel(
    val player: VideoPlayer,
    val watchProgressRepository: WatchProgressRepository? = null,
    val resumeWatchingRepository: ResumeWatchingRepository? = null,
    val bookmarkRepository: BookmarkRepository? = null,
    coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.Default,
    val progressSaveIntervalMs: Long = 5000L,
    initialState: PlayerUiState = PlayerUiState()
) : MviViewModel<PlayerUiState, PlayerUiEvent>(
    initialState = initialState,
    coroutineContext = coroutineContext
), AutoCloseable {

    /** Alias to [state] for UI consumption */
    val uiState: StateFlow<PlayerUiState> get() = state

    private var hasAutoPlayedCurrentEpisode = false

    init {
        // Collect reactive state from player
        launch {
            player.stateFlow.collect { playerState ->
                handlePlayerStateUpdate(playerState)
            }
        }

        // Collect discrete events from player
        launch {
            player.events.collect { event ->
                handlePlayerEvent(event)
            }
        }
    }

    private fun handlePlayerStateUpdate(playerState: PlayerState) {
        println("CloudStreamDebug: PlayerControllerViewModel.handlePlayerStateUpdate: isPlaying=${playerState.isPlaying}, isBuffering=${playerState.isBuffering}, pos=${playerState.positionMs}, currentUrl=${playerState.currentUrl}")
        val wasPlaying = currentState.isPlaying
        val isNowPlaying = playerState.isPlaying

        updateState {
            val isStopped = !playerState.isPlaying && !playerState.isBuffering &&
                    playerState.positionMs == 0L && playerState.currentUrl == null

            val activeStamp = skipTimestamps.firstOrNull { it.contains(playerState.positionMs) }

            copy(
                isPlaying = playerState.isPlaying,
                isBuffering = playerState.isBuffering,
                isStopped = isStopped,
                positionMs = playerState.positionMs,
                durationMs = playerState.durationMs,
                currentUrl = playerState.currentUrl ?: currentUrl,
                activeSkipTimestamp = activeStamp
            )
        }

        if (wasPlaying != isNowPlaying) {
            onPlaybackActiveChanged(isNowPlaying)
        }
    }

    private fun handlePlayerEvent(event: PlayerEvent) {
        when (event) {
            is PlayerEvent.OnPlay -> {
                updateState { copy(isPlaying = true, isBuffering = false, isStopped = false) }
                onPlaybackActiveChanged(true)
            }
            is PlayerEvent.OnPause -> {
                updateState { copy(isPlaying = false) }
                onPlaybackActiveChanged(false)
                launch { saveCurrentWatchProgress() }
            }
            is PlayerEvent.OnStop -> {
                updateState { copy(isPlaying = false, isBuffering = false, isStopped = true) }
                onPlaybackActiveChanged(false)
                launch { saveCurrentWatchProgress() }
            }
            is PlayerEvent.OnBuffering -> {
                updateState { copy(isBuffering = true) }
            }
            is PlayerEvent.OnError -> {
                println("CloudStreamDebug: PlayerControllerViewModel received PlayerEvent.OnError: ${event.message}")
                updateState {
                    copy(
                        isPlaying = false,
                        isBuffering = false,
                        errorMessage = event.message,
                        areControlsVisible = true
                    )
                }
                onPlaybackActiveChanged(false)
                emitEffect(PlayerUiEffect.PlaybackError(event.message))
            }
            is PlayerEvent.OnPositionChanged -> {
                updateState {
                    val activeStamp = skipTimestamps.firstOrNull { it.contains(event.positionMs) }
                    copy(
                        positionMs = event.positionMs,
                        durationMs = event.durationMs,
                        activeSkipTimestamp = activeStamp
                    )
                }
            }
            is PlayerEvent.OnUserInteraction -> {
                updateState { copy(areControlsVisible = true) }
            }
            is PlayerEvent.OnToggleControls -> {
                updateState { copy(areControlsVisible = !areControlsVisible) }
            }
            is PlayerEvent.OnBackRequested -> {
                println("CloudStreamDebug: PlayerControllerViewModel received PlayerEvent.OnBackRequested")
                emitEffect(PlayerUiEffect.NavigateBack)
            }
        }
    }

    override fun handleEvent(event: PlayerUiEvent) {
        when (event) {
            is PlayerUiEvent.Play -> {
                player.resume()
                updateState { copy(isPlaying = true, isStopped = false) }
                onPlaybackActiveChanged(true)
            }

            is PlayerUiEvent.Pause -> {
                player.pause()
                updateState { copy(isPlaying = false) }
                onPlaybackActiveChanged(false)
                launch { saveCurrentWatchProgress() }
            }

            is PlayerUiEvent.TogglePlayPause -> {
                if (currentState.isPlaying) {
                    handleEvent(PlayerUiEvent.Pause)
                } else {
                    handleEvent(PlayerUiEvent.Play)
                }
            }

            is PlayerUiEvent.Stop -> {
                onPlaybackActiveChanged(false)
                launch { saveCurrentWatchProgress() }
                player.stop()
                updateState {
                    copy(
                        isPlaying = false,
                        isBuffering = false,
                        isStopped = true,
                        positionMs = 0L
                    )
                }
            }

            is PlayerUiEvent.SeekTo -> {
                val duration = currentState.durationMs
                val clamped = if (duration > 0) event.positionMs.coerceIn(0L, duration) else event.positionMs.coerceAtLeast(0L)
                player.seekTo(clamped)
                updateState {
                    val activeStamp = skipTimestamps.firstOrNull { it.contains(clamped) }
                    copy(positionMs = clamped, activeSkipTimestamp = activeStamp)
                }
                launch { saveCurrentWatchProgress() }
            }

            is PlayerUiEvent.SeekBy -> {
                val currentPos = currentState.positionMs
                val duration = currentState.durationMs
                val targetPos = (currentPos + event.offsetMs).let { pos ->
                    if (duration > 0) pos.coerceIn(0L, duration) else pos.coerceAtLeast(0L)
                }
                handleEvent(PlayerUiEvent.SeekTo(targetPos))
            }

            is PlayerUiEvent.SelectQuality -> {
                hasAutoPlayedCurrentEpisode = true
                val currentPos = currentState.positionMs
                val wasPlaying = currentState.isPlaying
                updateState {
                    copy(
                        selectedQuality = event.quality,
                        currentUrl = event.quality.url
                    )
                }
                if (event.quality.url.isNotBlank()) {
                    player.play(event.quality, currentState.availableSubtitles)
                    if (currentPos > 0L) {
                        player.seekTo(currentPos)
                    }
                    if (!wasPlaying) {
                        player.pause()
                    }
                }
            }

            is PlayerUiEvent.SelectSubtitle -> {
                updateState { copy(selectedSubtitle = event.subtitle) }
                if (event.subtitle != null && event.subtitle.url.isNotBlank()) {
                    player.loadSubtitle(event.subtitle.url, event.subtitle.headers)
                }
            }

            is PlayerUiEvent.SetSpeed -> {
                val clampedSpeed = event.speed.coerceIn(0.25f, 4.0f)
                player.setPlaybackSpeed(clampedSpeed)
                updateState { copy(playbackSpeed = clampedSpeed) }
            }

            is PlayerUiEvent.SkipIntro -> {
                val active = currentState.activeSkipTimestamp
                    ?: currentState.skipTimestamps.firstOrNull { it.isIntro }
                if (active != null) {
                    handleEvent(PlayerUiEvent.SeekTo(active.endMs))
                }
            }

            is PlayerUiEvent.SkipOutro -> {
                val active = currentState.activeSkipTimestamp
                    ?: currentState.skipTimestamps.firstOrNull { it.isOutro }
                if (active != null) {
                    if (currentState.hasNextEpisode) {
                        handleEvent(PlayerUiEvent.NextEpisode)
                    } else {
                        handleEvent(PlayerUiEvent.SeekTo(active.endMs))
                    }
                }
            }

            is PlayerUiEvent.SkipToTimestamp -> {
                handleEvent(PlayerUiEvent.SeekTo(event.timestamp.endMs))
            }

            is PlayerUiEvent.NextEpisode -> {
                val current = currentState
                if (current.hasNextEpisode) {
                    launch { saveCurrentWatchProgress() }
                    val nextIndex = current.currentEpisodeIndex + 1
                    val nextEp = current.playlist[nextIndex]
                    loadEpisodeInternal(
                        episode = nextEp,
                        playlist = current.playlist,
                        index = nextIndex,
                        accountId = current.accountId,
                        autoPlay = true,
                        resumePosition = null
                    )
                }
            }

            is PlayerUiEvent.PreviousEpisode -> {
                val current = currentState
                if (current.hasPreviousEpisode) {
                    launch { saveCurrentWatchProgress() }
                    val prevIndex = current.currentEpisodeIndex - 1
                    val prevEp = current.playlist[prevIndex]
                    loadEpisodeInternal(
                        episode = prevEp,
                        playlist = current.playlist,
                        index = prevIndex,
                        accountId = current.accountId,
                        autoPlay = true,
                        resumePosition = null
                    )
                }
            }

            is PlayerUiEvent.ToggleControlsLock -> {
                updateState {
                    val newLockState = event.isLocked ?: !isControlsLocked
                    copy(isControlsLocked = newLockState)
                }
            }

            is PlayerUiEvent.SelectAudioTrack -> {
                updateState { copy(selectedAudioTrack = event.track) }
            }

            is PlayerUiEvent.SetLockPin -> {
                updateState {
                    copy(
                        lockPin = event.pin,
                        isPinLocked = !event.pin.isNullOrBlank()
                    )
                }
            }

            is PlayerUiEvent.ShowLockPinDialog -> {
                updateState {
                    copy(
                        showLockPinDialog = event.show,
                        lockPinDialogMode = event.mode
                    )
                }
            }

            is PlayerUiEvent.UnlockWithPin -> {
                val currentPin = currentState.lockPin
                if (currentPin.isNullOrBlank() || currentPin == event.pin) {
                    updateState {
                        copy(
                            isControlsLocked = false,
                            showLockPinDialog = false,
                            areControlsVisible = true
                        )
                    }
                }
            }

            is PlayerUiEvent.ClearLockPin -> {
                updateState {
                    copy(
                        lockPin = null,
                        isPinLocked = false,
                        showLockPinDialog = false
                    )
                }
            }

            is PlayerUiEvent.VisibilityChanged -> {
                updateState { copy(areControlsVisible = event.isVisible) }
            }

            is PlayerUiEvent.ToggleControlsVisibility -> {
                updateState { copy(areControlsVisible = !areControlsVisible) }
            }

            is PlayerUiEvent.SetActiveModal -> {
                updateState { copy(activeModal = event.modal) }
            }

            is PlayerUiEvent.LoadEpisode -> {
                loadEpisodeInternal(
                    episode = event.episode,
                    playlist = listOf(event.episode),
                    index = 0,
                    accountId = event.accountId,
                    autoPlay = event.autoPlay,
                    resumePosition = event.resumePosition
                )
            }

            is PlayerUiEvent.LoadPlaylist -> {
                val safeIndex = event.startIndex.coerceIn(0, (event.playlist.size - 1).coerceAtLeast(0))
                val targetEpisode = event.playlist.getOrNull(safeIndex)
                if (targetEpisode != null) {
                    loadEpisodeInternal(
                        episode = targetEpisode,
                        playlist = event.playlist,
                        index = safeIndex,
                        accountId = event.accountId,
                        autoPlay = event.autoPlay,
                        resumePosition = null
                    )
                }
            }

            is PlayerUiEvent.LoadMedia -> {
                loadMediaInternal(
                    url = event.url,
                    mediaId = event.mediaId,
                    parentId = event.parentId,
                    accountId = event.accountId,
                    qualities = event.qualities,
                    subtitles = event.subtitles,
                    initialSubtitle = event.initialSubtitle,
                    skipTimestamps = event.skipTimestamps,
                    autoPlay = event.autoPlay,
                    resumePosition = event.resumePosition
                )
            }

            is PlayerUiEvent.UpdateQualitiesAndSubtitles -> {
                val currentQualities = currentState.availableQualities
                val currentSubs = currentState.availableSubtitles
                val newQualities = (currentQualities + event.qualities)
                    .distinctBy { it.url }
                    .sortedByDescending { it.effectiveResolution }
                val newSubs = (currentSubs + event.subtitles).distinctBy { it.url }

                val isAlreadyActive = hasAutoPlayedCurrentEpisode || currentState.isPlaying || currentState.isBuffering || !currentState.currentUrl.isNullOrBlank()
                val shouldAutoPlayFirst = !isAlreadyActive && newQualities.isNotEmpty()
                if (shouldAutoPlayFirst) {
                    hasAutoPlayedCurrentEpisode = true
                }
                val selectedQ = if (shouldAutoPlayFirst) {
                    newQualities.first()
                } else {
                    currentState.selectedQuality ?: newQualities.firstOrNull { it.url == currentState.currentUrl } ?: newQualities.firstOrNull()
                }
                val selectedSub = currentState.selectedSubtitle ?: newSubs.firstOrNull { it.isDefault } ?: newSubs.firstOrNull()

                updateState {
                    copy(
                        availableQualities = newQualities,
                        availableSubtitles = newSubs,
                        selectedQuality = selectedQ,
                        selectedSubtitle = selectedSub,
                        currentUrl = if (shouldAutoPlayFirst) selectedQ?.url ?: currentUrl else currentUrl,
                        isBuffering = if (shouldAutoPlayFirst) true else isBuffering,
                        isStopped = if (shouldAutoPlayFirst) false else isStopped
                    )
                }

                if (shouldAutoPlayFirst && selectedQ?.url?.isNotBlank() == true) {
                    player.play(selectedQ, newSubs)
                    if (currentState.positionMs > 0L) {
                        player.seekTo(currentState.positionMs)
                    }
                    if (selectedSub != null && selectedSub.url.isNotBlank()) {
                        player.loadSubtitle(selectedSub.url, selectedSub.headers)
                    }
                    onPlaybackActiveChanged(true)
                }
            }


            is PlayerUiEvent.SetAspectRatio -> {
                player.setAspectRatio(event.aspectRatio.name)
                updateState { copy(aspectRatio = event.aspectRatio) }
            }
            is PlayerUiEvent.CycleResizeMode -> {
                val entries = PlayerAspectRatio.entries
                val nextIndex = (currentState.aspectRatio.ordinal + 1) % entries.size
                val nextRatio = entries[nextIndex]
                handleEvent(PlayerUiEvent.SetAspectRatio(nextRatio))
            }
            is PlayerUiEvent.SetSubtitleDelay -> {
                player.setSubtitleDelay(event.delayMs)
                updateState { copy(subtitleDelayMs = event.delayMs) }
            }
            is PlayerUiEvent.SetAudioDelay -> {
                player.setAudioDelay(event.delayMs)
                updateState { copy(audioDelayMs = event.delayMs) }
            }
            is PlayerUiEvent.SelectEpisode -> {
                val current = currentState
                if (event.index in current.playlist.indices) {
                    launch { saveCurrentWatchProgress() }
                    val targetEp = current.playlist[event.index]
                    loadEpisodeInternal(
                        episode = targetEp,
                        playlist = current.playlist,
                        index = event.index,
                        accountId = current.accountId,
                        autoPlay = true,
                        resumePosition = null
                    )
                }
            }
            is PlayerUiEvent.DismissError -> {
                updateState { copy(errorMessage = null) }
            }

            is PlayerUiEvent.SaveProgressNow -> {
                launch { saveCurrentWatchProgress() }
            }
        }
    }

    private fun loadEpisodeInternal(
        episode: PlayerEpisode,
        playlist: List<PlayerEpisode>,
        index: Int,
        accountId: Int,
        autoPlay: Boolean,
        resumePosition: Long?
    ) {
        launchSafeJob(key = "load_media") {
            var targetResume = resumePosition
            if (targetResume == null && watchProgressRepository != null) {
                try {
                    val saved = watchProgressRepository.getProgress(accountId = accountId, mediaId = episode.id)
                    if (saved != null && saved.position > 0L) {
                        targetResume = saved.position
                    }
                } catch (e: Exception) {
                    // Ignore persistence lookup error
                }
            }

            val sortedQualities = episode.qualities.distinctBy { it.url }.sortedByDescending { it.effectiveResolution }
            val defaultQuality = sortedQualities.firstOrNull()
            val initialUrl = defaultQuality?.url ?: ""
            hasAutoPlayedCurrentEpisode = initialUrl.isNotBlank()
            val defaultSubtitle = episode.subtitles.firstOrNull { it.isDefault } ?: episode.subtitles.firstOrNull()

            updateState {
                copy(
                    isPlaying = autoPlay && initialUrl.isNotBlank(),
                    isBuffering = initialUrl.isNotBlank(),
                    isStopped = initialUrl.isBlank(),
                    positionMs = targetResume ?: 0L,
                    durationMs = 0L,
                    currentUrl = initialUrl,
                    availableQualities = sortedQualities,
                    selectedQuality = defaultQuality,
                    availableSubtitles = episode.subtitles,
                    selectedSubtitle = defaultSubtitle,
                    skipTimestamps = episode.skipTimestamps,
                    activeSkipTimestamp = null,
                    accountId = accountId,
                    parentId = parentId ?: episode.id,
                    currentEpisodeId = episode.id,
                    currentEpisode = episode,
                    playlist = playlist,
                    currentEpisodeIndex = index,
                    hasNextEpisode = index < playlist.size - 1,
                    hasPreviousEpisode = index > 0,
                    errorMessage = null
                )
            }

            if (initialUrl.isNotBlank()) {
                player.play(defaultQuality ?: PlayerQuality(url = initialUrl), episode.subtitles)
                if (targetResume != null && targetResume > 0L) {
                    player.seekTo(targetResume)
                }
                if (!autoPlay) {
                    player.pause()
                }
            }

            if (defaultSubtitle != null && defaultSubtitle.url.isNotBlank()) {
                player.loadSubtitle(defaultSubtitle.url, defaultSubtitle.headers)
            }

            onPlaybackActiveChanged(autoPlay && initialUrl.isNotBlank())
        }
    }

    private fun loadMediaInternal(
        url: String,
        mediaId: Int?,
        parentId: Int? = null,
        accountId: Int,
        qualities: List<PlayerQuality>,
        subtitles: List<PlayerSubtitleTrack>,
        initialSubtitle: PlayerSubtitleTrack? = null,
        skipTimestamps: List<PlayerSkipTimestamp>,
        autoPlay: Boolean,
        resumePosition: Long?
    ) {
        println("CloudStreamDebug: PlayerControllerViewModel.loadMediaInternal: url=$url, autoPlay=$autoPlay, mediaId=$mediaId, qualities=${qualities.size}")
        hasAutoPlayedCurrentEpisode = url.isNotBlank()
        if (url.isNotBlank()) {
            val sortedQualities = qualities.distinctBy { it.url }.sortedByDescending { it.effectiveResolution }
            val defaultQuality = sortedQualities.firstOrNull { it.url == url } ?: sortedQualities.firstOrNull()
            val defaultSubtitle = initialSubtitle ?: subtitles.firstOrNull { it.isDefault } ?: subtitles.firstOrNull()
            updateState {
                copy(
                    isPlaying = autoPlay,
                    isBuffering = autoPlay,
                    isStopped = false,
                    currentUrl = url,
                    positionMs = resumePosition ?: 0L,
                    durationMs = 0L,
                    availableQualities = sortedQualities,
                    selectedQuality = defaultQuality,
                    availableSubtitles = subtitles,
                    selectedSubtitle = defaultSubtitle,
                    skipTimestamps = skipTimestamps,
                    accountId = accountId,
                    parentId = parentId ?: mediaId,
                    currentEpisodeId = mediaId,
                    errorMessage = null
                )
            }
        }
        launchSafeJob(key = "load_media") {
            var targetResume = resumePosition
            if (targetResume == null && mediaId != null && watchProgressRepository != null) {
                try {
                    val saved = watchProgressRepository.getProgress(accountId = accountId, mediaId = mediaId)
                    if (saved != null && saved.position > 0L) {
                        targetResume = saved.position
                    }
                } catch (e: Exception) {
                    // Ignore persistence lookup error
                }
            }

            val sortedQualities = qualities.distinctBy { it.url }.sortedByDescending { it.effectiveResolution }
            val defaultQuality = sortedQualities.firstOrNull { it.url == url } ?: sortedQualities.firstOrNull()
            val defaultSubtitle = initialSubtitle ?: subtitles.firstOrNull { it.isDefault } ?: subtitles.firstOrNull()

            updateState {
                copy(
                    isPlaying = autoPlay,
                    isBuffering = url.isNotBlank(),
                    isStopped = url.isBlank(),
                    positionMs = targetResume ?: 0L,
                    durationMs = 0L,
                    currentUrl = url,
                    availableQualities = sortedQualities,
                    selectedQuality = defaultQuality,
                    availableSubtitles = subtitles,
                    selectedSubtitle = defaultSubtitle,
                    skipTimestamps = skipTimestamps,
                    activeSkipTimestamp = null,
                    accountId = accountId,
                    parentId = parentId ?: mediaId,
                    currentEpisodeId = mediaId,
                    currentEpisode = null,
                    playlist = emptyList(),
                    currentEpisodeIndex = 0,
                    hasNextEpisode = false,
                    hasPreviousEpisode = false,
                    errorMessage = null
                )
            }

            if (url.isNotBlank()) {
                player.play(defaultQuality ?: PlayerQuality(url = url), subtitles)
                if (targetResume != null && targetResume > 0L) {
                    player.seekTo(targetResume)
                }
                if (!autoPlay) {
                    player.pause()
                }
            }

            if (defaultSubtitle != null && defaultSubtitle.url.isNotBlank()) {
                player.loadSubtitle(defaultSubtitle.url, defaultSubtitle.headers)
            }

            onPlaybackActiveChanged(autoPlay && url.isNotBlank())
        }
    }

    /**
     * Persists current playback progress into the [WatchProgressRepository], [ResumeWatchingRepository],
     * and updates the associated [BookmarkRepository] entry's update timestamp.
     */
    suspend fun saveCurrentWatchProgress() {
        val current = currentState
        val mediaId = current.currentEpisodeId ?: return
        val pos = if (current.positionMs > 0L) current.positionMs else player.state.positionMs
        val dur = if (current.durationMs > 0L) current.durationMs else player.state.durationMs

        if (pos <= 0L && dur <= 0L) return

        val watchState = if (dur > 0L && (pos.toDouble() / dur.toDouble()) >= 0.90) {
            2 // Watched
        } else if (pos > 0L) {
            1 // Watching
        } else {
            0 // None
        }

        try {
            watchProgressRepository?.setProgress(
                accountId = current.accountId,
                mediaId = mediaId,
                position = pos,
                duration = dur,
                watchState = watchState
            )
        } catch (e: Exception) {
            // Ignore persistence error
        }

        try {
            val parentId = current.parentId ?: mediaId
            resumeWatchingRepository?.saveResumeWatching(
                ResumeWatchingEntity(
                    accountId = current.accountId,
                    parentId = parentId,
                    episodeId = mediaId,
                    episode = current.currentEpisode?.episode ?: current.currentEpisode?.episodeNumber,
                    season = current.currentEpisode?.season ?: current.currentEpisode?.seasonNumber,
                    isFromDownload = false,
                    updateTime = APIHolder.unixTimeMS
                )
            )
        } catch (e: Exception) {
            // Ignore persistence error
        }

        try {
            val parentId = current.parentId ?: mediaId
            bookmarkRepository?.let { repo ->
                val bookmark = repo.getBookmark(current.accountId, parentId)
                if (bookmark != null) {
                    repo.saveBookmark(bookmark.copy(latestUpdatedTime = APIHolder.unixTimeMS))
                }
            }
        } catch (e: Exception) {
            // Ignore persistence error
        }
    }

    private fun onPlaybackActiveChanged(isActive: Boolean) {
        if (isActive && progressSaveIntervalMs > 0L) {
            startPeriodicProgressSaver()
        } else {
            stopPeriodicProgressSaver()
        }
    }

    private fun startPeriodicProgressSaver() {
        if (isJobActive("periodic_saver")) return
        if (progressSaveIntervalMs <= 0L) return
        launchSafeJob(key = "periodic_saver") {
            while (isActive) {
                delay(progressSaveIntervalMs)
                if (currentState.isPlaying && currentState.positionMs > 0L) {
                    saveCurrentWatchProgress()
                }
            }
        }
    }

    private fun stopPeriodicProgressSaver() {
        cancelJob("periodic_saver")
    }

    /**
     * Releases controller resources, persists pending progress, and stops the player.
     */
    fun release() {
        stopPeriodicProgressSaver()
        launch {
            saveCurrentWatchProgress()
        }
    }

    override fun onCleared() {
        release()
        super.onCleared()
    }
}
