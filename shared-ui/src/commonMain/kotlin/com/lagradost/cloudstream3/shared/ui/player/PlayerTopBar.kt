package com.lagradost.cloudstream3.shared.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cloudstream.shared_ui.generated.resources.*
import com.lagradost.cloudstream3.shared.ui.focus.dpadFocusable
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors
import com.lagradost.cloudstream3.shared.viewmodels.player.PlayerActiveModal
import com.lagradost.cloudstream3.shared.viewmodels.player.PlayerUiEvent
import com.lagradost.cloudstream3.shared.viewmodels.player.PlayerUiState
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Top control bar for the video player overlay.
 *
 * Provides:
 * - Back button with wide touch target (min 48x48dp), hover feedback, and TV D-Pad focus.
 * - Content title and episode subtitle metadata with clear typography.
 * - Quick action buttons for Quality/Sources, Subtitles, Audio Tracks, Playback Speed, and Controls Lock.
 * - Safe drawing window insets adherence.
 */
@Composable
fun PlayerTopBar(
    state: PlayerUiState,
    onEvent: (PlayerUiEvent) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentEp = state.currentEpisode
    val episodePrefix = stringResource(Res.string.episode)
    val title = currentEp?.name
        ?: currentEp?.episodeNumber?.let { "$episodePrefix $it" }
        ?: state.currentUrl?.substringAfterLast("/")?.substringBefore("?")
        ?: stringResource(Res.string.playing)

    val seasonShort = stringResource(Res.string.season_short)
    val episodeShort = stringResource(Res.string.episode_short)
    val subtitleInfo = buildString {
        if (currentEp?.seasonNumber != null && currentEp.seasonNumber > 0) {
            append("$seasonShort${currentEp.seasonNumber} ")
        }
        if (currentEp?.episodeNumber != null) {
            append("$episodeShort${currentEp.episodeNumber}")
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left Section: Back Button & Media Details
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f, fill = false)
        ) {
            // Back Navigation Button (min 48x48dp touch target)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.50f), CircleShape)
                    .dpadFocusable(
                        onClick = onBackClick,
                        shape = CircleShape,
                        scaleOnFocus = 1.12f
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(Res.string.action_back),
                    tint = CloudStreamColors.OnMediaScrim,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Title & Episode Subtitle
            Column(
                modifier = Modifier.padding(start = 2.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.subtitle1.copy(
                        fontWeight = FontWeight.Bold,
                        color = CloudStreamColors.OnMediaScrim,
                        fontSize = 16.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (subtitleInfo.isNotBlank()) {
                    Text(
                        text = subtitleInfo,
                        style = MaterialTheme.typography.caption.copy(
                            fontWeight = FontWeight.Medium,
                            color = CloudStreamColors.TextSecondary,
                            fontSize = 12.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Right Section: Quick Action Buttons
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. Sources / Quality Selector Button
            val autoLabel = stringResource(Res.string.quality_auto)
            val qualityText = state.selectedQuality?.let {
                if (it.quality > 0) "${it.quality}p" else autoLabel
            } ?: stringResource(Res.string.quality)

            PlayerTopBarActionButton(
                iconPainter = painterResource(Res.drawable.ic_baseline_hd_24),
                label = qualityText,
                contentDescription = stringResource(Res.string.quality),
                onClick = { onEvent(PlayerUiEvent.SetActiveModal(PlayerActiveModal.QUALITY_SOURCES)) }
            )

            // 2. Subtitles Selector Button
            val hasActiveSubtitle = state.selectedSubtitle != null
            val subLabel = state.selectedSubtitle?.languageCode?.uppercase() ?: stringResource(Res.string.subtitles)

            PlayerTopBarActionButton(
                iconPainter = painterResource(Res.drawable.ic_outline_subtitles_24),
                label = subLabel,
                contentDescription = stringResource(Res.string.subtitles),
                isHighlighted = hasActiveSubtitle,
                highlightColor = CloudStreamColors.Secondary,
                onClick = { onEvent(PlayerUiEvent.SetActiveModal(PlayerActiveModal.SUBTITLES)) }
            )

            // 3. Audio Tracks Button (if multiple or available)
            if (state.availableAudioTracks.isNotEmpty()) {
                val audioLabel = state.selectedAudioTrack?.languageCode?.uppercase()
                    ?: stringResource(Res.string.audio_singular)

                PlayerTopBarActionButton(
                    iconPainter = painterResource(Res.drawable.ic_baseline_volume_up_24),
                    label = audioLabel,
                    contentDescription = stringResource(Res.string.audio_tracks_dialog_title),
                    onClick = { onEvent(PlayerUiEvent.SetActiveModal(PlayerActiveModal.AUDIO_TRACKS)) }
                )
            }

            // 4. Playback Speed Button
            val isCustomSpeed = state.playbackSpeed != 1.0f
            val speedLabel = if (state.playbackSpeed == 1.0f) "1x" else "${state.playbackSpeed}x"

            PlayerTopBarActionButton(
                iconPainter = painterResource(Res.drawable.ic_baseline_speed_24),
                label = speedLabel,
                contentDescription = stringResource(Res.string.speed),
                isHighlighted = isCustomSpeed,
                highlightColor = CloudStreamColors.Primary,
                onClick = { onEvent(PlayerUiEvent.SetActiveModal(PlayerActiveModal.SPEED)) }
            )

            // 5. Episodes List Button (if playlist available)
            if (state.playlist.isNotEmpty()) {
                PlayerTopBarActionButton(
                    iconPainter = painterResource(Res.drawable.ic_baseline_playlist_play_24),
                    label = stringResource(Res.string.episodes),
                    contentDescription = stringResource(Res.string.episodes),
                    onClick = { onEvent(PlayerUiEvent.SetActiveModal(PlayerActiveModal.EPISODES)) }
                )
            }

            // 6. Aspect Ratio / Scaling Button
            PlayerTopBarActionButton(
                iconPainter = painterResource(Res.drawable.ic_baseline_aspect_ratio_24),
                label = stringResource(Res.string.aspect_ratio),
                contentDescription = stringResource(Res.string.aspect_ratio),
                onClick = { onEvent(PlayerUiEvent.CycleResizeMode) }
            )

            // 7. Controls Lock Button (min 48x48dp touch target)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.50f), CircleShape)
                    .dpadFocusable(
                        onClick = { onEvent(PlayerUiEvent.ToggleControlsLock(true)) },
                        shape = CircleShape,
                        scaleOnFocus = 1.12f
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(Res.drawable.video_locked),
                    contentDescription = stringResource(Res.string.action_lock_controls),
                    tint = CloudStreamColors.OnMediaScrim,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * Standardized Top Bar action pill button with icon, label, hover and D-Pad focus indicators.
 */
@Composable
private fun PlayerTopBarActionButton(
    iconPainter: Painter,
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false,
    highlightColor: Color = CloudStreamColors.Primary
) {
    val bgColor by animateColorAsState(
        targetValue = if (isHighlighted) highlightColor.copy(alpha = 0.28f) else Color.Black.copy(alpha = 0.50f),
        animationSpec = tween(durationMillis = 150)
    )
    val contentColor by animateColorAsState(
        targetValue = if (isHighlighted) highlightColor else CloudStreamColors.OnMediaScrim,
        animationSpec = tween(durationMillis = 150)
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bgColor,
        modifier = modifier
            .dpadFocusable(
                onClick = onClick,
                shape = RoundedCornerShape(20.dp),
                scaleOnFocus = 1.08f
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(
                painter = iconPainter,
                contentDescription = contentDescription,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.caption.copy(
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    fontSize = 12.sp
                )
            )
        }
    }
}
