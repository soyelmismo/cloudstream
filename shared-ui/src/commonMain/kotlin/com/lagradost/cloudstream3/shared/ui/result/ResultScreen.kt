package com.lagradost.cloudstream3.shared.ui.result

import com.lagradost.cloudstream3.utils.asString
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
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
import org.jetbrains.compose.resources.stringResource
import cloudstream.shared_ui.generated.resources.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.shared.player.VideoPlayer
import com.lagradost.cloudstream3.shared.ui.search.SearchResultCard
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors
import com.lagradost.cloudstream3.shared.viewmodels.result.ResultEpisode
import com.lagradost.cloudstream3.shared.viewmodels.result.ResultEvent
import com.lagradost.cloudstream3.shared.viewmodels.result.ResultState
import com.lagradost.cloudstream3.shared.viewmodels.result.ResultViewModel
import com.lagradost.cloudstream3.utils.ExtractorLink

/**
 * Full Media Details Screen for Compose Multiplatform.
 * Connects directly to [ResultViewModel] in MVI architecture.
 */
@Composable
fun ResultScreen(
    viewModel: ResultViewModel,
    showSourcesOnPlay: Boolean = false,
    onBack: (() -> Unit)? = null,
    onPlayEpisode: ((ResultEpisode) -> Unit)? = null,
    onPlayLink: ((ExtractorLink, List<ExtractorLink>, List<SubtitleFile>, SubtitleFile?) -> Unit)? = null,
    onNavigateToRecommendation: ((url: String, apiName: String) -> Unit)? = null,
    onDownloadEpisode: ((ResultEpisode) -> Unit)? = null,
    player: VideoPlayer? = null,
    videoPlayerContent: (@Composable (VideoPlayer, Modifier) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()

    ResultScreen(
        state = state,
        onEvent = viewModel::onEvent,
        showSourcesOnPlay = showSourcesOnPlay,
        onBack = onBack,
        onPlayEpisode = onPlayEpisode,
        onPlayLink = onPlayLink,
        onNavigateToRecommendation = onNavigateToRecommendation,
        onDownloadEpisode = onDownloadEpisode,
        player = player,
        videoPlayerContent = videoPlayerContent,
        modifier = modifier
    )
}

/**
 * Stateless Media Details Screen composable with fluid scrolling,
 * adaptive desktop/mobile container layout, and clean transitions.
 */
@Composable
fun ResultScreen(
    state: ResultState,
    onEvent: (ResultEvent) -> Unit,
    showSourcesOnPlay: Boolean = false,
    onBack: (() -> Unit)? = null,
    onPlayEpisode: ((ResultEpisode) -> Unit)? = null,
    onPlayLink: ((ExtractorLink, List<ExtractorLink>, List<SubtitleFile>, SubtitleFile?) -> Unit)? = null,
    onNavigateToRecommendation: ((url: String, apiName: String) -> Unit)? = null,
    onDownloadEpisode: ((ResultEpisode) -> Unit)? = null,
    player: VideoPlayer? = null,
    videoPlayerContent: (@Composable (VideoPlayer, Modifier) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showLinksDialog by remember { mutableStateOf(false) }
    var activeEpisodeForLinks by remember { mutableStateOf<ResultEpisode?>(null) }

    Scaffold(
        backgroundColor = CloudStreamColors.Background,
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(CloudStreamColors.Background)
        ) {
                when {
                    // Fullscreen Initial Loading
                    state.isLoading && state.loadResponse == null -> {
                        ResultLoadingView(onBack = onBack)
                    }

                    // Fullscreen Initial Error
                    state.error != null && state.loadResponse == null -> {
                        ResultErrorView(
                            error = state.error.asString(),
                            onRetry = { onEvent(ResultEvent.Refresh) },
                            onBack = onBack
                        )
                    }

                    // Loaded Details Content with Smooth Unified Scroll
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 48.dp)
                        ) {
                            // 1. Premium Media Header (Backdrop, Poster, Metadata Badges, Action Buttons, Synopsis)
                            item {
                                ResultHeader(
                                    state = state,
                                    onBack = onBack,
                                    onEvent = onEvent,
                                    onPlayEpisode = { ep ->
                                        if (showSourcesOnPlay) {
                                            activeEpisodeForLinks = ep
                                            onEvent(ResultEvent.SelectEpisode(ep))
                                            onEvent(ResultEvent.ReloadLinks(ep))
                                            showLinksDialog = true
                                        } else {
                                            onPlayEpisode?.invoke(ep)
                                        }
                                    }
                                )
                            }

                            // 2. Modern Seasons & Dub/Sub Chips Selector (for Series / Anime)
                            if (state.isEpisodeBased || state.availableSeasons.isNotEmpty() || state.availableDubStatuses.size > 1) {
                                item {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    ResultEpisodesSelectorHeader(
                                        state = state,
                                        onEvent = onEvent
                                    )
                                }
                            }

                            // 3. Episodes List Items (16:9 Thumbnail, Progress bar, Expandable plot, Quick play)
                            if (state.isEpisodeBased || state.episodes.isNotEmpty()) {
                                resultEpisodesListItems(
                                    episodes = state.episodes,
                                    selectedEpisode = state.selectedEpisode,
                                    onEpisodeClick = { ep ->
                                        if (showSourcesOnPlay) {
                                            activeEpisodeForLinks = ep
                                            onEvent(ResultEvent.SelectEpisode(ep))
                                            onEvent(ResultEvent.ReloadLinks(ep))
                                            showLinksDialog = true
                                        } else {
                                            onPlayEpisode?.invoke(ep)
                                        }
                                    },
                                    onSetWatchState = { epId, watchState ->
                                        onEvent(ResultEvent.SetWatchState(epId, watchState))
                                    },
                                    onDownloadEpisode = onDownloadEpisode,
                                    onEpisodeMenuClick = { ep ->
                                        onEvent(ResultEvent.OpenEpisodeMenu(ep))
                                    }
                                )
                            }

                            // 4. Recommendations & Similar Titles Section
                            if (state.recommendations.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    RecommendationsSection(
                                        recommendations = state.recommendations,
                                        onSelectRecommendation = { item ->
                                            onNavigateToRecommendation?.invoke(item.url, item.apiName)
                                                ?: onEvent(ResultEvent.LoadResult(item.url, item.apiName))
                                        }
                                    )
                                }
                            }
                        }

                        // STICKY BACK BUTTON
                        if (onBack != null) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(16.dp)
                                    .size(42.dp)
                                    .background(CloudStreamColors.Background.copy(alpha = 0.6f), CircleShape)
                                    .clip(CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(Res.string.action_back),
                                    tint = MaterialTheme.colors.onSurface
                                )
                            }
                        }
                    }
                }

                // Streaming Links and Subtitles Extraction Modal Dialog
                if (showLinksDialog) {
                    ResultLinksDialog(
                        state = state,
                        targetEpisode = activeEpisodeForLinks ?: state.selectedEpisode,
                        onPlayLink = { link, links, subs, initialSub ->
                            onPlayLink?.invoke(link, links, subs, initialSub)
                            showLinksDialog = false
                        },
                        onEvent = onEvent,
                        onDismiss = {
                            showLinksDialog = false
                            onEvent(ResultEvent.ClearLinks)
                        }
                    )
                }

                // Cinematic Trailer Viewer Modal Dialog
                if (state.isTrailerDialogOpen) {
                    TrailerDialog(
                        state = state,
                        onEvent = onEvent,
                        onDismiss = {
                            onEvent(ResultEvent.CloseTrailer)
                        },
                        player = player,
                        videoPlayerContent = videoPlayerContent
                    )
                }

                // Episode Context / Action Modal Dialog
                if (state.isEpisodeMenuOpen && state.selectedMenuEpisode != null) {
                    val menuEpisode = state.selectedMenuEpisode
                    EpisodeActionDialog(
                        episode = menuEpisode,
                        isMovie = state.isMovie,
                        onDismiss = {
                            onEvent(ResultEvent.CloseEpisodeMenu)
                        },
                        onPlayInApp = {
                            onEvent(ResultEvent.CloseEpisodeMenu)
                            onEvent(ResultEvent.SelectEpisode(menuEpisode))
                            if (onPlayEpisode != null) {
                                onPlayEpisode(menuEpisode)
                            } else {
                                activeEpisodeForLinks = menuEpisode
                                onEvent(ResultEvent.ReloadLinks(menuEpisode))
                                showLinksDialog = true
                            }
                        },
                        onPlayMirror = {
                            onEvent(ResultEvent.CloseEpisodeMenu)
                            onEvent(ResultEvent.SelectEpisode(menuEpisode))
                            activeEpisodeForLinks = menuEpisode
                            onEvent(ResultEvent.ReloadLinks(menuEpisode))
                            showLinksDialog = true
                        },
                        onReloadLinks = {
                            onEvent(ResultEvent.CloseEpisodeMenu)
                            onEvent(ResultEvent.SelectEpisode(menuEpisode))
                            activeEpisodeForLinks = menuEpisode
                            onEvent(ResultEvent.ReloadLinks(menuEpisode, clearCache = true))
                            showLinksDialog = true
                        },
                        onCopyLink = {
                            onEvent(ResultEvent.CloseEpisodeMenu)
                            onEvent(ResultEvent.CopyEpisodeLink(menuEpisode))
                        },
                        onDownload = {
                            onEvent(ResultEvent.CloseEpisodeMenu)
                            onDownloadEpisode?.invoke(menuEpisode) ?: run {
                                activeEpisodeForLinks = menuEpisode
                                onEvent(ResultEvent.ReloadLinks(menuEpisode))
                                showLinksDialog = true
                            }
                        },
                        onDownloadMirror = {
                            onEvent(ResultEvent.CloseEpisodeMenu)
                            onEvent(ResultEvent.SelectEpisode(menuEpisode))
                            activeEpisodeForLinks = menuEpisode
                            onEvent(ResultEvent.ReloadLinks(menuEpisode))
                            showLinksDialog = true
                        },
                        onToggleWatchState = {
                            onEvent(ResultEvent.CloseEpisodeMenu)
                            val newState = if (menuEpisode.isWatched) 0 else 2
                            onEvent(ResultEvent.SetWatchState(menuEpisode.id, newState))
                        },
                        onMarkUpToThisEpisode = if (!state.isMovie) {
                            {
                                onEvent(ResultEvent.CloseEpisodeMenu)
                                onEvent(ResultEvent.MarkEpisodesUpTo(menuEpisode.id, menuEpisode.season ?: 0))
                            }
                        } else null
                    )
                }
            }
        }
    }

/**
 * Recommendations / Related Media horizontal row.
 */
@Composable
fun RecommendationsSection(
    recommendations: List<SearchResponse>,
    onSelectRecommendation: (SearchResponse) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
    ) {
        Text(
            text = stringResource(Res.string.recommendations_and_similar),
            style = MaterialTheme.typography.subtitle1.copy(
                fontWeight = FontWeight.Bold,
                color = CloudStreamColors.TextPrimary,
                fontSize = 16.sp
            ),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(recommendations) { item ->
                SearchResultCard(
                    item = item,
                    onClick = { onSelectRecommendation(item) },
                    modifier = Modifier.width(135.dp)
                )
            }
        }
    }
}

/**
 * Loading state view for ResultScreen.
 */
@Composable
fun ResultLoadingView(
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CloudStreamColors.Background)
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .size(42.dp)
                    .background(CloudStreamColors.SurfaceVariant.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(Res.string.action_back),
                    tint = CloudStreamColors.TextPrimary
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.align(Alignment.Center)
        ) {
            CircularProgressIndicator(
                color = CloudStreamColors.Primary,
                strokeWidth = 3.dp,
                modifier = Modifier.size(44.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(Res.string.loading_details),
                style = MaterialTheme.typography.body2.copy(
                    color = CloudStreamColors.TextSecondary,
                    fontSize = 14.sp
                )
            )
        }
    }
}

/**
 * Error state view for ResultScreen.
 */
@Composable
fun ResultErrorView(
    error: String,
    onRetry: () -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CloudStreamColors.Background)
            .padding(24.dp)
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .size(42.dp)
                    .background(CloudStreamColors.SurfaceVariant.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(Res.string.action_back),
                    tint = CloudStreamColors.TextPrimary
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.align(Alignment.Center)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = stringResource(Res.string.error),
                tint = CloudStreamColors.Error,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(Res.string.reload_error),
                style = MaterialTheme.typography.h6.copy(
                    fontWeight = FontWeight.Bold,
                    color = CloudStreamColors.TextPrimary
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = error,
                style = MaterialTheme.typography.body2.copy(
                    color = CloudStreamColors.TextSecondary,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = CloudStreamColors.Primary
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = MaterialTheme.colors.onPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(Res.string.retry_search),
                    color = MaterialTheme.colors.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
