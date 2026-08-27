package com.lagradost.cloudstream3.shared.persistence.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.lagradost.cloudstream3.shared.persistence.entity.WatchProgressEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for video watch progress, duration, and watched state.
 */
@Dao
interface WatchProgressDao {
    @Query("SELECT * FROM watch_progress WHERE accountId = :accountId AND mediaId = :mediaId LIMIT 1")
    suspend fun getWatchProgress(accountId: Int, mediaId: Int): WatchProgressEntity?

    @Query("SELECT * FROM watch_progress WHERE accountId = :accountId AND mediaId = :mediaId LIMIT 1")
    fun getWatchProgressFlow(accountId: Int, mediaId: Int): Flow<WatchProgressEntity?>

    @Query("SELECT * FROM watch_progress WHERE accountId = :accountId ORDER BY lastUpdated DESC")
    suspend fun getAllWatchProgress(accountId: Int): List<WatchProgressEntity>

    @Query("SELECT * FROM watch_progress WHERE accountId = :accountId ORDER BY lastUpdated DESC")
    fun getAllWatchProgressFlow(accountId: Int): Flow<List<WatchProgressEntity>>

    @Query("SELECT mediaId FROM watch_progress WHERE accountId = :accountId")
    suspend fun getAllMediaIds(accountId: Int): List<Int>

    @Upsert
    suspend fun upsertWatchProgress(progress: WatchProgressEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(progressList: List<WatchProgressEntity>)

    @Query("DELETE FROM watch_progress WHERE accountId = :accountId AND mediaId = :mediaId")
    suspend fun deleteWatchProgress(accountId: Int, mediaId: Int)

    @Query("DELETE FROM watch_progress WHERE accountId = :accountId")
    suspend fun clearAccountProgress(accountId: Int)

    @Delete
    suspend fun delete(progress: WatchProgressEntity)
}
