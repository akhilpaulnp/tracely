package dev.tracely.core.model

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QueryResultTest {

    @Test
    fun `QueryResult defaults are sensible`() {
        val result = QueryResult()
        assertTrue(result.columns.isEmpty())
        assertTrue(result.rows.isEmpty())
        assertEquals(0, result.rowCount)
        assertEquals(false, result.truncated)
        assertNull(result.error)
    }

    @Test
    fun `QueryResult with error`() {
        val result = QueryResult(error = "something broke")
        assertEquals("something broke", result.error)
    }

    @Test
    fun `QueryResult serializes to JSON`() {
        val result = QueryResult(
            columns = listOf("name", "value"),
            rows = listOf(mapOf("name" to "foo", "value" to "42")),
            rowCount = 1,
        )
        val json = Json.encodeToString(result)
        assertTrue(json.contains("\"name\""))
        assertTrue(json.contains("\"foo\""))
        assertTrue(json.contains("\"rowCount\":1"))
    }

    @Test
    fun `CaptureStatus defaults to IDLE`() {
        val status = CaptureStatus()
        assertEquals(CaptureState.IDLE, status.state)
        assertEquals(0, status.durationS)
        assertNull(status.resultPath)
        assertNull(status.error)
    }

    @Test
    fun `DeviceInfo has correct defaults`() {
        val device = DeviceInfo(serial = "ABC123")
        assertEquals("ABC123", device.serial)
        assertEquals("unknown", device.model)
        assertEquals("unknown", device.apiLevel)
    }

    @Test
    fun `TraceInfo holds all fields`() {
        val info = TraceInfo(
            filename = "trace_20260505_120000_com.example.perfetto-trace",
            path = "/tmp/trace.perfetto-trace",
            sizeBytes = 1024 * 1024,
            sizeMb = 1.0,
            timestamp = "2026-05-05T12:00:00",
            packageName = "com.example",
        )
        assertEquals("com.example", info.packageName)
        assertEquals(1.0, info.sizeMb)
    }

    @Test
    fun `RepoInfo holds all fields`() {
        val repo = RepoInfo(
            name = "MyProject",
            path = "/home/.tracely/MyProject/traces",
            traceCount = 5,
            totalSizeMb = 100.5,
            latest = "2026-05-05T12:00:00",
        )
        assertEquals(5, repo.traceCount)
        assertEquals(100.5, repo.totalSizeMb)
    }
}
