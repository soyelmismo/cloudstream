package com.lagradost.cloudstream3.shared.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cloudstream.shared_ui.generated.resources.*
import com.lagradost.cloudstream3.shared.ui.components.designsystem.ActionDialog
import com.lagradost.cloudstream3.shared.ui.components.designsystem.BodyMutedText
import com.lagradost.cloudstream3.shared.ui.components.designsystem.PrimaryButton
import com.lagradost.cloudstream3.shared.ui.components.designsystem.SecondaryButton
import com.lagradost.cloudstream3.shared.ui.focus.dpadFocusable
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamTheme
import com.lagradost.cloudstream3.shared.viewmodels.player.LockPinDialogMode
import com.lagradost.cloudstream3.shared.viewmodels.player.PlayerUiEvent
import com.lagradost.cloudstream3.shared.viewmodels.player.PlayerUiState
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

private val KEYPAD_LAYOUT = listOf(
    listOf("1", "2", "3"),
    listOf("4", "5", "6"),
    listOf("7", "8", "9"),
    listOf("C", "0", "DEL")
)

@Composable
fun PlayerLockOverlay(
    state: PlayerUiState,
    onEvent: (PlayerUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    var showUnlockPill by remember { mutableStateOf(false) }

    LaunchedEffect(showUnlockPill) {
        if (showUnlockPill) {
            delay(3000)
            showUnlockPill = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { showUnlockPill = !showUnlockPill }
            ),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = showUnlockPill,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.85f)
        ) {
            PlayerLockUnlockBadge(
                onClick = {
                    if (!state.lockPin.isNullOrBlank()) {
                        onEvent(PlayerUiEvent.ShowLockPinDialog(true, LockPinDialogMode.Unlock))
                    } else {
                        onEvent(PlayerUiEvent.ToggleControlsLock(false))
                        onEvent(PlayerUiEvent.VisibilityChanged(true))
                    }
                    showUnlockPill = false
                }
            )
        }
    }
}

@Composable
private fun PlayerLockUnlockBadge(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = CloudStreamColors.SurfaceVariant.copy(alpha = 0.92f),
        border = BorderStroke(1.5.dp, CloudStreamColors.Primary.copy(alpha = 0.8f)),
        elevation = 16.dp,
        modifier = modifier
            .dpadFocusable(
                onClick = onClick,
                shape = RoundedCornerShape(28.dp)
            )
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(CloudStreamColors.Primary.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(Res.drawable.video_unlocked),
                    contentDescription = stringResource(Res.string.action_unlock),
                    tint = CloudStreamColors.Primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column {
                Text(
                    text = stringResource(Res.string.action_unlock),
                    style = MaterialTheme.typography.subtitle2.copy(
                        fontWeight = FontWeight.Bold,
                        color = CloudStreamColors.TextPrimary,
                        fontSize = 14.sp
                    )
                )
                Text(
                    text = stringResource(Res.string.lock_pin_quick_unlock),
                    style = MaterialTheme.typography.caption.copy(
                        color = CloudStreamColors.TextSecondary,
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
}

@Composable
fun PlayerLockPinDialog(
    mode: LockPinDialogMode,
    currentPin: String?,
    onPinConfirmed: (String) -> Unit,
    onClearPin: () -> Unit,
    onDismiss: () -> Unit
) {
    var enteredPin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    val isUnlockMode = mode == LockPinDialogMode.Unlock
    val titleRes = if (isUnlockMode) Res.string.lock_pin_unlock_title else Res.string.lock_pin_dialog_title
    val subtitleRes = if (isUnlockMode) Res.string.lock_pin_enter_pin else Res.string.lock_pin_set_pin_hint

    fun handleDigitPress(digit: String) {
        if (enteredPin.length >= 4) return
        val nextPin = enteredPin + digit
        enteredPin = nextPin
        isError = false
        if (nextPin.length == 4 && isUnlockMode) {
            if (currentPin.isNullOrBlank() || currentPin == nextPin) {
                onPinConfirmed(nextPin)
                onDismiss()
            } else {
                isError = true
            }
        }
    }

    ActionDialog(
        onDismissRequest = onDismiss,
        titleRes = titleRes,
        icon = {
            Icon(
                imageVector = if (isUnlockMode) Icons.Default.LockOpen else Icons.Default.Lock,
                contentDescription = stringResource(titleRes),
                tint = CloudStreamColors.Primary,
                modifier = Modifier.size(24.dp)
            )
        },
        showCloseButton = true,
        content = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                BodyMutedText(
                    text = stringResource(subtitleRes),
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp
                )

                PinIndicatorDots(
                    pinLength = enteredPin.length,
                    isError = isError
                )

                if (isError) {
                    Text(
                        text = stringResource(Res.string.lock_pin_invalid),
                        style = MaterialTheme.typography.caption.copy(
                            fontWeight = FontWeight.Bold,
                            color = CloudStreamColors.Error,
                            fontSize = 12.sp
                        )
                    )
                }

                NumericKeypad(
                    onDigit = ::handleDigitPress,
                    onDelete = {
                        if (enteredPin.isNotEmpty()) {
                            enteredPin = enteredPin.dropLast(1)
                            isError = false
                        }
                    },
                    onClear = {
                        enteredPin = ""
                        isError = false
                    }
                )
            }
        },
        buttons = {
            LockPinDialogActions(
                isUnlockMode = isUnlockMode,
                currentPin = currentPin,
                enteredPin = enteredPin,
                onClearPin = onClearPin,
                onPinConfirmed = onPinConfirmed,
                onDismiss = onDismiss
            )
        }
    )
}

@Composable
private fun PinIndicatorDots(
    pinLength: Int,
    isError: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(vertical = 10.dp)
    ) {
        for (i in 0 until 4) {
            val isFilled = i < pinLength
            val dotColor = when {
                isError -> CloudStreamColors.Error
                isFilled -> CloudStreamColors.Primary
                else -> CloudStreamColors.Divider
            }

            Box(
                modifier = Modifier
                    .size(18.dp)
                    .background(dotColor, CircleShape)
            )
        }
    }
}

@Composable
private fun NumericKeypad(
    onDigit: (String) -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        KEYPAD_LAYOUT.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                row.forEach { key ->
                    KeypadButton(
                        key = key,
                        onClick = {
                            when (key) {
                                "DEL" -> onDelete()
                                "C" -> onClear()
                                else -> onDigit(key)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun KeypadButton(
    key: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CloudStreamColors.SurfaceVariant,
        border = BorderStroke(1.dp, CloudStreamColors.Divider.copy(alpha = 0.5f)),
        modifier = modifier
            .height(48.dp)
            .dpadFocusable(
                onClick = onClick,
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            when (key) {
                "DEL" -> {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = stringResource(Res.string.delete),
                        tint = CloudStreamColors.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                "C" -> {
                    Text(
                        text = stringResource(Res.string.clear),
                        style = MaterialTheme.typography.button.copy(
                            fontWeight = FontWeight.Bold,
                            color = CloudStreamColors.TextMuted,
                            fontSize = 12.sp
                        )
                    )
                }
                else -> {
                    Text(
                        text = key,
                        style = MaterialTheme.typography.h6.copy(
                            fontWeight = FontWeight.Bold,
                            color = CloudStreamColors.TextPrimary,
                            fontSize = 18.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun LockPinDialogActions(
    isUnlockMode: Boolean,
    currentPin: String?,
    enteredPin: String,
    onClearPin: () -> Unit,
    onPinConfirmed: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        if (!isUnlockMode && !currentPin.isNullOrBlank()) {
            SecondaryButton(
                text = stringResource(Res.string.lock_pin_clear),
                onClick = {
                    onClearPin()
                    onDismiss()
                }
            )
        } else {
            Spacer(modifier = Modifier.width(1.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(
                text = stringResource(Res.string.cancel),
                onClick = onDismiss
            )

            if (!isUnlockMode) {
                PrimaryButton(
                    text = stringResource(Res.string.lock_pin_lock_screen),
                    enabled = enteredPin.length == 4 || enteredPin.isEmpty(),
                    onClick = {
                        onPinConfirmed(enteredPin)
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Preview
@Composable
private fun PlayerLockOverlayPreview() {
    CloudStreamTheme {
        PlayerLockOverlay(
            state = PlayerUiState(isControlsLocked = true),
            onEvent = {}
        )
    }
}

@Preview
@Composable
private fun PlayerLockPinDialogPreview() {
    CloudStreamTheme {
        PlayerLockPinDialog(
            mode = LockPinDialogMode.Lock,
            currentPin = null,
            onPinConfirmed = {},
            onClearPin = {},
            onDismiss = {}
        )
    }
}
