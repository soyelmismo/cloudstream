package com.lagradost.cloudstream3.services

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.os.Build.VERSION.SDK_INT
import androidx.core.app.NotificationCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import cloudstream.shared_ui.generated.resources.*
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.EpisodeResponse
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.shared.persistence.driver.DatabaseDriverFactory
import com.lagradost.cloudstream3.shared.persistence.repository.AppPreferenceManager
import com.lagradost.cloudstream3.shared.services.BackgroundJobScheduler
import com.lagradost.cloudstream3.utils.AppContextUtils.createNotificationChannel
import com.lagradost.cloudstream3.utils.AppContextUtils.getApiDubstatusSettings
import com.lagradost.cloudstream3.utils.AppContextUtils.getImageBitmapFromUrl
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.Coroutines.ioWork
import com.lagradost.cloudstream3.utils.UIHelper.colorFromAttribute
import com.lagradost.cloudstream3.utils.asString
import com.lagradost.cloudstream3.utils.getDub
import com.lagradost.cloudstream3.utils.txt
import kotlinx.coroutines.withTimeoutOrNull

const val SUBSCRIPTION_CHANNEL_ID = "cloudstream3.subscriptions"
const val SUBSCRIPTION_WORK_NAME = BackgroundJobScheduler.SUBSCRIPTION_WORK_NAME
const val SUBSCRIPTION_CHANNEL_NAME = "Subscriptions"
const val SUBSCRIPTION_CHANNEL_DESCRIPTION = "Notifications for new episodes on subscribed shows"
const val SUBSCRIPTION_NOTIFICATION_ID = 938712897 // Random unique

class SubscriptionWorkManager(val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {
    companion object {
        fun enqueuePeriodicWork(context: Context?, intervalMinutes: Long = 360L) {
            BackgroundJobScheduler.getInstance(context).scheduleSubscriptionCheck(intervalMinutes)
        }

        fun cancelPeriodicWork(context: Context?) {
            BackgroundJobScheduler.getInstance(context).cancelSubscriptionCheck()
        }
    }

    private val progressNotificationBuilder =
        NotificationCompat.Builder(context, SUBSCRIPTION_CHANNEL_ID)
            .setAutoCancel(false)
            .setColorized(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setColor(context.colorFromAttribute(androidx.appcompat.R.attr.colorPrimary))
            .setContentTitle(txt(Res.string.subscription_in_progress_notification).asString(context))
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(0, 0, true)

    private val updateNotificationBuilder =
        NotificationCompat.Builder(context, SUBSCRIPTION_CHANNEL_ID)
            .setColorized(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setColor(context.colorFromAttribute(androidx.appcompat.R.attr.colorPrimary))
            .setSmallIcon(android.R.drawable.stat_sys_download)

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun updateProgress(max: Int, progress: Int, indeterminate: Boolean) {
        notificationManager.notify(
            SUBSCRIPTION_NOTIFICATION_ID, progressNotificationBuilder
                .setProgress(max, progress, indeterminate)
                .build()
        )
    }

    override suspend fun doWork(): Result {
        try {
            context.createNotificationChannel(
                SUBSCRIPTION_CHANNEL_ID,
                SUBSCRIPTION_CHANNEL_NAME,
                SUBSCRIPTION_CHANNEL_DESCRIPTION
            )

            val foregroundInfo = if (SDK_INT >= 29)
                ForegroundInfo(
                    SUBSCRIPTION_NOTIFICATION_ID,
                    progressNotificationBuilder.build(),
                    FOREGROUND_SERVICE_TYPE_DATA_SYNC
                ) else ForegroundInfo(SUBSCRIPTION_NOTIFICATION_ID, progressNotificationBuilder.build())
            setForeground(foregroundInfo)

            val accountId = AppPreferenceManager.getIntSync("data_store_helper/account_key_index", 0)
            val db = DatabaseDriverFactory.getDatabase()
            val subscriptions = db.subscriptionDao().getAllSubscriptions(accountId)

            if (subscriptions.isEmpty()) {
                WorkManager.getInstance(context).cancelWorkById(this.id)
                return Result.success()
            }

            val max = subscriptions.size
            var progress = 0

            updateProgress(max, progress, true)

            // We need all plugins loaded.
            WorkManagerHooks.loadPlugins(context)

            subscriptions.amap { savedData ->
                try {
                    val id = savedData.id
                    val api = getApiFromNameNull(savedData.apiName) ?: return@amap null

                    // Reasonable timeout to prevent having this worker run forever.
                    val response = withTimeoutOrNull(60_000) {
                        api.load(savedData.url) as? EpisodeResponse
                    } ?: return@amap null

                    val dubPreference =
                        getDub(id) ?: if (
                            context.getApiDubstatusSettings().contains(DubStatus.Dubbed)
                        ) {
                            DubStatus.Dubbed
                        } else {
                            DubStatus.Subbed
                        }

                    val latestEpisodes = response.getLatestEpisodes()
                    val latestPreferredEpisode = latestEpisodes[dubPreference]
                    val lastSeenMap: Map<String, Int?> = savedData.lastSeenEpisodeCountJson?.let {
                        tryParseJson<Map<String, Int?>>(it)
                    } ?: emptyMap()

                    val (shouldUpdate, latestEpisode) = if (latestPreferredEpisode != null) {
                        val latestSeenEpisode =
                            lastSeenMap[dubPreference.name] ?: Int.MIN_VALUE
                        val shouldUpdate = latestPreferredEpisode > latestSeenEpisode
                        shouldUpdate to latestPreferredEpisode
                    } else {
                        val latestEpisode = latestEpisodes[DubStatus.None] ?: Int.MIN_VALUE
                        val latestSeenEpisode =
                            lastSeenMap[DubStatus.None.name] ?: Int.MIN_VALUE
                        val shouldUpdate = latestEpisode > latestSeenEpisode
                        shouldUpdate to latestEpisode
                    }

                    val updatedSubscription = savedData.copy(
                        latestUpdatedTime = APIHolder.unixTimeMS,
                        lastSeenEpisodeCountJson = toJson(latestEpisodes.mapKeys { it.key.name })
                    )
                    db.subscriptionDao().upsertSubscription(updatedSubscription)

                    if (shouldUpdate) {
                        val updateHeader = savedData.name
                        val updateDescription = txt(
                            Res.string.subscription_episode_released,
                            latestEpisode,
                            savedData.name
                        ).asString(context)

                        val intent = (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent()).apply {
                            setClassName(context.packageName, "com.lagradost.cloudstream3.MainActivity")
                            data = savedData.url.toUri()
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            putExtra("API_NAME_EXTRA_KEY", api.name)
                        }

                        val pendingIntent =
                            PendingIntentCompat.getActivity(context, 0, intent, 0, false)

                        val poster = ioWork {
                            savedData.posterUrl?.let { url ->
                                context.getImageBitmapFromUrl(
                                    url,
                                    null
                                )
                            }
                        }

                        val updateNotification =
                            updateNotificationBuilder.setContentTitle(updateHeader)
                                .setContentText(updateDescription)
                                .setContentIntent(pendingIntent)
                                .setLargeIcon(poster)
                                .build()

                        notificationManager.notify(id, updateNotification)
                    }

                    // You can probably get some issues here since this is async but it does not matter much.
                    updateProgress(max, ++progress, false)
                } catch (t: Throwable) {
                    logError(t)
                }
            }

            return Result.success()
        } catch (t: Throwable) {
            logError(t)
            return Result.success()
        }
    }
}
