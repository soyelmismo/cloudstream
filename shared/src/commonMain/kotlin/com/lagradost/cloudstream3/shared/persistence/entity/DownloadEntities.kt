package com.lagradost.cloudstream3.shared.persistence.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lagradost.cloudstream3.TvType
import kotlinx.serialization.Serializable

@Entity(tableName = "download_headers")
@Serializable
@Immutable
data class DownloadHeaderEntity(
    @PrimaryKey val id: Int,
    val apiName: String,
    val url: String,
    val type: TvType,
    val name: String,
    val poster: String? = null,
    val cacheTime: Long = 0L
)

@Entity(
    tableName = "download_episodes",
    primaryKeys = ["id"],
    indices = [
        Index(value = ["parentId"]),
        Index(value = ["cacheTime"])
    ]
)
@Serializable
@Immutable
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
