package com.titpea.papersudoku.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Paper (light) and Night (dark), with every Material 3 color role defined so no component
 * falls back to the baseline purple. Contrast of text roles on their surfaces is ≥ 4.5:1.
 */
private val Paper = lightColorScheme(
    primary = Color(0xFFA8442F),
    onPrimary = Color(0xFFFFF9F3),
    primaryContainer = Color(0xFFF6DCD0),
    onPrimaryContainer = Color(0xFF3A1B12),
    inversePrimary = Color(0xFFFFB4A1),
    secondary = Color(0xFF2D5577),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD8E4EE),
    onSecondaryContainer = Color(0xFF0E2436),
    tertiary = Color(0xFF5E6B3A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE2E8C8),
    onTertiaryContainer = Color(0xFF1B2208),
    background = Color(0xFFF4EFE5),
    onBackground = Color(0xFF1F1C18),
    surface = Color(0xFFF4EFE5),
    onSurface = Color(0xFF1F1C18),
    surfaceVariant = Color(0xFFE9E1D3),
    onSurfaceVariant = Color(0xFF5F574C),
    surfaceTint = Color(0xFFA8442F),
    inverseSurface = Color(0xFF34302A),
    inverseOnSurface = Color(0xFFF7F0E6),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    outline = Color(0xFF8E8273),
    outlineVariant = Color(0xFFD3C8B8),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFBF7EF),
    surfaceDim = Color(0xFFE3DCD0),
    surfaceContainerLowest = Color(0xFFFFFDF8),
    surfaceContainerLow = Color(0xFFF8F3EA),
    surfaceContainer = Color(0xFFF1EBE0),
    surfaceContainerHigh = Color(0xFFEBE4D8),
    surfaceContainerHighest = Color(0xFFE5DED1),
)

private val Night = darkColorScheme(
    primary = Color(0xFFEFA489),
    onPrimary = Color(0xFF4A1A0C),
    primaryContainer = Color(0xFF6B2D1C),
    onPrimaryContainer = Color(0xFFFFDBCF),
    inversePrimary = Color(0xFFA8442F),
    secondary = Color(0xFFA9C8E8),
    onSecondary = Color(0xFF0F2F48),
    secondaryContainer = Color(0xFF2A4964),
    onSecondaryContainer = Color(0xFFD2E5F7),
    tertiary = Color(0xFFC3CF9B),
    onTertiary = Color(0xFF2E3611),
    tertiaryContainer = Color(0xFF444D25),
    onTertiaryContainer = Color(0xFFDFEBB5),
    background = Color(0xFF14130F),
    onBackground = Color(0xFFEDE6DA),
    surface = Color(0xFF14130F),
    onSurface = Color(0xFFEDE6DA),
    surfaceVariant = Color(0xFF3A362E),
    onSurfaceVariant = Color(0xFFCFC5B5),
    surfaceTint = Color(0xFFEFA489),
    inverseSurface = Color(0xFFEDE6DA),
    inverseOnSurface = Color(0xFF322F29),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF9A9080),
    outlineVariant = Color(0xFF4B463D),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF3B3933),
    surfaceDim = Color(0xFF14130F),
    surfaceContainerLowest = Color(0xFF0F0E0A),
    surfaceContainerLow = Color(0xFF1D1B17),
    surfaceContainer = Color(0xFF211F1A),
    surfaceContainerHigh = Color(0xFF2C2A24),
    surfaceContainerHighest = Color(0xFF37342E),
)

private val PaperShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Roles the board needs beyond Material's: the three writing layers and the coach marks. */
@Immutable
data class PaperInk(
    val printed: Color,
    val pen: Color,
    val pencil: Color,
    val gridLine: Color,
    val boxLine: Color,
    val sheet: Color,
    val grain: Color,
    val isPaper: Boolean,
)

val LocalPaperInk = staticCompositionLocalOf {
    PaperInk(Color.Black, Color.Blue, Color.Gray, Color.Gray, Color.Black, Color.White, Color.Black, true)
}

private fun inkFor(scheme: ColorScheme, dark: Boolean, isPaper: Boolean) = PaperInk(
    printed = scheme.onSurface,
    pen = scheme.secondary,
    pencil = scheme.onSurfaceVariant,
    gridLine = scheme.outlineVariant,
    boxLine = scheme.outline,
    sheet = scheme.surfaceContainerLowest,
    grain = if (dark) Color.White else Color(0xFF3B2A14),
    isPaper = isPaper,
)

@Composable
fun SudokuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val scheme = when {
        useDynamic && darkTheme -> dynamicDarkColorScheme(LocalContext.current)
        useDynamic -> dynamicLightColorScheme(LocalContext.current)
        darkTheme -> Night
        else -> Paper
    }
    CompositionLocalProvider(LocalPaperInk provides inkFor(scheme, darkTheme, isPaper = !useDynamic)) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = MotionScheme.expressive(),
            typography = PaperTypography,
            shapes = PaperShapes,
            content = content,
        )
    }
}

object PaperTheme {
    val ink: PaperInk
        @Composable get() = LocalPaperInk.current
    val colors: ColorScheme
        @Composable get() = MaterialTheme.colorScheme
}
