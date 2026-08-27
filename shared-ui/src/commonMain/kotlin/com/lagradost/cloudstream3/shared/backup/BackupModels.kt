package com.lagradost.cloudstream3.shared.backup

import cloudstream.shared_ui.generated.resources.Res
import cloudstream.shared_ui.generated.resources.backup_category_bookmarks
import cloudstream.shared_ui.generated.resources.backup_category_bookmarks_desc
import cloudstream.shared_ui.generated.resources.backup_category_plugins
import cloudstream.shared_ui.generated.resources.backup_category_plugins_desc
import cloudstream.shared_ui.generated.resources.backup_category_settings
import cloudstream.shared_ui.generated.resources.backup_category_settings_desc
import cloudstream.shared_ui.generated.resources.backup_category_sync_accounts
import cloudstream.shared_ui.generated.resources.backup_category_sync_accounts_desc
import cloudstream.shared_ui.generated.resources.backup_category_watch_progress
import cloudstream.shared_ui.generated.resources.backup_category_watch_progress_desc
import com.lagradost.cloudstream3.shared.persistence.entity.AccountEntity
import com.lagradost.cloudstream3.shared.persistence.entity.BookmarkEntity
import com.lagradost.cloudstream3.shared.persistence.entity.FavoriteEntity
import com.lagradost.cloudstream3.shared.persistence.entity.ResumeWatchingEntity
import com.lagradost.cloudstream3.shared.persistence.entity.SubscriptionEntity
import com.lagradost.cloudstream3.shared.persistence.entity.WatchProgressEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource

/**
 * Categories available for granular export and import in CloudStream backup system.
 */
@Serializable
enum class BackupCategory(
    val nameRes: StringResource,
    val descriptionRes: StringResource
) {
    SETTINGS(
        nameRes = Res.string.backup_category_settings,
        descriptionRes = Res.string.backup_category_settings_desc
    ),
    WATCH_PROGRESS(
        nameRes = Res.string.backup_category_watch_progress,
        descriptionRes = Res.string.backup_category_watch_progress_desc
    ),
    BOOKMARKS(
        nameRes = Res.string.backup_category_bookmarks,
        descriptionRes = Res.string.backup_category_bookmarks_desc
    ),
    PLUGINS(
        nameRes = Res.string.backup_category_plugins,
        descriptionRes = Res.string.backup_category_plugins_desc
    ),
    SYNC_ACCOUNTS(
        nameRes = Res.string.backup_category_sync_accounts,
        descriptionRes = Res.string.backup_category_sync_accounts_desc
    )
}

/**
 * Cross-platform modern v2 backup container payload.
 */
@Serializable
data class BackupPayload(
    val version: Int = 2,
    val createdAt: Long = 0L,
    val categories: Set<BackupCategory> = emptySet(),
    val settings: Map<String, String>? = null,
    val watchProgress: List<WatchProgressEntity>? = null,
    val resumeWatching: List<ResumeWatchingEntity>? = null,
    val bookmarks: List<BookmarkEntity>? = null,
    val favorites: List<FavoriteEntity>? = null,
    val subscriptions: List<SubscriptionEntity>? = null,
    val accounts: List<AccountEntity>? = null
)

/**
 * Legacy SharedPreferences / DataStore backup variable payload structure.
 */
@Serializable
data class LegacyBackupVars(
    @SerialName("_Bool") val bool: Map<String, Boolean>? = null,
    @SerialName("_Int") val int: Map<String, Int>? = null,
    @SerialName("_String") val string: Map<String, String>? = null,
    @SerialName("_Float") val float: Map<String, Float>? = null,
    @SerialName("_Long") val long: Map<String, Long>? = null,
    @SerialName("_StringSet") val stringSet: Map<String, Set<String>?>? = null
)

/**
 * Legacy Android backup format container model.
 */
@Serializable
data class LegacyBackupFile(
    @SerialName("datastore") val datastore: LegacyBackupVars? = null,
    @SerialName("settings") val settings: LegacyBackupVars? = null
)

/**
 * Result model representing the outcome of a backup restoration operation.
 */
@Serializable
sealed class BackupRestoreResult {
    @Serializable
    @SerialName("success")
    data class Success(
        val restoredCategories: Set<BackupCategory>,
        val itemsCount: Int
    ) : BackupRestoreResult()

    @Serializable
    @SerialName("error")
    data class Error(
        val message: String
    ) : BackupRestoreResult()
}
