package com.lagradost.cloudstream3.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream4.generated.resources.*
import com.lagradost.cloudstream3.shared.ui.Screen
import com.lagradost.cloudstream3.shared.ui.focus.dpadFocusable
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamTheme
import com.lagradost.cloudstream3.shared.ui.theme.CloudstreamTheme
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

@Immutable
private data class BottomNavItemData(
    val screen: Screen,
    val icon: ImageVector,
    val label: String,
    val badgeCount: Int? = null,
    val showDotBadge: Boolean = false,
    val isSelected: Boolean,
    val onClick: () -> Unit
)

@Immutable
private data class NavigationItemColors(
    val iconColor: Color,
    val textColor: Color,
    val pillColor: Color?,
    val fontWeight: FontWeight
)

@Immutable
private data class RailItemColors(
    val backgroundColor: Color,
    val contentColor: Color,
    val fontWeight: FontWeight
)

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
        persistentListOf(
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

@Composable
private fun resolveNavigationItemColors(
    isSelected: Boolean,
    isHighlighted: Boolean
): NavigationItemColors {
    val isActive = isSelected || isHighlighted
    val primaryColor = MaterialTheme.colors.primary
    val (iconColor, textColor, fontWeight) = if (isActive) {
        Triple(primaryColor, primaryColor, FontWeight.SemiBold)
    } else {
        Triple(
            CloudstreamTheme.extendedColors.textSecondary,
            CloudstreamTheme.extendedColors.textMuted,
            FontWeight.Medium
        )
    }

    val pillColor = when {
        isSelected -> primaryColor.copy(alpha = 0.16f)
        isHighlighted -> CloudStreamColors.SurfaceElevated
        else -> null
    }

    return NavigationItemColors(
        iconColor = iconColor,
        textColor = textColor,
        pillColor = pillColor,
        fontWeight = fontWeight
    )
}

@Composable
private fun BottomNavItemPill(color: Color) {
    Box(
        modifier = Modifier
            .size(width = 48.dp, height = 26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(color)
    )
}

@Composable
private fun BottomNavItemBadge(
    badgeCount: Int?,
    showDotBadge: Boolean,
    modifier: Modifier = Modifier
) {
    if (badgeCount != null && badgeCount > 0) {
        CountBadge(count = badgeCount, modifier = modifier)
    } else if (showDotBadge) {
        DotBadge(modifier = modifier)
    }
}

@Composable
private fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    val text = if (count > 99) "99+" else count.toString()
    Box(
        modifier = modifier
            .offset(x = 6.dp, y = (-4).dp)
            .size(16.dp)
            .clip(CircleShape)
            .background(CloudStreamColors.Error),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = MaterialTheme.colors.onError,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun DotBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .offset(x = 2.dp, y = (-2).dp)
            .size(7.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colors.primary)
    )
}

@Composable
private fun BottomNavigationItemView(
    item: BottomNavItemData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val colors = resolveNavigationItemColors(
        isSelected = item.isSelected,
        isHighlighted = isHovered || isFocused
    )

    Column(
        modifier = modifier
            .dpadFocusable(
                onClick = onClick,
                interactionSource = interactionSource,
                shape = RoundedCornerShape(16.dp),
                scaleOnFocus = 1.08f,
                focusedBorderWidth = 0.dp,
                focusedBorderColor = Color.Transparent
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            colors.pillColor?.let { BottomNavItemPill(color = it) }

            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = colors.iconColor,
                modifier = Modifier.size(22.dp)
            )

            BottomNavItemBadge(
                badgeCount = item.badgeCount,
                showDotBadge = item.showDotBadge,
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = item.label,
            color = colors.textColor,
            fontSize = 11.sp,
            fontWeight = colors.fontWeight,
            maxLines = 1
        )
    }
}

@Composable
fun AppNavigationRail(
    currentScreen: Screen,
    canNavigateBack: Boolean = false,
    onNavigateBack: () -> Unit = {},
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colors.surface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (canNavigateBack) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .dpadFocusable(
                            onClick = onNavigateBack,
                            shape = RoundedCornerShape(12.dp),
                            scaleOnFocus = 1.06f
                        )
                        .background(CloudStreamColors.SurfaceVariant, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(Res.string.action_back),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .dpadFocusable(
                            onClick = { onNavigate(Screen.Home) },
                            shape = RoundedCornerShape(12.dp),
                            scaleOnFocus = 1.06f
                        )
                        .background(CloudStreamColors.SurfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.cloud_2_gradient),
                        contentDescription = stringResource(Res.string.app_name),
                        tint = Color.Unspecified,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            NavigationRailItem(
                icon = Icons.Default.Home,
                label = stringResource(Res.string.navHome),
                isSelected = currentScreen is Screen.Home,
                onClick = { onNavigate(Screen.Home) }
            )

            Spacer(modifier = Modifier.height(10.dp))

            NavigationRailItem(
                icon = Icons.Default.Search,
                label = stringResource(Res.string.navSearch),
                isSelected = currentScreen is Screen.Search,
                onClick = { onNavigate(Screen.Search) }
            )

            Spacer(modifier = Modifier.height(10.dp))

            NavigationRailItem(
                icon = Icons.Default.Bookmark,
                label = stringResource(Res.string.navLibrary),
                isSelected = currentScreen is Screen.Library,
                onClick = { onNavigate(Screen.Library) }
            )

            Spacer(modifier = Modifier.height(10.dp))

            NavigationRailItem(
                icon = Icons.Default.Download,
                label = stringResource(Res.string.navDownloads),
                isSelected = currentScreen is Screen.Downloads,
                onClick = { onNavigate(Screen.Downloads) }
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            NavigationRailItem(
                icon = Icons.Default.Settings,
                label = stringResource(Res.string.navSettings),
                isSelected = currentScreen is Screen.Settings,
                onClick = { onNavigate(Screen.Settings) }
            )
        }
    }
}

@Composable
private fun resolveRailItemColors(
    isSelected: Boolean,
    isHighlighted: Boolean
): RailItemColors = when {
    isSelected -> RailItemColors(
        backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.16f),
        contentColor = MaterialTheme.colors.primary,
        fontWeight = FontWeight.SemiBold
    )
    isHighlighted -> RailItemColors(
        backgroundColor = CloudstreamTheme.extendedColors.hoverBackground,
        contentColor = CloudstreamTheme.extendedColors.textPrimary,
        fontWeight = FontWeight.SemiBold
    )
    else -> RailItemColors(
        backgroundColor = Color.Transparent,
        contentColor = CloudstreamTheme.extendedColors.textSecondary,
        fontWeight = FontWeight.Medium
    )
}

@Composable
fun NavigationRailItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val colors = resolveRailItemColors(
        isSelected = isSelected,
        isHighlighted = isHovered || isFocused
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .dpadFocusable(
                onClick = onClick,
                interactionSource = interactionSource,
                shape = RoundedCornerShape(12.dp),
                scaleOnFocus = 1.06f,
                focusedBorderWidth = 0.dp,
                focusedBorderColor = Color.Transparent
            )
            .background(colors.backgroundColor, RoundedCornerShape(12.dp))
            .padding(vertical = 10.dp, horizontal = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = colors.contentColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = colors.fontWeight,
            color = colors.contentColor
        )
    }
}

@Preview
@Composable
private fun AppBottomNavigationPreview() {
    CloudStreamTheme {
        AppBottomNavigation(
            currentScreen = Screen.Home,
            onNavigate = {}
        )
    }
}

@Preview
@Composable
private fun AppNavigationRailPreview() {
    CloudStreamTheme {
        AppNavigationRail(
            currentScreen = Screen.Home,
            canNavigateBack = false,
            onNavigateBack = {},
            onNavigate = {},
            modifier = Modifier
                .width(88.dp)
                .fillMaxHeight()
        )
    }
}
