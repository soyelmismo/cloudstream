package com.lagradost.cloudstream3.shared.backup

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

actual object PlatformFilePicker {
    /**
     * Optional hook registered by Android application layer (e.g. Activity OpenDocument launcher).
     */
    var openFileHook: (suspend (List<String>) -> ByteArray?)? = null

    /**
     * Optional hook registered by Android application layer (e.g. Activity CreateDocument launcher).
     */
    var saveFileHook: (suspend (String, String) -> Boolean)? = null

    actual suspend fun readTextFromFile(extensions: List<String>): String? = withContext(Dispatchers.IO) {
        val bytes = pickFileForOpen(extensions) ?: return@withContext null
        try {
            bytes.decodeToString()
        } catch (_: Throwable) {
            null
        }
    }

    actual suspend fun pickFileForOpen(extensions: List<String>): ByteArray? = withContext(Dispatchers.IO) {
        openFileHook?.invoke(extensions)?.let { return@withContext it }

        // Fallback: search backups directory or downloads
        try {
            val context = com.lagradost.api.getContext() as? Context
            val candidateDirs = listOfNotNull(
                context?.getExternalFilesDir("backups"),
                context?.filesDir?.let { File(it, "backups") },
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            )

            for (dir in candidateDirs) {
                if (dir.exists() && dir.isDirectory) {
                    val matchingFile = dir.listFiles()
                        ?.filter { file -> extensions.any { ext -> file.name.endsWith(".$ext", ignoreCase = true) } }
                        ?.maxByOrNull { it.lastModified() }

                    if (matchingFile != null && matchingFile.canRead()) {
                        return@withContext matchingFile.readBytes()
                    }
                }
            }
        } catch (_: Throwable) {}
        null
    }

    actual suspend fun pickFileForSave(defaultFileName: String, content: String): Boolean = withContext(Dispatchers.IO) {
        saveFileHook?.invoke(defaultFileName, content)?.let { return@withContext it }

        try {
            val context = com.lagradost.api.getContext() as? Context
            val backupDir = context?.getExternalFilesDir("backups")
                ?: context?.filesDir?.let { File(it, "backups") }
                ?: File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "CloudStream")

            if (!backupDir.exists()) {
                backupDir.mkdirs()
            }
            val targetFile = File(backupDir, defaultFileName)
            targetFile.writeText(content, Charsets.UTF_8)
            true
        } catch (_: Throwable) {
            false
        }
    }
}
