package dev.tracely.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.tracely.core.model.QueryResult
import dev.tracely.ui.components.charts.BarChart
import dev.tracely.ui.viewmodel.AppViewModel
import kotlinx.coroutines.launch

@Composable
fun CompareScreen(viewModel: AppViewModel) {
    var alias1 by remember { mutableStateOf("") }
    var alias2 by remember { mutableStateOf("") }
    var packageFilter by remember { mutableStateOf("") }
    var result1 by remember { mutableStateOf<QueryResult?>(null) }
    var result2 by remember { mutableStateOf<QueryResult?>(null) }
    var selectedTool by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        Text("Compare Traces", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        if (viewModel.loadedTraces.size < 2) {
            Card(Modifier.fillMaxWidth()) {
                Text(
                    "Load at least 2 traces from the Library to compare. Currently loaded: ${viewModel.loadedTraces.size}",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return
        }

        // Setup loaded aliases
        LaunchedEffect(viewModel.loadedTraces.size) {
            if (viewModel.loadedTraces.size >= 2) {
                alias1 = viewModel.loadedTraces[0].alias
                alias2 = viewModel.loadedTraces[1].alias
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = alias1,
                        onValueChange = { alias1 = it },
                        label = { Text("Baseline") },
                        modifier = Modifier.width(150.dp),
                    )
                    Text("vs", modifier = Modifier.padding(top = 24.dp))
                    OutlinedTextField(
                        value = alias2,
                        onValueChange = { alias2 = it },
                        label = { Text("Current") },
                        modifier = Modifier.width(150.dp),
                    )
                    OutlinedTextField(
                        value = packageFilter,
                        onValueChange = { packageFilter = it },
                        label = { Text("Package") },
                        modifier = Modifier.width(200.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("jank", "startup", "memory", "scheduling", "gc").forEach { tool ->
                        OutlinedButton(
                            onClick = {
                                selectedTool = tool
                                isLoading = true
                                scope.launch {
                                    result1 = viewModel.analysisEngine.run(tool, alias1, packageFilter)
                                    result2 = viewModel.analysisEngine.run(tool, alias2, packageFilter)
                                    isLoading = false
                                }
                            },
                        ) { Text("Compare $tool") }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Results
        Card(Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.padding(16.dp)) {
                when {
                    isLoading -> {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Comparing...")
                    }
                    result1 == null || result2 == null -> {
                        Text("Select a tool to compare.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    result1!!.error != null -> Text("Baseline error: ${result1!!.error}", color = MaterialTheme.colorScheme.error)
                    result2!!.error != null -> Text("Current error: ${result2!!.error}", color = MaterialTheme.colorScheme.error)
                    else -> {
                        ComparisonView(selectedTool, alias1, alias2, result1!!, result2!!)
                    }
                }
            }
        }
    }
}

@Composable
private fun ComparisonView(tool: String, alias1: String, alias2: String, r1: QueryResult, r2: QueryResult) {
    val columns = r1.columns
    val numericCols = columns.filter { col ->
        r1.rows.firstOrNull()?.get(col)?.toDoubleOrNull() != null
    }
    val labelCol = columns.firstOrNull { it.contains("name") || it.contains("package") } ?: columns.firstOrNull() ?: return

    Text("$tool: $alias1 vs $alias2", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(8.dp))

    // Side-by-side bar chart using the first numeric column
    if (numericCols.isNotEmpty()) {
        val valCol = numericCols.first()
        val labels = r1.rows.take(10).map { it[labelCol]?.take(20) ?: "?" }
        val vals1 = r1.rows.take(10).map { it[valCol]?.toDoubleOrNull() ?: 0.0 }

        val r2Map = r2.rows.associate { (it[labelCol] ?: "") to (it[valCol]?.toDoubleOrNull() ?: 0.0) }
        val vals2 = labels.map { r2Map[it] ?: 0.0 }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BarChart(labels, vals1, Modifier.weight(1f), title = "$alias1 ($valCol)", barColor = Color(0xFF4F8CFF))
            BarChart(labels, vals2, Modifier.weight(1f), title = "$alias2 ($valCol)", barColor = Color(0xFFFFB84F))
        }
        Spacer(Modifier.height(12.dp))
    }

    // Delta table
    if (numericCols.isNotEmpty()) {
        val valCol = numericCols.first()
        LazyColumn {
            item {
                Row {
                    listOf("Metric", alias1, alias2, "Delta", "%").forEach { h ->
                        Text(h, Modifier.width(120.dp).padding(4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider()
            }
            items(r1.rows.take(15)) { row1 ->
                val key = row1[labelCol] ?: ""
                val row2 = r2.rows.find { it[labelCol] == key }
                val v1 = row1[valCol]?.toDoubleOrNull() ?: 0.0
                val v2 = row2?.get(valCol)?.toDoubleOrNull() ?: 0.0
                val delta = v2 - v1
                val pct = if (v1 != 0.0) (delta / v1 * 100) else 0.0
                val color = when {
                    delta > 0 -> Color(0xFFFF4F6A)
                    delta < 0 -> Color(0xFF4FDC7C)
                    else -> MaterialTheme.colorScheme.onSurface
                }

                Row {
                    Text(key.take(18), Modifier.width(120.dp).padding(4.dp), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(String.format("%.1f", v1), Modifier.width(120.dp).padding(4.dp), style = MaterialTheme.typography.bodySmall)
                    Text(String.format("%.1f", v2), Modifier.width(120.dp).padding(4.dp), style = MaterialTheme.typography.bodySmall)
                    Text("${if (delta > 0) "+" else ""}${String.format("%.1f", delta)}", Modifier.width(120.dp).padding(4.dp), style = MaterialTheme.typography.bodySmall, color = color)
                    Text("${String.format("%.1f", pct)}%", Modifier.width(120.dp).padding(4.dp), style = MaterialTheme.typography.bodySmall, color = color)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            }
        }
    }
}
