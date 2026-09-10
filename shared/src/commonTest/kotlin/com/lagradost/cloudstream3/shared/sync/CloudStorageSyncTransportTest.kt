package com.lagradost.cloudstream3.shared.sync

import com.lagradost.cloudstream3.shared.persistence.database.DefaultAppDatabase
import com.lagradost.cloudstream3.shared.persistence.entity.BookmarkEntity
import com.lagradost.cloudstream3.shared.persistence.entity.WatchProgressEntity
import com.lagradost.cloudstream3.shared.sync.cloud.LocalStorageClient
import com.lagradost.cloudstream3.shared.sync.engine.SyncEngineImpl
import com.lagradost.cloudstream3.shared.sync.layout.CloudFileLayoutMapper
import com.lagradost.cloudstream3.shared.sync.models.BookmarkDelta
import com.lagradost.cloudstream3.shared.sync.models.SyncDelta
import com.lagradost.cloudstream3.shared.sync.models.TombstoneDelta
import com.lagradost.cloudstream3.shared.sync.models.WatchProgressDelta
import com.lagradost.cloudstream3.shared.sync.transport.CloudStorageSyncTransport
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CloudStorageSyncTransportTest {

    private lateinit var tempDir: File
    private lateinit var storageClient: LocalStorageClient
    private lateinit var transport: CloudStorageSyncTransport

    @BeforeTest
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "cs3_sync_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        storageClient = LocalStorageClient(tempDir.absolutePath)
        transport = CloudStorageSyncTransport(storageClient)
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testCloudFileLayoutMapperPathsAndParsing() {
        val watchPath = CloudFileLayoutMapper.getWatchProgressPath("acc-123", 456)
        assertEquals("user_data/acc-123/watch_progress/456.json", watchPath)
        assertEquals(456, CloudFileLayoutMapper.extractMediaIdFromWatchProgressPath(watchPath))

        val bookmarkPath = CloudFileLayoutMapper.getBookmarkPath("acc-123", 789)
        assertEquals("user_data/acc-123/bookmarks/789.json", bookmarkPath)
        assertEquals(789, CloudFileLayoutMapper.extractIdFromBookmarkPath(bookmarkPath))

        val favPath = CloudFileLayoutMapper.getFavoritePath("acc-123", 999)
        assertEquals("user_data/acc-123/favorites/999.json", favPath)
        assertEquals(999, CloudFileLayoutMapper.extractIdFromFavoritePath(favPath))

        val tombstonePath = CloudFileLayoutMapper.getTombstonePath("acc-123", "watch_progress", "456")
        assertEquals("user_data/acc-123/tombstones/watch_progress_456.json", tombstonePath)
        val tombstoneInfo = CloudFileLayoutMapper.extractTombstoneInfoFromPath(tombstonePath)
        assertNotNull(tombstoneInfo)
        assertEquals("watch_progress", tombstoneInfo.first)
        assertEquals("456", tombstoneInfo.second)

        assertEquals("acc-123", CloudFileLayoutMapper.extractAccountUuidFromPath(watchPath))
    }

    @Test
    fun testPushGranularFilesToStorage() = runTest {
        val delta = SyncDelta(
            deviceId = "test-device",
            accountId = 1,
            accountUuid = "user-uuid-1",
            timestamp = 1000L,
            watchProgress = persistentListOf(
                WatchProgressDelta(
                    mediaId = 555,
                    position = 30_000L,
                    duration = 120_000L,
                    watchState = 1,
                    updatedAt = 1000L
                )
            ),
            bookmarks = persistentListOf(
                BookmarkDelta(
                    id = 777,
                    name = "Sousou no Frieren",
                    url = "https://example.com/frieren",
                    apiName = "provider",
                    watchType = 1,
                    updatedAt = 1000L
                )
            )
        )

        val pushResult = transport.pushDelta(delta)
        assertTrue(pushResult.isSuccess)

        // Verify granular files exist in the directory structure
        val progressFile = File(tempDir, "user_data/user-uuid-1/watch_progress/555.json")
        assertTrue(progressFile.exists(), "Watch progress file should exist")
        assertTrue(progressFile.readText().contains("30000"))

        val bookmarkFile = File(tempDir, "user_data/user-uuid-1/bookmarks/777.json")
        assertTrue(bookmarkFile.exists(), "Bookmark file should exist")
        assertTrue(bookmarkFile.readText().contains("Sousou no Frieren"))
    }

    @Test
    fun testTombstonePushesAndCleansPositiveFile() = runTest {
        // First push bookmark 888
        val initialDelta = SyncDelta(
            deviceId = "device-a",
            accountId = 1,
            accountUuid = "user-uuid-1",
            timestamp = 1000L,
            bookmarks = persistentListOf(
                BookmarkDelta(
                    id = 888,
                    name = "Steins;Gate",
                    url = "https://example.com/sg",
                    apiName = "provider",
                    watchType = 1,
                    updatedAt = 1000L
                )
            )
        )
        transport.pushDelta(initialDelta).getOrThrow()

        val bookmarkFile = File(tempDir, "user_data/user-uuid-1/bookmarks/888.json")
        assertTrue(bookmarkFile.exists())

        // Now push tombstone for bookmark 888
        val tombstoneDelta = SyncDelta(
            deviceId = "device-a",
            accountId = 1,
            accountUuid = "user-uuid-1",
            timestamp = 2000L,
            tombstones = persistentListOf(
                TombstoneDelta(
                    entityType = "bookmark",
                    entityId = "888",
                    deletedAt = 2000L
                )
            )
        )
        transport.pushDelta(tombstoneDelta).getOrThrow()

        // Positive file should be deleted
        assertFalse(bookmarkFile.exists(), "Positive bookmark file should be cleaned up by tombstone")

        // Tombstone file should exist
        val tombstoneFile = File(tempDir, "user_data/user-uuid-1/tombstones/bookmark_888.json")
        assertTrue(tombstoneFile.exists(), "Tombstone file should exist")
    }

    @Test
    fun testFetchAndSyncEngineEndToEndWithCloudStorageTransport() = runTest {
        val database = object : DefaultAppDatabase(null) {}
        val engine = SyncEngineImpl(database) { 3000L }

        // Device A pushes initial data to the cloud storage
        val deltaFromDeviceA = SyncDelta(
            deviceId = "device-a",
            accountId = 0,
            accountUuid = "main-user",
            timestamp = 1000L,
            watchProgress = persistentListOf(
                WatchProgressDelta(
                    mediaId = 123,
                    position = 50_000L,
                    duration = 100_000L,
                    watchState = 1,
                    updatedAt = 1000L
                )
            )
        )
        transport.pushDelta(deltaFromDeviceA).getOrThrow()

        // Device B sets local bookmark
        database.bookmarkDao().upsertBookmark(
            BookmarkEntity(
                accountId = 0,
                id = 456,
                name = "Hunter x Hunter",
                url = "https://example.com/hxh",
                apiName = "ani",
                latestUpdatedTime = 2000L
            )
        )

        // Device B executes sync against the cloud transport
        val syncResult = engine.sync(
            accountId = 0,
            deviceId = "device-b",
            transport = transport,
            lastSyncTimestamp = 0L
        ).getOrThrow()

        assertEquals(1, syncResult.watchProgressApplied)

        // Verify Device B merged the watch progress from Device A
        val mergedProgress = database.watchProgressDao().getWatchProgress(0, 123)
        assertNotNull(mergedProgress)
        assertEquals(50_000L, mergedProgress.position)

        // Verify Device B's local bookmark was pushed to cloud storage
        val hxhCloudFile = File(tempDir, "user_data/0/bookmarks/456.json")
        assertTrue(hxhCloudFile.exists(), "Bookmark from Device B should have been pushed to cloud")
    }

    @Test
    fun testPushAndFetchSettingsPluginsAndAccounts() = runTest {
        val delta = SyncDelta(
            deviceId = "device-sender",
            accountId = 0,
            timestamp = 2000L,
            settings = kotlinx.collections.immutable.persistentMapOf("key1" to "val1"),
            plugins = kotlinx.collections.immutable.persistentMapOf("pluginA" to "repoA"),
            accounts = kotlinx.collections.immutable.persistentListOf(
                com.lagradost.cloudstream3.shared.persistence.entity.AccountEntity(keyIndex = 0, name = "Cloud User", accountUuid = "uuid-cloud")
            )
        )
        transport.pushDelta(delta).getOrThrow()

        val appSettingsFile = File(tempDir, "settings/app_settings.json")
        val pluginsFile = File(tempDir, "settings/plugins.json")
        val accountsFile = File(tempDir, "settings/accounts.json")

        assertTrue(appSettingsFile.exists(), "settings/app_settings.json should exist")
        assertTrue(pluginsFile.exists(), "settings/plugins.json should exist")
        assertTrue(accountsFile.exists(), "settings/accounts.json should exist")

        val fetchedDeltas = transport.fetchDeltas(0L).getOrThrow()
        val settingsDelta = fetchedDeltas.firstOrNull { it.settings.isNotEmpty() }
        assertNotNull(settingsDelta)
        assertEquals("val1", settingsDelta.settings["key1"])
        assertEquals("repoA", settingsDelta.plugins["pluginA"])
        assertEquals(1, settingsDelta.accounts.size)
        assertEquals("Cloud User", settingsDelta.accounts.first().name)
    }
}
