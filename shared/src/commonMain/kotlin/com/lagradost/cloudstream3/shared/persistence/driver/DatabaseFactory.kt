package com.lagradost.cloudstream3.shared.persistence.driver

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.lagradost.cloudstream3.shared.persistence.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/**
 * Common Database Factory for creating and configuring the Room Multiplatform AppDatabase instance.
 */
expect class DatabaseDriverFactory {
    fun createDatabase(): AppDatabase

    companion object {
        fun getInstance(): DatabaseDriverFactory
        fun getDatabase(): AppDatabase
    }
}

/**
 * Configures a RoomDatabase.Builder with BundledSQLiteDriver and IO coroutine dispatcher.
 */
fun RoomDatabase.Builder<AppDatabase>.configureCommonDriver(): RoomDatabase.Builder<AppDatabase> {
    return this
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
}
