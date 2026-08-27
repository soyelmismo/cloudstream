package com.lagradost.cloudstream3.shared.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvSeriesSearchResponse
import com.lagradost.cloudstream3.shared.ui.components.designsystem.dsCombinedClickable
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors

/**
 * Standard poster card dimensions following cinematic aspect ratios.
 */
object MediaCardDefaults {
    val VerticalWidth = 112.dp
    val HorizontalWidth = 195.dp
    const val AspectRatioVertical = 2f / 3f
    const val AspectRatioHorizontal = 16f / 9f
    val CornerRadius = 10.dp
}

/**
 * Modern Media Card component with sophisticated hover/focus states, gradients, and metadata badges.
 *
 * @param item The [SearchResponse] media item to display.
 * @param isHorizontal If true, renders a 16:9 landscape banner. Otherwise, a 2:3 vertical poster.
 * @param onClick Callback when the card is clicked.
 * @param onLongClick Callback when the card is long-clicked.
 * @param onPlayClick Optional callback when the play button overlay is clicked for quick play.
 * @param progress Optional progress value (0f to 1f) for resume watching.
 * @param modifier Optional modifier.
 * @param cardWidth Custom width override, defaults to [MediaCardDefaults.VerticalWidth] or [MediaCardDefaults.HorizontalWidth].
 * @param watchStatus Optional watch status enum integer for overlay badge.
 */
@Composable
fun MediaCard(
    item: SearchResponse,
    isHorizontal: Boolean = false,
    onClick: (SearchResponse) -> Unit,
    onLongClick: ((SearchResponse) -> Unit)? = null,
    onPlayClick: ((SearchResponse) -> Unit)? = null,
    progress: Float? = null,
    modifier: Modifier = Modifier,
    cardWidth: Dp = if (isHorizontal) MediaCardDefaults.HorizontalWidth else MediaCardDefaults.VerticalWidth,
    watchStatus: Int? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val isHighlighted = isHovered || isFocused

    // Smooth hover / D-Pad focus / touch press micro-animations
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else if (isHighlighted) 1.08f else 1f,
        animationSpec = tween(durationMillis = 200)
    )

    val elevation by animateDpAsState(
        targetValue = if (isHighlighted) 10.dp else 2.dp,
        animationSpec = tween(durationMillis = 200)
    )

    val borderColor = if (isHighlighted) CloudStreamColors.Primary else Color.Transparent

    val aspectRatio = if (isHorizontal) MediaCardDefaults.AspectRatioHorizontal else MediaCardDefaults.AspectRatioVertical
    val cardShape = RoundedCornerShape(MediaCardDefaults.CornerRadius)

    Column(
        modifier = modifier
            .width(cardWidth)
            .pointerHoverIcon(PointerIcon.Hand)
            .dsCombinedClickable(
                onClick = { onClick(item) },
                onLongClick = { onLongClick?.invoke(item) },
                interactionSource = interactionSource,
                scale = scale
            )
    ) {
        // Media Poster Card Surface
        Card(
            shape = cardShape,
            backgroundColor = CloudStreamColors.Surface,
            elevation = elevation,
            border = BorderStroke(if (isHighlighted) 2.dp else 0.5.dp, borderColor),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Media Poster Image
                AsyncImage(
                    url = item.posterUrl,
                    contentDescription = item.name,
                    headers = item.posterHeaders,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Top gradient for badge contrast and legibility
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.70f),
                                    Color.Black.copy(alpha = 0.30f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Badges Row (Top)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Badges: Quality and Type
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        QualityBadge(quality = item.quality)
                        TypeBadge(type = item.type)
                    }

                    // Right Badges: Dub/Sub
                    DubSubBadges(searchResponse = item)
                }

                // Watch Status Overlay Badge (Bottom Left)
                if (watchStatus != null && watchStatus > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                    ) {
                        WatchStatusBadge(watchType = watchStatus)
                    }
                }

                // Provider Tag Badge (Bottom Right overlay if available)
                if (item.apiName.isNotBlank() && !isHorizontal) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                    ) {
                        ProviderBadge(apiName = item.apiName)
                    }
                }

                // Hover / TV D-Pad Focus Play Button Overlay
                androidx.compose.animation.AnimatedVisibility(
                    visible = isHighlighted,
                    enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.85f),
                    exit = fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.85f),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = CloudStreamColors.Primary,
                            elevation = 8.dp,
                            modifier = Modifier
                                .size(44.dp)
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    onPlayClick?.invoke(item) ?: onClick(item)
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = MaterialTheme.colors.onPrimary,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    }
                }

                // Bottom Gradient for Horizontal cards with title overlay
                if (isHorizontal) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.6f),
                                        Color.Black.copy(alpha = 0.92f)
                                    )
                                )
                            )
                    )
                }

                // Playback progress bar for resume watching
                if (progress != null && progress > 0f) {
                    val clampedProgress = progress.coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .align(Alignment.BottomCenter)
                            .background(CloudStreamColors.SurfaceElevated)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(clampedProgress)
                                .height(4.dp)
                                .background(CloudStreamColors.Primary)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(7.dp))

        // Title Label
        val year = when (item) {
            is MovieSearchResponse -> item.year
            is TvSeriesSearchResponse -> item.year
            else -> null
        }

        Text(
            text = item.name,
            style = MaterialTheme.typography.caption.copy(
                fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isHighlighted) CloudStreamColors.Primary else CloudStreamColors.TextPrimary,
                fontSize = 13.sp
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 17.sp,
            modifier = Modifier.fillMaxWidth()
        )

        // Metadata Row: Year and Provider name
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (year != null && year > 0) {
                Text(
                    text = year.toString(),
                    style = MaterialTheme.typography.caption.copy(
                        fontSize = 11.sp,
                        color = CloudStreamColors.TextMuted,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
            }

            if (item.apiName.isNotBlank()) {
                Text(
                    text = item.apiName,
                    style = MaterialTheme.typography.caption.copy(
                        fontSize = 10.sp,
                        color = CloudStreamColors.TextMuted.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Normal
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(start = 4.dp)
                )
            }
        }
    }
}

