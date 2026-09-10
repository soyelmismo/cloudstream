package com.lagradost.cloudstream3.services

import android.content.Context

/**
 * Hook registry allowing application layer to delegate plugin loading and backup routines
 * to shared-ui WorkManagers without circular dependencies.
 */
object WorkManagerHooks {
    var pluginLoader: (suspend (Context) -> Unit)? = null
    var backupRunner: (suspend (Context) -> Unit)? = null

    suspend fun loadPlugins(context: Context) {
        pluginLoader?.invoke(context)
    }

    suspend fun runBackup(context: Context) {
        backupRunner?.invoke(context)
    }
}
