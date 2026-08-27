package com.lagradost.cloudstream3.shared.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.StateFlow

/**
 * Encapsulates the detected native system visual preferences.
 *
 * @param accentColor System accent color detected from the OS desktop environment.
 * @param isDarkMode Whether the operating system is configured for dark mode.
 */
data class NativeThemeState(
    val accentColor: Color? = null,
    val isDarkMode: Boolean? = null
)

/**
 * Cross-platform native system theme and accent color detector.
 */
expect object NativeSystemTheme {
    val themeState: StateFlow<NativeThemeState>
    fun getThemeState(): NativeThemeState
}

/**
 * Composable helper to observe real-time native OS theme and accent color changes.
 */
@Composable
expect fun rememberNativeSystemTheme(): NativeThemeState
