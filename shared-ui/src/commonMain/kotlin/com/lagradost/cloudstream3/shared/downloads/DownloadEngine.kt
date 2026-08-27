package com.lagradost.cloudstream3.shared.downloads

import com.lagradost.cloudstream3.TvType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/**
 * Status of a download item during its lifecycle.
 */
enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

/**
 * Metadata representing a single media item enqueued for background download.
 */
@Serializable
data class DownloadQueueItem(
    val id: String,
    val parentId: Int,
    val episodeId: Int,
    val url: String,
    val headerName: String,
    val episodeName: String? = null,
    val episodeIndex: Int? = null,
    val seasonIndex: Int? = null,
    val posterUrl: String? = null,
    val type: TvType = TvType.Movie,
    val apiName: String = "",
    val sourceUrl: String = "",
    val headers: Map<String, String> = emptyMap(),
    val videoQuality: String? = null,
    val description: String? = null,
    val destinationPath: String? = null
)

/**
 * Real-time progress update for an active or completed download.
 */
@Serializable
data class DownloadProgress(
    val id: String,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val progress: Float = 0f, // 0.0f to 1.0f
    val speedBytesPerSec: Long = 0L,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val errorMessage: String? = null,
    val etaSeconds: Long? = null,
    val filePath: String? = null
) {
    val progressPercentage: Int
        get() = (progress.coerceIn(0f, 1f) * 100).toInt()

    val isDone: Boolean
        get() = status == DownloadStatus.COMPLETED || status == DownloadStatus.FAILED || status == DownloadStatus.CANCELLED
}

/**
 * Core Cross-Platform Background Download Engine Interface for Kotlin Multiplatform.
 */
interface DownloadEngine {
    val queueFlow: StateFlow<List<DownloadQueueItem>>
    val progressFlow: StateFlow<Map<String, DownloadProgress>>

    /**
     * Enqueues and starts downloading the given item.
     */
    suspend fun startDownload(item: DownloadQueueItem)

    /**
     * Pauses an ongoing download.
     */
    suspend fun pauseDownload(id: String)

    /**
     * Resumes a paused or failed download.
     */
    suspend fun resumeDownload(id: String)

    /**
     * Cancels an active or queued download and cleans up partial files.
     */
    suspend fun cancelDownload(id: String)

    /**
     * Returns a dedicated [Flow] of [DownloadProgress] for a specific download ID.
     */
    fun getDownloadProgressFlow(id: String): Flow<DownloadProgress>

    /**
     * Pauses all ongoing downloads.
     */
    suspend fun pauseAll()

    /**
     * Resumes all paused downloads.
     */
    suspend fun resumeAll()

    /**
     * Cancels all active and queued downloads.
     */
    suspend fun cancelAll()

    /**
     * Retries a failed download item.
     */
    suspend fun retryDownload(id: String)
}
