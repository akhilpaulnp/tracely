import pytest
from unittest.mock import MagicMock, patch
from mcp_server.core.trace_manager import TraceManager


class TestTraceManager:
    def setup_method(self):
        self.tm = TraceManager()

    def test_no_traces_initially(self):
        assert self.tm.list_traces() == []

    def test_require_trace_fails_when_empty(self):
        result = self.tm.require_trace()
        assert result is not None
        assert "no trace" in result.lower()

    @patch("mcp_server.core.trace_manager.TraceProcessor")
    def test_load_trace_success(self, mock_tp_class):
        mock_tp = MagicMock()
        mock_tp_class.return_value = mock_tp
        self.tm.load_trace("/fake/trace.perfetto-trace", "test")
        assert "test" in [t["alias"] for t in self.tm.list_traces()]

    @patch("mcp_server.core.trace_manager.TraceProcessor")
    def test_load_trace_default_alias(self, mock_tp_class):
        mock_tp_class.return_value = MagicMock()
        self.tm.load_trace("/fake/trace.perfetto-trace")
        traces = self.tm.list_traces()
        assert len(traces) == 1
        assert traces[0]["alias"] == "default"

    @patch("mcp_server.core.trace_manager.TraceProcessor")
    def test_max_traces_enforced(self, mock_tp_class):
        mock_tp_class.return_value = MagicMock()
        self.tm.load_trace("/fake/a.trace", "a")
        self.tm.load_trace("/fake/b.trace", "b")
        self.tm.load_trace("/fake/c.trace", "c")
        with pytest.raises(RuntimeError, match="(?i)maximum"):
            self.tm.load_trace("/fake/d.trace", "d")

    @patch("mcp_server.core.trace_manager.TraceProcessor")
    def test_close_trace(self, mock_tp_class):
        mock_tp = MagicMock()
        mock_tp_class.return_value = mock_tp
        self.tm.load_trace("/fake/trace.perfetto-trace", "test")
        self.tm.close_trace("test")
        assert self.tm.list_traces() == []
        mock_tp.close.assert_called_once()

    def test_close_nonexistent_trace(self):
        with pytest.raises(KeyError):
            self.tm.close_trace("nonexistent")

    @patch("mcp_server.core.trace_manager.TraceProcessor")
    def test_get_trace(self, mock_tp_class):
        mock_tp = MagicMock()
        mock_tp_class.return_value = mock_tp
        self.tm.load_trace("/fake/trace.perfetto-trace", "test")
        assert self.tm.get_trace("test") is mock_tp

    @patch("mcp_server.core.trace_manager.TraceProcessor")
    def test_require_trace_succeeds_when_loaded(self, mock_tp_class):
        mock_tp_class.return_value = MagicMock()
        self.tm.load_trace("/fake/trace.perfetto-trace", "test")
        assert self.tm.require_trace("test") is None

    @patch("mcp_server.core.trace_manager.TraceProcessor")
    def test_load_replaces_same_alias(self, mock_tp_class):
        mock_tp1 = MagicMock()
        mock_tp2 = MagicMock()
        mock_tp_class.side_effect = [mock_tp1, mock_tp2]
        self.tm.load_trace("/fake/a.trace", "test")
        self.tm.load_trace("/fake/b.trace", "test")
        assert len(self.tm.list_traces()) == 1
        mock_tp1.close.assert_called_once()
