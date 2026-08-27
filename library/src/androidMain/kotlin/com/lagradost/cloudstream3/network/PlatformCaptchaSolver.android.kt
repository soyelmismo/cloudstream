package com.lagradost.cloudstream3.network

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.lagradost.api.Log
import com.lagradost.api.getContext
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.Coroutines.mainWork
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

actual class PlatformCaptchaSolver actual constructor() {
    private val savedCookiesStorage = ConcurrentHashMap<String, Map<String, String>>()
    private var cachedUserAgent: String? = null

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

    init {
        safe {
            CookieManager.getInstance()?.removeAllCookies(null)
        }
    }

    actual fun getSavedCookies(host: String): Map<String, String> {
        return savedCookiesStorage[host] ?: emptyMap()
    }

    actual fun saveCookies(host: String, cookies: Map<String, String>) {
        if (cookies.isNotEmpty()) {
            savedCookiesStorage[host] = cookies
        }
    }

    actual fun clearCookies() {
        savedCookiesStorage.clear()
        safe {
            CookieManager.getInstance()?.removeAllCookies(null)
        }
    }

    actual fun getUserAgent(): String? {
        if (cachedUserAgent != null) return cachedUserAgent
        return safe {
            (getContext() as? Context)?.let { ctx ->
                runBlocking {
                    mainWork {
                        WebView(ctx).settings.userAgentString.also {
                            cachedUserAgent = it
                            WebViewResolver.webViewUserAgent = it
                        }
                    }
                }
            }
        } ?: WebViewResolver.webViewUserAgent ?: USER_AGENT
    }

    @SuppressLint("SetJavaScriptEnabled")
    actual suspend fun solve(
        url: String,
        headers: Map<String, String>,
        timeoutMs: Long
    ): CaptchaResult {
        val host = try {
            URI(url).host ?: ""
        } catch (_: Throwable) {
            ""
        }

        // Check if CookieManager already has valid clearance
        val existingCookie = safe { CookieManager.getInstance()?.getCookie(url) }
        if (!existingCookie.isNullOrBlank() && existingCookie.contains("cf_clearance")) {
            val cookieMap = parseCookieMap(existingCookie)
            if (host.isNotBlank()) {
                saveCookies(host, cookieMap)
            }
            return CaptchaResult(
                success = true,
                cookies = cookieMap,
                userAgent = getUserAgent()
            )
        }

        val context = (getContext() as? Context) ?: return CaptchaResult(
            success = false,
            errorMessage = "No Android Context available for WebView Captcha Solver"
        )

        var isSolved = false
        var webView: WebView? = null
        var solvedCookieMap: Map<String, String> = emptyMap()

        main {
            try {
                webView = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true

                    val ua = settings.userAgentString
                    cachedUserAgent = ua
                    WebViewResolver.webViewUserAgent = ua

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                            super.onPageFinished(view, finishedUrl)
                            val c = safe { CookieManager.getInstance()?.getCookie(finishedUrl ?: url) }
                            if (!c.isNullOrBlank() && (c.contains("cf_clearance") || c.contains("__cf_bm"))) {
                                solvedCookieMap = parseCookieMap(c)
                                if (host.isNotBlank()) {
                                    saveCookies(host, solvedCookieMap)
                                }
                                isSolved = true
                            }
                        }

                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest
                        ): android.webkit.WebResourceResponse? {
                            val reqUrl = request.url.toString()
                            val c = safe { CookieManager.getInstance()?.getCookie(reqUrl) }
                            if (!c.isNullOrBlank() && c.contains("cf_clearance")) {
                                solvedCookieMap = parseCookieMap(c)
                                if (host.isNotBlank()) {
                                    saveCookies(host, solvedCookieMap)
                                }
                                isSolved = true
                            }
                            return super.shouldInterceptRequest(view, request)
                        }
                    }

                    loadUrl(url, headers)
                }
            } catch (e: Throwable) {
                logError(e)
            }
        }

        val stepDelay = 150L
        var elapsed = 0L
        while (elapsed < timeoutMs && !isSolved) {
            delay(stepDelay)
            elapsed += stepDelay

            val c = safe { CookieManager.getInstance()?.getCookie(url) }
            if (!c.isNullOrBlank() && c.contains("cf_clearance")) {
                solvedCookieMap = parseCookieMap(c)
                if (host.isNotBlank()) {
                    saveCookies(host, solvedCookieMap)
                }
                isSolved = true
                break
            }
        }

        main {
            safe {
                webView?.stopLoading()
                webView?.destroy()
                webView = null
            }
        }

        return if (isSolved && solvedCookieMap.isNotEmpty()) {
            CaptchaResult(
                success = true,
                cookies = solvedCookieMap,
                userAgent = getUserAgent()
            )
        } else {
            // Check final fallback cookie
            val finalCookie = safe { CookieManager.getInstance()?.getCookie(url) }
            if (!finalCookie.isNullOrBlank()) {
                val parsed = parseCookieMap(finalCookie)
                if (host.isNotBlank()) saveCookies(host, parsed)
                CaptchaResult(
                    success = parsed.containsKey("cf_clearance"),
                    cookies = parsed,
                    userAgent = getUserAgent(),
                    errorMessage = if (!parsed.containsKey("cf_clearance")) "Timed out waiting for cf_clearance" else null
                )
            } else {
                CaptchaResult(
                    success = false,
                    errorMessage = "Timed out solving Cloudflare captcha after ${timeoutMs}ms"
                )
            }
        }
    }
}
