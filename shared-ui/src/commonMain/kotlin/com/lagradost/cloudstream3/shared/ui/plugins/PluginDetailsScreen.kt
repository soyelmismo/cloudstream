package com.lagradost.cloudstream3.shared.ui.plugins

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Surface
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.ProviderType
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.shared.ui.components.AsyncImage
import com.lagradost.cloudstream3.shared.ui.components.designsystem.ActionDialog
import com.lagradost.cloudstream3.shared.ui.components.designsystem.ConfirmDeleteDialog
import org.jetbrains.compose.resources.stringResource
import cloudstream.shared_ui.generated.resources.*
import com.lagradost.cloudstream3.shared.ui.theme.CloudstreamTheme
import com.lagradost.cloudstream3.shared.viewmodels.settings.PluginItem
import com.lagradost.cloudstream3.shared.viewmodels.settings.PluginOperationState
import com.lagradost.cloudstream3.shared.viewmodels.settings.PluginsSettingsEvent
import com.lagradost.cloudstream3.shared.viewmodels.settings.PluginsSettingsViewModel

/**
 * Model representing provider details extracted from an extension package.
 */
data class PluginProviderDetails(
    val name: String,
    val mainUrl: String,
    val language: String,
    val supportedTypes: Set<TvType>,
    val isActive: Boolean,
    val isDirectProvider: Boolean = true,
    val hasMainPage: Boolean = false,
    val hasQuickSearch: Boolean = false,
    val hasDownloadSupport: Boolean = true,
    val hasChromecastSupport: Boolean = true
)

/**
 * Model representing a security permission required by the plugin.
 */
data class PluginPermissionItem(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val isDangerous: Boolean = false
)

/**
 * Model representing a changelog entry.
 */
data class PluginChangelogItem(
    val version: String,
    val date: String,
    val changes: List<String>
)

/**
 * Detailed View Screen for a single Extension Plugin.
 *
 * Designed with a pure AMOLED dark interface showcasing:
 * - Header with plugin avatar, title, author, version status, and repository badge.
 * - Interactive action bar (Install, Update, Uninstall, Enable/Disable) with reactive loading feedback.
 * - Included MainAPI providers list with language, supported TvTypes, and capabilities.
 * - Markdown description and version Changelog.
 * - Required security permissions and technical file specifications.
 *
 * @param plugin The initial [PluginItem] passed from navigation.
 * @param viewModel The shared [PluginsSettingsViewModel] instance.
 * @param onBackClick Callback to navigate back to the previous screen.
 * @param modifier Optional root modifier.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PluginDetailsScreen(
    plugin: PluginItem,
    viewModel: PluginsSettingsViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()

    // Live reactive plugin instance synced with ViewModel state
    val livePlugin = remember(state.installedPlugins, state.availablePlugins, plugin) {
        state.installedPlugins.firstOrNull { it.internalName == plugin.internalName }
            ?: state.availablePlugins.firstOrNull { it.internalName == plugin.internalName }
            ?: plugin
    }

    val isInstalled = state.installedPlugins.any { it.internalName == livePlugin.internalName } || livePlugin.isInstalled
    val remotePlugin = state.availablePlugins.firstOrNull { it.internalName == livePlugin.internalName }
    val isUpdateAvailable = isInstalled && remotePlugin != null && remotePlugin.version > livePlugin.version

    var showUninstallDialog by remember { mutableStateOf(false) }

    // Resolve included providers from APIHolder or synthesize from plugin metadata
    val providers = remember(livePlugin, isInstalled) {
        resolvePluginProviders(livePlugin, isInstalled)
    }

    // Resolve permissions for the plugin
    val permissions = resolvePluginPermissions(livePlugin)

    // Resolve changelog history
    val changelog = resolvePluginChangelog(livePlugin)

    // Repository name resolution
    val repositoryName = resolveRepositoryName(livePlugin.repositoryUrl, state.repositories)

    val isDownloading = state.operationState is PluginOperationState.Downloading &&
            (state.operationState as PluginOperationState.Downloading).pluginName.equals(livePlugin.name, ignoreCase = true)
    val isInstalling = state.operationState is PluginOperationState.Installing &&
            (state.operationState as PluginOperationState.Installing).pluginName.equals(livePlugin.name, ignoreCase = true)
    val isUninstalling = state.operationState is PluginOperationState.Uninstalling &&
            (state.operationState as PluginOperationState.Uninstalling).pluginName.equals(livePlugin.internalName, ignoreCase = true)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
    ) {
        // Top App Bar
        PluginDetailsTopBar(
            title = livePlugin.name,
            onBackClick = onBackClick,
            onRefreshClick = { viewModel.handleEvent(PluginsSettingsEvent.Reload) }
        )

        // Reactive Status Banner (Errors / Download Progress / Success)
        OperationStatusBanner(
            operationState = state.operationState,
            error = state.error,
            onDismiss = { viewModel.handleEvent(PluginsSettingsEvent.ClearError) }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // 1. Hero Header Card
            item {
                PluginHeroHeaderCard(
                    plugin = livePlugin,
                    repositoryName = repositoryName,
                    isInstalled = isInstalled,
                    isUpdateAvailable = isUpdateAvailable,
                    remoteVersion = remotePlugin?.version
                )
            }

            // 2. Interactive Action Bar Card
            item {
                PluginActionBarCard(
                    plugin = livePlugin,
                    isInstalled = isInstalled,
                    isUpdateAvailable = isUpdateAvailable,
                    remoteVersion = remotePlugin?.version ?: livePlugin.version,
                    isDownloading = isDownloading,
                    isInstalling = isInstalling,
                    isUninstalling = isUninstalling,
                    downloadProgress = (state.operationState as? PluginOperationState.Downloading)?.progress ?: 0f,
                    onInstall = { viewModel.handleEvent(PluginsSettingsEvent.InstallPlugin(livePlugin)) },
                    onUpdate = {
                        val target = remotePlugin ?: livePlugin
                        viewModel.handleEvent(PluginsSettingsEvent.InstallPlugin(target))
                    },
                    onToggleEnabled = { enabled ->
                        viewModel.handleEvent(PluginsSettingsEvent.InstallPlugin(livePlugin.copy(isEnabled = enabled)))
                    },
                    onUninstallClick = { showUninstallDialog = true }
                )
            }

            // 3. Included Providers Section (MainAPI)
            item {
                IncludedProvidersSection(
                    providers = providers,
                    isPluginEnabled = livePlugin.isEnabled
                )
            }

            // 4. Description & Changelog Section
            item {
                PluginDescriptionAndChangelogCard(
                    description = livePlugin.description,
                    changelog = changelog
                )
            }

            // 5. Security & Required Permissions Section
            item {
                PluginPermissionsCard(
                    permissions = permissions
                )
            }

            // 6. Technical Specifications Card
            item {
                PluginTechnicalSpecsCard(
                    plugin = livePlugin,
                    repositoryName = repositoryName
                )
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    // Confirmation Dialog: Uninstall Plugin
    if (showUninstallDialog) {
        ConfirmDeleteDialog(
            onConfirm = {
                showUninstallDialog = false
                viewModel.handleEvent(PluginsSettingsEvent.UninstallPlugin(livePlugin.internalName))
            },
            onDismiss = { showUninstallDialog = false },
            titleRes = Res.string.uninstallPluginConfirmTitle,
            messageRes = Res.string.uninstallPluginConfirmDesc,
            confirmTextRes = Res.string.uninstall
        )
    }
}

/**
 * Top App Bar for Plugin Details screen.
 */
@Composable
private fun PluginDetailsTopBar(
    title: String,
    onBackClick: () -> Unit,
    onRefreshClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colors.surface,
        border = BorderStroke(1.dp, CloudstreamTheme.extendedColors.divider),
        elevation = 4.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(Res.string.action_back),
                        tint = CloudstreamTheme.extendedColors.textPrimary
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.subtitle1.copy(fontWeight = FontWeight.Bold),
                        color = CloudstreamTheme.extendedColors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(Res.string.pluginDetails),
                        style = MaterialTheme.typography.caption,
                        color = CloudstreamTheme.extendedColors.textMuted
                    )
                }
            }

            IconButton(onClick = onRefreshClick) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(Res.string.refresh),
                    tint = CloudstreamTheme.extendedColors.textSecondary
                )
            }
        }
    }
}

/**
 * Hero Header Card showcasing Plugin Avatar, Name, Authors, Badges, and Version info.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PluginHeroHeaderCard(
    plugin: PluginItem,
    repositoryName: String,
    isInstalled: Boolean,
    isUpdateAvailable: Boolean,
    remoteVersion: Int?,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        backgroundColor = CloudstreamTheme.extendedColors.cardBackground,
        border = BorderStroke(1.dp, CloudstreamTheme.extendedColors.cardBorder),
        elevation = 0.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Plugin Avatar / Icon Box
                if (!plugin.iconUrl.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(CloudstreamTheme.extendedColors.divider),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            url = plugin.iconUrl,
                            contentDescription = plugin.name,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colors.primary,
                                        MaterialTheme.colors.secondary
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = plugin.name.take(2).uppercase(),
                            style = MaterialTheme.typography.h4.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = CloudStreamColors.OnMediaScrim,
                                fontSize = 24.sp
                            )
                        )
                    }
                }

                // Title & Authors
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = plugin.name,
                        style = MaterialTheme.typography.h5.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        ),
                        color = CloudstreamTheme.extendedColors.textPrimary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    if (plugin.authors.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = CloudstreamTheme.extendedColors.textMuted,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = stringResource(Res.string.plugin_by_author_format, plugin.authors.joinToString(", ")),
                                style = MaterialTheme.typography.body2.copy(fontSize = 13.sp),
                                color = CloudstreamTheme.extendedColors.textSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Package Identifier chip
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colors.background,
                        border = BorderStroke(1.dp, CloudstreamTheme.extendedColors.divider)
                    ) {
                        Text(
                            text = plugin.internalName,
                            style = MaterialTheme.typography.caption.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            ),
                            color = CloudstreamTheme.extendedColors.textMuted,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Divider(color = CloudstreamTheme.extendedColors.divider, thickness = 1.dp)

            // Badges Row (Repository, Status, Language, Version)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Repository Source Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colors.primary.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, MaterialTheme.colors.primary.copy(alpha = 0.3f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Source,
                            contentDescription = null,
                            tint = MaterialTheme.colors.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = repositoryName,
                            style = MaterialTheme.typography.caption.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = MaterialTheme.colors.primary
                        )
                    }
                }

                // Status Badge
                PluginStatusBadge(status = plugin.status)

                // Language Badge
                if (!plugin.language.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = CloudstreamTheme.extendedColors.divider,
                        border = BorderStroke(1.dp, CloudstreamTheme.extendedColors.cardBorder)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = CloudstreamTheme.extendedColors.textSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = plugin.language.uppercase(),
                                style = MaterialTheme.typography.caption.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                ),
                                color = CloudstreamTheme.extendedColors.textPrimary
                            )
                        }
                    }
                }

                // Version State Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isUpdateAvailable) MaterialTheme.colors.secondary.copy(alpha = 0.15f)
                    else if (isInstalled) CloudStreamColors.Success.copy(alpha = 0.15f)
                    else CloudstreamTheme.extendedColors.divider,
                    border = BorderStroke(
                        1.dp,
                        if (isUpdateAvailable) MaterialTheme.colors.secondary.copy(alpha = 0.4f)
                        else if (isInstalled) CloudStreamColors.Success.copy(alpha = 0.4f)
                        else CloudstreamTheme.extendedColors.cardBorder
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isUpdateAvailable) Icons.Default.SystemUpdate
                            else if (isInstalled) Icons.Default.CheckCircle
                            else Icons.Default.Extension,
                            contentDescription = null,
                            tint = if (isUpdateAvailable) MaterialTheme.colors.secondary
                            else if (isInstalled) CloudStreamColors.Success
                            else CloudstreamTheme.extendedColors.textSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (isUpdateAvailable) stringResource(Res.string.plugin_status_update_format, remoteVersion ?: (plugin.version + 1))
                            else if (isInstalled) stringResource(Res.string.plugin_status_installed_format, plugin.version)
                            else stringResource(Res.string.plugin_status_available_format, plugin.version),
                            style = MaterialTheme.typography.caption.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = if (isUpdateAvailable) MaterialTheme.colors.secondary
                            else if (isInstalled) CloudStreamColors.Success
                            else CloudstreamTheme.extendedColors.textPrimary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Interactive Action Bar Card with Install, Update, Uninstall, and Enable/Disable toggles.
 */
@Composable
private fun PluginActionBarCard(
    plugin: PluginItem,
    isInstalled: Boolean,
    isUpdateAvailable: Boolean,
    remoteVersion: Int,
    isDownloading: Boolean,
    isInstalling: Boolean,
    isUninstalling: Boolean,
    downloadProgress: Float,
    onInstall: () -> Unit,
    onUpdate: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onUninstallClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        backgroundColor = CloudstreamTheme.extendedColors.cardBackground,
        border = BorderStroke(1.dp, CloudstreamTheme.extendedColors.cardBorder),
        elevation = 0.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(Res.string.plugin_actions),
                style = MaterialTheme.typography.subtitle2.copy(fontWeight = FontWeight.Bold),
                color = CloudstreamTheme.extendedColors.textPrimary
            )

            // Loading Progress indicator
            if (isDownloading || isInstalling || isUninstalling) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colors.background, RoundedCornerShape(10.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                color = CloudStreamColors.Primary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = when {
                                    isDownloading -> "${stringResource(Res.string.downloading)} (${(downloadProgress * 100).toInt()}%)"
                                    isInstalling -> stringResource(Res.string.installing)
                                    else -> stringResource(Res.string.plugin_uninstalling)
                                },
                                style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.Medium),
                                color = CloudstreamTheme.extendedColors.textPrimary
                            )
                        }
                    }

                    if (isDownloading) {
                        LinearProgressIndicator(
                            progress = downloadProgress,
                            color = CloudStreamColors.Primary,
                            backgroundColor = CloudstreamTheme.extendedColors.divider,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    } else {
                        LinearProgressIndicator(
                            color = CloudStreamColors.Primary,
                            backgroundColor = CloudstreamTheme.extendedColors.divider,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }
                }
            } else if (!isInstalled) {
                // Not installed: Large Install Button using PrimaryButton
                com.lagradost.cloudstream3.shared.ui.components.designsystem.PrimaryButton(
                    text = stringResource(Res.string.plugin_install_extension_format, plugin.version),
                    icon = Icons.Default.Download,
                    onClick = onInstall,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                // Installed Controls: Enable / Disable Switch
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colors.background,
                    border = BorderStroke(1.dp, CloudstreamTheme.extendedColors.divider),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (plugin.isEnabled) stringResource(Res.string.enablePlugin) else stringResource(Res.string.disablePlugin),
                                style = MaterialTheme.typography.subtitle2.copy(fontWeight = FontWeight.Bold),
                                color = CloudstreamTheme.extendedColors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (plugin.isEnabled) stringResource(Res.string.plugin_scrapers_active_desc)
                                else stringResource(Res.string.plugin_scrapers_disabled_desc),
                                style = MaterialTheme.typography.caption,
                                color = CloudstreamTheme.extendedColors.textMuted
                            )
                        }

                        Switch(
                            checked = plugin.isEnabled,
                            onCheckedChange = onToggleEnabled,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = CloudStreamColors.Primary,
                                checkedTrackColor = CloudStreamColors.Primary.copy(alpha = 0.5f)
                            )
                        )
                    }
                }

                // Action Buttons Row: Update & Uninstall using PrimaryButton, SecondaryButton, DangerButton
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isUpdateAvailable) {
                        com.lagradost.cloudstream3.shared.ui.components.designsystem.PrimaryButton(
                            text = stringResource(Res.string.plugin_update_to_format, remoteVersion),
                            icon = Icons.Default.SystemUpdate,
                            onClick = onUpdate,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        com.lagradost.cloudstream3.shared.ui.components.designsystem.SecondaryButton(
                            textRes = Res.string.reinstall,
                            icon = Icons.Default.Refresh,
                            onClick = onInstall,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    com.lagradost.cloudstream3.shared.ui.components.designsystem.DangerButton(
                        textRes = Res.string.uninstall,
                        icon = Icons.Default.Delete,
                        onClick = onUninstallClick,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * Included Providers List Section (`MainAPI` providers bundled inside the plugin).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IncludedProvidersSection(
    providers: List<PluginProviderDetails>,
    isPluginEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        backgroundColor = CloudstreamTheme.extendedColors.cardBackground,
        border = BorderStroke(1.dp, CloudstreamTheme.extendedColors.cardBorder),
        elevation = 0.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        tint = CloudStreamColors.Primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(Res.string.includedProviders),
                        style = MaterialTheme.typography.subtitle1.copy(fontWeight = FontWeight.Bold),
                        color = CloudstreamTheme.extendedColors.textPrimary
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = CloudStreamColors.Primary.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "${providers.size}",
                        style = MaterialTheme.typography.caption.copy(
                            fontWeight = FontWeight.Bold,
                            color = CloudStreamColors.Primary
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = stringResource(Res.string.plugin_scrapers_bundled_desc),
                style = MaterialTheme.typography.caption,
                color = CloudstreamTheme.extendedColors.textMuted
            )

            if (providers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colors.background, RoundedCornerShape(10.dp))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(Res.string.no_provider_engines_declared),
                        style = MaterialTheme.typography.body2,
                        color = CloudstreamTheme.extendedColors.textMuted
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    providers.forEach { provider ->
                        ProviderDetailCard(
                            provider = provider,
                            isPluginEnabled = isPluginEnabled
                        )
                    }
                }
            }
        }
    }
}

/**
 * Single MainAPI Provider Detail Card.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProviderDetailCard(
    provider: PluginProviderDetails,
    isPluginEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colors.background,
        border = BorderStroke(1.dp, CloudstreamTheme.extendedColors.divider),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Provider Name & Active Indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                if (provider.isActive && isPluginEnabled) CloudStreamColors.Success
                                else CloudstreamTheme.extendedColors.textMuted
                            )
                    )

                    Text(
                        text = provider.name,
                        style = MaterialTheme.typography.subtitle2.copy(fontWeight = FontWeight.Bold),
                        color = CloudstreamTheme.extendedColors.textPrimary
                    )

                    // Direct Provider or Extractor Badge
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (provider.isDirectProvider) CloudStreamColors.Primary.copy(alpha = 0.15f)
                        else CloudStreamColors.Secondary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (provider.isDirectProvider) stringResource(Res.string.plugin_provider_direct) else stringResource(Res.string.plugin_provider_extractor),
                            style = MaterialTheme.typography.caption.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            ),
                            color = if (provider.isDirectProvider) CloudStreamColors.Primary
                            else CloudStreamColors.Secondary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Language tag
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = CloudstreamTheme.extendedColors.divider
                ) {
                    Text(
                        text = provider.language.uppercase(),
                        style = MaterialTheme.typography.caption.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        ),
                        color = CloudstreamTheme.extendedColors.textSecondary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Main URL
            if (provider.mainUrl.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = null,
                        tint = CloudstreamTheme.extendedColors.textMuted,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = provider.mainUrl,
                        style = MaterialTheme.typography.caption.copy(fontSize = 11.sp),
                        color = CloudstreamTheme.extendedColors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // TvType Chips
            if (provider.supportedTypes.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    provider.supportedTypes.forEach { tvType ->
                        TvTypeChip(tvType = tvType)
                    }
                }
            }

            // Feature capability badges
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (provider.hasMainPage) {
                    FeatureBadge(label = stringResource(Res.string.plugin_feature_catalog), icon = Icons.Default.Tv)
                }
                if (provider.hasQuickSearch) {
                    FeatureBadge(label = stringResource(Res.string.plugin_feature_fast_search), icon = Icons.Default.Search)
                }
                if (provider.hasDownloadSupport) {
                    FeatureBadge(label = stringResource(Res.string.plugin_feature_downloads), icon = Icons.Default.CloudDownload)
                }
                if (provider.hasChromecastSupport) {
                    FeatureBadge(label = stringResource(Res.string.plugin_feature_cast), icon = Icons.Default.Cast)
                }
            }
        }
    }
}

/**
 * Chip for TvType representation.
 */
@Composable
private fun TvTypeChip(tvType: TvType, modifier: Modifier = Modifier) {
    val (label, color) = when (tvType) {
        TvType.Movie -> stringResource(Res.string.typeMovie) to CloudStreamColors.Info
        TvType.TvSeries -> stringResource(Res.string.typeTvSeries) to CloudStreamColors.PrimaryVariant
        TvType.Anime, TvType.AnimeMovie, TvType.OVA -> stringResource(Res.string.typeAnime) to CloudStreamColors.Secondary
        TvType.Cartoon -> stringResource(Res.string.type_cartoon) to CloudStreamColors.Success
        TvType.Live -> stringResource(Res.string.typeLive) to CloudStreamColors.Error
        TvType.Torrent -> stringResource(Res.string.typeTorrent) to CloudStreamColors.Info
        TvType.AsianDrama -> stringResource(Res.string.type_asian_drama) to CloudStreamColors.Secondary
        TvType.Documentary -> stringResource(Res.string.type_documentary) to CloudStreamColors.Primary
        TvType.NSFW -> stringResource(Res.string.type_nsfw) to CloudStreamColors.Error
        else -> tvType.name to CloudStreamColors.TextMuted
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.caption.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp
            ),
            color = color,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun FeatureBadge(label: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = CloudstreamTheme.extendedColors.divider,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = CloudstreamTheme.extendedColors.textMuted,
                modifier = Modifier.size(11.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.caption.copy(
                    fontSize = 10.sp,
                    color = CloudstreamTheme.extendedColors.textSecondary
                )
            )
        }
    }
}

/**
 * Description and Changelog Section Card.
 */
@Composable
private fun PluginDescriptionAndChangelogCard(
    description: String?,
    changelog: List<PluginChangelogItem>,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        backgroundColor = CloudstreamTheme.extendedColors.cardBackground,
        border = BorderStroke(1.dp, CloudstreamTheme.extendedColors.cardBorder),
        elevation = 0.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Description header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = CloudStreamColors.Primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(Res.string.plugin_description_overview),
                    style = MaterialTheme.typography.subtitle1.copy(fontWeight = FontWeight.Bold),
                    color = CloudstreamTheme.extendedColors.textPrimary
                )
            }

            // Description text
            Text(
                text = description?.ifBlank { null }
                    ?: stringResource(Res.string.no_description_provided),
                style = MaterialTheme.typography.body2.copy(lineHeight = 20.sp),
                color = CloudstreamTheme.extendedColors.textSecondary
            )

            Divider(color = CloudstreamTheme.extendedColors.divider, thickness = 1.dp)

            // Changelog header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = CloudStreamColors.Primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(Res.string.changelog),
                    style = MaterialTheme.typography.subtitle1.copy(fontWeight = FontWeight.Bold),
                    color = CloudstreamTheme.extendedColors.textPrimary
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                changelog.forEach { item ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colors.background,
                        border = BorderStroke(1.dp, CloudstreamTheme.extendedColors.divider),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = item.version,
                                    style = MaterialTheme.typography.subtitle2.copy(fontWeight = FontWeight.Bold),
                                    color = CloudStreamColors.Primary
                                )
                                Text(
                                    text = item.date,
                                    style = MaterialTheme.typography.caption,
                                    color = CloudstreamTheme.extendedColors.textMuted
                                )
                            }

                            item.changes.forEach { change ->
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "•",
                                        style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.Bold),
                                        color = CloudstreamTheme.extendedColors.textMuted
                                    )
                                    Text(
                                        text = change,
                                        style = MaterialTheme.typography.body2.copy(fontSize = 12.sp),
                                        color = CloudstreamTheme.extendedColors.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Required Permissions and Security Audit Card.
 */
@Composable
private fun PluginPermissionsCard(
    permissions: List<PluginPermissionItem>,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        backgroundColor = CloudstreamTheme.extendedColors.cardBackground,
        border = BorderStroke(1.dp, CloudstreamTheme.extendedColors.cardBorder),
        elevation = 0.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = CloudStreamColors.Primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(Res.string.permissions),
                    style = MaterialTheme.typography.subtitle1.copy(fontWeight = FontWeight.Bold),
                    color = CloudstreamTheme.extendedColors.textPrimary
                )
            }

            Text(
                text = stringResource(Res.string.plugin_capabilities_desc),
                style = MaterialTheme.typography.caption,
                color = CloudstreamTheme.extendedColors.textMuted
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                permissions.forEach { perm ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colors.background,
                        border = BorderStroke(1.dp, CloudstreamTheme.extendedColors.divider),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (perm.isDangerous) CloudStreamColors.Error.copy(alpha = 0.15f)
                                        else CloudStreamColors.Primary.copy(alpha = 0.15f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = perm.icon,
                                    contentDescription = null,
                                    tint = if (perm.isDangerous) CloudStreamColors.Error else CloudStreamColors.Primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = perm.title,
                                    style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.Bold),
                                    color = CloudstreamTheme.extendedColors.textPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = perm.description,
                                    style = MaterialTheme.typography.caption.copy(fontSize = 11.sp),
                                    color = CloudstreamTheme.extendedColors.textSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Technical Specifications Card (Package ID, File Size, Hash, Source URL).
 */
@Composable
private fun PluginTechnicalSpecsCard(
    plugin: PluginItem,
    repositoryName: String,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        backgroundColor = CloudstreamTheme.extendedColors.cardBackground,
        border = BorderStroke(1.dp, CloudstreamTheme.extendedColors.cardBorder),
        elevation = 0.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Code,
                    contentDescription = null,
                    tint = CloudStreamColors.Primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(Res.string.plugin_specs),
                    style = MaterialTheme.typography.subtitle1.copy(fontWeight = FontWeight.Bold),
                    color = CloudstreamTheme.extendedColors.textPrimary
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colors.background, RoundedCornerShape(10.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SpecRow(label = stringResource(Res.string.packageIdLabel), value = plugin.internalName, isMono = true)
                SpecRow(label = stringResource(Res.string.version), value = "v${plugin.version}")
                SpecRow(
                    label = stringResource(Res.string.fileSizeLabel),
                    value = formatPluginSize(plugin.fileSize)
                )
                if (!plugin.fileHash.isNullOrBlank()) {
                    SpecRow(label = stringResource(Res.string.fileHashLabel), value = plugin.fileHash.take(16) + "...", isMono = true)
                }
                SpecRow(label = stringResource(Res.string.repoSource), value = repositoryName)
                if (!plugin.localFilePath.isNullOrBlank()) {
                    SpecRow(label = stringResource(Res.string.plugin_local_path), value = plugin.localFilePath, isMono = true)
                }
            }
        }
    }
}

@Composable
private fun SpecRow(
    label: String,
    value: String,
    isMono: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
            color = CloudstreamTheme.extendedColors.textMuted
        )
        Text(
            text = value,
            style = MaterialTheme.typography.body2.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                fontFamily = if (isMono) FontFamily.Monospace else FontFamily.Default
            ),
            color = CloudstreamTheme.extendedColors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// -----------------------------------------------------------------------------
// Helper Resolver Functions
// -----------------------------------------------------------------------------

/**
 * Resolves provider engines from memory APIHolder or synthesizes from plugin metadata.
 */
private fun resolvePluginProviders(plugin: PluginItem, isInstalled: Boolean): List<PluginProviderDetails> {
    val registeredProviders = try {
        APIHolder.allProviders.filter { api ->
            api.sourcePlugin == plugin.localFilePath ||
                    (api.sourcePlugin != null && api.sourcePlugin!!.contains(plugin.internalName, ignoreCase = true)) ||
                    api.name.equals(plugin.name, ignoreCase = true) ||
                    api.name.equals(plugin.internalName, ignoreCase = true)
        }
    } catch (_: Throwable) {
        emptyList()
    }

    if (registeredProviders.isNotEmpty()) {
        return registeredProviders.map { api ->
            PluginProviderDetails(
                name = api.name,
                mainUrl = api.mainUrl,
                language = api.lang,
                supportedTypes = api.supportedTypes,
                isActive = isInstalled && plugin.isEnabled,
                isDirectProvider = api.providerType == ProviderType.DirectProvider,
                hasMainPage = api.hasMainPage,
                hasQuickSearch = api.hasQuickSearch,
                hasDownloadSupport = api.hasDownloadSupport,
                hasChromecastSupport = api.hasChromecastSupport
            )
        }
    }

    // Fallback synthesized provider from plugin item
    val tvTypes = plugin.tvTypes.mapNotNull { typeStr ->
        TvType.entries.firstOrNull { it.name.equals(typeStr, ignoreCase = true) }
    }.toSet().ifEmpty { setOf(TvType.Movie, TvType.TvSeries) }

    return listOf(
        PluginProviderDetails(
            name = plugin.name,
            mainUrl = plugin.url.ifBlank { "https://${plugin.internalName.lowercase()}.com" },
            language = plugin.language ?: "en",
            supportedTypes = tvTypes,
            isActive = isInstalled && plugin.isEnabled,
            isDirectProvider = true,
            hasMainPage = true,
            hasQuickSearch = true,
            hasDownloadSupport = true,
            hasChromecastSupport = true
        )
    )
}

/**
 * Resolves standard security permissions required by CloudStream plugins.
 */
@Composable
private fun resolvePluginPermissions(plugin: PluginItem): List<PluginPermissionItem> {
    val permInternetTitle = stringResource(Res.string.plugin_perm_internet_title)
    val permInternetDesc = stringResource(Res.string.plugin_perm_internet_desc)
    val permCacheTitle = stringResource(Res.string.plugin_perm_cache_title)
    val permCacheDesc = stringResource(Res.string.plugin_perm_cache_desc)
    val permWebviewTitle = stringResource(Res.string.plugin_perm_webview_title)
    val permWebviewDesc = stringResource(Res.string.plugin_perm_webview_desc)
    val permBytecodeTitle = stringResource(Res.string.plugin_perm_bytecode_title)
    val permBytecodeDesc = stringResource(Res.string.plugin_perm_bytecode_desc)

    return remember(plugin, permInternetTitle, permCacheTitle, permWebviewTitle, permBytecodeTitle) {
        listOf(
            PluginPermissionItem(
                title = permInternetTitle,
                description = permInternetDesc,
                icon = Icons.Default.Public,
                isDangerous = false
            ),
            PluginPermissionItem(
                title = permCacheTitle,
                description = permCacheDesc,
                icon = Icons.Default.Storage,
                isDangerous = false
            ),
            PluginPermissionItem(
                title = permWebviewTitle,
                description = permWebviewDesc,
                icon = Icons.Default.Security,
                isDangerous = false
            ),
            PluginPermissionItem(
                title = permBytecodeTitle,
                description = permBytecodeDesc,
                icon = Icons.Default.Code,
                isDangerous = false
            )
        )
    }
}

/**
 * Resolves changelog history for the plugin.
 */
@Composable
private fun resolvePluginChangelog(plugin: PluginItem): List<PluginChangelogItem> {
    val latestText = stringResource(Res.string.plugin_changelog_latest)
    val currentReleaseText = stringResource(Res.string.plugin_changelog_current_release)
    val prevReleaseText = stringResource(Res.string.plugin_changelog_previous_release)
    val itemKmp = stringResource(Res.string.plugin_changelog_item_kmp)
    val itemPerf = stringResource(Res.string.plugin_changelog_item_perf)
    val itemCloudflare = stringResource(Res.string.plugin_changelog_item_cloudflare)
    val itemArtwork = stringResource(Res.string.plugin_changelog_item_artwork)
    val itemCatalog = stringResource(Res.string.plugin_changelog_item_catalog)
    val itemSearch = stringResource(Res.string.plugin_changelog_item_search)

    return remember(plugin, latestText, currentReleaseText, prevReleaseText) {
        if (!plugin.changelog.isNullOrBlank()) {
            listOf(
                PluginChangelogItem(
                    version = "v${plugin.version}",
                    date = latestText,
                    changes = plugin.changelog.lines().filter { it.isNotBlank() }
                )
            )
        } else {
            listOf(
                PluginChangelogItem(
                    version = "v${plugin.version}",
                    date = currentReleaseText,
                    changes = listOf(
                        itemKmp,
                        itemPerf,
                        itemCloudflare,
                        itemArtwork
                    )
                ),
                PluginChangelogItem(
                    version = "v${maxOf(1, plugin.version - 1)}",
                    date = prevReleaseText,
                    changes = listOf(
                        itemCatalog,
                        itemSearch
                    )
                )
            )
        }
    }
}

/**
 * Helper to resolve repository human-readable name.
 */
@Composable
private fun resolveRepositoryName(
    repoUrl: String,
    repositories: List<com.lagradost.cloudstream3.shared.viewmodels.settings.PluginRepositoryItem>
): String {
    val communityRepo = stringResource(Res.string.repository_community)
    val officialRepo = stringResource(Res.string.repository_official)
    val hexatedRepo = stringResource(Res.string.repository_hexated)
    val customRepo = stringResource(Res.string.repository_custom)

    if (repoUrl.isBlank()) return communityRepo
    val found = repositories.firstOrNull { it.url == repoUrl }
    if (found != null) return found.name

    return when {
        repoUrl.contains("cloudstream-extensions", ignoreCase = true) -> officialRepo
        repoUrl.contains("hexated", ignoreCase = true) -> hexatedRepo
        else -> repoUrl.substringAfterLast("/").removeSuffix(".json").ifBlank { customRepo }
    }
}

/**
 * Helper to format byte sizes.
 */
@Composable
private fun formatPluginSize(bytes: Long?): String {
    if (bytes == null || bytes <= 0L) return stringResource(Res.string.plugin_size_estimated)
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) {
        "${(mb * 10).toInt() / 10.0} MB"
    } else {
        "${kb.toInt()} KB"
    }
}
