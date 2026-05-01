"""Composite diagnosis tools that run multiple analyses in one call."""
import json
from tracely.server import mcp
from tracely.tools.core_tools import trace_manager, query_engine


@mcp.tool()
def full_startup_analysis(package: str = "", alias: str = "default") -> str:
    """Run a complete startup analysis in one call.

    Combines: startup timing, TTID/TTFD, startup breakdown, GC during startup,
    blocking calls during startup, lock contention, and dex loading analysis.
    Returns a structured report instead of requiring 8+ separate tool calls.

    Args:
        package: Package name to analyze
        alias: Trace alias (default: "default")
    """
    err = trace_manager.require_trace(alias)
    if err:
        return json.dumps({"error": err})

    tp = trace_manager.get_trace(alias)
    report = {"package": package, "sections": {}}

    def run(sql, mods=None):
        if mods:
            for m in mods:
                query_engine.ensure_module(tp, m)
        return json.loads(query_engine.run_query(tp, sql))

    safe_pkg = package.replace("'", "''") if package else ""

    # 1. Startup timing
    pkg_where = f"WHERE s.package LIKE '%{safe_pkg}%'" if safe_pkg else ""
    r = run(f"""
        SELECT s.package, s.startup_type,
            ROUND(s.dur / 1e6, 0) as total_ms,
            ROUND(ttd.time_to_initial_display / 1e6, 0) as ttid_ms,
            ROUND(ttd.time_to_full_display / 1e6, 0) as ttfd_ms
        FROM android_startups s
        LEFT JOIN android_startup_time_to_display ttd
            ON s.startup_id = ttd.startup_id
        {pkg_where}
    """, ["android.startup.startups", "android.startup.time_to_display"])
    report["sections"]["startup_timing"] = r

    # 2. Startup breakdown
    pkg_and = f"AND s.package LIKE '%{safe_pkg}%'" if safe_pkg else ""
    r = run(f"""
        SELECT b.reason,
            ROUND(SUM(b.dur) / 1e6, 1) as total_ms,
            COUNT(*) as slices,
            ROUND(MAX(b.dur) / 1e6, 1) as max_single_ms
        FROM android_startup_opinionated_breakdown b
        JOIN android_startups s ON b.startup_id = s.startup_id
        WHERE 1=1 {pkg_and}
        GROUP BY b.reason ORDER BY total_ms DESC
    """, ["android.startup.startup_breakdowns"])
    report["sections"]["startup_breakdown"] = r

    # 3. GC during startup (first 5s of process)
    proc_where = f"WHERE process_name = '{safe_pkg}'" if safe_pkg else ""
    r = run(f"""
        SELECT gc_type, COUNT(*) as n,
            ROUND(SUM(gc_dur) / 1e6, 1) as total_ms,
            ROUND(SUM(reclaimed_mb), 1) as reclaimed_mb,
            ROUND(MAX(max_heap_mb), 1) as peak_heap_mb
        FROM android_garbage_collection_events
        {proc_where}
        GROUP BY gc_type
    """, ["android.garbage_collection"])
    report["sections"]["gc"] = r

    # 4. Blocking calls (first 3s)
    if safe_pkg:
        r = run(f"""
            SELECT name, COUNT(*) as n,
                ROUND(SUM(dur) / 1e6, 1) as total_ms,
                ROUND(MAX(dur) / 1e6, 1) as max_ms
            FROM _android_critical_blocking_calls
            WHERE process_name = '{safe_pkg}'
            AND ts < (SELECT MIN(start_ts) + 3000000000
                      FROM process WHERE name = '{safe_pkg}')
            GROUP BY name ORDER BY total_ms DESC LIMIT 10
        """, ["android.critical_blocking_calls"])
        report["sections"]["blocking_calls_startup"] = r

    # 5. Lock contention
    if safe_pkg:
        r = run(f"""
            SELECT short_blocking_method as blocker,
                short_blocked_method as blocked,
                blocking_thread_name, blocked_thread_name,
                COUNT(*) as n, ROUND(SUM(dur) / 1e6, 1) as total_ms
            FROM android_monitor_contention
            WHERE process_name = '{safe_pkg}'
            AND ts < (SELECT MIN(start_ts) + 3000000000
                      FROM process WHERE name = '{safe_pkg}')
            GROUP BY blocker, blocked ORDER BY total_ms DESC LIMIT 8
        """, ["android.monitor_contention"])
        report["sections"]["lock_contention_startup"] = r

    # 6. Dex/class loading
    if safe_pkg:
        r = run(f"""
            SELECT s.name, ROUND(s.dur / 1e6, 1) as dur_ms
            FROM slice s
            JOIN thread_track tt ON s.track_id = tt.id
            JOIN thread t ON tt.utid = t.utid
            JOIN process p ON t.upid = p.upid
            WHERE p.name = '{safe_pkg}'
            AND (s.name LIKE 'OpenDexFilesFromOat%'
                 OR s.name LIKE 'AppImage%'
                 OR s.name LIKE 'VerifyClass%')
            AND s.dur > 5000000
            ORDER BY s.dur DESC LIMIT 10
        """)
        report["sections"]["dex_loading"] = r

    # 7. Activity lifecycle
    if safe_pkg:
        r = run(f"""
            SELECT s.name, ROUND(s.dur / 1e6, 1) as dur_ms,
                ROUND((s.ts - p.start_ts) / 1e6, 0) as offset_ms
            FROM slice s
            JOIN thread_track tt ON s.track_id = tt.id
            JOIN thread t ON tt.utid = t.utid
            JOIN process p ON t.upid = p.upid
            WHERE p.name = '{safe_pkg}' AND t.tid = p.pid
            AND (s.name LIKE 'bindApplication%'
                 OR s.name LIKE 'activityStart%'
                 OR s.name LIKE 'activityResume%'
                 OR s.name LIKE 'performCreate%'
                 OR (s.name LIKE 'inflate%' AND s.dur > 20000000))
            ORDER BY s.ts LIMIT 15
        """)
        report["sections"]["activity_lifecycle"] = r

    # 8. Main thread state during startup
    if safe_pkg:
        r = run(f"""
            SELECT
                ROUND(SUM(CASE WHEN ts2.state = 'Running' THEN ts2.dur ELSE 0 END) / 1e6, 0) as cpu_ms,
                ROUND(SUM(CASE WHEN ts2.state IN ('R', 'R+') THEN ts2.dur ELSE 0 END) / 1e6, 0) as wait_cpu_ms,
                ROUND(SUM(CASE WHEN ts2.state = 'S' THEN ts2.dur ELSE 0 END) / 1e6, 0) as sleep_ms,
                ROUND(SUM(CASE WHEN ts2.state IN ('D', 'DK') THEN ts2.dur ELSE 0 END) / 1e6, 0) as io_ms
            FROM thread_state ts2
            JOIN thread t USING (utid)
            JOIN process p USING (upid)
            WHERE p.name = '{safe_pkg}' AND t.tid = p.pid
            AND ts2.ts < (p.start_ts + 3000000000)
        """)
        report["sections"]["main_thread_state"] = r

    return json.dumps(report)


@mcp.tool()
def full_memory_analysis(package: str = "", alias: str = "default") -> str:
    """Run a complete memory analysis in one call.

    Combines: memory counters, GC events, Java heap class histogram,
    heap dominator tree (retained memory), and largest objects.
    Use after loading a memory profiling trace (capture-memory-trace).

    Args:
        package: Package name to analyze
        alias: Trace alias (default: "default")
    """
    err = trace_manager.require_trace(alias)
    if err:
        return json.dumps({"error": err})

    tp = trace_manager.get_trace(alias)
    report = {"package": package, "sections": {}}

    def run(sql, mods=None):
        if mods:
            for m in mods:
                query_engine.ensure_module(tp, m)
        return json.loads(query_engine.run_query(tp, sql))

    safe_pkg = package.replace("'", "''") if package else ""

    # 1. Memory counters
    if safe_pkg:
        r = run(f"""
            SELECT pct.name as counter,
                ROUND(MIN(c.value) / 1024 / 1024, 1) as min_mb,
                ROUND(MAX(c.value) / 1024 / 1024, 1) as max_mb,
                ROUND(AVG(c.value) / 1024 / 1024, 1) as avg_mb
            FROM counter c
            JOIN process_counter_track pct ON c.track_id = pct.id
            JOIN process p ON pct.upid = p.upid
            WHERE p.name = '{safe_pkg}'
            AND (pct.name LIKE '%rss%' OR pct.name LIKE '%pss%'
                 OR pct.name LIKE '%swap%')
            GROUP BY pct.name ORDER BY max_mb DESC
        """)
        report["sections"]["memory_counters"] = r

    # 2. GC events
    proc_where = f"WHERE process_name = '{safe_pkg}'" if safe_pkg else ""
    r = run(f"""
        SELECT gc_type, COUNT(*) as n,
            ROUND(SUM(gc_dur) / 1e6, 1) as total_ms,
            ROUND(MAX(gc_dur) / 1e6, 1) as max_ms,
            ROUND(SUM(reclaimed_mb), 1) as reclaimed_mb,
            ROUND(MAX(max_heap_mb), 1) as peak_heap_mb
        FROM android_garbage_collection_events
        {proc_where}
        GROUP BY gc_type ORDER BY total_ms DESC
    """, ["android.garbage_collection"])
    report["sections"]["gc_events"] = r

    # 3. Java heap class histogram (top by size)
    if safe_pkg:
        r = run(f"""
            SELECT hgc.name as class_name,
                COUNT(*) as instances,
                ROUND(SUM(hgo.self_size) / 1024.0 / 1024.0, 2) as shallow_mb
            FROM heap_graph_object hgo
            JOIN heap_graph_class hgc ON hgo.type_id = hgc.id
            JOIN process p ON hgo.upid = p.upid
            WHERE p.name = '{safe_pkg}' AND hgc.name != 'None'
            GROUP BY hgc.name ORDER BY SUM(hgo.self_size) DESC LIMIT 20
        """)
        report["sections"]["heap_class_histogram"] = r

    # 4. Dominator tree (retained memory)
    if safe_pkg:
        r = run(f"""
            SELECT hgc.name as class_name,
                COUNT(*) as instances,
                ROUND(SUM(d.dominated_size_bytes) / 1024.0 / 1024.0, 2) as retained_mb,
                MAX(d.dominated_obj_count) as max_dominated_objs
            FROM heap_graph_dominator_tree d
            JOIN heap_graph_object hgo ON d.id = hgo.id
            JOIN heap_graph_class hgc ON hgo.type_id = hgc.id
            JOIN process p ON hgo.upid = p.upid
            WHERE p.name = '{safe_pkg}' AND d.depth <= 3 AND hgc.name != 'None'
            GROUP BY hgc.name ORDER BY SUM(d.dominated_size_bytes) DESC LIMIT 15
        """, ["android.memory.heap_graph.dominator_tree"])
        report["sections"]["dominator_tree"] = r

    # 5. Largest single objects
    if safe_pkg:
        r = run(f"""
            SELECT hgc.name as class_name,
                ROUND(hgo.self_size / 1024.0, 2) as size_kb
            FROM heap_graph_object hgo
            JOIN heap_graph_class hgc ON hgo.type_id = hgc.id
            JOIN process p ON hgo.upid = p.upid
            WHERE p.name = '{safe_pkg}' AND hgo.self_size > 50000
            ORDER BY hgo.self_size DESC LIMIT 15
        """)
        report["sections"]["largest_objects"] = r

    # 6. Suspicious high-count classes (leak candidates)
    if safe_pkg:
        r = run(f"""
            SELECT hgc.name as class_name, COUNT(*) as instances,
                ROUND(SUM(hgo.self_size) / 1024.0, 2) as total_kb
            FROM heap_graph_object hgo
            JOIN heap_graph_class hgc ON hgo.type_id = hgc.id
            JOIN process p ON hgo.upid = p.upid
            WHERE p.name = '{safe_pkg}'
            AND hgc.name NOT IN ('None', 'java.lang.String', 'byte[]', 'int[]',
                'long[]', 'char[]', 'java.lang.Object[]')
            GROUP BY hgc.name HAVING COUNT(*) > 500
            ORDER BY COUNT(*) DESC LIMIT 15
        """)
        report["sections"]["high_count_classes"] = r

    return json.dumps(report)
