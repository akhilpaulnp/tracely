package dev.tracely.ui

import androidx.compose.runtime.*
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.dp
import dev.tracely.core.analysis.AnalysisEngine
import dev.tracely.core.capture.CaptureManager
import dev.tracely.core.traceprocessor.TraceProcessorManager
import dev.tracely.ui.theme.TracelyTheme
import dev.tracely.ui.viewmodel.AppViewModel

fun main(args: Array<String>) {
    // TODO: if "--mcp" in args, run MCP server instead

    application {
        val windowState = rememberWindowState(width = 1200.dp, height = 800.dp)
        val tpManager = remember { TraceProcessorManager() }
        val analysisEngine = remember { AnalysisEngine(tpManager) }
        val captureManager = remember { CaptureManager() }
        val viewModel = remember { AppViewModel(tpManager, analysisEngine, captureManager) }

        Window(
            onCloseRequest = {
                tpManager.closeAll()
                exitApplication()
            },
            title = "Tracely - Android Trace Analysis",
            state = windowState,
        ) {
            TracelyTheme {
                TracelyApp(viewModel)
            }
        }
    }
}
