import json
from perfetto.trace_processor import TraceProcessor
from perfetto.common.exceptions import PerfettoException

MAX_ROWS = 5000


class QueryEngine:
    """Execute SQL queries against TraceProcessor with formatting and module management."""

    def __init__(self):
        self._loaded_modules: dict[int, set[str]] = {}

    def ensure_module(self, tp: TraceProcessor, module: str) -> None:
        """Load a PerfettoSQL module if not already loaded.

        Must be called in a separate query before using module tables.
        """
        tp_id = id(tp)
        if tp_id not in self._loaded_modules:
            self._loaded_modules[tp_id] = set()

        if module not in self._loaded_modules[tp_id]:
            tp.query(f"INCLUDE PERFETTO MODULE {module}")
            self._loaded_modules[tp_id].add(module)

    def run_query(
        self,
        tp: TraceProcessor,
        sql: str,
        max_rows: int = MAX_ROWS,
    ) -> str:
        """Execute SQL and return JSON string with rows, columns, metadata.

        Returns JSON: {"columns": [...], "rows": [...], "row_count": N, "truncated": bool}
        On error: {"error": "message", "sql": "the query"}
        """
        try:
            result = tp.query(sql)
            columns = result.column_names
            rows = []
            truncated = False

            for row in result:
                if len(rows) >= max_rows:
                    truncated = True
                    break
                row_dict = {}
                for col in columns:
                    val = getattr(row, col)
                    if isinstance(val, bytes):
                        val = val.hex()
                    row_dict[col] = val
                rows.append(row_dict)

            return json.dumps({
                "columns": columns,
                "rows": rows,
                "row_count": len(rows),
                "truncated": truncated,
            })
        except PerfettoException as e:
            return json.dumps({
                "error": str(e),
                "sql": sql,
                "suggestion": "Check SQL syntax. Use list-tables to see available tables.",
            })
        except Exception as e:
            return json.dumps({
                "error": f"Query execution error: {e}",
                "sql": sql,
            })
