package com.lagradost.cloudstream3.services

import android.content.Context
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.os.Build.VERSION.SDK_INT
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import cloudstream.shared_ui.generated.resources.*
import com.lagradost.cloudstream3.shared.services.BackgroundJobScheduler
import com.lagradost.cloudstream3.utils.AppContextUtils.createNotificationChannel
import com.lagradost.cloudstream3.utils.UIHelper.colorFromAttribute
import com.lagradost.cloudstream3.utils.asString
import com.lagradost.cloudstream3.utils.txt

const val BACKUP_CHANNEL_ID = "cloudstream3.backups"
const val BACKUP_WORK_NAME = BackgroundJobScheduler.BACKUP_WORK_NAME
const val BACKUP_CHANNEL_NAME = "Backups"
const val BACKUP_CHANNEL_DESCRIPTION = "Notifications for background backups"
const val BACKUP_NOTIFICATION_ID = 938712898 // Random unique

class BackupWorkManager(val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {
    companion object {
        fun enqueuePeriodicWork(context: Context?, intervalHours: Long) {
            BackgroundJobScheduler.getInstance(context).scheduleBackup(intervalHours)
        }

        fun cancelPeriodicWork(context: Context?) {
            BackgroundJobScheduler.getInstance(context).cancelBackup()
        }
    }

    private val backupNotificationBuilder =
        NotificationCompat.Builder(context, BACKUP_CHANNEL_ID)
            .setColorized(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setAutoCancel(true)
            .setContentTitle(txt(Res.string.pref_category_backup).asString(context))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setColor(context.colorFromAttribute(androidx.appcompat.R.attr.colorPrimary))
            .setSmallIcon(android.R.drawable.stat_sys_download)

    override suspend fun doWork(): Result {
        context.createNotificationChannel(
            BACKUP_CHANNEL_ID,
            BACKUP_CHANNEL_NAME,
            BACKUP_CHANNEL_DESCRIPTION
        )

        val foregroundInfo = if (SDK_INT >= 29)
            ForegroundInfo(
                BACKUP_NOTIFICATION_ID, backupNotificationBuilder.build(), FOREGROUND_SERVICE_TYPE_DATA_SYNC
            ) else ForegroundInfo(BACKUP_NOTIFICATION_ID, backupNotificationBuilder.build())
        setForeground(foregroundInfo)

        WorkManagerHooks.runBackup(context)

        return Result.success()
    }
}
