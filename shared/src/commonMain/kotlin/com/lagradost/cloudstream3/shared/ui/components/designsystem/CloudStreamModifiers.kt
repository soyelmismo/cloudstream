package com.lagradost.cloudstream3.shared.ui.components.designsystem

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Shared Design System interaction modifier: applies the standardized press/hover/focus scale,
 * click handling without ripple indication, focusability for TV & Desktop navigation, and
 * remote/keyboard activation (D-Pad center / Enter / Numpad Enter / Space).
 *
 * Every clickable design system surface (buttons, cards, chips, dropdown containers) routes
 * through this single modifier so interaction behavior stays consistent and DRY.
 */
fun Modifier.dsClickable(
    onClick: () -> Unit,
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    scale: Float = 1f
): Modifier = this
    .scale(scale)
    .clickable(
        interactionSource = interactionSource,
        indication = null,
        enabled = enabled,
        onClick = onClick
    )
    .focusable(
        enabled = enabled,
        interactionSource = interactionSource
    )
    .onKeyEvent { keyEvent ->
        if (!enabled) return@onKeyEvent false
        if (keyEvent.type == KeyEventType.KeyDown) {
            when (keyEvent.key) {
                Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                    onClick()
                    true
                }
                else -> false
            }
        } else {
            false
        }
    }

/**
 * Shared Design System combined interaction modifier: supports standard click, long click (touch),
 * secondary/right click (mouse pointer input), focusability, scale, and TV/keyboard navigation.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.dsCombinedClickable(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onSecondaryClick: (() -> Unit)? = onLongClick,
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    scale: Float = 1f
): Modifier = this
    .scale(scale)
    .pointerInput(enabled, onSecondaryClick) {
        if (!enabled || onSecondaryClick == null) return@pointerInput
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                    onSecondaryClick()
                }
            }
        }
    }
    .combinedClickable(
        interactionSource = interactionSource,
        indication = null,
        enabled = enabled,
        onClick = onClick,
        onLongClick = onLongClick
    )
    .focusable(
        enabled = enabled,
        interactionSource = interactionSource
    )
    .onKeyEvent { keyEvent ->
        if (!enabled) return@onKeyEvent false
        if (keyEvent.type == KeyEventType.KeyDown) {
            when (keyEvent.key) {
                Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                    onClick()
                    true
                }
                Key.Menu -> {
                    val secondaryAction = onSecondaryClick ?: onLongClick
                    if (secondaryAction != null) {
                        secondaryAction()
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
