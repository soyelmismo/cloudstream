package com.lagradost.cloudstream3.shared.ui.theme

import androidx.compose.material.Colors
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.ui.graphics.Color
import com.lagradost.cloudstream3.shared.viewmodels.settings.AppTheme

/**
 * Extended color tokens for advanced CloudStream UI styling.
 */
data class CloudstreamExtendedColors(
    val cardBackground: Color,
    val cardBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val divider: Color,
    val hoverBackground: Color,
    val activeBackground: Color,
    val success: Color = Color(0xFF10B981),
    val warning: Color = Color(0xFFF59E0B),
    val info: Color = Color(0xFF3B82F6),
    val badgeBackground: Color,
    val badgeText: Color,
    val previewPalette: List<Color>
)

object AppColors {
    // Canonical App Colors from Android colors.xml

    /**
     * Fixed high-contrast foreground for content drawn on top of media imagery
     * (posters, hero banners, video player chrome over scrims). Intentionally
     * theme-invariant: overlay content must stay legible regardless of light/dark theme.
     */
    val OnMediaScrim = Color(0xFFFFFFFF)
    val PrimaryDark = Color(0xFF3700B3)

    // Rating star
    val StarRating = Color(0xFFFFC107)

    // Quality badges
    val Quality4K = Color(0xFFE65100)
    val QualityHD = Color(0xFF0288D1)
    val QualityHQ = Color(0xFF2E7D32)
    val QualityCAM = Color(0xFFC62828)
    val QualitySD = Color(0xFF78909C)

    // Media type badges
    val TypeMovie = Color(0xFF1976D2)
    val TypeAnimeMovie = Color(0xFF7B1FA2)
    val TypeTvSeries = Color(0xFF388E3C)
    val TypeAnime = Color(0xFF8E24AA)
    val TypeOVA = Color(0xFF5E35B1)
    val TypeCartoon = Color(0xFFF57C00)
    val TypeLive = Color(0xFFD32F2F)
    val TypeDocumentary = Color(0xFF00796B)
    val TypeAsianDrama = Color(0xFFC2185B)
    val TypeTorrent = Color(0xFF455A64)
    val TypeNSFW = Color(0xFFB71C1C)
    val TypeOther = Color(0xFF455A64)

    // NSFW Filter
    val NsfwFilterContainer = Color(0xFFC2185B).copy(alpha = 0.2f)
    val NsfwFilterContent = Color(0xFFFF4081)

    // Gradients
    val CardOverlayGradient = listOf(Color.Transparent, Color(0xCC000000), Color(0xF0000000))
    val ShimmerGradient = listOf(Color(0xFF2D3342), Color(0xFF161922))

    // Subtitle Customizer Presets
    val SubtitleTextColors = listOf(
        Color(0xFFFFFFFF), // White
        Color(0xFFFFEB3B), // Yellow
        Color(0xFF00E5FF), // Cyan
        Color(0xFF69F0AE), // Green
        Color(0xFFFF80AB), // Pink
        Color(0xFFFFD180), // Orange
        Color(0xFFE0E0E0)  // Light Gray
    )

    val SubtitleEdgeColors = listOf(
        Color(0xFF000000), // Solid Black
        Color(0xFF212121), // Dark Gray
        Color(0xFF37474F), // Slate Gray
        Color(0xFF1A237E), // Deep Navy
        Color(0xFF3E2723), // Dark Brown
        Color(0xFFB71C1C)  // Dark Red
    )

    val SubtitleBackgroundColors = listOf(
        Color(0x00000000), // Transparent
        Color(0x66000000), // 40% Black
        Color(0xAA000000), // 66% Black
        Color(0xFF000000), // Solid Black
        Color(0x88121824)  // Semi-transparent Slate
    )

    // External Sync & Subtitle Brand Colors
    val BrandAniList = Color(0xFF02A9FF)
    val BrandMyAnimeList = Color(0xFF2E51A2)
    val BrandTrakt = Color(0xFFED1C24)
    val BrandSimkl = Color(0xFF00B2FE)
    val BrandKitsu = Color(0xFFFD755C)
    val BrandOpenSubtitles = Color(0xFFE5A00D)
    val BrandSubdl = Color(0xFF3B82F6)
    val BrandAddic7ed = Color(0xFF10B981)
    val BrandSubSource = Color(0xFFEC4899)

    // External Sync Status Colors
    val SyncStatusNone = Color(0xFF9E9E9E)
    val SyncStatusWatching = Color(0xFF00E676)
    val SyncStatusCompleted = Color(0xFF2979FF)
    val SyncStatusPlanToWatch = Color(0xFFFF9100)
    val SyncStatusPaused = Color(0xFFFFD600)
    val SyncStatusDropped = Color(0xFFFF5252)

    // Dracula palette constants
    val DraculaBackground = Color(0xFF282A36)
    val DraculaCurrentLine = Color(0xFF44475A)
    val DraculaForeground = Color(0xFFF8F8F2)
    val DraculaComment = Color(0xFF6272A4)
    val DraculaPurple = Color(0xFFBD93F9)
    val DraculaPink = Color(0xFFFF79C6)
    val DraculaCyan = Color(0xFF8BE9FD)
    val DraculaRed = Color(0xFFFF5555)
    val DraculaItem = Color(0xFF373844)

    // Dark Gray palette (Canonical CloudStream Dark)
    val DarkBackground = Color(0xFF111111)
    val DarkSurface = Color(0xFF1C1C20)
    val DarkCard = Color(0xFF161616)
    val DarkPrimary = Color(0xFF3D50FA)
    val DarkSecondary = Color(0xFF3B65F5)
    val DarkDivider = Color(0xFF2B2C30)
    val DarkTextPrimary = Color(0xFFE9EAEE)
    val DarkTextSecondary = Color(0xFF9BA0A4)
    val DarkTextMuted = Color(0xFF6B7280)

    // AMOLED palette (Pure black canonical)
    val AmoledBackground = Color(0xFF000000)
    val AmoledSurface = Color(0xFF111111)
    val AmoledCard = Color(0xFF000000)
    val AmoledPrimary = Color(0xFF3D50FA)
    val AmoledSecondary = Color(0xFF3B65F5)
    val AmoledDivider = Color(0xFF202125)
    val AmoledTextPrimary = Color(0xFFE9EAEE)
    val AmoledTextSecondary = Color(0xFF9BA0A4)
    val AmoledTextMuted = Color(0xFF6B7280)

    // Light palette (Canonical CloudStream Light)
    val LightBg = Color(0xFFF1F1F1)
    val LightSurf = Color(0xFFFFFFFF)
    val LightCard = Color(0xFFEEEEEE)
    val LightPrimary = Color(0xFF3D50FA)
    val LightSecondary = Color(0xFF3B65F5)
    val LightTextPrimary = Color(0xFF202125)
    val LightTextSecondary = Color(0xFF5F6267)

    // Lavender Dreams (Canonical Lavender Mode)
    val LavenderBackground = Color(0xFFFDF0FB)
    val LavenderSurface = Color(0xFFF7EEFC)
    val LavenderCard = Color(0xFFF8F5FF)
    val LavenderPrimary = Color(0xFF7C3AED)
    val LavenderSecondary = Color(0xFFB794F6)
    val LavenderTextPrimary = Color(0xFF2D1B47)
    val LavenderTextSecondary = Color(0xFF9AB3FF)

    // Silent Blue (Canonical Silent Blue Mode)
    val SilentBlueBackground = Color(0xFF151A30)
    val SilentBlueSurface = Color(0xFF282F49)
    val SilentBlueCard = Color(0xFF3A446A)
    val SilentBluePrimary = Color(0xFF38BDF8)
    val SilentBlueSecondary = Color(0xFF7B83B0)
    val SilentBlueTextPrimary = Color(0xFFE0E1F3)
    val SilentBlueTextSecondary = Color(0xFF7B83B0)

    // Canonical Dracula Light (Dracula Day)
    val DraculaLightBg = Color(0xFFF8F8FC)
    val DraculaLightSurf = Color(0xFFFFFFFF)
    val DraculaLightCard = Color(0xFFF1F1F8)
    val DraculaLightPrimary = Color(0xFF7C3AED)
    val DraculaLightSecondary = Color(0xFFDB2777)
    val DraculaLightTextPrimary = Color(0xFF282A36)
    val DraculaLightTextSecondary = Color(0xFF6272A4)

    // Canonical Lavender Dark
    val LavenderDarkBg = Color(0xFF191224)
    val LavenderDarkSurf = Color(0xFF241A33)
    val LavenderDarkCard = Color(0xFF2F2342)
    val LavenderDarkPrimary = Color(0xFFA78BFA)
    val LavenderDarkSecondary = Color(0xFFC084FC)
    val LavenderDarkTextPrimary = Color(0xFFF5EEFD)
    val LavenderDarkTextSecondary = Color(0xFFD8B4FE)
    val LavenderDarkTextMuted = Color(0xFF9333EA)

    // Canonical Silent Blue Light
    val SilentBlueLightBg = Color(0xFFF0F9FF)
    val SilentBlueLightSurf = Color(0xFFFFFFFF)
    val SilentBlueLightCard = Color(0xFFE0F2FE)
    val SilentBlueLightPrimary = Color(0xFF0284C7)
    val SilentBlueLightSecondary = Color(0xFF0369A1)
    val SilentBlueLightTextPrimary = Color(0xFF0C4A6E)
    val SilentBlueLightTextSecondary = Color(0xFF38BDF8)

    fun getMaterialColors(
        theme: AppTheme,
        isDarkMode: Boolean,
        systemAccentColor: Color? = null
    ): Colors {
        return when (theme) {
            AppTheme.SYSTEM -> {
                val baseColors = if (isDarkMode) getDarkMaterialColors() else getLightMaterialColors()
                if (systemAccentColor != null) {
                    val variant = Color(
                        red = (systemAccentColor.red * 0.82f).coerceIn(0f, 1f),
                        green = (systemAccentColor.green * 0.82f).coerceIn(0f, 1f),
                        blue = (systemAccentColor.blue * 0.82f).coerceIn(0f, 1f),
                        alpha = systemAccentColor.alpha
                    )
                    baseColors.copy(
                        primary = systemAccentColor,
                        primaryVariant = variant,
                        secondary = systemAccentColor
                    )
                } else {
                    baseColors
                }
            }
            AppTheme.DEFAULT -> if (isDarkMode) getDarkMaterialColors() else getLightMaterialColors()
            AppTheme.AMOLED -> if (isDarkMode) {
                darkColors(
                    primary = AmoledPrimary,
                    primaryVariant = PrimaryDark,
                    secondary = AmoledSecondary,
                    background = AmoledBackground,
                    surface = AmoledSurface,
                    onPrimary = Color.White,
                    onSecondary = Color.White,
                    onBackground = AmoledTextPrimary,
                    onSurface = AmoledTextPrimary,
                    error = Color(0xFFEF4444)
                )
            } else {
                lightColors(
                    primary = Color(0xFF000000),
                    primaryVariant = Color(0xFF222222),
                    secondary = Color(0xFF333333),
                    background = Color(0xFFFFFFFF),
                    surface = Color(0xFFFFFFFF),
                    onPrimary = Color.White,
                    onSecondary = Color.White,
                    onBackground = Color(0xFF000000),
                    onSurface = Color(0xFF000000),
                    error = Color(0xFFDC2626)
                )
            }
            AppTheme.DRACULA -> if (isDarkMode) {
                darkColors(
                    primary = DraculaPurple,
                    primaryVariant = Color(0xFF956ED6),
                    secondary = DraculaPink,
                    background = DraculaBackground,
                    surface = DraculaCurrentLine,
                    onPrimary = DraculaBackground,
                    onSecondary = DraculaBackground,
                    onBackground = DraculaForeground,
                    onSurface = DraculaForeground,
                    error = DraculaRed
                )
            } else {
                lightColors(
                    primary = DraculaLightPrimary,
                    primaryVariant = Color(0xFF6D28D9),
                    secondary = DraculaLightSecondary,
                    background = DraculaLightBg,
                    surface = DraculaLightSurf,
                    onPrimary = Color.White,
                    onSecondary = Color.White,
                    onBackground = DraculaLightTextPrimary,
                    onSurface = DraculaLightTextPrimary,
                    error = Color(0xFFDC2626)
                )
            }
            AppTheme.LAVENDER -> if (isDarkMode) {
                darkColors(
                    primary = LavenderDarkPrimary,
                    primaryVariant = Color(0xFF8B5CF6),
                    secondary = LavenderDarkSecondary,
                    background = LavenderDarkBg,
                    surface = LavenderDarkSurf,
                    onPrimary = Color(0xFF191224),
                    onSecondary = Color(0xFF191224),
                    onBackground = LavenderDarkTextPrimary,
                    onSurface = LavenderDarkTextPrimary,
                    error = Color(0xFFEF5350)
                )
            } else {
                lightColors(
                    primary = LavenderPrimary,
                    primaryVariant = Color(0xFF6D28D9),
                    secondary = LavenderSecondary,
                    background = LavenderBackground,
                    surface = LavenderSurface,
                    onPrimary = Color.White,
                    onSecondary = Color.Black,
                    onBackground = LavenderTextPrimary,
                    onSurface = LavenderTextPrimary,
                    error = Color(0xFFEF5350)
                )
            }
            AppTheme.SILENT_BLUE -> if (isDarkMode) {
                darkColors(
                    primary = SilentBluePrimary,
                    primaryVariant = Color(0xFF0284C7),
                    secondary = SilentBlueSecondary,
                    background = SilentBlueBackground,
                    surface = SilentBlueSurface,
                    onPrimary = Color(0xFF0F172A),
                    onSecondary = Color.White,
                    onBackground = SilentBlueTextPrimary,
                    onSurface = SilentBlueTextPrimary,
                    error = Color(0xFFF43F5E)
                )
            } else {
                lightColors(
                    primary = SilentBlueLightPrimary,
                    primaryVariant = Color(0xFF0369A1),
                    secondary = SilentBlueLightSecondary,
                    background = SilentBlueLightBg,
                    surface = SilentBlueLightSurf,
                    onPrimary = Color.White,
                    onSecondary = Color.White,
                    onBackground = SilentBlueLightTextPrimary,
                    onSurface = SilentBlueLightTextPrimary,
                    error = Color(0xFFDC2626)
                )
            }
        }
    }

    fun getExtendedColors(
        theme: AppTheme,
        isDarkMode: Boolean,
        systemAccentColor: Color? = null
    ): CloudstreamExtendedColors {
        return when (theme) {
            AppTheme.SYSTEM -> {
                val baseExtended = if (isDarkMode) getDarkExtendedColors() else getLightExtendedColors()
                if (systemAccentColor != null) {
                    baseExtended.copy(
                        badgeBackground = systemAccentColor.copy(alpha = if (isDarkMode) 0.22f else 0.14f),
                        badgeText = systemAccentColor,
                        hoverBackground = if (isDarkMode) {
                            systemAccentColor.copy(alpha = 0.08f)
                        } else {
                            systemAccentColor.copy(alpha = 0.05f)
                        },
                        activeBackground = if (isDarkMode) {
                            systemAccentColor.copy(alpha = 0.16f)
                        } else {
                            systemAccentColor.copy(alpha = 0.10f)
                        },
                        previewPalette = if (isDarkMode) {
                            listOf(systemAccentColor, DarkBackground, DarkSurface, systemAccentColor)
                        } else {
                            listOf(systemAccentColor, LightBg, LightSurf, systemAccentColor)
                        }
                    )
                } else {
                    baseExtended
                }
            }
            AppTheme.DEFAULT -> if (isDarkMode) getDarkExtendedColors() else getLightExtendedColors()
            AppTheme.AMOLED -> if (isDarkMode) {
                CloudstreamExtendedColors(
                    cardBackground = AmoledCard,
                    cardBorder = AmoledDivider,
                    textPrimary = AmoledTextPrimary,
                    textSecondary = AmoledTextSecondary,
                    textMuted = AmoledTextMuted,
                    divider = AmoledDivider,
                    hoverBackground = Color(0xFF161616),
                    activeBackground = Color(0xFF222222),
                    badgeBackground = AmoledPrimary.copy(alpha = 0.2f),
                    badgeText = AmoledPrimary,
                    previewPalette = listOf(AmoledPrimary, AmoledBackground, AmoledSurface, AmoledSecondary)
                )
            } else {
                CloudstreamExtendedColors(
                    cardBackground = Color(0xFFFFFFFF),
                    cardBorder = Color(0xFF000000),
                    textPrimary = Color(0xFF000000),
                    textSecondary = Color(0xFF333333),
                    textMuted = Color(0xFF666666),
                    divider = Color(0xFFCCCCCC),
                    hoverBackground = Color(0xFFF0F0F0),
                    activeBackground = Color(0xFFE0E0E0),
                    badgeBackground = Color(0xFF000000).copy(alpha = 0.12f),
                    badgeText = Color(0xFF000000),
                    previewPalette = listOf(Color(0xFF000000), Color(0xFFFFFFFF), Color(0xFFF5F5F5), Color(0xFF333333))
                )
            }
            AppTheme.DRACULA -> if (isDarkMode) {
                CloudstreamExtendedColors(
                    cardBackground = DraculaItem,
                    cardBorder = DraculaComment.copy(alpha = 0.4f),
                    textPrimary = DraculaForeground,
                    textSecondary = DraculaCyan,
                    textMuted = DraculaComment,
                    divider = DraculaComment.copy(alpha = 0.3f),
                    hoverBackground = DraculaCurrentLine,
                    activeBackground = Color(0xFF6272A4).copy(alpha = 0.4f),
                    badgeBackground = DraculaPurple.copy(alpha = 0.25f),
                    badgeText = DraculaPurple,
                    previewPalette = listOf(DraculaPurple, DraculaBackground, DraculaCurrentLine, DraculaPink)
                )
            } else {
                CloudstreamExtendedColors(
                    cardBackground = DraculaLightCard,
                    cardBorder = Color(0xFFE2E8F0),
                    textPrimary = DraculaLightTextPrimary,
                    textSecondary = DraculaLightTextSecondary,
                    textMuted = Color(0xFF94A3B8),
                    divider = Color(0xFFE2E8F0),
                    hoverBackground = Color(0xFFEEF2F6),
                    activeBackground = Color(0xFFE2E8F0),
                    badgeBackground = DraculaLightPrimary.copy(alpha = 0.12f),
                    badgeText = DraculaLightPrimary,
                    previewPalette = listOf(DraculaLightPrimary, DraculaLightBg, DraculaLightSurf, DraculaLightSecondary)
                )
            }
            AppTheme.LAVENDER -> if (isDarkMode) {
                CloudstreamExtendedColors(
                    cardBackground = LavenderDarkCard,
                    cardBorder = Color(0xFF4C1D95).copy(alpha = 0.5f),
                    textPrimary = LavenderDarkTextPrimary,
                    textSecondary = LavenderDarkTextSecondary,
                    textMuted = LavenderDarkTextMuted,
                    divider = Color(0xFF3B1D66),
                    hoverBackground = Color(0xFF3B2556),
                    activeBackground = Color(0xFF4C2F6E),
                    badgeBackground = LavenderDarkPrimary.copy(alpha = 0.2f),
                    badgeText = LavenderDarkPrimary,
                    previewPalette = listOf(LavenderDarkPrimary, LavenderDarkBg, LavenderDarkSurf, LavenderDarkSecondary)
                )
            } else {
                CloudstreamExtendedColors(
                    cardBackground = LavenderCard,
                    cardBorder = Color(0xFFE9D5FF),
                    textPrimary = LavenderTextPrimary,
                    textSecondary = Color(0xFF6B21A8),
                    textMuted = Color(0xFF9333EA),
                    divider = Color(0xFFF3E8FF),
                    hoverBackground = Color(0xFFF5EEFD),
                    activeBackground = Color(0xFFEEDBFC),
                    badgeBackground = LavenderPrimary.copy(alpha = 0.2f),
                    badgeText = LavenderPrimary,
                    previewPalette = listOf(LavenderPrimary, LavenderBackground, LavenderSurface, LavenderSecondary)
                )
            }
            AppTheme.SILENT_BLUE -> if (isDarkMode) {
                CloudstreamExtendedColors(
                    cardBackground = SilentBlueCard,
                    cardBorder = Color(0xFF475569),
                    textPrimary = SilentBlueTextPrimary,
                    textSecondary = SilentBlueTextSecondary,
                    textMuted = Color(0xFF64748B),
                    divider = Color(0xFF282F49),
                    hoverBackground = Color(0xFF2D3759),
                    activeBackground = Color(0xFF3B4874),
                    badgeBackground = SilentBluePrimary.copy(alpha = 0.2f),
                    badgeText = SilentBluePrimary,
                    previewPalette = listOf(SilentBluePrimary, SilentBlueBackground, SilentBlueSurface, SilentBlueSecondary)
                )
            } else {
                CloudstreamExtendedColors(
                    cardBackground = SilentBlueLightCard,
                    cardBorder = Color(0xFFBAE6FD),
                    textPrimary = SilentBlueLightTextPrimary,
                    textSecondary = SilentBlueLightTextSecondary,
                    textMuted = Color(0xFF0284C7),
                    divider = Color(0xFFE0F2FE),
                    hoverBackground = Color(0xFFE0F2FE),
                    activeBackground = Color(0xFFBAE6FD),
                    badgeBackground = SilentBlueLightPrimary.copy(alpha = 0.12f),
                    badgeText = SilentBlueLightPrimary,
                    previewPalette = listOf(SilentBlueLightPrimary, SilentBlueLightBg, SilentBlueLightSurf, SilentBlueLightSecondary)
                )
            }
        }
    }

    private fun getDarkMaterialColors() = darkColors(
        primary = DarkPrimary,
        primaryVariant = PrimaryDark,
        secondary = DarkSecondary,
        background = DarkBackground,
        surface = DarkSurface,
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = DarkTextPrimary,
        onSurface = DarkTextPrimary,
        error = Color(0xFFEF4444)
    )

    private fun getLightMaterialColors() = lightColors(
        primary = LightPrimary,
        primaryVariant = PrimaryDark,
        secondary = LightSecondary,
        background = LightBg,
        surface = LightSurf,
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = LightTextPrimary,
        onSurface = LightTextPrimary,
        error = Color(0xFFDC2626)
    )

    private fun getDarkExtendedColors() = CloudstreamExtendedColors(
        cardBackground = DarkCard,
        cardBorder = DarkDivider,
        textPrimary = DarkTextPrimary,
        textSecondary = DarkTextSecondary,
        textMuted = DarkTextMuted,
        divider = DarkDivider,
        hoverBackground = Color(0xFF222226),
        activeBackground = Color(0xFF2B2C30),
        badgeBackground = DarkPrimary.copy(alpha = 0.2f),
        badgeText = DarkPrimary,
        previewPalette = listOf(DarkPrimary, DarkBackground, DarkSurface, DarkSecondary)
    )

    private fun getLightExtendedColors() = CloudstreamExtendedColors(
        cardBackground = LightCard,
        cardBorder = Color(0xFFE0E0E0),
        textPrimary = LightTextPrimary,
        textSecondary = LightTextSecondary,
        textMuted = Color(0xFF9AA0A6),
        divider = Color(0xFFE8EAED),
        hoverBackground = Color(0xFFE8ECEF),
        activeBackground = Color(0xFFDFE3E7),
        badgeBackground = LightPrimary.copy(alpha = 0.12f),
        badgeText = LightPrimary,
        previewPalette = listOf(LightPrimary, LightBg, LightSurf, LightSecondary)
    )
}

