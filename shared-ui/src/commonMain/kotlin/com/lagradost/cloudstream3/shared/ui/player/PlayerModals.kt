package com.lagradost.cloudstream3.shared.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cloudstream.shared_ui.generated.resources.*
import com.lagradost.cloudstream3.shared.ui.components.MediaBadge
import com.lagradost.cloudstream3.shared.ui.components.designsystem.ActionDialog
import com.lagradost.cloudstream3.shared.ui.components.designsystem.BodyMutedText
import com.lagradost.cloudstream3.shared.ui.components.designsystem.CloudStreamTextField
import com.lagradost.cloudstream3.shared.ui.components.designsystem.SelectableOptionCard
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors
import com.lagradost.cloudstream3.shared.viewmodels.player.PlayerActiveModal
import com.lagradost.cloudstream3.shared.viewmodels.player.PlayerAspectRatio
import com.lagradost.cloudstream3.shared.viewmodels.player.PlayerAudioTrack
import com.lagradost.cloudstream3.shared.viewmodels.player.PlayerEpisode
import com.lagradost.cloudstream3.shared.viewmodels.player.PlayerQuality
import com.lagradost.cloudstream3.shared.viewmodels.player.PlayerSubtitleTrack
import com.lagradost.cloudstream3.shared.viewmodels.player.PlayerUiEvent
import com.lagradost.cloudstream3.shared.viewmodels.player.PlayerUiState
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

/**
 * Ergonomic modal dialog for selecting video playback qualities and streaming sources.
 */
@Composable
fun PlayerQualitySourcesModal(
    state: PlayerUiState,
    onEvent: (PlayerUiEvent) -> Unit,
    onDismiss: () -> Unit = { onEvent(PlayerUiEvent.SetActiveModal(null)) }
) {
    ActionDialog(
        onDismissRequest = onDismiss,
        titleRes = Res.string.video_quality,
        icon = {
            Icon(
                painter = painterResource(Res.drawable.ic_baseline_hd_24),
                contentDescription = stringResource(Res.string.quality),
                tint = CloudStreamColors.Primary,
                modifier = Modifier.size(24.dp)
            )
        },
        showCloseButton = true,
        confirmTextRes = Res.string.close,
        onConfirm = onDismiss,
        content = {
            if (state.availableQualities.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    BodyMutedText(
                        text = stringResource(Res.string.no_qualities_available)
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp)
                ) {
                    items(state.availableQualities) { quality ->
                        val isSelected = quality.url == state.currentUrl ||
                                quality == state.selectedQuality ||
                                quality.id == state.selectedQuality?.id ||
                                (quality.isAuto && state.selectedQuality == null)

                        val effectiveRes = quality.effectiveResolution
                        val badgeColor = when {
                            effectiveRes >= 2160 -> CloudStreamColors.Quality4K
                            effectiveRes >= 1080 -> CloudStreamColors.QualityHD
                            effectiveRes >= 720 -> CloudStreamColors.QualityHQ
                            else -> CloudStreamColors.QualitySD
                        }

                        val qualityLabel = if (effectiveRes > 1) {
                            "${effectiveRes}p"
                        } else {
                            stringResource(Res.string.quality_auto)
                        }

                        SelectableOptionCard(
                            isSelected = isSelected,
                            title = quality.name.ifBlank { qualityLabel },
                            leadingContent = {
                                MediaBadge(
                                    text = qualityLabel,
                                    backgroundColor = badgeColor,
                                    textColor = CloudStreamColors.OnMediaScrim
                                )
                            },
                            onClick = {
                                onEvent(PlayerUiEvent.SelectQuality(quality))
                                onEvent(PlayerUiEvent.SetActiveModal(null))
                            }
                        )
                    }
                }
            }
        }
    )
}

/**
 * Ergonomic modal dialog for selecting subtitle tracks with search filter and disable option.
 */
@Composable
fun PlayerSubtitlesModal(
    state: PlayerUiState,
    onEvent: (PlayerUiEvent) -> Unit,
    onDismiss: () -> Unit = { onEvent(PlayerUiEvent.SetActiveModal(null)) }
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredSubtitles = remember(state.availableSubtitles, searchQuery) {
        if (searchQuery.isBlank()) {
            state.availableSubtitles
        } else {
            state.availableSubtitles.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                        it.languageCode?.contains(searchQuery, ignoreCase = true) == true
            }
        }
    }

    ActionDialog(
        onDismissRequest = onDismiss,
        titleRes = Res.string.subtitles,
        icon = {
            Icon(
                painter = painterResource(Res.drawable.ic_outline_subtitles_24),
                contentDescription = stringResource(Res.string.subtitles),
                tint = CloudStreamColors.Secondary,
                modifier = Modifier.size(24.dp)
            )
        },
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
                // Search filter field when subtitle list is large
                if (state.availableSubtitles.size > 5) {
                    CloudStreamTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = stringResource(Res.string.search),
                        leadingIconVector = Icons.Default.Search,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // "Desactivado / None (Off)" initial option
                if (searchQuery.isBlank()) {
                    val isOffSelected = state.selectedSubtitle == null
                    SelectableOptionCard(
                        isSelected = isOffSelected,
                        titleRes = Res.string.noSubtitles,
                        accentColor = CloudStreamColors.Secondary,
                        onClick = {
                            onEvent(PlayerUiEvent.SelectSubtitle(null))
                            onEvent(PlayerUiEvent.SetActiveModal(null))
                        }
                    )
                }

                if (filteredSubtitles.isEmpty() && searchQuery.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        BodyMutedText(
                            text = stringResource(Res.string.no_subtitles_loaded)
                        )
                    }
                } else if (filteredSubtitles.isNotEmpty()) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                    ) {
                        items(filteredSubtitles) { subtitle ->
                            val isSelected = subtitle == state.selectedSubtitle || subtitle.id == state.selectedSubtitle?.id
                            val subtitleFormat = subtitle.languageCode?.takeIf { it.isNotBlank() }
                                ?: if (subtitle.isAutoGenerated) {
                                    stringResource(Res.string.subtitles_from_online)
                                } else {
                                    stringResource(Res.string.subtitles_from_embedded)
                                }

                            SelectableOptionCard(
                                isSelected = isSelected,
                                title = subtitle.name,
                                subtitle = subtitleFormat,
                                accentColor = CloudStreamColors.Secondary,
                                onClick = {
                                    onEvent(PlayerUiEvent.SelectSubtitle(subtitle))
                                    onEvent(PlayerUiEvent.SetActiveModal(null))
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
 * Ergonomic modal dialog for selecting active audio tracks.
 */
@Composable
fun PlayerAudioTracksModal(
    state: PlayerUiState,
    onEvent: (PlayerUiEvent) -> Unit,
    onDismiss: () -> Unit = { onEvent(PlayerUiEvent.SetActiveModal(null)) }
) {
    ActionDialog(
        onDismissRequest = onDismiss,
        titleRes = Res.string.audio_tracks_dialog_title,
        icon = {
            Icon(
                painter = painterResource(Res.drawable.ic_baseline_volume_up_24),
                contentDescription = stringResource(Res.string.audio_tracks_dialog_title),
                tint = CloudStreamColors.Primary,
                modifier = Modifier.size(24.dp)
            )
        },
        showCloseButton = true,
        confirmTextRes = Res.string.close,
        onConfirm = onDismiss,
        content = {
            if (state.availableAudioTracks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    BodyMutedText(
                        text = stringResource(Res.string.no_audio_tracks_available)
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp)
                ) {
                    items(state.availableAudioTracks) { track ->
                        val isSelected = track == state.selectedAudioTrack || track.id == state.selectedAudioTrack?.id

                        SelectableOptionCard(
                            isSelected = isSelected,
                            title = track.name.ifBlank { stringResource(Res.string.audio_singular) },
                            subtitle = track.languageCode?.takeIf { it.isNotBlank() },
                            onClick = {
                                onEvent(PlayerUiEvent.SelectAudioTrack(track))
                                onEvent(PlayerUiEvent.SetActiveModal(null))
                            }
                        )
                    }
                }
            }
        }
    )
}

/**
 * Ergonomic modal dialog for adjusting playback rate / speed.
 */
@Composable
fun PlayerSpeedModal(
    state: PlayerUiState,
    onEvent: (PlayerUiEvent) -> Unit,
    onDismiss: () -> Unit = { onEvent(PlayerUiEvent.SetActiveModal(null)) }
) {
    val speedOptions = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

    ActionDialog(
        onDismissRequest = onDismiss,
        titleRes = Res.string.playback_speed,
        icon = {
            Icon(
                painter = painterResource(Res.drawable.ic_baseline_speed_24),
                contentDescription = stringResource(Res.string.speed),
                tint = CloudStreamColors.Primary,
                modifier = Modifier.size(24.dp)
            )
        },
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
                items(speedOptions) { speed ->
                    val isSelected = abs(state.playbackSpeed - speed) < 0.01f
                    val label = if (speed == 1.0f) stringResource(Res.string.speed_normal) else "${speed}x"

                    SelectableOptionCard(
                        isSelected = isSelected,
                        title = label,
                        onClick = {
                            onEvent(PlayerUiEvent.SetSpeed(speed))
                            onEvent(PlayerUiEvent.SetActiveModal(null))
                        }
                    )
                }
            }
        }
    )
}

/**
 * Ergonomic modal dialog for fast episode navigation with smooth scroll and active episode badge.
 */
@Composable
fun PlayerEpisodesModal(
    state: PlayerUiState,
    onEvent: (PlayerUiEvent) -> Unit,
    onDismiss: () -> Unit = { onEvent(PlayerUiEvent.SetActiveModal(null)) }
) {
    var searchQuery by remember { mutableStateOf("") }
    val episodeWord = stringResource(Res.string.episode)
    val seasonShort = stringResource(Res.string.season_short)
    val episodeShort = stringResource(Res.string.episode_short)

    val filteredPlaylistWithIndices = remember(state.playlist, searchQuery) {
        state.playlist.mapIndexed { index, episode -> index to episode }.filter { (_, ep) ->
            if (searchQuery.isBlank()) {
                true
            } else {
                val epName = ep.name.orEmpty()
                val epNum = ep.episodeNumber?.toString().orEmpty()
                epName.contains(searchQuery, ignoreCase = true) || epNum.contains(searchQuery)
            }
        }
    }

    val listState = rememberLazyListState()

    // Smoothly scroll to current active episode on dialog open
    LaunchedEffect(state.currentEpisodeIndex) {
        if (state.currentEpisodeIndex in state.playlist.indices) {
            val targetIndex = filteredPlaylistWithIndices.indexOfFirst { it.first == state.currentEpisodeIndex }
            if (targetIndex >= 0) {
                listState.animateScrollToItem(targetIndex)
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
                if (state.playlist.size > 5) {
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
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                    ) {
                        itemsIndexed(filteredPlaylistWithIndices) { _, (originalIndex, ep) ->
                            val isSelected = originalIndex == state.currentEpisodeIndex || ep.id == state.currentEpisode?.id
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
                                leadingContent = if (isSelected) {
                                    {
                                        MediaBadge(
                                            text = stringResource(Res.string.playing),
                                            backgroundColor = CloudStreamColors.Primary,
                                            textColor = CloudStreamColors.OnMediaScrim
                                        )
                                    }
                                } else null,
                                onClick = {
                                    onEvent(PlayerUiEvent.SelectEpisode(originalIndex))
                                    onEvent(PlayerUiEvent.SetActiveModal(null))
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
 * Ergonomic modal dialog for selecting video aspect ratio / scaling mode.
 */
@Composable
fun PlayerAspectRatioModal(
    state: PlayerUiState,
    onEvent: (PlayerUiEvent) -> Unit,
    onDismiss: () -> Unit = { onEvent(PlayerUiEvent.SetActiveModal(null)) }
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
                    val isSelected = state.aspectRatio == ratio
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
                            onEvent(PlayerUiEvent.SetAspectRatio(ratio))
                            onEvent(PlayerUiEvent.SetActiveModal(null))
                        }
                    )
                }
            }
        }
    )
}

/**
 * Centralized active modal dispatcher rendering the corresponding dialog on top of the player overlay.
 */
@Composable
fun PlayerActiveModals(
    state: PlayerUiState,
    onEvent: (PlayerUiEvent) -> Unit,
    onDismiss: () -> Unit = { onEvent(PlayerUiEvent.SetActiveModal(null)) }
) {
    when (state.activeModal) {
        PlayerActiveModal.QUALITY_SOURCES -> PlayerQualitySourcesModal(
            state = state,
            onEvent = onEvent,
            onDismiss = onDismiss
        )
        PlayerActiveModal.SUBTITLES -> PlayerSubtitlesModal(
            state = state,
            onEvent = onEvent,
            onDismiss = onDismiss
        )
        PlayerActiveModal.AUDIO_TRACKS -> PlayerAudioTracksModal(
            state = state,
            onEvent = onEvent,
            onDismiss = onDismiss
        )
        PlayerActiveModal.SPEED -> PlayerSpeedModal(
            state = state,
            onEvent = onEvent,
            onDismiss = onDismiss
        )
        PlayerActiveModal.EPISODES -> PlayerEpisodesModal(
            state = state,
            onEvent = onEvent,
            onDismiss = onDismiss
        )
        PlayerActiveModal.SETTINGS -> PlayerAspectRatioModal(
            state = state,
            onEvent = onEvent,
            onDismiss = onDismiss
        )
        null -> Unit
    }
}
