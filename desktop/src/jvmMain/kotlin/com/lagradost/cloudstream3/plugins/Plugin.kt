package com.lagradost.cloudstream3.plugins

import android.content.Context

abstract class Plugin : BasePlugin() {
    /**
     * Called when your Plugin is loaded on Android or JVM Desktop.
     */
    open fun load(context: Context? = null) {
        load()
    }

    var resources: Any? = null
    var openSettings: ((context: Any) -> Unit)? = null
}
