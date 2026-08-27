package com.lagradost.cloudstream3.shared.persistence.database

import com.lagradost.cloudstream3.shared.persistence.dao.AccountDao
import com.lagradost.cloudstream3.shared.persistence.dao.AppPreferenceDao
import com.lagradost.cloudstream3.shared.persistence.dao.BookmarkDao
import com.lagradost.cloudstream3.shared.persistence.dao.DownloadCacheDao
import com.lagradost.cloudstream3.shared.persistence.dao.FavoriteDao
import com.lagradost.cloudstream3.shared.persistence.dao.ResumeWatchingDao
import com.lagradost.cloudstream3.shared.persistence.dao.SubscriptionDao
import com.lagradost.cloudstream3.shared.persistence.dao.SyncMappingDao
import com.lagradost.cloudstream3.shared.persistence.dao.WatchProgressDao
import com.lagradost.cloudstream3.shared.persistence.entity.AccountEntity
import com.lagradost.cloudstream3.shared.persistence.entity.AppPreferenceEntity
import com.lagradost.cloudstream3.shared.persistence.entity.BookmarkEntity
import com.lagradost.cloudstream3.shared.persistence.entity.DownloadEpisodeEntity
import com.lagradost.cloudstream3.shared.persistence.entity.DownloadHeaderEntity
import com.lagradost.cloudstream3.shared.persistence.entity.FavoriteEntity
import com.lagradost.cloudstream3.shared.persistence.entity.ResumeWatchingEntity
import com.lagradost.cloudstream3.shared.persistence.entity.SubscriptionEntity
import com.lagradost.cloudstream3.shared.persistence.entity.SyncMappingEntity
import com.lagradost.cloudstream3.shared.persistence.entity.WatchProgressEntity
import androidx.room.InvalidationTracker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Pure Kotlin Multiplatform thread-safe, reactive default database implementation for [AppDatabase].
 * Provides fallback persistence and in-memory caching without relying on runtime reflection.
 */
abstract class DefaultAppDatabase(
    storageDir: java.io.File? = null
) : AppDatabase() {

    private val _accountDao = DefaultAccountDao()
    private val _watchProgressDao = DefaultWatchProgressDao()
    private val _resumeWatchingDao = DefaultResumeWatchingDao()
    private val _bookmarkDao = DefaultBookmarkDao()
    private val _subscriptionDao = DefaultSubscriptionDao()
    private val _favoriteDao = DefaultFavoriteDao()
    private val _downloadCacheDao = DefaultDownloadCacheDao()
    private val _syncMappingDao = DefaultSyncMappingDao()
    private val _appPreferenceDao = DefaultAppPreferenceDao(
        storageFile = storageDir?.let { java.io.File(it, "preferences.properties") }
    )

    override fun accountDao(): AccountDao = _accountDao
    override fun watchProgressDao(): WatchProgressDao = _watchProgressDao
    override fun resumeWatchingDao(): ResumeWatchingDao = _resumeWatchingDao
    override fun bookmarkDao(): BookmarkDao = _bookmarkDao
    override fun subscriptionDao(): SubscriptionDao = _subscriptionDao
    override fun favoriteDao(): FavoriteDao = _favoriteDao
    override fun downloadCacheDao(): DownloadCacheDao = _downloadCacheDao
    override fun syncMappingDao(): SyncMappingDao = _syncMappingDao
    override fun appPreferenceDao(): AppPreferenceDao = _appPreferenceDao

    override fun createInvalidationTracker(): InvalidationTracker {
        return InvalidationTracker(
            this,
            emptyMap(),
            emptyMap(),
            "accounts",
            "watch_progress",
            "resume_watching",
            "bookmarks",
            "subscriptions",
            "favorites",
            "download_headers",
            "download_episodes",
            "sync_mappings",
            "app_preferences"
        )
    }
}

// -----------------------------------------------------------------------------
// DAO Implementations
// -----------------------------------------------------------------------------

internal class DefaultAccountDao : AccountDao {
    private val store = MutableStateFlow<Map<Int, AccountEntity>>(emptyMap())

    override fun getAllAccountsFlow(): Flow<List<AccountEntity>> = store.map { it.values.toList() }
    override suspend fun getAllAccounts(): List<AccountEntity> = store.value.values.toList()
    override suspend fun getAccountById(keyIndex: Int): AccountEntity? = store.value[keyIndex]
    override fun getAccountByIdFlow(keyIndex: Int): Flow<AccountEntity?> = store.map { it[keyIndex] }
    override suspend fun upsertAccount(account: AccountEntity) { store.update { it + (account.keyIndex to account) } }
    override suspend fun insertAccount(account: AccountEntity) { store.update { it + (account.keyIndex to account) } }
    override suspend fun updateAccount(account: AccountEntity) { store.update { it + (account.keyIndex to account) } }
    override suspend fun deleteAccountById(keyIndex: Int) { store.update { it - keyIndex } }
    override suspend fun getAccountCount(): Int = store.value.size
}

internal class DefaultAppPreferenceDao(
    private val storageFile: java.io.File? = null
) : AppPreferenceDao {
    private val store: MutableStateFlow<Map<String, AppPreferenceEntity>>

    init {
        val initial = mutableMapOf<String, AppPreferenceEntity>()
        if (storageFile != null && storageFile.exists()) {
            try {
                storageFile.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        val idx = line.indexOf('=')
                        if (idx > 0) {
                            val k = line.substring(0, idx)
                            val v = line.substring(idx + 1)
                            initial[k] = AppPreferenceEntity(key = k, value = v, updatedAt = 0L)
                        }
                    }
                }
            } catch (_: Throwable) {}
        }
        store = MutableStateFlow(initial)
    }

    private fun persist() {
        if (storageFile != null) {
            try {
                val parent = storageFile.parentFile
                if (parent != null && !parent.exists()) parent.mkdirs()
                val tempFile = java.io.File(storageFile.parentFile, "${storageFile.name}.tmp")
                tempFile.bufferedWriter().use { writer ->
                    store.value.forEach { (k, v) ->
                        writer.write("$k=${v.value}\n")
                    }
                }
                tempFile.renameTo(storageFile)
            } catch (_: Throwable) {}
        }
    }

    override suspend fun getPreference(key: String): AppPreferenceEntity? = store.value[key]
    override suspend fun getString(key: String): String? = store.value[key]?.value
    override fun getStringFlow(key: String): Flow<String?> = store.map { it[key]?.value }
    override suspend fun getPreferencesWithPrefix(prefix: String): List<AppPreferenceEntity> =
        store.value.filterKeys { it.startsWith(prefix) }.values.toList()

    override suspend fun getAllPreferences(): List<AppPreferenceEntity> =
        store.value.values.toList()

    override suspend fun upsertPreference(preference: AppPreferenceEntity) {
        store.update { it + (preference.key to preference) }
        persist()
    }

    override suspend fun insertPreferences(preferences: List<AppPreferenceEntity>) {
        store.update { it + preferences.associateBy { p -> p.key } }
        persist()
    }

    override suspend fun deletePreference(key: String) {
        store.update { it - key }
        persist()
    }

    override suspend fun deletePreferencesWithPrefix(prefix: String): Int {
        val count = store.value.count { it.key.startsWith(prefix) }
        store.update { it.filterKeys { k -> !k.startsWith(prefix) } }
        persist()
        return count
    }

    override suspend fun clearAll() {
        store.value = emptyMap()
        persist()
    }

    override fun getStringSync(key: String): String? = store.value[key]?.value

    override fun getPreferencesWithPrefixSync(prefix: String): List<AppPreferenceEntity> =
        store.value.filterKeys { it.startsWith(prefix) }.values.toList()

    override fun getAllPreferencesSync(): List<AppPreferenceEntity> =
        store.value.values.toList()

    override fun upsertPreferenceSync(preference: AppPreferenceEntity) {
        store.update { it + (preference.key to preference) }
        persist()
    }

    override fun deletePreferenceSync(key: String) {
        store.update { it - key }
        persist()
    }

    override fun deletePreferencesWithPrefixSync(prefix: String): Int {
        val count = store.value.count { it.key.startsWith(prefix) }
        store.update { it.filterKeys { k -> !k.startsWith(prefix) } }
        persist()
        return count
    }

    override fun clearAllSync() {
        store.value = emptyMap()
        persist()
    }
}

internal class DefaultBookmarkDao : BookmarkDao {
    private val store = MutableStateFlow<Map<Pair<Int, Int>, BookmarkEntity>>(emptyMap())

    override suspend fun getBookmark(accountId: Int, id: Int): BookmarkEntity? = store.value[accountId to id]
    override fun getBookmarkFlow(accountId: Int, id: Int): Flow<BookmarkEntity?> = store.map { it[accountId to id] }
    override suspend fun getAllBookmarks(accountId: Int): List<BookmarkEntity> =
        store.value.filterKeys { it.first == accountId }.values.toList()

    override fun getAllBookmarksFlow(accountId: Int): Flow<List<BookmarkEntity>> =
        store.map { it.filterKeys { k -> k.first == accountId }.values.toList() }

    override suspend fun getBookmarksByWatchType(accountId: Int, watchType: Int): List<BookmarkEntity> =
        store.value.filterKeys { it.first == accountId }.values.filter { it.watchType == watchType }

    override fun getBookmarksByWatchTypeFlow(accountId: Int, watchType: Int): Flow<List<BookmarkEntity>> =
        store.map { it.filterKeys { k -> k.first == accountId }.values.filter { b -> b.watchType == watchType } }

    override suspend fun getAllBookmarkIds(accountId: Int): List<Int> =
        store.value.filterKeys { it.first == accountId }.values.map { it.id }

    override suspend fun getWatchType(accountId: Int, id: Int): Int? =
        store.value[accountId to id]?.watchType

    override suspend fun upsertBookmark(bookmark: BookmarkEntity) {
        store.update { it + ((bookmark.accountId to bookmark.id) to bookmark) }
    }

    override suspend fun upsertAll(bookmarks: List<BookmarkEntity>) {
        store.update { it + bookmarks.associateBy { b -> b.accountId to b.id } }
    }

    override suspend fun deleteBookmark(accountId: Int, id: Int) {
        store.update { it - (accountId to id) }
    }

    override suspend fun clearAccountBookmarks(accountId: Int) {
        store.update { it.filterKeys { k -> k.first != accountId } }
    }

    override suspend fun delete(bookmark: BookmarkEntity) {
        deleteBookmark(bookmark.accountId, bookmark.id)
    }
}

internal class DefaultFavoriteDao : FavoriteDao {
    private val store = MutableStateFlow<Map<Pair<Int, Int>, FavoriteEntity>>(emptyMap())

    override suspend fun getFavorite(accountId: Int, id: Int): FavoriteEntity? = store.value[accountId to id]
    override fun getFavoriteFlow(accountId: Int, id: Int): Flow<FavoriteEntity?> = store.map { it[accountId to id] }
    override suspend fun getAllFavorites(accountId: Int): List<FavoriteEntity> =
        store.value.filterKeys { it.first == accountId }.values.toList()

    override fun getAllFavoritesFlow(accountId: Int): Flow<List<FavoriteEntity>> =
        store.map { it.filterKeys { k -> k.first == accountId }.values.toList() }

    override suspend fun upsertFavorite(favorite: FavoriteEntity) {
        store.update { it + ((favorite.accountId to favorite.id) to favorite) }
    }

    override suspend fun upsertAll(favorites: List<FavoriteEntity>) {
        store.update { it + favorites.associateBy { f -> f.accountId to f.id } }
    }

    override suspend fun deleteFavorite(accountId: Int, id: Int) {
        store.update { it - (accountId to id) }
    }

    override suspend fun clearAccountFavorites(accountId: Int) {
        store.update { it.filterKeys { k -> k.first != accountId } }
    }

    override suspend fun delete(favorite: FavoriteEntity) {
        deleteFavorite(favorite.accountId, favorite.id)
    }
}

internal class DefaultWatchProgressDao : WatchProgressDao {
    private val store = MutableStateFlow<Map<Pair<Int, Int>, WatchProgressEntity>>(emptyMap())

    override suspend fun getWatchProgress(accountId: Int, mediaId: Int): WatchProgressEntity? =
        store.value[accountId to mediaId]

    override fun getWatchProgressFlow(accountId: Int, mediaId: Int): Flow<WatchProgressEntity?> =
        store.map { it[accountId to mediaId] }

    override suspend fun getAllWatchProgress(accountId: Int): List<WatchProgressEntity> =
        store.value.filterKeys { it.first == accountId }.values.toList()

    override fun getAllWatchProgressFlow(accountId: Int): Flow<List<WatchProgressEntity>> =
        store.map { it.filterKeys { k -> k.first == accountId }.values.toList() }

    override suspend fun getAllMediaIds(accountId: Int): List<Int> =
        store.value.filterKeys { it.first == accountId }.values.map { it.mediaId }

    override suspend fun upsertWatchProgress(progress: WatchProgressEntity) {
        store.update { it + ((progress.accountId to progress.mediaId) to progress) }
    }

    override suspend fun upsertAll(progressList: List<WatchProgressEntity>) {
        store.update { it + progressList.associateBy { p -> p.accountId to p.mediaId } }
    }

    override suspend fun deleteWatchProgress(accountId: Int, mediaId: Int) {
        store.update { it - (accountId to mediaId) }
    }

    override suspend fun clearAccountProgress(accountId: Int) {
        store.update { it.filterKeys { k -> k.first != accountId } }
    }

    override suspend fun delete(progress: WatchProgressEntity) {
        deleteWatchProgress(progress.accountId, progress.mediaId)
    }
}

internal class DefaultResumeWatchingDao : ResumeWatchingDao {
    private val store = MutableStateFlow<Map<Pair<Int, Int>, ResumeWatchingEntity>>(emptyMap())

    override suspend fun getResumeWatching(accountId: Int, parentId: Int): ResumeWatchingEntity? =
        store.value[accountId to parentId]

    override fun getResumeWatchingFlow(accountId: Int, parentId: Int): Flow<ResumeWatchingEntity?> =
        store.map { it[accountId to parentId] }

    override suspend fun getAllResumeWatching(accountId: Int): List<ResumeWatchingEntity> =
        store.value.filterKeys { it.first == accountId }.values.toList()

    override fun getAllResumeWatchingFlow(accountId: Int): Flow<List<ResumeWatchingEntity>> =
        store.map { it.filterKeys { k -> k.first == accountId }.values.toList() }

    override suspend fun getAllResumeParentIds(accountId: Int): List<Int> =
        store.value.filterKeys { it.first == accountId }.values.map { it.parentId }

    override suspend fun upsertResumeWatching(entity: ResumeWatchingEntity) {
        store.update { it + ((entity.accountId to entity.parentId) to entity) }
    }

    override suspend fun upsertAll(entities: List<ResumeWatchingEntity>) {
        store.update { it + entities.associateBy { e -> e.accountId to e.parentId } }
    }

    override suspend fun deleteResumeWatching(accountId: Int, parentId: Int) {
        store.update { it - (accountId to parentId) }
    }

    override suspend fun clearAccountResumeWatching(accountId: Int) {
        store.update { it.filterKeys { k -> k.first != accountId } }
    }

    override suspend fun delete(entity: ResumeWatchingEntity) {
        deleteResumeWatching(entity.accountId, entity.parentId)
    }
}

internal class DefaultSubscriptionDao : SubscriptionDao {
    private val store = MutableStateFlow<Map<Pair<Int, Int>, SubscriptionEntity>>(emptyMap())

    override suspend fun getSubscription(accountId: Int, id: Int): SubscriptionEntity? =
        store.value[accountId to id]

    override fun getSubscriptionFlow(accountId: Int, id: Int): Flow<SubscriptionEntity?> =
        store.map { it[accountId to id] }

    override suspend fun getAllSubscriptions(accountId: Int): List<SubscriptionEntity> =
        store.value.filterKeys { it.first == accountId }.values.toList()

    override fun getAllSubscriptionsFlow(accountId: Int): Flow<List<SubscriptionEntity>> =
        store.map { it.filterKeys { k -> k.first == accountId }.values.toList() }

    override suspend fun upsertSubscription(subscription: SubscriptionEntity) {
        store.update { it + ((subscription.accountId to subscription.id) to subscription) }
    }

    override suspend fun upsertAll(subscriptions: List<SubscriptionEntity>) {
        store.update { it + subscriptions.associateBy { s -> s.accountId to s.id } }
    }

    override suspend fun deleteSubscription(accountId: Int, id: Int) {
        store.update { it - (accountId to id) }
    }

    override suspend fun clearAccountSubscriptions(accountId: Int) {
        store.update { it.filterKeys { k -> k.first != accountId } }
    }

    override suspend fun delete(subscription: SubscriptionEntity) {
        deleteSubscription(subscription.accountId, subscription.id)
    }
}

internal class DefaultSyncMappingDao : SyncMappingDao {
    private val store = MutableStateFlow<Map<Pair<String, Int>, SyncMappingEntity>>(emptyMap())

    override suspend fun getSyncMapping(accountId: Int, mediaId: Int, syncPrefix: String): SyncMappingEntity? =
        store.value[syncPrefix to mediaId]

    override suspend fun getSyncMappingsForMedia(accountId: Int, mediaId: Int): List<SyncMappingEntity> =
        store.value.values.filter { it.accountId == accountId && it.mediaId == mediaId }

    override fun getSyncMappingsForMediaFlow(accountId: Int, mediaId: Int): Flow<List<SyncMappingEntity>> =
        store.map { it.values.filter { m -> m.accountId == accountId && m.mediaId == mediaId } }

    override suspend fun upsertSyncMapping(mapping: SyncMappingEntity) {
        store.update { it + ((mapping.syncPrefix to mapping.mediaId) to mapping) }
    }

    override suspend fun insertSyncMappings(mappings: List<SyncMappingEntity>) {
        store.update { it + mappings.associateBy { m -> m.syncPrefix to m.mediaId } }
    }

    override suspend fun deleteSyncMapping(accountId: Int, mediaId: Int, syncPrefix: String) {
        store.update { it - (syncPrefix to mediaId) }
    }

    override suspend fun deleteSyncMappingsForMedia(accountId: Int, mediaId: Int) {
        store.update { it.filterValues { m -> !(m.accountId == accountId && m.mediaId == mediaId) } }
    }

    override suspend fun clearAccountSyncMappings(accountId: Int) {
        store.update { it.filterValues { m -> m.accountId != accountId } }
    }

    override suspend fun delete(mapping: SyncMappingEntity) {
        deleteSyncMapping(mapping.accountId, mapping.mediaId, mapping.syncPrefix)
    }
}

internal class DefaultDownloadCacheDao : DownloadCacheDao {
    private val headersState = MutableStateFlow<Map<Int, DownloadHeaderEntity>>(emptyMap())
    private val episodesState = MutableStateFlow<Map<Int, DownloadEpisodeEntity>>(emptyMap())

    override suspend fun getHeader(id: Int): DownloadHeaderEntity? = headersState.value[id]
    override suspend fun getAllHeaders(): List<DownloadHeaderEntity> = headersState.value.values.toList()
    override fun getAllHeadersFlow(): Flow<List<DownloadHeaderEntity>> = headersState.map { it.values.toList() }
    override suspend fun upsertHeader(header: DownloadHeaderEntity) { headersState.update { it + (header.id to header) } }
    override suspend fun insertHeaders(headers: List<DownloadHeaderEntity>) {
        headersState.update { it + headers.associateBy { h -> h.id } }
    }
    override suspend fun deleteHeader(id: Int) { headersState.update { it - id } }

    override suspend fun getEpisode(id: Int): DownloadEpisodeEntity? = episodesState.value[id]
    override suspend fun getEpisodesForParent(parentId: Int): List<DownloadEpisodeEntity> =
        episodesState.value.values.filter { it.parentId == parentId }
    override fun getEpisodesForParentFlow(parentId: Int): Flow<List<DownloadEpisodeEntity>> =
        episodesState.map { it.values.filter { e -> e.parentId == parentId } }
    override suspend fun upsertEpisode(episode: DownloadEpisodeEntity) { episodesState.update { it + (episode.id to episode) } }
    override suspend fun insertEpisodes(episodes: List<DownloadEpisodeEntity>) {
        episodesState.update { it + episodes.associateBy { e -> e.id } }
    }
    override suspend fun deleteEpisode(id: Int) { episodesState.update { it - id } }
    override suspend fun deleteEpisodesForParent(parentId: Int) {
        episodesState.update { it.filterValues { e -> e.parentId != parentId } }
    }
    fun clearAllTables() {
        // Fallback in-memory reset
    }
    override suspend fun clearAllHeaders() { headersState.value = emptyMap() }
    override suspend fun clearAllEpisodes() { episodesState.value = emptyMap() }
}
