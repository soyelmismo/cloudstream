package com.lagradost.cloudstream3.network

import androidx.annotation.AnyThread
import com.lagradost.api.Log
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.debugWarning
import com.lagradost.nicehttp.Requests.Companion.await
import com.lagradost.nicehttp.cookies
import io.ktor.http.Url
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.runBlocking
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * Cross-platform Cloudflare and Anti-Bot Challenge Solver Interceptor.
 *
 * Automatically detects Cloudflare 403/503 challenges and solves them using
 * platform-specific engines via [PlatformCaptchaSolver] (hidden WebView on Android,
 * headless cookie engine on Desktop JVM), caching solved clearance cookies per host
 * and injecting matching User-Agent headers.
 */
@AnyThread
open class CloudflareSolver : Interceptor {
    val savedCookies: MutableMap<String, Map<String, String>> = mutableMapOf()

    companion object {
        const val TAG: String = "CloudflareSolver"
        val ERROR_CODES: List<Int> = listOf(403, 503)
        val CLOUDFLARE_SERVERS: List<String> = listOf("cloudflare-nginx", "cloudflare")

        fun parseCookieMap(cookie: String): Map<String, String> {
            return PlatformCaptchaSolver.parseCookieMap(cookie)
        }

        fun isCloudflareChallenge(
            code: Int,
            serverHeader: String?,
            bodySnippet: String? = null
        ): Boolean {
            val isServer = serverHeader != null && CLOUDFLARE_SERVERS.any { serverHeader.contains(it, ignoreCase = true) }
            val isCode = code in ERROR_CODES
            if (isServer && isCode) return true
            if (bodySnippet != null) {
                if (bodySnippet.contains("cf-mitigated") ||
                    bodySnippet.contains("__cf_chl_") ||
                    bodySnippet.contains("cf-chl-widget") ||
                    bodySnippet.contains("challenge-platform")
                ) {
                    return true
                }
            }
            return false
        }
    }

    /**
     * Builds OkHttp Headers combining request headers, solver User-Agent and clearance cookies.
     */
    fun buildHeaders(headers: Map<String, String>, cookies: Map<String, String>): Headers {
        val builder = Headers.Builder()
        headers.forEach { (k, v) ->
            try { builder.add(k, v) } catch (_: Throwable) {}
        }
        if (cookies.isNotEmpty()) {
            val cookieStr = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
            builder.add("Cookie", cookieStr)
        }
        return builder.build()
    }

    /**
     * Returns OkHttp Headers containing saved cookies and solver User-Agent for the specified URL.
     */
    fun getCookieHeaders(url: String): Headers {
        val userAgentHeaders = PlatformCaptchaSolver.instance.getUserAgent()?.let {
            mapOf("user-agent" to it)
        } ?: emptyMap()

        val host = try { Url(url).host } catch (_: Throwable) { "" }
        val cookies = synchronized(savedCookies) {
            savedCookies[host]
        } ?: PlatformCaptchaSolver.instance.getSavedCookies(host)

        return buildHeaders(userAgentHeaders, cookies)
    }

    /**
     * Programmatically solves Cloudflare challenge for the given target URL.
     */
    suspend fun solveCloudflare(
        url: String,
        headers: Map<String, String> = emptyMap()
    ): CaptchaResult {
        val result = PlatformCaptchaSolver.instance.solve(url, headers)
        if (result.success && result.cookies.isNotEmpty()) {
            val host = try { Url(url).host } catch (_: Throwable) { "" }
            if (host.isNotBlank()) {
                synchronized(savedCookies) {
                    savedCookies[host] = result.cookies
                }
            }
        }
        return result
    }

    override fun intercept(chain: Interceptor.Chain): Response = runBlocking {
        val request = chain.request()
        val host = request.url.host

        val activeCookies = synchronized(savedCookies) {
            savedCookies[host]
        } ?: PlatformCaptchaSolver.instance.getSavedCookies(host).ifEmpty { null }

        if (activeCookies == null) {
            val response = chain.proceed(request)
            val server = response.header("Server")
            if (!isCloudflareChallenge(response.code, server)) {
                return@runBlocking response
            } else {
                response.close()
                bypassCloudflare(request)?.let {
                    Log.d(TAG, "Succeeded bypassing Cloudflare for: ${request.url}")
                    return@runBlocking it
                }
            }
        } else {
            return@runBlocking proceedWithCookies(request, activeCookies)
        }

        debugWarning({ true }) { "Failed Cloudflare bypass at: ${request.url}" }
        return@runBlocking chain.proceed(request)
    }

    suspend fun proceedWithCookies(request: Request, cookies: Map<String, String>): Response {
        val userAgentMap = PlatformCaptchaSolver.instance.getUserAgent()?.let {
            mapOf("user-agent" to it)
        } ?: emptyMap()

        val headers = buildHeaders(request.headers.toMap() + userAgentMap, cookies + request.cookies)
        return app.baseClient.newCall(
            request.newBuilder()
                .headers(headers)
                .build()
        ).await()
    }

    suspend fun bypassCloudflare(request: Request): Response? {
        val url = request.url.toString()
        val host = request.url.host

        Log.d(TAG, "Solving Cloudflare challenge for: $url")
        val solveResult = solveCloudflare(url, request.headers.toMap())
        val cookies = if (solveResult.success && solveResult.cookies.isNotEmpty()) {
            solveResult.cookies
        } else {
            synchronized(savedCookies) {
                savedCookies[host]
            } ?: PlatformCaptchaSolver.instance.getSavedCookies(host).ifEmpty { null }
        } ?: return null

        return proceedWithCookies(request, cookies)
    }
}
