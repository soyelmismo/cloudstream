package com.lagradost.cloudstream3.shared.viewmodels.player

import com.lagradost.cloudstream3.shared.mvi.UiEffect
import com.lagradost.cloudstream3.shared.mvi.UiEvent

sealed interface PlayerUiEvent : UiEvent {
    data object Play : PlayerUiEvent
    data object Pause : PlayerUiEvent
    data object TogglePlayPause : PlayerUiEvent
    data object Stop : PlayerUiEvent
    data class SeekTo(val positionMs: Long) : PlayerUiEvent
    data class SeekBy(val offsetMs: Long) : PlayerUiEvent
    data class SelectQuality(val quality: PlayerQuality) : PlayerUiEvent
    data class SelectSubtitle(val subtitle: PlayerSubtitleTrack?) : PlayerUiEvent
    data class SetSpeed(val speed: Float) : PlayerUiEvent
    typealias SetPlaybackSpeed = SetSpeed

    data object SkipIntro : PlayerUiEvent
    data object SkipOutro : PlayerUiEvent
    data class SkipToTimestamp(val timestamp: PlayerSkipTimestamp) : PlayerUiEvent
    data object NextEpisode : PlayerUiEvent
    data object PreviousEpisode : PlayerUiEvent
    data class ToggleControlsLock(val isLocked: Boolean? = null) : PlayerUiEvent
    data class VisibilityChanged(val isVisible: Boolean) : PlayerUiEvent
    data object ToggleControlsVisibility : PlayerUiEvent
    data class SetActiveModal(val modal: PlayerActiveModal?) : PlayerUiEvent

    data class LoadEpisode(
        val episode: PlayerEpisode,
        val accountId: Int = 0,
        val autoPlay: Boolean = true,
        val resumePosition: Long? = null
    ) : PlayerUiEvent

    data class LoadPlaylist(
        val playlist: List<PlayerEpisode>,
        val startIndex: Int = 0,
        val accountId: Int = 0,
        val autoPlay: Boolean = true
    ) : PlayerUiEvent

    data class LoadMedia(
        val url: String,
        val mediaId: Int? = null,
        val parentId: Int? = null,
        val accountId: Int = 0,
        val qualities: List<PlayerQuality> = emptyList(),
        val subtitles: List<PlayerSubtitleTrack> = emptyList(),
        val initialSubtitle: PlayerSubtitleTrack? = null,
        val skipTimestamps: List<PlayerSkipTimestamp> = emptyList(),
        val autoPlay: Boolean = true,
        val resumePosition: Long? = null
    ) : PlayerUiEvent

    data class UpdateQualitiesAndSubtitles(
        val qualities: List<PlayerQuality>,
        val subtitles: List<PlayerSubtitleTrack>
    ) : PlayerUiEvent

    data object DismissError : PlayerUiEvent
    data class SetAspectRatio(val aspectRatio: PlayerAspectRatio) : PlayerUiEvent
    data object CycleResizeMode : PlayerUiEvent
    data class SetSubtitleDelay(val delayMs: Long) : PlayerUiEvent
    data class SetAudioDelay(val delayMs: Long) : PlayerUiEvent
    data class SelectAudioTrack(val track: PlayerAudioTrack?) : PlayerUiEvent
    data class SetLockPin(val pin: String?) : PlayerUiEvent
    data class ShowLockPinDialog(
        val show: Boolean,
        val mode: LockPinDialogMode = LockPinDialogMode.Unlock
    ) : PlayerUiEvent
    data class UnlockWithPin(val pin: String) : PlayerUiEvent
    data object ClearLockPin : PlayerUiEvent
    data class SelectEpisode(val index: Int) : PlayerUiEvent
    data object SaveProgressNow : PlayerUiEvent
}

sealed interface PlayerUiEffect : UiEffect {
    data class ShowToast(val message: String) : PlayerUiEffect
    data class PlaybackError(val message: String) : PlayerUiEffect
    data object EpisodeCompleted : PlayerUiEffect
    data object NavigateBack : PlayerUiEffect
}
