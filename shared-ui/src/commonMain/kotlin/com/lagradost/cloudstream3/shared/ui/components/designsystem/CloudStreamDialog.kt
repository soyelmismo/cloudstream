package com.lagradost.cloudstream3.shared.ui.components.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import cloudstream.shared_ui.generated.resources.*
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors

/**
 * Base standardized dialog container for the CloudStream Design System.
 * Encapsulates the Dialog window, standard shape (16.dp rounded corners),
 * surface elevation, border stroke, and maximum responsive width.
 */
@Composable
fun CloudStreamDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(),
    shape: Shape = RoundedCornerShape(16.dp),
    backgroundColor: Color = CloudStreamColors.Surface,
    border: BorderStroke? = BorderStroke(1.dp, CloudStreamColors.Divider.copy(alpha = 0.5f)),
    elevation: Dp = 12.dp,
    maxWidth: Dp = 480.dp,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties
    ) {
        Surface(
            shape = shape,
            color = backgroundColor,
            border = border,
            elevation = elevation,
            modifier = modifier
                .fillMaxWidth()
                .widthIn(max = maxWidth)
                .padding(16.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth().padding(contentPadding)) {
                content()
            }
        }
    }
}

/**
 * Standardized Action Dialog for CloudStream Multiplatform (Android / Desktop / TV).
 *
 * Features:
 * - Standardized [RoundedCornerShape(16.dp)] container with [CloudStreamColors.Surface] background.
 * - Semantic header with optional icon badge, title, subtitle, and close button.
 * - Scrollable body area for either simple text or complex custom layouts.
 * - Flexible standardized action buttons (Confirm, Cancel, Neutral) supporting Primary or Danger styles.
 */
@Composable
fun ActionDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    titleRes: StringResource? = null,
    titleContent: (@Composable () -> Unit)? = null,
    subtitle: String? = null,
    subtitleRes: StringResource? = null,
    message: String? = null,
    messageRes: StringResource? = null,
    icon: (@Composable () -> Unit)? = null,
    iconVector: ImageVector? = null,
    iconTint: Color = CloudStreamColors.Primary,
    confirmText: String? = null,
    confirmTextRes: StringResource? = null,
    confirmButtonText: String? = confirmText,
    onConfirm: (() -> Unit)? = null,
    confirmLoading: Boolean = false,
    confirmEnabled: Boolean = true,
    isConfirmDanger: Boolean = false,
    isDestructive: Boolean = isConfirmDanger,
    cancelText: String? = null,
    cancelTextRes: StringResource? = Res.string.cancel,
    dismissButtonText: String? = cancelText,
    onCancel: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = onCancel,
    neutralText: String? = null,
    neutralTextRes: StringResource? = null,
    onNeutral: (() -> Unit)? = null,
    showCloseButton: Boolean = true,
    shape: Shape = RoundedCornerShape(16.dp),
    backgroundColor: Color = CloudStreamColors.Surface,
    maxWidth: Dp = 480.dp,
    properties: DialogProperties = DialogProperties(),
    buttons: (@Composable () -> Unit)? = null,
    content: (@Composable () -> Unit)? = null
) {
    val resolvedTitle = title ?: titleRes?.let { stringResource(it) }
    val resolvedSubtitle = subtitle ?: subtitleRes?.let { stringResource(it) }
    val resolvedMessage = message ?: messageRes?.let { stringResource(it) }
    val resolvedConfirmText = confirmText ?: confirmButtonText ?: confirmTextRes?.let { stringResource(it) }
    val resolvedCancelText = cancelText ?: dismissButtonText ?: cancelTextRes?.let { stringResource(it) }
    val resolvedNeutralText = neutralText ?: neutralTextRes?.let { stringResource(it) }
    val effectiveConfirmDanger = isConfirmDanger || isDestructive
    val effectiveCancelAction = onCancel ?: onDismiss ?: onDismissRequest

    CloudStreamDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        properties = properties,
        shape = shape,
        backgroundColor = backgroundColor,
        maxWidth = maxWidth,
        contentPadding = PaddingValues(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // =============================================================
            // 1. HEADER SECTION
            // =============================================================
            if (titleContent != null || resolvedTitle != null || icon != null || iconVector != null || showCloseButton) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (icon != null) {
                            icon()
                        } else if (iconVector != null) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(iconTint.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = iconVector,
                                    contentDescription = null,
                                    tint = iconTint,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        if (titleContent != null) {
                            titleContent()
                        } else if (resolvedTitle != null) {
                            Column {
                                Text(
                                    text = resolvedTitle,
                                    style = MaterialTheme.typography.h6.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = CloudStreamColors.TextPrimary,
                                        fontSize = 18.sp
                                    ),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (resolvedSubtitle != null) {
                                    Text(
                                        text = resolvedSubtitle,
                                        style = MaterialTheme.typography.caption.copy(
                                            color = CloudStreamColors.TextSecondary,
                                            fontSize = 12.sp
                                        ),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    if (showCloseButton) {
                        IconButton(
                            onClick = onDismissRequest,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(Res.string.close),
                                tint = CloudStreamColors.TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // =============================================================
            // 2. BODY CONTENT SECTION
            // =============================================================
            if (content != null || resolvedMessage != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (resolvedMessage != null) {
                        Text(
                            text = resolvedMessage,
                            style = MaterialTheme.typography.body2.copy(
                                color = CloudStreamColors.TextSecondary,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                        )
                    }
                    content?.invoke()
                }
            }

            // =============================================================
            // 3. ACTION BUTTONS SECTION
            // =============================================================
            if (buttons != null) {
                buttons()
            } else if (onConfirm != null || onCancel != null || onDismiss != null || onNeutral != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Neutral Action (if provided)
                    if (onNeutral != null && resolvedNeutralText != null) {
                        GhostButton(
                            text = resolvedNeutralText,
                            onClick = onNeutral,
                            contentColor = CloudStreamColors.TextMuted
                        )
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    // Right: Cancel & Confirm Buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onCancel != null || onDismiss != null || (resolvedCancelText != null && onConfirm != null)) {
                            GhostButton(
                                text = resolvedCancelText ?: stringResource(Res.string.cancel),
                                onClick = effectiveCancelAction,
                                contentColor = CloudStreamColors.TextSecondary
                            )
                        }

                        if (onConfirm != null && resolvedConfirmText != null) {
                            if (effectiveConfirmDanger) {
                                DangerButton(
                                    text = resolvedConfirmText,
                                    onClick = onConfirm,
                                    loading = confirmLoading,
                                    enabled = confirmEnabled
                                )
                            } else {
                                PrimaryButton(
                                    text = resolvedConfirmText,
                                    onClick = onConfirm,
                                    loading = confirmLoading,
                                    enabled = confirmEnabled
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// CONFIRM DELETE DIALOG
// =============================================================================

/**
 * Pre-configured danger confirmation dialog with localized cancel/confirm and warning indicator.
 *
 * @param title Custom title text, or defaults to localized "Delete".
 * @param titleRes String resource for title.
 * @param message Confirmation explanatory text.
 * @param messageRes String resource for message.
 * @param itemName Optional name of the item being deleted to highlight.
 * @param confirmText Custom confirm button text, or defaults to localized "Delete".
 * @param confirmTextRes String resource for confirm button.
 * @param cancelText Custom cancel button text, or defaults to localized "Cancel".
 * @param cancelTextRes String resource for cancel button.
 * @param onConfirm Action to execute when delete is confirmed.
 * @param onDismiss Action to execute when dismissed/cancelled.
 * @param confirmLoading Indicates whether the delete action is currently processing.
 */
@Composable
fun ConfirmDeleteDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    titleRes: StringResource? = Res.string.delete,
    message: String? = null,
    messageRes: StringResource? = null,
    itemName: String? = null,
    confirmText: String? = null,
    confirmTextRes: StringResource = Res.string.delete,
    cancelText: String? = null,
    cancelTextRes: StringResource = Res.string.cancel,
    confirmLoading: Boolean = false
) {
    val resolvedMessage = when {
        message != null -> message
        messageRes != null -> stringResource(messageRes)
        itemName != null -> "${stringResource(Res.string.delete_file)}: \"$itemName\""
        else -> stringResource(Res.string.delete_download_confirm_desc)
    }

    ActionDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = title,
        titleRes = if (title == null) titleRes else null,
        message = resolvedMessage,
        iconVector = Icons.Default.Warning,
        iconTint = CloudStreamColors.Error,
        confirmText = confirmText,
        confirmTextRes = if (confirmText == null) confirmTextRes else null,
        onConfirm = onConfirm,
        isConfirmDanger = true,
        confirmLoading = confirmLoading,
        cancelText = cancelText,
        cancelTextRes = if (cancelText == null) cancelTextRes else null,
        onCancel = onDismiss,
        showCloseButton = true
    )
}
