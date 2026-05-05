package dev.tracely.core.library

import dev.tracely.core.model.TraceInfo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TraceLibraryTest {

    // Test the parsing logic via reflection since parseTimestamp/parsePackage are private
    private fun parseTimestamp(filename: String): String? {
        val method = TraceLibrary::class.java.getDeclaredMethod("parseTimestamp", String::class.java)
        method.isAccessible = true
        return method.invoke(TraceLibrary, filename) as? String
    }

    private fun parsePackage(filename: String): String? {
        val method = TraceLibrary::class.java.getDeclaredMethod("parsePackage", String::class.java)
        method.isAccessible = true
        return method.invoke(TraceLibrary, filename) as? String
    }

    @Test
    fun `parseTimestamp extracts ISO datetime from trace filename`() {
        val ts = parseTimestamp("trace_20260505_165012.perfetto-trace")
        assertEquals("2026-05-05T16:50:12", ts)
    }

    @Test
    fun `parseTimestamp extracts datetime from filename with package`() {
        val ts = parseTimestamp("trace_20260505_165054_com.entri.app.perfetto-trace")
        assertEquals("2026-05-05T16:50:54", ts)
    }

    @Test
    fun `parseTimestamp returns null for non-trace filename`() {
        assertNull(parseTimestamp("random_file.perfetto-trace"))
        assertNull(parseTimestamp("not_a_trace.txt"))
    }

    @Test
    fun `parseTimestamp returns null for invalid date`() {
        assertNull(parseTimestamp("trace_99999999_999999.perfetto-trace"))
    }

    @Test
    fun `parsePackage extracts package name`() {
        val pkg = parsePackage("trace_20260505_165054_com.entri.app.perfetto-trace")
        assertEquals("com.entri.app", pkg)
    }

    @Test
    fun `parsePackage returns null when no package in filename`() {
        val pkg = parsePackage("trace_20260505_165012.perfetto-trace")
        assertNull(pkg)
    }

    @Test
    fun `parsePackage handles package with underscores`() {
        val pkg = parsePackage("trace_20260505_165054_com.entri.app_memory.perfetto-trace")
        assertEquals("com.entri.app_memory", pkg)
    }

    @Test
    fun `listTraces rejects directory traversal attempts`() {
        // These should all return empty due to path validation
        assertTrue(TraceLibrary.listTraces("../etc").isEmpty())
        assertTrue(TraceLibrary.listTraces("../../root").isEmpty())
        assertTrue(TraceLibrary.listTraces("foo/bar").isEmpty())
        assertTrue(TraceLibrary.listTraces("foo\\bar").isEmpty())
        assertTrue(TraceLibrary.listTraces("..").isEmpty())
    }

    @Test
    fun `listTraces returns empty for nonexistent repo`() {
        assertTrue(TraceLibrary.listTraces("nonexistent_repo_xyz").isEmpty())
    }

    @Test
    fun `listRepos returns list including real repos if they exist`() {
        // This test verifies the function runs without error
        // Actual results depend on ~/.tracely/ contents
        val repos = TraceLibrary.listRepos()
        // Just verify it returns a list without throwing
        assertTrue(repos is List)
    }
}
