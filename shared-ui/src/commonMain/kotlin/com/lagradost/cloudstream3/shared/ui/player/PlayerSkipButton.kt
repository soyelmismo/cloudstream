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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cloudstream.shared_ui.generated.resources.*
import com.lagradost.cloudstream3.shared.ui.components.designsystem.CloudStreamFilterChip
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors
import com.lagradost.cloudstream3.shared.viewmodels.player.PlayerSkipTimestamp
import com.lagradost.cloudstream3.shared.viewmodels.player.PlayerUiEvent
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Floating "Skip Intro" / "Skip Outro" / "Next Episode" animated button adhering to CloudStream Design System.
 * Built with [CloudStreamFilterChip] and automatically appears when playback position matches an active skip marker.
 */
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
            val label = activeSkipTimestamp.label
                ?: if (activeSkipTimestamp.isIntro) {
                    stringResource(Res.string.skip_intro)
                } else if (activeSkipTimestamp.isOutro && hasNextEpisode) {
                    stringResource(Res.string.action_next_episode)
                } else if (activeSkipTimestamp.isOutro) {
                    stringResource(Res.string.skip_outro)
                } else {
                    stringResource(Res.string.skip_intro)
                }

            CloudStreamFilterChip(
                label = label,
                isSelected = true,
                showCheckIconWhenSelected = false,
                leadingPainter = painterResource(Res.drawable.ic_baseline_skip_next_24),
                activeContainerColor = CloudStreamColors.Primary,
                activeContentColor = MaterialTheme.colors.onPrimary,
                shape = RoundedCornerShape(24.dp),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                onClick = {
                    if (activeSkipTimestamp.isIntro) {
                        onEvent(PlayerUiEvent.SkipIntro)
                    } else if (activeSkipTimestamp.isOutro) {
                        if (hasNextEpisode) {
                            onEvent(PlayerUiEvent.NextEpisode)
                        } else {
                            onEvent(PlayerUiEvent.SkipOutro)
                        }
                    } else {
                        onEvent(PlayerUiEvent.SkipToTimestamp(activeSkipTimestamp))
                    }
                }
            )
        }
    }
}
