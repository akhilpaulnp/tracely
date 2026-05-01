"""Lock contention analysis tools."""
import json
from mcp_server.server import mcp
from mcp_server.tools.core_tools import trace_manager, query_engine


@mcp.tool()
def analyze_lock_contention(package: str = "", alias: str = "default") -> str:
    """Analyze monitor lock contention: which methods block which threads.

    Shows blocking method, blocked method, waiter count, and duration.
    Identifies lock chains that cause main thread blocking.

    Args:
        package: Optional package name to filter
        alias: Trace alias (default: "default")
    """
    err = trace_manager.require_trace(alias)
    if err:
        return json.dumps({"error": err})

    tp = trace_manager.get_trace(alias)
    query_engine.ensure_module(tp, "android.monitor_contention")

    where = ""
    if package:
        safe_pkg = package.replace("'", "''")
        where = f"WHERE process_name LIKE '%{safe_pkg}%'"

    sql = f"""
        SELECT
            process_name,
            short_blocking_method,
            short_blocked_method,
            blocked_thread_name,
            blocking_thread_name,
            COUNT(*) as contention_count,
            ROUND(SUM(dur) / 1e6, 2) as total_ms,
            ROUND(MAX(dur) / 1e6, 2) as max_ms,
            MAX(waiter_count) as max_waiters
        FROM android_monitor_contention
        {where}
        GROUP BY process_name, short_blocking_method, short_blocked_method
        ORDER BY total_ms DESC
        LIMIT 30
    """
    return query_engine.run_query(tp, sql)
