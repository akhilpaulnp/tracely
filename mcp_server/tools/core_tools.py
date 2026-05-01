"""Core trace management tools."""
import json
import os
from mcp_server.server import mcp
from mcp_server.core.trace_manager import TraceManager
from mcp_server.core.query_engine import QueryEngine

# Shared instances used by all tool modules
trace_manager = TraceManager()
query_engine = QueryEngine()


@mcp.tool()
def load_trace(path: str, alias: str = "default") -> str:
    """Load a Perfetto trace file for analysis.

    Args:
        path: Path to the .perfetto-trace file
        alias: Optional alias for multi-trace comparison (default: "default")
    """
    if not os.path.exists(path):
        return json.dumps({"error": f"File not found: {path}"})

    try:
        info = trace_manager.load_trace(path, alias)
        tp = trace_manager.get_trace(alias)
        summary = _generate_summary(tp, alias, path)
        return json.dumps({"status": "loaded", **info, "summary": summary})
    except RuntimeError as e:
        return json.dumps({"error": str(e)})
    except Exception as e:
        return json.dumps({"error": f"Failed to load trace: {e}"})


@mcp.tool()
def close_trace(alias: str = "default") -> str:
    """Close a loaded trace to free resources.

    Args:
        alias: Alias of the trace to close (default: "default")
    """
    try:
        trace_manager.close_trace(alias)
        return json.dumps({"status": "closed", "alias": alias})
    except KeyError as e:
        return json.dumps({"error": str(e)})


@mcp.tool()
def list_traces() -> str:
    """List all currently loaded traces with their aliases and paths."""
    traces = trace_manager.list_traces()
    return json.dumps({"traces": traces, "count": len(traces)})


@mcp.tool()
def trace_summary(alias: str = "default") -> str:
    """Get a summary overview of a loaded trace.

    Returns duration, processes, available data sources, and key metrics.
    Helps Claude understand what's in the trace before detailed analysis.

    Args:
        alias: Alias of the trace to summarize (default: "default")
    """
    err = trace_manager.require_trace(alias)
    if err:
        return json.dumps({"error": err})

    tp = trace_manager.get_trace(alias)
    return json.dumps(_generate_summary(tp, alias, ""))


def _generate_summary(tp, alias: str, path: str) -> dict:
    """Generate trace overview: duration, processes, data sources."""
    summary = {"alias": alias, "path": path}

    try:
        result = tp.query("SELECT min(ts) as start_ts, max(ts) as end_ts FROM slice")
        for row in result:
            start_ns = row.start_ts or 0
            end_ns = row.end_ts or 0
            summary["duration_ns"] = end_ns - start_ns
            summary["duration_s"] = round((end_ns - start_ns) / 1e9, 2)

        result = tp.query(
            "SELECT pid, name FROM process WHERE name IS NOT NULL "
            "ORDER BY pid LIMIT 20"
        )
        procs = [{"pid": r.pid, "name": r.name} for r in result]
        summary["processes"] = procs
        summary["process_count"] = len(procs)

        available = []
        for table, label in [
            ("slice", "slices/events"),
            ("sched_slice", "CPU scheduling"),
            ("counter", "counters (memory, CPU freq, etc.)"),
            ("android_logs", "Android logcat"),
            ("heap_profile_allocation", "native heap profiles"),
            ("heap_graph_object", "Java heap dumps"),
        ]:
            try:
                r = tp.query(f"SELECT count(*) as cnt FROM {table}")
                for row in r:
                    if row.cnt > 0:
                        available.append({"source": label, "rows": row.cnt})
            except Exception:
                pass
        summary["data_sources"] = available

    except Exception as e:
        summary["error"] = f"Summary generation partial: {e}"

    return summary
