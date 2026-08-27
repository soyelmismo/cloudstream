package com.lagradost.cloudstream3.shared.cast

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

/**
 * Protocol family supported by the discovered casting device.
 */
@Immutable
enum class CastProtocol {
    GOOGLE_CAST,
    UPNP_DLNA,
    DIAL,
    LOCAL_FALLBACK
}

/**
 * Lifecycle states of an active or pending cast connection.
 */
@Immutable
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
@Immutable
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
@Immutable
data class CastSubtitle(
    val name: String,
    val url: String,
    val language: String? = null,
    val mimeType: String = "text/vtt"
)

/**
 * Media description payload sent to remote casting renderers.
 */
@Immutable
data class CastMediaItem(
    val title: String,
    val subtitle: String? = null,
    val url: String,
    val posterUrl: String? = null,
    val isMovie: Boolean = true,
    val currentEpisode: Int? = null,
    val durationMs: Long = 0L,
    val headers: ImmutableMap<String, String> = persistentMapOf(),
    val subtitles: ImmutableList<CastSubtitle> = persistentListOf()
)

/**
 * Active remote casting playback session info.
 */
@Immutable
data class CastSessionInfo(
    val device: CastDevice,
    val state: CastState = CastState.CONNECTED,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val volume: Float = 1.0f,
    val currentMedia: CastMediaItem? = null
)
