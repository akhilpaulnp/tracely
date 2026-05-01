"""CPU scheduling analysis tools."""
import json
from mcp_server.server import mcp
from mcp_server.tools.core_tools import trace_manager, query_engine


@mcp.tool()
def analyze_scheduling(package: str = "", alias: str = "default") -> str:
    """Analyze CPU scheduling: running, runnable, sleeping time per thread.

    Shows how much time each thread spent in each CPU state,
    helping identify threads that are blocked or starved.

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

    # Use thread_state table (has state column) instead of sched_slice (has end_state)
    sql = f"""
        SELECT
            p.name as process_name,
            t.name as thread_name,
            t.tid,
            ROUND(SUM(CASE WHEN ts.state = 'Running' THEN ts.dur ELSE 0 END) / 1e6, 2) as running_ms,
            ROUND(SUM(CASE WHEN ts.state IN ('R', 'R+') THEN ts.dur ELSE 0 END) / 1e6, 2) as runnable_ms,
            ROUND(SUM(CASE WHEN ts.state = 'S' THEN ts.dur ELSE 0 END) / 1e6, 2) as sleeping_ms,
            ROUND(SUM(CASE WHEN ts.state IN ('D', 'DK') THEN ts.dur ELSE 0 END) / 1e6, 2) as uninterruptible_ms
        FROM thread_state ts
        JOIN thread t USING (utid)
        JOIN process p USING (upid)
        WHERE ts.dur > 0 {where}
        GROUP BY p.name, t.name, t.tid
        ORDER BY running_ms DESC
        LIMIT 50
    """
    return query_engine.run_query(tp, sql)
