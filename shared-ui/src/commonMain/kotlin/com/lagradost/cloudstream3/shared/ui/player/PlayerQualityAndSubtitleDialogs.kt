package com.lagradost.cloudstream3.shared.ui.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cloudstream.shared_ui.generated.resources.*
import com.lagradost.cloudstream3.shared.ui.components.designsystem.ActionDialog
import com.lagradost.cloudstream3.shared.ui.components.designsystem.BodyMutedText
import com.lagradost.cloudstream3.shared.ui.components.designsystem.CloudStreamTextField
import com.lagradost.cloudstream3.shared.ui.components.designsystem.SelectableOptionCard
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors
import com.lagradost.cloudstream3.shared.viewmodels.player.PlayerAspectRatio
import com.lagradost.cloudstream3.shared.viewmodels.player.PlayerAudioTrack
import com.lagradost.cloudstream3.shared.viewmodels.player.PlayerQuality
import com.lagradost.cloudstream3.shared.viewmodels.player.PlayerSubtitleTrack
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Modal dialog for selecting video playback quality adhering to the CloudStream Design System.
 */
@Composable
fun PlayerQualityDialog(
    qualities: List<PlayerQuality>,
    selectedQuality: PlayerQuality?,
    onSelectQuality: (PlayerQuality) -> Unit,
    onDismiss: () -> Unit
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
            if (qualities.isEmpty()) {
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
                    items(qualities) { quality ->
                        val isSelected = quality.id == selectedQuality?.id || (quality.isAuto && selectedQuality == null)
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
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = badgeColor.copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, badgeColor)
                                ) {
                                    Text(
                                        text = qualityLabel,
                                        style = MaterialTheme.typography.caption.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = badgeColor,
                                            fontSize = 11.sp
                                        ),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            },
                            onClick = {
                                onSelectQuality(quality)
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    )
}

/**
 * Modal dialog for selecting subtitle tracks adhering to the CloudStream Design System.
 */
@Composable
fun PlayerSubtitleDialog(
    subtitles: List<PlayerSubtitleTrack>,
    selectedSubtitle: PlayerSubtitleTrack?,
    onSelectSubtitle: (PlayerSubtitleTrack?) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredSubtitles = remember(subtitles, searchQuery) {
        if (searchQuery.isBlank()) {
            subtitles
        } else {
            subtitles.filter {
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
                if (subtitles.size > 5) {
                    CloudStreamTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = stringResource(Res.string.search),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = CloudStreamColors.TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // "Desactivar / Off" Option
                if (searchQuery.isBlank()) {
                    val isOffSelected = selectedSubtitle == null
                    SelectableOptionCard(
                        isSelected = isOffSelected,
                        titleRes = Res.string.noSubtitles,
                        accentColor = CloudStreamColors.Secondary,
                        onClick = {
                            onSelectSubtitle(null)
                            onDismiss()
                        }
                    )

                    if (filteredSubtitles.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Divider(color = CloudStreamColors.Divider)
                    }
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
                            val isSelected = selectedSubtitle?.id == subtitle.id

                            SelectableOptionCard(
                                isSelected = isSelected,
                                title = subtitle.name,
                                subtitle = subtitle.languageCode?.takeIf { it.isNotBlank() },
                                accentColor = CloudStreamColors.Secondary,
                                onClick = {
                                    onSelectSubtitle(subtitle)
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
 * Alias for PlayerSubtitleDialog to match design system naming.
 */
@Composable
fun PlayerSubtitleTracksDialog(
    subtitles: List<PlayerSubtitleTrack>,
    selectedSubtitle: PlayerSubtitleTrack?,
    onSelectSubtitle: (PlayerSubtitleTrack?) -> Unit,
    onDismiss: () -> Unit
) = PlayerSubtitleDialog(
    subtitles = subtitles,
    selectedSubtitle = selectedSubtitle,
    onSelectSubtitle = onSelectSubtitle,
    onDismiss = onDismiss
)

/**
 * Modal dialog for selecting video playback speed adhering to the CloudStream Design System.
 */
@Composable
fun PlayerSpeedDialog(
    currentSpeed: Float,
    onSelectSpeed: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val speedOptions = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)

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
                    val isSelected = (currentSpeed - speed).let { if (it < 0) -it else it } < 0.01f
                    val label = if (speed == 1.0f) stringResource(Res.string.speed_normal) else "${speed}x"

                    SelectableOptionCard(
                        isSelected = isSelected,
                        title = label,
                        onClick = {
                            onSelectSpeed(speed)
                            onDismiss()
                        }
                    )
                }
            }
        }
    )
}

/**
 * Alias for PlayerSpeedDialog to match design system naming.
 */
@Composable
fun PlayerPlaybackSpeedDialog(
    currentSpeed: Float,
    onSelectSpeed: (Float) -> Unit,
    onDismiss: () -> Unit
) = PlayerSpeedDialog(
    currentSpeed = currentSpeed,
    onSelectSpeed = onSelectSpeed,
    onDismiss = onDismiss
)

/**
 * Modal dialog for selecting video audio tracks adhering to the CloudStream Design System.
 */
@Composable
fun PlayerAudioTrackDialog(
    audioTracks: List<PlayerAudioTrack>,
    selectedAudioTrack: PlayerAudioTrack?,
    onSelectAudioTrack: (PlayerAudioTrack) -> Unit,
    onDismiss: () -> Unit
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
            if (audioTracks.isEmpty()) {
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
                    items(audioTracks) { track ->
                        val isSelected = track.id == selectedAudioTrack?.id

                        SelectableOptionCard(
                            isSelected = isSelected,
                            title = track.name.ifBlank { stringResource(Res.string.audio_singular) },
                            subtitle = track.languageCode?.takeIf { it.isNotBlank() },
                            onClick = {
                                onSelectAudioTrack(track)
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    )
}

/**
 * Alias for PlayerAudioTrackDialog to match design system naming.
 */
@Composable
fun PlayerAudioTracksDialog(
    audioTracks: List<PlayerAudioTrack>,
    selectedAudioTrack: PlayerAudioTrack?,
    onSelectAudioTrack: (PlayerAudioTrack) -> Unit,
    onDismiss: () -> Unit
) = PlayerAudioTrackDialog(
    audioTracks = audioTracks,
    selectedAudioTrack = selectedAudioTrack,
    onSelectAudioTrack = onSelectAudioTrack,
    onDismiss = onDismiss
)

/**
 * Modal dialog for selecting video aspect ratio / resize mode adhering to the CloudStream Design System.
 */
@Composable
fun PlayerResizeModeDialog(
    currentRatio: PlayerAspectRatio,
    onSelectRatio: (PlayerAspectRatio) -> Unit,
    onDismiss: () -> Unit
) {
    ActionDialog(
        onDismissRequest = onDismiss,
        titleRes = Res.string.aspect_ratio,
        icon = {
            Icon(
                painter = painterResource(Res.drawable.ic_baseline_aspect_ratio_24),
                contentDescription = stringResource(Res.string.aspect_ratio),
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
                items(PlayerAspectRatio.entries) { ratio ->
                    val isSelected = currentRatio == ratio
                    val titleRes = when (ratio) {
                        PlayerAspectRatio.Fit -> Res.string.aspect_ratio_fit
                        PlayerAspectRatio.Zoom -> Res.string.aspect_ratio_zoom
                        PlayerAspectRatio.Stretch -> Res.string.aspect_ratio_stretch
                        PlayerAspectRatio.Original -> Res.string.aspect_ratio_original
                    }

                    SelectableOptionCard(
                        titleRes = titleRes,
                        isSelected = isSelected,
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
