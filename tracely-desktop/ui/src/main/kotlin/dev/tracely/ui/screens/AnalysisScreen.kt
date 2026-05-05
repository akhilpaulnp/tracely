package dev.tracely.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.tracely.ui.viewmodel.AppViewModel

@Composable
fun AnalysisScreen(viewModel: AppViewModel) {
    var selectedAlias by remember { mutableStateOf("default") }
    var packageFilter by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        Text("Analysis", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        if (viewModel.loadedTraces.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Text(
                    "No traces loaded. Load one from Library or capture a new trace.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return
        }

        // Toolbar
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Trace selector
                    OutlinedTextField(
                        value = selectedAlias,
                        onValueChange = { selectedAlias = it },
                        label = { Text("Trace alias") },
                        modifier = Modifier.width(150.dp),
                    )
                    OutlinedTextField(
                        value = packageFilter,
                        onValueChange = { packageFilter = it },
                        label = { Text("Package filter") },
                        placeholder = { Text("com.example") },
                        modifier = Modifier.width(250.dp),
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Tool buttons by category
                val tools = viewModel.analysisEngine.tools
                val categories = tools.values.map { it.category }.distinct()

                categories.forEach { category ->
                    Text(
                        category.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        tools.filter { it.value.category == category }.forEach { (name, tool) ->
                            val isSelected = viewModel.currentTool == name
                            if (isSelected) {
                                FilledTonalButton(
                                    onClick = { viewModel.runAnalysis(name, selectedAlias, packageFilter) },
                                ) { Text(name) }
                            } else {
                                OutlinedButton(
                                    onClick = { viewModel.runAnalysis(name, selectedAlias, packageFilter) },
                                ) { Text(name) }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Results
        Card(Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.padding(16.dp)) {
                val result = viewModel.analysisResult
                when {
                    viewModel.isAnalyzing -> {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Text("Running ${viewModel.currentTool}...")
                    }
                    result == null -> {
                        Text("Select a tool above to analyze.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    result.error != null -> {
                        Text("Error: ${result.error}", color = MaterialTheme.colorScheme.error)
                    }
                    result.rows.isEmpty() -> {
                        Text("No data found for ${viewModel.currentTool}.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> {
                        Text(
                            "${viewModel.currentTool} - ${result.rowCount} rows",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(Modifier.height(8.dp))

                        // Scrollable table
                        val scrollState = rememberScrollState()
                        Box(Modifier.fillMaxSize().horizontalScroll(scrollState)) {
                            LazyColumn {
                                // Header
                                item {
                                    Row {
                                        result.columns.forEach { col ->
                                            Text(
                                                col,
                                                modifier = Modifier.width(150.dp).padding(4.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                }
                                // Data rows
                                items(result.rows) { row ->
                                    Row {
                                        result.columns.forEach { col ->
                                            Text(
                                                row[col] ?: "-",
                                                modifier = Modifier.width(150.dp).padding(4.dp),
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
