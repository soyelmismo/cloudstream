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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Storage
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cloudstream.shared_ui.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import com.lagradost.cloudstream3.shared.ui.components.AsyncImage
import com.lagradost.cloudstream3.shared.ui.components.designsystem.ActionDialog
import com.lagradost.cloudstream3.shared.ui.components.designsystem.BodyMutedText
import com.lagradost.cloudstream3.shared.ui.components.designsystem.CloudStreamTextField
import com.lagradost.cloudstream3.shared.ui.components.designsystem.ConfirmDeleteDialog
import com.lagradost.cloudstream3.shared.ui.components.designsystem.PrimaryButton
import com.lagradost.cloudstream3.shared.ui.components.designsystem.SecondaryButton
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors
import com.lagradost.cloudstream3.shared.ui.components.designsystem.CloudStreamFilterChip
import com.lagradost.cloudstream3.shared.viewmodels.settings.PluginItem
import com.lagradost.cloudstream3.shared.viewmodels.settings.PluginOperationState
import com.lagradost.cloudstream3.shared.viewmodels.settings.PluginRepositoryItem
import com.lagradost.cloudstream3.shared.viewmodels.settings.PluginsSettingsEvent
import com.lagradost.cloudstream3.shared.viewmodels.settings.PluginsSettingsViewModel
import com.lagradost.cloudstream3.utils.UiText
import com.lagradost.cloudstream3.utils.asString

private const val COMMUNITY_REPOSITORIES_URL = "https://recloudstream.github.io/csdocs/repositories/"

/**
 * Main Plugins & Extensions Management Screen with 3-Level Hierarchical Navigation:
 * - Level 1: Repositories List (when selectedRepositoryUrl == null)
 * - Level 2: Repository Plugins / Providers List (when selectedRepositoryUrl != null)
 * - Level 3: Plugin Details Modal Dialog (PluginDetailsDialog)
 */
@Composable
fun PluginsScreen(
    viewModel: PluginsSettingsViewModel,
    onNavigateToPluginDetails: ((PluginItem) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()

    var showAddRepoDialog by remember { mutableStateOf(false) }
    var repoToDelete by remember { mutableStateOf<PluginRepositoryItem?>(null) }
    var selectedPluginForDetails by remember { mutableStateOf<PluginItem?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
    ) {
        // Operation Status Banner (Downloading, Installing, Success, Error)
        OperationStatusBanner(
            operationState = state.operationState,
            error = state.error,
            onDismiss = { viewModel.handleEvent(PluginsSettingsEvent.ClearError) }
        )

        // Hierarchical Level Switching: Level 1 (Repositories) vs Level 2 (Repository Plugins)
        Box(modifier = Modifier.weight(1f)) {
            if (state.selectedRepositoryUrl == null) {
                // LEVEL 1: Repositories List
                RepositoriesLevelView(
                    repositories = state.repositories,
                    installedPlugins = state.installedPlugins,
                    isLoading = state.isLoading,
                    onEvent = viewModel::handleEvent,
                    onAddRepoClick = { showAddRepoDialog = true },
                    onDeleteRepoClick = { repoToDelete = it },
                    onRepoClick = { repoUrl ->
                        viewModel.handleEvent(PluginsSettingsEvent.FilterByRepository(repoUrl))
                    }
                )
            } else {
                // LEVEL 2: Plugins of the Selected Repository
                RepositoryPluginsLevelView(
                    selectedRepoUrl = state.selectedRepositoryUrl!!,
                    repositories = state.repositories,
                    plugins = state.filteredAvailablePlugins,
                    searchQuery = state.searchQuery,
                    selectedLanguage = state.selectedLanguage,
                    selectedTvType = state.selectedTvType,
                    operationState = state.operationState,
                    isLoading = state.isLoading,
                    onEvent = viewModel::handleEvent,
                    onBackClick = { viewModel.handleEvent(PluginsSettingsEvent.FilterByRepository(null)) },
                    onPluginClick = { plugin ->
                        selectedPluginForDetails = plugin
                        onNavigateToPluginDetails?.invoke(plugin)
                    }
                )
            }
        }
    }

    // Modal Dialog: Add Repository
    if (showAddRepoDialog) {
        AddRepositoryDialog(
            viewModel = viewModel,
            onDismiss = { showAddRepoDialog = false }
        )
    }

    // Confirmation Dialog: Delete Repository
    if (repoToDelete != null) {
        ConfirmDeleteDialog(
            onConfirm = {
                val url = repoToDelete?.url
                if (url != null) {
                    viewModel.handleEvent(PluginsSettingsEvent.RemoveRepository(url))
                }
                repoToDelete = null
            },
            onDismiss = { repoToDelete = null },
            titleRes = Res.string.delete_repository,
            messageRes = Res.string.delete_repository_plugins,
            confirmTextRes = Res.string.delete
        )
    }

    // LEVEL 3: Plugin Details Interactive Modal
    if (selectedPluginForDetails != null) {
        PluginDetailsDialog(
            plugin = selectedPluginForDetails!!,
            viewModel = viewModel,
            onDismiss = { selectedPluginForDetails = null }
        )
    }
}

// =========================================================================================
// NIVEL 1: Repositories List View
// =========================================================================================

@Composable
fun RepositoriesLevelView(
    repositories: List<PluginRepositoryItem>,
    installedPlugins: List<PluginItem>,
    isLoading: Boolean,
    onEvent: (PluginsSettingsEvent) -> Unit,
    onAddRepoClick: () -> Unit,
    onDeleteRepoClick: (PluginRepositoryItem) -> Unit,
    onRepoClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current

    Column(modifier = modifier.fillMaxSize()) {
        // Level 1 Top Bar Header
        RepositoriesHeader(
            repoCount = repositories.size,
            onRefresh = { onEvent(PluginsSettingsEvent.Reload) },
            onAddRepoClick = onAddRepoClick,
            onViewPublicListClick = { uriHandler.openUri(COMMUNITY_REPOSITORIES_URL) }
        )

        Divider(color = CloudStreamColors.Divider)

        // Main List Content
        if (isLoading && repositories.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colors.primary)
            }
        } else if (repositories.isEmpty()) {
            EmptyRepositoriesState(
                onAddRepoClick = onAddRepoClick,
                onViewPublicListClick = { uriHandler.openUri(COMMUNITY_REPOSITORIES_URL) }
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    count = repositories.size,
                    key = { index -> repositories[index].url }
                ) { index ->
                    val repo = repositories[index]
                    val installedCount = installedPlugins.count { it.repositoryUrl == repo.url }

                    RepositoryCard(
                        repository = repo,
                        installedCount = installedCount,
                        onRepoClick = { onRepoClick(repo.url) },
                        onReload = { onEvent(PluginsSettingsEvent.Reload) },
                        onRemove = { onDeleteRepoClick(repo) }
                    )
                }
            }
        }
    }
}

@Composable
fun RepositoriesHeader(
    repoCount: Int,
    onRefresh: () -> Unit,
    onAddRepoClick: () -> Unit,
    onViewPublicListClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(CloudStreamColors.SurfaceVariant)
    ) {
        val isWideScreen = maxWidth >= 600.dp

        if (isWideScreen) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colors.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Extension,
                            contentDescription = null,
                            tint = MaterialTheme.colors.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column {
                        Text(
                            text = stringResource(Res.string.extensions),
                            style = MaterialTheme.typography.h6.copy(fontWeight = FontWeight.Bold),
                            color = CloudStreamColors.TextPrimary
                        )
                        Text(
                            text = stringResource(Res.string.repositories_configured_format, repoCount),
                            style = MaterialTheme.typography.caption,
                            color = CloudStreamColors.TextSecondary
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SecondaryButton(
                        onClick = onViewPublicListClick,
                        icon = Icons.Default.Public,
                        text = stringResource(Res.string.view_public_repositories_button_short),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(42.dp)
                    )

                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(CloudStreamColors.Divider)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(Res.string.refresh),
                            tint = CloudStreamColors.TextPrimary
                        )
                    }

                    PrimaryButton(
                        onClick = onAddRepoClick,
                        icon = Icons.Default.Add,
                        text = stringResource(Res.string.add_repo),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(42.dp)
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colors.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Extension,
                                contentDescription = null,
                                tint = MaterialTheme.colors.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column {
                            Text(
                                text = stringResource(Res.string.extensions),
                                style = MaterialTheme.typography.subtitle1.copy(fontWeight = FontWeight.Bold),
                                color = CloudStreamColors.TextPrimary
                            )
                            Text(
                                text = stringResource(Res.string.repositories_configured_format, repoCount),
                                style = MaterialTheme.typography.caption,
                                color = CloudStreamColors.TextSecondary
                            )
                        }
                    }

                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(CloudStreamColors.Divider)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(Res.string.refresh),
                            tint = CloudStreamColors.TextPrimary
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SecondaryButton(
                        onClick = onViewPublicListClick,
                        icon = Icons.Default.Public,
                        text = stringResource(Res.string.view_public_repositories_button_short),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                    )

                    PrimaryButton(
                        onClick = onAddRepoClick,
                        icon = Icons.Default.Add,
                        text = stringResource(Res.string.add_repo),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun RepositoryCard(
    repository: PluginRepositoryItem,
    installedCount: Int,
    onRepoClick: () -> Unit,
    onReload: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val cardBg by animateColorAsState(
        targetValue = if (isHovered) CloudStreamColors.SurfaceElevated
        else CloudStreamColors.SurfaceVariant,
        animationSpec = tween(150)
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onRepoClick
            ),
        shape = RoundedCornerShape(12.dp),
        backgroundColor = cardBg,
        border = BorderStroke(1.dp, CloudStreamColors.Divider),
        elevation = if (isHovered) 3.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar / Icon
                if (!repository.iconUrl.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(CloudStreamColors.Divider),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            url = repository.iconUrl,
                            contentDescription = repository.name,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = if (!repository.isRemovable) {
                                        listOf(MaterialTheme.colors.primary, MaterialTheme.colors.primaryVariant)
                                    } else {
                                        listOf(MaterialTheme.colors.secondary, MaterialTheme.colors.secondary.copy(alpha = 0.7f))
                                    }
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (!repository.isRemovable) Icons.Default.Public else Icons.Default.Source,
                            contentDescription = null,
                            tint = MaterialTheme.colors.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = repository.name,
                            style = MaterialTheme.typography.subtitle1.copy(fontWeight = FontWeight.Bold),
                            color = CloudStreamColors.TextPrimary
                        )

                        if (!repository.isRemovable) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colors.primary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = stringResource(Res.string.official),
                                    style = MaterialTheme.typography.caption.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    ),
                                    color = MaterialTheme.colors.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = repository.url,
                        style = MaterialTheme.typography.caption,
                        color = CloudStreamColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (!repository.description.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = repository.description,
                            style = MaterialTheme.typography.caption.copy(fontSize = 11.sp),
                            color = CloudStreamColors.TextMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Stats Badges
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = CloudStreamColors.Divider
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Extension,
                                    contentDescription = null,
                                    tint = CloudStreamColors.TextSecondary,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${repository.pluginCount} ${stringResource(Res.string.plugin)} · $installedCount ${stringResource(Res.string.tabInstalled)}",
                                    style = MaterialTheme.typography.caption.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = CloudStreamColors.TextSecondary
                                    )
                                )
                            }
                        }

                        Text(
                            text = "${stringResource(Res.string.lastSynced)}: ${PluginsSettingsViewModel.formatSyncTime(repository.lastSyncTime)}",
                            style = MaterialTheme.typography.caption.copy(
                                fontSize = 10.sp,
                                color = CloudStreamColors.TextMuted
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Action Buttons on Card
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onReload,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(Res.string.refresh),
                        tint = CloudStreamColors.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (repository.isRemovable) {
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(Res.string.delete_repo),
                            tint = MaterialTheme.colors.error.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onRepoClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colors.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyRepositoriesState(
    onAddRepoClick: () -> Unit,
    onViewPublicListClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            backgroundColor = CloudStreamColors.SurfaceVariant,
            border = BorderStroke(1.dp, CloudStreamColors.Divider),
            elevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colors.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        tint = MaterialTheme.colors.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(Res.string.extensions),
                    style = MaterialTheme.typography.h6.copy(fontWeight = FontWeight.Bold),
                    color = CloudStreamColors.TextPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                BodyMutedText(
                    text = stringResource(Res.string.blank_repo_message),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SecondaryButton(
                        onClick = onViewPublicListClick,
                        icon = Icons.Default.Public,
                        text = stringResource(Res.string.view_public_repositories_button)
                    )

                    PrimaryButton(
                        onClick = onAddRepoClick,
                        icon = Icons.Default.Add,
                        text = stringResource(Res.string.addRepository)
                    )
                }
            }
        }
    }
}

// =========================================================================================
// NIVEL 2: Selected Repository Plugins View
// =========================================================================================

@Composable
fun RepositoryPluginsLevelView(
    selectedRepoUrl: String,
    repositories: List<PluginRepositoryItem>,
    plugins: List<PluginItem>,
    searchQuery: String,
    selectedLanguage: String?,
    selectedTvType: String?,
    operationState: PluginOperationState,
    isLoading: Boolean,
    onEvent: (PluginsSettingsEvent) -> Unit,
    onBackClick: () -> Unit,
    onPluginClick: (PluginItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentRepo = repositories.firstOrNull { it.url == selectedRepoUrl }
    val repoName = currentRepo?.name ?: selectedRepoUrl
    val distinctLanguages = remember(plugins) {
        plugins.mapNotNull { it.language?.takeIf { lang -> lang.isNotBlank() } }.distinct()
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Dedicated Level 2 Top Bar
        RepositoryDetailTopBar(
            repoName = repoName,
            repoUrl = selectedRepoUrl,
            pluginCount = plugins.size,
            onBackClick = onBackClick,
            onBatchDownloadClick = {
                onEvent(PluginsSettingsEvent.InstallAllPlugins(selectedRepoUrl))
            },
            onRefreshClick = {
                onEvent(PluginsSettingsEvent.Reload)
            }
        )

        // Search Bar and Filter Chips
        RepositoryPluginsFilterBar(
            query = searchQuery,
            onQueryChange = { onEvent(PluginsSettingsEvent.Search(it)) },
            selectedTvType = selectedTvType,
            onTvTypeSelected = { onEvent(PluginsSettingsEvent.FilterByTvType(it)) },
            selectedLanguage = selectedLanguage,
            onLanguageSelected = { onEvent(PluginsSettingsEvent.FilterByLanguage(it)) },
            availableLanguages = distinctLanguages
        )

        Divider(color = CloudStreamColors.Divider)

        // Plugins List
        if (isLoading && plugins.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colors.primary)
            }
        } else if (plugins.isEmpty()) {
            EmptyPluginState(
                message = stringResource(Res.string.no_available_plugins_match),
                subtitle = stringResource(Res.string.no_available_plugins_match_desc)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    count = plugins.size,
                    key = { index -> plugins[index].internalName }
                ) { index ->
                    val plugin = plugins[index]
                    PluginCard(
                        plugin = plugin,
                        operationState = operationState,
                        isInstalled = plugin.isInstalled,
                        onClick = { onPluginClick(plugin) },
                        onInstall = { onEvent(PluginsSettingsEvent.InstallPlugin(plugin)) },
                        onUninstall = { onEvent(PluginsSettingsEvent.UninstallPlugin(plugin.internalName)) },
                        onToggleEnabled = { enabled ->
                            onEvent(PluginsSettingsEvent.InstallPlugin(plugin.copy(isEnabled = enabled)))
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun RepositoryDetailTopBar(
    repoName: String,
    repoUrl: String,
    pluginCount: Int,
    onBackClick: () -> Unit,
    onBatchDownloadClick: () -> Unit,
    onRefreshClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = CloudStreamColors.SurfaceVariant,
        border = BorderStroke(1.dp, CloudStreamColors.Divider),
        elevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val isWideScreen = maxWidth >= 600.dp

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = !isWideScreen)
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(CloudStreamColors.SurfaceElevated)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.action_back),
                            tint = CloudStreamColors.TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = repoName,
                            style = MaterialTheme.typography.subtitle1.copy(fontWeight = FontWeight.Bold),
                            color = CloudStreamColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "$pluginCount ${stringResource(Res.string.plugin)} · $repoUrl",
                            style = MaterialTheme.typography.caption,
                            color = CloudStreamColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PrimaryButton(
                        onClick = onBatchDownloadClick,
                        icon = Icons.Default.Download,
                        text = stringResource(Res.string.batch_download),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(40.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    )

                    IconButton(
                        onClick = onRefreshClick,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(CloudStreamColors.Divider)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(Res.string.refresh),
                            tint = CloudStreamColors.TextPrimary
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RepositoryPluginsFilterBar(
    query: String,
    onQueryChange: (String) -> Unit,
    selectedTvType: String?,
    onTvTypeSelected: (String?) -> Unit,
    selectedLanguage: String?,
    onLanguageSelected: (String?) -> Unit,
    availableLanguages: List<String>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CloudStreamColors.SurfaceVariant)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CloudStreamTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = stringResource(Res.string.search_plugins_hint),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(Res.string.search_plugins_hint),
                    tint = CloudStreamColors.TextSecondary
                )
            },
            trailingIcon = if (query.isNotBlank()) {
                {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(Res.string.filter_clear),
                            tint = CloudStreamColors.TextSecondary
                        )
                    }
                }
            } else null,
            singleLine = true
        )

        // Filter Chips (TV Types + Languages)
        val tvTypes = listOf("Movie", "TvSeries", "Anime", "Cartoon", "Live", "Torrent")
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CloudStreamFilterChip(
                label = stringResource(Res.string.all_types),
                isSelected = selectedTvType == null,
                onClick = { onTvTypeSelected(null) }
            )

            tvTypes.forEach { type ->
                val typeLabel = when (type) {
                    "Movie" -> stringResource(Res.string.typeMovie)
                    "TvSeries" -> stringResource(Res.string.typeTvSeries)
                    "Anime" -> stringResource(Res.string.typeAnime)
                    "Cartoon" -> stringResource(Res.string.type_cartoon)
                    "Live" -> stringResource(Res.string.typeLive)
                    "Torrent" -> stringResource(Res.string.typeTorrent)
                    else -> type
                }
                CloudStreamFilterChip(
                    label = typeLabel,
                    isSelected = selectedTvType.equals(type, ignoreCase = true),
                    onClick = {
                        if (selectedTvType.equals(type, ignoreCase = true)) {
                            onTvTypeSelected(null)
                        } else {
                            onTvTypeSelected(type)
                        }
                    }
                )
            }

            if (availableLanguages.isNotEmpty()) {
                availableLanguages.forEach { lang ->
                    CloudStreamFilterChip(
                        label = lang.uppercase(),
                        isSelected = selectedLanguage.equals(lang, ignoreCase = true),
                        onClick = {
                            if (selectedLanguage.equals(lang, ignoreCase = true)) {
                                onLanguageSelected(null)
                            } else {
                                onLanguageSelected(lang)
                            }
                        }
                    )
                }
            }
        }
    }
}



/**
 * Individual Plugin Card with status badges, version, clean authors formatting, and interactive actions.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PluginCard(
    plugin: PluginItem,
    operationState: PluginOperationState,
    isInstalled: Boolean,
    onClick: (() -> Unit)? = null,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val isThisDownloading = operationState is PluginOperationState.Downloading &&
            operationState.pluginName.equals(plugin.name, ignoreCase = true)
    val isThisInstalling = operationState is PluginOperationState.Installing &&
            operationState.pluginName.equals(plugin.name, ignoreCase = true)
    val isThisUninstalling = operationState is PluginOperationState.Uninstalling &&
            operationState.pluginName.equals(plugin.internalName, ignoreCase = true)

    val validAuthors = plugin.authors.filter { it.isNotBlank() }

    val cardBg by animateColorAsState(
        targetValue = if (isHovered) CloudStreamColors.SurfaceElevated
        else CloudStreamColors.SurfaceVariant,
        animationSpec = tween(150)
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onClick?.invoke() }
            ),
        shape = RoundedCornerShape(12.dp),
        backgroundColor = cardBg,
        border = BorderStroke(1.dp, CloudStreamColors.Divider),
        elevation = if (isHovered) 3.dp else 0.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Plugin Avatar & Info
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!plugin.iconUrl.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(CloudStreamColors.Divider),
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
                                .size(46.dp)
                                .clip(RoundedCornerShape(12.dp))
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
                                style = MaterialTheme.typography.h6.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colors.onPrimary,
                                    fontSize = 16.sp
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = plugin.name,
                                style = MaterialTheme.typography.subtitle1.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                ),
                                color = CloudStreamColors.TextPrimary
                            )

                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = CloudStreamColors.Divider
                            ) {
                                Text(
                                    text = "v${plugin.version}",
                                    style = MaterialTheme.typography.caption.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    ),
                                    color = CloudStreamColors.TextSecondary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            PluginStatusBadge(status = plugin.status)
                        }

                        // Authors & Language (Clean formatting - Zero "por %s" bug)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (validAuthors.isNotEmpty()) {
                                Text(
                                    text = stringResource(Res.string.plugin_by_author_format, validAuthors.joinToString(", ")),
                                    style = MaterialTheme.typography.caption,
                                    color = CloudStreamColors.TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (!plugin.language.isNullOrBlank()) {
                                Text(
                                    text = if (validAuthors.isNotEmpty()) "• ${plugin.language.uppercase()}" else plugin.language.uppercase(),
                                    style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colors.primary
                                )
                            }
                        }
                    }
                }

                // Quick Action Controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isThisDownloading || isThisInstalling || isThisUninstalling) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colors.primary,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(28.dp)
                        )
                    } else if (isInstalled) {
                        Switch(
                            checked = plugin.isEnabled,
                            onCheckedChange = onToggleEnabled,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colors.primary,
                                checkedTrackColor = MaterialTheme.colors.primary.copy(alpha = 0.5f)
                            )
                        )

                        IconButton(
                            onClick = onUninstall,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(Res.string.uninstall),
                                tint = MaterialTheme.colors.error.copy(alpha = 0.8f)
                            )
                        }
                    } else {
                        PrimaryButton(
                            onClick = onInstall,
                            icon = Icons.Default.Download,
                            text = stringResource(Res.string.install),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(36.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Description
            if (!plugin.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = plugin.description,
                    style = MaterialTheme.typography.body2.copy(fontSize = 13.sp),
                    color = CloudStreamColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // TV Types Tags
            if (plugin.tvTypes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    plugin.tvTypes.forEach { tvType ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = CloudStreamColors.SurfaceElevated,
                            border = BorderStroke(1.dp, CloudStreamColors.Divider)
                        ) {
                            Text(
                                text = tvType,
                                style = MaterialTheme.typography.caption.copy(fontSize = 10.sp),
                                color = CloudStreamColors.TextSecondary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Progress bar if downloading
            if (operationState is PluginOperationState.Downloading && operationState.pluginName.equals(plugin.name, ignoreCase = true)) {
                val progress = operationState.progress
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = progress,
                    color = MaterialTheme.colors.primary,
                    backgroundColor = CloudStreamColors.Divider,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                )
            }
        }
    }
}

@Composable
fun PluginStatusBadge(status: Int) {
    val (labelRes, color) = when (status) {
        0 -> Res.string.plugin_status_down to CloudStreamColors.Error
        2 -> Res.string.plugin_status_slow to CloudStreamColors.Warning
        3 -> Res.string.plugin_status_beta to CloudStreamColors.Quality4K
        else -> Res.string.plugin_status_ok to CloudStreamColors.Success
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.caption.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            ),
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

// =========================================================================================
// NIVEL 3: Plugin Details Interactive Modal Dialog
// =========================================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PluginDetailsDialog(
    plugin: PluginItem,
    viewModel: PluginsSettingsViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()

    val livePlugin = remember(state.installedPlugins, state.availablePlugins, plugin) {
        state.installedPlugins.firstOrNull { it.internalName == plugin.internalName }
            ?: state.availablePlugins.firstOrNull { it.internalName == plugin.internalName }
            ?: plugin
    }

    val isInstalled = state.installedPlugins.any { it.internalName == livePlugin.internalName } || livePlugin.isInstalled
    val isThisDownloading = state.operationState is PluginOperationState.Downloading &&
            (state.operationState as PluginOperationState.Downloading).pluginName.equals(livePlugin.name, ignoreCase = true)
    val isThisInstalling = state.operationState is PluginOperationState.Installing &&
            (state.operationState as PluginOperationState.Installing).pluginName.equals(livePlugin.name, ignoreCase = true)

    val validAuthors = livePlugin.authors.filter { it.isNotBlank() }

    ActionDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.fillMaxWidth(),
        titleContent = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!livePlugin.iconUrl.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(CloudStreamColors.Divider),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                url = livePlugin.iconUrl,
                                contentDescription = livePlugin.name,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
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
                                text = livePlugin.name.take(2).uppercase(),
                                style = MaterialTheme.typography.h6.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colors.onPrimary
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = livePlugin.name,
                                style = MaterialTheme.typography.subtitle1.copy(fontWeight = FontWeight.Bold),
                                color = CloudStreamColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = CloudStreamColors.Divider
                            ) {
                                Text(
                                    text = "v${livePlugin.version}",
                                    style = MaterialTheme.typography.caption.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    ),
                                    color = CloudStreamColors.TextSecondary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            PluginStatusBadge(status = livePlugin.status)
                        }

                        if (validAuthors.isNotEmpty()) {
                            Text(
                                text = stringResource(Res.string.plugin_by_author_format, validAuthors.joinToString(", ")),
                                style = MaterialTheme.typography.caption,
                                color = CloudStreamColors.TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(Res.string.close),
                        tint = CloudStreamColors.TextSecondary
                    )
                }
            }
        },
        buttons = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (isInstalled) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Switch(
                            checked = livePlugin.isEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.handleEvent(PluginsSettingsEvent.InstallPlugin(livePlugin.copy(isEnabled = enabled)))
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colors.primary,
                                checkedTrackColor = MaterialTheme.colors.primary.copy(alpha = 0.5f)
                            )
                        )
                        Text(
                            text = if (livePlugin.isEnabled) stringResource(Res.string.plugin_status_ok) else stringResource(Res.string.plugin_status_down),
                            style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
                            color = CloudStreamColors.TextSecondary
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton(
                            text = stringResource(Res.string.uninstall),
                            icon = Icons.Default.Delete,
                            onClick = {
                                viewModel.handleEvent(PluginsSettingsEvent.UninstallPlugin(livePlugin.internalName))
                                onDismiss()
                            }
                        )
                        SecondaryButton(
                            text = stringResource(Res.string.close),
                            onClick = onDismiss
                        )
                    }
                } else {
                    SecondaryButton(
                        text = stringResource(Res.string.close),
                        onClick = onDismiss
                    )

                    if (isThisDownloading || isThisInstalling) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colors.primary,
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        PrimaryButton(
                            text = stringResource(Res.string.install),
                            icon = Icons.Default.Download,
                            onClick = {
                                viewModel.handleEvent(PluginsSettingsEvent.InstallPlugin(livePlugin))
                            }
                        )
                    }
                }
            }
        },
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Divider(color = CloudStreamColors.Divider)

                // Badges row: Language & TV Types
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (!livePlugin.language.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = CloudStreamColors.Divider
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = null,
                                    tint = CloudStreamColors.TextSecondary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = livePlugin.language.uppercase(),
                                    style = MaterialTheme.typography.caption.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    ),
                                    color = CloudStreamColors.TextPrimary
                                )
                            }
                        }
                    }

                    livePlugin.tvTypes.forEach { tvType ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = CloudStreamColors.SurfaceElevated,
                            border = BorderStroke(1.dp, CloudStreamColors.Divider)
                        ) {
                            Text(
                                text = tvType,
                                style = MaterialTheme.typography.caption.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp
                                ),
                                color = CloudStreamColors.TextSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Description
                if (!livePlugin.description.isNullOrBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(Res.string.extension_description),
                            style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                            color = CloudStreamColors.TextMuted
                        )
                        Text(
                            text = livePlugin.description,
                            style = MaterialTheme.typography.body2.copy(fontSize = 13.sp, lineHeight = 18.sp),
                            color = CloudStreamColors.TextPrimary
                        )
                    }
                }

                // Changelog if present
                if (!livePlugin.changelog.isNullOrBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = CloudStreamColors.TextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = stringResource(Res.string.changelog),
                                style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                                color = CloudStreamColors.TextMuted
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = CloudStreamColors.SurfaceElevated,
                            border = BorderStroke(1.dp, CloudStreamColors.Divider),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = livePlugin.changelog,
                                style = MaterialTheme.typography.caption.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                ),
                                color = CloudStreamColors.TextSecondary,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }

                // Technical Package identifier
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colors.background,
                    border = BorderStroke(1.dp, CloudStreamColors.Divider)
                ) {
                    Text(
                        text = livePlugin.internalName,
                        style = MaterialTheme.typography.caption.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        ),
                        color = CloudStreamColors.TextMuted,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    )
}

// =========================================================================================
// Status Banner & Helpers
// =========================================================================================

@Composable
fun OperationStatusBanner(
    operationState: PluginOperationState,
    error: UiText?,
    onDismiss: () -> Unit
) {
    val message = when {
        error != null -> error.asString()
        operationState is PluginOperationState.Error -> {
            if (operationState.messageRes != null) {
                if (operationState.formatArgs.isNotEmpty()) {
                    stringResource(operationState.messageRes, *operationState.formatArgs.toTypedArray())
                } else {
                    stringResource(operationState.messageRes)
                }
            } else {
                operationState.message
            }
        }
        operationState is PluginOperationState.Success -> {
            if (operationState.messageRes != null) {
                if (operationState.formatArgs.isNotEmpty()) {
                    stringResource(operationState.messageRes, *operationState.formatArgs.toTypedArray())
                } else {
                    stringResource(operationState.messageRes)
                }
            } else {
                operationState.message
            }
        }
        operationState is PluginOperationState.Installing -> stringResource(Res.string.plugin_installing_format, operationState.pluginName)
        operationState is PluginOperationState.Uninstalling -> stringResource(Res.string.plugin_uninstalling_format, operationState.pluginName)
        else -> null
    }

    val isError = error != null || operationState is PluginOperationState.Error

    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        if (message != null) {
            Surface(
                color = if (isError) MaterialTheme.colors.error.copy(alpha = 0.15f)
                else MaterialTheme.colors.primary.copy(alpha = 0.15f),
                border = BorderStroke(
                    1.dp,
                    if (isError) MaterialTheme.colors.error.copy(alpha = 0.4f)
                    else MaterialTheme.colors.primary.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isError) Icons.Default.ErrorOutline else Icons.Default.Check,
                            contentDescription = null,
                            tint = if (isError) MaterialTheme.colors.error else MaterialTheme.colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = message,
                            style = MaterialTheme.typography.body2.copy(
                                fontWeight = FontWeight.Medium,
                                color = if (isError) MaterialTheme.colors.error else CloudStreamColors.TextPrimary
                            )
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(Res.string.close),
                            tint = CloudStreamColors.TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyPluginState(
    message: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(CloudStreamColors.Divider),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Extension,
                    contentDescription = null,
                    tint = CloudStreamColors.TextMuted,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.subtitle1.copy(fontWeight = FontWeight.Bold),
                color = CloudStreamColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            BodyMutedText(
                text = subtitle,
                textAlign = TextAlign.Center
            )
        }
    }
}
