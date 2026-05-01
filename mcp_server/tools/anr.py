"""ANR (Application Not Responding) detection tools."""
import json
from mcp_server.server import mcp
from mcp_server.tools.core_tools import trace_manager, query_engine


@mcp.tool()
def detect_anr(package: str = "", alias: str = "default") -> str:
    """Detect ANR events in the trace.

    Looks for main thread blocking >5s on input dispatch timeout.
    Distinct from jank (frame drops). ANR = system_server input dispatch timeout.

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
            s.name as slice_name,
            ROUND(s.dur / 1e6, 2) as duration_ms,
            s.ts,
            t.name as thread_name
        FROM slice s
        JOIN thread_track tt ON s.track_id = tt.id
        JOIN thread t ON tt.utid = t.utid
        JOIN process p ON t.upid = p.upid
        WHERE t.tid = p.pid
        AND s.dur > 5000000000
        {where}
        ORDER BY s.dur DESC
        LIMIT 20
    """
    return query_engine.run_query(tp, sql)
