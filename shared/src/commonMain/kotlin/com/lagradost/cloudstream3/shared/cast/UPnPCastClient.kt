package com.lagradost.cloudstream3.shared.cast

import com.lagradost.cloudstream4.generated.resources.Res
import com.lagradost.cloudstream4.generated.resources.cast_default_device_name
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import kotlin.math.max

private const val AV_TRANSPORT_SERVICE_TYPE = "urn:schemas-upnp-org:service:AVTransport:1"
private const val SOAP_ACTION_SET_URI = "$AV_TRANSPORT_SERVICE_TYPE#SetAVTransportURI"
private const val SOAP_ACTION_PREFIX = "$AV_TRANSPORT_SERVICE_TYPE#"
private const val SSDP_HEADER_LOCATION = "location"
private const val SSDP_HEADER_USN = "usn"

private data class SsdpHeaders(val location: String?, val usn: String?)

/**
 * Unified UPnP / DLNA and SSDP Casting Client in [commonMain].
 */
class UPnPCastClient(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val onCustomDiscovery: (suspend () -> Unit)? = null
) {
    private var discoveryJob: Job? = null
    private var playbackPollingJob: Job? = null

    private val _castState = MutableStateFlow(CastState.DISCONNECTED)
    val castState: StateFlow<CastState> = _castState.asStateFlow()

    private val _availableDevices = MutableStateFlow<List<CastDevice>>(emptyList())
    val availableDevices: StateFlow<List<CastDevice>> = _availableDevices.asStateFlow()

    private val _currentDevice = MutableStateFlow<CastDevice?>(null)
    val currentDevice: StateFlow<CastDevice?> = _currentDevice.asStateFlow()

    private val _currentSession = MutableStateFlow<CastSessionInfo?>(null)
    val currentSession: StateFlow<CastSessionInfo?> = _currentSession.asStateFlow()

    val isCastingSupported: Boolean = true

    private val discoveredDevicesMap = mutableMapOf<String, CastDevice>()
    private val devicesLock = Any()

    fun startDiscovery() {
        if (discoveryJob?.isActive == true) return

        discoveryJob = scope.launch {
            while (isActive) {
                try {
                    discoverSsdpDevices()
                    onCustomDiscovery?.invoke()
                } catch (_: Throwable) {
                }
                delay(12000)
            }
        }
    }

    fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
    }

    fun connect(device: CastDevice) {
        scope.launch {
            _castState.value = CastState.CONNECTING
            _currentDevice.value = device

            val session = CastSessionInfo(
                device = device.copy(isConnected = true),
                state = CastState.CONNECTED,
                positionMs = 0L,
                durationMs = 0L,
                isPlaying = false,
                volume = 1.0f
            )
            _currentSession.value = session
            _castState.value = CastState.CONNECTED
        }
    }

    fun disconnect() {
        stop()
        _castState.value = CastState.DISCONNECTED
        _currentDevice.value = null
        _currentSession.value = null
        playbackPollingJob?.cancel()
        playbackPollingJob = null
    }

    fun loadMedia(media: CastMediaItem, startPositionMs: Long = 0L, autoPlay: Boolean = true) {
        val device = _currentDevice.value ?: return
        val controlUrl = device.controlUrl

        scope.launch {
            _castState.value = CastState.BUFFERING
            try {
                if (controlUrl != null) {
                    val setUriSoap = buildSetAvTransportUriSoap(media)
                    UPnPTransport.sendSoap(
                        controlUrl = controlUrl,
                        soapAction = SOAP_ACTION_SET_URI,
                        xmlPayload = setUriSoap
                    )

                    if (startPositionMs > 0) {
                        seekTo(startPositionMs)
                    }

                    if (autoPlay) {
                        play()
                    } else {
                        _castState.value = CastState.CONNECTED
                    }
                } else {
                    _castState.value = CastState.CASTING
                }

                _currentSession.value = _currentSession.value?.copy(
                    state = if (autoPlay) CastState.CASTING else CastState.PAUSED,
                    currentMedia = media,
                    positionMs = startPositionMs,
                    durationMs = media.durationMs,
                    isPlaying = autoPlay
                )

                startPlaybackPolling()
            } catch (_: Throwable) {
                _castState.value = CastState.ERROR
            }
        }
    }

    private fun sendTransportCommand(
        soapActionName: String,
        xmlPayload: String,
        newState: CastState? = null,
        isPlaying: Boolean? = null,
        onSuccess: (() -> Unit)? = null
    ) {
        val controlUrl = _currentDevice.value?.controlUrl
        scope.launch {
            try {
                if (controlUrl != null) {
                    UPnPTransport.sendSoap(
                        controlUrl = controlUrl,
                        soapAction = "$SOAP_ACTION_PREFIX$soapActionName",
                        xmlPayload = xmlPayload
                    )
                }
                if (newState != null) {
                    _castState.value = newState
                }
                _currentSession.value = _currentSession.value?.let { session ->
                    session.copy(
                        state = newState ?: session.state,
                        isPlaying = isPlaying ?: session.isPlaying
                    )
                }
                onSuccess?.invoke()
            } catch (_: Throwable) {
                if (newState != null) {
                    _castState.value = CastState.ERROR
                }
            }
        }
    }

    fun play() {
        sendTransportCommand(
            soapActionName = "Play",
            xmlPayload = buildPlaySoap(),
            newState = CastState.CASTING,
            isPlaying = true
        )
    }

    fun pause() {
        sendTransportCommand(
            soapActionName = "Pause",
            xmlPayload = buildPauseSoap(),
            newState = CastState.PAUSED,
            isPlaying = false
        )
    }

    fun seekTo(positionMs: Long) {
        val timeStr = formatRelTime(positionMs)
        sendTransportCommand(
            soapActionName = "Seek",
            xmlPayload = buildSeekSoap(timeStr),
            onSuccess = {
                _currentSession.value = _currentSession.value?.copy(positionMs = positionMs)
            }
        )
    }

    fun stop() {
        sendTransportCommand(
            soapActionName = "Stop",
            xmlPayload = buildStopSoap(),
            newState = CastState.STOPPED,
            isPlaying = false
        )
    }

    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0.0f, 1.0f)
        _currentSession.value = _currentSession.value?.copy(volume = clamped)
    }

    fun release() {
        stopDiscovery()
        disconnect()
        scope.cancel()
    }

    private suspend fun discoverSsdpDevices() {
        val responses = UPnPTransport.discoverSsdp()
        for (resp in responses) {
            parseSsdpResponse(resp.rawResponse, resp.senderHost)
        }
    }

    private fun parseSsdpHeaders(rawResponse: String): SsdpHeaders {
        var location: String? = null
        var usn: String? = null
        for (line in rawResponse.lines()) {
            val colonIdx = line.indexOf(':')
            if (colonIdx == -1) continue
            val key = line.substring(0, colonIdx).trim().lowercase()
            val value = line.substring(colonIdx + 1).trim()
            when (key) {
                SSDP_HEADER_LOCATION -> location = value
                SSDP_HEADER_USN -> usn = value
            }
        }
        return SsdpHeaders(location, usn)
    }

    private suspend fun parseSsdpResponse(response: String, senderHost: String) {
        val (location, usn) = parseSsdpHeaders(response)
        if (location == null || usn == null) return

        val deviceId = usn.substringBefore("::").ifEmpty { location }
        val isNewDevice = synchronized(devicesLock) {
            !discoveredDevicesMap.containsKey(deviceId)
        }
        if (isNewDevice) {
            fetchDeviceDescription(location, deviceId, senderHost)
        }
    }

    private suspend fun fetchDeviceDescription(locationUrl: String, deviceId: String, senderHost: String) {
        try {
            val xml = UPnPTransport.fetchXml(locationUrl) ?: return
            val defaultName = try {
                getString(Res.string.cast_default_device_name, senderHost)
            } catch (_: Throwable) {
                "Smart TV ($senderHost)"
            }
            val friendlyName = extractXmlTag(xml, "friendlyName") ?: defaultName
            val modelName = extractXmlTag(xml, "modelName")
            val controlUrl = resolveControlUrl(locationUrl, extractAvTransportControlUrl(xml))
            val port = parsePortFromUrl(locationUrl)

            val device = CastDevice(
                id = deviceId,
                name = friendlyName,
                hostAddress = senderHost,
                port = port,
                protocol = CastProtocol.UPNP_DLNA,
                modelName = modelName,
                locationXmlUrl = locationUrl,
                controlUrl = controlUrl
            )

            val updatedList = synchronized(devicesLock) {
                discoveredDevicesMap[deviceId] = device
                discoveredDevicesMap.values.toList()
            }
            _availableDevices.value = updatedList
        } catch (_: Throwable) {}
    }

    private fun startPlaybackPolling() {
        playbackPollingJob?.cancel()
        playbackPollingJob = scope.launch {
            while (isActive && _castState.value == CastState.CASTING) {
                delay(1000)
                val current = _currentSession.value ?: break
                if (current.isPlaying) {
                    val updatedPos = current.positionMs + 1000L
                    _currentSession.value = current.copy(
                        positionMs = if (current.durationMs > 0) updatedPos.coerceAtMost(current.durationMs) else updatedPos
                    )
                }
            }
        }
    }

    companion object {
        fun buildSoapEnvelope(innerBody: String): String {
            return """
                <?xml version="1.0" encoding="utf-8"?>
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                    <s:Body>
                        $innerBody
                    </s:Body>
                </s:Envelope>
            """.trimIndent()
        }

        fun buildSetAvTransportUriSoap(media: CastMediaItem): String {
            return buildSoapEnvelope(
                """
                <u:SetAVTransportURI xmlns:u="$AV_TRANSPORT_SERVICE_TYPE">
                    <InstanceID>0</InstanceID>
                    <CurrentURI>${escapeXml(media.url)}</CurrentURI>
                    <CurrentURIMetaData>${buildDidlMetadata(media)}</CurrentURIMetaData>
                </u:SetAVTransportURI>
                """.trimIndent()
            )
        }

        fun buildPlaySoap(): String {
            return buildSoapEnvelope(
                """
                <u:Play xmlns:u="$AV_TRANSPORT_SERVICE_TYPE">
                    <InstanceID>0</InstanceID>
                    <Speed>1</Speed>
                </u:Play>
                """.trimIndent()
            )
        }

        fun buildPauseSoap(): String {
            return buildSoapEnvelope(
                """
                <u:Pause xmlns:u="$AV_TRANSPORT_SERVICE_TYPE">
                    <InstanceID>0</InstanceID>
                </u:Pause>
                """.trimIndent()
            )
        }

        fun buildSeekSoap(relTimeStr: String): String {
            return buildSoapEnvelope(
                """
                <u:Seek xmlns:u="$AV_TRANSPORT_SERVICE_TYPE">
                    <InstanceID>0</InstanceID>
                    <Unit>REL_TIME</Unit>
                    <Target>$relTimeStr</Target>
                </u:Seek>
                """.trimIndent()
            )
        }

        fun buildStopSoap(): String {
            return buildSoapEnvelope(
                """
                <u:Stop xmlns:u="$AV_TRANSPORT_SERVICE_TYPE">
                    <InstanceID>0</InstanceID>
                </u:Stop>
                """.trimIndent()
            )
        }

        fun buildDidlMetadata(media: CastMediaItem): String {
            val didl = """
                <DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">
                    <item id="0" parentID="-1" restricted="1">
                        <dc:title>${escapeXml(media.title)}</dc:title>
                        <upnp:class>object.item.videoItem</upnp:class>
                        <res protocolInfo="http-get:*:video/mp4:*">${escapeXml(media.url)}</res>
                    </item>
                </DIDL-Lite>
            """.trimIndent()
            return escapeXml(didl)
        }

        fun escapeXml(text: String): String {
            return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;")
        }

        fun formatRelTime(ms: Long): String {
            val totalSec = max(0L, ms / 1000)
            val hours = totalSec / 3600
            val minutes = (totalSec % 3600) / 60
            val seconds = totalSec % 60
            val hh = if (hours < 10) "0$hours" else "$hours"
            val mm = if (minutes < 10) "0$minutes" else "$minutes"
            val ss = if (seconds < 10) "0$seconds" else "$seconds"
            return "$hh:$mm:$ss"
        }

        fun extractXmlTag(xml: String, tag: String): String? {
            val startTag = "<$tag>"
            val endTag = "</$tag>"
            val startIndex = xml.indexOf(startTag)
            if (startIndex == -1) return null
            val endIndex = xml.indexOf(endTag, startIndex)
            if (endIndex == -1) return null
            return xml.substring(startIndex + startTag.length, endIndex).trim()
        }

        fun extractAvTransportControlUrl(xml: String): String? {
            val serviceIndex = xml.indexOf(AV_TRANSPORT_SERVICE_TYPE)
            if (serviceIndex == -1) return null

            val block = xml.substring(serviceIndex, (serviceIndex + 500).coerceAtMost(xml.length))
            return extractXmlTag(block, "controlURL")
        }

        fun resolveControlUrl(baseUrl: String, controlPath: String?): String? {
            if (controlPath.isNullOrBlank()) return null
            if (controlPath.startsWith("http://", ignoreCase = true) || controlPath.startsWith("https://", ignoreCase = true)) {
                return controlPath
            }
            return buildAbsoluteUrl(baseUrl, controlPath)
        }

        private fun buildAbsoluteUrl(baseUrl: String, path: String): String {
            val schemeEnd = baseUrl.indexOf("://")
            if (schemeEnd == -1) return path
            val schemeAndHost = baseUrl.substring(0, schemeEnd + 3) + baseUrl.substring(schemeEnd + 3).substringBefore('/')
            val normalizedPath = if (path.startsWith('/')) path else "/$path"
            return "$schemeAndHost$normalizedPath"
        }

        fun parsePortFromUrl(urlStr: String): Int {
            val schemeEnd = urlStr.indexOf("://")
            val withoutScheme = if (schemeEnd != -1) urlStr.substring(schemeEnd + 3) else urlStr
            val hostPort = withoutScheme.substringBefore("/")
            if (hostPort.contains(":")) {
                return hostPort.substringAfter(":").toIntOrNull() ?: 80
            }
            return if (urlStr.startsWith("https://", ignoreCase = true)) 443 else 80
        }
    }
}
