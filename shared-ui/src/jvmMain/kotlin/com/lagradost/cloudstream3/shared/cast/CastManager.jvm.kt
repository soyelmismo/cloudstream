package com.lagradost.cloudstream3.shared.cast

import kotlinx.coroutines.flow.StateFlow

/**
 * JVM Desktop implementation of [CastManager] with SSDP / UPnP discovery
 * and AVTransport remote playback control.
 * Delegates SSDP discovery and AVTransport SOAP commands to [UPnPCastClient].
 */
actual class CastManager actual constructor() {
    private val client = UPnPCastClient()

    actual val isCastingSupported: Boolean get() = client.isCastingSupported
    actual val castState: StateFlow<CastState> get() = client.castState
    actual val availableDevices: StateFlow<List<CastDevice>> get() = client.availableDevices
    actual val currentDevice: StateFlow<CastDevice?> get() = client.currentDevice
    actual val currentSession: StateFlow<CastSessionInfo?> get() = client.currentSession

    actual fun startDiscovery() = client.startDiscovery()
    actual fun stopDiscovery() = client.stopDiscovery()
    actual fun connect(device: CastDevice) = client.connect(device)
    actual fun disconnect() = client.disconnect()
    actual fun loadMedia(media: CastMediaItem, startPositionMs: Long, autoPlay: Boolean) =
        client.loadMedia(media, startPositionMs, autoPlay)
    actual fun play() = client.play()
    actual fun pause() = client.pause()
    actual fun seekTo(positionMs: Long) = client.seekTo(positionMs)
    actual fun stop() = client.stop()
    actual fun setVolume(volume: Float) = client.setVolume(volume)
    actual fun release() = client.release()

    actual companion object {
        private var instance: CastManager? = null

        actual fun getInstance(): CastManager {
            return instance ?: synchronized(this) {
                instance ?: CastManager().also { instance = it }
            }
        }
    }
}
