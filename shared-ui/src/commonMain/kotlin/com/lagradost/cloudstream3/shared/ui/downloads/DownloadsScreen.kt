package com.lagradost.cloudstream3.shared.ui.downloads

import androidx.compose.material.MaterialTheme
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.TabRowDefaults
import androidx.compose.material.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.shared.persistence.entity.DownloadEpisodeEntity
import com.lagradost.cloudstream3.shared.persistence.entity.DownloadHeaderEntity
import com.lagradost.cloudstream3.shared.ui.components.AsyncImage
import com.lagradost.cloudstream3.shared.ui.components.designsystem.CloudStreamEmptyState
import com.lagradost.cloudstream3.shared.ui.components.designsystem.ActionDialog
import com.lagradost.cloudstream3.shared.ui.components.designsystem.ConfirmDeleteDialog
import com.lagradost.cloudstream3.shared.ui.components.designsystem.BodyMutedText
import com.lagradost.cloudstream3.shared.ui.components.designsystem.CloudStreamTextField
import com.lagradost.cloudstream3.shared.ui.components.designsystem.PrimaryButton
import com.lagradost.cloudstream3.shared.ui.components.designsystem.SecondaryButton
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors
import com.lagradost.cloudstream3.shared.viewmodels.downloads.ActiveDownloadItem
import com.lagradost.cloudstream3.shared.viewmodels.downloads.CompletedHeaderGroup
import com.lagradost.cloudstream3.shared.viewmodels.downloads.DownloadItemStatus
import com.lagradost.cloudstream3.shared.viewmodels.downloads.DownloadsEvent
import com.lagradost.cloudstream3.shared.viewmodels.downloads.DownloadsState
import com.lagradost.cloudstream3.shared.viewmodels.downloads.DownloadsTab
import com.lagradost.cloudstream3.shared.viewmodels.downloads.DownloadsViewModel
import com.lagradost.cloudstream3.shared.viewmodels.downloads.StorageUsageInfo
import org.jetbrains.compose.resources.stringResource
import cloudstream.shared_ui.generated.resources.*

/**
 * Compose Multiplatform Downloads & Offline Media Screen.
 */
@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel,
    onPlayOffline: ((DownloadEpisodeEntity, DownloadHeaderEntity?) -> Unit)? = null,
    onNavigateToExplore: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()

    DownloadsScreenContent(
        state = state,
        onEvent = viewModel::handleEvent,
        onPlayOffline = onPlayOffline,
        onNavigateToExplore = onNavigateToExplore,
        modifier = modifier
    )
}

@Composable
fun DownloadsScreenContent(
    state: DownloadsState,
    onEvent: (DownloadsEvent) -> Unit,
    onPlayOffline: ((DownloadEpisodeEntity, DownloadHeaderEntity?) -> Unit)? = null,
    onNavigateToExplore: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CloudStreamColors.Background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header & Storage bar
            DownloadsTopHeader(
                storageUsage = state.storageUsage,
                activeSpeed = state.formattedTotalSpeed,
                hasActiveDownloads = state.totalActiveDownloadsCount > 0
            )

            // Tabs
            DownloadsTabs(
                selectedTab = state.selectedTab,
                activeCount = state.totalActiveDownloadsCount,
                completedCount = state.completedGroups.sumOf { it.episodeCount },
                onTabSelected = { onEvent(DownloadsEvent.SwitchTab(it)) }
            )

            // Content
            Crossfade(
                targetState = state.selectedTab,
                modifier = Modifier.weight(1f)
            ) { tab ->
                when (tab) {
                    DownloadsTab.DOWNLOADING -> {
                        ActiveDownloadsList(
                            activeDownloads = state.activeDownloads,
                            onEvent = onEvent,
                            onNavigateToExplore = onNavigateToExplore
                        )
                    }
                    DownloadsTab.COMPLETED -> {
                        CompletedDownloadsList(
                            groups = state.filteredCompletedGroups,
                            searchQuery = state.searchQuery,
                            expandedHeaderIds = state.expandedHeaderIds,
                            onEvent = onEvent,
                            onPlayOffline = onPlayOffline,
                            onNavigateToExplore = onNavigateToExplore
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadsTopHeader(
    storageUsage: StorageUsageInfo,
    activeSpeed: String,
    hasActiveDownloads: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CloudStreamColors.Background)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(Res.string.downloadsTitle),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = CloudStreamColors.TextPrimary
                )
                BodyMutedText(
                    text = stringResource(Res.string.downloads_media_subtitle),
                    style = MaterialTheme.typography.caption
                )
            }

            if (hasActiveDownloads) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = CloudStreamColors.Primary.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, CloudStreamColors.Primary.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Downloading,
                            contentDescription = null,
                            tint = CloudStreamColors.Primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = activeSpeed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = CloudStreamColors.Primary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Storage visual bar
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BodyMutedText(
                    text = "${stringResource(Res.string.storageUsage)}: ${storageUsage.formattedAppSize} ${stringResource(Res.string.storageApp)}",
                    style = MaterialTheme.typography.caption.copy(fontSize = 11.sp)
                )
                Text(
                    text = "${storageUsage.formattedFreeSize} ${stringResource(Res.string.storageFree)}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = CloudStreamColors.TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Storage bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(CloudStreamColors.SurfaceVariant)
            ) {
                val appFraction = storageUsage.appFraction.coerceIn(0.01f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(appFraction)
                        .fillMaxSize()
                        .background(CloudStreamColors.Primary)
                )
            }
        }
    }
}

@Composable
private fun DownloadsTabs(
    selectedTab: DownloadsTab,
    activeCount: Int,
    completedCount: Int,
    onTabSelected: (DownloadsTab) -> Unit
) {
    TabRow(
        selectedTabIndex = selectedTab.ordinal,
        backgroundColor = CloudStreamColors.Background,
        contentColor = CloudStreamColors.Primary,
        indicator = { tabPositions ->
            TabRowDefaults.Indicator(
                Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                color = CloudStreamColors.Primary,
                height = 3.dp
            )
        },
        divider = {
            Divider(color = CloudStreamColors.SurfaceVariant, thickness = 1.dp)
        }
    ) {
        Tab(
            selected = selectedTab == DownloadsTab.DOWNLOADING,
            onClick = { onTabSelected(DownloadsTab.DOWNLOADING) },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.tabDownloading),
                        fontWeight = if (selectedTab == DownloadsTab.DOWNLOADING) FontWeight.Bold else FontWeight.Normal,
                        color = if (selectedTab == DownloadsTab.DOWNLOADING) CloudStreamColors.TextPrimary else CloudStreamColors.TextMuted
                    )
                    if (activeCount > 0) {
                        Surface(
                            shape = CircleShape,
                            color = CloudStreamColors.Primary,
                            modifier = Modifier.size(18.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = activeCount.toString(),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colors.onPrimary
                                )
                            }
                        }
                    }
                }
            }
        )

        Tab(
            selected = selectedTab == DownloadsTab.COMPLETED,
            onClick = { onTabSelected(DownloadsTab.COMPLETED) },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.tabCompleted),
                        fontWeight = if (selectedTab == DownloadsTab.COMPLETED) FontWeight.Bold else FontWeight.Normal,
                        color = if (selectedTab == DownloadsTab.COMPLETED) CloudStreamColors.TextPrimary else CloudStreamColors.TextMuted
                    )
                    if (completedCount > 0) {
                        Surface(
                            shape = CircleShape,
                            color = CloudStreamColors.SurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = completedCount.toString(),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CloudStreamColors.TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun ActiveDownloadsList(
    activeDownloads: List<ActiveDownloadItem>,
    onEvent: (DownloadsEvent) -> Unit,
    onNavigateToExplore: (() -> Unit)?
) {
    var isCancelAllDialogOpen by remember { mutableStateOf(false) }

    if (activeDownloads.isEmpty()) {
        EmptyDownloadsView(
            title = stringResource(Res.string.noActiveDownloads),
            subtitle = stringResource(Res.string.noActiveDownloadsDesc),
            buttonText = stringResource(Res.string.exploreCatalog),
            onAction = onNavigateToExplore
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Queue Control Actions Header
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = CloudStreamColors.SurfaceVariant,
                border = BorderStroke(1.dp, CloudStreamColors.SurfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Downloading,
                            contentDescription = null,
                            tint = CloudStreamColors.Primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "${activeDownloads.size} ${stringResource(Res.string.activeDownloadsCount)}",
                            style = MaterialTheme.typography.subtitle2.copy(
                                fontWeight = FontWeight.Bold,
                                color = CloudStreamColors.TextPrimary
                            )
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Pause All
                        OutlinedButton(
                            onClick = { onEvent(DownloadsEvent.PauseAll) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                backgroundColor = CloudStreamColors.Background,
                                contentColor = CloudStreamColors.TextPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Pause,
                                contentDescription = stringResource(Res.string.pauseAll),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(Res.string.pauseAll),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Resume All
                        OutlinedButton(
                            onClick = { onEvent(DownloadsEvent.ResumeAll) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                backgroundColor = CloudStreamColors.Background,
                                contentColor = CloudStreamColors.Primary
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = stringResource(Res.string.resumeAll),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(Res.string.resumeAll),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Cancel All
                        IconButton(
                            onClick = { isCancelAllDialogOpen = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(Res.string.cancelAll),
                                tint = CloudStreamColors.Error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        items(activeDownloads, key = { it.id }) { item ->
            ActiveDownloadCard(
                item = item,
                onPause = { onEvent(DownloadsEvent.PauseDownload(item.id)) },
                onResume = { onEvent(DownloadsEvent.ResumeDownload(item.id)) },
                onCancel = { onEvent(DownloadsEvent.CancelDownload(item.id)) },
                onRetry = { onEvent(DownloadsEvent.RetryDownload(item.id)) }
            )
        }
    }

    if (isCancelAllDialogOpen) {
        ActionDialog(
            onDismissRequest = { isCancelAllDialogOpen = false },
            titleRes = Res.string.cancelAll,
            iconVector = Icons.Default.Close,
            iconTint = CloudStreamColors.Error,
            messageRes = Res.string.cancel_queue_message,
            confirmTextRes = Res.string.cancelAll,
            onConfirm = {
                onEvent(DownloadsEvent.CancelAll)
                isCancelAllDialogOpen = false
            },
            cancelTextRes = Res.string.cancel,
            onCancel = { isCancelAllDialogOpen = false },
            isConfirmDanger = true
        )
    }
}

@Composable
private fun ActiveDownloadCard(
    item: ActiveDownloadItem,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CloudStreamColors.Background,
        border = BorderStroke(1.dp, CloudStreamColors.SurfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Poster
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = CloudStreamColors.SurfaceVariant,
                    modifier = Modifier.size(width = 48.dp, height = 72.dp)
                ) {
                    if (item.posterUrl != null) {
                        AsyncImage(
                            url = item.posterUrl,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = CloudStreamColors.TextMuted,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Info
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.headerName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = CloudStreamColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        if (item.videoQuality != null) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = CloudStreamColors.Primary.copy(alpha = 0.15f),
                                modifier = Modifier.padding(start = 6.dp)
                            ) {
                                Text(
                                    text = item.videoQuality,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CloudStreamColors.Primary,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    if (item.episodeName != null || item.episodeIndex != null) {
                        val epLabel = buildString {
                            if (item.seasonIndex != null && item.seasonIndex > 0) {
                                append("${stringResource(Res.string.season)} ${item.seasonIndex} • ")
                            }
                            if (item.episodeName != null) {
                                append(item.episodeName)
                            } else {
                                append("${stringResource(Res.string.episode)} ${item.episodeIndex}")
                            }
                        }
                        Text(
                            text = epLabel,
                            fontSize = 12.sp,
                            color = CloudStreamColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Progress text
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = item.formattedProgressText,
                            fontSize = 11.sp,
                            color = CloudStreamColors.TextMuted
                        )

                        Text(
                            text = when (item.status) {
                                DownloadItemStatus.DOWNLOADING -> {
                                    if (item.etaSeconds != null && item.etaSeconds > 0) {
                                        val m = item.etaSeconds / 60
                                        val s = item.etaSeconds % 60
                                        "${item.formattedSpeed} (${m}m ${s}s)"
                                    } else {
                                        item.formattedSpeed
                                    }
                                }
                                DownloadItemStatus.PAUSED -> stringResource(Res.string.downloadPaused)
                                DownloadItemStatus.ERROR -> stringResource(Res.string.downloadError)
                                DownloadItemStatus.QUEUED -> stringResource(Res.string.downloadQueued)
                                DownloadItemStatus.COMPLETED -> stringResource(Res.string.downloadCompleted)
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = when (item.status) {
                                DownloadItemStatus.DOWNLOADING -> CloudStreamColors.Primary
                                DownloadItemStatus.PAUSED -> CloudStreamColors.Warning
                                DownloadItemStatus.ERROR -> CloudStreamColors.Error
                                else -> CloudStreamColors.TextMuted
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Action button
                when (item.status) {
                    DownloadItemStatus.DOWNLOADING -> {
                        IconButton(onClick = onPause) {
                            Icon(
                                imageVector = Icons.Default.Pause,
                                contentDescription = stringResource(Res.string.pause),
                                tint = CloudStreamColors.TextPrimary
                            )
                        }
                    }
                    DownloadItemStatus.PAUSED -> {
                        IconButton(onClick = onResume) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = stringResource(Res.string.resume),
                                tint = CloudStreamColors.Primary
                            )
                        }
                    }
                    DownloadItemStatus.ERROR -> {
                        IconButton(onClick = onRetry) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(Res.string.reload_error),
                                tint = CloudStreamColors.Error
                            )
                        }
                    }
                    else -> {
                        IconButton(onClick = onCancel) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(Res.string.cancel),
                                tint = CloudStreamColors.TextMuted
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = item.progress.coerceIn(0f, 1f),
                color = when (item.status) {
                    DownloadItemStatus.PAUSED -> MaterialTheme.colors.secondary
                    DownloadItemStatus.ERROR -> CloudStreamColors.Error
                    else -> CloudStreamColors.Primary
                },
                backgroundColor = CloudStreamColors.SurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
            )
        }
    }
}

@Composable
private fun CompletedDownloadsList(
    groups: List<CompletedHeaderGroup>,
    searchQuery: String,
    expandedHeaderIds: Set<Int>,
    onEvent: (DownloadsEvent) -> Unit,
    onPlayOffline: ((DownloadEpisodeEntity, DownloadHeaderEntity?) -> Unit)?,
    onNavigateToExplore: (() -> Unit)?
) {
    var headerToDelete by remember { mutableStateOf<CompletedHeaderGroup?>(null) }
    var episodeToDelete by remember { mutableStateOf<Pair<DownloadEpisodeEntity, Int>?>(null) }

    if (groups.isEmpty()) {
        EmptyDownloadsView(
            title = stringResource(Res.string.noCompletedDownloads),
            subtitle = stringResource(Res.string.noCompletedDownloadsDesc),
            buttonText = stringResource(Res.string.exploreCatalog),
            onAction = onNavigateToExplore
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Search bar
        item {
            CloudStreamTextField(
                value = searchQuery,
                onValueChange = { onEvent(DownloadsEvent.SearchQueryChanged(it)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(Res.string.search_hint),
                        tint = CloudStreamColors.TextMuted
                    )
                },
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = { onEvent(DownloadsEvent.SearchQueryChanged("")) }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = stringResource(Res.string.sort_clear),
                                tint = CloudStreamColors.TextMuted
                            )
                        }
                    }
                } else null,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        items(groups, key = { it.header.id }) { group ->
            val isExpanded = expandedHeaderIds.contains(group.header.id)
            CompletedHeaderGroupCard(
                group = group,
                isExpanded = isExpanded,
                onToggleExpand = { onEvent(DownloadsEvent.ToggleHeaderExpanded(group.header.id)) },
                onDeleteHeader = { headerToDelete = group },
                onDeleteEpisode = { epId ->
                    val ep = group.episodes.firstOrNull { it.id == epId }
                    if (ep != null) {
                        episodeToDelete = ep to group.header.id
                    } else {
                        onEvent(DownloadsEvent.DeleteCompletedEpisode(epId, group.header.id))
                    }
                },
                onPlayEpisode = { ep ->
                    onPlayOffline?.invoke(ep, group.header)
                        ?: onEvent(DownloadsEvent.PlayOffline(ep, group.header))
                }
            )
        }
    }

    // Confirmation Dialog for deleting entire series/movie header
    headerToDelete?.let { group ->
        ConfirmDeleteDialog(
            onConfirm = {
                onEvent(DownloadsEvent.DeleteCompletedHeader(group.header.id))
                headerToDelete = null
            },
            onDismiss = { headerToDelete = null },
            titleRes = Res.string.delete_files,
            itemName = group.header.name,
            confirmTextRes = Res.string.delete_files
        )
    }

    // Confirmation Dialog for deleting an episode
    episodeToDelete?.let { (ep, headerId) ->
        val epName = if (!ep.name.isNullOrBlank()) ep.name else "${stringResource(Res.string.episode)} ${ep.episode}"
        ConfirmDeleteDialog(
            onConfirm = {
                onEvent(DownloadsEvent.DeleteCompletedEpisode(ep.id, headerId))
                episodeToDelete = null
            },
            onDismiss = { episodeToDelete = null },
            titleRes = Res.string.delete_file,
            itemName = epName,
            confirmTextRes = Res.string.delete_file
        )
    }
}

@Composable
private fun CompletedHeaderGroupCard(
    group: CompletedHeaderGroup,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onDeleteHeader: () -> Unit,
    onDeleteEpisode: (Int) -> Unit,
    onPlayEpisode: (DownloadEpisodeEntity) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = CloudStreamColors.Background,
        border = BorderStroke(1.dp, CloudStreamColors.SurfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Poster
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = CloudStreamColors.Background,
                    modifier = Modifier.size(width = 54.dp, height = 80.dp)
                ) {
                    if (group.header.poster != null) {
                        AsyncImage(
                            url = group.header.poster,
                            contentDescription = group.header.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = CloudStreamColors.TextMuted
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Metadata
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.header.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = CloudStreamColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (group.isMovie) stringResource(Res.string.typeMovie) else "${group.episodeCount} ${stringResource(Res.string.episodes)}",
                        fontSize = 12.sp,
                        color = CloudStreamColors.TextSecondary
                    )

                    BodyMutedText(
                        text = group.formattedTotalSize,
                        style = MaterialTheme.typography.caption.copy(fontSize = 11.sp)
                    )
                }

                // Delete button
                IconButton(onClick = onDeleteHeader) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(Res.string.delete_files),
                        tint = CloudStreamColors.TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Expand icon
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = CloudStreamColors.TextSecondary
                )
            }

            // Episodes list if expanded
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(150))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CloudStreamColors.Background)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Divider(color = CloudStreamColors.SurfaceVariant, thickness = 1.dp)

                    group.episodes.forEach { episode ->
                        CompletedEpisodeRow(
                            episode = episode,
                            onPlay = { onPlayEpisode(episode) },
                            onDelete = { onDeleteEpisode(episode.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompletedEpisodeRow(
    episode: DownloadEpisodeEntity,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail or play icon
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = CloudStreamColors.SurfaceVariant,
            modifier = Modifier.size(width = 42.dp, height = 28.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = stringResource(Res.string.play),
                    tint = CloudStreamColors.Primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            val title = if (episode.name.isNullOrBlank()) {
                "${stringResource(Res.string.episode)} ${episode.episode}"
            } else {
                "${episode.episode}. ${episode.name}"
            }
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = CloudStreamColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (episode.season != null && episode.season > 0) {
                BodyMutedText(
                    text = "${stringResource(Res.string.season)} ${episode.season}",
                    style = MaterialTheme.typography.caption.copy(fontSize = 11.sp)
                )
            }
        }

        // Delete episode
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(Res.string.delete_file),
                tint = CloudStreamColors.TextMuted,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * Fullscreen empty state for downloads tabs using centralized [CloudStreamEmptyState].
 */
@Composable
private fun EmptyDownloadsView(
    title: String,
    subtitle: String,
    buttonText: String,
    onAction: (() -> Unit)?
) {
    CloudStreamEmptyState(
        icon = Icons.Default.DownloadDone,
        iconTint = CloudStreamColors.Primary,
        iconBackgroundColor = CloudStreamColors.Background,
        iconBorderColor = CloudStreamColors.SurfaceVariant,
        iconContainerSize = 96.dp,
        iconSize = 44.dp,
        title = title,
        subtitle = subtitle,
        actionText = buttonText,
        onActionClick = onAction
    )
}
