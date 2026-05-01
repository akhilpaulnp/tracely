"""Frame jank analysis tools."""
import json
from mcp_server.server import mcp
from mcp_server.tools.core_tools import trace_manager, query_engine


@mcp.tool()
def analyze_jank(package: str = "", alias: str = "default") -> str:
    """Analyze frame rendering performance and jank in a trace.

    Identifies dropped frames, janky frames, and frame timing issues
    using Perfetto's frame timeline data.

    Args:
        package: Optional package name to filter (e.g., "com.example.app")
        alias: Trace alias (default: "default")
    """
    err = trace_manager.require_trace(alias)
    if err:
        return json.dumps({"error": err})

    tp = trace_manager.get_trace(alias)
    query_engine.ensure_module(tp, "android.frames.timeline")

    where = ""
    if package:
        safe_pkg = package.replace("'", "''")
        where = f"AND process_name LIKE '%{safe_pkg}%'"

    sql = f"""
        SELECT
            process_name,
            COUNT(*) as total_frames,
            SUM(CASE WHEN jank_type != 'None' THEN 1 ELSE 0 END) as janky_frames,
            SUM(CASE WHEN jank_type = 'Dropped' THEN 1 ELSE 0 END) as dropped_frames,
            ROUND(AVG(dur) / 1e6, 2) as avg_frame_dur_ms,
            ROUND(MAX(dur) / 1e6, 2) as max_frame_dur_ms
        FROM android_frames_timeline
        WHERE 1=1 {where}
        GROUP BY process_name
        ORDER BY janky_frames DESC
    """
    return query_engine.run_query(tp, sql)
