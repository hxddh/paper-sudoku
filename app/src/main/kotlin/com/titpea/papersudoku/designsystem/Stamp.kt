package com.titpea.papersudoku.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

/**
 * A rubber ink stamp: double ring, a dashed inner band, and its word ([caption] under it, if
 * any). When [animate] is true it is pressed onto the page: from 1.06 to rest, without a bounce.
 *
 * It is ink on the page, not a sticker over it: it multiplies into the paper (screens on the night
 * page), so the digits beneath show through. [seed] (the page) sets its slight tilt and offset, so
 * no two pages are stamped quite alike, and the same page always is.
 */
@Composable
fun InkStamp(
    title: String,
    modifier: Modifier = Modifier,
    /** A second, smaller line; the page's stamp has none (the note beside it says the time). */
    caption: String? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 168.dp,
    animate: Boolean = true,
    seed: Int = 0,
    /** Called once when the stamp comes to rest (for the landing's haptic). */
    onLanded: () -> Unit = {},
) {
    val hand = remember(seed) { java.util.Random(seed.toLong()) }
    val tilt = remember(seed) { -14f + 8f * hand.nextFloat() }
    val nudgeX = remember(seed) { (hand.nextFloat() - 0.5f) * 12f }
    val nudgeY = remember(seed) { (hand.nextFloat() - 0.5f) * 12f }
    val night = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // Landed once, landed for good: the flag survives rotation, so a turned phone never stamps
    // the page a second time.
    var landed by rememberSaveable { mutableStateOf(!animate) }
    val scale = remember { Animatable(if (landed) 1f else 1.06f) }
    val alpha = remember { Animatable(if (landed) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (landed) return@LaunchedEffect
        launch { alpha.animateTo(1f, spring(stiffness = Spring.StiffnessMedium)) }
        // Pressed, not dropped: critically damped, so it never springs back past the page.
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))
        landed = true
        onLanded()
    }
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
                rotationZ = tilt
                translationX = nudgeX.dp.toPx()
                translationY = nudgeY.dp.toPx()
                blendMode = if (night) BlendMode.Screen else BlendMode.Multiply
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val r = this.size.minDimension / 2f
            val c = Offset(this.size.width / 2f, this.size.height / 2f)
            drawCircle(color, radius = r - 3.dp.toPx(), center = c, style = Stroke(3.dp.toPx()))
            drawCircle(color, radius = r - 9.dp.toPx(), center = c, style = Stroke(1.dp.toPx()))
            drawCircle(
                color,
                radius = r - 15.dp.toPx(),
                center = c,
                style = Stroke(
                    width = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 4.dp.toPx())),
                ),
            )
        }
        // The stamp is a picture: its lettering is sized to the ring, not to the font scale, and the
        // solved note beside the board says the same thing to TalkBack.
        // Dp → sp through the density, which also undoes Android 14's non-linear font scaling.
        val density = LocalDensity.current
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clearAndSetSemantics {},
        ) {
            // Cut letters, as on a rubber stamp: Fraunces capitals, spaced (CJK keeps its own face).
            Text(
                text = title.uppercase(),
                color = color,
                style = TextStyle(
                    fontFamily = FrauncesDisplay,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.12.em,
                ),
                fontSize = with(density) { (size / 7.5f).toSp() },
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            if (caption != null) {
                Text(
                    text = caption,
                    color = color,
                    style = TextStyle(fontFamily = FrauncesDisplay, fontFeatureSettings = "lnum, tnum"),
                    fontSize = with(density) { (size / 9f).toSp() },
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}
