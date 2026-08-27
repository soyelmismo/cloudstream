package com.lagradost.cloudstream3.network

import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.nicehttp.ignoreAllSSLErrors
import okhttp3.Cache
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.io.File
import java.security.Security
import java.util.concurrent.TimeUnit

open class SessionCookieJar : CookieJar {
    private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        synchronized(cookieStore) {
            val hostCookies = cookieStore.getOrPut(url.host) { mutableListOf() }
            for (cookie in cookies) {
                hostCookies.removeAll { it.name == cookie.name }
                hostCookies.add(cookie)
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        synchronized(cookieStore) {
            val result = mutableListOf<Cookie>()
            cookieStore[url.host]?.let { result.addAll(it) }
            cookieStore.forEach { (domain, cookies) ->
                if (domain != url.host && (url.host.endsWith(".$domain") || domain.endsWith(".${url.host}"))) {
                    result.addAll(cookies)
                }
            }
            return result
        }
    }

    fun clear() {
        synchronized(cookieStore) {
            cookieStore.clear()
        }
    }
}

fun buildSharedOkHttpClient(
    cacheDir: File? = null,
    dnsPreference: Int = 0,
    ignoreSSL: Boolean = false,
    cookieJar: CookieJar = SessionCookieJar()
): OkHttpClient {
    // Attempt to insert Conscrypt security provider if present on classpath
    safe {
        try {
            val conscryptClass = Class.forName("org.conscrypt.Conscrypt")
            val newProviderMethod = conscryptClass.getMethod("newProvider")
            val provider = newProviderMethod.invoke(null) as java.security.Provider
            Security.insertProviderAt(provider, 1)
        } catch (_: Throwable) {}
    }

    val builder = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .apply {
            if (ignoreSSL) {
                ignoreAllSSLErrors()
            }
        }

    if (cacheDir != null) {
        try {
            builder.cache(
                Cache(
                    directory = File(cacheDir, "http_cache"),
                    maxSize = 50L * 1024L * 1024L // 50 MiB
                )
            )
        } catch (_: Throwable) {}
    }

    when (dnsPreference) {
        1 -> builder.addGoogleDns()
        2 -> builder.addCloudFlareDns()
        4 -> builder.addAdGuardDns()
        5 -> builder.addDNSWatchDns()
        6 -> builder.addQuad9Dns()
        7 -> builder.addDnsSbDns()
        8 -> builder.addCanadianShieldDns()
    }

    return builder.build()
}

private val DEFAULT_NETWORK_HEADERS = mapOf("user-agent" to USER_AGENT)

fun getHeaders(
    headers: Map<String, String>,
    cookie: Map<String, String> = emptyMap()
): okhttp3.Headers {
    val cookieMap =
        if (cookie.isNotEmpty()) mapOf(
            "Cookie" to cookie.entries.joinToString(" ") {
                "${it.key}=${it.value};"
            }
        ) else mapOf()
    val tempHeaders = (DEFAULT_NETWORK_HEADERS + headers + cookieMap)
    val builder = okhttp3.Headers.Builder()
    tempHeaders.forEach { (k, v) ->
        safe {
            builder.add(k, v)
        }
    }
    return builder.build()
}
