"""App startup performance analysis tools."""
import json
from mcp_server.server import mcp
from mcp_server.tools.core_tools import trace_manager, query_engine


@mcp.tool()
def analyze_startup(package: str = "", alias: str = "default") -> str:
    """Analyze app startup performance (cold, warm, hot starts).

    Uses Perfetto's android.startups module to identify startup events
    and measure time-to-initial-display and time-to-full-display.

    Args:
        package: Optional package name to filter
        alias: Trace alias (default: "default")
    """
    err = trace_manager.require_trace(alias)
    if err:
        return json.dumps({"error": err})

    tp = trace_manager.get_trace(alias)
    query_engine.ensure_module(tp, "android.startups.startups")

    where = ""
    if package:
        safe_pkg = package.replace("'", "''")
        where = f"WHERE package LIKE '%{safe_pkg}%'"

    sql = f"""
        SELECT
            package,
            startup_type,
            ROUND(dur / 1e6, 2) as duration_ms,
            ts,
            ROUND(time_to_initial_display / 1e6, 2) as ttid_ms,
            ROUND(time_to_full_display / 1e6, 2) as ttfd_ms
        FROM android_startups
        {where}
        ORDER BY ts
    """
    return query_engine.run_query(tp, sql)
