package com.lagradost.cloudstream3.shared.ui.result

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.Immutable
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
import cloudstream.shared_ui.generated.resources.*
import com.lagradost.cloudstream3.EpisodeResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.shared.ui.components.AsyncImage
import com.lagradost.cloudstream3.shared.ui.components.TranslucentBadge
import com.lagradost.cloudstream3.shared.ui.layout.Layout
import com.lagradost.cloudstream3.shared.ui.layout.isLayoutState
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors
import com.lagradost.cloudstream3.shared.viewmodels.result.ExternalSyncEntry
import com.lagradost.cloudstream3.shared.viewmodels.result.ExternalSyncStatus
import com.lagradost.cloudstream3.shared.viewmodels.result.ResultEpisode
import com.lagradost.cloudstream3.shared.viewmodels.result.ResultEvent
import com.lagradost.cloudstream3.shared.viewmodels.result.ResultState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

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
    val isWideScreen by isLayoutState(Layout.TV or Layout.COMPUTER or Layout.EMULATOR)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(CloudStreamColors.Background)
    ) {
        if (isWideScreen) {
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
        ResultBackdropHero(
            backgroundUrl = state.displayBackgroundPosterUrl,
            title = state.title,
            posterHeaders = state.posterHeaders,
            isWide = true
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.Top
        ) {
            ResultPosterCard(
                posterUrl = state.displayPosterUrl,
                title = state.title,
                posterHeaders = state.posterHeaders,
                isWide = true
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 10.dp)
            ) {
                ResultTitleOrLogo(
                    logoUrl = state.logoUrl,
                    title = state.title,
                    posterHeaders = state.posterHeaders,
                    isWide = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                ResultHeaderBadgesRow(
                    state = state,
                    onOpenTrailer = onEvent?.let { { it(ResultEvent.OpenTrailer(0)) } },
                    isCompact = false
                )

                Spacer(modifier = Modifier.height(14.dp))

                if (onEvent != null && onPlayEpisode != null) {
                    ResultActionButtons(
                        state = state,
                        onEvent = onEvent,
                        onPlayEpisode = onPlayEpisode,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                ResultGenreTagsFlow(
                    tags = state.tags,
                    maxCount = 10,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (!state.synopsis.isNullOrBlank()) {
                    ExpandableSynopsisView(
                        synopsis = state.synopsis,
                        isExpanded = isSynopsisExpanded,
                        onToggle = onToggleSynopsis
                    )
                }

                if (state.actors.isNotEmpty()) {
                    ResultCastRow(actors = state.actors, onSearchClick = onSearchClick)
                }
            }
        }
    }
}

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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
        ) {
            ResultBackdropHero(
                backgroundUrl = state.displayBackgroundPosterUrl,
                title = state.title,
                posterHeaders = state.posterHeaders,
                isWide = false
            )

            ResultTopBadgesRow(
                tvType = state.tvType,
                contentRating = state.contentRating,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .align(Alignment.TopCenter)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                ResultPosterCard(
                    posterUrl = state.displayPosterUrl,
                    title = state.title,
                    posterHeaders = state.posterHeaders,
                    isWide = false
                )

                Column(modifier = Modifier.weight(1f)) {
                    ResultTitleOrLogo(
                        logoUrl = state.logoUrl,
                        title = state.title,
                        posterHeaders = state.posterHeaders,
                        isWide = false
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    ResultHeaderBadgesRow(
                        state = state,
                        onOpenTrailer = null,
                        isCompact = true
                    )
                }
            }
        }

        ResultSecondaryBadgesRow(
            state = state,
            onOpenTrailer = onEvent?.let { { it(ResultEvent.OpenTrailer(0)) } },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )

        if (onEvent != null && onPlayEpisode != null) {
            ResultActionButtons(
                state = state,
                onEvent = onEvent,
                onPlayEpisode = onPlayEpisode,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        ResultGenreTagsFlow(
            tags = state.tags,
            maxCount = 8,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )

        if (!state.synopsis.isNullOrBlank()) {
            ExpandableSynopsisView(
                synopsis = state.synopsis,
                isExpanded = isSynopsisExpanded,
                onToggle = onToggleSynopsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        if (state.actors.isNotEmpty()) {
            ResultCastRow(actors = state.actors, onSearchClick = onSearchClick)
        }
    }
}

@Composable
private fun ResultBackdropHero(
    backgroundUrl: String?,
    title: String,
    posterHeaders: ImmutableMap<String, String>?,
    isWide: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(if (isWide) 420.dp else 340.dp)
    ) {
        if (!backgroundUrl.isNullOrBlank()) {
            AsyncImage(
                url = backgroundUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                headers = posterHeaders
            )
        } else if (!isWide) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CloudStreamColors.SurfaceVariant)
            )
        }

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

        if (isWide) {
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
    }
}

@Composable
private fun ResultPosterCard(
    posterUrl: String?,
    title: String,
    posterHeaders: ImmutableMap<String, String>?,
    isWide: Boolean,
    modifier: Modifier = Modifier
) {
    val cardModifier = if (isWide) {
        modifier.width(200.dp)
    } else {
        modifier.width(110.dp)
    }

    Card(
        shape = RoundedCornerShape(if (isWide) 14.dp else 10.dp),
        elevation = if (isWide) 12.dp else 8.dp,
        backgroundColor = CloudStreamColors.SurfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.15f)),
        modifier = cardModifier.aspectRatio(2f / 3f)
    ) {
        AsyncImage(
            url = posterUrl,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            headers = posterHeaders
        )
    }
}

@Composable
private fun ResultTitleOrLogo(
    logoUrl: String?,
    title: String,
    posterHeaders: ImmutableMap<String, String>?,
    isWide: Boolean,
    modifier: Modifier = Modifier
) {
    if (!logoUrl.isNullOrBlank()) {
        AsyncImage(
            url = logoUrl,
            contentDescription = title,
            contentScale = ContentScale.Fit,
            modifier = modifier
                .height(if (isWide) 68.dp else 50.dp)
                .padding(bottom = if (isWide) 8.dp else 4.dp),
            headers = posterHeaders
        )
    } else {
        val textStyle = if (isWide) {
            MaterialTheme.typography.h4.copy(
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colors.onSurface,
                fontSize = 28.sp,
                lineHeight = 34.sp
            )
        } else {
            MaterialTheme.typography.h6.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.onSurface,
                fontSize = 19.sp,
                lineHeight = 23.sp
            )
        }

        Text(
            text = title,
            style = textStyle,
            maxLines = if (isWide) 2 else 3,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier
        )
    }
}

@Composable
private fun ResultTopBadgesRow(
    tvType: TvType?,
    contentRating: String?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            tvType?.let { TranslucentTypeBadge(tvType = it) }
            if (!contentRating.isNullOrBlank()) {
                TranslucentContentRatingBadge(rating = contentRating)
            }
        }
    }
}

@Composable
private fun ResultScoreBadge(
    rating: Score?,
    modifier: Modifier = Modifier
) {
    val scoreDouble = rating?.toDouble(10)
    if (scoreDouble != null && scoreDouble > 0.0) {
        TranslucentScoreBadge(score = scoreDouble, modifier = modifier)
    }
}

@Composable
private fun ResultYearBadge(
    year: Int?,
    modifier: Modifier = Modifier
) {
    year?.let { y ->
        TranslucentPillBadge(text = y.toString(), modifier = modifier)
    }
}

@Composable
private fun ResultDurationBadge(
    duration: Int?,
    modifier: Modifier = Modifier
) {
    duration?.let { d ->
        val formatted = if (d >= 60) "${d / 60}h ${d % 60}m" else "${d}m"
        TranslucentPillBadge(text = formatted, modifier = modifier)
    }
}

@Composable
private fun ResultNextAiringBadge(
    loadResponse: LoadResponse?,
    modifier: Modifier = Modifier
) {
    val nextAiring = (loadResponse as? EpisodeResponse)?.nextAiring
    if (nextAiring != null) {
        TranslucentPillBadge(
            text = "${stringResource(Res.string.episode)} ${nextAiring.episode}",
            modifier = modifier
        )
    }
}

@Composable
private fun ResultNsfwBadge(
    isNsfw: Boolean,
    modifier: Modifier = Modifier
) {
    if (isNsfw) {
        TranslucentPillBadge(
            text = stringResource(Res.string.type_nsfw),
            modifier = modifier
        )
    }
}

@Composable
private fun ResultSyncBadge(
    syncEntry: ExternalSyncEntry?,
    modifier: Modifier = Modifier
) {
    syncEntry?.let { sync ->
        if (sync.hasTracking) {
            TranslucentSyncBadge(syncEntry = sync, modifier = modifier)
        }
    }
}

@Composable
private fun ResultTrailerBadge(
    hasTrailers: Boolean,
    onOpenTrailer: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    if (hasTrailers && onOpenTrailer != null) {
        TranslucentTrailerBadge(
            onClick = onOpenTrailer,
            modifier = modifier
        )
    }
}

private fun ResultState.isNsfwContent(): Boolean {
    return tvType == TvType.NSFW || tags.any { it.equals("nsfw", ignoreCase = true) }
}

@Composable
private fun ResultHeaderBadgesRow(
    state: ResultState,
    onOpenTrailer: (() -> Unit)? = null,
    isCompact: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (isCompact) 6.dp else 8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        ResultScoreBadge(rating = state.rating)
        ResultYearBadge(year = state.year)
        ResultDurationBadge(duration = state.duration)
        ResultNextAiringBadge(loadResponse = state.loadResponse)
        ResultNsfwBadge(isNsfw = state.isNsfwContent())

        if (!isCompact) {
            state.tvType?.let { TranslucentTypeBadge(tvType = it) }
            state.showStatus?.let { TranslucentStatusBadge(status = it) }
            if (!state.contentRating.isNullOrBlank()) {
                TranslucentContentRatingBadge(rating = state.contentRating)
            }
            if (!state.apiName.isNullOrBlank()) {
                TranslucentProviderBadge(apiName = state.apiName)
            }
            ResultSyncBadge(syncEntry = state.primaryLinkedSync)
            ResultTrailerBadge(hasTrailers = state.hasTrailers, onOpenTrailer = onOpenTrailer)
        }
    }
}

@Composable
private fun ResultSecondaryBadgesRow(
    state: ResultState,
    onOpenTrailer: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        state.showStatus?.let { TranslucentStatusBadge(status = it) }
        if (!state.apiName.isNullOrBlank()) {
            TranslucentProviderBadge(apiName = state.apiName)
        }
        ResultSyncBadge(syncEntry = state.primaryLinkedSync)
        ResultTrailerBadge(hasTrailers = state.hasTrailers, onOpenTrailer = onOpenTrailer)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultGenreTagsFlow(
    tags: ImmutableList<String>,
    maxCount: Int,
    modifier: Modifier = Modifier
) {
    if (tags.isEmpty()) return

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        tags.take(maxCount).forEach { tag ->
            TranslucentTagChip(tag = tag)
        }
    }
}

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

@Immutable
private data class TypeBadgeStyle(
    val stringRes: StringResource? = null,
    val fallbackName: String? = null,
    val color: Color
)

@Composable
private fun resolveTypeBadgeStyle(tvType: TvType): TypeBadgeStyle {
    return when (tvType) {
        TvType.Movie -> TypeBadgeStyle(stringRes = Res.string.typeMovie, color = CloudStreamColors.Info)
        TvType.Anime, TvType.AnimeMovie, TvType.OVA -> TypeBadgeStyle(stringRes = Res.string.typeAnime, color = CloudStreamColors.Primary)
        TvType.TvSeries -> TypeBadgeStyle(stringRes = Res.string.typeTvSeries, color = CloudStreamColors.Success)
        TvType.Live -> TypeBadgeStyle(stringRes = Res.string.typeLive, color = CloudStreamColors.Error)
        TvType.Torrent -> TypeBadgeStyle(stringRes = Res.string.typeTorrent, color = CloudStreamColors.Warning)
        else -> TypeBadgeStyle(fallbackName = tvType.name, color = CloudStreamColors.PrimaryVariant)
    }
}

@Composable
fun TranslucentTypeBadge(
    tvType: TvType,
    modifier: Modifier = Modifier
) {
    val style = resolveTypeBadgeStyle(tvType)
    val label = style.stringRes?.let { stringResource(it) } ?: (style.fallbackName ?: tvType.name)

    TranslucentBadge(
        text = label,
        backgroundColor = style.color.copy(alpha = 0.22f),
        borderColor = style.color.copy(alpha = 0.6f),
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
    syncEntry: ExternalSyncEntry,
    modifier: Modifier = Modifier
) {
    val service = syncEntry.service
    val color = service.brandColor
    val statusText = if (syncEntry.status != ExternalSyncStatus.None) {
        stringResource(syncEntry.status.stringRes)
    } else {
        stringResource(Res.string.sync_linked)
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

@Preview
@Composable
private fun ResultHeaderCompactPreview() {
    MaterialTheme {
        ResultHeader(
            state = ResultState(
                title = "Cyberpunk: Edgerunners",
                synopsis = "A street kid trying to survive in a technology and body modification-obsessed city of the future.",
                tvType = TvType.Anime,
                year = 2022,
                showStatus = ShowStatus.Completed,
                tags = kotlinx.collections.immutable.persistentListOf("Sci-Fi", "Action", "Cyberpunk")
            )
        )
    }
}

@Preview
@Composable
private fun ResultHeaderBadgesPreview() {
    MaterialTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TranslucentScoreBadge(score = 8.6)
            TranslucentPillBadge(text = "2024")
            TranslucentTypeBadge(tvType = TvType.Anime)
            TranslucentStatusBadge(status = ShowStatus.Ongoing)
        }
    }
}
