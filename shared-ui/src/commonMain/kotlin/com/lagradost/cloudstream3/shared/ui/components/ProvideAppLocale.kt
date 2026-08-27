package com.lagradost.cloudstream3.shared.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

expect fun setPlatformLocale(languageCode: String)

/**
 * Dynamic Locale provider that switches system locale and triggers
 * immediate recomposition across all stringResource calls.
 */
@Composable
fun ProvideAppLocale(
    languageCode: String,
    content: @Composable () -> Unit
) {
    remember(languageCode) {
        if (languageCode.isNotBlank()) {
            setPlatformLocale(languageCode)
        }
    }

    LaunchedEffect(languageCode) {
        if (languageCode.isNotBlank()) {
            setPlatformLocale(languageCode)
        }
    }

    content()
}
