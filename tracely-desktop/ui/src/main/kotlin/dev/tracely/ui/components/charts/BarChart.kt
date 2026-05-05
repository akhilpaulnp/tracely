package dev.tracely.ui.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BarChart(
    labels: List<String>,
    values: List<Double>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    dangerThreshold: Double? = null,
    dangerColor: Color = MaterialTheme.colorScheme.error,
    title: String = "",
) {
    if (labels.isEmpty() || values.isEmpty()) return

    val maxVal = values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurface

    Column(modifier) {
        if (title.isNotEmpty()) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
        }

        Canvas(Modifier.fillMaxWidth().height((labels.size * 32 + 16).dp)) {
            val labelWidth = 180f
            val chartWidth = size.width - labelWidth - 60f
            val barHeight = 22f
            val gap = 10f

            labels.forEachIndexed { i, label ->
                val y = i * (barHeight + gap) + 8f
                val value = values.getOrElse(i) { 0.0 }
                val barW = (value / maxVal * chartWidth).toFloat().coerceAtLeast(2f)
                val color = if (dangerThreshold != null && value > dangerThreshold) dangerColor else barColor

                // Label
                val labelResult = textMeasurer.measure(
                    text = label.take(22),
                    style = TextStyle(fontSize = 11.sp, color = labelColor),
                )
                drawText(labelResult, topLeft = Offset(0f, y + (barHeight - labelResult.size.height) / 2))

                // Bar
                drawRoundRect(
                    color = color,
                    topLeft = Offset(labelWidth, y),
                    size = Size(barW, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f),
                )

                // Value
                val valText = if (value == value.toLong().toDouble()) value.toLong().toString()
                else String.format("%.1f", value)
                val valResult = textMeasurer.measure(
                    text = valText,
                    style = TextStyle(fontSize = 11.sp, color = textColor),
                )
                drawText(valResult, topLeft = Offset(labelWidth + barW + 6f, y + (barHeight - valResult.size.height) / 2))
            }
        }
    }
}
