package com.lagradost.cloudstream3.shared.persistence.migration

import com.lagradost.cloudstream3.APIHolder.unixTimeMS
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.shared.persistence.database.AppDatabase
import com.lagradost.cloudstream3.shared.persistence.entity.AccountEntity
import com.lagradost.cloudstream3.shared.persistence.entity.BookmarkEntity
import com.lagradost.cloudstream3.shared.persistence.entity.FavoriteEntity
import com.lagradost.cloudstream3.shared.persistence.entity.ResumeWatchingEntity
import com.lagradost.cloudstream3.shared.persistence.entity.SubscriptionEntity
import com.lagradost.cloudstream3.shared.persistence.entity.WatchProgressEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object DataStoreToRoomMigrator {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Serializable
    private data class LegacyAccount(
        val keyIndex: Int,
        val name: String,
        val customImage: String? = null,
        val defaultImageIndex: Int = 0,
        val lockPin: String? = null
    )

    @Serializable
    private data class LegacyPosDur(
        val position: Long,
        val duration: Long
    )

    @Serializable
    private data class LegacyResumeWatching(
        val parentId: Int,
        val episodeId: Int? = null,
        val episode: Int? = null,
        val season: Int? = null,
        val updateTime: Long = 0L,
        val isFromDownload: Boolean = false
    )

    @Serializable
    private data class LegacyBookmarkData(
        val bookmarkedTime: Long = 0L,
        val id: Int? = null,
        val latestUpdatedTime: Long = 0L,
        val name: String = "",
        val url: String = "",
        val apiName: String = "",
        val type: TvType? = null,
        val posterUrl: String? = null,
        val year: Int? = null,
        val syncData: Map<String, String>? = null,
        val quality: SearchQuality? = null,
        val posterHeaders: Map<String, String>? = null,
        val plot: String? = null,
        val tags: List<String>? = null
    )

    @Serializable
    private data class LegacySubscribedData(
        val subscribedTime: Long = 0L,
        val lastSeenEpisodeCount: Map<String, Int?>? = null,
        val id: Int? = null,
        val latestUpdatedTime: Long = 0L,
        val name: String = "",
        val url: String = "",
        val apiName: String = "",
        val type: TvType? = null,
        val posterUrl: String? = null,
        val year: Int? = null
    )

    @Serializable
    private data class LegacyFavoritesData(
        val favoritesTime: Long = 0L,
        val id: Int? = null,
        val latestUpdatedTime: Long = 0L,
        val name: String = "",
        val url: String = "",
        val apiName: String = "",
        val type: TvType? = null,
        val posterUrl: String? = null,
        val year: Int? = null
    )

    private data class ParsedKey(
        val accountId: Int,
        val category: String,
        val itemKey: String
    )

    interface LegacyStoreReader {
        fun getAllKeys(): Set<String>
        fun getString(key: String): String?
    }

    private fun parseAccounts(accountsJson: String?): List<LegacyAccount> {
        if (accountsJson.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString<List<LegacyAccount>>(accountsJson)
        } catch (e: Exception) {
            logError(e)
            emptyList()
        }
    }

    private suspend fun migrateAccounts(reader: LegacyStoreReader, database: AppDatabase): Int {
        val accounts = parseAccounts(reader.getString("data_store_helper/account"))
        accounts.forEach { acc ->
            database.accountDao().upsertAccount(
                AccountEntity(
                    keyIndex = acc.keyIndex,
                    name = acc.name,
                    customImage = acc.customImage,
                    defaultImageIndex = acc.defaultImageIndex,
                    lockPin = acc.lockPin
                )
            )
        }
        return accounts.size
    }

    private suspend fun migrateWatchProgress(database: AppDatabase, accountId: Int, itemKey: String, jsonVal: String): Int {
        val mediaId = itemKey.toIntOrNull() ?: return 0
        val posDur = json.decodeFromString<LegacyPosDur>(jsonVal)
        database.watchProgressDao().upsertWatchProgress(
            WatchProgressEntity(
                accountId = accountId,
                mediaId = mediaId,
                position = posDur.position,
                duration = posDur.duration,
                lastUpdated = unixTimeMS
            )
        )
        return 1
    }

    private suspend fun migrateResumeWatching(database: AppDatabase, accountId: Int, itemKey: String, jsonVal: String): Int {
        val parentId = itemKey.toIntOrNull() ?: return 0
        val resume = json.decodeFromString<LegacyResumeWatching>(jsonVal)
        database.resumeWatchingDao().upsertResumeWatching(
            ResumeWatchingEntity(
                accountId = accountId,
                parentId = parentId,
                episodeId = resume.episodeId,
                episode = resume.episode,
                season = resume.season,
                isFromDownload = resume.isFromDownload,
                updateTime = resume.updateTime
            )
        )
        return 1
    }

    private suspend fun migrateBookmark(database: AppDatabase, accountId: Int, itemKey: String, jsonVal: String): Int {
        val id = itemKey.toIntOrNull() ?: return 0
        val bookmark = json.decodeFromString<LegacyBookmarkData>(jsonVal)
        database.bookmarkDao().upsertBookmark(
            BookmarkEntity(
                accountId = accountId,
                id = id,
                name = bookmark.name,
                url = bookmark.url,
                apiName = bookmark.apiName,
                type = bookmark.type,
                posterUrl = bookmark.posterUrl,
                year = bookmark.year,
                bookmarkedTime = bookmark.bookmarkedTime,
                latestUpdatedTime = bookmark.latestUpdatedTime,
                quality = bookmark.quality,
                plot = bookmark.plot
            )
        )
        return 1
    }

    private suspend fun migrateSubscription(database: AppDatabase, accountId: Int, itemKey: String, jsonVal: String): Int {
        val id = itemKey.toIntOrNull() ?: return 0
        val sub = json.decodeFromString<LegacySubscribedData>(jsonVal)
        database.subscriptionDao().upsertSubscription(
            SubscriptionEntity(
                accountId = accountId,
                id = id,
                name = sub.name,
                url = sub.url,
                apiName = sub.apiName,
                type = sub.type,
                posterUrl = sub.posterUrl,
                year = sub.year,
                subscribedTime = sub.subscribedTime,
                latestUpdatedTime = sub.latestUpdatedTime
            )
        )
        return 1
    }

    private suspend fun migrateFavorite(database: AppDatabase, accountId: Int, itemKey: String, jsonVal: String): Int {
        val id = itemKey.toIntOrNull() ?: return 0
        val fav = json.decodeFromString<LegacyFavoritesData>(jsonVal)
        database.favoriteDao().upsertFavorite(
            FavoriteEntity(
                accountId = accountId,
                id = id,
                name = fav.name,
                url = fav.url,
                apiName = fav.apiName,
                type = fav.type,
                posterUrl = fav.posterUrl,
                favoritesTime = fav.favoritesTime
            )
        )
        return 1
    }

    private suspend fun dispatchCategoryMigration(
        database: AppDatabase,
        category: String,
        accountId: Int,
        itemKey: String,
        jsonVal: String
    ): Int = when (category) {
        "video_pos_dur" -> migrateWatchProgress(database, accountId, itemKey, jsonVal)
        "result_resume_watching_2", "result_resume_watching" -> migrateResumeWatching(database, accountId, itemKey, jsonVal)
        "result_watch_state_data" -> migrateBookmark(database, accountId, itemKey, jsonVal)
        "result_subscribed_state_data" -> migrateSubscription(database, accountId, itemKey, jsonVal)
        "result_favorites_state_data" -> migrateFavorite(database, accountId, itemKey, jsonVal)
        else -> 0
    }

    private suspend fun migrateCategory(
        database: AppDatabase,
        category: String,
        accountId: Int,
        itemKey: String,
        jsonVal: String
    ): Int = try {
        dispatchCategoryMigration(database, category, accountId, itemKey, jsonVal)
    } catch (e: Exception) {
        logError(e)
        0
    }

    private fun parseKey(key: String): ParsedKey? {
        val parts = key.split("/")
        if (parts.size < 3) return null
        val accountId = parts[0].toIntOrNull() ?: 0
        val category = parts[1]
        val itemKey = parts.subList(2, parts.size).joinToString("/")
        return ParsedKey(accountId, category, itemKey)
    }

    private suspend fun migrateKey(reader: LegacyStoreReader, database: AppDatabase, key: String): Int {
        val parsed = parseKey(key) ?: return 0
        val jsonVal = reader.getString(key) ?: return 0
        return migrateCategory(database, parsed.category, parsed.accountId, parsed.itemKey, jsonVal)
    }

    private suspend fun migrateEntries(reader: LegacyStoreReader, database: AppDatabase, keys: Set<String>): Int {
        var count = 0
        for (key in keys) {
            count += migrateKey(reader, database, key)
        }
        return count
    }

    suspend fun migrate(reader: LegacyStoreReader, database: AppDatabase): Int {
        val accountsCount = migrateAccounts(reader, database)
        val entriesCount = migrateEntries(reader, database, reader.getAllKeys())
        return accountsCount + entriesCount
    }
}
