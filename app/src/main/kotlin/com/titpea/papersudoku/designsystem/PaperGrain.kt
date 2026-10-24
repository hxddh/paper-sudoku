package com.titpea.papersudoku.designsystem

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor
import kotlin.math.roundToInt

/*
 * Paper: the page colour with a very faint fibre texture baked in, on Android 13+ only (the flat
 * colour is the designed fallback on older versions, not a degraded state).
 *
 * The noise (a per-cell hash, plus a coarser one stretched into horizontal fibres) is computed once
 * on the CPU, already mixed into the paper colour, into one opaque tile that the surface repeats as
 * a bitmap shader. The tile is the surface's only fill: nothing is drawn under it and nothing is
 * blended over it. It is TILE_DP square at any density and a whole number of noise cells (UNITS
 * across) and of fibre cells (12.5 × 1⅔ units), so the fibres stay unbroken where tiles meet.
 */
private const val TILE_DP = 100f
private const val UNITS = 250
private const val STRENGTH = 0.045f

/**
 * Fills the element with paper of [color]: its only background, in place of a background with a
 * grain drawn over it. A tile is made off the main thread the first time its colour is asked for
 * (the flat colour shows for the frames until it is ready) and is shared by every surface after.
 */
fun Modifier.paper(color: Color): Modifier = this then PaperElement(color)

private data class PaperElement(val color: Color) : ModifierNodeElement<PaperNode>() {
    override fun create() = PaperNode(color)

    override fun update(node: PaperNode) {
        node.color = color
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "paper"
        properties["color"] = color
    }
}

private class PaperNode(var color: Color) : Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {
    private var making: Tile? = null

    override fun onAttach() {
        tile()?.let { if (PaperTiles.get(it) == null) make(it) }
    }

    override fun ContentDrawScope.draw() {
        val brush = tile()?.let { tile -> PaperTiles.get(tile).also { if (it == null) make(tile) } }
        if (brush != null) drawRect(brush) else drawRect(color)
        drawContent()
    }

    /** The tile to draw, or null where paper is flat. Read in draw, so a theme change redraws. */
    private fun tile(): Tile? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val px = (TILE_DP * currentValueOf(LocalDensity).density).roundToInt().coerceAtLeast(1)
        return Tile(color.toArgb(), currentValueOf(LocalPaperInk).grain.toArgb(), px)
    }

    private fun make(tile: Tile) {
        if (making == tile) return
        making = tile
        coroutineScope.launch {
            withContext(Dispatchers.Default) { PaperTiles.make(tile) }
            invalidateDraw()
        }
    }
}

private data class Tile(val paper: Int, val grain: Int, val px: Int)

/** The tiles made so far: a handful at most (desk and sheet, day and night). */
private object PaperTiles {
    private val made = ConcurrentHashMap<Tile, ShaderBrush>()

    fun get(tile: Tile): ShaderBrush? = made[tile]

    fun make(tile: Tile): ShaderBrush = made[tile] ?: bake(tile).also {
        if (made.size >= 8) made.clear()
        made[tile] = it
    }

    private fun bake(tile: Tile): ShaderBrush {
        val px = tile.px
        val pixels = IntArray(px * px)
        // Tile pixels to noise units: the texture keeps its size in dp at every density.
        val scale = UNITS.toFloat() / px
        for (y in 0 until px) {
            // Pixel centres, as a fragment shader sees them.
            val vy = (y + 0.5f) * scale
            val fineY = floor(vy)
            val fibreY = floor(vy * 0.6f)
            for (x in 0 until px) {
                val vx = (x + 0.5f) * scale
                val n = hash(floor(vx), fineY) * 0.65f + hash(floor(vx * 0.08f), fibreY) * 0.35f
                pixels[y * px + x] = mix(tile.paper, tile.grain, n * STRENGTH)
            }
        }
        val bitmap = Bitmap.createBitmap(pixels, px, px, Bitmap.Config.ARGB_8888)
        return ShaderBrush(ImageShader(bitmap.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
    }
}

/** [grain] over [paper] at [amount], premixed: as opaque as the paper. */
private fun mix(paper: Int, grain: Int, amount: Float): Int {
    fun channel(shift: Int): Int {
        val p = (paper shr shift) and 0xFF
        val g = (grain shr shift) and 0xFF
        return (p + (g - p) * amount).roundToInt().coerceIn(0, 255) shl shift
    }
    return (0xFF shl 24) or channel(16) or channel(8) or channel(0)
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

/** 10.0's grain, drawn over a background. Null where it is not drawn (before Android 13). */
@Deprecated("Fill with Modifier.paper(color): one opaque layer instead of a background plus the grain.")
@Composable
fun rememberPaperGrain(ink: PaperInk): ShaderBrush? =
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) null else remember(ink.grain) { overlay(ink.grain) }

@Deprecated("Fill with Modifier.paper(color): one opaque layer instead of a background plus the grain.")
fun Modifier.paperGrain(brush: ShaderBrush?): Modifier =
    if (brush == null) this else drawBehind { drawRect(brush) }

private fun overlay(grain: Color): ShaderBrush {
    val rgb = grain.toArgb() and 0xFFFFFF
    val pixels = IntArray(UNITS * UNITS)
    for (y in 0 until UNITS) {
        for (x in 0 until UNITS) {
            val n = hash(x.toFloat(), y.toFloat()) * 0.65f + hash(floor((x + 0.5f) * 0.08f), floor((y + 0.5f) * 0.6f)) * 0.35f
            pixels[y * UNITS + x] = ((n * STRENGTH * 255f).roundToInt().coerceIn(0, 255) shl 24) or rgb
        }
    }
    val bitmap = Bitmap.createBitmap(pixels, UNITS, UNITS, Bitmap.Config.ARGB_8888)
    return ShaderBrush(ImageShader(bitmap.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
}
