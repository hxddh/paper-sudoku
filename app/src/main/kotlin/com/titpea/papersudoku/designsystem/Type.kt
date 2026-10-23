@file:OptIn(ExperimentalTextApi::class)

package com.titpea.papersudoku.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.titpea.papersudoku.R

/*
 * Two voices only. Fraunces (SIL OFL 1.1, third_party/fraunces) is reserved for numerals: the
 * digits on the page and the dates on its corner. Everything you read is the platform sans, so
 * system text settings, CJK and the device's own typeface behave natively. The Fraunces files are
 * Latin subsets pinned to WONK=0 with opsz and wght still variable.
 */
private fun fraunces(weight: Int, opticalSize: Float, italic: Boolean = false) = Font(
    resId = if (italic) R.font.fraunces_italic else R.font.fraunces,
    weight = FontWeight(weight),
    style = if (italic) FontStyle.Italic else FontStyle.Normal,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.Setting("opsz", opticalSize),
    ),
)

/** Printed digits on the board (givens) and page numbers. */
val FrauncesPrinted = FontFamily(fraunces(560, 36f))

/** The player's pen. */
val FrauncesPen = FontFamily(fraunces(420, 36f, italic = true))

/** Large dates and figures. */
val FrauncesDisplay = FontFamily(fraunces(400, 96f), fraunces(600, 96f))

/**
 * The same cuts for code that draws with a Paint (widget, print), which would otherwise get the
 * variable font's default instance: Fraunces 9pt Black.
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
