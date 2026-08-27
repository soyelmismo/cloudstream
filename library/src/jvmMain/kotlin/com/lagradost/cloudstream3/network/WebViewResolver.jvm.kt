package com.lagradost.cloudstream3.network

import com.lagradost.api.Log
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.debugException
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.cloudstream3.utils.Coroutines.atomicListOf
import com.lagradost.nicehttp.Requests.Companion.await
import com.lagradost.nicehttp.requestCreator
import kotlinx.coroutines.delay
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * When used as Interceptor additionalUrls cannot be returned, use WebViewResolver(...).resolveUsingWebView(...)
 * @param interceptUrl will stop the resolver when reaching this url.
 * @param additionalUrls this will make resolveUsingWebView also return all other requests matching the list of Regex.
 * @param userAgent if null then will use the default user agent
 * @param useOkhttp will try to use the okhttp client as much as possible.
 * @param script pass custom js to execute
 * @param scriptCallback will be called with the result from custom js
 * @param timeout close resolver after timeout
 * */
actual class WebViewResolver actual constructor(
    val interceptUrl: Regex,
    val additionalUrls: List<Regex>,
    val userAgent: String?,
    val useOkhttp: Boolean,
    val script: String?,
    val scriptCallback: ((String) -> Unit)?,
    val timeout: Long
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        return chain.proceed(request)
    }

    actual companion object {
        actual val DEFAULT_TIMEOUT = 60_000L
        actual var webViewUserAgent: String? = USER_AGENT
        private const val TAG = "WebViewResolverJvm"
    }

    actual suspend fun resolveUsingWebView(
        url: String,
        referer: String?,
        method: String,
        requestCallBack: (Request) -> Boolean,
    ): Pair<Request?, List<Request>> =
        resolveUsingWebView(url, referer, emptyMap(), method, requestCallBack)

    actual suspend fun resolveUsingWebView(
        url: String,
        referer: String?,
        headers: Map<String, String>,
        method: String,
        requestCallBack: (Request) -> Boolean
    ): Pair<Request?, List<Request>> {
        return try {
            resolveUsingWebView(
                requestCreator(method, url, referer = referer, headers = headers), requestCallBack
            )
        } catch (e: java.lang.IllegalArgumentException) {
            logError(e)
            debugException { "ILLEGAL URL IN resolveUsingWebView!" }
            return null to emptyList()
        }
    }

    actual suspend fun resolveUsingWebView(
        request: Request,
        requestCallBack: (Request) -> Boolean
    ): Pair<Request?, List<Request>> {
        val url = request.url.toString()
        Log.i(TAG, "Desktop headless resolver request: $url")

        var fixedRequest: Request? = null
        val extraRequestList = atomicListOf<Request>()

        val requestHeaders = request.headers.names().associateWith { request.header(it) ?: "" }.toMutableMap()
        if (userAgent != null) {
            requestHeaders["User-Agent"] = userAgent
        } else if (!requestHeaders.containsKey("User-Agent")) {
            requestHeaders["User-Agent"] = webViewUserAgent ?: USER_AGENT
        }

        try {
            val builder = Headers.Builder()
            requestHeaders.forEach { (k, v) ->
                try { builder.add(k, v) } catch (_: Throwable) {}
            }

            val response = app.baseClient.newCall(
                request.newBuilder()
                    .headers(builder.build())
                    .build()
            ).await()

            val finalUrl = response.request.url.toString()

            if (interceptUrl.containsMatchIn(finalUrl)) {
                fixedRequest = response.request.also {
                    requestCallBack(it)
                }
            }

            if (additionalUrls.any { it.containsMatchIn(finalUrl) }) {
                extraRequestList.add(response.request)
                requestCallBack(response.request)
            }

            val body = response.body.string()

            // Search for links/scripts inside response matching regex patterns
            val extractedUrls = Regex("""(?:https?://|/)[^\s"'<>]+""").findAll(body)
                .map { it.value }
                .distinct()

            for (foundUrl in extractedUrls) {
                if (interceptUrl.containsMatchIn(foundUrl) && fixedRequest == null) {
                    val req = safe { requestCreator("GET", foundUrl, referer = finalUrl) }
                    if (req != null) {
                        fixedRequest = req
                        requestCallBack(req)
                    }
                }
                if (additionalUrls.any { it.containsMatchIn(foundUrl) }) {
                    val req = safe { requestCreator("GET", foundUrl, referer = finalUrl) }
                    if (req != null) {
                        extraRequestList.add(req)
                        requestCallBack(req)
                    }
                }
            }

            // Execute script callback if script is given
            if (script != null) {
                scriptCallback?.invoke(body)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error in Desktop headless resolve: ${e.message}")
        }

        return fixedRequest to extraRequestList
    }
}
