package com.lagradost.cloudstream3.shared.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cloudstream.shared_ui.generated.resources.*
import com.lagradost.cloudstream3.shared.ui.focus.dpadFocusable
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors
import com.lagradost.cloudstream3.shared.viewmodels.player.PlayerUiEvent
import com.lagradost.cloudstream3.shared.viewmodels.player.PlayerUiState
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Center hero interactive controls for the video player:
 * - Rewind 10s button
 * - Central hero 64dp Play / Pause button with tactile scale feedback
 * - Forward 10s button
 * - Buffering progress indicator
 * Equipped with full Android TV / D-Pad remote focus support.
 */
@Composable
fun PlayerCenterControls(
    state: PlayerUiState,
    onEvent: (PlayerUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Buffering Indicator
        if (state.isBuffering) {
            CircularProgressIndicator(
                color = CloudStreamColors.Primary,
                strokeWidth = 4.dp,
                modifier = Modifier.size(64.dp)
            )
        } else {
            // Interactive Hero Play / Seek Controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(36.dp)
            ) {
                // Seek Rewind (-10s) Button
                val rewindInteraction = remember { MutableInteractionSource() }
                val isRewindPressed by rewindInteraction.collectIsPressedAsState()
                val rewindScale by animateFloatAsState(
                    targetValue = if (isRewindPressed) 0.88f else 1.0f,
                    animationSpec = tween(durationMillis = 120)
                )

                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .scale(rewindScale)
                        .background(Color.Black.copy(alpha = 0.50f), CircleShape)
                        .dpadFocusable(
                            interactionSource = rewindInteraction,
                            onClick = { onEvent(PlayerUiEvent.SeekBy(-10_000L)) },
                            shape = CircleShape,
                            scaleOnFocus = 1.12f
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.netflix_skip_back),
                        contentDescription = stringResource(Res.string.action_rewind_10),
                        tint = CloudStreamColors.OnMediaScrim,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Primary Large Hero 64dp Play / Pause Button
                val playInteraction = remember { MutableInteractionSource() }
                val isPlayPressed by playInteraction.collectIsPressedAsState()
                val playScale by animateFloatAsState(
                    targetValue = if (isPlayPressed) 0.90f else 1.0f,
                    animationSpec = tween(durationMillis = 120)
                )

                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .scale(playScale)
                        .background(CloudStreamColors.Primary.copy(alpha = 0.90f), CircleShape)
                        .dpadFocusable(
                            interactionSource = playInteraction,
                            onClick = { onEvent(PlayerUiEvent.TogglePlayPause) },
                            shape = CircleShape,
                            scaleOnFocus = 1.12f
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(if (state.isPlaying) Res.drawable.netflix_pause else Res.drawable.netflix_play),
                        contentDescription = if (state.isPlaying) stringResource(Res.string.pause) else stringResource(Res.string.action_play),
                        tint = MaterialTheme.colors.onPrimary,
                        modifier = Modifier.size(34.dp)
                    )
                }

                // Seek Forward (+10s) Button
                val forwardInteraction = remember { MutableInteractionSource() }
                val isForwardPressed by forwardInteraction.collectIsPressedAsState()
                val forwardScale by animateFloatAsState(
                    targetValue = if (isForwardPressed) 0.88f else 1.0f,
                    animationSpec = tween(durationMillis = 120)
                )

                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .scale(forwardScale)
                        .background(Color.Black.copy(alpha = 0.50f), CircleShape)
                        .dpadFocusable(
                            interactionSource = forwardInteraction,
                            onClick = { onEvent(PlayerUiEvent.SeekBy(10_000L)) },
                            shape = CircleShape,
                            scaleOnFocus = 1.12f
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.netflix_skip_forward),
                        contentDescription = stringResource(Res.string.action_forward_10),
                        tint = CloudStreamColors.OnMediaScrim,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}
