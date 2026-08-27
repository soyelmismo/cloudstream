package com.lagradost.cloudstream3.shared.persistence.repository

import com.lagradost.cloudstream3.APIHolder
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
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface AccountRepository {
    fun getAllAccountsFlow(): Flow<ImmutableList<AccountEntity>>
    suspend fun getAllAccounts(): ImmutableList<AccountEntity>
    suspend fun getAccount(keyIndex: Int): AccountEntity?
    suspend fun saveAccount(account: AccountEntity)
    suspend fun deleteAccount(keyIndex: Int)
}

class AccountRepositoryImpl(private val dao: AccountDao) : AccountRepository {
    override fun getAllAccountsFlow(): Flow<ImmutableList<AccountEntity>> =
        dao.getAllAccountsFlow().map { it.toImmutableList() }

    override suspend fun getAllAccounts(): ImmutableList<AccountEntity> =
        dao.getAllAccounts().toImmutableList()

    override suspend fun getAccount(keyIndex: Int): AccountEntity? = dao.getAccountById(keyIndex)
    override suspend fun saveAccount(account: AccountEntity) = dao.upsertAccount(account)
    override suspend fun deleteAccount(keyIndex: Int) = dao.deleteAccountById(keyIndex)
}

interface WatchProgressRepository {
    suspend fun getProgress(accountId: Int, mediaId: Int): WatchProgressEntity?
    fun getProgressFlow(accountId: Int, mediaId: Int): Flow<WatchProgressEntity?>
    suspend fun getAllProgress(accountId: Int): ImmutableList<WatchProgressEntity>
    fun getAllProgressFlow(accountId: Int): Flow<ImmutableList<WatchProgressEntity>>
    suspend fun setProgress(accountId: Int, mediaId: Int, position: Long, duration: Long, watchState: Int = 0)
    suspend fun deleteProgress(accountId: Int, mediaId: Int)
    suspend fun clearProgress(accountId: Int)
}

class WatchProgressRepositoryImpl(private val dao: WatchProgressDao) : WatchProgressRepository {
    override suspend fun getProgress(accountId: Int, mediaId: Int): WatchProgressEntity? =
        dao.getWatchProgress(accountId, mediaId)

    override fun getProgressFlow(accountId: Int, mediaId: Int): Flow<WatchProgressEntity?> =
        dao.getWatchProgressFlow(accountId, mediaId)

    override suspend fun getAllProgress(accountId: Int): ImmutableList<WatchProgressEntity> =
        dao.getAllWatchProgress(accountId).toImmutableList()

    override fun getAllProgressFlow(accountId: Int): Flow<ImmutableList<WatchProgressEntity>> =
        dao.getAllWatchProgressFlow(accountId).map { it.toImmutableList() }

    override suspend fun setProgress(
        accountId: Int,
        mediaId: Int,
        position: Long,
        duration: Long,
        watchState: Int
    ) {
        dao.upsertWatchProgress(
            WatchProgressEntity(
                accountId = accountId,
                mediaId = mediaId,
                position = position,
                duration = duration,
                watchState = watchState,
                lastUpdated = APIHolder.unixTimeMS
            )
        )
    }

    override suspend fun deleteProgress(accountId: Int, mediaId: Int) =
        dao.deleteWatchProgress(accountId, mediaId)

    override suspend fun clearProgress(accountId: Int) =
        dao.clearAccountProgress(accountId)
}

interface ResumeWatchingRepository {
    suspend fun getResumeWatching(accountId: Int, parentId: Int): ResumeWatchingEntity?
    fun getResumeWatchingFlow(accountId: Int, parentId: Int): Flow<ResumeWatchingEntity?>
    suspend fun getAllResumeWatching(accountId: Int): ImmutableList<ResumeWatchingEntity>
    fun getAllResumeWatchingFlow(accountId: Int): Flow<ImmutableList<ResumeWatchingEntity>>
    suspend fun setResumeWatching(
        accountId: Int,
        parentId: Int,
        episodeId: Int?,
        episode: Int?,
        season: Int?,
        isFromDownload: Boolean,
        updateTime: Long? = null
    )
    suspend fun saveResumeWatching(resumeWatching: ResumeWatchingEntity)
    suspend fun deleteResumeWatching(accountId: Int, parentId: Int)
    suspend fun clearAll(accountId: Int)
}

class ResumeWatchingRepositoryImpl(private val dao: ResumeWatchingDao) : ResumeWatchingRepository {
    override suspend fun getResumeWatching(accountId: Int, parentId: Int): ResumeWatchingEntity? =
        dao.getResumeWatching(accountId, parentId)

    override fun getResumeWatchingFlow(accountId: Int, parentId: Int): Flow<ResumeWatchingEntity?> =
        dao.getResumeWatchingFlow(accountId, parentId)

    override suspend fun getAllResumeWatching(accountId: Int): ImmutableList<ResumeWatchingEntity> =
        dao.getAllResumeWatching(accountId).toImmutableList()

    override fun getAllResumeWatchingFlow(accountId: Int): Flow<ImmutableList<ResumeWatchingEntity>> =
        dao.getAllResumeWatchingFlow(accountId).map { it.toImmutableList() }

    override suspend fun setResumeWatching(
        accountId: Int,
        parentId: Int,
        episodeId: Int?,
        episode: Int?,
        season: Int?,
        isFromDownload: Boolean,
        updateTime: Long?
    ) {
        val time = updateTime ?: APIHolder.unixTimeMS
        dao.upsertResumeWatching(
            ResumeWatchingEntity(
                accountId = accountId,
                parentId = parentId,
                episodeId = episodeId,
                episode = episode,
                season = season,
                isFromDownload = isFromDownload,
                updateTime = time
            )
        )
    }

    override suspend fun saveResumeWatching(resumeWatching: ResumeWatchingEntity) =
        dao.upsertResumeWatching(resumeWatching)

    override suspend fun deleteResumeWatching(accountId: Int, parentId: Int) =
        dao.deleteResumeWatching(accountId, parentId)

    override suspend fun clearAll(accountId: Int) =
        dao.clearAccountResumeWatching(accountId)
}

interface BookmarkRepository {
    suspend fun getBookmark(accountId: Int, id: Int): BookmarkEntity?
    fun getBookmarkFlow(accountId: Int, id: Int): Flow<BookmarkEntity?>
    suspend fun getAllBookmarks(accountId: Int): ImmutableList<BookmarkEntity>
    fun getAllBookmarksFlow(accountId: Int): Flow<ImmutableList<BookmarkEntity>>
    suspend fun getBookmarksByWatchType(accountId: Int, watchType: Int): ImmutableList<BookmarkEntity>
    fun getBookmarksByWatchTypeFlow(accountId: Int, watchType: Int): Flow<ImmutableList<BookmarkEntity>>
    suspend fun saveBookmark(bookmark: BookmarkEntity)
    suspend fun deleteBookmark(accountId: Int, id: Int)
    suspend fun clearAll(accountId: Int)
}

class BookmarkRepositoryImpl(private val dao: BookmarkDao) : BookmarkRepository {
    override suspend fun getBookmark(accountId: Int, id: Int): BookmarkEntity? =
        dao.getBookmark(accountId, id)

    override fun getBookmarkFlow(accountId: Int, id: Int): Flow<BookmarkEntity?> =
        dao.getBookmarkFlow(accountId, id)

    override suspend fun getAllBookmarks(accountId: Int): ImmutableList<BookmarkEntity> =
        dao.getAllBookmarks(accountId).toImmutableList()

    override fun getAllBookmarksFlow(accountId: Int): Flow<ImmutableList<BookmarkEntity>> =
        dao.getAllBookmarksFlow(accountId).map { it.toImmutableList() }

    override suspend fun getBookmarksByWatchType(accountId: Int, watchType: Int): ImmutableList<BookmarkEntity> =
        dao.getBookmarksByWatchType(accountId, watchType).toImmutableList()

    override fun getBookmarksByWatchTypeFlow(accountId: Int, watchType: Int): Flow<ImmutableList<BookmarkEntity>> =
        dao.getBookmarksByWatchTypeFlow(accountId, watchType).map { it.toImmutableList() }

    override suspend fun saveBookmark(bookmark: BookmarkEntity) =
        dao.upsertBookmark(bookmark)

    override suspend fun deleteBookmark(accountId: Int, id: Int) =
        dao.deleteBookmark(accountId, id)

    override suspend fun clearAll(accountId: Int) =
        dao.clearAccountBookmarks(accountId)
}

interface SubscriptionRepository {
    suspend fun getSubscription(accountId: Int, id: Int): SubscriptionEntity?
    fun getSubscriptionFlow(accountId: Int, id: Int): Flow<SubscriptionEntity?>
    suspend fun getAllSubscriptions(accountId: Int): ImmutableList<SubscriptionEntity>
    fun getAllSubscriptionsFlow(accountId: Int): Flow<ImmutableList<SubscriptionEntity>>
    suspend fun saveSubscription(subscription: SubscriptionEntity)
    suspend fun deleteSubscription(accountId: Int, id: Int)
    suspend fun clearAll(accountId: Int)
}

class SubscriptionRepositoryImpl(private val dao: SubscriptionDao) : SubscriptionRepository {
    override suspend fun getSubscription(accountId: Int, id: Int): SubscriptionEntity? =
        dao.getSubscription(accountId, id)

    override fun getSubscriptionFlow(accountId: Int, id: Int): Flow<SubscriptionEntity?> =
        dao.getSubscriptionFlow(accountId, id)

    override suspend fun getAllSubscriptions(accountId: Int): ImmutableList<SubscriptionEntity> =
        dao.getAllSubscriptions(accountId).toImmutableList()

    override fun getAllSubscriptionsFlow(accountId: Int): Flow<ImmutableList<SubscriptionEntity>> =
        dao.getAllSubscriptionsFlow(accountId).map { it.toImmutableList() }

    override suspend fun saveSubscription(subscription: SubscriptionEntity) =
        dao.upsertSubscription(subscription)

    override suspend fun deleteSubscription(accountId: Int, id: Int) =
        dao.deleteSubscription(accountId, id)

    override suspend fun clearAll(accountId: Int) =
        dao.clearAccountSubscriptions(accountId)
}

interface FavoriteRepository {
    suspend fun getFavorite(accountId: Int, id: Int): FavoriteEntity?
    fun getFavoriteFlow(accountId: Int, id: Int): Flow<FavoriteEntity?>
    suspend fun getAllFavorites(accountId: Int): ImmutableList<FavoriteEntity>
    fun getAllFavoritesFlow(accountId: Int): Flow<ImmutableList<FavoriteEntity>>
    suspend fun saveFavorite(favorite: FavoriteEntity)
    suspend fun deleteFavorite(accountId: Int, id: Int)
    suspend fun clearAll(accountId: Int)
}

class FavoriteRepositoryImpl(private val dao: FavoriteDao) : FavoriteRepository {
    override suspend fun getFavorite(accountId: Int, id: Int): FavoriteEntity? =
        dao.getFavorite(accountId, id)

    override fun getFavoriteFlow(accountId: Int, id: Int): Flow<FavoriteEntity?> =
        dao.getFavoriteFlow(accountId, id)

    override suspend fun getAllFavorites(accountId: Int): ImmutableList<FavoriteEntity> =
        dao.getAllFavorites(accountId).toImmutableList()

    override fun getAllFavoritesFlow(accountId: Int): Flow<ImmutableList<FavoriteEntity>> =
        dao.getAllFavoritesFlow(accountId).map { it.toImmutableList() }

    override suspend fun saveFavorite(favorite: FavoriteEntity) =
        dao.upsertFavorite(favorite)

    override suspend fun deleteFavorite(accountId: Int, id: Int) =
        dao.deleteFavorite(accountId, id)

    override suspend fun clearAll(accountId: Int) =
        dao.clearAccountFavorites(accountId)
}

interface DownloadCacheRepository {
    suspend fun getHeader(id: Int): DownloadHeaderEntity?
    suspend fun getAllHeaders(): ImmutableList<DownloadHeaderEntity>
    suspend fun saveHeader(header: DownloadHeaderEntity)
    suspend fun deleteHeader(id: Int)

    suspend fun getEpisode(id: Int): DownloadEpisodeEntity?
    suspend fun getEpisodesForParent(parentId: Int): ImmutableList<DownloadEpisodeEntity>
    suspend fun saveEpisode(episode: DownloadEpisodeEntity)
    suspend fun deleteEpisode(id: Int)
}

class DownloadCacheRepositoryImpl(private val dao: DownloadCacheDao) : DownloadCacheRepository {
    override suspend fun getHeader(id: Int): DownloadHeaderEntity? = dao.getHeader(id)
    override suspend fun getAllHeaders(): ImmutableList<DownloadHeaderEntity> = dao.getAllHeaders().toImmutableList()
    override suspend fun saveHeader(header: DownloadHeaderEntity) = dao.upsertHeader(header)
    override suspend fun deleteHeader(id: Int) = dao.deleteHeader(id)

    override suspend fun getEpisode(id: Int): DownloadEpisodeEntity? = dao.getEpisode(id)
    override suspend fun getEpisodesForParent(parentId: Int): ImmutableList<DownloadEpisodeEntity> = dao.getEpisodesForParent(parentId).toImmutableList()
    override suspend fun saveEpisode(episode: DownloadEpisodeEntity) = dao.upsertEpisode(episode)
    override suspend fun deleteEpisode(id: Int) = dao.deleteEpisode(id)
}

interface AppPreferenceRepository {
    suspend fun getString(key: String, defaultValue: String? = null): String?
    fun getStringFlow(key: String): Flow<String?>
    suspend fun setString(key: String, value: String)
    suspend fun getInt(key: String, defaultValue: Int = 0): Int
    suspend fun setInt(key: String, value: Int)
    suspend fun getBoolean(key: String, defaultValue: Boolean = false): Boolean
    suspend fun setBoolean(key: String, value: Boolean)
    suspend fun getStringSet(key: String, defaultValue: Set<String>? = null): ImmutableSet<String>?
    suspend fun setStringSet(key: String, value: Set<String>)
    suspend fun getKeys(prefix: String = ""): ImmutableList<String>
    suspend fun removeKeys(prefix: String): Int
    suspend fun deletePreference(key: String)
    suspend fun clearAll()

    fun getStringSync(key: String, defaultValue: String? = null): String?
    fun getIntSync(key: String, defaultValue: Int = 0): Int
    fun getBooleanSync(key: String, defaultValue: Boolean = false): Boolean
    fun getStringSetSync(key: String, defaultValue: Set<String>? = null): ImmutableSet<String>?
    fun setStringSync(key: String, value: String)
    fun setIntSync(key: String, value: Int)
    fun setBooleanSync(key: String, value: Boolean)
    fun setStringSetSync(key: String, value: Set<String>)
    fun deletePreferenceSync(key: String)
    fun getKeysSync(prefix: String = ""): ImmutableList<String>
    fun removeKeysSync(prefix: String): Int
    fun getAllSync(): ImmutableMap<String, String>
}

class AppPreferenceRepositoryImpl(
    private val dao: AppPreferenceDao,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }
) : AppPreferenceRepository {
    override suspend fun getString(key: String, defaultValue: String?): String? =
        dao.getString(key) ?: defaultValue

    override fun getStringFlow(key: String): Flow<String?> =
        dao.getStringFlow(key)

    override suspend fun setString(key: String, value: String) {
        dao.upsertPreference(
            AppPreferenceEntity(
                key = key,
                value = value,
                updatedAt = APIHolder.unixTimeMS
            )
        )
    }

    override suspend fun getInt(key: String, defaultValue: Int): Int =
        dao.getString(key)?.toIntOrNull() ?: defaultValue

    override suspend fun setInt(key: String, value: Int) {
        setString(key, value.toString())
    }

    override suspend fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        val str = dao.getString(key) ?: return defaultValue
        return str.toBooleanStrictOrNull() ?: (str.toIntOrNull()?.let { it != 0 } ?: defaultValue)
    }

    override suspend fun setBoolean(key: String, value: Boolean) {
        setString(key, value.toString())
    }

    override suspend fun getStringSet(key: String, defaultValue: Set<String>?): ImmutableSet<String>? {
        val raw = dao.getString(key) ?: return defaultValue?.toImmutableSet()
        return parseStringSet(raw, defaultValue)?.toImmutableSet()
    }

    override suspend fun setStringSet(key: String, value: Set<String>) {
        setString(key, json.encodeToString(value))
    }

    override suspend fun getKeys(prefix: String): ImmutableList<String> =
        dao.getPreferencesWithPrefix(prefix).map { it.key }.toImmutableList()

    override suspend fun removeKeys(prefix: String): Int =
        dao.deletePreferencesWithPrefix(prefix)

    override suspend fun deletePreference(key: String) =
        dao.deletePreference(key)

    override suspend fun clearAll() =
        dao.clearAll()

    override fun getStringSync(key: String, defaultValue: String?): String? =
        dao.getStringSync(key) ?: defaultValue

    override fun getIntSync(key: String, defaultValue: Int): Int =
        dao.getStringSync(key)?.toIntOrNull() ?: defaultValue

    override fun getBooleanSync(key: String, defaultValue: Boolean): Boolean {
        val str = dao.getStringSync(key) ?: return defaultValue
        return str.toBooleanStrictOrNull() ?: (str.toIntOrNull()?.let { it != 0 } ?: defaultValue)
    }

    override fun getStringSetSync(key: String, defaultValue: Set<String>?): ImmutableSet<String>? {
        val raw = dao.getStringSync(key) ?: return defaultValue?.toImmutableSet()
        return parseStringSet(raw, defaultValue)?.toImmutableSet()
    }

    override fun setStringSync(key: String, value: String) {
        dao.upsertPreferenceSync(
            AppPreferenceEntity(
                key = key,
                value = value,
                updatedAt = APIHolder.unixTimeMS
            )
        )
    }

    override fun setIntSync(key: String, value: Int) {
        setStringSync(key, value.toString())
    }

    override fun setBooleanSync(key: String, value: Boolean) {
        setStringSync(key, value.toString())
    }

    override fun setStringSetSync(key: String, value: Set<String>) {
        setStringSync(key, json.encodeToString(value))
    }

    override fun deletePreferenceSync(key: String) {
        dao.deletePreferenceSync(key)
    }

    override fun getKeysSync(prefix: String): ImmutableList<String> =
        dao.getPreferencesWithPrefixSync(prefix).map { it.key }.toImmutableList()

    override fun removeKeysSync(prefix: String): Int =
        dao.deletePreferencesWithPrefixSync(prefix)

    override fun getAllSync(): ImmutableMap<String, String> =
        dao.getAllPreferencesSync().associate { it.key to it.value }.toImmutableMap()

    private fun parseRawCsvSet(raw: String, defaultValue: Set<String>?): Set<String>? {
        return raw.removePrefix("[")
            .removeSuffix("]")
            .split(",")
            .map { it.trim().trim('"', '\'') }
            .filter { it.isNotBlank() }
            .toSet()
            .ifEmpty { defaultValue }
    }

    private fun parseJsonListOrSet(raw: String): Set<String>? {
        return try {
            json.decodeFromString<Set<String>>(raw)
        } catch (_: Exception) {
            try {
                json.decodeFromString<List<String>>(raw).toSet()
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun parseStringSet(raw: String, defaultValue: Set<String>?): Set<String>? {
        val parsedJson = parseJsonListOrSet(raw)
        if (parsedJson != null) return parsedJson
        return parseRawCsvSet(raw, defaultValue)
    }
}

interface SyncMappingRepository {
    suspend fun getSyncMapping(accountId: Int, mediaId: Int, syncPrefix: String): SyncMappingEntity?
    suspend fun getSyncMappings(accountId: Int, mediaId: Int): ImmutableList<SyncMappingEntity>
    fun getSyncMappingsFlow(accountId: Int, mediaId: Int): Flow<ImmutableList<SyncMappingEntity>>
    suspend fun saveSyncMapping(mapping: SyncMappingEntity)
    suspend fun deleteSyncMapping(accountId: Int, mediaId: Int, syncPrefix: String)
    suspend fun clearSyncMappings(accountId: Int, mediaId: Int)
}

class SyncMappingRepositoryImpl(
    private val dao: SyncMappingDao
) : SyncMappingRepository {
    override suspend fun getSyncMapping(accountId: Int, mediaId: Int, syncPrefix: String) =
        dao.getSyncMapping(accountId, mediaId, syncPrefix)

    override suspend fun getSyncMappings(accountId: Int, mediaId: Int): ImmutableList<SyncMappingEntity> =
        dao.getSyncMappingsForMedia(accountId, mediaId).toImmutableList()

    override fun getSyncMappingsFlow(accountId: Int, mediaId: Int): Flow<ImmutableList<SyncMappingEntity>> =
        dao.getSyncMappingsForMediaFlow(accountId, mediaId).map { it.toImmutableList() }

    override suspend fun saveSyncMapping(mapping: SyncMappingEntity) =
        dao.upsertSyncMapping(mapping)

    override suspend fun deleteSyncMapping(accountId: Int, mediaId: Int, syncPrefix: String) =
        dao.deleteSyncMapping(accountId, mediaId, syncPrefix)

    override suspend fun clearSyncMappings(accountId: Int, mediaId: Int) =
        dao.deleteSyncMappingsForMedia(accountId, mediaId)
}
