"""Critical blocking call detection tools."""
import json
from tracely.server import mcp
from tracely.tools.core_tools import trace_manager, query_engine


@mcp.tool()
def analyze_blocking_calls(package: str = "", alias: str = "default") -> str:
    """Detect critical blocking calls on the main thread.

    Identifies known blocking patterns: Handler delays, lock contention,
    binder transactions, GC pauses, Compose recomposition, and more.
    These are the root causes of jank and ANR.

    Args:
        package: Optional package name to filter
        alias: Trace alias (default: "default")
    """
    err = trace_manager.require_trace(alias)
    if err:
        return json.dumps({"error": err})

    tp = trace_manager.get_trace(alias)
    query_engine.ensure_module(tp, "android.critical_blocking_calls")

    where = ""
    if package:
        safe_pkg = package.replace("'", "''")
        where = f"WHERE process_name LIKE '%{safe_pkg}%'"

    sql = f"""
        SELECT
            process_name,
            name as blocking_call,
            COUNT(*) as occurrences,
            ROUND(SUM(dur) / 1e6, 2) as total_ms,
            ROUND(MAX(dur) / 1e6, 2) as max_ms,
            ROUND(AVG(dur) / 1e6, 2) as avg_ms
        FROM _android_critical_blocking_calls
        {where}
        GROUP BY process_name, name
        ORDER BY total_ms DESC
        LIMIT 30
    """
    return query_engine.run_query(tp, sql)
