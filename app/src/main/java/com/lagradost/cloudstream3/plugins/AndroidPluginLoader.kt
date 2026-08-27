package com.lagradost.cloudstream3.plugins

import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import android.util.Log
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import dalvik.system.PathClassLoader
import java.io.File
import java.io.InputStreamReader
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/**
 * Android implementation of PluginLoader.
 * Loads plugins packaged as .cs3, .zip or .jar files using PathClassLoader.
 */
class AndroidPluginLoader(
    private val context: Context,
    private val parentClassLoader: ClassLoader = context.classLoader
) : PluginLoader {

    override val pluginsDirectory: String
        get() = File(context.filesDir, "plugins").apply { mkdirs() }.absolutePath

    companion object {
        private const val TAG = "AndroidPluginLoader"
    }

    private val loadedPlugins = mutableMapOf<String, BasePlugin>()

    init {
        loadExistingPlugins()
    }

    fun loadExistingPlugins() {
        val pluginsDir = File(context.filesDir, "plugins")
        if (pluginsDir.exists() && pluginsDir.isDirectory) {
            pluginsDir.listFiles { f -> f.extension.lowercase() in listOf("cs3", "jar", "zip") }?.forEach { pluginFile ->
                try {
                    loadPlugin(pluginFile.absolutePath)
                } catch (t: Throwable) {
                    Log.e(TAG, "Error loading existing plugin ${pluginFile.name}: ${t.message}")
                }
            }
        }
    }

    override fun getManifest(filePath: String): BasePlugin.Manifest? {
        val file = File(filePath)
        if (!file.exists() || !file.isFile) return null

        try {
            ZipFile(file).use { zip ->
                val entry = zip.getEntry("manifest.json")
                if (entry != null) {
                    zip.getInputStream(entry).bufferedReader().use { reader ->
                        return parseJson<BasePlugin.Manifest>(reader.readText())
                    }
                }
            }
        } catch (e: Throwable) {
            try {
                ZipInputStream(file.inputStream().buffered()).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (entry.name == "manifest.json") {
                            return parseJson<BasePlugin.Manifest>(zis.bufferedReader().readText())
                        }
                        entry = zis.nextEntry
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to read manifest from $filePath: ${t.message}")
            }
        }
        return null
    }

    override fun loadPlugin(filePath: String): BasePlugin? {
        val file = File(filePath)
        if (!file.exists() || !file.isFile) {
            Log.e(TAG, "Plugin file not found: $filePath")
            return null
        }

        try {
            // Android 14+ requires read-only dex files
            try {
                if (!file.setReadOnly()) {
                    Log.w(TAG, "Failed to set read-only on plugin file: ${file.name}")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to set dex as read-only")
                logError(t)
            }

            val loader = PathClassLoader(filePath, parentClassLoader)
            var manifest: BasePlugin.Manifest? = null
            loader.getResourceAsStream("manifest.json")?.use { stream ->
                InputStreamReader(stream).use { reader ->
                    manifest = parseJson<BasePlugin.Manifest>(reader.readText())
                }
            }

            if (manifest == null) {
                manifest = getManifest(filePath)
            }

            if (manifest == null) {
                Log.e(TAG, "Failed to load plugin ${file.name}: No manifest found")
                return null
            }

            val className = manifest.pluginClassName
            if (className.isNullOrBlank()) {
                Log.e(TAG, "Failed to load plugin ${file.name}: pluginClassName is missing")
                return null
            }

            @Suppress("UNCHECKED_CAST")
            val pluginClass = loader.loadClass(className) as Class<out BasePlugin?>
            val pluginInstance = pluginClass.getDeclaredConstructor().newInstance() as BasePlugin

            pluginInstance.filename = file.absolutePath
            pluginInstance.manifest = manifest

            if (manifest.requiresResources) {
                Log.d(TAG, "Loading resources for ${file.name}")
                try {
                    // based on https://stackoverflow.com/questions/7483568/dynamic-resource-loading-from-other-apk
                    val assets = AssetManager::class.java.getDeclaredConstructor().newInstance()
                    val addAssetPath =
                        AssetManager::class.java.getMethod("addAssetPath", String::class.java)
                    addAssetPath.invoke(assets, file.absolutePath)

                    @Suppress("DEPRECATION")
                    (pluginInstance as? Plugin)?.resources = Resources(
                        assets,
                        context.resources.displayMetrics,
                        context.resources.configuration
                    )
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed to load resources: ${t.message}")
                    logError(t)
                }
            }

            // Invoke load(context) or load() to register providers
            try {
                if (pluginInstance is Plugin) {
                    pluginInstance.load(context)
                } else {
                    pluginInstance.load()
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to call load() on plugin ${file.name}: ${t.message}")
                logError(t)
            }

            synchronized(loadedPlugins) {
                loadedPlugins[file.absolutePath] = pluginInstance
                loadedPlugins[file.name] = pluginInstance
                loadedPlugins[file.nameWithoutExtension] = pluginInstance
                manifest.name?.let { loadedPlugins[it] = pluginInstance }
            }

            APIHolder.notifyProvidersChanged()
            Log.i(TAG, "Successfully loaded plugin ${manifest.name ?: file.name}")
            return pluginInstance
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to load plugin $filePath: ${Log.getStackTraceString(e)}")
            return null
        }
    }

    private fun matchesProvider(provider: com.lagradost.cloudstream3.MainAPI, filePathOrName: String, rawName: String): Boolean {
        val src = provider.sourcePlugin ?: ""
        val srcFile = File(src)
        val srcName = srcFile.name
        val srcRaw = srcFile.nameWithoutExtension
        val targetName = File(filePathOrName).name
        val provName = provider.name
        val normProvName = provName.replace(" ", "")
        val normTargetRaw = rawName.replace(" ", "").removeSuffix("Provider")

        if (src.isNotBlank()) {
            if (src.equals(filePathOrName, ignoreCase = true) ||
                src.equals(rawName, ignoreCase = true) ||
                srcName.equals(targetName, ignoreCase = true) ||
                srcName.equals(filePathOrName, ignoreCase = true) ||
                srcRaw.equals(rawName, ignoreCase = true) ||
                src.contains(rawName, ignoreCase = true) ||
                filePathOrName.contains(srcRaw, ignoreCase = true)
            ) {
                return true
            }
        }

        if (provName.equals(filePathOrName, ignoreCase = true) ||
            provName.equals(rawName, ignoreCase = true) ||
            provName.equals(targetName, ignoreCase = true) ||
            normProvName.equals(normTargetRaw, ignoreCase = true) ||
            rawName.contains(normProvName, ignoreCase = true) ||
            normTargetRaw.contains(normProvName, ignoreCase = true)
        ) {
            return true
        }

        if (provider.mainUrl.isNotBlank()) {
            val mainUrl = provider.mainUrl
            if (mainUrl.contains(rawName, ignoreCase = true) ||
                (normTargetRaw.length >= 4 && mainUrl.contains(normTargetRaw, ignoreCase = true))
            ) {
                return true
            }
        }

        return false
    }

    private fun unloadPluginInMemoryOnly(filePathOrName: String) {
        val rawName = File(filePathOrName).nameWithoutExtension
        val plugin = synchronized(loadedPlugins) {
            loadedPlugins.remove(filePathOrName)
                ?: loadedPlugins.remove(rawName)
                ?: loadedPlugins.remove(File(filePathOrName).name)
        }

        try {
            plugin?.beforeUnload()
        } catch (_: Throwable) {}

        // Remove from APIHolder.apis
        val apisToRemove = com.lagradost.cloudstream3.APIHolder.apis.withLock {
            com.lagradost.cloudstream3.APIHolder.apis.filter { matchesProvider(it, filePathOrName, rawName) }
        }
        apisToRemove.forEach { provider ->
            com.lagradost.cloudstream3.APIHolder.removePluginMapping(provider)
        }

        // Remove from APIHolder.allProviders
        com.lagradost.cloudstream3.APIHolder.allProviders.withLock {
            com.lagradost.cloudstream3.APIHolder.allProviders.removeAll { provider ->
                matchesProvider(provider, filePathOrName, rawName)
            }
        }

        // Remove from extractorApis
        com.lagradost.cloudstream3.utils.extractorApis.withLock {
            com.lagradost.cloudstream3.utils.extractorApis.removeAll { extractor ->
                val src = extractor.sourcePlugin ?: ""
                val srcRaw = File(src).nameWithoutExtension
                (src.isNotBlank() && (src.contains(rawName, ignoreCase = true) || srcRaw.equals(rawName, ignoreCase = true))) ||
                extractor.name.equals(filePathOrName, ignoreCase = true) ||
                extractor.name.equals(rawName, ignoreCase = true)
            }
        }

        com.lagradost.cloudstream3.APIHolder.notifyProvidersChanged()
    }

    override fun unloadPlugin(filePathOrName: String): Boolean {
        return try {
            unloadPluginInMemoryOnly(filePathOrName)
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Error unloading plugin $filePathOrName: ${t.message}")
            false
        }
    }
}
