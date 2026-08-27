package com.lagradost.cloudstream3.shared.persistence.entity

import androidx.room.Entity
import androidx.room.Index
import kotlinx.serialization.Serializable

/**
 * Entity representing resume watching information for a parent series or movie.
 */
@Entity(
    tableName = "resume_watching",
    primaryKeys = ["accountId", "parentId"],
    indices = [
        Index(value = ["accountId"]),
        Index(value = ["parentId"]),
        Index(value = ["updateTime"])
    ]
)
@Serializable
data class ResumeWatchingEntity(
    val accountId: Int,
    val parentId: Int,
    val episodeId: Int? = null,
    val episode: Int? = null,
    val season: Int? = null,
    val isFromDownload: Boolean = false,
    val updateTime: Long = 0L
)
