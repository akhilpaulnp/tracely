# Perfetto MCP Server Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a modular Python MCP server that gives Claude deep access to Android performance trace analysis via Perfetto's TraceProcessor.

**Architecture:** FastMCP server with stdio transport. Core layer (trace_manager, query_engine, device, capture) provides shared infrastructure. Analysis modules in tools/ use @mcp.tool() decorators directly. Resources provide domain knowledge. Prompts guide analysis workflows.

**Tech Stack:** Python 3.9+, FastMCP (mcp package), Perfetto Python SDK (TraceProcessor), asyncio, subprocess (adb)

**Design Doc:** `~/.gstack/projects/PerfettoAI/entri--design-20260430-233657.md`

---

## File Structure

```
mcp_server/
  __init__.py
  server.py              # FastMCP instance + tool module imports + run()
  core/
    __init__.py
    trace_manager.py      # Multi-trace lifecycle (max 3, thread-safe)
    query_engine.py       # SQL execution + module loading + result formatting
    device.py             # ADB device detection
    capture.py            # Trace capture orchestration (timed + live)
  tools/
    __init__.py           # Imports all tool modules to register decorators
    core_tools.py         # load-trace, close-trace, list-traces, trace-summary
    query_tools.py        # execute-sql, list-tables, describe-table
    process_tools.py      # list-processes
    jank.py               # analyze-jank
    startup.py            # analyze-startup
    memory.py             # analyze-memory
    scheduling.py         # analyze-scheduling
    binder.py             # analyze-binder
    anr.py                # detect-anr
    heap.py               # analyze-heap-native, analyze-heap-java
    capture_tools.py      # capture-trace, capture-memory, live-trace-start/stop
    regression.py         # compare-traces (stretch goal)
  resources/
    android_analysis.md   # Domain knowledge for Claude
    perfetto_sql_ref.md   # PerfettoSQL quick reference
  tests/
    __init__.py
    conftest.py           # Shared fixtures (sample traces, mocked adb)
    test_trace_manager.py
    test_query_engine.py
    test_core_tools.py
    test_query_tools.py
    test_jank.py
    test_startup.py
    test_memory.py
    test_capture.py
  pyproject.toml
  README.md
```

---

### Task 1: Project Scaffolding

**Files:**
- Create: `mcp_server/__init__.py`
- Create: `mcp_server/server.py`
- Create: `mcp_server/core/__init__.py`
- Create: `mcp_server/tools/__init__.py`
- Create: `mcp_server/resources/` (directory)
- Create: `mcp_server/tests/__init__.py`
- Create: `pyproject.toml`

- [ ] **Step 1: Create pyproject.toml**

```toml
[build-system]
requires = ["setuptools>=68.0"]
build-backend = "setuptools.backends._legacy:_Backend"

[project]
name = "perfetto-mcp"
version = "0.1.0"
description = "MCP server for Perfetto trace analysis"
readme = "README.md"
license = "Apache-2.0"
requires-python = ">=3.9"
dependencies = [
    "mcp",
    "perfetto",
]

[project.optional-dependencies]
dev = [
    "pytest",
    "pytest-asyncio",
]

[tool.setuptools.packages.find]
where = ["."]
include = ["mcp_server*"]
```

- [ ] **Step 2: Create server.py with FastMCP instance**

```python
# mcp_server/server.py
from mcp.server.fastmcp import FastMCP

mcp = FastMCP("perfetto")


def main():
    """Entry point for the MCP server."""
    # Import tool modules to register their @mcp.tool() decorators
    import mcp_server.tools  # noqa: F401
    mcp.run(transport="stdio")


if __name__ == "__main__":
    main()
```

- [ ] **Step 3: Create empty __init__.py files**

```python
# mcp_server/__init__.py
# mcp_server/core/__init__.py
# mcp_server/tests/__init__.py
```

```python
# mcp_server/tools/__init__.py
"""Import all tool modules to register their @mcp.tool() decorators."""
from mcp_server.tools import core_tools  # noqa: F401
from mcp_server.tools import query_tools  # noqa: F401
```

- [ ] **Step 4: Create stub tool files so imports don't fail**

```python
# mcp_server/tools/core_tools.py
"""Core trace management tools."""

# mcp_server/tools/query_tools.py
"""SQL query tools."""
```

- [ ] **Step 5: Verify the server starts**

Run: `cd /Users/entri/PerfettoAI && python -c "from mcp_server.server import mcp; print('OK')"`
Expected: `OK`

- [ ] **Step 6: Commit**

```bash
git init /Users/entri/PerfettoAI
cd /Users/entri/PerfettoAI
git add mcp_server/ pyproject.toml
git commit -m "feat: scaffold perfetto MCP server with FastMCP"
```

---

### Task 2: TraceManager (Core)

**Files:**
- Create: `mcp_server/core/trace_manager.py`
- Create: `mcp_server/tests/test_trace_manager.py`

- [ ] **Step 1: Write failing tests for TraceManager**

```python
# mcp_server/tests/test_trace_manager.py
import pytest
from unittest.mock import MagicMock, patch
from mcp_server.core.trace_manager import TraceManager


class TestTraceManager:
    def setup_method(self):
        self.tm = TraceManager()

    def test_no_traces_initially(self):
        assert self.tm.list_traces() == []

    def test_require_trace_fails_when_empty(self):
        result = self.tm.require_trace()
        assert result is not None  # Returns error string
        assert "no trace" in result.lower()

    @patch("mcp_server.core.trace_manager.TraceProcessor")
    def test_load_trace_success(self, mock_tp_class):
        mock_tp = MagicMock()
        mock_tp_class.return_value = mock_tp
        self.tm.load_trace("/fake/trace.perfetto-trace", "test")
        assert "test" in [t["alias"] for t in self.tm.list_traces()]

    @patch("mcp_server.core.trace_manager.TraceProcessor")
    def test_load_trace_default_alias(self, mock_tp_class):
        mock_tp_class.return_value = MagicMock()
        self.tm.load_trace("/fake/trace.perfetto-trace")
        traces = self.tm.list_traces()
        assert len(traces) == 1
        assert traces[0]["alias"] == "default"

    @patch("mcp_server.core.trace_manager.TraceProcessor")
    def test_max_traces_enforced(self, mock_tp_class):
        mock_tp_class.return_value = MagicMock()
        self.tm.load_trace("/fake/a.trace", "a")
        self.tm.load_trace("/fake/b.trace", "b")
        self.tm.load_trace("/fake/c.trace", "c")
        with pytest.raises(RuntimeError, match="maximum"):
            self.tm.load_trace("/fake/d.trace", "d")

    @patch("mcp_server.core.trace_manager.TraceProcessor")
    def test_close_trace(self, mock_tp_class):
        mock_tp = MagicMock()
        mock_tp_class.return_value = mock_tp
        self.tm.load_trace("/fake/trace.perfetto-trace", "test")
        self.tm.close_trace("test")
        assert self.tm.list_traces() == []
        mock_tp.close.assert_called_once()

    def test_close_nonexistent_trace(self):
        with pytest.raises(KeyError):
            self.tm.close_trace("nonexistent")

    @patch("mcp_server.core.trace_manager.TraceProcessor")
    def test_get_trace(self, mock_tp_class):
        mock_tp = MagicMock()
        mock_tp_class.return_value = mock_tp
        self.tm.load_trace("/fake/trace.perfetto-trace", "test")
        assert self.tm.get_trace("test") is mock_tp

    @patch("mcp_server.core.trace_manager.TraceProcessor")
    def test_require_trace_succeeds_when_loaded(self, mock_tp_class):
        mock_tp_class.return_value = MagicMock()
        self.tm.load_trace("/fake/trace.perfetto-trace", "test")
        assert self.tm.require_trace() is None  # No error
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd /Users/entri/PerfettoAI && python -m pytest mcp_server/tests/test_trace_manager.py -v`
Expected: FAIL (module not found)

- [ ] **Step 3: Implement TraceManager**

```python
# mcp_server/core/trace_manager.py
import threading
from typing import Optional
from perfetto.trace_processor import TraceProcessor, TraceProcessorConfig

MAX_TRACES = 3


class TraceManager:
    """Manages multiple TraceProcessor instances with aliases."""

    def __init__(self):
        self._traces: dict[str, dict] = {}
        self._lock = threading.Lock()

    def load_trace(self, path: str, alias: str = "default") -> dict:
        """Load a trace file and assign it an alias.

        Returns dict with alias, path info. Raises RuntimeError if max exceeded.
        """
        with self._lock:
            if alias in self._traces:
                # Close existing trace with same alias
                self._traces[alias]["tp"].close()
                del self._traces[alias]

            if len(self._traces) >= MAX_TRACES:
                loaded = ", ".join(self._traces.keys())
                raise RuntimeError(
                    f"Maximum {MAX_TRACES} traces. Close one first. "
                    f"Loaded: {loaded}"
                )

            config = TraceProcessorConfig(bin_path=None, verbose=False)
            tp = TraceProcessor(trace=path, config=config)
            self._traces[alias] = {"tp": tp, "path": path, "alias": alias}
            return {"alias": alias, "path": path}

    def close_trace(self, alias: str = "default") -> None:
        """Close a trace by alias. Raises KeyError if not found."""
        with self._lock:
            if alias not in self._traces:
                raise KeyError(f"No trace with alias '{alias}'")
            self._traces[alias]["tp"].close()
            del self._traces[alias]

    def get_trace(self, alias: str = "default") -> TraceProcessor:
        """Get TraceProcessor by alias. Raises KeyError if not found."""
        with self._lock:
            if alias not in self._traces:
                raise KeyError(f"No trace with alias '{alias}'")
            return self._traces[alias]["tp"]

    def list_traces(self) -> list[dict]:
        """List all loaded traces with alias and path."""
        with self._lock:
            return [
                {"alias": info["alias"], "path": info["path"]}
                for info in self._traces.values()
            ]

    def require_trace(self, alias: str = "default") -> Optional[str]:
        """Return None if trace exists, error string otherwise."""
        with self._lock:
            if not self._traces:
                return "No trace loaded. Use load-trace first."
            if alias not in self._traces:
                loaded = ", ".join(self._traces.keys())
                return f"No trace '{alias}'. Loaded: {loaded}"
            return None
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd /Users/entri/PerfettoAI && python -m pytest mcp_server/tests/test_trace_manager.py -v`
Expected: All PASS

- [ ] **Step 5: Commit**

```bash
cd /Users/entri/PerfettoAI
git add mcp_server/core/trace_manager.py mcp_server/tests/test_trace_manager.py
git commit -m "feat: add TraceManager with multi-trace support"
```

---

### Task 3: QueryEngine (Core)

**Files:**
- Create: `mcp_server/core/query_engine.py`
- Create: `mcp_server/tests/test_query_engine.py`

- [ ] **Step 1: Write failing tests**

```python
# mcp_server/tests/test_query_engine.py
import pytest
import json
from unittest.mock import MagicMock
from mcp_server.core.query_engine import QueryEngine


class TestQueryEngine:
    def setup_method(self):
        self.mock_tp = MagicMock()
        self.engine = QueryEngine()

    def test_run_query_returns_json(self):
        mock_result = MagicMock()
        mock_result.column_names.return_value = ["name", "pid"]
        row1 = MagicMock()
        row1.name = "com.example"
        row1.pid = 1234
        mock_result.__iter__ = MagicMock(return_value=iter([row1]))
        self.mock_tp.query.return_value = mock_result

        result = self.engine.run_query(self.mock_tp, "SELECT name, pid FROM process")
        parsed = json.loads(result)
        assert len(parsed["rows"]) == 1
        assert parsed["rows"][0]["name"] == "com.example"

    def test_run_query_max_rows(self):
        mock_result = MagicMock()
        mock_result.column_names.return_value = ["id"]
        rows = [MagicMock(id=i) for i in range(6000)]
        mock_result.__iter__ = MagicMock(return_value=iter(rows))
        self.mock_tp.query.return_value = mock_result

        result = self.engine.run_query(self.mock_tp, "SELECT id FROM big_table")
        parsed = json.loads(result)
        assert len(parsed["rows"]) == 5000
        assert parsed["truncated"] is True

    def test_run_query_error(self):
        from perfetto.common.exceptions import PerfettoException
        self.mock_tp.query.side_effect = PerfettoException("bad sql")

        result = self.engine.run_query(self.mock_tp, "INVALID SQL")
        parsed = json.loads(result)
        assert "error" in parsed

    def test_ensure_module_loads_once(self):
        mock_result = MagicMock()
        mock_result.column_names.return_value = []
        mock_result.__iter__ = MagicMock(return_value=iter([]))
        self.mock_tp.query.return_value = mock_result

        self.engine.ensure_module(self.mock_tp, "android.frames.timeline")
        self.engine.ensure_module(self.mock_tp, "android.frames.timeline")
        # Should only call query once for the INCLUDE statement
        include_calls = [
            c for c in self.mock_tp.query.call_args_list
            if "INCLUDE PERFETTO MODULE" in str(c)
        ]
        assert len(include_calls) == 1
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd /Users/entri/PerfettoAI && python -m pytest mcp_server/tests/test_query_engine.py -v`
Expected: FAIL

- [ ] **Step 3: Implement QueryEngine**

```python
# mcp_server/core/query_engine.py
import json
from typing import Optional
from perfetto.trace_processor import TraceProcessor
from perfetto.common.exceptions import PerfettoException

MAX_ROWS = 5000


class QueryEngine:
    """Execute SQL queries against TraceProcessor with formatting and module management."""

    def __init__(self):
        # Track loaded modules per trace processor instance (by id)
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
        except PerfettoException as e:
            return json.dumps({
                "error": str(e),
                "sql": sql,
                "suggestion": "Check SQL syntax. Use list-tables to see available tables.",
            })

        columns = result.column_names()
        rows = []
        truncated = False

        for row in result:
            if len(rows) >= max_rows:
                truncated = True
                break
            row_dict = {}
            for col in columns:
                val = getattr(row, col)
                # Handle bytes for JSON serialization
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
```

- [ ] **Step 4: Run tests**

Run: `cd /Users/entri/PerfettoAI && python -m pytest mcp_server/tests/test_query_engine.py -v`
Expected: All PASS

- [ ] **Step 5: Commit**

```bash
cd /Users/entri/PerfettoAI
git add mcp_server/core/query_engine.py mcp_server/tests/test_query_engine.py
git commit -m "feat: add QueryEngine with SQL execution and module management"
```

---

### Task 4: Core Tools (load-trace, execute-sql, list-tables, describe-table, trace-summary)

**Files:**
- Create: `mcp_server/tools/core_tools.py`
- Create: `mcp_server/tools/query_tools.py`
- Create: `mcp_server/tests/test_core_tools.py`

- [ ] **Step 1: Write core_tools.py with trace management tools**

```python
# mcp_server/tools/core_tools.py
"""Core trace management tools."""
import json
import os
from mcp_server.server import mcp
from mcp_server.core.trace_manager import TraceManager
from mcp_server.core.query_engine import QueryEngine

# Shared instances
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
        # Auto-generate summary
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
    qe = query_engine
    summary = {"alias": alias, "path": path}

    try:
        # Trace duration
        result = tp.query("SELECT min(ts) as start_ts, max(ts) as end_ts FROM slice")
        for row in result:
            start_ns = row.start_ts or 0
            end_ns = row.end_ts or 0
            summary["duration_ns"] = end_ns - start_ns
            summary["duration_s"] = round((end_ns - start_ns) / 1e9, 2)

        # Process count and top processes
        result = tp.query(
            "SELECT pid, name FROM process WHERE name IS NOT NULL "
            "ORDER BY pid LIMIT 20"
        )
        procs = [{"pid": r.pid, "name": r.name} for r in result]
        summary["processes"] = procs
        summary["process_count"] = len(procs)

        # Available data sources (check key tables)
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
```

- [ ] **Step 2: Write query_tools.py**

```python
# mcp_server/tools/query_tools.py
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

    # Validate table name exists before interpolating
    check = tp.query(
        "SELECT name FROM sqlite_schema WHERE name = '{}' LIMIT 1".format(
            table_name.replace("'", "''")
        )
    )
    found = False
    for _ in check:
        found = True
    if not found:
        return json.dumps({
            "error": f"Table '{table_name}' not found",
            "suggestion": "Use list-tables to see available tables.",
        })

    return query_engine.run_query(
        tp, f"PRAGMA table_info('{table_name.replace(chr(39), chr(39)+chr(39))}')"
    )


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
```

- [ ] **Step 3: Update tools/__init__.py to import new modules**

```python
# mcp_server/tools/__init__.py
"""Import all tool modules to register their @mcp.tool() decorators."""
from mcp_server.tools import core_tools  # noqa: F401
from mcp_server.tools import query_tools  # noqa: F401
```

- [ ] **Step 4: Verify server starts with tools registered**

Run: `cd /Users/entri/PerfettoAI && python -c "from mcp_server.server import mcp; import mcp_server.tools; print(f'Tools registered: {len(mcp._tool_manager._tools)}')"`
Expected: Shows number of registered tools > 0

- [ ] **Step 5: Commit**

```bash
cd /Users/entri/PerfettoAI
git add mcp_server/tools/core_tools.py mcp_server/tools/query_tools.py mcp_server/tools/__init__.py
git commit -m "feat: add core trace management and SQL query tools"
```

---

### Task 5: Analysis Plugins (jank, startup, memory, scheduling, binder)

**Files:**
- Create: `mcp_server/tools/jank.py`
- Create: `mcp_server/tools/startup.py`
- Create: `mcp_server/tools/memory.py`
- Create: `mcp_server/tools/scheduling.py`
- Create: `mcp_server/tools/binder.py`

- [ ] **Step 1: Implement jank analysis**

```python
# mcp_server/tools/jank.py
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
        where = f"AND process_name LIKE '%{package}%'"

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
```

- [ ] **Step 2: Implement startup analysis**

```python
# mcp_server/tools/startup.py
"""App startup performance analysis tools."""
import json
from mcp_server.server import mcp
from mcp_server.tools.core_tools import trace_manager, query_engine


@mcp.tool()
def analyze_startup(package: str = "", alias: str = "default") -> str:
    """Analyze app startup performance (cold, warm, hot starts).

    Uses Perfetto's android.startups module to identify startup events
    and measure time-to-initial-display and time-to-full-display.

    Args:
        package: Optional package name to filter
        alias: Trace alias (default: "default")
    """
    err = trace_manager.require_trace(alias)
    if err:
        return json.dumps({"error": err})

    tp = trace_manager.get_trace(alias)
    query_engine.ensure_module(tp, "android.startups.startups")

    where = ""
    if package:
        where = f"WHERE package LIKE '%{package}%'"

    sql = f"""
        SELECT
            package,
            startup_type,
            ROUND(dur / 1e6, 2) as duration_ms,
            ts,
            ROUND(time_to_initial_display / 1e6, 2) as ttid_ms,
            ROUND(time_to_full_display / 1e6, 2) as ttfd_ms
        FROM android_startups
        {where}
        ORDER BY ts
    """
    return query_engine.run_query(tp, sql)
```

- [ ] **Step 3: Implement memory analysis**

```python
# mcp_server/tools/memory.py
"""Memory counter analysis tools."""
import json
from mcp_server.server import mcp
from mcp_server.tools.core_tools import trace_manager, query_engine


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
        where = f"AND p.name LIKE '%{package}%'"

    sql = f"""
        SELECT
            p.name as process_name,
            c.name as counter_name,
            ROUND(MIN(c.value), 0) as min_value,
            ROUND(MAX(c.value), 0) as max_value,
            ROUND(AVG(c.value), 0) as avg_value,
            COUNT(*) as sample_count
        FROM counter c
        JOIN process_counter_track pct ON c.track_id = pct.id
        JOIN process p ON pct.upid = p.upid
        WHERE (c.name LIKE '%rss%' OR c.name LIKE '%pss%'
               OR c.name LIKE '%swap%' OR c.name LIKE '%mem%')
        {where}
        GROUP BY p.name, c.name
        ORDER BY max_value DESC
    """
    return query_engine.run_query(tp, sql)
```

- [ ] **Step 4: Implement scheduling analysis**

```python
# mcp_server/tools/scheduling.py
"""CPU scheduling analysis tools."""
import json
from mcp_server.server import mcp
from mcp_server.tools.core_tools import trace_manager, query_engine


@mcp.tool()
def analyze_scheduling(package: str = "", alias: str = "default") -> str:
    """Analyze CPU scheduling: running, runnable, sleeping time per thread.

    Shows how much time each thread spent in each CPU state,
    helping identify threads that are blocked or starved.

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
        where = f"AND p.name LIKE '%{package}%'"

    sql = f"""
        SELECT
            p.name as process_name,
            t.name as thread_name,
            t.tid,
            ROUND(SUM(CASE WHEN ss.state = 'Running' THEN ss.dur ELSE 0 END) / 1e6, 2) as running_ms,
            ROUND(SUM(CASE WHEN ss.state IN ('R', 'R+') THEN ss.dur ELSE 0 END) / 1e6, 2) as runnable_ms,
            ROUND(SUM(CASE WHEN ss.state = 'S' THEN ss.dur ELSE 0 END) / 1e6, 2) as sleeping_ms,
            ROUND(SUM(CASE WHEN ss.state IN ('D', 'DK') THEN ss.dur ELSE 0 END) / 1e6, 2) as uninterruptible_ms
        FROM sched_slice ss
        JOIN thread t USING (utid)
        JOIN process p USING (upid)
        WHERE ss.dur > 0 {where}
        GROUP BY p.name, t.name, t.tid
        ORDER BY running_ms DESC
        LIMIT 50
    """
    return query_engine.run_query(tp, sql)
```

- [ ] **Step 5: Implement binder analysis**

```python
# mcp_server/tools/binder.py
"""Binder IPC analysis tools."""
import json
from mcp_server.server import mcp
from mcp_server.tools.core_tools import trace_manager, query_engine


@mcp.tool()
def analyze_binder(package: str = "", alias: str = "default") -> str:
    """Analyze Binder IPC performance: call counts and latency distribution.

    Shows binder transactions grouped by interface, with timing stats.

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
        where = f"AND p.name LIKE '%{package}%'"

    sql = f"""
        SELECT
            p.name as process_name,
            s.name as binder_call,
            COUNT(*) as call_count,
            ROUND(AVG(s.dur) / 1e6, 2) as avg_dur_ms,
            ROUND(MAX(s.dur) / 1e6, 2) as max_dur_ms,
            ROUND(MIN(s.dur) / 1e6, 2) as min_dur_ms
        FROM slice s
        JOIN thread_track tt ON s.track_id = tt.id
        JOIN thread t ON tt.utid = t.utid
        JOIN process p ON t.upid = p.upid
        WHERE s.name LIKE 'binder%' {where}
        GROUP BY p.name, s.name
        ORDER BY call_count DESC
        LIMIT 50
    """
    return query_engine.run_query(tp, sql)
```

- [ ] **Step 6: Update tools/__init__.py**

```python
# mcp_server/tools/__init__.py
"""Import all tool modules to register their @mcp.tool() decorators."""
from mcp_server.tools import core_tools  # noqa: F401
from mcp_server.tools import query_tools  # noqa: F401
from mcp_server.tools import jank  # noqa: F401
from mcp_server.tools import startup  # noqa: F401
from mcp_server.tools import memory  # noqa: F401
from mcp_server.tools import scheduling  # noqa: F401
from mcp_server.tools import binder  # noqa: F401
```

- [ ] **Step 7: Commit**

```bash
cd /Users/entri/PerfettoAI
git add mcp_server/tools/jank.py mcp_server/tools/startup.py mcp_server/tools/memory.py mcp_server/tools/scheduling.py mcp_server/tools/binder.py mcp_server/tools/__init__.py
git commit -m "feat: add analysis tools (jank, startup, memory, scheduling, binder)"
```

---

### Task 6: ANR Detection and Heap Analysis

**Files:**
- Create: `mcp_server/tools/anr.py`
- Create: `mcp_server/tools/heap.py`

- [ ] **Step 1: Implement ANR detection**

```python
# mcp_server/tools/anr.py
"""ANR (Application Not Responding) detection tools."""
import json
from mcp_server.server import mcp
from mcp_server.tools.core_tools import trace_manager, query_engine


@mcp.tool()
def detect_anr(package: str = "", alias: str = "default") -> str:
    """Detect ANR events in the trace.

    Looks for main thread blocking >5s on input dispatch timeout.
    Distinct from jank (frame drops). ANR = system_server input dispatch timeout.

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
        where = f"AND p.name LIKE '%{package}%'"

    # Look for long main-thread slices that indicate ANR
    sql = f"""
        SELECT
            p.name as process_name,
            s.name as slice_name,
            ROUND(s.dur / 1e6, 2) as duration_ms,
            s.ts,
            t.name as thread_name
        FROM slice s
        JOIN thread_track tt ON s.track_id = tt.id
        JOIN thread t ON tt.utid = t.utid
        JOIN process p ON t.upid = p.upid
        WHERE t.is_main_thread = 1
        AND s.dur > 5000000000
        {where}
        ORDER BY s.dur DESC
        LIMIT 20
    """
    return query_engine.run_query(tp, sql)
```

- [ ] **Step 2: Implement heap analysis tools**

```python
# mcp_server/tools/heap.py
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
        where = f"WHERE p.name LIKE '%{package}%'"

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
        where = f"WHERE p.name LIKE '%{package}%'"

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
```

- [ ] **Step 3: Update tools/__init__.py**

Add to the imports:
```python
from mcp_server.tools import anr  # noqa: F401
from mcp_server.tools import heap  # noqa: F401
```

- [ ] **Step 4: Commit**

```bash
cd /Users/entri/PerfettoAI
git add mcp_server/tools/anr.py mcp_server/tools/heap.py mcp_server/tools/__init__.py
git commit -m "feat: add ANR detection and heap analysis tools"
```

---

### Task 7: Device Management and Trace Capture

**Files:**
- Create: `mcp_server/core/device.py`
- Create: `mcp_server/core/capture.py`
- Create: `mcp_server/tools/capture_tools.py`

- [ ] **Step 1: Implement device manager**

```python
# mcp_server/core/device.py
"""ADB device detection and management."""
import subprocess
import json
from typing import Optional


def check_adb() -> Optional[str]:
    """Check if adb is available. Returns None if OK, error string otherwise."""
    try:
        subprocess.run(["adb", "version"], capture_output=True, check=True, timeout=5)
        return None
    except FileNotFoundError:
        return "adb not found. Install Android SDK platform-tools."
    except subprocess.TimeoutExpired:
        return "adb timed out. Check your adb installation."
    except subprocess.CalledProcessError as e:
        return f"adb error: {e}"


def list_devices() -> list[dict]:
    """List connected Android devices via adb."""
    try:
        result = subprocess.run(
            ["adb", "devices", "-l"],
            capture_output=True, text=True, timeout=10,
        )
        devices = []
        for line in result.stdout.strip().split("\n")[1:]:
            if "\tdevice" in line:
                parts = line.split()
                serial = parts[0]
                info = {"serial": serial}
                for part in parts[2:]:
                    if ":" in part:
                        k, v = part.split(":", 1)
                        info[k] = v
                devices.append(info)
        return devices
    except Exception:
        return []


def get_device_info(serial: str = "") -> dict:
    """Get device properties (model, API level, etc.)."""
    cmd_prefix = ["adb"]
    if serial:
        cmd_prefix += ["-s", serial]

    info = {}
    props = {
        "model": "ro.product.model",
        "api_level": "ro.build.version.sdk",
        "android_version": "ro.build.version.release",
        "manufacturer": "ro.product.manufacturer",
    }
    for key, prop in props.items():
        try:
            r = subprocess.run(
                cmd_prefix + ["shell", "getprop", prop],
                capture_output=True, text=True, timeout=5,
            )
            info[key] = r.stdout.strip()
        except Exception:
            info[key] = "unknown"
    return info
```

- [ ] **Step 2: Implement capture orchestrator**

```python
# mcp_server/core/capture.py
"""Trace capture orchestration via adb."""
import subprocess
import os
import time
from datetime import datetime
from typing import Optional

TRACES_DIR = os.path.join(os.path.dirname(os.path.dirname(__file__)), "traces")
DEVICE_TRACE_PATH = "/data/misc/perfetto-traces/mcp_trace.perfetto-trace"


def _ensure_traces_dir():
    os.makedirs(TRACES_DIR, exist_ok=True)


def _generate_trace_path(package: str = "") -> str:
    _ensure_traces_dir()
    ts = datetime.now().strftime("%Y%m%d_%H%M%S")
    suffix = f"_{package}" if package else ""
    return os.path.join(TRACES_DIR, f"trace_{ts}{suffix}.perfetto-trace")


def _build_config(
    duration_s: int = 10,
    categories: str = "am,binder_driver,dalvik,gfx,hal,input,sched,view,wm",
    buffer_size_kb: int = 65536,
    package: str = "",
) -> str:
    """Build a Perfetto text-format trace config."""
    pkg_filter = ""
    if package:
        pkg_filter = f"""
    atrace_apps: "{package}"
"""

    return f"""
buffers {{
  size_kb: {buffer_size_kb}
  fill_policy: RING_BUFFER
}}
data_sources {{
  config {{
    name: "linux.ftrace"
    ftrace_config {{
      ftrace_events: "sched/sched_switch"
      ftrace_events: "power/suspend_resume"
      ftrace_events: "power/cpu_frequency"
      atrace_categories: "{categories}"{pkg_filter}
    }}
  }}
}}
data_sources {{
  config {{
    name: "linux.process_stats"
    process_stats_config {{
      scan_all_processes_on_start: true
      proc_stats_poll_ms: 1000
    }}
  }}
}}
duration_ms: {duration_s * 1000}
"""


def capture_trace(
    duration_s: int = 10,
    package: str = "",
    categories: str = "am,binder_driver,dalvik,gfx,hal,input,sched,view,wm",
    buffer_size_kb: int = 65536,
    launch_app: bool = False,
    serial: str = "",
) -> dict:
    """Capture a trace from connected device. Returns local path."""
    cmd_prefix = ["adb"]
    if serial:
        cmd_prefix += ["-s", serial]

    config = _build_config(duration_s, categories, buffer_size_kb, package)

    # Push config and start perfetto
    proc = subprocess.run(
        cmd_prefix + ["shell", "perfetto", "-c", "-", "-o", DEVICE_TRACE_PATH],
        input=config,
        capture_output=True,
        text=True,
        timeout=duration_s + 30,
    )

    if proc.returncode != 0:
        return {"error": f"Perfetto failed: {proc.stderr}"}

    # Launch app if requested
    if launch_app and package:
        subprocess.run(
            cmd_prefix + ["shell", "monkey", "-p", package, "-c",
                         "android.intent.category.LAUNCHER", "1"],
            capture_output=True, timeout=10,
        )

    # Pull trace
    local_path = _generate_trace_path(package)
    pull = subprocess.run(
        cmd_prefix + ["pull", DEVICE_TRACE_PATH, local_path],
        capture_output=True, text=True, timeout=60,
    )

    if pull.returncode != 0:
        return {"error": f"Failed to pull trace: {pull.stderr}"}

    # Cleanup device
    subprocess.run(
        cmd_prefix + ["shell", "rm", "-f", DEVICE_TRACE_PATH],
        capture_output=True, timeout=5,
    )

    return {"path": local_path, "duration_s": duration_s}
```

- [ ] **Step 3: Implement capture tools**

```python
# mcp_server/tools/capture_tools.py
"""Device trace capture tools."""
import json
import asyncio
from mcp_server.server import mcp
from mcp_server.tools.core_tools import trace_manager
from mcp_server.core import device, capture


@mcp.tool()
async def capture_trace(
    duration_s: int = 10,
    package: str = "",
    categories: str = "am,binder_driver,dalvik,gfx,hal,input,sched,view,wm",
    buffer_size_kb: int = 65536,
    launch_app: bool = False,
    alias: str = "default",
) -> str:
    """Capture a Perfetto trace from a connected Android device.

    Captures system-wide trace data for the specified duration, pulls it
    from the device, and auto-loads it for analysis.

    Args:
        duration_s: Capture duration in seconds (default: 10)
        package: Package name to filter (e.g., "com.example.app")
        categories: Atrace categories (comma-separated)
        buffer_size_kb: Ring buffer size in KB (default: 64MB)
        launch_app: If true, launch the package on trace start
        alias: Alias for the loaded trace (default: "default")
    """
    err = device.check_adb()
    if err:
        return json.dumps({"error": err})

    devices = device.list_devices()
    if not devices:
        return json.dumps({"error": "No Android device connected. Connect via USB and enable USB debugging."})

    result = await asyncio.to_thread(
        capture.capture_trace,
        duration_s=duration_s,
        package=package,
        categories=categories,
        buffer_size_kb=buffer_size_kb,
        launch_app=launch_app,
    )

    if "error" in result:
        return json.dumps(result)

    # Auto-load the captured trace
    try:
        info = trace_manager.load_trace(result["path"], alias)
        return json.dumps({
            "status": "captured_and_loaded",
            "path": result["path"],
            "alias": alias,
            "duration_s": duration_s,
        })
    except Exception as e:
        return json.dumps({
            "status": "captured_but_load_failed",
            "path": result["path"],
            "error": str(e),
        })


@mcp.tool()
def list_devices() -> str:
    """List connected Android devices with model and API level info."""
    err = device.check_adb()
    if err:
        return json.dumps({"error": err})

    devices = device.list_devices()
    if not devices:
        return json.dumps({"devices": [], "message": "No devices connected."})

    # Enrich with device info
    for d in devices:
        info = device.get_device_info(d["serial"])
        d.update(info)

    return json.dumps({"devices": devices})
```

- [ ] **Step 4: Update tools/__init__.py**

Add:
```python
from mcp_server.tools import capture_tools  # noqa: F401
```

- [ ] **Step 5: Commit**

```bash
cd /Users/entri/PerfettoAI
git add mcp_server/core/device.py mcp_server/core/capture.py mcp_server/tools/capture_tools.py mcp_server/tools/__init__.py
git commit -m "feat: add device management and trace capture tools"
```

---

### Task 8: Resources and Prompts

**Files:**
- Create: `mcp_server/resources/android_analysis.md`
- Create: `mcp_server/resources/perfetto_sql_ref.md`
- Modify: `mcp_server/server.py` (add resource and prompt registration)

- [ ] **Step 1: Create android_analysis.md resource**

```markdown
# Android Performance Analysis Guide

## PerfettoSQL Basics
- Timestamps are in nanoseconds. Divide by 1e6 for milliseconds, 1e9 for seconds.
- Load stdlib modules: `INCLUDE PERFETTO MODULE <module>` (must be a separate query)
- Key modules: `android.frames.timeline`, `android.startups.startups`, `slices`, `counters`

## Core Tables
| Table | Description |
|-------|------------|
| slice | All trace events (functions, system events) |
| process | Process metadata (pid, name, uid) |
| thread | Thread metadata (tid, name, is_main_thread) |
| sched_slice | CPU scheduling events |
| counter | Counter values over time (memory, CPU freq) |
| android_logs | Logcat entries |
| heap_profile_allocation | Native heap allocations (heapprofd) |
| heap_graph_object | Java heap objects (java_hprof) |

## Common Join Pattern
```sql
SELECT s.name, s.dur, t.name as thread, p.name as process
FROM slice s
JOIN thread_track tt ON s.track_id = tt.id
JOIN thread t ON tt.utid = t.utid
JOIN process p ON t.upid = p.upid
```

## Analysis Workflow
1. Load trace and check trace-summary for overview
2. Identify the app process of interest
3. Run domain-specific analysis (jank, startup, memory, etc.)
4. Use execute-sql for custom queries when needed
5. Compare traces for regression detection
```

- [ ] **Step 2: Register resources and prompts in server.py**

```python
# mcp_server/server.py
import os
from mcp.server.fastmcp import FastMCP

mcp = FastMCP("perfetto")

RESOURCES_DIR = os.path.join(os.path.dirname(__file__), "resources")


@mcp.resource("perfetto://knowledge/android-analysis")
def android_analysis_guide() -> str:
    """Android performance analysis domain knowledge and SQL reference."""
    path = os.path.join(RESOURCES_DIR, "android_analysis.md")
    with open(path) as f:
        return f.read()


@mcp.prompt()
def diagnose_performance(package: str = "") -> str:
    """Guided workflow: diagnose app performance issues step by step."""
    pkg = f" for {package}" if package else ""
    return f"""Analyze the performance{pkg} using this workflow:
1. First, call trace-summary to understand what data is available
2. Call list-processes to find the app process
3. Call analyze-jank to check for frame drops
4. Call analyze-startup to check startup timing
5. Call analyze-memory to check memory usage
6. Call analyze-scheduling to check CPU scheduling
7. Summarize findings with specific metrics and recommendations"""


@mcp.prompt()
def investigate_memory_leak(package: str = "") -> str:
    """Guided workflow: investigate a potential memory leak."""
    pkg = f" for {package}" if package else ""
    return f"""Investigate memory issues{pkg}:
1. Call trace-summary to check available memory data
2. Call analyze-memory to see RSS/PSS trends
3. If native heap data available, call analyze-heap-native for top allocators
4. If Java heap data available, call analyze-heap-java for class histogram
5. Look for growing counters or large allocations without frees
6. Summarize findings with specific leak candidates"""


def main():
    """Entry point for the MCP server."""
    import mcp_server.tools  # noqa: F401
    mcp.run(transport="stdio")


if __name__ == "__main__":
    main()
```

- [ ] **Step 3: Commit**

```bash
cd /Users/entri/PerfettoAI
git add mcp_server/resources/ mcp_server/server.py
git commit -m "feat: add MCP resources and guided analysis prompts"
```

---

### Task 9: .mcp.json Configuration and README

**Files:**
- Create: `.mcp.json`
- Create: `README.md`

- [ ] **Step 1: Create .mcp.json**

```json
{
  "mcpServers": {
    "perfetto": {
      "command": "python3",
      "args": ["-m", "mcp_server.server"],
      "cwd": "/Users/entri/PerfettoAI",
      "env": {
        "PYTHONPATH": "/Users/entri/PerfettoAI:/Users/entri/PerfettoAI/perfetto/python"
      }
    }
  }
}
```

- [ ] **Step 2: Create README.md**

```markdown
# Perfetto MCP Server

MCP server that gives Claude deep access to Android performance trace analysis via Perfetto.

## Quick Start

1. Install dependencies:
   ```bash
   pip install mcp perfetto
   ```

2. Add to your Claude Code project (`.mcp.json` is already configured)

3. Start analyzing:
   - "Load my trace file at /path/to/trace.perfetto-trace"
   - "Analyze jank for com.example.app"
   - "Capture a 10 second trace from my connected device"

## Tools

| Tool | What it does |
|------|-------------|
| load-trace | Load a .perfetto-trace file |
| trace-summary | Get overview of loaded trace |
| execute-sql | Run PerfettoSQL queries |
| list-tables | Show available tables |
| analyze-jank | Frame timeline / dropped frames |
| analyze-startup | App startup timing |
| analyze-memory | Memory counters (RSS, PSS) |
| analyze-scheduling | CPU scheduling analysis |
| analyze-binder | Binder IPC latency |
| detect-anr | ANR detection |
| analyze-heap-native | Native heap profiling |
| analyze-heap-java | Java heap dump analysis |
| capture-trace | Capture from connected device |
| list-devices | List connected Android devices |

## Architecture

Modular design using FastMCP decorators. Each analysis domain is a self-contained
Python module in `mcp_server/tools/`. Core infrastructure (trace management, SQL
engine, device management) lives in `mcp_server/core/`.

## License

Apache 2.0
```

- [ ] **Step 3: Commit**

```bash
cd /Users/entri/PerfettoAI
git add .mcp.json README.md
git commit -m "feat: add MCP configuration and README"
```

---

### Task 10: End-to-End Verification

- [ ] **Step 1: Verify all tool modules import cleanly**

Run: `cd /Users/entri/PerfettoAI && PYTHONPATH=.:/Users/entri/PerfettoAI/perfetto/python python -c "from mcp_server.server import mcp; import mcp_server.tools; print('All tools imported OK')"`

- [ ] **Step 2: Run all tests**

Run: `cd /Users/entri/PerfettoAI && PYTHONPATH=.:/Users/entri/PerfettoAI/perfetto/python python -m pytest mcp_server/tests/ -v`

- [ ] **Step 3: Test server starts and responds**

Run: `cd /Users/entri/PerfettoAI && echo '{"jsonrpc":"2.0","method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test"}},"id":1}' | PYTHONPATH=.:/Users/entri/PerfettoAI/perfetto/python python -m mcp_server.server`
Expected: JSON response with server capabilities listing all tools

- [ ] **Step 4: Final commit**

```bash
cd /Users/entri/PerfettoAI
git add -A
git commit -m "feat: perfetto MCP server v0.1.0 - modular trace analysis platform"
```
