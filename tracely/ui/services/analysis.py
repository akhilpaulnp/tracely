"""Analysis service - registry of tools callable from the UI.

The MCP tool functions use singletons from tracely.tools.core_tools.
Each tool module does `from tracely.tools.core_tools import trace_manager`
which creates a local binding. We must patch BOTH core_tools AND each
tool module's local reference so they all point to the UI's instances.
"""
import json
import sys
import tracely.tools.core_tools as core_tools
from tracely.ui.app import trace_manager, query_engine

# Patch the core_tools module
core_tools.trace_manager = trace_manager
core_tools.query_engine = query_engine

# Import all tool modules so they're in sys.modules
from tracely.tools.jank import analyze_jank
from tracely.tools.startup import analyze_startup, startup_breakdown
from tracely.tools.memory import analyze_memory
from tracely.tools.scheduling import analyze_scheduling
from tracely.tools.binder import analyze_binder
from tracely.tools.anr import detect_anr
from tracely.tools.blocking import analyze_blocking_calls
from tracely.tools.contention import analyze_lock_contention
from tracely.tools.gc import analyze_gc
from tracely.tools.heap import analyze_heap_native, analyze_heap_java, analyze_heap_dominators
from tracely.tools.input_latency import analyze_input_latency
from tracely.tools.lmk import detect_lmk
from tracely.tools.network import analyze_network
from tracely.tools.oom import analyze_oom_priority
from tracely.tools.surfaceflinger import analyze_surfaceflinger

# Patch each tool module's local trace_manager/query_engine binding.
# They did `from core_tools import trace_manager` which copied the reference.
_tool_modules = [
    "tracely.tools.jank", "tracely.tools.startup", "tracely.tools.memory",
    "tracely.tools.scheduling", "tracely.tools.binder", "tracely.tools.anr",
    "tracely.tools.blocking", "tracely.tools.contention", "tracely.tools.gc",
    "tracely.tools.heap", "tracely.tools.input_latency", "tracely.tools.lmk",
    "tracely.tools.network", "tracely.tools.oom", "tracely.tools.surfaceflinger",
    "tracely.tools.query_tools", "tracely.tools.regression", "tracely.tools.diagnose",
    "tracely.tools.core_tools",
]
for _mod_name in _tool_modules:
    _mod = sys.modules.get(_mod_name)
    if _mod:
        if hasattr(_mod, "trace_manager"):
            _mod.trace_manager = trace_manager
        if hasattr(_mod, "query_engine"):
            _mod.query_engine = query_engine

# Tool registry: name -> (function, description, category)
TOOLS = {
    "jank": (analyze_jank, "Frame timeline jank analysis", "rendering"),
    "startup": (analyze_startup, "App startup timing", "startup"),
    "startup-breakdown": (startup_breakdown, "Startup phase breakdown", "startup"),
    "memory": (analyze_memory, "Memory counter analysis", "memory"),
    "scheduling": (analyze_scheduling, "CPU scheduling per thread", "cpu"),
    "binder": (analyze_binder, "Binder IPC latency", "ipc"),
    "anr": (detect_anr, "ANR detection", "stability"),
    "blocking-calls": (analyze_blocking_calls, "Blocking calls on main thread", "stability"),
    "lock-contention": (analyze_lock_contention, "Lock contention analysis", "concurrency"),
    "gc": (analyze_gc, "Garbage collection events", "memory"),
    "heap-native": (analyze_heap_native, "Native heap allocations", "memory"),
    "heap-java": (analyze_heap_java, "Java heap objects", "memory"),
    "heap-dominators": (analyze_heap_dominators, "Heap dominator tree", "memory"),
    "input-latency": (analyze_input_latency, "Input dispatch latency", "rendering"),
    "lmk": (detect_lmk, "Low Memory Killer events", "memory"),
    "network": (analyze_network, "Network traffic per app", "network"),
    "oom-priority": (analyze_oom_priority, "OOM adjuster scores", "memory"),
    "surfaceflinger": (analyze_surfaceflinger, "SurfaceFlinger pipeline", "rendering"),
}


def list_tools() -> list[dict]:
    """List available analysis tools."""
    return [
        {"name": name, "description": desc, "category": cat}
        for name, (_, desc, cat) in TOOLS.items()
    ]


def run_tool(tool_name: str, alias: str = "default", package: str = "") -> dict | None:
    """Run a tool by name. Returns parsed JSON dict or None if unknown."""
    entry = TOOLS.get(tool_name)
    if not entry:
        return None

    fn = entry[0]
    try:
        result_json = fn(package=package, alias=alias)
        return json.loads(result_json)
    except TypeError:
        # Some tools don't take package arg
        try:
            result_json = fn(alias=alias)
            return json.loads(result_json)
        except Exception as e:
            return {"error": str(e)}
    except Exception as e:
        return {"error": str(e)}
