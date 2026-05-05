package dev.tracely.core.traceprocessor

import dev.tracely.core.model.LoadedTrace
import dev.tracely.core.model.QueryResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages trace_processor_shell subprocesses.
 * Each loaded trace gets its own process on a unique port.
 * Thread-safe: uses ConcurrentHashMap + Mutex for multi-step operations.
 */
class TraceProcessorManager {

    private val instances = ConcurrentHashMap<String, TraceProcessorInstance>()
    private val loadMutex = Mutex()

    data class TraceProcessorInstance(
        val process: Process,
        val client: TraceProcessorClient,
        val port: Int,
        val path: String,
    )

    /**
     * Load a trace file by spawning a new trace_processor_shell.
     * Mutex prevents concurrent loads from leaking processes.
     */
    suspend fun loadTrace(path: String, alias: String = "default"): LoadedTrace {
        loadMutex.withLock {
            // Close existing trace with same alias
            closeTrace(alias)

            val binary = TraceProcessorBinary.getBinaryPath()

            // Launch in interactive mode (stdin/stdout for queries)
            val process = ProcessBuilder(binary, path)
                .redirectErrorStream(true)
                .start()

            try {
                val client = TraceProcessorClient(process)
                client.waitForReady()
                instances[alias] = TraceProcessorInstance(process, client, 0, path)
            } catch (e: Exception) {
                process.destroyForcibly()
                throw e
            }

            return LoadedTrace(alias = alias, path = path, port = 0)
        }
    }

    /**
     * Execute SQL on a loaded trace.
     */
    fun query(alias: String, sql: String): QueryResult {
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
        return if (instances.containsKey(alias)) null
        else "No trace loaded with alias '$alias'. Use load-trace first."
    }

    /**
     * Close all traces.
     */
    fun closeAll() {
        instances.keys.toList().forEach { closeTrace(it) }
    }

}
