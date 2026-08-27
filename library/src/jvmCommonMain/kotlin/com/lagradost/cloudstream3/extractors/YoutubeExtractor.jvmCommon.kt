package com.lagradost.cloudstream3.extractors

import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newAudioFile
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.nicehttp.RequestBodyTypes
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType

actual open class YoutubeExtractor actual constructor() : ExtractorApi() {

    actual override val mainUrl = "https://www.youtube.com"
    actual override val name = "YouTube"
    actual override val requiresReferer = false

    actual override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val videoId = extractYouTubeId(url) ?: return
        val watchUrl = "$mainUrl/watch?v=$videoId"

        var extracted = false

        // 1. Try NewPipeExtractor
        try {
            val info = StreamInfo.getInfo(watchUrl)
            val isLive = info.streamType == StreamType.LIVE_STREAM
                || info.streamType == StreamType.AUDIO_LIVE_STREAM
                || info.streamType == StreamType.POST_LIVE_STREAM
                || info.streamType == StreamType.POST_LIVE_AUDIO_STREAM

            if (isLive && info.hlsUrl != null) {
                callback(
                    newExtractorLink(
                        source = name,
                        name = "YouTube Live",
                        url = info.hlsUrl
                    ) {
                        type = ExtractorLinkType.M3U8
                    }
                )
                extracted = true
            } else {
                extracted = processVideo(info, subtitleCallback, callback)
            }
        } catch (e: Throwable) {
            Log.e(name, "NewPipe extractor failed: ${e.message}")
        }

        // 2. Pure HTTP Android Player API Fallback
        if (!extracted) {
            extractViaPlayerApi(videoId, subtitleCallback, callback)
        }
    }

    private suspend fun processVideo(
        info: StreamInfo,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        var foundAny = false

        // 1. Progressive streams (contain muxed video and audio for direct player playback)
        val progressiveStreams = info.videoStreams.orEmpty()
        progressiveStreams.forEach { video ->
            val qualityVal = video.height.takeIf { it > 0 } ?: Qualities.Unknown.value
            callback(
                newExtractorLink(
                    source = name,
                    name = "YouTube ${video.resolution ?: "${video.height}p"}",
                    url = video.content
                ) {
                    quality = qualityVal
                    type = ExtractorLinkType.VIDEO
                }
            )
            foundAny = true
        }

        // 2. Adaptive streams (video-only streams with attached audio tracks)
        val videoOnlyStreams = info.videoOnlyStreams.orEmpty()
        val audioStreams = info.audioStreams.orEmpty()
        videoOnlyStreams.forEach { video ->
            val qualityVal = video.height.takeIf { it > 0 } ?: Qualities.Unknown.value
            callback(
                newExtractorLink(
                    source = name,
                    name = "YouTube ${normalizeCodec(video.codec)} ${video.resolution ?: "${video.height}p"}",
                    url = video.content
                ) {
                    quality = qualityVal
                    audioTracks = audioStreams.map { newAudioFile(it.content) }
                    type = ExtractorLinkType.VIDEO
                }
            )
            foundAny = true
        }

        // 3. Subtitles
        info.subtitles.orEmpty().forEach { subtitle ->
            subtitleCallback(
                newSubtitleFile(
                    lang = subtitle.displayLanguageName
                        ?: subtitle.languageTag
                        ?: "Unknown",
                    url = subtitle.content
                )
            )
        }

        return foundAny
    }

    private suspend fun extractViaPlayerApi(
        videoId: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            val payload = """
                {
                    "context": {
                        "client": {
                            "clientName": "ANDROID",
                            "clientVersion": "19.29.35",
                            "androidSdkVersion": 34,
                            "hl": "en",
                            "gl": "US"
                        }
                    },
                    "videoId": "$videoId"
                }
            """.trimIndent()

            val response = app.post(
                "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
                requestBody = payload.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()),
                headers = mapOf(
                    "User-Agent" to "com.google.android.youtube/19.29.35 (Linux; U; Android 14; US) gzip",
                    "Content-Type" to "application/json"
                )
            )

            val json = tryParseJson<Map<String, Any?>>(response.text) ?: return false
            val streamingData = json["streamingData"] as? Map<*, *> ?: return false

            var found = false

            // Progressive formats
            val formats = (streamingData["formats"] as? List<*>)?.filterIsInstance<Map<String, Any?>>()
            formats?.forEach { fmt ->
                val streamUrl = fmt["url"] as? String
                if (!streamUrl.isNullOrBlank()) {
                    val qualityLabel = fmt["qualityLabel"] as? String ?: "${fmt["height"] ?: "360"}p"
                    val height = (fmt["height"] as? Number)?.toInt() ?: 360
                    callback(
                        newExtractorLink(
                            source = name,
                            name = "YouTube $qualityLabel",
                            url = streamUrl
                        ) {
                            quality = height
                            type = ExtractorLinkType.VIDEO
                        }
                    )
                    found = true
                }
            }

            // HLS Manifest URL if available
            val hlsUrl = streamingData["hlsManifestUrl"] as? String
            if (!hlsUrl.isNullOrBlank()) {
                callback(
                    newExtractorLink(
                        source = name,
                        name = "YouTube HLS",
                        url = hlsUrl
                    ) {
                        type = ExtractorLinkType.M3U8
                    }
                )
                found = true
            }

            // Captions
            val captions = (json["captions"] as? Map<*, *>)
                ?.get("playerCaptionsTracklistRenderer") as? Map<*, *>
            val captionTracks = (captions?.get("captionTracks") as? List<*>)?.filterIsInstance<Map<String, Any?>>()
            captionTracks?.forEach { track ->
                val baseUrl = track["baseUrl"] as? String
                val nameObj = track["name"] as? Map<*, *>
                val langName = nameObj?.get("simpleText") as? String ?: track["languageCode"] as? String ?: "Unknown"
                if (!baseUrl.isNullOrBlank()) {
                    subtitleCallback(
                        newSubtitleFile(
                            lang = langName,
                            url = baseUrl
                        )
                    )
                }
            }

            found
        } catch (e: Throwable) {
            Log.e("YoutubeExtractor", "Error in Android Player API fallback: ${e.message}")
            false
        }
    }

    companion object {
        fun extractYouTubeId(url: String): String? {
            val trimmed = url.trim()
            if (trimmed.length == 11 && trimmed.matches(Regex("^[a-zA-Z0-9_-]{11}$"))) {
                return trimmed
            }

            val regex = Regex(
                """(?:youtu\.be/|youtube(?:-nocookie)?\.com/(?:.*v=|v/|u/\w/|embed/|shorts/|live/))([a-zA-Z0-9_-]{11})"""
            )

            return regex.find(url)?.groupValues?.get(1)
        }

        fun normalizeCodec(codec: String?): String {
            if (codec.isNullOrBlank()) return ""
            val c = codec.lowercase()
            return when {
                c.startsWith("av01") -> "AV1"
                c.startsWith("vp9") -> "VP9"
                c.startsWith("avc1") || c.startsWith("h264") -> "H264"
                c.startsWith("hev1") || c.startsWith("hvc1") || c.startsWith("hevc") -> "H265"
                else -> codec.substringBefore('.').uppercase()
            }
        }
    }
}
