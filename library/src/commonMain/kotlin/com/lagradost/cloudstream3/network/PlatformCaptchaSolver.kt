package com.lagradost.cloudstream3.network

/**
 * Result of a platform-specific captcha or anti-bot challenge resolution.
 *
 * @param success True if the challenge was resolved successfully.
 * @param cookies Resolved cookies map (key-value).
 * @param userAgent The user-agent that was used during challenge resolution (crucial for Cloudflare clearance matching).
 * @param responseBody Optional resolved HTML / payload response body.
 * @param headers Additional response headers captured during resolution.
 * @param errorMessage Optional human-readable error description if resolution failed.
 */
data class CaptchaResult(
    val success: Boolean,
    val cookies: Map<String, String> = emptyMap(),
    val userAgent: String? = null,
    val responseBody: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val errorMessage: String? = null
)

/**
 * Platform-agnostic Captcha and Anti-Bot Solver contract.
 *
 * Provides expect/actual bindings for solving Cloudflare, Turnstile, and other anti-bot
 * challenges across Android (via hidden WebView & CookieManager) and Desktop JVM (via headless cookie engine & challenge HTTP interceptors).
 */
expect class PlatformCaptchaSolver() {
    companion object {
        val instance: PlatformCaptchaSolver

        /**
         * Parses a standard HTTP `Cookie` or `Set-Cookie` string header into a key-value map.
         */
        fun parseCookieMap(cookie: String): Map<String, String>
    }

    /**
     * Resolves a challenge for the specified URL and optional request headers.
     */
    suspend fun solve(
        url: String,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Long = 30_000L
    ): CaptchaResult

    /**
     * Gets previously saved cookies for the specified host.
     */
    fun getSavedCookies(host: String): Map<String, String>

    /**
     * Saves cookies for a specific host.
     */
    fun saveCookies(host: String, cookies: Map<String, String>)

    /**
     * Clears all cached challenge cookies.
     */
    fun clearCookies()

    /**
     * Returns the platform user-agent matching the solver engine.
     */
    fun getUserAgent(): String?
}
