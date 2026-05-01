# Perfetto MCP Server

MCP server that gives Claude deep access to Android performance trace analysis via Perfetto. 33 tools covering trace capture, jank analysis, startup profiling, memory/heap inspection, GC analysis, lock contention, input latency, and more. Adaptive capture config probes device capabilities before recording.

## Requirements

- **Python 3.9+**
- **adb** (Android SDK platform-tools) -- only needed for device capture

## Quick Install

```bash
git clone https://github.com/AkhilPaulnp/PerfettoAI.git
cd PerfettoAI
python3 -m venv .venv
source .venv/bin/activate
pip install mcp perfetto
```

That's it. The `.mcp.json` is included -- Claude Code auto-connects the server.

For device capture, install adb: `brew install android-platform-tools` (Mac) or download [platform-tools](https://developer.android.com/tools/releases/platform-tools)

The Perfetto `trace_processor_shell` binary is auto-downloaded on first use.

### Verify

```bash
source .venv/bin/activate
PYTHONPATH=. python -c "from mcp_server.server import mcp; import mcp_server.tools; print(f'{len(mcp._tool_manager._tools)} tools registered')"
```

### Manual MCP config (if .mcp.json doesn't auto-connect)

Add to your Claude Code settings or project `.mcp.json`:
```json
{
  "mcpServers": {
    "perfetto": {
      "command": "/path/to/PerfettoAI/.venv/bin/python3",
      "args": ["-m", "mcp_server"],
      "cwd": "/path/to/PerfettoAI"
    }
  }
}
```

## Usage

After setup, the MCP server connects automatically via Claude Code. Just talk to Claude:

- "Load my trace at /path/to/trace.perfetto-trace"
- "Analyze jank for com.example.app"
- "Capture a 20 second cold start trace for com.example.app"
- "What's causing GC pressure in my app?"
- "Compare startup between baseline and current traces"

## Tools (33)

### Trace Management
| Tool | What it does |
|------|-------------|
| load-trace | Load a .perfetto-trace file (supports multiple with aliases) |
| close-trace | Close a loaded trace |
| list-traces | List all loaded traces |
| trace-summary | Auto-overview: duration, processes, data sources |

### SQL & Exploration
| Tool | What it does |
|------|-------------|
| execute-sql | Run PerfettoSQL queries directly |
| list-tables | Show available tables and views |
| describe-table | Show column names and types |
| list-processes | List all processes in the trace |

### Performance Analysis
| Tool | What it does |
|------|-------------|
| analyze-jank | Frame timeline / dropped frames (Android 12+) |
| analyze-startup | Cold/warm/hot start timing with TTID/TTFD |
| startup-breakdown | Startup phase breakdown by cause (GC, inflate, binder, etc.) |
| analyze-scheduling | CPU scheduling: running, runnable, sleeping per thread |
| analyze-binder | Binder IPC call counts and latency |
| detect-anr | Main thread blocking >5s |
| analyze-blocking-calls | Known blocking patterns (handlers, locks, GC, Compose) |
| analyze-lock-contention | Monitor lock contention chains |
| analyze-input-latency | Input dispatch/handling/ACK timing |
| analyze-surfaceflinger | SF rendering pipeline per-stage timing |

### Memory Analysis
| Tool | What it does |
|------|-------------|
| analyze-memory | Memory counters (RSS, PSS, swap) over time |
| analyze-gc | GC frequency, duration, heap before/after |
| analyze-heap-native | Native heap allocations via heapprofd (Android 10+) |
| analyze-heap-java | Java heap class histogram via java_hprof (Android 11+) |
| analyze-heap-dominators | Retained memory via dominator tree (memory leak detection) |
| detect-lmk | Low Memory Killer events with kill reason |
| analyze-oom-priority | OOM adjuster score tracking |

### Network & Device
| Tool | What it does |
|------|-------------|
| analyze-network | Per-app network packets and bytes (Android 14+) |
| device-capabilities | Probe device: API level, available categories, supported features |
| capture-trace | Capture from connected device (auto-adapts config to device) |
| capture-memory-trace | Capture with heap profiling (heapprofd + java_hprof) |
| list-android-devices | List connected devices with model and API level |

### Composite Analysis (one call, full report)
| Tool | What it does |
|------|-------------|
| full-startup-analysis | Complete startup report: timing, TTID, breakdown, GC, blocking, contention, dex loading |
| full-memory-analysis | Complete memory report: counters, GC, class histogram, dominator tree, leak suspects |

### Multi-trace
| Tool | What it does |
|------|-------------|
| compare-traces | Compare two traces for regression detection (startup, jank, memory, GC) |

## Guided Workflows

MCP prompts guide Claude through structured analysis:
- **diagnose-performance** -- step-by-step jank, startup, memory, scheduling analysis
- **investigate-memory-leak** -- memory counters + heap profiling + dominator tree workflow

## Trace Capture

The capture config auto-adapts to your device's Android version:

| Data Source | What it enables | Android version |
|---|---|---|
| linux.ftrace (20+ events) | Scheduling, OOM, LMK, memory, I/O | All |
| linux.process_stats | Process metadata, memory polling | All |
| atrace (23 categories) | App events, binder, GC, graphics, input | All |
| android.surfaceflinger.frametimeline | Frame jank classification | 12+ |
| android.network_packets | Per-packet network tracing | 14+ |
| android.log | Logcat | All |
| android.heapprofd | Native heap profiling (memory capture) | 10+ |
| android.java_hprof | Java heap dumps (memory capture) | 11+ |

## Multi-trace Comparison

```
"Load baseline trace with alias 'baseline'"
"Load current trace with alias 'current'"
"Compare startup between baseline and current"
```

Supports comparing: startup, jank, memory, scheduling, GC metrics.

## Architecture

```
mcp_server/
  __main__.py     # Entry point (python -m mcp_server)
  server.py       # FastMCP instance + prompts + resources
  core/
    trace_manager.py   # Multi-trace lifecycle (max 3 concurrent)
    query_engine.py    # SQL execution + stdlib module loading
    device.py          # ADB device detection
    capture.py         # Trace capture (API-level-aware config)
  tools/               # 19 tool modules, each self-contained
  resources/           # Domain knowledge for Claude
```

## License

Apache 2.0
