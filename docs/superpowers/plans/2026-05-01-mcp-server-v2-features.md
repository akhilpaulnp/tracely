# MCP Server v2 Features Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add 10 new analysis tools and improve 3 existing tools using Perfetto stdlib modules and SDK features discovered in the source code exploration.

**Architecture:** Each new tool is an independent Python module in `mcp_server/tools/` using `@mcp.tool()` decorators. Existing tools get SQL fixes. New `BatchTraceProcessor` integration for regression comparison. All SQL queries verified against actual Perfetto stdlib table schemas.

**Tech Stack:** Python 3.9+, FastMCP, Perfetto Python SDK (TraceProcessor, BatchTraceProcessor), Perfetto SQL stdlib

---

## File Structure

```
Modified files:
  mcp_server/tools/startup.py          -- Add TTID/TTFD + startup breakdowns
  mcp_server/tools/heap.py             -- Add dominator tree analysis
  mcp_server/tools/jank.py             -- Add critical blocking calls integration
  mcp_server/tools/__init__.py         -- Import new modules
  mcp_server/resources/android_analysis.md -- Update with new tables

New files:
  mcp_server/tools/gc.py               -- GC analysis
  mcp_server/tools/blocking.py         -- Critical blocking calls
  mcp_server/tools/contention.py       -- Lock contention chains
  mcp_server/tools/input_latency.py    -- Input event latency
  mcp_server/tools/lmk.py              -- Low Memory Killer events
  mcp_server/tools/oom.py              -- OOM adjuster tracking
  mcp_server/tools/network.py          -- Network packet analysis
  mcp_server/tools/surfaceflinger.py   -- SurfaceFlinger rendering pipeline
  mcp_server/tools/regression.py       -- Multi-trace comparison via BatchTraceProcessor
```

---

### Task 1: Fix startup tool -- add TTID/TTFD and startup breakdowns

**Files:**
- Modify: `mcp_server/tools/startup.py`

- [ ] **Step 1: Add TTID/TTFD to startup analysis**

```python
# mcp_server/tools/startup.py
"""App startup performance analysis tools."""
import json
from mcp_server.server import mcp
from mcp_server.tools.core_tools import trace_manager, query_engine


@mcp.tool()
def analyze_startup(package: str = "", alias: str = "default") -> str:
    """Analyze app startup performance (cold, warm, hot starts).

    Shows startup duration, type, time-to-initial-display (TTID),
    and time-to-full-display (TTFD).

    Args:
        package: Optional package name to filter
        alias: Trace alias (default: "default")
    """
    err = trace_manager.require_trace(alias)
    if err:
        return json.dumps({"error": err})

    tp = trace_manager.get_trace(alias)
    query_engine.ensure_module(tp, "android.startup.startups")
    query_engine.ensure_module(tp, "android.startup.time_to_display")

    where = ""
    if package:
        safe_pkg = package.replace("'", "''")
        where = f"WHERE s.package LIKE '%{safe_pkg}%'"

    sql = f"""
        SELECT
            s.package,
            s.startup_type,
            ROUND(s.dur / 1e6, 2) as duration_ms,
            s.ts,
            ROUND(ttd.time_to_initial_display / 1e6, 2) as ttid_ms,
            ROUND(ttd.time_to_full_display / 1e6, 2) as ttfd_ms
        FROM android_startups s
        LEFT JOIN android_startup_time_to_display ttd
            ON s.startup_id = ttd.startup_id
        {where}
        ORDER BY s.ts
    """
    return query_engine.run_query(tp, sql)


@mcp.tool()
def startup_breakdown(package: str = "", alias: str = "default") -> str:
    """Break down app startup into phases with root cause for each delay.

    Shows what caused each phase of startup delay: GC, inflate, binder,
    class loading, resource loading, etc.

    Args:
        package: Optional package name to filter
        alias: Trace alias (default: "default")
    """
    err = trace_manager.require_trace(alias)
    if err:
        return json.dumps({"error": err})

    tp = trace_manager.get_trace(alias)
    query_engine.ensure_module(tp, "android.startup.startups")
    query_engine.ensure_module(tp, "android.startup.startup_breakdowns")

    where = ""
    if package:
        safe_pkg = package.replace("'", "''")
        where = f"AND s.package LIKE '%{safe_pkg}%'"

    sql = f"""
        SELECT
            s.package,
            b.reason,
            COUNT(*) as occurrences,
            ROUND(SUM(b.dur) / 1e6, 2) as total_ms,
            ROUND(MAX(b.dur) / 1e6, 2) as max_ms
        FROM android_startup_opinionated_breakdown b
        JOIN android_startups s ON b.startup_id = s.startup_id
        WHERE 1=1 {where}
        GROUP BY s.package, b.reason
        ORDER BY total_ms DESC
    """
    return query_engine.run_query(tp, sql)
```

- [ ] **Step 2: Verify imports work**

Run: `source .venv/bin/activate && PYTHONPATH=.:/Users/entri/PerfettoAI/perfetto/python python -c "from mcp_server.tools.startup import analyze_startup, startup_breakdown; print('OK')"`

- [ ] **Step 3: Commit**

```bash
git add mcp_server/tools/startup.py
git commit -m "feat: add TTID/TTFD and startup breakdown to startup analysis"
```

---

### Task 2: Add GC analysis tool

**Files:**
- Create: `mcp_server/tools/gc.py`

- [ ] **Step 1: Implement GC analysis**

```python
# mcp_server/tools/gc.py
"""Garbage collection analysis tools."""
import json
from mcp_server.server import mcp
from mcp_server.tools.core_tools import trace_manager, query_engine


@mcp.tool()
def analyze_gc(package: str = "", alias: str = "default") -> str:
    """Analyze garbage collection events: frequency, duration, heap impact.

    Shows GC type, duration, heap size before/after, and reclaimed memory.
    Helps identify GC pressure during startup or animation.

    Args:
        package: Optional package name to filter
        alias: Trace alias (default: "default")
    """
    err = trace_manager.require_trace(alias)
    if err:
        return json.dumps({"error": err})

    tp = trace_manager.get_trace(alias)
    query_engine.ensure_module(tp, "android.garbage_collection")

    where = ""
    if package:
        safe_pkg = package.replace("'", "''")
        where = f"WHERE process_name LIKE '%{safe_pkg}%'"

    sql = f"""
        SELECT
            process_name,
            gc_type,
            COUNT(*) as gc_count,
            ROUND(SUM(gc_dur) / 1e6, 2) as total_gc_ms,
            ROUND(MAX(gc_dur) / 1e6, 2) as max_gc_ms,
            ROUND(AVG(gc_dur) / 1e6, 2) as avg_gc_ms,
            ROUND(SUM(reclaimed_mb), 2) as total_reclaimed_mb,
            ROUND(MAX(max_heap_mb), 2) as peak_heap_mb,
            SUM(CASE WHEN is_mark_compact THEN 1 ELSE 0 END) as mark_compact_count
        FROM android_garbage_collection_events
        {where}
        GROUP BY process_name, gc_type
        ORDER BY total_gc_ms DESC
    """
    return query_engine.run_query(tp, sql)
```

- [ ] **Step 2: Commit**

```bash
git add mcp_server/tools/gc.py
git commit -m "feat: add GC analysis tool"
```

---

### Task 3: Add critical blocking calls tool

**Files:**
- Create: `mcp_server/tools/blocking.py`

- [ ] **Step 1: Implement blocking calls analysis**

```python
# mcp_server/tools/blocking.py
"""Critical blocking call detection tools."""
import json
from mcp_server.server import mcp
from mcp_server.tools.core_tools import trace_manager, query_engine


@mcp.tool()
def analyze_blocking_calls(package: str = "", alias: str = "default") -> str:
    """Detect critical blocking calls on the main thread.

    Identifies known blocking patterns: Handler delays, lock contention,
    binder transactions, GC pauses, Compose recomposition, and more.
    These are the root causes of jank and ANR.

    Args:
        package: Optional package name to filter
        alias: Trace alias (default: "default")
    """
    err = trace_manager.require_trace(alias)
    if err:
        return json.dumps({"error": err})

    tp = trace_manager.get_trace(alias)
    query_engine.ensure_module(tp, "android.critical_blocking_calls")

    where = ""
    if package:
        safe_pkg = package.replace("'", "''")
        where = f"WHERE process_name LIKE '%{safe_pkg}%'"

    sql = f"""
        SELECT
            process_name,
            name as blocking_call,
            COUNT(*) as occurrences,
            ROUND(SUM(dur) / 1e6, 2) as total_ms,
            ROUND(MAX(dur) / 1e6, 2) as max_ms,
            ROUND(AVG(dur) / 1e6, 2) as avg_ms
        FROM _android_critical_blocking_calls
        {where}
        GROUP BY process_name, name
        ORDER BY total_ms DESC
        LIMIT 30
    """
    return query_engine.run_query(tp, sql)
```

- [ ] **Step 2: Commit**

```bash
git add mcp_server/tools/blocking.py
git commit -m "feat: add critical blocking calls detection tool"
```

---

### Task 4: Add lock contention analysis tool

**Files:**
- Create: `mcp_server/tools/contention.py`

- [ ] **Step 1: Implement lock contention analysis**

```python
# mcp_server/tools/contention.py
"""Lock contention analysis tools."""
import json
from mcp_server.server import mcp
from mcp_server.tools.core_tools import trace_manager, query_engine


@mcp.tool()
def analyze_lock_contention(package: str = "", alias: str = "default") -> str:
    """Analyze monitor lock contention: which methods block which threads.

    Shows blocking method, blocked method, waiter count, and duration.
    Identifies lock chains that cause main thread blocking.

    Args:
        package: Optional package name to filter
        alias: Trace alias (default: "default")
    """
    err = trace_manager.require_trace(alias)
    if err:
        return json.dumps({"error": err})

    tp = trace_manager.get_trace(alias)
    query_engine.ensure_module(tp, "android.monitor_contention")

    where = ""
    if package:
        safe_pkg = package.replace("'", "''")
        where = f"WHERE process_name LIKE '%{safe_pkg}%'"

    sql = f"""
        SELECT
            process_name,
            short_blocking_method,
            short_blocked_method,
            blocked_thread_name,
            blocking_thread_name,
            COUNT(*) as contention_count,
            ROUND(SUM(dur) / 1e6, 2) as total_ms,
            ROUND(MAX(dur) / 1e6, 2) as max_ms,
            MAX(waiter_count) as max_waiters
        FROM android_monitor_contention
        {where}
        GROUP BY process_name, short_blocking_method, short_blocked_method
        ORDER BY total_ms DESC
        LIMIT 30
    """
    return query_engine.run_query(tp, sql)
```

- [ ] **Step 2: Commit**

```bash
git add mcp_server/tools/contention.py
git commit -m "feat: add lock contention analysis tool"
```

---

### Task 5: Add input latency analysis tool

**Files:**
- Create: `mcp_server/tools/input_latency.py`

- [ ] **Step 1: Implement input latency analysis**

```python
# mcp_server/tools/input_latency.py
"""Input event latency analysis tools."""
import json
from mcp_server.server import mcp
from mcp_server.tools.core_tools import trace_manager, query_engine


@mcp.tool()
def analyze_input_latency(package: str = "", alias: str = "default") -> str:
    """Analyze input event latency: dispatch, handling, and end-to-end timing.

    Measures the full input pipeline from dispatch to frame presentation.
    Helps identify touch responsiveness issues.

    Args:
        package: Optional package name to filter
        alias: Trace alias (default: "default")
    """
    err = trace_manager.require_trace(alias)
    if err:
        return json.dumps({"error": err})

    tp = trace_manager.get_trace(alias)
    query_engine.ensure_module(tp, "android.input")

    where = ""
    if package:
        safe_pkg = package.replace("'", "''")
        where = f"WHERE process_name LIKE '%{safe_pkg}%'"

    sql = f"""
        SELECT
            process_name,
            COUNT(*) as event_count,
            ROUND(AVG(dispatch_latency_dur) / 1e6, 2) as avg_dispatch_ms,
            ROUND(AVG(handling_latency_dur) / 1e6, 2) as avg_handling_ms,
            ROUND(AVG(total_latency_dur) / 1e6, 2) as avg_total_ms,
            ROUND(MAX(total_latency_dur) / 1e6, 2) as max_total_ms,
            ROUND(AVG(end_to_end_latency_dur) / 1e6, 2) as avg_e2e_ms,
            ROUND(MAX(end_to_end_latency_dur) / 1e6, 2) as max_e2e_ms
        FROM android_input_events
        {where}
        GROUP BY process_name
        ORDER BY avg_total_ms DESC
    """
    return query_engine.run_query(tp, sql)
```

- [ ] **Step 2: Commit**

```bash
git add mcp_server/tools/input_latency.py
git commit -m "feat: add input event latency analysis tool"
```

---

### Task 6: Add LMK and OOM tools

**Files:**
- Create: `mcp_server/tools/lmk.py`
- Create: `mcp_server/tools/oom.py`

- [ ] **Step 1: Implement LMK detection**

```python
# mcp_server/tools/lmk.py
"""Low Memory Killer event detection tools."""
import json
from mcp_server.server import mcp
from mcp_server.tools.core_tools import trace_manager, query_engine


@mcp.tool()
def detect_lmk(package: str = "", alias: str = "default") -> str:
    """Detect Low Memory Killer (LMK) events in the trace.

    Shows which processes were killed, their OOM score, and the kill reason.
    Answers: 'Why did my app get killed in the background?'

    Args:
        package: Optional package name to filter
        alias: Trace alias (default: "default")
    """
    err = trace_manager.require_trace(alias)
    if err:
        return json.dumps({"error": err})

    tp = trace_manager.get_trace(alias)
    query_engine.ensure_module(tp, "android.memory.lmk")

    where = ""
    if package:
        safe_pkg = package.replace("'", "''")
        where = f"WHERE process_name LIKE '%{safe_pkg}%'"

    sql = f"""
        SELECT
            process_name,
            pid,
            oom_score_adj,
            kill_reason,
            ts
        FROM android_lmk_events
        {where}
        ORDER BY ts
    """
    return query_engine.run_query(tp, sql)
```

- [ ] **Step 2: Implement OOM adjuster tracking**

```python
# mcp_server/tools/oom.py
"""OOM adjuster score tracking tools."""
import json
from mcp_server.server import mcp
from mcp_server.tools.core_tools import trace_manager, query_engine


@mcp.tool()
def analyze_oom_priority(package: str = "", alias: str = "default") -> str:
    """Track OOM adjuster score changes for processes.

    Shows how the system prioritizes processes over time.
    Higher scores = more likely to be killed. Helps understand
    why an app gets killed or deprioritized.

    Args:
        package: Optional package name to filter
        alias: Trace alias (default: "default")
    """
    err = trace_manager.require_trace(alias)
    if err:
        return json.dumps({"error": err})

    tp = trace_manager.get_trace(alias)
    query_engine.ensure_module(tp, "android.oom_adjuster")

    where = ""
    if package:
        safe_pkg = package.replace("'", "''")
        where = f"WHERE process_name LIKE '%{safe_pkg}%'"

    sql = f"""
        SELECT
            process_name,
            bucket,
            score,
            COUNT(*) as transitions,
            ROUND(SUM(dur) / 1e9, 2) as total_time_s,
            ROUND(MIN(dur) / 1e6, 2) as min_dur_ms,
            ROUND(MAX(dur) / 1e6, 2) as max_dur_ms
        FROM android_oom_adj_intervals
        {where}
        GROUP BY process_name, bucket, score
        ORDER BY process_name, score
    """
    return query_engine.run_query(tp, sql)
```

- [ ] **Step 3: Commit**

```bash
git add mcp_server/tools/lmk.py mcp_server/tools/oom.py
git commit -m "feat: add LMK detection and OOM priority tracking tools"
```

---

### Task 7: Add network and SurfaceFlinger tools

**Files:**
- Create: `mcp_server/tools/network.py`
- Create: `mcp_server/tools/surfaceflinger.py`

- [ ] **Step 1: Implement network analysis**

```python
# mcp_server/tools/network.py
"""Network packet analysis tools."""
import json
from mcp_server.server import mcp
from mcp_server.tools.core_tools import trace_manager, query_engine


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
```

- [ ] **Step 2: Implement SurfaceFlinger analysis**

```python
# mcp_server/tools/surfaceflinger.py
"""SurfaceFlinger rendering pipeline analysis tools."""
import json
from mcp_server.server import mcp
from mcp_server.tools.core_tools import trace_manager, query_engine


@mcp.tool()
def analyze_surfaceflinger(alias: str = "default") -> str:
    """Analyze SurfaceFlinger rendering pipeline per-stage timing.

    Shows CPU time for frame signal, commit, composite stages,
    plus HWC present time and RenderEngine GPU time.
    Identifies bottlenecks in the graphics composition pipeline.

    Args:
        alias: Trace alias (default: "default")
    """
    err = trace_manager.require_trace(alias)
    if err:
        return json.dumps({"error": err})

    tp = trace_manager.get_trace(alias)
    query_engine.ensure_module(tp, "android.surfaceflinger")

    sql = """
        SELECT
            source,
            output_name,
            COUNT(*) as frame_count,
            ROUND(AVG(sf_cpu_frame_signal_nanos) / 1e6, 2) as avg_signal_ms,
            ROUND(AVG(sf_cpu_commit_nanos) / 1e6, 2) as avg_commit_ms,
            ROUND(AVG(sf_cpu_composite_nanos) / 1e6, 2) as avg_composite_ms,
            ROUND(AVG(hwc_present_nanos) / 1e6, 2) as avg_hwc_present_ms,
            ROUND(MAX(sf_cpu_composite_nanos) / 1e6, 2) as max_composite_ms
        FROM android_surfaceflinger_workloads
        GROUP BY source, output_name
        ORDER BY frame_count DESC
    """
    return query_engine.run_query(tp, sql)
```

- [ ] **Step 3: Commit**

```bash
git add mcp_server/tools/network.py mcp_server/tools/surfaceflinger.py
git commit -m "feat: add network and SurfaceFlinger analysis tools"
```

---

### Task 8: Fix heap Java tool -- add dominator tree analysis

**Files:**
- Modify: `mcp_server/tools/heap.py`

- [ ] **Step 1: Add dominator tree analysis to heap.py**

Add this new tool function after the existing `analyze_heap_java`:

```python
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
```

- [ ] **Step 2: Commit**

```bash
git add mcp_server/tools/heap.py
git commit -m "feat: add heap dominator tree analysis for memory leak detection"
```

---

### Task 9: Add regression comparison via BatchTraceProcessor

**Files:**
- Create: `mcp_server/tools/regression.py`

- [ ] **Step 1: Implement regression comparison tool**

```python
# mcp_server/tools/regression.py
"""Multi-trace regression comparison tools."""
import json
import os
from mcp_server.server import mcp
from mcp_server.tools.core_tools import trace_manager, query_engine


@mcp.tool()
def compare_traces(
    metric: str = "startup",
    baseline_alias: str = "baseline",
    current_alias: str = "current",
) -> str:
    """Compare two loaded traces to find performance regressions.

    Load two traces with different aliases first:
      load-trace /path/to/baseline.trace --alias baseline
      load-trace /path/to/current.trace --alias current

    Then compare specific metrics between them.

    Available metrics: startup, jank, memory, scheduling, gc

    Args:
        metric: Which metric to compare (startup, jank, memory, scheduling, gc)
        baseline_alias: Alias of the baseline trace (default: "baseline")
        current_alias: Alias of the current trace (default: "current")
    """
    for alias in [baseline_alias, current_alias]:
        err = trace_manager.require_trace(alias)
        if err:
            return json.dumps({"error": err})

    tp_base = trace_manager.get_trace(baseline_alias)
    tp_curr = trace_manager.get_trace(current_alias)

    queries = {
        "startup": {
            "modules": ["android.startup.startups", "android.startup.time_to_display"],
            "sql": """
                SELECT
                    s.package,
                    s.startup_type,
                    ROUND(s.dur / 1e6, 2) as duration_ms,
                    ROUND(ttd.time_to_initial_display / 1e6, 2) as ttid_ms
                FROM android_startups s
                LEFT JOIN android_startup_time_to_display ttd
                    ON s.startup_id = ttd.startup_id
                ORDER BY s.ts
            """,
        },
        "jank": {
            "modules": [],
            "sql": """
                SELECT
                    p.name as process_name,
                    COUNT(*) as total_frames,
                    SUM(CASE WHEN afts.jank_type != 0 THEN 1 ELSE 0 END) as janky_frames,
                    ROUND(AVG(afts.dur) / 1e6, 2) as avg_frame_ms
                FROM actual_frame_timeline_slice afts
                JOIN process_track pt ON afts.track_id = pt.id
                JOIN process p ON pt.upid = p.upid
                GROUP BY p.name
                ORDER BY janky_frames DESC
            """,
        },
        "memory": {
            "modules": [],
            "sql": """
                SELECT
                    p.name as process_name,
                    pct.name as counter_name,
                    ROUND(MAX(c.value), 0) as peak_value,
                    ROUND(AVG(c.value), 0) as avg_value
                FROM counter c
                JOIN process_counter_track pct ON c.track_id = pct.id
                JOIN process p ON pct.upid = p.upid
                WHERE pct.name LIKE '%rss%' OR pct.name LIKE '%pss%'
                GROUP BY p.name, pct.name
                ORDER BY peak_value DESC
                LIMIT 20
            """,
        },
        "scheduling": {
            "modules": [],
            "sql": """
                SELECT
                    p.name as process_name,
                    ROUND(SUM(CASE WHEN ts.state = 'Running' THEN ts.dur ELSE 0 END) / 1e6, 2) as running_ms,
                    ROUND(SUM(CASE WHEN ts.state IN ('R', 'R+') THEN ts.dur ELSE 0 END) / 1e6, 2) as runnable_ms
                FROM thread_state ts
                JOIN thread t USING (utid)
                JOIN process p USING (upid)
                WHERE t.tid = p.pid
                GROUP BY p.name
                ORDER BY running_ms DESC
                LIMIT 20
            """,
        },
        "gc": {
            "modules": ["android.garbage_collection"],
            "sql": """
                SELECT
                    process_name,
                    gc_type,
                    COUNT(*) as gc_count,
                    ROUND(SUM(gc_dur) / 1e6, 2) as total_gc_ms,
                    ROUND(SUM(reclaimed_mb), 2) as total_reclaimed_mb
                FROM android_garbage_collection_events
                GROUP BY process_name, gc_type
                ORDER BY total_gc_ms DESC
            """,
        },
    }

    if metric not in queries:
        return json.dumps({
            "error": f"Unknown metric '{metric}'. Available: {', '.join(queries.keys())}"
        })

    q = queries[metric]

    # Load modules on both traces
    for mod in q["modules"]:
        query_engine.ensure_module(tp_base, mod)
        query_engine.ensure_module(tp_curr, mod)

    baseline_result = json.loads(query_engine.run_query(tp_base, q["sql"]))
    current_result = json.loads(query_engine.run_query(tp_curr, q["sql"]))

    return json.dumps({
        "metric": metric,
        "baseline": {
            "alias": baseline_alias,
            "data": baseline_result,
        },
        "current": {
            "alias": current_alias,
            "data": current_result,
        },
        "note": "Compare the baseline and current data to identify regressions.",
    })
```

- [ ] **Step 2: Commit**

```bash
git add mcp_server/tools/regression.py
git commit -m "feat: add multi-trace regression comparison tool"
```

---

### Task 10: Update __init__.py and resources

**Files:**
- Modify: `mcp_server/tools/__init__.py`
- Modify: `mcp_server/resources/android_analysis.md`

- [ ] **Step 1: Update tools/__init__.py to import all new modules**

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
from mcp_server.tools import anr  # noqa: F401
from mcp_server.tools import heap  # noqa: F401
from mcp_server.tools import capture_tools  # noqa: F401
from mcp_server.tools import gc  # noqa: F401
from mcp_server.tools import blocking  # noqa: F401
from mcp_server.tools import contention  # noqa: F401
from mcp_server.tools import input_latency  # noqa: F401
from mcp_server.tools import lmk  # noqa: F401
from mcp_server.tools import oom  # noqa: F401
from mcp_server.tools import network  # noqa: F401
from mcp_server.tools import surfaceflinger  # noqa: F401
from mcp_server.tools import regression  # noqa: F401
```

- [ ] **Step 2: Add new tables to android_analysis.md resource**

Append to the Core Tables section:

```markdown
## Advanced Analysis Tables (via stdlib modules)

| Module | Table | What it provides |
|--------|-------|-----------------|
| android.startup.time_to_display | android_startup_time_to_display | TTID and TTFD metrics per startup |
| android.startup.startup_breakdowns | android_startup_opinionated_breakdown | Startup delay breakdown by cause |
| android.garbage_collection | android_garbage_collection_events | GC type, duration, heap before/after |
| android.critical_blocking_calls | _android_critical_blocking_calls | Known blocking patterns on main thread |
| android.monitor_contention | android_monitor_contention | Lock contention: who blocks whom |
| android.input | android_input_events | Input dispatch/handling/ACK latency |
| android.memory.lmk | android_lmk_events | Low Memory Killer kills with reason |
| android.oom_adjuster | android_oom_adj_intervals | OOM priority score changes |
| android.network_packets | android_network_packets | Per-app network traffic |
| android.surfaceflinger | android_surfaceflinger_workloads | SF rendering pipeline per-stage timing |
| android.memory.heap_graph.dominator_tree | heap_graph_dominator_tree | Retained memory per object (leak detection) |

Load these modules before querying:
```sql
-- Always in a SEPARATE query before your data query
INCLUDE PERFETTO MODULE android.garbage_collection;
```
```

- [ ] **Step 3: Verify all tools register**

Run: `source .venv/bin/activate && PYTHONPATH=.:/Users/entri/PerfettoAI/perfetto/python python -c "from mcp_server.server import mcp; import mcp_server.tools; print(f'{len(mcp._tool_manager._tools)} tools registered')"`
Expected: 28+ tools

- [ ] **Step 4: Run tests**

Run: `source .venv/bin/activate && PYTHONPATH=. python -m pytest mcp_server/tests/ -v`
Expected: All pass

- [ ] **Step 5: Commit**

```bash
git add mcp_server/tools/__init__.py mcp_server/resources/android_analysis.md
git commit -m "feat: register all v2 tools and update analysis resource docs"
```

---

### Task 11: End-to-end verification

- [ ] **Step 1: Verify tool count and names**

Run: `source .venv/bin/activate && PYTHONPATH=.:/Users/entri/PerfettoAI/perfetto/python python -c "from mcp_server.server import mcp; import mcp_server.tools; [print(f'  {t}') for t in sorted(mcp._tool_manager._tools.keys())]"`

Expected: 28+ tools including all new ones (analyze_gc, analyze_blocking_calls, analyze_lock_contention, analyze_input_latency, detect_lmk, analyze_oom_priority, analyze_network, analyze_surfaceflinger, analyze_heap_dominators, compare_traces, startup_breakdown)

- [ ] **Step 2: Test MCP protocol serves all tools**

Run: `source .venv/bin/activate && PYTHONPATH=.:/Users/entri/PerfettoAI/perfetto/python printf '{"jsonrpc":"2.0","method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}},"id":1}\n{"jsonrpc":"2.0","method":"notifications/initialized","params":{}}\n{"jsonrpc":"2.0","method":"tools/list","params":{},"id":2}\n' | timeout 5 python -m mcp_server 2>/dev/null | python3 -c "import sys,json; [print(f'{len(d[\"result\"][\"tools\"])} tools via MCP') for line in sys.stdin for d in [json.loads(line.strip())] if d.get('id')==2]"`

Expected: 28+ tools via MCP

- [ ] **Step 3: Final commit**

```bash
git add -A
git commit -m "feat: perfetto MCP server v0.2.0 - 28 tools with advanced analysis"
```
