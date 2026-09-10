package com.lagradost.cloudstream3.shared.sync.cloud

import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

class LocalStorageClient(
    val rootDirectoryPath: String
) : CloudStorageClient {
    override val providerId: String = "local_storage"
    override val displayName: String = "Local Storage"

    private val rootDir = File(rootDirectoryPath)

    override suspend fun testConnection(): Result<Boolean> = runCatching {
        if (!rootDir.exists()) {
            rootDir.mkdirs()
        }
        if (!rootDir.exists() || !rootDir.isDirectory) {
            throw IOException("Root path is not a valid directory: $rootDirectoryPath")
        }
        if (!rootDir.canWrite()) {
            throw IOException("Cannot write to root directory: $rootDirectoryPath")
        }
        true
    }

    override suspend fun listFiles(remoteDir: String, modifiedSince: Long): Result<List<RemoteFileMetadata>> = runCatching {
        val targetDir = resolvePath(remoteDir)
        if (!targetDir.exists() || !targetDir.isDirectory) {
            return@runCatching emptyList()
        }
        targetDir.walkTopDown()
            .filter { it != targetDir }
            .mapNotNull { file ->
                mapToFileMetadata(file, modifiedSince)
            }
            .toList()
    }

    override suspend fun readFile(remotePath: String): Result<String> = runCatching {
        val file = resolvePath(remotePath)
        if (!file.exists() || file.isDirectory) {
            throw FileNotFoundException("File not found: $remotePath")
        }
        file.readText(Charsets.UTF_8)
    }

    override suspend fun writeFile(remotePath: String, content: String): Result<Unit> = runCatching {
        val file = resolvePath(remotePath)
        val parent = file.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        file.writeText(content, Charsets.UTF_8)
    }

    override suspend fun deleteFile(remotePath: String): Result<Unit> = runCatching {
        val file = resolvePath(remotePath)
        if (!file.exists()) return@runCatching
        val deleted = if (file.isDirectory) file.deleteRecursively() else file.delete()
        if (!deleted && file.exists()) {
            throw IOException("Failed to delete file: $remotePath")
        }
    }

    private fun resolvePath(relativePath: String): File {
        val clean = relativePath.trim('/').removePrefix("./")
        return if (clean.isEmpty() || clean == ".") {
            rootDir
        } else {
            File(rootDir, clean)
        }
    }

    private fun mapToFileMetadata(file: File, modifiedSince: Long): RemoteFileMetadata? {
        val lastModified = file.lastModified()
        if (modifiedSince > 0L && lastModified < modifiedSince) {
            return null
        }
        val isDir = file.isDirectory
        val relativePath = file.relativeTo(rootDir).path.replace('\\', '/')
        return RemoteFileMetadata(
            path = relativePath,
            name = file.name,
            size = if (isDir) 0L else file.length(),
            modifiedTime = lastModified,
            isDirectory = isDir
        )
    }
}
