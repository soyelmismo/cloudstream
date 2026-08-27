package com.lagradost.cloudstream3.shared.ui.result

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cloudstream.shared_ui.generated.resources.*
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.shared.ui.components.AsyncImage
import com.lagradost.cloudstream3.shared.ui.components.designsystem.BodyMutedText
import com.lagradost.cloudstream3.shared.ui.components.designsystem.CloudStreamFilterChip
import com.lagradost.cloudstream3.shared.ui.components.designsystem.dsCombinedClickable
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors
import com.lagradost.cloudstream3.shared.viewmodels.result.ResultEpisode
import com.lagradost.cloudstream3.shared.viewmodels.result.ResultEvent
import com.lagradost.cloudstream3.shared.viewmodels.result.ResultSeason
import com.lagradost.cloudstream3.shared.viewmodels.result.ResultState
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Premium Seasons & Episodes List view for Series and Anime.
 * Features:
 * - Modern Dub/Sub chip tabs and Season selector chips / dropdown.
 * - 16:9 episode thumbnails with embedded progress bar at base.
 * - Play hover overlay, watched checkmark badge, and EP pill.
 * - Expandable synopsis per episode using [BodyMutedText].
 * - Direct quick-play action button and overflow menu.
 */
@Composable
fun ResultEpisodesList(
    state: ResultState,
    onEpisodeClick: (ResultEpisode) -> Unit,
    onSetWatchState: (Int, Int) -> Unit,
    onEvent: (ResultEvent) -> Unit,
    modifier: Modifier = Modifier,
    onDownloadEpisode: ((ResultEpisode) -> Unit)? = null,
    onEpisodeMenuClick: ((ResultEpisode) -> Unit)? = null
) {
    if (!state.isEpisodeBased && state.episodes.isEmpty()) return

    val playString = stringResource(Res.string.action_play)
    val watchedString = stringResource(Res.string.watched)
    val episodeString = stringResource(Res.string.episode)
    val downloadString = stringResource(Res.string.download)
    val moreOptionsString = stringResource(Res.string.episode_more_options_des)
    val removeFromWatchedString = stringResource(Res.string.action_remove_from_watched)
    val markAsWatchedString = stringResource(Res.string.action_mark_as_watched)
    val showLessString = stringResource(Res.string.show_less)
    val readMoreString = stringResource(Res.string.read_more)

    Column(modifier = modifier.fillMaxWidth()) {
        // Modern Dub & Season Selector Header
        ResultEpisodesSelectorHeader(
            state = state,
            onEvent = onEvent
        )

        // Episodes Rows
        state.episodes.forEach { episode ->
            ResultEpisodeItem(
                episode = episode,
                isSelected = state.selectedEpisode?.id == episode.id,
                onClick = { onEpisodeClick(episode) },
                onSetWatchState = onSetWatchState,
                onDownloadClick = onDownloadEpisode?.let { { it(episode) } },
                onEpisodeMenuClick = {
                    onEpisodeMenuClick?.invoke(episode) ?: onEvent(ResultEvent.OpenEpisodeMenu(episode))
                },
                playString = playString,
                watchedString = watchedString,
                episodeString = episodeString,
                downloadString = downloadString,
                moreOptionsString = moreOptionsString,
                removeFromWatchedString = removeFromWatchedString,
                markAsWatchedString = markAsWatchedString,
                showLessString = showLessString,
                readMoreString = readMoreString,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)
            )
        }
    }
}

/**
 * Modern Dub/Sub chips and Season selector header.
 */
@Composable
fun ResultEpisodesSelectorHeader(
    state: ResultState,
    onEvent: (ResultEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val episodesString = stringResource(Res.string.episodes)
    val episodesLower = episodesString.lowercase()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // Dub/Sub Tabs (for Anime / Multi-audio)
        if (state.availableDubStatuses.size > 1) {
            ModernDubStatusChips(
                availableDubStatuses = state.availableDubStatuses,
                selectedDubStatus = state.selectedDubStatus,
                onSelectDubStatus = { onEvent(ResultEvent.SelectDubStatus(it)) }
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Season Selector and Episode Count Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Season Selector Chips / Dropdown
            if (state.availableSeasons.size > 1) {
                if (state.availableSeasons.size <= 4) {
                    ModernSeasonChipsRow(
                        seasons = state.availableSeasons,
                        selectedSeason = state.selectedSeason ?: state.availableSeasons.first().season,
                        onSelectSeason = { onEvent(ResultEvent.SelectSeason(it)) }
                    )
                } else {
                    ModernSeasonSelectorDropdown(
                        seasons = state.availableSeasons,
                        selectedSeason = state.selectedSeason ?: state.availableSeasons.first().season,
                        onSelectSeason = { onEvent(ResultEvent.SelectSeason(it)) }
                    )
                }
            } else if (state.availableSeasons.size == 1) {
                val singleSeason = state.availableSeasons.first()
                Text(
                    text = singleSeason.displayName(),
                    style = MaterialTheme.typography.subtitle1.copy(
                        fontWeight = FontWeight.Bold,
                        color = CloudStreamColors.TextPrimary
                    )
                )
            } else {
                Text(
                    text = episodesString,
                    style = MaterialTheme.typography.subtitle1.copy(
                        fontWeight = FontWeight.Bold,
                        color = CloudStreamColors.TextPrimary
                    )
                )
            }

            // Episode Count Translucent Badge
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = CloudStreamColors.SurfaceElevated,
                border = BorderStroke(1.dp, CloudStreamColors.Divider.copy(alpha = 0.5f))
            ) {
                Text(
                    text = "${state.episodes.size} $episodesLower",
                    style = MaterialTheme.typography.caption.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = CloudStreamColors.TextSecondary,
                        fontSize = 11.5.sp
                    ),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/**
 * Modern Dub / Sub selection chips.
 */
@Composable
fun ModernDubStatusChips(
    availableDubStatuses: List<DubStatus>,
    selectedDubStatus: DubStatus,
    onSelectDubStatus: (DubStatus) -> Unit,
    modifier: Modifier = Modifier
) {
    val subbedLabel = stringResource(Res.string.app_subbed_text)
    val dubbedLabel = stringResource(Res.string.app_dubbed_text)
    val defaultSubsLabel = stringResource(Res.string.default_subtitles)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        availableDubStatuses.forEach { dubStatus ->
            val isSelected = selectedDubStatus == dubStatus
            val (label, activeColor) = when (dubStatus) {
                DubStatus.Subbed -> subbedLabel to CloudStreamColors.SubBadge
                DubStatus.Dubbed -> dubbedLabel to CloudStreamColors.DubBadge
                DubStatus.None -> defaultSubsLabel to CloudStreamColors.Primary
            }

            CloudStreamFilterChip(
                label = label,
                isSelected = isSelected,
                onClick = { onSelectDubStatus(dubStatus) },
                activeContainerColor = activeColor.copy(alpha = 0.22f),
                activeContentColor = activeColor
            )
        }
    }
}

/**
 * Modern Season Chips Row (when 4 or fewer seasons).
 */
@Composable
fun ModernSeasonChipsRow(
    seasons: List<ResultSeason>,
    selectedSeason: Int,
    onSelectSeason: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        seasons.forEach { season ->
            val isSelected = season.season == selectedSeason
            CloudStreamFilterChip(
                label = season.displayName(),
                isSelected = isSelected,
                onClick = { onSelectSeason(season.season) }
            )
        }
    }
}

/**
 * Dropdown Menu to switch between many available seasons.
 */
@Composable
fun ModernSeasonSelectorDropdown(
    seasons: List<ResultSeason>,
    selectedSeason: Int,
    onSelectSeason: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val currentSeason = seasons.firstOrNull { it.season == selectedSeason } ?: seasons.firstOrNull()
    val seasonString = stringResource(Res.string.season)
    val selectedString = stringResource(Res.string.selected)
    val displayText = currentSeason?.displayName() ?: "$seasonString $selectedSeason"

    Box(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = CloudStreamColors.SurfaceElevated,
            border = BorderStroke(1.dp, CloudStreamColors.Primary.copy(alpha = 0.5f)),
            modifier = Modifier.focusable().clickable { expanded = true }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.body2.copy(
                        fontWeight = FontWeight.Bold,
                        color = CloudStreamColors.TextPrimary,
                        fontSize = 13.sp
                    )
                )

                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = seasonString,
                    tint = CloudStreamColors.Primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(CloudStreamColors.SurfaceElevated)
                .padding(vertical = 4.dp)
        ) {
            seasons.forEach { season ->
                val isSelected = season.season == selectedSeason
                DropdownMenuItem(
                    onClick = {
                        onSelectSeason(season.season)
                        expanded = false
                    }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = season.displayName(),
                            style = MaterialTheme.typography.body2.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) CloudStreamColors.Primary else CloudStreamColors.TextPrimary
                            )
                        )

                        if (isSelected) {
                            Spacer(modifier = Modifier.width(12.dp))
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = selectedString,
                                tint = CloudStreamColors.Primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Premium Episode Card Item with 16:9 thumbnail, progress bar, expandable synopsis, and direct play action.
 */
@Composable
fun ResultEpisodeItem(
    episode: ResultEpisode,
    isSelected: Boolean,
    onClick: () -> Unit,
    onSetWatchState: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    onDownloadClick: (() -> Unit)? = null,
    onEpisodeMenuClick: (() -> Unit)? = null,
    playString: String = stringResource(Res.string.action_play),
    watchedString: String = stringResource(Res.string.watched),
    episodeString: String = stringResource(Res.string.episode),
    downloadString: String = stringResource(Res.string.download),
    moreOptionsString: String = stringResource(Res.string.episode_more_options_des),
    removeFromWatchedString: String = stringResource(Res.string.action_remove_from_watched),
    markAsWatchedString: String = stringResource(Res.string.action_mark_as_watched),
    showLessString: String = stringResource(Res.string.show_less),
    readMoreString: String = stringResource(Res.string.read_more)
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()

    var showMenu by remember { mutableStateOf(false) }
    var isDescriptionExpanded by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isHovered || isFocused) 1.012f else 1.0f,
        label = "ep_scale"
    )

    val borderColor = when {
        isSelected -> CloudStreamColors.Primary
        isFocused -> CloudStreamColors.Primary.copy(alpha = 0.8f)
        isHovered -> CloudStreamColors.Primary.copy(alpha = 0.45f)
        else -> CloudStreamColors.Divider.copy(alpha = 0.3f)
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        backgroundColor = if (isSelected) CloudStreamColors.SurfaceElevated else CloudStreamColors.SurfaceVariant,
        elevation = if (isHovered || isFocused) 6.dp else 1.dp,
        border = BorderStroke(1.5.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .pointerHoverIcon(PointerIcon.Hand)
            .dsCombinedClickable(
                onClick = onClick,
                onLongClick = onEpisodeMenuClick,
                onSecondaryClick = onEpisodeMenuClick,
                interactionSource = interactionSource,
                scale = scale
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                // =============================================================
                // Episode Thumbnail (16:9) with Embedded Bottom Progress Bar
                // =============================================================
                Box(
                    modifier = Modifier
                        .width(140.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(CloudStreamColors.SurfaceElevated)
                ) {
                    if (!episode.poster.isNullOrBlank()) {
                        AsyncImage(
                            url = episode.poster,
                            contentDescription = episode.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(CloudStreamColors.SurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_baseline_play_arrow_24),
                                contentDescription = null,
                                tint = CloudStreamColors.TextMuted,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    // Hover/Focus Play Icon Overlay
                    if (isHovered || isFocused) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.35f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_baseline_play_arrow_24),
                                contentDescription = playString,
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    // Watched Checkmark Badge (Top-Right)
                    if (episode.isWatched) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(20.dp)
                                .background(CloudStreamColors.Success, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = watchedString,
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }

                    // Episode Number Pill (Bottom-Left)
                    if (episode.episode > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(4.dp)
                                .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "EP ${episode.episode}",
                                style = MaterialTheme.typography.caption.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    color = Color.White
                                )
                            )
                        }
                    }

                    // Bottom Embedded Progress Bar
                    val watchProgress = episode.getWatchProgress()
                    if (watchProgress > 0f) {
                        LinearProgressIndicator(
                            progress = watchProgress,
                            color = CloudStreamColors.Primary,
                            backgroundColor = Color.Black.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.5.dp)
                                .align(Alignment.BottomCenter)
                        )
                    }
                }

                // =============================================================
                // Episode Metadata (Title, Date, Runtime, Score, Actions)
                // =============================================================
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val displayTitle = episode.name?.ifBlank { "$episodeString ${episode.episode}" }
                            ?: "$episodeString ${episode.episode}"

                        Text(
                            text = displayTitle,
                            style = MaterialTheme.typography.subtitle2.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) CloudStreamColors.Primary else CloudStreamColors.TextPrimary,
                                fontSize = 14.5.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Inline Download Button
                            IconButton(
                                onClick = { onDownloadClick?.invoke() ?: onEpisodeMenuClick?.invoke() },
                                modifier = Modifier.size(28.dp).focusable()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(CloudStreamColors.SurfaceElevated, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(Res.drawable.baseline_downloading_24),
                                        contentDescription = downloadString,
                                        tint = CloudStreamColors.TextSecondary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }

                            // Direct Play Button
                            IconButton(
                                onClick = onClick,
                                modifier = Modifier.size(28.dp).focusable()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(CloudStreamColors.Primary.copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(Res.drawable.ic_baseline_play_arrow_24),
                                        contentDescription = playString,
                                        tint = CloudStreamColors.Primary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }

                            // 3-Dots Quick Actions Menu
                            Box {
                                IconButton(
                                    onClick = {
                                        if (onEpisodeMenuClick != null) {
                                            onEpisodeMenuClick()
                                        } else {
                                            showMenu = true
                                        }
                                    },
                                    modifier = Modifier.size(28.dp).focusable()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = moreOptionsString,
                                        tint = CloudStreamColors.TextMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                if (onEpisodeMenuClick == null) {
                                    DropdownMenu(
                                        expanded = showMenu,
                                        onDismissRequest = { showMenu = false },
                                        modifier = Modifier.background(CloudStreamColors.SurfaceElevated)
                                    ) {
                                        DropdownMenuItem(
                                            onClick = {
                                                val newState = if (episode.isWatched) 0 else 2
                                                onSetWatchState(episode.id, newState)
                                                showMenu = false
                                            }
                                        ) {
                                            Text(
                                                text = if (episode.isWatched) removeFromWatchedString else markAsWatchedString,
                                                color = CloudStreamColors.TextPrimary,
                                                style = MaterialTheme.typography.body2
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Metadata Row: Runtime & Air Date
                    val runtimeText = episode.runTime?.let { rt ->
                        val mins = rt / 60
                        if (mins > 0) stringResource(Res.string.duration_format, mins) else "${rt}s"
                    }

                    if (!runtimeText.isNullOrBlank()) {
                        BodyMutedText(
                            text = runtimeText,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // Expandable Episode Synopsis
                    if (!episode.description.isNullOrBlank()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateContentSize(animationSpec = tween(200))
                        ) {
                            Text(
                                text = episode.description,
                                style = MaterialTheme.typography.caption.copy(
                                    color = CloudStreamColors.TextSecondary,
                                    lineHeight = 17.sp,
                                    fontSize = 12.sp
                                ),
                                maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (episode.description.length > 90) {
                                Text(
                                    text = if (isDescriptionExpanded) showLessString else readMoreString,
                                    style = MaterialTheme.typography.caption.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = CloudStreamColors.Primary,
                                        fontSize = 11.sp
                                    ),
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            isDescriptionExpanded = !isDescriptionExpanded
                                        }
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
 * LazyList helper to render items seamlessly in LazyColumn (Plural naming).
 */
fun LazyListScope.resultEpisodesListItems(
    episodes: List<ResultEpisode>,
    selectedEpisode: ResultEpisode?,
    onEpisodeClick: (ResultEpisode) -> Unit,
    onSetWatchState: (Int, Int) -> Unit,
    onDownloadEpisode: ((ResultEpisode) -> Unit)? = null,
    onEpisodeMenuClick: ((ResultEpisode) -> Unit)? = null
) {
    items(
        items = episodes,
        key = { it.id.takeIf { id -> id != 0 } ?: it.data }
    ) { episode ->
        ResultEpisodeItem(
            episode = episode,
            isSelected = selectedEpisode?.id == episode.id,
            onClick = { onEpisodeClick(episode) },
            onSetWatchState = onSetWatchState,
            onDownloadClick = onDownloadEpisode?.let { { it(episode) } },
            onEpisodeMenuClick = onEpisodeMenuClick?.let { { it(episode) } },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)
        )
    }
}

/**
 * Backward compatibility alias (Singular naming).
 */
fun LazyListScope.resultEpisodeListItems(
    episodes: List<ResultEpisode>,
    selectedEpisode: ResultEpisode?,
    onEpisodeClick: (ResultEpisode) -> Unit,
    onSetWatchState: (Int, Int) -> Unit,
    onDownloadEpisode: ((ResultEpisode) -> Unit)? = null,
    onEpisodeMenuClick: ((ResultEpisode) -> Unit)? = null
) = resultEpisodesListItems(
    episodes = episodes,
    selectedEpisode = selectedEpisode,
    onEpisodeClick = onEpisodeClick,
    onSetWatchState = onSetWatchState,
    onDownloadEpisode = onDownloadEpisode,
    onEpisodeMenuClick = onEpisodeMenuClick
)
