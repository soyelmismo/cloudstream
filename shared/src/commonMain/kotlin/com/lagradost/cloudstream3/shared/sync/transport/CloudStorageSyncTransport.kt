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
import com.lagradost.cloudstream3.shared.persistence.entity.AccountEntity
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
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
        pushSettings(delta.settings)
        pushPlugins(delta.plugins)
        pushAccounts(delta.accounts)
    }

    override suspend fun fetchDeltas(sinceTimestamp: Long): Result<ImmutableList<SyncDelta>> = runCatching {
        val files = storageClient.listFiles(CloudFileLayoutMapper.USER_DATA_ROOT, sinceTimestamp).getOrThrow()
        val accumulators = mutableMapOf<String, AccountDeltaAccumulator>()

        for (file in files) {
            processRemoteFile(file, sinceTimestamp, accumulators)
        }

        val settingsDelta = fetchSettingsDelta(sinceTimestamp)

        val deltas = accumulators.values
            .map { it.toSyncDelta(transportId, sinceTimestamp) }
            .toMutableList()

        if (settingsDelta != null && !settingsDelta.isEmpty) {
            deltas.add(settingsDelta)
        }

        deltas.filterNot { it.isEmpty }.toImmutableList()
    }

    private suspend fun fetchSettingsDelta(sinceTimestamp: Long): SyncDelta? {
        val settingsFiles = storageClient.listFiles(CloudFileLayoutMapper.SETTINGS_ROOT, sinceTimestamp).getOrElse { emptyList() }
        if (settingsFiles.isEmpty()) return null

        val acc = SettingsAccumulator()
        for (file in settingsFiles) {
            processSettingsFile(file, sinceTimestamp, acc)
        }
        return acc.toSyncDelta(transportId, sinceTimestamp)
    }

    private suspend fun processSettingsFile(
        file: RemoteFileMetadata,
        sinceTimestamp: Long,
        acc: SettingsAccumulator
    ) {
        val normalizedPath = file.path.replace('\\', '/')
        if (!isEligibleFile(normalizedPath, file.modifiedTime, file.isDirectory, sinceTimestamp)) return
        val content = storageClient.readFile(file.path).getOrNull() ?: return
        applySettingsContent(normalizedPath, content, acc)
    }

    private fun applySettingsContent(path: String, content: String, acc: SettingsAccumulator) {
        runCatching {
            when {
                path.endsWith(CloudFileLayoutMapper.APP_SETTINGS_FILE) ->
                    acc.settings = json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), content)
                path.endsWith(CloudFileLayoutMapper.PLUGINS_FILE) ->
                    acc.plugins = json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), content)
                path.endsWith(CloudFileLayoutMapper.ACCOUNTS_FILE) ->
                    acc.accounts = json.decodeFromString(ListSerializer(AccountEntity.serializer()), content)
            }
        }
    }

    private suspend fun pushSettings(settings: Map<String, String>) {
        if (settings.isEmpty()) return
        val content = json.encodeToString(MapSerializer(String.serializer(), String.serializer()), settings)
        storageClient.writeFile(CloudFileLayoutMapper.getAppSettingsPath(), content).getOrThrow()
    }

    private suspend fun pushPlugins(plugins: Map<String, String>) {
        if (plugins.isEmpty()) return
        val content = json.encodeToString(MapSerializer(String.serializer(), String.serializer()), plugins)
        storageClient.writeFile(CloudFileLayoutMapper.getPluginsPath(), content).getOrThrow()
    }

    private suspend fun pushAccounts(accounts: List<AccountEntity>) {
        if (accounts.isEmpty()) return
        val content = json.encodeToString(ListSerializer(AccountEntity.serializer()), accounts)
        storageClient.writeFile(CloudFileLayoutMapper.getAccountsPath(), content).getOrThrow()
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

private class SettingsAccumulator(
    var settings: Map<String, String> = emptyMap(),
    var plugins: Map<String, String> = emptyMap(),
    var accounts: List<AccountEntity> = emptyList()
) {
    fun toSyncDelta(deviceId: String, defaultTimestamp: Long): SyncDelta {
        return SyncDelta(
            schemaVersion = 1,
            deviceId = deviceId,
            accountId = 0,
            accountUuid = "",
            timestamp = defaultTimestamp,
            settings = settings.toImmutableMap(),
            plugins = plugins.toImmutableMap(),
            accounts = accounts.toImmutableList()
        )
    }
}
