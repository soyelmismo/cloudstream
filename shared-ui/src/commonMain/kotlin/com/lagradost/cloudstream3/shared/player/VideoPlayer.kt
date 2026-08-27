package com.lagradost.cloudstream3.shared.player

import com.lagradost.cloudstream3.shared.viewmodels.player.PlayerQuality
import com.lagradost.cloudstream3.shared.viewmodels.player.PlayerSubtitleTrack
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Agnostic Video Player interface for Compose Multiplatform.
 * This abstracts away ExoPlayer (Android) and VLCJ/MPV (Desktop).
 */
interface VideoPlayer {
    /** Plays the media using full PlayerQuality metadata and available subtitle tracks. */
    fun play(quality: PlayerQuality, subtitles: List<PlayerSubtitleTrack> = emptyList()) {
        play(quality.url, quality.headers)
        val defaultSub = subtitles.firstOrNull { it.isDefault } ?: subtitles.firstOrNull()
        if (defaultSub != null && defaultSub.url.isNotBlank()) {
            loadSubtitle(defaultSub.url, defaultSub.headers)
        }
    }

    /** Plays the media at the specified URL with optional HTTP headers. */
    fun play(url: String, headers: Map<String, String>? = null) {
        play(url)
    }

    /** Plays the media at the specified URL. */
    fun play(url: String)
    
    /** Pauses playback. */
    fun pause()
    
    /** Resumes playback. */
    fun resume()
    
    /** Stops playback and releases resources. */
    fun stop()
    
    /** Seeks to a specific position in milliseconds. */
    fun seekTo(positionMs: Long)

    /** Sets the playback speed (e.g. 1.0f for normal, 1.5f, 2.0f, etc.). */
    fun setPlaybackSpeed(speed: Float) {}

    /** Loads or selects external subtitle track by URL or file path. */
    fun loadSubtitle(url: String, headers: Map<String, String>? = null) {}

    /** Sets the aspect ratio. */
    fun setAspectRatio(ratio: String) {}

    /** Sets the subtitle delay in milliseconds. */
    fun setSubtitleDelay(delayMs: Long) {}

    /** Sets the audio delay in milliseconds. */
    fun setAudioDelay(delayMs: Long) {}

    
    /** Returns the current state of the player. */
    val state: PlayerState

    /** Returns the StateFlow of the player state for reactive UI updates. */
    val stateFlow: StateFlow<PlayerState>

    /** Flow of player events emitted to the UI (MVI). */
    val events: SharedFlow<PlayerEvent>
}

/**
 * Represents the current state of the video player.
 */
data class PlayerState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val currentUrl: String? = null
)

/**
 * Events emitted by the VideoPlayer implementation to the UI (MVI).
 */
sealed class PlayerEvent {
    object OnPlay : PlayerEvent()
    object OnPause : PlayerEvent()
    object OnStop : PlayerEvent()
    object OnBuffering : PlayerEvent()
    object OnUserInteraction : PlayerEvent()
    object OnToggleControls : PlayerEvent()
    object OnBackRequested : PlayerEvent()
    data class OnError(val message: String) : PlayerEvent()
    data class OnPositionChanged(val positionMs: Long, val durationMs: Long) : PlayerEvent()
}

/**
 * CompositionLocal providing access to the active [VideoPlayer] instance.
 */
val LocalVideoPlayer = androidx.compose.runtime.staticCompositionLocalOf<VideoPlayer?> { null }

/**
 * CompositionLocal providing the platform-specific video surface composable.
 */
val LocalVideoPlayerContent = androidx.compose.runtime.staticCompositionLocalOf<(@androidx.compose.runtime.Composable (VideoPlayer, androidx.compose.ui.Modifier) -> Unit)?> { null }

