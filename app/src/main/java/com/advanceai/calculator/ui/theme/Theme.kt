package com.advanceai.calculator.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.advanceai.calculator.data.AppThemeMode

/*
 * Every colour the app draws comes from here, and every pair below is meant to
 * be readable: `tools/check_theme.py` measures the contrast of each
 * (text, background) pair the screens actually use and fails the build when a
 * pair drops under its limit. That is how "some text is not visible in dark or
 * light" stays fixed instead of coming back with the next screen.
 *
 * Two rules for anyone editing this file:
 *   1. never paint a colour that is not in this scheme (`Color(0x…)` anywhere
 *      else in the app is a bug — the audit script looks for it);
 *   2. put text on the matching container role, e.g. onPrimaryContainer goes on
 *      primaryContainer, onSurfaceVariant on a surface, and so on.
 */

// ---- Dark theme ------------------------------------------------------------ //

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8E7BFF),
    onPrimary = Color(0xFF160B45),
    primaryContainer = Color(0xFF2C2A57),
    onPrimaryContainer = Color(0xFFE4E0FF),
    inversePrimary = Color(0xFF5140D6),
    secondary = Color(0xFF7CD6F7),
    onSecondary = Color(0xFF04252F),
    secondaryContainer = Color(0xFF1B3B4C),
    onSecondaryContainer = Color(0xFFCFEEFB),
    tertiary = Color(0xFFFFC266),
    onTertiary = Color(0xFF2B1B00),
    tertiaryContainer = Color(0xFF4A3413),
    onTertiaryContainer = Color(0xFFFFE3B8),
    background = Color(0xFF0D0F1A),
    onBackground = Color(0xFFE7E9F3),
    surface = Color(0xFF141726),
    onSurface = Color(0xFFE7E9F3),
    surfaceVariant = Color(0xFF232840),
    onSurfaceVariant = Color(0xFFBAC0D8),
    surfaceTint = Color(0xFF8E7BFF),
    inverseSurface = Color(0xFFE7E9F3),
    inverseOnSurface = Color(0xFF141726),
    error = Color(0xFFFF8080),
    onError = Color(0xFF3A0A0A),
    errorContainer = Color(0xFF55201F),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF3B4163),
    outlineVariant = Color(0xFF272C45),
    scrim = Color(0xFF000000),
    surfaceDim = Color(0xFF0B0D16),
    surfaceBright = Color(0xFF2E3349),
    surfaceContainerLowest = Color(0xFF0F1220),
    surfaceContainerLow = Color(0xFF121522),
    surfaceContainer = Color(0xFF191D2E),
    surfaceContainerHigh = Color(0xFF20253A),
    surfaceContainerHighest = Color(0xFF272C43)
)

// ---- Light theme ----------------------------------------------------------- //

private val LightColors = lightColorScheme(
    primary = Color(0xFF5140D6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE4E0FF),
    onPrimaryContainer = Color(0xFF1A1148),
    inversePrimary = Color(0xFFA99CFF),
    secondary = Color(0xFF0B6D9C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD2ECFA),
    onSecondaryContainer = Color(0xFF05303F),
    tertiary = Color(0xFF97580A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE6C6),
    onTertiaryContainer = Color(0xFF351F00),
    background = Color(0xFFF5F6FB),
    onBackground = Color(0xFF171A2B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171A2B),
    surfaceVariant = Color(0xFFE4E8F3),
    onSurfaceVariant = Color(0xFF4A5168),
    surfaceTint = Color(0xFF5140D6),
    inverseSurface = Color(0xFF22263A),
    inverseOnSurface = Color(0xFFF2F4FA),
    error = Color(0xFFC0362F),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410E0B),
    outline = Color(0xFFB9C0D4),
    outlineVariant = Color(0xFFDDE2EE),
    scrim = Color(0xFF000000),
    surfaceDim = Color(0xFFE6E9F3),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F9FD),
    surfaceContainer = Color(0xFFF0F2FA),
    surfaceContainerHigh = Color(0xFFE9EDF7),
    surfaceContainerHighest = Color(0xFFE2E7F3)
)

// ---- Roles Material 3 has no slot for -------------------------------------- //

/** Colours the Material scheme does not cover, provided through the theme. */
@Immutable
data class AppColors(val chart: List<Color>)

private val DarkAppColors = AppColors(
    chart = listOf(
        Color(0xFF9B8BFF),   // violet
        Color(0xFF5FD3F5),   // cyan
        Color(0xFF62DDA6),   // green
        Color(0xFFFFC266),   // amber
        Color(0xFFFF93C8)    // pink
    )
)

private val LightAppColors = AppColors(
    chart = listOf(
        Color(0xFF5140D6),   // violet
        Color(0xFF0B6D9C),   // cyan
        Color(0xFF10704A),   // green
        Color(0xFF8A4E00),   // amber
        Color(0xFFAA2E70)    // pink
    )
)

/**
 * The one chip style used across the app. Material 3's out-of-the-box filter
 * chip keeps *both* states transparent, so a selected page/unit chip looked
 * exactly like an unselected one; here the selection is a filled container plus
 * the check icon the component adds by itself.
 */
@Composable
fun appChipColors(): SelectableChipColors {
    val colors = MaterialTheme.colorScheme
    return FilterChipDefaults.filterChipColors(
        selectedContainerColor = colors.secondaryContainer,
        selectedLabelColor = colors.onSecondaryContainer,
        selectedLeadingIconColor = colors.onSecondaryContainer,
        selectedTrailingIconColor = colors.onSecondaryContainer
    )
}

/** Chart and plot colours for the current theme. */
val chartColors: List<Color>
    @Composable get() = LocalAppColors.current.chart

private val LocalAppColors = staticCompositionLocalOf { DarkAppColors }

// ---- Type ------------------------------------------------------------------ //

private val AppTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium),
        bodySmall = base.bodySmall.copy(lineHeight = 17.sp, textAlign = TextAlign.Start)
    )
}

/**
 * The app theme. [mode] decides which palette is used: the system setting, or
 * one theme regardless of the system (the "Appearance" switch in the menu).
 */
@Composable
fun MCalculatorTheme(
    mode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val dark = when (mode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    // The status and navigation bar icons follow the *chosen* theme, not the
    // system one: forcing Light while the phone is in dark mode used to leave
    // the clock and battery icons invisible against the light background.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }

    CompositionLocalProvider(
        LocalAppColors provides if (dark) DarkAppColors else LightAppColors
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            content = content
        )
    }
}
