package com.lagradost.cloudstream3.shared.persistence.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.lagradost.cloudstream3.shared.persistence.entity.DownloadEpisodeEntity
import com.lagradost.cloudstream3.shared.persistence.entity.DownloadHeaderEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for cached download headers and episode metadata.
 */
@Dao
interface DownloadCacheDao {
    // Headers
    @Query("SELECT * FROM download_headers WHERE id = :id LIMIT 1")
    suspend fun getHeader(id: Int): DownloadHeaderEntity?

    @Query("SELECT * FROM download_headers ORDER BY cacheTime DESC")
    suspend fun getAllHeaders(): List<DownloadHeaderEntity>

    @Query("SELECT * FROM download_headers ORDER BY cacheTime DESC")
    fun getAllHeadersFlow(): Flow<List<DownloadHeaderEntity>>

    @Upsert
    suspend fun upsertHeader(header: DownloadHeaderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHeaders(headers: List<DownloadHeaderEntity>)

    @Query("DELETE FROM download_headers WHERE id = :id")
    suspend fun deleteHeader(id: Int)

    // Episodes
    @Query("SELECT * FROM download_episodes WHERE id = :id LIMIT 1")
    suspend fun getEpisode(id: Int): DownloadEpisodeEntity?

    @Query("SELECT * FROM download_episodes WHERE parentId = :parentId ORDER BY season ASC, episode ASC")
    suspend fun getEpisodesForParent(parentId: Int): List<DownloadEpisodeEntity>

    @Query("SELECT * FROM download_episodes WHERE parentId = :parentId ORDER BY season ASC, episode ASC")
    fun getEpisodesForParentFlow(parentId: Int): Flow<List<DownloadEpisodeEntity>>

    @Upsert
    suspend fun upsertEpisode(episode: DownloadEpisodeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisodes(episodes: List<DownloadEpisodeEntity>)

    @Query("DELETE FROM download_episodes WHERE id = :id")
    suspend fun deleteEpisode(id: Int)

    @Query("DELETE FROM download_episodes WHERE parentId = :parentId")
    suspend fun deleteEpisodesForParent(parentId: Int)

    @Query("DELETE FROM download_headers")
    suspend fun clearAllHeaders()

    @Query("DELETE FROM download_episodes")
    suspend fun clearAllEpisodes()
}
