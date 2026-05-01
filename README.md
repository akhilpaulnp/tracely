# Perfetto MCP Server

MCP server that gives Claude deep access to Android performance trace analysis via Perfetto.

## Quick Start

1. Install dependencies:
   ```bash
   python3 -m venv .venv
   source .venv/bin/activate
   pip install mcp
   pip install -e perfetto/python/
   ```

2. The `.mcp.json` is already configured for Claude Code.

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
| describe-table | Show column info for a table |
| list-processes | List processes in the trace |
| analyze-jank | Frame timeline / dropped frames |
| analyze-startup | App startup timing |
| analyze-memory | Memory counters (RSS, PSS) |
| analyze-scheduling | CPU scheduling analysis |
| analyze-binder | Binder IPC latency |
| detect-anr | ANR detection |
| analyze-heap-native | Native heap profiling (heapprofd) |
| analyze-heap-java | Java heap dump analysis (java_hprof) |
| capture-trace | Capture from connected device |
| list-android-devices | List connected Android devices |

## Guided Workflows

The server includes MCP prompts that guide Claude through structured analysis:
- **diagnose-performance** -- step-by-step jank, startup, memory, scheduling analysis
- **investigate-memory-leak** -- memory counter + heap profiling workflow

## Architecture

Modular design using FastMCP decorators. Each analysis domain is a self-contained
Python module in `mcp_server/tools/`. Core infrastructure (trace management, SQL
engine, device management) lives in `mcp_server/core/`.

```
mcp_server/
  server.py          # FastMCP instance + prompts + resources
  core/              # TraceManager, QueryEngine, device, capture
  tools/             # Analysis tool modules (@mcp.tool)
  resources/         # Domain knowledge for Claude
```

## Multi-trace Support

Load multiple traces with aliases for comparison:
```
"Load baseline trace with alias 'baseline'"
"Load current trace with alias 'current'"
"Compare startup times between baseline and current"
```

## License

Apache 2.0
