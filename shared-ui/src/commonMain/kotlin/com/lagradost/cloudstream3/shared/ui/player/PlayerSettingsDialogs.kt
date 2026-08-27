package com.lagradost.cloudstream3.shared.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cloudstream.shared_ui.generated.resources.*
import com.lagradost.cloudstream3.shared.ui.components.designsystem.ActionDialog
import com.lagradost.cloudstream3.shared.ui.components.designsystem.BodyMutedText
import com.lagradost.cloudstream3.shared.ui.components.designsystem.CloudStreamTextField
import com.lagradost.cloudstream3.shared.ui.components.designsystem.NumberStepAdjuster
import com.lagradost.cloudstream3.shared.ui.components.designsystem.PrimaryButton
import com.lagradost.cloudstream3.shared.ui.components.designsystem.SecondaryButton
import com.lagradost.cloudstream3.shared.ui.components.designsystem.SelectableOptionCard
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors
import com.lagradost.cloudstream3.shared.viewmodels.player.PlayerAspectRatio
import com.lagradost.cloudstream3.shared.viewmodels.player.PlayerEpisode
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Modal dialog for adjusting audio and subtitle synchronization delay.
 * Provides fine-tuning step buttons, manual numeric input, and reset action using [NumberStepAdjuster] and [ActionDialog].
 */
@Composable
fun PlayerDelayDialog(
    title: String? = null,
    titleRes: StringResource? = null,
    currentDelayMs: Long,
    onDelayChanged: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val resolvedTitle = title ?: titleRes?.let { stringResource(it) } ?: stringResource(Res.string.subtitle_offset_title)

    ActionDialog(
        onDismissRequest = onDismiss,
        title = resolvedTitle,
        iconVector = Icons.Default.Timer,
        showCloseButton = true,
        confirmTextRes = Res.string.close,
        onConfirm = onDismiss,
        content = {
            NumberStepAdjuster(
                value = currentDelayMs,
                onValueChange = onDelayChanged,
                unitRes = Res.string.unit_ms,
                explanationRes = Res.string.delay_explanation,
                labelRes = Res.string.manual_delay_input,
                stepMinusRes = Res.string.delay_minus_50,
                stepPlusRes = Res.string.delay_plus_50,
                step = 50L
            )
        }
    )
}

/**
 * Modal dialog for selecting video aspect ratio / scaling mode adhering to Design System.
 */
@Composable
fun PlayerAspectRatioDialog(
    currentRatio: PlayerAspectRatio,
    onSelectRatio: (PlayerAspectRatio) -> Unit,
    onDismiss: () -> Unit
) {
    ActionDialog(
        onDismissRequest = onDismiss,
        titleRes = Res.string.aspect_ratio,
        iconVector = Icons.Default.AspectRatio,
        showCloseButton = true,
        confirmTextRes = Res.string.close,
        onConfirm = onDismiss,
        content = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp)
            ) {
                items(PlayerAspectRatio.entries) { ratio ->
                    val isSelected = currentRatio == ratio
                    val (titleRes, descRes) = when (ratio) {
                        PlayerAspectRatio.Fit -> Res.string.aspect_ratio_fit to Res.string.aspect_ratio_fit
                        PlayerAspectRatio.Zoom -> Res.string.aspect_ratio_zoom to Res.string.aspect_ratio_zoom
                        PlayerAspectRatio.Stretch -> Res.string.aspect_ratio_stretch to Res.string.aspect_ratio_stretch
                        PlayerAspectRatio.Original -> Res.string.aspect_ratio_original to Res.string.aspect_ratio_original
                    }

                    SelectableOptionCard(
                        isSelected = isSelected,
                        titleRes = titleRes,
                        subtitleRes = descRes,
                        onClick = {
                            onSelectRatio(ratio)
                            onDismiss()
                        }
                    )
                }
            }
        }
    )
}

/**
 * Fast Episode Switcher modal dialog with search filtering, episode numbering, and season grouping.
 */
@Composable
fun PlayerEpisodeSwitcherDialog(
    playlist: List<PlayerEpisode>,
    currentIndex: Int,
    onSelectEpisode: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val episodeWord = stringResource(Res.string.episode)
    val seasonShort = stringResource(Res.string.season_short)
    val episodeShort = stringResource(Res.string.episode_short)

    val filteredPlaylistWithIndices = remember(playlist, searchQuery) {
        playlist.mapIndexed { index, episode -> index to episode }.filter { (_, ep) ->
            if (searchQuery.isBlank()) {
                true
            } else {
                val epName = ep.name.orEmpty()
                val epNum = ep.episodeNumber?.toString().orEmpty()
                epName.contains(searchQuery, ignoreCase = true) || epNum.contains(searchQuery)
            }
        }
    }

    ActionDialog(
        onDismissRequest = onDismiss,
        titleRes = Res.string.episodes,
        iconVector = Icons.Default.VideoLibrary,
        showCloseButton = true,
        confirmTextRes = Res.string.close,
        onConfirm = onDismiss,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Search filter field when playlist is large
                if (playlist.size > 5) {
                    CloudStreamTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = stringResource(Res.string.search_episodes_hint),
                        leadingIconVector = Icons.Default.Search,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (filteredPlaylistWithIndices.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        BodyMutedText(
                            text = stringResource(Res.string.no_episodes_available)
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                    ) {
                        itemsIndexed(filteredPlaylistWithIndices) { _, (originalIndex, ep) ->
                            val isSelected = originalIndex == currentIndex
                            val episodeTitle = ep.name ?: "$episodeWord ${ep.episodeNumber ?: (originalIndex + 1)}"

                            val subtitleDetails = buildString {
                                if (ep.seasonNumber != null && ep.seasonNumber > 0) {
                                    append("$seasonShort${ep.seasonNumber} ")
                                }
                                if (ep.episodeNumber != null) {
                                    append("$episodeShort${ep.episodeNumber}")
                                }
                            }

                            SelectableOptionCard(
                                isSelected = isSelected,
                                title = episodeTitle,
                                subtitle = subtitleDetails.takeIf { it.isNotBlank() },
                                onClick = {
                                    onSelectEpisode(originalIndex)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
            }
        }
    )
}

/**
 * Fast Episode Switcher modal dialog alias for design system consistency.
 */
@Composable
fun PlayerEpisodesFastSwitcherDialog(
    playlist: List<PlayerEpisode>,
    currentIndex: Int,
    onSelectEpisode: (Int) -> Unit,
    onDismiss: () -> Unit
) = PlayerEpisodeSwitcherDialog(
    playlist = playlist,
    currentIndex = currentIndex,
    onSelectEpisode = onSelectEpisode,
    onDismiss = onDismiss
)

/**
 * Modal dialog for accessing subtitle appearance settings within the player.
 */
@Composable
fun PlayerSubtitleAppearanceDialog(
    onDismiss: () -> Unit
) {
    ActionDialog(
        onDismissRequest = onDismiss,
        titleRes = Res.string.subtitle_appearance,
        icon = {
            Icon(
                painter = painterResource(Res.drawable.ic_outline_subtitles_24),
                contentDescription = stringResource(Res.string.subtitle_appearance),
                tint = CloudStreamColors.Primary,
                modifier = Modifier.size(24.dp)
            )
        },
        showCloseButton = true,
        content = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                BodyMutedText(
                    text = stringResource(Res.string.subtitles_settings)
                )
            }
        },
        buttons = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SecondaryButton(
                    text = stringResource(Res.string.cancel),
                    onClick = onDismiss
                )
                Spacer(modifier = Modifier.width(8.dp))
                PrimaryButton(
                    text = stringResource(Res.string.close),
                    onClick = onDismiss
                )
            }
        }
    )
}
