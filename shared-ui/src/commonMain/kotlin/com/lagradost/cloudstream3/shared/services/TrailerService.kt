package com.lagradost.cloudstream3.shared.services

import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TrailerData
import com.lagradost.cloudstream3.extractors.YoutubeExtractor
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.CancellationException

/**
 * Multiplatform Trailer Extraction and Resolution Service.
 *
 * Handles normalizing trailer URLs, resolving YouTube trailers via NewPipe & Android Player API fallback,
 * extracting third-party video hosts via [loadExtractor], and providing direct fallback streaming links
 * to ensure smooth trailer playback in [com.lagradost.cloudstream3.shared.ui.result.TrailerDialog] on Android and Desktop JVM.
 */
object TrailerService {
    private const val TAG = "TrailerService"
    private val YT_ID_REGEX = Regex("^[a-zA-Z0-9_-]{11}$")
    private val YT_URL_REGEX = Regex(
        """(?:youtu\.be/|youtube(?:-nocookie)?\.com/(?:.*v=|v/|u/\w/|embed/|shorts/|live/))([a-zA-Z0-9_-]{11})"""
    )

    private inline fun runCatchingTrailer(label: String, block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.e(TAG, "$label error: ${e.message}")
        }
    }

    /**
     * Extracts YouTube Video ID if present.
     */
    fun extractYouTubeId(url: String): String? {
        val trimmed = url.trim()
        if (trimmed.length == 11 && trimmed.matches(YT_ID_REGEX)) {
            return trimmed
        }
        return YT_URL_REGEX.find(trimmed)?.groupValues?.get(1)
    }

    /**
     * Checks if the given URL or ID corresponds to a YouTube video.
     */
    fun isYouTubeUrl(url: String): Boolean {
        if (extractYouTubeId(url) != null) return true
        val trimmed = url.trim()
        return trimmed.contains("youtube.com", ignoreCase = true) ||
            trimmed.contains("youtu.be", ignoreCase = true) ||
            trimmed.contains("youtube-nocookie.com", ignoreCase = true)
    }

    /**
     * Normalizes a trailer URL into a canonical format.
     */
    fun normalizeTrailerUrl(url: String): String {
        val ytId = extractYouTubeId(url)
        if (ytId != null) {
            return "https://www.youtube.com/watch?v=$ytId"
        }
        return url.trim()
    }

    /**
     * Extracts playable streaming links and subtitles for a [TrailerData] descriptor.
     */
    suspend fun extractTrailer(
        trailer: TrailerData,
        subtitleCallback: (SubtitleFile) -> Unit,
        linkCallback: (ExtractorLink) -> Unit
    ): Boolean {
        if (trailer.raw) {
            val link = newExtractorLink(
                source = "Trailer",
                name = "Direct Trailer",
                url = trailer.extractorUrl,
                type = INFER_TYPE
            ) {
                this.referer = trailer.referer ?: ""
                this.quality = Qualities.Unknown.value
                this.headers = trailer.headers
            }
            linkCallback(link)
            return true
        }

        return extractTrailerUrl(
            url = trailer.extractorUrl,
            referer = trailer.referer,
            headers = trailer.headers,
            subtitleCallback = subtitleCallback,
            linkCallback = linkCallback
        )
    }

    /**
     * Extracts playable streaming links and subtitles from a raw URL.
     */
    suspend fun extractTrailerUrl(
        url: String,
        referer: String? = null,
        headers: Map<String, String> = emptyMap(),
        subtitleCallback: (SubtitleFile) -> Unit,
        linkCallback: (ExtractorLink) -> Unit
    ): Boolean {
        val normalizedUrl = normalizeTrailerUrl(url)
        var foundLinks = false

        val wrappedLinkCallback: (ExtractorLink) -> Unit = { link ->
            foundLinks = true
            linkCallback(link)
        }

        runCatchingTrailer("General trailer extraction") {
            // 1. YouTube specific handler
            if (isYouTubeUrl(normalizedUrl)) {
                runCatchingTrailer("YouTube extractor") {
                    YoutubeExtractor().getUrl(
                        url = normalizedUrl,
                        referer = referer,
                        subtitleCallback = subtitleCallback,
                        callback = wrappedLinkCallback
                    )
                }
            }

            // 2. Generic registered extractors via loadExtractor
            if (!foundLinks) {
                runCatchingTrailer("loadExtractor") {
                    val handled = loadExtractor(
                        url = normalizedUrl,
                        referer = referer,
                        subtitleCallback = subtitleCallback,
                        callback = wrappedLinkCallback
                    )
                    if (handled) foundLinks = true
                }
            }

            // 3. Fallback direct stream link if URL is a direct web link
            if (!foundLinks && (normalizedUrl.startsWith("http://") || normalizedUrl.startsWith("https://"))) {
                val direct = newExtractorLink(
                    source = "Trailer",
                    name = "Trailer Stream",
                    url = normalizedUrl,
                    type = INFER_TYPE
                ) {
                    this.referer = referer ?: ""
                    this.quality = Qualities.Unknown.value
                    this.headers = headers
                }
                wrappedLinkCallback(direct)
            }
        }

        return foundLinks
    }
}
