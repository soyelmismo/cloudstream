package com.lagradost.cloudstream3.shared.viewmodels.settings

import androidx.compose.runtime.Immutable
import cloudstream.shared_ui.generated.resources.*
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.plugins.PluginLoader
import com.lagradost.cloudstream3.shared.mvi.BaseViewModel
import com.lagradost.cloudstream3.shared.mvi.UiState
import com.lagradost.cloudstream3.shared.persistence.repository.AppPreferenceRepository
import com.lagradost.cloudstream3.shared.plugins.DefaultPluginManager
import com.lagradost.cloudstream3.shared.plugins.DefaultPluginsRepository
import com.lagradost.cloudstream3.shared.plugins.PluginFilterMode
import com.lagradost.cloudstream3.shared.plugins.PluginItem
import com.lagradost.cloudstream3.shared.plugins.matchesPlugin
import com.lagradost.cloudstream3.shared.plugins.mergePluginWithInstalled
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
import org.jetbrains.compose.resources.StringResource
import kotlin.coroutines.CoroutineContext

typealias PluginRepositoryItem = com.lagradost.cloudstream3.shared.plugins.PluginRepositoryItem
typealias PluginItem = com.lagradost.cloudstream3.shared.plugins.PluginItem
typealias PluginStatus = com.lagradost.cloudstream3.shared.plugins.PluginStatus
typealias PluginFilterMode = com.lagradost.cloudstream3.shared.plugins.PluginFilterMode
typealias RepositoryManifest = com.lagradost.cloudstream3.shared.plugins.RepositoryManifest
typealias ProviderStatus = com.lagradost.cloudstream3.shared.plugins.ProviderStatus

@Immutable
sealed class PluginOperationState {
    @Immutable
    data object Idle : PluginOperationState()

    @Immutable
    data class Downloading(val pluginName: String) : PluginOperationState()

    @Immutable
    data class Installing(val pluginName: String) : PluginOperationState()

    @Immutable
    data class Uninstalling(val pluginName: String) : PluginOperationState()

    @Immutable
    data class Success(
        val message: String? = null,
        val messageRes: StringResource? = null,
        val formatArgs: ImmutableList<Any> = persistentListOf()
    ) : PluginOperationState()

    @Immutable
    data class Error(
        val message: String? = null,
        val messageRes: StringResource? = null,
        val formatArgs: ImmutableList<Any> = persistentListOf()
    ) : PluginOperationState()
}

typealias PluginsRepository = com.lagradost.cloudstream3.shared.plugins.PluginsRepository
typealias DefaultPluginsRepository = com.lagradost.cloudstream3.shared.plugins.DefaultPluginsRepository
typealias PluginManager = com.lagradost.cloudstream3.shared.plugins.PluginManager
typealias DefaultPluginManager = com.lagradost.cloudstream3.shared.plugins.DefaultPluginManager

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
            val normalizedRepoUrl = com.lagradost.cloudstream3.shared.plugins.normalizeUrlKey(repoUrl)
            return availablePlugins.filter {
                com.lagradost.cloudstream3.shared.plugins.normalizeUrlKey(it.repositoryUrl) == normalizedRepoUrl
            }.toImmutableList()
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

class PluginsSettingsViewModel(
    private val pluginsRepository: PluginsRepository = DefaultPluginsRepository(),
    coroutineScope: CoroutineScope? = null,
    private val onPluginLoaded: (() -> Unit)? = null
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
        pluginsRepository = DefaultPluginsRepository(preferenceRepository, pluginLoader),
        coroutineScope = null,
        onPluginLoaded = onPluginLoaded
    )

    constructor(
        pluginsRepository: PluginsRepository,
        coroutineContext: CoroutineContext
    ) : this(pluginsRepository, CoroutineScope(coroutineContext))

    companion object {
        private val VALID_URL_PREFIXES = listOf(
            "cloudstreamrepo://",
            "cs3-repo://",
            "https://",
            "http://",
            "raw.githubusercontent.com",
            "github.com"
        )

        fun isValidRepoUrl(url: String): Boolean {
            val trimmed = url.trim()
            if (trimmed.isBlank() || trimmed.contains(" ") || trimmed.startsWith("ftp://")) return false
            if (trimmed.startsWith("!")) return trimmed.length > 1
            if (VALID_URL_PREFIXES.any { trimmed.startsWith(it, ignoreCase = true) }) return true
            return trimmed.contains(".") && !trimmed.endsWith(".") && trimmed.length >= 4
        }

        fun normalizeRepoUrl(url: String): String {
            val trimmed = url.trim()
            return when {
                trimmed.startsWith("!") -> "https://py.md/${trimmed.substring(1)}"
                trimmed.startsWith("cloudstreamrepo://", ignoreCase = true) -> "https://" + trimmed.substring("cloudstreamrepo://".length)
                trimmed.startsWith("cs3-repo://", ignoreCase = true) -> "https://" + trimmed.substring("cs3-repo://".length)
                trimmed.startsWith("https://cs.repo/?", ignoreCase = true) -> trimmed.substring("https://cs.repo/?".length)
                trimmed.startsWith("http://cs.repo/?", ignoreCase = true) -> trimmed.substring("http://cs.repo/?".length)
                trimmed.startsWith("cs.repo/?", ignoreCase = true) -> trimmed.substring("cs.repo/?".length)
                else -> normalizeHttpRepoUrl(trimmed)
            }
        }

        private fun normalizeHttpRepoUrl(url: String): String {
            val prefixed = if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
                "https://$url"
            } else {
                url
            }
            val cleanPrefixed = prefixed.trim().removeSuffix("/")
            return when {
                cleanPrefixed.startsWith("https://github.com/", ignoreCase = true) ||
                    cleanPrefixed.startsWith("http://github.com/", ignoreCase = true) -> normalizeGithubRepoUrl(cleanPrefixed)
                cleanPrefixed.startsWith("https://raw.githubusercontent.com/", ignoreCase = true) ||
                    cleanPrefixed.startsWith("http://raw.githubusercontent.com/", ignoreCase = true) -> normalizeRawGithubRepoUrl(cleanPrefixed)
                else -> cleanPrefixed
            }
        }

        private fun normalizeGithubRepoUrl(url: String): String {
            var raw = url.replace("https://github.com/", "https://raw.githubusercontent.com/")
            if (raw.contains("/blob/")) {
                raw = raw.replace("/blob/", "/")
            }
            val clean = raw.removeSuffix("/").removeSuffix("/builds")
            return if (clean.endsWith(".json")) clean else "$clean/builds/repo.json"
        }

        private fun normalizeRawGithubRepoUrl(url: String): String {
            if (url.endsWith(".json")) return url
            val clean = url.removeSuffix("/")
            return if (clean.endsWith("/builds")) "$clean/repo.json" else "$clean/builds/repo.json"
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

    fun loadData() = loadDataInternal(isRefresh = false)

    fun refreshData() = loadDataInternal(isRefresh = true)

    fun syncRepositories() = refreshData()

    private fun loadDataInternal(isRefresh: Boolean) {
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
            if (isRefresh) {
                updateState { copy(isRefreshing = true, error = null) }
            } else {
                emitCachedDataIfAvailable()
            }

            val repos = pluginsRepository.getRepositories().map { repo ->
                if (repo.lastSyncTime == null) repo.copy(lastSyncTime = APIHolder.unixTimeMS) else repo
            }
            val freshAvailable = pluginsRepository.getAvailablePlugins(repos)
            val freshInstalled = pluginsRepository.getInstalledPlugins()
            val freshRepos = pluginsRepository.getRepositories().map { repo ->
                if (repo.lastSyncTime == null) repo.copy(lastSyncTime = APIHolder.unixTimeMS) else repo
            }

            val reposWithFreshCounts = computeReposWithCounts(freshRepos, freshAvailable, freshInstalled)
            updateStateWithPlugins(
                repos = reposWithFreshCounts,
                installed = freshInstalled,
                available = freshAvailable,
                loading = false,
                refreshing = false
            )
        }
    }

    private suspend fun emitCachedDataIfAvailable() {
        val cachedRepos = pluginsRepository.getRepositories().map { repo ->
            if (repo.lastSyncTime == null) repo.copy(lastSyncTime = APIHolder.unixTimeMS) else repo
        }
        val cachedAvailable = pluginsRepository.getCachedAvailablePlugins()
        val cachedInstalled = pluginsRepository.reconcileWithDisk(cachedAvailable)

        val hasCached = cachedRepos.isNotEmpty() || cachedAvailable.isNotEmpty() || cachedInstalled.isNotEmpty()
        if (hasCached) {
            val reposWithCounts = computeReposWithCounts(cachedRepos, cachedAvailable, cachedInstalled)
            updateStateWithPlugins(
                repos = reposWithCounts,
                installed = cachedInstalled,
                available = cachedAvailable,
                loading = true,
                refreshing = false
            )
        } else {
            updateState { copy(isLoading = true, error = null) }
        }
    }

    private fun computeReposWithCounts(
        repos: List<PluginRepositoryItem>,
        available: List<PluginItem>,
        installed: List<PluginItem> = emptyList()
    ): List<PluginRepositoryItem> {
        return repos.map { repo ->
            val count = countPluginsForRepo(repo.url, available)
            val instCount = countInstalledPluginsForRepo(repo.url, installed, available)
            repo.copy(
                pluginCount = if (count > 0) count else repo.pluginCount,
                installedCount = instCount,
                lastSyncTime = repo.lastSyncTime ?: APIHolder.unixTimeMS
            )
        }
    }

    private fun countPluginsForRepo(repoUrl: String, plugins: List<PluginItem>): Int {
        val cleanRepoUrl = com.lagradost.cloudstream3.shared.plugins.normalizeUrlKey(repoUrl)
        return plugins.count {
            com.lagradost.cloudstream3.shared.plugins.normalizeUrlKey(it.repositoryUrl) == cleanRepoUrl
        }
    }

    private fun countInstalledPluginsForRepo(
        repoUrl: String,
        installed: List<PluginItem>,
        available: List<PluginItem>
    ): Int {
        val cleanRepoUrl = com.lagradost.cloudstream3.shared.plugins.normalizeUrlKey(repoUrl)
        return installed.count { inst ->
            isInstalledMatchingRepo(inst, cleanRepoUrl, available)
        }
    }

    private fun isInstalledMatchingRepo(
        inst: PluginItem,
        cleanRepoUrl: String,
        available: List<PluginItem>
    ): Boolean {
        if (com.lagradost.cloudstream3.shared.plugins.normalizeUrlKey(inst.repositoryUrl) == cleanRepoUrl) {
            return true
        }
        val normInst = inst.internalName.replace(" ", "").removeSuffix("Provider")
        val fileBase = inst.localFilePath?.substringAfterLast('/')?.substringAfterLast('\\')?.substringBeforeLast('.')
        val normFile = fileBase?.replace(" ", "")?.removeSuffix("Provider")

        return available.any { avail ->
            if (com.lagradost.cloudstream3.shared.plugins.normalizeUrlKey(avail.repositoryUrl) != cleanRepoUrl) return@any false
            val normAvail = avail.internalName.replace(" ", "").removeSuffix("Provider")
            avail.internalName.equals(inst.internalName, ignoreCase = true) ||
                (avail.name.isNotBlank() && avail.name.equals(inst.name, ignoreCase = true)) ||
                (normInst.isNotBlank() && normAvail.equals(normInst, ignoreCase = true)) ||
                (normFile != null && normFile.isNotBlank() && normAvail.equals(normFile, ignoreCase = true))
        }
    }

    private fun updateStateWithPlugins(
        repos: List<PluginRepositoryItem>,
        installed: List<PluginItem>,
        available: List<PluginItem>,
        loading: Boolean,
        refreshing: Boolean
    ) {
        updateState {
            val updatedSelectedRepo = findUpdatedSelectedRepo(selectedRepository, repos)
            val repoPlugins = filterPluginsForRepo(updatedSelectedRepo?.url, available)

            copy(
                repositories = repos.toImmutableList(),
                downloadedPlugins = installed.toImmutableList(),
                availablePlugins = available.toImmutableList(),
                selectedRepository = updatedSelectedRepo,
                selectedRepositoryPlugins = repoPlugins,
                isLoading = loading,
                isRefreshing = refreshing,
                error = null
            )
        }
    }

    private fun findUpdatedSelectedRepo(
        selected: PluginRepositoryItem?,
        repos: List<PluginRepositoryItem>
    ): PluginRepositoryItem? {
        if (selected == null) return null
        val targetUrl = selected.url.trim().removeSuffix("/")
        return repos.firstOrNull {
            it.url.trim().removeSuffix("/").equals(targetUrl, ignoreCase = true)
        } ?: selected
    }

    private fun filterPluginsForRepo(
        repoUrl: String?,
        available: List<PluginItem>
    ): ImmutableList<PluginItem> {
        if (repoUrl == null) return persistentListOf()
        val cleanTarget = repoUrl.trim().removeSuffix("/")
        return available.filter {
            it.repositoryUrl.trim().removeSuffix("/").equals(cleanTarget, ignoreCase = true)
        }.toImmutableList()
    }

    private fun findRepoByUrl(
        url: String?,
        repos: List<PluginRepositoryItem>
    ): PluginRepositoryItem? {
        if (url == null) return null
        val cleanTarget = url.trim().removeSuffix("/")
        return repos.firstOrNull {
            it.url.trim().removeSuffix("/").equals(cleanTarget, ignoreCase = true)
        }
    }

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
            val repoPlugins = filterPluginsForRepo(repo?.url, availablePlugins)
            copy(
                selectedRepository = repo,
                selectedRepositoryPlugins = repoPlugins
            )
        }
    }

    fun filterByRepository(url: String?) {
        selectRepository(findRepoByUrl(url, currentState.repositories))
    }

    fun installPlugin(plugin: PluginItem) {
        if (!plugin.canInstall || plugin.isDown) {
            updateState {
                copy(
                    operationState = PluginOperationState.Error(
                        messageRes = Res.string.plugin_disabled_cannot_install,
                        formatArgs = persistentListOf(plugin.name)
                    )
                )
            }
            return
        }

        launchSafeJob(
            key = "install_plugin_${plugin.internalName}",
            onError = { t ->
                updateState {
                    copy(
                        operationState = PluginOperationState.Error(
                            message = t.message,
                            messageRes = Res.string.plugin_install_failed,
                            formatArgs = persistentListOf(plugin.name)
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
                    val updatedRepos = computeReposWithCounts(currentState.repositories, updatedAvailable, updatedInstalled)

                    updateState {
                        copy(
                            repositories = updatedRepos.toImmutableList(),
                            availablePlugins = updatedAvailable,
                            downloadedPlugins = updatedInstalled,
                            operationState = PluginOperationState.Success(
                                messageRes = Res.string.plugin_installed_success,
                                formatArgs = persistentListOf(plugin.name)
                            )
                        )
                    }
                    onPluginLoaded?.invoke()
                },
                onFailure = { t ->
                    updateState {
                        copy(
                            operationState = PluginOperationState.Error(
                                message = t.message,
                                messageRes = Res.string.plugin_install_failed,
                                formatArgs = persistentListOf(plugin.name)
                            )
                        )
                    }
                }
            )
        }
    }

    fun downloadPlugin(plugin: PluginItem) = installPlugin(plugin)

    fun uninstallPlugin(filenameOrName: String) {
        val cleanName = filenameOrName
            .substringAfterLast("/")
            .substringAfterLast("\\")
            .removeSuffix(".cs3")
            .removeSuffix(".jar")

        launchSafeJob(
            key = "uninstall_plugin_$filenameOrName",
            onError = { t ->
                updateState {
                    copy(
                        operationState = PluginOperationState.Error(
                            message = t.message,
                            messageRes = Res.string.plugin_uninstall_failed,
                            formatArgs = persistentListOf(filenameOrName)
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
                        val matches = it.internalName == filenameOrName ||
                            it.name == filenameOrName ||
                            it.internalName.equals(cleanName, ignoreCase = true) ||
                            it.name.equals(cleanName, ignoreCase = true)
                        if (matches) it.copy(isInstalled = false, isDownloaded = false) else it
                    }.toImmutableList()
                    val updatedInstalled = currentState.downloadedPlugins.filter {
                        it.internalName != filenameOrName &&
                            it.name != filenameOrName &&
                            !it.internalName.equals(cleanName, ignoreCase = true) &&
                            !it.name.equals(cleanName, ignoreCase = true)
                    }.toImmutableList()
                    val updatedRepos = computeReposWithCounts(currentState.repositories, updatedAvailable, updatedInstalled)

                    updateState {
                        copy(
                            repositories = updatedRepos.toImmutableList(),
                            availablePlugins = updatedAvailable,
                            downloadedPlugins = updatedInstalled,
                            operationState = PluginOperationState.Success(
                                messageRes = Res.string.plugin_uninstalled_success,
                                formatArgs = persistentListOf(filenameOrName)
                            )
                        )
                    }
                    onPluginLoaded?.invoke()
                },
                onFailure = { t ->
                    updateState {
                        copy(
                            operationState = PluginOperationState.Error(
                                message = t.message,
                                messageRes = Res.string.plugin_uninstall_failed,
                                formatArgs = persistentListOf(filenameOrName)
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
        val toUpdate = currentState.allPlugins.filter { it.hasUpdate && it.canInstall }
        if (toUpdate.isEmpty()) return
        toUpdate.forEach { installPlugin(it) }
    }

    fun installAllPlugins(repoUrl: String) {
        val cleanRepoUrl = com.lagradost.cloudstream3.shared.plugins.normalizeUrlKey(repoUrl)
        val targets = currentState.availablePlugins.filter {
            com.lagradost.cloudstream3.shared.plugins.normalizeUrlKey(it.repositoryUrl) == cleanRepoUrl &&
                !it.isInstalled && it.canInstall
        }
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
                            formatArgs = persistentListOf(targets.size)
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
            val updatedRepos = computeReposWithCounts(currentState.repositories, available, installed)

            val opState = if (failCount == 0) {
                PluginOperationState.Success(
                    messageRes = Res.string.batch_install_success,
                    formatArgs = persistentListOf(successCount)
                )
            } else {
                PluginOperationState.Error(
                    messageRes = Res.string.batch_operation_failed,
                    formatArgs = persistentListOf(failCount)
                )
            }

            updateState {
                copy(
                    repositories = updatedRepos.toImmutableList(),
                    availablePlugins = available.toImmutableList(),
                    downloadedPlugins = installed.toImmutableList(),
                    operationState = opState
                )
            }
            if (successCount > 0) {
                onPluginLoaded?.invoke()
            }
        }
    }

    fun uninstallAllPlugins(repoUrl: String) {
        val cleanRepoUrl = com.lagradost.cloudstream3.shared.plugins.normalizeUrlKey(repoUrl)
        val targets = currentState.downloadedPlugins.filter {
            isInstalledMatchingRepo(it, cleanRepoUrl, currentState.availablePlugins)
        }
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
                            formatArgs = persistentListOf(targets.size)
                        )
                    )
                }
            }
        ) {
            var successCount = 0
            var failCount = 0
            for (plugin in targets) {
                val res = pluginsRepository.uninstallPlugin(plugin.internalName.ifBlank { plugin.name })
                if (res.isSuccess) {
                    successCount++
                } else {
                    failCount++
                }
            }

            val installed = pluginsRepository.getInstalledPlugins()
            val available = pluginsRepository.getAvailablePlugins(currentState.repositories)
            val updatedRepos = computeReposWithCounts(currentState.repositories, available, installed)

            val opState = if (failCount == 0) {
                PluginOperationState.Success(
                    messageRes = Res.string.batch_uninstall_success,
                    formatArgs = persistentListOf(successCount)
                )
            } else {
                PluginOperationState.Error(
                    messageRes = Res.string.batch_operation_failed,
                    formatArgs = persistentListOf(failCount)
                )
            }

            updateState {
                copy(
                    repositories = updatedRepos.toImmutableList(),
                    availablePlugins = available.toImmutableList(),
                    downloadedPlugins = installed.toImmutableList(),
                    operationState = opState
                )
            }
            if (successCount > 0) {
                onPluginLoaded?.invoke()
            }
        }
    }

    fun togglePlugin(plugin: PluginItem) {
        if (!plugin.isEnabled && (!plugin.canEnable || plugin.isDown)) {
            updateState {
                copy(
                    operationState = PluginOperationState.Error(
                        messageRes = Res.string.plugin_disabled_cannot_enable,
                        formatArgs = persistentListOf(plugin.name)
                    )
                )
            }
            return
        }
        val updated = plugin.copy(isEnabled = !plugin.isEnabled)
        installPlugin(updated)
    }
}
