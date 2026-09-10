package com.lagradost.cloudstream3.shared.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

actual object NativeSystemTheme {
    // Starts empty on purpose: detection is expensive (spawns subprocesses such as gdbus on
    // Linux) and must never run blocking during object initialization.
    private val _themeState = MutableStateFlow(NativeThemeState())
    actual val themeState: StateFlow<NativeThemeState> = _themeState.asStateFlow()

    actual fun getThemeState(): NativeThemeState {
        return _themeState.value
    }

    fun refresh(): NativeThemeState {
        val latest = detectNativeTheme()
        _themeState.value = latest
        return latest
    }
}

@Composable
actual fun rememberNativeSystemTheme(): NativeThemeState {
    val state by NativeSystemTheme.themeState.collectAsState()
    // One-shot reactive detection bound to the composition lifecycle: no poller, no timer loop.
    // The coroutine is dispatched off the main thread and cancelled when this composable leaves
    // composition, so no long-lived scope leaks behind the call site.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            NativeSystemTheme.refresh()
        }
    }
    return state
}

/**
 * Detects native system theme and accent color based on the running operating system.
 */
internal fun detectNativeTheme(): NativeThemeState {
    val os = System.getProperty("os.name", "").lowercase()
    return when {
        os.contains("linux") || os.contains("unix") -> detectLinuxTheme()
        os.contains("win") -> detectWindowsTheme()
        os.contains("mac") -> detectMacOsTheme()
        else -> NativeThemeState()
    }
}

// -----------------------------------------------------------------------------
// Linux Theme & Accent Detection (KDE Plasma, XDG Portal, GNOME)
// -----------------------------------------------------------------------------

internal fun detectLinuxTheme(): NativeThemeState {
    var kdeAccent: Color? = null
    var kdeDarkMode: Boolean? = null

    // 1. KDE Plasma: Read ~/.config/kdeglobals
    val xdgConfig = System.getenv("XDG_CONFIG_HOME")
    val kdeglobalsFile = if (!xdgConfig.isNullOrBlank()) {
        File(xdgConfig, "kdeglobals")
    } else {
        File(System.getProperty("user.home", "."), ".config/kdeglobals")
    }

    if (kdeglobalsFile.exists() && kdeglobalsFile.canRead()) {
        try {
            var currentSection = ""
            var generalAccent: String? = null
            var generalColorScheme: String? = null
            var selectionBg: String? = null
            var selectionFocus: String? = null
            var windowBg: String? = null

            kdeglobalsFile.forEachLine { rawLine ->
                val line = rawLine.trim()
                if (line.startsWith("[") && line.endsWith("]")) {
                    currentSection = line.substring(1, line.length - 1).trim().lowercase()
                } else if (line.contains("=")) {
                    val key = line.substringBefore("=").trim()
                    val value = line.substringAfter("=").trim()
                    when (currentSection) {
                        "general" -> {
                            if (key.equals("AccentColor", ignoreCase = true)) generalAccent = value
                            if (key.equals("ColorScheme", ignoreCase = true)) generalColorScheme = value
                        }
                        "colors:selection" -> {
                            if (key.equals("BackgroundNormal", ignoreCase = true)) selectionBg = value
                            if (key.equals("DecorationFocus", ignoreCase = true)) selectionFocus = value
                        }
                        "colors:window" -> {
                            if (key.equals("BackgroundNormal", ignoreCase = true)) windowBg = value
                        }
                    }
                }
            }

            // Resolve KDE Accent
            val rawAccent = generalAccent ?: selectionBg ?: selectionFocus
            if (rawAccent != null) {
                kdeAccent = parseRgbString(rawAccent)
            }

            // Resolve KDE Dark Mode
            if (generalColorScheme != null) {
                val scheme = generalColorScheme.lowercase()
                if (scheme.contains("dark") || scheme.contains("black")) {
                    kdeDarkMode = true
                } else if (scheme.contains("light") || scheme.contains("white")) {
                    kdeDarkMode = false
                }
            }
            if (kdeDarkMode == null && windowBg != null) {
                val winColor = parseRgbString(windowBg)
                if (winColor != null) {
                    val luma = 0.299f * winColor.red + 0.587f * winColor.green + 0.114f * winColor.blue
                    kdeDarkMode = luma < 0.5f
                }
            }
        } catch (_: Throwable) {
            // Ignore file read issues
        }
    }

    // 2. XDG Desktop Portal fallback via gdbus
    var portalAccent: Color? = null
    var portalDarkMode: Boolean? = null

    val portalAccentOutput = runProcess(
        "gdbus", "call", "--session",
        "--dest", "org.freedesktop.portal.Desktop",
        "--object-path", "/org/freedesktop/portal/desktop",
        "--method", "org.freedesktop.portal.Settings.Read",
        "org.freedesktop.appearance", "accent-color"
    )
    if (!portalAccentOutput.isNullOrBlank()) {
        portalAccent = parsePortalAccentColor(portalAccentOutput)
    }

    val portalThemeOutput = runProcess(
        "gdbus", "call", "--session",
        "--dest", "org.freedesktop.portal.Desktop",
        "--object-path", "/org/freedesktop/portal/desktop",
        "--method", "org.freedesktop.portal.Settings.Read",
        "org.freedesktop.appearance", "color-scheme"
    )
    if (!portalThemeOutput.isNullOrBlank()) {
        portalDarkMode = parsePortalColorScheme(portalThemeOutput)
    }

    // 3. GNOME gsettings fallback
    var gnomeAccent: Color? = null
    var gnomeDarkMode: Boolean? = null

    val gsettingsAccentOutput = runProcess("gsettings", "get", "org.gnome.desktop.interface", "accent-color")
    if (!gsettingsAccentOutput.isNullOrBlank()) {
        gnomeAccent = parseGnomeAccentColor(gsettingsAccentOutput)
    }

    val gsettingsThemeOutput = runProcess("gsettings", "get", "org.gnome.desktop.interface", "color-scheme")
    if (!gsettingsThemeOutput.isNullOrBlank()) {
        gnomeDarkMode = parseGnomeColorScheme(gsettingsThemeOutput)
    }

    val resolvedAccent = kdeAccent ?: portalAccent ?: gnomeAccent
    val resolvedDarkMode = kdeDarkMode ?: portalDarkMode ?: gnomeDarkMode

    return NativeThemeState(
        accentColor = resolvedAccent,
        isDarkMode = resolvedDarkMode
    )
}

// -----------------------------------------------------------------------------
// Windows Theme & Accent Detection (DWM Registry & Personalize)
// -----------------------------------------------------------------------------

internal fun detectWindowsTheme(): NativeThemeState {
    var accentColor: Color? = null
    var isDarkMode: Boolean? = null

    // 1. Accent Color: HKCU\Software\Microsoft\Windows\DWM\AccentColor (ABGR hex)
    val dwmAccentOutput = runProcess("reg", "query", "HKCU\\Software\\Microsoft\\Windows\\DWM", "/v", "AccentColor")
    if (!dwmAccentOutput.isNullOrBlank()) {
        accentColor = parseWindowsDwmAccent(dwmAccentOutput)
    }

    // Fallback: HKCU\Software\Microsoft\Windows\DWM\ColorizationColor (ARGB hex)
    if (accentColor == null) {
        val dwmColorizationOutput = runProcess("reg", "query", "HKCU\\Software\\Microsoft\\Windows\\DWM", "/v", "ColorizationColor")
        if (!dwmColorizationOutput.isNullOrBlank()) {
            accentColor = parseWindowsDwmColorization(dwmColorizationOutput)
        }
    }

    // 2. Dark Mode: HKCU\Software\Microsoft\Windows\CurrentVersion\Themes\Personalize\AppsUseLightTheme
    val personalizeOutput = runProcess(
        "reg", "query",
        "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
        "/v", "AppsUseLightTheme"
    )
    if (!personalizeOutput.isNullOrBlank()) {
        isDarkMode = parseWindowsAppsUseLightTheme(personalizeOutput)
    }

    return NativeThemeState(
        accentColor = accentColor,
        isDarkMode = isDarkMode
    )
}

// -----------------------------------------------------------------------------
// macOS Fallback
// -----------------------------------------------------------------------------

internal fun detectMacOsTheme(): NativeThemeState {
    var isDarkMode: Boolean? = null
    val styleOutput = runProcess("defaults", "read", "-g", "AppleInterfaceStyle")
    if (styleOutput != null) {
        isDarkMode = styleOutput.trim().equals("Dark", ignoreCase = true)
    }

    var accentColor: Color? = null
    val accentOutput = runProcess("defaults", "read", "-g", "AppleAccentColor")
    if (accentOutput != null) {
        val code = accentOutput.trim().toIntOrNull()
        accentColor = when (code) {
            -1 -> Color(0xFF8E8E93) // Graphite
            0 -> Color(0xFFFF3B30)  // Red
            1 -> Color(0xFFFF9500)  // Orange
            2 -> Color(0xFFFFCC00)  // Yellow
            3 -> Color(0xFF34C759)  // Green
            4 -> Color(0xFF007AFF)  // Blue
            5 -> Color(0xFFAF52DE)  // Purple
            6 -> Color(0xFFFF2D55)  // Pink
            else -> null
        }
    }

    return NativeThemeState(
        accentColor = accentColor,
        isDarkMode = isDarkMode
    )
}

// -----------------------------------------------------------------------------
// Parsing Utilities
// -----------------------------------------------------------------------------

internal fun parseRgbString(rgbStr: String): Color? {
    val parts = rgbStr.split(",").mapNotNull { it.trim().toIntOrNull() }
    return if (parts.size >= 3) {
        val r = parts[0].coerceIn(0, 255)
        val g = parts[1].coerceIn(0, 255)
        val b = parts[2].coerceIn(0, 255)
        Color(r, g, b)
    } else {
        null
    }
}

internal fun parsePortalAccentColor(output: String): Color? {
    val match = Regex("""\(\s*([0-9.]+)\s*,\s*([0-9.]+)\s*,\s*([0-9.]+)\s*\)""").find(output) ?: return null
    val (rStr, gStr, bStr) = match.destructured
    val r = rStr.toFloatOrNull()?.coerceIn(0f, 1f) ?: return null
    val g = gStr.toFloatOrNull()?.coerceIn(0f, 1f) ?: return null
    val b = bStr.toFloatOrNull()?.coerceIn(0f, 1f) ?: return null
    return Color(r, g, b)
}

internal fun parsePortalColorScheme(output: String): Boolean? {
    val match = Regex("""uint32\s+(\d+)""").find(output) ?: return null
    return when (match.groupValues[1].toIntOrNull()) {
        1 -> true  // prefer-dark
        2 -> false // prefer-light
        else -> null
    }
}

internal fun parseGnomeAccentColor(raw: String): Color? {
    val clean = raw.trim().trim('\'', '"').trim().lowercase()
    return when (clean) {
        "blue" -> Color(0xFF3584E4)
        "teal" -> Color(0xFF2190A4)
        "green" -> Color(0xFF3A944C)
        "yellow" -> Color(0xFFE5A50A)
        "orange" -> Color(0xFFED5B00)
        "red" -> Color(0xFFE01B24)
        "pink" -> Color(0xFFD56199)
        "purple" -> Color(0xFF9141AC)
        "slate" -> Color(0xFF63788C)
        else -> {
            if (clean.startsWith("#")) {
                parseHexColor(clean)
            } else if (clean.startsWith("rgb")) {
                val inside = clean.substringAfter("(").substringBefore(")")
                parseRgbString(inside)
            } else {
                null
            }
        }
    }
}

internal fun parseGnomeColorScheme(raw: String): Boolean? {
    val clean = raw.trim().trim('\'', '"').trim().lowercase()
    return when {
        clean.contains("dark") -> true
        clean.contains("light") -> false
        else -> null
    }
}

internal fun parseWindowsDwmAccent(output: String): Color? {
    val match = Regex("""REG_DWORD\s+0x([0-9a-fA-F]+)""").find(output) ?: return null
    val hexStr = match.groupValues[1]
    val dword = hexStr.toLongOrNull(16) ?: return null
    // ABGR format in DWM AccentColor: 0xAABBGGRR
    val r = (dword and 0xFF).toInt().coerceIn(0, 255)
    val g = ((dword shr 8) and 0xFF).toInt().coerceIn(0, 255)
    val b = ((dword shr 16) and 0xFF).toInt().coerceIn(0, 255)
    return Color(r, g, b)
}

internal fun parseWindowsDwmColorization(output: String): Color? {
    val match = Regex("""REG_DWORD\s+0x([0-9a-fA-F]+)""").find(output) ?: return null
    val hexStr = match.groupValues[1]
    val dword = hexStr.toLongOrNull(16) ?: return null
    // ARGB format in DWM ColorizationColor: 0xAARRGGBB
    val b = (dword and 0xFF).toInt().coerceIn(0, 255)
    val g = ((dword shr 8) and 0xFF).toInt().coerceIn(0, 255)
    val r = ((dword shr 16) and 0xFF).toInt().coerceIn(0, 255)
    return Color(r, g, b)
}

internal fun parseWindowsAppsUseLightTheme(output: String): Boolean? {
    val match = Regex("""REG_DWORD\s+0x([0-9a-fA-F]+)""").find(output) ?: return null
    val value = match.groupValues[1].toIntOrNull(16) ?: return null
    return value == 0 // 0 = Dark, 1 = Light
}

internal fun parseHexColor(hex: String): Color? {
    val clean = hex.removePrefix("#").trim()
    return when (clean.length) {
        6 -> {
            val num = clean.toLongOrNull(16) ?: return null
            val r = ((num shr 16) and 0xFF).toInt()
            val g = ((num shr 8) and 0xFF).toInt()
            val b = (num and 0xFF).toInt()
            Color(r, g, b)
        }
        8 -> {
            val num = clean.toLongOrNull(16) ?: return null
            val a = ((num shr 24) and 0xFF).toInt()
            val r = ((num shr 16) and 0xFF).toInt()
            val g = ((num shr 8) and 0xFF).toInt()
            val b = (num and 0xFF).toInt()
            Color(r, g, b, a)
        }
        else -> null
    }
}

internal fun runProcess(vararg command: String, timeoutMs: Long = 1000L): String? {
    return try {
        val process = ProcessBuilder(*command)
            .redirectErrorStream(true)
            .start()
        val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        if (finished && process.exitValue() == 0) {
            process.inputStream.bufferedReader().use { it.readText() }
        } else {
            if (!finished) {
                process.destroyForcibly()
            }
            null
        }
    } catch (_: Throwable) {
        null
    }
}
