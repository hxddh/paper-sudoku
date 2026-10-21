package com.titpea.papersudoku.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Paper / Night tokens.
 *
 * The accent and muted colors are deliberately darker than the first visual prototype:
 * #A8442F on #F4EFE5 is 5.19:1, and #5F574C is 6.2:1, so small text passes WCAG AA.
 * The prototype's #C2543C / #7A7165 did not.
 *
 * Fraunces / Figtree are replaced with platform serif/sans here so the skeleton has no binary
 * font assets. Bundle the real fonts (OFL) and replace displayFamily/uiFamily when ready.
 */
private val displayFamily = FontFamily.Serif
private val uiFamily = FontFamily.SansSerif

private val PaperLight = lightColorScheme(
    primary = Color(0xFFA8442F),
    onPrimary = Color(0xFFFFF9F3),
    primaryContainer = Color(0xFFF0D6C8),
    onPrimaryContainer = Color(0xFF3A1B12),
    secondary = Color(0xFF1F6F74),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6E9E7),
    onSecondaryContainer = Color(0xFF0B2A2C),
    background = Color(0xFFF4EFE5),
    onBackground = Color(0xFF1F1C18),
    surface = Color(0xFFFBF7EF),
    onSurface = Color(0xFF1F1C18),
    surfaceVariant = Color(0xFFEFE7DA),
    onSurfaceVariant = Color(0xFF5F574C),
    outline = Color(0xFF8E8273),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

private val PaperDark = darkColorScheme(
    primary = Color(0xFFE0A458),
    onPrimary = Color(0xFF17130D),
    primaryContainer = Color(0xFF4A3A22),
    onPrimaryContainer = Color(0xFFFFDDB0),
    secondary = Color(0xFF8FD0E0),
    onSecondary = Color(0xFF0B2A30),
    secondaryContainer = Color(0xFF203C42),
    onSecondaryContainer = Color(0xFFC5E9F0),
    background = Color(0xFF111417),
    onBackground = Color(0xFFF2EEE5),
    surface = Color(0xFF1A1E23),
    onSurface = Color(0xFFF2EEE5),
    surfaceVariant = Color(0xFF2A3038),
    onSurfaceVariant = Color(0xFFA7B0B9),
    outline = Color(0xFF8A949F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

private val SudokuTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = displayFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = displayFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 30.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.3).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = uiFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = uiFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = uiFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = uiFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.8.sp,
    ),
)

private val SudokuShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun SudokuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) PaperDark else PaperLight,
        typography = SudokuTypography,
        shapes = SudokuShapes,
        content = content,
    )
}
