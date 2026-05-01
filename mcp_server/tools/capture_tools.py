"""Device trace capture tools."""
import json
import asyncio
from mcp_server.server import mcp
from mcp_server.tools.core_tools import trace_manager
from mcp_server.core import device, capture


@mcp.tool()
async def capture_trace(
    duration_s: int = 10,
    package: str = "",
    categories: str = "am,binder_driver,dalvik,gfx,hal,input,sched,view,wm",
    buffer_size_kb: int = 65536,
    launch_app: bool = False,
    alias: str = "default",
) -> str:
    """Capture a Perfetto trace from a connected Android device.

    Captures system-wide trace data for the specified duration, pulls it
    from the device, and auto-loads it for analysis.

    Args:
        duration_s: Capture duration in seconds (default: 10)
        package: Package name to filter (e.g., "com.example.app")
        categories: Atrace categories (comma-separated)
        buffer_size_kb: Ring buffer size in KB (default: 64MB)
        launch_app: If true, launch the package on trace start
        alias: Alias for the loaded trace (default: "default")
    """
    err = device.check_adb()
    if err:
        return json.dumps({"error": err})

    devices = device.list_devices()
    if not devices:
        return json.dumps({
            "error": "No Android device connected. Connect via USB and enable USB debugging."
        })

    result = await asyncio.to_thread(
        capture.capture_trace,
        duration_s=duration_s,
        package=package,
        categories=categories,
        buffer_size_kb=buffer_size_kb,
        launch_app=launch_app,
    )

    if "error" in result:
        return json.dumps(result)

    try:
        trace_manager.load_trace(result["path"], alias)
        return json.dumps({
            "status": "captured_and_loaded",
            "path": result["path"],
            "alias": alias,
            "duration_s": duration_s,
        })
    except Exception as e:
        return json.dumps({
            "status": "captured_but_load_failed",
            "path": result["path"],
            "error": str(e),
        })


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
