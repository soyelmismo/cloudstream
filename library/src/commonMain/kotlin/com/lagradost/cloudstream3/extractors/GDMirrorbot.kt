package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import io.ktor.http.Url
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class Techinmind : GDMirrorbot() {
    override val name = "Techinmind Cloud AIO"
    override val mainUrl = "https://stream.techinmind.space"
    override val requiresReferer = true
}

open class GDMirrorbot : ExtractorApi() {
    override val name = "GDMirrorbot"
    override val mainUrl = "https://gdmirrorbot.nl"
    override val requiresReferer = true

    private fun buildApiUrl(url: String, finalId: String, myKey: String, idType: String): String {
        if (!url.contains("/tv/")) return "$mainUrl/mymovieapi?$idType=$finalId&key=$myKey"
        val season = REGEX_TV_SEASON.find(url)?.groupValues?.get(1) ?: "1"
        val episode = REGEX_TV_EPISODE.find(url)?.groupValues?.get(1) ?: "1"
        return "$mainUrl/myseriesapi?tmdbid=$finalId&season=$season&epname=$episode&key=$myKey"
    }

    private suspend fun resolveKeyedSidAndHost(url: String): Pair<String, String?> {
        val pageText = app.get(url).text
        val finalId = REGEX_FINAL_ID.find(pageText)?.groupValues?.get(1)
        val myKey = REGEX_MY_KEY.find(pageText)?.groupValues?.get(1)
        val idType = REGEX_ID_TYPE.find(pageText)?.groupValues?.get(1) ?: "imdbid"
        val baseUrl = REGEX_BASE_URL.find(pageText)?.groupValues?.get(1)
        val hostUrl = baseUrl?.let { getBaseUrl(it) }

        val resolvedPageText = if (finalId != null && myKey != null) {
            val apiUrl = buildApiUrl(url, finalId, myKey, idType)
            app.get(apiUrl).text
        } else {
            pageText
        }

        val embedData = tryParseJson<EmbedData>(resolvedPageText)
        val embedId = url.substringAfterLast("/")
        val sidValue = embedData?.data?.firstOrNull()?.fileSlug?.takeIf { it.isNotBlank() } ?: embedId
        return Pair(sidValue, hostUrl)
    }

    private suspend fun resolveSidAndHost(url: String): Pair<String, String?> {
        if (!url.contains("key=")) {
            return Pair(url.substringAfterLast("embed/"), getBaseUrl(app.get(url).url))
        }
        return resolveKeyedSidAndHost(url)
    }

    private fun parseMresult(responseText: String): Map<String, String>? {
        val raw = responseText.substringAfter("\"mresult\":").trimStart()
        return when {
            raw.startsWith("\"") -> {
                try {
                    tryParseJson<Map<String, String>>(base64Decode(raw.trim('"')))
                } catch (_: Exception) {
                    null
                }
            }
            raw.startsWith("{") -> {
                tryParseJson<Map<String, String>>(responseText.substringAfter("\"mresult\":").trimStart())
            }
            else -> null
        }
    }

    private suspend fun extractFromSite(
        key: String,
        siteUrls: Map<String, String>,
        mresult: Map<String, String>,
        siteFriendlyNames: Map<String, String>?,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val base = siteUrls[key]?.trimEnd('/') ?: return
        val path = mresult[key]?.trimStart('/') ?: return
        val fullUrl = "$base/$path"
        val friendlyName = siteFriendlyNames?.get(key) ?: key
        try {
            when (friendlyName) {
                "StreamHG", "EarnVids" -> VidHidePro().getUrl(fullUrl, referer, subtitleCallback, callback)
                "RpmShare", "UpnShare", "StreamP2p" -> VidStack().getUrl(fullUrl, referer, subtitleCallback, callback)
                else -> loadExtractor(fullUrl, referer ?: mainUrl, subtitleCallback, callback)
            }
        } catch (e: Exception) {
            Log.e("GDMirrorbot", "Failed to extract from $friendlyName at $fullUrl: $e")
        }
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val (sid, host) = resolveSidAndHost(url)
        val responseText = app.post("$host/embedhelper.php", data = mapOf("sid" to sid)).text

        val root = tryParseJson<EmbedHelper>(responseText) ?: return
        val siteUrls = root.siteUrls ?: return
        val mresult = parseMresult(responseText) ?: return

        siteUrls.keys.intersect(mresult.keys).forEach { key ->
            extractFromSite(key, siteUrls, mresult, root.siteFriendlyNames, referer, subtitleCallback, callback)
        }
    }

    private fun getBaseUrl(url: String): String =
        Url(url).let { "${it.protocol.name}://${it.host}" }

    companion object {
        private val REGEX_FINAL_ID = Regex("""FinalID\s*=\s*"([^"]+)"""")
        private val REGEX_MY_KEY = Regex("""myKey\s*=\s*"([^"]+)"""")
        private val REGEX_ID_TYPE = Regex("""idType\s*=\s*"([^"]+)"""")
        private val REGEX_BASE_URL = Regex("""let\s+baseUrl\s*=\s*"([^"]+)"""")
        private val REGEX_TV_SEASON = Regex("""/tv/\d+/(\d+)/""")
        private val REGEX_TV_EPISODE = Regex("""/tv/\d+/\d+/(\d+)""")
    }

    @Serializable
    private data class EmbedData(
        @JsonProperty("data") @SerialName("data") val data: List<FileSlug>? = null,
    )

    @Serializable
    private data class FileSlug(
        @JsonProperty("fileslug") @SerialName("fileslug") val fileSlug: String? = null,
    )

    @Serializable
    private data class EmbedHelper(
        @JsonProperty("siteUrls") @SerialName("siteUrls") val siteUrls: Map<String, String>? = null,
        @JsonProperty("siteFriendlyNames") @SerialName("siteFriendlyNames") val siteFriendlyNames: Map<String, String>? = null,
    )
}
