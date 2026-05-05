"""Tracely Web UI - Starlette application."""
import os
import webbrowser
import uvicorn
from starlette.applications import Starlette
from starlette.routing import Route, Mount
from starlette.staticfiles import StaticFiles
from starlette.templating import Jinja2Templates

from tracely.core.trace_manager import TraceManager
from tracely.core.query_engine import QueryEngine

# Shared instances for the UI process
trace_manager = TraceManager()
query_engine = QueryEngine()

# Template and static paths
UI_DIR = os.path.dirname(os.path.abspath(__file__))
TEMPLATES_DIR = os.path.join(UI_DIR, "templates")
STATIC_DIR = os.path.join(UI_DIR, "static")

templates = Jinja2Templates(directory=TEMPLATES_DIR)


def create_app() -> Starlette:
    """Create the Starlette application."""
    from tracely.ui.routes.pages import routes as page_routes
    from tracely.ui.routes.api_library import routes as library_routes
    from tracely.ui.routes.api_traces import routes as traces_routes
    from tracely.ui.routes.api_devices import routes as devices_routes
    from tracely.ui.routes.api_capture import routes as capture_routes
    from tracely.ui.routes.api_analysis import routes as analysis_routes

    all_routes = [
        *page_routes,
        *library_routes,
        *traces_routes,
        *devices_routes,
        *capture_routes,
        *analysis_routes,
        Mount("/static", StaticFiles(directory=STATIC_DIR), name="static"),
    ]

    app = Starlette(routes=all_routes)
    return app


def main():
    """Entry point for tracely-ui command."""
    port = int(os.environ.get("TRACELY_PORT", "8420"))
    host = os.environ.get("TRACELY_HOST", "127.0.0.1")

    print(f"Tracely UI starting at http://{host}:{port}")
    webbrowser.open(f"http://{host}:{port}")

    app = create_app()
    uvicorn.run(app, host=host, port=port, log_level="info")


if __name__ == "__main__":
    main()
