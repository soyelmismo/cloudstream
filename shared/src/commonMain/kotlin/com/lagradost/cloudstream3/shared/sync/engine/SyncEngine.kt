package com.lagradost.cloudstream3.shared.sync.engine

import com.lagradost.cloudstream3.APIHolder.unixTimeMS
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.shared.backup.BackupManager
import com.lagradost.cloudstream3.shared.persistence.database.AppDatabase
import com.lagradost.cloudstream3.shared.persistence.entity.AccountEntity
import com.lagradost.cloudstream3.shared.persistence.entity.AppPreferenceEntity
import com.lagradost.cloudstream3.shared.persistence.entity.BookmarkEntity
import com.lagradost.cloudstream3.shared.persistence.entity.FavoriteEntity
import com.lagradost.cloudstream3.shared.persistence.entity.SyncTombstoneEntity
import com.lagradost.cloudstream3.shared.persistence.entity.WatchProgressEntity
import com.lagradost.cloudstream3.shared.sync.models.BookmarkDelta
import com.lagradost.cloudstream3.shared.sync.models.FavoriteDelta
import com.lagradost.cloudstream3.shared.sync.models.SyncApplyResult
import com.lagradost.cloudstream3.shared.sync.models.SyncDelta
import com.lagradost.cloudstream3.shared.sync.models.SyncEntityType
import com.lagradost.cloudstream3.shared.sync.models.TombstoneDelta
import com.lagradost.cloudstream3.shared.sync.models.WatchProgressDelta
import com.lagradost.cloudstream3.shared.sync.resolver.ConflictResolver
import com.lagradost.cloudstream3.shared.sync.transport.SyncTransport
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap

interface SyncEngine {
    suspend fun createDelta(accountId: Int, deviceId: String, sinceTimestamp: Long): SyncDelta
    suspend fun applyDelta(delta: SyncDelta): SyncApplyResult
    suspend fun sync(accountId: Int, deviceId: String, transport: SyncTransport, lastSyncTimestamp: Long): Result<SyncApplyResult>
}

class SyncEngineImpl(
    val database: AppDatabase,
    private val timeProvider: () -> Long = { unixTimeMS }
) : SyncEngine {

    override suspend fun createDelta(
        accountId: Int,
        deviceId: String,
        sinceTimestamp: Long
    ): SyncDelta {
        val now = timeProvider()
        val account = database.accountDao().getAccountById(accountId)
        val accountUuid = account?.accountUuid?.ifBlank { null } ?: accountId.toString()

        val watchProgress = database.watchProgressDao()
            .getWatchProgressSince(accountId, sinceTimestamp)
            .map {
                WatchProgressDelta(
                    mediaId = it.mediaId,
                    position = it.position,
                    duration = it.duration,
                    watchState = it.watchState,
                    updatedAt = it.lastUpdated
                )
            }
            .toImmutableList()

        val bookmarks = database.bookmarkDao()
            .getBookmarksSince(accountId, sinceTimestamp)
            .map {
                BookmarkDelta(
                    id = it.id,
                    name = it.name,
                    url = it.url,
                    apiName = it.apiName,
                    type = it.type?.name,
                    posterUrl = it.posterUrl,
                    year = it.year,
                    watchType = it.watchType,
                    updatedAt = it.latestUpdatedTime
                )
            }
            .toImmutableList()

        val favorites = database.favoriteDao()
            .getFavoritesSince(accountId, sinceTimestamp)
            .map {
                FavoriteDelta(
                    id = it.id,
                    name = it.name,
                    url = it.url,
                    apiName = it.apiName,
                    type = it.type?.name,
                    posterUrl = it.posterUrl,
                    updatedAt = it.latestUpdatedTime
                )
            }
            .toImmutableList()

        val tombstones = database.syncTombstoneDao()
            .getTombstonesSince(accountId, sinceTimestamp)
            .map {
                TombstoneDelta(
                    entityType = it.entityType,
                    entityId = it.entityId,
                    deletedAt = it.deletedAt
                )
            }
            .toImmutableList()

        val (settingsMap, pluginsMap) = extractPreferences()
        val accounts = database.accountDao().getAllAccounts().toImmutableList()

        return SyncDelta(
            schemaVersion = 1,
            deviceId = deviceId,
            accountId = accountId,
            accountUuid = accountUuid,
            timestamp = now,
            watchProgress = watchProgress,
            bookmarks = bookmarks,
            favorites = favorites,
            tombstones = tombstones,
            settings = settingsMap,
            plugins = pluginsMap,
            accounts = accounts
        )
    }

    override suspend fun applyDelta(delta: SyncDelta): SyncApplyResult {
        val (tombstonesApplied, tombstonesSkipped) = applyTombstones(delta.accountId, delta.tombstones)
        val (progressApplied, progressSkipped) = applyWatchProgress(delta.accountId, delta.watchProgress)
        val (bookmarksApplied, bookmarksSkipped) = applyBookmarks(delta.accountId, delta.bookmarks)
        val (favoritesApplied, favoritesSkipped) = applyFavorites(delta.accountId, delta.favorites)
        val settingsApplied = applyPreferenceEntries(delta.settings)
        val pluginsApplied = applyPreferenceEntries(delta.plugins)
        val accountsApplied = applyAccounts(delta.accounts)

        return SyncApplyResult(
            watchProgressApplied = progressApplied,
            bookmarksApplied = bookmarksApplied,
            favoritesApplied = favoritesApplied,
            tombstonesApplied = tombstonesApplied,
            conflictsSkipped = tombstonesSkipped + progressSkipped + bookmarksSkipped + favoritesSkipped,
            settingsApplied = settingsApplied,
            pluginsApplied = pluginsApplied,
            accountsApplied = accountsApplied
        )
    }

    override suspend fun sync(
        accountId: Int,
        deviceId: String,
        transport: SyncTransport,
        lastSyncTimestamp: Long
    ): Result<SyncApplyResult> = runCatching {
        // 0. Ensure remote repo root is initialized
        transport.initRepository(deviceId).getOrThrow()

        // 1. Fetch remote deltas (git fetch)
        val remoteDeltas = transport.fetchDeltas(lastSyncTimestamp).getOrThrow()
        var accumulatedResult = SyncApplyResult.EMPTY

        // 2. Merge remote changes into local state with conflict resolution (git merge)
        for (delta in remoteDeltas) {
            if (delta.deviceId == deviceId) continue
            accumulatedResult += applyDelta(delta)
        }

        // 3. Create delta of locally originated modifications after merge
        val localDelta = createDelta(accountId, deviceId, lastSyncTimestamp)

        // 4. Push local changes only (git push)
        val pushedCount = if (!localDelta.isEmpty) {
            transport.pushDelta(localDelta).getOrThrow()
            localDelta.totalItems
        } else {
            0
        }

        accumulatedResult.copy(localItemsPushed = pushedCount)
    }

    private suspend fun applyTombstones(
        accountId: Int,
        tombstones: List<TombstoneDelta>
    ): Pair<Int, Int> {
        var applied = 0
        var skipped = 0
        for (tombstone in tombstones) {
            if (applySingleTombstone(accountId, tombstone)) {
                applied++
            } else {
                skipped++
            }
        }
        return applied to skipped
    }

    private suspend fun applySingleTombstone(
        accountId: Int,
        delta: TombstoneDelta
    ): Boolean {
        val existing = database.syncTombstoneDao().getTombstone(accountId, delta.entityType, delta.entityId)
        if (existing != null && existing.deletedAt >= delta.deletedAt) {
            return false
        }

        database.syncTombstoneDao().upsertTombstone(
            SyncTombstoneEntity(
                accountId = accountId,
                entityType = delta.entityType,
                entityId = delta.entityId,
                deletedAt = delta.deletedAt
            )
        )
        deleteLocalEntityIfOlder(accountId, delta.entityType, delta.entityId, delta.deletedAt)
        return true
    }

    private suspend fun deleteLocalEntityIfOlder(
        accountId: Int,
        entityType: String,
        entityId: String,
        deletedAt: Long
    ) {
        val id = entityId.toIntOrNull() ?: return
        when (entityType) {
            SyncEntityType.WATCH_PROGRESS -> deleteWatchProgressIfOlder(accountId, id, deletedAt)
            SyncEntityType.BOOKMARK -> deleteBookmarkIfOlder(accountId, id, deletedAt)
            SyncEntityType.FAVORITE -> deleteFavoriteIfOlder(accountId, id, deletedAt)
        }
    }

    private suspend fun deleteWatchProgressIfOlder(accountId: Int, mediaId: Int, deletedAt: Long) {
        val local = database.watchProgressDao().getWatchProgress(accountId, mediaId)
        if (local != null && local.lastUpdated <= deletedAt) {
            database.watchProgressDao().deleteWatchProgress(accountId, mediaId)
        }
    }

    private suspend fun deleteBookmarkIfOlder(accountId: Int, id: Int, deletedAt: Long) {
        val local = database.bookmarkDao().getBookmark(accountId, id)
        if (local != null && local.latestUpdatedTime <= deletedAt) {
            database.bookmarkDao().deleteBookmark(accountId, id)
        }
    }

    private suspend fun deleteFavoriteIfOlder(accountId: Int, id: Int, deletedAt: Long) {
        val local = database.favoriteDao().getFavorite(accountId, id)
        if (local != null && local.latestUpdatedTime <= deletedAt) {
            database.favoriteDao().deleteFavorite(accountId, id)
        }
    }

    private suspend fun applyWatchProgress(
        accountId: Int,
        deltas: List<WatchProgressDelta>
    ): Pair<Int, Int> {
        var applied = 0
        var skipped = 0
        for (delta in deltas) {
            if (applySingleWatchProgress(accountId, delta)) {
                applied++
            } else {
                skipped++
            }
        }
        return applied to skipped
    }

    private suspend fun applySingleWatchProgress(
        accountId: Int,
        remote: WatchProgressDelta
    ): Boolean {
        val entityId = remote.mediaId.toString()
        val tombstone = database.syncTombstoneDao().getTombstone(accountId, SyncEntityType.WATCH_PROGRESS, entityId)
        val local = database.watchProgressDao().getWatchProgress(accountId, remote.mediaId)

        val shouldApply = ConflictResolver.shouldApplyWatchProgress(
            localLastUpdated = local?.lastUpdated ?: 0L,
            remoteUpdatedAt = remote.updatedAt,
            localPosition = local?.position ?: 0L,
            remotePosition = remote.position,
            tombstoneDeletedAt = tombstone?.deletedAt
        )
        if (!shouldApply) return false

        database.watchProgressDao().upsertWatchProgress(
            WatchProgressEntity(
                accountId = accountId,
                mediaId = remote.mediaId,
                position = remote.position,
                duration = remote.duration,
                watchState = remote.watchState,
                lastUpdated = remote.updatedAt
            )
        )
        if (tombstone != null) {
            database.syncTombstoneDao().deleteTombstone(accountId, SyncEntityType.WATCH_PROGRESS, entityId)
        }
        return true
    }

    private suspend fun applyBookmarks(
        accountId: Int,
        deltas: List<BookmarkDelta>
    ): Pair<Int, Int> {
        var applied = 0
        var skipped = 0
        for (delta in deltas) {
            if (applySingleBookmark(accountId, delta)) {
                applied++
            } else {
                skipped++
            }
        }
        return applied to skipped
    }

    private suspend fun applySingleBookmark(
        accountId: Int,
        remote: BookmarkDelta
    ): Boolean {
        val entityId = remote.id.toString()
        val tombstone = database.syncTombstoneDao().getTombstone(accountId, SyncEntityType.BOOKMARK, entityId)
        val local = database.bookmarkDao().getBookmark(accountId, remote.id)

        val shouldApply = ConflictResolver.shouldApplyBookmark(
            localUpdatedAt = local?.latestUpdatedTime ?: 0L,
            remoteUpdatedAt = remote.updatedAt,
            tombstoneDeletedAt = tombstone?.deletedAt
        )
        if (!shouldApply) return false

        val tvType = parseTvType(remote.type) ?: local?.type
        val entity = BookmarkEntity(
            accountId = accountId,
            id = remote.id,
            name = remote.name,
            url = remote.url,
            apiName = remote.apiName,
            type = tvType,
            posterUrl = remote.posterUrl ?: local?.posterUrl,
            year = remote.year ?: local?.year,
            watchType = remote.watchType,
            bookmarkedTime = local?.bookmarkedTime ?: remote.updatedAt,
            latestUpdatedTime = remote.updatedAt,
            quality = local?.quality,
            plot = local?.plot,
            score = local?.score,
            tagsJson = local?.tagsJson,
            syncDataJson = local?.syncDataJson,
            posterHeadersJson = local?.posterHeadersJson
        )
        database.bookmarkDao().upsertBookmark(entity)
        if (tombstone != null) {
            database.syncTombstoneDao().deleteTombstone(accountId, SyncEntityType.BOOKMARK, entityId)
        }
        return true
    }

    private suspend fun applyFavorites(
        accountId: Int,
        deltas: List<FavoriteDelta>
    ): Pair<Int, Int> {
        var applied = 0
        var skipped = 0
        for (delta in deltas) {
            if (applySingleFavorite(accountId, delta)) {
                applied++
            } else {
                skipped++
            }
        }
        return applied to skipped
    }

    private suspend fun applySingleFavorite(
        accountId: Int,
        remote: FavoriteDelta
    ): Boolean {
        val entityId = remote.id.toString()
        val tombstone = database.syncTombstoneDao().getTombstone(accountId, SyncEntityType.FAVORITE, entityId)
        val local = database.favoriteDao().getFavorite(accountId, remote.id)

        val shouldApply = ConflictResolver.shouldApplyFavorite(
            localUpdatedAt = local?.latestUpdatedTime ?: 0L,
            remoteUpdatedAt = remote.updatedAt,
            tombstoneDeletedAt = tombstone?.deletedAt
        )
        if (!shouldApply) return false

        val tvType = parseTvType(remote.type) ?: local?.type
        val entity = FavoriteEntity(
            accountId = accountId,
            id = remote.id,
            name = remote.name,
            url = remote.url,
            apiName = remote.apiName,
            type = tvType,
            posterUrl = remote.posterUrl ?: local?.posterUrl,
            favoritesTime = local?.favoritesTime ?: remote.updatedAt,
            latestUpdatedTime = remote.updatedAt
        )
        database.favoriteDao().upsertFavorite(entity)
        if (tombstone != null) {
            database.syncTombstoneDao().deleteTombstone(accountId, SyncEntityType.FAVORITE, entityId)
        }
        return true
    }

    private fun parseTvType(typeString: String?): TvType? {
        if (typeString == null) return null
        return runCatching { TvType.valueOf(typeString) }.getOrNull()
    }

    private suspend fun extractPreferences(): Pair<ImmutableMap<String, String>, ImmutableMap<String, String>> {
        val allPrefs = database.appPreferenceDao().getAllPreferences()
        val settings = mutableMapOf<String, String>()
        val plugins = mutableMapOf<String, String>()

        for (pref in allPrefs) {
            if (!BackupManager.isKeyTransferable(pref.key)) continue
            if (BackupManager.isPluginKey(pref.key)) {
                plugins[pref.key] = pref.value
            } else {
                settings[pref.key] = pref.value
            }
        }
        return settings.toImmutableMap() to plugins.toImmutableMap()
    }

    private suspend fun applyPreferenceEntries(entries: Map<String, String>): Int {
        if (entries.isEmpty()) return 0
        val current = database.appPreferenceDao().getAllPreferences().associate { it.key to it.value }
        val toUpsert = filterChangedPreferences(entries, current, timeProvider())
        if (toUpsert.isNotEmpty()) {
            database.appPreferenceDao().insertPreferences(toUpsert)
        }
        return toUpsert.size
    }

    private fun filterChangedPreferences(
        entries: Map<String, String>,
        current: Map<String, String>,
        now: Long
    ): List<AppPreferenceEntity> {
        val result = mutableListOf<AppPreferenceEntity>()
        for ((key, value) in entries) {
            if (BackupManager.isKeyTransferable(key) && current[key] != value) {
                result.add(AppPreferenceEntity(key = key, value = value, updatedAt = now))
            }
        }
        return result
    }

    private suspend fun applyAccounts(accounts: List<AccountEntity>): Int {
        if (accounts.isEmpty()) return 0
        val currentAccounts = database.accountDao().getAllAccounts()
        val byUuid = currentAccounts.filter { it.accountUuid.isNotEmpty() }.associateBy { it.accountUuid }
        val byKey = currentAccounts.associateBy { it.keyIndex }
        var applied = 0
        for (remote in accounts) {
            if (applySingleAccount(remote, byUuid, byKey, currentAccounts)) {
                applied++
            }
        }
        return applied
    }

    private suspend fun applySingleAccount(
        remote: AccountEntity,
        byUuid: Map<String, AccountEntity>,
        byKey: Map<Int, AccountEntity>,
        currentAccounts: List<AccountEntity>
    ): Boolean {
        val localMatch = if (remote.accountUuid.isNotEmpty()) {
            byUuid[remote.accountUuid]
        } else {
            byKey[remote.keyIndex]
        }

        return if (localMatch != null) {
            updateExistingAccountIfChanged(localMatch, remote)
        } else {
            insertNewRemoteAccount(remote, byKey, currentAccounts)
        }
    }

    private suspend fun updateExistingAccountIfChanged(
        local: AccountEntity,
        remote: AccountEntity
    ): Boolean {
        val hasChanges = local.name != remote.name ||
            local.customImage != remote.customImage ||
            local.defaultImageIndex != remote.defaultImageIndex ||
            (local.accountUuid.isEmpty() && remote.accountUuid.isNotEmpty())

        if (!hasChanges) return false

        val updatedUuid = local.accountUuid.ifEmpty { remote.accountUuid }
        database.accountDao().upsertAccount(
            local.copy(
                name = remote.name,
                customImage = remote.customImage,
                defaultImageIndex = remote.defaultImageIndex,
                accountUuid = updatedUuid
            )
        )
        return true
    }

    private suspend fun insertNewRemoteAccount(
        remote: AccountEntity,
        byKey: Map<Int, AccountEntity>,
        currentAccounts: List<AccountEntity>
    ): Boolean {
        val targetKey = if (byKey.containsKey(remote.keyIndex)) {
            (currentAccounts.maxOfOrNull { it.keyIndex } ?: 0) + 1
        } else {
            remote.keyIndex
        }
        database.accountDao().upsertAccount(remote.copy(keyIndex = targetKey))
        return true
    }
}
