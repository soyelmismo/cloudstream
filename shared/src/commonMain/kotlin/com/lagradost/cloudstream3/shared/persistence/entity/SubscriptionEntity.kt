package com.lagradost.cloudstream3.shared.persistence.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import com.lagradost.cloudstream3.TvType
import kotlinx.serialization.Serializable

@Entity(
    tableName = "subscriptions",
    primaryKeys = ["accountId", "id"],
    indices = [
        Index(value = ["accountId"]),
        Index(value = ["latestUpdatedTime"])
    ]
)
@Serializable
@Immutable
data class SubscriptionEntity(
    val accountId: Int,
    val id: Int,
    val name: String,
    val url: String,
    val apiName: String,
    val type: TvType? = null,
    val posterUrl: String? = null,
    val year: Int? = null,
    val subscribedTime: Long = 0L,
    val latestUpdatedTime: Long = 0L,
    val lastSeenEpisodeCountJson: String? = null
)
