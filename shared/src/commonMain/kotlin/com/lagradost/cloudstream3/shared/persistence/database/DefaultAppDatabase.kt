package com.lagradost.cloudstream3.shared.persistence.database

import androidx.room.InvalidationTracker
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.shared.persistence.dao.AccountDao
import com.lagradost.cloudstream3.shared.persistence.dao.AppPreferenceDao
import com.lagradost.cloudstream3.shared.persistence.dao.BookmarkDao
import com.lagradost.cloudstream3.shared.persistence.dao.DownloadCacheDao
import com.lagradost.cloudstream3.shared.persistence.dao.FavoriteDao
import com.lagradost.cloudstream3.shared.persistence.dao.ResumeWatchingDao
import com.lagradost.cloudstream3.shared.persistence.dao.SubscriptionDao
import com.lagradost.cloudstream3.shared.persistence.dao.SyncMappingDao
import com.lagradost.cloudstream3.shared.persistence.dao.SyncTombstoneDao
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
import com.lagradost.cloudstream3.shared.persistence.entity.SyncTombstoneEntity
import com.lagradost.cloudstream3.shared.persistence.entity.WatchProgressEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val jsonHelper = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
    encodeDefaults = true
}

private fun <T> loadEntitiesFromFile(
    storageFile: java.io.File?,
    serializer: kotlinx.serialization.KSerializer<List<T>>
): List<T> {
    if (storageFile == null || !storageFile.exists()) return emptyList()
    return try {
        val text = storageFile.readText()
        if (text.isBlank()) emptyList() else jsonHelper.decodeFromString(serializer, text)
    } catch (e: Exception) {
        logError(e)
        emptyList()
    }
}

private fun <T> persistEntitiesToFile(
    storageFile: java.io.File?,
    entities: List<T>,
    serializer: kotlinx.serialization.KSerializer<List<T>>
) {
    if (storageFile == null) return
    try {
        val parent = storageFile.parentFile
        if (parent != null && !parent.exists()) parent.mkdirs()
        val tempFile = java.io.File(parent, "${storageFile.name}.tmp")
        val content = jsonHelper.encodeToString(serializer, entities)
        tempFile.writeText(content)
        tempFile.renameTo(storageFile)
    } catch (e: Exception) {
        logError(e)
    }
}

abstract class DefaultAppDatabase(
    storageDir: java.io.File? = null
) : AppDatabase() {

    private val _accountDao = DefaultAccountDao(
        storageFile = storageDir?.let { java.io.File(it, "accounts.json") }
    )
    private val _watchProgressDao = DefaultWatchProgressDao(
        storageFile = storageDir?.let { java.io.File(it, "watch_progress.json") }
    )
    private val _resumeWatchingDao = DefaultResumeWatchingDao(
        storageFile = storageDir?.let { java.io.File(it, "resume_watching.json") }
    )
    private val _bookmarkDao = DefaultBookmarkDao(
        storageFile = storageDir?.let { java.io.File(it, "bookmarks.json") }
    )
    private val _subscriptionDao = DefaultSubscriptionDao(
        storageFile = storageDir?.let { java.io.File(it, "subscriptions.json") }
    )
    private val _favoriteDao = DefaultFavoriteDao(
        storageFile = storageDir?.let { java.io.File(it, "favorites.json") }
    )
    private val _downloadCacheDao = DefaultDownloadCacheDao()
    private val _syncMappingDao = DefaultSyncMappingDao()
    private val _syncTombstoneDao = DefaultSyncTombstoneDao(
        storageFile = storageDir?.let { java.io.File(it, "sync_tombstones.json") }
    )
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
    override fun syncTombstoneDao(): SyncTombstoneDao = _syncTombstoneDao
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
            "app_preferences",
            "sync_tombstones"
        )
    }
}

internal class DefaultAccountDao(
    private val storageFile: java.io.File? = null
) : AccountDao {
    private val store: MutableStateFlow<Map<Int, AccountEntity>>

    init {
        val initial = loadEntitiesFromFile(storageFile, ListSerializer(AccountEntity.serializer()))
            .associateBy { it.keyIndex }
        store = MutableStateFlow(initial)
    }

    private fun persist() {
        persistEntitiesToFile(storageFile, store.value.values.toList(), ListSerializer(AccountEntity.serializer()))
    }

    override fun getAllAccountsFlow(): Flow<List<AccountEntity>> = store.map { it.values.toList() }
    override suspend fun getAllAccounts(): List<AccountEntity> = store.value.values.toList()
    override suspend fun getAccountById(keyIndex: Int): AccountEntity? = store.value[keyIndex]
    override fun getAccountByIdFlow(keyIndex: Int): Flow<AccountEntity?> = store.map { it[keyIndex] }
    override suspend fun upsertAccount(account: AccountEntity) {
        store.update { it + (account.keyIndex to account) }
        persist()
    }
    override suspend fun insertAccount(account: AccountEntity) {
        store.update { it + (account.keyIndex to account) }
        persist()
    }
    override suspend fun updateAccount(account: AccountEntity) {
        store.update { it + (account.keyIndex to account) }
        persist()
    }
    override suspend fun deleteAccountById(keyIndex: Int) {
        store.update { it - keyIndex }
        persist()
    }
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
            } catch (e: Exception) {
                logError(e)
            }
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
            } catch (e: Exception) {
                logError(e)
            }
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

internal class DefaultBookmarkDao(
    private val storageFile: java.io.File? = null
) : BookmarkDao {
    private val store: MutableStateFlow<Map<Pair<Int, Int>, BookmarkEntity>>

    init {
        val initial = loadEntitiesFromFile(storageFile, ListSerializer(BookmarkEntity.serializer()))
            .associateBy { it.accountId to it.id }
        store = MutableStateFlow(initial)
    }

    private fun persist() {
        persistEntitiesToFile(storageFile, store.value.values.toList(), ListSerializer(BookmarkEntity.serializer()))
    }

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

    override suspend fun getBookmarksSince(accountId: Int, sinceTimestamp: Long): List<BookmarkEntity> =
        store.value.filterKeys { it.first == accountId }.values
            .filter { if (sinceTimestamp == 0L) true else it.latestUpdatedTime > sinceTimestamp }
            .sortedBy { it.latestUpdatedTime }

    override suspend fun getWatchType(accountId: Int, id: Int): Int? =
        store.value[accountId to id]?.watchType

    override suspend fun upsertBookmark(bookmark: BookmarkEntity) {
        store.update { it + ((bookmark.accountId to bookmark.id) to bookmark) }
        persist()
    }

    override suspend fun upsertAll(bookmarks: List<BookmarkEntity>) {
        store.update { it + bookmarks.associateBy { b -> b.accountId to b.id } }
        persist()
    }

    override suspend fun deleteBookmark(accountId: Int, id: Int) {
        store.update { it - (accountId to id) }
        persist()
    }

    override suspend fun clearAccountBookmarks(accountId: Int) {
        store.update { it.filterKeys { k -> k.first != accountId } }
        persist()
    }

    override suspend fun delete(bookmark: BookmarkEntity) {
        deleteBookmark(bookmark.accountId, bookmark.id)
    }
}

internal class DefaultFavoriteDao(
    private val storageFile: java.io.File? = null
) : FavoriteDao {
    private val store: MutableStateFlow<Map<Pair<Int, Int>, FavoriteEntity>>

    init {
        val initial = loadEntitiesFromFile(storageFile, ListSerializer(FavoriteEntity.serializer()))
            .associateBy { it.accountId to it.id }
        store = MutableStateFlow(initial)
    }

    private fun persist() {
        persistEntitiesToFile(storageFile, store.value.values.toList(), ListSerializer(FavoriteEntity.serializer()))
    }

    override suspend fun getFavorite(accountId: Int, id: Int): FavoriteEntity? = store.value[accountId to id]
    override fun getFavoriteFlow(accountId: Int, id: Int): Flow<FavoriteEntity?> = store.map { it[accountId to id] }
    override suspend fun getAllFavorites(accountId: Int): List<FavoriteEntity> =
        store.value.filterKeys { it.first == accountId }.values.toList()

    override fun getAllFavoritesFlow(accountId: Int): Flow<List<FavoriteEntity>> =
        store.map { it.filterKeys { k -> k.first == accountId }.values.toList() }

    override suspend fun getFavoritesSince(accountId: Int, sinceTimestamp: Long): List<FavoriteEntity> =
        store.value.filterKeys { it.first == accountId }.values
            .filter { if (sinceTimestamp == 0L) true else it.latestUpdatedTime > sinceTimestamp }
            .sortedBy { it.latestUpdatedTime }

    override suspend fun upsertFavorite(favorite: FavoriteEntity) {
        store.update { it + ((favorite.accountId to favorite.id) to favorite) }
        persist()
    }

    override suspend fun upsertAll(favorites: List<FavoriteEntity>) {
        store.update { it + favorites.associateBy { f -> f.accountId to f.id } }
        persist()
    }

    override suspend fun deleteFavorite(accountId: Int, id: Int) {
        store.update { it - (accountId to id) }
        persist()
    }

    override suspend fun clearAccountFavorites(accountId: Int) {
        store.update { it.filterKeys { k -> k.first != accountId } }
        persist()
    }

    override suspend fun delete(favorite: FavoriteEntity) {
        deleteFavorite(favorite.accountId, favorite.id)
    }
}

internal class DefaultWatchProgressDao(
    private val storageFile: java.io.File? = null
) : WatchProgressDao {
    private val store: MutableStateFlow<Map<Pair<Int, Int>, WatchProgressEntity>>

    init {
        val initial = loadEntitiesFromFile(storageFile, ListSerializer(WatchProgressEntity.serializer()))
            .associateBy { it.accountId to it.mediaId }
        store = MutableStateFlow(initial)
    }

    private fun persist() {
        persistEntitiesToFile(storageFile, store.value.values.toList(), ListSerializer(WatchProgressEntity.serializer()))
    }

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

    override suspend fun getWatchProgressSince(accountId: Int, sinceTimestamp: Long): List<WatchProgressEntity> =
        store.value.filterKeys { it.first == accountId }.values
            .filter { if (sinceTimestamp == 0L) true else it.lastUpdated > sinceTimestamp }
            .sortedBy { it.lastUpdated }

    override suspend fun upsertWatchProgress(progress: WatchProgressEntity) {
        store.update { it + ((progress.accountId to progress.mediaId) to progress) }
        persist()
    }

    override suspend fun upsertAll(progressList: List<WatchProgressEntity>) {
        store.update { it + progressList.associateBy { p -> p.accountId to p.mediaId } }
        persist()
    }

    override suspend fun deleteWatchProgress(accountId: Int, mediaId: Int) {
        store.update { it - (accountId to mediaId) }
        persist()
    }

    override suspend fun clearAccountProgress(accountId: Int) {
        store.update { it.filterKeys { k -> k.first != accountId } }
        persist()
    }

    override suspend fun delete(progress: WatchProgressEntity) {
        deleteWatchProgress(progress.accountId, progress.mediaId)
    }
}

internal class DefaultResumeWatchingDao(
    private val storageFile: java.io.File? = null
) : ResumeWatchingDao {
    private val store: MutableStateFlow<Map<Pair<Int, Int>, ResumeWatchingEntity>>

    init {
        val initial = loadEntitiesFromFile(storageFile, ListSerializer(ResumeWatchingEntity.serializer()))
            .associateBy { it.accountId to it.parentId }
        store = MutableStateFlow(initial)
    }

    private fun persist() {
        persistEntitiesToFile(storageFile, store.value.values.toList(), ListSerializer(ResumeWatchingEntity.serializer()))
    }

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
        persist()
    }

    override suspend fun upsertAll(entities: List<ResumeWatchingEntity>) {
        store.update { it + entities.associateBy { e -> e.accountId to e.parentId } }
        persist()
    }

    override suspend fun deleteResumeWatching(accountId: Int, parentId: Int) {
        store.update { it - (accountId to parentId) }
        persist()
    }

    override suspend fun clearAccountResumeWatching(accountId: Int) {
        store.update { it.filterKeys { k -> k.first != accountId } }
        persist()
    }

    override suspend fun delete(entity: ResumeWatchingEntity) {
        deleteResumeWatching(entity.accountId, entity.parentId)
    }
}

internal class DefaultSubscriptionDao(
    private val storageFile: java.io.File? = null
) : SubscriptionDao {
    private val store: MutableStateFlow<Map<Pair<Int, Int>, SubscriptionEntity>>

    init {
        val initial = loadEntitiesFromFile(storageFile, ListSerializer(SubscriptionEntity.serializer()))
            .associateBy { it.accountId to it.id }
        store = MutableStateFlow(initial)
    }

    private fun persist() {
        persistEntitiesToFile(storageFile, store.value.values.toList(), ListSerializer(SubscriptionEntity.serializer()))
    }

    override suspend fun getSubscription(accountId: Int, id: Int): SubscriptionEntity? =
        store.value[accountId to id]

    override fun getSubscriptionFlow(accountId: Int, id: Int): Flow<SubscriptionEntity?> =
        store.map { it[accountId to id] }

    override suspend fun getAllSubscriptions(accountId: Int): List<SubscriptionEntity> =
        store.value.filterKeys { it.first == accountId }.values.toList()

    override fun getAllSubscriptionsFlow(accountId: Int): Flow<List<SubscriptionEntity>> =
        store.map { it.filterKeys { k -> k.first == accountId }.values.toList() }

    override suspend fun getSubscriptionsSince(accountId: Int, sinceTimestamp: Long): List<SubscriptionEntity> =
        store.value.filterKeys { it.first == accountId }.values
            .filter { if (sinceTimestamp == 0L) true else it.latestUpdatedTime > sinceTimestamp }
            .sortedBy { it.latestUpdatedTime }

    override suspend fun upsertSubscription(subscription: SubscriptionEntity) {
        store.update { it + ((subscription.accountId to subscription.id) to subscription) }
        persist()
    }

    override suspend fun upsertAll(subscriptions: List<SubscriptionEntity>) {
        store.update { it + subscriptions.associateBy { s -> s.accountId to s.id } }
        persist()
    }

    override suspend fun deleteSubscription(accountId: Int, id: Int) {
        store.update { it - (accountId to id) }
        persist()
    }

    override suspend fun clearAccountSubscriptions(accountId: Int) {
        store.update { it.filterKeys { k -> k.first != accountId } }
        persist()
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
    }
    override suspend fun clearAllHeaders() { headersState.value = emptyMap() }
    override suspend fun clearAllEpisodes() { episodesState.value = emptyMap() }
}

internal class DefaultSyncTombstoneDao(
    private val storageFile: java.io.File? = null
) : SyncTombstoneDao {
    private val store: MutableStateFlow<Map<Triple<Int, String, String>, SyncTombstoneEntity>>

    init {
        val initial = loadEntitiesFromFile(storageFile, ListSerializer(SyncTombstoneEntity.serializer()))
            .associateBy { Triple(it.accountId, it.entityType, it.entityId) }
        store = MutableStateFlow(initial)
    }

    private fun persist() {
        persistEntitiesToFile(storageFile, store.value.values.toList(), ListSerializer(SyncTombstoneEntity.serializer()))
    }

    override suspend fun getTombstonesSince(accountId: Int, sinceTimestamp: Long): List<SyncTombstoneEntity> =
        store.value.values.filter { it.accountId == accountId && (if (sinceTimestamp == 0L) true else it.deletedAt > sinceTimestamp) }

    override suspend fun getTombstone(accountId: Int, entityType: String, entityId: String): SyncTombstoneEntity? =
        store.value[Triple(accountId, entityType, entityId)]

    override suspend fun upsertTombstone(tombstone: SyncTombstoneEntity) {
        store.update { it + (Triple(tombstone.accountId, tombstone.entityType, tombstone.entityId) to tombstone) }
        persist()
    }

    override suspend fun upsertAll(tombstones: List<SyncTombstoneEntity>) {
        store.update { it + tombstones.associateBy { t -> Triple(t.accountId, t.entityType, t.entityId) } }
        persist()
    }

    override suspend fun deleteTombstone(accountId: Int, entityType: String, entityId: String) {
        store.update { it - Triple(accountId, entityType, entityId) }
        persist()
    }

    override suspend fun purgeOldTombstones(cutoffTimestamp: Long) {
        store.update { it.filterValues { t -> t.deletedAt >= cutoffTimestamp } }
        persist()
    }
}
