package dev.tracely.core.model

import kotlinx.serialization.Serializable

@Serializable
data class QueryResult(
    val columns: List<String> = emptyList(),
    val rows: List<Map<String, String>> = emptyList(),
    val rowCount: Int = 0,
    val truncated: Boolean = false,
    val error: String? = null,
)

@Serializable
data class TraceInfo(
    val filename: String,
    val path: String,
    val sizeBytes: Long,
    val sizeMb: Double,
    val timestamp: String? = null,
    val packageName: String? = null,
)

@Serializable
data class RepoInfo(
    val name: String,
    val path: String,
    val traceCount: Int,
    val totalSizeMb: Double,
    val latest: String? = null,
)

@Serializable
data class DeviceInfo(
    val serial: String,
    val model: String = "unknown",
    val apiLevel: String = "unknown",
    val androidVersion: String = "unknown",
    val manufacturer: String = "unknown",
)

@Serializable
data class LoadedTrace(
    val alias: String,
    val path: String,
    val port: Int,  // trace_processor_shell port for this trace
)

enum class CaptureState {
    IDLE, CAPTURING, DONE, ERROR
}

@Serializable
data class CaptureStatus(
    val state: CaptureState = CaptureState.IDLE,
    val durationS: Int = 0,
    val packageName: String = "",
    val progress: Float = 0f,
    val resultPath: String? = null,
    val error: String? = null,
)
