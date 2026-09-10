package com.lagradost.cloudstream3.shared.sync

import com.lagradost.cloudstream3.shared.persistence.database.DefaultAppDatabase
import com.lagradost.cloudstream3.shared.persistence.entity.AccountEntity
import com.lagradost.cloudstream3.shared.persistence.entity.AppPreferenceEntity
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

    @Test
    fun testSettingsPluginsAndAccountsSync() = runTest {
        val database = createTestDatabase()
        val engine = SyncEngineImpl(database) { 5000L }

        // Prepopulate local preferences and accounts
        database.appPreferenceDao().upsertPreference(
            AppPreferenceEntity(key = "app_theme", value = "Amoled", updatedAt = 1000L)
        )
        database.appPreferenceDao().upsertPreference(
            AppPreferenceEntity(key = "INSTALLED_PLUGINS_KEY", value = "[\"pluginA\"]", updatedAt = 1000L)
        )
        database.appPreferenceDao().upsertPreference(
            AppPreferenceEntity(key = "cloud_sync_token", value = "secret", updatedAt = 1000L)
        )
        database.accountDao().upsertAccount(
            AccountEntity(keyIndex = 0, name = "Primary User", accountUuid = "uuid-root")
        )

        // Verify createDelta extracts transferable settings and plugins, excluding sensitive keys
        val localDelta = engine.createDelta(accountId = 0, deviceId = "device-1", sinceTimestamp = 0L)
        assertEquals("Amoled", localDelta.settings["app_theme"])
        assertEquals("[\"pluginA\"]", localDelta.plugins["INSTALLED_PLUGINS_KEY"])
        assertFalse(localDelta.settings.containsKey("cloud_sync_token"))
        assertEquals(1, localDelta.accounts.size)
        assertEquals("Primary User", localDelta.accounts.first().name)

        // Verify applyDelta applies remote settings, plugins, and accounts
        val incomingDelta = SyncDelta(
            deviceId = "device-remote",
            accountId = 0,
            timestamp = 4000L,
            settings = kotlinx.collections.immutable.persistentMapOf(
                "subtitles_size" to "18",
                "cloud_sync_evil_token" to "hacked" // should be ignored
            ),
            plugins = kotlinx.collections.immutable.persistentMapOf(
                "REPOSITORIES_KEY" to "[\"repoX\"]"
            ),
            accounts = kotlinx.collections.immutable.persistentListOf(
                AccountEntity(keyIndex = 1, name = "Secondary Profile", accountUuid = "uuid-secondary")
            )
        )

        val result = engine.applyDelta(incomingDelta)
        assertEquals(1, result.settingsApplied)
        assertEquals(1, result.pluginsApplied)
        assertEquals(1, result.accountsApplied)

        assertEquals("18", database.appPreferenceDao().getString("subtitles_size"))
        assertEquals("[\"repoX\"]", database.appPreferenceDao().getString("REPOSITORIES_KEY"))
        assertNull(database.appPreferenceDao().getString("cloud_sync_evil_token"))

        val secondaryAccount = database.accountDao().getAccountById(1)
        assertNotNull(secondaryAccount)
        assertEquals("Secondary Profile", secondaryAccount.name)
        assertEquals("uuid-secondary", secondaryAccount.accountUuid)
    }
}
