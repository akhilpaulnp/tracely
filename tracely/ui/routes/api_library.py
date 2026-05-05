"""Library API routes - browse repos and traces."""
import json
from starlette.requests import Request
from starlette.responses import JSONResponse
from starlette.routing import Route

from tracely.ui.services.library import list_repos, list_traces


async def get_repos(request: Request):
    repos = list_repos()
    return JSONResponse({"repos": repos})


async def get_traces(request: Request):
    repo = request.query_params.get("repo", "")
    if not repo:
        return JSONResponse({"error": "repo parameter required"}, status_code=400)
    traces = list_traces(repo)
    return JSONResponse({"repo": repo, "traces": traces})


routes = [
    Route("/api/library/repos", get_repos),
    Route("/api/library/traces", get_traces),
]
