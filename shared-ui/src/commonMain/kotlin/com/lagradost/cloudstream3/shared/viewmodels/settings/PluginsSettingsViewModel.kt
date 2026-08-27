package com.lagradost.cloudstream3.shared.viewmodels.settings

import androidx.compose.runtime.Immutable
import cloudstream.shared_ui.generated.resources.*
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.plugins.PluginLoader
import com.lagradost.cloudstream3.shared.mvi.BaseViewModel
import com.lagradost.cloudstream3.shared.mvi.UiState
import com.lagradost.cloudstream3.shared.persistence.repository.AppPreferenceManager
import com.lagradost.cloudstream3.shared.persistence.repository.AppPreferenceRepository
import com.lagradost.cloudstream3.utils.UiText
import com.lagradost.cloudstream3.utils.txt
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.StringResource
import kotlin.coroutines.CoroutineContext

@Serializable
@Immutable
data class PluginRepositoryItem(
    val name: String = "",
    val url: String = "",
    val isRemovable: Boolean = true,
    val pluginCount: Int = 0,
    val iconUrl: String? = null,
    val description: String? = null,
    val lastSyncTime: Long? = null
)

@Serializable
@Immutable
data class PluginItem(
    val internalName: String = "",
    val name: String = "",
    val version: Int = 1,
    val description: String? = null,
    val authors: List<String> = emptyList(),
    val iconUrl: String? = null,
    val repositoryUrl: String = "",
    val isDownloaded: Boolean = false,
    val isLoaded: Boolean = false,
    val isEnabled: Boolean = true,
    val hasUpdate: Boolean = false,
    val remoteVersion: Int? = null,
    val language: String? = null,
    val fileSize: Long? = null,
    val url: String = repositoryUrl,
    val fileHash: String? = null,
    val localFilePath: String? = null,
    val tvTypes: List<String> = emptyList(),
    val changelog: String? = null,
    val progress: Float? = null,
    val status: PluginStatus = PluginStatus.NOT_INSTALLED,
    val permissions: List<String> = emptyList(),
    val isInstalled: Boolean = isDownloaded
) {
    val id: String get() = internalName.ifBlank { name }
    val authorsImmutable: ImmutableList<String> get() = authors.toImmutableList()
    val tvTypesImmutable: ImmutableList<String> get() = tvTypes.toImmutableList()

    constructor(
        internalName: String,
        name: String,
        version: Int = 1,
        url: String = "",
        repositoryUrl: String = "",
        tvTypes: List<String> = emptyList(),
        language: String? = null,
        description: String? = null,
        authors: List<String> = emptyList(),
        iconUrl: String? = null,
        isDownloaded: Boolean = false,
        isInstalled: Boolean = isDownloaded,
        localFilePath: String? = null,
        changelog: String? = null,
        permissions: List<String> = emptyList(),
        remoteVersion: Int? = null,
        isEnabled: Boolean = true,
        status: PluginStatus = PluginStatus.NOT_INSTALLED
    ) : this(
        internalName = internalName,
        name = name,
        version = version,
        description = description,
        authors = authors,
        iconUrl = iconUrl,
        repositoryUrl = repositoryUrl,
        isDownloaded = isDownloaded || isInstalled,
        isLoaded = false,
        isEnabled = isEnabled,
        hasUpdate = (remoteVersion ?: version) > version,
        remoteVersion = remoteVersion,
        language = language,
        fileSize = null,
        url = url.ifBlank { repositoryUrl },
        fileHash = null,
        localFilePath = localFilePath,
        tvTypes = tvTypes,
        changelog = changelog,
        progress = null,
        status = status,
        permissions = permissions,
        isInstalled = isInstalled || isDownloaded
    )
}

enum class PluginStatus {
    NOT_INSTALLED,
    DOWNLOADING,
    INSTALLED,
    UPDATING,
    ERROR
}

enum class PluginFilterMode {
    ALL,
    INSTALLED,
    AVAILABLE,
    UPDATES_AVAILABLE
}

@Serializable
@Immutable
data class RepositoryManifest(
    val name: String? = null,
    val description: String? = null,
    val iconUrl: String? = null,
    val manifestVersion: Int? = null,
    val pluginCount: Int = 0,
    val pluginLists: List<String> = emptyList(),
    val plugins: List<PluginItem> = emptyList()
)

@Immutable
sealed class PluginOperationState {
    data object Idle : PluginOperationState()
    data class Downloading(val pluginName: String) : PluginOperationState()
    data class Installing(val pluginName: String) : PluginOperationState()
    data class Uninstalling(val pluginName: String) : PluginOperationState()
    data class Success(val message: String? = null, val messageRes: StringResource? = null, val formatArgs: List<Any> = emptyList()) : PluginOperationState()
    data class Error(val message: String? = null, val messageRes: StringResource? = null, val formatArgs: List<Any> = emptyList()) : PluginOperationState()
}

@Immutable
data class PluginsSettingsState(
    val repositories: ImmutableList<PluginRepositoryItem> = persistentListOf(),
    val availablePlugins: ImmutableList<PluginItem> = persistentListOf(),
    val downloadedPlugins: ImmutableList<PluginItem> = persistentListOf(),
    val selectedRepository: PluginRepositoryItem? = null,
    val selectedRepositoryPlugins: ImmutableList<PluginItem> = persistentListOf(),
    val selectedPluginForDetails: PluginItem? = null,
    val updatingPlugins: ImmutableList<String> = persistentListOf(),
    val searchQuery: String = "",
    val selectedLanguage: String? = null,
    val selectedTvType: String? = null,
    val filterMode: PluginFilterMode = PluginFilterMode.ALL,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val operationState: PluginOperationState = PluginOperationState.Idle,
    val error: UiText? = null,
    val successMessage: UiText? = null
) : UiState {
    val allPlugins: ImmutableList<PluginItem>
        get() {
            val map = linkedMapOf<String, PluginItem>()
            for (p in downloadedPlugins) {
                map[p.internalName.ifBlank { p.name }] = p
            }
            for (p in availablePlugins) {
                val key = p.internalName.ifBlank { p.name }
                val existing = map[key]
                map[key] = if (existing != null) mergePluginWithInstalled(existing, p) else p
            }
            return map.values.toImmutableList()
        }

    val currentRepoPlugins: ImmutableList<PluginItem>
        get() {
            val repoUrl = selectedRepository?.url ?: return availablePlugins
            return availablePlugins.filter { it.repositoryUrl == repoUrl }.toImmutableList()
        }

    val filteredPlugins: ImmutableList<PluginItem>
        get() {
            val baseList = selectedRepository?.let { currentRepoPlugins } ?: allPlugins
            return baseList
                .filter { matchesPlugin(it, filterMode, searchQuery, selectedLanguage, selectedTvType) }
                .toImmutableList()
        }

    val filteredAvailablePlugins: ImmutableList<PluginItem>
        get() = currentRepoPlugins
            .filter { matchesPlugin(it, filterMode, searchQuery, selectedLanguage, selectedTvType) }
            .toImmutableList()

    val availableLanguages: ImmutableList<String>
        get() = allPlugins
            .mapNotNull { it.language }
            .filter { it.isNotBlank() && it != "all" }
            .distinct()
            .sorted()
            .toImmutableList()

    val selectedRepositoryUrl: String? get() = selectedRepository?.url
    val installedPlugins: ImmutableList<PluginItem> get() = downloadedPlugins

    val hasUpdates: Boolean
        get() = allPlugins.any { it.hasUpdate }
}

private fun mergePluginWithInstalled(installed: PluginItem, available: PluginItem): PluginItem {
    val hasUpdate = available.version > installed.version
    return installed.copy(
        hasUpdate = hasUpdate,
        remoteVersion = available.version,
        description = installed.description ?: available.description,
        authors = installed.authors.ifEmpty { available.authors },
        iconUrl = installed.iconUrl ?: available.iconUrl
    )
}

private fun matchesFilterMode(plugin: PluginItem, mode: PluginFilterMode): Boolean = when (mode) {
    PluginFilterMode.ALL -> true
    PluginFilterMode.INSTALLED -> plugin.isInstalled || plugin.isDownloaded
    PluginFilterMode.AVAILABLE -> !plugin.isInstalled && !plugin.isDownloaded
    PluginFilterMode.UPDATES_AVAILABLE -> plugin.hasUpdate
}

private fun matchesSearchQuery(plugin: PluginItem, query: String): Boolean {
    if (query.isBlank()) return true
    val q = query.trim().lowercase()
    val matchesName = plugin.name.lowercase().contains(q) || plugin.internalName.lowercase().contains(q)
    val matchesDescription = plugin.description?.lowercase()?.contains(q) == true
    val matchesAuthors = plugin.authors.any { it.lowercase().contains(q) }
    return matchesName || matchesDescription || matchesAuthors
}

private fun matchesLanguage(pluginLanguage: String?, selectedLanguage: String?): Boolean {
    if (selectedLanguage == null) return true
    if (pluginLanguage == null || pluginLanguage == "all") return true
    return pluginLanguage.equals(selectedLanguage, ignoreCase = true)
}

private fun matchesPlugin(
    plugin: PluginItem,
    filterMode: PluginFilterMode,
    searchQuery: String,
    selectedLanguage: String?,
    selectedTvType: String?
): Boolean {
    if (!matchesFilterMode(plugin, filterMode)) return false
    if (!matchesSearchQuery(plugin, searchQuery)) return false
    if (!matchesLanguage(plugin.language, selectedLanguage)) return false
    if (selectedTvType != null && !plugin.tvTypes.any { it.equals(selectedTvType, ignoreCase = true) }) return false
    return true
}

interface PluginsRepository {
    suspend fun getRepositories(): List<PluginRepositoryItem>
    suspend fun getInstalledPlugins(): List<PluginItem>
    suspend fun getAvailablePlugins(repositories: List<PluginRepositoryItem>): List<PluginItem>
    suspend fun addRepository(repository: PluginRepositoryItem)
    suspend fun removeRepository(url: String)
    suspend fun installPlugin(plugin: PluginItem): Result<PluginItem>
    suspend fun uninstallPlugin(filenameOrName: String): Result<Unit>
}

class DefaultPluginsRepository(
    private val preferenceRepository: AppPreferenceRepository? = null,
    private val pluginLoader: PluginLoader? = null,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }
) : PluginsRepository {
    companion object {
        const val KEY_REPOSITORIES = "REPOSITORIES_KEY"
        const val KEY_INSTALLED_PLUGINS = "INSTALLED_PLUGINS_KEY"
        val PREBUILT_REPOSITORIES: List<PluginRepositoryItem> = emptyList()
    }

    override suspend fun getRepositories(): List<PluginRepositoryItem> {
        val savedJson = preferenceRepository?.getString(KEY_REPOSITORIES)
            ?: AppPreferenceManager.getStringSync(KEY_REPOSITORIES)
        return if (!savedJson.isNullOrBlank()) {
            try {
                json.decodeFromString<List<PluginRepositoryItem>>(savedJson)
            } catch (_: Throwable) {
                try {
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
        } else {
            emptyList()
        }
    }

    override suspend fun addRepository(repository: PluginRepositoryItem) {
        val currentRepos = getRepositories()
        val updated = (currentRepos + repository).distinctBy { it.url }
        val serialized = json.encodeToString(updated)
        preferenceRepository?.setString(KEY_REPOSITORIES, serialized)
        AppPreferenceManager.setStringSync(KEY_REPOSITORIES, serialized)
    }

    override suspend fun removeRepository(url: String) {
        val currentRepos = getRepositories()
        val updated = currentRepos.filter { it.url != url }
        val serialized = json.encodeToString(updated)
        preferenceRepository?.setString(KEY_REPOSITORIES, serialized)
        AppPreferenceManager.setStringSync(KEY_REPOSITORIES, serialized)
    }

    override suspend fun getInstalledPlugins(): List<PluginItem> {
        val savedJson = preferenceRepository?.getString(KEY_INSTALLED_PLUGINS)
            ?: AppPreferenceManager.getStringSync(KEY_INSTALLED_PLUGINS)
        return if (!savedJson.isNullOrBlank()) {
            try {
                json.decodeFromString<List<PluginItem>>(savedJson)
            } catch (_: Throwable) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    override suspend fun getAvailablePlugins(repositories: List<PluginRepositoryItem>): List<PluginItem> {
        val installed = getInstalledPlugins()
        val installedMap = installed.associateBy { it.internalName }
        val allPlugins = mutableListOf<PluginItem>()

        for (repo in repositories) {
            try {
                val repoBaseUrl = repo.url.substringBeforeLast("/")
                val manifestRes = app.get(repo.url).text.trim()

                if (manifestRes.startsWith("{")) {
                    val manifest = json.decodeFromString<RepositoryManifest>(manifestRes)
                    val manifestName = manifest.name?.trim()?.ifBlank { null }
                    if (!manifestName.isNullOrBlank()) {
                        val isGeneric = repo.name.isBlank() ||
                                repo.name.equals("Custom Repository", ignoreCase = true) ||
                                repo.name.equals("Repository", ignoreCase = true) ||
                                repo.name.equals("repo", ignoreCase = true) ||
                                repo.name.equals("repo.json", ignoreCase = true) ||
                                repo.name.equals(repo.url.substringAfterLast("/").removeSuffix(".json"), ignoreCase = true)
                        if (isGeneric && repo.name != manifestName) {
                            try {
                                val currentRepos = getRepositories().map {
                                    if (it.url == repo.url) it.copy(
                                        name = manifestName,
                                        description = manifest.description?.trim()?.ifBlank { null } ?: it.description
                                    ) else it
                                }
                                val serialized = json.encodeToString(currentRepos)
                                preferenceRepository?.setString(KEY_REPOSITORIES, serialized)
                                AppPreferenceManager.setStringSync(KEY_REPOSITORIES, serialized)
                            } catch (_: Throwable) {}
                        }
                    }

                    for (pluginListUrl in manifest.pluginLists) {
                        try {
                            val fullUrl = if (pluginListUrl.startsWith("http://", ignoreCase = true) || pluginListUrl.startsWith("https://", ignoreCase = true)) {
                                pluginListUrl
                            } else {
                                "$repoBaseUrl/${pluginListUrl.removePrefix("/")}"
                            }

                            val pluginsRes = app.get(fullUrl).text.trim()
                            if (pluginsRes.startsWith("[")) {
                                val plugins = json.decodeFromString<List<PluginItem>>(pluginsRes)
                                allPlugins.addAll(plugins.map { plugin ->
                                    val resolvedUrl = if (plugin.url.isNotBlank()) {
                                        if (plugin.url.startsWith("http://", ignoreCase = true) || plugin.url.startsWith("https://", ignoreCase = true)) {
                                            plugin.url
                                        } else {
                                            "$repoBaseUrl/${plugin.url.removePrefix("/")}"
                                        }
                                    } else {
                                        plugin.url
                                    }
                                    val resolvedIcon = plugin.iconUrl?.let { icon ->
                                        if (icon.startsWith("http://", ignoreCase = true) || icon.startsWith("https://", ignoreCase = true)) {
                                            icon
                                        } else {
                                            "$repoBaseUrl/${icon.removePrefix("/")}"
                                        }
                                    }

                                    plugin.copy(
                                        url = resolvedUrl,
                                        iconUrl = resolvedIcon,
                                        repositoryUrl = repo.url,
                                        isInstalled = installedMap.containsKey(plugin.internalName)
                                    )
                                })
                            }
                        } catch (_: Throwable) {}
                    }
                } else if (manifestRes.startsWith("[")) {
                    val plugins = json.decodeFromString<List<PluginItem>>(manifestRes)
                    allPlugins.addAll(plugins.map { plugin ->
                        val resolvedUrl = if (plugin.url.isNotBlank()) {
                            if (plugin.url.startsWith("http://", ignoreCase = true) || plugin.url.startsWith("https://", ignoreCase = true)) {
                                plugin.url
                            } else {
                                "$repoBaseUrl/${plugin.url.removePrefix("/")}"
                            }
                        } else {
                            plugin.url
                        }
                        val resolvedIcon = plugin.iconUrl?.let { icon ->
                            if (icon.startsWith("http://", ignoreCase = true) || icon.startsWith("https://", ignoreCase = true)) {
                                icon
                            } else {
                                "$repoBaseUrl/${icon.removePrefix("/")}"
                            }
                        }

                        plugin.copy(
                            url = resolvedUrl,
                            iconUrl = resolvedIcon,
                            repositoryUrl = repo.url,
                            isInstalled = installedMap.containsKey(plugin.internalName)
                        )
                    })
                }
            } catch (_: Throwable) {}
        }
        return allPlugins.distinctBy { it.internalName }
    }

    override suspend fun installPlugin(plugin: PluginItem): Result<PluginItem> {
        return try {
            val pluginsDir = pluginLoader?.pluginsDirectory?.let { java.io.File(it) }
                ?: java.io.File(System.getProperty("user.home") ?: ".", ".cloudstream/plugins")
            if (!pluginsDir.exists()) {
                pluginsDir.mkdirs()
            }
            val ext = if (plugin.url.endsWith(".jar", ignoreCase = true)) "jar" else "cs3"
            val targetFile = plugin.localFilePath?.let { java.io.File(it) }?.takeIf { it.exists() }
                ?: java.io.File(pluginsDir, "${plugin.internalName}.$ext")
            var localPath = targetFile.absolutePath

            if ((!targetFile.exists() || plugin.url.isNotBlank()) && plugin.url.startsWith("http", ignoreCase = true)) {
                try {
                    val response = app.get(plugin.url)
                    val bytes = response.body.bytes()
                    targetFile.parentFile?.mkdirs()
                    targetFile.writeBytes(bytes)
                    localPath = targetFile.absolutePath
                } catch (e: Throwable) {
                    if (!targetFile.exists()) {
                        return Result.failure(e)
                    }
                }
            }

            if (plugin.isEnabled) {
                if (targetFile.exists() && pluginLoader != null) {
                    pluginLoader.unloadPlugin(targetFile.absolutePath)
                    pluginLoader.unloadPlugin(plugin.internalName)
                    pluginLoader.loadPlugin(targetFile.absolutePath)
                }
            } else {
                if (pluginLoader != null) {
                    pluginLoader.unloadPlugin(targetFile.absolutePath)
                    pluginLoader.unloadPlugin(plugin.internalName)
                    pluginLoader.unloadPlugin(plugin.name)
                }
            }

            val currentInstalled = getInstalledPlugins().toMutableList()
            val installedPlugin = plugin.copy(
                isInstalled = true,
                isDownloaded = true,
                localFilePath = localPath
            )
            currentInstalled.removeAll { it.internalName == plugin.internalName }
            currentInstalled.add(installedPlugin)
            val serialized = json.encodeToString(currentInstalled)
            preferenceRepository?.setString(KEY_INSTALLED_PLUGINS, serialized)
            AppPreferenceManager.setStringSync(KEY_INSTALLED_PLUGINS, serialized)
            APIHolder.notifyProvidersChanged()
            Result.success(installedPlugin)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    override suspend fun uninstallPlugin(filenameOrName: String): Result<Unit> {
        return try {
            val pluginsDir = pluginLoader?.pluginsDirectory?.let { java.io.File(it) }
                ?: java.io.File(System.getProperty("user.home") ?: ".", ".cloudstream/plugins")
            val targetFile = java.io.File(pluginsDir, "$filenameOrName.cs3").takeIf { it.exists() }
                ?: java.io.File(pluginsDir, "$filenameOrName.jar").takeIf { it.exists() }
                ?: java.io.File(filenameOrName).takeIf { it.exists() }

            if (pluginLoader != null) {
                targetFile?.let { pluginLoader.unloadPlugin(it.absolutePath) }
                pluginLoader.unloadPlugin(filenameOrName)
            }
            targetFile?.delete()

            val currentInstalled = getInstalledPlugins().toMutableList()
            currentInstalled.removeAll { it.internalName == filenameOrName || it.name == filenameOrName || it.localFilePath == filenameOrName }
            val serialized = json.encodeToString(currentInstalled)
            preferenceRepository?.setString(KEY_INSTALLED_PLUGINS, serialized)
            AppPreferenceManager.setStringSync(KEY_INSTALLED_PLUGINS, serialized)
            APIHolder.notifyProvidersChanged()
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }
}

class PluginsSettingsViewModel(
    private val pluginsRepository: PluginsRepository = DefaultPluginsRepository(),
    coroutineScope: CoroutineScope? = null
) : BaseViewModel(coroutineScope) {

    private val _state = MutableStateFlow(PluginsSettingsState(isLoading = true))
    val state: StateFlow<PluginsSettingsState> = _state.asStateFlow()
    val currentState: PluginsSettingsState
        get() = _state.value

    protected fun updateState(reducer: PluginsSettingsState.() -> PluginsSettingsState) {
        _state.update { it.reducer() }
    }

    constructor(
        preferenceRepository: AppPreferenceRepository? = null,
        pluginLoader: PluginLoader? = null,
        onPluginLoaded: (() -> Unit)? = null
    ) : this(
        pluginsRepository = DefaultPluginsRepository(preferenceRepository, pluginLoader)
    )

    constructor(
        pluginsRepository: PluginsRepository,
        coroutineContext: CoroutineContext
    ) : this(pluginsRepository, CoroutineScope(coroutineContext))

    companion object {
        fun isValidRepoUrl(url: String): Boolean {
            val trimmed = url.trim()
            if (trimmed.isBlank() || trimmed.contains(" ")) return false
            if (trimmed.startsWith("!")) return trimmed.length > 1
            if (trimmed.startsWith("ftp://")) return false
            if (trimmed.startsWith("cloudstreamrepo://") || trimmed.startsWith("cs3-repo://")) return true
            if (trimmed.startsWith("https://") || trimmed.startsWith("http://")) return true
            if (trimmed.startsWith("raw.githubusercontent.com") || trimmed.startsWith("github.com")) return true
            return false
        }

        fun normalizeRepoUrl(url: String): String {
            val trimmed = url.trim()
            if (trimmed.startsWith("!")) {
                return "https://py.md/${trimmed.substring(1)}"
            }
            if (trimmed.startsWith("cloudstreamrepo://")) {
                return "https://" + trimmed.removePrefix("cloudstreamrepo://")
            }
            if (trimmed.startsWith("cs3-repo://")) {
                return "https://" + trimmed.removePrefix("cs3-repo://")
            }
            if (trimmed.startsWith("https://cs.repo/?")) {
                return trimmed.removePrefix("https://cs.repo/?")
            }
            var result = if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                "https://$trimmed"
            } else {
                trimmed
            }
            if (result.startsWith("https://github.com/")) {
                var raw = result.replace("https://github.com/", "https://raw.githubusercontent.com/")
                if (raw.contains("/blob/")) {
                    raw = raw.replace("/blob/", "/")
                }
                val clean = raw.removeSuffix("/").removeSuffix("/builds")
                return if (clean.endsWith(".json")) clean else "$clean/builds/repo.json"
            }
            if (result.startsWith("https://raw.githubusercontent.com/") && !result.endsWith(".json")) {
                val clean = result.removeSuffix("/")
                return if (clean.endsWith("/builds")) "$clean/repo.json" else "$clean/builds/repo.json"
            }
            return result
        }

        fun formatSyncTime(timestamp: Long?): String {
            if (timestamp == null || timestamp <= 0L) return "Never"
            val diff = APIHolder.unixTimeMS - timestamp
            val minutes = diff / 60_000L
            val hours = minutes / 60L
            val days = hours / 24L
            return when {
                diff < 60_000L -> "Just now"
                minutes < 60L -> "$minutes min ago"
                hours < 24L -> "$hours hr ago"
                days == 1L -> "Yesterday"
                else -> "$days days ago"
            }
        }
    }

    init {
        loadData()
    }

    fun setSearchQuery(query: String) {
        updateState { copy(searchQuery = query) }
    }

    fun search(query: String) = setSearchQuery(query)

    fun setLanguageFilter(lang: String?) {
        updateState { copy(selectedLanguage = lang) }
    }

    fun setFilterMode(mode: PluginFilterMode) {
        updateState { copy(filterMode = mode) }
    }

    fun filterByTvType(tvType: String?) {
        updateState { copy(selectedTvType = tvType) }
    }

    fun selectPluginForDetails(plugin: PluginItem?) {
        updateState { copy(selectedPluginForDetails = plugin) }
    }

    fun clearSearch() {
        updateState { copy(searchQuery = "", selectedLanguage = null, selectedTvType = null) }
    }

    fun dismissError() {
        updateState { copy(error = null) }
    }

    fun clearError() = dismissError()

    fun dismissSuccessMessage() {
        updateState { copy(successMessage = null) }
    }

    fun loadData() {
        launchSafeJob(
            key = "load_plugins_data",
            onError = { t ->
                updateState {
                    copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = t.message?.let { txt(it) } ?: txt("Failed to load plugins")
                    )
                }
            }
        ) {
            updateState { copy(isLoading = true, error = null) }
            val repos = pluginsRepository.getRepositories().map { repo ->
                if (repo.lastSyncTime == null) repo.copy(lastSyncTime = APIHolder.unixTimeMS) else repo
            }
            val installed = pluginsRepository.getInstalledPlugins()
            val available = pluginsRepository.getAvailablePlugins(repos)

            updateState {
                copy(
                    repositories = repos.toImmutableList(),
                    downloadedPlugins = installed.toImmutableList(),
                    availablePlugins = available.toImmutableList(),
                    isLoading = false,
                    isRefreshing = false,
                    error = null
                )
            }
        }
    }

    fun refreshData() {
        launchSafeJob(
            key = "refresh_plugins_data",
            onError = { t ->
                updateState {
                    copy(
                        isRefreshing = false,
                        error = t.message?.let { txt(it) } ?: txt("Failed to refresh plugins")
                    )
                }
            }
        ) {
            updateState { copy(isRefreshing = true, error = null) }
            loadData()
        }
    }

    fun syncRepositories() = refreshData()

    fun addRepository(url: String, name: String = "") {
        val trimmedUrl = url.trim()
        if (!isValidRepoUrl(trimmedUrl)) {
            updateState { copy(error = txt("Invalid repository URL")) }
            return
        }

        val normalizedUrl = normalizeRepoUrl(trimmedUrl)
        launchSafeJob(
            key = "add_repository",
            onError = { t ->
                updateState {
                    copy(error = t.message?.let { txt(it) } ?: txt("Failed to add repository"))
                }
            }
        ) {
            val repoName = name.ifBlank { "Repository" }
            val repoItem = PluginRepositoryItem(
                name = repoName,
                url = normalizedUrl,
                isRemovable = true,
                lastSyncTime = APIHolder.unixTimeMS
            )
            pluginsRepository.addRepository(repoItem)
            loadData()
        }
    }

    fun removeRepository(url: String) {
        launchSafeJob(
            key = "remove_repository",
            onError = { t ->
                updateState {
                    copy(error = t.message?.let { txt(it) } ?: txt("Failed to remove repository"))
                }
            }
        ) {
            pluginsRepository.removeRepository(url)
            loadData()
        }
    }

    fun removeRepository(repo: PluginRepositoryItem) = removeRepository(repo.url)

    fun selectRepository(repo: PluginRepositoryItem?) {
        updateState {
            val repoPlugins = if (repo != null) {
                availablePlugins.filter { it.repositoryUrl == repo.url }.toImmutableList()
            } else {
                persistentListOf()
            }
            copy(
                selectedRepository = repo,
                selectedRepositoryPlugins = repoPlugins
            )
        }
    }

    fun filterByRepository(url: String?) {
        val repo = currentState.repositories.firstOrNull { it.url == url }
        selectRepository(repo)
    }

    fun installPlugin(plugin: PluginItem) {
        launchSafeJob(
            key = "install_plugin_${plugin.internalName}",
            onError = { t ->
                updateState {
                    copy(
                        operationState = PluginOperationState.Error(
                            message = t.message,
                            messageRes = Res.string.plugin_install_failed,
                            formatArgs = listOf(plugin.name)
                        )
                    )
                }
            }
        ) {
            updateState { copy(operationState = PluginOperationState.Installing(plugin.name)) }
            val result = pluginsRepository.installPlugin(plugin)
            result.fold(
                onSuccess = { installed ->
                    val updatedAvailable = currentState.availablePlugins.map {
                        if (it.internalName == plugin.internalName) it.copy(isInstalled = true, isDownloaded = true) else it
                    }.toImmutableList()
                    val updatedInstalled = (currentState.downloadedPlugins.filter { it.internalName != plugin.internalName } + installed).toImmutableList()

                    updateState {
                        copy(
                            availablePlugins = updatedAvailable,
                            downloadedPlugins = updatedInstalled,
                            operationState = PluginOperationState.Success(
                                messageRes = Res.string.plugin_installed_success,
                                formatArgs = listOf(plugin.name)
                            )
                        )
                    }
                },
                onFailure = { t ->
                    updateState {
                        copy(
                            operationState = PluginOperationState.Error(
                                message = t.message,
                                messageRes = Res.string.plugin_install_failed,
                                formatArgs = listOf(plugin.name)
                            )
                        )
                    }
                }
            )
        }
    }

    fun downloadPlugin(plugin: PluginItem) = installPlugin(plugin)

    fun uninstallPlugin(filenameOrName: String) {
        launchSafeJob(
            key = "uninstall_plugin_$filenameOrName",
            onError = { t ->
                updateState {
                    copy(
                        operationState = PluginOperationState.Error(
                            message = t.message,
                            messageRes = Res.string.plugin_uninstall_failed,
                            formatArgs = listOf(filenameOrName)
                        )
                    )
                }
            }
        ) {
            updateState { copy(operationState = PluginOperationState.Uninstalling(filenameOrName)) }
            val result = pluginsRepository.uninstallPlugin(filenameOrName)
            result.fold(
                onSuccess = {
                    val updatedAvailable = currentState.availablePlugins.map {
                        if (it.internalName == filenameOrName || it.name == filenameOrName) it.copy(isInstalled = false, isDownloaded = false) else it
                    }.toImmutableList()
                    val updatedInstalled = currentState.downloadedPlugins.filter {
                        it.internalName != filenameOrName && it.name != filenameOrName
                    }.toImmutableList()

                    updateState {
                        copy(
                            availablePlugins = updatedAvailable,
                            downloadedPlugins = updatedInstalled,
                            operationState = PluginOperationState.Success(
                                messageRes = Res.string.plugin_uninstalled_success,
                                formatArgs = listOf(filenameOrName)
                            )
                        )
                    }
                },
                onFailure = { t ->
                    updateState {
                        copy(
                            operationState = PluginOperationState.Error(
                                message = t.message,
                                messageRes = Res.string.plugin_uninstall_failed,
                                formatArgs = listOf(filenameOrName)
                            )
                        )
                    }
                }
            )
        }
    }

    fun uninstallPlugin(plugin: PluginItem) = uninstallPlugin(plugin.internalName.ifBlank { plugin.name })

    fun updatePlugin(plugin: PluginItem) = installPlugin(plugin)

    fun updateAllPlugins() {
        val toUpdate = currentState.allPlugins.filter { it.hasUpdate }
        if (toUpdate.isEmpty()) return
        toUpdate.forEach { installPlugin(it) }
    }

    fun installAllPlugins(repoUrl: String) {
        val targets = currentState.availablePlugins.filter { it.repositoryUrl == repoUrl && !it.isInstalled }
        if (targets.isEmpty()) {
            updateState { copy(operationState = PluginOperationState.Idle) }
            return
        }

        launchSafeJob(
            key = "install_all_$repoUrl",
            onError = { t ->
                updateState {
                    copy(
                        operationState = PluginOperationState.Error(
                            message = t.message,
                            messageRes = Res.string.batch_operation_failed,
                            formatArgs = listOf(targets.size)
                        )
                    )
                }
            }
        ) {
            var successCount = 0
            var failCount = 0
            for (plugin in targets) {
                val res = pluginsRepository.installPlugin(plugin)
                if (res.isSuccess) {
                    successCount++
                } else {
                    failCount++
                }
            }

            val installed = pluginsRepository.getInstalledPlugins()
            val available = pluginsRepository.getAvailablePlugins(currentState.repositories)

            val opState = if (failCount == 0) {
                PluginOperationState.Success(
                    messageRes = Res.string.batch_install_success,
                    formatArgs = listOf(successCount)
                )
            } else {
                PluginOperationState.Error(
                    messageRes = Res.string.batch_operation_failed,
                    formatArgs = listOf(failCount)
                )
            }

            updateState {
                copy(
                    availablePlugins = available.toImmutableList(),
                    downloadedPlugins = installed.toImmutableList(),
                    operationState = opState
                )
            }
        }
    }

    fun uninstallAllPlugins(repoUrl: String) {
        val targets = currentState.downloadedPlugins.filter { it.repositoryUrl == repoUrl }
        if (targets.isEmpty()) {
            updateState { copy(operationState = PluginOperationState.Idle) }
            return
        }

        launchSafeJob(
            key = "uninstall_all_$repoUrl",
            onError = { t ->
                updateState {
                    copy(
                        operationState = PluginOperationState.Error(
                            message = t.message,
                            messageRes = Res.string.batch_operation_failed,
                            formatArgs = listOf(targets.size)
                        )
                    )
                }
            }
        ) {
            var successCount = 0
            var failCount = 0
            for (plugin in targets) {
                val res = pluginsRepository.uninstallPlugin(plugin.internalName)
                if (res.isSuccess) {
                    successCount++
                } else {
                    failCount++
                }
            }

            val installed = pluginsRepository.getInstalledPlugins()
            val available = pluginsRepository.getAvailablePlugins(currentState.repositories)

            val opState = if (failCount == 0) {
                PluginOperationState.Success(
                    messageRes = Res.string.batch_uninstall_success,
                    formatArgs = listOf(successCount)
                )
            } else {
                PluginOperationState.Error(
                    messageRes = Res.string.batch_operation_failed,
                    formatArgs = listOf(failCount)
                )
            }

            updateState {
                copy(
                    availablePlugins = available.toImmutableList(),
                    downloadedPlugins = installed.toImmutableList(),
                    operationState = opState
                )
            }
        }
    }

    fun togglePlugin(plugin: PluginItem) {
        val updated = plugin.copy(isEnabled = !plugin.isEnabled)
        installPlugin(updated)
    }
}

