package com.lagradost.cloudstream3.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build.VERSION.SDK_INT
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.core.net.toUri
import cloudstream.shared_ui.generated.resources.*
import com.lagradost.cloudstream3.BuildConfig
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.utils.Globals.PHONE
import com.lagradost.cloudstream3.utils.Globals.isLayout
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString

private const val PACKAGE_NAME = BuildConfig.APPLICATION_ID
private const val TAG = "PowerManagerAPI"

object BatteryOptimizationChecker {

    fun isAppRestricted(context: Context?): Boolean {
        if (SDK_INT >= 23 && context != null) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return !powerManager.isIgnoringBatteryOptimizations(context.packageName)
        }

        return false // below Marshmallow, it's always unrestricted when app is in background
    }

    fun openBatteryOptimizationSettings(context: Context) {
        if (shouldShowBatteryOptimizationDialog(context)) {
            context.showBatteryOptimizationDialog()
        }
    }

    fun Context.showBatteryOptimizationDialog() {
        try {
            AlertDialog.Builder(this)
                .setTitle(txt(Res.string.battery_dialog_title).asString(this))
                .setIcon(R.drawable.ic_battery)
                .setMessage(txt(Res.string.battery_dialog_message).asString(this))
                .setPositiveButton(txt(Res.string.ok).asString(this)) { _, _ -> showRequestIgnoreBatteryOptDialog() }
                .setNegativeButton(txt(Res.string.cancel).asString(this)) { _, _ ->
                    com.lagradost.cloudstream3.shared.persistence.repository.AppPreferenceManager.setBooleanSync(
                        com.lagradost.cloudstream3.shared.persistence.repository.AppPreferenceManager.KEY_BATTERY_OPTIMISATION,
                        false
                    )
                }
                .show()
        } catch (t: Throwable) {
            Log.e(TAG, "Error showing battery optimization dialog", t)
        }
    }

    private fun shouldShowBatteryOptimizationDialog(context: Context): Boolean {
        val isRestricted = isAppRestricted(context)
        val isOptimizedNotShown = com.lagradost.cloudstream3.shared.persistence.repository.AppPreferenceManager.getBooleanSync(
            com.lagradost.cloudstream3.shared.persistence.repository.AppPreferenceManager.KEY_BATTERY_OPTIMISATION,
            true
        )
        return isRestricted && isOptimizedNotShown && isLayout(PHONE)
    }

    private fun Context.showRequestIgnoreBatteryOptDialog() {
        try {
            val intent = Intent().apply {
                action =  Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                data = "package:$PACKAGE_NAME".toUri()
            }
            startActivity(intent)
        } catch (t: Throwable) {
            Log.e(TAG, "Unable to invoke APP_DETAILS intent", t)
            if (t is ActivityNotFoundException) {
                showToast("Exception: Activity Not Found")
            } else {
                showToast(Res.string.app_info_intent_error)
            }
        }
    }
}
