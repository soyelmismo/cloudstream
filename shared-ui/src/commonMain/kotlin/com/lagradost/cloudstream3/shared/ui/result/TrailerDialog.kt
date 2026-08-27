package com.lagradost.cloudstream3.shared.ui.result

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cloudstream.shared_ui.generated.resources.*
import com.lagradost.cloudstream3.TrailerData
import com.lagradost.cloudstream3.shared.player.LocalVideoPlayer
import com.lagradost.cloudstream3.shared.player.LocalVideoPlayerContent
import com.lagradost.cloudstream3.shared.player.VideoPlayer
import com.lagradost.cloudstream3.shared.ui.components.designsystem.PrimaryButton
import com.lagradost.cloudstream3.shared.ui.focus.dpadFocusable
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamTheme
import com.lagradost.cloudstream3.shared.viewmodels.result.ResultEvent
import com.lagradost.cloudstream3.shared.viewmodels.result.ResultState
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

private fun formatTrailerTime(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSeconds = ms / 1000L
    val seconds = (totalSeconds % 60L).toString().padStart(2, '0')
    val minutes = ((totalSeconds / 60L) % 60L).toString().padStart(2, '0')
    val hours = totalSeconds / 3600L

    if (hours > 0L) {
        val hStr = hours.toString().padStart(2, '0')
        return "$hStr:$minutes:$seconds"
    }
    return "$minutes:$seconds"
}

@Composable
fun TrailerVideoSurface(
    player: VideoPlayer?,
    videoPlayerContent: (@Composable (VideoPlayer, Modifier) -> Unit)?,
    title: String,
    modifier: Modifier = Modifier
) {
    if (player != null && videoPlayerContent != null) {
        videoPlayerContent(player, modifier.fillMaxSize())
    } else {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(CloudStreamColors.Background),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.h6.copy(
                    color = CloudStreamColors.TextSecondary,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

@Composable
fun TrailerLoadingOverlay(
    isExtracting: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CloudStreamColors.Background.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(
                color = CloudStreamColors.Primary,
                strokeWidth = 3.dp,
                modifier = Modifier.size(44.dp)
            )
            val labelRes = if (isExtracting) Res.string.loadingTrailer else Res.string.loading
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.body2.copy(
                    color = CloudStreamColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp
                )
            )
        }
    }
}

@Composable
fun TrailerErrorBanner(
    errorMessage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CloudStreamColors.SurfaceVariant.copy(alpha = 0.95f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = stringResource(Res.string.error),
                tint = CloudStreamColors.Error,
                modifier = Modifier.size(42.dp)
            )

            Text(
                text = errorMessage,
                style = MaterialTheme.typography.body1.copy(
                    color = CloudStreamColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            )

            PrimaryButton(
                text = stringResource(Res.string.retry),
                icon = Icons.Default.Refresh,
                onClick = onRetry
            )
        }
    }
}

@Composable
fun TrailerQualityMenu(
    links: ImmutableList<ExtractorLink>,
    activeLink: ExtractorLink?,
    onSelectQuality: (ExtractorLink) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val qualityLabel = Qualities.getStringByInt(activeLink?.quality).ifBlank { stringResource(Res.string.quality_auto) }

    Box(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = CloudStreamColors.SurfaceElevated,
            border = BorderStroke(1.dp, CloudStreamColors.Divider),
            modifier = Modifier.dpadFocusable(
                onClick = { expanded = true },
                shape = RoundedCornerShape(8.dp)
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_baseline_hd_24),
                    contentDescription = stringResource(Res.string.quality),
                    tint = CloudStreamColors.Secondary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = qualityLabel,
                    style = MaterialTheme.typography.caption.copy(
                        fontWeight = FontWeight.Bold,
                        color = CloudStreamColors.TextPrimary,
                        fontSize = 11.5.sp
                    )
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(CloudStreamColors.SurfaceElevated)
        ) {
            links.forEach { link ->
                val isSelected = link.url == activeLink?.url
                val label = Qualities.getStringByIntFull(link.quality).ifBlank { link.name }
                DropdownMenuItem(
                    onClick = {
                        onSelectQuality(link)
                        expanded = false
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) CloudStreamColors.Secondary else CloudStreamColors.TextPrimary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                        if (isSelected) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = CloudStreamColors.Secondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TrailerMultiSelector(
    trailers: ImmutableList<TrailerData>,
    selectedIndex: Int,
    onSelectTrailer: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        itemsIndexed(trailers) { idx, _ ->
            val isSelected = idx == selectedIndex
            val chipColor = if (isSelected) CloudStreamColors.Primary.copy(alpha = 0.35f) else CloudStreamColors.SurfaceElevated.copy(alpha = 0.6f)
            val borderColor = if (isSelected) CloudStreamColors.Primary else CloudStreamColors.Divider

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = chipColor,
                border = BorderStroke(1.dp, borderColor),
                modifier = Modifier.dpadFocusable(
                    onClick = {
                        if (idx != selectedIndex) {
                            onSelectTrailer(idx)
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                )
            ) {
                Text(
                    text = "${stringResource(Res.string.trailer)} ${idx + 1}",
                    style = MaterialTheme.typography.caption.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) CloudStreamColors.TextPrimary else CloudStreamColors.TextSecondary,
                        fontSize = 11.sp
                    ),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.5.dp)
                )
            }
        }
    }
}

@Composable
fun TrailerHeaderBar(
    title: String,
    selectedIndex: Int,
    trailers: ImmutableList<TrailerData>,
    extractedLinks: ImmutableList<ExtractorLink>,
    activeLink: ExtractorLink?,
    onSelectQuality: (ExtractorLink) -> Unit,
    onSelectTrailer: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val trailerLabel = stringResource(Res.string.trailer)
    val displayHeader = if (trailers.size > 1) {
        "$title • $trailerLabel ${selectedIndex + 1}"
    } else {
        "$title • $trailerLabel"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = CloudStreamColors.Primary.copy(alpha = 0.25f),
                    border = BorderStroke(1.dp, CloudStreamColors.Primary.copy(alpha = 0.6f))
                ) {
                    Text(
                        text = trailerLabel.uppercase(),
                        style = MaterialTheme.typography.caption.copy(
                            fontWeight = FontWeight.Bold,
                            color = CloudStreamColors.Primary,
                            fontSize = 10.5.sp
                        ),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                    )
                }

                Text(
                    text = displayHeader,
                    style = MaterialTheme.typography.subtitle1.copy(
                        fontWeight = FontWeight.Bold,
                        color = CloudStreamColors.TextPrimary,
                        fontSize = 15.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (extractedLinks.isNotEmpty()) {
                    TrailerQualityMenu(
                        links = extractedLinks,
                        activeLink = activeLink,
                        onSelectQuality = onSelectQuality
                    )
                }

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(CloudStreamColors.Background.copy(alpha = 0.65f), CircleShape)
                        .dpadFocusable(
                            onClick = onClose,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(Res.string.close),
                        tint = CloudStreamColors.TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        if (trailers.size > 1) {
            Spacer(modifier = Modifier.height(6.dp))
            TrailerMultiSelector(
                trailers = trailers,
                selectedIndex = selectedIndex,
                onSelectTrailer = onSelectTrailer
            )
        }
    }
}

@Composable
fun TrailerPlaybackCenterControls(
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(CloudStreamColors.Background.copy(alpha = 0.5f), CircleShape)
                .dpadFocusable(
                    onClick = {
                        val targetPos = (positionMs - 10_000L).coerceAtLeast(0L)
                        onSeek(targetPos)
                    },
                    shape = CircleShape,
                    scaleOnFocus = 1.12f
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(Res.drawable.netflix_skip_back),
                contentDescription = stringResource(Res.string.skip_backward_10s),
                tint = CloudStreamColors.TextPrimary,
                modifier = Modifier.size(24.dp)
            )
        }

        Box(
            modifier = Modifier
                .size(56.dp)
                .background(CloudStreamColors.Primary, CircleShape)
                .dpadFocusable(
                    onClick = onTogglePlay,
                    shape = CircleShape,
                    scaleOnFocus = 1.12f
                ),
            contentAlignment = Alignment.Center
        ) {
            val iconRes = if (isPlaying) Res.drawable.netflix_pause else Res.drawable.netflix_play
            val descRes = if (isPlaying) Res.string.pause else Res.string.action_play
            Icon(
                painter = painterResource(iconRes),
                contentDescription = stringResource(descRes),
                tint = MaterialTheme.colors.onPrimary,
                modifier = Modifier.size(30.dp)
            )
        }

        Box(
            modifier = Modifier
                .size(44.dp)
                .background(CloudStreamColors.Background.copy(alpha = 0.5f), CircleShape)
                .dpadFocusable(
                    onClick = {
                        val targetPos = (positionMs + 10_000L).coerceAtMost(durationMs)
                        onSeek(targetPos)
                    },
                    shape = CircleShape,
                    scaleOnFocus = 1.12f
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(Res.drawable.netflix_skip_forward),
                contentDescription = stringResource(Res.string.skip_forward_10s),
                tint = CloudStreamColors.TextPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun TrailerBottomBar(
    currentPositionMs: Long,
    durationMs: Long,
    activeTrailerLink: ExtractorLink?,
    onSeek: (Long) -> Unit,
    onScrubbingChange: (Boolean, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val progressFraction = if (durationMs > 0L) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${formatTrailerTime(currentPositionMs)} / ${formatTrailerTime(durationMs)}",
                style = MaterialTheme.typography.caption.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = CloudStreamColors.TextPrimary,
                    fontSize = 11.5.sp
                )
            )

            if (activeTrailerLink != null) {
                Text(
                    text = activeTrailerLink.name.ifBlank { stringResource(Res.string.trailer_stream) },
                    style = MaterialTheme.typography.caption.copy(
                        color = CloudStreamColors.TextMuted,
                        fontSize = 11.sp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Slider(
            value = progressFraction,
            onValueChange = { fraction ->
                val newPos = (fraction * durationMs).toLong()
                onScrubbingChange(true, newPos)
            },
            onValueChangeFinished = {
                onSeek(currentPositionMs)
                onScrubbingChange(false, currentPositionMs)
            },
            colors = SliderDefaults.colors(
                thumbColor = CloudStreamColors.Primary,
                activeTrackColor = CloudStreamColors.Primary,
                inactiveTrackColor = CloudStreamColors.Divider
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
        )
    }
}

@Composable
fun TrailerControlsOverlay(
    visible: Boolean,
    title: String,
    selectedIndex: Int,
    trailers: ImmutableList<TrailerData>,
    extractedLinks: ImmutableList<ExtractorLink>,
    activeLink: ExtractorLink?,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    onClose: () -> Unit,
    onSelectQuality: (ExtractorLink) -> Unit,
    onSelectTrailer: (Int) -> Unit,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onScrubbingChange: (Boolean, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            CloudStreamColors.Background.copy(alpha = 0.85f),
                            Color.Transparent,
                            CloudStreamColors.Background.copy(alpha = 0.88f)
                        )
                    )
                )
        ) {
            TrailerHeaderBar(
                title = title,
                selectedIndex = selectedIndex,
                trailers = trailers,
                extractedLinks = extractedLinks,
                activeLink = activeLink,
                onSelectQuality = onSelectQuality,
                onSelectTrailer = onSelectTrailer,
                onClose = onClose,
                modifier = Modifier.align(Alignment.TopCenter)
            )

            TrailerPlaybackCenterControls(
                isPlaying = isPlaying,
                positionMs = currentPositionMs,
                durationMs = durationMs,
                onTogglePlay = onTogglePlay,
                onSeek = onSeek,
                modifier = Modifier.align(Alignment.Center)
            )

            TrailerBottomBar(
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
                activeTrailerLink = activeLink,
                onSeek = onSeek,
                onScrubbingChange = onScrubbingChange,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
fun TrailerDialog(
    state: ResultState,
    onEvent: (ResultEvent) -> Unit,
    onDismiss: () -> Unit,
    player: VideoPlayer? = null,
    videoPlayerContent: (@Composable (VideoPlayer, Modifier) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val effectivePlayer = player ?: LocalVideoPlayer.current
    val effectiveVideoPlayerContent = videoPlayerContent ?: LocalVideoPlayerContent.current
    val playerState = effectivePlayer?.stateFlow?.collectAsState()?.value

    LaunchedEffect(state.selectedTrailerIndex) {
        if (state.extractedTrailerLinks.isEmpty() && !state.isExtractingTrailer && state.trailerExtractionError == null) {
            onEvent(ResultEvent.LoadTrailer(state.selectedTrailerIndex))
        }
    }

    val activeTrailerLink = state.selectedTrailerQuality ?: state.extractedTrailerLinks.firstOrNull()

    LaunchedEffect(activeTrailerLink?.url) {
        val url = activeTrailerLink?.url
        if (!url.isNullOrBlank()) {
            effectivePlayer?.play(url, activeTrailerLink.getAllHeaders())
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            effectivePlayer?.pause()
        }
    }

    var areControlsVisible by remember { mutableStateOf(true) }
    var userInteractionCount by remember { mutableStateOf(0) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableStateOf(0L) }

    val isPlaying = playerState?.isPlaying == true
    val isBuffering = playerState?.isBuffering == true || state.isExtractingTrailer

    LaunchedEffect(areControlsVisible, isPlaying, userInteractionCount, isScrubbing) {
        if (areControlsVisible && isPlaying && !isScrubbing) {
            delay(3500)
            areControlsVisible = false
        }
    }

    val currentPosition = if (isScrubbing) scrubPositionMs else (playerState?.positionMs ?: 0L)
    val duration = playerState?.durationMs ?: 0L
    val allTrailers = state.trailers.ifEmpty { state.loadResponse?.trailers?.toImmutableList() ?: persistentListOf() }

    Dialog(
        onDismissRequest = {
            onEvent(ResultEvent.CloseTrailer)
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        CloudStreamTheme {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(CloudStreamColors.Background.copy(alpha = 0.94f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            onEvent(ResultEvent.CloseTrailer)
                            onDismiss()
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    backgroundColor = CloudStreamColors.Surface,
                    border = BorderStroke(1.dp, CloudStreamColors.Divider.copy(alpha = 0.5f)),
                    elevation = 24.dp,
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                        .widthIn(max = 940.dp)
                        .aspectRatio(16f / 10f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                userInteractionCount++
                                areControlsVisible = !areControlsVisible
                            }
                        )
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        TrailerVideoSurface(
                            player = effectivePlayer,
                            videoPlayerContent = effectiveVideoPlayerContent,
                            title = state.title
                        )

                        if (isBuffering && state.trailerExtractionError == null) {
                            TrailerLoadingOverlay(isExtracting = state.isExtractingTrailer)
                        }

                        if (state.trailerExtractionError != null && !state.isExtractingTrailer) {
                            TrailerErrorBanner(
                                errorMessage = state.trailerExtractionError,
                                onRetry = { onEvent(ResultEvent.LoadTrailer(state.selectedTrailerIndex)) }
                            )
                        }

                        val showOverlay = areControlsVisible || !isPlaying || state.trailerExtractionError != null
                        TrailerControlsOverlay(
                            visible = showOverlay,
                            title = state.title,
                            selectedIndex = state.selectedTrailerIndex,
                            trailers = allTrailers,
                            extractedLinks = state.extractedTrailerLinks,
                            activeLink = activeTrailerLink,
                            isPlaying = isPlaying,
                            currentPositionMs = currentPosition,
                            durationMs = duration,
                            onClose = {
                                onEvent(ResultEvent.CloseTrailer)
                                onDismiss()
                            },
                            onSelectQuality = { link -> onEvent(ResultEvent.SelectTrailerQuality(link)) },
                            onSelectTrailer = { idx -> onEvent(ResultEvent.LoadTrailer(idx)) },
                            onTogglePlay = {
                                userInteractionCount++
                                effectivePlayer?.let { p ->
                                    if (isPlaying) p.pause() else p.resume()
                                }
                            },
                            onSeek = { pos ->
                                userInteractionCount++
                                effectivePlayer?.seekTo(pos)
                            },
                            onScrubbingChange = { scrubbing, pos ->
                                userInteractionCount++
                                isScrubbing = scrubbing
                                scrubPositionMs = pos
                            }
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun TrailerDialogPreview() {
    CloudStreamTheme {
        TrailerDialog(
            state = ResultState(
                title = "Interstellar Trailer",
                trailers = persistentListOf(
                    TrailerData(
                        extractorUrl = "https://example.com/trailer",
                        referer = null,
                        raw = false
                    )
                )
            ),
            onEvent = {},
            onDismiss = {}
        )
    }
}

