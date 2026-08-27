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
import cloudstream.shared_ui.generated.resources.*
import com.lagradost.cloudstream3.shared.ui.components.designsystem.ActionDialog
import com.lagradost.cloudstream3.shared.ui.components.designsystem.SelectableOptionCard
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors
import com.lagradost.cloudstream3.shared.viewmodels.result.ResultEpisode
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Standardized Context Action Modal Dialog for Episodes and Movies in Compose Multiplatform.
 *
 * Provides quick actions:
 * 1. Play in App ([onPlayInApp])
 * 2. Play Mirror / Server Selection ([onPlayMirror])
 * 3. Reload Links ([onReloadLinks])
 * 4. Copy Link to Clipboard ([onCopyLink])
 * 5. Download Episode ([onDownload])
 * 6. Download with Mirror ([onDownloadMirror])
 * 7. Mark as Watched / Unwatched ([onToggleWatchState])
 * 8. Mark Watched up to this episode ([onMarkUpToThisEpisode], hidden for movies)
 */
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

    val displayTitle = when {
        !episode.name.isNullOrBlank() -> episode.name
        isMovie -> episode.headerName.ifBlank { stringResource(Res.string.typeMovie) }
        episode.episode > 0 -> "$episodeText ${episode.episode}"
        else -> episode.headerName.ifBlank { episodeText }
    }

    val displaySubtitle = when {
        !isMovie && episode.season != null && episode.season > 0 && !episode.name.isNullOrBlank() -> {
            "$seasonText ${episode.season} • $episodeText ${episode.episode}"
        }
        !isMovie && episode.headerName.isNotBlank() && episode.name != episode.headerName -> {
            episode.headerName
        }
        else -> null
    }

    ActionDialog(
        onDismissRequest = onDismiss,
        title = displayTitle,
        subtitle = displaySubtitle,
        showCloseButton = true,
        maxWidth = 460.dp,
        modifier = modifier,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. Play in app
                SelectableOptionCard(
                    title = stringResource(Res.string.episode_action_play_in_app),
                    painter = painterResource(Res.drawable.ic_baseline_play_arrow_24),
                    accentColor = CloudStreamColors.Primary,
                    isSelected = false,
                    shape = RoundedCornerShape(12.dp),
                    onClick = onPlayInApp
                )

                // 2. Play mirror / server selector
                SelectableOptionCard(
                    title = stringResource(Res.string.episode_action_play_mirror),
                    painter = painterResource(Res.drawable.ic_baseline_playlist_play_24),
                    accentColor = CloudStreamColors.Primary,
                    isSelected = false,
                    shape = RoundedCornerShape(12.dp),
                    onClick = onPlayMirror
                )

                // 3. Reload links
                SelectableOptionCard(
                    title = stringResource(Res.string.episode_action_reload_links),
                    icon = Icons.Default.Refresh,
                    accentColor = CloudStreamColors.TextSecondary,
                    isSelected = false,
                    shape = RoundedCornerShape(12.dp),
                    onClick = onReloadLinks
                )

                // 4. Copy link to clipboard
                SelectableOptionCard(
                    title = stringResource(Res.string.sort_copy),
                    painter = painterResource(Res.drawable.ic_baseline_link_24),
                    accentColor = CloudStreamColors.TextSecondary,
                    isSelected = false,
                    shape = RoundedCornerShape(12.dp),
                    onClick = onCopyLink
                )

                // 5. Download episode
                SelectableOptionCard(
                    title = stringResource(Res.string.episode_action_auto_download),
                    painter = painterResource(Res.drawable.baseline_downloading_24),
                    accentColor = CloudStreamColors.TextSecondary,
                    isSelected = false,
                    shape = RoundedCornerShape(12.dp),
                    onClick = onDownload
                )

                // 6. Download with mirror
                SelectableOptionCard(
                    title = stringResource(Res.string.episode_action_download_mirror),
                    painter = painterResource(Res.drawable.baseline_downloading_24),
                    accentColor = CloudStreamColors.TextSecondary,
                    isSelected = false,
                    shape = RoundedCornerShape(12.dp),
                    onClick = onDownloadMirror
                )

                // 7. Toggle watched state
                val isWatched = episode.isWatched
                val watchStateTitle = if (isWatched) {
                    stringResource(Res.string.action_remove_from_watched)
                } else {
                    stringResource(Res.string.action_mark_as_watched)
                }
                val watchStateColor = if (isWatched) CloudStreamColors.Warning else CloudStreamColors.Success

                SelectableOptionCard(
                    title = watchStateTitle,
                    icon = Icons.Default.Check,
                    accentColor = watchStateColor,
                    isSelected = false,
                    shape = RoundedCornerShape(12.dp),
                    onClick = onToggleWatchState
                )

                // 8. Mark watched up to this episode (hidden for movies)
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
    )
}
