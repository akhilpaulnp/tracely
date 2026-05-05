package dev.tracely.core.traceprocessor

import dev.tracely.core.model.LoadedTrace
import dev.tracely.core.model.QueryResult
import java.io.File
import java.net.ServerSocket

/**
 * Manages trace_processor_shell subprocesses.
 * Each loaded trace gets its own process on a unique port.
 */
class TraceProcessorManager {

    private val instances = mutableMapOf<String, TraceProcessorInstance>()

    data class TraceProcessorInstance(
        val process: Process,
        val client: TraceProcessorClient,
        val port: Int,
        val path: String,
    )

    /**
     * Load a trace file by spawning a new trace_processor_shell.
     */
    suspend fun loadTrace(path: String, alias: String = "default"): LoadedTrace {
        // Close existing trace with same alias
        closeTrace(alias)

        val port = findFreePort()
        val binary = TraceProcessorBinary.getBinaryPath()

        val process = ProcessBuilder(
            binary, "--httpd", "--http-port", port.toString(), path
        ).redirectErrorStream(true).start()

        val client = TraceProcessorClient(port)
        client.waitForReady()

        instances[alias] = TraceProcessorInstance(process, client, port, path)

        return LoadedTrace(alias = alias, path = path, port = port)
    }

    /**
     * Execute SQL on a loaded trace.
     */
    suspend fun query(alias: String, sql: String): QueryResult {
        val instance = instances[alias]
            ?: return QueryResult(error = "No trace loaded with alias '$alias'. Use load-trace first.")
        return instance.client.query(sql)
    }

    /**
     * Close a loaded trace and kill its process.
     */
    fun closeTrace(alias: String) {
        instances.remove(alias)?.let { instance ->
            instance.client.close()
            instance.process.destroyForcibly()
        }
    }

    /**
     * List all loaded traces.
     */
    fun listTraces(): List<LoadedTrace> {
        return instances.map { (alias, inst) ->
            LoadedTrace(alias = alias, path = inst.path, port = inst.port)
        }
    }

    /**
     * Check if a trace is loaded with the given alias.
     */
    fun requireTrace(alias: String): String? {
        return if (alias in instances) null
        else "No trace loaded with alias '$alias'. Use load-trace first."
    }

    /**
     * Close all traces.
     */
    fun closeAll() {
        instances.keys.toList().forEach { closeTrace(it) }
    }

    private fun findFreePort(): Int {
        ServerSocket(0).use { return it.localPort }
    }
}
