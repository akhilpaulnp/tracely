"""Trace management API routes."""
import os
from starlette.requests import Request
from starlette.responses import JSONResponse
from starlette.routing import Route

from tracely.ui.app import trace_manager


async def get_traces(request: Request):
    traces = trace_manager.list_traces()
    return JSONResponse({"traces": traces, "count": len(traces)})


async def load_trace(request: Request):
    content_type = request.headers.get("content-type", "")
    if "json" in content_type:
        body = await request.json()
    else:
        form = await request.form()
        body = dict(form)

    path = body.get("path", "")
    alias = body.get("alias", "default")

    if not path or not os.path.exists(path):
        return JSONResponse({"error": f"File not found: {path}"}, status_code=400)

    try:
        info = trace_manager.load_trace(path, alias)
        return JSONResponse({"status": "loaded", **info})
    except Exception as e:
        return JSONResponse({"error": str(e)}, status_code=500)


async def close_trace(request: Request):
    alias = request.path_params["alias"]
    try:
        trace_manager.close_trace(alias)
        return JSONResponse({"status": "closed", "alias": alias})
    except KeyError as e:
        return JSONResponse({"error": str(e)}, status_code=404)


routes = [
    Route("/api/traces", get_traces, methods=["GET"]),
    Route("/api/traces/load", load_trace, methods=["POST"]),
    Route("/api/traces/{alias}", close_trace, methods=["DELETE"]),
]
