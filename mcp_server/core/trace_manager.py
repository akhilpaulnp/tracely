import threading
from typing import Optional
from perfetto.trace_processor import TraceProcessor, TraceProcessorConfig

MAX_TRACES = 3


class TraceManager:
    """Manages multiple TraceProcessor instances with aliases."""

    def __init__(self):
        self._traces: dict[str, dict] = {}
        self._lock = threading.Lock()

    def load_trace(self, path: str, alias: str = "default") -> dict:
        """Load a trace file and assign it an alias.

        Returns dict with alias, path info. Raises RuntimeError if max exceeded.
        """
        with self._lock:
            if alias in self._traces:
                self._traces[alias]["tp"].close()
                del self._traces[alias]

            if len(self._traces) >= MAX_TRACES:
                loaded = ", ".join(self._traces.keys())
                raise RuntimeError(
                    f"Maximum {MAX_TRACES} traces. Close one first. "
                    f"Loaded: {loaded}"
                )

            config = TraceProcessorConfig(bin_path=None, verbose=False)
            tp = TraceProcessor(trace=path, config=config)
            self._traces[alias] = {"tp": tp, "path": path, "alias": alias}
            return {"alias": alias, "path": path}

    def close_trace(self, alias: str = "default") -> None:
        """Close a trace by alias. Raises KeyError if not found."""
        with self._lock:
            if alias not in self._traces:
                raise KeyError(f"No trace with alias '{alias}'")
            self._traces[alias]["tp"].close()
            del self._traces[alias]

    def get_trace(self, alias: str = "default") -> TraceProcessor:
        """Get TraceProcessor by alias. Raises KeyError if not found."""
        with self._lock:
            if alias not in self._traces:
                raise KeyError(f"No trace with alias '{alias}'")
            return self._traces[alias]["tp"]

    def list_traces(self) -> list[dict]:
        """List all loaded traces with alias and path."""
        with self._lock:
            return [
                {"alias": info["alias"], "path": info["path"]}
                for info in self._traces.values()
            ]

    def require_trace(self, alias: str = "default") -> Optional[str]:
        """Return None if trace exists, error string otherwise."""
        with self._lock:
            if not self._traces:
                return "No trace loaded. Use load-trace first."
            if alias not in self._traces:
                loaded = ", ".join(self._traces.keys())
                return f"No trace '{alias}'. Loaded: {loaded}"
            return None
