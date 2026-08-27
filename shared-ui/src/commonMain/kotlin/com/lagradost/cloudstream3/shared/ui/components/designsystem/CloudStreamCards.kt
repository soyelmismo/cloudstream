package com.lagradost.cloudstream3.shared.ui.components.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cloudstream.shared_ui.generated.resources.*
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

// =============================================================================
// 1. SELECTABLE OPTION CARD (SLOT / CUSTOM CONTENT)
// =============================================================================

/**
 * Standardized Selectable Option Card for CloudStream selection lists, dialogs, and settings.
 *
 * Features:
 * - 10-12dp rounded corners ([RoundedCornerShape(12.dp)] by default).
 * - Highlighted border ([BorderStroke(1.5.dp, CloudStreamColors.Primary)]) and translucent primary background when [isSelected] is true.
 * - [CloudStreamColors.SurfaceVariant] background when unselected with subtle divider border.
 * - Hover / focus scale and elevation feedback for TV and Desktop navigations.
 * - Customizable content slot with row layout.
 */
@Composable
fun SelectableOptionCard(
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = CloudStreamColors.Primary,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(12.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable RowScope.() -> Unit
) {
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val isHighlighted = (isHovered || isFocused) && enabled

    val scale by animateFloatAsState(
        targetValue = when {
            !enabled -> 1.0f
            isPressed -> 0.98f
            isHighlighted -> 1.02f
            else -> 1.0f
        },
        animationSpec = tween(durationMillis = 150)
    )

    val cardBgColor by animateColorAsState(
        targetValue = when {
            !enabled -> CloudStreamColors.SurfaceVariant.copy(alpha = 0.4f)
            isSelected -> accentColor.copy(alpha = 0.15f)
            isHighlighted -> CloudStreamColors.SurfaceElevated
            else -> CloudStreamColors.SurfaceVariant
        },
        animationSpec = tween(durationMillis = 150)
    )

    val cardBorder = when {
        !enabled -> null
        isSelected -> BorderStroke(1.5.dp, accentColor)
        isHighlighted -> BorderStroke(1.5.dp, accentColor.copy(alpha = 0.6f))
        else -> BorderStroke(1.dp, CloudStreamColors.Divider.copy(alpha = 0.4f))
    }

    val cardElevation = when {
        !enabled -> 0.dp
        isHighlighted -> 4.dp
        else -> 0.dp
    }

    Surface(
        shape = shape,
        color = cardBgColor,
        contentColor = if (isSelected) accentColor else CloudStreamColors.TextPrimary,
        border = cardBorder,
        elevation = cardElevation,
        modifier = modifier
            .fillMaxWidth()
            .dsClickable(
                onClick = onClick,
                interactionSource = interactionSource,
                enabled = enabled,
                scale = scale
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            content = content
        )
    }
}

// =============================================================================
// 2. SELECTABLE OPTION CARD (CANONICAL IMPLEMENTATION & STRING RESOURCE OVERLOAD)
// =============================================================================

/**
 * Standardized Selectable Option Card with String title and optional subtitle, icons, and actions.
 */
@Composable
fun SelectableOptionCard(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    painter: Painter? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    accentColor: Color = CloudStreamColors.Primary,
    shape: Shape = RoundedCornerShape(12.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    SelectableOptionCard(
        isSelected = isSelected,
        onClick = onClick,
        modifier = modifier,
        accentColor = accentColor,
        enabled = enabled,
        shape = shape,
        contentPadding = contentPadding,
        interactionSource = interactionSource
    ) {
        Row(
            modifier = Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingContent != null) {
                leadingContent()
                Spacer(modifier = Modifier.width(12.dp))
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) accentColor else CloudStreamColors.TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
            } else if (painter != null) {
                Icon(
                    painter = painter,
                    contentDescription = null,
                    tint = if (isSelected) accentColor else CloudStreamColors.TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.body2.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 15.sp
                    ),
                    color = if (isSelected) accentColor else CloudStreamColors.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (subtitle != null) {
                    BodyMutedText(
                        text = subtitle,
                        style = MaterialTheme.typography.caption.copy(fontSize = 12.sp),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (trailingContent != null) {
            Spacer(modifier = Modifier.width(8.dp))
            trailingContent()
        } else if (isSelected) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = stringResource(Res.string.selected),
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Standardized Selectable Option Card with StringResource title and optional subtitle.
 */
@Composable
fun SelectableOptionCard(
    titleRes: StringResource,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitleRes: StringResource? = null,
    subtitle: String? = subtitleRes?.let { stringResource(it) },
    icon: ImageVector? = null,
    painter: Painter? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    accentColor: Color = CloudStreamColors.Primary,
    shape: Shape = RoundedCornerShape(12.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) = SelectableOptionCard(
    title = stringResource(titleRes),
    isSelected = isSelected,
    onClick = onClick,
    modifier = modifier,
    subtitle = subtitle,
    icon = icon,
    painter = painter,
    leadingContent = leadingContent,
    trailingContent = trailingContent,
    accentColor = accentColor,
    shape = shape,
    contentPadding = contentPadding,
    enabled = enabled,
    interactionSource = interactionSource
)
