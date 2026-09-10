package com.lagradost.cloudstream3.shared.sync.cloud

import androidx.compose.runtime.Immutable
import com.lagradost.cloudstream3.app
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.FileNotFoundException
import java.io.IOException
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Immutable
class S3StorageClient(
    val endpointUrl: String,
    val bucketName: String,
    val accessKey: String,
    val secretKey: String,
    val region: String = "auto",
    val baseDirectory: String = "CloudStreamSync"
) : CloudStorageClient {
    override val providerId: String = "s3_compatible"
    override val displayName: String = "S3 / Cloudflare R2"

    private val cleanBucket: String get() = bucketName.trim('/')

    override suspend fun testConnection(): Result<Boolean> = runCatching {
        val path = "/$cleanBucket"
        val queryParams = mapOf("max-keys" to "1")
        val response = executeRequest(method = "GET", path = path, queryParams = queryParams)
        if (!response.isSuccessful) {
            throw IOException("S3 connection test failed: HTTP ${response.code} ${response.text}")
        }
        true
    }

    override suspend fun listFiles(remoteDir: String, modifiedSince: Long): Result<List<RemoteFileMetadata>> = runCatching {
        val path = "/$cleanBucket"
        val prefix = buildListPrefix(remoteDir)
        val queryParams = if (prefix.isNotEmpty()) mapOf("prefix" to prefix) else emptyMap()
        val response = executeRequest(method = "GET", path = path, queryParams = queryParams)
        if (response.code == 404) {
            return@runCatching emptyList()
        }
        if (!response.isSuccessful) {
            throw IOException("S3 listFiles failed: HTTP ${response.code} ${response.text}")
        }
        parseContentsList(response.text, modifiedSince)
    }

    override suspend fun readFile(remotePath: String): Result<String> = runCatching {
        val path = buildObjectPath(remotePath)
        val response = executeRequest(method = "GET", path = path)
        if (response.code == 404) {
            throw FileNotFoundException("File not found in S3: $remotePath")
        }
        if (!response.isSuccessful) {
            throw IOException("S3 readFile failed: HTTP ${response.code} ${response.text}")
        }
        response.text
    }

    override suspend fun writeFile(remotePath: String, content: String): Result<Unit> = runCatching {
        val path = buildObjectPath(remotePath)
        val response = executeRequest(
            method = "PUT",
            path = path,
            bodyContent = content,
            contentType = JSON_CONTENT_TYPE
        )
        if (!response.isSuccessful) {
            throw IOException("S3 writeFile failed: HTTP ${response.code} ${response.text}")
        }
    }

    override suspend fun deleteFile(remotePath: String): Result<Unit> = runCatching {
        val path = buildObjectPath(remotePath)
        val response = executeRequest(method = "DELETE", path = path)
        if (!response.isSuccessful && response.code != 404) {
            throw IOException("S3 deleteFile failed: HTTP ${response.code} ${response.text}")
        }
    }

    private fun buildObjectPath(remotePath: String): String {
        val cleanBase = baseDirectory.trim('/')
        val cleanPath = remotePath.trim('/')
        return when {
            cleanBase.isEmpty() && cleanPath.isEmpty() -> "/$cleanBucket"
            cleanBase.isEmpty() -> "/$cleanBucket/$cleanPath"
            cleanPath.isEmpty() -> "/$cleanBucket/$cleanBase"
            else -> "/$cleanBucket/$cleanBase/$cleanPath"
        }
    }

    private fun buildListPrefix(remoteDir: String): String {
        val cleanBase = baseDirectory.trim('/')
        val cleanDir = remoteDir.trim('/')
        return when {
            cleanBase.isEmpty() && cleanDir.isEmpty() -> ""
            cleanBase.isEmpty() -> "$cleanDir/"
            cleanDir.isEmpty() -> "$cleanBase/"
            else -> "$cleanBase/$cleanDir/"
        }
    }

    private suspend fun executeRequest(
        method: String,
        path: String,
        queryParams: Map<String, String> = emptyMap(),
        bodyContent: String? = null,
        contentType: String? = null
    ): com.lagradost.nicehttp.NiceResponse {
        val payloadBytes = bodyContent?.toByteArray(Charsets.UTF_8)
        val payloadHash = if (payloadBytes != null) sha256Hex(payloadBytes) else EMPTY_PAYLOAD_HASH

        val canonicalUri = encodeUriPath(path)
        val canonicalQueryString = buildCanonicalQueryString(queryParams)
        val headers = signRequest(
            method = method,
            canonicalUri = canonicalUri,
            canonicalQueryString = canonicalQueryString,
            payloadHash = payloadHash,
            contentType = contentType
        )

        val fullUrl = buildFullUrl(canonicalUri, canonicalQueryString)
        return sendHttpRequest(method, fullUrl, headers, bodyContent, contentType)
    }

    private fun buildFullUrl(canonicalUri: String, canonicalQueryString: String): String {
        val base = endpointUrl.trimEnd('/')
        return if (canonicalQueryString.isEmpty()) {
            "$base$canonicalUri"
        } else {
            "$base$canonicalUri?$canonicalQueryString"
        }
    }

    private suspend fun sendHttpRequest(
        method: String,
        url: String,
        headers: Map<String, String>,
        bodyContent: String?,
        contentType: String?
    ): com.lagradost.nicehttp.NiceResponse {
        return when (method) {
            "GET" -> app.get(url, headers = headers)
            "DELETE" -> app.delete(url, headers = headers)
            "HEAD" -> app.head(url, headers = headers)
            "PUT" -> {
                val body = (bodyContent ?: "").toByteArray(Charsets.UTF_8)
                    .toRequestBody(contentType?.toMediaTypeOrNull())
                app.custom(method = "PUT", url = url, headers = headers, requestBody = body)
            }
            else -> {
                val body = bodyContent?.toByteArray(Charsets.UTF_8)
                    ?.toRequestBody(contentType?.toMediaTypeOrNull())
                app.custom(method = method, url = url, headers = headers, requestBody = body)
            }
        }
    }

    private fun signRequest(
        method: String,
        canonicalUri: String,
        canonicalQueryString: String,
        payloadHash: String,
        contentType: String?
    ): Map<String, String> {
        val host = resolveHostHeader(endpointUrl)
        val (amzDate, dateStamp) = getUtcTimestamps()
        val headersToSign = buildHeadersToSign(host, amzDate, payloadHash, contentType)

        val canonicalHeaders = buildCanonicalHeaders(headersToSign)
        val signedHeaders = buildSignedHeaders(headersToSign)
        val canonicalRequest = buildCanonicalRequest(
            method = method,
            canonicalUri = canonicalUri,
            canonicalQueryString = canonicalQueryString,
            canonicalHeaders = canonicalHeaders,
            signedHeaders = signedHeaders,
            payloadHash = payloadHash
        )

        val credentialScope = "$dateStamp/$region/$SERVICE_S3/$AWS4_TERMINATOR"
        val stringToSign = buildStringToSign(amzDate, credentialScope, canonicalRequest)
        val signingKey = deriveSigningKey(dateStamp)
        val signature = hmacSha256(signingKey, stringToSign.toByteArray(Charsets.UTF_8)).toHex()

        val authHeader = buildAuthorizationHeader(credentialScope, signedHeaders, signature)
        val finalHeaders = headersToSign.toMutableMap()
        finalHeaders["Authorization"] = authHeader
        return finalHeaders
    }

    private fun buildHeadersToSign(
        host: String,
        amzDate: String,
        payloadHash: String,
        contentType: String?
    ): Map<String, String> {
        val headers = sortedMapOf<String, String>()
        headers["host"] = host
        headers["x-amz-content-sha256"] = payloadHash
        headers["x-amz-date"] = amzDate
        if (contentType != null) {
            headers["content-type"] = contentType
        }
        return headers
    }

    private fun buildCanonicalHeaders(headers: Map<String, String>): String {
        return headers.entries.joinToString("") { (name, value) ->
            "${name.lowercase()}:${value.trim()}\n"
        }
    }

    private fun buildSignedHeaders(headers: Map<String, String>): String {
        return headers.keys.joinToString(";") { it.lowercase() }
    }

    // SigV4 canonical request leaves an empty line between canonical headers and signed headers per AWS spec
    private fun buildCanonicalRequest(
        method: String,
        canonicalUri: String,
        canonicalQueryString: String,
        canonicalHeaders: String,
        signedHeaders: String,
        payloadHash: String
    ): String {
        return "$method\n$canonicalUri\n$canonicalQueryString\n$canonicalHeaders\n$signedHeaders\n$payloadHash"
    }

    private fun buildStringToSign(
        amzDate: String,
        credentialScope: String,
        canonicalRequest: String
    ): String {
        val hashedCanonicalRequest = sha256Hex(canonicalRequest)
        return "$AWS4_ALGORITHM\n$amzDate\n$credentialScope\n$hashedCanonicalRequest"
    }

    private fun deriveSigningKey(dateStamp: String): ByteArray {
        val kSecret = ("AWS4" + secretKey).toByteArray(Charsets.UTF_8)
        val kDate = hmacSha256(kSecret, dateStamp.toByteArray(Charsets.UTF_8))
        val kRegion = hmacSha256(kDate, region.toByteArray(Charsets.UTF_8))
        val kService = hmacSha256(kRegion, SERVICE_S3.toByteArray(Charsets.UTF_8))
        return hmacSha256(kService, AWS4_TERMINATOR.toByteArray(Charsets.UTF_8))
    }

    private fun buildAuthorizationHeader(
        credentialScope: String,
        signedHeaders: String,
        signature: String
    ): String {
        return "$AWS4_ALGORITHM Credential=$accessKey/$credentialScope, SignedHeaders=$signedHeaders, Signature=$signature"
    }

    private fun resolveHostHeader(url: String): String {
        val hostWithPort = url.substringAfter("://").substringBefore('/')
        return when {
            url.startsWith("https://") && hostWithPort.endsWith(":443") -> hostWithPort.removeSuffix(":443")
            url.startsWith("http://") && hostWithPort.endsWith(":80") -> hostWithPort.removeSuffix(":80")
            else -> hostWithPort
        }
    }

    private fun getUtcTimestamps(now: Long = System.currentTimeMillis()): Pair<String, String> {
        val date = Date(now)
        val amzFormat = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val stampFormat = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return amzFormat.format(date) to stampFormat.format(date)
    }

    private fun encodeUriPath(path: String): String {
        val trimmed = path.trim('/')
        if (trimmed.isEmpty()) return "/"
        val encodedSegments = trimmed.split('/').joinToString("/") { encodeRfc3986(it) }
        val trailingSlash = if (path.endsWith('/')) "/" else ""
        return "/$encodedSegments$trailingSlash"
    }

    private fun buildCanonicalQueryString(queryParams: Map<String, String>): String {
        if (queryParams.isEmpty()) return ""
        return queryParams.toSortedMap()
            .map { (key, value) -> "${encodeRfc3986(key)}=${encodeRfc3986(value)}" }
            .joinToString("&")
    }

    private fun encodeRfc3986(value: String): String {
        return URLEncoder.encode(value, "UTF-8")
            .replace("+", "%20")
            .replace("*", "%2A")
            .replace("%7E", "~")
    }

    private fun sha256Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data).toHex()
    }

    private fun sha256Hex(text: String): String = sha256Hex(text.toByteArray(Charsets.UTF_8))

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val algorithm = "HmacSHA256"
        val mac = Mac.getInstance(algorithm)
        mac.init(SecretKeySpec(key, algorithm))
        return mac.doFinal(data)
    }

    private fun parseContentsList(xml: String, modifiedSince: Long): List<RemoteFileMetadata> {
        return CONTENTS_REGEX.findAll(xml).mapNotNull { match ->
            parseSingleContent(match.groupValues[1], modifiedSince)
        }.toList()
    }

    private fun parseSingleContent(contentXml: String, modifiedSince: Long): RemoteFileMetadata? {
        val rawKey = KEY_REGEX.find(contentXml)?.groupValues?.get(1)?.trim() ?: return null
        val key = unescapeXml(rawKey)
        val relativePath = resolveRelativePath(key)
        if (relativePath.isEmpty()) return null

        val modifiedTime = parseLastModified(LAST_MODIFIED_REGEX.find(contentXml)?.groupValues?.get(1))
        if (isOlderThanModifiedSince(modifiedTime, modifiedSince)) return null

        val size = SIZE_REGEX.find(contentXml)?.groupValues?.get(1)?.trim()?.toLongOrNull() ?: 0L
        val isDirectory = key.endsWith('/')
        val name = relativePath.trimEnd('/').substringAfterLast('/')

        return RemoteFileMetadata(
            path = relativePath,
            name = name,
            size = if (isDirectory) 0L else size,
            modifiedTime = modifiedTime,
            isDirectory = isDirectory
        )
    }

    private fun isOlderThanModifiedSince(modifiedTime: Long, modifiedSince: Long): Boolean {
        return modifiedSince > 0L && modifiedTime < modifiedSince
    }

    private fun resolveRelativePath(fullKey: String): String {
        val cleanBase = baseDirectory.trim('/')
        return when {
            cleanBase.isEmpty() -> fullKey.trimStart('/')
            fullKey.startsWith("$cleanBase/") -> fullKey.removePrefix("$cleanBase/").trimStart('/')
            fullKey == cleanBase -> ""
            else -> fullKey.trimStart('/')
        }
    }

    private fun parseLastModified(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L
        return runCatching {
            kotlinx.datetime.Instant.parse(dateStr.trim()).toEpochMilliseconds()
        }.getOrDefault(0L)
    }

    private fun unescapeXml(text: String): String {
        if (!text.contains('&')) return text
        return text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
    }

    companion object {
        private const val EMPTY_PAYLOAD_HASH = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        private const val JSON_CONTENT_TYPE = "application/json"
        private const val AWS4_ALGORITHM = "AWS4-HMAC-SHA256"
        private const val AWS4_TERMINATOR = "aws4_request"
        private const val SERVICE_S3 = "s3"

        private val CONTENTS_REGEX = Regex(
            "<(?:[a-zA-Z0-9_-]+:)?Contents[\\s>](.*?)</(?:[a-zA-Z0-9_-]+:)?Contents>",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        private val KEY_REGEX = Regex(
            "<(?:[a-zA-Z0-9_-]+:)?Key[\\s>](.*?)</(?:[a-zA-Z0-9_-]+:)?Key>",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        private val LAST_MODIFIED_REGEX = Regex(
            "<(?:[a-zA-Z0-9_-]+:)?LastModified[\\s>](.*?)</(?:[a-zA-Z0-9_-]+:)?LastModified>",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        private val SIZE_REGEX = Regex(
            "<(?:[a-zA-Z0-9_-]+:)?Size[\\s>](.*?)</(?:[a-zA-Z0-9_-]+:)?Size>",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )

        private val HEX_CHARS = "0123456789abcdef".toCharArray()

        private fun ByteArray.toHex(): String {
            val result = CharArray(size * 2)
            for (i in indices) {
                val v = this[i].toInt() and 0xFF
                result[i * 2] = HEX_CHARS[v ushr 4]
                result[i * 2 + 1] = HEX_CHARS[v and 0x0F]
            }
            return String(result)
        }
    }
}
