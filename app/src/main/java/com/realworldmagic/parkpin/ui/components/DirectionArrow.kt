package com.realworldmagic.parkpin.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Canvas-drawn navigation arrow. [rotationDeg] is the relative bearing (0 = straight ahead,
 * positive = clockwise). Rotation is animated along the shortest path so 359 -> 1 does not spin.
 */
@Composable
fun DirectionArrow(
    rotationDeg: Float?,
    modifier: Modifier = Modifier,
    size: Dp = 240.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    ringColor: Color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f),
) {
    val anim = remember { Animatable(rotationDeg ?: 0f) }
    LaunchedEffect(rotationDeg) {
        if (rotationDeg == null) return@LaunchedEffect
        val current = anim.value
        var delta = (rotationDeg - current) % 360f
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        anim.animateTo(current + delta, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow))
    }
    val active = rotationDeg != null
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val c = Offset(w / 2f, h / 2f)
        val r = minOf(w, h) / 2f
        // Outer ring with tick marks.
        drawCircle(ringColor, radius = r * 0.96f, center = c, style = Stroke(width = r * 0.04f))
        for (i in 0 until 12) {
            rotate(i * 30f, c) {
                drawLine(
                    ringColor,
                    start = Offset(c.x, c.y - r * 0.96f),
                    end = Offset(c.x, c.y - r * (if (i % 3 == 0) 0.82f else 0.88f)),
                    strokeWidth = r * 0.025f,
                )
            }
        }
        rotate(anim.value, c) {
            val path = Path().apply {
                moveTo(c.x, c.y - r * 0.78f)          // tip
                lineTo(c.x + r * 0.46f, c.y + r * 0.52f)
                lineTo(c.x, c.y + r * 0.28f)          // notch
                lineTo(c.x - r * 0.46f, c.y + r * 0.52f)
                close()
            }
            val alpha = if (active) 1f else 0.3f
            drawPath(
                path,
                brush = Brush.verticalGradient(
                    listOf(color.copy(alpha = alpha), color.copy(alpha = alpha * 0.55f)),
                    startY = c.y - r * 0.78f,
                    endY = c.y + r * 0.52f,
                ),
            )
            drawPath(path, Color.White.copy(alpha = 0.25f * alpha), style = Stroke(width = r * 0.02f))
        }
    }
}
