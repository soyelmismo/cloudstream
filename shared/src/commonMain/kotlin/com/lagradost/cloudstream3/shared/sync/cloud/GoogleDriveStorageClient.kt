package com.lagradost.cloudstream3.shared.sync.cloud

import androidx.compose.runtime.Immutable
import com.lagradost.cloudstream3.app
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
data class GoogleDriveFile(
    val id: String = "",
    val name: String = "",
    val mimeType: String = "",
    val size: String? = null,
    val modifiedTime: String? = null,
    val trashed: Boolean = false
)

@Serializable
@Immutable
data class GoogleDriveFileList(
    val files: List<GoogleDriveFile> = emptyList(),
    val nextPageToken: String? = null
)

@Serializable
@Immutable
data class GoogleDriveCreateFolderRequest(
    val name: String,
    val mimeType: String = "application/vnd.google-apps.folder",
    val parents: List<String>? = null
)

@Serializable
@Immutable
data class GoogleDriveCreateFileRequest(
    val name: String,
    val parents: List<String>? = null
)

class GoogleDriveStorageClient(
    val accessTokenProvider: suspend () -> String?,
    val baseFolderName: String = "CloudStreamSync"
) : CloudStorageClient {
    override val providerId: String = "google_drive"
    override val displayName: String = "Google Drive"

    private val folderCache = mutableMapOf<String, String>()
    private val folderMutex = Mutex()

    override suspend fun testConnection(): Result<Boolean> = runCatching {
        val headers = getAuthHeaders()
        val url = "$DRIVE_API_URL?pageSize=1&fields=files(id)"
        val response = app.get(url, headers = headers)
        if (!response.isSuccessful) {
            throw IOException("Google Drive connection test failed: HTTP ${response.code}")
        }
        true
    }

    override suspend fun listFiles(remoteDir: String, modifiedSince: Long): Result<List<RemoteFileMetadata>> = runCatching {
        val headers = getAuthHeaders()
        val folderId = findFolderId(remoteDir, headers) ?: return@runCatching emptyList()
        val result = mutableListOf<RemoteFileMetadata>()
        listFolderRecursive(folderId, remoteDir.trim('/'), modifiedSince, headers, result)
        result
    }

    private suspend fun listFolderRecursive(
        folderId: String,
        currentRelativeDir: String,
        modifiedSince: Long,
        headers: Map<String, String>,
        result: MutableList<RemoteFileMetadata>
    ) {
        val fileList = fetchDriveFileList(folderId, headers) ?: return
        for (file in fileList.files) {
            processDriveListItem(file, currentRelativeDir, modifiedSince, headers, result)
        }
    }

    private suspend fun fetchDriveFileList(folderId: String, headers: Map<String, String>): GoogleDriveFileList? {
        val query = "'$folderId' in parents and trashed = false"
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "$DRIVE_API_URL?q=$encodedQuery&fields=files(id,name,mimeType,size,modifiedTime)&pageSize=1000&spaces=drive"
        val response = app.get(url, headers = headers)
        if (!response.isSuccessful) return null
        return runCatching { json.decodeFromString<GoogleDriveFileList>(response.text) }.getOrNull()
    }

    private suspend fun processDriveListItem(
        file: GoogleDriveFile,
        currentRelativeDir: String,
        modifiedSince: Long,
        headers: Map<String, String>,
        result: MutableList<RemoteFileMetadata>
    ) {
        if (file.mimeType == FOLDER_MIME_TYPE) {
            val subDir = resolveSubDirPath(currentRelativeDir, file.name)
            listFolderRecursive(file.id, subDir, modifiedSince, headers, result)
        } else {
            mapToFileMetadata(file, currentRelativeDir, modifiedSince)?.let { result.add(it) }
        }
    }

    private fun resolveSubDirPath(baseDir: String, name: String): String {
        return if (baseDir.isEmpty() || baseDir == ".") name else "$baseDir/$name"
    }

    override suspend fun readFile(remotePath: String): Result<String> = runCatching {
        val headers = getAuthHeaders()
        val (dirPart, fileName) = splitPath(remotePath)
        val folderId = findFolderId(dirPart, headers)
            ?: throw FileNotFoundException("Directory not found in Drive: $dirPart")
        val file = findFileInFolder(fileName, folderId, headers)
            ?: throw FileNotFoundException("File not found in Drive: $remotePath")
        val url = "$DRIVE_API_URL/${file.id}?alt=media"
        val response = app.get(url, headers = headers)
        if (!response.isSuccessful) {
            throw IOException("Failed to read Google Drive file: HTTP ${response.code}")
        }
        response.text
    }

    override suspend fun writeFile(remotePath: String, content: String): Result<Unit> = runCatching {
        val headers = getAuthHeaders()
        val (dirPart, fileName) = splitPath(remotePath)
        val folderId = getOrCreateFolder(dirPart, headers)
        val existingFile = findFileInFolder(fileName, folderId, headers)
        if (existingFile != null) {
            updateFileContent(existingFile.id, content, headers)
        } else {
            createNewFile(fileName, folderId, content, headers)
        }
    }

    override suspend fun deleteFile(remotePath: String): Result<Unit> = runCatching {
        val headers = getAuthHeaders()
        val (dirPart, fileName) = splitPath(remotePath)
        val folderId = findFolderId(dirPart, headers) ?: return@runCatching
        val file = findFileInFolder(fileName, folderId, headers) ?: return@runCatching
        val url = "$DRIVE_API_URL/${file.id}"
        val response = app.delete(url, headers = headers)
        if (!response.isSuccessful && response.code != 404) {
            throw IOException("Failed to delete Google Drive file: HTTP ${response.code}")
        }
    }

    private suspend fun getAuthHeaders(): Map<String, String> {
        val token = accessTokenProvider()
            ?: throw IllegalStateException("No Google Drive access token available")
        return mapOf("Authorization" to "Bearer $token")
    }

    private suspend fun getOrCreateBaseFolder(headers: Map<String, String>): String {
        folderCache[""]?.let { return it }
        val query = "name = '${escapeDriveQuery(baseFolderName)}' and 'root' in parents and mimeType = '$FOLDER_MIME_TYPE' and trashed = false"
        val existingId = findFileId(query, headers)
        val folderId = existingId ?: createFolderInternal(baseFolderName, listOf("root"), headers)
        folderCache[""] = folderId
        return folderId
    }

    private suspend fun resolveFolderSegment(
        segment: String,
        parentId: String,
        currentPath: String,
        headers: Map<String, String>
    ): String {
        folderCache[currentPath]?.let { return it }
        val query = "name = '${escapeDriveQuery(segment)}' and '$parentId' in parents and mimeType = '$FOLDER_MIME_TYPE' and trashed = false"
        val existingId = findFileId(query, headers)
        val folderId = existingId ?: createFolderInternal(segment, listOf(parentId), headers)
        folderCache[currentPath] = folderId
        return folderId
    }

    private suspend fun getOrCreateFolder(path: String, headers: Map<String, String>): String = folderMutex.withLock {
        val clean = path.trim('/')
        if (clean.isEmpty() || clean == ".") {
            return getOrCreateBaseFolder(headers)
        }
        var currentParent = getOrCreateBaseFolder(headers)
        var accumulatedPath = ""
        val segments = clean.split('/').filter { it.isNotEmpty() }
        for (segment in segments) {
            accumulatedPath = if (accumulatedPath.isEmpty()) segment else "$accumulatedPath/$segment"
            currentParent = resolveFolderSegment(segment, currentParent, accumulatedPath, headers)
        }
        currentParent
    }

    private suspend fun findBaseFolderId(headers: Map<String, String>): String? {
        folderCache[""]?.let { return it }
        val query = "name = '${escapeDriveQuery(baseFolderName)}' and 'root' in parents and mimeType = '$FOLDER_MIME_TYPE' and trashed = false"
        val id = findFileId(query, headers) ?: return null
        folderCache[""] = id
        return id
    }

    private suspend fun findSubfolderId(segment: String, parentId: String, headers: Map<String, String>): String? {
        val query = "name = '${escapeDriveQuery(segment)}' and '$parentId' in parents and mimeType = '$FOLDER_MIME_TYPE' and trashed = false"
        return findFileId(query, headers)
    }

    private suspend fun findFolderId(path: String, headers: Map<String, String>): String? = folderMutex.withLock {
        val clean = path.trim('/')
        folderCache[clean]?.let { return it }
        val baseId = findBaseFolderId(headers) ?: return null
        if (clean.isEmpty() || clean == ".") {
            return baseId
        }
        var currentParent = baseId
        val segments = clean.split('/').filter { it.isNotEmpty() }
        for (segment in segments) {
            currentParent = findSubfolderId(segment, currentParent, headers) ?: return null
        }
        folderCache[clean] = currentParent
        currentParent
    }

    private suspend fun createFolderInternal(
        name: String,
        parents: List<String>,
        headers: Map<String, String>
    ): String {
        val payload = json.encodeToString(GoogleDriveCreateFolderRequest(name = name, parents = parents))
        val body = payload.toRequestBody(JSON_MEDIA_TYPE.toMediaTypeOrNull())
        val response = app.post(
            DRIVE_API_URL,
            headers = headers + mapOf("Content-Type" to JSON_MEDIA_TYPE),
            requestBody = body
        )
        if (!response.isSuccessful) {
            throw IOException("Failed to create folder '$name': HTTP ${response.code}")
        }
        val created = json.decodeFromString<GoogleDriveFile>(response.text)
        return created.id
    }

    private suspend fun findFileId(query: String, headers: Map<String, String>): String? {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "$DRIVE_API_URL?q=$encodedQuery&fields=files(id)&spaces=drive&pageSize=1"
        val response = app.get(url, headers = headers)
        if (!response.isSuccessful) return null
        val result = runCatching { json.decodeFromString<GoogleDriveFileList>(response.text) }.getOrNull()
        return result?.files?.firstOrNull()?.id
    }

    private suspend fun findFileInFolder(
        name: String,
        folderId: String,
        headers: Map<String, String>
    ): GoogleDriveFile? {
        val query = "name = '${escapeDriveQuery(name)}' and '$folderId' in parents and trashed = false"
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "$DRIVE_API_URL?q=$encodedQuery&fields=files(id,name,mimeType,size,modifiedTime)&spaces=drive&pageSize=1"
        val response = app.get(url, headers = headers)
        if (!response.isSuccessful) return null
        val result = runCatching { json.decodeFromString<GoogleDriveFileList>(response.text) }.getOrNull()
        return result?.files?.firstOrNull()
    }

    private suspend fun updateFileContent(fileId: String, content: String, headers: Map<String, String>) {
        val url = "$DRIVE_UPLOAD_URL/$fileId?uploadType=media"
        val body = content.toRequestBody(TEXT_MEDIA_TYPE.toMediaTypeOrNull())
        val response = app.patch(
            url,
            headers = headers + mapOf("Content-Type" to TEXT_MEDIA_TYPE),
            requestBody = body
        )
        if (!response.isSuccessful) {
            throw IOException("Failed to update Google Drive file: HTTP ${response.code}")
        }
    }

    private suspend fun createNewFile(
        fileName: String,
        folderId: String,
        content: String,
        headers: Map<String, String>
    ) {
        val payload = json.encodeToString(GoogleDriveCreateFileRequest(name = fileName, parents = listOf(folderId)))
        val body = payload.toRequestBody(JSON_MEDIA_TYPE.toMediaTypeOrNull())
        val response = app.post(
            DRIVE_API_URL,
            headers = headers + mapOf("Content-Type" to JSON_MEDIA_TYPE),
            requestBody = body
        )
        if (!response.isSuccessful) {
            throw IOException("Failed to create Google Drive file metadata: HTTP ${response.code}")
        }
        val created = json.decodeFromString<GoogleDriveFile>(response.text)
        updateFileContent(created.id, content, headers)
    }

    private fun escapeDriveQuery(value: String): String = value.replace("\\", "\\\\").replace("'", "\\'")

    private fun splitPath(remotePath: String): Pair<String, String> {
        val clean = remotePath.trim('/')
        val lastSlash = clean.lastIndexOf('/')
        return if (lastSlash >= 0) {
            clean.substring(0, lastSlash) to clean.substring(lastSlash + 1)
        } else {
            "" to clean
        }
    }

    private fun mapToFileMetadata(
        file: GoogleDriveFile,
        remoteDir: String,
        modifiedSince: Long
    ): RemoteFileMetadata? {
        val modifiedTime = parseIsoDate(file.modifiedTime)
        if (modifiedSince > 0L && modifiedTime < modifiedSince) {
            return null
        }
        val isDir = file.mimeType == FOLDER_MIME_TYPE
        val cleanDir = remoteDir.trim('/')
        val path = if (cleanDir.isEmpty() || cleanDir == ".") file.name else "$cleanDir/${file.name}"
        return RemoteFileMetadata(
            path = path,
            name = file.name,
            size = file.size?.toLongOrNull() ?: 0L,
            modifiedTime = modifiedTime,
            isDirectory = isDir
        )
    }

    private fun parseIsoDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L
        return runCatching { kotlinx.datetime.Instant.parse(dateStr).toEpochMilliseconds() }.getOrDefault(0L)
    }

    companion object {
        private const val DRIVE_API_URL = "https://www.googleapis.com/drive/v3/files"
        private const val DRIVE_UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"
        private const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
        private const val JSON_MEDIA_TYPE = "application/json; charset=utf-8"
        private const val TEXT_MEDIA_TYPE = "text/plain; charset=utf-8"

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = true
        }
    }
}
