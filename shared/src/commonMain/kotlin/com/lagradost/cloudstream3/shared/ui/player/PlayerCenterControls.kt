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
import com.lagradost.cloudstream4.generated.resources.*
import com.lagradost.cloudstream3.shared.ui.focus.dpadFocusable
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamTheme
import com.lagradost.cloudstream3.shared.viewmodels.player.PlayerUiEvent
import com.lagradost.cloudstream3.shared.viewmodels.player.PlayerUiState
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

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
        if (state.isBuffering) {
            PlayerHeroBufferingIndicator()
        } else {
            PlayerCenterActionControls(
                isPlaying = state.isPlaying,
                onEvent = onEvent
            )
        }
    }
}

@Composable
private fun PlayerHeroBufferingIndicator(
    modifier: Modifier = Modifier
) {
    CircularProgressIndicator(
        color = CloudStreamColors.Primary,
        strokeWidth = 4.dp,
        modifier = modifier.size(64.dp)
    )
}

@Composable
private fun PlayerCenterActionControls(
    isPlaying: Boolean,
    onEvent: (PlayerUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(36.dp)
    ) {
        PlayerSeekButton(
            iconRes = Res.drawable.netflix_skip_back,
            contentDescriptionRes = Res.string.action_rewind_10,
            onClick = { onEvent(PlayerUiEvent.SeekBy(-10_000L)) }
        )

        PlayerHeroPlayPauseButton(
            isPlaying = isPlaying,
            onClick = { onEvent(PlayerUiEvent.TogglePlayPause) }
        )

        PlayerSeekButton(
            iconRes = Res.drawable.netflix_skip_forward,
            contentDescriptionRes = Res.string.action_forward_10,
            onClick = { onEvent(PlayerUiEvent.SeekBy(10_000L)) }
        )
    }
}

@Composable
private fun PlayerSeekButton(
    iconRes: DrawableResource,
    contentDescriptionRes: StringResource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1.0f,
        animationSpec = tween(durationMillis = 120)
    )

    Box(
        modifier = modifier
            .size(52.dp)
            .scale(scale)
            .background(Color.Black.copy(alpha = 0.50f), CircleShape)
            .dpadFocusable(
                interactionSource = interactionSource,
                onClick = onClick,
                shape = CircleShape,
                scaleOnFocus = 1.12f
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = stringResource(contentDescriptionRes),
            tint = CloudStreamColors.OnMediaScrim,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
private fun PlayerHeroPlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1.0f,
        animationSpec = tween(durationMillis = 120)
    )

    val iconRes = if (isPlaying) Res.drawable.netflix_pause else Res.drawable.netflix_play
    val contentDescriptionRes = if (isPlaying) Res.string.pause else Res.string.action_play

    Box(
        modifier = modifier
            .size(64.dp)
            .scale(scale)
            .background(CloudStreamColors.Primary.copy(alpha = 0.90f), CircleShape)
            .dpadFocusable(
                interactionSource = interactionSource,
                onClick = onClick,
                shape = CircleShape,
                scaleOnFocus = 1.12f
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = stringResource(contentDescriptionRes),
            tint = MaterialTheme.colors.onPrimary,
            modifier = Modifier.size(34.dp)
        )
    }
}

@Preview
@Composable
private fun PlayerCenterControlsPreview() {
    CloudStreamTheme {
        PlayerCenterControls(
            state = PlayerUiState(isPlaying = true),
            onEvent = {}
        )
    }
}
