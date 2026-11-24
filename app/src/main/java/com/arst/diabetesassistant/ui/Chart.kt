package com.arst.diabetesassistant.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.arst.diabetesassistant.data.GlucoseEntry
import kotlin.math.max
import kotlin.math.min

/** Simple glucose line chart over [days] days, with the target range shaded. Always drawn left->right in time. */
@Composable
fun GlucoseChart(entries: List<GlucoseEntry>, lowMgdl: Double, highMgdl: Double, days: Int = 7) {
    val now = System.currentTimeMillis()
    val start = now - days * 24L * 3600 * 1000
    val pts = entries.filter { it.time >= start }.sortedBy { it.time }
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val band = ColorOk.copy(alpha = 0.12f)

    Canvas(Modifier.fillMaxWidth().height(200.dp)) {
        val yMin = min(40.0, (pts.minOfOrNull { it.mgdl } ?: 40.0) - 10)
        val yMax = max(300.0, (pts.maxOfOrNull { it.mgdl } ?: 300.0) + 10)
        fun x(t: Long) = ((t - start).toFloat() / (now - start)) * size.width
        fun y(v: Double) = (size.height - ((v - yMin) / (yMax - yMin) * size.height)).toFloat()

        // target band
        drawRect(band, topLeft = Offset(0f, y(highMgdl)), size = Size(size.width, y(lowMgdl) - y(highMgdl)))
        // day separators
        for (d in 1 until days) {
            val gx = size.width * d / days
            drawLine(gridColor, Offset(gx, 0f), Offset(gx, size.height), strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
        }
        drawLine(ColorLow.copy(alpha = 0.6f), Offset(0f, y(lowMgdl)), Offset(size.width, y(lowMgdl)), 2f)
        drawLine(ColorHigh.copy(alpha = 0.6f), Offset(0f, y(highMgdl)), Offset(size.width, y(highMgdl)), 2f)

        if (pts.size >= 2) {
            val path = Path()
            pts.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(p.time), y(p.mgdl)) else path.lineTo(x(p.time), y(p.mgdl)) }
            drawPath(path, lineColor, style = Stroke(width = 3f))
        }
        pts.forEach { p ->
            val c: Color = when {
                p.mgdl < lowMgdl -> ColorLow
                p.mgdl > highMgdl -> ColorHigh
                else -> ColorOk
            }
            drawCircle(c, radius = 7f, center = Offset(x(p.time), y(p.mgdl)))
        }
    }
}
