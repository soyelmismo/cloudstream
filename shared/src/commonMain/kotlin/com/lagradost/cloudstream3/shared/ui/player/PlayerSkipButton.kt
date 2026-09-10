package com.lagradost.cloudstream3.shared.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream4.generated.resources.*
import com.lagradost.cloudstream3.shared.ui.components.designsystem.CloudStreamFilterChip
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamTheme
import com.lagradost.cloudstream3.shared.viewmodels.player.PlayerSkipTimestamp
import com.lagradost.cloudstream3.shared.viewmodels.player.PlayerSkipType
import com.lagradost.cloudstream3.shared.viewmodels.player.PlayerUiEvent
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

private data class SkipActionConfig(
    val customLabel: String?,
    val defaultLabelRes: StringResource,
    val event: PlayerUiEvent
)

private fun resolveSkipAction(
    timestamp: PlayerSkipTimestamp,
    hasNextEpisode: Boolean
): SkipActionConfig {
    return when {
        timestamp.isIntro -> SkipActionConfig(
            customLabel = timestamp.label,
            defaultLabelRes = Res.string.skip_intro,
            event = PlayerUiEvent.SkipIntro
        )
        timestamp.isOutro && hasNextEpisode -> SkipActionConfig(
            customLabel = timestamp.label,
            defaultLabelRes = Res.string.action_next_episode,
            event = PlayerUiEvent.NextEpisode
        )
        timestamp.isOutro -> SkipActionConfig(
            customLabel = timestamp.label,
            defaultLabelRes = Res.string.skip_outro,
            event = PlayerUiEvent.SkipOutro
        )
        else -> SkipActionConfig(
            customLabel = timestamp.label,
            defaultLabelRes = Res.string.skip_intro,
            event = PlayerUiEvent.SkipToTimestamp(timestamp)
        )
    }
}

@Composable
fun PlayerSkipButton(
    activeSkipTimestamp: PlayerSkipTimestamp?,
    onEvent: (PlayerUiEvent) -> Unit,
    modifier: Modifier = Modifier,
    hasNextEpisode: Boolean = false
) {
    AnimatedVisibility(
        visible = activeSkipTimestamp != null,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
        modifier = modifier
    ) {
        if (activeSkipTimestamp != null) {
            PlayerSkipButtonChip(
                timestamp = activeSkipTimestamp,
                hasNextEpisode = hasNextEpisode,
                onEvent = onEvent
            )
        }
    }
}

@Composable
private fun PlayerSkipButtonChip(
    timestamp: PlayerSkipTimestamp,
    hasNextEpisode: Boolean,
    onEvent: (PlayerUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val action = remember(timestamp, hasNextEpisode) {
        resolveSkipAction(timestamp, hasNextEpisode)
    }
    val label = action.customLabel ?: stringResource(action.defaultLabelRes)

    CloudStreamFilterChip(
        label = label,
        isSelected = true,
        showCheckIconWhenSelected = false,
        leadingPainter = painterResource(Res.drawable.ic_baseline_skip_next_24),
        activeContainerColor = CloudStreamColors.Primary,
        activeContentColor = MaterialTheme.colors.onPrimary,
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
        onClick = { onEvent(action.event) },
        modifier = modifier
    )
}

@Preview
@Composable
private fun PlayerSkipButtonPreview() {
    CloudStreamTheme {
        PlayerSkipButton(
            activeSkipTimestamp = PlayerSkipTimestamp(
                type = PlayerSkipType.Intro,
                startMs = 0L,
                endMs = 85_000L
            ),
            onEvent = {}
        )
    }
}
