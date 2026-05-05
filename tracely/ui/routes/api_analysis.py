"""Analysis API routes - run tools and return results."""
import json
from starlette.requests import Request
from starlette.responses import JSONResponse
from starlette.routing import Route

from tracely.ui.app import trace_manager, query_engine
from tracely.ui.services.analysis import run_tool, list_tools


async def get_tools(request: Request):
    """List available analysis tools."""
    return JSONResponse({"tools": list_tools()})


async def run_analysis(request: Request):
    """Run an analysis tool and return results."""
    tool_name = request.path_params["tool"]
    alias = request.query_params.get("alias", "default")
    package = request.query_params.get("package", "")

    err = trace_manager.require_trace(alias)
    if err:
        return JSONResponse({"error": err}, status_code=400)

    result = run_tool(tool_name, alias=alias, package=package)
    if result is None:
        return JSONResponse({"error": f"Unknown tool: {tool_name}"}, status_code=404)

    return JSONResponse(result)


routes = [
    Route("/api/analysis/tools", get_tools),
    Route("/api/analysis/{tool}", run_analysis),
]
