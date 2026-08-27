package com.lagradost.cloudstream3.shared.ui.components

import java.util.Locale

actual fun setPlatformLocale(languageCode: String) {
    val clean = languageCode.trim().lowercase()
    val locale = when {
        clean.contains("-") -> Locale(clean.substringBefore("-"), clean.substringAfter("-"))
        clean.contains("_") -> Locale(clean.substringBefore("_"), clean.substringAfter("_"))
        else -> Locale(clean)
    }
    Locale.setDefault(locale)
}
