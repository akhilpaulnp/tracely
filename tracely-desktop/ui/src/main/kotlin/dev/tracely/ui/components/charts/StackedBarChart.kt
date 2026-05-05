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

data class StackedBarData(
    val label: String,
    val segments: List<Double>,
)

@Composable
fun StackedBarChart(
    data: List<StackedBarData>,
    segmentLabels: List<String>,
    segmentColors: List<Color>,
    modifier: Modifier = Modifier,
    title: String = "",
) {
    if (data.isEmpty()) return

    val maxVal = data.maxOfOrNull { it.segments.sum() }?.coerceAtLeast(1.0) ?: 1.0
    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurface

    Column(modifier) {
        if (title.isNotEmpty()) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
        }

        Canvas(Modifier.fillMaxWidth().height((data.size * 28 + 16).dp)) {
            val labelWidth = 160f
            val chartWidth = size.width - labelWidth - 80f
            val barHeight = 20f
            val gap = 8f

            data.forEachIndexed { i, item ->
                val y = i * (barHeight + gap) + 8f

                // Label
                val labelResult = textMeasurer.measure(
                    text = item.label.take(20),
                    style = TextStyle(fontSize = 11.sp, color = labelColor),
                )
                drawText(labelResult, topLeft = Offset(0f, y + (barHeight - labelResult.size.height) / 2))

                // Stacked segments
                var offset = labelWidth
                item.segments.forEachIndexed { si, segVal ->
                    val w = (segVal / maxVal * chartWidth).toFloat().coerceAtLeast(0f)
                    if (w > 0) {
                        drawRoundRect(
                            color = segmentColors.getOrElse(si) { Color.Gray },
                            topLeft = Offset(offset, y),
                            size = Size(w, barHeight),
                            cornerRadius = CornerRadius(3f),
                        )
                    }
                    offset += w
                }

                // Total
                val total = item.segments.sum()
                val totalText = "${String.format("%.0f", total)}ms"
                val valResult = textMeasurer.measure(
                    text = totalText,
                    style = TextStyle(fontSize = 10.sp, color = textColor),
                )
                drawText(valResult, topLeft = Offset(offset + 6f, y + (barHeight - valResult.size.height) / 2))
            }
        }

        // Legend
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            segmentLabels.forEachIndexed { i, label ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Canvas(Modifier.size(10.dp)) {
                        drawRoundRect(
                            color = segmentColors.getOrElse(i) { Color.Gray },
                            cornerRadius = CornerRadius(2f),
                        )
                    }
                    Text(label, style = MaterialTheme.typography.labelSmall, color = labelColor)
                }
            }
        }
    }
}
