package com.lagradost.cloudstream3.shared.persistence.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import kotlinx.serialization.Serializable

@Entity(
    tableName = "sync_tombstones",
    primaryKeys = ["accountId", "entityType", "entityId"]
)
@Serializable
@Immutable
data class SyncTombstoneEntity(
    val accountId: Int,
    val entityType: String,
    val entityId: String,
    val deletedAt: Long = 0L
) {
    companion object {
        const val TYPE_WATCH_PROGRESS = "watch_progress"
        const val TYPE_BOOKMARK = "bookmark"
        const val TYPE_FAVORITE = "favorite"
        const val TYPE_SUBSCRIPTION = "subscription"
    }
}
