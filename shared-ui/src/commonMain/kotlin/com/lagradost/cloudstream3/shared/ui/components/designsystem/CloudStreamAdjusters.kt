package com.lagradost.cloudstream3.shared.ui.components.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cloudstream.shared_ui.generated.resources.*
import com.lagradost.cloudstream3.shared.ui.theme.CloudStreamColors
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

// =============================================================================
// SHARED STEP ADJUSTER LAYOUT
// =============================================================================

@Composable
private fun StepAdjusterLayout(
    title: String? = null,
    subtitle: String? = null,
    fullReadout: String,
    isDefaultValue: Boolean,
    explanation: String? = null,
    minusButtonText: String,
    canStepMinus: Boolean,
    onStepMinus: () -> Unit,
    plusButtonText: String,
    canStepPlus: Boolean,
    onStepPlus: () -> Unit,
    showReset: Boolean,
    resetButtonText: String,
    canReset: Boolean,
    onReset: () -> Unit,
    showManualInput: Boolean,
    manualInputValue: String,
    onManualInputValueChange: (String) -> Unit,
    manualInputLabel: String?,
    manualInputPlaceholder: String,
    keyboardType: KeyboardType,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Optional Header Title & Subtitle
        if (title != null || subtitle != null) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (title != null) {
                    TitleText(
                        text = title,
                        textAlign = TextAlign.Center,
                        fontSize = 16.sp
                    )
                }
                if (subtitle != null) {
                    BodyMutedText(
                        text = subtitle,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.caption
                    )
                }
            }
        }

        // Large Formatted Value Readout
        Text(
            text = fullReadout,
            style = MaterialTheme.typography.h4.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp
            ),
            color = if (!isDefaultValue) CloudStreamColors.Primary else CloudStreamColors.TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        // Optional explanation text
        if (explanation != null) {
            BodyMutedText(
                text = explanation,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.caption.copy(fontSize = 12.sp),
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }

        // Step Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SecondaryButton(
                text = minusButtonText,
                enabled = enabled && canStepMinus,
                onClick = onStepMinus,
                modifier = Modifier.weight(1f)
            )

            SecondaryButton(
                text = plusButtonText,
                enabled = enabled && canStepPlus,
                onClick = onStepPlus,
                modifier = Modifier.weight(1f)
            )

            if (showReset) {
                SecondaryButton(
                    text = resetButtonText,
                    enabled = enabled && canReset,
                    onClick = onReset,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Optional Direct Manual Numeric Input
        AnimatedVisibility(
            visible = showManualInput,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                CloudStreamTextField(
                    value = manualInputValue,
                    onValueChange = onManualInputValueChange,
                    label = manualInputLabel,
                    placeholder = manualInputPlaceholder,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// =============================================================================
// 1. NUMBER STEP ADJUSTER (LONG)
// =============================================================================

/**
 * Standardized Number Step Adjuster component adhering to the CloudStream design system.
 *
 * Features:
 * - Large formatted value readout with unit (e.g. `+150 ms`, `0 ms`, `-50 ms`).
 * - Step buttons row ([SecondaryButton] / [PrimaryButton]) for adjusting by -step / +step and Reset.
 * - Optional direct manual numeric input ([CloudStreamTextField]).
 * - Optional title, subtitle, and helper explanation text.
 * - Zero Magic Strings and Zero Hardcoded Colors compliance.
 */
@Composable
fun NumberStepAdjuster(
    value: Long,
    onValueChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    step: Long = 50L,
    min: Long = Long.MIN_VALUE,
    max: Long = Long.MAX_VALUE,
    defaultValue: Long = 0L,
    resetValue: Long = defaultValue,
    title: String? = null,
    titleRes: StringResource? = null,
    subtitle: String? = null,
    subtitleRes: StringResource? = null,
    explanation: String? = null,
    explanationRes: StringResource? = null,
    unit: String? = null,
    unitRes: StringResource? = null,
    label: String? = null,
    labelRes: StringResource? = null,
    showSign: Boolean = true,
    formatter: ((Long) -> String)? = null,
    showReset: Boolean = true,
    showResetButton: Boolean = showReset,
    resetButtonText: String? = null,
    resetButtonTextRes: StringResource? = Res.string.reset,
    stepMinusText: String? = null,
    stepMinusRes: StringResource? = null,
    stepMinusTextRes: StringResource? = stepMinusRes,
    stepPlusText: String? = null,
    stepPlusRes: StringResource? = null,
    stepPlusTextRes: StringResource? = stepPlusRes,
    showManualInput: Boolean = true,
    manualInputLabel: String? = label,
    manualInputLabelRes: StringResource? = labelRes,
    manualInputPlaceholder: String = "0",
    enabled: Boolean = true
) {
    val resolvedTitle = title ?: titleRes?.let { stringResource(it) }
    val resolvedSubtitle = subtitle ?: subtitleRes?.let { stringResource(it) }
    val resolvedExplanation = explanation ?: explanationRes?.let { stringResource(it) }
    val resolvedUnit = unit ?: unitRes?.let { stringResource(it) }
    val resolvedResetText = resetButtonText ?: resetButtonTextRes?.let { stringResource(it) } ?: stringResource(Res.string.reset)
    val resolvedManualLabel = manualInputLabel ?: manualInputLabelRes?.let { stringResource(it) } ?: label ?: labelRes?.let { stringResource(it) } ?: stringResource(Res.string.manual_delay_input)

    val formattedNumber = when {
        formatter != null -> formatter(value)
        showSign && value > 0L -> "+$value"
        else -> "$value"
    }

    val fullReadout = if (!resolvedUnit.isNullOrBlank()) {
        "$formattedNumber $resolvedUnit"
    } else {
        formattedNumber
    }

    val defaultMinusText = if (!resolvedUnit.isNullOrBlank()) "-$step $resolvedUnit" else "-$step"
    val defaultPlusText = if (!resolvedUnit.isNullOrBlank()) "+$step $resolvedUnit" else "+$step"

    val resolvedMinusText = stepMinusText ?: stepMinusTextRes?.let { stringResource(it) } ?: stepMinusRes?.let { stringResource(it) } ?: defaultMinusText
    val resolvedPlusText = stepPlusText ?: stepPlusTextRes?.let { stringResource(it) } ?: stepPlusRes?.let { stringResource(it) } ?: defaultPlusText

    val effectiveDefaultValue = if (resetValue != 0L && defaultValue == 0L) resetValue else defaultValue
    val effectiveShowReset = showReset && showResetButton

    var manualInput by remember(value) { mutableStateOf(value.toString()) }

    StepAdjusterLayout(
        title = resolvedTitle,
        subtitle = resolvedSubtitle,
        fullReadout = fullReadout,
        isDefaultValue = value == effectiveDefaultValue,
        explanation = resolvedExplanation,
        minusButtonText = resolvedMinusText,
        canStepMinus = value > min,
        onStepMinus = {
            val next = (value - step).coerceAtLeast(min)
            onValueChange(next)
            manualInput = next.toString()
        },
        plusButtonText = resolvedPlusText,
        canStepPlus = value < max,
        onStepPlus = {
            val next = (value + step).coerceAtMost(max)
            onValueChange(next)
            manualInput = next.toString()
        },
        showReset = effectiveShowReset,
        resetButtonText = resolvedResetText,
        canReset = value != effectiveDefaultValue,
        onReset = {
            onValueChange(effectiveDefaultValue)
            manualInput = effectiveDefaultValue.toString()
        },
        showManualInput = showManualInput,
        manualInputValue = manualInput,
        onManualInputValueChange = { input ->
            manualInput = input
            val parsed = input.trim().toLongOrNull()
            if (parsed != null && parsed in min..max) {
                onValueChange(parsed)
            }
        },
        manualInputLabel = resolvedManualLabel,
        manualInputPlaceholder = manualInputPlaceholder,
        keyboardType = KeyboardType.Number,
        enabled = enabled,
        modifier = modifier
    )
}

// =============================================================================
// 2. NUMBER STEP ADJUSTER (INT OVERLOAD)
// =============================================================================

/**
 * Integer overload of [NumberStepAdjuster].
 */
@Composable
fun NumberStepAdjuster(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    step: Int = 1,
    min: Int = Int.MIN_VALUE,
    max: Int = Int.MAX_VALUE,
    defaultValue: Int = 0,
    resetValue: Int = defaultValue,
    title: String? = null,
    titleRes: StringResource? = null,
    subtitle: String? = null,
    subtitleRes: StringResource? = null,
    explanation: String? = null,
    explanationRes: StringResource? = null,
    unit: String? = null,
    unitRes: StringResource? = null,
    label: String? = null,
    labelRes: StringResource? = null,
    showSign: Boolean = false,
    formatter: ((Int) -> String)? = null,
    showReset: Boolean = true,
    showResetButton: Boolean = showReset,
    resetButtonText: String? = null,
    resetButtonTextRes: StringResource? = Res.string.reset,
    stepMinusText: String? = null,
    stepMinusRes: StringResource? = null,
    stepMinusTextRes: StringResource? = stepMinusRes,
    stepPlusText: String? = null,
    stepPlusRes: StringResource? = null,
    stepPlusTextRes: StringResource? = stepPlusRes,
    showManualInput: Boolean = true,
    manualInputLabel: String? = label,
    manualInputLabelRes: StringResource? = labelRes,
    manualInputPlaceholder: String = "0",
    enabled: Boolean = true
) {
    NumberStepAdjuster(
        value = value.toLong(),
        onValueChange = { onValueChange(it.toInt()) },
        modifier = modifier,
        step = step.toLong(),
        min = min.toLong(),
        max = max.toLong(),
        defaultValue = defaultValue.toLong(),
        resetValue = resetValue.toLong(),
        title = title,
        titleRes = titleRes,
        subtitle = subtitle,
        subtitleRes = subtitleRes,
        explanation = explanation,
        explanationRes = explanationRes,
        unit = unit,
        unitRes = unitRes,
        label = label,
        labelRes = labelRes,
        showSign = showSign,
        formatter = formatter?.let { fmt -> { fmt(it.toInt()) } },
        showReset = showReset,
        showResetButton = showResetButton,
        resetButtonText = resetButtonText,
        resetButtonTextRes = resetButtonTextRes,
        stepMinusText = stepMinusText,
        stepMinusRes = stepMinusRes,
        stepMinusTextRes = stepMinusTextRes,
        stepPlusText = stepPlusText,
        stepPlusRes = stepPlusRes,
        stepPlusTextRes = stepPlusTextRes,
        showManualInput = showManualInput,
        manualInputLabel = manualInputLabel,
        manualInputLabelRes = manualInputLabelRes,
        manualInputPlaceholder = manualInputPlaceholder,
        enabled = enabled
    )
}
