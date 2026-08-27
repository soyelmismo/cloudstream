package com.lagradost.cloudstream3.shared.persistence.entity

import androidx.room.Entity
import androidx.room.Index
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.TvType
import kotlinx.serialization.Serializable

/**
 * Entity representing a bookmarked media item in the user's library.
 */
@Entity(
    tableName = "bookmarks",
    primaryKeys = ["accountId", "id"],
    indices = [
        Index(value = ["accountId"]),
        Index(value = ["watchType"]),
        Index(value = ["apiName"]),
        Index(value = ["bookmarkedTime"]),
        Index(value = ["latestUpdatedTime"])
    ]
)
@Serializable
data class BookmarkEntity(
    val accountId: Int,
    val id: Int,
    val name: String,
    val url: String,
    val apiName: String,
    val type: TvType? = null,
    val posterUrl: String? = null,
    val year: Int? = null,
    val watchType: Int = 0, // WatchType: 0=NONE, 1=WATCHING, 2=COMPLETED, 3=ON_HOLD, 4=DROPPED, 5=PLANNED
    val bookmarkedTime: Long = 0L,
    val latestUpdatedTime: Long = 0L,
    val quality: SearchQuality? = null,
    val plot: String? = null,
    val score: Double? = null,
    val tagsJson: String? = null,
    val syncDataJson: String? = null,
    val posterHeadersJson: String? = null
)
