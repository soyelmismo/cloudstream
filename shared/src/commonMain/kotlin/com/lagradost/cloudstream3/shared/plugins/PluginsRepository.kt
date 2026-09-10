package com.lagradost.cloudstream3.shared.plugins

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.plugins.PluginLoader
import com.lagradost.cloudstream3.plugins.SitePlugin
import com.lagradost.cloudstream3.shared.persistence.repository.AppPreferenceManager
import com.lagradost.cloudstream3.shared.persistence.repository.AppPreferenceRepository
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

interface PluginsRepository {
    suspend fun getRepositories(): List<PluginRepositoryItem>
    suspend fun getInstalledPlugins(): List<PluginItem>
    suspend fun reconcileWithDisk(availablePlugins: List<PluginItem> = emptyList()): List<PluginItem> = getInstalledPlugins()
    suspend fun getAvailablePlugins(repositories: List<PluginRepositoryItem>): List<PluginItem>
    suspend fun getCachedAvailablePlugins(): List<PluginItem> = emptyList()
    suspend fun addRepository(repository: PluginRepositoryItem)
    suspend fun removeRepository(url: String)
    suspend fun installPlugin(plugin: PluginItem): Result<PluginItem>
    suspend fun uninstallPlugin(filenameOrName: String): Result<Unit>
}

@Serializable
private data class RawRepositoryManifest(
    val name: String? = null,
    val description: String? = null,
    val iconUrl: String? = null,
    val manifestVersion: Int? = null,
    val pluginCount: Int = 0,
    val pluginLists: List<String> = emptyList()
)

@Serializable
private data class LegacyPluginData(
    val internalName: String = "",
    val url: String? = null,
    val isOnline: Boolean = true,
    val filePath: String = "",
    val version: Int = 1
)

fun normalizeUrlKey(url: String): String =
    url.trim().removeSuffix("/").lowercase()

class DefaultPluginsRepository(
    private val preferenceRepository: AppPreferenceRepository? = null,
    private val pluginManager: PluginManager = DefaultPluginManager(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }
) : PluginsRepository {

    private val reconciliationMutex = Mutex()

    constructor(
        preferenceRepository: AppPreferenceRepository? = null,
        pluginLoader: PluginLoader? = null
    ) : this(
        preferenceRepository = preferenceRepository,
        pluginManager = DefaultPluginManager(pluginLoader)
    )

    companion object {
        const val KEY_REPOSITORIES = "REPOSITORIES_KEY"
        const val KEY_INSTALLED_PLUGINS = "INSTALLED_PLUGINS_KEY"
        const val KEY_CACHED_AVAILABLE_PLUGINS = "CACHED_AVAILABLE_PLUGINS_KEY"
        const val KEY_LEGACY_PLUGINS = "PLUGINS_KEY"
        const val KEY_LEGACY_PLUGINS_LOCAL = "PLUGINS_KEY_LOCAL"
        val PREBUILT_REPOSITORIES: List<PluginRepositoryItem> = emptyList()
    }

    private suspend fun readPreferenceString(key: String): String? {
        val repo = preferenceRepository
        return if (repo != null) repo.getString(key) else AppPreferenceManager.getStringSync(key)
    }

    private suspend fun savePreferenceString(key: String, value: String) {
        val repo = preferenceRepository
        if (repo != null) {
            repo.setString(key, value)
        } else {
            AppPreferenceManager.setStringSync(key, value)
        }
    }

    override suspend fun getRepositories(): List<PluginRepositoryItem> {
        val savedJson = readPreferenceString(KEY_REPOSITORIES)
        if (savedJson.isNullOrBlank()) return emptyList()

        return try {
            json.decodeFromString<List<PluginRepositoryItem>>(savedJson)
        } catch (_: Throwable) {
            parseFallbackRepositories(savedJson)
        }
    }

    private fun parseFallbackRepositories(savedJson: String): List<PluginRepositoryItem> {
        return try {
            val array = json.decodeFromString<List<Map<String, String>>>(savedJson)
            array.mapNotNull { map ->
                val url = map["url"] ?: return@mapNotNull null
                val name = map["name"] ?: "Repository"
                PluginRepositoryItem(name = name, url = url)
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    override suspend fun addRepository(repository: PluginRepositoryItem) {
        val currentRepos = getRepositories()
        val updated = (currentRepos + repository).distinctBy { normalizeUrlKey(it.url) }
        savePreferenceString(KEY_REPOSITORIES, json.encodeToString(updated))
    }

    override suspend fun removeRepository(url: String) {
        val currentRepos = getRepositories()
        val normalizedTarget = normalizeUrlKey(url)
        val updated = currentRepos.filter { normalizeUrlKey(it.url) != normalizedTarget }
        savePreferenceString(KEY_REPOSITORIES, json.encodeToString(updated))

        val cached = getCachedAvailablePlugins()
        val pruned = cached.filter { normalizeUrlKey(it.repositoryUrl) != normalizedTarget }
        saveCachedAvailablePlugins(pruned)
    }

    override suspend fun getInstalledPlugins(): List<PluginItem> {
        return reconcileInstalledWithDisk(availablePool = null)
    }

    override suspend fun reconcileWithDisk(availablePlugins: List<PluginItem>): List<PluginItem> {
        return reconcileInstalledWithDisk(availablePool = availablePlugins)
    }

    private suspend fun reconcileInstalledWithDisk(availablePool: List<PluginItem>?): List<PluginItem> {
        return reconciliationMutex.withLock {
            val modernList = tryDecodeModernInstalled()
            val legacyList = tryDecodeLegacyInstalled()
            val baseInstalled = combineInstalledLists(modernList, legacyList)

            val diskFiles = collectDiskPluginFiles()
            val availableList = availablePool ?: getCachedAvailablePlugins()
            val (reconciledList, hasChanges) = reconcileFilesAndInstalled(
                diskFiles = diskFiles,
                installed = baseInstalled,
                available = availableList
            )

            if (hasChanges || modernList.size != reconciledList.size) {
                saveInstalledPlugins(reconciledList)
            }

            reconciledList
        }
    }

    private fun combineInstalledLists(modern: List<PluginItem>, legacy: List<PluginItem>): List<PluginItem> {
        if (legacy.isEmpty()) return modern
        val modernKeys = modern.map { it.internalName.ifBlank { it.name } }.toSet()
        val missingFromModern = legacy.filter { (it.internalName.ifBlank { it.name }) !in modernKeys }
        return modern + missingFromModern
    }

    private fun collectDiskPluginFiles(): List<File> {
        val dir = pluginManager.pluginsDirectory
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        return dir.listFiles { f ->
            f.isFile && (f.extension.equals("cs3", ignoreCase = true) || f.extension.equals("jar", ignoreCase = true))
        }?.toList() ?: emptyList()
    }

    private fun reconcileFilesAndInstalled(
        diskFiles: List<File>,
        installed: List<PluginItem>,
        available: List<PluginItem>
    ): Pair<List<PluginItem>, Boolean> {
        val result = mutableListOf<PluginItem>()
        var changed = false
        val pluginsDir = pluginManager.pluginsDirectory

        for (current in installed) {
            val matchedFile = diskFiles.firstOrNull { matchesDiskFile(it, current) }
            if (matchedFile != null) {
                val updated = updateInstalledItem(current, matchedFile, available)
                result.add(updated)
                if (updated != current) changed = true
            } else if (isFileDeletedFromDisk(current, pluginsDir)) {
                changed = true
            } else {
                val matchingAvail = findMatchingAvailablePlugin(current.internalName, available)
                    ?: findMatchingAvailablePlugin(current.name, available)
                val enriched = if (matchingAvail != null && shouldEnrichInstalledItem(current, matchingAvail)) {
                    mergePluginWithInstalled(current, matchingAvail)
                } else {
                    current
                }
                val finalItem = if (enriched.isDown && enriched.isEnabled) enriched.copy(isEnabled = false) else enriched
                if (finalItem != current) changed = true
                result.add(finalItem)
            }
        }

        for (file in diskFiles) {
            val existsInResult = result.any { matchesDiskFile(file, it) }
            if (!existsInResult) {
                val newItem = createPluginItemFromDiskFile(file, available)
                result.add(newItem)
                changed = true
            }
        }

        return Pair(result.distinctBy { it.internalName.ifBlank { it.name } }, changed)
    }

    private fun isFileDeletedFromDisk(item: PluginItem, pluginsDir: File): Boolean {
        val path = item.localFilePath ?: return false
        val file = File(path)
        if (file.exists()) return false
        val parentCanonical = runCatching { file.parentFile?.canonicalPath }.getOrNull() ?: return false
        val dirCanonical = runCatching { pluginsDir.canonicalPath }.getOrNull() ?: return false
        return parentCanonical == dirCanonical
    }

    private fun updateInstalledItem(
        current: PluginItem,
        matchedFile: File?,
        available: List<PluginItem>
    ): PluginItem {
        var item = current
        if (matchedFile != null) {
            val needsPathUpdate = item.localFilePath != matchedFile.absolutePath
            val needsStatusUpdate = !item.isInstalled || !item.isDownloaded
            if (needsPathUpdate || needsStatusUpdate) {
                item = item.copy(
                    localFilePath = matchedFile.absolutePath,
                    isInstalled = true,
                    isDownloaded = true,
                    status = PluginStatus.INSTALLED
                )
            }
        }

        val loaded = pluginManager.isPluginLoaded(item.internalName.ifBlank { item.name })
        if (item.isLoaded != loaded) {
            item = item.copy(isLoaded = loaded)
        }

        val matchingAvail = findMatchingAvailablePlugin(item.internalName, available)
            ?: findMatchingAvailablePlugin(item.name, available)
            ?: item.localFilePath?.let { File(it).nameWithoutExtension }?.let { findMatchingAvailablePlugin(it, available) }

        if (matchingAvail != null && shouldEnrichInstalledItem(item, matchingAvail)) {
            item = mergePluginWithInstalled(item, matchingAvail)
        }

        if (item.isDown && item.isEnabled) {
            item = item.copy(isEnabled = false)
        }

        return item
    }

    private fun shouldEnrichInstalledItem(installed: PluginItem, available: PluginItem): Boolean {
        return installed.repositoryUrl.isBlank() ||
            installed.providerStatus != available.providerStatus ||
            (available.isDown && installed.isEnabled) ||
            (installed.iconUrl == null && available.iconUrl != null) ||
            (installed.description == null && available.description != null) ||
            (installed.authors.isEmpty() && available.authors.isNotEmpty()) ||
            (installed.tvTypes.isEmpty() && available.tvTypes.isNotEmpty()) ||
            (installed.language == null && available.language != null) ||
            (installed.name == installed.internalName && available.name.isNotBlank() && available.name != installed.name) ||
            available.version > installed.version
    }

    private fun createPluginItemFromDiskFile(file: File, available: List<PluginItem>): PluginItem {
        val rawName = file.nameWithoutExtension
        val isLoaded = pluginManager.isPluginLoaded(rawName)
        val manifest = pluginManager.getPluginManifest(file)

        val matchedAvail = findMatchingAvailablePlugin(rawName, available)
            ?: manifest?.name?.takeIf { it.isNotBlank() }?.let { findMatchingAvailablePlugin(it, available) }

        val diskVersion = manifest?.version ?: 1
        val pluginName = manifest?.name?.takeIf { it.isNotBlank() } ?: matchedAvail?.name ?: rawName

        if (matchedAvail != null) {
            val remoteVersion = matchedAvail.version
            val effectiveVersion = manifest?.version ?: remoteVersion
            val hasUpdate = remoteVersion > effectiveVersion
            val isDown = matchedAvail.isDown
            return matchedAvail.copy(
                name = pluginName,
                version = effectiveVersion,
                remoteVersion = remoteVersion,
                hasUpdate = hasUpdate,
                isInstalled = true,
                isDownloaded = true,
                localFilePath = file.absolutePath,
                status = PluginStatus.INSTALLED,
                isLoaded = if (isDown) false else isLoaded,
                isEnabled = if (isDown) false else matchedAvail.isEnabled,
                providerStatus = matchedAvail.providerStatus
            )
        }

        return PluginItem(
            internalName = rawName,
            name = pluginName,
            version = diskVersion,
            url = "",
            repositoryUrl = "",
            isDownloaded = true,
            isInstalled = true,
            localFilePath = file.absolutePath,
            status = PluginStatus.INSTALLED,
            isLoaded = isLoaded
        )
    }

    private fun matchesDiskFile(file: File, item: PluginItem): Boolean {
        val rawName = file.nameWithoutExtension
        if (item.internalName.equals(rawName, ignoreCase = true) || item.name.equals(rawName, ignoreCase = true)) {
            return true
        }
        val normRaw = rawName.replace(" ", "").removeSuffix("Provider")
        val normInternal = item.internalName.replace(" ", "").removeSuffix("Provider")
        val normName = item.name.replace(" ", "").removeSuffix("Provider")
        if (normRaw.isNotBlank() && (normInternal.equals(normRaw, ignoreCase = true) || normName.equals(normRaw, ignoreCase = true))) {
            return true
        }
        val path = item.localFilePath ?: return false
        val pathFile = File(path)
        return pathFile.name.equals(file.name, ignoreCase = true) ||
            pathFile.nameWithoutExtension.equals(rawName, ignoreCase = true) ||
            runCatching { pathFile.canonicalPath == file.canonicalPath }.getOrDefault(false)
    }

    private fun findMatchingAvailablePlugin(rawName: String, available: List<PluginItem>): PluginItem? {
        if (rawName.isBlank()) return null
        val normRaw = rawName.replace(" ", "").removeSuffix("Provider")
        return available.firstOrNull { avail ->
            avail.internalName.equals(rawName, ignoreCase = true) ||
                avail.name.equals(rawName, ignoreCase = true) ||
                isMatchingUrlFileName(avail.url, rawName) ||
                isMatchingNormalizedName(avail, normRaw)
        }
    }

    private fun isMatchingUrlFileName(url: String, rawName: String): Boolean {
        if (url.isBlank()) return false
        val cleanUrlName = url.substringBefore('?').substringBefore('#')
            .substringAfterLast('/')
            .removeSuffix(".cs3")
            .removeSuffix(".jar")
        return cleanUrlName.equals(rawName, ignoreCase = true)
    }

    private fun isMatchingNormalizedName(avail: PluginItem, normRaw: String): Boolean {
        if (normRaw.isBlank()) return false
        val normInternal = avail.internalName.replace(" ", "").removeSuffix("Provider")
        val normName = avail.name.replace(" ", "").removeSuffix("Provider")
        return normInternal.equals(normRaw, ignoreCase = true) || normName.equals(normRaw, ignoreCase = true)
    }


    private suspend fun tryDecodeModernInstalled(): List<PluginItem> {
        val savedJson = readPreferenceString(KEY_INSTALLED_PLUGINS)
        if (savedJson.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString<List<PluginItem>>(savedJson)
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private suspend fun tryDecodeLegacyInstalled(): List<PluginItem> {
        val rawOnline = readPreferenceString(KEY_LEGACY_PLUGINS)
        val rawLocal = readPreferenceString(KEY_LEGACY_PLUGINS_LOCAL)
        val listOnline = decodeLegacyJson(rawOnline)
        val listLocal = decodeLegacyJson(rawLocal)
        return (listOnline + listLocal).distinctBy { it.internalName.ifBlank { it.name } }
    }

    private fun decodeLegacyJson(raw: String?): List<PluginItem> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val items = json.decodeFromString<List<LegacyPluginData>>(raw)
            items.map { leg ->
                PluginItem(
                    internalName = leg.internalName,
                    name = leg.internalName,
                    version = maxOf(1, leg.version),
                    url = leg.url ?: "",
                    localFilePath = leg.filePath.ifBlank { null },
                    isDownloaded = true,
                    isInstalled = true
                )
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private suspend fun saveInstalledPlugins(plugins: List<PluginItem>) {
        val modernJson = json.encodeToString(plugins)
        savePreferenceString(KEY_INSTALLED_PLUGINS, modernJson)

        runCatching {
            val legacyItems = plugins.map {
                LegacyPluginData(
                    internalName = it.internalName,
                    url = it.url.ifBlank { null },
                    isOnline = it.url.startsWith("http", ignoreCase = true),
                    filePath = it.localFilePath ?: "",
                    version = it.version
                )
            }
            val legacyJson = json.encodeToString(legacyItems)
            savePreferenceString(KEY_LEGACY_PLUGINS, legacyJson)
        }
    }

    override suspend fun getCachedAvailablePlugins(): List<PluginItem> {
        val savedJson = readPreferenceString(KEY_CACHED_AVAILABLE_PLUGINS)
        if (savedJson.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString<List<PluginItem>>(savedJson)
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private suspend fun saveCachedAvailablePlugins(plugins: List<PluginItem>) {
        runCatching {
            savePreferenceString(KEY_CACHED_AVAILABLE_PLUGINS, json.encodeToString(plugins))
        }
    }

    override suspend fun getAvailablePlugins(repositories: List<PluginRepositoryItem>): List<PluginItem> {
        val installed = getInstalledPlugins()
        val installedMap = installed.associateBy { it.internalName.ifBlank { it.name } }
        val cachedPlugins = getCachedAvailablePlugins()
        val cachedByRepo = cachedPlugins.groupBy { normalizeUrlKey(it.repositoryUrl) }

        val allPlugins = mutableListOf<PluginItem>()
        val updatedRepos = repositories.toMutableList()
        var reposChanged = false

        for ((index, repo) in repositories.withIndex()) {
            val freshPlugins = fetchRepositoryPlugins(repo, installedMap)
            val repoKey = normalizeUrlKey(repo.url)
            val resolvedPlugins = if (freshPlugins.isNotEmpty()) {
                freshPlugins
            } else {
                cachedByRepo[repoKey] ?: emptyList()
            }

            allPlugins.addAll(resolvedPlugins)

            if (resolvedPlugins.isNotEmpty() && repo.pluginCount != resolvedPlugins.size) {
                updatedRepos[index] = repo.copy(
                    pluginCount = resolvedPlugins.size,
                    lastSyncTime = APIHolder.unixTimeMS
                )
                reposChanged = true
            }
        }

        val distinctPlugins = allPlugins.distinctBy { it.internalName.ifBlank { it.name } }
        if (distinctPlugins.isNotEmpty()) {
            saveCachedAvailablePlugins(distinctPlugins)
            reconcileWithDisk(distinctPlugins)
        }

        if (reposChanged) {
            persistUpdatedRepositories(updatedRepos)
        }

        return distinctPlugins
    }

    private suspend fun persistUpdatedRepositories(updatedRepos: List<PluginRepositoryItem>) {
        runCatching {
            val currentSaved = getRepositories()
            val merged = currentSaved.map { saved ->
                val matchingUpdated = updatedRepos.firstOrNull {
                    normalizeUrlKey(it.url) == normalizeUrlKey(saved.url)
                }
                if (matchingUpdated != null) {
                    saved.copy(
                        pluginCount = matchingUpdated.pluginCount,
                        lastSyncTime = matchingUpdated.lastSyncTime ?: saved.lastSyncTime
                    )
                } else {
                    saved
                }
            }
            savePreferenceString(KEY_REPOSITORIES, json.encodeToString(merged))
        }
    }

    private fun getBaseUrl(url: String): String {
        val cleanUrl = url.trim().removeSuffix("/")
        val schemeIndex = cleanUrl.indexOf("://")
        if (schemeIndex == -1) return cleanUrl
        val pathIndex = cleanUrl.indexOf('/', schemeIndex + 3)
        if (pathIndex == -1) return cleanUrl
        return cleanUrl.substringBeforeLast('/')
    }

    private fun isJsonPayload(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed.startsWith("{") || trimmed.startsWith("[")
    }

    private fun normalizeToRawGitUrlIfApplicable(url: String): String {
        var result = url.trim()
        if (result.contains("github.com/") && !result.contains("raw.githubusercontent.com/")) {
            result = result.replace("https://github.com/", "https://raw.githubusercontent.com/")
                .replace("http://github.com/", "https://raw.githubusercontent.com/")
            if (result.contains("/blob/")) {
                result = result.replace("/blob/", "/")
            }
        }
        return result
    }

    private fun buildCandidateUrls(baseUrl: String): List<String> {
        val clean = baseUrl.trim().removeSuffix("/")
        if (clean.endsWith(".json", ignoreCase = true)) return listOf(clean)

        val rawClean = normalizeToRawGitUrlIfApplicable(clean)
        val candidates = linkedSetOf<String>()
        if (rawClean != clean) {
            candidates.add(if (rawClean.endsWith("/builds")) "$rawClean/repo.json" else "$rawClean/builds/repo.json")
            candidates.add(if (rawClean.endsWith("/builds")) "$rawClean/plugins.json" else "$rawClean/builds/plugins.json")
            candidates.add("$rawClean/main/repo.json")
            candidates.add("$rawClean/master/repo.json")
            candidates.add("$rawClean/main/builds/repo.json")
            candidates.add("$rawClean/master/builds/repo.json")
        }
        candidates.add("$clean/repo.json")
        candidates.add("$clean/builds/repo.json")
        candidates.add("$clean/plugins.json")
        candidates.add("$clean/manifest.json")
        candidates.add("$clean/builds.json")
        return candidates.toList()
    }

    private suspend fun probeCandidateUrls(candidates: List<String>): Pair<String, String>? {
        for (candidate in candidates) {
            val res = runCatching { app.get(candidate, allowRedirects = true) }.getOrNull() ?: continue
            if (!res.isSuccessful) continue
            val text = res.text.trim()
            if (isJsonPayload(text)) {
                return Pair(text, res.url.ifBlank { candidate })
            }
        }
        return null
    }

    private suspend fun fetchManifestData(repoUrl: String): Pair<String, String>? {
        val directRes = runCatching {
            app.get(repoUrl, allowRedirects = true)
        }.getOrNull()

        if (directRes != null && directRes.isSuccessful) {
            val text = directRes.text.trim()
            if (isJsonPayload(text)) {
                return Pair(text, directRes.url.ifBlank { repoUrl })
            }
        }

        val effectiveTarget = directRes?.url?.takeIf { it.isNotBlank() } ?: repoUrl
        val candidates = buildCandidateUrls(effectiveTarget) + buildCandidateUrls(repoUrl)
        return probeCandidateUrls(candidates.distinct())
    }

    private suspend fun fetchRepositoryPlugins(
        repo: PluginRepositoryItem,
        installedMap: Map<String, PluginItem>
    ): List<PluginItem> {
        val (manifestRes, effectiveUrl) = fetchManifestData(repo.url) ?: return emptyList()
        val baseUrl = getBaseUrl(effectiveUrl)

        return when {
            manifestRes.startsWith("{") -> parseObjectManifest(manifestRes, repo, baseUrl, installedMap)
            manifestRes.startsWith("[") -> parseArrayManifest(manifestRes, repo.url, baseUrl, installedMap)
            else -> emptyList()
        }
    }

    private suspend fun parseObjectManifest(
        manifestRes: String,
        repo: PluginRepositoryItem,
        baseUrl: String,
        installedMap: Map<String, PluginItem>
    ): List<PluginItem> {
        val manifest = runCatching {
            json.decodeFromString<RawRepositoryManifest>(manifestRes)
        }.getOrNull()

        manifest?.name?.let { updateRepoNameIfGeneric(repo, it, manifest.description) }

        val results = mutableListOf<PluginItem>()

        val jsonObject = runCatching {
            json.parseToJsonElement(manifestRes) as? JsonObject
        }.getOrNull()
        val inlinePluginsElement = jsonObject?.get("plugins")
        if (inlinePluginsElement is JsonArray && inlinePluginsElement.isNotEmpty()) {
            results.addAll(parseArrayManifest(inlinePluginsElement.toString(), repo.url, baseUrl, installedMap))
        }

        if (manifest != null) {
            for (pluginListUrl in manifest.pluginLists) {
                val fullUrl = resolveUrl(pluginListUrl, baseUrl)
                val pluginsRes = runCatching { app.get(fullUrl, allowRedirects = true) }.getOrNull() ?: continue
                if (!pluginsRes.isSuccessful) continue
                val text = pluginsRes.text.trim()
                val listBaseUrl = getBaseUrl(pluginsRes.url.ifBlank { fullUrl })
                if (text.startsWith("[")) {
                    results.addAll(parseArrayManifest(text, repo.url, listBaseUrl, installedMap))
                } else if (text.startsWith("{")) {
                    val nestedObj = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
                    val nestedPlugins = nestedObj?.get("plugins")
                    if (nestedPlugins is JsonArray) {
                        results.addAll(parseArrayManifest(nestedPlugins.toString(), repo.url, listBaseUrl, installedMap))
                    }
                }
            }
        }

        return results
    }

    private fun parseArrayManifest(
        jsonString: String,
        repoUrl: String,
        baseUrl: String,
        installedMap: Map<String, PluginItem>
    ): List<PluginItem> {
        val sitePlugins = runCatching {
            json.decodeFromString<List<SitePlugin>>(jsonString)
        }.getOrNull()

        if (!sitePlugins.isNullOrEmpty()) {
            return sitePlugins.map { sp ->
                val installed = findMatchingInstalledPlugin(sp.name, sp.internalName, installedMap)
                val item = sp.toPluginItem(repoUrl, baseUrl, installed != null)
                if (installed != null) mergePluginWithInstalled(installed, item) else item
            }
        }

        val pluginItems = runCatching {
            json.decodeFromString<List<PluginItem>>(jsonString)
        }.getOrNull()

        if (!pluginItems.isNullOrEmpty()) {
            return pluginItems.map { plugin ->
                val installed = findMatchingInstalledPlugin(plugin.name, plugin.internalName, installedMap)
                val resolved = resolvePlugin(plugin, repoUrl, baseUrl, installed != null)
                if (installed != null) mergePluginWithInstalled(installed, resolved) else resolved
            }
        }

        return parseArrayManifestLenient(jsonString, repoUrl, baseUrl, installedMap)
    }

    private fun findMatchingInstalledPlugin(
        name: String,
        internalName: String,
        installedMap: Map<String, PluginItem>
    ): PluginItem? {
        val key = internalName.ifBlank { name }
        val direct = installedMap[key] ?: installedMap[name]
        if (direct != null) return direct

        val normKey = key.replace(" ", "").removeSuffix("Provider")
        val normName = name.replace(" ", "").removeSuffix("Provider")
        return installedMap.values.firstOrNull { inst ->
            inst.internalName.equals(key, ignoreCase = true) ||
                inst.name.equals(name, ignoreCase = true) ||
                (normKey.isNotBlank() && inst.internalName.replace(" ", "").removeSuffix("Provider").equals(normKey, ignoreCase = true)) ||
                (normName.isNotBlank() && inst.name.replace(" ", "").removeSuffix("Provider").equals(normName, ignoreCase = true))
        }
    }

    private fun parseArrayManifestLenient(
        jsonString: String,
        repoUrl: String,
        baseUrl: String,
        installedMap: Map<String, PluginItem>
    ): List<PluginItem> {
        val jsonArray = runCatching {
            json.parseToJsonElement(jsonString) as? JsonArray
        }.getOrNull() ?: return emptyList()

        val results = mutableListOf<PluginItem>()
        for (element in jsonArray) {
            val item = parseLenientElement(element, repoUrl, baseUrl, installedMap) ?: continue
            results.add(item)
        }
        return results
    }

    private fun parseLenientElement(
        element: JsonElement,
        repoUrl: String,
        baseUrl: String,
        installedMap: Map<String, PluginItem>
    ): PluginItem? {
        val sitePlugin = runCatching {
            json.decodeFromJsonElement<SitePlugin>(element)
        }.getOrNull()

        if (sitePlugin != null && (sitePlugin.name.isNotBlank() || sitePlugin.internalName.isNotBlank())) {
            val installed = findMatchingInstalledPlugin(sitePlugin.name, sitePlugin.internalName, installedMap)
            val item = sitePlugin.toPluginItem(repoUrl, baseUrl, installed != null)
            return if (installed != null) mergePluginWithInstalled(installed, item) else item
        }

        val pluginItem = runCatching {
            json.decodeFromJsonElement<PluginItem>(element)
        }.getOrNull()

        if (pluginItem != null && (pluginItem.name.isNotBlank() || pluginItem.internalName.isNotBlank())) {
            val installed = findMatchingInstalledPlugin(pluginItem.name, pluginItem.internalName, installedMap)
            val resolved = resolvePlugin(pluginItem, repoUrl, baseUrl, installed != null)
            return if (installed != null) mergePluginWithInstalled(installed, resolved) else resolved
        }
        return null
    }

    private fun resolvePlugin(
        plugin: PluginItem,
        repoUrl: String,
        baseUrl: String,
        isInstalled: Boolean
    ): PluginItem {
        val resolvedUrl = if (plugin.url.isNotBlank()) resolveUrl(plugin.url, baseUrl) else plugin.url
        val resolvedIcon = plugin.iconUrl?.let { resolveUrl(it, baseUrl) }
        return plugin.copy(
            url = resolvedUrl,
            iconUrl = resolvedIcon,
            repositoryUrl = repoUrl,
            isInstalled = isInstalled,
            isDownloaded = isInstalled
        )
    }

    private fun resolveUrl(url: String, baseUrl: String): String {
        return resolvePluginUrl(url, baseUrl)
    }

    private suspend fun updateRepoNameIfGeneric(
        repo: PluginRepositoryItem,
        manifestName: String,
        manifestDesc: String?
    ) {
        val trimmedName = manifestName.trim()
        if (trimmedName.isBlank() || !isGenericRepoName(repo.name, repo.url) || repo.name == trimmedName) return

        runCatching {
            val currentRepos = getRepositories().map {
                if (normalizeUrlKey(it.url) == normalizeUrlKey(repo.url)) {
                    it.copy(
                        name = trimmedName,
                        description = manifestDesc?.trim()?.ifBlank { null } ?: it.description
                    )
                } else {
                    it
                }
            }
            savePreferenceString(KEY_REPOSITORIES, json.encodeToString(currentRepos))
        }
    }

    private fun isGenericRepoName(name: String, url: String): Boolean {
        if (name.isBlank()) return true
        val filename = url.substringAfterLast("/").removeSuffix(".json")
        val genericNames = listOf("Custom Repository", "Repository", "repo", "repo.json", filename)
        return genericNames.any { it.equals(name, ignoreCase = true) }
    }

    override suspend fun installPlugin(plugin: PluginItem): Result<PluginItem> {
        if (!plugin.canInstall || plugin.isDown) {
            return Result.failure(IllegalStateException("Cannot install plugin '${plugin.name}': provider is disabled or down"))
        }
        val result = pluginManager.installPlugin(plugin)
        return result.map { installedPlugin ->
            val currentInstalled = getInstalledPlugins().toMutableList()
            currentInstalled.removeAll { it.internalName == plugin.internalName }
            currentInstalled.add(installedPlugin)
            saveInstalledPlugins(currentInstalled)
            APIHolder.notifyProvidersChanged()
            installedPlugin
        }
    }

    override suspend fun uninstallPlugin(filenameOrName: String): Result<Unit> {
        val cleanName = filenameOrName
            .substringAfterLast("/")
            .substringAfterLast("\\")
            .removeSuffix(".cs3")
            .removeSuffix(".jar")

        val currentInstalled = getInstalledPlugins().toMutableList()
        val targetItem = currentInstalled.firstOrNull { matchesUninstallTarget(it, filenameOrName, cleanName) }
        val targetPathOrName = resolveTargetPathForUninstall(targetItem, filenameOrName)

        val result = pluginManager.uninstallPlugin(targetPathOrName)
        return result.map {
            currentInstalled.removeAll { matchesUninstallTarget(it, filenameOrName, cleanName, targetItem) }
            saveInstalledPlugins(currentInstalled)
            APIHolder.notifyProvidersChanged()
        }
    }

    private fun resolveTargetPathForUninstall(targetItem: PluginItem?, filenameOrName: String): String {
        return targetItem?.localFilePath
            ?: if (filenameOrName.endsWith(".cs3", ignoreCase = true) || filenameOrName.endsWith(".jar", ignoreCase = true)) {
                filenameOrName
            } else {
                targetItem?.internalName ?: filenameOrName
            }
    }

    private fun matchesUninstallTarget(
        item: PluginItem,
        filenameOrName: String,
        cleanName: String,
        targetItem: PluginItem? = null
    ): Boolean {
        if (targetItem != null && item.internalName.equals(targetItem.internalName, ignoreCase = true)) return true
        if (item.internalName.equals(filenameOrName, ignoreCase = true) || item.name.equals(filenameOrName, ignoreCase = true)) return true
        if (item.localFilePath == filenameOrName) return true
        if (item.internalName.equals(cleanName, ignoreCase = true) || item.name.equals(cleanName, ignoreCase = true)) return true
        if (cleanName.isBlank()) return false

        val normTarget = cleanName.replace(" ", "").removeSuffix("Provider")
        val normItem = item.internalName.replace(" ", "").removeSuffix("Provider")
        return normItem.equals(normTarget, ignoreCase = true)
    }
}
