package com.lagradost.cloudstream3.shared.ui.theme

import androidx.compose.ui.graphics.Color
import com.lagradost.cloudstream3.shared.viewmodels.settings.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NativeSystemThemeTest {

    @Test
    fun testParseRgbString() {
        val color = parseRgbString("93,101,46")
        assertNotNull(color)
        assertEquals((93f / 255f), color.red, 0.01f)
        assertEquals((101f / 255f), color.green, 0.01f)
        assertEquals((46f / 255f), color.blue, 0.01f)

        val invalid = parseRgbString("invalid,string")
        assertNull(invalid)
    }

    @Test
    fun testParsePortalAccentColor() {
        val output = "(<<(0.29019609093666077, 0.30980393290519714, 0.15686275064945221)>>,\n)"
        val color = parsePortalAccentColor(output)
        assertNotNull(color)
        assertEquals(0.290196f, color.red, 0.01f)
        assertEquals(0.309803f, color.green, 0.01f)
        assertEquals(0.156862f, color.blue, 0.01f)
    }

    @Test
    fun testParsePortalColorScheme() {
        assertEquals(true, parsePortalColorScheme("(<<uint32 1>>, )"))
        assertEquals(false, parsePortalColorScheme("(<<uint32 2>>, )"))
        assertNull(parsePortalColorScheme("(<<uint32 0>>, )"))
    }

    @Test
    fun testParseGnomeAccentColor() {
        val blue = parseGnomeAccentColor("'blue'")
        assertNotNull(blue)
        assertEquals(Color(0xFF3584E4), blue)

        val teal = parseGnomeAccentColor("teal")
        assertNotNull(teal)
        assertEquals(Color(0xFF2190A4), teal)

        val hex = parseGnomeAccentColor("#38BDF8")
        assertNotNull(hex)
        assertEquals(Color(0xFF38BDF8), hex)
    }

    @Test
    fun testParseGnomeColorScheme() {
        assertEquals(true, parseGnomeColorScheme("'prefer-dark'"))
        assertEquals(false, parseGnomeColorScheme("'prefer-light'"))
        assertNull(parseGnomeColorScheme("'default'"))
    }

    @Test
    fun testParseWindowsDwmAccent() {
        // DWM AccentColor is stored as ABGR (0xAABBGGRR)
        // 0xFF3A8EE6 -> Alpha=0xFF, Blue=0x3A, Green=0x8E, Red=0xE6
        val output = "    AccentColor    REG_DWORD    0xff3a8ee6\n"
        val color = parseWindowsDwmAccent(output)
        assertNotNull(color)
        assertEquals((0xE6 / 255f), color.red, 0.01f)
        assertEquals((0x8E / 255f), color.green, 0.01f)
        assertEquals((0x3A / 255f), color.blue, 0.01f)
    }

    @Test
    fun testParseWindowsDwmColorization() {
        // DWM ColorizationColor is stored as ARGB (0xAARRGGBB)
        // 0xC4E68E3A -> Alpha=0xC4, Red=0xE6, Green=0x8E, Blue=0x3A
        val output = "    ColorizationColor    REG_DWORD    0xc4e68e3a\n"
        val color = parseWindowsDwmColorization(output)
        assertNotNull(color)
        assertEquals((0xE6 / 255f), color.red, 0.01f)
        assertEquals((0x8E / 255f), color.green, 0.01f)
        assertEquals((0x3A / 255f), color.blue, 0.01f)
    }

    @Test
    fun testParseWindowsAppsUseLightTheme() {
        val darkOutput = "    AppsUseLightTheme    REG_DWORD    0x0\n"
        val lightOutput = "    AppsUseLightTheme    REG_DWORD    0x1\n"
        assertEquals(true, parseWindowsAppsUseLightTheme(darkOutput))
        assertEquals(false, parseWindowsAppsUseLightTheme(lightOutput))
    }

    @Test
    fun testSystemThemeCustomAccentIntegration() {
        val customAccent = Color(0xFF10B981) // Emerald
        val material = AppColors.getMaterialColors(AppTheme.SYSTEM, isDarkMode = true, systemAccentColor = customAccent)
        assertEquals(customAccent, material.primary)
        assertEquals(customAccent, material.secondary)

        val extended = AppColors.getExtendedColors(AppTheme.SYSTEM, isDarkMode = true, systemAccentColor = customAccent)
        assertEquals(customAccent, extended.badgeText)
        assertEquals(customAccent, extended.previewPalette[0])
    }

    @Test
    fun testNativeSystemThemeDetectionDoesNotThrow() {
        // Detection is lazy/on-demand: the object initializer starts no background poller.
        val state = NativeSystemTheme.refresh()
        assertNotNull(state)
    }
}
