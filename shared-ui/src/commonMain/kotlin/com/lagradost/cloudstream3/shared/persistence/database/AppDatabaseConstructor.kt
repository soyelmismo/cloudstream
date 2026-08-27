package com.lagradost.cloudstream3.shared.persistence.database

import androidx.room.RoomDatabaseConstructor

/**
 * Expected RoomDatabaseConstructor for AppDatabase.
 * The actual implementations are generated at compile-time by AndroidX Room KSP / compiler plugin.
 */
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
