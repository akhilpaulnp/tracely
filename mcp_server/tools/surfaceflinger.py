"""SurfaceFlinger rendering pipeline analysis tools."""
import json
from mcp_server.server import mcp
from mcp_server.tools.core_tools import trace_manager, query_engine


@mcp.tool()
def analyze_surfaceflinger(alias: str = "default") -> str:
    """Analyze SurfaceFlinger rendering pipeline per-stage timing.

    Shows CPU time for frame signal, commit, composite stages,
    plus HWC present time and RenderEngine GPU time.
    Identifies bottlenecks in the graphics composition pipeline.

    Args:
        alias: Trace alias (default: "default")
    """
    err = trace_manager.require_trace(alias)
    if err:
        return json.dumps({"error": err})

    tp = trace_manager.get_trace(alias)
    query_engine.ensure_module(tp, "android.surfaceflinger")

    sql = """
        SELECT
            source,
            output_name,
            COUNT(*) as frame_count,
            ROUND(AVG(sf_cpu_frame_signal_nanos) / 1e6, 2) as avg_signal_ms,
            ROUND(AVG(sf_cpu_commit_nanos) / 1e6, 2) as avg_commit_ms,
            ROUND(AVG(sf_cpu_composite_nanos) / 1e6, 2) as avg_composite_ms,
            ROUND(AVG(hwc_present_nanos) / 1e6, 2) as avg_hwc_present_ms,
            ROUND(MAX(sf_cpu_composite_nanos) / 1e6, 2) as max_composite_ms
        FROM android_surfaceflinger_workloads
        GROUP BY source, output_name
        ORDER BY frame_count DESC
    """
    return query_engine.run_query(tp, sql)
