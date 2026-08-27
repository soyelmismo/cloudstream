package com.lagradost.cloudstream3.shared.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

actual object NativeSystemTheme {
    private val _themeState = MutableStateFlow(NativeThemeState())
    actual val themeState: StateFlow<NativeThemeState> = _themeState.asStateFlow()

    actual fun getThemeState(): NativeThemeState {
        return _themeState.value
    }
}

@Composable
actual fun rememberNativeSystemTheme(): NativeThemeState {
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current
    val systemAccent = remember(context, isDark) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val resId = if (isDark) android.R.color.system_accent1_200 else android.R.color.system_accent1_600
            try {
                Color(context.getColor(resId))
            } catch (_: Throwable) {
                null
            }
        } else {
            null
        }
    }
    return remember(isDark, systemAccent) {
        NativeThemeState(
            accentColor = systemAccent,
            isDarkMode = isDark
        )
    }
}

