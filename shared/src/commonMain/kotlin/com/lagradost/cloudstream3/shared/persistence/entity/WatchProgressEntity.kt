package com.lagradost.cloudstream3.shared.persistence.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import kotlinx.serialization.Serializable

@Entity(
    tableName = "watch_progress",
    primaryKeys = ["accountId", "mediaId"],
    indices = [
        Index(value = ["accountId"]),
        Index(value = ["mediaId"]),
        Index(value = ["lastUpdated"])
    ]
)
@Serializable
@Immutable
data class WatchProgressEntity(
    val accountId: Int,
    val mediaId: Int,
    val position: Long,
    val duration: Long,
    val watchState: Int = 0,
    val lastUpdated: Long = 0L
)
