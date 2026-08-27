package com.lagradost.cloudstream3.shared.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.shared.ui.components.MediaCard
import com.lagradost.cloudstream3.shared.ui.components.MediaCardDefaults
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors
import com.lagradost.cloudstream3.shared.viewmodels.HomeCarousel
import org.jetbrains.compose.resources.stringResource
import cloudstream.shared_ui.generated.resources.*

/**
 * Modern horizontal media carousel row component featuring:
 * - Refined category header with vertical primary accent bar or custom category icon.
 * - Interactive "Ver más" button with subtle hover animation and chevron shift.
 * - Dynamic support for vertical 2:3 posters and horizontal 16:9 banners.
 * - Smooth LazyRow horizontal scrolling with consistent padding and spacing (14.dp).
 * - Automatic horizontal pagination trigger when scrolling near the end.
 * - Styled inline loading indicator card for `isLoadingMore` state.
 *
 * @param carousel The [HomeCarousel] category data to display.
 * @param onItemClick Callback when a media card is clicked.
 * @param onItemLongClick Optional callback when a media card is long clicked (e.g., remove from resume watching).
 * @param onPlayClick Optional callback when the overlay play button is clicked directly (Quick Play).
 * @param onExpandCarousel Callback to paginate and fetch more items for this carousel category.
 * @param progressMap Map containing playback progress fractions (0f..1f) keyed by media url.
 * @param headerIcon Optional leading [ImageVector] for the carousel header.
 * @param modifier Optional modifier.
 */
@Composable
fun HomeCarouselView(
    carousel: HomeCarousel,
    onItemClick: (SearchResponse) -> Unit,
    onItemLongClick: ((SearchResponse) -> Unit)? = null,
    onPlayClick: ((SearchResponse) -> Unit)? = null,
    onExpandCarousel: (String) -> Unit,
    progressMap: Map<String, Float> = emptyMap(),
    headerIcon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    if (carousel.items.isEmpty() && !carousel.isLoadingMore) return

    val seeAllText = stringResource(Res.string.seeAll)
    val listState = rememberLazyListState()

    // Detect when user scrolls near the end of the carousel to trigger auto-pagination
    val shouldLoadMore by remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            val lastVisibleItemIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            carousel.hasNext && !carousel.isLoadingMore && totalItems > 0 && lastVisibleItemIndex >= totalItems - 3
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            onExpandCarousel(carousel.name)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        // Carousel Header (Accent Bar / Icon + Category Title & "Ver más" Action)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Title with primary colored accent bar or header icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (headerIcon != null) {
                    Icon(
                        imageVector = headerIcon,
                        contentDescription = null,
                        tint = CloudStreamColors.Primary,
                        modifier = Modifier.size(19.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .width(3.5.dp)
                            .height(18.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(CloudStreamColors.Primary)
                    )
                }

                Text(
                    text = carousel.name,
                    style = MaterialTheme.typography.subtitle1.copy(
                        fontWeight = FontWeight.Bold,
                        color = CloudStreamColors.TextPrimary,
                        fontSize = 17.5.sp
                    )
                )
            }

            // "Ver más" Expand Action
            if (carousel.hasNext) {
                val seeAllInteractionSource = remember { MutableInteractionSource() }
                val isSeeAllHovered by seeAllInteractionSource.collectIsHoveredAsState()
                val isSeeAllFocused by seeAllInteractionSource.collectIsFocusedAsState()
                val isSeeAllHighlighted = isSeeAllHovered || isSeeAllFocused

                val chevronOffset by animateFloatAsState(
                    targetValue = if (isSeeAllHighlighted) 3f else 0f,
                    animationSpec = tween(150)
                )

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSeeAllHighlighted) CloudStreamColors.Primary.copy(alpha = 0.15f)
                            else Color.Transparent
                        )
                        .clickable(
                            interactionSource = seeAllInteractionSource,
                            indication = null,
                            onClick = { onExpandCarousel(carousel.name) }
                        )
                        .focusable(interactionSource = seeAllInteractionSource)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = seeAllText,
                        color = CloudStreamColors.Primary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = stringResource(Res.string.see_all_category, seeAllText, carousel.name),
                        tint = CloudStreamColors.Primary,
                        modifier = Modifier
                            .size(16.dp)
                            .graphicsLayer {
                                translationX = chevronOffset
                            }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Horizontal LazyRow of Media Cards with consistent fluid spacing
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(
                items = carousel.items,
                key = { it.url + "_" + it.apiName }
            ) { item ->
                MediaCard(
                    item = item,
                    isHorizontal = carousel.isHorizontalImages,
                    onClick = onItemClick,
                    onLongClick = onItemLongClick,
                    onPlayClick = onPlayClick,
                    progress = progressMap[item.url]
                )
            }

            // Pagination Loading Indicator Card at the end of the carousel
            if (carousel.isLoadingMore) {
                item(key = "loading_indicator_${carousel.name}") {
                    val cardWidth = if (carousel.isHorizontalImages) MediaCardDefaults.HorizontalWidth else MediaCardDefaults.VerticalWidth
                    val aspectRatio = if (carousel.isHorizontalImages) MediaCardDefaults.AspectRatioHorizontal else MediaCardDefaults.AspectRatioVertical

                    Card(
                        shape = RoundedCornerShape(MediaCardDefaults.CornerRadius),
                        backgroundColor = CloudStreamColors.SurfaceVariant.copy(alpha = 0.4f),
                        elevation = 0.dp,
                        modifier = Modifier
                            .width(cardWidth)
                            .aspectRatio(aspectRatio)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = CloudStreamColors.Primary,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
