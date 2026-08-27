package com.lagradost.cloudstream3.shared.cast

/**
 * Raw SSDP network response received over UDP multicast.
 */
data class SsdpResponse(
    val rawResponse: String,
    val senderHost: String
)

/**
 * Multiplatform network transport interface for SSDP discovery and SOAP AVTransport control.
 */
expect object UPnPTransport {
    /**
     * Broadcasts SSDP M-SEARCH discovery datagram and collects responses.
     */
    suspend fun discoverSsdp(timeoutMs: Long = 3500L): List<SsdpResponse>

    /**
     * Performs HTTP GET to fetch device XML description.
     */
    suspend fun fetchXml(locationUrl: String): String?

    /**
     * Performs HTTP POST with SOAPACTION header to send UPnP control payloads.
     */
    suspend fun sendSoap(controlUrl: String, soapAction: String, xmlPayload: String)
}
