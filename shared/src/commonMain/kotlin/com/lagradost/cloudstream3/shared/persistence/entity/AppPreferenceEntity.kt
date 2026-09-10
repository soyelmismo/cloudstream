package com.lagradost.cloudstream3.shared.persistence.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "app_preferences")
@Serializable
@Immutable
data class AppPreferenceEntity(
    @PrimaryKey val key: String,
    val value: String,
    val updatedAt: Long = 0L
)
