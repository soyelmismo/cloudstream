package com.lagradost.cloudstream3.shared.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.FilenameFilter

actual object PlatformFilePicker {
    actual suspend fun readTextFromFile(extensions: List<String>): String? = withContext(Dispatchers.IO) {
        val bytes = pickFileForOpen(extensions) ?: return@withContext null
        try {
            bytes.decodeToString()
        } catch (_: Throwable) {
            null
        }
    }

    actual suspend fun pickFileForOpen(extensions: List<String>): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val frame: Frame? = null
            val dialog = FileDialog(frame, "Select Backup File", FileDialog.LOAD)
            if (extensions.isNotEmpty()) {
                dialog.filenameFilter = FilenameFilter { _, name ->
                    extensions.any { ext -> name.endsWith(".$ext", ignoreCase = true) }
                }
            }
            dialog.isVisible = true
            val dir = dialog.directory
            val file = dialog.file
            if (dir != null && file != null) {
                val selectedFile = File(dir, file)
                if (selectedFile.exists() && selectedFile.canRead()) {
                    return@withContext selectedFile.readBytes()
                }
            }
            null
        } catch (_: Throwable) {
            null
        }
    }

    actual suspend fun pickFileForSave(defaultFileName: String, content: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val frame: Frame? = null
            val dialog = FileDialog(frame, "Save Backup File", FileDialog.SAVE)
            dialog.file = defaultFileName
            dialog.isVisible = true
            val dir = dialog.directory
            val file = dialog.file
            if (dir != null && file != null) {
                val targetFile = File(dir, file)
                targetFile.writeText(content, Charsets.UTF_8)
                return@withContext true
            }
            false
        } catch (_: Throwable) {
            // Fallback: save to user home / downloads or working directory
            try {
                val userHome = System.getProperty("user.home") ?: "."
                val downloads = File(userHome, "Downloads")
                val targetDir = if (downloads.exists() && downloads.isDirectory) downloads else File(userHome)
                val targetFile = File(targetDir, defaultFileName)
                targetFile.writeText(content, Charsets.UTF_8)
                true
            } catch (_: Throwable) {
                false
            }
        }
    }
}
