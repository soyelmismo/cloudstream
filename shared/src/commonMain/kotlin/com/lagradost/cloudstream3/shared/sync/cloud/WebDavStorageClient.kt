package com.lagradost.cloudstream3.shared.sync.cloud

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Encode
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.FileNotFoundException
import java.io.IOException
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class WebDavStorageClient(
    val serverUrl: String,
    val username: String,
    val password: String,
    val baseDirectory: String = "CloudStreamSync"
) : CloudStorageClient {
    override val providerId: String = "webdav"
    override val displayName: String = "WebDAV"

    override suspend fun testConnection(): Result<Boolean> = runCatching {
        val url = buildUrl("")
        val response = app.custom(
            method = "PROPFIND",
            url = url,
            headers = buildHeaders(mapOf("Depth" to "0", "Content-Type" to XML_MEDIA_TYPE)),
            requestBody = PROPFIND_XML.toRequestBody(XML_MEDIA_TYPE.toMediaTypeOrNull())
        )
        evaluateConnectionResponse(response, url)
    }

    override suspend fun listFiles(remoteDir: String, modifiedSince: Long): Result<List<RemoteFileMetadata>> = runCatching {
        val url = buildUrl(remoteDir)
        val response = app.custom(
            method = "PROPFIND",
            url = url,
            headers = buildHeaders(mapOf("Depth" to "1", "Content-Type" to XML_MEDIA_TYPE)),
            requestBody = PROPFIND_XML.toRequestBody(XML_MEDIA_TYPE.toMediaTypeOrNull())
        )
        if (response.code == 404) {
            return@runCatching emptyList()
        }
        if (response.code != 207 && !response.isSuccessful) {
            throw IOException("WebDAV PROPFIND failed: HTTP ${response.code}")
        }
        parsePropfindResponse(response.text, remoteDir, modifiedSince)
    }

    override suspend fun readFile(remotePath: String): Result<String> = runCatching {
        val url = buildUrl(remotePath)
        val response = app.get(url, headers = buildHeaders())
        if (response.code == 404) {
            throw FileNotFoundException("File not found: $remotePath")
        }
        if (!response.isSuccessful) {
            throw IOException("WebDAV GET failed: HTTP ${response.code}")
        }
        response.text
    }

    override suspend fun writeFile(remotePath: String, content: String): Result<Unit> = runCatching {
        ensureParentDirectories(remotePath)
        val url = buildUrl(remotePath)
        val body = content.toRequestBody(TEXT_MEDIA_TYPE.toMediaTypeOrNull())
        val response = app.custom(
            method = "PUT",
            url = url,
            headers = buildHeaders(),
            requestBody = body
        )
        if (!response.isSuccessful) {
            throw IOException("WebDAV PUT failed with HTTP ${response.code}: ${response.text}")
        }
    }

    override suspend fun deleteFile(remotePath: String): Result<Unit> = runCatching {
        val url = buildUrl(remotePath)
        val response = app.delete(url, headers = buildHeaders())
        if (!response.isSuccessful && response.code != 404) {
            throw IOException("WebDAV DELETE failed: HTTP ${response.code}")
        }
    }

    private fun buildAuthHeader(): String? {
        if (username.isBlank() && password.isBlank()) return null
        return "Basic " + base64Encode("$username:$password".encodeToByteArray())
    }

    private fun buildHeaders(extra: Map<String, String> = emptyMap()): Map<String, String> {
        val auth = buildAuthHeader()
        val headers = mutableMapOf<String, String>()
        if (auth != null) {
            headers["Authorization"] = auth
        }
        headers.putAll(extra)
        return headers
    }

    private fun buildUrl(path: String): String {
        val cleanServer = serverUrl.trimEnd('/')
        val cleanBase = baseDirectory.trim('/')
        val cleanPath = path.trim('/')
        return when {
            cleanBase.isEmpty() && cleanPath.isEmpty() -> cleanServer
            cleanBase.isEmpty() -> "$cleanServer/$cleanPath"
            cleanPath.isEmpty() -> "$cleanServer/$cleanBase"
            else -> "$cleanServer/$cleanBase/$cleanPath"
        }
    }

    private suspend fun evaluateConnectionResponse(response: com.lagradost.nicehttp.NiceResponse, url: String): Boolean {
        if (response.code == 207 || response.isSuccessful) {
            return true
        }
        if (response.code == 404) {
            return createDirectoryIfMissing(url)
        }
        if (response.code in AUTH_ERROR_CODES) {
            throw IOException("Authentication failed: HTTP ${response.code}")
        }
        throw IOException("Connection failed: HTTP ${response.code}")
    }

    private suspend fun createDirectoryIfMissing(url: String): Boolean {
        val mkcolRes = app.custom("MKCOL", "${url.trimEnd('/')}/", headers = buildHeaders())
        // 405 Method Not Allowed indicates collection already exists per RFC 4918
        return mkcolRes.isSuccessful || mkcolRes.code == 405
    }

    private fun parsePropfindResponse(xml: String, remoteDir: String, modifiedSince: Long): List<RemoteFileMetadata> {
        val targetPath = buildUrl(remoteDir).substringAfter("://").substringAfter('/').trim('/')
        return RESPONSE_REGEX.findAll(xml).mapNotNull { match ->
            parseSingleResponse(match.groupValues[1], remoteDir, targetPath, modifiedSince)
        }.toList()
    }

    private fun parseSingleResponse(
        responseXml: String,
        remoteDir: String,
        targetPath: String,
        modifiedSince: Long
    ): RemoteFileMetadata? {
        val rawHref = HREF_REGEX.find(responseXml)?.groupValues?.get(1)?.trim() ?: return null
        val decodedHref = runCatching { URLDecoder.decode(rawHref, "UTF-8") }.getOrDefault(rawHref)
        val itemPath = decodedHref.substringAfter("://").substringAfter('/').trim('/')
        if (itemPath.equals(targetPath, ignoreCase = true)) {
            return null
        }
        val isDirectory = COLLECTION_REGEX.containsMatchIn(responseXml)
        val modifiedTime = parseDate(MODIFIED_REGEX.find(responseXml)?.groupValues?.get(1)?.trim())
        if (modifiedSince > 0L && modifiedTime < modifiedSince) {
            return null
        }
        val name = resolveItemName(responseXml, decodedHref)
        val relativePath = buildItemRelativePath(remoteDir, name)
        val size = LENGTH_REGEX.find(responseXml)?.groupValues?.get(1)?.trim()?.toLongOrNull() ?: 0L
        return RemoteFileMetadata(
            path = relativePath,
            name = name,
            size = if (isDirectory) 0L else size,
            modifiedTime = modifiedTime,
            isDirectory = isDirectory
        )
    }

    private fun resolveItemName(responseXml: String, decodedHref: String): String {
        val displayName = DISPLAY_NAME_REGEX.find(responseXml)?.groupValues?.get(1)?.trim()
        if (!displayName.isNullOrBlank()) {
            return displayName
        }
        return decodedHref.trimEnd('/').substringAfterLast('/')
    }

    private fun buildItemRelativePath(remoteDir: String, name: String): String {
        val cleanDir = remoteDir.trim('/')
        return if (cleanDir.isEmpty() || cleanDir == ".") name else "$cleanDir/$name"
    }

    private fun parseDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L
        return parseRfc1123(dateStr) ?: parseIso8601(dateStr) ?: 0L
    }

    private fun parseRfc1123(dateStr: String): Long? = runCatching {
        val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
        format.timeZone = TimeZone.getTimeZone("GMT")
        format.parse(dateStr)?.time
    }.getOrNull()

    private fun parseIso8601(dateStr: String): Long? = runCatching {
        kotlinx.datetime.Instant.parse(dateStr).toEpochMilliseconds()
    }.getOrNull()

    private suspend fun ensureParentDirectories(remotePath: String) {
        val cleanPath = remotePath.trim('/')
        ensureDirectory(baseDirectory)
        if (!cleanPath.contains('/')) return
        val segments = cleanPath.substringBeforeLast('/').split('/').filter { it.isNotEmpty() }
        var current = baseDirectory.trim('/')
        for (segment in segments) {
            current = if (current.isEmpty()) segment else "$current/$segment"
            ensureDirectory(current)
        }
    }

    private suspend fun ensureDirectory(dir: String) {
        if (dir.isBlank()) return
        val url = "${serverUrl.trimEnd('/')}/${dir.trim('/')}/"
        app.custom(method = "MKCOL", url = url, headers = buildHeaders())
    }

    companion object {
        private const val XML_MEDIA_TYPE = "application/xml; charset=utf-8"
        private const val TEXT_MEDIA_TYPE = "text/plain; charset=utf-8"
        private val AUTH_ERROR_CODES = setOf(401, 403)

        private val RESPONSE_REGEX = Regex(
            "<(?:[a-zA-Z0-9_-]+:)?response[\\s>](.*?)</(?:[a-zA-Z0-9_-]+:)?response>",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        private val HREF_REGEX = Regex(
            "<(?:[a-zA-Z0-9_-]+:)?href[\\s>](.*?)</(?:[a-zA-Z0-9_-]+:)?href>",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        private val COLLECTION_REGEX = Regex(
            "<(?:[a-zA-Z0-9_-]+:)?collection[\\s/>]",
            RegexOption.IGNORE_CASE
        )
        private val LENGTH_REGEX = Regex(
            "<(?:[a-zA-Z0-9_-]+:)?getcontentlength[\\s>](.*?)</(?:[a-zA-Z0-9_-]+:)?getcontentlength>",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        private val MODIFIED_REGEX = Regex(
            "<(?:[a-zA-Z0-9_-]+:)?getlastmodified[\\s>](.*?)</(?:[a-zA-Z0-9_-]+:)?getlastmodified>",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        private val DISPLAY_NAME_REGEX = Regex(
            "<(?:[a-zA-Z0-9_-]+:)?displayname[\\s>](.*?)</(?:[a-zA-Z0-9_-]+:)?displayname>",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )

        private const val PROPFIND_XML = """<?xml version="1.0" encoding="utf-8" ?>
<d:propfind xmlns:d="DAV:">
  <d:prop>
    <d:displayname/>
    <d:getcontentlength/>
    <d:getlastmodified/>
    <d:resourcetype/>
  </d:prop>
</d:propfind>"""
    }
}
