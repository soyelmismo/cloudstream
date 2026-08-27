package com.lagradost.cloudstream3.shared.ui.focus

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors

/**
 * Universal D-Pad & TV Remote focus modifier providing:
 * - Keyboard / D-Pad key event handling (Enter, D-Pad Center, Space, NumPad Enter).
 * - Smooth TV scale micro-animation on focus/hover.
 * - Standardized glowing TV focus border.
 * - Full accessibility and interaction source propagation.
 */
fun Modifier.dpadFocusable(
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(8.dp),
    focusedBorderColor: Color? = null,
    focusedBorderWidth: Dp = 2.dp,
    scaleOnFocus: Float = 1.04f,
    focusRequester: FocusRequester? = null
): Modifier = composed {
    val currentInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val isFocused by currentInteractionSource.collectIsFocusedAsState()
    val isHovered by currentInteractionSource.collectIsHoveredAsState()
    val isHighlighted = (isFocused || isHovered) && enabled

    val scale by animateFloatAsState(
        targetValue = if (isHighlighted) scaleOnFocus else 1.0f,
        animationSpec = tween(durationMillis = 150)
    )

    val activeBorderColor = focusedBorderColor ?: CloudStreamColors.Primary

    this
        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
        .scale(scale)
        .then(
            if (isHighlighted) {
                Modifier.border(
                    BorderStroke(focusedBorderWidth, activeBorderColor),
                    shape
                )
            } else {
                Modifier
            }
        )
        .clip(shape)
        .then(
            if (onClick != null && enabled) {
                Modifier.clickable(
                    interactionSource = currentInteractionSource,
                    indication = null,
                    onClick = onClick
                )
            } else {
                Modifier.focusable(
                    enabled = enabled,
                    interactionSource = currentInteractionSource
                )
            }
        )
        .onKeyEvent { keyEvent ->
            if (!enabled) return@onKeyEvent false
            if (keyEvent.type == KeyEventType.KeyDown) {
                when (keyEvent.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                        onClick?.invoke()
                        true
                    }
                    Key.Menu -> {
                        if (onLongClick != null) {
                            onLongClick.invoke()
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            } else {
                false
            }
        }
}

/**
 * Directional D-Pad Key Interceptor Modifier for custom remote navigation flows.
 */
fun Modifier.onDpadKeyEvent(
    onUp: (() -> Boolean)? = null,
    onDown: (() -> Boolean)? = null,
    onLeft: (() -> Boolean)? = null,
    onRight: (() -> Boolean)? = null,
    onEnter: (() -> Boolean)? = null,
    onBack: (() -> Boolean)? = null,
    onMediaPlayPause: (() -> Boolean)? = null
): Modifier = onKeyEvent { keyEvent ->
    if (keyEvent.type == KeyEventType.KeyDown) {
        when (keyEvent.key) {
            Key.DirectionUp -> onUp?.invoke() ?: false
            Key.DirectionDown -> onDown?.invoke() ?: false
            Key.DirectionLeft -> onLeft?.invoke() ?: false
            Key.DirectionRight -> onRight?.invoke() ?: false
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> onEnter?.invoke() ?: false
            Key.Back, Key.Escape -> onBack?.invoke() ?: false
            Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> onMediaPlayPause?.invoke() ?: false
            else -> false
        }
    } else {
        false
    }
}

/**
 * Standard TV focus highlight border and scale modifier.
 */
fun Modifier.tvFocusHighlight(
    isFocused: Boolean,
    isHovered: Boolean = false,
    shape: Shape = RoundedCornerShape(8.dp),
    borderColor: Color? = null,
    borderWidth: Dp = 2.dp,
    scaleMultiplier: Float = 1.04f
): Modifier = composed {
    val isHighlighted = isFocused || isHovered
    val scale by animateFloatAsState(
        targetValue = if (isHighlighted) scaleMultiplier else 1.0f,
        animationSpec = tween(durationMillis = 150)
    )
    val activeBorderColor = borderColor ?: CloudStreamColors.Primary

    this
        .scale(scale)
        .then(
            if (isHighlighted) {
                Modifier.border(BorderStroke(borderWidth, activeBorderColor), shape)
            } else {
                Modifier
            }
        )
}
