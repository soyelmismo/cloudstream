package com.lagradost.cloudstream3.shared.player.native

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.Layout
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import android.util.TypedValue
import androidx.annotation.FontRes
import androidx.annotation.OptIn
import androidx.annotation.Px
import androidx.core.content.res.ResourcesCompat
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.shared.persistence.repository.AppPreferenceManager
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.Event
import com.lagradost.cloudstream3.utils.UIHelper.toPx
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File

const val SUBTITLE_KEY = "subtitle_settings"
const val SUBTITLE_AUTO_SELECT_KEY = "subs_auto_select"
const val SUBTITLE_DOWNLOAD_KEY = "subs_auto_download"
const val DEF_SUBS_ELEVATION = 20

@Serializable
data class SaveCaptionStyle(
    @JsonProperty("foregroundColor") @SerialName("foregroundColor") var foregroundColor: Int,
    @JsonProperty("backgroundColor") @SerialName("backgroundColor") var backgroundColor: Int,
    @JsonProperty("windowColor") @SerialName("windowColor") var windowColor: Int,
    @OptIn(UnstableApi::class)
    @JsonProperty("edgeType") @SerialName("edgeType") var edgeType: @CaptionStyleCompat.EdgeType Int,
    @JsonProperty("edgeColor") @SerialName("edgeColor") var edgeColor: Int,
    @FontRes @JsonProperty("typeface") @SerialName("typeface") var typeface: Int?,
    @JsonProperty("typefaceFilePath") @SerialName("typefaceFilePath") var typefaceFilePath: String?,
    @JsonProperty("elevation") @SerialName("elevation") var elevation: Int, // in dp
    @JsonProperty("fixedTextSize") @SerialName("fixedTextSize") var fixedTextSize: Float?, // in sp
    @Px @JsonProperty("edgeSize") @SerialName("edgeSize") var edgeSize: Float? = null,
    @JsonProperty("removeCaptions") @SerialName("removeCaptions") var removeCaptions: Boolean = false,
    @JsonProperty("removeBloat") @SerialName("removeBloat") var removeBloat: Boolean = true,
    @JsonProperty("upperCase") @SerialName("upperCase") var upperCase: Boolean = false,
    @JsonProperty("bold") @SerialName("bold") var bold: Boolean = false,
    @JsonProperty("italic") @SerialName("italic") var italic: Boolean = false,
    @JsonProperty("backgroundRadius") @SerialName("backgroundRadius") var backgroundRadius: Float? = null,
    @JsonProperty("alignment") @SerialName("alignment") var alignment: Int? = null,
)

@OptIn(UnstableApi::class)
object SubtitlesHelper {
    val applyStyleEvent = Event<SaveCaptionStyle>()
    private val captionRegex = Regex("""(-\s?|)[\[({][\S\s]*?[])}]\s*""")
    private var cachedSubtitleStyle: SaveCaptionStyle? = null

    fun setSubtitleViewStyle(
        view: SubtitleView?,
        data: SaveCaptionStyle,
        applyElevation: Boolean
    ) {
        if (view == null) return
        val ctx = view.context ?: return
        val style = ctx.fromSaveToStyle(data)
        view.setStyle(style)

        if (applyElevation) {
            view.setPadding(
                view.paddingLeft, data.elevation.toPx, view.paddingRight, view.paddingBottom
            )
        }

        view.clipToPadding = false
        view.clipChildren = false

        val size = data.fixedTextSize ?: 25.0f
        view.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        view.setBottomPaddingFraction(0.0f)
    }

    fun Cue.Builder.applyStyle(style: SaveCaptionStyle): Cue.Builder {
        val edgeSize = style.edgeSize
        setTextSize(Cue.DIMEN_UNSET, Cue.TYPE_UNSET)

        text?.let { text ->
            val customSpan = SpannableString.valueOf(text)
            if (edgeSize != null) {
                customSpan.setSpan(
                    OutlineSpan(edgeSize), 0, customSpan.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            setText(customSpan)
        }

        text?.let { text ->
            val customSpan = SpannableString.valueOf(text)
            val tf = when (style.bold to style.italic) {
                (true to true) -> Typeface.BOLD_ITALIC
                (true to false) -> Typeface.BOLD
                (false to true) -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            if (tf != Typeface.NORMAL) {
                val styleSpan = StyleSpan(tf)
                customSpan.setSpan(
                    styleSpan, 0, customSpan.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            setText(customSpan)
        }

        text?.let { text ->
            val customSpan = SpannableString.valueOf(text)
            val radius = style.backgroundRadius

            if (radius != null && style.backgroundColor != Color.TRANSPARENT) {
                val styleSpan = RoundedBackgroundColorSpan(
                    style.backgroundColor,
                    this.textAlignment ?: Layout.Alignment.ALIGN_CENTER,
                    2.0F + radius * 0.5f,
                    radius
                )
                customSpan.setSpan(
                    styleSpan, 0, customSpan.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            setText(customSpan)
        }

        text?.let { text ->
            if (style.removeCaptions) {
                setText(text.replace(captionRegex, ""))
            }
        }

        return this
    }

    fun Context.fromSaveToStyle(data: SaveCaptionStyle): CaptionStyleCompat {
        return CaptionStyleCompat(
            data.foregroundColor,
            if (data.backgroundRadius == null) data.backgroundColor else Color.TRANSPARENT,
            data.windowColor,
            data.edgeType,
            data.edgeColor,
            data.typefaceFilePath?.let {
                try {
                    Typeface.createFromFile(File(it))
                } catch (e: Exception) {
                    null
                }
            } ?: data.typeface?.let {
                ResourcesCompat.getFont(this, it)
            } ?: Typeface.SANS_SERIF
        )
    }

    private fun getDefColor(id: Int): Int {
        return when (id) {
            0 -> Color.WHITE
            1 -> Color.BLACK
            2 -> Color.TRANSPARENT
            3 -> Color.TRANSPARENT
            else -> Color.TRANSPARENT
        }
    }

    fun Context.saveStyle(style: SaveCaptionStyle) {
        cachedSubtitleStyle = style
        AppPreferenceManager.setStringSync(SUBTITLE_KEY, toJson(style))
    }

    fun getCurrentSavedStyle(): SaveCaptionStyle {
        return cachedSubtitleStyle ?: (AppPreferenceManager.getStringSync(SUBTITLE_KEY)?.let {
            parseJson<SaveCaptionStyle>(it)
        } ?: SaveCaptionStyle(
            foregroundColor = getDefColor(0),
            backgroundColor = getDefColor(2),
            windowColor = getDefColor(3),
            edgeType = CaptionStyleCompat.EDGE_TYPE_OUTLINE,
            edgeColor = getDefColor(1),
            typeface = null,
            typefaceFilePath = null,
            elevation = DEF_SUBS_ELEVATION,
            fixedTextSize = null,
        )).also { cachedSubtitleStyle = it }
    }

    fun getDownloadSubsLanguageTagIETF(): List<String> {
        return AppPreferenceManager.getStringSetSync(SUBTITLE_DOWNLOAD_KEY)?.toList()
            ?: AppPreferenceManager.getStringSync(SUBTITLE_DOWNLOAD_KEY)?.let { parseJson<List<String>>(it) }
            ?: listOf("en")
    }

    fun getAutoSelectLanguageTagIETF(): String {
        return AppPreferenceManager.getStringSync(SUBTITLE_AUTO_SELECT_KEY, "en") ?: "en"
    }
}
