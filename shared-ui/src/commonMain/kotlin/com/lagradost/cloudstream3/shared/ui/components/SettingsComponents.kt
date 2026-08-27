package com.lagradost.cloudstream3.shared.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.RadioButton
import androidx.compose.material.RadioButtonDefaults
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.Surface
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import cloudstream.shared_ui.generated.resources.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.shared.ui.components.designsystem.SectionHeader
import com.lagradost.cloudstream3.shared.ui.components.designsystem.SelectableOptionCard
import com.lagradost.cloudstream3.shared.ui.theme.CloudstreamTheme

/**
 * Common Card container for settings groups.
 */
@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = CloudstreamTheme.extendedColors.cardBackground,
    borderColor: Color = CloudstreamTheme.extendedColors.cardBorder,
    shape: RoundedCornerShape = RoundedCornerShape(12.dp),
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        backgroundColor = backgroundColor,
        border = BorderStroke(1.dp, borderColor),
        elevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            content()
        }
    }
}

/**
 * Section Title with optional icon and description.
 */
@Composable
fun SettingsSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colors.primary
) {
    SectionHeader(
        title = title,
        modifier = modifier,
        description = description,
        icon = icon,
        iconTint = iconTint
    )
}

/**
 * Generic Settings item row with hover and click support for Desktop and Mobile.
 */
@Composable
fun SettingsItemRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colors.primary,
    valueText: String? = null,
    badgeText: String? = null,
    badgeColor: Color = MaterialTheme.colors.primary,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isHighlighted = (isHovered || isFocused) && onClick != null && enabled

    val rowBgColor by animateColorAsState(
        targetValue = when {
            !enabled -> Color.Transparent
            isHighlighted -> CloudstreamTheme.extendedColors.hoverBackground
            else -> Color.Transparent
        },
        animationSpec = tween(150)
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(rowBgColor)
            .then(
                if (onClick != null && enabled) {
                    Modifier
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onClick
                        )
                        .focusable(
                            enabled = enabled,
                            interactionSource = interactionSource
                        )
                        .onKeyEvent { keyEvent ->
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
                } else Modifier
            )
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconTint.copy(alpha = if (enabled) 0.15f else 0.05f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (enabled) iconTint else iconTint.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.body1.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp
                        ),
                        color = if (enabled) CloudstreamTheme.extendedColors.textPrimary
                        else CloudstreamTheme.extendedColors.textMuted
                    )

                    if (badgeText != null) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = badgeColor.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.caption.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                ),
                                color = badgeColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.body2.copy(fontSize = 12.sp),
                        color = CloudstreamTheme.extendedColors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (valueText != null) {
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.body2.copy(
                        color = MaterialTheme.colors.primary,
                        fontWeight = FontWeight.Medium
                    ),
                    modifier = Modifier.padding(end = 4.dp)
                )
            }

            if (trailingContent != null) {
                trailingContent()
            } else if (onClick != null) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = CloudstreamTheme.extendedColors.textMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Settings item with toggle switch.
 */
@Composable
fun SettingsSwitchItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colors.primary,
    enabled: Boolean = true
) {
    SettingsItemRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        iconTint = iconTint,
        enabled = enabled,
        modifier = modifier,
        onClick = { if (enabled) onCheckedChange(!checked) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = if (enabled) onCheckedChange else null,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colors.primary,
                    checkedTrackColor = MaterialTheme.colors.primary.copy(alpha = 0.5f),
                    uncheckedThumbColor = CloudstreamTheme.extendedColors.textMuted,
                    uncheckedTrackColor = CloudstreamTheme.extendedColors.divider
                )
            )
        }
    )
}

/**
 * Settings item with Slider.
 */
@Composable
fun SettingsSliderItem(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    steps: Int = 0,
    valueDisplay: String = "${value.toInt()}",
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colors.primary,
    enabled: Boolean = true
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(iconTint.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.Medium),
                        color = CloudstreamTheme.extendedColors.textPrimary
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.caption,
                            color = CloudstreamTheme.extendedColors.textSecondary
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colors.primary.copy(alpha = 0.15f)
            ) {
                Text(
                    text = valueDisplay,
                    style = MaterialTheme.typography.body2.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colors.primary
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colors.primary,
                activeTrackColor = MaterialTheme.colors.primary,
                inactiveTrackColor = CloudstreamTheme.extendedColors.divider
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Modal dialog for selecting a single item from a list.
 * Utilizes standard Design System [ActionDialog] and [SelectableOptionCard].
 */
@Composable
fun <T> SettingsChoiceDialog(
    title: String,
    items: List<T>,
    selectedItem: T,
    itemLabel: (T) -> String,
    onItemSelected: (T) -> Unit,
    onDismissRequest: () -> Unit,
    itemSubtitle: ((T) -> String?)? = null
) {
    com.lagradost.cloudstream3.shared.ui.components.designsystem.ActionDialog(
        onDismissRequest = onDismissRequest,
        title = title,
        showCloseButton = true,
        cancelTextRes = Res.string.cancel,
        onCancel = onDismissRequest,
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items.forEach { item ->
                    val isSelected = item == selectedItem
                    SelectableOptionCard(
                        title = itemLabel(item),
                        subtitle = itemSubtitle?.invoke(item),
                        isSelected = isSelected,
                        onClick = {
                            onItemSelected(item)
                            onDismissRequest()
                        }
                    )
                }
            }
        }
    )
}

/**
 * Settings Navigation Category Model for Responsive Dual-Pane / Tab layouts.
 */
data class SettingsCategory(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val badgeCount: Int = 0
)

/**
 * Responsive Dual-Pane master-detail layout for wide screens / desktop
 * and drilldown layout for mobile screens.
 */
@Composable
fun ResponsiveSettingsScaffold(
    categories: List<SettingsCategory>,
    selectedCategoryId: String?,
    onSelectCategory: (String?) -> Unit,
    topBarTitle: String = stringResource(Res.string.settings_title),
    onBackClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable (selectedCategory: SettingsCategory) -> Unit
) {
    val activeCategory = categories.firstOrNull { it.id == selectedCategoryId } ?: categories.first()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
    ) {
        val isWideScreen = maxWidth >= 720.dp

        if (isWideScreen) {
            // Dual-Pane Layout: Left Sidebar + Right Content Pane
            Row(
                modifier = Modifier.fillMaxSize()
            ) {
                // Sidebar
                Surface(
                    modifier = Modifier
                        .width(280.dp)
                        .fillMaxHeight(),
                    color = MaterialTheme.colors.background,
                    border = BorderStroke(1.dp, CloudstreamTheme.extendedColors.divider.copy(alpha = 0.5f)),
                    elevation = 0.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // Header
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            Text(
                                text = topBarTitle,
                                style = MaterialTheme.typography.h5.copy(fontWeight = FontWeight.Bold),
                                color = CloudstreamTheme.extendedColors.textPrimary
                            )
                        }

                        Divider(
                            color = CloudstreamTheme.extendedColors.divider,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        // Category Navigation Items
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            categories.forEach { category ->
                                val isSelected = category.id == (selectedCategoryId ?: categories.first().id)
                                val interactionSource = remember { MutableInteractionSource() }
                                val isHovered by interactionSource.collectIsHoveredAsState()

                                val itemBg by animateColorAsState(
                                    targetValue = when {
                                        isSelected -> MaterialTheme.colors.primary.copy(alpha = 0.15f)
                                        isHovered -> CloudstreamTheme.extendedColors.hoverBackground
                                        else -> Color.Transparent
                                    },
                                    animationSpec = tween(150)
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(itemBg)
                                        .clickable(
                                            interactionSource = interactionSource,
                                            indication = null,
                                            onClick = { onSelectCategory(category.id) }
                                        )
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colors.primary
                                                else CloudstreamTheme.extendedColors.divider
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = category.icon,
                                            contentDescription = category.title,
                                            tint = if (isSelected) MaterialTheme.colors.onPrimary else CloudstreamTheme.extendedColors.textSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = category.title,
                                            style = MaterialTheme.typography.body1.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            ),
                                            color = if (isSelected) MaterialTheme.colors.primary
                                            else CloudstreamTheme.extendedColors.textPrimary
                                        )
                                    }

                                    if (category.badgeCount > 0) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colors.primary
                                        ) {
                                            Text(
                                                text = "${category.badgeCount}",
                                                style = MaterialTheme.typography.caption.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colors.onPrimary
                                                ),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Detail Content Pane
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(24.dp)
                ) {
                    content(activeCategory)
                }
            }
        } else {
            // Single-Pane / Mobile Layout
            if (selectedCategoryId == null) {
                // Settings Root Menu: Show full list of categories
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    TopAppBar(
                        title = {
                            Text(
                                text = topBarTitle,
                                style = MaterialTheme.typography.h6.copy(fontWeight = FontWeight.Bold)
                            )
                        },
                        navigationIcon = if (onBackClick != null) {
                            {
                                IconButton(onClick = onBackClick) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(Res.string.action_back)
                                    )
                                }
                            }
                        } else null,
                        backgroundColor = CloudstreamTheme.extendedColors.cardBackground,
                        contentColor = CloudstreamTheme.extendedColors.textPrimary,
                        elevation = 0.dp
                    )

                    Divider(color = CloudstreamTheme.extendedColors.divider)

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(categories) { category ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                backgroundColor = CloudstreamTheme.extendedColors.cardBackground,
                                border = BorderStroke(1.dp, CloudstreamTheme.extendedColors.divider),
                                elevation = 0.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectCategory(category.id) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colors.primary.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = category.icon,
                                            contentDescription = category.title,
                                            tint = MaterialTheme.colors.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(16.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = category.title,
                                            style = MaterialTheme.typography.subtitle1.copy(fontWeight = FontWeight.SemiBold),
                                            color = CloudstreamTheme.extendedColors.textPrimary
                                        )
                                        if (category.description.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = category.description,
                                                style = MaterialTheme.typography.body2,
                                                color = CloudstreamTheme.extendedColors.textSecondary
                                            )
                                        }
                                    }

                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = CloudstreamTheme.extendedColors.textMuted,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Inside Category Detail View
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    TopAppBar(
                        title = {
                            Text(
                                text = activeCategory.title,
                                style = MaterialTheme.typography.h6.copy(fontWeight = FontWeight.Bold)
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { onSelectCategory(null) }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(Res.string.action_back)
                                )
                            }
                        },
                        backgroundColor = CloudstreamTheme.extendedColors.cardBackground,
                        contentColor = CloudstreamTheme.extendedColors.textPrimary,
                        elevation = 0.dp
                    )

                    Divider(color = CloudstreamTheme.extendedColors.divider)

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        content(activeCategory)
                    }
                }
            }
        }
    }
}
