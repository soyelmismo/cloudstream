package com.lagradost.cloudstream3.shared.persistence.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.lagradost.cloudstream3.shared.persistence.entity.FavoriteEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for user favorites.
 */
@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites WHERE accountId = :accountId AND id = :id LIMIT 1")
    suspend fun getFavorite(accountId: Int, id: Int): FavoriteEntity?

    @Query("SELECT * FROM favorites WHERE accountId = :accountId AND id = :id LIMIT 1")
    fun getFavoriteFlow(accountId: Int, id: Int): Flow<FavoriteEntity?>

    @Query("SELECT * FROM favorites WHERE accountId = :accountId ORDER BY favoritesTime DESC")
    suspend fun getAllFavorites(accountId: Int): List<FavoriteEntity>

    @Query("SELECT * FROM favorites WHERE accountId = :accountId ORDER BY favoritesTime DESC")
    fun getAllFavoritesFlow(accountId: Int): Flow<List<FavoriteEntity>>

    @Upsert
    suspend fun upsertFavorite(favorite: FavoriteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(favorites: List<FavoriteEntity>)

    @Query("DELETE FROM favorites WHERE accountId = :accountId AND id = :id")
    suspend fun deleteFavorite(accountId: Int, id: Int)

    @Query("DELETE FROM favorites WHERE accountId = :accountId")
    suspend fun clearAccountFavorites(accountId: Int)

    @Delete
    suspend fun delete(favorite: FavoriteEntity)
}
