package com.lagradost.cloudstream3.shared.persistence.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import kotlinx.serialization.Serializable

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
@Immutable
data class ResumeWatchingEntity(
    val accountId: Int,
    val parentId: Int,
    val episodeId: Int? = null,
    val episode: Int? = null,
    val season: Int? = null,
    val isFromDownload: Boolean = false,
    val updateTime: Long = 0L
)
