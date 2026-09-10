package com.lagradost.cloudstream3.shared.player.native

import com.lagradost.cloudstream3.utils.SubtitleHelper.fromLanguageToTagIETF
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class SubtitleStatus {
    IS_ACTIVE,
    REQUIRES_RELOAD,
    NOT_FOUND,
}

enum class SubtitleOrigin {
    URL,
    DOWNLOADED_FILE,
    EMBEDDED_IN_VIDEO
}

/**
 * @param originalName the start of the name to be displayed in the player
 * @param nameSuffix An extra suffix added to the subtitle to make sure it is unique
 * @param url Url for the subtitle, when EMBEDDED_IN_VIDEO this variable is used as the real backend id
 * @param headers if empty it will use the base onlineDataSource headers else only the specified headers
 * @param languageCode usually, tags such as "en", "es-mx", or "zh-hant-TW". But it could be something like "English 4"
 */
@Serializable
data class SubtitleData(
    @SerialName("originalName") val originalName: String,
    @SerialName("nameSuffix") val nameSuffix: String = "",
    @SerialName("url") val url: String,
    @SerialName("origin") val origin: SubtitleOrigin = SubtitleOrigin.URL,
    @SerialName("mimeType") val mimeType: String = "",
    @SerialName("headers") val headers: Map<String, String> = emptyMap(),
    @SerialName("languageCode") val languageCode: String? = null,
    @SerialName("name") val name: String = if (nameSuffix.isNotBlank()) "$originalName $nameSuffix" else originalName,
) {
    constructor(
        name: String,
        url: String,
        origin: SubtitleOrigin = SubtitleOrigin.URL,
        mimeType: String = "",
        headers: Map<String, String> = emptyMap(),
        languageCode: String? = null,
    ) : this(
        originalName = name,
        nameSuffix = "",
        url = url,
        origin = origin,
        mimeType = mimeType,
        headers = headers,
        languageCode = languageCode,
        name = name
    )

    /** Internal ID for media3, unique for each link. */
    fun getId(): String {
        return if (origin == SubtitleOrigin.EMBEDDED_IN_VIDEO) url
        else "$url|$name"
    }

    /** Returns true if langCode is the same as the IETF tag */
    fun matchesLanguageCode(langCode: String): Boolean {
        return getIETF_tag() == langCode
    }

    /** Tries hard to figure out a valid IETF tag based on language code and name. Will return null if not found. */
    fun getIETF_tag(): String? {
        return fromLanguageToTagIETF(this.languageCode) ?: fromLanguageToTagIETF(this.originalName, halfMatch = true)
    }

    /**
     * Gets the URL, but tries to fix it if it is malformed.
     */
    fun getFixedUrl(): String {
        // Some extensions fail to include the protocol, this helps with that.
        val fixedSubUrl = if (this.url.startsWith("//")) {
            "https:${this.url}"
        } else this.url
        return fixedSubUrl
    }
}
