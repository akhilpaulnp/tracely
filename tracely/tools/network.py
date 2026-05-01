"""Network packet analysis tools."""
import json
from tracely.server import mcp
from tracely.tools.core_tools import trace_manager, query_engine


@mcp.tool()
def analyze_network(package: str = "", alias: str = "default") -> str:
    """Analyze network activity: packets sent/received per app.

    Shows packet counts, bytes transferred, interfaces used, and direction.
    Helps identify battery drain from excessive networking.

    Args:
        package: Optional package name to filter
        alias: Trace alias (default: "default")
    """
    err = trace_manager.require_trace(alias)
    if err:
        return json.dumps({"error": err})

    tp = trace_manager.get_trace(alias)
    query_engine.ensure_module(tp, "android.network_packets")

    where = ""
    if package:
        safe_pkg = package.replace("'", "''")
        where = f"WHERE package_name LIKE '%{safe_pkg}%'"

    sql = f"""
        SELECT
            package_name,
            iface,
            direction,
            SUM(packet_count) as total_packets,
            SUM(packet_length) as total_bytes,
            ROUND(SUM(packet_length) / 1024.0, 2) as total_kb
        FROM android_network_packets
        {where}
        GROUP BY package_name, iface, direction
        ORDER BY total_bytes DESC
    """
    return query_engine.run_query(tp, sql)
