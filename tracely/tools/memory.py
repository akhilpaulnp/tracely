"""Memory counter analysis tools."""
import json
from tracely.server import mcp
from tracely.tools.core_tools import trace_manager, query_engine


@mcp.tool()
def analyze_memory(package: str = "", alias: str = "default") -> str:
    """Analyze memory usage from trace counters (RSS, PSS, swap).

    Shows min/max/avg memory for each counter type per process.

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

    # counter table has no name column -- name is on the track
    sql = f"""
        SELECT
            p.name as process_name,
            pct.name as counter_name,
            ROUND(MIN(c.value), 0) as min_value,
            ROUND(MAX(c.value), 0) as max_value,
            ROUND(AVG(c.value), 0) as avg_value,
            COUNT(*) as sample_count
        FROM counter c
        JOIN process_counter_track pct ON c.track_id = pct.id
        JOIN process p ON pct.upid = p.upid
        WHERE (pct.name LIKE '%rss%' OR pct.name LIKE '%pss%'
               OR pct.name LIKE '%swap%' OR pct.name LIKE '%mem%')
        {where}
        GROUP BY p.name, pct.name
        ORDER BY max_value DESC
    """
    return query_engine.run_query(tp, sql)
