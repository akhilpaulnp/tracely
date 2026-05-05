package dev.tracely.core.traceprocessor

import dev.tracely.core.model.QueryResult
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TraceProcessorClientTest {

    /**
     * Test parseTableOutput via a mock process.
     * We create a fake Process with known output to test parsing.
     */
    @Test
    fun `parseTableOutput handles single-column output`() {
        val method = TraceProcessorClient::class.java.getDeclaredMethod(
            "parseTableOutput", List::class.java, Int::class.javaPrimitiveType
        )
        method.isAccessible = true

        // Simulate trace_processor_shell output
        val lines = listOf(
            "cnt",
            "--------------------",
            "81591",
        )

        // Need a dummy client instance
        val dummyProcess = ProcessBuilder("echo").start()
        val client = TraceProcessorClient(dummyProcess)
        val result = method.invoke(client, lines, 100) as QueryResult

        assertEquals(listOf("cnt"), result.columns)
        assertEquals(1, result.rowCount)
        assertEquals("81591", result.rows[0]["cnt"])
        client.close()
    }

    @Test
    fun `parseTableOutput handles multi-column output`() {
        val method = TraceProcessorClient::class.java.getDeclaredMethod(
            "parseTableOutput", List::class.java, Int::class.javaPrimitiveType
        )
        method.isAccessible = true

        val lines = listOf(
            "pid                   name",
            "--------------------  --------------------",
            "7855                  com.example.app",
            "5235                  surfaceflinger",
        )

        val dummyProcess = ProcessBuilder("echo").start()
        val client = TraceProcessorClient(dummyProcess)
        val result = method.invoke(client, lines, 100) as QueryResult

        assertEquals(listOf("pid", "name"), result.columns)
        assertEquals(2, result.rowCount)
        assertEquals("7855", result.rows[0]["pid"])
        assertEquals("com.example.app", result.rows[0]["name"])
        client.close()
    }

    @Test
    fun `parseTableOutput respects maxRows`() {
        val method = TraceProcessorClient::class.java.getDeclaredMethod(
            "parseTableOutput", List::class.java, Int::class.javaPrimitiveType
        )
        method.isAccessible = true

        val lines = listOf(
            "col",
            "----",
            "a",
            "b",
            "c",
            "d",
        )

        val dummyProcess = ProcessBuilder("echo").start()
        val client = TraceProcessorClient(dummyProcess)
        val result = method.invoke(client, lines, 2) as QueryResult

        assertEquals(2, result.rowCount)
        assertTrue(result.truncated)
        client.close()
    }

    @Test
    fun `parseTableOutput handles empty input`() {
        val method = TraceProcessorClient::class.java.getDeclaredMethod(
            "parseTableOutput", List::class.java, Int::class.javaPrimitiveType
        )
        method.isAccessible = true

        val dummyProcess = ProcessBuilder("echo").start()
        val client = TraceProcessorClient(dummyProcess)
        val result = method.invoke(client, emptyList<String>(), 100) as QueryResult

        assertEquals(0, result.rowCount)
        client.close()
    }
}
