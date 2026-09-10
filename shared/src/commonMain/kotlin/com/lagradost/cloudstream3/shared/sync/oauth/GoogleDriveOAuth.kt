package com.lagradost.cloudstream3.shared.sync.oauth

import androidx.compose.runtime.Immutable
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Encode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

@Serializable
@Immutable
data class GoogleOAuthToken(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Long = 3600L,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("scope") val scope: String? = null,
    @SerialName("token_type") val tokenType: String? = "Bearer",
    @SerialName("id_token") val idToken: String? = null
)

@Serializable
@Immutable
data class GoogleUserInfo(
    @SerialName("id") val id: String = "",
    @SerialName("email") val email: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("picture") val picture: String? = null
)

object GoogleDriveOAuth {
    const val DEFAULT_CLIENT_ID = "1043236712316-4tq98dksfq240g6p4gq92955qsl0o1u1.apps.googleusercontent.com"
    const val DEFAULT_REDIRECT_URI = "http://127.0.0.1:8080"
    const val CUSTOM_SCHEME_REDIRECT_URI = "cloudstream://oauth2/google"

    private const val AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
    private const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
    private const val USER_INFO_ENDPOINT = "https://www.googleapis.com/oauth2/v2/userinfo"
    private const val OAUTH_SCOPES = "https://www.googleapis.com/auth/drive.file https://www.googleapis.com/auth/userinfo.email https://www.googleapis.com/auth/userinfo.profile"
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
        clientId: String = DEFAULT_CLIENT_ID,
        codeChallenge: String,
        redirectUri: String = DEFAULT_REDIRECT_URI
    ): String {
        val params = listOf(
            "client_id" to clientId,
            "redirect_uri" to redirectUri,
            "response_type" to "code",
            "scope" to OAUTH_SCOPES,
            "code_challenge" to codeChallenge,
            "code_challenge_method" to "S256",
            "access_type" to "offline",
            "prompt" to "consent"
        )
        val query = params.joinToString("&") { (key, value) ->
            "${encodeUrl(key)}=${encodeUrl(value)}"
        }
        return "$AUTH_ENDPOINT?$query"
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
        clientId: String = DEFAULT_CLIENT_ID,
        clientSecret: String? = null,
        code: String,
        codeVerifier: String,
        redirectUri: String = DEFAULT_REDIRECT_URI
    ): Result<GoogleOAuthToken> = runCatching {
        val params = mutableMapOf(
            "client_id" to clientId,
            "code" to code,
            "code_verifier" to codeVerifier,
            "grant_type" to "authorization_code",
            "redirect_uri" to redirectUri
        )
        if (!clientSecret.isNullOrBlank()) {
            params["client_secret"] = clientSecret
        }
        val response = app.post(
            TOKEN_ENDPOINT,
            data = params
        )
        if (!response.isSuccessful) {
            throw IOException("Google OAuth token exchange failed: HTTP ${response.code} ${response.text}")
        }
        json.decodeFromString<GoogleOAuthToken>(response.text)
    }

    suspend fun refreshAccessToken(
        clientId: String = DEFAULT_CLIENT_ID,
        clientSecret: String? = null,
        refreshToken: String
    ): Result<GoogleOAuthToken> = runCatching {
        val params = mutableMapOf(
            "client_id" to clientId,
            "refresh_token" to refreshToken,
            "grant_type" to "refresh_token"
        )
        if (!clientSecret.isNullOrBlank()) {
            params["client_secret"] = clientSecret
        }
        val response = app.post(
            TOKEN_ENDPOINT,
            data = params
        )
        if (!response.isSuccessful) {
            throw IOException("Google OAuth token refresh failed: HTTP ${response.code} ${response.text}")
        }
        json.decodeFromString<GoogleOAuthToken>(response.text)
    }

    suspend fun fetchUserInfo(accessToken: String): Result<GoogleUserInfo> = runCatching {
        val response = app.get(
            USER_INFO_ENDPOINT,
            headers = mapOf("Authorization" to "Bearer $accessToken")
        )
        if (!response.isSuccessful) {
            throw IOException("Failed to fetch Google user info: HTTP ${response.code} ${response.text}")
        }
        json.decodeFromString<GoogleUserInfo>(response.text)
    }
}
