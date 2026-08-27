package com.lagradost.cloudstream3.desktop.player

import com.lagradost.cloudstream3.shared.player.PlayerEvent
import com.lagradost.cloudstream3.shared.player.PlayerState
import com.lagradost.cloudstream3.shared.player.VideoPlayer
import com.lagradost.cloudstream3.shared.viewmodels.player.PlayerQuality
import com.lagradost.cloudstream3.shared.viewmodels.player.PlayerSubtitleTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image as SkiaImage
import org.jetbrains.skia.ImageInfo
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.CallbackVideoSurface
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import com.lagradost.cloudstream3.app
import okhttp3.Request
import java.io.File
import java.io.Closeable
import java.nio.ByteBuffer
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

/**
 * VLCJ-based implementation of [VideoPlayer] for Compose Desktop (JVM).
 *
 * Performs asynchronous non-blocking native discovery and initialization on Dispatchers.IO,
 * manages deferred volume control and playback queueing, synchronizes reactive [PlayerState],
 * and renders video frames directly into Compose Skia ImageBitmap via [videoFrameFlow].
 */
class DesktopVideoPlayer(
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val vlcArgs: List<String> = defaultVlcArgs
) : VideoPlayer, Closeable {

    companion object {
        val defaultVlcArgs: List<String> = listOf(
            "--no-video-title-show",
            "--network-caching=3000",
            "--file-caching=3000",
            "--live-caching=3000",
            "--disc-caching=3000",
            "--no-stats",
            "--no-osd",
            "--no-snapshot-preview",
            "--no-sub-autodetect-file",
            "--avcodec-threads=1",
            "--http-user-agent=" + com.lagradost.cloudstream3.USER_AGENT
        )
    }

    private data class PendingPlayRequest(
        val url: String,
        val headers: Map<String, String>? = null,
        val subtitles: List<PlayerSubtitleTrack> = emptyList()
    )

    private val _stateFlow = MutableStateFlow(PlayerState())
    override val stateFlow: StateFlow<PlayerState> = _stateFlow.asStateFlow()
    override val state: PlayerState get() = _stateFlow.value

    private val _events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<PlayerEvent> = _events.asSharedFlow()

    private val _isReadyFlow = MutableStateFlow(false)
    /** Emits true once the underlying VLCJ player has completed initialization. */
    val isReadyFlow: StateFlow<Boolean> = _isReadyFlow.asStateFlow()
    val isReady: Boolean get() = _isReadyFlow.value

    private val _videoFrameFlow = MutableStateFlow<ImageBitmap?>(null)
    /** Reactive stream of video frames rendered directly to Compose ImageBitmap. */
    val videoFrameFlow: StateFlow<ImageBitmap?> = _videoFrameFlow.asStateFlow()

    private val _aspectRatioFlow = MutableStateFlow("Fit")
    val aspectRatioFlow: StateFlow<String> = _aspectRatioFlow.asStateFlow()

    private var mediaPlayerFactory: MediaPlayerFactory? = null
    private var embeddedMediaPlayer: EmbeddedMediaPlayer? = null

    private val rootSurfacePanel = JPanel(BorderLayout()).apply {
        background = Color.BLACK
        isOpaque = true
        preferredSize = Dimension(640, 360)
    }

    @Volatile
    private var isNativePlayerAvailable: Boolean = false

    @Volatile
    private var targetVolume: Int = 100

    @Volatile
    private var targetMuted: Boolean = false

    private val pendingRequestLock = Any()
    private var pendingPlayRequest: PendingPlayRequest? = null

    init {
        // Run heavy LibVLC discovery and native instantiation on Dispatchers.IO
        coroutineScope.launch(Dispatchers.IO) {
            initializeVlcBackend()
        }
    }

    private suspend fun initializeVlcBackend() {
        var errorMsg: String? = null

        try {
            // Attempt to discover LibVLC native binaries on host OS (Linux / Windows / macOS)
            NativeDiscovery().discover()

            // Instantiate hardware-accelerated VLC player with direct Skia frame rendering
            val factory = MediaPlayerFactory(*vlcArgs.toTypedArray())
            mediaPlayerFactory = factory
            val player = factory.mediaPlayers().newEmbeddedMediaPlayer()
            embeddedMediaPlayer = player

            val videoSurface = CallbackVideoSurface(
                object : BufferFormatCallback {
                    override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
                        val w = if (sourceWidth > 0) sourceWidth else 1920
                        val h = if (sourceHeight > 0) sourceHeight else 1080
                        return RV32BufferFormat(w, h)
                    }

                    override fun allocatedBuffers(buffers: Array<ByteBuffer>) {}
                },
                object : RenderCallback {
                    override fun display(
                        mediaPlayer: MediaPlayer,
                        nativeBuffers: Array<ByteBuffer>,
                        bufferFormat: BufferFormat
                    ) {
                        val width = bufferFormat.width
                        val height = bufferFormat.height
                        if (width <= 0 || height <= 0 || nativeBuffers.isEmpty()) return

                        try {
                            val byteBuffer = nativeBuffers[0].duplicate()
                            val expectedBytes = width * height * 4
                            if (byteBuffer.capacity() < expectedBytes) return

                            byteBuffer.position(0)
                            val byteArray = ByteArray(expectedBytes)
                            byteBuffer.get(byteArray, 0, expectedBytes)

                            val imageInfo = ImageInfo(
                                width = width,
                                height = height,
                                colorType = ColorType.BGRA_8888,
                                alphaType = ColorAlphaType.PREMUL
                            )
                            val skiaImage = SkiaImage.makeRaster(imageInfo, byteArray, width * 4)
                            _videoFrameFlow.value = skiaImage.toComposeImageBitmap()
                        } catch (_: Throwable) {}
                    }
                },
                false,
                null
            )
            player.videoSurface().set(videoSurface)

            player.events().addMediaPlayerEventListener(createEventListener())
            try {
                player.audio().setVolume(100)
                player.audio().setMute(false)
            } catch (_: Throwable) {}

            isNativePlayerAvailable = true
        } catch (t: Throwable) {
            System.err.println("DesktopVideoPlayer: Failed to initialize LibVLC/VLCJ backend: ${t.message}")
            errorMsg = t.message ?: "LibVLC native library could not be loaded."
            isNativePlayerAvailable = false
        }

        _isReadyFlow.value = true

        if (!isNativePlayerAvailable) {
            _events.emit(PlayerEvent.OnError("LibVLC native library not available on system: $errorMsg"))
        }

        // Process any play requests queued during async initialization
        val requestToPlay = synchronized(pendingRequestLock) {
            val req = pendingPlayRequest
            pendingPlayRequest = null
            req
        }

        if (requestToPlay != null) {
            play(
                quality = PlayerQuality(url = requestToPlay.url, headers = requestToPlay.headers ?: emptyMap()),
                subtitles = requestToPlay.subtitles
            )
        }
    }

    /**
     * Returns the AWT [Component] used to render the video surface if Swing interop is needed.
     */
    val videoSurfaceComponent: Component
        get() = rootSurfacePanel

    /**
     * The underlying VLCJ [MediaPlayer] instance, or null if unavailable.
     */
    val mediaPlayer: MediaPlayer?
        get() = embeddedMediaPlayer

    private fun createEventListener(): MediaPlayerEventAdapter {
        return object : MediaPlayerEventAdapter() {
            override fun playing(mediaPlayer: MediaPlayer) {
                // Apply deferred volume and mute settings safely when physical playback starts
                coroutineScope.launch {
                    try {
                        mediaPlayer.audio().setVolume(targetVolume)
                        if (targetMuted) {
                            mediaPlayer.audio().setMute(true)
                        }
                    } catch (_: Throwable) {}
                }

                _stateFlow.update { it.copy(isPlaying = true, isBuffering = false) }
                coroutineScope.launch {
                    _events.emit(PlayerEvent.OnPlay)
                }
            }

            override fun paused(mediaPlayer: MediaPlayer) {
                _stateFlow.update { it.copy(isPlaying = false) }
                coroutineScope.launch {
                    _events.emit(PlayerEvent.OnPause)
                }
            }

            override fun stopped(mediaPlayer: MediaPlayer) {
                _stateFlow.update { it.copy(isPlaying = false, isBuffering = false) }
                coroutineScope.launch {
                    _events.emit(PlayerEvent.OnStop)
                }
            }

            override fun finished(mediaPlayer: MediaPlayer) {
                _stateFlow.update { it.copy(isPlaying = false, isBuffering = false) }
                coroutineScope.launch {
                    _events.emit(PlayerEvent.OnStop)
                }
            }

            override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) {
                val isBuffering = newCache < 100.0f
                _stateFlow.update { it.copy(isBuffering = isBuffering) }
                if (isBuffering) {
                    coroutineScope.launch {
                        _events.emit(PlayerEvent.OnBuffering)
                    }
                }
            }

            override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) {
                val duration = _stateFlow.value.durationMs
                _stateFlow.update { it.copy(positionMs = newTime) }
                coroutineScope.launch {
                    _events.emit(PlayerEvent.OnPositionChanged(newTime, duration))
                }
            }

            override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
                _stateFlow.update { it.copy(durationMs = newLength.coerceAtLeast(0L)) }
            }

            override fun error(mediaPlayer: MediaPlayer) {
                _stateFlow.update { it.copy(isPlaying = false, isBuffering = false) }
                coroutineScope.launch {
                    _events.emit(PlayerEvent.OnError("VLC playback error occurred."))
                }
            }
        }
    }

    private fun createPlaceholderPanel(message: String, bgColor: Color): JPanel {
        return JPanel(BorderLayout()).apply {
            background = bgColor
            isOpaque = true
            preferredSize = Dimension(640, 360)
            val label = JLabel(message, SwingConstants.CENTER).apply {
                foreground = Color.LIGHT_GRAY
                font = Font(Font.SANS_SERIF, Font.PLAIN, 14)
            }
            add(label, BorderLayout.CENTER)
        }
    }

    private val nativePlayerLock = Any()
    private var playbackJob: Job? = null

    /**
     * Loads and immediately begins playback of the media item using [PlayerQuality] and [subtitles].
     * If called before async initialization finishes, the request is safely queued.
     */
    override fun play(quality: PlayerQuality, subtitles: List<PlayerSubtitleTrack>) {
        if (!_isReadyFlow.value) {
            synchronized(pendingRequestLock) {
                pendingPlayRequest = PendingPlayRequest(
                    url = quality.url,
                    headers = quality.headers,
                    subtitles = subtitles
                )
            }
            _stateFlow.update { it.copy(currentUrl = quality.url, isBuffering = true) }
            return
        }

        playbackJob?.cancel()
        playbackJob = coroutineScope.launch(Dispatchers.IO) {
            val player = mediaPlayer
            if (player == null || !isNativePlayerAvailable) {
                _stateFlow.update { it.copy(currentUrl = quality.url, isPlaying = false, isBuffering = false) }
                _events.emit(PlayerEvent.OnError("Cannot play media: LibVLC backend is not available."))
                return@launch
            }

            try {
                _stateFlow.update {
                    it.copy(
                        currentUrl = quality.url,
                        isPlaying = true,
                        isBuffering = true,
                        positionMs = 0L
                    )
                }

                val mediaOptions = mutableListOf<String>()
                mediaOptions.add(":http-forward-cookies")
                val customUa = quality.headers.entries.firstOrNull { it.key.equals("user-agent", ignoreCase = true) }?.value
                mediaOptions.add(":http-user-agent=" + (customUa ?: com.lagradost.cloudstream3.USER_AGENT))
                val rawReferer = quality.headers.entries.firstOrNull { it.key.equals("referer", ignoreCase = true) }?.value
                val referer = if (quality.url.contains(".vimeos.zip", ignoreCase = true) && rawReferer?.contains("vimeos.net") == true) {
                    "https://vimeos.zip/"
                } else if (quality.url.contains(".vimeos.net", ignoreCase = true) && rawReferer?.contains("vimeos.zip") == true) {
                    "https://vimeos.net/"
                } else {
                    rawReferer
                }

                if (!referer.isNullOrBlank()) {
                    mediaOptions.add(":http-referrer=$referer")
                }

                println("DesktopVideoPlayer: Playing directly via LibVLC: ${quality.url} with options: $mediaOptions")
                synchronized(nativePlayerLock) {
                    player.media().play(quality.url, *mediaOptions.toTypedArray())
                }

                val defaultSub = subtitles.firstOrNull { it.isDefault } ?: subtitles.firstOrNull()
                if (defaultSub != null && defaultSub.url.isNotBlank()) {
                    loadSubtitle(defaultSub.url, defaultSub.headers)
                }
            } catch (e: Exception) {
                _stateFlow.update { it.copy(isPlaying = false, isBuffering = false) }
                _events.emit(PlayerEvent.OnError("Failed to play media: ${e.message}"))
            }
        }
    }

    /**
     * Loads and immediately begins playback of the media item at [url].
     * If called before async initialization finishes, the request is safely queued.
     */
    override fun play(url: String, headers: Map<String, String>?) {
        play(
            quality = PlayerQuality(url = url, headers = headers ?: emptyMap()),
            subtitles = emptyList()
        )
    }

    override fun play(url: String) {
        play(
            quality = PlayerQuality(url = url, headers = emptyMap()),
            subtitles = emptyList()
        )
    }

    override fun pause() {
        val player = mediaPlayer ?: return
        try {
            synchronized(nativePlayerLock) {
                player.controls().pause()
            }
        } catch (e: Exception) {
            coroutineScope.launch {
                _events.emit(PlayerEvent.OnError("Failed to pause: ${e.message}"))
            }
        }
    }

    override fun resume() {
        val player = mediaPlayer ?: return
        try {
            synchronized(nativePlayerLock) {
                player.controls().play()
            }
        } catch (e: Exception) {
            coroutineScope.launch {
                _events.emit(PlayerEvent.OnError("Failed to resume: ${e.message}"))
            }
        }
    }

    override fun stop() {
        synchronized(pendingRequestLock) {
            pendingPlayRequest = null
        }
        val player = mediaPlayer
        try {
            synchronized(nativePlayerLock) {
                player?.controls()?.stop()
            }
        } catch (e: Exception) {
            // Ignore error on stop
        } finally {
            _stateFlow.update {
                it.copy(isPlaying = false, isBuffering = false, positionMs = 0L)
            }
            coroutineScope.launch {
                _events.emit(PlayerEvent.OnStop)
            }
        }
    }

    override fun seekTo(positionMs: Long) {
        val player = mediaPlayer ?: return
        try {
            synchronized(nativePlayerLock) {
                player.controls().setTime(positionMs)
            }
            _stateFlow.update { it.copy(positionMs = positionMs) }
        } catch (e: Exception) {
            coroutineScope.launch {
                _events.emit(PlayerEvent.OnError("Failed to seek: ${e.message}"))
            }
        }
    }

    /**
     * Sets playback volume (0 - 100 or up to 200).
     *
     * Handles deferred volume: saves desired volume and automatically applies it
     * immediately if playing, or as soon as the media begins physical playback.
     */
    fun setVolume(volume: Int) {
        val clamped = volume.coerceIn(0, 200)
        targetVolume = clamped
        val player = mediaPlayer
        if (player != null && player.status().isPlaying) {
            try {
                player.audio().setVolume(clamped)
            } catch (e: Exception) {
                System.err.println("DesktopVideoPlayer: Error setting volume: ${e.message}")
            }
        }
    }

    /**
     * Gets current playback volume or the deferred target volume.
     */
    fun getVolume(): Int {
        val player = mediaPlayer
        return if (player != null && player.status().isPlaying) {
            player.audio().volume().takeIf { it >= 0 } ?: targetVolume
        } else {
            targetVolume
        }
    }

    /**
     * Toggles or sets audio mute.
     * Stores state and applies immediately if playing or deferred upon playback.
     */
    fun setMute(mute: Boolean) {
        targetMuted = mute
        val player = mediaPlayer
        if (player != null && player.status().isPlaying) {
            try {
                player.audio().setMute(mute)
            } catch (e: Exception) {
                System.err.println("DesktopVideoPlayer: Error setting mute: ${e.message}")
            }
        }
    }

    /**
     * Whether audio is currently muted.
     */
    fun isMuted(): Boolean {
        val player = mediaPlayer
        return if (player != null && player.status().isPlaying) {
            player.audio().isMute
        } else {
            targetMuted
        }
    }

    /**
     * Sets playback speed on VLC player.
     */
    override fun setPlaybackSpeed(speed: Float) {
        try {
            mediaPlayer?.controls()?.setRate(speed)
        } catch (e: Exception) {
            System.err.println("DesktopVideoPlayer: Error setting playback speed: ${e.message}")
        }
    }

    /**
     * Loads external subtitles from URL or file path.
     */
    override fun loadSubtitle(url: String, headers: Map<String, String>?) {
        try {
            if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val reqBuilder = Request.Builder().url(url)
                        headers?.forEach { (key, value) ->
                            reqBuilder.header(key, value)
                        }
                        val req = reqBuilder.build()
                        val res = app.baseClient.newCall(req).execute()
                        if (res.isSuccessful) {
                            val ext = when {
                                url.contains(".vtt", ignoreCase = true) -> ".vtt"
                                url.contains(".srt", ignoreCase = true) -> ".srt"
                                url.contains(".ass", ignoreCase = true) -> ".ass"
                                else -> ".srt"
                            }
                            val tempFile = File.createTempFile("cs_sub_", ext).apply {
                                deleteOnExit()
                                writeBytes(res.body.bytes())
                            }
                            mediaPlayer?.subpictures()?.setSubTitleFile(tempFile.absolutePath)
                        }
                        res.close()
                    } catch (e: Exception) {
                        System.err.println("DesktopVideoPlayer: Error downloading subtitle: ${e.message}")
                    }
                }
            } else {
                mediaPlayer?.subpictures()?.setSubTitleFile(url)
            }
        } catch (e: Exception) {
            System.err.println("DesktopVideoPlayer: Error loading subtitle: ${e.message}")
        }
    }

    /**
     * Convenience method for loading subtitles from a path or URL without explicit headers.
     */
    fun loadSubtitle(subtitlePath: String): Boolean {
        loadSubtitle(subtitlePath, headers = null)
        return true
    }

    override fun setAspectRatio(ratio: String) {
        _aspectRatioFlow.value = ratio
        try {
            mediaPlayer?.video()?.setAspectRatio(ratio)
        } catch (e: Exception) {
            System.err.println("DesktopVideoPlayer: Error setting aspect ratio: ${e.message}")
        }
    }

    override fun setSubtitleDelay(delayMs: Long) {
        try {
            // VLC subpicture delay is in microseconds
            mediaPlayer?.subpictures()?.setDelay(delayMs * 1000L)
        } catch (e: Exception) {
            System.err.println("DesktopVideoPlayer: Error setting subtitle delay: ${e.message}")
        }
    }

    override fun setAudioDelay(delayMs: Long) {
        try {
            // VLC audio delay is in microseconds
            mediaPlayer?.audio()?.setDelay(delayMs * 1000L)
        } catch (e: Exception) {
            System.err.println("DesktopVideoPlayer: Error setting audio delay: ${e.message}")
        }
    }

    /**
     * Releases the native player and clears resources.
     */
    fun release() {
        synchronized(pendingRequestLock) {
            pendingPlayRequest = null
        }
        try {
            embeddedMediaPlayer?.release()
            embeddedMediaPlayer = null
            mediaPlayerFactory?.release()
            mediaPlayerFactory = null
            _videoFrameFlow.value = null
        } catch (e: Exception) {
            System.err.println("DesktopVideoPlayer: Error releasing VLC player: ${e.message}")
        } finally {
            coroutineScope.cancel()
        }
    }

    override fun close() {
        release()
    }
}
