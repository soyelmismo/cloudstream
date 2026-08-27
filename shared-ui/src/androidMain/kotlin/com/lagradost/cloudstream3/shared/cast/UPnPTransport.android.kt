package com.lagradost.cloudstream3.shared.cast

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URL

private const val SSDP_MULTICAST_HOST = "239.255.255.250"
private const val SSDP_MULTICAST_PORT = 1900
private const val SSDP_SOCKET_TIMEOUT_MS = 3000
private const val SSDP_BUFFER_SIZE = 4096
private const val SSDP_SEARCH_MESSAGE = "M-SEARCH * HTTP/1.1\r\n" +
        "HOST: 239.255.255.250:1900\r\n" +
        "MAN: \"ssdp:discover\"\r\n" +
        "MX: 3\r\n" +
        "ST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n\r\n"

/**
 * Android network implementation of [UPnPTransport].
 */
actual object UPnPTransport {
    actual suspend fun discoverSsdp(timeoutMs: Long): List<SsdpResponse> = withContext(Dispatchers.IO) {
        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = SSDP_SOCKET_TIMEOUT_MS
                sendMulticastSearch(socket)
                collectSsdpResponses(socket, timeoutMs)
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun sendMulticastSearch(socket: DatagramSocket) {
        val data = SSDP_SEARCH_MESSAGE.toByteArray(Charsets.UTF_8)
        val group = InetAddress.getByName(SSDP_MULTICAST_HOST)
        val packet = DatagramPacket(data, data.size, group, SSDP_MULTICAST_PORT)
        socket.send(packet)
    }

    private fun collectSsdpResponses(socket: DatagramSocket, timeoutMs: Long): List<SsdpResponse> {
        val results = mutableListOf<SsdpResponse>()
        val receiveBuffer = ByteArray(SSDP_BUFFER_SIZE)
        val deadline = System.currentTimeMillis() + timeoutMs

        while (System.currentTimeMillis() < deadline) {
            try {
                val responsePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
                socket.receive(responsePacket)
                val responseText = String(responsePacket.data, 0, responsePacket.length, Charsets.UTF_8)
                val host = responsePacket.address.hostAddress ?: continue
                results.add(SsdpResponse(responseText, host))
            } catch (_: SocketTimeoutException) {
                break
            } catch (_: Throwable) {}
        }
        return results
    }

    actual suspend fun fetchXml(locationUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL(locationUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.requestMethod = "GET"

            if (conn.responseCode == 200) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    actual suspend fun sendSoap(controlUrl: String, soapAction: String, xmlPayload: String) = withContext(Dispatchers.IO) {
        val url = URL(controlUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 4000
        conn.readTimeout = 4000
        conn.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
        conn.setRequestProperty("SOAPACTION", "\"$soapAction\"")

        conn.outputStream.use { os ->
            os.write(xmlPayload.toByteArray(Charsets.UTF_8))
            os.flush()
        }

        val code = conn.responseCode
        if (code !in 200..299) {
            throw RuntimeException("SOAP request failed with HTTP $code")
        }
    }
}
