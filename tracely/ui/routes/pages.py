"""HTML page routes."""
from starlette.requests import Request
from starlette.routing import Route

from tracely.ui.app import templates, trace_manager
from tracely.ui.services.library import list_repos


async def dashboard(request: Request):
    repos = list_repos()
    loaded = trace_manager.list_traces()
    return templates.TemplateResponse(request, "dashboard.html", {
        "repos": repos,
        "loaded_traces": loaded,
    })


async def library(request: Request):
    repos = list_repos()
    selected_repo = request.query_params.get("repo", "")
    traces = []
    if selected_repo:
        from tracely.ui.services.library import list_traces
        traces = list_traces(selected_repo)
    return templates.TemplateResponse(request, "library.html", {
        "repos": repos,
        "selected_repo": selected_repo,
        "traces": traces,
    })


async def capture_page(request: Request):
    return templates.TemplateResponse(request, "capture.html", {})


async def analysis_page(request: Request):
    loaded = trace_manager.list_traces()
    return templates.TemplateResponse(request, "analysis.html", {
        "loaded_traces": loaded,
    })


routes = [
    Route("/", dashboard),
    Route("/library", library),
    Route("/capture", capture_page),
    Route("/analysis", analysis_page),
]
