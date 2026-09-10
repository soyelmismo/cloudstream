package com.lagradost.cloudstream3.desktop.window

import java.awt.Image
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon

object DesktopTrayManager {
    val isSupported: Boolean get() = SystemTray.isSupported()

    fun createTrayIcon(
        image: Image,
        tooltip: String = "CloudStream",
        onOpen: () -> Unit,
        onExit: () -> Unit
    ): TrayIcon? {
        if (!isSupported) return null

        val popup = PopupMenu().apply {
            val openItem = MenuItem("Open CloudStream").apply {
                addActionListener { onOpen() }
            }
            val exitItem = MenuItem("Exit").apply {
                addActionListener { onExit() }
            }
            add(openItem)
            addSeparator()
            add(exitItem)
        }

        val trayIcon = TrayIcon(image, tooltip, popup).apply {
            isImageAutoSize = true
            addActionListener { onOpen() }
        }

        try {
            SystemTray.getSystemTray().add(trayIcon)
        } catch (_: Throwable) {
            return null
        }

        return trayIcon
    }

    fun removeTrayIcon(trayIcon: TrayIcon?) {
        if (trayIcon != null && isSupported) {
            try {
                SystemTray.getSystemTray().remove(trayIcon)
            } catch (_: Throwable) {}
        }
    }
}
