package com.lagradost.cloudstream3.shared.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.lagradost.cloudstream3.shared.persistence.entity.AppPreferenceEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for cross-platform app preferences and key-value configuration.
 */
@Dao
interface AppPreferenceDao {
    @Query("SELECT * FROM app_preferences WHERE `key` = :key LIMIT 1")
    suspend fun getPreference(key: String): AppPreferenceEntity?

    @Query("SELECT `value` FROM app_preferences WHERE `key` = :key LIMIT 1")
    suspend fun getString(key: String): String?

    @Query("SELECT `value` FROM app_preferences WHERE `key` = :key LIMIT 1")
    fun getStringFlow(key: String): Flow<String?>

    @Query("SELECT * FROM app_preferences WHERE `key` LIKE :prefix || '%'")
    suspend fun getPreferencesWithPrefix(prefix: String): List<AppPreferenceEntity>

    @Query("SELECT * FROM app_preferences")
    suspend fun getAllPreferences(): List<AppPreferenceEntity>

    @Upsert
    suspend fun upsertPreference(preference: AppPreferenceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreferences(preferences: List<AppPreferenceEntity>)

    @Query("DELETE FROM app_preferences WHERE `key` = :key")
    suspend fun deletePreference(key: String)

    @Query("DELETE FROM app_preferences WHERE `key` LIKE :prefix || '%'")
    suspend fun deletePreferencesWithPrefix(prefix: String): Int

    @Query("DELETE FROM app_preferences")
    suspend fun clearAll()

    @Query("SELECT `value` FROM app_preferences WHERE `key` = :key LIMIT 1")
    fun getStringSync(key: String): String?

    @Query("SELECT * FROM app_preferences WHERE `key` LIKE :prefix || '%'")
    fun getPreferencesWithPrefixSync(prefix: String): List<AppPreferenceEntity>

    @Query("SELECT * FROM app_preferences")
    fun getAllPreferencesSync(): List<AppPreferenceEntity>

    @Upsert
    fun upsertPreferenceSync(preference: AppPreferenceEntity)

    @Query("DELETE FROM app_preferences WHERE `key` = :key")
    fun deletePreferenceSync(key: String)

    @Query("DELETE FROM app_preferences WHERE `key` LIKE :prefix || '%'")
    fun deletePreferencesWithPrefixSync(prefix: String): Int

    @Query("DELETE FROM app_preferences")
    fun clearAllSync()
}
