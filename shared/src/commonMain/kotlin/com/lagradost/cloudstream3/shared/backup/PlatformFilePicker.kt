package com.lagradost.cloudstream3.shared.backup

/**
 * Cross-platform file picker interface for opening and saving backup/export files.
 */
expect object PlatformFilePicker {
    /**
     * Opens a native file picker to read a file as text.
     * @param extensions Allowed file extensions (e.g. listOf("json", "txt"))
     * @return Content of the file as String, or null if cancelled / error
     */
    suspend fun readTextFromFile(extensions: List<String> = listOf("json", "txt")): String?

    /**
     * Opens a native file picker to read a file as bytes.
     * @param extensions Allowed file extensions (e.g. listOf("json", "txt"))
     * @return Content of the file as ByteArray, or null if cancelled / error
     */
    suspend fun pickFileForOpen(extensions: List<String> = listOf("json", "txt")): ByteArray?

    /**
     * Opens a native file picker or storage mechanism to save text content to a file.
     * @param defaultFileName Default file name suggested to user (e.g. "CS3_Backup_2026_08_26.json")
     * @param content The text content to write into the file
     * @return True if file was saved successfully, false otherwise
     */
    suspend fun pickFileForSave(defaultFileName: String, content: String): Boolean
}
