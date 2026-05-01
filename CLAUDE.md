# Perfetto MCP Server - Project Instructions

## Overview

This is a Python MCP server (33 tools) for Android performance trace analysis using Perfetto's TraceProcessor. It connects to Claude Code via `.mcp.json`.

## Project Structure

```
mcp_server/
  __main__.py           # Entry point: python -m mcp_server
  server.py             # FastMCP instance, prompts, resources
  core/
    trace_manager.py    # Multi-trace lifecycle (max 3, thread-safe)
    query_engine.py     # SQL execution + stdlib module loading
    device.py           # ADB device detection
    capture.py          # Trace capture with adaptive device config
  tools/                # 21 tool modules using @mcp.tool() decorators
  resources/            # Domain knowledge for Claude
  tests/                # Unit tests (pytest)
  traces/               # Captured trace files (gitignored)
```

## Development

```bash
source .venv/bin/activate
PYTHONPATH=. python -m pytest mcp_server/tests/ -v    # Run tests
PYTHONPATH=.:/path/to/perfetto/python python -m mcp_server  # Run server
```

## Key Patterns

- All tools follow: `require_trace -> get_trace -> ensure_module -> run_query` pattern
- `query_engine.ensure_module(tp, "module.name")` MUST be called in a separate step before querying stdlib tables
- `column_names` on QueryResultIterator is a PROPERTY, not a method
- Capture config adapts to device API level via `get_device_capabilities()`
- Native heap profiling requires debuggable app or `profileableFromShell=true` in manifest

## SQL Gotchas (verified against Perfetto stdlib source)

- `counter` table has NO `name` column -- use track table (`process_counter_track.name`)
- `actual_frame_timeline_slice.jank_type` is INTEGER (0 = no jank), not a string
- `thread` table has NO `is_main_thread` -- detect with `t.tid = p.pid`
- `android_startups` does NOT have `time_to_initial_display` -- that's on `android_startup_time_to_display` (separate table, separate module)
- Always verify column/table names against `perfetto/src/trace_processor/perfetto_sql/stdlib/`

## Testing

Tests use mocked TraceProcessor. Run with:
```bash
source .venv/bin/activate && PYTHONPATH=. python -m pytest mcp_server/tests/ -v
```

Verify tool registration:
```bash
PYTHONPATH=.:/path/to/perfetto/python python -c "from mcp_server.server import mcp; import mcp_server.tools; print(len(mcp._tool_manager._tools))"
```
