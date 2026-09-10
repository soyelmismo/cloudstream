package com.lagradost.cloudstream3.shared.backup

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.shared.persistence.database.AppDatabase
import com.lagradost.cloudstream3.shared.persistence.migration.DataStoreToRoomMigrator
import com.lagradost.cloudstream3.shared.persistence.repository.AppPreferenceRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Interface defining the cross-platform Backup and Restore operations.
 */
interface BackupManager {
    /**
     * Creates a formatted JSON backup string containing the selected categories.
     * @param categories Set of [BackupCategory] to include in the backup payload.
     * @return Formatted JSON representation of [BackupPayload].
     */
    suspend fun createBackup(categories: Set<BackupCategory> = BackupCategory.entries.toSet()): String

    /**
     * Restores data from a backup JSON string (supporting both modern [BackupPayload] and [LegacyBackupFile]).
     * @param jsonContent The raw JSON string containing backup data.
     * @param selectedCategories Optional filter of categories to restore. If null, all available categories in payload are restored.
     * @return [BackupRestoreResult.Success] with restored details or [BackupRestoreResult.Error] upon failure.
     */
    suspend fun restoreBackup(
        jsonContent: String,
        selectedCategories: Set<BackupCategory>? = null
    ): BackupRestoreResult

    companion object {
        /**
         * Keys containing sensitive auth tokens, hardware/biometric bindings,
         * or local file paths that must NEVER be exported or shared.
         */
        val nonTransferableKeys: Set<String> = setOf(
            "account_token",
            "account_ids",
            "biometric_key",
            "nginx_user",
            "download_path_key",
            "download_path_key_visual",
            "backup_path_key",
            "backup_dir_path_key",
            "backup_dir_key",
            "backup_key",
            "automatic_backup_key",
            "anilist_token",
            "anilist_user",
            "mal_user",
            "mal_token",
            "mal_refresh_token",
            "mal_unixtime",
            "open_subtitles_user",
            "subdl_user",
            "simkl_token",
            "download_episode_cache",
            "BACKUP_download_episode_cache",
            "download_info",
            "download_resume_queue_key",
            "download_resume_2",
            "download_queue_key",
            "auto_download_plugins_key2",
            "anilist_cached_list",
            "mal_cached_list",
            "kitsu_cached_list"
        )

        /**
         * Known preference keys related specifically to plugin and extension management.
         */
        val pluginKeys: Set<String> = setOf(
            "REPOSITORIES_KEY",
            "INSTALLED_PLUGINS_KEY",
            "PLUGINS_KEY",
            "PLUGINS_KEY_LOCAL",
            "PLUGINS_KEY_HEADER"
        )
    }
}

/**
 * Default implementation of [BackupManager] backed by Room [AppDatabase] and [AppPreferenceRepository].
 */
class BackupManagerImpl(
    private val database: AppDatabase,
    private val preferenceRepo: AppPreferenceRepository
) : BackupManager {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
        encodeDefaults = true
    }

    private fun isKeyTransferable(key: String): Boolean {
        val lower = key.lowercase()
        return !BackupManager.nonTransferableKeys.any { lower.contains(it.lowercase()) }
    }

    private fun isPluginKey(key: String): Boolean {
        val upper = key.uppercase()
        return BackupManager.pluginKeys.any { upper.contains(it) } ||
                upper.startsWith("PLUGIN_") ||
                upper.startsWith("REPOSITORY_") ||
                upper.startsWith("PLUGINS_")
    }

    override suspend fun createBackup(categories: Set<BackupCategory>): String {
        val accounts = database.accountDao().getAllAccounts()
        val allAccountIds = (accounts.map { it.keyIndex } + 0).distinct()

        val settingsMap = if (BackupCategory.SETTINGS in categories || BackupCategory.PLUGINS in categories) {
            val allPrefs = preferenceRepo.getAllSync()
            allPrefs.filter { (key, _) ->
                if (!isKeyTransferable(key)) return@filter false
                val isPlugin = isPluginKey(key)
                if (isPlugin) {
                    BackupCategory.PLUGINS in categories
                } else {
                    BackupCategory.SETTINGS in categories
                }
            }
        } else null

        val watchProgressList = if (BackupCategory.WATCH_PROGRESS in categories) {
            allAccountIds.flatMap { accountId ->
                database.watchProgressDao().getAllWatchProgress(accountId)
            }.distinctBy { it.accountId to it.mediaId }
        } else null

        val resumeWatchingList = if (BackupCategory.WATCH_PROGRESS in categories) {
            allAccountIds.flatMap { accountId ->
                database.resumeWatchingDao().getAllResumeWatching(accountId)
            }.distinctBy { it.accountId to it.parentId }
        } else null

        val bookmarksList = if (BackupCategory.BOOKMARKS in categories) {
            allAccountIds.flatMap { accountId ->
                database.bookmarkDao().getAllBookmarks(accountId)
            }.distinctBy { it.accountId to it.id }
        } else null

        val favoritesList = if (BackupCategory.BOOKMARKS in categories) {
            allAccountIds.flatMap { accountId ->
                database.favoriteDao().getAllFavorites(accountId)
            }.distinctBy { it.accountId to it.id }
        } else null

        val subscriptionsList = if (BackupCategory.BOOKMARKS in categories) {
            allAccountIds.flatMap { accountId ->
                database.subscriptionDao().getAllSubscriptions(accountId)
            }.distinctBy { it.accountId to it.id }
        } else null

        val accountsList = if (BackupCategory.SYNC_ACCOUNTS in categories) {
            accounts.ifEmpty { null }
        } else null

        val payload = BackupPayload(
            version = 2,
            createdAt = APIHolder.unixTimeMS,
            categories = categories,
            settings = settingsMap,
            watchProgress = watchProgressList,
            resumeWatching = resumeWatchingList,
            bookmarks = bookmarksList,
            favorites = favoritesList,
            subscriptions = subscriptionsList,
            accounts = accountsList
        )

        return json.encodeToString(payload)
    }

    override suspend fun restoreBackup(
        jsonContent: String,
        selectedCategories: Set<BackupCategory>?
    ): BackupRestoreResult {
        if (jsonContent.isBlank()) {
            return BackupRestoreResult.Error("Backup content is empty")
        }

        // 1. Attempt to parse as modern BackupPayload (version >= 2)
        try {
            val payload = json.decodeFromString<BackupPayload>(jsonContent)
            val hasData = payload.settings != null ||
                    !payload.watchProgress.isNullOrEmpty() ||
                    !payload.resumeWatching.isNullOrEmpty() ||
                    !payload.bookmarks.isNullOrEmpty() ||
                    !payload.favorites.isNullOrEmpty() ||
                    !payload.subscriptions.isNullOrEmpty() ||
                    !payload.accounts.isNullOrEmpty()

            if (payload.version >= 2 || hasData) {
                val categoriesToRestore = selectedCategories ?: payload.categories.ifEmpty { BackupCategory.entries.toSet() }
                var itemsRestored = 0
                val restoredCategories = mutableSetOf<BackupCategory>()

                val restoreSettings = BackupCategory.SETTINGS in categoriesToRestore
                val restorePlugins = BackupCategory.PLUGINS in categoriesToRestore

                if ((restoreSettings || restorePlugins) && payload.settings != null) {
                    payload.settings.forEach { (key, value) ->
                        if (isKeyTransferable(key)) {
                            val isPlugin = isPluginKey(key)
                            if (isPlugin && restorePlugins) {
                                preferenceRepo.setString(key, value)
                                itemsRestored++
                            } else if (!isPlugin && restoreSettings) {
                                preferenceRepo.setString(key, value)
                                itemsRestored++
                            }
                        }
                    }
                    if (restoreSettings) restoredCategories.add(BackupCategory.SETTINGS)
                    if (restorePlugins) restoredCategories.add(BackupCategory.PLUGINS)
                }

                if (BackupCategory.WATCH_PROGRESS in categoriesToRestore) {
                    var progressRestored = false
                    if (!payload.watchProgress.isNullOrEmpty()) {
                        database.watchProgressDao().upsertAll(payload.watchProgress)
                        itemsRestored += payload.watchProgress.size
                        progressRestored = true
                    }
                    if (!payload.resumeWatching.isNullOrEmpty()) {
                        database.resumeWatchingDao().upsertAll(payload.resumeWatching)
                        itemsRestored += payload.resumeWatching.size
                        progressRestored = true
                    }
                    if (progressRestored) {
                        restoredCategories.add(BackupCategory.WATCH_PROGRESS)
                    }
                }

                if (BackupCategory.BOOKMARKS in categoriesToRestore) {
                    var bookmarkRestored = false
                    if (!payload.bookmarks.isNullOrEmpty()) {
                        database.bookmarkDao().upsertAll(payload.bookmarks)
                        itemsRestored += payload.bookmarks.size
                        bookmarkRestored = true
                    }
                    if (!payload.favorites.isNullOrEmpty()) {
                        database.favoriteDao().upsertAll(payload.favorites)
                        itemsRestored += payload.favorites.size
                        bookmarkRestored = true
                    }
                    if (!payload.subscriptions.isNullOrEmpty()) {
                        database.subscriptionDao().upsertAll(payload.subscriptions)
                        itemsRestored += payload.subscriptions.size
                        bookmarkRestored = true
                    }
                    if (bookmarkRestored) {
                        restoredCategories.add(BackupCategory.BOOKMARKS)
                    }
                }

                if (BackupCategory.SYNC_ACCOUNTS in categoriesToRestore && !payload.accounts.isNullOrEmpty()) {
                    payload.accounts.forEach { acc ->
                        database.accountDao().upsertAccount(acc)
                        itemsRestored++
                    }
                    restoredCategories.add(BackupCategory.SYNC_ACCOUNTS)
                }

                return BackupRestoreResult.Success(
                    restoredCategories = restoredCategories,
                    itemsCount = itemsRestored
                )
            }
        } catch (_: Throwable) {
            // Fallthrough to legacy parser
        }

        // 2. Fallback: Parse as LegacyBackupFile (Legacy Android Datastore format)
        try {
            val legacy = json.decodeFromString<LegacyBackupFile>(jsonContent)
            if (legacy.datastore != null || legacy.settings != null) {
                var itemsRestored = 0
                val restoredCategories = mutableSetOf<BackupCategory>()
                val categoriesToRestore = selectedCategories ?: BackupCategory.entries.toSet()

                val legacyStoreMap = mutableMapOf<String, String>()

                fun extractMap(vars: LegacyBackupVars?) {
                    if (vars == null) return
                    vars.string?.forEach { (k, v) -> legacyStoreMap[k] = v }
                    listOf(vars.bool, vars.int, vars.long, vars.float).forEach { map ->
                        map?.forEach { (k, v) -> legacyStoreMap[k] = v.toString() }
                    }
                    vars.stringSet?.forEach { (k, v) ->
                        if (v != null) {
                            legacyStoreMap[k] = json.encodeToString(v)
                        }
                    }
                }

                extractMap(legacy.datastore)
                extractMap(legacy.settings)

                if (BackupCategory.SETTINGS in categoriesToRestore || BackupCategory.PLUGINS in categoriesToRestore) {
                    legacyStoreMap.forEach { (key, value) ->
                        if (isKeyTransferable(key)) {
                            val isPlugin = isPluginKey(key)
                            if (isPlugin && BackupCategory.PLUGINS in categoriesToRestore) {
                                preferenceRepo.setString(key, value)
                                itemsRestored++
                                restoredCategories.add(BackupCategory.PLUGINS)
                            } else if (!isPlugin && BackupCategory.SETTINGS in categoriesToRestore) {
                                preferenceRepo.setString(key, value)
                                itemsRestored++
                                restoredCategories.add(BackupCategory.SETTINGS)
                            }
                        }
                    }
                }

                // Migrate legacy DataStore JSON entities into Room database
                val reader = object : DataStoreToRoomMigrator.LegacyStoreReader {
                    override fun getAllKeys(): Set<String> = legacyStoreMap.keys
                    override fun getString(key: String): String? = legacyStoreMap[key]
                }

                val migratedDbRecords = DataStoreToRoomMigrator.migrate(reader, database)
                if (migratedDbRecords > 0) {
                    itemsRestored += migratedDbRecords
                    if (BackupCategory.WATCH_PROGRESS in categoriesToRestore) restoredCategories.add(BackupCategory.WATCH_PROGRESS)
                    if (BackupCategory.BOOKMARKS in categoriesToRestore) restoredCategories.add(BackupCategory.BOOKMARKS)
                    if (BackupCategory.SYNC_ACCOUNTS in categoriesToRestore) restoredCategories.add(BackupCategory.SYNC_ACCOUNTS)
                }

                return BackupRestoreResult.Success(
                    restoredCategories = restoredCategories.ifEmpty { categoriesToRestore },
                    itemsCount = itemsRestored
                )
            }
        } catch (_: Throwable) {
            // Fallthrough
        }

        return BackupRestoreResult.Error("Could not parse backup content. Invalid or corrupted format.")
    }
}

/**
 * Factory function creating a [BackupManager] instance.
 */
fun BackupManager(
    database: AppDatabase,
    preferenceRepo: AppPreferenceRepository
): BackupManager = BackupManagerImpl(database, preferenceRepo)
