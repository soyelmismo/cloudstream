package com.lagradost.cloudstream3.plugins

/**
 * Interface to abstract how plugins are loaded across different platforms.
 */
interface PluginLoader {
    /**
     * Absolute path to the platform-specific directory where plugins are stored, or null if unspecified.
     */
    val pluginsDirectory: String? get() = null

    /**
     * Attempts to load a plugin from the specified file path.
     * @param filePath The absolute path to the plugin file (.cs3 or .jar).
     * @return The loaded BasePlugin instance, or null if loading failed.
     */
    fun loadPlugin(filePath: String): BasePlugin?
    
    /**
     * Attempts to read and parse the manifest.json from the specified plugin file.
     * @param filePath The absolute path to the plugin file (.cs3 or .jar).
     * @return The parsed Manifest, or null if reading failed.
     */
    fun getManifest(filePath: String): BasePlugin.Manifest? = null

    /**
     * Unloads and cleans up the plugin from memory and disk.
     * @param filePathOrName The file path, internal name, or display name of the plugin.
     * @return True if uninstalled and cleaned up, false otherwise.
     */
    fun unloadPlugin(filePathOrName: String): Boolean = false
}
