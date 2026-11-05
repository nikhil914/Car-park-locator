package com.realworldmagic.parkpin.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Animated figure-8 hint shown when the magnetometer reports low accuracy. */
@Composable
fun CompassCalibrationDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Calibrate compass") },
        text = {
            Column {
                FigureEightAnimation(Modifier.fillMaxWidth().height(160.dp))
                Spacer(Modifier.height(12.dp))
                Text(
                    "Move your phone in a figure-8 a few times, away from metal and magnets. " +
                        "The arrow will become more accurate.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
fun FigureEightAnimation(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "fig8")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "t",
    )
    val trail = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
    val phone = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val a = minOf(size.width * 0.38f, size.height * 0.8f)
        // Lemniscate of Gerono: x = a cos t, y = a sin t cos t
        fun pt(tt: Float) = Offset(cx + a * cos(tt), cy + a * sin(tt) * cos(tt))
        val path = Path()
        val steps = 80
        for (i in 0..steps) {
            val p = pt((i / steps.toFloat()) * 2f * PI.toFloat())
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        drawPath(path, trail, style = Stroke(width = 4.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))
        val p = pt(t)
        val phoneW = 22.dp.toPx()
        val phoneH = 38.dp.toPx()
        rotate(degrees = (sin(t) * 35f), pivot = p) {
            drawRoundRect(
                phone,
                topLeft = Offset(p.x - phoneW / 2, p.y - phoneH / 2),
                size = Size(phoneW, phoneH),
                cornerRadius = CornerRadius(5.dp.toPx()),
            )
        }
    }
}
