"""Multi-trace regression comparison tools."""
import json
from mcp_server.server import mcp
from mcp_server.tools.core_tools import trace_manager, query_engine


@mcp.tool()
def compare_traces(
    metric: str = "startup",
    baseline_alias: str = "baseline",
    current_alias: str = "current",
) -> str:
    """Compare two loaded traces to find performance regressions.

    Load two traces with different aliases first:
      load-trace /path/to/baseline.trace --alias baseline
      load-trace /path/to/current.trace --alias current

    Then compare specific metrics between them.

    Available metrics: startup, jank, memory, scheduling, gc

    Args:
        metric: Which metric to compare (startup, jank, memory, scheduling, gc)
        baseline_alias: Alias of the baseline trace (default: "baseline")
        current_alias: Alias of the current trace (default: "current")
    """
    for alias in [baseline_alias, current_alias]:
        err = trace_manager.require_trace(alias)
        if err:
            return json.dumps({"error": err})

    tp_base = trace_manager.get_trace(baseline_alias)
    tp_curr = trace_manager.get_trace(current_alias)

    queries = {
        "startup": {
            "modules": ["android.startup.startups", "android.startup.time_to_display"],
            "sql": """
                SELECT
                    s.package,
                    s.startup_type,
                    ROUND(s.dur / 1e6, 2) as duration_ms,
                    ROUND(ttd.time_to_initial_display / 1e6, 2) as ttid_ms
                FROM android_startups s
                LEFT JOIN android_startup_time_to_display ttd
                    ON s.startup_id = ttd.startup_id
                ORDER BY s.ts
            """,
        },
        "jank": {
            "modules": [],
            "sql": """
                SELECT
                    p.name as process_name,
                    COUNT(*) as total_frames,
                    SUM(CASE WHEN afts.jank_type != 0 THEN 1 ELSE 0 END) as janky_frames,
                    ROUND(AVG(afts.dur) / 1e6, 2) as avg_frame_ms
                FROM actual_frame_timeline_slice afts
                JOIN process_track pt ON afts.track_id = pt.id
                JOIN process p ON pt.upid = p.upid
                GROUP BY p.name
                ORDER BY janky_frames DESC
            """,
        },
        "memory": {
            "modules": [],
            "sql": """
                SELECT
                    p.name as process_name,
                    pct.name as counter_name,
                    ROUND(MAX(c.value), 0) as peak_value,
                    ROUND(AVG(c.value), 0) as avg_value
                FROM counter c
                JOIN process_counter_track pct ON c.track_id = pct.id
                JOIN process p ON pct.upid = p.upid
                WHERE pct.name LIKE '%rss%' OR pct.name LIKE '%pss%'
                GROUP BY p.name, pct.name
                ORDER BY peak_value DESC
                LIMIT 20
            """,
        },
        "scheduling": {
            "modules": [],
            "sql": """
                SELECT
                    p.name as process_name,
                    ROUND(SUM(CASE WHEN ts.state = 'Running' THEN ts.dur ELSE 0 END) / 1e6, 2) as running_ms,
                    ROUND(SUM(CASE WHEN ts.state IN ('R', 'R+') THEN ts.dur ELSE 0 END) / 1e6, 2) as runnable_ms
                FROM thread_state ts
                JOIN thread t USING (utid)
                JOIN process p USING (upid)
                WHERE t.tid = p.pid
                GROUP BY p.name
                ORDER BY running_ms DESC
                LIMIT 20
            """,
        },
        "gc": {
            "modules": ["android.garbage_collection"],
            "sql": """
                SELECT
                    process_name,
                    gc_type,
                    COUNT(*) as gc_count,
                    ROUND(SUM(gc_dur) / 1e6, 2) as total_gc_ms,
                    ROUND(SUM(reclaimed_mb), 2) as total_reclaimed_mb
                FROM android_garbage_collection_events
                GROUP BY process_name, gc_type
                ORDER BY total_gc_ms DESC
            """,
        },
    }

    if metric not in queries:
        return json.dumps({
            "error": f"Unknown metric '{metric}'. Available: {', '.join(queries.keys())}"
        })

    q = queries[metric]

    for mod in q["modules"]:
        query_engine.ensure_module(tp_base, mod)
        query_engine.ensure_module(tp_curr, mod)

    baseline_result = json.loads(query_engine.run_query(tp_base, q["sql"]))
    current_result = json.loads(query_engine.run_query(tp_curr, q["sql"]))

    return json.dumps({
        "metric": metric,
        "baseline": {"alias": baseline_alias, "data": baseline_result},
        "current": {"alias": current_alias, "data": current_result},
        "note": "Compare the baseline and current data to identify regressions.",
    })
