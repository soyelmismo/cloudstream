@file:Suppress("DEPRECATION")

package com.lagradost.cloudstream3.shared.ui.components.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ProvideTextStyle
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors

/**
 * Shared Design System base button providing standardized shape, scale animations,
 * focus/glow feedback for TV and Desktop, and loading indicator handling.
 */
@Suppress("DEPRECATION")
@Composable
private fun DesignSystemBaseButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    shape: Shape = RoundedCornerShape(10.dp),
    backgroundColor: Color,
    contentColor: Color,
    disabledBackgroundColor: Color = backgroundColor.copy(alpha = 0.38f),
    disabledContentColor: Color = contentColor.copy(alpha = 0.38f),
    border: BorderStroke? = null,
    focusedBorder: BorderStroke? = null,
    elevation: Dp = 0.dp,
    focusedElevation: Dp = 4.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable RowScope.() -> Unit
) {
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val isHighlighted = (isHovered || isFocused) && enabled && !loading

    val scale by animateFloatAsState(
        targetValue = when {
            !enabled || loading -> 1.0f
            isPressed -> 0.96f
            isHighlighted -> 1.04f
            else -> 1.0f
        },
        animationSpec = tween(durationMillis = 150)
    )

    val currentBgColor = if (enabled) backgroundColor else disabledBackgroundColor
    val currentContentColor = if (enabled) contentColor else disabledContentColor
    val currentBorder = when {
        !enabled -> null
        isFocused && focusedBorder != null -> focusedBorder
        isHovered && focusedBorder != null -> focusedBorder
        else -> border
    }
    val currentElevation = when {
        !enabled || loading -> 0.dp
        isHighlighted -> focusedElevation
        else -> elevation
    }

    val clickModifier = if (onLongClick != null) {
        modifier.dsCombinedClickable(
            onClick = onClick,
            onLongClick = onLongClick,
            interactionSource = interactionSource,
            enabled = enabled && !loading,
            scale = scale
        )
    } else {
        modifier.dsClickable(
            onClick = onClick,
            interactionSource = interactionSource,
            enabled = enabled && !loading,
            scale = scale
        )
    }

    Surface(
        shape = shape,
        color = currentBgColor,
        contentColor = currentContentColor,
        border = currentBorder,
        elevation = currentElevation,
        modifier = clickModifier
    ) {
        CompositionLocalProvider(
            LocalContentColor provides currentContentColor,
            LocalContentAlpha provides currentContentColor.alpha
        ) {
            ProvideTextStyle(
                value = MaterialTheme.typography.button.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            ) {
                Row(
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .padding(contentPadding),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            color = currentContentColor,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        content()
                    }
                }
            }
        }
    }
}

/**
 * Shared internal helper to layout button text and leading/trailing icons consistently.
 */
@Composable
internal fun RowScope.ButtonContent(
    text: String,
    icon: ImageVector? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    iconSpacing: Dp = 8.dp,
    iconSize: Dp = 18.dp
) {
    if (leadingIcon != null) {
        leadingIcon()
        Spacer(modifier = Modifier.width(iconSpacing))
    } else if (icon != null) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(iconSize)
        )
        Spacer(modifier = Modifier.width(iconSpacing))
    }
    Text(
        text = text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center
    )
    if (trailingIcon != null) {
        Spacer(modifier = Modifier.width(iconSpacing))
        trailingIcon()
    }
}

// =============================================================================
// 1. PRIMARY BUTTON
// =============================================================================

/**
 * Standardized Primary action button using [CloudStreamColors.Primary],
 * 10dp rounded corners, with scale and glow feedback on TV and desktop.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(10.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
) {
    PrimaryButton(
        onClick = onClick,
        modifier = modifier,
        onLongClick = onLongClick,
        enabled = enabled,
        loading = loading,
        shape = shape,
        contentPadding = contentPadding
    ) {
        ButtonContent(
            text = text,
            icon = icon,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon
        )
    }
}

@Composable
fun PrimaryButton(
    textRes: StringResource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(10.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
) = PrimaryButton(
    text = stringResource(textRes),
    onClick = onClick,
    modifier = modifier,
    onLongClick = onLongClick,
    enabled = enabled,
    loading = loading,
    icon = icon,
    leadingIcon = leadingIcon,
    trailingIcon = trailingIcon,
    shape = shape,
    contentPadding = contentPadding
)

@Composable
fun PrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    shape: Shape = RoundedCornerShape(10.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit
) {
    DesignSystemBaseButton(
        onClick = onClick,
        modifier = modifier,
        onLongClick = onLongClick,
        enabled = enabled,
        loading = loading,
        shape = shape,
        backgroundColor = CloudStreamColors.Primary,
        contentColor = MaterialTheme.colors.onPrimary,
        disabledBackgroundColor = CloudStreamColors.Primary.copy(alpha = 0.35f),
        disabledContentColor = MaterialTheme.colors.onPrimary.copy(alpha = 0.5f),
        focusedBorder = BorderStroke(1.5.dp, MaterialTheme.colors.onPrimary.copy(alpha = 0.85f)),
        elevation = 2.dp,
        focusedElevation = 6.dp,
        contentPadding = contentPadding,
        content = content
    )
}

// =============================================================================
// 2. SECONDARY BUTTON
// =============================================================================

/**
 * Surface-variant or tonal button using [CloudStreamColors.SurfaceVariant] and [CloudStreamColors.TextPrimary].
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(10.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
) {
    SecondaryButton(
        onClick = onClick,
        modifier = modifier,
        onLongClick = onLongClick,
        enabled = enabled,
        loading = loading,
        shape = shape,
        contentPadding = contentPadding
    ) {
        ButtonContent(
            text = text,
            icon = icon,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon
        )
    }
}

@Composable
fun SecondaryButton(
    textRes: StringResource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(10.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
) = SecondaryButton(
    text = stringResource(textRes),
    onClick = onClick,
    modifier = modifier,
    onLongClick = onLongClick,
    enabled = enabled,
    loading = loading,
    icon = icon,
    leadingIcon = leadingIcon,
    trailingIcon = trailingIcon,
    shape = shape,
    contentPadding = contentPadding
)

@Composable
fun SecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    shape: Shape = RoundedCornerShape(10.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit
) {
    DesignSystemBaseButton(
        onClick = onClick,
        modifier = modifier,
        onLongClick = onLongClick,
        enabled = enabled,
        loading = loading,
        shape = shape,
        backgroundColor = CloudStreamColors.SurfaceVariant,
        contentColor = CloudStreamColors.TextPrimary,
        disabledBackgroundColor = CloudStreamColors.SurfaceVariant.copy(alpha = 0.5f),
        disabledContentColor = CloudStreamColors.TextMuted,
        border = BorderStroke(1.dp, CloudStreamColors.Divider),
        focusedBorder = BorderStroke(1.5.dp, CloudStreamColors.Primary),
        elevation = 0.dp,
        focusedElevation = 3.dp,
        contentPadding = contentPadding,
        content = content
    )
}

// =============================================================================
// 3. DANGER BUTTON
// =============================================================================

/**
 * Red/destructive action button using [CloudStreamColors.Error] and MaterialTheme.colors.onError.
 */
@Composable
fun DangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(10.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
) {
    DangerButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        loading = loading,
        shape = shape,
        contentPadding = contentPadding
    ) {
        ButtonContent(
            text = text,
            icon = icon,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon
        )
    }
}

@Composable
fun DangerButton(
    textRes: StringResource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(10.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
) = DangerButton(
    text = stringResource(textRes),
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    loading = loading,
    icon = icon,
    leadingIcon = leadingIcon,
    trailingIcon = trailingIcon,
    shape = shape,
    contentPadding = contentPadding
)

@Composable
fun DangerButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    shape: Shape = RoundedCornerShape(10.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit
) {
    DesignSystemBaseButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        loading = loading,
        shape = shape,
        backgroundColor = CloudStreamColors.Error,
        contentColor = MaterialTheme.colors.onError,
        disabledBackgroundColor = CloudStreamColors.Error.copy(alpha = 0.35f),
        disabledContentColor = MaterialTheme.colors.onError.copy(alpha = 0.5f),
        focusedBorder = BorderStroke(1.5.dp, MaterialTheme.colors.onError.copy(alpha = 0.85f)),
        elevation = 2.dp,
        focusedElevation = 6.dp,
        contentPadding = contentPadding,
        content = content
    )
}

// =============================================================================
// 4. GHOST BUTTON
// =============================================================================

/**
 * Text-only subtle button with ripple and subtle hover background.
 */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    contentColor: Color = CloudStreamColors.TextSecondary,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(10.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
) {
    GhostButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        loading = loading,
        contentColor = contentColor,
        shape = shape,
        contentPadding = contentPadding
    ) {
        ButtonContent(
            text = text,
            icon = icon,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            iconSpacing = 6.dp,
            iconSize = 16.dp
        )
    }
}

@Composable
fun GhostButton(
    textRes: StringResource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    contentColor: Color = CloudStreamColors.TextSecondary,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(10.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
) = GhostButton(
    text = stringResource(textRes),
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    loading = loading,
    icon = icon,
    contentColor = contentColor,
    leadingIcon = leadingIcon,
    trailingIcon = trailingIcon,
    shape = shape,
    contentPadding = contentPadding
)

@Composable
fun GhostButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    contentColor: Color = CloudStreamColors.TextSecondary,
    shape: Shape = RoundedCornerShape(10.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit
) {
    DesignSystemBaseButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        loading = loading,
        shape = shape,
        backgroundColor = Color.Transparent,
        contentColor = contentColor,
        disabledBackgroundColor = Color.Transparent,
        disabledContentColor = CloudStreamColors.TextMuted,
        focusedBorder = BorderStroke(1.dp, CloudStreamColors.Primary.copy(alpha = 0.5f)),
        elevation = 0.dp,
        focusedElevation = 0.dp,
        contentPadding = contentPadding,
        content = content
    )
}

// =============================================================================
// 5. OUTLINED ACTION BUTTON
// =============================================================================

/**
 * Outlined bordered button with theme stroke and smooth focus feedback.
 */
@Composable
fun OutlinedActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    borderColor: Color = CloudStreamColors.Primary,
    contentColor: Color = CloudStreamColors.Primary,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(10.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
) {
    OutlinedActionButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        loading = loading,
        borderColor = borderColor,
        contentColor = contentColor,
        shape = shape,
        contentPadding = contentPadding
    ) {
        ButtonContent(
            text = text,
            icon = icon,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon
        )
    }
}

@Composable
fun OutlinedActionButton(
    textRes: StringResource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    borderColor: Color = CloudStreamColors.Primary,
    contentColor: Color = CloudStreamColors.Primary,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(10.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
) = OutlinedActionButton(
    text = stringResource(textRes),
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    loading = loading,
    icon = icon,
    borderColor = borderColor,
    contentColor = contentColor,
    leadingIcon = leadingIcon,
    trailingIcon = trailingIcon,
    shape = shape,
    contentPadding = contentPadding
)

@Composable
fun OutlinedActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    borderColor: Color = CloudStreamColors.Primary,
    contentColor: Color = CloudStreamColors.Primary,
    shape: Shape = RoundedCornerShape(10.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit
) {
    DesignSystemBaseButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        loading = loading,
        shape = shape,
        backgroundColor = Color.Transparent,
        contentColor = contentColor,
        disabledBackgroundColor = Color.Transparent,
        disabledContentColor = CloudStreamColors.TextMuted,
        border = BorderStroke(1.dp, borderColor.copy(alpha = 0.7f)),
        focusedBorder = BorderStroke(1.5.dp, borderColor),
        elevation = 0.dp,
        focusedElevation = 2.dp,
        contentPadding = contentPadding,
        content = content
    )
}
