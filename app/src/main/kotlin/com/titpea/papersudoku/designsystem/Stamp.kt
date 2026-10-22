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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A rubber ink stamp: double ring, a dashed inner band, and two short lines of text. When
 * [animate] is true it lands with a short overshoot, like a hand pressing it onto the page.
 */
@Composable
fun InkStamp(
    title: String,
    caption: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 168.dp,
    animate: Boolean = true,
) {
    val scale = remember { Animatable(if (animate) 1.6f else 1f) }
    val alpha = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        alpha.animateTo(1f, spring(stiffness = Spring.StiffnessMedium))
    }
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
    }
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value * 0.92f
                rotationZ = -10f
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
        val fontScale = LocalDensity.current.fontScale
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clearAndSetSemantics {},
        ) {
            Text(
                text = title,
                color = color,
                style = MaterialTheme.typography.headlineSmall,
                fontSize = (size.value / 7f / fontScale).sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Text(
                text = caption,
                color = color,
                style = MaterialTheme.typography.labelMedium,
                fontSize = (size.value / 14f / fontScale).sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}
