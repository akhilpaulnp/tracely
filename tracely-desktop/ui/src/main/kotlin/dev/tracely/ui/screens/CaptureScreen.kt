package dev.tracely.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.tracely.core.model.CaptureState
import dev.tracely.ui.viewmodel.AppViewModel

@Composable
fun CaptureScreen(viewModel: AppViewModel) {
    var durationS by remember { mutableStateOf("10") }
    var packageName by remember { mutableStateOf("") }
    var launchApp by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Text("Capture Trace", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // Device card
            Card(Modifier.weight(1f)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Device", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    if (viewModel.devices.isEmpty()) {
                        Text("No device connected", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { viewModel.refreshDevices() }) { Text("Refresh") }
                    } else {
                        viewModel.devices.forEach { d ->
                            Text("${d.manufacturer} ${d.model}", style = MaterialTheme.typography.bodyMedium)
                            Text("Android ${d.androidVersion} (API ${d.apiLevel})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Settings card
            Card(Modifier.weight(1f)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Settings", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = durationS,
                        onValueChange = { durationS = it.filter { c -> c.isDigit() } },
                        label = { Text("Duration (seconds)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = packageName,
                        onValueChange = { packageName = it },
                        label = { Text("Package name") },
                        placeholder = { Text("com.example.app") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(checked = launchApp, onCheckedChange = { launchApp = it })
                        Text("Cold-launch app")
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            viewModel.captureTrace(
                                durationS.toIntOrNull() ?: 10,
                                packageName,
                                launchApp,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = viewModel.captureStatus.state != CaptureState.CAPTURING,
                    ) {
                        Text(if (viewModel.captureStatus.state == CaptureState.CAPTURING) "Capturing..." else "Start Capture")
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Status card
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                when (viewModel.captureStatus.state) {
                    CaptureState.IDLE -> Text("Ready to capture.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    CaptureState.CAPTURING -> {
                        Text("Capturing...", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    CaptureState.DONE -> {
                        Text("Capture Complete", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.height(4.dp))
                        Text(viewModel.captureStatus.resultPath?.substringAfterLast("/") ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { viewModel.currentScreen = "analysis" }) {
                            Text("Analyze Trace")
                        }
                    }
                    CaptureState.ERROR -> {
                        Text("Error: ${viewModel.captureStatus.error}", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
