package com.lagradost.cloudstream3.shared.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatColorReset
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cloudstream.shared_ui.generated.resources.*
import com.lagradost.cloudstream3.shared.ui.components.designsystem.SectionHeader
import com.lagradost.cloudstream3.shared.ui.components.designsystem.SecondaryButton
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors
import com.lagradost.cloudstream3.shared.viewmodels.settings.SubtitleEdgeType
import com.lagradost.cloudstream3.shared.viewmodels.settings.SubtitleStyle
import org.jetbrains.compose.resources.stringResource

/**
 * Converts a raw-ARGB `Long` (e.g. `0xFFFFFFFFL`, matching `SubtitleStyle` defaults)
 * into a Compose `Color` via the `Color(Int)` constructor, which correctly encodes
 * the value as sRGB. Calling `Color(Long)` directly would shift the full 64 bits
 * left and produce an out-of-bounds colorSpace id.
 */
private fun colorFromArgbLong(argb: Long): Color = Color((argb and 0xFFFFFFFFL).toInt())

/**
 * Converts a Compose `Color` back into a raw-ARGB `Long`, masking the sign-extended
 * `toArgb()` Int so the stored value stays a clean unsigned 32-bit ARGB.
 */
private fun composeColorToArgbLong(color: Color): Long = color.toArgb().toLong() and 0xFFFFFFFFL

/**
 * Real-time Subtitle Customizer adhering to CloudStream Design System.
 * Includes live video scene preview box, typography controls, border/shadow styling,
 * caption cleanup settings, and preset reset actions.
 */
@Composable
fun SubtitleCustomizer(
    style: SubtitleStyle,
    onStyleChanged: (SubtitleStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Real-Time Live Preview Scene
        SubtitleLivePreviewBox(style = style)

        // Subtitle Text Appearance Group
        SettingsCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionHeader(
                    title = stringResource(Res.string.sectionPlayerSubtitles),
                    icon = Icons.Default.FormatSize,
                    modifier = Modifier.weight(1f, fill = false)
                )

                SecondaryButton(
                    text = stringResource(Res.string.reset),
                    icon = Icons.Default.RestartAlt,
                    onClick = { onStyleChanged(SubtitleStyle()) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Font Size Slider
            SettingsSliderItem(
                title = stringResource(Res.string.fontSize),
                value = style.fontSize,
                valueRange = 12f..48f,
                onValueChange = { onStyleChanged(style.copy(fontSize = it)) },
                valueDisplay = "${style.fontSize.toInt()} sp"
            )

            Divider(color = CloudStreamColors.Divider, modifier = Modifier.padding(vertical = 8.dp))

            // Text Color Swatches
            ColorPickerRow(
                title = stringResource(Res.string.textColor),
                selectedColor = colorFromArgbLong(style.textColor),
                colors = CloudStreamColors.SubtitleTextPresets,
                onColorSelected = { color ->
                    onStyleChanged(style.copy(textColor = composeColorToArgbLong(color)))
                }
            )

            Divider(color = CloudStreamColors.Divider, modifier = Modifier.padding(vertical = 8.dp))

            // Typography Toggles (Bold, Italic, Uppercase)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StyleToggleButton(
                    label = stringResource(Res.string.bold),
                    isActive = style.bold,
                    onClick = { onStyleChanged(style.copy(bold = !style.bold)) },
                    modifier = Modifier.weight(1f)
                )

                StyleToggleButton(
                    label = stringResource(Res.string.italic),
                    isActive = style.italic,
                    onClick = { onStyleChanged(style.copy(italic = !style.italic)) },
                    modifier = Modifier.weight(1f)
                )

                StyleToggleButton(
                    label = stringResource(Res.string.allCaps),
                    isActive = style.uppercase,
                    onClick = { onStyleChanged(style.copy(uppercase = !style.uppercase)) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Subtitle Border, Shadows & Background Group
        SettingsCard {
            SectionHeader(
                title = stringResource(Res.string.edgeEffect),
                icon = Icons.Default.Subtitles
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Edge / Border Type
            Text(
                text = stringResource(Res.string.edgeEffect),
                style = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.Medium),
                color = CloudStreamColors.TextPrimary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )

            EdgeTypeSelector(
                selectedType = style.edgeType,
                onTypeSelected = { onStyleChanged(style.copy(edgeType = it)) }
            )

            Divider(color = CloudStreamColors.Divider, modifier = Modifier.padding(vertical = 8.dp))

            // Subtitle Text Outline Width Slider (0dp to 4dp, default 1dp)
            SettingsSliderItem(
                title = stringResource(Res.string.subs_outline_width),
                value = style.outlineWidth,
                valueRange = 0f..4f,
                steps = 7,
                onValueChange = { onStyleChanged(style.copy(outlineWidth = it)) },
                valueDisplay = "${(style.outlineWidth * 10).toInt() / 10f} dp"
            )

            Divider(color = CloudStreamColors.Divider, modifier = Modifier.padding(vertical = 8.dp))

            // Edge Color Swatches
            ColorPickerRow(
                title = stringResource(Res.string.edgeColor),
                selectedColor = colorFromArgbLong(style.edgeColor),
                colors = CloudStreamColors.SubtitleEdgePresets,
                onColorSelected = { color ->
                    onStyleChanged(style.copy(edgeColor = composeColorToArgbLong(color)))
                }
            )

            Divider(color = CloudStreamColors.Divider, modifier = Modifier.padding(vertical = 8.dp))

            // Background Box Color
            ColorPickerRow(
                title = stringResource(Res.string.backgroundColor),
                selectedColor = colorFromArgbLong(style.backgroundColor),
                colors = CloudStreamColors.SubtitleBackgroundPresets,
                onColorSelected = { color ->
                    val alphaPercent = if (color.alpha > 0f) color.alpha * 100f else 0f
                    onStyleChanged(style.copy(backgroundColor = composeColorToArgbLong(color), backgroundOpacity = alphaPercent))
                }
            )

            Divider(color = CloudStreamColors.Divider, modifier = Modifier.padding(vertical = 8.dp))

            // Subtitle Background Opacity / Alpha Slider (0% to 100%)
            SettingsSliderItem(
                title = stringResource(Res.string.subs_background_opacity),
                value = style.backgroundOpacity,
                valueRange = 0f..100f,
                steps = 19,
                onValueChange = { onStyleChanged(style.copy(backgroundOpacity = it)) },
                valueDisplay = "${style.backgroundOpacity.toInt()}%"
            )

            Divider(color = CloudStreamColors.Divider, modifier = Modifier.padding(vertical = 8.dp))

            // Subtitle Elevation Slider
            SettingsSliderItem(
                title = stringResource(Res.string.subs_bottom_margin_elevation),
                value = style.elevation.toFloat(),
                valueRange = 0f..60f,
                onValueChange = { onStyleChanged(style.copy(elevation = it.toInt())) },
                valueDisplay = "${style.elevation} dp"
            )
        }

        // Caption Processing & Auto Features
        SettingsCard {
            SectionHeader(
                title = stringResource(Res.string.subs_behavior_cleaners_title)
            )

            Spacer(modifier = Modifier.height(12.dp))

            SettingsSwitchItem(
                title = stringResource(Res.string.subs_auto_select_title),
                subtitle = stringResource(Res.string.subs_auto_select_desc),
                checked = style.autoSelectSubtitles,
                onCheckedChange = { onStyleChanged(style.copy(autoSelectSubtitles = it)) }
            )

            Divider(color = CloudStreamColors.Divider)

            SettingsSwitchItem(
                title = stringResource(Res.string.subs_auto_download_title),
                subtitle = stringResource(Res.string.subs_auto_download_desc),
                checked = style.autoDownloadSubtitles,
                onCheckedChange = { onStyleChanged(style.copy(autoDownloadSubtitles = it)) }
            )

            Divider(color = CloudStreamColors.Divider)

            SettingsSwitchItem(
                title = stringResource(Res.string.subs_remove_captions_title),
                subtitle = stringResource(Res.string.subs_remove_captions_desc),
                checked = style.removeCaptions,
                onCheckedChange = { onStyleChanged(style.copy(removeCaptions = it)) }
            )

            Divider(color = CloudStreamColors.Divider)

            SettingsSwitchItem(
                title = stringResource(Res.string.subs_remove_bloat_title),
                subtitle = stringResource(Res.string.subs_remove_bloat_desc),
                checked = style.removeBloat,
                onCheckedChange = { onStyleChanged(style.copy(removeBloat = it)) }
            )
        }
    }
}

/**
 * Visual Mock Video Player box displaying live subtitle preview.
 */
@Composable
fun SubtitleLivePreviewBox(
    style: SubtitleStyle,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(170.dp),
        shape = RoundedCornerShape(16.dp),
        backgroundColor = CloudStreamColors.SurfaceVariant,
        border = BorderStroke(1.5.dp, CloudStreamColors.Primary.copy(alpha = 0.5f)),
        elevation = 4.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            CloudStreamColors.SurfaceVariant,
                            CloudStreamColors.SurfaceElevated,
                            CloudStreamColors.Background
                        )
                    )
                )
        ) {
            // Video Scene Mock Elements
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                // Top Info Tag
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = CloudStreamColors.Background.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = stringResource(Res.string.subs_live_preview_tag),
                        style = MaterialTheme.typography.caption.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        ),
                        color = CloudStreamColors.TextSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // Subtitle Line rendered in real-time
                val rawText = stringResource(Res.string.subtitlePreviewText)
                val displayText = if (style.uppercase) rawText.uppercase() else rawText

                val textColor = colorFromArgbLong(style.textColor)
                val rawBgColor = colorFromArgbLong(style.backgroundColor)
                val edgeColor = colorFromArgbLong(style.edgeColor)

                val bgAlpha = if (style.backgroundOpacity > 0f) {
                    (style.backgroundOpacity / 100f).coerceIn(0f, 1f)
                } else {
                    rawBgColor.alpha
                }
                val bgColor = if (rawBgColor.alpha == 0f && style.backgroundOpacity > 0f) {
                    Color.Black.copy(alpha = bgAlpha)
                } else {
                    rawBgColor.copy(alpha = bgAlpha)
                }

                val outlineWidthPx = style.outlineWidth.coerceAtLeast(0f)
                val textShadow = when (style.edgeType) {
                    SubtitleEdgeType.NONE -> null
                    SubtitleEdgeType.DROP_SHADOW -> Shadow(
                        color = edgeColor,
                        offset = Offset(3f * outlineWidthPx.coerceAtLeast(0.5f), 3f * outlineWidthPx.coerceAtLeast(0.5f)),
                        blurRadius = 4f * outlineWidthPx.coerceAtLeast(0.5f)
                    )
                    SubtitleEdgeType.OUTLINE -> if (outlineWidthPx > 0f) {
                        Shadow(
                            color = edgeColor,
                            offset = Offset(0f, 0f),
                            blurRadius = outlineWidthPx * 4f
                        )
                    } else null
                    SubtitleEdgeType.RAISED -> Shadow(
                        color = edgeColor,
                        offset = Offset(-2f * outlineWidthPx.coerceAtLeast(0.5f), -2f * outlineWidthPx.coerceAtLeast(0.5f)),
                        blurRadius = 2f * outlineWidthPx.coerceAtLeast(0.5f)
                    )
                    SubtitleEdgeType.DEPRESSED -> Shadow(
                        color = edgeColor,
                        offset = Offset(2f * outlineWidthPx.coerceAtLeast(0.5f), 2f * outlineWidthPx.coerceAtLeast(0.5f)),
                        blurRadius = 2f * outlineWidthPx.coerceAtLeast(0.5f)
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = (style.elevation / 2).dp.coerceIn(4.dp, 40.dp))
                        .clip(RoundedCornerShape(4.dp))
                        .background(bgColor)
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = displayText,
                        textAlign = TextAlign.Center,
                        style = TextStyle(
                            color = textColor,
                            fontSize = (style.fontSize * 0.85f).sp,
                            fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
                            fontStyle = if (style.italic) FontStyle.Italic else FontStyle.Normal,
                            shadow = textShadow
                        )
                    )
                }
            }
        }
    }
}

/**
 * Color picker row with circle swatches adhering to CloudStream colors.
 */
@Composable
fun ColorPickerRow(
    title: String,
    selectedColor: Color,
    colors: List<Color>,
    onColorSelected: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.Medium),
            color = CloudStreamColors.TextPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            colors.forEach { color ->
                val isSelected = selectedColor.value == color.value
                val interactionSource = remember { MutableInteractionSource() }

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (color.alpha == 0f) CloudStreamColors.SurfaceElevated else color)
                        .border(
                            width = if (isSelected) 2.5.dp else 1.dp,
                            color = if (isSelected) CloudStreamColors.Primary else CloudStreamColors.Divider,
                            shape = CircleShape
                        )
                        .clickable(interactionSource = interactionSource, indication = null) {
                            onColorSelected(color)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (color.alpha == 0f) {
                        Icon(
                            imageVector = Icons.Default.FormatColorReset,
                            contentDescription = stringResource(Res.string.filter_clear),
                            tint = CloudStreamColors.TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    } else if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(Res.string.selected),
                            tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EdgeTypeSelector(
    selectedType: SubtitleEdgeType,
    onTypeSelected: (SubtitleEdgeType) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SubtitleEdgeType.entries.forEach { type ->
            val isSelected = type == selectedType
            val interactionSource = remember { MutableInteractionSource() }
            val isHovered by interactionSource.collectIsHoveredAsState()

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = when {
                    isSelected -> CloudStreamColors.Primary
                    isHovered -> CloudStreamColors.SurfaceElevated
                    else -> CloudStreamColors.SurfaceVariant
                },
                border = BorderStroke(
                    1.dp,
                    if (isSelected) CloudStreamColors.Primary else CloudStreamColors.Divider
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(interactionSource = interactionSource, indication = null) {
                        onTypeSelected(type)
                    }
            ) {
                val label = when (type) {
                    SubtitleEdgeType.NONE -> stringResource(Res.string.edgeNone)
                    SubtitleEdgeType.DROP_SHADOW -> stringResource(Res.string.edgeDropShadow)
                    SubtitleEdgeType.OUTLINE -> stringResource(Res.string.edgeOutline)
                    SubtitleEdgeType.RAISED -> stringResource(Res.string.edgeRaised)
                    SubtitleEdgeType.DEPRESSED -> stringResource(Res.string.edgeDepressed)
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.body2.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (isSelected) MaterialTheme.colors.onPrimary else CloudStreamColors.TextPrimary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
fun StyleToggleButton(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = when {
            isActive -> CloudStreamColors.Primary.copy(alpha = 0.2f)
            isHovered -> CloudStreamColors.SurfaceElevated
            else -> CloudStreamColors.SurfaceVariant
        },
        border = BorderStroke(
            1.dp,
            if (isActive) CloudStreamColors.Primary else CloudStreamColors.Divider
        ),
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    ) {
        Box(
            modifier = Modifier.padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.button.copy(
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 13.sp
                ),
                color = if (isActive) CloudStreamColors.Primary else CloudStreamColors.TextPrimary
            )
        }
    }
}
