package com.lagradost.cloudstream3.shared.downloads

import com.lagradost.cloudstream3.network.buildSharedOkHttpClient
import com.lagradost.cloudstream3.shared.persistence.dao.DownloadCacheDao
import com.lagradost.cloudstream3.shared.persistence.entity.DownloadEpisodeEntity
import com.lagradost.cloudstream3.shared.persistence.entity.DownloadHeaderEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Production Kotlin Multiplatform Background Download Engine Implementation.
 *
 * Supports:
 * - Direct-to-disk chunk streaming with temporary `.part` file handling.
 * - HTTP Range header resumption (RFC 7233).
 * - Multi-item concurrency control via [Semaphore].
 * - Real-time speed and ETA calculation with smoothed time windows.
 * - Automatic persistence of completed show & episode metadata into Room [DownloadCacheDao].
 * - Reactive state streams for queue and per-item download progress.
 */
class KmpDownloadEngineImpl(
    private val downloadCacheDao: DownloadCacheDao? = null,
    private val directoryProvider: DownloadDirectoryProvider = DefaultDownloadDirectoryProvider(),
    private val okHttpClient: OkHttpClient = buildSharedOkHttpClient(),
    private val maxConcurrentDownloads: Int = 2,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : DownloadEngine {

    private val _queueFlow = MutableStateFlow<List<DownloadQueueItem>>(emptyList())
    override val queueFlow: StateFlow<List<DownloadQueueItem>> = _queueFlow.asStateFlow()

    private val _progressFlow = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    override val progressFlow: StateFlow<Map<String, DownloadProgress>> = _progressFlow.asStateFlow()

    private val mutex = Mutex()
    private val semaphore = Semaphore(maxConcurrentDownloads.coerceAtLeast(1))
    private val downloadJobs = mutableMapOf<String, Job>()
    private val pausedIds = mutableSetOf<String>()
    private val cancelledIds = mutableSetOf<String>()

    override suspend fun startDownload(item: DownloadQueueItem) {
        mutex.withLock {
            if (!_queueFlow.value.any { it.id == item.id }) {
                _queueFlow.update { it + item }
            }
            cancelledIds.remove(item.id)
            pausedIds.remove(item.id)
            _progressFlow.update { current ->
                val existing = current[item.id]
                current + (item.id to (existing?.copy(status = DownloadStatus.QUEUED, errorMessage = null)
                    ?: DownloadProgress(id = item.id, status = DownloadStatus.QUEUED)))
            }
        }
        scheduleNextDownloads()
    }

    override suspend fun pauseDownload(id: String) {
        mutex.withLock {
            pausedIds.add(id)
            downloadJobs[id]?.cancel()
            downloadJobs.remove(id)
            _progressFlow.update { current ->
                val existing = current[id] ?: return@update current
                current + (id to existing.copy(status = DownloadStatus.PAUSED, speedBytesPerSec = 0L))
            }
        }
        scheduleNextDownloads()
    }

    override suspend fun resumeDownload(id: String) {
        mutex.withLock {
            pausedIds.remove(id)
            cancelledIds.remove(id)
            _progressFlow.update { current ->
                val existing = current[id] ?: return@update current
                current + (id to existing.copy(status = DownloadStatus.QUEUED, errorMessage = null))
            }
        }
        scheduleNextDownloads()
    }

    override suspend fun cancelDownload(id: String) {
        val item = mutex.withLock {
            cancelledIds.add(id)
            pausedIds.remove(id)
            downloadJobs[id]?.cancel()
            downloadJobs.remove(id)
            val found = _queueFlow.value.firstOrNull { it.id == id }
            _queueFlow.update { list -> list.filterNot { it.id == id } }
            _progressFlow.update { current -> current - id }
            found
        }
        if (item != null) {
            cleanUpPartFile(item)
        }
        scheduleNextDownloads()
    }

    override fun getDownloadProgressFlow(id: String): Flow<DownloadProgress> {
        return progressFlow.mapNotNull { it[id] }
    }

    private fun updateItemsStatus(
        predicate: (DownloadStatus) -> Boolean,
        newStatus: DownloadStatus,
        speedBytesPerSec: Long = 0L,
        clearError: Boolean = false
    ) {
        _progressFlow.update { current ->
            current.mapValues { (_, progress) ->
                if (predicate(progress.status)) {
                    progress.copy(
                        status = newStatus,
                        speedBytesPerSec = speedBytesPerSec,
                        errorMessage = if (clearError) null else progress.errorMessage
                    )
                } else progress
            }
        }
    }

    override suspend fun pauseAll() {
        mutex.withLock {
            _queueFlow.value.forEach { item ->
                pausedIds.add(item.id)
                downloadJobs[item.id]?.cancel()
                downloadJobs.remove(item.id)
            }
            updateItemsStatus(
                predicate = { it == DownloadStatus.DOWNLOADING || it == DownloadStatus.QUEUED },
                newStatus = DownloadStatus.PAUSED,
                speedBytesPerSec = 0L
            )
        }
    }

    override suspend fun resumeAll() {
        mutex.withLock {
            pausedIds.clear()
            cancelledIds.clear()
            updateItemsStatus(
                predicate = { it == DownloadStatus.PAUSED || it == DownloadStatus.FAILED },
                newStatus = DownloadStatus.QUEUED,
                clearError = true
            )
        }
        scheduleNextDownloads()
    }

    override suspend fun cancelAll() {
        val items = mutex.withLock {
            _queueFlow.value.forEach { item ->
                cancelledIds.add(item.id)
                downloadJobs[item.id]?.cancel()
            }
            downloadJobs.clear()
            val allItems = _queueFlow.value
            _queueFlow.value = emptyList()
            _progressFlow.value = emptyMap()
            pausedIds.clear()
            allItems
        }
        items.forEach { cleanUpPartFile(it) }
    }

    override suspend fun retryDownload(id: String) {
        resumeDownload(id)
    }

    private fun scheduleNextDownloads() {
        scope.launch {
            val itemsToStart = mutex.withLock {
                val currentProgresses = _progressFlow.value
                _queueFlow.value.filter { item ->
                    !pausedIds.contains(item.id) &&
                    !cancelledIds.contains(item.id) &&
                    !downloadJobs.containsKey(item.id) &&
                    (currentProgresses[item.id]?.status == DownloadStatus.QUEUED || currentProgresses[item.id]?.status == null)
                }
            }

            for (item in itemsToStart) {
                val job = scope.launch {
                    try {
                        semaphore.withPermit {
                            val shouldRun = mutex.withLock {
                                !pausedIds.contains(item.id) && !cancelledIds.contains(item.id)
                            }
                            if (shouldRun) {
                                executeDownload(item)
                            }
                        }
                    } finally {
                        mutex.withLock {
                            downloadJobs.remove(item.id)
                        }
                        scheduleNextDownloads()
                    }
                }

                mutex.withLock {
                    if (!pausedIds.contains(item.id) && !cancelledIds.contains(item.id)) {
                        downloadJobs[item.id] = job
                    } else {
                        job.cancel()
                    }
                }
            }
        }
    }

    private fun resolveDestinationFile(item: DownloadQueueItem, isPart: Boolean = false): File {
        val baseFile = if (item.destinationPath != null) {
            File(item.destinationPath)
        } else {
            directoryProvider.getEpisodeFile(item.parentId, item.episodeId)
        }
        return if (isPart) File("${baseFile.absolutePath}.part") else baseFile
    }

    private suspend fun executeDownload(item: DownloadQueueItem) {
        val targetFile = resolveDestinationFile(item, isPart = false)
        val partFile = resolveDestinationFile(item, isPart = true)
        targetFile.parentFile?.mkdirs()

        _progressFlow.update { current ->
            val existing = current[item.id] ?: DownloadProgress(id = item.id)
            current + (item.id to existing.copy(status = DownloadStatus.DOWNLOADING, errorMessage = null))
        }

        try {
            var downloadedBytes = if (partFile.exists()) partFile.length() else 0L

            val requestBuilder = Request.Builder()
                .url(item.url)
                .addHeader("User-Agent", com.lagradost.cloudstream3.USER_AGENT)

            item.headers.forEach { (k, v) ->
                requestBuilder.header(k, v)
            }

            if (downloadedBytes > 0L) {
                requestBuilder.header("Range", "bytes=$downloadedBytes-")
            }

            val request = requestBuilder.build()
            val response: Response = withContext(Dispatchers.IO) {
                okHttpClient.newCall(request).execute()
            }

            if (!response.isSuccessful && response.code != 206) {
                if (response.code == 416 && downloadedBytes > 0L) {
                    response.close()
                    downloadedBytes = 0L
                    if (partFile.exists()) partFile.delete()
                    val freshRequest = requestBuilder.removeHeader("Range").build()
                    val freshResponse = withContext(Dispatchers.IO) {
                        okHttpClient.newCall(freshRequest).execute()
                    }
                    if (!freshResponse.isSuccessful) {
                        freshResponse.close()
                        throw IOException("HTTP ${freshResponse.code}: ${freshResponse.message}")
                    }
                    streamResponseBody(item, freshResponse, partFile, targetFile, 0L, 0L, false)
                } else {
                    val code = response.code
                    val msg = response.message
                    response.close()
                    throw IOException("HTTP $code: $msg")
                }
            } else {
                val isPartial = response.code == 206
                val appendMode = isPartial && downloadedBytes > 0L
                if (!isPartial) {
                    downloadedBytes = 0L
                }
                val responseBody = response.body
                val contentLength = responseBody.contentLength()
                val totalBytes = if (isPartial && contentLength > 0L) {
                    downloadedBytes + contentLength
                } else if (contentLength > 0L) {
                    contentLength
                } else 0L

                streamResponseBody(item, response, partFile, targetFile, downloadedBytes, totalBytes, appendMode)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            mutex.withLock {
                if (!cancelledIds.contains(item.id)) {
                    _progressFlow.update { current ->
                        val existing = current[item.id] ?: DownloadProgress(id = item.id)
                        current + (item.id to existing.copy(
                            status = DownloadStatus.FAILED,
                            errorMessage = e.message ?: "Download failed",
                            speedBytesPerSec = 0L
                        ))
                    }
                }
            }
        }
    }

    private suspend fun streamResponseBody(
        item: DownloadQueueItem,
        response: Response,
        partFile: File,
        targetFile: File,
        initialDownloaded: Long,
        initialTotal: Long,
        append: Boolean
    ) {
        var downloadedBytes = initialDownloaded
        val responseBody = response.body
        val contentLength = responseBody.contentLength()
        val totalBytes = if (initialTotal > 0L) initialTotal else if (contentLength > 0L) initialDownloaded + contentLength else 0L

        try {
            withContext(Dispatchers.IO) {
                responseBody.byteStream().use { input ->
                    FileOutputStream(partFile, append).use { output ->
                        val buffer = ByteArray(65536) // 64 KB
                        var bytesRead: Int
                        var lastUpdateTime = System.currentTimeMillis()
                        var bytesSinceLastUpdate = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            coroutineContext.ensureActive()

                            if (pausedIds.contains(item.id) || cancelledIds.contains(item.id)) {
                                throw CancellationException("Download paused or cancelled")
                            }

                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            bytesSinceLastUpdate += bytesRead

                            val now = System.currentTimeMillis()
                            val elapsed = now - lastUpdateTime
                            if (elapsed >= 400L) {
                                val speed = (bytesSinceLastUpdate * 1000L) / elapsed.coerceAtLeast(1L)
                                val progress = if (totalBytes > 0L) (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
                                val eta = if (speed > 0L && totalBytes > downloadedBytes) (totalBytes - downloadedBytes) / speed else null

                                _progressFlow.update { current ->
                                    val existing = current[item.id] ?: DownloadProgress(id = item.id)
                                    current + (item.id to existing.copy(
                                        bytesDownloaded = downloadedBytes,
                                        totalBytes = totalBytes,
                                        progress = progress,
                                        speedBytesPerSec = speed,
                                        etaSeconds = eta,
                                        status = DownloadStatus.DOWNLOADING,
                                        errorMessage = null
                                    ))
                                }
                                lastUpdateTime = now
                                bytesSinceLastUpdate = 0L
                            }
                        }
                        output.flush()
                    }
                }
            }

            // Rename .part file to final file
            if (partFile.exists()) {
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                partFile.renameTo(targetFile)
            }

            val finalTotal = if (totalBytes > 0L) totalBytes else downloadedBytes
            _progressFlow.update { current ->
                val existing = current[item.id] ?: DownloadProgress(id = item.id)
                current + (item.id to existing.copy(
                    bytesDownloaded = finalTotal,
                    totalBytes = finalTotal,
                    progress = 1.0f,
                    speedBytesPerSec = 0L,
                    etaSeconds = 0L,
                    status = DownloadStatus.COMPLETED,
                    errorMessage = null,
                    filePath = targetFile.absolutePath
                ))
            }

            // Automatically persist completed metadata to Room database
            downloadCacheDao?.let { dao ->
                try {
                    dao.upsertHeader(
                        DownloadHeaderEntity(
                            id = item.parentId,
                            apiName = item.apiName,
                            url = item.sourceUrl.ifBlank { item.url },
                            type = item.type,
                            name = item.headerName,
                            poster = item.posterUrl,
                            cacheTime = System.currentTimeMillis()
                        )
                    )

                    dao.upsertEpisode(
                        DownloadEpisodeEntity(
                            id = item.episodeId,
                            parentId = item.parentId,
                            name = item.episodeName,
                            poster = item.posterUrl,
                            episode = item.episodeIndex ?: 1,
                            season = item.seasonIndex,
                            score = null,
                            description = item.description,
                            cacheTime = System.currentTimeMillis()
                        )
                    )
                } catch (_: Throwable) {}
            }
        } finally {
            response.close()
        }
    }

    private fun cleanUpPartFile(item: DownloadQueueItem) {
        try {
            val partFile = resolveDestinationFile(item, isPart = true)
            if (partFile.exists()) {
                partFile.delete()
            }
        } catch (_: Throwable) {}
    }
}
