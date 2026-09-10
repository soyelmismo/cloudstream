package com.lagradost.cloudstream3.shared.persistence.driver

import com.lagradost.cloudstream3.shared.persistence.database.AppDatabase
import com.lagradost.cloudstream3.shared.persistence.database.DefaultAppDatabase
import java.io.File

actual class DatabaseDriverFactory {
    actual fun createDatabase(): AppDatabase {
        return getDatabase()
    }

    actual companion object {
        @Volatile
        private var instance: DatabaseDriverFactory? = null
        @Volatile
        private var databaseInstance: AppDatabase? = null
        private val lock = Any()

        actual fun getInstance(): DatabaseDriverFactory {
            return instance ?: synchronized(lock) {
                instance ?: DatabaseDriverFactory().also { instance = it }
            }
        }

        actual fun getDatabase(): AppDatabase {
            return databaseInstance ?: synchronized(lock) {
                databaseInstance ?: run {
                    val userHome = System.getProperty("user.home") ?: "."
                    val appDir = File(userHome, ".cloudstream")
                    if (!appDir.exists()) {
                        appDir.mkdirs()
                    }
                    object : DefaultAppDatabase(appDir) {}
                }.also { databaseInstance = it }
            }
        }
    }
}
