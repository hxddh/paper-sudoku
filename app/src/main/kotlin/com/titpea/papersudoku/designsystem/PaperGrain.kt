package com.titpea.papersudoku.designsystem

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import kotlin.math.floor
import kotlin.math.roundToInt

/*
 * A very faint paper fibre texture, on Android 13+ only: the flat Paper color is the designed
 * fallback on older versions, not a degraded state.
 *
 * The noise (a per-pixel hash, plus a coarser one stretched into horizontal fibres) is computed
 * once on the CPU into a small tile, which the page repeats as a bitmap shader: drawing it costs a
 * texture lookup per pixel instead of running a shader program over the whole screen every frame.
 * The tile is TILE pixels square, a whole number of fibre cells in both directions (12.5 × 1⅔ px),
 * so the fibres stay unbroken where tiles meet.
 */
private const val TILE = 250

/** Null where the grain is not drawn (before Android 13). */
@Composable
fun rememberPaperGrain(ink: PaperInk, strength: Float = 0.045f): ShaderBrush? =
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        null
    } else {
        remember(ink.grain, strength) { grainBrush(ink, strength) }
    }

fun Modifier.paperGrain(brush: ShaderBrush?): Modifier =
    if (brush == null) this else drawBehind { drawRect(brush) }

private fun grainBrush(ink: PaperInk, strength: Float): ShaderBrush {
    val rgb = ink.grain.toArgb() and 0xFFFFFF
    val pixels = IntArray(TILE * TILE)
    for (y in 0 until TILE) {
        // Pixel centers, as a fragment shader sees them.
        val cy = y + 0.5f
        val fibreY = floor(cy * 0.6f)
        for (x in 0 until TILE) {
            val cx = x + 0.5f
            val fine = hash(x.toFloat(), y.toFloat())
            val fibre = hash(floor(cx * 0.08f), fibreY)
            val n = fine * 0.65f + fibre * 0.35f
            // The ink color at alpha strength × n (unpremultiplied; the bitmap premultiplies).
            val alpha = (n * strength * 255f).roundToInt().coerceIn(0, 255)
            pixels[y * TILE + x] = (alpha shl 24) or rgb
        }
    }
    val bitmap = Bitmap.createBitmap(pixels, TILE, TILE, Bitmap.Config.ARGB_8888)
    return ShaderBrush(ImageShader(bitmap.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
}

private fun fract(v: Float): Float = v - floor(v)

/** The 2D → 1D hash the grain was designed with ("hash without sine"). */
private fun hash(px: Float, py: Float): Float {
    var x = fract(px * 0.1031f)
    var y = fract(py * 0.1030f)
    val d = x * (y + 33.33f) + y * (x + 33.33f)
    x += d
    y += d
    return fract((x + y) * x)
}
