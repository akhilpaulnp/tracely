"""Import all tool modules to register their @mcp.tool() decorators."""
from mcp_server.tools import core_tools  # noqa: F401
from mcp_server.tools import query_tools  # noqa: F401
from mcp_server.tools import jank  # noqa: F401
from mcp_server.tools import startup  # noqa: F401
from mcp_server.tools import memory  # noqa: F401
from mcp_server.tools import scheduling  # noqa: F401
from mcp_server.tools import binder  # noqa: F401
from mcp_server.tools import anr  # noqa: F401
from mcp_server.tools import heap  # noqa: F401
