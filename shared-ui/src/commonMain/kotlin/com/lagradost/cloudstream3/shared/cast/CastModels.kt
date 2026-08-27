package com.lagradost.cloudstream3.shared.cast

/**
 * Protocol family supported by the discovered casting device.
 */
enum class CastProtocol {
    GOOGLE_CAST,
    UPNP_DLNA,
    DIAL,
    LOCAL_FALLBACK
}

/**
 * Lifecycle states of an active or pending cast connection.
 */
enum class CastState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    CASTING,
    PAUSED,
    BUFFERING,
    STOPPED,
    ERROR
}

/**
 * Target device capable of receiving media streams.
 */
data class CastDevice(
    val id: String,
    val name: String,
    val hostAddress: String? = null,
    val port: Int? = null,
    val protocol: CastProtocol = CastProtocol.LOCAL_FALLBACK,
    val isConnected: Boolean = false,
    val modelName: String? = null,
    val locationXmlUrl: String? = null,
    val controlUrl: String? = null
)

/**
 * Remote subtitle track for cast playback.
 */
data class CastSubtitle(
    val name: String,
    val url: String,
    val language: String? = null,
    val mimeType: String = "text/vtt"
)

/**
 * Media description payload sent to remote casting renderers.
 */
data class CastMediaItem(
    val title: String,
    val subtitle: String? = null,
    val url: String,
    val posterUrl: String? = null,
    val isMovie: Boolean = true,
    val currentEpisode: Int? = null,
    val durationMs: Long = 0L,
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<CastSubtitle> = emptyList()
)

/**
 * Active remote casting playback session info.
 */
data class CastSessionInfo(
    val device: CastDevice,
    val state: CastState = CastState.CONNECTED,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val volume: Float = 1.0f,
    val currentMedia: CastMediaItem? = null
)
