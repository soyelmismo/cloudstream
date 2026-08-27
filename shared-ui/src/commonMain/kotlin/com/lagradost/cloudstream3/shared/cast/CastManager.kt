package com.lagradost.cloudstream3.shared.cast

import kotlinx.coroutines.flow.StateFlow

/**
 * Multiplatform Cast Manager abstraction providing discovery, connection,
 * remote media loading, and playback controls.
 *
 * Implementations:
 * - Android: Uses Google Cast SDK / MediaRouter + UPnP fallback.
 * - JVM Desktop: Uses SSDP / UPnP discovery with transparent fallback.
 */
expect class CastManager() {
    val isCastingSupported: Boolean
    val castState: StateFlow<CastState>
    val availableDevices: StateFlow<List<CastDevice>>
    val currentDevice: StateFlow<CastDevice?>
    val currentSession: StateFlow<CastSessionInfo?>

    fun startDiscovery()
    fun stopDiscovery()
    fun connect(device: CastDevice)
    fun disconnect()
    fun loadMedia(media: CastMediaItem, startPositionMs: Long = 0L, autoPlay: Boolean = true)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun stop()
    fun setVolume(volume: Float)
    fun release()

    companion object {
        fun getInstance(): CastManager
    }
}
