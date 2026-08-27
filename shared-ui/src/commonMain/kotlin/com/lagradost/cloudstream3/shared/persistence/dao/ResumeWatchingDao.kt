package com.lagradost.cloudstream3.shared.persistence.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.lagradost.cloudstream3.shared.persistence.entity.ResumeWatchingEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for resume watching state per parent media.
 */
@Dao
interface ResumeWatchingDao {
    @Query("SELECT * FROM resume_watching WHERE accountId = :accountId AND parentId = :parentId LIMIT 1")
    suspend fun getResumeWatching(accountId: Int, parentId: Int): ResumeWatchingEntity?

    @Query("SELECT * FROM resume_watching WHERE accountId = :accountId AND parentId = :parentId LIMIT 1")
    fun getResumeWatchingFlow(accountId: Int, parentId: Int): Flow<ResumeWatchingEntity?>

    @Query("SELECT * FROM resume_watching WHERE accountId = :accountId ORDER BY updateTime DESC")
    suspend fun getAllResumeWatching(accountId: Int): List<ResumeWatchingEntity>

    @Query("SELECT * FROM resume_watching WHERE accountId = :accountId ORDER BY updateTime DESC")
    fun getAllResumeWatchingFlow(accountId: Int): Flow<List<ResumeWatchingEntity>>

    @Query("SELECT parentId FROM resume_watching WHERE accountId = :accountId ORDER BY updateTime DESC")
    suspend fun getAllResumeParentIds(accountId: Int): List<Int>

    @Upsert
    suspend fun upsertResumeWatching(entity: ResumeWatchingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ResumeWatchingEntity>)

    @Query("DELETE FROM resume_watching WHERE accountId = :accountId AND parentId = :parentId")
    suspend fun deleteResumeWatching(accountId: Int, parentId: Int)

    @Query("DELETE FROM resume_watching WHERE accountId = :accountId")
    suspend fun clearAccountResumeWatching(accountId: Int)

    @Delete
    suspend fun delete(entity: ResumeWatchingEntity)
}
