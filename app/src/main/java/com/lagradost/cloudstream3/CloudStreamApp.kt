package com.lagradost.cloudstream3

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.services.WorkManagerHooks
import com.lagradost.cloudstream3.utils.AppDebug
import com.lagradost.cloudstream3.utils.BackupUtils
import com.lagradost.cloudstream3.utils.ImageLoader.buildImageLoader

import java.lang.ref.WeakReference

class CloudStreamApp : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        AppDebug.isDebug = BuildConfig.DEBUG
        context = this

        WorkManagerHooks.pluginLoader = { ctx ->
            PluginManager.___DO_NOT_CALL_FROM_A_PLUGIN_loadAllOnlinePlugins(ctx)
            PluginManager.___DO_NOT_CALL_FROM_A_PLUGIN_loadAllLocalPlugins(ctx, false)
        }
        WorkManagerHooks.backupRunner = { ctx ->
            BackupUtils.backup(ctx)
        }
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        com.lagradost.api.setContext(base)
        context = base
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        // Coil 3 singleton image loader initialization
        return buildImageLoader(applicationContext)
    }

    companion object {
        private var _context: WeakReference<Context>? = null
        var context: Context?
            get() = _context?.get()
            private set(value) {
                _context = value?.let { WeakReference(it) }
            }

        /** Use to get Activity from Context. */
        tailrec fun Context.getActivity(): Activity? {
            return when (this) {
                is Activity -> this
                is ContextWrapper -> baseContext.getActivity()
                else -> null
            }
        }
    }
}

val appContext: Context? get() = CloudStreamApp.context

