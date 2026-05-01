"""Heap profiling analysis tools (native heapprofd + Java java_hprof)."""
import json
from mcp_server.server import mcp
from mcp_server.tools.core_tools import trace_manager, query_engine


@mcp.tool()
def analyze_heap_native(package: str = "", alias: str = "default") -> str:
    """Analyze native heap allocations from heapprofd data.

    Shows top allocation sites by size. Requires a trace captured
    with heapprofd enabled (Android 10+).

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
        where = f"WHERE p.name LIKE '%{safe_pkg}%'"

    sql = f"""
        SELECT
            p.name as process_name,
            spf.name as function_name,
            spm.name as module,
            SUM(hpa.count) as alloc_count,
            SUM(hpa.size) as total_bytes,
            ROUND(SUM(hpa.size) / 1024.0 / 1024.0, 2) as total_mb
        FROM heap_profile_allocation hpa
        JOIN stack_profile_callsite spc ON hpa.callsite_id = spc.id
        JOIN stack_profile_frame spf ON spc.frame_id = spf.id
        JOIN stack_profile_mapping spm ON spf.mapping = spm.id
        JOIN process p USING (upid)
        {where}
        GROUP BY p.name, spf.name, spm.name
        ORDER BY total_bytes DESC
        LIMIT 30
    """
    return query_engine.run_query(tp, sql)


@mcp.tool()
def analyze_heap_java(package: str = "", alias: str = "default") -> str:
    """Analyze Java heap dump from java_hprof data.

    Shows class histogram: object count and shallow size per class.
    Requires a trace captured with java_hprof enabled (Android 11+).

    Args:
        package: Optional package name to filter (filters by process)
        alias: Trace alias (default: "default")
    """
    err = trace_manager.require_trace(alias)
    if err:
        return json.dumps({"error": err})

    tp = trace_manager.get_trace(alias)

    where = ""
    if package:
        safe_pkg = package.replace("'", "''")
        where = f"WHERE p.name LIKE '%{safe_pkg}%'"

    sql = f"""
        SELECT
            p.name as process_name,
            hgc.name as class_name,
            COUNT(*) as instance_count,
            SUM(hgo.self_size) as total_shallow_bytes,
            ROUND(SUM(hgo.self_size) / 1024.0, 2) as total_kb
        FROM heap_graph_object hgo
        JOIN heap_graph_class hgc ON hgo.type_id = hgc.id
        JOIN process p ON hgo.upid = p.upid
        {where}
        GROUP BY p.name, hgc.name
        ORDER BY total_shallow_bytes DESC
        LIMIT 30
    """
    return query_engine.run_query(tp, sql)


@mcp.tool()
def analyze_heap_dominators(package: str = "", alias: str = "default") -> str:
    """Analyze Java heap dominator tree: which objects retain the most memory.

    Shows the top objects by retained (dominated) size -- the amount of memory
    that would be freed if this object were garbage collected.
    This is the key tool for finding memory leaks.

    Args:
        package: Optional package name to filter
        alias: Trace alias (default: "default")
    """
    err = trace_manager.require_trace(alias)
    if err:
        return json.dumps({"error": err})

    tp = trace_manager.get_trace(alias)
    query_engine.ensure_module(tp, "android.memory.heap_graph.dominator_tree")

    where = ""
    if package:
        safe_pkg = package.replace("'", "''")
        where = f"AND p.name LIKE '%{safe_pkg}%'"

    sql = f"""
        SELECT
            p.name as process_name,
            hgc.name as class_name,
            COUNT(*) as instance_count,
            ROUND(SUM(d.dominated_size_bytes) / 1024.0, 2) as total_retained_kb,
            ROUND(MAX(d.dominated_size_bytes) / 1024.0, 2) as max_retained_kb,
            ROUND(SUM(d.dominated_native_size_bytes) / 1024.0, 2) as total_native_kb,
            SUM(d.dominated_obj_count) as total_dominated_objects
        FROM heap_graph_dominator_tree d
        JOIN heap_graph_object hgo ON d.id = hgo.id
        JOIN heap_graph_class hgc ON hgo.type_id = hgc.id
        JOIN process p ON hgo.upid = p.upid
        WHERE d.depth <= 3 {where}
        GROUP BY p.name, hgc.name
        ORDER BY total_retained_kb DESC
        LIMIT 30
    """
    return query_engine.run_query(tp, sql)
