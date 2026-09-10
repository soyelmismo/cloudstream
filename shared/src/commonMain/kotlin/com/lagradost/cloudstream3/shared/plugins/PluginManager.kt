package com.lagradost.cloudstream3.shared.plugins

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.PluginLoader
import java.io.File

interface PluginManager {
    val pluginsDirectory: File
    suspend fun downloadPlugin(url: String, targetFile: File): Result<File>
    suspend fun installPlugin(plugin: PluginItem): Result<PluginItem>
    suspend fun uninstallPlugin(filenameOrName: String): Result<Unit>
    fun isPluginLoaded(name: String): Boolean
    fun getPluginManifest(file: File): BasePlugin.Manifest? = null
}

class DefaultPluginManager(
    private val pluginLoader: PluginLoader? = null,
    baseDirectory: File? = null
) : PluginManager {

    override val pluginsDirectory: File = run {
        val customDir = pluginLoader?.pluginsDirectory
        val dir = when {
            baseDirectory != null -> baseDirectory
            customDir != null -> File(customDir)
            else -> File(System.getProperty("user.home") ?: ".", ".cloudstream/plugins")
        }
        if (!dir.exists()) {
            dir.mkdirs()
        }
        dir
    }

    private val json: kotlinx.serialization.json.Json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    override fun getPluginManifest(file: File): BasePlugin.Manifest? {
        val loaderManifest = pluginLoader?.getManifest(file.absolutePath)
        if (loaderManifest != null) return loaderManifest
        if (!file.exists() || !file.isFile) return null
        return readManifestDirectly(file)
    }

    private fun readManifestDirectly(file: File): BasePlugin.Manifest? {
        return runCatching {
            java.util.zip.ZipFile(file).use { zip ->
                val entry = zip.getEntry("manifest.json") ?: return@use null
                zip.getInputStream(entry).bufferedReader().use { reader ->
                    json.decodeFromString<BasePlugin.Manifest>(reader.readText())
                }
            }
        }.getOrNull()
    }

    override suspend fun downloadPlugin(url: String, targetFile: File): Result<File> {
        return runCatching {
            val response = app.get(url)
            if (!response.isSuccessful) {
                throw IllegalStateException("Failed to download plugin: HTTP ${response.code}")
            }
            val bytes = response.body.bytes()
            if (bytes.isEmpty()) {
                throw IllegalStateException("Downloaded plugin binary is empty from $url")
            }
            val parent = targetFile.parentFile ?: pluginsDirectory
            if (!parent.exists()) {
                parent.mkdirs()
            }
            val tempFile = File(parent, "${targetFile.name}.${System.currentTimeMillis()}.tmp")
            tempFile.writeBytes(bytes)
            if (targetFile.exists()) {
                targetFile.delete()
            }
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
            targetFile
        }
    }

    private fun resolveTargetFile(plugin: PluginItem): File {
        val existing = plugin.localFilePath?.let { File(it) }?.takeIf { it.exists() }
        if (existing != null) return existing
        val ext = if (plugin.url.endsWith(".jar", ignoreCase = true)) "jar" else "cs3"
        return File(pluginsDirectory, "${plugin.internalName}.$ext")
    }

    private suspend fun downloadIfNeeded(plugin: PluginItem, targetFile: File): Result<Unit> {
        val shouldDownload = (!targetFile.exists() || plugin.url.isNotBlank()) &&
            plugin.url.startsWith("http", ignoreCase = true)
        if (!shouldDownload) return Result.success(Unit)

        val downloadResult = downloadPlugin(plugin.url, targetFile)
        if (downloadResult.isFailure) {
            return Result.failure(
                downloadResult.exceptionOrNull() ?: IllegalStateException("Download failed")
            )
        }
        return Result.success(Unit)
    }

    private fun applyPluginLoading(targetFile: File, plugin: PluginItem) {
        val loader = pluginLoader ?: return
        if (plugin.isEnabled && targetFile.exists()) {
            loader.unloadPlugin(targetFile.absolutePath)
            loader.unloadPlugin(plugin.internalName)
            loader.loadPlugin(targetFile.absolutePath)
        } else {
            loader.unloadPlugin(targetFile.absolutePath)
            loader.unloadPlugin(plugin.internalName)
            loader.unloadPlugin(plugin.name)
        }
    }

    override suspend fun installPlugin(plugin: PluginItem): Result<PluginItem> {
        return runCatching {
            val targetFile = resolveTargetFile(plugin)
            val downloadRes = downloadIfNeeded(plugin, targetFile)
            if (downloadRes.isFailure) {
                throw downloadRes.exceptionOrNull() ?: IllegalStateException("Failed to download plugin")
            }
            applyPluginLoading(targetFile, plugin)
            plugin.copy(
                isInstalled = true,
                isDownloaded = true,
                localFilePath = targetFile.absolutePath
            )
        }
    }

    private fun resolveUninstallFile(filenameOrName: String): File? {
        val directFile = File(filenameOrName).takeIf { it.exists() }
        if (directFile != null) return directFile

        val dir = pluginsDirectory
        if (!dir.exists() || !dir.isDirectory) return null

        val cleanName = filenameOrName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .removeSuffix(".cs3")
            .removeSuffix(".jar")

        return findFileInDirectory(dir, filenameOrName, cleanName)
    }

    private fun findFileInDirectory(dir: File, name: String, cleanName: String): File? {
        val candidates = listOf(
            File(dir, name),
            File(dir, "$name.cs3"),
            File(dir, "$name.jar"),
            File(dir, "$cleanName.cs3"),
            File(dir, "$cleanName.jar")
        )
        candidates.firstOrNull { it.exists() }?.let { return it }

        return dir.listFiles()?.firstOrNull { f ->
            f.isFile && matchesUninstallFileName(f, name, cleanName)
        }
    }

    private fun matchesUninstallFileName(f: File, name: String, cleanName: String): Boolean {
        val fName = f.name
        val fRaw = f.nameWithoutExtension
        return fName.equals(name, ignoreCase = true) ||
            fRaw.equals(cleanName, ignoreCase = true) ||
            fRaw.equals("${cleanName}Provider", ignoreCase = true) ||
            cleanName.equals("${fRaw}Provider", ignoreCase = true)
    }

    override suspend fun uninstallPlugin(filenameOrName: String): Result<Unit> {
        return runCatching {
            val targetFile = resolveUninstallFile(filenameOrName)
            val cleanName = filenameOrName.removeSuffix(".cs3").removeSuffix(".jar")
            pluginLoader?.let { loader ->
                targetFile?.let { loader.unloadPlugin(it.absolutePath) }
                loader.unloadPlugin(cleanName)
                if (cleanName != filenameOrName) {
                    loader.unloadPlugin(filenameOrName)
                }
            }
            targetFile?.delete()
        }
    }

    override fun isPluginLoaded(name: String): Boolean {
        if (name.isBlank()) return false
        if (pluginLoader?.isPluginLoaded(name) == true) return true
        val cleanName = File(name).nameWithoutExtension
        if (cleanName != name && pluginLoader?.isPluginLoaded(cleanName) == true) return true
        return isProviderLoadedInApiHolder(name, cleanName)
    }

    private fun isProviderLoadedInApiHolder(name: String, cleanName: String): Boolean {
        val inApis = APIHolder.apis.withLock {
            APIHolder.apis.any { matchesLoadedProvider(it, name, cleanName) }
        }
        if (inApis) return true

        return APIHolder.allProviders.withLock {
            APIHolder.allProviders.any { matchesLoadedProvider(it, name, cleanName) }
        }
    }

    private fun matchesLoadedProvider(
        provider: com.lagradost.cloudstream3.MainAPI,
        name: String,
        cleanName: String
    ): Boolean = matchesProviderName(provider.name, name, cleanName) ||
        matchesProviderSource(provider.sourcePlugin, name, cleanName) ||
        matchesNormalizedName(provider.name, cleanName)

    private fun matchesProviderName(provName: String, name: String, cleanName: String): Boolean {
        return provName.equals(name, ignoreCase = true) || provName.equals(cleanName, ignoreCase = true)
    }

    private fun matchesProviderSource(sourcePlugin: String?, name: String, cleanName: String): Boolean {
        if (sourcePlugin.isNullOrBlank()) return false
        val srcFile = File(sourcePlugin)
        return sourcePlugin.equals(name, ignoreCase = true) ||
            srcFile.name.equals(name, ignoreCase = true) ||
            srcFile.nameWithoutExtension.equals(cleanName, ignoreCase = true) ||
            sourcePlugin.contains(cleanName, ignoreCase = true)
    }

    private fun matchesNormalizedName(provName: String, cleanName: String): Boolean {
        val normTarget = cleanName.replace(" ", "").removeSuffix("Provider")
        if (normTarget.isBlank()) return false
        val normProv = provName.replace(" ", "").removeSuffix("Provider")
        return normProv.equals(normTarget, ignoreCase = true)
    }
}

