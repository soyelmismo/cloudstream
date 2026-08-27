package com.lagradost.cloudstream3.shared.services

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.lagradost.cloudstream3.services.BackupWorkManager
import com.lagradost.cloudstream3.services.SubscriptionWorkManager
import java.util.concurrent.TimeUnit

/**
 * Android implementation of [BackgroundJobScheduler] utilizing AndroidX WorkManager.
 */
actual class BackgroundJobScheduler actual constructor() {
    private var customContext: Context? = null

    constructor(context: Context?) : this() {
        this.customContext = context
    }

    private fun getCtx(): Context? = customContext ?: (com.lagradost.api.getContext() as? Context)

    actual fun scheduleSubscriptionCheck(intervalMinutes: Long) {
        val ctx = getCtx() ?: return
        if (intervalMinutes <= 0L) {
            cancelSubscriptionCheck()
            return
        }

        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val periodicWork = PeriodicWorkRequest.Builder(
                SubscriptionWorkManager::class.java,
                intervalMinutes,
                TimeUnit.MINUTES
            )
                .addTag(SUBSCRIPTION_WORK_NAME)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                SUBSCRIPTION_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicWork
            )
        } catch (e: Throwable) {
            // Worker class lookup or WorkManager enqueue error
        }
    }

    actual fun cancelSubscriptionCheck() {
        val ctx = getCtx() ?: return
        try {
            WorkManager.getInstance(ctx).cancelUniqueWork(SUBSCRIPTION_WORK_NAME)
        } catch (_: Throwable) {}
    }

    actual fun scheduleBackup(intervalHours: Long) {
        val ctx = getCtx() ?: return
        if (intervalHours <= 0L) {
            cancelBackup()
            return
        }

        try {
            val constraints = Constraints.Builder()
                .setRequiresStorageNotLow(true)
                .build()

            val periodicWork = PeriodicWorkRequest.Builder(
                BackupWorkManager::class.java,
                intervalHours,
                TimeUnit.HOURS
            )
                .addTag(BACKUP_WORK_NAME)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                BACKUP_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicWork
            )
        } catch (e: Throwable) {
            // Worker class lookup or WorkManager enqueue error
        }
    }

    actual fun cancelBackup() {
        val ctx = getCtx() ?: return
        try {
            WorkManager.getInstance(ctx).cancelUniqueWork(BACKUP_WORK_NAME)
        } catch (_: Throwable) {}
    }

    actual fun cancelAll() {
        cancelSubscriptionCheck()
        cancelBackup()
    }

    actual companion object {
        const val SUBSCRIPTION_WORK_NAME = "work_subscription"
        const val BACKUP_WORK_NAME = "work_backup"

        @Volatile
        private var instance: BackgroundJobScheduler? = null
        private val lock = Any()

        fun getInstance(context: Context?): BackgroundJobScheduler {
            return instance ?: synchronized(lock) {
                instance ?: BackgroundJobScheduler(context).also { instance = it }
            }
        }

        actual fun getInstance(): BackgroundJobScheduler = getInstance(null)
    }
}
