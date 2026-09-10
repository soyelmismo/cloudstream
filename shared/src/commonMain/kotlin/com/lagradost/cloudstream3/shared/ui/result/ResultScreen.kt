package com.lagradost.cloudstream3.shared.ui.result

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

import com.lagradost.cloudstream3.TvType
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
import com.lagradost.cloudstream4.generated.resources.*
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

import com.lagradost.cloudstream3.shared.ui.layout.Layout
import com.lagradost.cloudstream3.shared.ui.layout.isLayout
import org.jetbrains.compose.ui.tooling.preview.Preview

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
    onPlayLink: ((ExtractorLink, ImmutableList<ExtractorLink>, ImmutableList<SubtitleFile>, SubtitleFile?) -> Unit)? = null,
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
    onPlayLink: ((ExtractorLink, ImmutableList<ExtractorLink>, ImmutableList<SubtitleFile>, SubtitleFile?) -> Unit)? = null,
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
                state.isLoading && state.loadResponse == null -> {
                    ResultLoadingView(onBack = onBack)
                }

                state.error != null && state.loadResponse == null -> {
                    ResultErrorView(
                        error = state.error.asString(),
                        onRetry = { onEvent(ResultEvent.Refresh) },
                        onBack = onBack
                    )
                }

                else -> {
                    ResultSuccessView(
                        state = state,
                        onBack = onBack,
                        onEvent = onEvent,
                        showSourcesOnPlay = showSourcesOnPlay,
                        onPlayEpisode = onPlayEpisode,
                        onNavigateToRecommendation = onNavigateToRecommendation,
                        onDownloadEpisode = onDownloadEpisode,
                        onOpenLinksDialog = { ep ->
                            activeEpisodeForLinks = ep
                            onEvent(ResultEvent.SelectEpisode(ep))
                            onEvent(ResultEvent.ReloadLinks(ep))
                            showLinksDialog = true
                        }
                    )
                }
            }

            ResultModalsHost(
                state = state,
                showLinksDialog = showLinksDialog,
                activeEpisodeForLinks = activeEpisodeForLinks,
                onPlayLink = onPlayLink,
                onPlayEpisode = onPlayEpisode,
                onDownloadEpisode = onDownloadEpisode,
                onOpenLinksForEpisode = { ep ->
                    activeEpisodeForLinks = ep
                    showLinksDialog = true
                },
                onCloseLinksDialog = {
                    showLinksDialog = false
                    onEvent(ResultEvent.ClearLinks)
                },
                onEvent = onEvent,
                player = player,
                videoPlayerContent = videoPlayerContent
            )
        }
    }
}

@Composable
private fun ResultSuccessView(
    state: ResultState,
    onBack: (() -> Unit)?,
    onEvent: (ResultEvent) -> Unit,
    showSourcesOnPlay: Boolean,
    onPlayEpisode: ((ResultEpisode) -> Unit)?,
    onNavigateToRecommendation: ((url: String, apiName: String) -> Unit)?,
    onDownloadEpisode: ((ResultEpisode) -> Unit)?,
    onOpenLinksDialog: (ResultEpisode) -> Unit
) {
    val isTV = isLayout(Layout.TV)

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = if (isTV) 64.dp else 48.dp)
        ) {
            item {
                ResultHeader(
                    state = state,
                    onBack = onBack,
                    onEvent = onEvent,
                    onPlayEpisode = { ep ->
                        if (showSourcesOnPlay) {
                            onOpenLinksDialog(ep)
                        } else {
                            onPlayEpisode?.invoke(ep)
                        }
                    }
                )
            }

            if (state.isEpisodeBased || state.availableSeasons.isNotEmpty() || state.availableDubStatuses.size > 1) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    ResultEpisodesSelectorHeader(
                        state = state,
                        onEvent = onEvent
                    )
                }
            }

            if (state.isEpisodeBased || state.episodes.isNotEmpty()) {
                resultEpisodesListItems(
                    episodes = state.episodes,
                    selectedEpisode = state.selectedEpisode,
                    onEpisodeClick = { ep ->
                        if (showSourcesOnPlay) {
                            onOpenLinksDialog(ep)
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

        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(if (isTV) 24.dp else 16.dp)
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

@Composable
private fun ResultModalsHost(
    state: ResultState,
    showLinksDialog: Boolean,
    activeEpisodeForLinks: ResultEpisode?,
    onPlayLink: ((ExtractorLink, ImmutableList<ExtractorLink>, ImmutableList<SubtitleFile>, SubtitleFile?) -> Unit)?,
    onPlayEpisode: ((ResultEpisode) -> Unit)?,
    onDownloadEpisode: ((ResultEpisode) -> Unit)?,
    onOpenLinksForEpisode: (ResultEpisode) -> Unit,
    onCloseLinksDialog: () -> Unit,
    onEvent: (ResultEvent) -> Unit,
    player: VideoPlayer?,
    videoPlayerContent: (@Composable (VideoPlayer, Modifier) -> Unit)?
) {
    if (showLinksDialog) {
        ResultLinksDialog(
            state = state,
            targetEpisode = activeEpisodeForLinks ?: state.selectedEpisode,
            onPlayLink = { link, links, subs, initialSub ->
                onPlayLink?.invoke(link, links, subs, initialSub)
                onCloseLinksDialog()
            },
            onEvent = onEvent,
            onDismiss = onCloseLinksDialog
        )
    }

    if (state.isTrailerDialogOpen) {
        TrailerDialog(
            state = state,
            onEvent = onEvent,
            onDismiss = { onEvent(ResultEvent.CloseTrailer) },
            player = player,
            videoPlayerContent = videoPlayerContent
        )
    }

    if (state.isEpisodeMenuOpen && state.selectedMenuEpisode != null) {
        val menuEpisode = state.selectedMenuEpisode
        EpisodeActionDialog(
            episode = menuEpisode,
            isMovie = state.isMovie,
            onDismiss = { onEvent(ResultEvent.CloseEpisodeMenu) },
            onPlayInApp = {
                onEvent(ResultEvent.CloseEpisodeMenu)
                onEvent(ResultEvent.SelectEpisode(menuEpisode))
                if (onPlayEpisode != null) {
                    onPlayEpisode(menuEpisode)
                } else {
                    onEvent(ResultEvent.ReloadLinks(menuEpisode))
                    onOpenLinksForEpisode(menuEpisode)
                }
            },
            onPlayMirror = {
                onEvent(ResultEvent.CloseEpisodeMenu)
                onEvent(ResultEvent.SelectEpisode(menuEpisode))
                onEvent(ResultEvent.ReloadLinks(menuEpisode))
                onOpenLinksForEpisode(menuEpisode)
            },
            onReloadLinks = {
                onEvent(ResultEvent.CloseEpisodeMenu)
                onEvent(ResultEvent.SelectEpisode(menuEpisode))
                onEvent(ResultEvent.ReloadLinks(menuEpisode, clearCache = true))
                onOpenLinksForEpisode(menuEpisode)
            },
            onCopyLink = {
                onEvent(ResultEvent.CloseEpisodeMenu)
                onEvent(ResultEvent.CopyEpisodeLink(menuEpisode))
            },
            onDownload = {
                onEvent(ResultEvent.CloseEpisodeMenu)
                onDownloadEpisode?.invoke(menuEpisode) ?: run {
                    onEvent(ResultEvent.ReloadLinks(menuEpisode))
                    onOpenLinksForEpisode(menuEpisode)
                }
            },
            onDownloadMirror = {
                onEvent(ResultEvent.CloseEpisodeMenu)
                onEvent(ResultEvent.SelectEpisode(menuEpisode))
                onEvent(ResultEvent.ReloadLinks(menuEpisode))
                onOpenLinksForEpisode(menuEpisode)
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

/**
 * Recommendations / Related Media horizontal row.
 */
@Composable
fun RecommendationsSection(
    recommendations: ImmutableList<SearchResponse>,
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

@Preview
@Composable
private fun ResultScreenPreview() {
    com.lagradost.cloudstream3.shared.ui.theme.CloudStreamTheme {
        ResultScreen(
            state = ResultState(
                title = "Example Movie",
                synopsis = "This is an example movie synopsis description.",
                isMovie = true,
                episodes = persistentListOf(
                    ResultEpisode(
                        headerName = "Season 1",
                        id = 1,
                        name = "Movie Feature",
                        episode = 1,
                        season = null,
                        data = "data_url",
                        apiName = "TestProvider",
                        index = 0,
                        tvType = TvType.Movie,
                        parentId = 100
                    )
                )
            ),
            onEvent = {}
        )
    }
}

