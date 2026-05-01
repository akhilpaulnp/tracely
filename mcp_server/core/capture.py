"""Trace capture orchestration via adb."""
import subprocess
import os
import time
from datetime import datetime

TRACES_DIR = os.path.join(os.path.dirname(os.path.dirname(__file__)), "traces")
DEVICE_TRACE_PATH = "/data/misc/perfetto-traces/mcp_trace.perfetto-trace"

# Default atrace categories matching Perfetto UI's default Android preset
DEFAULT_CATEGORIES = [
    "aidl",           # AIDL binder tracing
    "am",             # Activity Manager
    "binder_driver",  # Binder kernel driver
    "camera",         # Camera subsystem
    "dalvik",         # ART/Dalvik VM (GC events, JIT, class loading)
    "disk",           # Disk I/O
    "freq",           # CPU frequency changes
    "gfx",            # Graphics (SurfaceFlinger, RenderThread, hwui)
    "hal",            # Hardware Abstraction Layer
    "idle",           # CPU idle states
    "input",          # Input events dispatch
    "memory",         # Memory events
    "memreclaim",     # Memory reclaim (LMK)
    "network",        # Network activity
    "power",          # Power management
    "res",            # Resource loading
    "sched",          # CPU scheduling
    "ss",             # System server
    "sync",           # Synchronization primitives
    "view",           # View system (inflate, measure, layout, draw)
    "wm",             # Window Manager
    "workq",          # Kernel workqueues
]

# ftrace events needed for our analysis tools
FTRACE_EVENTS = [
    # CPU scheduling (scheduling tool, thread_state table)
    "sched/sched_switch",
    "sched/sched_blocked_reason",
    "sched/sched_wakeup",
    "sched/sched_wakeup_new",
    "sched/sched_waking",
    "sched/sched_process_exit",
    "sched/sched_process_free",
    # Task tracking
    "task/task_newtask",
    "task/task_rename",
    # Power / CPU frequency
    "power/suspend_resume",
    "power/cpu_frequency",
    "power/cpu_idle",
    "power/clock_enable",
    "power/clock_disable",
    "power/clock_set_rate",
    # OOM / LMK (oom_adjuster tool, lmk tool)
    "oom/oom_score_adj_update",
    "lowmemorykiller/lowmemorykiller_kill",
    # Memory (memory tool)
    "mm_event/mm_event_record",
    "kmem/rss_stat",
    # Filesystem I/O
    "f2fs/f2fs_sync_file_enter",
    "f2fs/f2fs_sync_file_exit",
    "f2fs/f2fs_write_begin",
    "f2fs/f2fs_write_end",
    # Ftrace print (needed for atrace markers)
    "ftrace/print",
]


def _ensure_traces_dir():
    os.makedirs(TRACES_DIR, exist_ok=True)


def _generate_trace_path(package: str = "") -> str:
    _ensure_traces_dir()
    ts = datetime.now().strftime("%Y%m%d_%H%M%S")
    suffix = f"_{package}" if package else ""
    return os.path.join(TRACES_DIR, f"trace_{ts}{suffix}.perfetto-trace")


def _get_api_level(serial: str = "") -> int:
    """Get the Android API level of the connected device."""
    cmd = ["adb"]
    if serial:
        cmd += ["-s", serial]
    try:
        r = subprocess.run(
            cmd + ["shell", "getprop", "ro.build.version.sdk"],
            capture_output=True, text=True, timeout=5,
        )
        return int(r.stdout.strip())
    except Exception:
        return 0


def _build_config(
    duration_s: int = 10,
    categories: list = None,
    buffer_size_kb: int = 65536,
    package: str = "",
    api_level: int = 0,
) -> str:
    """Build a Perfetto text-format trace config.

    Adapts data sources to device API level:
    - All devices: ftrace, process_stats, packages_list, logcat, sys_stats
    - Android 12+ (API 31): surfaceflinger.frametimeline
    - Android 14+ (API 34): network_packets
    """
    if categories is None:
        categories = DEFAULT_CATEGORIES

    cat_lines = "\n".join(
        f'      atrace_categories: "{c}"' for c in categories
    )

    ftrace_lines = "\n".join(
        f'      ftrace_events: "{e}"' for e in FTRACE_EVENTS
    )

    pkg_line = ""
    if package:
        pkg_line = f'\n      atrace_apps: "{package}"'

    # Core data sources (all Android versions)
    config = f"""buffers {{
  size_kb: {buffer_size_kb}
  fill_policy: RING_BUFFER
}}
buffers {{
  size_kb: 2048
  fill_policy: RING_BUFFER
}}
data_sources {{
  config {{
    name: "linux.ftrace"
    target_buffer: 0
    ftrace_config {{
{ftrace_lines}
{cat_lines}{pkg_line}
    }}
  }}
}}
data_sources {{
  config {{
    name: "linux.process_stats"
    target_buffer: 1
    process_stats_config {{
      scan_all_processes_on_start: true
      proc_stats_poll_ms: 1000
    }}
  }}
}}
data_sources {{
  config {{
    name: "android.packages_list"
  }}
}}
data_sources {{
  config {{
    name: "android.log"
    android_log_config {{
      log_ids: LID_DEFAULT
      log_ids: LID_SYSTEM
      log_ids: LID_CRASH
      log_ids: LID_EVENTS
    }}
  }}
}}
data_sources {{
  config {{
    name: "linux.sys_stats"
    sys_stats_config {{
      meminfo_period_ms: 1000
      meminfo_counters: MEMINFO_MEM_TOTAL
      meminfo_counters: MEMINFO_MEM_FREE
      meminfo_counters: MEMINFO_MEM_AVAILABLE
      vmstat_period_ms: 1000
      stat_period_ms: 1000
      stat_counters: STAT_CPU_TIMES
      stat_counters: STAT_FORK_COUNT
    }}
  }}
}}
"""

    # Android 12+ (API 31): Frame timeline for jank analysis
    if api_level >= 31:
        config += """data_sources {
  config {
    name: "android.surfaceflinger.frametimeline"
  }
}
"""

    # Android 14+ (API 34): Network packet tracing
    if api_level >= 34:
        config += """data_sources {
  config {
    name: "android.network_packets"
    network_packet_trace_config {
      poll_ms: 250
    }
  }
}
"""

    config += f"duration_ms: {duration_s * 1000}\n"
    return config


def _build_memory_config(
    duration_s: int = 30,
    package: str = "",
    heap_sampling_bytes: int = 4096,
    java_heap: bool = True,
    native_heap: bool = True,
    api_level: int = 0,
) -> str:
    """Build a Perfetto config for memory profiling.

    Includes heapprofd (native) and java_hprof (Java) data sources
    on top of the standard config.
    """
    base = _build_config(duration_s=duration_s, package=package, api_level=api_level)

    extra = ""
    if native_heap and package:
        extra += f"""
data_sources {{
  config {{
    name: "android.heapprofd"
    heapprofd_config {{
      sampling_interval_bytes: {heap_sampling_bytes}
      process_cmdline: "{package}"
      shmem_size_bytes: 8388608
      block_client: true
    }}
  }}
}}
"""

    if java_heap and package:
        extra += f"""
data_sources {{
  config {{
    name: "android.java_hprof"
    java_hprof_config {{
      process_cmdline: "{package}"
      continuous_dump_config {{
        dump_interval_ms: 5000
      }}
    }}
  }}
}}
"""

    return base + extra


def capture_trace(
    duration_s: int = 10,
    package: str = "",
    categories: list = None,
    buffer_size_kb: int = 65536,
    launch_app: bool = False,
    serial: str = "",
) -> dict:
    """Capture a trace from connected device. Returns dict with local path or error."""
    cmd_prefix = ["adb"]
    if serial:
        cmd_prefix += ["-s", serial]

    api_level = _get_api_level(serial)
    config = _build_config(duration_s, categories, buffer_size_kb, package, api_level)

    # Use Popen (non-blocking) so we can launch the app while tracing
    # --txt flag needed for text-format configs on most Android devices
    proc = subprocess.Popen(
        cmd_prefix + ["shell", "perfetto", "--txt", "-c", "-", "-o", DEVICE_TRACE_PATH],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    proc.stdin.write(config)
    proc.stdin.close()

    # Launch app after a short delay for perfetto to start collecting
    if launch_app and package:
        time.sleep(2)
        subprocess.run(
            cmd_prefix + ["shell", "monkey", "-p", package, "-c",
                         "android.intent.category.LAUNCHER", "1"],
            capture_output=True, timeout=10,
        )

    # Wait for perfetto to finish (it runs for duration_ms then exits)
    try:
        _, stderr = proc.communicate(timeout=duration_s + 30)
    except subprocess.TimeoutExpired:
        proc.kill()
        return {"error": "Perfetto capture timed out"}

    if proc.returncode != 0:
        return {"error": f"Perfetto failed: {stderr}"}

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


def capture_memory_trace(
    duration_s: int = 30,
    package: str = "",
    heap_sampling_bytes: int = 4096,
    java_heap: bool = True,
    native_heap: bool = True,
    serial: str = "",
) -> dict:
    """Capture a memory profiling trace. Includes heapprofd + java_hprof."""
    if not package:
        return {"error": "Package name required for memory profiling"}

    cmd_prefix = ["adb"]
    if serial:
        cmd_prefix += ["-s", serial]

    api_level = _get_api_level(serial)
    config = _build_memory_config(
        duration_s, package, heap_sampling_bytes, java_heap, native_heap, api_level
    )

    proc = subprocess.Popen(
        cmd_prefix + ["shell", "perfetto", "--txt", "-c", "-", "-o", DEVICE_TRACE_PATH],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    proc.stdin.write(config)
    proc.stdin.close()

    try:
        _, stderr = proc.communicate(timeout=duration_s + 30)
    except subprocess.TimeoutExpired:
        proc.kill()
        return {"error": "Memory trace capture timed out"}

    if proc.returncode != 0:
        return {"error": f"Perfetto failed: {stderr}"}

    local_path = _generate_trace_path(package + "_memory")
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
