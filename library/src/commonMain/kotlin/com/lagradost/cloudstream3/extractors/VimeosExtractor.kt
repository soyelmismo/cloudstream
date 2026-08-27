package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.getPacked

class VimeosExtractor : ExtractorApi() {
    override var name = "Vimeos"
    override val mainUrl = "https://vimeos.net"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val effectiveReferer = referer ?: mainUrl
        val fileCode = url.trimEnd('/').split("/").last().replace(".html", "").replace("embed-", "")
        val postResp = try {
            app.post(
                "https://vimeos.net/dl",
                data = mapOf("op" to "embed", "file_code" to fileCode, "auto" to "1", "referer" to ""),
                referer = url,
                headers = mapOf("Origin" to "https://vimeos.net")
            )
        } catch (_: Throwable) { null }

        val doc = postResp?.document ?: app.get(url, referer = effectiveReferer).document
        val scripts = doc.select("script").map { it.data() }
        val unpackedScripts = scripts.mapNotNull { data ->
            if (data.contains("eval(function(p,a,c,k,e,d)")) {
                getPacked(data) ?: data
            } else {
                data
            }
        }

        val m3u8Regex = Regex("""https?://[^\s"'<>\\]+?\.(?:m3u8)[^\s"'<>\\]*""")
        for (script in unpackedScripts) {
            val matches = m3u8Regex.findAll(script)
            for (match in matches) {
                val streamUrl = match.value
                val streamReferer = if (streamUrl.contains(".vimeos.zip")) {
                    "https://vimeos.zip/"
                } else if (streamUrl.contains(".vimeos.net")) {
                    "https://vimeos.net/"
                } else {
                    url
                }

                M3u8Helper.generateM3u8(
                    name,
                    streamUrl,
                    referer = streamReferer,
                    headers = mapOf("Referer" to streamReferer)
                ).forEach(callback)
                return
            }
        }
    }
}
