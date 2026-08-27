package com.lagradost.cloudstream3.utils

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.WorkerThread
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import com.fasterxml.jackson.annotation.JsonProperty
import cloudstream.shared_ui.generated.resources.*
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getActivity
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.plugins.PLUGINS_KEY
import com.lagradost.cloudstream3.plugins.PLUGINS_KEY_LOCAL
import com.lagradost.cloudstream3.shared.syncproviders.AccountManager
import com.lagradost.cloudstream3.shared.syncproviders.providers.AniListApi.Companion.ANILIST_CACHED_LIST
import com.lagradost.cloudstream3.shared.syncproviders.providers.MALApi.Companion.MAL_CACHED_LIST
import com.lagradost.cloudstream3.shared.syncproviders.providers.KitsuApi.Companion.KITSU_CACHED_LIST
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import com.lagradost.cloudstream3.shared.persistence.repository.AppPreferenceManager
import com.lagradost.cloudstream3.utils.UIHelper.checkWrite
import com.lagradost.cloudstream3.utils.UIHelper.requestRW
import com.lagradost.safefile.MediaFileContentType
import com.lagradost.safefile.SafeFile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.internal.closeQuietly
import java.io.IOException
import java.io.OutputStream
import java.io.PrintWriter
import java.lang.System.currentTimeMillis
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val DOWNLOAD_HEADER_CACHE = "download_header_cache"
private const val DOWNLOAD_HEADER_CACHE_BACKUP = "BACKUP_download_header_cache"
private const val DOWNLOAD_EPISODE_CACHE = "download_episode_cache"
private const val DOWNLOAD_EPISODE_CACHE_BACKUP = "BACKUP_download_episode_cache"

object BackupUtils {

    /**
     * No sensitive or breaking data in the backup
     */
    private val nonTransferableKeys = listOf(
        ANILIST_CACHED_LIST,
        MAL_CACHED_LIST,
        KITSU_CACHED_LIST,

        // The plugins themselves are not backed up
        PLUGINS_KEY,
        PLUGINS_KEY_LOCAL,

        AccountManager.ACCOUNT_TOKEN,
        AccountManager.ACCOUNT_IDS,

        // TODO proper getter for string res keys to ensure that they are updated
        "biometric_key", // can lock down users if backup is shared on a incompatible device
        "nginx_user", // Nginx user key

        // No access rights after restore data from backup
        "download_path_key",
        "download_path_key_visual",
        "backup_path_key",
        "backup_dir_path_key",

        // When sharing backup we do not want to transfer what is essentially the password
        // Note that this is deprecated, and can be removed after all tokens have expired
        "anilist_token",
        "anilist_user",
        "mal_user",
        "mal_token",
        "mal_refresh_token",
        "mal_unixtime",
        "open_subtitles_user",
        "subdl_user",
        "simkl_token",


        // Downloads can not be restored from backups.
        // The download path URI can not be transferred.
        // In the future we may potentially write metadata to files in the download directory
        // and make it possible to restore download folders using that metadata.
        DOWNLOAD_EPISODE_CACHE_BACKUP,
        DOWNLOAD_EPISODE_CACHE,
        
        // Download headers are unintuitively used in the resume watching system.
        // We can therefore not prune download headers in backups.
        // DOWNLOAD_HEADER_CACHE_BACKUP,
        // DOWNLOAD_HEADER_CACHE,
        

        // This may overwrite valid local data with invalid data
        "download_info",

        // Prevent backups from automatically starting downloads
        "download_resume_queue_key",
        "download_resume_2",
        "download_queue_key",

        // Prevent automatic plugin download after restoring backup
        "auto_download_plugins_key2"
    )

    /** false if key should not be contained in backup */
    private fun String.isTransferable(): Boolean {
        return !nonTransferableKeys.any { this.contains(it) }
    }

    private var restoreFileSelector: ActivityResultLauncher<Array<String>>? = null

    // Kinda hack, but I couldn't think of a better way
    @Serializable
    data class BackupVars(
        @SerialName("_Bool") val bool: Map<String, Boolean>? = null,
        @SerialName("_Int") val int: Map<String, Int>? = null,
        @SerialName("_String") val string: Map<String, String>? = null,
        @SerialName("_Float") val float: Map<String, Float>? = null,
        @SerialName("_Long") val long: Map<String, Long>? = null,
        @SerialName("_StringSet") val stringSet: Map<String, Set<String>?>? = null,
    )

    @Serializable
    data class BackupFile(
        @JsonProperty("datastore") @SerialName("datastore") val datastore: BackupVars,
        @JsonProperty("settings") @SerialName("settings") val settings: BackupVars,
    )

    @Suppress("UNCHECKED_CAST")
    private fun getBackup(context: Context): BackupFile {
        val allData = AppPreferenceManager.getAllSync().filter { it.key.isTransferable() }

        val allDataSorted = BackupVars(
            string = allData
        )

        val allSettingsSorted = BackupVars()

        return BackupFile(
            allDataSorted,
            allSettingsSorted,
        )
    }

    @WorkerThread
    fun restore(
        context: Context?,
        backupFile: BackupFile,
        restoreSettings: Boolean,
        restoreData: Boolean,
    ) {
        if (context == null) return
        if (restoreSettings) {
            context.restoreMap(backupFile.settings.bool, true)
            context.restoreMap(backupFile.settings.int, true)
            context.restoreMap(backupFile.settings.string, true)
            context.restoreMap(backupFile.settings.float, true)
            context.restoreMap(backupFile.settings.long, true)
            context.restoreMap(backupFile.settings.stringSet, true)
        }

        if (restoreData) {
            context.restoreMap(backupFile.datastore.bool)
            context.restoreMap(backupFile.datastore.int)
            context.restoreMap(backupFile.datastore.string)
            context.restoreMap(backupFile.datastore.float)
            context.restoreMap(backupFile.datastore.long)
            context.restoreMap(backupFile.datastore.stringSet)
        }

        // Make sure the library is fresh
        for(api in AccountManager.syncApis) {
            api.requireLibraryRefresh = true
        }
    }

    fun backup(context: Context?) = ioSafe {
        if (context == null) return@ioSafe
        var fileStream: OutputStream? = null
        var printStream: PrintWriter? = null

        try {
            if (!context.checkWrite()) {
                showToast(Res.string.backup_failed, Toast.LENGTH_LONG)
                context.getActivity()?.requestRW()
                return@ioSafe
            }

            val date = SimpleDateFormat("yyyy_MM_dd_HH_mm", Locale.getDefault()).format(Date(currentTimeMillis()))
            val displayName = "CS3_Backup_${date}.txt"
            val backupFile = getBackup(context)
            val baseDir = getCurrentBackupDir(context).first ?: getDefaultBackupDir(context) ?: throw IOException("Bad config")
            val targetFile = baseDir.findFile(displayName) ?: baseDir.createFileOrThrow(displayName)

            fileStream = targetFile.openOutputStreamOrThrow(false)
            printStream = PrintWriter(fileStream)
            printStream.print(backupFile.toJson())
            showToast(Res.string.backup_success, Toast.LENGTH_LONG)
        } catch (e: Exception) {
            logError(e)
            try {
                showToast(
                    txt(Res.string.backup_failed_error_format, e.toString()),
                    Toast.LENGTH_LONG,
                )
            } catch (e: Exception) {
                logError(e)
            }
        } finally {
            printStream?.closeQuietly()
            fileStream?.closeQuietly()
        }
    }

    fun FragmentActivity.setUpBackup() {
        try {
            restoreFileSelector =
                registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                    if (uri == null) return@registerForActivityResult
                    val activity = this
                    ioSafe {
                        try {
                            val input = activity.contentResolver.openInputStream(uri)
                                ?: return@ioSafe

                            val text = input.bufferedReader().readText()
                            val restoredValue = parseJson<BackupFile>(text)

                            restore(
                                activity,
                                restoredValue,
                                restoreSettings = true,
                                restoreData = true,
                            )
                            activity.runOnUiThread { activity.recreate() }
                        } catch (e: Exception) {
                            logError(e)
                            main { // smth can fail in .format
                                showToast(
                                    txt(Res.string.restore_failed_format, e.toString())
                                )
                            }
                        }
                    }
                }
        } catch (e: Exception) {
            logError(e)
        }
    }

    fun FragmentActivity.restorePrompt() {
        runOnUiThread {
            try {
                restoreFileSelector?.launch(
                    arrayOf(
                        "text/plain",
                        "text/str",
                        "text/x-unknown",
                        "application/json",
                        "unknown/unknown",
                        "content/unknown",
                        "application/octet-stream",
                    )
                )
            } catch (e: Exception) {
                showToast(e.message)
                logError(e)
            }
        }
    }

    private fun <T> Context.restoreMap(
        map: Map<String, T>?,
        isEditingAppSettings: Boolean = false,
    ) {
        map?.forEach { (key, value) ->
            if (key.isTransferable() && value != null) {
                when (value) {
                    is String -> AppPreferenceManager.setStringSync(key, value)
                    is Boolean -> AppPreferenceManager.setBooleanSync(key, value)
                    is Int -> AppPreferenceManager.setIntSync(key, value)
                    is Set<*> -> AppPreferenceManager.setStringSetSync(key, value.filterIsInstance<String>().toSet())
                    else -> AppPreferenceManager.setStringSync(key, value.toString())
                }
            }
        }
    }

    /**
     * Gets the default backup directory.
     */
    fun getDefaultBackupDir(context: Context): SafeFile? {
        return SafeFile.fromMedia(context, MediaFileContentType.Downloads)
    }

    /**
     * Gets current backup directory based on settings.
     */
    fun getCurrentBackupDir(context: Context): Pair<SafeFile?, String?> {
        val basePathSetting = AppPreferenceManager.getStringSync("backup_path_key", null)
        return baseBackupPathToFile(context, basePathSetting) to basePathSetting
    }

    /**
     * Resolves a string path to SafeFile for backup storage.
     */
    private fun baseBackupPathToFile(context: Context, path: String?): SafeFile? {
        return when {
            path.isNullOrBlank() -> getDefaultBackupDir(context)
            path.startsWith("content://") -> SafeFile.fromUri(context, path.toUri())
            else -> SafeFile.fromFilePath(context, path)
        }
    }
}
