package com.lagradost.cloudstream3.shared.sync.oauth

import androidx.compose.runtime.Immutable
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Encode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

@Serializable
@Immutable
data class OneDriveOAuthToken(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String? = "Bearer",
    @SerialName("expires_in") val expiresIn: Long = 3600L,
    @SerialName("ext_expires_in") val extExpiresIn: Long? = null,
    @SerialName("scope") val scope: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("id_token") val idToken: String? = null
)

@Serializable
@Immutable
data class OneDriveUserInfo(
    @SerialName("id") val id: String = "",
    @SerialName("displayName") val displayName: String = "",
    @SerialName("userPrincipalName") val userPrincipalName: String = "",
    @SerialName("mail") val mail: String? = null
)

object OneDriveOAuth {
    // Rclone public client ID and secret matching CloudRedirect for personal and organizational accounts
    const val DEFAULT_CLIENT_ID = "b15665d9-eda6-4092-8539-0eec376afd59"
    const val DEFAULT_CLIENT_SECRET = "qtyfaBBYA403=unZUP40~_#"
    const val DEFAULT_PORT = 53682
    const val DEFAULT_REDIRECT_URI = "http://localhost:53682/"
    const val AUTH_URL = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize"
    const val TOKEN_URL = "https://login.microsoftonline.com/common/oauth2/v2.0/token"
    const val GRAPH_ME_URL = "https://graph.microsoft.com/v1.0/me"
    const val SCOPE = "Files.ReadWrite offline_access User.Read"

    private const val PKCE_CHARACTERS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    fun generateCodeVerifier(): String {
        val random = SecureRandom()
        val chars = CharArray(64)
        for (i in chars.indices) {
            chars[i] = PKCE_CHARACTERS[random.nextInt(PKCE_CHARACTERS.length)]
        }
        return String(chars)
    }

    fun generateCodeChallenge(codeVerifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(codeVerifier.toByteArray(Charsets.US_ASCII))
        return base64UrlEncode(hash)
    }

    private fun base64UrlEncode(bytes: ByteArray): String {
        return base64Encode(bytes)
            .trimEnd('=')
            .replace("+", "-")
            .replace("/", "_")
            .replace("\r", "")
            .replace("\n", "")
    }

    fun buildAuthorizationUrl(
        codeChallenge: String,
        state: String? = null,
        clientId: String = DEFAULT_CLIENT_ID,
        redirectUri: String = DEFAULT_REDIRECT_URI,
        scope: String = SCOPE
    ): String {
        val params = buildList {
            add("client_id" to clientId)
            add("response_type" to "code")
            add("redirect_uri" to redirectUri)
            add("response_mode" to "query")
            add("scope" to scope)
            add("code_challenge" to codeChallenge)
            add("code_challenge_method" to "S256")
            if (!state.isNullOrBlank()) {
                add("state" to state)
            }
        }
        val query = params.joinToString("&") { (key, value) ->
            "${encodeUrl(key)}=${encodeUrl(value)}"
        }
        return "$AUTH_URL?$query"
    }

    private fun encodeUrl(value: String): String = URLEncoder.encode(value, "UTF-8")

    fun sanitizeAuthCode(rawInput: String): String {
        val trimmed = rawInput.trim()
        if (!trimmed.contains("code=")) {
            return trimmed
        }
        val afterCode = trimmed.substringAfter("code=")
        val rawCode = afterCode.takeWhile { it != '&' && it != '#' && it != ' ' }
        return runCatching { URLDecoder.decode(rawCode, "UTF-8") }.getOrDefault(rawCode)
    }

    suspend fun exchangeCode(
        code: String,
        codeVerifier: String,
        clientId: String = DEFAULT_CLIENT_ID,
        clientSecret: String? = DEFAULT_CLIENT_SECRET,
        redirectUri: String = DEFAULT_REDIRECT_URI,
        scope: String = SCOPE
    ): Result<OneDriveOAuthToken> = runCatching {
        val params = mutableMapOf(
            "client_id" to clientId,
            "code" to code,
            "code_verifier" to codeVerifier,
            "grant_type" to "authorization_code",
            "redirect_uri" to redirectUri,
            "scope" to scope
        )
        if (!clientSecret.isNullOrBlank()) {
            params["client_secret"] = clientSecret
        }
        val response = app.post(
            TOKEN_URL,
            data = params
        )
        if (!response.isSuccessful) {
            throw IOException("OneDrive OAuth token exchange failed: HTTP ${response.code} ${response.text}")
        }
        json.decodeFromString<OneDriveOAuthToken>(response.text)
    }

    suspend fun refreshAccessToken(
        refreshToken: String,
        clientId: String = DEFAULT_CLIENT_ID,
        clientSecret: String? = DEFAULT_CLIENT_SECRET,
        redirectUri: String = DEFAULT_REDIRECT_URI,
        scope: String = SCOPE
    ): Result<OneDriveOAuthToken> = runCatching {
        val params = mutableMapOf(
            "client_id" to clientId,
            "refresh_token" to refreshToken,
            "grant_type" to "refresh_token",
            "redirect_uri" to redirectUri,
            "scope" to scope
        )
        if (!clientSecret.isNullOrBlank()) {
            params["client_secret"] = clientSecret
        }
        val response = app.post(
            TOKEN_URL,
            data = params
        )
        if (!response.isSuccessful) {
            throw IOException("OneDrive OAuth token refresh failed: HTTP ${response.code} ${response.text}")
        }
        json.decodeFromString<OneDriveOAuthToken>(response.text)
    }

    suspend fun fetchUserInfo(accessToken: String): Result<OneDriveUserInfo> = runCatching {
        val response = app.get(
            GRAPH_ME_URL,
            headers = mapOf("Authorization" to "Bearer $accessToken")
        )
        if (!response.isSuccessful) {
            throw IOException("Failed to fetch OneDrive user info: HTTP ${response.code} ${response.text}")
        }
        json.decodeFromString<OneDriveUserInfo>(response.text)
    }

    fun startLocalCallbackServer(
        scope: CoroutineScope,
        port: Int = DEFAULT_PORT,
        onCodeReceived: (String) -> Unit
    ): AutoCloseable {
        val server = LocalOneDriveCallbackServer(port, onCodeReceived)
        server.start(scope)
        return server
    }
}

class LocalOneDriveCallbackServer(
    private val port: Int = OneDriveOAuth.DEFAULT_PORT,
    private val onCodeReceived: (String) -> Unit
) : AutoCloseable {
    private var serverSocket: ServerSocket? = null
    private var job: Job? = null
    @Volatile private var isClosed = false

    fun start(scope: CoroutineScope) {
        job = scope.launch(Dispatchers.IO) {
            try {
                // Backlog of 50 allows browser preflight and asset requests without dropping connection
                val server = ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"))
                serverSocket = server
                while (!isClosed && !server.isClosed) {
                    val socket = try {
                        server.accept()
                    } catch (_: Throwable) {
                        break
                    }
                    launch(Dispatchers.IO) {
                        handleConnection(socket)
                    }
                }
            } catch (_: Throwable) {
            }
        }
    }

    private fun handleConnection(socket: Socket) {
        try {
            socket.soTimeout = 5000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val firstLine = reader.readLine() ?: return
            if (firstLine.contains("code=")) {
                val code = OneDriveOAuth.sanitizeAuthCode(firstLine)
                sendHtmlResponse(socket)
                onCodeReceived(code)
                close()
            } else {
                sendNoContentResponse(socket)
            }
        } catch (_: Throwable) {
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun sendHtmlResponse(socket: Socket) {
        val html = """
            <!DOCTYPE html>
            <html>
            <head><meta charset="utf-8"><title>CloudStream</title></head>
            <body style="font-family:system-ui,sans-serif;text-align:center;padding:50px;background:#121212;color:#ffffff;">
              <h2>¡Autenticación con OneDrive exitosa!</h2>
              <p>Puedes cerrar esta pestaña y regresar a la aplicación de CloudStream.</p>
            </body>
            </html>
        """.trimIndent()
        val bytes = html.toByteArray(Charsets.UTF_8)
        val out = socket.getOutputStream()
        out.write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray(Charsets.UTF_8))
        out.write(bytes)
        out.flush()
    }

    private fun sendNoContentResponse(socket: Socket) {
        val out = socket.getOutputStream()
        out.write("HTTP/1.1 204 No Content\r\nConnection: close\r\n\r\n".toByteArray(Charsets.UTF_8))
        out.flush()
    }

    override fun close() {
        if (isClosed) return
        isClosed = true
        runCatching { serverSocket?.close() }
        job?.cancel()
    }
}
