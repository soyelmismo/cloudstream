package com.lagradost.cloudstream3.shared.persistence.migration

import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.shared.persistence.database.AppDatabase
import com.lagradost.cloudstream3.shared.persistence.entity.AccountEntity
import com.lagradost.cloudstream3.shared.persistence.entity.BookmarkEntity
import com.lagradost.cloudstream3.shared.persistence.entity.DownloadEpisodeEntity
import com.lagradost.cloudstream3.shared.persistence.entity.DownloadHeaderEntity
import com.lagradost.cloudstream3.shared.persistence.entity.FavoriteEntity
import com.lagradost.cloudstream3.shared.persistence.entity.ResumeWatchingEntity
import com.lagradost.cloudstream3.APIHolder.unixTimeMS
import com.lagradost.cloudstream3.shared.persistence.entity.SubscriptionEntity
import com.lagradost.cloudstream3.shared.persistence.entity.SyncMappingEntity
import com.lagradost.cloudstream3.shared.persistence.entity.WatchProgressEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Migration helper to migrate legacy SharedPreferences / DataStore JSON data into Room Multiplatform.
 */
object DataStoreToRoomMigrator {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // Helper data classes matching legacy JSON models
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

    /**
     * Interface for reading legacy key-value data during migration.
     */
    interface LegacyStoreReader {
        fun getAllKeys(): Set<String>
        fun getString(key: String): String?
    }

    /**
     * Executes the migration process from the legacy store into Room database.
     * @return Number of total records migrated across all tables.
     */
    suspend fun migrate(reader: LegacyStoreReader, database: AppDatabase): Int {
        var totalMigrated = 0
        val allKeys = reader.getAllKeys()

        // 1. Migrate Accounts
        val accountsJson = reader.getString("data_store_helper/account")
        if (accountsJson != null) {
            try {
                val accounts = json.decodeFromString<List<LegacyAccount>>(accountsJson)
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
                    totalMigrated++
                }
            } catch (_: Throwable) {}
        }

        // 2. Iterate through all keys to find user account prefixed keys
        allKeys.forEach { key ->
            val parts = key.split("/")
            if (parts.size >= 3) {
                val accountId = parts[0].toIntOrNull() ?: 0
                val category = parts[1]
                val itemKey = parts.subList(2, parts.size).joinToString("/")
                val jsonVal = reader.getString(key) ?: return@forEach

                try {
                    when (category) {
                        "video_pos_dur" -> {
                            val mediaId = itemKey.toIntOrNull()
                            if (mediaId != null) {
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
                                totalMigrated++
                            }
                        }
                        "result_resume_watching_2", "result_resume_watching" -> {
                            val parentId = itemKey.toIntOrNull()
                            if (parentId != null) {
                                val resume = json.decodeFromString<LegacyResumeWatching>(jsonVal)
                                database.resumeWatchingDao().upsertResumeWatching(
                                    ResumeWatchingEntity(
                                        accountId = accountId,
                                        parentId = resume.parentId,
                                        episodeId = resume.episodeId,
                                        episode = resume.episode,
                                        season = resume.season,
                                        isFromDownload = resume.isFromDownload,
                                        updateTime = resume.updateTime
                                    )
                                )
                                totalMigrated++
                            }
                        }
                        "result_watch_state_data" -> {
                            val id = itemKey.toIntOrNull()
                            if (id != null) {
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
                                totalMigrated++
                            }
                        }
                        "result_subscribed_state_data" -> {
                            val id = itemKey.toIntOrNull()
                            if (id != null) {
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
                                totalMigrated++
                            }
                        }
                        "result_favorites_state_data" -> {
                            val id = itemKey.toIntOrNull()
                            if (id != null) {
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
                                totalMigrated++
                            }
                        }
                    }
                } catch (_: Throwable) {}
            }
        }

        return totalMigrated
    }
}
