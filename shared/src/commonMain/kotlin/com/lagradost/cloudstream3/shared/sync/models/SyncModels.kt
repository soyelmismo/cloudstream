@file:UseSerializers(ImmutableListSerializer::class)

package com.lagradost.cloudstream3.shared.sync.models

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

class ImmutableListSerializer<T>(
    elementSerializer: KSerializer<T>
) : KSerializer<ImmutableList<T>> {
    private val delegate = ListSerializer(elementSerializer)
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: ImmutableList<T>) {
        delegate.serialize(encoder, value)
    }

    override fun deserialize(decoder: Decoder): ImmutableList<T> {
        return delegate.deserialize(decoder).toImmutableList()
    }
}

object SyncEntityType {
    const val WATCH_PROGRESS = "watch_progress"
    const val BOOKMARK = "bookmark"
    const val FAVORITE = "favorite"
    const val SUBSCRIPTION = "subscription"
}

@Serializable
@Immutable
data class WatchProgressDelta(
    val mediaId: Int,
    val position: Long,
    val duration: Long,
    val watchState: Int,
    val updatedAt: Long
)

@Serializable
@Immutable
data class BookmarkDelta(
    val id: Int,
    val name: String,
    val url: String,
    val apiName: String,
    val type: String? = null,
    val posterUrl: String? = null,
    val year: Int? = null,
    val watchType: Int,
    val updatedAt: Long
)

@Serializable
@Immutable
data class FavoriteDelta(
    val id: Int,
    val name: String,
    val url: String,
    val apiName: String,
    val type: String? = null,
    val posterUrl: String? = null,
    val updatedAt: Long
)

@Serializable
@Immutable
data class TombstoneDelta(
    val entityType: String,
    val entityId: String,
    val deletedAt: Long
)

@Serializable
@Immutable
data class SyncDelta(
    val schemaVersion: Int = 1,
    val deviceId: String,
    val accountId: Int,
    val accountUuid: String = "",
    val timestamp: Long,
    val watchProgress: ImmutableList<WatchProgressDelta> = persistentListOf(),
    val bookmarks: ImmutableList<BookmarkDelta> = persistentListOf(),
    val favorites: ImmutableList<FavoriteDelta> = persistentListOf(),
    val tombstones: ImmutableList<TombstoneDelta> = persistentListOf()
) {
    val isEmpty: Boolean
        get() = watchProgress.isEmpty() &&
            bookmarks.isEmpty() &&
            favorites.isEmpty() &&
            tombstones.isEmpty()

    val totalItems: Int
        get() = watchProgress.size + bookmarks.size + favorites.size + tombstones.size
}

@Serializable
@Immutable
data class SyncManifest(
    val schemaVersion: Int = 1,
    val appName: String = "CloudStream",
    val deviceId: String = "",
    val updatedAt: Long = 0L
)

@Serializable
@Immutable
data class SyncApplyResult(
    val watchProgressApplied: Int,
    val bookmarksApplied: Int,
    val favoritesApplied: Int,
    val tombstonesApplied: Int,
    val conflictsSkipped: Int,
    val localItemsPushed: Int = 0
) {
    val totalApplied: Int
        get() = watchProgressApplied + bookmarksApplied + favoritesApplied + tombstonesApplied

    val totalChanges: Int
        get() = totalApplied + localItemsPushed

    operator fun plus(other: SyncApplyResult): SyncApplyResult = SyncApplyResult(
        watchProgressApplied = watchProgressApplied + other.watchProgressApplied,
        bookmarksApplied = bookmarksApplied + other.bookmarksApplied,
        favoritesApplied = favoritesApplied + other.favoritesApplied,
        tombstonesApplied = tombstonesApplied + other.tombstonesApplied,
        conflictsSkipped = conflictsSkipped + other.conflictsSkipped,
        localItemsPushed = localItemsPushed + other.localItemsPushed
    )

    companion object {
        val EMPTY = SyncApplyResult(
            watchProgressApplied = 0,
            bookmarksApplied = 0,
            favoritesApplied = 0,
            tombstonesApplied = 0,
            conflictsSkipped = 0,
            localItemsPushed = 0
        )
    }
}
