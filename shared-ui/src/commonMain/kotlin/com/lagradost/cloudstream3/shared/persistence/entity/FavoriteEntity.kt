package com.lagradost.cloudstream3.shared.persistence.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import com.lagradost.cloudstream3.TvType
import kotlinx.serialization.Serializable

@Entity(
    tableName = "favorites",
    primaryKeys = ["accountId", "id"],
    indices = [
        Index(value = ["accountId"]),
        Index(value = ["favoritesTime"]),
        Index(value = ["latestUpdatedTime"])
    ]
)
@Serializable
@Immutable
data class FavoriteEntity(
    val accountId: Int,
    val id: Int,
    val name: String,
    val url: String,
    val apiName: String,
    val type: TvType? = null,
    val posterUrl: String? = null,
    val favoritesTime: Long = 0L,
    val latestUpdatedTime: Long = favoritesTime
)
