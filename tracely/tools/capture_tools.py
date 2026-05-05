"""Device trace capture tools."""
import json
import asyncio
import threading
from tracely.server import mcp
from tracely.tools.core_tools import trace_manager
from tracely.core import device, capture

# Background capture state
_capture_task = None  # dict with keys: thread, result, status, type, duration_s, package


def _run_capture_in_background(capture_fn, kwargs, task_info):
    """Run a capture function in background thread, store result."""
    try:
        result = capture_fn(**kwargs)
        task_info["result"] = result
        if "error" in result:
            task_info["status"] = "error"
        else:
            task_info["status"] = "done"
    except Exception as e:
        task_info["result"] = {"error": str(e)}
        task_info["status"] = "error"


@mcp.tool()
async def capture_trace(
    duration_s: int = 10,
    package: str = "",
    buffer_size_kb: int = 65536,
    launch_app: bool = False,
    alias: str = "default",
) -> str:
    """Capture a Perfetto trace from a connected Android device.

    Starts capture in background. For traces >5s, call capture-status
    to check completion, then the trace is auto-loaded.

    Args:
        duration_s: Capture duration in seconds (default: 10)
        package: Package name to filter (e.g., "com.example.app")
        buffer_size_kb: Ring buffer size in KB (default: 64MB)
        launch_app: If true, force-stop and cold-launch the package
        alias: Alias for the loaded trace (default: "default")
    """
    global _capture_task

    err = device.check_adb()
    if err:
        return json.dumps({"error": err})

    devices = device.list_devices()
    if not devices:
        return json.dumps({
            "error": "No Android device connected. Connect via USB and enable USB debugging."
        })

    if _capture_task and _capture_task["status"] == "capturing":
        return json.dumps({"error": "A capture is already in progress. Call capture-status to check."})

    # Force-stop for clean cold start if requested
    if launch_app and package:
        import subprocess
        from tracely.core.device import _find_adb
        subprocess.run(
            [_find_adb(), "shell", "am", "force-stop", package],
            capture_output=True, timeout=5,
        )
        await asyncio.sleep(1)

    task_info = {
        "status": "capturing",
        "result": None,
        "type": "trace",
        "duration_s": duration_s,
        "package": package,
        "alias": alias,
    }

    thread = threading.Thread(
        target=_run_capture_in_background,
        args=(capture.capture_trace, {
            "duration_s": duration_s,
            "package": package,
            "buffer_size_kb": buffer_size_kb,
            "launch_app": launch_app,
        }, task_info),
        daemon=True,
    )
    task_info["thread"] = thread
    _capture_task = task_info
    thread.start()

    # For short captures (<=5s), wait for completion inline
    if duration_s <= 5:
        thread.join(timeout=duration_s + 15)
        if task_info["status"] == "done":
            return _finalize_capture(task_info)
        elif task_info["status"] == "error":
            return json.dumps(task_info["result"])

    return json.dumps({
        "status": "capture_started",
        "message": f"Trace capture started ({duration_s}s). Call capture-status to check completion.",
        "duration_s": duration_s,
        "package": package,
    })


@mcp.tool()
async def capture_memory_trace(
    duration_s: int = 30,
    package: str = "",
    native_heap: bool = True,
    java_heap: bool = True,
    alias: str = "default",
) -> str:
    """Capture a memory profiling trace with heap dump data.

    Starts capture in background. Call capture-status to check completion.

    Includes heapprofd (native heap) and java_hprof (Java heap dumps).
    Use analyze-heap-native, analyze-heap-java, and analyze-heap-dominators.

    Requires: Android 10+ for native, Android 11+ for Java.

    Args:
        duration_s: Capture duration in seconds (default: 30)
        package: Package name (required for heap profiling)
        native_heap: Enable native heap profiling (default: true)
        java_heap: Enable Java heap dumps (default: true)
        alias: Alias for the loaded trace (default: "default")
    """
    global _capture_task

    if not package:
        return json.dumps({"error": "Package name required for memory profiling"})

    err = device.check_adb()
    if err:
        return json.dumps({"error": err})

    devices = device.list_devices()
    if not devices:
        return json.dumps({"error": "No Android device connected."})

    if _capture_task and _capture_task["status"] == "capturing":
        return json.dumps({"error": "A capture is already in progress. Call capture-status to check."})

    task_info = {
        "status": "capturing",
        "result": None,
        "type": "memory_trace",
        "duration_s": duration_s,
        "package": package,
        "alias": alias,
    }

    thread = threading.Thread(
        target=_run_capture_in_background,
        args=(capture.capture_memory_trace, {
            "duration_s": duration_s,
            "package": package,
            "java_heap": java_heap,
            "native_heap": native_heap,
        }, task_info),
        daemon=True,
    )
    task_info["thread"] = thread
    _capture_task = task_info
    thread.start()

    # For short captures (<=5s), wait inline
    if duration_s <= 5:
        thread.join(timeout=duration_s + 15)
        if task_info["status"] == "done":
            return _finalize_capture(task_info)
        elif task_info["status"] == "error":
            return json.dumps(task_info["result"])

    return json.dumps({
        "status": "capture_started",
        "message": f"Memory trace capture started ({duration_s}s). Call capture-status to check completion.",
        "duration_s": duration_s,
        "package": package,
    })


@mcp.tool()
def capture_status() -> str:
    """Check the status of a running trace capture.

    Returns the current capture state. If capture is done, auto-loads
    the trace and returns the path. Call this after capture-trace or
    capture-memory-trace when duration > 5s.
    """
    global _capture_task

    if _capture_task is None:
        return json.dumps({"status": "idle", "message": "No capture in progress."})

    if _capture_task["status"] == "capturing":
        return json.dumps({
            "status": "capturing",
            "type": _capture_task["type"],
            "duration_s": _capture_task["duration_s"],
            "package": _capture_task.get("package", ""),
            "message": f"Still capturing... (~{_capture_task['duration_s']}s total)",
        })

    if _capture_task["status"] == "done":
        return _finalize_capture(_capture_task)

    if _capture_task["status"] == "error":
        result = _capture_task["result"]
        _capture_task = None
        return json.dumps(result)

    return json.dumps({"status": "unknown"})


def _finalize_capture(task_info) -> str:
    """Load a completed capture into trace manager and return result."""
    global _capture_task
    result = task_info["result"]
    alias = task_info.get("alias", "default")

    try:
        trace_manager.load_trace(result["path"], alias)
        _capture_task = None
        response = {
            "status": "captured_and_loaded",
            "path": result["path"],
            "alias": alias,
            "duration_s": task_info["duration_s"],
        }
        if task_info["type"] == "memory_trace":
            response["type"] = "memory_trace"
        return json.dumps(response)
    except Exception as e:
        _capture_task = None
        return json.dumps({
            "status": "captured_but_load_failed",
            "path": result["path"],
            "error": str(e),
        })


@mcp.tool()
def device_capabilities() -> str:
    """Probe the connected device for trace capture capabilities.

    Reports Android version, available atrace categories, supported
    data sources, RAM, and device model. Run this before capturing
    to understand what data the trace will contain.
    """
    err = device.check_adb()
    if err:
        return json.dumps({"error": err})

    devices = device.list_devices()
    if not devices:
        return json.dumps({"error": "No device connected."})

    caps = capture.get_device_capabilities()
    caps["available_categories_count"] = len(caps.get("available_categories", []))

    # Summarize what tools will have data
    tools_with_data = ["analyze-scheduling", "analyze-memory", "analyze-binder",
                       "detect-anr", "analyze-blocking-calls", "analyze-oom-priority",
                       "analyze-gc", "analyze-lock-contention", "startup-breakdown",
                       "analyze-startup"]
    tools_conditional = []
    if caps["has_frametimeline"]:
        tools_with_data.append("analyze-jank (frame timeline)")
    else:
        tools_conditional.append("analyze-jank: needs Android 12+ (device is API " + str(caps["api_level"]) + ")")
    if caps["has_network_packets"]:
        tools_with_data.append("analyze-network")
    else:
        tools_conditional.append("analyze-network: needs Android 14+")
    if caps["has_heapprofd"]:
        tools_with_data.append("analyze-heap-native (via capture-memory-trace)")
    if caps["has_java_hprof"]:
        tools_with_data.append("analyze-heap-java (via capture-memory-trace)")
        tools_with_data.append("analyze-heap-dominators (via capture-memory-trace)")

    caps["tools_with_data"] = tools_with_data
    caps["tools_unavailable"] = tools_conditional

    return json.dumps(caps)


@mcp.tool()
def list_android_devices() -> str:
    """List connected Android devices with model and API level info."""
    err = device.check_adb()
    if err:
        return json.dumps({"error": err})

    devices = device.list_devices()
    if not devices:
        return json.dumps({"devices": [], "message": "No devices connected."})

    for d in devices:
        info = device.get_device_info(d["serial"])
        d.update(info)

    return json.dumps({"devices": devices})


@mcp.tool()
async def live_trace_start(
    package: str = "",
    max_file_size_mb: int = 500,
) -> str:
    """Start a long-running trace on the connected device.

    Unlike capture-trace (which has a fixed duration), this runs
    indefinitely until you call live-trace-stop. Use for:
    - Long user journeys (5-30 minutes)
    - Reproducing intermittent bugs
    - Monitoring app behavior over time

    Data streams to a file on device (no buffer overflow).
    Max file size prevents filling device storage.

    Args:
        package: Package name to filter (e.g., "com.example.app")
        max_file_size_mb: Max trace file size in MB (default: 500)
    """
    err = device.check_adb()
    if err:
        return json.dumps({"error": err})

    devices = device.list_devices()
    if not devices:
        return json.dumps({"error": "No device connected."})

    import asyncio
    result = await asyncio.to_thread(
        capture.live_trace_start,
        package=package,
        max_file_size_mb=max_file_size_mb,
    )
    return json.dumps(result)


@mcp.tool()
async def live_trace_stop(alias: str = "default") -> str:
    """Stop a running live trace, pull it from device, and auto-load it.

    Call this after live-trace-start when you're done with your test session.
    The trace is pulled from the device and loaded for analysis.

    Args:
        alias: Alias for the loaded trace (default: "default")
    """
    import asyncio
    result = await asyncio.to_thread(capture.live_trace_stop)

    if "error" in result:
        return json.dumps(result)

    try:
        trace_manager.load_trace(result["path"], alias)
        result["status"] = "stopped_and_loaded"
        result["alias"] = alias
    except Exception as e:
        result["status"] = "stopped_but_load_failed"
        result["error"] = str(e)

    return json.dumps(result)


@mcp.tool()
def live_trace_status() -> str:
    """Check if a live trace is currently running."""
    return json.dumps(capture.live_trace_status())
