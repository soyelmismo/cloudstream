package com.lagradost.cloudstream3.network

import com.lagradost.api.Log
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.nicehttp.Requests.Companion.await
import io.ktor.http.Url
import kotlinx.coroutines.delay
import okhttp3.Cookie
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

actual class PlatformCaptchaSolver actual constructor() {
    private val savedCookiesStorage = ConcurrentHashMap<String, Map<String, String>>()
    private val defaultDesktopUserAgent = USER_AGENT

    actual companion object {
        private const val TAG = "PlatformCaptchaSolver"
        actual val instance: PlatformCaptchaSolver = PlatformCaptchaSolver()

        actual fun parseCookieMap(cookie: String): Map<String, String> {
            return cookie.split(";").associate {
                val split = it.split("=")
                (split.getOrNull(0)?.trim() ?: "") to (split.getOrNull(1)?.trim() ?: "")
            }.filter { it.key.isNotBlank() && it.value.isNotBlank() }
        }
    }

    actual fun getSavedCookies(host: String): Map<String, String> {
        return savedCookiesStorage[host] ?: emptyMap()
    }

    actual fun saveCookies(host: String, cookies: Map<String, String>) {
        if (cookies.isNotEmpty()) {
            val existing = savedCookiesStorage.getOrPut(host) { emptyMap() }.toMutableMap()
            existing.putAll(cookies)
            savedCookiesStorage[host] = existing
        }
    }

    actual fun clearCookies() {
        savedCookiesStorage.clear()
    }

    actual fun getUserAgent(): String? {
        return defaultDesktopUserAgent
    }

    actual suspend fun solve(
        url: String,
        headers: Map<String, String>,
        timeoutMs: Long
    ): CaptchaResult {
        val host = try { Url(url).host } catch (_: Throwable) { "" }

        // Check if we already have clearance
        val existing = getSavedCookies(host)
        if (existing.containsKey("cf_clearance")) {
            return CaptchaResult(
                success = true,
                cookies = existing,
                userAgent = getUserAgent()
            )
        }

        val browserHeaders = mapOf(
            "User-Agent" to defaultDesktopUserAgent,
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
            "Accept-Language" to "en-US,en;q=0.9",
            "Sec-Ch-Ua" to "\"Not A(Brand\";v=\"8\", \"Chromium\";v=\"132\", \"Google Chrome\";v=\"132\"",
            "Sec-Ch-Ua-Mobile" to "?0",
            "Sec-Ch-Ua-Platform" to "\"Linux\"",
            "Sec-Fetch-Dest" to "document",
            "Sec-Fetch-Mode" to "navigate",
            "Sec-Fetch-Site" to "none",
            "Sec-Fetch-User" to "?1",
            "Upgrade-Insecure-Requests" to "1"
        ) + headers

        val collectedCookies = mutableMapOf<String, String>()

        try {
            val httpUrl = url.toHttpUrlOrNull()
            val requestBuilder = Request.Builder().url(url)
            browserHeaders.forEach { (k, v) -> requestBuilder.header(k, v) }

            val response = app.baseClient.newCall(requestBuilder.build()).await()
            val setCookies = response.headers("Set-Cookie")
            for (sc in setCookies) {
                val parsedCookie = safe { Cookie.parse(httpUrl ?: return@safe null, sc) }
                if (parsedCookie != null) {
                    collectedCookies[parsedCookie.name] = parsedCookie.value
                } else {
                    val parsed = parseCookieMap(sc)
                    collectedCookies.putAll(parsed)
                }
            }

            val body = response.body.string()

            // Check if clearance is present or if simple refresh redirect is present
            if (collectedCookies.containsKey("cf_clearance")) {
                if (host.isNotBlank()) saveCookies(host, collectedCookies)
                return CaptchaResult(
                    success = true,
                    cookies = collectedCookies,
                    userAgent = getUserAgent(),
                    responseBody = body
                )
            }

            // Check meta refresh challenge
            val metaRefresh = Regex("""<meta http-equiv="refresh" content="\d+;\s*url=([^">]+)"""", RegexOption.IGNORE_CASE)
                .find(body)?.groupValues?.getOrNull(1)

            if (!metaRefresh.isNullOrBlank()) {
                val redirectTarget = if (metaRefresh.startsWith("http")) metaRefresh else "${Url(url).protocol.name}://$host$metaRefresh"
                delay(1000) // Wait for challenge threshold

                val redirectHeaders = browserHeaders + mapOf(
                    "Referer" to url,
                    "Cookie" to collectedCookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
                )
                val redirectReq = Request.Builder().url(redirectTarget)
                redirectHeaders.forEach { (k, v) -> redirectReq.header(k, v) }

                val redirectResp = app.baseClient.newCall(redirectReq.build()).await()
                val newCookies = redirectResp.headers("Set-Cookie")
                for (sc in newCookies) {
                    val parsed = parseCookieMap(sc)
                    collectedCookies.putAll(parsed)
                }
                val redirectBody = redirectResp.body.string()

                if (host.isNotBlank() && collectedCookies.isNotEmpty()) {
                    saveCookies(host, collectedCookies)
                }

                return CaptchaResult(
                    success = redirectResp.isSuccessful || collectedCookies.containsKey("cf_clearance"),
                    cookies = collectedCookies,
                    userAgent = getUserAgent(),
                    responseBody = redirectBody
                )
            }

            if (host.isNotBlank() && collectedCookies.isNotEmpty()) {
                saveCookies(host, collectedCookies)
            }

            return CaptchaResult(
                success = response.isSuccessful || collectedCookies.containsKey("cf_clearance"),
                cookies = collectedCookies,
                userAgent = getUserAgent(),
                responseBody = body,
                errorMessage = if (response.code in listOf(403, 503)) "Headless Cloudflare challenge requires interactive resolution" else null
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Error resolving headless challenge for $url: ${e.message}")
            return CaptchaResult(
                success = false,
                errorMessage = e.message ?: "Headless challenge error"
            )
        }
    }
}
