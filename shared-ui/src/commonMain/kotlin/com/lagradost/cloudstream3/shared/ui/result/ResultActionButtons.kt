package com.lagradost.cloudstream3.shared.ui.result

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cloudstream.shared_ui.generated.resources.*
import com.lagradost.cloudstream3.shared.ui.components.designsystem.ActionDialog
import com.lagradost.cloudstream3.shared.ui.components.designsystem.PrimaryButton
import com.lagradost.cloudstream3.shared.ui.focus.dpadFocusable
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors
import com.lagradost.cloudstream3.shared.viewmodels.result.ResultEpisode
import com.lagradost.cloudstream3.shared.viewmodels.result.ResultEvent
import com.lagradost.cloudstream3.shared.viewmodels.result.ResultState
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Action buttons bar for the Details Screen:
 * - Prominent "Continuar viendo" (Continue Watching) / "Reproducir" hero banner using [PrimaryButton].
 * - Bookmark status button with modal watch-type picker [ActionDialog].
 * - Favorite button (toggle).
 * - Subscription button (toggle notifications).
 * - Refresh button.
 * - Trailer action button.
 */
@Composable
fun ResultActionButtons(
    state: ResultState,
    onEvent: (ResultEvent) -> Unit,
    onPlayEpisode: (ResultEpisode) -> Unit,
    modifier: Modifier = Modifier,
    onEpisodeMenuClick: ((ResultEpisode) -> Unit)? = null
) {
    var showBookmarkDialog by remember { mutableStateOf(false) }
    var showSyncDialog by remember { mutableStateOf(false) }

    // Target Episode for Hero Play Button
    val targetEp = state.lastWatchedEpisode ?: state.episodes.firstOrNull()
    val progress = targetEp?.getWatchProgress() ?: 0f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Hero "Continuar Viendo" / "Reproducir" Button
        targetEp?.let { ep ->
            ContinueWatchingHeroButton(
                episode = ep,
                isMovie = state.isMovie,
                progress = progress,
                onClick = {
                    onPlayEpisode(ep)
                },
                onLongClick = {
                    onEpisodeMenuClick?.invoke(ep) ?: onEvent(ResultEvent.OpenEpisodeMenu(ep))
                }
            )
        }

        // Secondary Action Icons Row (Trailer, Bookmark, Sync, Favorite, Subscription, Refresh)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Trailer Action (visible when state.hasTrailers == true)
            if (state.hasTrailers) {
                TrailerActionButton(
                    onClick = { onEvent(ResultEvent.OpenTrailer(0)) }
                )
            }

            // Bookmark Action
            BookmarkButton(
                isBookmarked = state.isBookmarked,
                watchType = state.bookmarkWatchType,
                onClick = { showBookmarkDialog = true }
            )

            // External Sync Action (AniList, MAL, Simkl, Kitsu)
            SyncActionButton(
                state = state,
                onClick = { showSyncDialog = true }
            )

            // Favorite Action
            FavoriteActionButton(
                isFavorite = state.isFavorite,
                onClick = { onEvent(ResultEvent.ToggleFavorite) }
            )

            // Subscription Action
            SubscriptionActionButton(
                isSubscribed = state.isSubscribed,
                onClick = { onEvent(ResultEvent.ToggleSubscription) }
            )

            // Refresh Action
            RefreshActionButton(
                isLoading = state.isLoading,
                onClick = { onEvent(ResultEvent.Refresh) }
            )
        }
    }

    // Modal Watch Type Selection Dialog
    if (showBookmarkDialog) {
        BookmarkWatchTypeDialog(
            currentWatchType = state.bookmarkWatchType,
            onSelectWatchType = { selectedType ->
                onEvent(ResultEvent.SetBookmark(selectedType))
                showBookmarkDialog = false
            },
            onDismiss = { showBookmarkDialog = false }
        )
    }

    // Modal External Sync Tracking Dialog
    if (showSyncDialog) {
        SyncDialog(
            state = state,
            onEvent = onEvent,
            onDismiss = { showSyncDialog = false }
        )
    }
}

@Composable
fun ContinueWatchingHeroButton(
    episode: ResultEpisode,
    isMovie: Boolean,
    progress: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onSecondaryClick: (() -> Unit)? = onLongClick
) {
    val continueWatchingText = stringResource(Res.string.continueWatching)
    val playText = stringResource(Res.string.play)
    val typeMovieText = stringResource(Res.string.typeMovie)
    val episodeText = stringResource(Res.string.episode)
    val buttonLabel = when {
        isMovie && progress > 0.05f -> "$continueWatchingText ($typeMovieText)"
        isMovie -> "$playText ($typeMovieText)"
        progress > 0.05f -> {
            val epName = episode.name ?: "$episodeText ${episode.episode}"
            "$continueWatchingText: $epName"
        }
        else -> {
            val epName = episode.name ?: "$episodeText ${episode.episode}"
            "$playText: $epName"
        }
    }

    PrimaryButton(
        onClick = onClick,
        onLongClick = onLongClick,
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 18.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_baseline_play_arrow_24),
                    contentDescription = stringResource(Res.string.action_play),
                    tint = MaterialTheme.colors.onPrimary,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = buttonLabel,
                    style = MaterialTheme.typography.button.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colors.onPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Progress Bar if started
            if (progress > 0.02f) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LinearProgressIndicator(
                        progress = progress,
                        color = CloudStreamColors.Secondary,
                        backgroundColor = MaterialTheme.colors.onPrimary.copy(alpha = 0.25f),
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.caption.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colors.onPrimary.copy(alpha = 0.9f),
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun BookmarkButton(
    isBookmarked: Boolean,
    watchType: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (label, iconTint) = when (watchType) {
        1 -> stringResource(Res.string.statusWatching) to CloudStreamColors.Secondary
        2 -> stringResource(Res.string.statusCompleted) to CloudStreamColors.Success
        3 -> stringResource(Res.string.statusOnHold) to CloudStreamColors.Warning
        4 -> stringResource(Res.string.statusDropped) to CloudStreamColors.Error
        5 -> stringResource(Res.string.statusPlanToWatch) to CloudStreamColors.Primary
        else -> if (isBookmarked) stringResource(Res.string.bookmarked) to CloudStreamColors.Secondary else stringResource(Res.string.bookmark) to CloudStreamColors.TextSecondary
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .dpadFocusable(
                onClick = onClick,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(
                    if (isBookmarked) CloudStreamColors.SurfaceElevated else CloudStreamColors.SurfaceVariant,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(if (isBookmarked) Res.drawable.ic_baseline_bookmark_24 else Res.drawable.ic_baseline_bookmark_border_24),
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.caption.copy(
                fontWeight = if (isBookmarked) FontWeight.Bold else FontWeight.Normal,
                color = if (isBookmarked) iconTint else CloudStreamColors.TextMuted,
                fontSize = 11.sp
            )
        )
    }
}

@Composable
fun SyncActionButton(
    state: ResultState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primarySync = state.primaryLinkedSync
    val isLinked = state.isSyncLinked
    val activeColor = primarySync?.service?.brandColor ?: CloudStreamColors.Secondary

    val label = when {
        primarySync != null && primarySync.status != com.lagradost.cloudstream3.shared.viewmodels.result.ExternalSyncStatus.None -> {
            primarySync.service.serviceName
        }
        isLinked -> stringResource(Res.string.syncLinked)
        else -> stringResource(Res.string.syncButton)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .dpadFocusable(
                onClick = onClick,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(
                    if (isLinked) activeColor.copy(alpha = 0.22f) else CloudStreamColors.SurfaceVariant,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(Res.drawable.baseline_sync_24),
                contentDescription = label,
                tint = if (isLinked) activeColor else CloudStreamColors.TextSecondary,
                modifier = Modifier.size(22.dp)
            )

            // Linked Indicator Badge Dot
            if (isLinked) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(CloudStreamColors.Success, CircleShape)
                        .align(Alignment.TopEnd)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.caption.copy(
                fontWeight = if (isLinked) FontWeight.Bold else FontWeight.Normal,
                color = if (isLinked) activeColor else CloudStreamColors.TextMuted,
                fontSize = 11.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun FavoriteActionButton(
    isFavorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val favoriteLabel = stringResource(if (isFavorite) Res.string.favorited else Res.string.favorite)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .dpadFocusable(
                onClick = onClick,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(
                    if (isFavorite) CloudStreamColors.Error.copy(alpha = 0.2f) else CloudStreamColors.SurfaceVariant,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = favoriteLabel,
                tint = if (isFavorite) CloudStreamColors.Error else CloudStreamColors.TextSecondary,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = favoriteLabel,
            style = MaterialTheme.typography.caption.copy(
                fontWeight = if (isFavorite) FontWeight.Bold else FontWeight.Normal,
                color = if (isFavorite) CloudStreamColors.Error else CloudStreamColors.TextMuted,
                fontSize = 11.sp
            )
        )
    }
}

@Composable
fun SubscriptionActionButton(
    isSubscribed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val subscribeLabel = stringResource(if (isSubscribed) Res.string.subscribed else Res.string.subscribe)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .dpadFocusable(
                onClick = onClick,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(
                    if (isSubscribed) CloudStreamColors.Primary.copy(alpha = 0.2f) else CloudStreamColors.SurfaceVariant,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = subscribeLabel,
                tint = if (isSubscribed) CloudStreamColors.Primary else CloudStreamColors.TextSecondary,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = subscribeLabel,
            style = MaterialTheme.typography.caption.copy(
                fontWeight = if (isSubscribed) FontWeight.Bold else FontWeight.Normal,
                color = if (isSubscribed) CloudStreamColors.Primary else CloudStreamColors.TextMuted,
                fontSize = 11.sp
            )
        )
    }
}

@Composable
fun RefreshActionButton(
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val refreshLabel = stringResource(Res.string.refresh)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .dpadFocusable(
                onClick = { if (!isLoading) onClick() },
                enabled = !isLoading,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(CloudStreamColors.SurfaceVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = refreshLabel,
                tint = CloudStreamColors.TextSecondary,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = refreshLabel,
            style = MaterialTheme.typography.caption.copy(
                color = CloudStreamColors.TextMuted,
                fontSize = 11.sp
            )
        )
    }
}

@Composable
fun TrailerActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val trailerText = stringResource(Res.string.trailer)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .dpadFocusable(
                onClick = onClick,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(
                    CloudStreamColors.Primary.copy(alpha = 0.22f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(Res.drawable.baseline_theaters_24),
                contentDescription = trailerText,
                tint = CloudStreamColors.Primary,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = trailerText,
            style = MaterialTheme.typography.caption.copy(
                fontWeight = FontWeight.SemiBold,
                color = CloudStreamColors.Primary,
                fontSize = 11.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Modal Watch Type Selector Dialog using [ActionDialog].
 */
@Composable
fun BookmarkWatchTypeDialog(
    currentWatchType: Int,
    onSelectWatchType: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        WatchTypeItem(1, stringResource(Res.string.statusWatching), "", CloudStreamColors.Secondary),
        WatchTypeItem(2, stringResource(Res.string.statusCompleted), "", CloudStreamColors.Success),
        WatchTypeItem(3, stringResource(Res.string.statusOnHold), "", CloudStreamColors.Warning),
        WatchTypeItem(4, stringResource(Res.string.statusDropped), "", CloudStreamColors.Error),
        WatchTypeItem(5, stringResource(Res.string.statusPlanToWatch), "", CloudStreamColors.Primary),
        WatchTypeItem(0, stringResource(Res.string.statusNone), "", CloudStreamColors.TextMuted)
    )

    ActionDialog(
        onDismissRequest = onDismiss,
        titleRes = Res.string.bookmark,
        cancelTextRes = Res.string.cancel,
        onCancel = onDismiss,
        showCloseButton = true,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEach { item ->
                    val isSelected = currentWatchType == item.id
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) item.color.copy(alpha = 0.2f) else CloudStreamColors.SurfaceVariant,
                        border = BorderStroke(
                            if (isSelected) 1.5.dp else 1.dp,
                            if (isSelected) item.color else CloudStreamColors.Divider.copy(alpha = 0.5f)
                        ),
                        elevation = if (isSelected) 2.dp else 0.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelectWatchType(item.id) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.body1.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) item.color else CloudStreamColors.TextPrimary
                                    )
                                )
                                if (item.subtitle.isNotBlank()) {
                                    Text(
                                        text = item.subtitle,
                                        style = MaterialTheme.typography.caption.copy(
                                            color = CloudStreamColors.TextMuted,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }

                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = stringResource(Res.string.selected),
                                    tint = item.color,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}

data class WatchTypeItem(
    val id: Int,
    val title: String,
    val subtitle: String,
    val color: Color
)
