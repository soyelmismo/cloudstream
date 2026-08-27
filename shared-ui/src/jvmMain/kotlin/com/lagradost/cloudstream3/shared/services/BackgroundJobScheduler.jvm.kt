package com.lagradost.cloudstream3.shared.services

import com.lagradost.api.Log
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.EpisodeResponse
import com.lagradost.cloudstream3.shared.persistence.driver.DatabaseDriverFactory
import com.lagradost.cloudstream3.shared.persistence.repository.AppPreferenceManager
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * JVM Desktop implementation of [BackgroundJobScheduler] running a background
 * coroutine daemon for periodic subscription checks and database backups.
 */
actual class BackgroundJobScheduler actual constructor() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var subscriptionJob: Job? = null
    private var backupJob: Job? = null

    actual fun scheduleSubscriptionCheck(intervalMinutes: Long) {
        subscriptionJob?.cancel()
        if (intervalMinutes <= 0L) return

        subscriptionJob = scope.launch {
            while (isActive) {
                try {
                    executeSubscriptionCheck()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Log.e(TAG, "Error executing subscription check: ${e.message}")
                }
                delay(intervalMinutes * 60 * 1000L)
            }
        }
    }

    actual fun cancelSubscriptionCheck() {
        subscriptionJob?.cancel()
        subscriptionJob = null
    }

    actual fun scheduleBackup(intervalHours: Long) {
        backupJob?.cancel()
        if (intervalHours <= 0L) return

        backupJob = scope.launch {
            while (isActive) {
                delay(intervalHours * 3600 * 1000L)
                try {
                    executeBackup()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Log.e(TAG, "Error executing periodic backup: ${e.message}")
                }
            }
        }
    }

    actual fun cancelBackup() {
        backupJob?.cancel()
        backupJob = null
    }

    actual fun cancelAll() {
        cancelSubscriptionCheck()
        cancelBackup()
    }

    private suspend fun executeSubscriptionCheck() {
        val accountId = AppPreferenceManager.getIntSync("data_store_helper/account_key_index", 0)
        val db = DatabaseDriverFactory.getDatabase()
        val subscriptions = db.subscriptionDao().getAllSubscriptions(accountId)
        if (subscriptions.isEmpty()) return

        for (savedData in subscriptions) {
            try {
                val api = APIHolder.getApiFromNameNull(savedData.apiName) ?: continue
                val response = withTimeoutOrNull(60_000) {
                    api.load(savedData.url) as? EpisodeResponse
                } ?: continue

                val latestEpisodes = response.getLatestEpisodes()
                val lastSeenMap: Map<String, Int?> = savedData.lastSeenEpisodeCountJson?.let {
                    tryParseJson<Map<String, Int?>>(it)
                } ?: emptyMap()

                val latestEpisode = latestEpisodes[DubStatus.None]
                    ?: latestEpisodes[DubStatus.Subbed]
                    ?: latestEpisodes[DubStatus.Dubbed]
                    ?: Int.MIN_VALUE
                val lastSeen = lastSeenMap[DubStatus.None.name]
                    ?: lastSeenMap[DubStatus.Subbed.name]
                    ?: lastSeenMap[DubStatus.Dubbed.name]
                    ?: Int.MIN_VALUE

                val updatedSubscription = savedData.copy(
                    latestUpdatedTime = System.currentTimeMillis(),
                    lastSeenEpisodeCountJson = toJson(latestEpisodes.mapKeys { it.key.name })
                )
                db.subscriptionDao().upsertSubscription(updatedSubscription)

                if (latestEpisode > lastSeen) {
                    Log.i(TAG, "New episode detected for ${savedData.name}: Episode $latestEpisode")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "Failed checking subscription ${savedData.name}: ${e.message}")
            }
        }
    }

    private suspend fun executeBackup() {
        val userHome = System.getProperty("user.home") ?: "."
        val backupsDir = File(userHome, ".cloudstream/backups").apply { if (!exists()) mkdirs() }
        val timestamp = System.currentTimeMillis()
        val backupFile = File(backupsDir, "cloudstream_backup_$timestamp.json")

        val accountId = AppPreferenceManager.getIntSync("data_store_helper/account_key_index", 0)
        val db = DatabaseDriverFactory.getDatabase()

        val subscriptions = db.subscriptionDao().getAllSubscriptions(accountId)
        val favorites = db.favoriteDao().getAllFavorites(accountId)
        val bookmarks = db.bookmarkDao().getAllBookmarks(accountId)
        val watchProgress = db.watchProgressDao().getAllWatchProgress(accountId)
        val resumeWatching = db.resumeWatchingDao().getAllResumeWatching(accountId)

        val backupMap = mapOf(
            "version" to "1",
            "timestamp" to timestamp.toString(),
            "accountId" to accountId.toString(),
            "subscriptions" to toJson(subscriptions),
            "favorites" to toJson(favorites),
            "bookmarks" to toJson(bookmarks),
            "watchProgress" to toJson(watchProgress),
            "resumeWatching" to toJson(resumeWatching)
        )

        backupFile.writeText(toJson(backupMap))
        Log.i(TAG, "Periodic backup created at ${backupFile.absolutePath}")

        // Retain max 10 backups
        backupsDir.listFiles { f -> f.isFile && f.name.startsWith("cloudstream_backup_") && f.name.endsWith(".json") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(10)
            ?.forEach { it.delete() }
    }

    actual companion object {
        private const val TAG = "BackgroundJobScheduler"

        @Volatile
        private var instance: BackgroundJobScheduler? = null
        private val lock = Any()

        actual fun getInstance(): BackgroundJobScheduler {
            return instance ?: synchronized(lock) {
                instance ?: BackgroundJobScheduler().also { instance = it }
            }
        }
    }
}
