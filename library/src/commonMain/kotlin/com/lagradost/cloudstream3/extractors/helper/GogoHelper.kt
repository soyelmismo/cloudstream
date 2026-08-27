package com.lagradost.cloudstream3.extractors.helper

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.base64DecodeArray
import com.lagradost.cloudstream3.base64Encode
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import io.ktor.http.Url
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.nodes.Document

object GogoHelper {

    private val aesCbc = CryptographyProvider.Default.get(AES.CBC)

    /**
     * @param id base64Decode(show_id) + IV
     * @return the encryption key
     */
    private fun getEncryptionKey(id: String): String? {
        return safe {
            id.map {
                it.code.toString(16)
            }.joinToString("").substring(0, 32)
        }
    }

    // https://github.com/saikou-app/saikou/blob/45d0a99b8a72665a29a1eadfb38c506b842a29d7/app/src/main/java/ani/saikou/parsers/anime/extractors/GogoCDN.kt#L97
    // No Licence on the function
    @OptIn(DelicateCryptographyApi::class)
    private suspend fun cryptoHandler(
        string: String,
        iv: String,
        secretKeyString: String,
        encrypt: Boolean = true,
    ): String {
        val ivBytes = iv.encodeToByteArray()
        val keyBytes = secretKeyString.encodeToByteArray()
        val aesKey = aesCbc.keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, keyBytes)
        val cipher = aesKey.cipher(padding = true)

        return if (!encrypt) {
            val plainBytes = cipher.decryptWithIv(ivBytes, base64DecodeArray(string))
            plainBytes.decodeToString()
        } else {
            base64Encode(cipher.encryptWithIv(ivBytes, string.encodeToByteArray()))
        }
    }

    private val ID_REGEX = Regex("id=([^&]+)")

    private data class EncryptionKeys(
        val iv: String,
        val key: String,
        val decryptKey: String
    )

    private suspend fun resolveKeys(
        iframeUrl: String,
        iv: String?,
        secretKey: String?,
        secretDecryptKey: String?,
        id: String,
        document: Document?
    ): EncryptionKeys? {
        val resolvedDoc = document ?: app.get(iframeUrl).document
        val foundIv = iv ?: resolvedDoc
            .select("""div.wrapper[class*=container]""")
            .attr("class").split("-").lastOrNull() ?: return null
        val foundKey = secretKey ?: getEncryptionKey(base64Decode(id) + foundIv) ?: return null
        val foundDecryptKey = secretDecryptKey ?: foundKey
        return EncryptionKeys(foundIv, foundKey, foundDecryptKey)
    }

    private suspend fun buildEncryptQuery(
        iframeUrl: String,
        id: String,
        encryptedId: String,
        foundIv: String,
        foundKey: String,
        isUsingAdaptiveData: Boolean,
        document: Document?
    ): String {
        if (!isUsingAdaptiveData) return "id=$encryptedId&alias=$id"
        val realDocument = document ?: app.get(iframeUrl).document
        val dataEncrypted = realDocument.select("script[data-name='episode']").attr("data-value")
        val headers = cryptoHandler(dataEncrypted, foundIv, foundKey, false)
        return "id=$encryptedId&alias=$id&" + headers.substringAfter("&")
    }

    private suspend fun invokeGogoSource(
        source: GogoSource,
        mainApiName: String,
        mainUrl: String,
        sourceCallback: (ExtractorLink) -> Unit,
    ) {
        if (source.file.contains(".m3u8")) {
            M3u8Helper.generateM3u8(
                mainApiName,
                source.file,
                mainUrl,
                headers = mapOf("Origin" to "https://plyr.link"),
            ).forEach(sourceCallback)
            return
        }
        sourceCallback.invoke(
            newExtractorLink(
                mainApiName,
                mainApiName,
                source.file,
            ) {
                this.referer = mainUrl
                this.quality = getQualityFromName(source.label)
            }
        )
    }

    /**
     * @param iframeUrl something like https://gogoplay4.com/streaming.php?id=XXXXXX
     * @param mainApiName used for ExtractorLink names and source
     * @param iv secret iv from site, required non-null if isUsingAdaptiveKeys is off
     * @param secretKey secret key for decryption from site, required non-null if isUsingAdaptiveKeys is off
     * @param secretDecryptKey secret key to decrypt the response json, required non-null if isUsingAdaptiveKeys is off
     * @param isUsingAdaptiveKeys generates keys from IV and ID, see [getEncryptionKey]
     * @param isUsingAdaptiveData generate encrypt-ajax data based on $("script[data-name='episode']")[0].dataset.value
     */
    suspend fun extractVidstream(
        iframeUrl: String,
        mainApiName: String,
        callback: (ExtractorLink) -> Unit,
        iv: String?,
        secretKey: String?,
        secretDecryptKey: String?,
        isUsingAdaptiveKeys: Boolean,
        isUsingAdaptiveData: Boolean,
        iframeDocument: Document? = null,
    ) = safeApiCall {
        if (!isUsingAdaptiveKeys && (iv == null || secretKey == null || secretDecryptKey == null)) {
            return@safeApiCall
        }

        val id = ID_REGEX.find(iframeUrl)?.groupValues?.get(1) ?: return@safeApiCall
        val keys = resolveKeys(iframeUrl, iv, secretKey, secretDecryptKey, id, iframeDocument) ?: return@safeApiCall

        val host = Url(iframeUrl).host
        val mainUrl = "https://$host"
        val encryptedId = cryptoHandler(id, keys.iv, keys.key)
        val encryptRequestData = buildEncryptQuery(
            iframeUrl, id, encryptedId, keys.iv, keys.key, isUsingAdaptiveData, iframeDocument
        )

        val jsonResponse = app.get(
            "$mainUrl/encrypt-ajax.php?$encryptRequestData",
            headers = mapOf("X-Requested-With" to "XMLHttpRequest")
        )
        val dataEncrypted = jsonResponse.parsedSafe<GogoJsonData>()?.data ?: return@safeApiCall
        val dataDecrypted = cryptoHandler(dataEncrypted, keys.iv, keys.decryptKey, false)
        val sources = AppUtils.parseJson<GogoSources>(dataDecrypted)

        sources.source?.forEach { invokeGogoSource(it, mainApiName, mainUrl, callback) }
        sources.sourceBk?.forEach { invokeGogoSource(it, mainApiName, mainUrl, callback) }
    }

    @Serializable
    data class GogoSources(
        @JsonProperty("source") @SerialName("source") val source: List<GogoSource>?,
        @JsonProperty("sourceBk") @SerialName("sourceBk") val sourceBk: List<GogoSource>?,
    )

    @Serializable
    data class GogoSource(
        @JsonProperty("file") @SerialName("file") val file: String,
        @JsonProperty("label") @SerialName("label") val label: String?,
        @JsonProperty("type") @SerialName("type") val type: String?,
        @JsonProperty("default") @SerialName("default") val default: String? = null,
    )

    @Serializable
    data class GogoJsonData(
        @JsonProperty("data") @SerialName("data") val data: String? = null,
    )
}
