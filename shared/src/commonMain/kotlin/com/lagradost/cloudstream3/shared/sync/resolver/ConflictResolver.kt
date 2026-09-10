package com.lagradost.cloudstream3.shared.sync.resolver

object ConflictResolver {

    private fun isTombstoneSuperseding(tombstoneDeletedAt: Long?, remoteUpdatedAt: Long): Boolean =
        tombstoneDeletedAt != null && tombstoneDeletedAt >= remoteUpdatedAt

    fun shouldApplyWatchProgress(
        localLastUpdated: Long,
        remoteUpdatedAt: Long,
        localPosition: Long,
        remotePosition: Long,
        tombstoneDeletedAt: Long?
    ): Boolean {
        if (isTombstoneSuperseding(tombstoneDeletedAt, remoteUpdatedAt)) return false
        if (remoteUpdatedAt > localLastUpdated) return true
        if (remoteUpdatedAt < localLastUpdated) return false
        return remotePosition > localPosition
    }

    fun shouldApplyBookmark(
        localUpdatedAt: Long,
        remoteUpdatedAt: Long,
        tombstoneDeletedAt: Long?
    ): Boolean {
        if (isTombstoneSuperseding(tombstoneDeletedAt, remoteUpdatedAt)) return false
        return remoteUpdatedAt > localUpdatedAt
    }

    fun shouldApplyFavorite(
        localUpdatedAt: Long,
        remoteUpdatedAt: Long,
        tombstoneDeletedAt: Long?
    ): Boolean {
        if (isTombstoneSuperseding(tombstoneDeletedAt, remoteUpdatedAt)) return false
        return remoteUpdatedAt > localUpdatedAt
    }
}
