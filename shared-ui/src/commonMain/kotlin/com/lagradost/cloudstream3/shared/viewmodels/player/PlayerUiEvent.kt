package com.lagradost.cloudstream3.shared.viewmodels.player

import com.lagradost.cloudstream3.shared.mvi.UiEffect
import com.lagradost.cloudstream3.shared.mvi.UiEvent

/**
 * UI Events dispatched to the [PlayerControllerViewModel] in the MVI architecture.
 */
sealed interface PlayerUiEvent : UiEvent {
    /** Resumes or starts playback. */
    data object Play : PlayerUiEvent

    /** Pauses active playback. */
    data object Pause : PlayerUiEvent

    /** Toggles play/pause state. */
    data object TogglePlayPause : PlayerUiEvent

    /** Stops playback and resets player state. */
    data object Stop : PlayerUiEvent

    /** Seeks to a specific millisecond timestamp. */
    data class SeekTo(val positionMs: Long) : PlayerUiEvent

    /** Seeks relative to the current position by [offsetMs] (positive for forward, negative for rewind). */
    data class SeekBy(val offsetMs: Long) : PlayerUiEvent

    /** Changes the active video stream quality. */
    data class SelectQuality(val quality: PlayerQuality) : PlayerUiEvent

    /** Selects or disables (if null) the active subtitle track. */
    data class SelectSubtitle(val subtitle: PlayerSubtitleTrack?) : PlayerUiEvent

    /** Sets the playback rate (e.g. 1.0f, 1.25f, 1.5f, 2.0f). */
    data class SetSpeed(val speed: Float) : PlayerUiEvent

    /** Alias for [SetSpeed] for semantic naming in player playback rate events. */
    typealias SetPlaybackSpeed = SetSpeed

    /** Skips the active intro or current skip segment. */
    data object SkipIntro : PlayerUiEvent

    /** Skips the active outro / credits segment or transitions to next episode. */
    data object SkipOutro : PlayerUiEvent

    /** Directly jumps to the end of a given skip timestamp marker. */
    data class SkipToTimestamp(val timestamp: PlayerSkipTimestamp) : PlayerUiEvent

    /** Navigates to the next episode in the loaded playlist. */
    data object NextEpisode : PlayerUiEvent

    /** Navigates to the previous episode in the loaded playlist. */
    data object PreviousEpisode : PlayerUiEvent

    /** Toggles or sets the controls lock state to prevent accidental touch inputs. */
    data class ToggleControlsLock(val isLocked: Boolean? = null) : PlayerUiEvent

    /** Updates the UI controls visibility. */
    data class VisibilityChanged(val isVisible: Boolean) : PlayerUiEvent

    /** Toggles UI controls visibility. */
    data object ToggleControlsVisibility : PlayerUiEvent

    /** Sets or dismisses the active modal dialog. */
    data class SetActiveModal(val modal: PlayerActiveModal?) : PlayerUiEvent

    /** Loads and plays a single episode metadata item. */
    data class LoadEpisode(
        val episode: PlayerEpisode,
        val accountId: Int = 0,
        val autoPlay: Boolean = true,
        val resumePosition: Long? = null
    ) : PlayerUiEvent

    /** Loads a playlist of episodes and initializes playback at [startIndex]. */
    data class LoadPlaylist(
        val playlist: List<PlayerEpisode>,
        val startIndex: Int = 0,
        val accountId: Int = 0,
        val autoPlay: Boolean = true
    ) : PlayerUiEvent

    /** Loads generic media directly from URL. */
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

    /** Dynamically updates or appends available qualities and subtitles as extraction completes. */
    data class UpdateQualitiesAndSubtitles(
        val qualities: List<PlayerQuality>,
        val subtitles: List<PlayerSubtitleTrack>
    ) : PlayerUiEvent

    /** Clears any error message in the UI state. */
    data object DismissError : PlayerUiEvent

    /** Changes aspect ratio */
    data class SetAspectRatio(val aspectRatio: PlayerAspectRatio) : PlayerUiEvent

    /** Cycles through available aspect ratio modes (Fit, Zoom, Stretch, Original). */
    data object CycleResizeMode : PlayerUiEvent

    /** Changes subtitle delay */
    data class SetSubtitleDelay(val delayMs: Long) : PlayerUiEvent

    /** Changes audio delay */
    data class SetAudioDelay(val delayMs: Long) : PlayerUiEvent

    /** Changes the active audio track */
    data class SelectAudioTrack(val track: PlayerAudioTrack?) : PlayerUiEvent

    /** Sets or updates the 4-digit PIN for screen locking */
    data class SetLockPin(val pin: String?) : PlayerUiEvent

    /** Displays or dismisses the PIN lock dialog */
    data class ShowLockPinDialog(
        val show: Boolean,
        val mode: LockPinDialogMode = LockPinDialogMode.Unlock
    ) : PlayerUiEvent

    /** Validates PIN entry to unlock screen */
    data class UnlockWithPin(val pin: String) : PlayerUiEvent

    /** Clears PIN lock */
    data object ClearLockPin : PlayerUiEvent

    /** Selects an episode from the playlist */
    data class SelectEpisode(val index: Int) : PlayerUiEvent

    /** Forces an immediate watch progress synchronization with Room KMP. */
    data object SaveProgressNow : PlayerUiEvent
}

/**
 * Optional single-shot side effects for the player.
 */
sealed interface PlayerUiEffect : UiEffect {
    data class ShowToast(val message: String) : PlayerUiEffect
    data class PlaybackError(val message: String) : PlayerUiEffect
    data object EpisodeCompleted : PlayerUiEffect
    data object NavigateBack : PlayerUiEffect
}
