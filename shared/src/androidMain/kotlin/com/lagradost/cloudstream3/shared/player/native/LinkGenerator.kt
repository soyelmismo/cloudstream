package com.lagradost.cloudstream3.shared.player.native

import android.net.Uri
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.SubtitleHelper.fromCodeToLangTagIETF
import com.lagradost.cloudstream3.utils.SubtitleHelper.fromLanguageToTagIETF
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.unshortenLinkSafe
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class ExtractorUri(
    val uri: Uri,
    val name: String,

    val basePath: String? = null,
    val relativePath: String? = null,
    val displayName: String? = null,

    val id: Int? = null,
    val parentId: Int? = null,
    val episode: Int? = null,
    val season: Int? = null,
    val headerName: String? = null,
    val tvType: TvType? = null,
)

/**
 * Used to open the player more easily with the LinkGenerator
 **/
data class BasicLink(
    val url: String,
    val name: String? = null,
)

class LinkGenerator(
    private val links: List<BasicLink>,
    private val extract: Boolean = true,
    private val refererUrl: String? = null,
    id: Int?
) : NoVideoGenerator(id) {
    override suspend fun generateLinks(
        clearCache: Boolean,
        sourceTypes: Set<ExtractorLinkType>,
        callback: (Pair<ExtractorLink?, ExtractorUri?>) -> Unit,
        subtitleCallback: (SubtitleData) -> Unit,
        offset: Int,
        isCasting: Boolean
    ): Boolean {
        links.amap { link ->
            if (!extract || !loadExtractor(link.url, refererUrl, {
                    subtitleCallback(PlayerSubtitleHelper.getSubtitleData(it))
                }) {
                    callback(it to null)
                }) {

                // if don't extract or if no extractor found simply return the link
                callback(
                    newExtractorLink(
                        "",
                        link.name ?: link.url,
                        unshortenLinkSafe(link.url), // unshorten because it might be a raw link
                        type = INFER_TYPE,
                    ) {
                        this.referer = refererUrl ?: ""
                        this.quality = Qualities.Unknown.value
                    } to null
                )
            }
        }

        return true
    }
}

@Serializable
data class MinimalVideoLink(
    val uri: String? = null,
    @SerialName("url") val url: String? = null,
    @SerialName("mimeType") val mimeType: String = "video/mp4",
    @SerialName("name") val name: String? = null,
    @SerialName("headers") var headers: Map<String, String> = mapOf(),
    @SerialName("quality") val quality: Int? = null,
) {
    suspend fun toExtractorLink(): Pair<ExtractorLink?, ExtractorUri?> =
        url?.let { url ->
            newExtractorLink(
                source = "NONE",
                name = name ?: "Unknown",
                url = url,
                type = ExtractorLinkType.entries.firstOrNull { ty -> ty.getMimeType() == mimeType }
                    ?: ExtractorLinkType.VIDEO) {

                this@newExtractorLink.headers =
                    this@MinimalVideoLink.headers

                this@newExtractorLink.quality =
                    this@MinimalVideoLink.quality ?: Qualities.Unknown.value
            }
        } to uri?.let { uriStr ->
            ExtractorUri(
                uri = Uri.parse(uriStr),
                name = name ?: "Unknown",
            )
        }
}

@Serializable
data class MinimalSubtitleLink(
    @SerialName("url") val url: String,
    @SerialName("mimeType") val mimeType: String = "text/vtt",
    @SerialName("name") val name: String? = null,
    @SerialName("headers") var headers: Map<String, String> = mapOf(),
) {
    fun toSubtitleData(): SubtitleData = SubtitleData(
        url = url,
        nameSuffix = "",
        mimeType = mimeType,
        originalName = name ?: "Unknown",
        headers = headers,
        origin = SubtitleOrigin.URL,
        languageCode = fromCodeToLangTagIETF(name) ?:
                       fromLanguageToTagIETF(name, true) ?:
                       name,
    )
}

class MinimalLinkGenerator(
    private val links: List<MinimalVideoLink>,
    private val subs: List<MinimalSubtitleLink>,
    id: Int?
) : NoVideoGenerator(id) {
    override suspend fun generateLinks(
        clearCache: Boolean,
        sourceTypes: Set<ExtractorLinkType>,
        callback: (Pair<ExtractorLink?, ExtractorUri?>) -> Unit,
        subtitleCallback: (SubtitleData) -> Unit,
        offset: Int,
        isCasting: Boolean
    ): Boolean {
        for (link in links) {
            callback(link.toExtractorLink())
        }
        for (link in subs) {
            subtitleCallback(link.toSubtitleData())
        }

        return true
    }
}