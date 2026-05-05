package dev.tracely.ui.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class GanttItem(
    val label: String,
    val startMs: Double,
    val durationMs: Double,
    val color: Color = Color(0xFF4F8CFF),
)

@Composable
fun GanttChart(
    items: List<GanttItem>,
    modifier: Modifier = Modifier,
    title: String = "",
) {
    if (items.isEmpty()) return

    val minStart = items.minOf { it.startMs }
    val maxEnd = items.maxOf { it.startMs + it.durationMs }
    val totalRange = (maxEnd - minStart).coerceAtLeast(1.0)
    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurface

    Column(modifier) {
        if (title.isNotEmpty()) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
        }

        Canvas(Modifier.fillMaxWidth().height((items.size * 28 + 16).dp)) {
            val labelWidth = 180f
            val chartWidth = size.width - labelWidth - 20f
            val barHeight = 18f
            val gap = 10f

            items.forEachIndexed { i, item ->
                val y = i * (barHeight + gap) + 8f

                // Label
                val labelResult = textMeasurer.measure(
                    text = item.label.take(22),
                    style = TextStyle(fontSize = 11.sp, color = labelColor),
                )
                drawText(labelResult, topLeft = Offset(0f, y + (barHeight - labelResult.size.height) / 2))

                // Bar positioned by start time
                val xStart = ((item.startMs - minStart) / totalRange * chartWidth).toFloat()
                val barW = (item.durationMs / totalRange * chartWidth).toFloat().coerceAtLeast(3f)

                drawRoundRect(
                    color = item.color,
                    topLeft = Offset(labelWidth + xStart, y),
                    size = Size(barW, barHeight),
                    cornerRadius = CornerRadius(3f),
                )

                // Duration label
                val durText = "${String.format("%.1f", item.durationMs)}ms"
                val durResult = textMeasurer.measure(
                    text = durText,
                    style = TextStyle(fontSize = 9.sp, color = textColor),
                )
                drawText(durResult, topLeft = Offset(labelWidth + xStart + barW + 4f, y + (barHeight - durResult.size.height) / 2))
            }
        }
    }
}
