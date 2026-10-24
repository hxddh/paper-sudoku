package com.titpea.papersudoku.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.titpea.papersudoku.R

/*
 * Two voices only. Fraunces (SIL OFL 1.1, third_party/fraunces) is reserved for numerals: the
 * digits on the page and the dates on its corner. Everything you read is the platform sans, so
 * system text settings, CJK and the device's own typeface behave natively.
 *
 * 10.0: each cut is a static instance (fontTools instancer, WONK=0) of the Latin subset at exactly
 * the weight and optical size the app sets, so nothing is interpolated at run time:
 * - fraunces: 560, opsz 36 (printed digits, page numbers, the digit keys);
 * - fraunces_italic: Italic 420, opsz 36, digits only (the pen writes nothing else);
 * - fraunces_display / fraunces_display_semibold: 400 and 600, opsz 96 (dates, figures, stamps).
 * 182 KB of variable fonts became 100 KB; outlines differ by at most 1.25 of 2000 units.
 */
private fun fraunces(resId: Int, weight: Int, italic: Boolean = false) = Font(
    resId = resId,
    weight = FontWeight(weight),
    style = if (italic) FontStyle.Italic else FontStyle.Normal,
)

/** Printed digits on the board (givens) and page numbers. */
val FrauncesPrinted = FontFamily(fraunces(R.font.fraunces, 560))

/** The player's pen: digits only. */
val FrauncesPen = FontFamily(fraunces(R.font.fraunces_italic, 420, italic = true))

/** Large dates and figures. */
val FrauncesDisplay = FontFamily(fraunces(R.font.fraunces_display, 400), fraunces(R.font.fraunces_display_semibold, 600))

/**
 * The cuts for code that draws with a Paint (widget, print): R.font.fraunces is the printed cut,
 * R.font.fraunces_italic the pen and R.font.fraunces_display the display cut. The fonts are
 * static now, so these settings change nothing; they are kept so a variable font dropped in
 * again would still be drawn at the same instance.
 */
object FrauncesAxes {
    const val PRINTED = "'wght' 560, 'opsz' 36"
    const val PEN = "'wght' 420, 'opsz' 36"
    const val DISPLAY = "'wght' 400, 'opsz' 96"
}

object PaperType {
    /** The date on a page corner, e.g. "21". */
    val pageNumeral = TextStyle(
        fontFamily = FrauncesDisplay,
        fontWeight = FontWeight.Normal,
        fontSize = 44.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.5).sp,
        fontFeatureSettings = "lnum, tnum",
    )

    /** Figures in the notebook (stamped pages, times). */
    val figure = TextStyle(
        fontFamily = FrauncesDisplay,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 36.sp,
        fontFeatureSettings = "lnum, tnum",
    )
}

private val Sans = FontFamily.Default

/** The Material 3 type scale in the platform sans. */
val PaperTypography = Typography().let { base ->
    Typography(
        displayLarge = base.displayLarge.copy(fontFamily = Sans),
        displayMedium = base.displayMedium.copy(fontFamily = Sans),
        displaySmall = base.displaySmall.copy(fontFamily = Sans),
        headlineLarge = base.headlineLarge.copy(fontFamily = Sans, fontWeight = FontWeight.Medium),
        headlineMedium = base.headlineMedium.copy(fontFamily = Sans, fontWeight = FontWeight.Medium),
        headlineSmall = base.headlineSmall.copy(fontFamily = Sans, fontWeight = FontWeight.Medium),
        titleLarge = base.titleLarge.copy(fontFamily = Sans, fontWeight = FontWeight.Medium),
        titleMedium = base.titleMedium.copy(fontFamily = Sans),
        titleSmall = base.titleSmall.copy(fontFamily = Sans),
        bodyLarge = base.bodyLarge.copy(fontFamily = Sans),
        bodyMedium = base.bodyMedium.copy(fontFamily = Sans),
        bodySmall = base.bodySmall.copy(fontFamily = Sans),
        labelLarge = base.labelLarge.copy(fontFamily = Sans),
        labelMedium = base.labelMedium.copy(fontFamily = Sans),
        labelSmall = base.labelSmall.copy(fontFamily = Sans),
    )
}
