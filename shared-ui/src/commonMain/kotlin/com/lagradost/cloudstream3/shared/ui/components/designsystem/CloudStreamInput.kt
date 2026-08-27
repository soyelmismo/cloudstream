package com.lagradost.cloudstream3.shared.ui.components.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextFieldColors
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import cloudstream.shared_ui.generated.resources.*
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors

/**
 * Standard default colors for CloudStream text fields.
 */
@Composable
fun defaultTextFieldColors(
    textColor: Color = CloudStreamColors.TextPrimary,
    disabledTextColor: Color = CloudStreamColors.TextMuted,
    backgroundColor: Color = CloudStreamColors.SurfaceVariant,
    cursorColor: Color = CloudStreamColors.Primary,
    errorCursorColor: Color = CloudStreamColors.Error,
    focusedBorderColor: Color = CloudStreamColors.Primary,
    unfocusedBorderColor: Color = MaterialTheme.colors.onSurface.copy(alpha = 0.15f),
    disabledBorderColor: Color = MaterialTheme.colors.onSurface.copy(alpha = 0.08f),
    errorBorderColor: Color = CloudStreamColors.Error,
    leadingIconColor: Color = CloudStreamColors.TextSecondary,
    disabledLeadingIconColor: Color = CloudStreamColors.TextMuted,
    errorLeadingIconColor: Color = CloudStreamColors.Error,
    trailingIconColor: Color = CloudStreamColors.TextSecondary,
    disabledTrailingIconColor: Color = CloudStreamColors.TextMuted,
    errorTrailingIconColor: Color = CloudStreamColors.Error,
    focusedLabelColor: Color = CloudStreamColors.Primary,
    unfocusedLabelColor: Color = CloudStreamColors.TextSecondary,
    disabledLabelColor: Color = CloudStreamColors.TextMuted,
    errorLabelColor: Color = CloudStreamColors.Error,
    placeholderColor: Color = CloudStreamColors.TextMuted,
    disabledPlaceholderColor: Color = CloudStreamColors.TextMuted.copy(alpha = 0.5f)
): TextFieldColors = TextFieldDefaults.outlinedTextFieldColors(
    textColor = textColor,
    disabledTextColor = disabledTextColor,
    backgroundColor = backgroundColor,
    cursorColor = cursorColor,
    errorCursorColor = errorCursorColor,
    focusedBorderColor = focusedBorderColor,
    unfocusedBorderColor = unfocusedBorderColor,
    disabledBorderColor = disabledBorderColor,
    errorBorderColor = errorBorderColor,
    leadingIconColor = leadingIconColor,
    disabledLeadingIconColor = disabledLeadingIconColor,
    errorLeadingIconColor = errorLeadingIconColor,
    trailingIconColor = trailingIconColor,
    disabledTrailingIconColor = disabledTrailingIconColor,
    errorTrailingIconColor = errorTrailingIconColor,
    focusedLabelColor = focusedLabelColor,
    unfocusedLabelColor = unfocusedLabelColor,
    disabledLabelColor = disabledLabelColor,
    errorLabelColor = errorLabelColor,
    placeholderColor = placeholderColor,
    disabledPlaceholderColor = disabledPlaceholderColor
)

/**
 * Standardized Input Text Field for CloudStream Multiplatform.
 *
 * Features:
 * - Unified design system color scheme (Primary focus border, SurfaceVariant background, semantic text colors).
 * - Standardized [RoundedCornerShape(12.dp)] shape.
 * - Built-in password visibility toggle (when [isPassword] = true).
 * - Built-in clear input action button (when [showClearButton] = true).
 * - Animated error and helper text integration.
 * - Multiplatform localization support (String & StringResource overloads).
 */
@Composable
fun CloudStreamTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    label: String? = null,
    labelRes: StringResource? = null,
    labelComposable: (@Composable () -> Unit)? = null,
    placeholder: String? = null,
    placeholderRes: StringResource? = null,
    placeholderComposable: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    leadingIconVector: ImageVector? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    isPassword: Boolean = false,
    showClearButton: Boolean = false,
    onClear: (() -> Unit)? = null,
    isError: Boolean = false,
    errorMessage: String? = null,
    errorRes: StringResource? = null,
    helperText: String? = null,
    helperTextRes: StringResource? = null,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    shape: Shape = RoundedCornerShape(12.dp),
    textStyle: TextStyle = MaterialTheme.typography.body1.copy(
        color = CloudStreamColors.TextPrimary,
        fontSize = 15.sp
    ),
    colors: TextFieldColors = defaultTextFieldColors()
) {
    val resolvedLabel = label ?: labelRes?.let { stringResource(it) }
    val resolvedPlaceholder = placeholder ?: placeholderRes?.let { stringResource(it) }
    val resolvedErrorMessage = errorMessage ?: errorRes?.let { stringResource(it) }
    val resolvedHelperText = helperText ?: helperTextRes?.let { stringResource(it) }
    val hasError = isError || resolvedErrorMessage != null

    var passwordVisible by remember { mutableStateOf(false) }

    val effectiveVisualTransformation = when {
        isPassword && !passwordVisible -> PasswordVisualTransformation()
        else -> visualTransformation
    }

    val effectiveTrailingIcon: (@Composable () -> Unit)? = when {
        trailingIcon != null -> trailingIcon
        isPassword -> {
            {
                IconButton(
                    onClick = { passwordVisible = !passwordVisible },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = stringResource(
                            if (passwordVisible) Res.string.hide_password else Res.string.show_password
                        ),
                        tint = CloudStreamColors.TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        showClearButton && value.isNotEmpty() -> {
            {
                IconButton(
                    onClick = {
                        onValueChange("")
                        onClear?.invoke()
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(Res.string.clear_input),
                        tint = CloudStreamColors.TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        else -> null
    }

    val effectiveLeadingIcon: (@Composable () -> Unit)? = when {
        leadingIcon != null -> leadingIcon
        leadingIconVector != null -> {
            {
                Icon(
                    imageVector = leadingIconVector,
                    contentDescription = null,
                    tint = CloudStreamColors.TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        else -> null
    }

    val effectiveLabel: (@Composable () -> Unit)? = when {
        labelComposable != null -> labelComposable
        resolvedLabel != null -> {
            {
                Text(
                    text = resolvedLabel,
                    style = MaterialTheme.typography.caption
                )
            }
        }
        else -> null
    }

    val effectivePlaceholder: (@Composable () -> Unit)? = when {
        placeholderComposable != null -> placeholderComposable
        resolvedPlaceholder != null -> {
            {
                Text(
                    text = resolvedPlaceholder,
                    style = MaterialTheme.typography.body2.copy(
                        color = CloudStreamColors.TextMuted,
                        fontSize = 14.sp
                    )
                )
            }
        }
        else -> null
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            readOnly = readOnly,
            textStyle = textStyle,
            label = effectiveLabel,
            placeholder = effectivePlaceholder,
            leadingIcon = effectiveLeadingIcon,
            trailingIcon = effectiveTrailingIcon,
            isError = hasError,
            visualTransformation = effectiveVisualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            maxLines = maxLines,
            interactionSource = interactionSource,
            shape = shape,
            colors = colors
        )

        // Helper / Error message area
        AnimatedVisibility(
            visible = hasError && resolvedErrorMessage != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            if (resolvedErrorMessage != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 12.dp, top = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = CloudStreamColors.Error,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = resolvedErrorMessage,
                        style = MaterialTheme.typography.caption.copy(
                            color = CloudStreamColors.Error,
                            fontSize = 12.sp
                        )
                    )
                }
            }
        }

        if (!hasError && resolvedHelperText != null) {
            Text(
                text = resolvedHelperText,
                style = MaterialTheme.typography.caption.copy(
                    color = CloudStreamColors.TextMuted,
                    fontSize = 12.sp
                ),
                modifier = Modifier.padding(start = 12.dp, top = 2.dp)
            )
        }
    }
}
