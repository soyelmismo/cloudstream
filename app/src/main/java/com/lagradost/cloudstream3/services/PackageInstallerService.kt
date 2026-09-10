package com.lagradost.cloudstream3.services

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.os.Build.VERSION.SDK_INT
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.PendingIntentCompat
import com.lagradost.cloudstream4.generated.resources.*
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.MainActivity.Companion.deleteFileOnExit
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.ApkInstaller
import com.lagradost.cloudstream3.utils.AppContextUtils.createNotificationChannel
import com.lagradost.cloudstream3.utils.UIHelper.colorFromAttribute
import com.lagradost.cloudstream3.utils.asString
import com.lagradost.cloudstream3.utils.txt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.resources.StringResource
import kotlin.math.roundToInt

class PackageInstallerService : Service() {
    private var installer: ApkInstaller? = null
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val updateLock = Mutex()

    private val baseNotification by lazy {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent =
            PendingIntentCompat.getActivity(this, 0, intent, 0, false)

        NotificationCompat.Builder(this, UPDATE_CHANNEL_ID)
            .setAutoCancel(false)
            .setColorized(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setColor(this.colorFromAttribute(R.attr.colorPrimary))
            .setContentTitle(txt(Res.string.update_notification_downloading).asString(this))
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.drawable.rdload)
    }

    override fun onCreate() {
        super.onCreate()
        this.createNotificationChannel(
            UPDATE_CHANNEL_ID,
            UPDATE_CHANNEL_NAME,
            UPDATE_CHANNEL_DESCRIPTION
        )
        if (SDK_INT >= 29) {
            startForeground(UPDATE_NOTIFICATION_ID, baseNotification.build(), FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(UPDATE_NOTIFICATION_ID, baseNotification.build())
        }
    }

    private fun cleanOldUpdates() {
        val appUpdateName = "CloudStream"
        val appUpdateSuffix = "apk"

        cacheDir.listFiles()?.filter {
            it.name.startsWith(appUpdateName) && it.extension == appUpdateSuffix
        }?.forEach {
            deleteFileOnExit(it)
        }
    }

    private suspend fun streamDownload(url: String) {
        val body = app.get(url).body
        val inputStream = body.byteStream()
        installer = ApkInstaller(this)
        val totalSize = body.contentLength()
        var currentSize = 0

        installer?.installApk(this, inputStream, totalSize, { bytesRead ->
            currentSize += bytesRead
            if (totalSize > 0L) {
                val percentage = currentSize / totalSize.toFloat()
                updateNotificationProgress(
                    percentage,
                    ApkInstaller.InstallProgressStatus.Downloading
                )
            }
        }) { status ->
            updateNotificationProgress(0f, status)
        }
    }

    private suspend fun downloadUpdate(url: String): Boolean {
        return try {
            Log.d("PackageInstallerService", "Downloading update: $url")
            cleanOldUpdates()
            updateLock.withLock {
                updateNotificationProgress(0f, ApkInstaller.InstallProgressStatus.Downloading)
                streamDownload(url)
            }
            true
        } catch (e: Exception) {
            logError(e)
            updateNotificationProgress(0f, ApkInstaller.InstallProgressStatus.Failed)
            false
        }
    }

    private fun resolveStatusTitle(state: ApkInstaller.InstallProgressStatus): StringResource {
        return when (state) {
            ApkInstaller.InstallProgressStatus.Installing -> Res.string.update_notification_installing
            ApkInstaller.InstallProgressStatus.Preparing,
            ApkInstaller.InstallProgressStatus.Downloading -> Res.string.update_notification_downloading
            ApkInstaller.InstallProgressStatus.Failed -> Res.string.update_notification_failed
        }
    }

    private fun buildUpdateNotification(
        percentage: Float,
        state: ApkInstaller.InstallProgressStatus
    ): Notification {
        val titleRes = resolveStatusTitle(state)
        val isFailed = state == ApkInstaller.InstallProgressStatus.Failed

        return baseNotification
            .setContentTitle(txt(titleRes).asString(this))
            .apply {
                if (isFailed) {
                    setSmallIcon(R.drawable.rderror)
                    setAutoCancel(true)
                } else {
                    val progressValue = (10000 * percentage).roundToInt()
                    val isIndeterminate = state != ApkInstaller.InstallProgressStatus.Downloading
                    setProgress(10000, progressValue, isIndeterminate)
                }
            }
            .build()
    }

    private fun updateNotificationProgress(
        percentage: Float,
        state: ApkInstaller.InstallProgressStatus
    ) {
        val newNotification = buildUpdateNotification(percentage, state)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val id = if (state == ApkInstaller.InstallProgressStatus.Failed) {
            UPDATE_NOTIFICATION_ID + 1
        } else {
            UPDATE_NOTIFICATION_ID
        }
        notificationManager.notify(id, newNotification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
        serviceScope.launch {
            try {
                downloadUpdate(url)
                delay(10_000)
            } finally {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceJob.cancel()
        installer?.unregisterInstallActionReceiver()
        installer = null
        super.onDestroy()
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onTimeout(reason: Int) {
        stopSelf()
        Log.e("PackageInstallerService", "Service stopped due to timeout: $reason")
    }

    companion object {
        private const val EXTRA_URL = "EXTRA_URL"

        const val UPDATE_CHANNEL_ID = "cloudstream3.updates"
        const val UPDATE_CHANNEL_NAME = "App Updates"
        const val UPDATE_CHANNEL_DESCRIPTION = "App updates notification channel"
        const val UPDATE_NOTIFICATION_ID = -68454136

        fun getIntent(
            context: Context,
            url: String,
        ): Intent {
            return Intent(context, PackageInstallerService::class.java)
                .putExtra(EXTRA_URL, url)
        }
    }
}