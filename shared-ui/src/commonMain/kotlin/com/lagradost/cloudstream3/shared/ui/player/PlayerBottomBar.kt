package com.lagradost.cloudstream3.shared.ui.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
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
 * Bottom control bar for the video player overlay.
 *
 * Provides:
 * - Smooth, reactive time slider with elapsed timestamp and total/remaining duration.
 * - Bottom action button row:
 *   - Previous episode button (if [PlayerUiState.hasPreviousEpisode])
 *   - Next episode button (if [PlayerUiState.hasNextEpisode])
 *   - Playlist / Episode list modal opener (if playlist size > 1)
 *   - Fullscreen toggle button
 * - Safe drawing window insets adherence.
 */
@Composable
fun PlayerBottomBar(
    state: PlayerUiState,
    onEvent: (PlayerUiEvent) -> Unit,
    onToggleFullscreen: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isSeeking by remember { mutableStateOf(false) }
    var seekPositionMs by remember { mutableFloatStateOf(0f) }

    val currentPos = if (isSeeking) seekPositionMs else state.positionMs.toFloat()
    val duration = state.durationMs.toFloat().coerceAtLeast(1f)

    val sliderInteractionSource = remember { MutableInteractionSource() }
    val isSliderFocused by sliderInteractionSource.collectIsFocusedAsState()

    val remainingMs = (state.durationMs - currentPos.toLong()).coerceAtLeast(0L)
    val formattedRemaining = "-${PlayerUiState.formatTime(remainingMs)}"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Time Labels & Interactive SeekBar with TV D-Pad Focus Glow
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isSliderFocused) {
                        Modifier
                            .background(CloudStreamColors.Primary.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                            .border(BorderStroke(1.5.dp, CloudStreamColors.Primary), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    } else {
                        Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    }
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Elapsed Time (e.g. "00:15")
            Text(
                text = PlayerUiState.formatTime(currentPos.toLong()),
                style = MaterialTheme.typography.caption.copy(
                    fontWeight = FontWeight.Bold,
                    color = CloudStreamColors.OnMediaScrim,
                    fontSize = 13.sp
                )
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Interactive Progress Slider with TV D-Pad Seeking
            Slider(
                value = currentPos.coerceIn(0f, duration),
                onValueChange = {
                    isSeeking = true
                    seekPositionMs = it
                },
                onValueChangeFinished = {
                    onEvent(PlayerUiEvent.SeekTo(seekPositionMs.toLong()))
                    isSeeking = false
                },
                valueRange = 0f..duration,
                interactionSource = sliderInteractionSource,
                modifier = Modifier
                    .weight(1f)
                    .focusable(interactionSource = sliderInteractionSource)
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            when (keyEvent.key) {
                                Key.DirectionLeft -> {
                                    val newPos = (currentPos - 10_000f).coerceAtLeast(0f)
                                    onEvent(PlayerUiEvent.SeekTo(newPos.toLong()))
                                    true
                                }
                                Key.DirectionRight -> {
                                    val newPos = (currentPos + 10_000f).coerceAtMost(duration)
                                    onEvent(PlayerUiEvent.SeekTo(newPos.toLong()))
                                    true
                                }
                                Key.DirectionCenter, Key.Enter, Key.Spacebar -> {
                                    onEvent(PlayerUiEvent.TogglePlayPause)
                                    true
                                }
                                else -> false
                            }
                        } else {
                            false
                        }
                    },
                colors = SliderDefaults.colors(
                    thumbColor = CloudStreamColors.Primary,
                    activeTrackColor = CloudStreamColors.Primary,
                    inactiveTrackColor = CloudStreamColors.OnMediaScrim.copy(alpha = 0.35f)
                )
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Duration & Remaining Time
            Text(
                text = "${state.formattedDuration} ($formattedRemaining)",
                style = MaterialTheme.typography.caption.copy(
                    fontWeight = FontWeight.Medium,
                    color = CloudStreamColors.OnMediaScrim.copy(alpha = 0.85f),
                    fontSize = 12.sp
                )
            )
        }

        // Bottom Action Button Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Group: Previous / Next Episode Navigation & Episode List
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Previous Episode Button
                if (state.hasPreviousEpisode) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.Black.copy(alpha = 0.50f), CircleShape)
                            .dpadFocusable(
                                onClick = { onEvent(PlayerUiEvent.PreviousEpisode) },
                                shape = CircleShape,
                                scaleOnFocus = 1.12f
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.baseline_skip_previous_24),
                            contentDescription = stringResource(Res.string.action_previous_episode),
                            tint = CloudStreamColors.OnMediaScrim,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Next Episode Button
                if (state.hasNextEpisode) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.Black.copy(alpha = 0.50f), CircleShape)
                            .dpadFocusable(
                                onClick = { onEvent(PlayerUiEvent.NextEpisode) },
                                shape = CircleShape,
                                scaleOnFocus = 1.12f
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_baseline_skip_next_24),
                            contentDescription = stringResource(Res.string.action_next_episode),
                            tint = CloudStreamColors.OnMediaScrim,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Episode List Modal Opener Button (if playlist has multiple episodes)
                if (state.playlist.size > 1) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.50f),
                        modifier = Modifier
                            .dpadFocusable(
                                onClick = { onEvent(PlayerUiEvent.SetActiveModal(PlayerActiveModal.EPISODES)) },
                                shape = RoundedCornerShape(20.dp),
                                scaleOnFocus = 1.08f
                            )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_baseline_playlist_play_24),
                                contentDescription = stringResource(Res.string.episodes),
                                tint = CloudStreamColors.OnMediaScrim,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = stringResource(Res.string.episodes),
                                style = MaterialTheme.typography.caption.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = CloudStreamColors.OnMediaScrim,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                }
            }

            // Right Group: Fullscreen Toggle
            if (onToggleFullscreen != null) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.Black.copy(alpha = 0.50f), CircleShape)
                        .dpadFocusable(
                            onClick = onToggleFullscreen,
                            shape = CircleShape,
                            scaleOnFocus = 1.12f
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.baseline_fullscreen_24),
                        contentDescription = stringResource(Res.string.action_fullscreen),
                        tint = CloudStreamColors.OnMediaScrim,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
