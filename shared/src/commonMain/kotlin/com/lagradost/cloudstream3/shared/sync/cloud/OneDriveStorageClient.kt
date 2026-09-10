package com.lagradost.cloudstream3.shared.sync.cloud

import androidx.compose.runtime.Immutable
import com.lagradost.cloudstream3.app
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.FileNotFoundException
import java.io.IOException
import java.net.URLEncoder

@Serializable
@Immutable
data class OneDriveFolderFacet(
    val childCount: Int? = null
)

@Serializable
@Immutable
data class OneDriveItem(
    val id: String = "",
    val name: String = "",
    val size: Long = 0L,
    val lastModifiedDateTime: String? = null,
    val folder: OneDriveFolderFacet? = null
)

@Serializable
@Immutable
data class OneDriveChildrenResponse(
    val value: List<OneDriveItem> = emptyList(),
    @SerialName("@odata.nextLink") val nextLink: String? = null
)

@Serializable
@Immutable
data class OneDriveCreateFolderRequest(
    val name: String,
    val folder: OneDriveFolderFacet = OneDriveFolderFacet(),
    @SerialName("@microsoft.graph.conflictBehavior") val conflictBehavior: String = "fail"
)

class OneDriveStorageClient(
    val accessTokenProvider: suspend () -> String?,
    val baseFolderName: String = "CloudStreamSync"
) : CloudStorageClient {
    override val providerId: String = "onedrive"
    override val displayName: String = "Microsoft OneDrive"

    private val knownDirectories = mutableSetOf<String>()
    private val folderMutex = Mutex()

    override suspend fun testConnection(): Result<Boolean> = runCatching {
        val headers = getAuthHeaders()
        val response = app.get(GRAPH_DRIVE_ROOT_ENDPOINT, headers = headers)
        if (!response.isSuccessful) {
            throw IOException("OneDrive connection test failed: HTTP ${response.code}")
        }
        true
    }

    override suspend fun listFiles(remoteDir: String, modifiedSince: Long): Result<List<RemoteFileMetadata>> = runCatching {
        val headers = getAuthHeaders()
        var currentUrl: String? = "${buildPathUrl(remoteDir, suffix = ":/children")}?\$top=1000"
        val result = mutableListOf<RemoteFileMetadata>()

        while (currentUrl != null) {
            val page = fetchChildrenPage(currentUrl, headers) ?: return@runCatching emptyList()
            page.value.mapNotNullTo(result) { item ->
                mapToFileMetadata(item, remoteDir, modifiedSince)
            }
            currentUrl = page.nextLink
        }
        result
    }

    override suspend fun readFile(remotePath: String): Result<String> = runCatching {
        val headers = getAuthHeaders()
        val url = buildPathUrl(remotePath, suffix = ":/content")
        val response = app.get(url, headers = headers)
        if (response.code == 404) {
            throw FileNotFoundException("File not found in OneDrive: $remotePath")
        }
        if (!response.isSuccessful) {
            throw IOException("Failed to read OneDrive file: HTTP ${response.code}")
        }
        response.text
    }

    override suspend fun writeFile(remotePath: String, content: String): Result<Unit> = runCatching {
        val headers = getAuthHeaders()
        ensureParentDirectories(remotePath, headers)
        val url = buildPathUrl(remotePath, suffix = ":/content")
        val body = content.toRequestBody(TEXT_MEDIA_TYPE.toMediaTypeOrNull())
        val response = app.put(
            url,
            headers = headers + mapOf("Content-Type" to TEXT_MEDIA_TYPE),
            requestBody = body
        )
        if (!response.isSuccessful) {
            throw IOException("Failed to write OneDrive file: HTTP ${response.code} ${response.text}")
        }
    }

    override suspend fun deleteFile(remotePath: String): Result<Unit> = runCatching {
        val headers = getAuthHeaders()
        val url = buildPathUrl(remotePath)
        val response = app.delete(url, headers = headers)
        if (!response.isSuccessful && response.code != 404) {
            throw IOException("Failed to delete OneDrive file: HTTP ${response.code}")
        }
    }

    private suspend fun getAuthHeaders(extra: Map<String, String> = emptyMap()): Map<String, String> {
        val token = accessTokenProvider()
            ?: throw IllegalStateException("No OneDrive access token available")
        val base = mapOf("Authorization" to "Bearer $token")
        return if (extra.isEmpty()) base else base + extra
    }

    private suspend fun fetchChildrenPage(
        url: String,
        headers: Map<String, String>
    ): OneDriveChildrenResponse? {
        val response = app.get(url, headers = headers)
        if (response.code == 404) return null
        if (!response.isSuccessful) {
            throw IOException("Failed to list OneDrive files: HTTP ${response.code}")
        }
        return json.decodeFromString<OneDriveChildrenResponse>(response.text)
    }

    private fun mapToFileMetadata(
        item: OneDriveItem,
        remoteDir: String,
        modifiedSince: Long
    ): RemoteFileMetadata? {
        val modifiedTime = parseIsoDate(item.lastModifiedDateTime)
        if (isOlderThanThreshold(modifiedTime, modifiedSince)) return null
        val cleanDir = remoteDir.trim('/')
        return RemoteFileMetadata(
            path = resolveRelativePath(cleanDir, item.name),
            name = item.name,
            size = item.size,
            modifiedTime = modifiedTime,
            isDirectory = item.folder != null
        )
    }

    private fun isOlderThanThreshold(modifiedTime: Long, modifiedSince: Long): Boolean {
        return modifiedSince > 0L && modifiedTime < modifiedSince
    }

    private fun resolveRelativePath(cleanDir: String, itemName: String): String {
        if (cleanDir.isEmpty() || cleanDir == ".") return itemName
        return "$cleanDir/$itemName"
    }

    private fun parseIsoDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L
        return runCatching { Instant.parse(dateStr).toEpochMilliseconds() }.getOrDefault(0L)
    }

    private fun encodePath(path: String): String {
        return path.split('/')
            .filter { it.isNotEmpty() }
            .joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
    }

    private fun buildPathUrl(subPath: String, suffix: String = ""): String {
        val cleanBase = baseFolderName.trim('/')
        val cleanSub = subPath.trim('/')
        val fullPath = resolveFullPath(cleanBase, cleanSub)
        val encoded = encodePath(fullPath)
        return if (encoded.isEmpty()) {
            "$GRAPH_DRIVE_ROOT_ENDPOINT$suffix"
        } else {
            "$GRAPH_DRIVE_ROOT_ENDPOINT:/$encoded$suffix"
        }
    }

    private fun resolveFullPath(base: String, sub: String): String = when {
        base.isEmpty() -> sub
        sub.isEmpty() || sub == "." -> base
        else -> "$base/$sub"
    }

    private suspend fun ensureParentDirectories(remotePath: String, headers: Map<String, String>) {
        val cleanBase = baseFolderName.trim('/')
        if (cleanBase.isNotEmpty()) {
            ensureDirectory(cleanBase, headers)
        }
        val clean = remotePath.trim('/')
        if (!clean.contains('/')) return
        val segments = clean.substringBeforeLast('/').split('/').filter { it.isNotEmpty() }
        var current = cleanBase
        for (segment in segments) {
            current = if (current.isEmpty()) segment else "$current/$segment"
            ensureDirectory(current, headers)
        }
    }

    private suspend fun ensureDirectory(path: String, headers: Map<String, String>): Unit {
        folderMutex.withLock {
            val clean = path.trim('/')
            if (clean.isEmpty() || knownDirectories.contains(clean)) return@withLock
            val parentPath = clean.substringBeforeLast('/', "")
            val folderName = clean.substringAfterLast('/')
            val url = resolveFolderCreationUrl(parentPath)
            createFolderApiCall(url, folderName, headers, clean)
        }
    }

    private fun resolveFolderCreationUrl(parentPath: String): String {
        return if (parentPath.isEmpty()) {
            "$GRAPH_DRIVE_ROOT_ENDPOINT/children"
        } else {
            "$GRAPH_DRIVE_ROOT_ENDPOINT:/${encodePath(parentPath)}:/children"
        }
    }

    private suspend fun createFolderApiCall(
        url: String,
        folderName: String,
        headers: Map<String, String>,
        directoryKey: String
    ) {
        val payload = json.encodeToString(OneDriveCreateFolderRequest(name = folderName))
        val body = payload.toRequestBody(JSON_MEDIA_TYPE.toMediaTypeOrNull())
        val response = app.post(
            url,
            headers = headers + mapOf("Content-Type" to JSON_MEDIA_TYPE),
            requestBody = body
        )
        // 201 Created or 409 Conflict (directory exists already) are successful outcomes
        if (response.isSuccessful || response.code == 409) {
            knownDirectories.add(directoryKey)
        }
    }

    companion object {
        private const val GRAPH_DRIVE_ROOT_ENDPOINT = "https://graph.microsoft.com/v1.0/me/drive/root"
        private const val JSON_MEDIA_TYPE = "application/json; charset=utf-8"
        private const val TEXT_MEDIA_TYPE = "text/plain; charset=utf-8"

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
            isLenient = true
        }
    }
}
