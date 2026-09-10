package com.lagradost.cloudstream3.shared.player.native

import android.util.Log
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.SubtitleView
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.shared.player.native.SubtitlesHelper.setSubtitleViewStyle
import com.lagradost.cloudstream3.utils.UIHelper.toPx

private const val SUBTITLE_TAG = "PlayerSubtitleHelper"

@OptIn(UnstableApi::class)
class PlayerSubtitleHelper {
    private var activeSubtitles: Set<SubtitleData> = emptySet()
    private var allSubtitles: Set<SubtitleData> = emptySet()

    fun getAllSubtitles(): Set<SubtitleData> {
        return allSubtitles
    }

    fun setActiveSubtitles(list: Set<SubtitleData>) {
        activeSubtitles = list
    }

    fun setAllSubtitles(list: Set<SubtitleData>) {
        allSubtitles = list
    }

    var subtitleView: SubtitleView? = null

    companion object {
        fun String.toSubtitleMimeType(): String {
            return when {
                endsWith("vtt", true) -> MimeTypes.TEXT_VTT
                endsWith("srt", true) -> MimeTypes.APPLICATION_SUBRIP
                endsWith("xml", true) || endsWith("ttml", true) -> MimeTypes.APPLICATION_TTML
                else -> MimeTypes.APPLICATION_SUBRIP
            }
        }

        fun getSubtitleData(subtitleFile: SubtitleFile): SubtitleData {
            return SubtitleData(
                originalName = subtitleFile.lang,
                nameSuffix = "",
                url = subtitleFile.url,
                origin = SubtitleOrigin.URL,
                mimeType = subtitleFile.url.toSubtitleMimeType(),
                headers = subtitleFile.headers ?: emptyMap(),
                languageCode = subtitleFile.langTag ?: subtitleFile.lang
            )
        }
    }

    fun subtitleStatus(sub: SubtitleData?): SubtitleStatus {
        if (activeSubtitles.contains(sub)) {
            return SubtitleStatus.IS_ACTIVE
        }
        if (allSubtitles.contains(sub)) {
            return SubtitleStatus.REQUIRES_RELOAD
        }
        return SubtitleStatus.NOT_FOUND
    }

    fun setSubStyle(style: SaveCaptionStyle) {
        Log.i(SUBTITLE_TAG, "SET STYLE = $style")
        subtitleView?.translationY = -style.elevation.toPx.toFloat()
        setSubtitleViewStyle(subtitleView, style, true)
    }

    fun initSubtitles(subView: SubtitleView?, subHolder: FrameLayout?, style: SaveCaptionStyle?) {
        subtitleView = subView
        subView?.let { sView ->
            (sView.parent as ViewGroup?)?.removeView(sView)
            subHolder?.addView(sView)
        }
        style?.let {
            setSubStyle(it)
        }
    }
}
