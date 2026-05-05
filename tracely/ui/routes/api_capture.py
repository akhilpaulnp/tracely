"""Capture API routes with SSE progress."""
import json
import asyncio
import threading
from starlette.requests import Request
from starlette.responses import JSONResponse
from starlette.routing import Route
from sse_starlette.sse import EventSourceResponse

from tracely.core import device, capture
from tracely.ui.app import trace_manager

# Background capture state
_capture_state = {"status": "idle", "result": None}


def _run_capture(capture_fn, kwargs):
    """Run capture in background thread."""
    global _capture_state
    try:
        result = capture_fn(**kwargs)
        if "error" in result:
            _capture_state = {"status": "error", "result": result}
        else:
            _capture_state = {"status": "done", "result": result}
    except Exception as e:
        _capture_state = {"status": "error", "result": {"error": str(e)}}


async def start_capture(request: Request):
    global _capture_state

    if _capture_state["status"] == "capturing":
        return JSONResponse({"error": "Capture already in progress"}, status_code=409)

    body = await request.json()
    duration_s = body.get("duration_s", 10)
    package = body.get("package", "")
    launch_app = body.get("launch_app", False)
    capture_type = body.get("type", "trace")  # "trace" or "memory"

    err = device.check_adb()
    if err:
        return JSONResponse({"error": err}, status_code=500)

    devices_list = device.list_devices()
    if not devices_list:
        return JSONResponse({"error": "No device connected"}, status_code=400)

    _capture_state = {"status": "capturing", "result": None,
                      "duration_s": duration_s, "package": package}

    if capture_type == "memory":
        fn = capture.capture_memory_trace
        kwargs = {"duration_s": duration_s, "package": package,
                  "java_heap": True, "native_heap": True}
    else:
        fn = capture.capture_trace
        kwargs = {"duration_s": duration_s, "package": package,
                  "launch_app": launch_app}

    thread = threading.Thread(target=_run_capture, args=(fn, kwargs), daemon=True)
    thread.start()

    return JSONResponse({"status": "capture_started", "duration_s": duration_s})


async def capture_status_stream(request: Request):
    """SSE endpoint for capture progress."""
    async def event_generator():
        while True:
            await request.is_disconnected()
            yield {"event": "status", "data": json.dumps(_capture_state)}
            if _capture_state["status"] in ("done", "error", "idle"):
                # If done, try to auto-load the trace
                if _capture_state["status"] == "done" and _capture_state["result"]:
                    path = _capture_state["result"].get("path", "")
                    if path:
                        try:
                            trace_manager.load_trace(path, "default")
                            yield {"event": "loaded", "data": json.dumps(
                                {"path": path, "alias": "default"})}
                        except Exception:
                            pass
                break
            await asyncio.sleep(1)

    return EventSourceResponse(event_generator())


async def get_capture_status(request: Request):
    return JSONResponse(_capture_state)


routes = [
    Route("/api/capture/start", start_capture, methods=["POST"]),
    Route("/api/capture/status", get_capture_status),
    Route("/api/capture/stream", capture_status_stream),
]
