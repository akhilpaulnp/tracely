import os
from mcp.server.fastmcp import FastMCP

mcp = FastMCP("tracely")

RESOURCES_DIR = os.path.join(os.path.dirname(__file__), "resources")


@mcp.resource("perfetto://knowledge/android-analysis")
def android_analysis_guide() -> str:
    """Android performance analysis domain knowledge and SQL reference."""
    path = os.path.join(RESOURCES_DIR, "android_analysis.md")
    with open(path) as f:
        return f.read()


@mcp.prompt()
def diagnose_performance(package: str = "") -> str:
    """Guided workflow: diagnose app performance issues step by step."""
    pkg = f" for {package}" if package else ""
    return f"""Analyze the performance{pkg} using this workflow:
1. First, call trace-summary to understand what data is available
2. Call list-processes to find the app process
3. Call analyze-jank to check for frame drops
4. Call analyze-startup to check startup timing
5. Call analyze-memory to check memory usage
6. Call analyze-scheduling to check CPU scheduling
7. Summarize findings with specific metrics and recommendations"""


@mcp.prompt()
def investigate_memory_leak(package: str = "") -> str:
    """Guided workflow: investigate a potential memory leak."""
    pkg = f" for {package}" if package else ""
    return f"""Investigate memory issues{pkg}:
1. Call trace-summary to check available memory data
2. Call analyze-memory to see RSS/PSS trends
3. If native heap data available, call analyze-heap-native for top allocators
4. If Java heap data available, call analyze-heap-java for class histogram
5. Look for growing counters or large allocations without frees
6. Summarize findings with specific leak candidates"""


def main():
    """Entry point for the MCP server."""
    import mcp_server.tools  # noqa: F401
    mcp.run(transport="stdio")


if __name__ == "__main__":
    main()
