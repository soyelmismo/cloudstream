package com.lagradost.cloudstream3.shared.ui.search

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.shared.ui.components.AsyncImage
import com.lagradost.cloudstream3.shared.ui.components.QualityBadge
import com.lagradost.cloudstream3.shared.ui.components.TypeBadge
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors
import org.jetbrains.compose.resources.stringResource
import cloudstream.shared_ui.generated.resources.*

/**
 * Responsive Card displaying a single [SearchResponse] search result item.
 * Supports Desktop mouse hover feedback, TV/Keyboard focus state, unified quality/type badges, and score.
 */
@Composable
fun SearchResultCard(
    item: SearchResponse,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isHovered by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isHovered || isFocused) 1.03f else 1.0f,
        label = "card_scale"
    )

    val elevation = if (isHovered || isFocused) 8.dp else 2.dp
    val borderColor = when {
        isFocused -> MaterialTheme.colors.primary
        isHovered -> MaterialTheme.colors.primary.copy(alpha = 0.7f)
        else -> Color.Transparent
    }

    Card(
        shape = RoundedCornerShape(8.dp),
        elevation = elevation,
        backgroundColor = MaterialTheme.colors.surface,
        modifier = modifier
            .scale(scale)
            .pointerHoverIcon(PointerIcon.Hand)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        when (event.type) {
                            PointerEventType.Enter -> isHovered = true
                            PointerEventType.Exit -> isHovered = false
                        }
                    }
                }
            }
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .border(
                border = BorderStroke(if (isHovered || isFocused) 2.dp else 1.dp, borderColor),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            // Poster Box with Aspect Ratio ~2:3 (Standard poster format)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.68f)
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .background(CloudStreamColors.SurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                if (!item.posterUrl.isNullOrBlank()) {
                    AsyncImage(
                        url = item.posterUrl,
                        contentDescription = item.name,
                        headers = item.posterHeaders,
                        modifier = Modifier.fillMaxSize(),
                        placeholder = { SearchPosterGraphic(item = item) },
                        error = { SearchPosterGraphic(item = item) }
                    )
                } else {
                    SearchPosterGraphic(item = item)
                }

                // Top badges row (Canonical Type & Quality Badges from MediaBadges.kt)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.type != null) {
                        TypeBadge(type = item.type)
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    QualityBadge(quality = item.quality)
                }

                // Bottom gradient with score & provider name
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.8f),
                                    Color.Black.copy(alpha = 0.94f)
                                )
                            )
                        )
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Provider Name
                        Text(
                            text = item.apiName,
                            style = MaterialTheme.typography.caption.copy(
                                color = CloudStreamColors.OnMediaScrim.copy(alpha = 0.85f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        // Score if available
                        item.score?.let { score ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = stringResource(Res.string.rating),
                                    tint = CloudStreamColors.Warning,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = score.toString(),
                                    style = MaterialTheme.typography.caption.copy(
                                        color = CloudStreamColors.OnMediaScrim,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Title
            Text(
                text = item.name,
                style = MaterialTheme.typography.subtitle2.copy(
                    fontWeight = FontWeight.Medium,
                    lineHeight = 16.sp
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            )
        }
    }
}

/**
 * Poster representation with graceful styling and fallback.
 */
@Composable
private fun SearchPosterGraphic(item: SearchResponse) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(CloudStreamColors.SurfaceElevated, CloudStreamColors.SurfaceVariant)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(8.dp)
        ) {
            // First letter abbreviation or decorative icon
            Text(
                text = item.name.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.h4.copy(
                    color = CloudStreamColors.OnMediaScrim.copy(alpha = 0.35f),
                    fontWeight = FontWeight.Bold
                ),
                textAlign = TextAlign.Center
            )
        }
    }
}
