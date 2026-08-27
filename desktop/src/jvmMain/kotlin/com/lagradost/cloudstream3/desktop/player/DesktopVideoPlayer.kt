package com.lagradost.cloudstream3.desktop.player

import com.lagradost.cloudstream3.shared.player.PlayerEvent
import com.lagradost.cloudstream3.shared.player.PlayerState
import com.lagradost.cloudstream3.shared.player.VideoPlayer
import com.lagradost.cloudstream3.shared.viewmodels.player.PlayerQuality
import com.lagradost.cloudstream3.shared.viewmodels.player.PlayerSubtitleTrack
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
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
import com.lagradost.cloudstream3.app
import okhttp3.Request
import java.io.File
import java.io.Closeable
import java.nio.ByteBuffer
import javax.swing.JPanel

private const val HEADER_USER_AGENT = "user-agent"
private const val HEADER_REFERER = "referer"
private const val VIMEOS_ZIP_DOMAIN = ".vimeos.zip"
private const val VIMEOS_NET_DOMAIN = ".vimeos.net"
private const val VIMEOS_ZIP_ORIGIN = "https://vimeos.zip/"
private const val VIMEOS_NET_ORIGIN = "https://vimeos.net/"
private const val EXT_VTT = ".vtt"
private const val EXT_SRT = ".srt"
private const val EXT_ASS = ".ass"
private const val OPT_FORWARD_COOKIES = ":http-forward-cookies"
private const val OPT_HTTP_USER_AGENT = ":http-user-agent="
private const val OPT_HTTP_REFERRER = ":http-referrer="
private const val DEFAULT_ASPECT_RATIO = "Fit"

/**
 * VLCJ-based implementation of [VideoPlayer] for Compose Desktop (JVM).
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
    val isReadyFlow: StateFlow<Boolean> = _isReadyFlow.asStateFlow()
    val isReady: Boolean get() = _isReadyFlow.value

    private val _videoFrameFlow = MutableStateFlow<ImageBitmap?>(null)
    val videoFrameFlow: StateFlow<ImageBitmap?> = _videoFrameFlow.asStateFlow()

    private val _aspectRatioFlow = MutableStateFlow(DEFAULT_ASPECT_RATIO)
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

    private val nativePlayerLock = Any()
    private var playbackJob: Job? = null

    init {
        coroutineScope.launch(Dispatchers.IO) {
            initializeVlcBackend()
        }
    }

    private suspend fun initializeVlcBackend() {
        var errorMsg: String? = null

        try {
            NativeDiscovery().discover()

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
                        renderVideoFrame(nativeBuffers, bufferFormat)
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

        dispatchQueuedPlayback()
    }

    private fun renderVideoFrame(nativeBuffers: Array<ByteBuffer>, bufferFormat: BufferFormat) {
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

    private fun dispatchQueuedPlayback() {
        val requestToPlay = synchronized(pendingRequestLock) {
            val req = pendingPlayRequest
            pendingPlayRequest = null
            req
        } ?: return

        play(
            quality = PlayerQuality(url = requestToPlay.url, headers = requestToPlay.headers?.toImmutableMap() ?: persistentMapOf()),
            subtitles = requestToPlay.subtitles
        )
    }

    val videoSurfaceComponent: Component
        get() = rootSurfacePanel

    val mediaPlayer: MediaPlayer?
        get() = embeddedMediaPlayer

    private fun createEventListener(): MediaPlayerEventAdapter {
        return object : MediaPlayerEventAdapter() {
            override fun playing(mediaPlayer: MediaPlayer) {
                applyDeferredAudioSettings(mediaPlayer)
                _stateFlow.update { it.copy(isPlaying = true, isBuffering = false) }
                coroutineScope.launch { _events.emit(PlayerEvent.OnPlay) }
            }

            override fun paused(mediaPlayer: MediaPlayer) {
                _stateFlow.update { it.copy(isPlaying = false) }
                coroutineScope.launch { _events.emit(PlayerEvent.OnPause) }
            }

            override fun stopped(mediaPlayer: MediaPlayer) {
                _stateFlow.update { it.copy(isPlaying = false, isBuffering = false) }
                coroutineScope.launch { _events.emit(PlayerEvent.OnStop) }
            }

            override fun finished(mediaPlayer: MediaPlayer) {
                _stateFlow.update { it.copy(isPlaying = false, isBuffering = false) }
                coroutineScope.launch { _events.emit(PlayerEvent.OnStop) }
            }

            override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) {
                val isBuffering = newCache < 100.0f
                _stateFlow.update { it.copy(isBuffering = isBuffering) }
                if (isBuffering) {
                    coroutineScope.launch { _events.emit(PlayerEvent.OnBuffering) }
                }
            }

            override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) {
                val duration = _stateFlow.value.durationMs
                _stateFlow.update { it.copy(positionMs = newTime) }
                coroutineScope.launch { _events.emit(PlayerEvent.OnPositionChanged(newTime, duration)) }
            }

            override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
                _stateFlow.update { it.copy(durationMs = newLength.coerceAtLeast(0L)) }
            }

            override fun error(mediaPlayer: MediaPlayer) {
                _stateFlow.update { it.copy(isPlaying = false, isBuffering = false) }
                coroutineScope.launch { _events.emit(PlayerEvent.OnError("VLC playback error occurred.")) }
            }
        }
    }

    private fun applyDeferredAudioSettings(mediaPlayer: MediaPlayer) {
        coroutineScope.launch {
            try {
                mediaPlayer.audio().setVolume(targetVolume)
                if (targetMuted) {
                    mediaPlayer.audio().setMute(true)
                }
            } catch (_: Throwable) {}
        }
    }

    override fun play(quality: PlayerQuality, subtitles: List<PlayerSubtitleTrack>) {
        if (!_isReadyFlow.value) {
            queuePendingRequest(quality, subtitles)
            return
        }

        playbackJob?.cancel()
        playbackJob = coroutineScope.launch(Dispatchers.IO) {
            val player = mediaPlayer?.takeIf { isNativePlayerAvailable }
            if (player == null) {
                handlePlaybackUnavailable(quality.url)
                return@launch
            }
            executePlayback(player, quality, subtitles)
        }
    }

    private fun queuePendingRequest(quality: PlayerQuality, subtitles: List<PlayerSubtitleTrack>) {
        synchronized(pendingRequestLock) {
            pendingPlayRequest = PendingPlayRequest(
                url = quality.url,
                headers = quality.headers,
                subtitles = subtitles
            )
        }
        _stateFlow.update { it.copy(currentUrl = quality.url, isBuffering = true) }
    }

    private suspend fun handlePlaybackUnavailable(url: String) {
        _stateFlow.update { it.copy(currentUrl = url, isPlaying = false, isBuffering = false) }
        _events.emit(PlayerEvent.OnError("Cannot play media: LibVLC backend is not available."))
    }

    private fun executePlayback(
        player: MediaPlayer,
        quality: PlayerQuality,
        subtitles: List<PlayerSubtitleTrack>
    ) {
        try {
            _stateFlow.update {
                it.copy(
                    currentUrl = quality.url,
                    isPlaying = true,
                    isBuffering = true,
                    positionMs = 0L
                )
            }

            val options = buildVlcMediaOptions(quality)
            synchronized(nativePlayerLock) {
                player.media().play(quality.url, *options)
            }
            loadInitialSubtitle(subtitles)
        } catch (e: Exception) {
            _stateFlow.update { it.copy(isPlaying = false, isBuffering = false) }
            coroutineScope.launch {
                _events.emit(PlayerEvent.OnError("Failed to play media: ${e.message}"))
            }
        }
    }

    private fun resolveReferer(url: String, headers: Map<String, String>): String? {
        val rawReferer = headers.entries.firstOrNull { it.key.equals(HEADER_REFERER, ignoreCase = true) }?.value ?: return null
        return when {
            url.contains(VIMEOS_ZIP_DOMAIN, ignoreCase = true) && rawReferer.contains("vimeos.net") -> VIMEOS_ZIP_ORIGIN
            url.contains(VIMEOS_NET_DOMAIN, ignoreCase = true) && rawReferer.contains("vimeos.zip") -> VIMEOS_NET_ORIGIN
            else -> rawReferer
        }
    }

    private fun resolveUserAgent(headers: Map<String, String>): String {
        return headers.entries.firstOrNull { it.key.equals(HEADER_USER_AGENT, ignoreCase = true) }?.value
            ?: com.lagradost.cloudstream3.USER_AGENT
    }

    private fun buildVlcMediaOptions(quality: PlayerQuality): Array<String> {
        val options = mutableListOf(OPT_FORWARD_COOKIES)
        options.add("$OPT_HTTP_USER_AGENT${resolveUserAgent(quality.headers)}")
        resolveReferer(quality.url, quality.headers)?.takeIf { it.isNotBlank() }?.let {
            options.add("$OPT_HTTP_REFERRER$it")
        }
        return options.toTypedArray()
    }

    private fun loadInitialSubtitle(subtitles: List<PlayerSubtitleTrack>) {
        val targetSub = subtitles.firstOrNull { it.isDefault && it.url.isNotBlank() }
            ?: subtitles.firstOrNull { it.url.isNotBlank() }
            ?: return
        loadSubtitle(targetSub.url, targetSub.headers)
    }

    override fun play(url: String, headers: Map<String, String>?) {
        play(
            quality = PlayerQuality(url = url, headers = headers?.toImmutableMap() ?: persistentMapOf()),
            subtitles = emptyList()
        )
    }

    override fun play(url: String) {
        play(
            quality = PlayerQuality(url = url, headers = persistentMapOf()),
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
        } catch (_: Exception) {
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

    fun getVolume(): Int {
        val player = mediaPlayer
        return if (player != null && player.status().isPlaying) {
            player.audio().volume().takeIf { it >= 0 } ?: targetVolume
        } else {
            targetVolume
        }
    }

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

    fun isMuted(): Boolean {
        val player = mediaPlayer
        return if (player != null && player.status().isPlaying) {
            player.audio().isMute
        } else {
            targetMuted
        }
    }

    override fun setPlaybackSpeed(speed: Float) {
        try {
            mediaPlayer?.controls()?.setRate(speed)
        } catch (e: Exception) {
            System.err.println("DesktopVideoPlayer: Error setting playback speed: ${e.message}")
        }
    }

    override fun loadSubtitle(url: String, headers: Map<String, String>?) {
        if (!isRemoteUrl(url)) {
            applySubtitleFile(url)
            return
        }

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val tempFile = downloadRemoteSubtitle(url, headers) ?: return@launch
                applySubtitleFile(tempFile.absolutePath)
            } catch (e: Exception) {
                System.err.println("DesktopVideoPlayer: Error downloading subtitle: ${e.message}")
            }
        }
    }

    private fun isRemoteUrl(url: String): Boolean =
        url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)

    private fun determineSubtitleExtension(url: String): String = when {
        url.contains(EXT_VTT, ignoreCase = true) -> EXT_VTT
        url.contains(EXT_SRT, ignoreCase = true) -> EXT_SRT
        url.contains(EXT_ASS, ignoreCase = true) -> EXT_ASS
        else -> EXT_SRT
    }

    private fun downloadRemoteSubtitle(url: String, headers: Map<String, String>?): File? {
        val reqBuilder = Request.Builder().url(url)
        headers?.forEach { (key, value) -> reqBuilder.header(key, value) }

        return app.baseClient.newCall(reqBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) return null
            val bodyBytes = response.body.bytes()
            val ext = determineSubtitleExtension(url)
            File.createTempFile("cs_sub_", ext).apply {
                deleteOnExit()
                writeBytes(bodyBytes)
            }
        }
    }

    private fun applySubtitleFile(path: String) {
        try {
            mediaPlayer?.subpictures()?.setSubTitleFile(path)
        } catch (e: Exception) {
            System.err.println("DesktopVideoPlayer: Error applying subtitle file: ${e.message}")
        }
    }

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
            // LibVLC subtitle delay expects microseconds
            mediaPlayer?.subpictures()?.setDelay(delayMs * 1000L)
        } catch (e: Exception) {
            System.err.println("DesktopVideoPlayer: Error setting subtitle delay: ${e.message}")
        }
    }

    override fun setAudioDelay(delayMs: Long) {
        try {
            // LibVLC audio delay expects microseconds
            mediaPlayer?.audio()?.setDelay(delayMs * 1000L)
        } catch (e: Exception) {
            System.err.println("DesktopVideoPlayer: Error setting audio delay: ${e.message}")
        }
    }

    fun release() {
        synchronized(pendingRequestLock) {
            pendingPlayRequest = null
        }
        playbackJob?.cancel()
        playbackJob = null
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
