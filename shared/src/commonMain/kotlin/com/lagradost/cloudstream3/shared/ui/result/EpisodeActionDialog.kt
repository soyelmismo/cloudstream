package com.lagradost.cloudstream3.shared.ui.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream4.generated.resources.*
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.shared.ui.components.designsystem.ActionDialog
import com.lagradost.cloudstream3.shared.ui.components.designsystem.SelectableOptionCard
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamTheme
import com.lagradost.cloudstream3.shared.viewmodels.result.ResultEpisode
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

fun buildEpisodeActionTitle(
    episode: ResultEpisode,
    isMovie: Boolean,
    episodeText: String,
    movieText: String
): String {
    if (!episode.name.isNullOrBlank()) return episode.name
    if (isMovie) return episode.headerName.ifBlank { movieText }
    if (episode.episode > 0) return "$episodeText ${episode.episode}"
    return episode.headerName.ifBlank { episodeText }
}

fun buildEpisodeActionSubtitle(
    episode: ResultEpisode,
    isMovie: Boolean,
    seasonText: String,
    episodeText: String
): String? {
    if (isMovie) return null
    val season = episode.season
    if (season != null && season > 0 && !episode.name.isNullOrBlank()) {
        return "$seasonText $season • $episodeText ${episode.episode}"
    }
    if (episode.headerName.isNotBlank() && episode.name != episode.headerName) {
        return episode.headerName
    }
    return null
}

@Composable
fun EpisodeActionDialog(
    episode: ResultEpisode,
    isMovie: Boolean = false,
    onDismiss: () -> Unit,
    onPlayInApp: () -> Unit,
    onPlayMirror: () -> Unit,
    onReloadLinks: () -> Unit,
    onCopyLink: () -> Unit,
    onDownload: () -> Unit,
    onDownloadMirror: () -> Unit,
    onToggleWatchState: () -> Unit,
    onMarkUpToThisEpisode: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val episodeText = stringResource(Res.string.episode)
    val seasonText = stringResource(Res.string.season)
    val movieText = stringResource(Res.string.typeMovie)

    val displayTitle = buildEpisodeActionTitle(episode, isMovie, episodeText, movieText)
    val displaySubtitle = buildEpisodeActionSubtitle(episode, isMovie, seasonText, episodeText)

    ActionDialog(
        onDismissRequest = onDismiss,
        title = displayTitle,
        subtitle = displaySubtitle,
        showCloseButton = true,
        maxWidth = 460.dp,
        modifier = modifier,
        content = {
            EpisodeActionList(
                isMovie = isMovie,
                isWatched = episode.isWatched,
                onPlayInApp = onPlayInApp,
                onPlayMirror = onPlayMirror,
                onReloadLinks = onReloadLinks,
                onCopyLink = onCopyLink,
                onDownload = onDownload,
                onDownloadMirror = onDownloadMirror,
                onToggleWatchState = onToggleWatchState,
                onMarkUpToThisEpisode = onMarkUpToThisEpisode
            )
        }
    )
}

@Composable
private fun EpisodeActionList(
    isMovie: Boolean,
    isWatched: Boolean,
    onPlayInApp: () -> Unit,
    onPlayMirror: () -> Unit,
    onReloadLinks: () -> Unit,
    onCopyLink: () -> Unit,
    onDownload: () -> Unit,
    onDownloadMirror: () -> Unit,
    onToggleWatchState: () -> Unit,
    onMarkUpToThisEpisode: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 440.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SelectableOptionCard(
            title = stringResource(Res.string.episode_action_play_in_app),
            painter = painterResource(Res.drawable.ic_baseline_play_arrow_24),
            accentColor = CloudStreamColors.Primary,
            isSelected = false,
            shape = RoundedCornerShape(12.dp),
            onClick = onPlayInApp
        )

        SelectableOptionCard(
            title = stringResource(Res.string.episode_action_play_mirror),
            painter = painterResource(Res.drawable.ic_baseline_playlist_play_24),
            accentColor = CloudStreamColors.Primary,
            isSelected = false,
            shape = RoundedCornerShape(12.dp),
            onClick = onPlayMirror
        )

        SelectableOptionCard(
            title = stringResource(Res.string.episode_action_reload_links),
            icon = Icons.Default.Refresh,
            accentColor = CloudStreamColors.TextSecondary,
            isSelected = false,
            shape = RoundedCornerShape(12.dp),
            onClick = onReloadLinks
        )

        SelectableOptionCard(
            title = stringResource(Res.string.sort_copy),
            painter = painterResource(Res.drawable.ic_baseline_link_24),
            accentColor = CloudStreamColors.TextSecondary,
            isSelected = false,
            shape = RoundedCornerShape(12.dp),
            onClick = onCopyLink
        )

        SelectableOptionCard(
            title = stringResource(Res.string.episode_action_auto_download),
            painter = painterResource(Res.drawable.baseline_downloading_24),
            accentColor = CloudStreamColors.TextSecondary,
            isSelected = false,
            shape = RoundedCornerShape(12.dp),
            onClick = onDownload
        )

        SelectableOptionCard(
            title = stringResource(Res.string.episode_action_download_mirror),
            painter = painterResource(Res.drawable.baseline_downloading_24),
            accentColor = CloudStreamColors.TextSecondary,
            isSelected = false,
            shape = RoundedCornerShape(12.dp),
            onClick = onDownloadMirror
        )

        EpisodeWatchStateCard(
            isWatched = isWatched,
            onClick = onToggleWatchState
        )

        if (!isMovie && onMarkUpToThisEpisode != null) {
            SelectableOptionCard(
                title = stringResource(Res.string.action_mark_watched_up_to_this_episode),
                painter = painterResource(Res.drawable.ic_baseline_playlist_play_24),
                accentColor = CloudStreamColors.Secondary,
                isSelected = false,
                shape = RoundedCornerShape(12.dp),
                onClick = onMarkUpToThisEpisode
            )
        }
    }
}

@Composable
private fun EpisodeWatchStateCard(
    isWatched: Boolean,
    onClick: () -> Unit
) {
    val titleRes = if (isWatched) Res.string.action_remove_from_watched else Res.string.action_mark_as_watched
    val accentColor = if (isWatched) CloudStreamColors.Warning else CloudStreamColors.Success

    SelectableOptionCard(
        title = stringResource(titleRes),
        icon = Icons.Default.Check,
        accentColor = accentColor,
        isSelected = false,
        shape = RoundedCornerShape(12.dp),
        onClick = onClick
    )
}

@Preview
@Composable
private fun EpisodeActionDialogPreview() {
    CloudStreamTheme {
        EpisodeActionDialog(
            episode = ResultEpisode(
                headerName = "Season 1",
                name = "Pilot Episode",
                poster = null,
                episode = 1,
                seasonIndex = 1,
                season = 1,
                data = "",
                apiName = "TestAPI",
                id = 1,
                index = 0,
                tvType = TvType.TvSeries,
                parentId = 100
            ),
            isMovie = false,
            onDismiss = {},
            onPlayInApp = {},
            onPlayMirror = {},
            onReloadLinks = {},
            onCopyLink = {},
            onDownload = {},
            onDownloadMirror = {},
            onToggleWatchState = {},
            onMarkUpToThisEpisode = {}
        )
    }
}
