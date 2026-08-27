package com.lagradost.cloudstream3.shared.ui.result

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.shared.ui.components.AsyncImage
import com.lagradost.cloudstream3.shared.ui.components.TranslucentBadge
import org.jetbrains.compose.resources.painterResource
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors
import com.lagradost.cloudstream3.shared.viewmodels.result.ResultEpisode
import com.lagradost.cloudstream3.shared.viewmodels.result.ResultEvent
import com.lagradost.cloudstream3.shared.viewmodels.result.ResultState
import org.jetbrains.compose.resources.stringResource
import cloudstream.shared_ui.generated.resources.*

/**
 * Premium Media Details Header with responsive layout:
 * - Wide screens (> 600dp): Left sidebar poster with technical data sheet and actions on the right.
 * - Mobile screens (< 600dp): Immersive cinematic vertical stack with broad backdrop gradient.
 * - Translucent badges for rating, year, duration, TV type, status, content rating, and genre tags.
 * - Integrated styled action buttons (Hero Play/Continue, Bookmark modal dialog, Favorite, Refresh).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResultHeader(
    state: ResultState,
    onBack: (() -> Unit)? = null,
    onEvent: ((ResultEvent) -> Unit)? = null,
    onPlayEpisode: ((ResultEpisode) -> Unit)? = null,
    onSearchClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isSynopsisExpanded by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(CloudStreamColors.Background)
    ) {
        val isWideScreen = maxWidth >= 600.dp

        if (isWideScreen) {
            // =================================================================
            // WIDE SCREEN LAYOUT (> 600dp): Desktop, Tablet & Landscape
            // =================================================================
            ResultHeaderWide(
                state = state,
                isSynopsisExpanded = isSynopsisExpanded,
                onToggleSynopsis = { isSynopsisExpanded = !isSynopsisExpanded },
                onBack = onBack,
                onEvent = onEvent,
                onPlayEpisode = onPlayEpisode,
                onSearchClick = onSearchClick
            )
        } else {
            // =================================================================
            // COMPACT SCREEN LAYOUT (< 600dp): Mobile & Portrait
            // =================================================================
            ResultHeaderCompact(
                state = state,
                isSynopsisExpanded = isSynopsisExpanded,
                onToggleSynopsis = { isSynopsisExpanded = !isSynopsisExpanded },
                onBack = onBack,
                onEvent = onEvent,
                onPlayEpisode = onPlayEpisode,
                onSearchClick = onSearchClick
            )
        }
    }
}

/**
 * Wide Screen Layout (> 600dp) with left poster and rich details side column.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultHeaderWide(
    state: ResultState,
    isSynopsisExpanded: Boolean,
    onToggleSynopsis: () -> Unit,
    onBack: (() -> Unit)?,
    onEvent: ((ResultEvent) -> Unit)?,
    onPlayEpisode: ((ResultEpisode) -> Unit)?,
    onSearchClick: ((String) -> Unit)?
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(CloudStreamColors.Background)
    ) {
        // Backdrop Hero with Deep Gradient Fade into pure #000000
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
        ) {
            val bgUrl = state.displayBackgroundPosterUrl
            if (!bgUrl.isNullOrBlank()) {
                AsyncImage(
                    url = bgUrl,
                    contentDescription = state.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    headers = state.posterHeaders
                )
            }

            // Multi-stop Vertical and Horizontal Deep Black Gradients matching bg_shadow.xml
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.2f),
                                Color.Black.copy(alpha = 0.6f),
                                CloudStreamColors.Background
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                CloudStreamColors.Background.copy(alpha = 0.95f),
                                CloudStreamColors.Background.copy(alpha = 0.65f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        // Two-Column Content: Poster on Left, Details on Right
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Poster Column
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(200.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    elevation = 12.dp,
                    backgroundColor = CloudStreamColors.SurfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.15f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                ) {
                    AsyncImage(
                        url = state.displayPosterUrl,
                        contentDescription = state.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        headers = state.posterHeaders
                    )
                }
            }

            // Details Column
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 10.dp)
            ) {
                // Logo or Title
                if (!state.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        url = state.logoUrl,
                        contentDescription = state.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .height(68.dp)
                            .padding(bottom = 8.dp),
                        headers = state.posterHeaders
                    )
                } else {
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.h4.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colors.onSurface,
                            fontSize = 28.sp,
                            lineHeight = 34.sp
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Translucent Pill Badges Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Rating Pill
                    val scoreDouble = state.rating?.toDouble(10)
                    if (scoreDouble != null && scoreDouble > 0.0) {
                        TranslucentScoreBadge(score = scoreDouble)
                    }

                    // Release Year Pill
                    state.year?.let { y ->
                        TranslucentPillBadge(text = y.toString())
                    }

                    // Duration Pill
                    state.duration?.let { d ->
                        val formatted = if (d >= 60) "${d / 60}h ${d % 60}m" else "${d}m"
                        TranslucentPillBadge(text = formatted)
                    }

                    val nextAiring = (state.loadResponse as? com.lagradost.cloudstream3.EpisodeResponse)?.nextAiring
                    if (nextAiring != null) {
                        TranslucentPillBadge(text = "${stringResource(Res.string.episode)} ${nextAiring.episode}")
                    }

                    val isNsfw = state.tvType == TvType.NSFW || state.tags.any { it.equals("nsfw", ignoreCase = true) }
                    if (isNsfw) {
                        TranslucentPillBadge(text = stringResource(Res.string.type_nsfw))
                    }

                    // TV Type Pill
                    state.tvType?.let { tvType ->
                        TranslucentTypeBadge(tvType = tvType)
                    }

                    // Status Pill
                    state.showStatus?.let { status ->
                        TranslucentStatusBadge(status = status)
                    }

                    // Content Rating Pill
                    if (!state.contentRating.isNullOrBlank()) {
                        TranslucentContentRatingBadge(rating = state.contentRating)
                    }

                    // Provider Pill
                    if (!state.apiName.isNullOrBlank()) {
                        TranslucentProviderBadge(apiName = state.apiName)
                    }

                    // External Sync Badge (if linked)
                    state.primaryLinkedSync?.let { sync ->
                        if (sync.hasTracking) {
                            TranslucentSyncBadge(syncEntry = sync)
                        }
                    }

                    // Trailer Pill Badge (if available)
                    if (state.hasTrailers && onEvent != null) {
                        TranslucentTrailerBadge(
                            onClick = { onEvent(ResultEvent.OpenTrailer(0)) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Buttons Bar (if callbacks provided)
                if (onEvent != null && onPlayEpisode != null) {
                    ResultActionButtons(
                        state = state,
                        onEvent = onEvent,
                        onPlayEpisode = onPlayEpisode,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                // Genre Tags Flow
                if (state.tags.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        state.tags.take(10).forEach { tag ->
                            TranslucentTagChip(tag = tag)
                        }
                    }
                }

                // Expandable Synopsis
                if (!state.synopsis.isNullOrBlank()) {
                    ExpandableSynopsisView(
                        synopsis = state.synopsis,
                        isExpanded = isSynopsisExpanded,
                        onToggle = onToggleSynopsis
                    )
                }

                // Cast Row
                if (state.actors.isNotEmpty()) {
                    ResultCastRow(actors = state.actors, onSearchClick = onSearchClick)
                }
            }
        }
    }
}

/**
 * Compact Screen Layout (< 600dp) with cinematic vertical header.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultHeaderCompact(
    state: ResultState,
    isSynopsisExpanded: Boolean,
    onToggleSynopsis: () -> Unit,
    onBack: (() -> Unit)?,
    onEvent: ((ResultEvent) -> Unit)?,
    onPlayEpisode: ((ResultEpisode) -> Unit)?,
    onSearchClick: ((String) -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CloudStreamColors.Background)
    ) {
        // Backdrop Hero Area with Broad Fade to #000000
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
        ) {
            val bgUrl = state.displayBackgroundPosterUrl
            if (!bgUrl.isNullOrBlank()) {
                AsyncImage(
                    url = bgUrl,
                    contentDescription = state.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    headers = state.posterHeaders
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(CloudStreamColors.SurfaceVariant)
                )
            }

            // Multi-stop Vertical Dark Gradient (Matching legacy bg_shadow.xml)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.2f),
                                Color.Black.copy(alpha = 0.6f),
                                CloudStreamColors.Background
                            )
                        )
                    )
            )

            // Top Bar: Badges
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.tvType?.let { tvType ->
                        TranslucentTypeBadge(tvType = tvType)
                    }
                    if (!state.contentRating.isNullOrBlank()) {
                        TranslucentContentRatingBadge(rating = state.contentRating)
                    }
                }
            }

            // Compact Poster & Title Overlay at Bottom of Backdrop
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // Poster
                Card(
                    shape = RoundedCornerShape(10.dp),
                    elevation = 8.dp,
                    backgroundColor = CloudStreamColors.SurfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.15f)),
                    modifier = Modifier
                        .width(110.dp)
                        .aspectRatio(2f / 3f)
                ) {
                    AsyncImage(
                        url = state.displayPosterUrl,
                        contentDescription = state.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        headers = state.posterHeaders
                    )
                }

                // Title & Score Column
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    if (!state.logoUrl.isNullOrBlank()) {
                        AsyncImage(
                            url = state.logoUrl,
                            contentDescription = state.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .height(50.dp)
                                .padding(bottom = 4.dp),
                            headers = state.posterHeaders
                        )
                    } else {
                        Text(
                            text = state.title,
                            style = MaterialTheme.typography.h6.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colors.onSurface,
                                fontSize = 19.sp,
                                lineHeight = 23.sp
                            ),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Rating & Year Quick Badges
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val scoreDouble = state.rating?.toDouble(10)
                        if (scoreDouble != null && scoreDouble > 0.0) {
                            TranslucentScoreBadge(score = scoreDouble)
                        }

                        state.year?.let { y ->
                            TranslucentPillBadge(text = y.toString())
                        }

                        state.duration?.let { d ->
                            val formatted = if (d >= 60) "${d / 60}h ${d % 60}m" else "${d}m"
                            TranslucentPillBadge(text = formatted)
                        }

                        val nextAiring = (state.loadResponse as? com.lagradost.cloudstream3.EpisodeResponse)?.nextAiring
                        if (nextAiring != null) {
                            TranslucentPillBadge(text = "${stringResource(Res.string.episode)} ${nextAiring.episode}")
                        }

                        val isNsfw = state.tvType == TvType.NSFW || state.tags.any { it.equals("nsfw", ignoreCase = true) }
                        if (isNsfw) {
                            TranslucentPillBadge(text = stringResource(Res.string.type_nsfw))
                        }
                    }
                }
            }
        }

        // Secondary Metadata Badges Row (Status, Provider)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            state.showStatus?.let { status ->
                TranslucentStatusBadge(status = status)
            }
            if (!state.apiName.isNullOrBlank()) {
                TranslucentProviderBadge(apiName = state.apiName)
            }
            // External Sync Badge (if linked)
            state.primaryLinkedSync?.let { sync ->
                if (sync.hasTracking) {
                    TranslucentSyncBadge(syncEntry = sync)
                }
            }
            // Trailer Pill Badge (if available)
            if (state.hasTrailers && onEvent != null) {
                TranslucentTrailerBadge(
                    onClick = { onEvent(ResultEvent.OpenTrailer(0)) }
                )
            }
        }

        // Action Buttons Bar (if callbacks provided)
        if (onEvent != null && onPlayEpisode != null) {
            ResultActionButtons(
                state = state,
                onEvent = onEvent,
                onPlayEpisode = onPlayEpisode,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        // Genre Tags Flow
        if (state.tags.isNotEmpty()) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                state.tags.take(8).forEach { tag ->
                    TranslucentTagChip(tag = tag)
                }
            }
        }

        // Expandable Synopsis
        if (!state.synopsis.isNullOrBlank()) {
            ExpandableSynopsisView(
                synopsis = state.synopsis,
                isExpanded = isSynopsisExpanded,
                onToggle = onToggleSynopsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // Cast & Crew Row
        if (state.actors.isNotEmpty()) {
            ResultCastRow(actors = state.actors, onSearchClick = onSearchClick)
        }
    }
}

/**
 * Expandable Synopsis component with smooth animated resize.
 */
@Composable
private fun ExpandableSynopsisView(
    synopsis: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(250))
    ) {
        Text(
            text = synopsis,
            style = MaterialTheme.typography.body2.copy(
                color = CloudStreamColors.TextSecondary,
                lineHeight = 21.sp,
                fontSize = 13.5.sp
            ),
            maxLines = if (isExpanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis
        )

        if (synopsis.length > 130) {
            Text(
                text = if (isExpanded) stringResource(Res.string.show_less) else stringResource(Res.string.read_more),
                style = MaterialTheme.typography.caption.copy(
                    fontWeight = FontWeight.Bold,
                    color = CloudStreamColors.Primary,
                    fontSize = 12.sp
                ),
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onToggle
                    )
            )
        }
    }
}

// -----------------------------------------------------------------------------
// Translucent Pill Badges & Chips
// -----------------------------------------------------------------------------

@Composable
fun TranslucentScoreBadge(
    score: Double,
    modifier: Modifier = Modifier
) {
    TranslucentBadge(
        text = score.toString().take(3),
        backgroundColor = CloudStreamColors.Warning.copy(alpha = 0.18f),
        borderColor = CloudStreamColors.Warning.copy(alpha = 0.55f),
        textColor = CloudStreamColors.Warning,
        fontWeight = FontWeight.Bold,
        fontSize = 11.5.sp,
        modifier = modifier,
        leadingContent = {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = stringResource(Res.string.rating),
                tint = CloudStreamColors.Warning,
                modifier = Modifier.size(13.dp)
            )
        }
    )
}

@Composable
fun TranslucentPillBadge(
    text: String,
    modifier: Modifier = Modifier
) {
    TranslucentBadge(
        text = text,
        backgroundColor = MaterialTheme.colors.onSurface.copy(alpha = 0.08f),
        borderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.18f),
        textColor = CloudStreamColors.TextPrimary,
        modifier = modifier
    )
}

@Composable
fun TranslucentTypeBadge(
    tvType: TvType,
    modifier: Modifier = Modifier
) {
    val (label, tintColor) = when (tvType) {
        TvType.Movie -> stringResource(Res.string.typeMovie) to CloudStreamColors.Info
        TvType.Anime, TvType.AnimeMovie, TvType.OVA -> stringResource(Res.string.typeAnime) to CloudStreamColors.Primary
        TvType.TvSeries -> stringResource(Res.string.typeTvSeries) to CloudStreamColors.Success
        TvType.Live -> stringResource(Res.string.typeLive) to CloudStreamColors.Error
        TvType.Torrent -> stringResource(Res.string.typeTorrent) to CloudStreamColors.Warning
        else -> tvType.name to CloudStreamColors.PrimaryVariant
    }

    TranslucentBadge(
        text = label,
        backgroundColor = tintColor.copy(alpha = 0.22f),
        borderColor = tintColor.copy(alpha = 0.6f),
        textColor = MaterialTheme.colors.onSurface,
        fontWeight = FontWeight.Bold,
        modifier = modifier
    )
}

@Composable
fun TranslucentStatusBadge(
    status: ShowStatus,
    modifier: Modifier = Modifier
) {
    val isOngoing = status == ShowStatus.Ongoing
    val color = if (isOngoing) CloudStreamColors.Success else CloudStreamColors.Info

    TranslucentBadge(
        text = if (isOngoing) stringResource(Res.string.status_ongoing) else stringResource(Res.string.status_completed),
        backgroundColor = color.copy(alpha = 0.16f),
        borderColor = color.copy(alpha = 0.5f),
        textColor = color,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier,
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(color, CircleShape)
            )
        }
    )
}

@Composable
fun TranslucentContentRatingBadge(
    rating: String,
    modifier: Modifier = Modifier
) {
    TranslucentBadge(
        text = rating,
        backgroundColor = CloudStreamColors.Background.copy(alpha = 0.6f),
        borderColor = CloudStreamColors.Divider,
        textColor = CloudStreamColors.TextSecondary,
        fontWeight = FontWeight.Bold,
        fontSize = 10.5.sp,
        horizontalPadding = 7.dp,
        modifier = modifier
    )
}

@Composable
fun TranslucentProviderBadge(
    apiName: String,
    modifier: Modifier = Modifier
) {
    TranslucentBadge(
        text = apiName,
        backgroundColor = MaterialTheme.colors.onSurface.copy(alpha = 0.05f),
        borderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.12f),
        textColor = CloudStreamColors.TextMuted,
        fontWeight = FontWeight.Normal,
        fontSize = 10.5.sp,
        horizontalPadding = 7.dp,
        modifier = modifier
    )
}

@Composable
fun TranslucentTagChip(
    tag: String,
    modifier: Modifier = Modifier
) {
    TranslucentBadge(
        text = tag,
        backgroundColor = MaterialTheme.colors.onSurface.copy(alpha = 0.06f),
        borderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.14f),
        textColor = CloudStreamColors.TextSecondary,
        fontWeight = FontWeight.Medium,
        fontSize = 11.5.sp,
        shape = RoundedCornerShape(14.dp),
        horizontalPadding = 10.dp,
        verticalPadding = 4.dp,
        modifier = modifier
    )
}

@Composable
fun TranslucentSyncBadge(
    syncEntry: com.lagradost.cloudstream3.shared.viewmodels.result.ExternalSyncEntry,
    modifier: Modifier = Modifier
) {
    val service = syncEntry.service
    val color = service.brandColor
    val statusText = if (syncEntry.status != com.lagradost.cloudstream3.shared.viewmodels.result.ExternalSyncStatus.None) {
        org.jetbrains.compose.resources.stringResource(syncEntry.status.stringRes)
    } else {
        org.jetbrains.compose.resources.stringResource(cloudstream.shared_ui.generated.resources.Res.string.sync_linked)
    }

    val label = if (syncEntry.watchedEpisodes > 0 && syncEntry.maxEpisodes != null && syncEntry.maxEpisodes > 0) {
        "${service.serviceName}: ${syncEntry.watchedEpisodes}/${syncEntry.maxEpisodes} • $statusText"
    } else {
        "${service.serviceName}: $statusText"
    }

    TranslucentBadge(
        text = label,
        backgroundColor = color.copy(alpha = 0.18f),
        borderColor = color.copy(alpha = 0.55f),
        textColor = color,
        fontWeight = FontWeight.SemiBold,
        spacing = 5.dp,
        maxLines = 1,
        modifier = modifier,
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(color, CircleShape)
            )
        }
    )
}

@Composable
fun TranslucentTrailerBadge(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val trailerText = stringResource(Res.string.trailer)
    TranslucentBadge(
        text = trailerText,
        backgroundColor = CloudStreamColors.Primary.copy(alpha = 0.22f),
        borderColor = CloudStreamColors.Primary.copy(alpha = 0.65f),
        textColor = CloudStreamColors.Primary,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        modifier = modifier,
        onClick = onClick,
        leadingContent = {
            Icon(
                painter = painterResource(Res.drawable.baseline_theaters_24),
                contentDescription = trailerText,
                tint = CloudStreamColors.Primary,
                modifier = Modifier.size(13.dp)
            )
        }
    )
}
