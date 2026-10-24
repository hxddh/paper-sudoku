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

/**
 * Fraunces has three uses only: the display date (today's, 44), figures (list dates, times, the
 * calendar, 28; below 20sp the printed opsz-36 cut, which holds up small) and the printed and pen
 * digits on the board and the keys. The stamp's capitals are the display cut at 600.
 */
object PaperType {
    /** Today's date on its card, e.g. "21": the one display numeral. */
    val pageNumeral = TextStyle(
        fontFamily = FrauncesDisplay,
        fontWeight = FontWeight.Normal,
        fontSize = 44.sp,
        lineHeight = 48.sp,
        letterSpacing = (-0.5).sp,
        fontFeatureSettings = "lnum, tnum",
    )

    /** Figures in the notebook: list dates, times, the calendar's month. */
    val figure = TextStyle(
        fontFamily = FrauncesDisplay,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        fontFeatureSettings = "lnum, tnum",
    )

    /**
     * Figures below 20sp (a calendar's day numbers): the printed cut, drawn for small sizes, on
     * the Support line's size. Copy it with another size up to 20sp; above that use [figure].
     */
    val figureSmall = TextStyle(
        fontFamily = FrauncesPrinted,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontFeatureSettings = "lnum, tnum",
    )
}

private val Sans = FontFamily.Default

/*
 * The platform sans in five styles, no more. Material's roles map onto them, so every component
 * inherits the scale: titles and headlines are Title, small titles and button labels Heading, body
 * text Body, smaller body Support, labels Caption.
 */
private val Title = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp)
private val Heading = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.sp)
private val Body = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.sp)
private val Support = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp)
private val Caption = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp)

/** The five sans styles by name, for code that means one of them rather than a Material role. */
object PaperText {
    val title = Title
    val heading = Heading
    val body = Body
    val support = Support
    val caption = Caption
}

/** Material's type roles, each one of the five styles. */
val PaperTypography = Typography(
    displayLarge = Title,
    displayMedium = Title,
    displaySmall = Title,
    headlineLarge = Title,
    headlineMedium = Title,
    headlineSmall = Title,
    titleLarge = Title,
    titleMedium = Heading,
    titleSmall = Heading,
    bodyLarge = Body,
    bodyMedium = Support,
    bodySmall = Support,
    labelLarge = Heading,
    labelMedium = Caption,
    labelSmall = Caption,
)
