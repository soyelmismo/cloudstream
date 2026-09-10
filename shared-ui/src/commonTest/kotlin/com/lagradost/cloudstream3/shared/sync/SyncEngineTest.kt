package com.lagradost.cloudstream3.shared.sync

import com.lagradost.cloudstream3.shared.persistence.database.DefaultAppDatabase
import com.lagradost.cloudstream3.shared.persistence.entity.BookmarkEntity
import com.lagradost.cloudstream3.shared.persistence.entity.FavoriteEntity
import com.lagradost.cloudstream3.shared.persistence.entity.SyncTombstoneEntity
import com.lagradost.cloudstream3.shared.persistence.entity.WatchProgressEntity
import com.lagradost.cloudstream3.shared.sync.engine.SyncEngineImpl
import com.lagradost.cloudstream3.shared.sync.models.BookmarkDelta
import com.lagradost.cloudstream3.shared.sync.models.FavoriteDelta
import com.lagradost.cloudstream3.shared.sync.models.SyncDelta
import com.lagradost.cloudstream3.shared.sync.models.TombstoneDelta
import com.lagradost.cloudstream3.shared.sync.models.WatchProgressDelta
import com.lagradost.cloudstream3.shared.sync.resolver.ConflictResolver
import com.lagradost.cloudstream3.shared.sync.transport.InMemorySyncTransport
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncEngineTest {

    private fun createTestDatabase() = object : DefaultAppDatabase(null) {}

    @Test
    fun testConflictResolverLww() {
        assertTrue(
            ConflictResolver.shouldApplyWatchProgress(
                localLastUpdated = 1000L,
                remoteUpdatedAt = 2000L,
                localPosition = 100L,
                remotePosition = 50L,
                tombstoneDeletedAt = null
            )
        )

        assertFalse(
            ConflictResolver.shouldApplyWatchProgress(
                localLastUpdated = 2000L,
                remoteUpdatedAt = 1000L,
                localPosition = 100L,
                remotePosition = 200L,
                tombstoneDeletedAt = null
            )
        )

        assertTrue(
            ConflictResolver.shouldApplyWatchProgress(
                localLastUpdated = 2000L,
                remoteUpdatedAt = 2000L,
                localPosition = 100L,
                remotePosition = 200L,
                tombstoneDeletedAt = null
            )
        )
    }

    @Test
    fun testConflictResolverTombstoneSupersedesOldDelta() {
        assertFalse(
            ConflictResolver.shouldApplyWatchProgress(
                localLastUpdated = 500L,
                remoteUpdatedAt = 1000L,
                localPosition = 10L,
                remotePosition = 20L,
                tombstoneDeletedAt = 1500L
            )
        )

        assertFalse(
            ConflictResolver.shouldApplyBookmark(
                localUpdatedAt = 500L,
                remoteUpdatedAt = 1000L,
                tombstoneDeletedAt = 1500L
            )
        )
    }

    @Test
    fun testConflictResolverNewerDeltaRevivesFromTombstone() {
        assertTrue(
            ConflictResolver.shouldApplyWatchProgress(
                localLastUpdated = 500L,
                remoteUpdatedAt = 2000L,
                localPosition = 10L,
                remotePosition = 20L,
                tombstoneDeletedAt = 1500L
            )
        )

        assertTrue(
            ConflictResolver.shouldApplyBookmark(
                localUpdatedAt = 500L,
                remoteUpdatedAt = 2000L,
                tombstoneDeletedAt = 1500L
            )
        )
    }

    @Test
    fun testSyncEngineAppliesRemoteWatchProgress() = runTest {
        val database = createTestDatabase()
        val engine = SyncEngineImpl(database) { 3000L }

        database.watchProgressDao().upsertWatchProgress(
            WatchProgressEntity(
                accountId = 0,
                mediaId = 42,
                position = 10_000L,
                duration = 60_000L,
                watchState = 1,
                lastUpdated = 1000L
            )
        )

        val delta = SyncDelta(
            deviceId = "device-b",
            accountId = 0,
            timestamp = 2500L,
            watchProgress = persistentListOf(
                WatchProgressDelta(
                    mediaId = 42,
                    position = 25_000L,
                    duration = 60_000L,
                    watchState = 1,
                    updatedAt = 2000L
                )
            )
        )

        val result = engine.applyDelta(delta)
        assertEquals(1, result.watchProgressApplied)

        val local = database.watchProgressDao().getWatchProgress(0, 42)
        assertNotNull(local)
        assertEquals(25_000L, local.position)
        assertEquals(2000L, local.lastUpdated)
    }

    @Test
    fun testSyncEngineTombstonePreventsZombieResurrection() = runTest {
        val database = createTestDatabase()
        val engine = SyncEngineImpl(database) { 3000L }

        database.syncTombstoneDao().upsertTombstone(
            SyncTombstoneEntity(
                accountId = 0,
                entityType = "bookmark",
                entityId = "99",
                deletedAt = 2000L
            )
        )

        val delta = SyncDelta(
            deviceId = "device-b",
            accountId = 0,
            timestamp = 2500L,
            bookmarks = persistentListOf(
                BookmarkDelta(
                    id = 99,
                    name = "Old Anime",
                    url = "https://example.com/anime",
                    apiName = "provider",
                    watchType = 1,
                    updatedAt = 1500L
                )
            )
        )

        val result = engine.applyDelta(delta)
        assertEquals(0, result.bookmarksApplied)
        assertEquals(1, result.conflictsSkipped)

        val bookmark = database.bookmarkDao().getBookmark(0, 99)
        assertNull(bookmark)
    }

    @Test
    fun testTwoWaySyncFetchMergePush() = runTest {
        val database = createTestDatabase()
        val transport = InMemorySyncTransport("in-memory-test")
        val engine = SyncEngineImpl(database) { 3000L }

        transport.pushDelta(
            SyncDelta(
                deviceId = "device-b",
                accountId = 0,
                timestamp = 2000L,
                favorites = persistentListOf(
                    FavoriteDelta(
                        id = 101,
                        name = "Frieren",
                        url = "https://example.com/frieren",
                        apiName = "ani-provider",
                        updatedAt = 2000L
                    )
                )
            )
        )

        database.bookmarkDao().upsertBookmark(
            BookmarkEntity(
                accountId = 0,
                id = 202,
                name = "Steins;Gate",
                url = "https://example.com/sg",
                apiName = "ani-provider",
                latestUpdatedTime = 2500L
            )
        )

        val syncResult = engine.sync(0, "device-a", transport, 1000L).getOrThrow()
        assertEquals(1, syncResult.favoritesApplied)

        val mergedFav = database.favoriteDao().getFavorite(0, 101)
        assertNotNull(mergedFav)
        assertEquals("Frieren", mergedFav.name)

        val transportDeltas = transport.fetchDeltas(1000L).getOrThrow()
        val pushedByA = transportDeltas.firstOrNull { it.deviceId == "device-a" }
        assertNotNull(pushedByA)
        assertEquals(1, pushedByA.bookmarks.size)
        assertEquals(202, pushedByA.bookmarks.first().id)
    }
}
