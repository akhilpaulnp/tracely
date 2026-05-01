# Changelog

## v0.2.0 (2026-05-01)

### New Tools (15 added)
- `startup-breakdown` -- startup phase breakdown by cause
- `analyze-gc` -- GC events with heap before/after and reclaimed memory
- `analyze-blocking-calls` -- critical blocking patterns on main thread
- `analyze-lock-contention` -- monitor lock contention chains
- `analyze-input-latency` -- input dispatch/handling/ACK timing
- `detect-lmk` -- Low Memory Killer event detection
- `analyze-oom-priority` -- OOM adjuster score tracking
- `analyze-network` -- per-app network packet analysis (Android 14+)
- `analyze-surfaceflinger` -- SF rendering pipeline per-stage timing
- `analyze-heap-dominators` -- retained memory via dominator tree (leak detection)
- `compare-traces` -- multi-trace regression comparison
- `device-capabilities` -- probe device features before capture
- `capture-memory-trace` -- capture with heapprofd + java_hprof
- `full-startup-analysis` -- complete startup report in one call
- `full-memory-analysis` -- complete memory report in one call

### Improved Tools
- `analyze-startup` -- now includes TTID/TTFD from `android_startup_time_to_display`
- `capture-trace` -- auto-adapts config to device API level and available atrace categories

### Capture Config
- 23 atrace categories (was 9), matching Perfetto UI defaults
- 20+ ftrace events (was 3) for scheduling, OOM, LMK, memory, I/O
- 7 data sources (was 2) including frametimeline, network, logcat, sys_stats
- Device capability detection filters config to what the device supports

### Bug Fixes
- Fixed 7 SQL bugs: wrong table/column names caught by Codex review and code review
- Fixed `column_names` property vs method crash
- Fixed `--txt` flag required for text-format configs on Android devices
- Fixed capture timing: app now launches during trace, not after
- Fixed `network_packet_trace_config` crash on Android < 14

## v0.1.0 (2026-05-01)

### Initial Release
- 18 tools: trace management, SQL queries, jank, startup, memory, scheduling, binder, ANR, heap profiling, device capture
- FastMCP server with stdio transport
- Multi-trace support (max 3 concurrent)
- MCP resources for domain knowledge
- Guided analysis prompts (diagnose-performance, investigate-memory-leak)
