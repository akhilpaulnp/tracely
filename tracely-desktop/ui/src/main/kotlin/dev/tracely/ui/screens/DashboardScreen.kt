package dev.tracely.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.tracely.ui.viewmodel.AppViewModel

@Composable
fun DashboardScreen(viewModel: AppViewModel) {
    Column(Modifier.fillMaxSize()) {
        Text("Dashboard", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // Device card
            Card(Modifier.weight(1f)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Devices", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    if (viewModel.devices.isEmpty()) {
                        Text("No devices connected", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        viewModel.devices.forEach { device ->
                            Text("${device.model}", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Android ${device.androidVersion} (API ${device.apiLevel})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { viewModel.refreshDevices() }) {
                        Text("Refresh")
                    }
                }
            }

            // Quick stats card
            Card(Modifier.weight(1f)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Trace Library", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    viewModel.repos.forEach { repo ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(repo.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${repo.traceCount} traces",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (viewModel.repos.isEmpty()) {
                        Text("No traces yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Loaded traces
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Loaded Traces", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                if (viewModel.loadedTraces.isEmpty()) {
                    Text(
                        "No traces loaded. Go to Library to load one, or Capture a new trace.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    viewModel.loadedTraces.forEach { trace ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(trace.alias, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    trace.path.substringAfterLast("/"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { viewModel.currentScreen = "analysis" }) {
                                    Text("Analyze")
                                }
                                OutlinedButton(onClick = { viewModel.closeTrace(trace.alias) }) {
                                    Text("Close")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
