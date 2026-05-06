package dev.tracely.core.analysis

import dev.tracely.core.model.QueryResult
import dev.tracely.core.traceprocessor.TraceProcessorManager

/**
 * Registry of all analysis tools. Each tool is a SQL query template
 * that runs against a loaded trace via TraceProcessorManager.
 */
class AnalysisEngine(private val tpManager: TraceProcessorManager) {

    data class Tool(
        val name: String,
        val description: String,
        val category: String,
        val modules: List<String> = emptyList(),  // Perfetto stdlib modules to load
        val sqlTemplate: (String) -> String,  // package -> SQL
    )

    val tools: Map<String, Tool> = buildToolRegistry()

    /**
     * Run a tool by name against a loaded trace.
     */
    fun run(toolName: String, alias: String = "default", packageName: String = ""): QueryResult {
        val tool = tools[toolName]
            ?: return QueryResult(error = "Unknown tool: $toolName")

        val err = tpManager.requireTrace(alias)
        if (err != null) return QueryResult(error = err)

        // Load required modules
        for (module in tool.modules) {
            tpManager.query(alias, "INCLUDE PERFETTO MODULE $module;")
        }

        val sql = tool.sqlTemplate(packageName)
        return tpManager.query(alias, sql)
    }

    /**
     * List available tools.
     */
    fun listTools(): List<Map<String, String>> {
        return tools.values.map {
            mapOf("name" to it.name, "description" to it.description, "category" to it.category)
        }
    }

    private fun buildToolRegistry(): Map<String, Tool> {
        val registry = mutableMapOf<String, Tool>()

        fun register(tool: Tool) { registry[tool.name] = tool }
        fun pkgFilter(pkg: String, col: String = "process_name"): String {
            if (pkg.isEmpty()) return ""
            // Sanitize: escape SQL wildcards and single quotes
            val safe = pkg
                .replace("'", "''")
                .replace("%", "\\%")
                .replace("_", "\\_")
            return "AND $col LIKE '%$safe%' ESCAPE '\\'"
        }

        register(Tool("jank", "Frame timeline jank analysis", "rendering") { pkg ->
            """SELECT
                p.name as process_name,
                COUNT(*) as total_frames,
                SUM(CASE WHEN aft.jank_type != 'None' AND aft.jank_type IS NOT NULL THEN 1 ELSE 0 END) as janky_frames,
                ROUND(AVG(aft.dur) / 1e6, 2) as avg_frame_dur_ms,
                ROUND(MAX(aft.dur) / 1e6, 2) as max_frame_dur_ms
            FROM actual_frame_timeline_slice aft
            JOIN process p ON aft.upid = p.upid
            WHERE p.name IS NOT NULL ${pkgFilter(pkg, "p.name")}
            GROUP BY p.name ORDER BY janky_frames DESC LIMIT 20"""
        })

        register(Tool("startup", "App startup timing", "startup",
            modules = listOf("android.startup.startups")
        ) { pkg ->
            """SELECT package, startup_type,
                ROUND(dur / 1e6, 2) as duration_ms,
                ts
            FROM android_startups
            WHERE 1=1 ${pkgFilter(pkg, "package")}
            ORDER BY ts DESC LIMIT 10"""
        })

        register(Tool("startup-breakdown", "Startup phase breakdown", "startup",
            modules = listOf("android.startup.startups")
        ) { pkg ->
            """SELECT s.package, slice_name as reason,
                COUNT(*) as occurrences,
                ROUND(SUM(slice_dur) / 1e6, 2) as total_ms,
                ROUND(MAX(slice_dur) / 1e6, 2) as max_ms
            FROM android_startups s
            JOIN thread_slice ts ON ts.ts >= s.ts AND ts.ts < s.ts + s.dur
            WHERE 1=1 ${pkgFilter(pkg, "s.package")}
            GROUP BY s.package, slice_name
            ORDER BY total_ms DESC LIMIT 30"""
        })

        register(Tool("memory", "Memory counter analysis", "memory") { pkg ->
            """SELECT p.name as process_name, t.name as counter_name,
                MIN(c.value) as min_value,
                MAX(c.value) as max_value,
                ROUND(AVG(c.value), 2) as avg_value,
                COUNT(*) as sample_count
            FROM counter c
            JOIN process_counter_track t ON c.track_id = t.id
            JOIN process p ON t.upid = p.upid
            WHERE p.name IS NOT NULL ${pkgFilter(pkg, "p.name")}
            GROUP BY p.name, t.name
            ORDER BY max_value DESC LIMIT 50"""
        })

        register(Tool("scheduling", "CPU scheduling per thread", "cpu") { pkg ->
            // sched_slice rows are 'Running' intervals. thread_state has wait states (R, S, etc.)
            """SELECT t.name as thread_name, p.name as process_name,
                ROUND(SUM(s.dur) / 1e6, 2) as running_ms,
                COUNT(*) as slices
            FROM sched_slice s
            JOIN thread t ON s.utid = t.utid
            JOIN process p ON t.upid = p.upid
            WHERE t.name IS NOT NULL ${pkgFilter(pkg, "p.name")}
            GROUP BY t.name, p.name
            ORDER BY running_ms DESC LIMIT 50"""
        })

        register(Tool("binder", "Binder IPC latency", "ipc",
            modules = listOf("android.binder")
        ) { pkg ->
            """SELECT client_process as client, server_process as server,
                COUNT(*) as call_count,
                ROUND(AVG(dur) / 1e6, 2) as avg_ms,
                ROUND(MAX(dur) / 1e6, 2) as max_ms
            FROM android_binder_txns
            WHERE 1=1 ${pkgFilter(pkg, "client_process")}
            GROUP BY client_process, server_process
            ORDER BY call_count DESC LIMIT 30"""
        })

        register(Tool("anr", "ANR detection", "stability",
            modules = listOf("android.anrs")
        ) { pkg ->
            """SELECT process_name, subject, error_id,
                ROUND(dur / 1e6, 2) as duration_ms
            FROM android_anrs
            WHERE 1=1 ${pkgFilter(pkg)}
            ORDER BY dur DESC"""
        })

        register(Tool("blocking-calls", "Blocking calls on main thread", "stability",
            modules = listOf("android.blocking_calls")
        ) { pkg ->
            """SELECT process_name, blocking_method as blocking_call,
                COUNT(*) as occurrences,
                ROUND(SUM(dur) / 1e6, 2) as total_ms,
                ROUND(MAX(dur) / 1e6, 2) as max_ms,
                ROUND(AVG(dur) / 1e6, 2) as avg_ms
            FROM android_blocking_calls
            WHERE 1=1 ${pkgFilter(pkg)}
            GROUP BY process_name, blocking_method
            ORDER BY total_ms DESC LIMIT 30"""
        })

        register(Tool("lock-contention", "Lock contention analysis", "concurrency",
            modules = listOf("android.monitor_contention")
        ) { pkg ->
            """SELECT blocking_method, blocked_method,
                COUNT(*) as waiter_count,
                ROUND(SUM(dur) / 1e6, 2) as total_ms,
                ROUND(MAX(dur) / 1e6, 2) as max_ms
            FROM android_monitor_contention
            WHERE 1=1 ${pkgFilter(pkg, "process_name")}
            GROUP BY blocking_method, blocked_method
            ORDER BY total_ms DESC LIMIT 20"""
        })

        register(Tool("gc", "Garbage collection events", "memory") { pkg ->
            """SELECT slice.name as gc_type,
                ROUND(slice.dur / 1e6, 2) as duration_ms
            FROM slice
            JOIN thread_track ON slice.track_id = thread_track.id
            JOIN thread ON thread_track.utid = thread.utid
            JOIN process ON thread.upid = process.upid
            WHERE slice.name LIKE '%GC%'
                ${pkgFilter(pkg, "process.name")}
            ORDER BY slice.ts LIMIT 100"""
        })

        register(Tool("heap-java", "Java heap objects", "memory") { pkg ->
            """SELECT p.name as process_name,
                o.type_name as class_name,
                COUNT(*) as instance_count,
                SUM(o.self_size) as total_shallow_bytes,
                ROUND(SUM(o.self_size) / 1024.0, 2) as total_kb
            FROM heap_graph_object o
            JOIN process p ON o.upid = p.upid
            WHERE o.type_name IS NOT NULL ${pkgFilter(pkg, "p.name")}
            GROUP BY p.name, o.type_name
            ORDER BY total_shallow_bytes DESC LIMIT 50"""
        })

        register(Tool("heap-native", "Native heap allocations", "memory") { pkg ->
            """SELECT p.name as process_name,
                f.name as function_name,
                f.mapping_name as module,
                COUNT(*) as alloc_count,
                SUM(a.size) as total_bytes,
                ROUND(SUM(a.size) / (1024.0 * 1024.0), 2) as total_mb
            FROM heap_profile_allocation a
            JOIN stack_profile_frame f ON a.frame_id = f.id
            JOIN process p ON a.upid = p.upid
            WHERE a.size > 0 ${pkgFilter(pkg, "p.name")}
            GROUP BY p.name, f.name, f.mapping_name
            ORDER BY total_bytes DESC LIMIT 50"""
        })

        register(Tool("heap-dominators", "Heap dominator tree", "memory") { pkg ->
            """SELECT p.name as process_name,
                o.type_name as class_name,
                SUM(o.self_size + o.native_size) as retained_bytes,
                ROUND(SUM(o.self_size + o.native_size) / (1024.0 * 1024.0), 2) as retained_mb,
                COUNT(*) as instance_count
            FROM heap_graph_object o
            JOIN process p ON o.upid = p.upid
            WHERE o.type_name IS NOT NULL ${pkgFilter(pkg, "p.name")}
            GROUP BY p.name, o.type_name
            ORDER BY retained_bytes DESC LIMIT 50"""
        })

        register(Tool("input-latency", "Input dispatch latency", "rendering") { pkg ->
            """SELECT name,
                ROUND(dur / 1e6, 2) as duration_ms,
                ts
            FROM slice
            WHERE name LIKE '%deliverInputEvent%' OR name LIKE '%dispatchTouchEvent%'
            ORDER BY dur DESC LIMIT 30"""
        })

        register(Tool("lmk", "Low Memory Killer events", "memory",
            modules = listOf("android.oom_adjuster")
        ) { _ ->
            """SELECT process_name, oom_adj_reason as reason,
                ROUND(dur / 1e6, 2) as duration_ms
            FROM android_oom_adj_intervals
            WHERE oom_adj_reason LIKE '%kill%' OR oom_adj_reason LIKE '%lmk%'
            ORDER BY dur DESC LIMIT 20"""
        })

        register(Tool("network", "Network traffic per app", "network") { pkg ->
            """SELECT p.name as package_name,
                t.name as interface,
                SUM(c.value) as bytes_total,
                COUNT(*) as sample_count
            FROM counter c
            JOIN process_counter_track t ON c.track_id = t.id
            JOIN process p ON t.upid = p.upid
            WHERE t.name LIKE '%bytes%' ${pkgFilter(pkg, "p.name")}
            GROUP BY p.name, t.name
            ORDER BY bytes_total DESC LIMIT 30"""
        })

        register(Tool("oom-priority", "OOM adjuster scores", "memory",
            modules = listOf("android.oom_adjuster")
        ) { pkg ->
            """SELECT process_name, oom_adj_id, oom_adj_reason,
                ROUND(dur / 1e6, 2) as duration_ms
            FROM android_oom_adj_intervals
            WHERE 1=1 ${pkgFilter(pkg)}
            ORDER BY dur DESC LIMIT 30"""
        })

        register(Tool("surfaceflinger", "SurfaceFlinger pipeline", "rendering") { _ ->
            """SELECT name as stage,
                ROUND(AVG(dur) / 1e6, 2) as avg_ms,
                ROUND(MAX(dur) / 1e6, 2) as max_ms,
                COUNT(*) as count
            FROM slice
            WHERE track_id IN (
                SELECT id FROM thread_track
                JOIN thread USING(utid)
                WHERE thread.name = 'surfaceflinger'
            )
            GROUP BY name ORDER BY avg_ms DESC LIMIT 20"""
        })

        return registry
    }
}
