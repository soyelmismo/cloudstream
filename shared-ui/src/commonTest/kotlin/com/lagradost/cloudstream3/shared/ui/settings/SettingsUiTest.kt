package com.lagradost.cloudstream3.shared.ui.settings

import com.lagradost.cloudstream3.shared.ui.theme.AppColors
import com.lagradost.cloudstream3.shared.viewmodels.settings.AppTheme
import com.lagradost.cloudstream3.shared.viewmodels.settings.DohProvider
import com.lagradost.cloudstream3.shared.viewmodels.settings.SubtitleEdgeType
import com.lagradost.cloudstream3.shared.viewmodels.settings.SubtitleStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SettingsUiTest {

    @Test
    fun testAllThemePalettesDefined() {
        AppTheme.entries.forEach { theme ->
            val materialColors = AppColors.getMaterialColors(theme, isDarkMode = true)
            val extendedColors = AppColors.getExtendedColors(theme, isDarkMode = true)

            assertNotNull(materialColors.primary)
            assertNotNull(materialColors.background)
            assertNotNull(materialColors.surface)
            assertNotNull(extendedColors.cardBackground)
            assertNotNull(extendedColors.textPrimary)
            assertTrue(extendedColors.previewPalette.size >= 4, "Theme $theme should have at least 4 preview swatches")
        }
    }

    @Test
    fun testSubtitleStyleDefaults() {
        val defaultStyle = SubtitleStyle()
        assertEquals(20f, defaultStyle.fontSize)
        assertEquals(SubtitleEdgeType.OUTLINE, defaultStyle.edgeType)
        assertEquals(1f, defaultStyle.outlineWidth)
        assertEquals(0f, defaultStyle.backgroundOpacity)
        assertTrue(defaultStyle.removeBloat)
        assertTrue(defaultStyle.autoSelectSubtitles)
    }

    @Test
    fun testDohProvidersConfigured() {
        val cloudflare = DohProvider.CLOUDFLARE
        assertEquals("Cloudflare", cloudflare.displayName)
        assertEquals("https://cloudflare-dns.com/dns-query", cloudflare.url)
        assertTrue(cloudflare.ips.contains("1.1.1.1"))

        val google = DohProvider.GOOGLE
        assertEquals("https://dns.google/dns-query", google.url)
        assertTrue(google.ips.contains("8.8.8.8"))
    }
}
