"""SQL query tools for direct trace exploration."""
import json
from mcp_server.server import mcp
from mcp_server.tools.core_tools import trace_manager, query_engine


@mcp.tool()
def execute_sql(sql: str, alias: str = "default") -> str:
    """Execute a PerfettoSQL query against a loaded trace.

    Returns up to 5000 rows. For large result sets, use GROUP BY or LIMIT.
    Load stdlib modules with: INCLUDE PERFETTO MODULE <module_name>
    (must be a separate query before the main query).

    Args:
        sql: PerfettoSQL query to execute
        alias: Trace alias to query against (default: "default")
    """
    err = trace_manager.require_trace(alias)
    if err:
        return json.dumps({"error": err})

    tp = trace_manager.get_trace(alias)
    return query_engine.run_query(tp, sql)


@mcp.tool()
def list_tables(alias: str = "default") -> str:
    """List all available tables and views in a loaded trace.

    Args:
        alias: Trace alias (default: "default")
    """
    err = trace_manager.require_trace(alias)
    if err:
        return json.dumps({"error": err})

    tp = trace_manager.get_trace(alias)
    sql = """
        SELECT name, type FROM sqlite_schema
        WHERE type IN ('table', 'view')
        AND name NOT LIKE 'sqlite_%'
        AND name NOT LIKE '\\_%' ESCAPE '\\'
        ORDER BY type, name
    """
    return query_engine.run_query(tp, sql)


@mcp.tool()
def describe_table(table_name: str, alias: str = "default") -> str:
    """Show column names and types for a table.

    Args:
        table_name: Name of the table to describe
        alias: Trace alias (default: "default")
    """
    err = trace_manager.require_trace(alias)
    if err:
        return json.dumps({"error": err})

    tp = trace_manager.get_trace(alias)

    # Validate table name against schema before interpolating
    safe_name = table_name.replace("'", "''")
    check = tp.query(
        f"SELECT name FROM sqlite_schema WHERE name = '{safe_name}' LIMIT 1"
    )
    found = False
    for _ in check:
        found = True
    if not found:
        return json.dumps({
            "error": f"Table '{table_name}' not found",
            "suggestion": "Use list-tables to see available tables.",
        })

    return query_engine.run_query(tp, f"PRAGMA table_info('{safe_name}')")


@mcp.tool()
def list_processes(alias: str = "default") -> str:
    """List all processes in the trace with pid, name, and uid.

    Args:
        alias: Trace alias (default: "default")
    """
    err = trace_manager.require_trace(alias)
    if err:
        return json.dumps({"error": err})

    tp = trace_manager.get_trace(alias)
    return query_engine.run_query(
        tp, "SELECT pid, name, uid FROM process ORDER BY pid"
    )
