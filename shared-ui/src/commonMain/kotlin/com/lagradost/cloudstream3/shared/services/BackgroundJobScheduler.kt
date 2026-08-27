package com.lagradost.cloudstream3.shared.services

/**
 * Multiplatform abstraction for scheduling and orchestrating periodic background tasks,
 * such as automated subscription updates and periodic database / settings backups.
 *
 * Implementations:
 * - Android: Delegates to AndroidX [androidx.work.WorkManager].
 * - JVM Desktop: Coroutine daemon loop executing tasks on [kotlinx.coroutines.Dispatchers.IO].
 */
expect class BackgroundJobScheduler() {
    /**
     * Schedules periodic checking of subscriptions for new episodes / releases.
     * @param intervalMinutes Interval in minutes (default 360 = 6 hours). If <= 0, cancels periodic checking.
     */
    fun scheduleSubscriptionCheck(intervalMinutes: Long = 360L)

    /**
     * Cancels scheduled subscription checking.
     */
    fun cancelSubscriptionCheck()

    /**
     * Schedules periodic database and settings backups.
     * @param intervalHours Interval in hours. If <= 0, cancels periodic backup.
     */
    fun scheduleBackup(intervalHours: Long)

    /**
     * Cancels scheduled periodic backups.
     */
    fun cancelBackup()

    /**
     * Cancels all scheduled background jobs.
     */
    fun cancelAll()

    companion object {
        fun getInstance(): BackgroundJobScheduler
    }
}
