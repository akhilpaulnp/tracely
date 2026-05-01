"""Trace capture orchestration via adb."""
import subprocess
import os
from datetime import datetime

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
        pkg_filter = f'\n    atrace_apps: "{package}"'

    return f"""buffers {{
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
    """Capture a trace from connected device. Returns dict with local path or error."""
    cmd_prefix = ["adb"]
    if serial:
        cmd_prefix += ["-s", serial]

    config = _build_config(duration_s, categories, buffer_size_kb, package)

    proc = subprocess.run(
        cmd_prefix + ["shell", "perfetto", "-c", "-", "-o", DEVICE_TRACE_PATH],
        input=config,
        capture_output=True,
        text=True,
        timeout=duration_s + 30,
    )

    if proc.returncode != 0:
        return {"error": f"Perfetto failed: {proc.stderr}"}

    if launch_app and package:
        subprocess.run(
            cmd_prefix + ["shell", "monkey", "-p", package, "-c",
                         "android.intent.category.LAUNCHER", "1"],
            capture_output=True, timeout=10,
        )

    local_path = _generate_trace_path(package)
    pull = subprocess.run(
        cmd_prefix + ["pull", DEVICE_TRACE_PATH, local_path],
        capture_output=True, text=True, timeout=60,
    )

    if pull.returncode != 0:
        return {"error": f"Failed to pull trace: {pull.stderr}"}

    subprocess.run(
        cmd_prefix + ["shell", "rm", "-f", DEVICE_TRACE_PATH],
        capture_output=True, timeout=5,
    )

    return {"path": local_path, "duration_s": duration_s}
