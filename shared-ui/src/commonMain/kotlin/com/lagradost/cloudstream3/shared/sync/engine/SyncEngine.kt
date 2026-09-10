package com.lagradost.cloudstream3.shared.sync.engine

import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.shared.persistence.database.AppDatabase
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
import com.lagradost.cloudstream3.APIHolder.unixTimeMS
import com.lagradost.cloudstream3.shared.sync.models.WatchProgressDelta
import com.lagradost.cloudstream3.shared.sync.resolver.ConflictResolver
import com.lagradost.cloudstream3.shared.sync.transport.SyncTransport
import kotlinx.collections.immutable.toImmutableList

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
        val accountUuid = database.accountDao().getAccountById(accountId)?.accountUuid ?: ""

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

        return SyncDelta(
            schemaVersion = 1,
            deviceId = deviceId,
            accountId = accountId,
            accountUuid = accountUuid,
            timestamp = now,
            watchProgress = watchProgress,
            bookmarks = bookmarks,
            favorites = favorites,
            tombstones = tombstones
        )
    }

    override suspend fun applyDelta(delta: SyncDelta): SyncApplyResult {
        val (tombstonesApplied, tombstonesSkipped) = applyTombstones(delta.accountId, delta.tombstones)
        val (progressApplied, progressSkipped) = applyWatchProgress(delta.accountId, delta.watchProgress)
        val (bookmarksApplied, bookmarksSkipped) = applyBookmarks(delta.accountId, delta.bookmarks)
        val (favoritesApplied, favoritesSkipped) = applyFavorites(delta.accountId, delta.favorites)

        return SyncApplyResult(
            watchProgressApplied = progressApplied,
            bookmarksApplied = bookmarksApplied,
            favoritesApplied = favoritesApplied,
            tombstonesApplied = tombstonesApplied,
            conflictsSkipped = tombstonesSkipped + progressSkipped + bookmarksSkipped + favoritesSkipped
        )
    }

    override suspend fun sync(
        accountId: Int,
        deviceId: String,
        transport: SyncTransport,
        lastSyncTimestamp: Long
    ): Result<SyncApplyResult> = runCatching {
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
        if (!localDelta.isEmpty) {
            transport.pushDelta(localDelta).getOrThrow()
        }

        accumulatedResult
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
}
