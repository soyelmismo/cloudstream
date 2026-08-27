package com.lagradost.cloudstream3.shared.persistence.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lagradost.cloudstream3.TvType
import kotlinx.serialization.Serializable

/**
 * Entity representing cached downloaded show/movie headers.
 */
@Entity(tableName = "download_headers")
@Serializable
data class DownloadHeaderEntity(
    @PrimaryKey val id: Int,
    val apiName: String,
    val url: String,
    val type: TvType,
    val name: String,
    val poster: String? = null,
    val cacheTime: Long = 0L
)

/**
 * Entity representing cached downloaded episode metadata.
 */
@Entity(
    tableName = "download_episodes",
    primaryKeys = ["id"],
    indices = [
        Index(value = ["parentId"]),
        Index(value = ["cacheTime"])
    ]
)
@Serializable
data class DownloadEpisodeEntity(
    val id: Int,
    val parentId: Int,
    val name: String? = null,
    val poster: String? = null,
    val episode: Int,
    val season: Int? = null,
    val score: Double? = null,
    val description: String? = null,
    val cacheTime: Long = 0L
)
