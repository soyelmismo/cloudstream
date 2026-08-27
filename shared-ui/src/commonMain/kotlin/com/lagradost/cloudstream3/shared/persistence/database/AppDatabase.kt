package com.lagradost.cloudstream3.shared.persistence.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.lagradost.cloudstream3.shared.persistence.converters.RoomTypeConverters
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

/**
 * CloudStream Room Multiplatform Database definition.
 * Supports Android and JVM / Desktop targets with BundledSQLiteDriver.
 */
@Database(
    entities = [
        AccountEntity::class,
        WatchProgressEntity::class,
        ResumeWatchingEntity::class,
        BookmarkEntity::class,
        SubscriptionEntity::class,
        FavoriteEntity::class,
        DownloadHeaderEntity::class,
        DownloadEpisodeEntity::class,
        SyncMappingEntity::class,
        AppPreferenceEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(RoomTypeConverters::class)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun watchProgressDao(): WatchProgressDao
    abstract fun resumeWatchingDao(): ResumeWatchingDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun downloadCacheDao(): DownloadCacheDao
    abstract fun syncMappingDao(): SyncMappingDao
    abstract fun appPreferenceDao(): AppPreferenceDao

    companion object {
        const val DB_NAME = "cloudstream.db"
    }
}
