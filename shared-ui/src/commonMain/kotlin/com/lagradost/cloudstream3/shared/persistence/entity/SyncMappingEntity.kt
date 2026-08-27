package com.lagradost.cloudstream3.shared.persistence.entity

import androidx.room.Entity
import androidx.room.Index
import kotlinx.serialization.Serializable

/**
 * Entity representing remote sync provider ID mappings (e.g. MAL, AniList, Simkl, Trakt).
 */
@Entity(
    tableName = "sync_mappings",
    primaryKeys = ["accountId", "mediaId", "syncPrefix"],
    indices = [
        Index(value = ["accountId", "mediaId"])
    ]
)
@Serializable
data class SyncMappingEntity(
    val accountId: Int,
    val mediaId: Int,
    val syncPrefix: String,
    val remoteUrl: String,
    val updatedAt: Long = 0L
)
