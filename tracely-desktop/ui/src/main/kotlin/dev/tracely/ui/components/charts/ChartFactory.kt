package dev.tracely.ui.components.charts

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.tracely.core.model.QueryResult

/**
 * Renders an appropriate chart based on the tool name and query result.
 * Returns true if a chart was rendered, false if no chart applies.
 */
@Composable
fun AnalysisChart(toolName: String, result: QueryResult, modifier: Modifier = Modifier): Boolean {
    if (result.rows.isEmpty() || result.error != null) return false

    return when (toolName) {
        "jank" -> { renderJankChart(result, modifier); true }
        "startup", "startup-breakdown" -> { renderStartupChart(result, toolName, modifier); true }
        "scheduling" -> { renderSchedulingChart(result, modifier); true }
        "memory" -> { renderMemoryChart(result, modifier); true }
        "gc" -> { renderGCChart(result, modifier); true }
        "binder" -> { renderBinderChart(result, modifier); true }
        "blocking-calls" -> { renderBlockingChart(result, modifier); true }
        else -> false
    }
}

@Composable
private fun renderJankChart(result: QueryResult, modifier: Modifier) {
    val labels = result.rows.map { it["process_name"] ?: "?" }
    val values = result.rows.map { it["janky_frames"]?.toDoubleOrNull() ?: 0.0 }

    BarChart(
        labels = labels.take(12),
        values = values.take(12),
        modifier = modifier,
        title = "Janky Frames by Process",
        dangerThreshold = values.maxOrNull()?.times(0.5),
        dangerColor = Color(0xFFFF4F6A),
    )
}

@Composable
private fun renderStartupChart(result: QueryResult, toolName: String, modifier: Modifier) {
    if (toolName == "startup-breakdown") {
        val labels = result.rows.map { it["reason"] ?: "?" }
        val values = result.rows.map { it["total_ms"]?.toDoubleOrNull() ?: 0.0 }

        BarChart(
            labels = labels.take(15),
            values = values.take(15),
            modifier = modifier,
            title = "Startup Breakdown (ms)",
            barColor = Color(0xFFFFB84F),
        )
    } else {
        val items = result.rows.take(5).mapIndexed { i, row ->
            GanttItem(
                label = row["package"] ?: "app",
                startMs = 0.0,
                durationMs = row["duration_ms"]?.toDoubleOrNull() ?: 0.0,
                color = if (i == 0) Color(0xFF4FDC7C) else Color(0xFF4F8CFF),
            )
        }
        GanttChart(items = items, modifier = modifier, title = "Startup Duration")
    }
}

@Composable
private fun renderSchedulingChart(result: QueryResult, modifier: Modifier) {
    val data = result.rows.take(12).map { row ->
        StackedBarData(
            label = row["thread_name"] ?: "?",
            segments = listOf(
                row["running_ms"]?.toDoubleOrNull() ?: 0.0,
                row["runnable_ms"]?.toDoubleOrNull() ?: 0.0,
                row["sleeping_ms"]?.toDoubleOrNull() ?: 0.0,
            ),
        )
    }

    StackedBarChart(
        data = data,
        segmentLabels = listOf("Running", "Runnable", "Sleeping"),
        segmentColors = listOf(Color(0xFF4FDC7C), Color(0xFFFFB84F), Color(0xFF8B8F9E)),
        modifier = modifier,
        title = "CPU Scheduling by Thread",
    )
}

@Composable
private fun renderMemoryChart(result: QueryResult, modifier: Modifier) {
    val labels = result.rows.map { "${it["process_name"]?.take(12) ?: ""}: ${it["counter_name"]?.take(15) ?: ""}" }
    val values = result.rows.map { it["max_value"]?.toDoubleOrNull() ?: 0.0 }

    BarChart(
        labels = labels.take(12),
        values = values.take(12),
        modifier = modifier,
        title = "Memory Counters (max value)",
        barColor = Color(0xFF9B59B6),
    )
}

@Composable
private fun renderGCChart(result: QueryResult, modifier: Modifier) {
    val labels = result.rows.mapIndexed { i, row -> "GC #${i + 1}: ${row["gc_type"]?.take(15) ?: ""}" }
    val values = result.rows.map { it["duration_ms"]?.toDoubleOrNull() ?: 0.0 }

    BarChart(
        labels = labels.take(20),
        values = values.take(20),
        modifier = modifier,
        title = "GC Pause Duration (ms)",
        dangerThreshold = 16.0,
        dangerColor = Color(0xFFFF4F6A),
    )
}

@Composable
private fun renderBinderChart(result: QueryResult, modifier: Modifier) {
    val labels = result.rows.map { "${it["client"]?.take(12) ?: ""} -> ${it["server"]?.take(12) ?: ""}" }
    val values = result.rows.map { it["avg_ms"]?.toDoubleOrNull() ?: 0.0 }

    BarChart(
        labels = labels.take(12),
        values = values.take(12),
        modifier = modifier,
        title = "Binder IPC Avg Latency (ms)",
        dangerThreshold = 10.0,
    )
}

@Composable
private fun renderBlockingChart(result: QueryResult, modifier: Modifier) {
    val labels = result.rows.map { it["blocking_call"]?.take(25) ?: "?" }
    val values = result.rows.map { it["total_ms"]?.toDoubleOrNull() ?: 0.0 }

    BarChart(
        labels = labels.take(12),
        values = values.take(12),
        modifier = modifier,
        title = "Blocking Calls - Total Time (ms)",
        dangerThreshold = 100.0,
        dangerColor = Color(0xFFFF4F6A),
    )
}
