"""Binder IPC analysis tools."""
import json
from tracely.server import mcp
from tracely.tools.core_tools import trace_manager, query_engine


@mcp.tool()
def analyze_binder(package: str = "", alias: str = "default") -> str:
    """Analyze Binder IPC performance: call counts and latency distribution.

    Shows binder transactions grouped by interface, with timing stats.

    Args:
        package: Optional package name to filter
        alias: Trace alias (default: "default")
    """
    err = trace_manager.require_trace(alias)
    if err:
        return json.dumps({"error": err})

    tp = trace_manager.get_trace(alias)

    where = ""
    if package:
        safe_pkg = package.replace("'", "''")
        where = f"AND p.name LIKE '%{safe_pkg}%'"

    sql = f"""
        SELECT
            p.name as process_name,
            s.name as binder_call,
            COUNT(*) as call_count,
            ROUND(AVG(s.dur) / 1e6, 2) as avg_dur_ms,
            ROUND(MAX(s.dur) / 1e6, 2) as max_dur_ms,
            ROUND(MIN(s.dur) / 1e6, 2) as min_dur_ms
        FROM slice s
        JOIN thread_track tt ON s.track_id = tt.id
        JOIN thread t ON tt.utid = t.utid
        JOIN process p ON t.upid = p.upid
        WHERE s.name LIKE 'binder%' {where}
        GROUP BY p.name, s.name
        ORDER BY call_count DESC
        LIMIT 50
    """
    return query_engine.run_query(tp, sql)
