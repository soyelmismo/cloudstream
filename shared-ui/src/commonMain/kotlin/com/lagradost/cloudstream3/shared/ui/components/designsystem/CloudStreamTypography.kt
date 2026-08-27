package com.lagradost.cloudstream3.shared.ui.components.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors

// =============================================================================
// 1. TITLE TEXT
// =============================================================================

/**
 * Semantic Title typography for headings, dialog titles, and card headers.
 */
@Composable
fun TitleText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = CloudStreamColors.TextPrimary,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    fontWeight: FontWeight = FontWeight.Bold,
    fontSize: TextUnit = 18.sp,
    lineHeight: TextUnit = TextUnit.Unspecified
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow,
        style = MaterialTheme.typography.h6.copy(
            fontWeight = fontWeight,
            fontSize = fontSize,
            lineHeight = lineHeight
        )
    )
}

@Composable
fun TitleText(
    textRes: StringResource,
    modifier: Modifier = Modifier,
    color: Color = CloudStreamColors.TextPrimary,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    fontWeight: FontWeight = FontWeight.Bold,
    fontSize: TextUnit = 18.sp,
    lineHeight: TextUnit = TextUnit.Unspecified
) = TitleText(
    text = stringResource(textRes),
    modifier = modifier,
    color = color,
    textAlign = textAlign,
    maxLines = maxLines,
    overflow = overflow,
    fontWeight = fontWeight,
    fontSize = fontSize,
    lineHeight = lineHeight
)

// =============================================================================
// 2. SUBTITLE TEXT
// =============================================================================

/**
 * Semantic Subtitle typography for section subheadings, descriptions, and secondary card lines.
 */
@Composable
fun SubtitleText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = CloudStreamColors.TextSecondary,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    fontWeight: FontWeight = FontWeight.Medium,
    fontSize: TextUnit = 14.sp,
    lineHeight: TextUnit = TextUnit.Unspecified
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow,
        style = MaterialTheme.typography.subtitle1.copy(
            fontWeight = fontWeight,
            fontSize = fontSize,
            lineHeight = lineHeight
        )
    )
}

@Composable
fun SubtitleText(
    textRes: StringResource,
    modifier: Modifier = Modifier,
    color: Color = CloudStreamColors.TextSecondary,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    fontWeight: FontWeight = FontWeight.Medium,
    fontSize: TextUnit = 14.sp,
    lineHeight: TextUnit = TextUnit.Unspecified
) = SubtitleText(
    text = stringResource(textRes),
    modifier = modifier,
    color = color,
    textAlign = textAlign,
    maxLines = maxLines,
    overflow = overflow,
    fontWeight = fontWeight,
    fontSize = fontSize,
    lineHeight = lineHeight
)

// =============================================================================
// 3. BODY TEXT
// =============================================================================

/**
 * Semantic Body typography for general UI descriptions, paragraphs, and primary content.
 */
@Composable
fun BodyText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = CloudStreamColors.TextPrimary,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    fontWeight: FontWeight = FontWeight.Normal,
    fontSize: TextUnit = 15.sp,
    lineHeight: TextUnit = 20.sp
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow,
        style = MaterialTheme.typography.body1.copy(
            fontWeight = fontWeight,
            fontSize = fontSize,
            lineHeight = lineHeight
        )
    )
}

@Composable
fun BodyText(
    textRes: StringResource,
    modifier: Modifier = Modifier,
    color: Color = CloudStreamColors.TextPrimary,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    fontWeight: FontWeight = FontWeight.Normal,
    fontSize: TextUnit = 15.sp,
    lineHeight: TextUnit = 20.sp
) = BodyText(
    text = stringResource(textRes),
    modifier = modifier,
    color = color,
    textAlign = textAlign,
    maxLines = maxLines,
    overflow = overflow,
    fontWeight = fontWeight,
    fontSize = fontSize,
    lineHeight = lineHeight
)

// =============================================================================
// 4. BODY MUTED TEXT
// =============================================================================

/**
 * Semantic Muted Body typography for low-emphasis labels, timestamps, and secondary info.
 */
@Composable
fun BodyMutedText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = CloudStreamColors.TextMuted,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    fontWeight: FontWeight = FontWeight.Normal,
    fontSize: TextUnit = 13.sp,
    lineHeight: TextUnit = 18.sp,
    style: TextStyle? = null
) {
    val baseStyle = style ?: MaterialTheme.typography.body2
    val effectiveStyle = baseStyle.copy(
        fontWeight = fontWeight,
        fontSize = if (fontSize != TextUnit.Unspecified && fontSize != 13.sp) fontSize else baseStyle.fontSize,
        lineHeight = if (lineHeight != TextUnit.Unspecified && lineHeight != 18.sp) lineHeight else baseStyle.lineHeight
    )
    Text(
        text = text,
        modifier = modifier,
        color = color,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow,
        style = effectiveStyle
    )
}

@Composable
fun BodyMutedText(
    textRes: StringResource,
    modifier: Modifier = Modifier,
    color: Color = CloudStreamColors.TextMuted,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    fontWeight: FontWeight = FontWeight.Normal,
    fontSize: TextUnit = 13.sp,
    lineHeight: TextUnit = 18.sp,
    style: TextStyle? = null
) = BodyMutedText(
    text = stringResource(textRes),
    modifier = modifier,
    color = color,
    textAlign = textAlign,
    maxLines = maxLines,
    overflow = overflow,
    fontWeight = fontWeight,
    fontSize = fontSize,
    lineHeight = lineHeight,
    style = style
)

// =============================================================================
// 5. SECTION HEADER
// =============================================================================

/**
 * Standardized Section Header with optional accent bar, icon, subtitle/description, and trailing action button slot.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    description: String? = subtitle,
    icon: ImageVector? = null,
    iconTint: Color = CloudStreamColors.Primary,
    showAccentBar: Boolean = false,
    action: (@Composable () -> Unit)? = null
) {
    val effectiveSubtitle = subtitle ?: description

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (showAccentBar) {
                Box(
                    modifier = Modifier
                        .size(width = 4.dp, height = 18.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(CloudStreamColors.Primary)
                )
            }

            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(verticalArrangement = Arrangement.Center) {
                TitleText(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (effectiveSubtitle != null) {
                    SubtitleText(
                        text = effectiveSubtitle,
                        fontSize = 12.sp,
                        color = CloudStreamColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (action != null) {
            action()
        }
    }
}

@Composable
fun SectionHeader(
    titleRes: StringResource,
    modifier: Modifier = Modifier,
    subtitleRes: StringResource? = null,
    descriptionRes: StringResource? = subtitleRes,
    icon: ImageVector? = null,
    iconTint: Color = CloudStreamColors.Primary,
    showAccentBar: Boolean = false,
    action: (@Composable () -> Unit)? = null
) = SectionHeader(
    title = stringResource(titleRes),
    modifier = modifier,
    subtitle = (subtitleRes ?: descriptionRes)?.let { stringResource(it) },
    icon = icon,
    iconTint = iconTint,
    showAccentBar = showAccentBar,
    action = action
)
