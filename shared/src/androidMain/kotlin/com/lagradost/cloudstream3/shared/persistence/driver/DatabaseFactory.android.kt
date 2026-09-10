package com.lagradost.cloudstream3.shared.persistence.driver

import android.content.Context
import com.lagradost.cloudstream3.shared.persistence.database.AppDatabase
import com.lagradost.cloudstream3.shared.persistence.database.DefaultAppDatabase

actual class DatabaseDriverFactory(private val context: Context? = null) {
    actual fun createDatabase(): AppDatabase {
        return getDatabase(context)
    }

    actual companion object {
        @Volatile
        private var instance: DatabaseDriverFactory? = null
        @Volatile
        private var databaseInstance: AppDatabase? = null
        private val lock = Any()

        fun getInstance(context: Context?): DatabaseDriverFactory {
            return instance ?: synchronized(lock) {
                instance ?: DatabaseDriverFactory(context).also { instance = it }
            }
        }

        actual fun getInstance(): DatabaseDriverFactory = getInstance(null)

        fun getDatabase(context: Context?): AppDatabase {
            return databaseInstance ?: synchronized(lock) {
                databaseInstance ?: run {
                    val targetContext = context ?: (com.lagradost.api.getContext() as? Context)
                    (object : DefaultAppDatabase(targetContext?.filesDir) {
                        override fun clearAllTables() {
                            // Fallback in-memory reset
                        }
                    }).also { databaseInstance = it }
                }
            }
        }

        actual fun getDatabase(): AppDatabase = getDatabase(null)
    }
}
