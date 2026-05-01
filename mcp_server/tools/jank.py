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

    where = ""
    if package:
        safe_pkg = package.replace("'", "''")
        where = f"AND p.name LIKE '%{safe_pkg}%'"

    # Use actual_frame_timeline_slice (built-in table, no module load needed)
    # which has jank_type column for per-frame jank classification
    sql = f"""
        SELECT
            p.name as process_name,
            COUNT(*) as total_frames,
            SUM(CASE WHEN afts.jank_type != 0 THEN 1 ELSE 0 END) as janky_frames,
            ROUND(AVG(afts.dur) / 1e6, 2) as avg_frame_dur_ms,
            ROUND(MAX(afts.dur) / 1e6, 2) as max_frame_dur_ms
        FROM actual_frame_timeline_slice afts
        JOIN process_track pt ON afts.track_id = pt.id
        JOIN process p ON pt.upid = p.upid
        WHERE 1=1 {where}
        GROUP BY p.name
        ORDER BY janky_frames DESC
    """
    return query_engine.run_query(tp, sql)
