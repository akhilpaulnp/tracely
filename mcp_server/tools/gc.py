"""Garbage collection analysis tools."""
import json
from mcp_server.server import mcp
from mcp_server.tools.core_tools import trace_manager, query_engine


@mcp.tool()
def analyze_gc(package: str = "", alias: str = "default") -> str:
    """Analyze garbage collection events: frequency, duration, heap impact.

    Shows GC type, duration, heap size before/after, and reclaimed memory.
    Helps identify GC pressure during startup or animation.

    Args:
        package: Optional package name to filter
        alias: Trace alias (default: "default")
    """
    err = trace_manager.require_trace(alias)
    if err:
        return json.dumps({"error": err})

    tp = trace_manager.get_trace(alias)
    query_engine.ensure_module(tp, "android.garbage_collection")

    where = ""
    if package:
        safe_pkg = package.replace("'", "''")
        where = f"WHERE process_name LIKE '%{safe_pkg}%'"

    sql = f"""
        SELECT
            process_name,
            gc_type,
            COUNT(*) as gc_count,
            ROUND(SUM(gc_dur) / 1e6, 2) as total_gc_ms,
            ROUND(MAX(gc_dur) / 1e6, 2) as max_gc_ms,
            ROUND(AVG(gc_dur) / 1e6, 2) as avg_gc_ms,
            ROUND(SUM(reclaimed_mb), 2) as total_reclaimed_mb,
            ROUND(MAX(max_heap_mb), 2) as peak_heap_mb,
            SUM(CASE WHEN is_mark_compact THEN 1 ELSE 0 END) as mark_compact_count
        FROM android_garbage_collection_events
        {where}
        GROUP BY process_name, gc_type
        ORDER BY total_gc_ms DESC
    """
    return query_engine.run_query(tp, sql)
