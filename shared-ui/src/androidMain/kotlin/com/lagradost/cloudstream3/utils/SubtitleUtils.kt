package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.shared.player.native.SubtitleData
import com.lagradost.cloudstream3.shared.player.native.SubtitleOrigin

object SubtitleUtils {

    // Only these files are allowed, so no videos as subtitles
    private val allowedExtensions = listOf(
        ".vtt", ".srt", ".txt", ".ass",
        ".ttml", ".sbv", ".dfxp"
    )

    /**
     * @param name the file name of the subtitle
     * @param display the file name of the video
     * @param cleanDisplay the cleanDisplayName of the video file name
     */
    fun isMatchingSubtitle(
        name: String,
        display: String,
        cleanDisplay: String
    ): Boolean {
        val hasValidExtension = allowedExtensions.any { name.endsWith(it, ignoreCase = true) }
        val isNotDisplayName = !name.equals(display, ignoreCase = true)
        val startsWithCleanDisplay = cleanDisplayName(name).startsWith(cleanDisplay, ignoreCase = true)
        return hasValidExtension && isNotDisplayName && startsWithCleanDisplay
    }

    fun cleanDisplayName(name: String): String {
        return name.substringBeforeLast('.').trim()
    }

    fun fromExtensionToMime(url: String): String {
        return when {
            url.endsWith(".vtt", ignoreCase = true) -> androidx.media3.common.MimeTypes.TEXT_VTT
            url.endsWith(".srt", ignoreCase = true) -> androidx.media3.common.MimeTypes.APPLICATION_SUBRIP
            url.endsWith(".xml", ignoreCase = true) || url.endsWith(".ttml", ignoreCase = true) -> androidx.media3.common.MimeTypes.APPLICATION_TTML
            url.endsWith(".ass", ignoreCase = true) -> androidx.media3.common.MimeTypes.TEXT_SSA
            else -> androidx.media3.common.MimeTypes.APPLICATION_SUBRIP
        }
    }

    fun fromSubtitleFile(file: SubtitleFile): SubtitleData {
        return SubtitleData(
            originalName = file.lang,
            nameSuffix = "",
            url = file.url,
            origin = SubtitleOrigin.URL,
            mimeType = fromExtensionToMime(file.url),
            headers = file.headers ?: emptyMap(),
            languageCode = file.langTag ?: file.lang
        )
    }
}
