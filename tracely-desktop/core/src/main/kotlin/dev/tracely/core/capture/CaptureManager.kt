package dev.tracely.core.capture

import dev.tracely.core.device.DeviceManager
import dev.tracely.core.model.CaptureState
import dev.tracely.core.model.CaptureStatus
import kotlinx.coroutines.*
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Manages trace capture from Android devices via adb + perfetto.
 */
class CaptureManager {

    private val tracelyHome = File(System.getProperty("user.home"), ".tracely")

    @Volatile
    var status = CaptureStatus()
        private set

    /**
     * Capture a performance trace from the connected device.
     */
    suspend fun captureTrace(
        durationS: Int = 10,
        packageName: String = "",
        launchApp: Boolean = false,
        serial: String = "",
    ): CaptureStatus = withContext(Dispatchers.IO) {
        status = CaptureStatus(
            state = CaptureState.CAPTURING,
            durationS = durationS,
            packageName = packageName,
        )

        try {
            // Force-stop and launch if requested
            if (launchApp && packageName.isNotEmpty()) {
                DeviceManager.adbShell("shell", "am", "force-stop", packageName, serial = serial)
                delay(1000)
                DeviceManager.adbShell(
                    "shell", "monkey", "-p", packageName,
                    "-c", "android.intent.category.LAUNCHER", "1",
                    serial = serial
                )
                delay(2000)
            }

            val config = buildConfig(durationS, packageName)
            val deviceTracePath = "/data/misc/perfetto-traces/mcp_trace.perfetto-trace"

            // Run perfetto via adb, pipe config through stdin
            val adb = DeviceManager.findAdb()
            val cmd = mutableListOf(adb)
            if (serial.isNotEmpty()) cmd.addAll(listOf("-s", serial))
            cmd.addAll(listOf("shell", "perfetto", "--txt", "-c", "-", "-o", deviceTracePath))

            val process = ProcessBuilder(cmd)
                .redirectErrorStream(false)
                .start()

            process.outputStream.bufferedWriter().use { it.write(config) }

            val completed = process.waitFor()
            if (process.exitValue() != 0) {
                val stderr = process.errorStream.bufferedReader().readText()
                status = CaptureStatus(state = CaptureState.ERROR, error = "Perfetto failed: $stderr")
                return@withContext status
            }

            // Pull trace from device
            val localPath = generateTracePath(packageName)
            val pullProcess = ProcessBuilder(
                mutableListOf(adb).apply {
                    if (serial.isNotEmpty()) addAll(listOf("-s", serial))
                    addAll(listOf("pull", deviceTracePath, localPath))
                }
            ).redirectErrorStream(true).start()
            pullProcess.waitFor()

            if (pullProcess.exitValue() != 0) {
                status = CaptureStatus(state = CaptureState.ERROR, error = "Failed to pull trace from device")
                return@withContext status
            }

            // Cleanup on device
            DeviceManager.adbShell("shell", "rm", "-f", deviceTracePath, serial = serial)

            status = CaptureStatus(
                state = CaptureState.DONE,
                durationS = durationS,
                packageName = packageName,
                resultPath = localPath,
            )
            status
        } catch (e: Exception) {
            status = CaptureStatus(state = CaptureState.ERROR, error = e.message)
            status
        }
    }

    private fun generateTracePath(packageName: String): String {
        val repoName = detectRepoName()
        val dir = if (repoName.isNotEmpty()) {
            File(tracelyHome, "$repoName/traces")
        } else {
            File(tracelyHome, "traces")
        }
        dir.mkdirs()

        val ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val suffix = if (packageName.isNotEmpty()) "_$packageName" else ""
        return File(dir, "trace_$ts$suffix.perfetto-trace").absolutePath
    }

    private fun detectRepoName(): String {
        return try {
            val p = ProcessBuilder("git", "rev-parse", "--show-toplevel")
                .redirectErrorStream(true).start()
            val result = p.inputStream.bufferedReader().readText().trim()
            p.waitFor()
            if (p.exitValue() == 0) File(result).name else ""
        } catch (_: Exception) { "" }
    }

    private fun buildConfig(durationS: Int, packageName: String): String {
        val durationMs = durationS * 1000L
        val bufferSizeKb = 65536

        return buildString {
            appendLine("duration_ms: $durationMs")
            appendLine("buffers { size_kb: $bufferSizeKb fill_policy: RING_BUFFER }")
            appendLine("data_sources { config { name: \"linux.process_stats\" target_buffer: 0 process_stats_config { scan_all_processes_on_start: true proc_stats_poll_ms: 1000 } } }")
            appendLine("data_sources { config { name: \"linux.sys_stats\" target_buffer: 0 sys_stats_config { meminfo_period_ms: 1000 } } }")
            appendLine("data_sources { config { name: \"linux.ftrace\" target_buffer: 0 ftrace_config { ftrace_events: \"sched/sched_switch\" ftrace_events: \"sched/sched_wakeup\" ftrace_events: \"power/suspend_resume\" ftrace_events: \"mm_event/mm_event_record\" }")
            if (packageName.isNotEmpty()) {
                appendLine("    atrace_apps: \"$packageName\"")
            }
            val categories = listOf("am", "binder_driver", "dalvik", "gfx", "input", "memory", "sched", "view", "wm")
            categories.forEach { appendLine("    atrace_categories: \"$it\"") }
            appendLine("} } }")
        }
    }
}
