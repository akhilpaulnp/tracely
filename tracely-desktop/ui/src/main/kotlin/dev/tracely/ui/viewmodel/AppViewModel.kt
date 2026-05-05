package dev.tracely.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.tracely.core.analysis.AnalysisEngine
import dev.tracely.core.capture.CaptureManager
import dev.tracely.core.device.DeviceManager
import dev.tracely.core.library.TraceLibrary
import dev.tracely.core.model.*
import dev.tracely.core.traceprocessor.TraceProcessorManager
import kotlinx.coroutines.*

class AppViewModel(
    val tpManager: TraceProcessorManager,
    val analysisEngine: AnalysisEngine,
    val captureManager: CaptureManager,
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Navigation
    var currentScreen by mutableStateOf("dashboard")

    // Devices
    val devices = mutableStateListOf<DeviceInfo>()

    // Library
    val repos = mutableStateListOf<RepoInfo>()
    var selectedRepo by mutableStateOf("")
    val repoTraces = mutableStateListOf<TraceInfo>()

    // Loaded traces
    val loadedTraces = mutableStateListOf<LoadedTrace>()

    // Capture
    var captureStatus by mutableStateOf(CaptureStatus())

    // Analysis
    var analysisResult by mutableStateOf<QueryResult?>(null)
    var currentTool by mutableStateOf("")
    var isAnalyzing by mutableStateOf(false)

    init {
        refreshRepos()
        refreshDevices()
    }

    fun refreshDevices() {
        scope.launch {
            val result = DeviceManager.listDevices()
            withContext(Dispatchers.Main) {
                devices.clear()
                devices.addAll(result)
            }
        }
    }

    fun refreshRepos() {
        scope.launch {
            val result = TraceLibrary.listRepos()
            withContext(Dispatchers.Main) {
                repos.clear()
                repos.addAll(result)
            }
        }
    }

    fun selectRepo(name: String) {
        selectedRepo = name
        scope.launch {
            val traces = TraceLibrary.listTraces(name)
            withContext(Dispatchers.Main) {
                repoTraces.clear()
                repoTraces.addAll(traces)
            }
        }
    }

    fun loadTrace(path: String, alias: String = "default") {
        scope.launch {
            try {
                val result = tpManager.loadTrace(path, alias)
                withContext(Dispatchers.Main) {
                    loadedTraces.clear()
                    loadedTraces.addAll(tpManager.listTraces())
                }
            } catch (e: Exception) {
                // TODO: show error
            }
        }
    }

    fun closeTrace(alias: String) {
        scope.launch {
            withContext(Dispatchers.IO) {
                tpManager.closeTrace(alias)
            }
            withContext(Dispatchers.Main) {
                loadedTraces.clear()
                loadedTraces.addAll(tpManager.listTraces())
            }
        }
    }

    fun runAnalysis(toolName: String, alias: String = "default", packageName: String = "") {
        currentTool = toolName
        isAnalyzing = true
        scope.launch {
            val result = analysisEngine.run(toolName, alias, packageName)
            withContext(Dispatchers.Main) {
                analysisResult = result
                isAnalyzing = false
            }
        }
    }

    fun captureTrace(durationS: Int, packageName: String, launchApp: Boolean) {
        scope.launch {
            val result = captureManager.captureTrace(durationS, packageName, launchApp)
            withContext(Dispatchers.Main) {
                captureStatus = result
                if (result.state == CaptureState.DONE && result.resultPath != null) {
                    loadTrace(result.resultPath!!)
                    refreshRepos()
                }
            }
        }
    }
}
