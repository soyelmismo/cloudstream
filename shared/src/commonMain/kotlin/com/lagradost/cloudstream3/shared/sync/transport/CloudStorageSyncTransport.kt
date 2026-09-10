package com.lagradost.cloudstream3.shared.sync.transport

import androidx.compose.runtime.Immutable
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.shared.sync.cloud.CloudStorageClient
import com.lagradost.cloudstream3.shared.sync.cloud.RemoteFileMetadata
import com.lagradost.cloudstream3.shared.sync.layout.CloudFileLayoutMapper
import com.lagradost.cloudstream3.shared.sync.models.BookmarkDelta
import com.lagradost.cloudstream3.shared.sync.models.FavoriteDelta
import com.lagradost.cloudstream3.shared.sync.models.SyncDelta
import com.lagradost.cloudstream3.shared.sync.models.SyncManifest
import com.lagradost.cloudstream3.shared.sync.models.TombstoneDelta
import com.lagradost.cloudstream3.shared.sync.models.WatchProgressDelta
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.Json

@Immutable
class CloudStorageSyncTransport(
    val storageClient: CloudStorageClient,
    val json: Json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }
) : SyncTransport {

    override val transportId: String = "cloud_storage_${storageClient.providerId}"

    override suspend fun initRepository(deviceId: String): Result<Unit> = runCatching {
        val manifest = SyncManifest(
            schemaVersion = 1,
            appName = "CloudStream",
            deviceId = deviceId,
            updatedAt = APIHolder.unixTimeMS
        )
        val content = json.encodeToString(SyncManifest.serializer(), manifest)
        storageClient.writeFile(CloudFileLayoutMapper.getManifestPath(), content).getOrThrow()
    }

    override suspend fun pushDelta(delta: SyncDelta): Result<Unit> = runCatching {
        initRepository(delta.deviceId).getOrThrow()
        val accountUuid = delta.accountUuid.ifBlank { delta.accountId.toString() }
        pushWatchProgress(accountUuid, delta.watchProgress)
        pushBookmarks(accountUuid, delta.bookmarks)
        pushFavorites(accountUuid, delta.favorites)
        pushTombstones(accountUuid, delta.tombstones)
    }

    override suspend fun fetchDeltas(sinceTimestamp: Long): Result<ImmutableList<SyncDelta>> = runCatching {
        val files = storageClient.listFiles(CloudFileLayoutMapper.USER_DATA_ROOT, sinceTimestamp).getOrThrow()
        val accumulators = mutableMapOf<String, AccountDeltaAccumulator>()

        for (file in files) {
            processRemoteFile(file, sinceTimestamp, accumulators)
        }

        accumulators.values
            .map { it.toSyncDelta(transportId, sinceTimestamp) }
            .filterNot { it.isEmpty }
            .toImmutableList()
    }

    suspend fun testConnection(): Result<Boolean> = storageClient.testConnection()

    private suspend fun pushWatchProgress(accountUuid: String, items: List<WatchProgressDelta>) {
        for (item in items) {
            val path = CloudFileLayoutMapper.getWatchProgressPath(accountUuid, item.mediaId)
            val content = json.encodeToString(WatchProgressDelta.serializer(), item)
            storageClient.writeFile(path, content).getOrThrow()
        }
    }

    private suspend fun pushBookmarks(accountUuid: String, items: List<BookmarkDelta>) {
        for (item in items) {
            val path = CloudFileLayoutMapper.getBookmarkPath(accountUuid, item.id)
            val content = json.encodeToString(BookmarkDelta.serializer(), item)
            storageClient.writeFile(path, content).getOrThrow()
        }
    }

    private suspend fun pushFavorites(accountUuid: String, items: List<FavoriteDelta>) {
        for (item in items) {
            val path = CloudFileLayoutMapper.getFavoritePath(accountUuid, item.id)
            val content = json.encodeToString(FavoriteDelta.serializer(), item)
            storageClient.writeFile(path, content).getOrThrow()
        }
    }

    private suspend fun pushTombstones(accountUuid: String, items: List<TombstoneDelta>) {
        for (item in items) {
            val path = CloudFileLayoutMapper.getTombstonePath(accountUuid, item.entityType, item.entityId)
            val content = json.encodeToString(TombstoneDelta.serializer(), item)
            storageClient.writeFile(path, content).getOrThrow()
            deletePositiveFileForTombstone(accountUuid, item)
        }
    }

    private suspend fun deletePositiveFileForTombstone(accountUuid: String, tombstone: TombstoneDelta) {
        val positivePath = CloudFileLayoutMapper.getPositivePathForTombstone(
            accountUuid = accountUuid,
            entityType = tombstone.entityType,
            entityId = tombstone.entityId
        ) ?: return
        // Best-effort cleanup: deletion failures on absent positive files should not invalidate tombstone publication
        runCatching { storageClient.deleteFile(positivePath) }
    }

    private suspend fun processRemoteFile(
        file: RemoteFileMetadata,
        sinceTimestamp: Long,
        accumulators: MutableMap<String, AccountDeltaAccumulator>
    ) {
        val normalizedPath = file.path.replace('\\', '/')
        if (!isEligibleFile(normalizedPath, file.modifiedTime, file.isDirectory, sinceTimestamp)) return

        val accountUuid = CloudFileLayoutMapper.extractAccountUuidFromPath(normalizedPath) ?: return
        val content = storageClient.readFile(file.path).getOrNull() ?: return
        val accumulator = accumulators.getOrPut(accountUuid) { AccountDeltaAccumulator(accountUuid) }
        parseAndAccumulateFile(normalizedPath, content, accumulator)
    }

    private fun isEligibleFile(path: String, lastModified: Long, isDirectory: Boolean, sinceTimestamp: Long): Boolean {
        if (isDirectory || !path.endsWith(".json")) return false
        if (lastModified in 1..<sinceTimestamp) return false
        return true
    }

    private fun parseAndAccumulateFile(
        path: String,
        content: String,
        accumulator: AccountDeltaAccumulator
    ) {
        runCatching {
            when {
                path.contains("/${CloudFileLayoutMapper.WATCH_PROGRESS_DIR}/") -> {
                    accumulator.watchProgress.add(json.decodeFromString(WatchProgressDelta.serializer(), content))
                }
                path.contains("/${CloudFileLayoutMapper.BOOKMARKS_DIR}/") -> {
                    accumulator.bookmarks.add(json.decodeFromString(BookmarkDelta.serializer(), content))
                }
                path.contains("/${CloudFileLayoutMapper.FAVORITES_DIR}/") -> {
                    accumulator.favorites.add(json.decodeFromString(FavoriteDelta.serializer(), content))
                }
                path.contains("/${CloudFileLayoutMapper.TOMBSTONES_DIR}/") -> {
                    accumulator.tombstones.add(json.decodeFromString(TombstoneDelta.serializer(), content))
                }
            }
        }
    }
}

private class AccountDeltaAccumulator(
    val accountUuid: String,
    val watchProgress: MutableList<WatchProgressDelta> = mutableListOf(),
    val bookmarks: MutableList<BookmarkDelta> = mutableListOf(),
    val favorites: MutableList<FavoriteDelta> = mutableListOf(),
    val tombstones: MutableList<TombstoneDelta> = mutableListOf()
) {
    fun toSyncDelta(deviceId: String, defaultTimestamp: Long): SyncDelta {
        return SyncDelta(
            schemaVersion = 1,
            deviceId = deviceId,
            accountId = accountUuid.toIntOrNull() ?: 0,
            accountUuid = accountUuid,
            timestamp = calculateMaxTimestamp(defaultTimestamp),
            watchProgress = watchProgress.toImmutableList(),
            bookmarks = bookmarks.toImmutableList(),
            favorites = favorites.toImmutableList(),
            tombstones = tombstones.toImmutableList()
        )
    }

    private fun calculateMaxTimestamp(fallback: Long): Long {
        val wpMax = watchProgress.maxOfOrNull { it.updatedAt } ?: 0L
        val bmMax = bookmarks.maxOfOrNull { it.updatedAt } ?: 0L
        val favMax = favorites.maxOfOrNull { it.updatedAt } ?: 0L
        val tsMax = tombstones.maxOfOrNull { it.deletedAt } ?: 0L
        return maxOf(fallback, wpMax, bmMax, favMax, tsMax)
    }
}
