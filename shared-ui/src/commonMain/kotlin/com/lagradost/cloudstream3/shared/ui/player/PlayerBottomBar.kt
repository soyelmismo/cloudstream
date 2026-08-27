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
import androidx.compose.ui.graphics.painter.Painter
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
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamTheme
import com.lagradost.cloudstream3.shared.viewmodels.player.PlayerActiveModal
import com.lagradost.cloudstream3.shared.viewmodels.player.PlayerUiEvent
import com.lagradost.cloudstream3.shared.viewmodels.player.PlayerUiState
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

private fun Modifier.playerDpadSeekHandler(
    currentPos: Float,
    duration: Float,
    onEvent: (PlayerUiEvent) -> Unit
): Modifier = onKeyEvent { keyEvent ->
    if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
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
}

@Composable
fun PlayerBottomBar(
    state: PlayerUiState,
    onEvent: (PlayerUiEvent) -> Unit,
    onToggleFullscreen: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val duration = state.durationMs.toFloat().coerceAtLeast(1f)
    val remainingMs = (state.durationMs - state.positionMs).coerceAtLeast(0L)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PlayerTimeControls(
            currentPos = state.positionMs.toFloat(),
            duration = duration,
            formattedDuration = state.formattedDuration,
            remainingMs = remainingMs,
            onEvent = onEvent
        )

        PlayerBottomActions(
            state = state,
            onEvent = onEvent,
            onToggleFullscreen = onToggleFullscreen
        )
    }
}

@Composable
private fun PlayerTimeControls(
    currentPos: Float,
    duration: Float,
    formattedDuration: String,
    remainingMs: Long,
    onEvent: (PlayerUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    var isSeeking by remember { mutableStateOf(false) }
    var seekPositionMs by remember { mutableFloatStateOf(0f) }

    val activePos = if (isSeeking) seekPositionMs else currentPos
    val formattedRemaining = "-${PlayerUiState.formatTime(remainingMs)}"

    val sliderInteractionSource = remember { MutableInteractionSource() }
    val isSliderFocused by sliderInteractionSource.collectIsFocusedAsState()

    Row(
        modifier = modifier
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
        Text(
            text = PlayerUiState.formatTime(activePos.toLong()),
            style = MaterialTheme.typography.caption.copy(
                fontWeight = FontWeight.Bold,
                color = CloudStreamColors.OnMediaScrim,
                fontSize = 13.sp
            )
        )

        Spacer(modifier = Modifier.width(12.dp))

        Slider(
            value = activePos.coerceIn(0f, duration),
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
                .playerDpadSeekHandler(
                    currentPos = activePos,
                    duration = duration,
                    onEvent = onEvent
                ),
            colors = SliderDefaults.colors(
                thumbColor = CloudStreamColors.Primary,
                activeTrackColor = CloudStreamColors.Primary,
                inactiveTrackColor = CloudStreamColors.OnMediaScrim.copy(alpha = 0.35f)
            )
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = "$formattedDuration ($formattedRemaining)",
            style = MaterialTheme.typography.caption.copy(
                fontWeight = FontWeight.Medium,
                color = CloudStreamColors.OnMediaScrim.copy(alpha = 0.85f),
                fontSize = 12.sp
            )
        )
    }
}

@Composable
private fun PlayerBottomActions(
    state: PlayerUiState,
    onEvent: (PlayerUiEvent) -> Unit,
    onToggleFullscreen: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlayerNavigationActions(
            hasPrevious = state.hasPreviousEpisode,
            hasNext = state.hasNextEpisode,
            hasMultipleEpisodes = state.playlist.size > 1,
            onEvent = onEvent
        )

        if (onToggleFullscreen != null) {
            PlayerFullscreenButton(onClick = onToggleFullscreen)
        }
    }
}

@Composable
private fun PlayerNavigationActions(
    hasPrevious: Boolean,
    hasNext: Boolean,
    hasMultipleEpisodes: Boolean,
    onEvent: (PlayerUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
    ) {
        if (hasPrevious) {
            PlayerCircleIconButton(
                painter = painterResource(Res.drawable.baseline_skip_previous_24),
                contentDescription = stringResource(Res.string.action_previous_episode),
                onClick = { onEvent(PlayerUiEvent.PreviousEpisode) }
            )
        }

        if (hasNext) {
            PlayerCircleIconButton(
                painter = painterResource(Res.drawable.ic_baseline_skip_next_24),
                contentDescription = stringResource(Res.string.action_next_episode),
                onClick = { onEvent(PlayerUiEvent.NextEpisode) }
            )
        }

        if (hasMultipleEpisodes) {
            PlayerEpisodesPillButton(
                onClick = { onEvent(PlayerUiEvent.SetActiveModal(PlayerActiveModal.EPISODES)) }
            )
        }
    }
}

@Composable
private fun PlayerCircleIconButton(
    painter: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .background(Color.Black.copy(alpha = 0.50f), CircleShape)
            .dpadFocusable(
                onClick = onClick,
                shape = CircleShape,
                scaleOnFocus = 1.12f
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = CloudStreamColors.OnMediaScrim,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun PlayerEpisodesPillButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.Black.copy(alpha = 0.50f),
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

@Composable
private fun PlayerFullscreenButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    PlayerCircleIconButton(
        painter = painterResource(Res.drawable.baseline_fullscreen_24),
        contentDescription = stringResource(Res.string.action_fullscreen),
        onClick = onClick,
        modifier = modifier
    )
}

@Preview
@Composable
private fun PlayerBottomBarPreview() {
    CloudStreamTheme {
        PlayerBottomBar(
            state = PlayerUiState(
                isPlaying = true,
                durationMs = 2400_000L,
                positionMs = 600_000L,
                hasNextEpisode = true,
                hasPreviousEpisode = true
            ),
            onEvent = {}
        )
    }
}
