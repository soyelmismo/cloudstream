package com.lagradost.cloudstream3.shared.downloads

import java.io.File

/**
 * Encapsulates device storage partition metrics.
 */
data class DiskStorageMetrics(
    val totalBytes: Long = 0L,
    val freeBytes: Long = 0L,
    val usedBytes: Long = 0L
)

/**
 * Provides directory paths and file resolvers for media downloads across platforms.
 */
interface DownloadDirectoryProvider {
    fun getDownloadsDirectory(): File

    /**
     * Resolves an episode destination or temporary part file, ensuring the parent directory exists.
     */
    fun resolveEpisodeFile(parentId: Int, episodeId: Int, extension: String = "mp4", isPart: Boolean = false): File {
        val parentDir = File(getDownloadsDirectory(), parentId.toString())
        if (!parentDir.exists()) {
            parentDir.mkdirs()
        }
        val fileName = if (isPart) "$episodeId.$extension.part" else "$episodeId.$extension"
        return File(parentDir, fileName)
    }

    fun getEpisodeFile(parentId: Int, episodeId: Int, extension: String = "mp4"): File {
        return resolveEpisodeFile(parentId, episodeId, extension, isPart = false)
    }

    fun getEpisodePartFile(parentId: Int, episodeId: Int, extension: String = "mp4"): File {
        return resolveEpisodeFile(parentId, episodeId, extension, isPart = true)
    }

    fun getParentDirectory(parentId: Int): File {
        return File(getDownloadsDirectory(), parentId.toString())
    }

    fun getStorageMetrics(): DiskStorageMetrics {
        val dir = getDownloadsDirectory()
        val total = dir.totalSpace
        val free = dir.usableSpace
        val used = if (total > free) total - free else 0L
        return DiskStorageMetrics(
            totalBytes = total,
            freeBytes = free,
            usedBytes = used
        )
    }

    fun calculateDirectorySizeBytes(): Long {
        val dir = getDownloadsDirectory()
        if (!dir.exists()) return 0L
        return try {
            dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } catch (_: Throwable) {
            0L
        }
    }
}

/**
 * Default directory provider creating and utilizing standard application download storage.
 */
class DefaultDownloadDirectoryProvider(
    private val customBaseDir: File? = null
) : DownloadDirectoryProvider {
    override fun getDownloadsDirectory(): File {
        if (customBaseDir != null) {
            if (!customBaseDir.exists()) customBaseDir.mkdirs()
            return customBaseDir
        }
        val userHome = System.getProperty("user.home") ?: "."
        val dir = File(userHome, ".cloudstream/downloads")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
}
