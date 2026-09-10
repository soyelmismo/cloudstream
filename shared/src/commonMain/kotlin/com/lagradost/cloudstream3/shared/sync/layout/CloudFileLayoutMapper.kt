package com.lagradost.cloudstream3.shared.sync.layout

import androidx.compose.runtime.Immutable
import com.lagradost.cloudstream3.shared.sync.models.SyncEntityType

@Immutable
object CloudFileLayoutMapper {
    const val USER_DATA_ROOT = "user_data"
    const val SETTINGS_ROOT = "settings"
    const val MANIFEST_FILE = "manifest.json"
    const val WATCH_PROGRESS_DIR = "watch_progress"
    const val BOOKMARKS_DIR = "bookmarks"
    const val FAVORITES_DIR = "favorites"
    const val TOMBSTONES_DIR = "tombstones"

    private val WATCH_PROGRESS_REGEX = Regex("""(?:^|/)watch_progress/(-?\d+)\.json$""")
    private val BOOKMARK_REGEX = Regex("""(?:^|/)bookmarks/(-?\d+)\.json$""")
    private val FAVORITE_REGEX = Regex("""(?:^|/)favorites/(-?\d+)\.json$""")
    private val TOMBSTONE_REGEX = Regex("""(?:^|/)tombstones/(.+?)\.json$""")
    private val ACCOUNT_UUID_REGEX = Regex("""(?:^|/)user_data/([^/]+)/""")

    private val KNOWN_ENTITY_TYPES = listOf(
        SyncEntityType.WATCH_PROGRESS,
        SyncEntityType.BOOKMARK,
        SyncEntityType.FAVORITE,
        SyncEntityType.SUBSCRIPTION
    )

    fun getWatchProgressPath(accountUuid: String, mediaId: Int): String =
        "$USER_DATA_ROOT/$accountUuid/$WATCH_PROGRESS_DIR/$mediaId.json"

    fun getBookmarkPath(accountUuid: String, id: Int): String =
        "$USER_DATA_ROOT/$accountUuid/$BOOKMARKS_DIR/$id.json"

    fun getFavoritePath(accountUuid: String, id: Int): String =
        "$USER_DATA_ROOT/$accountUuid/$FAVORITES_DIR/$id.json"

    fun getTombstonePath(accountUuid: String, entityType: String, id: String): String =
        "$USER_DATA_ROOT/$accountUuid/$TOMBSTONES_DIR/${entityType}_$id.json"

    fun getSettingsPath(category: String): String =
        "$SETTINGS_ROOT/$category.json"

    fun getManifestPath(): String = MANIFEST_FILE

    fun getAccountDir(accountUuid: String): String =
        "$USER_DATA_ROOT/$accountUuid"

    fun extractMediaIdFromWatchProgressPath(path: String): Int? {
        val match = WATCH_PROGRESS_REGEX.find(path.replace('\\', '/')) ?: return null
        return match.groupValues[1].toIntOrNull()
    }

    fun extractIdFromBookmarkPath(path: String): Int? {
        val match = BOOKMARK_REGEX.find(path.replace('\\', '/')) ?: return null
        return match.groupValues[1].toIntOrNull()
    }

    fun extractIdFromFavoritePath(path: String): Int? {
        val match = FAVORITE_REGEX.find(path.replace('\\', '/')) ?: return null
        return match.groupValues[1].toIntOrNull()
    }

    fun extractTombstoneInfoFromPath(path: String): Pair<String, String>? {
        val match = TOMBSTONE_REGEX.find(path.replace('\\', '/')) ?: return null
        return parseTombstoneBaseName(match.groupValues[1])
    }

    fun extractAccountUuidFromPath(path: String): String? {
        val match = ACCOUNT_UUID_REGEX.find(path.replace('\\', '/')) ?: return null
        return match.groupValues[1]
    }

    fun getPositivePathForTombstone(accountUuid: String, entityType: String, entityId: String): String? {
        val numericId = entityId.toIntOrNull() ?: return null
        return resolvePositivePath(accountUuid, entityType, numericId)
    }

    private fun resolvePositivePath(accountUuid: String, entityType: String, numericId: Int): String? {
        return when (entityType) {
            SyncEntityType.WATCH_PROGRESS -> getWatchProgressPath(accountUuid, numericId)
            SyncEntityType.BOOKMARK -> getBookmarkPath(accountUuid, numericId)
            SyncEntityType.FAVORITE -> getFavoritePath(accountUuid, numericId)
            else -> null
        }
    }

    private fun parseTombstoneBaseName(baseName: String): Pair<String, String>? {
        val known = findKnownEntityType(baseName)
        if (known != null) return known
        return parseGenericTombstone(baseName)
    }

    private fun findKnownEntityType(baseName: String): Pair<String, String>? {
        for (type in KNOWN_ENTITY_TYPES) {
            val prefix = "${type}_"
            if (baseName.startsWith(prefix)) {
                return type to baseName.removePrefix(prefix)
            }
        }
        return null
    }

    private fun parseGenericTombstone(baseName: String): Pair<String, String>? {
        val separator = baseName.indexOf('_')
        if (separator <= 0) return null
        if (separator >= baseName.length - 1) return null
        return baseName.substring(0, separator) to baseName.substring(separator + 1)
    }
}
