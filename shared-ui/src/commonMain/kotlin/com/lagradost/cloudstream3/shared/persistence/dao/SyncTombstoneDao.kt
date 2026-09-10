package com.lagradost.cloudstream3.shared.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.lagradost.cloudstream3.shared.persistence.entity.SyncTombstoneEntity

@Dao
interface SyncTombstoneDao {
    @Query("SELECT * FROM sync_tombstones WHERE accountId = :accountId AND deletedAt > :sinceTimestamp")
    suspend fun getTombstonesSince(accountId: Int, sinceTimestamp: Long): List<SyncTombstoneEntity>

    @Query("SELECT * FROM sync_tombstones WHERE accountId = :accountId AND entityType = :entityType AND entityId = :entityId LIMIT 1")
    suspend fun getTombstone(accountId: Int, entityType: String, entityId: String): SyncTombstoneEntity?

    @Upsert
    suspend fun upsertTombstone(tombstone: SyncTombstoneEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tombstones: List<SyncTombstoneEntity>)

    @Query("DELETE FROM sync_tombstones WHERE accountId = :accountId AND entityType = :entityType AND entityId = :entityId")
    suspend fun deleteTombstone(accountId: Int, entityType: String, entityId: String)

    @Query("DELETE FROM sync_tombstones WHERE deletedAt < :cutoffTimestamp")
    suspend fun purgeOldTombstones(cutoffTimestamp: Long)
}
