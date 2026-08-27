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
import androidx.compose.material.IconButton
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
import com.lagradost.cloudstream3.shared.player.LocalVideoPlayer
import com.lagradost.cloudstream3.shared.player.LocalVideoPlayerContent
import com.lagradost.cloudstream3.shared.player.VideoPlayer
import com.lagradost.cloudstream3.shared.ui.components.designsystem.PrimaryButton
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamTheme
import com.lagradost.cloudstream3.shared.viewmodels.result.ResultEvent
import com.lagradost.cloudstream3.shared.viewmodels.result.ResultState
import com.lagradost.cloudstream3.utils.Qualities
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Formats a duration in milliseconds to a mm:ss or hh:mm:ss string.
 */
private fun formatTrailerTime(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSeconds = ms / 1000L
    val seconds = totalSeconds % 60L
    val minutes = (totalSeconds / 60L) % 60L
    val hours = totalSeconds / 3600L

    return if (hours > 0L) {
        val hStr = hours.toString().padStart(2, '0')
        val mStr = minutes.toString().padStart(2, '0')
        val sStr = seconds.toString().padStart(2, '0')
        "$hStr:$mStr:$sStr"
    } else {
        val mStr = minutes.toString().padStart(2, '0')
        val sStr = seconds.toString().padStart(2, '0')
        "$mStr:$sStr"
    }
}

/**
 * Modal Cinematic Dark Trailer Viewer Dialog for Compose Multiplatform.
 *
 * Features:
 * - Immersive card overlay with semantic design system tokens.
 * - Multi-trailer horizontal selector when multiple trailers are available.
 * - Platform-agnostic video player surface layer with automatic loading/streaming.
 * - Overlay controls: floating close button, media & trailer title, seek bar, time display,
 *   play/pause, skip 10s backward/forward, quality selector dropdown, and buffering indicator.
 * - Auto-hiding overlay during active playback.
 */
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

    // Auto-trigger extraction if not already started
    LaunchedEffect(state.selectedTrailerIndex) {
        if (state.extractedTrailerLinks.isEmpty() && !state.isExtractingTrailer && state.trailerExtractionError == null) {
            onEvent(ResultEvent.LoadTrailer(state.selectedTrailerIndex))
        }
    }

    // Active trailer streaming link
    val activeTrailerLink = state.selectedTrailerQuality ?: state.extractedTrailerLinks.firstOrNull()

    // Play stream when link is available
    LaunchedEffect(activeTrailerLink?.url) {
        val url = activeTrailerLink?.url
        if (!url.isNullOrBlank()) {
            effectivePlayer?.play(url, activeTrailerLink.getAllHeaders())
        }
    }

    // Pause/Stop playback on dismiss/dispose
    DisposableEffect(Unit) {
        onDispose {
            effectivePlayer?.pause()
        }
    }

    // Controls visibility and auto-hide logic
    var areControlsVisible by remember { mutableStateOf(true) }
    var userInteractionCount by remember { mutableStateOf(0) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableStateOf(0L) }
    var showQualityMenu by remember { mutableStateOf(false) }

    val isPlaying = playerState?.isPlaying == true
    val isBuffering = playerState?.isBuffering == true || state.isExtractingTrailer

    // Auto-hide controls after 3.5 seconds of active playback without user interaction
    LaunchedEffect(areControlsVisible, isPlaying, userInteractionCount, isScrubbing) {
        if (areControlsVisible && isPlaying && !isScrubbing) {
            delay(3500)
            areControlsVisible = false
        }
    }

    val currentPosition = if (isScrubbing) scrubPositionMs else (playerState?.positionMs ?: 0L)
    val duration = playerState?.durationMs ?: 0L
    val progressFraction = if (duration > 0L) (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

    val allTrailers = state.trailers.ifEmpty { state.loadResponse?.trailers ?: emptyList() }
    val hasMultipleTrailers = allTrailers.size > 1

    Dialog(
        onDismissRequest = {
            onEvent(ResultEvent.CloseTrailer)
            onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
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
                // Video Container Card
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
                        // 1. Video Surface Canvas
                        if (effectivePlayer != null && effectiveVideoPlayerContent != null) {
                            effectiveVideoPlayerContent(effectivePlayer, Modifier.fillMaxSize())
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(CloudStreamColors.Background),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = state.title,
                                    style = MaterialTheme.typography.h6.copy(
                                        color = CloudStreamColors.TextSecondary,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }

                        // 2. Loading / Buffering Spinner
                        if (isBuffering && state.trailerExtractionError == null) {
                            Box(
                                modifier = Modifier
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
                                    Text(
                                        text = if (state.isExtractingTrailer) stringResource(Res.string.loadingTrailer) else stringResource(Res.string.loading),
                                        style = MaterialTheme.typography.body2.copy(
                                            color = CloudStreamColors.TextPrimary,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.5.sp
                                        )
                                    )
                                }
                            }
                        }

                        // 3. Error Banner View
                        if (state.trailerExtractionError != null && !state.isExtractingTrailer) {
                            Box(
                                modifier = Modifier
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
                                        text = state.trailerExtractionError,
                                        style = MaterialTheme.typography.body1.copy(
                                            color = CloudStreamColors.TextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                    )

                                    PrimaryButton(
                                        text = stringResource(Res.string.retry),
                                        icon = Icons.Default.Refresh,
                                        onClick = {
                                            onEvent(ResultEvent.LoadTrailer(state.selectedTrailerIndex))
                                        }
                                    )
                                }
                            }
                        }

                        // 4. Interactive Overlay Layer (Top bar, multi-trailer selector, center & bottom controls)
                        AnimatedVisibility(
                            visible = areControlsVisible || !isPlaying || state.trailerExtractionError != null,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier.fillMaxSize()
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
                                // -------------------------------------------------------------
                                // Top Header Bar
                                // -------------------------------------------------------------
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.TopCenter)
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                    ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Media & Trailer Title
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            val trailerLabel = stringResource(Res.string.trailer)
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
                                                text = if (allTrailers.size > 1) {
                                                    "${state.title} • $trailerLabel ${state.selectedTrailerIndex + 1}"
                                                } else {
                                                    "${state.title} • $trailerLabel"
                                                },
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
                                            // Quality Selector Button
                                            if (state.extractedTrailerLinks.isNotEmpty()) {
                                                Box {
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = CloudStreamColors.SurfaceElevated,
                                                        border = BorderStroke(1.dp, CloudStreamColors.Divider),
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .clickable { showQualityMenu = true }
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
                                                            val qualityLabel = Qualities.getStringByInt(activeTrailerLink?.quality).ifBlank { stringResource(Res.string.quality_auto) }
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
                                                        expanded = showQualityMenu,
                                                        onDismissRequest = { showQualityMenu = false },
                                                        modifier = Modifier.background(CloudStreamColors.SurfaceElevated)
                                                    ) {
                                                        state.extractedTrailerLinks.forEach { link ->
                                                            val isSelected = link.url == activeTrailerLink?.url
                                                            val label = Qualities.getStringByIntFull(link.quality).ifBlank { link.name }
                                                            DropdownMenuItem(
                                                                onClick = {
                                                                    onEvent(ResultEvent.SelectTrailerQuality(link))
                                                                    showQualityMenu = false
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

                                            // Floating Close Button
                                            IconButton(
                                                onClick = {
                                                    onEvent(ResultEvent.CloseTrailer)
                                                    onDismiss()
                                                },
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .background(CloudStreamColors.Background.copy(alpha = 0.65f), CircleShape)
                                                    .clip(CircleShape)
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

                                    // Multi-Trailer Selector Chips (if > 1 trailer available)
                                    if (hasMultipleTrailers) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            itemsIndexed(allTrailers) { idx, _ ->
                                                val isSelected = idx == state.selectedTrailerIndex
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = if (isSelected) CloudStreamColors.Primary.copy(alpha = 0.35f) else CloudStreamColors.SurfaceElevated.copy(alpha = 0.6f),
                                                    border = BorderStroke(
                                                        1.dp,
                                                        if (isSelected) CloudStreamColors.Primary else CloudStreamColors.Divider
                                                    ),
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .clickable {
                                                            if (idx != state.selectedTrailerIndex) {
                                                                onEvent(ResultEvent.LoadTrailer(idx))
                                                            }
                                                        }
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
                                }

                                // -------------------------------------------------------------
                                // Center Quick Controls (Play / Pause / Seek 10s)
                                // -------------------------------------------------------------
                                Row(
                                    modifier = Modifier.align(Alignment.Center),
                                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Seek -10s
                                    IconButton(
                                        onClick = {
                                            userInteractionCount++
                                            val newPos = ((playerState?.positionMs ?: 0L) - 10_000L).coerceAtLeast(0L)
                                            effectivePlayer?.seekTo(newPos)
                                        },
                                        modifier = Modifier
                                            .size(44.dp)
                                            .background(CloudStreamColors.Background.copy(alpha = 0.5f), CircleShape)
                                            .clip(CircleShape)
                                    ) {
                                        Icon(
                                            painter = painterResource(Res.drawable.netflix_skip_back),
                                            contentDescription = stringResource(Res.string.skip_backward_10s),
                                            tint = CloudStreamColors.TextPrimary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    // Main Play / Pause Circle
                                    IconButton(
                                        onClick = {
                                            userInteractionCount++
                                            if (isPlaying) {
                                                effectivePlayer.pause()
                                            } else {
                                                effectivePlayer?.resume()
                                            }
                                        },
                                        modifier = Modifier
                                            .size(56.dp)
                                            .background(CloudStreamColors.Primary, CircleShape)
                                            .clip(CircleShape)
                                    ) {
                                        Icon(
                                            painter = painterResource(if (isPlaying) Res.drawable.netflix_pause else Res.drawable.netflix_play),
                                            contentDescription = stringResource(if (isPlaying) Res.string.pause else Res.string.action_play),
                                            tint = MaterialTheme.colors.onPrimary,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }

                                    // Seek +10s
                                    IconButton(
                                        onClick = {
                                            userInteractionCount++
                                            val newPos = ((playerState?.positionMs ?: 0L) + 10_000L).coerceAtMost(duration)
                                            effectivePlayer?.seekTo(newPos)
                                        },
                                        modifier = Modifier
                                            .size(44.dp)
                                            .background(CloudStreamColors.Background.copy(alpha = 0.5f), CircleShape)
                                            .clip(CircleShape)
                                    ) {
                                        Icon(
                                            painter = painterResource(Res.drawable.netflix_skip_forward),
                                            contentDescription = stringResource(Res.string.skip_forward_10s),
                                            tint = CloudStreamColors.TextPrimary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }

                                // -------------------------------------------------------------
                                // Bottom Playback Bar
                                // -------------------------------------------------------------
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.BottomCenter)
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    // Time Indicator + Quality Label
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${formatTrailerTime(currentPosition)} / ${formatTrailerTime(duration)}",
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

                                    // Scrubbing Seek Bar Slider
                                    Slider(
                                        value = progressFraction,
                                        onValueChange = { fraction ->
                                            isScrubbing = true
                                            scrubPositionMs = (fraction * duration).toLong()
                                            userInteractionCount++
                                        },
                                        onValueChangeFinished = {
                                            effectivePlayer?.seekTo(scrubPositionMs)
                                            isScrubbing = false
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
                        }
                    }
                }
            }
        }
    }
}
