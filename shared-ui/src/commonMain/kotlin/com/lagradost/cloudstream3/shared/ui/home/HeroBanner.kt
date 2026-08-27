package com.lagradost.cloudstream3.shared.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cloudstream.shared_ui.generated.resources.*
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvSeriesSearchResponse
import com.lagradost.cloudstream3.shared.ui.components.AsyncImage
import com.lagradost.cloudstream3.shared.ui.components.DubSubBadges
import com.lagradost.cloudstream3.shared.ui.components.ProviderBadge
import com.lagradost.cloudstream3.shared.ui.components.QualityBadge
import com.lagradost.cloudstream3.shared.ui.components.TypeBadge
import com.lagradost.cloudstream3.shared.ui.components.YearBadge
import com.lagradost.cloudstream3.shared.ui.components.designsystem.PrimaryButton
import com.lagradost.cloudstream3.shared.ui.components.designsystem.SecondaryButton
import com.lagradost.cloudstream3.shared.ui.focus.dpadFocusable
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * Modern Featured Hero Banner with streaming-service aesthetics (Netflix / HBO / AppleTV style).
 * Features cinematic full-width artwork, double gradient overlay (lateral fade and deep bottom fade to background),
 * large bold typography, pill-shaped metadata badges, concise 2-line synopsis,
 * high-impact interactive [PrimaryButton] ("Play") and [SecondaryButton] ("More Info"), and animated pill pagination.
 *
 * @param items List of featured [SearchResponse] items.
 * @param onItemClick Callback invoked when an item or button is clicked.
 * @param onPlayClick Optional callback invoked when the primary play button is clicked for direct Quick Play.
 * @param modifier Optional layout modifier.
 * @param bannerHeight Height of the hero banner.
 */
@Composable
fun HeroBanner(
    items: List<SearchResponse>,
    onItemClick: (SearchResponse) -> Unit,
    onPlayClick: ((SearchResponse) -> Unit)? = null,
    modifier: Modifier = Modifier,
    bannerHeight: Dp = 420.dp
) {
    if (items.isEmpty()) return

    var currentIndex by remember { mutableStateOf(0) }
    val currentItem = items.getOrNull(currentIndex) ?: items.first()

    // Auto-cycle through featured items every 8 seconds if multiple exist
    LaunchedEffect(items.size) {
        if (items.size > 1) {
            while (true) {
                delay(8000)
                currentIndex = (currentIndex + 1) % items.size
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(bannerHeight)
            .background(CloudStreamColors.Background)
    ) {
        // 1. Background Hero Poster with Smooth Crossfade Transition
        Crossfade(
            targetState = currentItem,
            animationSpec = tween(durationMillis = 600),
            modifier = Modifier.fillMaxSize()
        ) { item ->
            AsyncImage(
                url = item.posterUrl,
                contentDescription = item.name,
                headers = item.posterHeaders,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 2. Cinematic Multi-Layer Gradients
        // Vertical Gradient: dark top for top bar, transparent center, deep fade to pure background at bottom
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = 0.65f),
                        0.25f to Color.Transparent,
                        0.55f to Color.Black.copy(alpha = 0.45f),
                        0.82f to CloudStreamColors.Background.copy(alpha = 0.88f),
                        1.0f to CloudStreamColors.Background
                    )
                )
        )

        // Horizontal Gradient: deep dark fade on the left for text legibility, fading to transparent on the right
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0.0f to CloudStreamColors.Background.copy(alpha = 0.95f),
                        0.35f to CloudStreamColors.Background.copy(alpha = 0.80f),
                        0.65f to Color.Black.copy(alpha = 0.35f),
                        1.0f to Color.Transparent
                    )
                )
        )

        // 3. Metadata, Badges & Action Controls Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.Start
        ) {
            // Capsule Badges Row (Type, Quality, Audio/Dub/Sub, Year, Provider)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 10.dp)
            ) {
                TypeBadge(type = currentItem.type)
                QualityBadge(quality = currentItem.quality)
                DubSubBadges(searchResponse = currentItem)

                val year = when (currentItem) {
                    is MovieSearchResponse -> currentItem.year
                    is TvSeriesSearchResponse -> currentItem.year
                    else -> null
                }
                YearBadge(year = year)

                if (currentItem.apiName.isNotBlank()) {
                    ProviderBadge(apiName = currentItem.apiName)
                }
            }

            // Big Bold Cinematic Title
            Text(
                text = currentItem.name,
                style = MaterialTheme.typography.h4.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = CloudStreamColors.OnMediaScrim,
                    fontSize = 30.sp,
                    lineHeight = 36.sp
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .padding(bottom = 8.dp)
            )

            // Concise 2-Line Streaming Synopsis / Metadata
            val synopsis = when {
                currentItem.apiName.isNotBlank() ->
                    stringResource(Res.string.hero_banner_synopsis_provider, currentItem.apiName)
                else ->
                    stringResource(Res.string.hero_banner_synopsis_default)
            }

            Text(
                text = synopsis,
                style = MaterialTheme.typography.body2.copy(
                    color = CloudStreamColors.OnMediaScrim.copy(alpha = 0.82f),
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Normal
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth(0.68f)
                    .padding(bottom = 18.dp)
            )

            // Action Buttons & Paging Indicator Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Action Buttons ("Play" & "More Info")
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Primary "Play" Action Button with Play Icon
                    PrimaryButton(
                        textRes = Res.string.home_play,
                        icon = Icons.Default.PlayArrow,
                        onClick = { onPlayClick?.invoke(currentItem) ?: onItemClick(currentItem) }
                    )

                    // Secondary "More Info" Action Button with Info Icon
                    SecondaryButton(
                        textRes = Res.string.home_more_info,
                        icon = Icons.Default.Info,
                        onClick = { onItemClick(currentItem) }
                    )
                }

                // Modern Expandable Pill Paging Indicator (if multiple featured items)
                if (items.size > 1) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        items.indices.forEach { index ->
                            val isSelected = index == currentIndex
                            val indicatorWidth by animateDpAsState(
                                targetValue = if (isSelected) 22.dp else 7.dp,
                                animationSpec = tween(durationMillis = 300)
                            )
                            val indicatorColor = if (isSelected) CloudStreamColors.Primary else CloudStreamColors.OnMediaScrim.copy(alpha = 0.35f)

                            Box(
                                modifier = Modifier
                                    .height(8.dp)
                                    .width(indicatorWidth)
                                    .clip(CircleShape)
                                    .background(indicatorColor)
                                    .dpadFocusable(
                                        onClick = { currentIndex = index },
                                        shape = CircleShape
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}
