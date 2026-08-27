package com.lagradost.cloudstream3.shared.persistence.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.lagradost.cloudstream3.shared.persistence.entity.SyncMappingEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for external synchronization mappings (e.g. MAL, AniList, Simkl, Trakt).
 */
@Dao
interface SyncMappingDao {
    @Query("SELECT * FROM sync_mappings WHERE accountId = :accountId AND mediaId = :mediaId AND syncPrefix = :syncPrefix LIMIT 1")
    suspend fun getSyncMapping(accountId: Int, mediaId: Int, syncPrefix: String): SyncMappingEntity?

    @Query("SELECT * FROM sync_mappings WHERE accountId = :accountId AND mediaId = :mediaId")
    suspend fun getSyncMappingsForMedia(accountId: Int, mediaId: Int): List<SyncMappingEntity>

    @Query("SELECT * FROM sync_mappings WHERE accountId = :accountId AND mediaId = :mediaId")
    fun getSyncMappingsForMediaFlow(accountId: Int, mediaId: Int): Flow<List<SyncMappingEntity>>

    @Upsert
    suspend fun upsertSyncMapping(mapping: SyncMappingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncMappings(mappings: List<SyncMappingEntity>)

    @Query("DELETE FROM sync_mappings WHERE accountId = :accountId AND mediaId = :mediaId AND syncPrefix = :syncPrefix")
    suspend fun deleteSyncMapping(accountId: Int, mediaId: Int, syncPrefix: String)

    @Query("DELETE FROM sync_mappings WHERE accountId = :accountId AND mediaId = :mediaId")
    suspend fun deleteSyncMappingsForMedia(accountId: Int, mediaId: Int)

    @Query("DELETE FROM sync_mappings WHERE accountId = :accountId")
    suspend fun clearAccountSyncMappings(accountId: Int)

    @Delete
    suspend fun delete(mapping: SyncMappingEntity)
}
