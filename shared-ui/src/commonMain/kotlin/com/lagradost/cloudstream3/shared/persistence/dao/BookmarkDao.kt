package com.lagradost.cloudstream3.shared.persistence.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.lagradost.cloudstream3.shared.persistence.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for bookmarks, watchlist, and user library items.
 */
@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE accountId = :accountId AND id = :id LIMIT 1")
    suspend fun getBookmark(accountId: Int, id: Int): BookmarkEntity?

    @Query("SELECT * FROM bookmarks WHERE accountId = :accountId AND id = :id LIMIT 1")
    fun getBookmarkFlow(accountId: Int, id: Int): Flow<BookmarkEntity?>

    @Query("SELECT * FROM bookmarks WHERE accountId = :accountId ORDER BY bookmarkedTime DESC")
    suspend fun getAllBookmarks(accountId: Int): List<BookmarkEntity>

    @Query("SELECT * FROM bookmarks WHERE accountId = :accountId ORDER BY bookmarkedTime DESC")
    fun getAllBookmarksFlow(accountId: Int): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE accountId = :accountId AND watchType = :watchType ORDER BY bookmarkedTime DESC")
    suspend fun getBookmarksByWatchType(accountId: Int, watchType: Int): List<BookmarkEntity>

    @Query("SELECT * FROM bookmarks WHERE accountId = :accountId AND watchType = :watchType ORDER BY bookmarkedTime DESC")
    fun getBookmarksByWatchTypeFlow(accountId: Int, watchType: Int): Flow<List<BookmarkEntity>>

    @Query("SELECT id FROM bookmarks WHERE accountId = :accountId")
    suspend fun getAllBookmarkIds(accountId: Int): List<Int>

    @Query("SELECT watchType FROM bookmarks WHERE accountId = :accountId AND id = :id LIMIT 1")
    suspend fun getWatchType(accountId: Int, id: Int): Int?

    @Upsert
    suspend fun upsertBookmark(bookmark: BookmarkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(bookmarks: List<BookmarkEntity>)

    @Query("DELETE FROM bookmarks WHERE accountId = :accountId AND id = :id")
    suspend fun deleteBookmark(accountId: Int, id: Int)

    @Query("DELETE FROM bookmarks WHERE accountId = :accountId")
    suspend fun clearAccountBookmarks(accountId: Int)

    @Delete
    suspend fun delete(bookmark: BookmarkEntity)
}
