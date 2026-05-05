package dev.tracely.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.tracely.ui.viewmodel.AppViewModel

@Composable
fun LibraryScreen(viewModel: AppViewModel) {
    Column(Modifier.fillMaxSize()) {
        Text("Trace Library", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // Repo list
            Card(Modifier.width(250.dp).fillMaxHeight()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Repositories", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    viewModel.repos.forEach { repo ->
                        val selected = repo.name == viewModel.selectedRepo
                        val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                        val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        Row(
                            Modifier.fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clickable { viewModel.selectRepo(repo.name) }
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(repo.name, color = fg, style = MaterialTheme.typography.bodyMedium)
                            Badge { Text("${repo.traceCount}") }
                        }
                    }
                }
            }

            // Trace list
            Card(Modifier.weight(1f).fillMaxHeight()) {
                Column(Modifier.padding(16.dp)) {
                    if (viewModel.selectedRepo.isEmpty()) {
                        Text("Select a repository", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("${viewModel.selectedRepo}", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))

                        LazyColumn {
                            items(viewModel.repoTraces) { trace ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            trace.timestamp?.substring(11, 19) ?: "-",
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        Text(
                                            "${trace.timestamp?.substring(0, 10) ?: ""} | ${trace.packageName ?: "no package"} | ${String.format("%.1f", trace.sizeMb)} MB",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Button(onClick = { viewModel.loadTrace(trace.path) }) {
                                        Text("Load")
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
            }
        }
    }
}
