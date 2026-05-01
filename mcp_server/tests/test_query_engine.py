import json
import pytest
from unittest.mock import MagicMock, PropertyMock
from mcp_server.core.query_engine import QueryEngine


def _make_mock_result(columns, rows_data):
    """Helper to create a mock QueryResultIterator."""
    mock_result = MagicMock()
    type(mock_result).column_names = PropertyMock(return_value=columns)

    mock_rows = []
    for row_dict in rows_data:
        row = MagicMock()
        for col in columns:
            setattr(row, col, row_dict[col])
        mock_rows.append(row)

    mock_result.__iter__ = MagicMock(return_value=iter(mock_rows))
    return mock_result


class TestQueryEngine:
    def setup_method(self):
        self.engine = QueryEngine()
        self.mock_tp = MagicMock()

    def test_run_query_returns_json(self):
        self.mock_tp.query.return_value = _make_mock_result(
            ["name", "pid"],
            [{"name": "com.example", "pid": 1234}],
        )
        result = self.engine.run_query(self.mock_tp, "SELECT name, pid FROM process")
        parsed = json.loads(result)
        assert len(parsed["rows"]) == 1
        assert parsed["rows"][0]["name"] == "com.example"
        assert parsed["columns"] == ["name", "pid"]

    def test_run_query_max_rows(self):
        rows = [{"id": i} for i in range(6000)]
        self.mock_tp.query.return_value = _make_mock_result(["id"], rows)

        result = self.engine.run_query(self.mock_tp, "SELECT id FROM big_table")
        parsed = json.loads(result)
        assert len(parsed["rows"]) == 5000
        assert parsed["truncated"] is True

    def test_run_query_error(self):
        from perfetto.common.exceptions import PerfettoException
        self.mock_tp.query.side_effect = PerfettoException("bad sql")

        result = self.engine.run_query(self.mock_tp, "INVALID SQL")
        parsed = json.loads(result)
        assert "error" in parsed
        assert "bad sql" in parsed["error"]

    def test_ensure_module_loads_once(self):
        self.mock_tp.query.return_value = _make_mock_result([], [])

        self.engine.ensure_module(self.mock_tp, "android.frames.timeline")
        self.engine.ensure_module(self.mock_tp, "android.frames.timeline")

        include_calls = [
            c for c in self.mock_tp.query.call_args_list
            if "INCLUDE PERFETTO MODULE" in str(c)
        ]
        assert len(include_calls) == 1

    def test_ensure_module_different_modules(self):
        self.mock_tp.query.return_value = _make_mock_result([], [])

        self.engine.ensure_module(self.mock_tp, "android.frames.timeline")
        self.engine.ensure_module(self.mock_tp, "android.startups.startups")

        assert self.mock_tp.query.call_count == 2

    def test_bytes_handled(self):
        self.mock_tp.query.return_value = _make_mock_result(
            ["data"], [{"data": b"\x00\xff"}]
        )
        result = self.engine.run_query(self.mock_tp, "SELECT data FROM t")
        parsed = json.loads(result)
        assert parsed["rows"][0]["data"] == "00ff"
