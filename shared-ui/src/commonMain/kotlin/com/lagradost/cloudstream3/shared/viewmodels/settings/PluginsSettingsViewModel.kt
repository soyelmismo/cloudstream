package com.lagradost.cloudstream3.shared.viewmodels.settings

import com.lagradost.cloudstream3.plugins.PluginLoader
import com.lagradost.cloudstream3.shared.mvi.MviViewModel
import com.lagradost.cloudstream3.shared.mvi.UiEvent
import com.lagradost.cloudstream3.shared.mvi.UiState
import com.lagradost.cloudstream3.shared.persistence.repository.AppPreferenceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.utils.UiText
import com.lagradost.cloudstream3.utils.txt
import kotlin.coroutines.CoroutineContext
import cloudstream.shared_ui.generated.resources.*
import org.jetbrains.compose.resources.StringResource

/**
 * Represents a plugin repository configuration.
 */
@Serializable
data class PluginRepositoryItem(
    val name: String,
    val url: String,
    val iconUrl: String? = null,
    val description: String? = null,
    val isRemovable: Boolean = true,
    val pluginCount: Int = 0,
    val lastSyncTime: Long? = null
)

/**
 * Cross-platform data model for a CloudStream plugin (installed or available for download).
 */
@Serializable
data class PluginItem(
    val internalName: String,
    val name: String,
    val version: Int = 1,
    val url: String = "",
    val repositoryUrl: String = "",
    val authors: List<String> = emptyList(),
    val description: String? = null,
    val tvTypes: List<String> = emptyList(),
    val language: String? = null,
    val iconUrl: String? = null,
    val fileSize: Long? = null,
    val fileHash: String? = null,
    val status: Int = 1, // 0: Down, 1: OK, 2: Slow, 3: Beta
    val isInstalled: Boolean = false,
    val isEnabled: Boolean = true,
    val localFilePath: String? = null,
    val changelog: String? = null,
    val permissions: List<String> = emptyList(),
    val remoteVersion: Int? = null
)

@Serializable
data class RepositoryManifest(
    val name: String? = null,
    val description: String? = null,
    val iconUrl: String? = null,
    val manifestVersion: Int? = null,
    val pluginLists: List<String> = emptyList()
)

/**
 * Represents the current status of plugin download/installation operations.
 */
sealed class PluginOperationState {
    data object Idle : PluginOperationState()
    data class Downloading(val pluginName: String, val progress: Float = 0f) : PluginOperationState()
    data class Installing(val pluginName: String) : PluginOperationState()
    data class Uninstalling(val pluginName: String) : PluginOperationState()
    data class Success(
        val messageRes: StringResource? = null,
        val formatArgs: List<Any> = emptyList(),
        val message: String? = null
    ) : PluginOperationState() {
        constructor(messageRes: StringResource, vararg args: Any) : this(messageRes, args.toList(), null)
        constructor(message: String) : this(null, emptyList(), message)
    }
    data class Error(
        val messageRes: StringResource? = null,
        val formatArgs: List<Any> = emptyList(),
        val message: String? = null
    ) : PluginOperationState() {
        constructor(messageRes: StringResource, vararg args: Any) : this(messageRes, args.toList(), null)
        constructor(message: String) : this(null, emptyList(), message)
    }
}

/**
 * Immutable UI State for the Plugins and Extensions settings screen.
 */
@Serializable
data class PluginsSettingsState(
    val installedPlugins: List<PluginItem> = emptyList(),
    val repositories: List<PluginRepositoryItem> = emptyList(),
    val availablePlugins: List<PluginItem> = emptyList(),
    @Transient
    val operationState: PluginOperationState = PluginOperationState.Idle,
    @Transient
    val error: UiText? = null,
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val selectedLanguage: String? = null,
    val selectedTvType: String? = null,
    val selectedRepositoryUrl: String? = null,
    val selectedPluginForDetails: PluginItem? = null
) : UiState {
    /**
     * Returns the currently selected repository item if selectedRepositoryUrl is set.
     */
    val selectedRepository: PluginRepositoryItem?
        get() = repositories.firstOrNull { it.url == selectedRepositoryUrl }

    /**
     * Returns plugins belonging to the selected repository (or all available plugins if no repo is selected),
     * filtered by current search query, language, and TV type.
     */
    val currentRepoPlugins: List<PluginItem>
        get() = filterList(availablePlugins)

    /**
     * Returns available plugins matching current search query, language, and TV type filter.
     */
    val filteredAvailablePlugins: List<PluginItem>
        get() = filterList(availablePlugins)

    /**
     * Returns installed plugins matching current search query, language, and TV type filter.
     */
    val filteredInstalledPlugins: List<PluginItem>
        get() = filterList(installedPlugins)

    private fun filterList(list: List<PluginItem>): List<PluginItem> {
        return list.filter { plugin ->
            val matchesQuery = searchQuery.isBlank() ||
                    plugin.name.contains(searchQuery, ignoreCase = true) ||
                    (plugin.description?.contains(searchQuery, ignoreCase = true) == true) ||
                    plugin.internalName.contains(searchQuery, ignoreCase = true)

            val matchesLang = selectedLanguage == null ||
                    (selectedLanguage.equals("none", ignoreCase = true) && plugin.language.isNullOrBlank()) ||
                    plugin.language.equals(selectedLanguage, ignoreCase = true)

            val matchesTv = selectedTvType == null ||
                    plugin.tvTypes.any { it.equals(selectedTvType, ignoreCase = true) }

            val matchesRepo = selectedRepositoryUrl == null ||
                    plugin.repositoryUrl == selectedRepositoryUrl

            matchesQuery && matchesLang && matchesTv && matchesRepo
        }
    }
}

/**
 * User actions and events for Plugins Settings.
 */
sealed class PluginsSettingsEvent : UiEvent {
    data object LoadPlugins : PluginsSettingsEvent()
    data object SyncRepositories : PluginsSettingsEvent()
    data class AddRepository(val url: String, val name: String? = null) : PluginsSettingsEvent()
    data class RemoveRepository(val url: String) : PluginsSettingsEvent()
    data class InstallPlugin(val plugin: PluginItem) : PluginsSettingsEvent()
    data class InstallAllPlugins(val repositoryUrl: String) : PluginsSettingsEvent()
    data class UninstallPlugin(val filenameOrName: String) : PluginsSettingsEvent()
    data class UninstallAllPlugins(val repositoryUrl: String) : PluginsSettingsEvent()
    data class SelectPluginForDetails(val plugin: PluginItem?) : PluginsSettingsEvent()
    data object Reload : PluginsSettingsEvent()
    data class Search(val query: String) : PluginsSettingsEvent()
    data class FilterByLanguage(val language: String?) : PluginsSettingsEvent()
    data class FilterByTvType(val tvType: String?) : PluginsSettingsEvent()
    data class FilterByRepository(val repoUrl: String?) : PluginsSettingsEvent()
    data object ClearError : PluginsSettingsEvent()
}

/**
 * Abstraction for plugin management, repository synchronization, and lifecycle.
 */
interface PluginsRepository {
    suspend fun getRepositories(): List<PluginRepositoryItem>
    suspend fun addRepository(repository: PluginRepositoryItem)
    suspend fun removeRepository(url: String)
    suspend fun getInstalledPlugins(): List<PluginItem>
    suspend fun getAvailablePlugins(repositories: List<PluginRepositoryItem>): List<PluginItem>
    suspend fun installPlugin(plugin: PluginItem): Result<PluginItem>
    suspend fun uninstallPlugin(filenameOrName: String): Result<Unit>
}

/**
 * Default implementation of PluginsRepository backed by AppPreferenceRepository and in-memory cache.
 */
class DefaultPluginsRepository(
    private val preferenceRepository: AppPreferenceRepository,
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
        val savedJson = preferenceRepository.getString(KEY_REPOSITORIES)
        return if (!savedJson.isNullOrBlank()) {
            try {
                json.decodeFromString<List<PluginRepositoryItem>>(savedJson)
            } catch (_: Throwable) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    override suspend fun addRepository(repository: PluginRepositoryItem) {
        val currentRepos = getRepositories()
        val updated = (currentRepos + repository).distinctBy { it.url }
        preferenceRepository.setString(KEY_REPOSITORIES, json.encodeToString(updated))
    }

    override suspend fun removeRepository(url: String) {
        val currentRepos = getRepositories()
        val updated = currentRepos.filter { it.url != url }
        preferenceRepository.setString(KEY_REPOSITORIES, json.encodeToString(updated))
    }

    override suspend fun getInstalledPlugins(): List<PluginItem> {
        val savedJson = preferenceRepository.getString(KEY_INSTALLED_PLUGINS)
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

                    // If manifest.name is present and repo name is default/generic, update/preserve the repository name
                    val manifestName = manifest.name?.trim()?.ifBlank { null }
                    if (!manifestName.isNullOrBlank()) {
                        val isGeneric = repo.name.isBlank() ||
                                repo.name.equals("Custom Repository", ignoreCase = true) ||
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
                                preferenceRepository.setString(KEY_REPOSITORIES, json.encodeToString(currentRepos))
                            } catch (_: Throwable) {}
                        }
                    }

                    // Fetch each plugin list
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
                        } catch (e: Exception) {
                            // Ignored
                        }
                    }
                } else if (manifestRes.startsWith("[")) {
                    // Direct plugin list JSON array
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
            } catch (e: Exception) {
                // Ignored
            }
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

            // If file does not exist or plugin.url is provided and needs fresh download
            if ((!targetFile.exists() || plugin.url.isNotBlank()) && plugin.url.startsWith("http", ignoreCase = true)) {
                try {
                    val response = app.get(plugin.url)
                    val bytes = response.body.bytes()
                    targetFile.parentFile?.mkdirs()
                    targetFile.writeBytes(bytes)
                    localPath = targetFile.absolutePath
                } catch (e: Throwable) {
                    if (!targetFile.exists()) {
                        System.err.println("PluginsSettingsViewModel: Error downloading plugin from ${plugin.url}: ${e.message}")
                        return Result.failure(e)
                    }
                }
            }

            if (plugin.isEnabled) {
                if (targetFile.exists() && pluginLoader != null) {
                    pluginLoader.unloadPlugin(targetFile.absolutePath)
                    pluginLoader.unloadPlugin(plugin.internalName)
                    val loaded = pluginLoader.loadPlugin(targetFile.absolutePath)
                    println("PluginsSettingsViewModel: Loaded plugin $loaded from ${targetFile.absolutePath}")
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
                localFilePath = localPath
            )
            currentInstalled.removeAll { it.internalName == plugin.internalName }
            currentInstalled.add(installedPlugin)
            preferenceRepository.setString(KEY_INSTALLED_PLUGINS, json.encodeToString(currentInstalled))
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
            val targetFile = java.io.File(pluginsDir, "$filenameOrName.cs3")
            if (targetFile.exists()) {
                targetFile.delete()
            }
            val jarFile = java.io.File(pluginsDir, "$filenameOrName.jar")
            if (jarFile.exists()) {
                jarFile.delete()
            }
            val cacheJar = java.io.File(java.io.File(pluginsDir, "cache"), "$filenameOrName.jar")
            if (cacheJar.exists()) {
                cacheJar.delete()
            }

            if (pluginLoader != null) {
                val target = getInstalledPlugins().firstOrNull {
                    it.internalName == filenameOrName || it.name == filenameOrName || it.localFilePath == filenameOrName
                }
                if (target?.localFilePath != null) {
                    pluginLoader.unloadPlugin(target.localFilePath)
                }
                if (target?.internalName != null) {
                    pluginLoader.unloadPlugin(target.internalName)
                }
                if (target?.name != null) {
                    pluginLoader.unloadPlugin(target.name)
                }
                pluginLoader.unloadPlugin(filenameOrName)
            }

            val currentInstalled = getInstalledPlugins().toMutableList()
            currentInstalled.removeAll {
                it.internalName == filenameOrName ||
                        it.localFilePath == filenameOrName ||
                        it.name.equals(filenameOrName, ignoreCase = true)
            }
            preferenceRepository.setString(KEY_INSTALLED_PLUGINS, json.encodeToString(currentInstalled))
            APIHolder.notifyProvidersChanged()
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }
}

/**
 * Cross-platform MVI ViewModel for managing plugins, extensions, and repositories.
 */
class PluginsSettingsViewModel(
    private val pluginsRepository: PluginsRepository,
    private val onPluginLoaded: (() -> Unit)? = null,
    coroutineScope: CoroutineScope? = null
) : MviViewModel<PluginsSettingsState, PluginsSettingsEvent>(
    initialState = PluginsSettingsState(isLoading = true),
    coroutineScope = coroutineScope
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    /**
     * Convenience secondary constructor when using AppPreferenceRepository directly.
     */
    constructor(
        preferenceRepository: AppPreferenceRepository,
        pluginLoader: PluginLoader? = null,
        onPluginLoaded: (() -> Unit)? = null,
        coroutineScope: CoroutineScope? = null
    ) : this(DefaultPluginsRepository(preferenceRepository, pluginLoader), onPluginLoaded, coroutineScope)

    constructor(
        preferenceRepository: AppPreferenceRepository,
        coroutineContext: CoroutineContext
    ) : this(DefaultPluginsRepository(preferenceRepository), null, CoroutineScope(coroutineContext))

    companion object {
        /**
         * Formats a unix millisecond timestamp to a human-readable relative time string.
         */
        fun formatSyncTime(timestamp: Long?): String {
            if (timestamp == null || timestamp <= 0L) return "Never"
            val now = APIHolder.unixTimeMS
            val diffMs = now - timestamp
            if (diffMs < 0L) return "Just now"
            val diffSec = diffMs / 1000L
            val diffMin = diffSec / 60L
            val diffHours = diffMin / 60L
            val diffDays = diffHours / 24L

            return if (diffSec < 60L) {
                "Just now"
            } else if (diffMin < 60L) {
                "$diffMin min ago"
            } else if (diffHours < 24L) {
                "$diffHours hr ago"
            } else if (diffDays == 1L) {
                "Yesterday"
            } else {
                "$diffDays days ago"
            }
        }

        /**
         * Validates whether an input repository URL is well-formed.
         */
        fun isValidRepoUrl(url: String): Boolean {
            val trimmed = url.trim()
            if (trimmed.isBlank()) return false
            val normalized = normalizeRepoUrl(trimmed)
            return (normalized.startsWith("https://", ignoreCase = true) || normalized.startsWith("http://", ignoreCase = true)) &&
                    normalized.length > 8 &&
                    !normalized.contains(" ")
        }

        /**
         * Normalizes shortened, raw github, or protocol-prefixed repository URLs.
         */
        fun normalizeRepoUrl(url: String): String {
            var trimmed = url.trim()
            if (trimmed.isBlank()) return trimmed

            // 1. Custom and shortened protocols
            if (trimmed.startsWith("cloudstreamrepo://", ignoreCase = true)) {
                trimmed = trimmed.substring("cloudstreamrepo://".length).trim()
                if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
                    trimmed = "https://$trimmed"
                }
            } else if (trimmed.startsWith("cs3-repo://", ignoreCase = true)) {
                trimmed = trimmed.substring("cs3-repo://".length).trim()
                if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
                    trimmed = "https://$trimmed"
                }
            } else if (trimmed.contains("^(https?://)?cs\\.repo/\\??".toRegex(RegexOption.IGNORE_CASE))) {
                trimmed = trimmed.replace("^(https?://)?cs\\.repo/\\??".toRegex(RegexOption.IGNORE_CASE), "").trim()
                if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
                    trimmed = "https://$trimmed"
                }
            } else if (trimmed.startsWith("!")) {
                trimmed = "https://py.md/${trimmed.removePrefix("!")}"
            } else if (trimmed.startsWith("cutt.ly/", ignoreCase = true) ||
                trimmed.startsWith("py.md/", ignoreCase = true) ||
                trimmed.startsWith("bit.ly/", ignoreCase = true) ||
                trimmed.startsWith("tinyurl.com/", ignoreCase = true)) {
                trimmed = "https://$trimmed"
            }

            // 2. Prepend https:// if domain starts without scheme
            if (trimmed.startsWith("raw.githubusercontent.com", ignoreCase = true) ||
                trimmed.startsWith("github.com", ignoreCase = true)) {
                trimmed = "https://$trimmed"
            }

            // 3. GitHub URL conversions
            val ghBlobRegex = "^https?://github\\.com/([^/]+)/([^/]+)/blob/(.+)$".toRegex(RegexOption.IGNORE_CASE)
            val ghRawRegex = "^https?://github\\.com/([^/]+)/([^/]+)/raw/(.+)$".toRegex(RegexOption.IGNORE_CASE)
            val ghTreeBuildsRegex = "^https?://github\\.com/([^/]+)/([^/]+)/tree/builds/?$".toRegex(RegexOption.IGNORE_CASE)
            val ghBuildsRegex = "^https?://github\\.com/([^/]+)/([^/]+)/builds/?$".toRegex(RegexOption.IGNORE_CASE)
            val ghRootRegex = "^https?://github\\.com/([^/]+)/([^/]+)/?$".toRegex(RegexOption.IGNORE_CASE)

            val rawGhBuildsRegex = "^https?://raw\\.githubusercontent\\.com/([^/]+)/([^/]+)/builds/?$".toRegex(RegexOption.IGNORE_CASE)
            val rawGhRootRegex = "^https?://raw\\.githubusercontent\\.com/([^/]+)/([^/]+)/?$".toRegex(RegexOption.IGNORE_CASE)

            trimmed = when {
                ghBlobRegex.matches(trimmed) -> ghBlobRegex.replace(trimmed, "https://raw.githubusercontent.com/$1/$2/$3")
                ghRawRegex.matches(trimmed) -> ghRawRegex.replace(trimmed, "https://raw.githubusercontent.com/$1/$2/$3")
                ghTreeBuildsRegex.matches(trimmed) -> ghTreeBuildsRegex.replace(trimmed, "https://raw.githubusercontent.com/$1/$2/builds/repo.json")
                ghBuildsRegex.matches(trimmed) -> ghBuildsRegex.replace(trimmed, "https://raw.githubusercontent.com/$1/$2/builds/repo.json")
                ghRootRegex.matches(trimmed) -> ghRootRegex.replace(trimmed, "https://raw.githubusercontent.com/$1/$2/builds/repo.json")
                rawGhBuildsRegex.matches(trimmed) -> rawGhBuildsRegex.replace(trimmed, "https://raw.githubusercontent.com/$1/$2/builds/repo.json")
                rawGhRootRegex.matches(trimmed) -> rawGhRootRegex.replace(trimmed, "https://raw.githubusercontent.com/$1/$2/builds/repo.json")
                else -> trimmed
            }

            // 4. End with /builds or / -> append repo.json if appropriate
            if (trimmed.endsWith("/builds", ignoreCase = true) || trimmed.endsWith("/builds/", ignoreCase = true)) {
                trimmed = trimmed.removeSuffix("/") + "/repo.json"
            } else if (trimmed.endsWith("/")) {
                trimmed = "${trimmed}repo.json"
            }

            return trimmed
        }
    }

    init {
        handleEvent(PluginsSettingsEvent.LoadPlugins)
    }

    override fun handleEvent(event: PluginsSettingsEvent) {
        when (event) {
            is PluginsSettingsEvent.LoadPlugins -> loadPlugins()
            is PluginsSettingsEvent.Reload -> loadPlugins()
            is PluginsSettingsEvent.SyncRepositories -> loadPlugins()
            is PluginsSettingsEvent.AddRepository -> addRepository(event.url, event.name)
            is PluginsSettingsEvent.RemoveRepository -> removeRepository(event.url)
            is PluginsSettingsEvent.InstallPlugin -> installPlugin(event.plugin)
            is PluginsSettingsEvent.UninstallPlugin -> uninstallPlugin(event.filenameOrName)
            is PluginsSettingsEvent.InstallAllPlugins -> installAllPlugins(event.repositoryUrl)
            is PluginsSettingsEvent.UninstallAllPlugins -> uninstallAllPlugins(event.repositoryUrl)
            is PluginsSettingsEvent.SelectPluginForDetails -> selectPluginForDetails(event.plugin)
            is PluginsSettingsEvent.Search -> updateState { copy(searchQuery = event.query) }
            is PluginsSettingsEvent.FilterByLanguage -> updateState { copy(selectedLanguage = event.language) }
            is PluginsSettingsEvent.FilterByTvType -> updateState { copy(selectedTvType = event.tvType) }
            is PluginsSettingsEvent.FilterByRepository -> updateState { copy(selectedRepositoryUrl = event.repoUrl) }
            is PluginsSettingsEvent.ClearError -> updateState {
                copy(error = null, operationState = PluginOperationState.Idle)
            }
        }
    }

    private fun loadPlugins() {
        launchSafeJob(
            key = "load_plugins",
            onError = { t ->
                updateState {
                    copy(
                        isLoading = false,
                        error = t.message?.let { txt(it) },
                        operationState = PluginOperationState.Error(
                            messageRes = Res.string.plugins_load_failed,
                            message = t.message
                        )
                    )
                }
            }
        ) {
            loadPluginsInternal()
        }
    }

    private suspend fun loadPluginsInternal() {
        updateState { copy(isLoading = true, error = null) }
        val repos = pluginsRepository.getRepositories()
        val installed = pluginsRepository.getInstalledPlugins()
        val available = pluginsRepository.getAvailablePlugins(repos)

        val installedNames = installed.map { it.internalName }.toSet()
        val updatedAvailable = available.map { plugin ->
            plugin.copy(isInstalled = installedNames.contains(plugin.internalName))
        }

        val updatedReposFromStorage = pluginsRepository.getRepositories()
        val reposToDisplay = if (updatedReposFromStorage.isNotEmpty()) updatedReposFromStorage else repos

        val now = APIHolder.unixTimeMS
        val updatedRepos = reposToDisplay.map { repo ->
            val count = updatedAvailable.count { it.repositoryUrl == repo.url }
            repo.copy(
                pluginCount = count,
                lastSyncTime = now
            )
        }

        val updatedSelectedPlugin = currentState.selectedPluginForDetails?.let { curr ->
            updatedAvailable.firstOrNull { it.internalName == curr.internalName }
                ?: installed.firstOrNull { it.internalName == curr.internalName }
                ?: curr
        }

        updateState {
            copy(
                repositories = updatedRepos,
                installedPlugins = installed,
                availablePlugins = updatedAvailable,
                selectedPluginForDetails = updatedSelectedPlugin,
                isLoading = false,
                error = null,
                operationState = PluginOperationState.Idle
            )
        }
    }

    private fun addRepository(url: String, name: String?) {
        launchSafeJob(
            key = "add_repo",
            onError = { t ->
                updateState {
                    copy(
                        isLoading = false,
                        error = t.message?.let { txt(it) },
                        operationState = PluginOperationState.Error(
                            messageRes = Res.string.repo_add_failed,
                            formatArgs = listOf(t.message ?: ""),
                            message = t.message
                        )
                    )
                }
            }
        ) job@{
            val normalizedUrl = normalizeRepoUrl(url)
            if (!isValidRepoUrl(normalizedUrl)) {
                updateState {
                    copy(
                        error = txt(Res.string.repo_invalid_url),
                        operationState = PluginOperationState.Error(
                            messageRes = Res.string.repo_invalid_url
                        )
                    )
                }
                return@job
            }

            updateState { copy(isLoading = true, error = null) }
            val trimmedName = name?.trim()?.ifBlank { null }
            val repoName = trimmedName
                ?: normalizedUrl.substringAfterLast("/").removeSuffix(".json").ifBlank { null }
                ?: "Custom Repository"

            val newRepo = PluginRepositoryItem(
                name = repoName,
                url = normalizedUrl,
                isRemovable = true
            )

            pluginsRepository.addRepository(newRepo)
            loadPluginsInternal()
        }
    }

    private fun removeRepository(url: String) {
        launchSafeJob(
            key = "remove_repo",
            onError = { t ->
                updateState {
                    copy(
                        isLoading = false,
                        error = t.message?.let { txt(it) },
                        operationState = PluginOperationState.Error(
                            messageRes = Res.string.repo_remove_failed,
                            formatArgs = listOf(t.message ?: ""),
                            message = t.message
                        )
                    )
                }
            }
        ) {
            updateState { copy(isLoading = true, error = null) }
            pluginsRepository.removeRepository(url)
            loadPluginsInternal()
        }
    }

    private fun installPlugin(plugin: PluginItem) {
        launchSafeJob(
            key = "install_${plugin.internalName}",
            onError = { t ->
                updateState {
                    copy(
                        error = t.message?.let { txt(it) },
                        operationState = PluginOperationState.Error(
                            messageRes = Res.string.plugin_install_failed,
                            formatArgs = listOf(plugin.name),
                            message = t.message
                        )
                    )
                }
            }
        ) {
            updateState {
                copy(
                    operationState = PluginOperationState.Downloading(plugin.name, 0.5f),
                    error = null
                )
            }

            updateState {
                copy(operationState = PluginOperationState.Installing(plugin.name))
            }

            val result = pluginsRepository.installPlugin(plugin)
            result.fold(
                onSuccess = { installedPlugin ->
                    val updatedInstalled = (currentState.installedPlugins.filter { it.internalName != plugin.internalName } + installedPlugin)
                    val updatedAvailable = currentState.availablePlugins.map {
                        if (it.internalName == plugin.internalName) it.copy(isInstalled = true) else it
                    }

                    updateState {
                        copy(
                            installedPlugins = updatedInstalled,
                            availablePlugins = updatedAvailable,
                            operationState = PluginOperationState.Success(
                                messageRes = Res.string.plugin_installed_success,
                                formatArgs = listOf(plugin.name)
                            )
                        )
                    }
                    onPluginLoaded?.invoke()
                },
                onFailure = { t ->
                    updateState {
                        copy(
                            error = t.message?.let { txt(it) },
                            operationState = PluginOperationState.Error(
                                messageRes = Res.string.plugin_install_failed,
                                formatArgs = listOf(plugin.name),
                                message = t.message
                            )
                        )
                    }
                }
            )
        }
    }

    private fun uninstallPlugin(filenameOrName: String) {
        launchSafeJob(
            key = "uninstall_$filenameOrName",
            onError = { t ->
                updateState {
                    copy(
                        error = t.message?.let { txt(it) },
                        operationState = PluginOperationState.Error(
                            messageRes = Res.string.plugin_uninstall_failed,
                            formatArgs = listOf(filenameOrName),
                            message = t.message
                        )
                    )
                }
            }
        ) {
            updateState {
                copy(
                    operationState = PluginOperationState.Uninstalling(filenameOrName),
                    error = null
                )
            }

            val result = pluginsRepository.uninstallPlugin(filenameOrName)
            result.fold(
                onSuccess = {
                    val updatedInstalled = currentState.installedPlugins.filterNot {
                        it.internalName == filenameOrName ||
                                it.localFilePath == filenameOrName ||
                                it.name.equals(filenameOrName, ignoreCase = true)
                    }
                    val updatedAvailable = currentState.availablePlugins.map {
                        if (it.internalName == filenameOrName || it.localFilePath == filenameOrName) {
                            it.copy(isInstalled = false)
                        } else it
                    }

                    updateState {
                        copy(
                            installedPlugins = updatedInstalled,
                            availablePlugins = updatedAvailable,
                            operationState = PluginOperationState.Success(
                                messageRes = Res.string.plugin_uninstalled_success,
                                formatArgs = listOf(filenameOrName)
                            )
                        )
                    }

                    try {
                        onPluginLoaded?.invoke()
                    } catch (_: Throwable) {}
                },
                onFailure = { t ->
                    updateState {
                        copy(
                            error = t.message?.let { txt(it) },
                            operationState = PluginOperationState.Error(
                                messageRes = Res.string.plugin_uninstall_failed,
                                formatArgs = listOf(filenameOrName),
                                message = t.message
                            )
                        )
                    }
                }
            )
        }
    }

    private fun installAllPlugins(repoUrl: String) {
        batchPluginOperation(repoUrl, install = true)
    }

    private fun uninstallAllPlugins(repoUrl: String) {
        batchPluginOperation(repoUrl, install = false)
    }

    private fun batchPluginOperation(repoUrl: String, install: Boolean) {
        val opKey = if (install) "batch_install_$repoUrl" else "batch_uninstall_$repoUrl"
        val errorRes = if (install) Res.string.batch_install_failed else Res.string.batch_uninstall_failed

        launchSafeJob(
            key = opKey,
            onError = { t ->
                updateState {
                    copy(
                        error = t.message?.let { txt(it) },
                        operationState = PluginOperationState.Error(
                            messageRes = errorRes,
                            message = t.message
                        )
                    )
                }
            }
        ) {
            val targets = currentState.availablePlugins.filter {
                it.repositoryUrl == repoUrl && (if (install) !it.isInstalled else it.isInstalled)
            }

            if (targets.isEmpty()) {
                updateState {
                    copy(operationState = PluginOperationState.Idle)
                }
                return@launchSafeJob
            }

            var failedCount = 0
            val total = targets.size

            targets.forEachIndexed { index, plugin ->
                if (install) {
                    val progress = (index.toFloat() + 0.5f) / total.toFloat()
                    updateState {
                        copy(
                            operationState = PluginOperationState.Downloading(plugin.name, progress),
                            error = null
                        )
                    }

                    updateState {
                        copy(operationState = PluginOperationState.Installing(plugin.name))
                    }

                    val result = pluginsRepository.installPlugin(plugin)
                    result.fold(
                        onSuccess = { installedPlugin ->
                            val updatedInstalled = (currentState.installedPlugins.filter { it.internalName != plugin.internalName } + installedPlugin)
                            val updatedAvailable = currentState.availablePlugins.map {
                                if (it.internalName == plugin.internalName) it.copy(isInstalled = true) else it
                            }
                            updateState {
                                copy(
                                    installedPlugins = updatedInstalled,
                                    availablePlugins = updatedAvailable
                                )
                            }
                        },
                        onFailure = {
                            failedCount++
                        }
                    )
                } else {
                    updateState {
                        copy(
                            operationState = PluginOperationState.Uninstalling(plugin.name),
                            error = null
                        )
                    }

                    val result = pluginsRepository.uninstallPlugin(plugin.internalName)
                    result.fold(
                        onSuccess = {
                            val updatedInstalled = currentState.installedPlugins.filterNot {
                                it.internalName == plugin.internalName ||
                                        it.localFilePath == plugin.internalName ||
                                        it.name.equals(plugin.name, ignoreCase = true)
                            }
                            val updatedAvailable = currentState.availablePlugins.map {
                                if (it.internalName == plugin.internalName) {
                                    it.copy(isInstalled = false)
                                } else it
                            }
                            updateState {
                                copy(
                                    installedPlugins = updatedInstalled,
                                    availablePlugins = updatedAvailable
                                )
                            }
                        },
                        onFailure = {
                            failedCount++
                        }
                    )
                }
            }

            loadPluginsInternal()
            try {
                onPluginLoaded?.invoke()
            } catch (_: Throwable) {}

            updateState {
                if (failedCount == 0) {
                    val successRes = if (install) Res.string.batch_install_success else Res.string.batch_uninstall_success
                    copy(
                        operationState = PluginOperationState.Success(
                            messageRes = successRes,
                            formatArgs = listOf(total)
                        )
                    )
                } else {
                    copy(
                        error = txt(Res.string.batch_operation_failed, failedCount),
                        operationState = PluginOperationState.Error(
                            messageRes = Res.string.batch_operation_failed,
                            formatArgs = listOf(failedCount)
                        )
                    )
                }
            }
        }
    }

    private fun selectPluginForDetails(plugin: PluginItem?) {
        updateState { copy(selectedPluginForDetails = plugin) }
    }
}
