package com.lagradost.cloudstream3.shared.ui.components.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cloudstream.shared_ui.generated.resources.*
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

// =============================================================================
// 1. SELECTABLE FILTER CHIP
// =============================================================================

/**
 * Standardized Selectable Filter Chip adhering to the CloudStream Design System.
 *
 * Supports hover / focus states for TV & Desktop, keyboard activation,
 * active/inactive color transitions, and optional leading or checkmark icons.
 */
@Composable
fun CloudStreamFilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    leadingPainter: Painter? = null,
    showCheckIconWhenSelected: Boolean = true,
    activeContainerColor: Color = CloudStreamColors.Primary.copy(alpha = 0.18f),
    activeContentColor: Color = CloudStreamColors.Primary,
    inactiveContainerColor: Color = CloudStreamColors.Surface,
    inactiveContentColor: Color = CloudStreamColors.TextSecondary,
    shape: Shape = RoundedCornerShape(16.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val isHighlighted = (isHovered || isFocused) && enabled

    val scale by animateFloatAsState(
        targetValue = when {
            !enabled -> 1.0f
            isPressed -> 0.96f
            isHighlighted -> 1.04f
            else -> 1.0f
        },
        animationSpec = tween(durationMillis = 150)
    )

    val currentBgColor by animateColorAsState(
        targetValue = when {
            !enabled -> inactiveContainerColor.copy(alpha = 0.5f)
            isSelected -> activeContainerColor
            isHighlighted -> CloudStreamColors.SurfaceElevated
            else -> inactiveContainerColor
        },
        animationSpec = tween(durationMillis = 150)
    )

    val currentContentColor by animateColorAsState(
        targetValue = when {
            !enabled -> inactiveContentColor.copy(alpha = 0.4f)
            isSelected -> activeContentColor
            isHighlighted -> CloudStreamColors.TextPrimary
            else -> inactiveContentColor
        },
        animationSpec = tween(durationMillis = 150)
    )

    val currentBorder = when {
        !enabled -> null
        isSelected -> BorderStroke(1.dp, activeContentColor)
        isHighlighted -> BorderStroke(1.dp, activeContentColor.copy(alpha = 0.6f))
        else -> BorderStroke(1.dp, CloudStreamColors.Divider.copy(alpha = 0.35f))
    }

    Surface(
        shape = shape,
        color = currentBgColor,
        border = currentBorder,
        modifier = modifier
            .pointerHoverIcon(PointerIcon.Hand)
            .clip(shape)
            .dsClickable(
                onClick = onClick,
                interactionSource = interactionSource,
                enabled = enabled,
                scale = scale
            )
    ) {
        Row(
            modifier = Modifier.padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = currentContentColor,
                    modifier = Modifier.size(14.dp)
                )
            } else if (leadingPainter != null) {
                Icon(
                    painter = leadingPainter,
                    contentDescription = null,
                    tint = currentContentColor,
                    modifier = Modifier.size(14.dp)
                )
            } else if (isSelected && showCheckIconWhenSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = currentContentColor,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = currentContentColor
            )
        }
    }
}

/**
 * StringResource overload for [CloudStreamFilterChip].
 */
@Composable
fun CloudStreamFilterChip(
    labelRes: StringResource,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    leadingPainter: Painter? = null,
    showCheckIconWhenSelected: Boolean = true,
    activeContainerColor: Color = CloudStreamColors.Primary.copy(alpha = 0.18f),
    activeContentColor: Color = CloudStreamColors.Primary,
    inactiveContainerColor: Color = CloudStreamColors.Surface,
    inactiveContentColor: Color = CloudStreamColors.TextSecondary,
    shape: Shape = RoundedCornerShape(16.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) = CloudStreamFilterChip(
    label = stringResource(labelRes),
    isSelected = isSelected,
    onClick = onClick,
    modifier = modifier,
    leadingIcon = leadingIcon,
    leadingPainter = leadingPainter,
    showCheckIconWhenSelected = showCheckIconWhenSelected,
    activeContainerColor = activeContainerColor,
    activeContentColor = activeContentColor,
    inactiveContainerColor = inactiveContainerColor,
    inactiveContentColor = inactiveContentColor,
    shape = shape,
    contentPadding = contentPadding,
    enabled = enabled,
    interactionSource = interactionSource
)

// =============================================================================
// 2. ACTION CHIP (CLEAR / RESET / QUICK ACTIONS)
// =============================================================================

/**
 * Standardized Action Chip (such as Clear Filters, Reset, or Action pills).
 */
@Composable
fun CloudStreamActionChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = CloudStreamColors.Error.copy(alpha = 0.15f),
    contentColor: Color = CloudStreamColors.Error,
    shape: Shape = RoundedCornerShape(16.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val isHighlighted = (isHovered || isFocused) && enabled

    val scale by animateFloatAsState(
        targetValue = when {
            !enabled -> 1.0f
            isPressed -> 0.96f
            isHighlighted -> 1.04f
            else -> 1.0f
        },
        animationSpec = tween(durationMillis = 150)
    )

    Surface(
        shape = shape,
        color = if (isHighlighted) containerColor.copy(alpha = 0.25f) else containerColor,
        border = BorderStroke(1.dp, contentColor.copy(alpha = if (isHighlighted) 0.8f else 0.45f)),
        modifier = modifier
            .pointerHoverIcon(PointerIcon.Hand)
            .clip(shape)
            .dsClickable(
                onClick = onClick,
                interactionSource = interactionSource,
                enabled = enabled,
                scale = scale
            )
    ) {
        Row(
            modifier = Modifier.padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
        }
    }
}

/**
 * StringResource overload for [CloudStreamActionChip].
 */
@Composable
fun CloudStreamActionChip(
    labelRes: StringResource,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = CloudStreamColors.Error.copy(alpha = 0.15f),
    contentColor: Color = CloudStreamColors.Error,
    shape: Shape = RoundedCornerShape(16.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) = CloudStreamActionChip(
    label = stringResource(labelRes),
    icon = icon,
    onClick = onClick,
    modifier = modifier,
    containerColor = containerColor,
    contentColor = contentColor,
    shape = shape,
    contentPadding = contentPadding,
    enabled = enabled,
    interactionSource = interactionSource
)

// =============================================================================
// 3. DROPDOWN FILTER CHIP (MULTI-SELECT & SINGLE-SELECT)
// =============================================================================

/**
 * Shared internal container managing the filter chip surface, animations, focus/hover states,
 * and the expanded DropdownMenu.
 */
@Composable
private fun DropdownFilterContainer(
    label: String,
    modifier: Modifier = Modifier,
    isFiltered: Boolean,
    menuTitle: String? = null,
    menuTitleRes: StringResource? = null,
    leadingIcon: ImageVector? = null,
    leadingPainter: Painter? = null,
    trailingIcon: ImageVector = Icons.Default.ArrowDropDown,
    activeContainerColor: Color = CloudStreamColors.Primary.copy(alpha = 0.18f),
    activeContentColor: Color = CloudStreamColors.Primary,
    inactiveContainerColor: Color = CloudStreamColors.Surface,
    inactiveContentColor: Color = CloudStreamColors.TextPrimary,
    shape: Shape = RoundedCornerShape(16.dp),
    minMenuWidth: Dp = 180.dp,
    maxMenuWidth: Dp = 280.dp,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    menuContent: @Composable (dismissMenu: () -> Unit) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val isHighlighted = (isHovered || isFocused || expanded) && enabled

    val scale by animateFloatAsState(
        targetValue = when {
            !enabled -> 1.0f
            isPressed -> 0.96f
            isHighlighted -> 1.04f
            else -> 1.0f
        },
        animationSpec = tween(durationMillis = 150)
    )

    val currentBgColor by animateColorAsState(
        targetValue = when {
            !enabled -> inactiveContainerColor.copy(alpha = 0.5f)
            isFiltered -> activeContainerColor
            isHighlighted -> CloudStreamColors.SurfaceElevated
            else -> inactiveContainerColor
        },
        animationSpec = tween(durationMillis = 150)
    )

    val currentContentColor by animateColorAsState(
        targetValue = when {
            !enabled -> inactiveContentColor.copy(alpha = 0.4f)
            isFiltered -> activeContentColor
            isHighlighted -> CloudStreamColors.TextPrimary
            else -> inactiveContentColor
        },
        animationSpec = tween(durationMillis = 150)
    )

    val currentBorder = when {
        !enabled -> null
        isFiltered -> BorderStroke(1.dp, activeContentColor)
        isHighlighted -> BorderStroke(1.dp, activeContentColor.copy(alpha = 0.6f))
        else -> BorderStroke(1.dp, CloudStreamColors.Divider.copy(alpha = 0.35f))
    }

    Box(modifier = modifier) {
        Surface(
            shape = shape,
            color = currentBgColor,
            border = currentBorder,
            modifier = Modifier
                .pointerHoverIcon(PointerIcon.Hand)
                .clip(shape)
                .dsClickable(
                    onClick = { expanded = true },
                    interactionSource = interactionSource,
                    enabled = enabled,
                    scale = scale
                )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = currentContentColor,
                        modifier = Modifier.size(16.dp)
                    )
                } else if (leadingPainter != null) {
                    Icon(
                        painter = leadingPainter,
                        contentDescription = null,
                        tint = currentContentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = if (isFiltered) FontWeight.Bold else FontWeight.Normal,
                    color = currentContentColor
                )
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    tint = if (isFiltered) currentContentColor else currentContentColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .widthIn(min = minMenuWidth, max = maxMenuWidth)
                .background(CloudStreamColors.SurfaceElevated)
        ) {
            val resolvedTitle = menuTitle ?: menuTitleRes?.let { stringResource(it) }
            if (!resolvedTitle.isNullOrBlank()) {
                Text(
                    text = resolvedTitle,
                    style = MaterialTheme.typography.caption.copy(
                        fontWeight = FontWeight.Bold,
                        color = CloudStreamColors.TextMuted
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            menuContent { expanded = false }
        }
    }
}

/**
 * Standardized Multi-Select Dropdown Filter Chip for the CloudStream Design System.
 *
 * Renders a clickable chip button that expands a [DropdownMenu] containing options with checkboxes.
 */
@Composable
fun <T> CloudStreamDropdownFilter(
    label: String,
    items: List<T>,
    selectedItems: Set<T>,
    onToggleItem: (T) -> Unit,
    modifier: Modifier = Modifier,
    itemLabel: @Composable (T) -> String = { it.toString() },
    itemLeadingContent: (@Composable (T) -> Unit)? = null,
    menuTitle: String? = null,
    menuTitleRes: StringResource? = null,
    leadingIcon: ImageVector? = null,
    leadingPainter: Painter? = null,
    trailingIcon: ImageVector = Icons.Default.ArrowDropDown,
    isFiltered: Boolean = selectedItems.isNotEmpty(),
    activeContainerColor: Color = CloudStreamColors.Primary.copy(alpha = 0.18f),
    activeContentColor: Color = CloudStreamColors.Primary,
    inactiveContainerColor: Color = CloudStreamColors.Surface,
    inactiveContentColor: Color = CloudStreamColors.TextPrimary,
    shape: Shape = RoundedCornerShape(16.dp),
    minMenuWidth: Dp = 180.dp,
    maxMenuWidth: Dp = 280.dp,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    DropdownFilterContainer(
        label = label,
        modifier = modifier,
        isFiltered = isFiltered,
        menuTitle = menuTitle,
        menuTitleRes = menuTitleRes,
        leadingIcon = leadingIcon,
        leadingPainter = leadingPainter,
        trailingIcon = trailingIcon,
        activeContainerColor = activeContainerColor,
        activeContentColor = activeContentColor,
        inactiveContainerColor = inactiveContainerColor,
        inactiveContentColor = inactiveContentColor,
        shape = shape,
        minMenuWidth = minMenuWidth,
        maxMenuWidth = maxMenuWidth,
        enabled = enabled,
        interactionSource = interactionSource
    ) {
        for (item in items) {
            val isItemSelected = selectedItems.contains(item)
            DropdownMenuItem(
                onClick = {
                    onToggleItem(item)
                }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (itemLeadingContent != null) {
                        itemLeadingContent(item)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = itemLabel(item),
                        fontSize = 13.sp,
                        color = CloudStreamColors.TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Checkbox(
                        checked = isItemSelected,
                        onCheckedChange = { onToggleItem(item) },
                        colors = CheckboxDefaults.colors(
                            checkedColor = CloudStreamColors.Primary,
                            checkmarkColor = MaterialTheme.colors.onPrimary,
                            uncheckedColor = CloudStreamColors.TextMuted
                        ),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Standardized Single-Select Dropdown Filter Chip for the CloudStream Design System.
 *
 * Renders a clickable chip button that expands a [DropdownMenu] containing options with check indicators.
 */
@Composable
fun <T> CloudStreamDropdownFilter(
    label: String,
    items: List<T>,
    selectedItem: T?,
    onSelectItem: (T) -> Unit,
    modifier: Modifier = Modifier,
    itemLabel: @Composable (T) -> String = { it.toString() },
    itemLeadingContent: (@Composable (T) -> Unit)? = null,
    menuTitle: String? = null,
    menuTitleRes: StringResource? = null,
    leadingIcon: ImageVector? = null,
    leadingPainter: Painter? = null,
    trailingIcon: ImageVector = Icons.Default.ArrowDropDown,
    isFiltered: Boolean = selectedItem != null,
    activeContainerColor: Color = CloudStreamColors.Primary.copy(alpha = 0.18f),
    activeContentColor: Color = CloudStreamColors.Primary,
    inactiveContainerColor: Color = CloudStreamColors.Surface,
    inactiveContentColor: Color = CloudStreamColors.TextPrimary,
    shape: Shape = RoundedCornerShape(16.dp),
    minMenuWidth: Dp = 180.dp,
    maxMenuWidth: Dp = 280.dp,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    DropdownFilterContainer(
        label = label,
        modifier = modifier,
        isFiltered = isFiltered,
        menuTitle = menuTitle,
        menuTitleRes = menuTitleRes,
        leadingIcon = leadingIcon,
        leadingPainter = leadingPainter,
        trailingIcon = trailingIcon,
        activeContainerColor = activeContainerColor,
        activeContentColor = activeContentColor,
        inactiveContainerColor = inactiveContainerColor,
        inactiveContentColor = inactiveContentColor,
        shape = shape,
        minMenuWidth = minMenuWidth,
        maxMenuWidth = maxMenuWidth,
        enabled = enabled,
        interactionSource = interactionSource
    ) { dismissMenu ->
        for (item in items) {
            val isItemSelected = selectedItem == item
            DropdownMenuItem(
                onClick = {
                    onSelectItem(item)
                    dismissMenu()
                }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (itemLeadingContent != null) {
                        itemLeadingContent(item)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = itemLabel(item),
                        fontSize = 13.sp,
                        fontWeight = if (isItemSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isItemSelected) CloudStreamColors.Primary else CloudStreamColors.TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    if (isItemSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = CloudStreamColors.Primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
