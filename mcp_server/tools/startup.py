"""App startup performance analysis tools."""
import json
from mcp_server.server import mcp
from mcp_server.tools.core_tools import trace_manager, query_engine


@mcp.tool()
def analyze_startup(package: str = "", alias: str = "default") -> str:
    """Analyze app startup performance (cold, warm, hot starts).

    Shows startup duration, type, time-to-initial-display (TTID),
    and time-to-full-display (TTFD).

    Args:
        package: Optional package name to filter
        alias: Trace alias (default: "default")
    """
    err = trace_manager.require_trace(alias)
    if err:
        return json.dumps({"error": err})

    tp = trace_manager.get_trace(alias)
    query_engine.ensure_module(tp, "android.startup.startups")
    query_engine.ensure_module(tp, "android.startup.time_to_display")

    where = ""
    if package:
        safe_pkg = package.replace("'", "''")
        where = f"WHERE s.package LIKE '%{safe_pkg}%'"

    sql = f"""
        SELECT
            s.package,
            s.startup_type,
            ROUND(s.dur / 1e6, 2) as duration_ms,
            s.ts,
            ROUND(ttd.time_to_initial_display / 1e6, 2) as ttid_ms,
            ROUND(ttd.time_to_full_display / 1e6, 2) as ttfd_ms
        FROM android_startups s
        LEFT JOIN android_startup_time_to_display ttd
            ON s.startup_id = ttd.startup_id
        {where}
        ORDER BY s.ts
    """
    return query_engine.run_query(tp, sql)


@mcp.tool()
def startup_breakdown(package: str = "", alias: str = "default") -> str:
    """Break down app startup into phases with root cause for each delay.

    Shows what caused each phase of startup delay: GC, inflate, binder,
    class loading, resource loading, etc.

    Args:
        package: Optional package name to filter
        alias: Trace alias (default: "default")
    """
    err = trace_manager.require_trace(alias)
    if err:
        return json.dumps({"error": err})

    tp = trace_manager.get_trace(alias)
    query_engine.ensure_module(tp, "android.startup.startups")
    query_engine.ensure_module(tp, "android.startup.startup_breakdowns")

    where = ""
    if package:
        safe_pkg = package.replace("'", "''")
        where = f"AND s.package LIKE '%{safe_pkg}%'"

    sql = f"""
        SELECT
            s.package,
            b.reason,
            COUNT(*) as occurrences,
            ROUND(SUM(b.dur) / 1e6, 2) as total_ms,
            ROUND(MAX(b.dur) / 1e6, 2) as max_ms
        FROM android_startup_opinionated_breakdown b
        JOIN android_startups s ON b.startup_id = s.startup_id
        WHERE 1=1 {where}
        GROUP BY s.package, b.reason
        ORDER BY total_ms DESC
    """
    return query_engine.run_query(tp, sql)
