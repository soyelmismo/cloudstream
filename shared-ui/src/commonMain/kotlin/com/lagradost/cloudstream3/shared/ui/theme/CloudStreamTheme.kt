package com.lagradost.cloudstream3.shared.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Shapes
import androidx.compose.material.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cloudstream.shared_ui.generated.resources.*
import com.lagradost.cloudstream3.shared.viewmodels.settings.AppTheme
import org.jetbrains.compose.resources.Font

typealias ExtendedColors = CloudstreamExtendedColors

val LocalExtendedColors = staticCompositionLocalOf<ExtendedColors> {
    AppColors.getExtendedColors(AppTheme.DEFAULT, isDarkMode = true)
}

object CloudstreamTheme {
    val extendedColors: ExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalExtendedColors.current
}

val CloudStreamShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp)
)

@Composable
fun getAppFontFamily(): FontFamily {
    return FontFamily(
        Font(Res.font.productsans_thin, FontWeight.Thin, FontStyle.Normal),
        Font(Res.font.productsans_thinitalic, FontWeight.Thin, FontStyle.Italic),
        Font(Res.font.productsans_light, FontWeight.Light, FontStyle.Normal),
        Font(Res.font.productsans_lightitalic, FontWeight.Light, FontStyle.Italic),
        Font(Res.font.productsans_regular, FontWeight.Normal, FontStyle.Normal),
        Font(Res.font.productsans_italic, FontWeight.Normal, FontStyle.Italic),
        Font(Res.font.productsans_medium, FontWeight.Medium, FontStyle.Normal),
        Font(Res.font.productsans_mediumitalic, FontWeight.Medium, FontStyle.Italic),
        Font(Res.font.productsans_bold, FontWeight.Bold, FontStyle.Normal),
        Font(Res.font.productsans_bolditalic, FontWeight.Bold, FontStyle.Italic),
        Font(Res.font.productsans_black, FontWeight.Black, FontStyle.Normal),
        Font(Res.font.productsans_blackitalic, FontWeight.Black, FontStyle.Italic),
    )
}

@Composable
fun getAppTypography(fontFamily: FontFamily = getAppFontFamily()): Typography {
    return Typography(
        defaultFontFamily = fontFamily,
        h4 = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            letterSpacing = 0.25.sp
        ),
        h5 = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            letterSpacing = 0.sp
        ),
        h6 = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            letterSpacing = 0.15.sp
        ),
        subtitle1 = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            letterSpacing = 0.15.sp
        ),
        subtitle2 = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            letterSpacing = 0.1.sp
        ),
        body1 = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 15.sp,
            letterSpacing = 0.25.sp
        ),
        body2 = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            letterSpacing = 0.2.sp
        ),
        button = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            letterSpacing = 0.5.sp
        ),
        caption = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            letterSpacing = 0.3.sp
        ),
        overline = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp,
            letterSpacing = 1.sp
        )
    )
}

/**
 * Dynamic CloudStream color accessor.
 * Exposes active MaterialTheme and CloudstreamExtendedColors dynamically at runtime.
 */
object CloudStreamColors {
    val Background: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colors.background

    val Surface: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colors.surface

    val SurfaceVariant: Color
        @Composable
        @ReadOnlyComposable
        get() = CloudstreamTheme.extendedColors.cardBackground

    val SurfaceElevated: Color
        @Composable
        @ReadOnlyComposable
        get() = CloudstreamTheme.extendedColors.hoverBackground

    val Primary: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colors.primary

    val PrimaryVariant: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colors.primaryVariant

    val Secondary: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colors.secondary

    val SecondaryVariant: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colors.secondary

    val TextPrimary: Color
        @Composable
        @ReadOnlyComposable
        get() = CloudstreamTheme.extendedColors.textPrimary

    val TextSecondary: Color
        @Composable
        @ReadOnlyComposable
        get() = CloudstreamTheme.extendedColors.textSecondary

    val TextMuted: Color
        @Composable
        @ReadOnlyComposable
        get() = CloudstreamTheme.extendedColors.textMuted

    val Success: Color
        @Composable
        @ReadOnlyComposable
        get() = CloudstreamTheme.extendedColors.success

    val Warning: Color
        @Composable
        @ReadOnlyComposable
        get() = CloudstreamTheme.extendedColors.warning

    val Error: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colors.error

    val Info: Color
        @Composable
        @ReadOnlyComposable
        get() = CloudstreamTheme.extendedColors.info

    val Divider: Color
        @Composable
        @ReadOnlyComposable
        get() = CloudstreamTheme.extendedColors.divider

    // Rating Star
    val StarRating = AppColors.StarRating

    // Content drawn over media imagery / scrim gradients (theme-invariant white)
    val OnMediaScrim = AppColors.OnMediaScrim

    // Quality Badges
    val Quality4K = AppColors.Quality4K
    val QualityHD = AppColors.QualityHD
    val QualityHQ = AppColors.QualityHQ
    val QualityCAM = AppColors.QualityCAM
    val QualitySD = AppColors.QualitySD

    // Media Type Badges
    val TypeMovie = AppColors.TypeMovie
    val TypeAnimeMovie = AppColors.TypeAnimeMovie
    val TypeTvSeries = AppColors.TypeTvSeries
    val TypeAnime = AppColors.TypeAnime
    val TypeOVA = AppColors.TypeOVA
    val TypeCartoon = AppColors.TypeCartoon
    val TypeLive = AppColors.TypeLive
    val TypeDocumentary = AppColors.TypeDocumentary
    val TypeAsianDrama = AppColors.TypeAsianDrama
    val TypeTorrent = AppColors.TypeTorrent
    val TypeNSFW = AppColors.TypeNSFW
    val TypeOther = AppColors.TypeOther

    // NSFW Filter Chip
    val NsfwContainer = AppColors.NsfwFilterContainer
    val NsfwContent = AppColors.NsfwFilterContent

    // Gradients & Overlays
    val CardOverlayGradient = AppColors.CardOverlayGradient
    val ShimmerGradient = AppColors.ShimmerGradient

    // Subtitle Customizer Presets
    val SubtitleTextPresets = AppColors.SubtitleTextColors
    val SubtitleEdgePresets = AppColors.SubtitleEdgeColors
    val SubtitleBackgroundPresets = AppColors.SubtitleBackgroundColors

    // Audio / Sub Badges
    val DubBadge: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colors.primary

    val SubBadge: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colors.secondary

    // Sync & Subtitle Provider Brand Colors
    val BrandAniList = AppColors.BrandAniList
    val BrandMyAnimeList = AppColors.BrandMyAnimeList
    val BrandTrakt = AppColors.BrandTrakt
    val BrandSimkl = AppColors.BrandSimkl
    val BrandKitsu = AppColors.BrandKitsu
    val BrandOpenSubtitles = AppColors.BrandOpenSubtitles
    val BrandSubdl = AppColors.BrandSubdl
    val BrandAddic7ed = AppColors.BrandAddic7ed
    val BrandSubSource = AppColors.BrandSubSource
}

/**
 * Root theme composable for CloudStream multiplatform.
 * Injects both Material colors and CloudstreamExtendedColors via CompositionLocalProvider.
 */
@Composable
fun CloudStreamTheme(
    theme: AppTheme = AppTheme.SYSTEM,
    isDarkMode: Boolean = true,
    systemAccentColor: Color? = null,
    content: @Composable () -> Unit
) {
    val nativeTheme = rememberNativeSystemTheme()
    val effectiveAccent = systemAccentColor ?: nativeTheme.accentColor
    val effectiveDarkMode = if (theme == AppTheme.SYSTEM) {
        nativeTheme.isDarkMode ?: isDarkMode
    } else {
        isDarkMode
    }
    val materialColors = remember(theme, effectiveDarkMode, effectiveAccent) {
        AppColors.getMaterialColors(theme, effectiveDarkMode, effectiveAccent)
    }
    val extendedColors = remember(theme, effectiveDarkMode, effectiveAccent) {
        AppColors.getExtendedColors(theme, effectiveDarkMode, effectiveAccent)
    }
    // Typography builds the full 12-font ProductSans family; composable-only, resolved per theme composition.
    val typography = getAppTypography()

    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        MaterialTheme(
            colors = materialColors,
            typography = typography,
            shapes = CloudStreamShapes,
            content = content
        )
    }
}
