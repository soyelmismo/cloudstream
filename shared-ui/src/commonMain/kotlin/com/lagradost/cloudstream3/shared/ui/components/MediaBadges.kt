package com.lagradost.cloudstream3.shared.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.TvSeriesSearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors
import org.jetbrains.compose.resources.stringResource
import cloudstream.shared_ui.generated.resources.*

/**
 * Shared translucent "frosted" badge shell used by the result-detail badges.
 *
 * Renders a rounded [Surface] with a translucent fill and matching border, an optional leading
 * icon/dot ([leadingContent]) and an optional click handler. Callers supply their own colors so
 * the exact appearance of each specialized badge is preserved while the duplicated
 * Surface + Row + Text boilerplate lives in a single place.
 */
@Composable
fun TranslucentBadge(
    text: String,
    backgroundColor: Color,
    borderColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 11.sp,
    fontWeight: FontWeight = FontWeight.Medium,
    shape: Shape = RoundedCornerShape(12.dp),
    horizontalPadding: Dp = 8.dp,
    verticalPadding: Dp = 3.dp,
    spacing: Dp = 4.dp,
    maxLines: Int = Int.MAX_VALUE,
    leadingContent: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Surface(
        shape = shape,
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier.then(
            if (onClick != null) Modifier.clip(shape).clickable(onClick = onClick) else Modifier
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing),
            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = verticalPadding)
        ) {
            leadingContent?.invoke(this)
            Text(
                text = text,
                style = MaterialTheme.typography.caption.copy(
                    fontWeight = fontWeight,
                    color = textColor,
                    fontSize = fontSize
                ),
                maxLines = maxLines,
                overflow = if (maxLines == 1) TextOverflow.Ellipsis else TextOverflow.Clip
            )
        }
    }
}

/**
 * Base badge component with rounded corners, semi-translucent background, and custom text/color.
 */
@Composable
fun MediaBadge(
    text: String,
    backgroundColor: Color,
    textColor: Color = CloudStreamColors.OnMediaScrim,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor.copy(alpha = 0.88f))
            .padding(horizontal = 6.dp, vertical = 2.5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

/**
 * Provider tag badge displaying the streaming provider source.
 */
@Composable
fun ProviderBadge(
    apiName: String?,
    modifier: Modifier = Modifier
) {
    if (apiName.isNullOrBlank()) return

    MediaBadge(
        text = apiName,
        backgroundColor = CloudStreamColors.SurfaceElevated.copy(alpha = 0.85f),
        textColor = CloudStreamColors.TextSecondary,
        modifier = modifier
    )
}

/**
 * Quality badge displayed over media posters (e.g. 4K, HD, CAM, HQ, DVD).
 */
@Composable
fun QualityBadge(
    quality: SearchQuality?,
    modifier: Modifier = Modifier
) {
    if (quality == null) return

    val (text, color) = when (quality) {
        SearchQuality.FourK, SearchQuality.UHD -> "4K" to CloudStreamColors.Quality4K
        SearchQuality.HD, SearchQuality.HDR, SearchQuality.BlueRay, SearchQuality.WebRip -> "HD" to CloudStreamColors.QualityHD
        SearchQuality.HQ -> "HQ" to CloudStreamColors.QualityHQ
        SearchQuality.Cam, SearchQuality.CamRip, SearchQuality.HdCam, SearchQuality.Telesync, SearchQuality.Telecine, SearchQuality.WorkPrint -> "CAM" to CloudStreamColors.QualityCAM
        SearchQuality.DVD, SearchQuality.SD, SearchQuality.SDR -> "SD" to CloudStreamColors.QualitySD
    }

    MediaBadge(
        text = text,
        backgroundColor = color,
        modifier = modifier
    )
}

/**
 * Dub / Sub audio and subtitle badges for anime and multi-audio content.
 */
@Composable
fun DubSubBadges(
    searchResponse: SearchResponse,
    modifier: Modifier = Modifier
) {
    if (searchResponse !is AnimeSearchResponse) return

    val dubCount = searchResponse.episodes[DubStatus.Dubbed]
    val subCount = searchResponse.episodes[DubStatus.Subbed]
    val hasDub = searchResponse.dubStatus?.contains(DubStatus.Dubbed) == true || (dubCount != null && dubCount > 0)
    val hasSub = searchResponse.dubStatus?.contains(DubStatus.Subbed) == true || (subCount != null && subCount > 0)

    if (!hasDub && !hasSub) return

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (hasSub) {
            val subLabel = stringResource(Res.string.app_subbed_text).uppercase()
            val subText = if (subCount != null && subCount > 0) "$subLabel $subCount" else subLabel
            MediaBadge(
                text = subText,
                backgroundColor = CloudStreamColors.SubBadge
            )
        }
        if (hasDub) {
            val dubLabel = stringResource(Res.string.app_dubbed_text).uppercase()
            val dubText = if (dubCount != null && dubCount > 0) "$dubLabel $dubCount" else dubLabel
            MediaBadge(
                text = dubText,
                backgroundColor = CloudStreamColors.DubBadge
            )
        }
    }
}

/**
 * Content type badge (e.g. Movie, TV, Anime, Live, Torrent).
 */
@Composable
fun TypeBadge(
    type: TvType?,
    modifier: Modifier = Modifier
) {
    if (type == null) return

    val text = when (type) {
        TvType.Movie -> stringResource(Res.string.typeMovie)
        TvType.TvSeries -> stringResource(Res.string.typeTvSeries)
        TvType.Anime -> stringResource(Res.string.typeAnime)
        TvType.OVA -> stringResource(Res.string.type_ova)
        TvType.AnimeMovie -> stringResource(Res.string.typeAnimeMovie)
        TvType.Live -> stringResource(Res.string.typeLive)
        TvType.Torrent -> stringResource(Res.string.typeTorrent)
        TvType.AsianDrama -> stringResource(Res.string.type_asian_drama)
        TvType.Cartoon -> stringResource(Res.string.type_cartoon)
        TvType.Documentary -> stringResource(Res.string.type_documentary)
        TvType.NSFW -> stringResource(Res.string.type_nsfw)
        else -> type.name
    }

    MediaBadge(
        text = text,
        backgroundColor = CloudStreamColors.SurfaceElevated,
        textColor = CloudStreamColors.TextSecondary,
        modifier = modifier
    )
}

/**
 * Year badge for Movies and TV Series.
 */
@Composable
fun YearBadge(
    year: Int?,
    modifier: Modifier = Modifier
) {
    if (year == null || year <= 0) return

    MediaBadge(
        text = year.toString(),
        backgroundColor = CloudStreamColors.SurfaceElevated.copy(alpha = 0.8f),
        textColor = CloudStreamColors.TextSecondary,
        modifier = modifier
    )
}

/**
 * Watch status badge overlay (e.g. green pill for "Watching", blue pill for "Completed", etc.)
 */
@Composable
fun WatchStatusBadge(
    watchType: Int?,
    modifier: Modifier = Modifier
) {
    if (watchType == null || watchType == 0) return

    val text = when (watchType) {
        1 -> stringResource(Res.string.statusWatching)
        2 -> stringResource(Res.string.statusCompleted)
        3 -> stringResource(Res.string.statusOnHold)
        4 -> stringResource(Res.string.statusDropped)
        5 -> stringResource(Res.string.statusPlanToWatch)
        else -> return
    }

    val color = when (watchType) {
        1 -> CloudStreamColors.Success
        2 -> CloudStreamColors.Secondary
        3 -> CloudStreamColors.Warning
        4 -> CloudStreamColors.Error
        5 -> CloudStreamColors.Primary
        else -> return
    }

    MediaBadge(
        text = text,
        backgroundColor = color,
        textColor = CloudStreamColors.OnMediaScrim,
        modifier = modifier
    )
}

/**
 * Star Rating / Score badge (e.g. ★ 8.5).
 */
@Composable
fun ScoreBadge(
    score: Double?,
    modifier: Modifier = Modifier
) {
    if (score == null || score <= 0.0) return

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(CloudStreamColors.Warning.copy(alpha = 0.22f))
            .padding(horizontal = 6.dp, vertical = 2.5.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = stringResource(Res.string.rating),
                tint = CloudStreamColors.Warning,
                modifier = Modifier.size(11.dp)
            )
            Text(
                text = score.toString().take(3),
                color = CloudStreamColors.Warning,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

/**
 * Show / Series release status badge (Ongoing / Completed).
 */
@Composable
fun ShowStatusBadge(
    status: ShowStatus?,
    modifier: Modifier = Modifier
) {
    if (status == null) return

    val isOngoing = status == ShowStatus.Ongoing
    val color = if (isOngoing) CloudStreamColors.Success else CloudStreamColors.Info
    val text = if (isOngoing) stringResource(Res.string.status_ongoing) else stringResource(Res.string.status_completed)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.2f))
            .padding(horizontal = 6.dp, vertical = 2.5.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .background(color, CircleShape)
            )
            Text(
                text = text,
                color = color,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

/**
 * Content age rating badge (e.g. PG-13, TV-MA, 18+).
 */
@Composable
fun ContentRatingBadge(
    rating: String?,
    modifier: Modifier = Modifier
) {
    if (rating.isNullOrBlank()) return

    MediaBadge(
        text = rating,
        backgroundColor = CloudStreamColors.SurfaceElevated,
        textColor = CloudStreamColors.TextSecondary,
        modifier = modifier
    )
}
