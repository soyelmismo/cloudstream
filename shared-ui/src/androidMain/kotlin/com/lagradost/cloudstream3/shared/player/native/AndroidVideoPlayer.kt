@file:Suppress("DEPRECATION")
package com.lagradost.cloudstream3.shared.player.native

import android.content.Context
import com.lagradost.cloudstream3.shared.player.PlayerEvent as SharedPlayerEvent
import com.lagradost.cloudstream3.shared.player.PlayerState
import com.lagradost.cloudstream3.shared.player.VideoPlayer
import com.lagradost.cloudstream3.shared.viewmodels.player.PlayerQuality
import com.lagradost.cloudstream3.shared.viewmodels.player.PlayerSubtitleTrack
import com.lagradost.cloudstream3.utils.Coroutines.runOnMainThread
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.SubtitleUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Android implementation of [VideoPlayer] contract that wraps [IPlayer] / [CS3IPlayer] (ExoPlayer).
 * Keeps video playback logic completely decoupled from Android UI views.
 *
 * @param context Android context for ExoPlayer data source and rendering factories.
 * @param player Underlying CloudStream player instance (defaults to [CS3IPlayer]).
 * @param scope Coroutine scope used for event emission fallbacks.
 */
class AndroidVideoPlayer(
    private val context: Context,
    val player: IPlayer = CS3IPlayer(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate)
) : VideoPlayer {

    private val _state = MutableStateFlow(PlayerState())
    override val state: PlayerState get() = _state.value
    override val stateFlow: StateFlow<PlayerState> get() = _state.asStateFlow()

    private val _exoPlayerState = MutableStateFlow<androidx.media3.common.Player?>(null)
    val exoPlayerState: StateFlow<androidx.media3.common.Player?> get() = _exoPlayerState.asStateFlow()
    val exoPlayer: androidx.media3.common.Player? get() = _exoPlayerState.value

    private val _resizeMode = MutableStateFlow(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT)
    val resizeMode: StateFlow<Int> get() = _resizeMode.asStateFlow()

    private val _events = MutableSharedFlow<SharedPlayerEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<SharedPlayerEvent> get() = _events.asSharedFlow()

    init {
        player.initCallbacks(eventHandler = { event ->
            handleInternalPlayerEvent(event)
        })
    }

    private fun handleInternalPlayerEvent(event: PlayerEvent) {
        android.util.Log.d("CloudStreamDebug", "AndroidVideoPlayer internal event: ${event::class.simpleName} ($event)")
        when (event) {
            is PlayerAttachedEvent -> {
                _exoPlayerState.value = event.player as? androidx.media3.common.Player
            }
            is PositionEvent -> {
                _state.value = _state.value.copy(
                    positionMs = event.toMs,
                    durationMs = event.durationMs
                )
                emitEvent(SharedPlayerEvent.OnPositionChanged(positionMs = event.toMs, durationMs = event.durationMs))
            }
            is StatusEvent -> {
                when (event.isPlaying) {
                    CSPlayerLoading.IsPlaying -> {
                        _state.value = _state.value.copy(isPlaying = true, isBuffering = false)
                        emitEvent(SharedPlayerEvent.OnPlay)
                    }
                    CSPlayerLoading.IsBuffering -> {
                        _state.value = _state.value.copy(isBuffering = true)
                        emitEvent(SharedPlayerEvent.OnBuffering)
                    }
                    CSPlayerLoading.IsPaused -> {
                        _state.value = _state.value.copy(isPlaying = false, isBuffering = false)
                        emitEvent(SharedPlayerEvent.OnPause)
                    }
                    CSPlayerLoading.IsEnded -> {
                        _state.value = _state.value.copy(isPlaying = false, isBuffering = false)
                        emitEvent(SharedPlayerEvent.OnStop)
                    }
                }
            }
            is PlayEvent -> {
                _state.value = _state.value.copy(isPlaying = true, isBuffering = false)
                emitEvent(SharedPlayerEvent.OnPlay)
            }
            is PauseEvent -> {
                _state.value = _state.value.copy(isPlaying = false)
                emitEvent(SharedPlayerEvent.OnPause)
            }
            is VideoEndedEvent -> {
                _state.value = _state.value.copy(isPlaying = false, isBuffering = false)
                emitEvent(SharedPlayerEvent.OnStop)
            }
            is ErrorEvent -> {
                val errorMsg = event.error.localizedMessage ?: event.error.message ?: "Playback error"
                emitEvent(SharedPlayerEvent.OnError(errorMsg))
            }
            else -> {
                // Other internal UI/torrent events are ignored by the shared contract
            }
        }
    }

    private fun emitEvent(event: SharedPlayerEvent) {
        if (!_events.tryEmit(event)) {
            scope.launch {
                _events.emit(event)
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun play(quality: PlayerQuality, subtitles: List<PlayerSubtitleTrack>) {
        runOnMainThread {
            android.util.Log.d("CloudStreamDebug", "AndroidVideoPlayer.play called: url=${quality.url}, headers=${quality.headers}, subtitles=${subtitles.size}")
            val link = quality.extractorLink ?: run {
                val referer = quality.headers["Referer"] ?: quality.headers["referer"] ?: ""
                val isM3u8 = quality.url.contains(".m3u8", ignoreCase = true) || quality.url.contains("m3u8", ignoreCase = true)
                val isMpd = quality.url.contains(".mpd", ignoreCase = true)
                val linkType = when {
                    isM3u8 -> ExtractorLinkType.M3U8
                    isMpd -> ExtractorLinkType.DASH
                    else -> ExtractorLinkType.VIDEO
                }
                ExtractorLink(
                    source = "Direct",
                    name = quality.name.ifBlank { "Video" },
                    url = quality.url,
                    referer = referer,
                    headers = quality.headers,
                    quality = if (quality.quality != 0) quality.quality else Qualities.Unknown.value,
                    type = linkType
                )
            }

            val subDataSet = subtitles.mapNotNull { track ->
                track.subtitleFile?.let { SubtitleUtils.fromSubtitleFile(it) } ?: if (track.url.isNotBlank()) {
                    SubtitleData(
                        name = track.name,
                        url = track.url,
                        origin = SubtitleOrigin.URL,
                        mimeType = SubtitleUtils.fromExtensionToMime(track.url),
                        headers = track.headers
                    )
                } else null
            }.toSet()

            val defaultTrack = subtitles.firstOrNull { it.isDefault }
            val selectedSub = defaultTrack?.let { def ->
                subDataSet.firstOrNull { it.url == def.url }
            } ?: subDataSet.firstOrNull()

            _state.value = _state.value.copy(
                currentUrl = quality.url,
                isBuffering = true,
                isPlaying = false,
                positionMs = 0L
            )
            emitEvent(SharedPlayerEvent.OnBuffering)

            player.loadPlayer(
                context = context,
                sameEpisode = false,
                link = link,
                data = null,
                startPosition = null,
                subtitles = subDataSet,
                subtitle = selectedSub,
                autoPlay = true
            )
        }
    }

    override fun play(url: String, headers: Map<String, String>?) {
        play(PlayerQuality(url = url, headers = headers ?: emptyMap()))
    }

    override fun play(url: String) {
        play(PlayerQuality(url = url))
    }

    override fun loadSubtitle(url: String, headers: Map<String, String>?) {
        if (url.isNotBlank()) {
            val sub = SubtitleData(
                name = "Subtitle",
                url = url,
                origin = SubtitleOrigin.URL,
                mimeType = SubtitleUtils.fromExtensionToMime(url),
                headers = headers ?: emptyMap()
            )
            runOnMainThread {
                player.initSubtitles(setOf(sub), sub)
            }
        }
    }

    override fun pause() {
        runOnMainThread {
            player.handleEvent(CSPlayerEvent.Pause)
            _state.value = _state.value.copy(isPlaying = false)
            emitEvent(SharedPlayerEvent.OnPause)
        }
    }

    override fun resume() {
        runOnMainThread {
            player.handleEvent(CSPlayerEvent.Play)
            _state.value = _state.value.copy(isPlaying = true)
            emitEvent(SharedPlayerEvent.OnPlay)
        }
    }

    override fun stop() {
        runOnMainThread {
            player.onStop()
            player.release()
            _state.value = PlayerState()
            emitEvent(SharedPlayerEvent.OnStop)
        }
    }

    override fun seekTo(positionMs: Long) {
        runOnMainThread {
            player.seekTo(positionMs)
            _state.value = _state.value.copy(positionMs = positionMs)
            emitEvent(SharedPlayerEvent.OnPositionChanged(positionMs, _state.value.durationMs))
        }
    }

    override fun setPlaybackSpeed(speed: Float) {
        runOnMainThread {
            player.setPlaybackSpeed(speed)
        }
    }

    override fun setAspectRatio(ratio: String) {
        val mode = when (ratio.lowercase()) {
            "zoom" -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            "stretch", "fill" -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
            "original" -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
            else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        _resizeMode.value = mode
    }

    /**
     * Release player resources and callbacks.
     */
    fun release() {
        runOnMainThread {
            player.releaseCallbacks()
            player.release()
        }
    }
}
