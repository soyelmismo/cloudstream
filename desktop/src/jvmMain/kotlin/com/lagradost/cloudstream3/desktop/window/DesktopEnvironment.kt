package com.lagradost.cloudstream3.desktop.window

import com.lagradost.api.Log
import com.lagradost.cloudstream3.desktop.player.DesktopVideoPlayer
import com.lagradost.cloudstream3.shared.persistence.database.AppDatabase
import com.lagradost.cloudstream3.shared.persistence.database.DefaultAppDatabase

private const val TAG = "DesktopEnvironment"

object DesktopEnvironment {
    fun configureGraphicsPipelines() {
        val osName = System.getProperty("os.name")?.lowercase() ?: ""
        if (System.getProperty("skiko.renderApi") == null && System.getenv("SKIKO_RENDER_API") == null) {
            val optimalRenderApi = when {
                osName.contains("mac") || osName.contains("darwin") -> "METAL"
                osName.contains("win") -> "DIRECTX"
                else -> "OPENGL"
            }
            System.setProperty("skiko.renderApi", optimalRenderApi)
        }

        if (System.getProperty("skiko.vsync.enabled") == null) {
            System.setProperty("skiko.vsync.enabled", "true")
        }

        if (System.getProperty("skiko.rendering.software") == null) {
            System.setProperty("skiko.rendering.software", "false")
        }

        System.setProperty("sun.awt.noerasebackground", "true")

        if (osName.contains("win")) {
            if (System.getProperty("sun.java2d.d3d") == null) {
                System.setProperty("sun.java2d.d3d", "true")
            }
        } else if (!osName.contains("mac")) {
            if (System.getProperty("sun.java2d.opengl") == null) {
                System.setProperty("sun.java2d.opengl", "true")
            }
        }
    }

    fun releaseResources(player: DesktopVideoPlayer?, database: AppDatabase?) {
        try {
            player?.release()
        } catch (e: Throwable) {
            Log.e(TAG, "Error releasing video player: ${e.message}")
        }
        try {
            if (database != null && database !is DefaultAppDatabase) {
                database.close()
            }
        } catch (_: Throwable) {}
    }
}
