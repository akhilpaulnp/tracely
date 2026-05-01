"""OOM adjuster score tracking tools."""
import json
from mcp_server.server import mcp
from mcp_server.tools.core_tools import trace_manager, query_engine


@mcp.tool()
def analyze_oom_priority(package: str = "", alias: str = "default") -> str:
    """Track OOM adjuster score changes for processes.

    Shows how the system prioritizes processes over time.
    Higher scores = more likely to be killed. Helps understand
    why an app gets killed or deprioritized.

    Args:
        package: Optional package name to filter
        alias: Trace alias (default: "default")
    """
    err = trace_manager.require_trace(alias)
    if err:
        return json.dumps({"error": err})

    tp = trace_manager.get_trace(alias)
    query_engine.ensure_module(tp, "android.oom_adjuster")

    where = ""
    if package:
        safe_pkg = package.replace("'", "''")
        where = f"WHERE process_name LIKE '%{safe_pkg}%'"

    sql = f"""
        SELECT
            process_name,
            bucket,
            score,
            COUNT(*) as transitions,
            ROUND(SUM(dur) / 1e9, 2) as total_time_s,
            ROUND(MIN(dur) / 1e6, 2) as min_dur_ms,
            ROUND(MAX(dur) / 1e6, 2) as max_dur_ms
        FROM android_oom_adj_intervals
        {where}
        GROUP BY process_name, bucket, score
        ORDER BY process_name, score
    """
    return query_engine.run_query(tp, sql)
