package com.lagradost.cloudstream3.shared.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.shared.ui.theme.AppColors
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors
import com.lagradost.cloudstream3.shared.ui.theme.CloudstreamTheme
import com.lagradost.cloudstream3.shared.ui.theme.rememberNativeSystemTheme
import com.lagradost.cloudstream3.shared.viewmodels.settings.AppTheme
import org.jetbrains.compose.resources.stringResource
import cloudstream.shared_ui.generated.resources.*

/**
 * Visual Theme Selector component showcasing color palettes, preview swatches,
 * hover elevation, and immediate selection feedback.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemeSelector(
    selectedTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit,
    modifier: Modifier = Modifier,
    isDarkMode: Boolean = true
) {
    val nativeTheme = rememberNativeSystemTheme()
    Column(modifier = modifier.fillMaxWidth()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            maxItemsInEachRow = 3
        ) {
            AppTheme.entries.forEach { theme ->
                ThemeCard(
                    theme = theme,
                    isSelected = theme == selectedTheme,
                    onSelect = { onThemeSelected(theme) },
                    isDarkMode = if (theme == AppTheme.SYSTEM) (nativeTheme.isDarkMode ?: isDarkMode) else isDarkMode,
                    systemAccentColor = if (theme == AppTheme.SYSTEM) nativeTheme.accentColor else null,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun ThemeCard(
    theme: AppTheme,
    isSelected: Boolean,
    onSelect: () -> Unit,
    isDarkMode: Boolean,
    systemAccentColor: Color? = null,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val extendedColors = remember(theme, isDarkMode, systemAccentColor) {
        AppColors.getExtendedColors(
            theme = theme,
            isDarkMode = isDarkMode,
            systemAccentColor = systemAccentColor
        )
    }

    val palette = extendedColors.previewPalette

    val borderColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colors.primary
            isHovered -> CloudstreamTheme.extendedColors.textSecondary.copy(alpha = 0.5f)
            else -> CloudstreamTheme.extendedColors.cardBorder
        },
        animationSpec = tween(150)
    )

    Card(
        modifier = modifier
            .width(130.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onSelect
            ),
        shape = RoundedCornerShape(12.dp),
        backgroundColor = CloudstreamTheme.extendedColors.cardBackground,
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
        elevation = if (isHovered || isSelected) 4.dp else 0.dp
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Theme Visual Swatches Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.getOrElse(1) { CloudStreamColors.Background })
            ) {
                // Surface Mock
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 8.dp, top = 8.dp)
                        .clip(RoundedCornerShape(topStart = 6.dp))
                        .background(palette.getOrElse(2) { CloudStreamColors.SurfaceVariant })
                ) {
                    // Mock Elements
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Accent Pill
                        Box(
                            modifier = Modifier
                                .size(width = 24.dp, height = 10.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(palette.getOrElse(0) { MaterialTheme.colors.primary })
                        )
                        // Secondary Pill
                        Box(
                            modifier = Modifier
                                .size(width = 16.dp, height = 10.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(palette.getOrElse(3) { CloudStreamColors.Secondary })
                        )
                    }
                }

                // Selected Checkmark Badge
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colors.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(Res.string.selected),
                            tint = MaterialTheme.colors.onPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Theme Display Name
            Text(
                text = stringResource(theme.displayNameRes),
                style = MaterialTheme.typography.body2.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 12.sp
                ),
                color = if (isSelected) MaterialTheme.colors.primary else CloudstreamTheme.extendedColors.textPrimary,
                maxLines = 1
            )
        }
    }
}
