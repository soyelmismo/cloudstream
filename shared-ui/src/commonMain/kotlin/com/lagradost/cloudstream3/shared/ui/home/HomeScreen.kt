package com.lagradost.cloudstream3.shared.ui.home

import com.lagradost.cloudstream3.utils.asString
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.shared.ui.components.designsystem.CloudStreamEmptyState
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamTheme
import com.lagradost.cloudstream3.shared.viewmodels.HomeCarousel
import com.lagradost.cloudstream3.shared.viewmodels.HomeEffect
import com.lagradost.cloudstream3.shared.viewmodels.HomeEvent
import org.jetbrains.compose.resources.stringResource
import cloudstream.shared_ui.generated.resources.*
import com.lagradost.cloudstream3.shared.viewmodels.HomeState
import com.lagradost.cloudstream3.shared.viewmodels.HomeViewModel
import kotlinx.coroutines.flow.collectLatest

/**
 * Stateful Home Screen connected to [HomeViewModel].
 * Observes [HomeViewModel.state] and processes [HomeViewModel.effects].
 *
 * @param viewModel The [HomeViewModel] instance managing state and intents.
 * @param onNavigateToDetails Callback when user selects a media card or banner item.
 * @param modifier Optional modifier.
 * @param onSearchClick Optional callback when search action is clicked in top bar.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToDetails: (SearchResponse, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onSearchClick: (() -> Unit)? = null
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is HomeEffect.NavigateToDetails -> {
                    onNavigateToDetails(effect.item, effect.autoResume)
                }
                is HomeEffect.ShowToast -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
            }
        }
    }

    HomeScreenContent(
        state = state,
        onEvent = viewModel::handleEvent,
        onNavigateToDetails = onNavigateToDetails,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
        onSearchClick = onSearchClick
    )
}

/**
 * Stateless Home Screen content composable for testing, previews, and responsive layouts.
 *
 * @param state The current [HomeState].
 * @param onEvent Callback to dispatch UI intents.
 * @param onNavigateToDetails Callback when user navigates to media details.
 * @param snackbarHostState Optional snackbar host state.
 * @param modifier Optional modifier.
 * @param onSearchClick Optional callback when search action is clicked.
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun HomeScreenContent(
    state: HomeState,
    onEvent: (HomeEvent) -> Unit,
    onNavigateToDetails: (SearchResponse, Boolean) -> Unit = { _, _ -> },
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    modifier: Modifier = Modifier,
    onSearchClick: (() -> Unit)? = null
) {
    val lazyListState = rememberLazyListState()
    val pullRefreshState = rememberPullRefreshState(
        refreshing = state.isRefreshing,
        onRefresh = { onEvent(HomeEvent.RefreshHome) }
    )

    val hasAnyContent = state.resumeWatching.isNotEmpty() || state.carousels.isNotEmpty() || state.featuredItems.isNotEmpty()

    Scaffold(
        backgroundColor = CloudStreamColors.Background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            HomeTopBar(
                state = state,
                onEvent = onEvent,
                onSearchClick = onSearchClick
            )
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(CloudStreamColors.Background)
                .pullRefresh(pullRefreshState)
        ) {
            when {
                // Fullscreen Loading State (Initial load with zero cached or local content)
                state.isLoading && !hasAnyContent -> {
                    HomeLoadingView()
                }

                // Fullscreen Error State (Initial load failure with zero cached or local content)
                state.error != null && !hasAnyContent -> {
                    HomeErrorView(
                        errorMessage = state.error.asString(),
                        onRetry = { onEvent(HomeEvent.RefreshHome) }
                    )
                }

                // Main Home Content (Featured Hero Banner + Resume Watching + Provider Carousels)
                else -> {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Hero Banner Item (Top)
                        if (state.featuredItems.isNotEmpty()) {
                            item(key = "home_hero_banner") {
                                HeroBanner(
                                    items = state.featuredItems,
                                    onItemClick = { item ->
                                        onEvent(HomeEvent.SelectItem(item))
                                    },
                                    onPlayClick = { item ->
                                        onEvent(HomeEvent.ResumeItem(item))
                                    }
                                )
                            }
                        }

                        // Resume Watching Carousel (Top priority below hero banner across all providers)
                        if (state.resumeWatching.isNotEmpty()) {
                            item(key = "home_resume_watching") {
                                HomeCarouselView(
                                    carousel = HomeCarousel(
                                        name = stringResource(Res.string.typeResumeWatching),
                                        items = state.resumeWatching
                                    ),
                                    headerIcon = Icons.Default.History,
                                    onItemClick = { item ->
                                        onEvent(HomeEvent.SelectItem(item))
                                    },
                                    onPlayClick = { item ->
                                        onEvent(HomeEvent.ResumeItem(item))
                                    },
                                    onItemLongClick = { item ->
                                        onEvent(HomeEvent.RemoveFromResumeWatching(item))
                                    },
                                    onExpandCarousel = {},
                                    progressMap = state.resumeWatchingProgress
                                )
                            }
                        }

                        // Content Carousels Rows with consistent fluid spacing
                        items(
                            items = state.carousels,
                            key = { it.name }
                        ) { carousel ->
                            HomeCarouselView(
                                carousel = carousel,
                                onItemClick = { item ->
                                    onEvent(HomeEvent.SelectItem(item))
                                },
                                onPlayClick = { item ->
                                    onEvent(HomeEvent.ResumeItem(item))
                                },
                                onExpandCarousel = { carouselName ->
                                    onEvent(HomeEvent.ExpandCarousel(carouselName))
                                }
                            )
                        }

                        // Provider inline loading state when carousels are loading but resume watching is already visible
                        if (state.isLoading && state.carousels.isEmpty()) {
                            item(key = "home_provider_loading") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            color = CloudStreamColors.Primary,
                                            strokeWidth = 2.5.dp,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Text(
                                            text = stringResource(Res.string.loading),
                                            color = CloudStreamColors.TextSecondary,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Provider inline error state when carousels failed but resume watching is visible
                        if (!state.isLoading && state.error != null && state.carousels.isEmpty()) {
                            item(key = "home_provider_error") {
                                HomeErrorView(
                                    errorMessage = state.error.asString(),
                                    onRetry = { onEvent(HomeEvent.RefreshHome) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                )
                            }
                        }

                        // Bottom spacer for navigation bars and safe scrolling area
                        item(key = "home_bottom_spacer") {
                            Spacer(modifier = Modifier.height(40.dp))
                        }
                    }
                }
            }

            // Subtle top linear progress indicator when loading provider data while keeping resume watching interactive
            if (state.isLoading && hasAnyContent) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.TopCenter),
                    color = CloudStreamColors.Primary,
                    backgroundColor = Color.Transparent
                )
            }

            PullRefreshIndicator(
                refreshing = state.isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                backgroundColor = CloudStreamColors.SurfaceElevated,
                contentColor = CloudStreamColors.Primary
            )
        }
    }
}

/**
 * Fullscreen loading placeholder with modern glowing branding spinner.
 */
@Composable
private fun HomeLoadingView(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = CloudStreamColors.SurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 36.dp, vertical = 28.dp)
            ) {
                CircularProgressIndicator(
                    color = CloudStreamColors.Primary,
                    strokeWidth = 3.5.dp,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = stringResource(Res.string.loading),
                    color = CloudStreamColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Fullscreen modern error view with retry action when provider content fails to load using centralized [CloudStreamEmptyState].
 */
@Composable
private fun HomeErrorView(
    errorMessage: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    CloudStreamEmptyState(
        icon = Icons.Default.CloudOff,
        iconTint = CloudStreamColors.Error,
        iconBackgroundColor = CloudStreamColors.Error.copy(alpha = 0.15f),
        iconBorderColor = null,
        iconContainerSize = 72.dp,
        iconSize = 36.dp,
        titleRes = Res.string.noHomeContent,
        subtitle = if (!errorMessage.isNullOrBlank()) errorMessage else stringResource(Res.string.noHomeContentDesc),
        actionTextRes = Res.string.retry,
        actionIcon = Icons.Default.Refresh,
        onActionClick = onRetry,
        useCardContainer = true,
        modifier = modifier
    )
}

