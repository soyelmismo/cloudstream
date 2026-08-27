package com.lagradost.cloudstream3.shared.persistence.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Key-value pair entity for storing application and multiplatform preferences.
 */
@Entity(tableName = "app_preferences")
@Serializable
data class AppPreferenceEntity(
    @PrimaryKey val key: String,
    val value: String,
    val updatedAt: Long = 0L
)
