"""Low Memory Killer event detection tools."""
import json
from mcp_server.server import mcp
from mcp_server.tools.core_tools import trace_manager, query_engine


@mcp.tool()
def detect_lmk(package: str = "", alias: str = "default") -> str:
    """Detect Low Memory Killer (LMK) events in the trace.

    Shows which processes were killed, their OOM score, and the kill reason.
    Answers: 'Why did my app get killed in the background?'

    Args:
        package: Optional package name to filter
        alias: Trace alias (default: "default")
    """
    err = trace_manager.require_trace(alias)
    if err:
        return json.dumps({"error": err})

    tp = trace_manager.get_trace(alias)
    query_engine.ensure_module(tp, "android.memory.lmk")

    where = ""
    if package:
        safe_pkg = package.replace("'", "''")
        where = f"WHERE process_name LIKE '%{safe_pkg}%'"

    sql = f"""
        SELECT
            process_name,
            pid,
            oom_score_adj,
            kill_reason,
            ts
        FROM android_lmk_events
        {where}
        ORDER BY ts
    """
    return query_engine.run_query(tp, sql)
