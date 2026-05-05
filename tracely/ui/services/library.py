"""Trace library service - scan ~/.tracely/ for repos and traces."""
import os
from datetime import datetime

TRACELY_HOME = os.path.join(os.path.expanduser("~"), ".tracely")


def list_repos() -> list[dict]:
    """List all repositories that have traces."""
    repos = []
    if not os.path.isdir(TRACELY_HOME):
        return repos

    for entry in sorted(os.listdir(TRACELY_HOME)):
        traces_dir = os.path.join(TRACELY_HOME, entry, "traces")
        if os.path.isdir(traces_dir):
            traces = _list_trace_files(traces_dir)
            if traces:
                total_size = sum(t["size_bytes"] for t in traces)
                repos.append({
                    "name": entry,
                    "path": traces_dir,
                    "trace_count": len(traces),
                    "total_size_mb": round(total_size / (1024 * 1024), 1),
                    "latest": traces[0]["timestamp"] if traces else None,
                })

    # Also check for traces directly in ~/.tracely/traces/
    fallback_dir = os.path.join(TRACELY_HOME, "traces")
    if os.path.isdir(fallback_dir):
        traces = _list_trace_files(fallback_dir)
        if traces:
            total_size = sum(t["size_bytes"] for t in traces)
            repos.append({
                "name": "(no repo)",
                "path": fallback_dir,
                "trace_count": len(traces),
                "total_size_mb": round(total_size / (1024 * 1024), 1),
                "latest": traces[0]["timestamp"] if traces else None,
            })

    return repos


def list_traces(repo: str) -> list[dict]:
    """List all trace files for a given repo."""
    if repo == "(no repo)":
        traces_dir = os.path.join(TRACELY_HOME, "traces")
    else:
        traces_dir = os.path.join(TRACELY_HOME, repo, "traces")

    if not os.path.isdir(traces_dir):
        return []

    return _list_trace_files(traces_dir)


def _list_trace_files(directory: str) -> list[dict]:
    """List .perfetto-trace files in a directory, newest first."""
    traces = []
    for fname in os.listdir(directory):
        if fname.endswith(".perfetto-trace"):
            fpath = os.path.join(directory, fname)
            stat = os.stat(fpath)
            # Parse timestamp from filename: trace_YYYYMMDD_HHMMSS[_pkg].perfetto-trace
            timestamp = _parse_timestamp(fname)
            package = _parse_package(fname)
            traces.append({
                "filename": fname,
                "path": fpath,
                "size_bytes": stat.st_size,
                "size_mb": round(stat.st_size / (1024 * 1024), 1),
                "timestamp": timestamp,
                "package": package,
            })

    traces.sort(key=lambda t: t["timestamp"] or "", reverse=True)
    return traces


def _parse_timestamp(filename: str) -> str | None:
    """Extract ISO timestamp from trace filename."""
    # trace_20260505_165012.perfetto-trace
    # trace_20260505_165012_com.example.app.perfetto-trace
    try:
        parts = filename.replace(".perfetto-trace", "").split("_")
        if len(parts) >= 3 and parts[0] == "trace":
            date_str = parts[1]  # YYYYMMDD
            time_str = parts[2]  # HHMMSS
            dt = datetime.strptime(f"{date_str}{time_str}", "%Y%m%d%H%M%S")
            return dt.isoformat()
    except (ValueError, IndexError):
        pass
    return None


def _parse_package(filename: str) -> str | None:
    """Extract package name from trace filename if present."""
    # trace_20260505_165054_com.entri.app.perfetto-trace
    name = filename.replace(".perfetto-trace", "")
    parts = name.split("_")
    if len(parts) >= 4 and parts[0] == "trace":
        # Everything after date and time is the package
        return "_".join(parts[3:])
    return None
