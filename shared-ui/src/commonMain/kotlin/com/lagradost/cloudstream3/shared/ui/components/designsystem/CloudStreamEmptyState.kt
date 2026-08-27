package com.lagradost.cloudstream3.shared.ui.components.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cloudstream.shared_ui.generated.resources.*
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Standardized reusable Empty State & Error State component for the CloudStream Design System.
 *
 * Provides responsive centering, circular glowing badge for icons or custom art,
 * typography slots for semantic title & subtitle, action and secondary buttons,
 * and optional card container wrapping.
 */
@Composable
fun CloudStreamEmptyState(
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconPainter: Painter? = null,
    iconContent: (@Composable () -> Unit)? = null,
    iconTint: Color = CloudStreamColors.Primary,
    iconBackgroundColor: Color = iconTint.copy(alpha = 0.12f),
    iconBorderColor: Color? = iconTint.copy(alpha = 0.3f),
    iconSize: Dp = 40.dp,
    iconContainerSize: Dp = 80.dp,
    title: String? = null,
    titleRes: StringResource? = null,
    subtitle: String? = null,
    subtitleRes: StringResource? = null,
    actionText: String? = null,
    actionTextRes: StringResource? = null,
    actionIcon: ImageVector? = null,
    onActionClick: (() -> Unit)? = null,
    secondaryActionText: String? = null,
    secondaryActionTextRes: StringResource? = null,
    secondaryActionIcon: ImageVector? = null,
    onSecondaryActionClick: (() -> Unit)? = null,
    useCardContainer: Boolean = false,
    cardBackgroundColor: Color = CloudStreamColors.SurfaceVariant.copy(alpha = 0.45f),
    cardMaxWidthFraction: Float = 0.9f,
    actionButtonContent: (@Composable () -> Unit)? = null,
    customContent: (@Composable () -> Unit)? = null
) {
    val resolvedTitle = title ?: titleRes?.let { stringResource(it) }
    val resolvedSubtitle = subtitle ?: subtitleRes?.let { stringResource(it) }
    val resolvedActionText = actionText ?: actionTextRes?.let { stringResource(it) }
    val resolvedSecondaryActionText = secondaryActionText ?: secondaryActionTextRes?.let { stringResource(it) }

    val contentBody = @Composable {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(24.dp)
        ) {
            // Glowing circular container with icon
            Surface(
                shape = CircleShape,
                color = iconBackgroundColor,
                border = iconBorderColor?.let { BorderStroke(1.5.dp, it) },
                modifier = Modifier.size(iconContainerSize)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    if (iconContent != null) {
                        iconContent()
                    } else if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(iconSize)
                        )
                    } else if (iconPainter != null) {
                        Icon(
                            painter = iconPainter,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(iconSize)
                        )
                    }
                }
            }

            if (!resolvedTitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(18.dp))
                TitleText(
                    text = resolvedTitle,
                    textAlign = TextAlign.Center
                )
            }

            if (!resolvedSubtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                BodyMutedText(
                    text = resolvedSubtitle,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 380.dp)
                )
            }

            if (customContent != null) {
                Spacer(modifier = Modifier.height(16.dp))
                customContent()
            }

            if (actionButtonContent != null) {
                Spacer(modifier = Modifier.height(24.dp))
                actionButtonContent()
            } else if (onActionClick != null && !resolvedActionText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PrimaryButton(
                        text = resolvedActionText,
                        icon = actionIcon,
                        onClick = onActionClick,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                    )

                    if (onSecondaryActionClick != null && !resolvedSecondaryActionText.isNullOrBlank()) {
                        SecondaryButton(
                            text = resolvedSecondaryActionText,
                            icon = secondaryActionIcon,
                            onClick = onSecondaryActionClick,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                        )
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (useCardContainer) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = cardBackgroundColor,
                elevation = 2.dp,
                modifier = Modifier.fillMaxWidth(cardMaxWidthFraction)
            ) {
                contentBody()
            }
        } else {
            contentBody()
        }
    }
}
