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
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.CoroutineContext

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

    val uiState: StateFlow<PlayerUiState> get() = state

    private var hasAutoPlayedCurrentEpisode = false
    private val saveProgressMutex = Mutex()

    init {
        launch {
            player.stateFlow.collect { playerState ->
                handlePlayerStateUpdate(playerState)
            }
        }

        launch {
            player.events.collect { event ->
                handlePlayerEvent(event)
            }
        }
    }

    private fun isPlaybackStopped(playerState: PlayerState): Boolean =
        !playerState.isPlaying && !playerState.isBuffering &&
                playerState.positionMs == 0L && playerState.currentUrl == null

    private fun handlePlayerStateUpdate(playerState: PlayerState) {
        val wasPlaying = currentState.isPlaying
        val isNowPlaying = playerState.isPlaying

        updateState {
            val isStopped = isPlaybackStopped(playerState)
            val activeStamp = skipTimestamps.firstOrNull { it.contains(playerState.positionMs) }

            copy(
                isPlaying = playerState.isPlaying,
                isBuffering = playerState.isBuffering,
                isStopped = isStopped,
                positionMs = playerState.positionMs,
                durationMs = if (playerState.durationMs > 0L) playerState.durationMs else durationMs,
                currentUrl = playerState.currentUrl ?: currentUrl,
                activeSkipTimestamp = activeStamp
            )
        }

        if (wasPlaying != isNowPlaying) {
            onPlaybackActiveChanged(isNowPlaying)
        }
        if (wasPlaying && !isNowPlaying) {
            launch {
                saveCurrentWatchProgress()
            }
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
                        durationMs = if (event.durationMs > 0L) event.durationMs else durationMs,
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
                emitEffect(PlayerUiEffect.NavigateBack)
            }
        }
    }

    override fun handleEvent(event: PlayerUiEvent) {
        when (event) {
            is PlayerUiEvent.Play,
            is PlayerUiEvent.Pause,
            is PlayerUiEvent.TogglePlayPause,
            is PlayerUiEvent.Stop,
            is PlayerUiEvent.SeekTo,
            is PlayerUiEvent.SeekBy,
            is PlayerUiEvent.SetSpeed,
            is PlayerUiEvent.SkipIntro,
            is PlayerUiEvent.SkipOutro,
            is PlayerUiEvent.SkipToTimestamp,
            is PlayerUiEvent.SaveProgressNow -> handlePlaybackEvents(event)

            is PlayerUiEvent.SelectQuality,
            is PlayerUiEvent.SelectSubtitle,
            is PlayerUiEvent.SelectAudioTrack,
            is PlayerUiEvent.UpdateQualitiesAndSubtitles,
            is PlayerUiEvent.SetSubtitleDelay,
            is PlayerUiEvent.SetAudioDelay,
            is PlayerUiEvent.SetAspectRatio,
            is PlayerUiEvent.CycleResizeMode -> handleTrackAndQualityEvents(event)

            is PlayerUiEvent.NextEpisode,
            is PlayerUiEvent.PreviousEpisode,
            is PlayerUiEvent.SelectEpisode,
            is PlayerUiEvent.LoadEpisode,
            is PlayerUiEvent.LoadPlaylist,
            is PlayerUiEvent.LoadMedia -> handlePlaylistAndMediaEvents(event)

            is PlayerUiEvent.ToggleControlsLock,
            is PlayerUiEvent.SetLockPin,
            is PlayerUiEvent.ShowLockPinDialog,
            is PlayerUiEvent.UnlockWithPin,
            is PlayerUiEvent.ClearLockPin,
            is PlayerUiEvent.VisibilityChanged,
            is PlayerUiEvent.ToggleControlsVisibility,
            is PlayerUiEvent.SetActiveModal,
            is PlayerUiEvent.DismissError -> handleUiControlsAndSecurityEvents(event)
        }
    }

    private fun handlePlaybackEvents(event: PlayerUiEvent) {
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
                if (currentState.isPlaying) handleEvent(PlayerUiEvent.Pause) else handleEvent(PlayerUiEvent.Play)
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
            is PlayerUiEvent.SeekTo -> handleSeekTo(event.positionMs)
            is PlayerUiEvent.SeekBy -> handleSeekBy(event.offsetMs)
            is PlayerUiEvent.SetSpeed -> handleSetSpeed(event.speed)
            is PlayerUiEvent.SkipIntro -> handleSkipIntro()
            is PlayerUiEvent.SkipOutro -> handleSkipOutro()
            is PlayerUiEvent.SkipToTimestamp -> handleSeekTo(event.timestamp.endMs)
            is PlayerUiEvent.SaveProgressNow -> launch { saveCurrentWatchProgress() }
            else -> Unit
        }
    }

    private fun handleTrackAndQualityEvents(event: PlayerUiEvent) {
        when (event) {
            is PlayerUiEvent.SelectQuality -> handleSelectQuality(event.quality)
            is PlayerUiEvent.SelectSubtitle -> handleSelectSubtitle(event.subtitle)
            is PlayerUiEvent.SelectAudioTrack -> updateState { copy(selectedAudioTrack = event.track) }
            is PlayerUiEvent.UpdateQualitiesAndSubtitles -> handleQualitiesUpdate(event)
            is PlayerUiEvent.SetAspectRatio -> handleSetAspectRatio(event.aspectRatio)
            is PlayerUiEvent.CycleResizeMode -> handleCycleResizeMode()
            is PlayerUiEvent.SetSubtitleDelay -> handleSetSubtitleDelay(event.delayMs)
            is PlayerUiEvent.SetAudioDelay -> handleSetAudioDelay(event.delayMs)
            else -> Unit
        }
    }

    private fun handlePlaylistAndMediaEvents(event: PlayerUiEvent) {
        when (event) {
            is PlayerUiEvent.NextEpisode -> handleEpisodeNavigation(offset = 1)
            is PlayerUiEvent.PreviousEpisode -> handleEpisodeNavigation(offset = -1)
            is PlayerUiEvent.SelectEpisode -> handleSelectEpisode(event.index)
            is PlayerUiEvent.LoadEpisode -> loadEpisodeInternal(
                episode = event.episode,
                playlist = persistentListOf(event.episode),
                index = 0,
                accountId = event.accountId,
                autoPlay = event.autoPlay,
                resumePosition = event.resumePosition
            )
            is PlayerUiEvent.LoadPlaylist -> handleLoadPlaylist(event)
            is PlayerUiEvent.LoadMedia -> loadMediaInternal(
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
            else -> Unit
        }
    }

    private fun handleUiControlsAndSecurityEvents(event: PlayerUiEvent) {
        when (event) {
            is PlayerUiEvent.ToggleControlsLock -> updateState {
                copy(isControlsLocked = event.isLocked ?: !isControlsLocked)
            }
            is PlayerUiEvent.SetLockPin -> updateState {
                copy(
                    lockPin = event.pin,
                    isPinLocked = !event.pin.isNullOrBlank()
                )
            }
            is PlayerUiEvent.ShowLockPinDialog -> updateState {
                copy(
                    showLockPinDialog = event.show,
                    lockPinDialogMode = event.mode
                )
            }
            is PlayerUiEvent.UnlockWithPin -> handleUnlockWithPin(event.pin)
            is PlayerUiEvent.ClearLockPin -> updateState {
                copy(
                    lockPin = null,
                    isPinLocked = false,
                    showLockPinDialog = false
                )
            }
            is PlayerUiEvent.VisibilityChanged -> updateState {
                copy(areControlsVisible = event.isVisible)
            }
            is PlayerUiEvent.ToggleControlsVisibility -> updateState {
                copy(areControlsVisible = !areControlsVisible)
            }
            is PlayerUiEvent.SetActiveModal -> updateState {
                copy(activeModal = event.modal)
            }
            is PlayerUiEvent.DismissError -> updateState { copy(errorMessage = null) }
            else -> Unit
        }
    }

    private fun handleSeekTo(positionMs: Long) {
        val duration = currentState.durationMs
        val clamped = if (duration > 0) positionMs.coerceIn(0L, duration) else positionMs.coerceAtLeast(0L)
        player.seekTo(clamped)
        updateState {
            val activeStamp = skipTimestamps.firstOrNull { it.contains(clamped) }
            copy(positionMs = clamped, activeSkipTimestamp = activeStamp)
        }
        launch { saveCurrentWatchProgress() }
    }

    private fun handleSeekBy(offsetMs: Long) {
        val currentPos = currentState.positionMs
        val duration = currentState.durationMs
        val targetPos = (currentPos + offsetMs).let { pos ->
            if (duration > 0) pos.coerceIn(0L, duration) else pos.coerceAtLeast(0L)
        }
        handleSeekTo(targetPos)
    }

    private fun handleSelectQuality(quality: PlayerQuality) {
        hasAutoPlayedCurrentEpisode = true
        val currentPos = currentState.positionMs
        val wasPlaying = currentState.isPlaying
        updateState {
            copy(
                selectedQuality = quality,
                currentUrl = quality.url
            )
        }
        if (quality.url.isNotBlank()) {
            player.play(quality, currentState.availableSubtitles, currentPos.takeIf { it > 0L })
            if (!wasPlaying) {
                player.pause()
            }
        }
    }

    private fun handleSelectSubtitle(subtitle: PlayerSubtitleTrack?) {
        updateState { copy(selectedSubtitle = subtitle) }
        if (subtitle != null && subtitle.url.isNotBlank()) {
            player.loadSubtitle(subtitle.url, subtitle.headers)
        }
    }

    private fun handleSetSpeed(speed: Float) {
        val clampedSpeed = speed.coerceIn(0.25f, 4.0f)
        player.setPlaybackSpeed(clampedSpeed)
        updateState { copy(playbackSpeed = clampedSpeed) }
    }

    private fun handleSkipIntro() {
        val active = currentState.activeSkipTimestamp
            ?: currentState.skipTimestamps.firstOrNull { it.isIntro }
        if (active != null) {
            handleSeekTo(active.endMs)
        }
    }

    private fun handleSkipOutro() {
        val active = currentState.activeSkipTimestamp
            ?: currentState.skipTimestamps.firstOrNull { it.isOutro }
        if (active != null) {
            if (currentState.hasNextEpisode) {
                handleEpisodeNavigation(offset = 1)
            } else {
                handleSeekTo(active.endMs)
            }
        }
    }

    private fun handleEpisodeNavigation(offset: Int) {
        val current = currentState
        val targetIndex = current.currentEpisodeIndex + offset
        if (targetIndex in current.playlist.indices) {
            launch { saveCurrentWatchProgress() }
            val targetEp = current.playlist[targetIndex]
            loadEpisodeInternal(
                episode = targetEp,
                playlist = current.playlist,
                index = targetIndex,
                accountId = current.accountId,
                autoPlay = true,
                resumePosition = null
            )
        }
    }

    private fun handleSelectEpisode(index: Int) {
        val current = currentState
        if (index in current.playlist.indices) {
            launch { saveCurrentWatchProgress() }
            val targetEp = current.playlist[index]
            loadEpisodeInternal(
                episode = targetEp,
                playlist = current.playlist,
                index = index,
                accountId = current.accountId,
                autoPlay = true,
                resumePosition = null
            )
        }
    }

    private fun handleUnlockWithPin(pin: String) {
        val currentPin = currentState.lockPin
        if (currentPin.isNullOrBlank() || currentPin == pin) {
            updateState {
                copy(
                    isControlsLocked = false,
                    showLockPinDialog = false,
                    areControlsVisible = true
                )
            }
        }
    }

    private fun handleLoadPlaylist(event: PlayerUiEvent.LoadPlaylist) {
        val safeIndex = event.startIndex.coerceIn(0, (event.playlist.size - 1).coerceAtLeast(0))
        val targetEpisode = event.playlist.getOrNull(safeIndex) ?: return
        loadEpisodeInternal(
            episode = targetEpisode,
            playlist = event.playlist.toImmutableList(),
            index = safeIndex,
            accountId = event.accountId,
            autoPlay = event.autoPlay,
            resumePosition = null
        )
    }

    private fun resolveSelectedQuality(
        newQualities: ImmutableList<PlayerQuality>,
        shouldAutoPlay: Boolean
    ): PlayerQuality? {
        if (shouldAutoPlay) return newQualities.firstOrNull()
        return currentState.selectedQuality
            ?: newQualities.firstOrNull { it.url == currentState.currentUrl }
            ?: newQualities.firstOrNull()
    }

    private fun resolveSelectedSubtitle(newSubs: ImmutableList<PlayerSubtitleTrack>): PlayerSubtitleTrack? {
        return currentState.selectedSubtitle
            ?: newSubs.firstOrNull { it.isDefault }
            ?: newSubs.firstOrNull()
    }

    private fun autoPlayInitialQuality(
        quality: PlayerQuality,
        subtitles: ImmutableList<PlayerSubtitleTrack>,
        selectedSub: PlayerSubtitleTrack?
    ) {
        if (quality.url.isBlank()) return
        val resumePos = currentState.positionMs.takeIf { it > 0L }
        player.play(quality, subtitles, resumePos)
        if (selectedSub != null && selectedSub.url.isNotBlank()) {
            player.loadSubtitle(selectedSub.url, selectedSub.headers)
        }
        onPlaybackActiveChanged(true)
    }

    private fun handleQualitiesUpdate(event: PlayerUiEvent.UpdateQualitiesAndSubtitles) {
        val newQualities = (currentState.availableQualities + event.qualities)
            .distinctBy { it.url }
            .sortedByDescending { it.effectiveResolution }
            .toImmutableList()
        val newSubs = (currentState.availableSubtitles + event.subtitles)
            .distinctBy { it.url }
            .toImmutableList()

        val isAlreadyActive = hasAutoPlayedCurrentEpisode || currentState.isPlaying ||
                currentState.isBuffering || !currentState.currentUrl.isNullOrBlank()
        val shouldAutoPlayFirst = !isAlreadyActive && newQualities.isNotEmpty()
        if (shouldAutoPlayFirst) {
            hasAutoPlayedCurrentEpisode = true
        }

        val selectedQ = resolveSelectedQuality(newQualities, shouldAutoPlayFirst)
        val selectedSub = resolveSelectedSubtitle(newSubs)

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

        if (shouldAutoPlayFirst && selectedQ != null) {
            autoPlayInitialQuality(selectedQ, newSubs, selectedSub)
        }
    }

    private fun handleSetAspectRatio(aspectRatio: PlayerAspectRatio) {
        player.setAspectRatio(aspectRatio.name)
        updateState { copy(aspectRatio = aspectRatio) }
    }

    private fun handleCycleResizeMode() {
        val entries = PlayerAspectRatio.entries
        val nextIndex = (currentState.aspectRatio.ordinal + 1) % entries.size
        val nextRatio = entries[nextIndex]
        player.setAspectRatio(nextRatio.name)
        updateState { copy(aspectRatio = nextRatio) }
    }

    private fun handleSetSubtitleDelay(delayMs: Long) {
        player.setSubtitleDelay(delayMs)
        updateState { copy(subtitleDelayMs = delayMs) }
    }

    private fun handleSetAudioDelay(delayMs: Long) {
        player.setAudioDelay(delayMs)
        updateState { copy(audioDelayMs = delayMs) }
    }

    private suspend fun resolveSavedProgressPosition(accountId: Int, mediaId: Int, explicitPosition: Long?): Long? {
        if (explicitPosition != null) return explicitPosition
        if (watchProgressRepository == null) return null
        return try {
            val saved = watchProgressRepository.getProgress(accountId = accountId, mediaId = mediaId)
            saved?.position?.takeIf { it > 0L }
        } catch (e: Exception) {
            null
        }
    }

    private fun startPlaybackSession(
        quality: PlayerQuality?,
        initialUrl: String,
        subtitles: List<PlayerSubtitleTrack>,
        defaultSubtitle: PlayerSubtitleTrack?,
        targetResume: Long?,
        autoPlay: Boolean
    ) {
        if (initialUrl.isNotBlank()) {
            player.play(
                quality = quality ?: PlayerQuality(url = initialUrl),
                subtitles = subtitles,
                startPositionMs = targetResume
            )
            if (!autoPlay) {
                player.pause()
            }
        }

        if (defaultSubtitle != null && defaultSubtitle.url.isNotBlank()) {
            player.loadSubtitle(defaultSubtitle.url, defaultSubtitle.headers)
        }

        onPlaybackActiveChanged(autoPlay && initialUrl.isNotBlank())
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
            val targetResume = resolveSavedProgressPosition(accountId, episode.id, resumePosition)
            val sortedQualities = episode.qualities.distinctBy { it.url }.sortedByDescending { it.effectiveResolution }.toImmutableList()
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
                    playlist = playlist.toImmutableList(),
                    currentEpisodeIndex = index,
                    hasNextEpisode = index < playlist.size - 1,
                    hasPreviousEpisode = index > 0,
                    errorMessage = null
                )
            }

            startPlaybackSession(
                quality = defaultQuality,
                initialUrl = initialUrl,
                subtitles = episode.subtitles,
                defaultSubtitle = defaultSubtitle,
                targetResume = targetResume,
                autoPlay = autoPlay
            )
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
        hasAutoPlayedCurrentEpisode = url.isNotBlank()
        val sortedQualities = qualities.distinctBy { it.url }.sortedByDescending { it.effectiveResolution }.toImmutableList()
        val defaultQuality = sortedQualities.firstOrNull { it.url == url } ?: sortedQualities.firstOrNull()
        val defaultSubtitle = initialSubtitle ?: subtitles.firstOrNull { it.isDefault } ?: subtitles.firstOrNull()

        if (url.isNotBlank()) {
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
                    availableSubtitles = subtitles.toImmutableList(),
                    selectedSubtitle = defaultSubtitle,
                    skipTimestamps = skipTimestamps.toImmutableList(),
                    accountId = accountId,
                    parentId = parentId ?: mediaId,
                    currentEpisodeId = mediaId,
                    errorMessage = null
                )
            }
        }

        launchSafeJob(key = "load_media") {
            val targetResume = if (mediaId != null) {
                resolveSavedProgressPosition(accountId, mediaId, resumePosition)
            } else {
                resumePosition
            }

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
                    availableSubtitles = subtitles.toImmutableList(),
                    selectedSubtitle = defaultSubtitle,
                    skipTimestamps = skipTimestamps.toImmutableList(),
                    activeSkipTimestamp = null,
                    accountId = accountId,
                    parentId = parentId ?: mediaId,
                    currentEpisodeId = mediaId,
                    currentEpisode = null,
                    playlist = persistentListOf(),
                    currentEpisodeIndex = 0,
                    hasNextEpisode = false,
                    hasPreviousEpisode = false,
                    errorMessage = null
                )
            }

            startPlaybackSession(
                quality = defaultQuality,
                initialUrl = url,
                subtitles = subtitles,
                defaultSubtitle = defaultSubtitle,
                targetResume = targetResume,
                autoPlay = autoPlay
            )
        }
    }

    private fun computeWatchState(position: Long, duration: Long): Int {
        return when {
            duration > 0L && (position.toDouble() / duration.toDouble()) >= 0.90 -> 2
            position > 0L -> 1
            else -> 0
        }
    }

    private suspend fun persistResumeWatching(accountId: Int, parentId: Int, mediaId: Int) {
        val resumeRepo = resumeWatchingRepository ?: return
        try {
            val ep = currentState.currentEpisode
            resumeRepo.saveResumeWatching(
                ResumeWatchingEntity(
                    accountId = accountId,
                    parentId = parentId,
                    episodeId = mediaId,
                    episode = ep?.episode ?: ep?.episodeNumber,
                    season = ep?.season ?: ep?.seasonNumber,
                    isFromDownload = false,
                    updateTime = APIHolder.unixTimeMS
                )
            )
        } catch (e: Exception) {
            // Ignore persistence error
        }
    }

    private suspend fun persistBookmarkUpdate(accountId: Int, parentId: Int) {
        val repo = bookmarkRepository ?: return
        try {
            val bookmark = repo.getBookmark(accountId, parentId)
            if (bookmark != null) {
                repo.saveBookmark(bookmark.copy(latestUpdatedTime = APIHolder.unixTimeMS))
            }
        } catch (e: Exception) {
            // Ignore persistence error
        }
    }

    suspend fun saveCurrentWatchProgress() {
        saveProgressMutex.withLock {
            val current = currentState
            val mediaId = current.currentEpisodeId ?: return@withLock
            val pos = if (current.positionMs > 0L) current.positionMs else player.state.positionMs
            val dur = if (current.durationMs > 0L) current.durationMs else player.state.durationMs

            if (pos <= 0L && dur <= 0L) return@withLock

            val watchState = computeWatchState(pos, dur)

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

            val parentId = current.parentId ?: mediaId
            persistResumeWatching(current.accountId, parentId, mediaId)
            persistBookmarkUpdate(current.accountId, parentId)
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
