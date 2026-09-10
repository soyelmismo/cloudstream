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
import com.lagradost.cloudstream3.shared.ui.theme.AppColors
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import com.lagradost.cloudstream4.generated.resources.*

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

private data class QualityBadgeConfig(val text: String, val color: Color)

private val QUALITY_CONFIG_MAP: Map<SearchQuality, QualityBadgeConfig> = mapOf(
    SearchQuality.FourK to QualityBadgeConfig("4K", AppColors.Quality4K),
    SearchQuality.UHD to QualityBadgeConfig("4K", AppColors.Quality4K),
    SearchQuality.HD to QualityBadgeConfig("HD", AppColors.QualityHD),
    SearchQuality.HDR to QualityBadgeConfig("HD", AppColors.QualityHD),
    SearchQuality.BlueRay to QualityBadgeConfig("HD", AppColors.QualityHD),
    SearchQuality.WebRip to QualityBadgeConfig("HD", AppColors.QualityHD),
    SearchQuality.HQ to QualityBadgeConfig("HQ", AppColors.QualityHQ),
    SearchQuality.Cam to QualityBadgeConfig("CAM", AppColors.QualityCAM),
    SearchQuality.CamRip to QualityBadgeConfig("CAM", AppColors.QualityCAM),
    SearchQuality.HdCam to QualityBadgeConfig("CAM", AppColors.QualityCAM),
    SearchQuality.Telesync to QualityBadgeConfig("CAM", AppColors.QualityCAM),
    SearchQuality.Telecine to QualityBadgeConfig("CAM", AppColors.QualityCAM),
    SearchQuality.WorkPrint to QualityBadgeConfig("CAM", AppColors.QualityCAM),
    SearchQuality.DVD to QualityBadgeConfig("SD", AppColors.QualitySD),
    SearchQuality.SD to QualityBadgeConfig("SD", AppColors.QualitySD),
    SearchQuality.SDR to QualityBadgeConfig("SD", AppColors.QualitySD)
)

/**
 * Quality badge displayed over media posters (e.g. 4K, HD, CAM, HQ, DVD).
 */
@Composable
fun QualityBadge(
    quality: SearchQuality?,
    modifier: Modifier = Modifier
) {
    if (quality == null) return
    val config = QUALITY_CONFIG_MAP[quality] ?: return

    MediaBadge(
        text = config.text,
        backgroundColor = config.color,
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

private val TV_TYPE_RES_MAP: Map<TvType, StringResource> = mapOf(
    TvType.Movie to Res.string.typeMovie,
    TvType.TvSeries to Res.string.typeTvSeries,
    TvType.Anime to Res.string.typeAnime,
    TvType.OVA to Res.string.type_ova,
    TvType.AnimeMovie to Res.string.typeAnimeMovie,
    TvType.Live to Res.string.typeLive,
    TvType.Torrent to Res.string.typeTorrent,
    TvType.AsianDrama to Res.string.type_asian_drama,
    TvType.Cartoon to Res.string.type_cartoon,
    TvType.Documentary to Res.string.type_documentary,
    TvType.NSFW to Res.string.type_nsfw
)

/**
 * Content type badge (e.g. Movie, TV, Anime, Live, Torrent).
 */
@Composable
fun TypeBadge(
    type: TvType?,
    modifier: Modifier = Modifier
) {
    if (type == null) return

    val res = TV_TYPE_RES_MAP[type]
    val text = if (res != null) stringResource(res) else type.name

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

private data class WatchStatusConfig(val textRes: StringResource, val color: Color)

private val WATCH_STATUS_MAP: Map<Int, WatchStatusConfig> = mapOf(
    1 to WatchStatusConfig(Res.string.statusWatching, AppColors.SyncStatusWatching),
    2 to WatchStatusConfig(Res.string.statusCompleted, AppColors.SyncStatusCompleted),
    3 to WatchStatusConfig(Res.string.statusOnHold, AppColors.SyncStatusPaused),
    4 to WatchStatusConfig(Res.string.statusDropped, AppColors.SyncStatusDropped),
    5 to WatchStatusConfig(Res.string.statusPlanToWatch, AppColors.SyncStatusPlanToWatch)
)

/**
 * Watch status badge overlay (e.g. green pill for "Watching", blue pill for "Completed", etc.)
 */
@Composable
fun WatchStatusBadge(
    watchType: Int?,
    modifier: Modifier = Modifier
) {
    if (watchType == null || watchType == 0) return
    val config = WATCH_STATUS_MAP[watchType] ?: return

    MediaBadge(
        text = stringResource(config.textRes),
        backgroundColor = config.color,
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

@Preview
@Composable
private fun MediaBadgesPreview() {
    CloudStreamTheme {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QualityBadge(quality = SearchQuality.FourK)
            TypeBadge(type = TvType.Movie)
            YearBadge(year = 2024)
            ScoreBadge(score = 8.8)
            ShowStatusBadge(status = ShowStatus.Ongoing)
            WatchStatusBadge(watchType = 1)
        }
    }
}

