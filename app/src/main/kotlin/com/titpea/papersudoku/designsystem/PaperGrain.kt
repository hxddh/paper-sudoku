package com.titpea.papersudoku.designsystem

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb

/*
 * A very faint paper fibre texture. AGSL on Android 13+, nothing on older versions: the flat
 * Paper color is the designed fallback, not a degraded state.
 */
private const val GRAIN_AGSL = """
    uniform half4 ink;
    float hash(float2 p) {
        p = fract(p * float2(0.1031, 0.1030));
        p += dot(p, p.yx + 33.33);
        return fract((p.x + p.y) * p.x);
    }
    half4 main(float2 coord) {
        float fine = hash(floor(coord));
        float fibre = hash(floor(coord * float2(0.08, 0.6)));
        float n = fine * 0.65 + fibre * 0.35;
        return ink * half(n);
    }
"""

/** Null where the grain is not drawn (before Android 13, or with Material You colors). */
@Composable
fun rememberPaperGrain(ink: PaperInk, strength: Float = 0.045f): ShaderBrush? =
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !ink.isPaper) {
        null
    } else {
        remember(ink.grain, strength) { grainBrush(ink, strength) }
    }

fun Modifier.paperGrain(brush: ShaderBrush?): Modifier =
    if (brush == null) this else drawBehind { drawRect(brush) }

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun grainBrush(ink: PaperInk, strength: Float): ShaderBrush {
    val shader = RuntimeShader(GRAIN_AGSL)
    val argb = ink.grain.toArgb()
    val r = ((argb shr 16) and 0xFF) / 255f
    val g = ((argb shr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    // Premultiplied: color × alpha.
    shader.setFloatUniform("ink", r * strength, g * strength, b * strength, strength)
    return ShaderBrush(shader)
}
