package com.lagradost.cloudstream3.shared.persistence.entity

import androidx.room.Entity
import androidx.room.Index
import com.lagradost.cloudstream3.TvType
import kotlinx.serialization.Serializable

/**
 * Entity representing user favorites.
 */
@Entity(
    tableName = "favorites",
    primaryKeys = ["accountId", "id"],
    indices = [
        Index(value = ["accountId"]),
        Index(value = ["favoritesTime"])
    ]
)
@Serializable
data class FavoriteEntity(
    val accountId: Int,
    val id: Int,
    val name: String,
    val url: String,
    val apiName: String,
    val type: TvType? = null,
    val posterUrl: String? = null,
    val favoritesTime: Long = 0L
)
