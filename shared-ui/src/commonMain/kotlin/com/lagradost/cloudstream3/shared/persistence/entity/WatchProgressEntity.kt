package com.lagradost.cloudstream3.shared.persistence.entity

import androidx.room.Entity
import androidx.room.Index
import kotlinx.serialization.Serializable

/**
 * Entity representing the playback progress, duration, and watched state for a given media item.
 */
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
data class WatchProgressEntity(
    val accountId: Int,
    val mediaId: Int,
    val position: Long,
    val duration: Long,
    val watchState: Int = 0, // VideoWatchState: 0=None, 1=Watching, 2=Watched
    val lastUpdated: Long = 0L
)
