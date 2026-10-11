package com.advanceai.calculator.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ---- Palette ---------------------------------------------------------------

val VioletDark = Color(0xFF7C6CFF)
val VioletLight = Color(0xFF5B4BE0)
val CyanAccent = Color(0xFF35C2F0)
val AmberAccent = Color(0xFFF0A93B)
val MagentaAccent = Color(0xFFE05FA8)
val GreenAccent = Color(0xFF3FD08B)

private val DarkColors = darkColorScheme(
    primary = VioletDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2A2A55),
    onPrimaryContainer = Color(0xFFDCD8FF),
    secondary = CyanAccent,
    onSecondary = Color(0xFF04121C),
    secondaryContainer = Color(0xFF17384A),
    onSecondaryContainer = Color(0xFFC8ECFB),
    tertiary = AmberAccent,
    onTertiary = Color(0xFF21160A),
    tertiaryContainer = Color(0xFF4A3413),
    onTertiaryContainer = Color(0xFFFFE3B8),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF2A0808),
    errorContainer = Color(0xFF4A1A1A),
    onErrorContainer = Color(0xFFFFD9D9),
    background = Color(0xFF0D0F1A),
    onBackground = Color(0xFFE7E9F3),
    surface = Color(0xFF141726),
    onSurface = Color(0xFFE7E9F3),
    surfaceVariant = Color(0xFF1D2135),
    onSurfaceVariant = Color(0xFFAEB4CC),
    surfaceContainer = Color(0xFF191D2E),
    surfaceContainerHigh = Color(0xFF1F2438),
    surfaceContainerHighest = Color(0xFF262B41),
    outline = Color(0xFF323854),
    outlineVariant = Color(0xFF232840),
    inverseSurface = Color(0xFFE7E9F3),
    inverseOnSurface = Color(0xFF141726)
)

private val LightColors = lightColorScheme(
    primary = VioletLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E2FF),
    onPrimaryContainer = Color(0xFF1D1454),
    secondary = Color(0xFF0E86C8),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6EEFB),
    onSecondaryContainer = Color(0xFF04303F),
    tertiary = Color(0xFFB4690E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE7C9),
    onTertiaryContainer = Color(0xFF3A2200),
    error = Color(0xFFD64545),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFF6F7FC),
    onBackground = Color(0xFF16182A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF16182A),
    surfaceVariant = Color(0xFFEDEFF7),
    onSurfaceVariant = Color(0xFF525872),
    surfaceContainer = Color(0xFFF1F3FA),
    surfaceContainerHigh = Color(0xFFEAEDF6),
    surfaceContainerHighest = Color(0xFFE3E7F2),
    outline = Color(0xFFD3D7E6),
    outlineVariant = Color(0xFFE4E7F1),
    inverseSurface = Color(0xFF16182A),
    inverseOnSurface = Color(0xFFF6F7FC)
)

private val AppTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium)
    )
}

/** Shared text styles for numbers and expressions. */
object NumberStyles {
    val display: TextStyle
        @Composable get() = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            fontSize = 34.sp,
            letterSpacing = (-0.5).sp
        )

    val expression: TextStyle
        @Composable get() = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium
        )

    val keyLabel: TextStyle
        @Composable get() = TextStyle(
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold
        )

    val keyFunction: TextStyle
        @Composable get() = TextStyle(
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
}

@Composable
fun MCalculatorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}
