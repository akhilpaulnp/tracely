"""Input event latency analysis tools."""
import json
from mcp_server.server import mcp
from mcp_server.tools.core_tools import trace_manager, query_engine


@mcp.tool()
def analyze_input_latency(package: str = "", alias: str = "default") -> str:
    """Analyze input event latency: dispatch, handling, and end-to-end timing.

    Measures the full input pipeline from dispatch to frame presentation.
    Helps identify touch responsiveness issues.

    Args:
        package: Optional package name to filter
        alias: Trace alias (default: "default")
    """
    err = trace_manager.require_trace(alias)
    if err:
        return json.dumps({"error": err})

    tp = trace_manager.get_trace(alias)
    query_engine.ensure_module(tp, "android.input")

    where = ""
    if package:
        safe_pkg = package.replace("'", "''")
        where = f"WHERE process_name LIKE '%{safe_pkg}%'"

    sql = f"""
        SELECT
            process_name,
            COUNT(*) as event_count,
            ROUND(AVG(dispatch_latency_dur) / 1e6, 2) as avg_dispatch_ms,
            ROUND(AVG(handling_latency_dur) / 1e6, 2) as avg_handling_ms,
            ROUND(AVG(total_latency_dur) / 1e6, 2) as avg_total_ms,
            ROUND(MAX(total_latency_dur) / 1e6, 2) as max_total_ms,
            ROUND(AVG(end_to_end_latency_dur) / 1e6, 2) as avg_e2e_ms,
            ROUND(MAX(end_to_end_latency_dur) / 1e6, 2) as max_e2e_ms
        FROM android_input_events
        {where}
        GROUP BY process_name
        ORDER BY avg_total_ms DESC
    """
    return query_engine.run_query(tp, sql)
