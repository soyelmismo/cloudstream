package com.lagradost.cloudstream3.shared.persistence.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import kotlinx.serialization.Serializable

@Entity(
    tableName = "sync_mappings",
    primaryKeys = ["accountId", "mediaId", "syncPrefix"],
    indices = [
        Index(value = ["accountId", "mediaId"])
    ]
)
@Serializable
@Immutable
data class SyncMappingEntity(
    val accountId: Int,
    val mediaId: Int,
    val syncPrefix: String,
    val remoteUrl: String,
    val updatedAt: Long = 0L
)
