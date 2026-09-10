package com.lagradost.cloudstream3.shared.plugins

import androidx.compose.runtime.Immutable
import com.lagradost.cloudstream3.PROVIDER_STATUS_BETA_ONLY
import com.lagradost.cloudstream3.PROVIDER_STATUS_DOWN
import com.lagradost.cloudstream3.PROVIDER_STATUS_OK
import com.lagradost.cloudstream3.PROVIDER_STATUS_SLOW
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource
import com.lagradost.cloudstream3.plugins.SitePlugin

@Serializable
enum class ProviderStatus(val value: Int) {
    DOWN(PROVIDER_STATUS_DOWN),
    OK(PROVIDER_STATUS_OK),
    SLOW(PROVIDER_STATUS_SLOW),
    BETA_ONLY(PROVIDER_STATUS_BETA_ONLY);

    val isDown: Boolean get() = this == DOWN
    val isOperational: Boolean get() = this != DOWN
    val isBad: Boolean get() = this == DOWN

    companion object {
        fun fromInt(value: Int?): ProviderStatus = when (value) {
            PROVIDER_STATUS_DOWN -> DOWN
            PROVIDER_STATUS_SLOW -> SLOW
            PROVIDER_STATUS_BETA_ONLY -> BETA_ONLY
            else -> OK
        }

        fun fromString(value: String?): ProviderStatus = when (value?.trim()?.lowercase()) {
            "0", "down", "disabled", "broken", "inactive", "false" -> DOWN
            "2", "slow" -> SLOW
            "3", "beta", "beta_only", "beta only" -> BETA_ONLY
            else -> OK
        }
    }
}

@Serializable
@Immutable
data class PluginRepositoryItem(
    val name: String = "",
    val url: String = "",
    val isRemovable: Boolean = true,
    val pluginCount: Int = 0,
    val installedCount: Int = 0,
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
    val isInstalled: Boolean = isDownloaded,
    val providerStatus: ProviderStatus = ProviderStatus.OK
) {
    val id: String get() = internalName.ifBlank { name }
    val authorsImmutable: ImmutableList<String> get() = authors.toImmutableList()
    val tvTypesImmutable: ImmutableList<String> get() = tvTypes.toImmutableList()
    val permissionsImmutable: ImmutableList<String> get() = permissions.toImmutableList()

    val isDown: Boolean get() = providerStatus.isDown || !providerStatus.isOperational
    val isBad: Boolean get() = isDown
    val canInstall: Boolean get() = !isDown
    val canEnable: Boolean get() = !isDown

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
        status: PluginStatus = PluginStatus.NOT_INSTALLED,
        providerStatus: ProviderStatus = ProviderStatus.OK
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
        isEnabled = if (providerStatus.isDown) false else isEnabled,
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
        isInstalled = isInstalled || isDownloaded,
        providerStatus = providerStatus
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


typealias PluginOperationState = com.lagradost.cloudstream3.shared.viewmodels.settings.PluginOperationState

fun resolvePluginUrl(url: String, baseUrl: String): String {
    if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
        return url
    }
    val cleanBase = baseUrl.trim().removeSuffix("/")
    val cleanPath = url.trim().removePrefix("/")
    return if (cleanBase.isEmpty()) cleanPath else "$cleanBase/$cleanPath"
}

fun SitePlugin.toPluginItem(
    repoUrl: String,
    baseUrl: String,
    isInstalled: Boolean
): PluginItem {
    val key = internalName.ifBlank { name }
    val resolvedUrl = if (url.isNotBlank()) resolvePluginUrl(url, baseUrl) else url
    val resolvedIcon = iconUrl?.let { resolvePluginUrl(it, baseUrl) }
    val pStatus = ProviderStatus.fromInt(status)
    return PluginItem(
        internalName = key,
        name = name.ifBlank { key },
        version = version,
        description = description,
        authors = authors,
        iconUrl = resolvedIcon,
        repositoryUrl = repoUrl,
        isDownloaded = isInstalled,
        isInstalled = isInstalled,
        isEnabled = !pStatus.isDown && (status != 0),
        language = language,
        fileSize = fileSize,
        url = resolvedUrl,
        fileHash = fileHash,
        tvTypes = tvTypes ?: emptyList(),
        status = if (isInstalled) PluginStatus.INSTALLED else PluginStatus.NOT_INSTALLED,
        providerStatus = pStatus
    )
}

fun mergePluginWithInstalled(installed: PluginItem, available: PluginItem): PluginItem {
    val hasUpdate = available.version > installed.version
    val effectiveName = when {
        available.name.isNotBlank() && (installed.name.isBlank() || installed.name == installed.internalName) -> available.name
        installed.name.isNotBlank() -> installed.name
        else -> available.name
    }
    val effectiveProviderStatus = available.providerStatus
    val isEffectivelyEnabled = if (effectiveProviderStatus.isDown) false else installed.isEnabled
    return installed.copy(
        name = effectiveName,
        hasUpdate = hasUpdate,
        remoteVersion = available.version,
        description = installed.description ?: available.description,
        authors = installed.authors.ifEmpty { available.authors },
        iconUrl = installed.iconUrl ?: available.iconUrl,
        url = if (available.url.isNotBlank()) available.url else installed.url,
        fileHash = available.fileHash ?: installed.fileHash,
        fileSize = available.fileSize ?: installed.fileSize,
        repositoryUrl = available.repositoryUrl.ifBlank { installed.repositoryUrl },
        tvTypes = installed.tvTypes.ifEmpty { available.tvTypes },
        language = installed.language ?: available.language,
        providerStatus = effectiveProviderStatus,
        isEnabled = isEffectivelyEnabled
    )
}

fun matchesFilterMode(plugin: PluginItem, mode: PluginFilterMode): Boolean = when (mode) {
    PluginFilterMode.ALL -> true
    PluginFilterMode.INSTALLED -> plugin.isInstalled || plugin.isDownloaded
    PluginFilterMode.AVAILABLE -> !plugin.isInstalled && !plugin.isDownloaded
    PluginFilterMode.UPDATES_AVAILABLE -> plugin.hasUpdate
}

fun matchesSearchQuery(plugin: PluginItem, query: String): Boolean {
    if (query.isBlank()) return true
    val q = query.trim().lowercase()
    val matchesName = plugin.name.lowercase().contains(q) || plugin.internalName.lowercase().contains(q)
    val matchesDescription = plugin.description?.lowercase()?.contains(q) == true
    val matchesAuthors = plugin.authors.any { it.lowercase().contains(q) }
    return matchesName || matchesDescription || matchesAuthors
}

fun matchesLanguage(pluginLanguage: String?, selectedLanguage: String?): Boolean {
    if (selectedLanguage.isNullOrBlank() || selectedLanguage.equals("all", ignoreCase = true)) return true
    if (pluginLanguage == null || pluginLanguage.equals("all", ignoreCase = true)) return true
    return pluginLanguage.equals(selectedLanguage, ignoreCase = true)
}

fun matchesTvType(pluginTvTypes: List<String>, selectedTvType: String?): Boolean {
    if (selectedTvType.isNullOrBlank() || selectedTvType.equals("all", ignoreCase = true)) return true
    return pluginTvTypes.any { it.equals(selectedTvType, ignoreCase = true) }
}

fun matchesPlugin(
    plugin: PluginItem,
    filterMode: PluginFilterMode,
    searchQuery: String,
    selectedLanguage: String?,
    selectedTvType: String?
): Boolean {
    if (!matchesFilterMode(plugin, filterMode)) return false
    if (!matchesSearchQuery(plugin, searchQuery)) return false
    if (!matchesLanguage(plugin.language, selectedLanguage)) return false
    return matchesTvType(plugin.tvTypes, selectedTvType)
}
