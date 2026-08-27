package com.lagradost.cloudstream3.shared.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.shared.ui.Screen
import org.jetbrains.compose.resources.stringResource
import cloudstream.shared_ui.generated.resources.*
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors
import com.lagradost.cloudstream3.shared.ui.theme.CloudstreamTheme

/**
 * Data representation for bottom navigation items.
 */
private data class BottomNavItemData(
    val screen: Screen,
    val icon: ImageVector,
    val label: String,
    val badgeCount: Int? = null,
    val showDotBadge: Boolean = false,
    val isSelected: Boolean,
    val onClick: () -> Unit
)

/**
 * Premium AMOLED Bottom Navigation Bar for mobile and compact window sizes (< 600dp).
 *
 * Features:
 * - Pure AMOLED / dark styling with subtle divider and high-contrast labels.
 * - Smooth animated indicator pill and icon scale transitions.
 * - Support for badge indicators (counters / dots).
 * - Tactile ripple feedback and accessible tap targets.
 *
 * @param currentScreen The currently active [Screen] destination.
 * @param onNavigate Callback invoked when a navigation item is selected.
 * @param modifier Optional modifier for styling and positioning.
 */
@Composable
fun AppBottomNavigation(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    val navHome = stringResource(Res.string.navHome)
    val navSearch = stringResource(Res.string.navSearch)
    val navLibrary = stringResource(Res.string.navLibrary)
    val navDownloads = stringResource(Res.string.navDownloads)
    val navSettings = stringResource(Res.string.navSettings)

    val items = remember(currentScreen, onNavigate, navHome, navSearch, navLibrary, navDownloads, navSettings) {
        listOf(
            BottomNavItemData(
                screen = Screen.Home,
                icon = Icons.Default.Home,
                label = navHome,
                isSelected = currentScreen is Screen.Home,
                onClick = { onNavigate(Screen.Home) }
            ),
            BottomNavItemData(
                screen = Screen.Search,
                icon = Icons.Default.Search,
                label = navSearch,
                isSelected = currentScreen is Screen.Search,
                onClick = { onNavigate(Screen.Search) }
            ),
            BottomNavItemData(
                screen = Screen.Library,
                icon = Icons.Default.Bookmark,
                label = navLibrary,
                isSelected = currentScreen is Screen.Library,
                onClick = { onNavigate(Screen.Library) }
            ),
            BottomNavItemData(
                screen = Screen.Downloads,
                icon = Icons.Default.Download,
                label = navDownloads,
                isSelected = currentScreen is Screen.Downloads,
                onClick = { onNavigate(Screen.Downloads) }
            ),
            BottomNavItemData(
                screen = Screen.Settings,
                icon = Icons.Default.Settings,
                label = navSettings,
                isSelected = currentScreen is Screen.Settings,
                onClick = { onNavigate(Screen.Settings) }
            )
        )
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colors.surface,
        elevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // Subtle top divider line for crisp AMOLED surface separation
            Divider(
                color = CloudstreamTheme.extendedColors.divider,
                thickness = 1.dp,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    BottomNavigationItemView(
                        item = item,
                        onClick = item.onClick,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * Individual bottom navigation item view with animated selection pill,
 * scale bounce, badge support, and touch ripple effect.
 */
@Composable
private fun BottomNavigationItemView(
    item: BottomNavItemData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSelected = item.isSelected
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isHighlighted = isHovered || isFocused

    val scale by animateFloatAsState(
        targetValue = if (isHighlighted) 1.08f else 1.0f,
        animationSpec = tween(150)
    )

    val iconColor = if (isSelected || isHighlighted) MaterialTheme.colors.primary else CloudstreamTheme.extendedColors.textSecondary
    val textColor = if (isSelected || isHighlighted) MaterialTheme.colors.primary else CloudstreamTheme.extendedColors.textMuted

    Column(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icon container with active indicator pill & badge
        Box(
            contentAlignment = Alignment.Center
        ) {
            // Pill background for active or focused item
            if (isSelected || isHighlighted) {
                Box(
                    modifier = Modifier
                        .size(width = 48.dp, height = 26.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(
                            if (isSelected) MaterialTheme.colors.primary.copy(alpha = 0.16f)
                            else CloudStreamColors.SurfaceElevated
                        )
                )
            }

            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )

            // Badge if present
            if (item.badgeCount != null && item.badgeCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = (-4).dp)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(CloudStreamColors.Error),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (item.badgeCount > 99) "99+" else item.badgeCount.toString(),
                        color = MaterialTheme.colors.onError,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else if (item.showDotBadge) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = (-2).dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colors.primary)
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = item.label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = if (isSelected || isHighlighted) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1
        )
    }
}
