package dev.tracely.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.tracely.ui.screens.*
import dev.tracely.ui.viewmodel.AppViewModel

@Composable
fun TracelyApp(viewModel: AppViewModel) {
    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Sidebar
        NavigationSidebar(viewModel)

        // Main content
        Box(Modifier.fillMaxSize().padding(24.dp)) {
            when (viewModel.currentScreen) {
                "dashboard" -> DashboardScreen(viewModel)
                "library" -> LibraryScreen(viewModel)
                "capture" -> CaptureScreen(viewModel)
                "analysis" -> AnalysisScreen(viewModel)
            }
        }
    }
}

@Composable
fun NavigationSidebar(viewModel: AppViewModel) {
    Column(
        modifier = Modifier
            .width(220.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
    ) {
        Text("Tracely", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Text("v1.0.0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(32.dp))

        NavItem("Dashboard", Icons.Default.Home, viewModel.currentScreen == "dashboard") {
            viewModel.currentScreen = "dashboard"
        }
        NavItem("Library", Icons.Default.FolderOpen, viewModel.currentScreen == "library") {
            viewModel.currentScreen = "library"
        }
        NavItem("Capture", Icons.Default.FiberManualRecord, viewModel.currentScreen == "capture") {
            viewModel.currentScreen = "capture"
        }
        NavItem("Analysis", Icons.Default.Analytics, viewModel.currentScreen == "analysis") {
            viewModel.currentScreen = "analysis"
        }

        Spacer(Modifier.weight(1f))

        // Loaded traces
        if (viewModel.loadedTraces.isNotEmpty()) {
            Divider(color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(8.dp))
            Text("Loaded", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            viewModel.loadedTraces.forEach { trace ->
                Text(
                    trace.alias,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
fun NavItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .background(bg, MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = fg, style = MaterialTheme.typography.bodyMedium)
    }
}
