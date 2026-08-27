package com.lagradost.cloudstream3.shared.ui.plugins

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import cloudstream.shared_ui.generated.resources.*
import com.lagradost.cloudstream3.shared.ui.components.designsystem.ActionDialog
import com.lagradost.cloudstream3.shared.ui.components.designsystem.ConfirmDeleteDialog
import com.lagradost.cloudstream3.shared.ui.components.designsystem.BodyMutedText
import com.lagradost.cloudstream3.shared.ui.components.designsystem.PrimaryButton
import com.lagradost.cloudstream3.shared.ui.components.designsystem.SecondaryButton
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors
import com.lagradost.cloudstream3.shared.viewmodels.settings.PluginRepositoryItem
import com.lagradost.cloudstream3.shared.viewmodels.settings.PluginsSettingsEvent
import com.lagradost.cloudstream3.shared.viewmodels.settings.PluginsSettingsViewModel

/**
 * Advanced Extension Repositories Management Dialog.
 *
 * Provides full management of plugin repository URLs:
 * - View repository list with details: name, URL, number of provided plugins, sync status/timestamp.
 * - Add repository with URL format validation (HTTP/HTTPS/GitHub raw) and clipboard paste shortcut.
 * - Force sync/refresh indexes across all configured repositories.
 * - Delete custom repositories with a safety confirmation prompt.
 * - Quick filter: click any repository to filter available extensions by it.
 */
@Composable
fun RepositoriesDialog(
    viewModel: PluginsSettingsViewModel,
    onDismiss: () -> Unit,
    onFilterByRepo: ((String) -> Unit)? = null,
    onAddRepoClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()

    var repoToDelete by remember { mutableStateOf<PluginRepositoryItem?>(null) }
    var showAddRepoDialog by remember { mutableStateOf(false) }

    ActionDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.fillMaxWidth(),
        titleContent = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
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
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = MaterialTheme.colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(Res.string.manageRepositories),
                                style = MaterialTheme.typography.h6.copy(fontWeight = FontWeight.Bold),
                                color = CloudStreamColors.TextPrimary
                            )

                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colors.primary
                            ) {
                                Text(
                                    text = "${state.repositories.size}",
                                    style = MaterialTheme.typography.caption.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colors.onPrimary
                                    ),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        BodyMutedText(
                            text = "${state.availablePlugins.size} ${stringResource(Res.string.pluginsProvided)}"
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(Res.string.close),
                        tint = CloudStreamColors.TextSecondary
                    )
                }
            }
        },
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Divider(color = CloudStreamColors.Divider)

                // Actions Header: Sync All Button + Add New Repo Trigger
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Sync All Button
                    PrimaryButton(
                        onClick = { viewModel.handleEvent(PluginsSettingsEvent.Reload) },
                        enabled = !state.isLoading,
                        modifier = Modifier.height(38.dp)
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colors.onPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(Res.string.syncingRepositories),
                                style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Bold)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(Res.string.syncAllRepositories),
                                style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    // Add Repository Button
                    PrimaryButton(
                        onClick = {
                            if (onAddRepoClick != null) {
                                onAddRepoClick()
                            } else {
                                showAddRepoDialog = true
                            }
                        },
                        icon = Icons.Default.Add,
                        text = stringResource(Res.string.addRepository),
                        modifier = Modifier.height(38.dp)
                    )
                }

                // Repositories List
                if (state.repositories.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Source,
                                contentDescription = null,
                                tint = CloudStreamColors.TextMuted,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = stringResource(Res.string.noPluginsFound),
                                style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.SemiBold),
                                color = CloudStreamColors.TextSecondary
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.repositories, key = { it.url }) { repo ->
                            RepositoryItemCard(
                                repository = repo,
                                onSelect = {
                                    onFilterByRepo?.invoke(repo.url)
                                    onDismiss()
                                },
                                onDelete = {
                                    repoToDelete = repo
                                }
                            )
                        }
                    }
                }
            }
        },
        showCloseButton = false,
        cancelTextRes = Res.string.close,
        onCancel = onDismiss
    )

    // Delete Confirmation Dialog
    repoToDelete?.let { repo ->
        ConfirmDeleteDialog(
            onConfirm = {
                viewModel.handleEvent(PluginsSettingsEvent.RemoveRepository(repo.url))
                repoToDelete = null
            },
            onDismiss = { repoToDelete = null },
            titleRes = Res.string.deleteRepoConfirmTitle,
            message = "${stringResource(Res.string.deleteRepoConfirmDesc)}\n\n\"${repo.name}\"\n${repo.url}",
            confirmTextRes = Res.string.deleteRepo
        )
    }

    // Modal Dialog: Add Repository
    if (showAddRepoDialog) {
        AddRepositoryDialog(
            viewModel = viewModel,
            onDismiss = { showAddRepoDialog = false }
        )
    }
}

/**
 * Individual Repository Item Card inside the management dialog.
 */
@Composable
private fun RepositoryItemCard(
    repository: PluginRepositoryItem,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val cardBg by animateColorAsState(
        targetValue = if (isHovered) CloudStreamColors.SurfaceElevated
        else MaterialTheme.colors.background,
        animationSpec = tween(150)
    )

    Card(
        shape = RoundedCornerShape(10.dp),
        backgroundColor = cardBg,
        border = BorderStroke(1.dp, CloudStreamColors.Divider),
        elevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onSelect
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Repo Icon & Details
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Leading Icon Box
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(8.dp))
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
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    // Header Row with Name and Badges
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = repository.name,
                            style = MaterialTheme.typography.body2.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            ),
                            color = CloudStreamColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colors.primary
                                    ),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }

                        // Plugin Count Badge
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = CloudStreamColors.Divider
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Extension,
                                    contentDescription = null,
                                    tint = CloudStreamColors.TextSecondary,
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "${repository.pluginCount}",
                                    style = MaterialTheme.typography.caption.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = CloudStreamColors.TextSecondary
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Repo URL
                    Text(
                        text = repository.url,
                        style = MaterialTheme.typography.caption.copy(fontSize = 11.sp),
                        color = CloudStreamColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Last Sync Time
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "${stringResource(Res.string.lastSynced)}: ${PluginsSettingsViewModel.formatSyncTime(repository.lastSyncTime)}",
                            style = MaterialTheme.typography.caption.copy(
                                fontSize = 10.sp,
                                color = CloudStreamColors.TextSecondary
                            )
                        )
                    }
                }
            }

            // Actions: Filter & Delete
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onSelect,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = stringResource(Res.string.filterByThisRepo),
                        tint = MaterialTheme.colors.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (repository.isRemovable) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(Res.string.deleteRepo),
                            tint = MaterialTheme.colors.error.copy(alpha = 0.85f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
