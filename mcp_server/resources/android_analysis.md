# Android Performance Analysis Guide

## PerfettoSQL Basics
- Timestamps are in nanoseconds. Divide by 1e6 for milliseconds, 1e9 for seconds.
- Load stdlib modules: `INCLUDE PERFETTO MODULE <module>` (must be a separate query)
- Key modules: `android.frames.timeline`, `android.startups.startups`, `slices`, `counters`

## Core Tables

| Table | Description |
|-------|------------|
| slice | All trace events (functions, system events) with ts, dur, name, track_id |
| process | Process metadata: pid, name, uid, upid (internal ID) |
| thread | Thread metadata: tid, name, utid, upid (main thread: tid = pid) |
| thread_track | Links track_id to utid (for joining slice to thread) |
| process_track | Links track_id to upid |
| sched_slice | CPU scheduling events: ts, dur, utid, cpu, state |
| counter | Counter values over time: ts, track_id, value |
| process_counter_track | Links counter track_id to upid |
| android_logs | Logcat entries: ts, prio, tag, msg |
| heap_profile_allocation | Native heap allocations (heapprofd): callsite_id, count, size |
| heap_graph_object | Java heap objects (java_hprof): type_id, self_size, upid |
| heap_graph_class | Java class metadata: name, id |
| stack_profile_callsite | Call stack frames for heap profiles |
| stack_profile_frame | Individual stack frame info |

## Common Join Pattern

```sql
-- Slice -> Thread -> Process
SELECT s.name, s.dur, t.name as thread, p.name as process
FROM slice s
JOIN thread_track tt ON s.track_id = tt.id
JOIN thread t ON tt.utid = t.utid
JOIN process p ON t.upid = p.upid
```

## Scheduling States

| State | Meaning |
|-------|---------|
| Running | Thread is executing on a CPU |
| R, R+ | Runnable (waiting for CPU) |
| S | Sleeping (interruptible) |
| D, DK | Uninterruptible sleep (usually I/O) |

## Android Analysis Patterns

### Jank Analysis
```sql
INCLUDE PERFETTO MODULE android.frames.timeline;
-- Then in a separate query:
SELECT process_name, jank_type, dur FROM android_frames_timeline;
```

### Startup Analysis
```sql
INCLUDE PERFETTO MODULE android.startups.startups;
-- Then in a separate query:
SELECT package, startup_type, ROUND(dur / 1e6, 2) as duration_ms FROM android_startups;
```

### Memory Counters
```sql
SELECT p.name, c.name as counter, MIN(c.value), MAX(c.value), AVG(c.value)
FROM counter c
JOIN process_counter_track pct ON c.track_id = pct.id
JOIN process p ON pct.upid = p.upid
WHERE c.name LIKE '%rss%' OR c.name LIKE '%pss%'
GROUP BY p.name, c.name;
```

### Binder IPC
```sql
SELECT p.name, s.name, COUNT(*), AVG(s.dur)/1e6 as avg_ms
FROM slice s
JOIN thread_track tt ON s.track_id = tt.id
JOIN thread t ON tt.utid = t.utid
JOIN process p ON t.upid = p.upid
WHERE s.name LIKE 'binder%'
GROUP BY p.name, s.name ORDER BY COUNT(*) DESC;
```

## Recommended Analysis Workflow

1. Load trace and check **trace-summary** for overview
2. Identify the app process with **list-processes**
3. Run domain-specific analysis (jank, startup, memory, etc.)
4. Use **execute-sql** for custom queries when tools don't cover your question
5. Compare traces with aliases for regression detection
