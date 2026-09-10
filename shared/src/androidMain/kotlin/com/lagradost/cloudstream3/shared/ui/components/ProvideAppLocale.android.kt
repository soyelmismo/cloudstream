package com.lagradost.cloudstream3.shared.ui.components

import java.util.Locale

actual fun setPlatformLocale(languageCode: String) {
    val clean = languageCode.trim().replace('_', '-')
    val locale = Locale.forLanguageTag(clean)
    Locale.setDefault(locale)
}
