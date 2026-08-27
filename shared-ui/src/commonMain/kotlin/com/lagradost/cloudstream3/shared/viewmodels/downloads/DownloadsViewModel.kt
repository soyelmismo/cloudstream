package com.lagradost.cloudstream3.shared.viewmodels.downloads

import cloudstream.shared_ui.generated.resources.*
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.shared.downloads.DefaultDownloadDirectoryProvider
import com.lagradost.cloudstream3.shared.downloads.DownloadDirectoryProvider
import com.lagradost.cloudstream3.shared.downloads.DownloadEngine
import com.lagradost.cloudstream3.shared.downloads.DownloadProgress
import com.lagradost.cloudstream3.shared.downloads.DownloadQueueItem
import com.lagradost.cloudstream3.shared.downloads.DownloadStatus
import com.lagradost.cloudstream3.shared.downloads.KmpDownloadEngineImpl
import com.lagradost.cloudstream3.shared.mvi.MviViewModel
import com.lagradost.cloudstream3.shared.mvi.UiEffect
import com.lagradost.cloudstream3.shared.mvi.UiEvent
import com.lagradost.cloudstream3.shared.mvi.UiState
import com.lagradost.cloudstream3.shared.persistence.dao.DownloadCacheDao
import com.lagradost.cloudstream3.shared.persistence.entity.DownloadEpisodeEntity
import com.lagradost.cloudstream3.shared.persistence.entity.DownloadHeaderEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import kotlin.coroutines.CoroutineContext

/**
 * Tab selection for Downloads screen.
 */
enum class DownloadsTab {
    DOWNLOADING,
    COMPLETED
}

/**
 * Lifecycle state of an active download queue item.
 */
enum class DownloadItemStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    ERROR,
    COMPLETED
}

/**
 * Represents an in-flight or queued download.
 */
data class ActiveDownloadItem(
    val id: Int,
    val parentId: Int? = null,
    val title: String,
    val headerName: String,
    val episodeName: String? = null,
    val episodeIndex: Int? = null,
    val seasonIndex: Int? = null,
    val progress: Float = 0f, // 0.0f to 1.0f
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val status: DownloadItemStatus = DownloadItemStatus.QUEUED,
    val errorMessage: String? = null,
    val posterUrl: String? = null,
    val sourceUrl: String? = null,
    val videoQuality: String? = null,
    val etaSeconds: Long? = null
) {
    val progressPercentage: Int
        get() = (progress.coerceIn(0f, 1f) * 100).toInt()

    val formattedSpeed: String
        get() = formatByteRate(speedBytesPerSec)

    val formattedDownloaded: String
        get() = formatBytes(bytesDownloaded)

    val formattedTotal: String
        get() = formatBytes(totalBytes)

    val formattedProgressText: String
        get() = if (totalBytes > 0) "$formattedDownloaded / $formattedTotal" else formattedDownloaded
}

/**
 * Grouped representation of completed downloads for a show or movie header.
 */
data class CompletedHeaderGroup(
    val header: DownloadHeaderEntity,
    val episodes: List<DownloadEpisodeEntity> = emptyList(),
    val totalEstimatedSizeBytes: Long = 0L
) {
    val episodeCount: Int
        get() = episodes.size

    val isMovie: Boolean
        get() = header.type == TvType.Movie || header.type == TvType.AnimeMovie

    val formattedTotalSize: String
        get() = formatBytes(totalEstimatedSizeBytes)
}

/**
 * Storage device capacity and app allocation information.
 */
data class StorageUsageInfo(
    val appBytes: Long = 0L,
    val usedBytes: Long = 0L,
    val freeBytes: Long = 0L,
    val totalBytes: Long = 0L
) {
    val appFraction: Float
        get() = if (totalBytes > 0) (appBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f

    val usedFraction: Float
        get() = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f

    val freeFraction: Float
        get() = if (totalBytes > 0) (freeBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f

    val formattedAppSize: String
        get() = formatBytes(appBytes)

    val formattedFreeSize: String
        get() = formatBytes(freeBytes)

    val formattedUsedSize: String
        get() = formatBytes(usedBytes)

    val formattedTotalSize: String
        get() = formatBytes(totalBytes)
}

/**
 * Immutable UI State for the Downloads Screen.
 */
data class DownloadsState(
    val selectedTab: DownloadsTab = DownloadsTab.DOWNLOADING,
    val activeDownloads: List<ActiveDownloadItem> = emptyList(),
    val completedGroups: List<CompletedHeaderGroup> = emptyList(),
    val storageUsage: StorageUsageInfo = StorageUsageInfo(),
    val searchQuery: String = "",
    val expandedHeaderIds: Set<Int> = emptySet(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) : UiState {
    val totalActiveDownloadsCount: Int
        get() = activeDownloads.count { it.status == DownloadItemStatus.DOWNLOADING || it.status == DownloadItemStatus.QUEUED }

    val totalActiveSpeedBytesPerSec: Long
        get() = activeDownloads.filter { it.status == DownloadItemStatus.DOWNLOADING }.sumOf { it.speedBytesPerSec }

    val formattedTotalSpeed: String
        get() = formatByteRate(totalActiveSpeedBytesPerSec)

    val filteredCompletedGroups: List<CompletedHeaderGroup>
        get() {
            if (searchQuery.isBlank()) return completedGroups
            val query = searchQuery.trim().lowercase()
            return completedGroups.mapNotNull { group ->
                val matchesHeader = group.header.name.lowercase().contains(query)
                val matchingEpisodes = group.episodes.filter { ep ->
                    matchesHeader || (ep.name?.lowercase()?.contains(query) == true) || "ep ${ep.episode}".contains(query)
                }
                if (matchesHeader || matchingEpisodes.isNotEmpty()) {
                    group.copy(episodes = if (matchesHeader) group.episodes else matchingEpisodes)
                } else null
            }
        }
}

/**
 * UI Events / Intents for Downloads.
 */
sealed interface DownloadsEvent : UiEvent {
    data class SwitchTab(val tab: DownloadsTab) : DownloadsEvent
    data class PauseDownload(val id: Int) : DownloadsEvent
    data class ResumeDownload(val id: Int) : DownloadsEvent
    data class CancelDownload(val id: Int) : DownloadsEvent
    data class RetryDownload(val id: Int) : DownloadsEvent
    data object PauseAll : DownloadsEvent
    data object ResumeAll : DownloadsEvent
    data object CancelAll : DownloadsEvent
    data class DeleteCompletedHeader(val id: Int) : DownloadsEvent
    data class DeleteCompletedEpisode(val id: Int, val parentId: Int) : DownloadsEvent
    data class PlayOffline(val episode: DownloadEpisodeEntity, val header: DownloadHeaderEntity?) : DownloadsEvent
    data class ToggleHeaderExpanded(val headerId: Int) : DownloadsEvent
    data class SearchQueryChanged(val query: String) : DownloadsEvent
    data object Refresh : DownloadsEvent
    data object ClearError : DownloadsEvent
}

/**
 * Single-shot UI side effects for navigation and user notifications.
 */
sealed interface DownloadsEffect : UiEffect {
    data class NavigateToPlayer(
        val title: String? = null,
        val titleRes: StringResource? = null,
        val url: String,
        val episodeIndex: Int? = null,
        val seasonIndex: Int? = null,
        val headerId: Int? = null,
        val episodeId: Int? = null
    ) : DownloadsEffect

    data object NavigateToExplore : DownloadsEffect
    data class ShowMessage(val message: String) : DownloadsEffect
    data class ShowMessageRes(val messageRes: StringResource) : DownloadsEffect
}

/**
 * Legacy cross-platform Download Service Interface.
 */
interface IDownloadService {
    val activeDownloadsFlow: StateFlow<List<ActiveDownloadItem>>
    fun pauseDownload(id: Int)
    fun resumeDownload(id: Int)
    fun cancelDownload(id: Int)
    fun retryDownload(id: Int)
    fun pauseAll()
    fun resumeAll()
    fun cancelAll()
    fun enqueueDownload(item: ActiveDownloadItem)
}

/**
 * Adapts an [IDownloadService] into a [DownloadEngine].
 */
class DownloadServiceEngineAdapter(
    private val downloadService: IDownloadService,
    coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.Default
) : DownloadEngine {
    private val scope = CoroutineScope(coroutineContext)

    override val queueFlow: StateFlow<List<DownloadQueueItem>> =
        downloadService.activeDownloadsFlow
            .map { list -> list.map { it.toQueueItem() } }
            .stateIn(scope, SharingStarted.Eagerly, downloadService.activeDownloadsFlow.value.map { it.toQueueItem() })

    override val progressFlow: StateFlow<Map<String, DownloadProgress>> =
        downloadService.activeDownloadsFlow
            .map { list ->
                list.associate { item ->
                    item.id.toString() to DownloadProgress(
                        id = item.id.toString(),
                        bytesDownloaded = item.bytesDownloaded,
                        totalBytes = item.totalBytes,
                        progress = item.progress,
                        speedBytesPerSec = item.speedBytesPerSec,
                        status = when (item.status) {
                            DownloadItemStatus.QUEUED -> DownloadStatus.QUEUED
                            DownloadItemStatus.DOWNLOADING -> DownloadStatus.DOWNLOADING
                            DownloadItemStatus.PAUSED -> DownloadStatus.PAUSED
                            DownloadItemStatus.COMPLETED -> DownloadStatus.COMPLETED
                            DownloadItemStatus.ERROR -> DownloadStatus.FAILED
                        },
                        errorMessage = item.errorMessage,
                        etaSeconds = item.etaSeconds
                    )
                }
            }
            .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    override suspend fun startDownload(item: DownloadQueueItem) {
        downloadService.enqueueDownload(item.toActiveDownloadItem())
    }

    override suspend fun pauseDownload(id: String) {
        id.toIntOrNull()?.let { downloadService.pauseDownload(it) }
    }

    override suspend fun resumeDownload(id: String) {
        id.toIntOrNull()?.let { downloadService.resumeDownload(it) }
    }

    override suspend fun cancelDownload(id: String) {
        id.toIntOrNull()?.let { downloadService.cancelDownload(it) }
    }

    override fun getDownloadProgressFlow(id: String): Flow<DownloadProgress> {
        return progressFlow.mapNotNull { it[id] }
    }

    override suspend fun pauseAll() {
        downloadService.pauseAll()
    }

    override suspend fun resumeAll() {
        downloadService.resumeAll()
    }

    override suspend fun cancelAll() {
        downloadService.cancelAll()
    }

    override suspend fun retryDownload(id: String) {
        id.toIntOrNull()?.let { downloadService.retryDownload(it) }
    }
}

/**
 * Pure Kotlin Multiplatform in-memory reactive download service.
 */
class DefaultDownloadService : IDownloadService {
    private val _activeDownloads = MutableStateFlow<List<ActiveDownloadItem>>(emptyList())
    override val activeDownloadsFlow: StateFlow<List<ActiveDownloadItem>> = _activeDownloads.asStateFlow()

    override fun pauseDownload(id: Int) {
        _activeDownloads.update { list ->
            list.map { item ->
                if (item.id == id && item.status == DownloadItemStatus.DOWNLOADING) {
                    item.copy(status = DownloadItemStatus.PAUSED, speedBytesPerSec = 0L)
                } else item
            }
        }
    }

    override fun resumeDownload(id: Int) {
        _activeDownloads.update { list ->
            list.map { item ->
                if (item.id == id && item.status == DownloadItemStatus.PAUSED) {
                    item.copy(status = DownloadItemStatus.DOWNLOADING)
                } else item
            }
        }
    }

    override fun cancelDownload(id: Int) {
        _activeDownloads.update { list ->
            list.filterNot { it.id == id }
        }
    }

    override fun retryDownload(id: Int) {
        _activeDownloads.update { list ->
            list.map { item ->
                if (item.id == id) {
                    item.copy(status = DownloadItemStatus.QUEUED, errorMessage = null)
                } else item
            }
        }
    }

    override fun pauseAll() {
        _activeDownloads.update { list ->
            list.map { item ->
                if (item.status == DownloadItemStatus.DOWNLOADING) {
                    item.copy(status = DownloadItemStatus.PAUSED, speedBytesPerSec = 0L)
                } else item
            }
        }
    }

    override fun resumeAll() {
        _activeDownloads.update { list ->
            list.map { item ->
                if (item.status == DownloadItemStatus.PAUSED) {
                    item.copy(status = DownloadItemStatus.DOWNLOADING)
                } else item
            }
        }
    }

    override fun cancelAll() {
        _activeDownloads.value = emptyList()
    }

    override fun enqueueDownload(item: ActiveDownloadItem) {
        _activeDownloads.update { current ->
            if (current.any { it.id == item.id }) {
                current.map { if (it.id == item.id) item else it }
            } else {
                current + item
            }
        }
    }

    fun updateProgress(id: Int, bytesDownloaded: Long, totalBytes: Long, speed: Long) {
        _activeDownloads.update { list ->
            list.map { item ->
                if (item.id == id) {
                    val progress = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes.toFloat() else 0f
                    val status = if (bytesDownloaded >= totalBytes && totalBytes > 0) DownloadItemStatus.COMPLETED else DownloadItemStatus.DOWNLOADING
                    item.copy(
                        bytesDownloaded = bytesDownloaded,
                        totalBytes = totalBytes,
                        progress = progress.coerceIn(0f, 1f),
                        speedBytesPerSec = speed,
                        status = status
                    )
                } else item
            }
        }
    }
}

/**
 * DownloadsViewModel for Kotlin Multiplatform (MVI Architecture).
 *
 * Connects Room KMP [DownloadCacheDao] for persistent completed downloads and [DownloadEngine]
 * for live background download management, chunk streaming, progress tracking, and offline playback.
 */
class DownloadsViewModel(
    private val downloadCacheDao: DownloadCacheDao,
    val downloadEngine: DownloadEngine = KmpDownloadEngineImpl(downloadCacheDao),
    private val directoryProvider: DownloadDirectoryProvider = DefaultDownloadDirectoryProvider(),
    initialState: DownloadsState = DownloadsState(),
    coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.Default
) : MviViewModel<DownloadsState, DownloadsEvent>(initialState, coroutineContext) {

    constructor(
        downloadCacheDao: DownloadCacheDao,
        downloadService: IDownloadService,
        directoryProvider: DownloadDirectoryProvider = DefaultDownloadDirectoryProvider(),
        initialState: DownloadsState = DownloadsState(),
        coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.Default
    ) : this(
        downloadCacheDao = downloadCacheDao,
        downloadEngine = DownloadServiceEngineAdapter(downloadService, coroutineContext),
        directoryProvider = directoryProvider,
        initialState = initialState,
        coroutineContext = coroutineContext
    )

    init {
        startObserving()
    }

    private fun refreshStorageMetrics(groups: List<CompletedHeaderGroup> = currentState.completedGroups) {
        val metrics = directoryProvider.getStorageMetrics()
        val totalAppStorage = groups.sumOf { it.totalEstimatedSizeBytes }
        val actualAppBytes = if (totalAppStorage > 0L) {
            totalAppStorage
        } else {
            directoryProvider.calculateDirectorySizeBytes()
        }

        updateState {
            copy(
                storageUsage = StorageUsageInfo(
                    appBytes = actualAppBytes,
                    usedBytes = metrics.usedBytes,
                    freeBytes = metrics.freeBytes,
                    totalBytes = metrics.totalBytes
                )
            )
        }
    }

    private fun startObserving() {
        launchSafeJob(key = "observation") {
            refreshStorageMetrics()

            // 1. Observe active and queued downloads from DownloadEngine
            launch {
                combine(downloadEngine.queueFlow, downloadEngine.progressFlow) { queue, progresses ->
                    queue.map { item ->
                        val progress = progresses[item.id] ?: DownloadProgress(id = item.id, status = DownloadStatus.QUEUED)
                        item.toActiveDownloadItem(progress)
                    }
                }.collectLatest { activeList ->
                    updateState { copy(activeDownloads = activeList) }
                }
            }

            // 2. Observe completed headers & episodes from Room KMP
            launch {
                downloadCacheDao.getAllHeadersFlow().collectLatest { headers ->
                    val groups = headers.map { header ->
                        val episodes = downloadCacheDao.getEpisodesForParent(header.id)
                        val totalEstimatedSize = episodes.sumOf { ep ->
                            val file = directoryProvider.getEpisodeFile(header.id, ep.id)
                            if (file.exists()) file.length() else 0L
                        }

                        CompletedHeaderGroup(
                            header = header,
                            episodes = episodes,
                            totalEstimatedSizeBytes = totalEstimatedSize
                        )
                    }

                    updateState {
                        copy(
                            completedGroups = groups,
                            isLoading = false
                        )
                    }
                    refreshStorageMetrics(groups)
                }
            }
        }
    }

    private fun downloadAction(id: Int, action: suspend DownloadEngine.(String) -> Unit) {
        launch { downloadEngine.action(id.toString()) }
    }

    override fun handleEvent(event: DownloadsEvent) {
        when (event) {
            is DownloadsEvent.SwitchTab -> {
                updateState { copy(selectedTab = event.tab) }
            }

            is DownloadsEvent.PauseDownload -> downloadAction(event.id, DownloadEngine::pauseDownload)
            is DownloadsEvent.ResumeDownload -> downloadAction(event.id, DownloadEngine::resumeDownload)
            is DownloadsEvent.CancelDownload -> downloadAction(event.id, DownloadEngine::cancelDownload)
            is DownloadsEvent.RetryDownload -> downloadAction(event.id, DownloadEngine::retryDownload)

            DownloadsEvent.PauseAll -> {
                launch { downloadEngine.pauseAll() }
            }

            DownloadsEvent.ResumeAll -> {
                launch { downloadEngine.resumeAll() }
            }

            DownloadsEvent.CancelAll -> {
                launch { downloadEngine.cancelAll() }
            }

            is DownloadsEvent.DeleteCompletedHeader -> {
                launchSafeJob(
                    key = "delete_header_${event.id}",
                    onError = { e -> updateState { copy(errorMessage = e.message) } }
                ) {
                    downloadCacheDao.deleteEpisodesForParent(event.id)
                    downloadCacheDao.deleteHeader(event.id)
                    val parentDir = directoryProvider.getParentDirectory(event.id)
                    if (parentDir.exists()) {
                        parentDir.deleteRecursively()
                    }
                    refreshStorageMetrics()
                    emitEffect(DownloadsEffect.ShowMessageRes(Res.string.delete_files))
                }
            }

            is DownloadsEvent.DeleteCompletedEpisode -> {
                launchSafeJob(
                    key = "delete_episode_${event.id}",
                    onError = { e -> updateState { copy(errorMessage = e.message) } }
                ) {
                    downloadCacheDao.deleteEpisode(event.id)
                    val epFile = directoryProvider.getEpisodeFile(event.parentId, event.id)
                    if (epFile.exists()) {
                        epFile.delete()
                    }
                    val remaining = downloadCacheDao.getEpisodesForParent(event.parentId)
                    if (remaining.isEmpty()) {
                        downloadCacheDao.deleteHeader(event.parentId)
                        val parentDir = directoryProvider.getParentDirectory(event.parentId)
                        if (parentDir.exists() && (parentDir.listFiles()?.isEmpty() == true)) {
                            parentDir.delete()
                        }
                    }
                    refreshStorageMetrics()
                    emitEffect(DownloadsEffect.ShowMessageRes(Res.string.delete_file))
                }
            }

            is DownloadsEvent.PlayOffline -> {
                val ep = event.episode
                val header = event.header
                val title = ep.name ?: header?.name
                val epFile = directoryProvider.getEpisodeFile(header?.id ?: ep.parentId, ep.id)
                val offlineUrl = if (epFile.exists()) epFile.toURI().toString() else "file://downloads/${header?.id ?: ep.parentId}/${ep.id}.mp4"

                emitEffect(
                    DownloadsEffect.NavigateToPlayer(
                        title = title,
                        titleRes = if (title == null) Res.string.offline_playback else null,
                        url = offlineUrl,
                        episodeIndex = ep.episode,
                        seasonIndex = ep.season,
                        headerId = header?.id ?: ep.parentId,
                        episodeId = ep.id
                    )
                )
            }

            is DownloadsEvent.ToggleHeaderExpanded -> {
                updateState {
                    val nextExpanded = if (expandedHeaderIds.contains(event.headerId)) {
                        expandedHeaderIds - event.headerId
                    } else {
                        expandedHeaderIds + event.headerId
                    }
                    copy(expandedHeaderIds = nextExpanded)
                }
            }

            is DownloadsEvent.SearchQueryChanged -> {
                updateState { copy(searchQuery = event.query) }
            }

            DownloadsEvent.Refresh -> {
                startObserving()
            }

            DownloadsEvent.ClearError -> {
                updateState { copy(errorMessage = null) }
            }
        }
    }

    fun enqueueDownload(item: DownloadQueueItem) {
        launch {
            downloadEngine.startDownload(item)
        }
    }

    fun enqueueDownload(item: ActiveDownloadItem) {
        launch {
            downloadEngine.startDownload(item.toQueueItem())
        }
    }
}

/**
 * Converts a [DownloadQueueItem] to an [ActiveDownloadItem].
 */
fun DownloadQueueItem.toActiveDownloadItem(
    progress: DownloadProgress = DownloadProgress(id = this.id)
): ActiveDownloadItem {
    val intId = this.episodeId
    return ActiveDownloadItem(
        id = intId,
        parentId = this.parentId,
        title = this.episodeName ?: this.headerName,
        headerName = this.headerName,
        episodeName = this.episodeName,
        episodeIndex = this.episodeIndex,
        seasonIndex = this.seasonIndex,
        progress = progress.progress,
        bytesDownloaded = progress.bytesDownloaded,
        totalBytes = progress.totalBytes,
        speedBytesPerSec = progress.speedBytesPerSec,
        status = when (progress.status) {
            DownloadStatus.QUEUED -> DownloadItemStatus.QUEUED
            DownloadStatus.DOWNLOADING -> DownloadItemStatus.DOWNLOADING
            DownloadStatus.PAUSED -> DownloadItemStatus.PAUSED
            DownloadStatus.COMPLETED -> DownloadItemStatus.COMPLETED
            DownloadStatus.FAILED -> DownloadItemStatus.ERROR
            DownloadStatus.CANCELLED -> DownloadItemStatus.QUEUED
        },
        errorMessage = progress.errorMessage,
        posterUrl = this.posterUrl,
        sourceUrl = this.url,
        videoQuality = this.videoQuality,
        etaSeconds = progress.etaSeconds
    )
}

/**
 * Converts an [ActiveDownloadItem] to a [DownloadQueueItem].
 */
fun ActiveDownloadItem.toQueueItem(): DownloadQueueItem {
    return DownloadQueueItem(
        id = this.id.toString(),
        parentId = this.parentId ?: this.id,
        episodeId = this.id,
        url = this.sourceUrl ?: "",
        headerName = this.headerName,
        episodeName = this.episodeName,
        episodeIndex = this.episodeIndex,
        seasonIndex = this.seasonIndex,
        posterUrl = this.posterUrl,
        videoQuality = this.videoQuality
    )
}

/**
 * Format raw bytes into human-readable representation (KB, MB, GB).
 */
fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.size - 1) {
        value /= 1024.0
        unitIndex++
    }
    val formatted = if (value >= 100 || unitIndex == 0) {
        "${value.toInt()}"
    } else {
        val rounded = (value * 10).toInt() / 10.0
        if (rounded % 1.0 == 0.0) "${rounded.toInt()}" else "$rounded"
    }
    return "$formatted ${units[unitIndex]}"
}

/**
 * Format raw byte rate into human-readable speed (KB/s, MB/s).
 */
fun formatByteRate(bytesPerSec: Long): String {
    if (bytesPerSec <= 0) return "0 KB/s"
    return "${formatBytes(bytesPerSec)}/s"
}
