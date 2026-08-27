package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.StringUtils.decodeUrl
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Document

open class InternetArchive : ExtractorApi() {
    override val mainUrl = "https://archive.org"
    override val requiresReferer = false
    override val name = "Internet Archive"
    override fun getExtractorUrl(id: String): String = "$mainUrl/details/$id"

    private suspend fun fetchDocument(url: String): Document? =
        archivedItems[url] ?: runCatching {
            val doc = app.get(url).document
            archivedItems[url] = doc
            doc
        }.onFailure { logError(it) }.getOrNull()

    private suspend fun extractSubtitles(document: Document, subtitleCallback: (SubtitleFile) -> Unit) {
        document.select("a[href*=\"/download/\"]").forEach { element ->
            val href = element.attr("href")
            val ext = href.substringAfterLast('.', "").lowercase()
            if (ext in SUBTITLE_EXTENSIONS) {
                val subtitleUrl = "$mainUrl$href"
                val fileName = subtitleUrl.substringAfterLast('/')
                val lang = fileName.substringBeforeLast(".").substringAfterLast(".")
                subtitleCallback(newSubtitleFile(lang = lang, url = subtitleUrl))
            }
        }
    }

    private fun inferQuality(fileName: String): Int = when {
        fileName.contains("1080", ignoreCase = true) -> Qualities.P1080.value
        fileName.contains("720", ignoreCase = true) -> Qualities.P720.value
        fileName.contains("480", ignoreCase = true) -> Qualities.P480.value
        else -> Qualities.Unknown.value
    }

    private fun resolveMediaUrl(element: org.jsoup.nodes.Element): String? = when {
        element.hasAttr("href") -> "$mainUrl${element.attr("href")}"
        element.hasAttr("content") -> element.attr("content")
        else -> null
    }

    private fun buildDisplayName(mediaUrl: String, fileName: String): String {
        if (mediaUrl.length <= 1) return this.name
        val fileExtension = mediaUrl.substringAfterLast(".")
        val fileNameCleaned = fileName.decodeUrl().substringBeforeLast('.')
        return "$fileNameCleaned ($fileExtension)"
    }

    private suspend fun emitMediaLink(element: org.jsoup.nodes.Element, callback: (ExtractorLink) -> Unit) {
        val mediaUrl = resolveMediaUrl(element) ?: return
        if (mediaUrl.isEmpty()) return

        val fileName = mediaUrl.substringAfterLast('/')
        val quality = inferQuality(fileName)
        val name = buildDisplayName(mediaUrl, fileName)

        callback(
            newExtractorLink(this.name, name, mediaUrl) {
                this.quality = quality
            }
        )
    }

    private suspend fun extractMediaLinks(document: Document, callback: (ExtractorLink) -> Unit) {
        val fileLinks = document.select("a[href*=\"/download/\"]").filter {
            it.attr("href").substringAfterLast('.', "").lowercase() in MEDIA_EXTENSIONS
        }
        val elements = fileLinks.ifEmpty {
            document.head().select("meta[property=\"og:video\"]")
        }
        elements.forEach { emitMediaLink(it, callback) }
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val document = fetchDocument(url) ?: return
        extractSubtitles(document, subtitleCallback)
        extractMediaLinks(document, callback)
    }

    companion object {
        private val SUBTITLE_EXTENSIONS = setOf("vtt", "srt")
        private val MEDIA_EXTENSIONS = setOf("mp4", "mpg", "mkv", "avi", "ogv", "ogg", "mp3", "wav", "flac")
        private var archivedItems: MutableMap<String, Document> = mutableMapOf()
    }
}