package dev.tracely.core.traceprocessor

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TraceProcessorClientTest {

    @Test
    fun `parseQueryResponse handles empty body`() {
        val client = TraceProcessorClient(0)
        val method = client::class.java.getDeclaredMethod("parseQueryResponse", String::class.java, Int::class.javaPrimitiveType)
        method.isAccessible = true

        val result = method.invoke(client, "", 100) as dev.tracely.core.model.QueryResult
        assertNotNull(result.error)
        assertTrue(result.error!!.contains("Empty"))
        client.close()
    }

    @Test
    fun `parseQueryResponse parses pipe-separated format`() {
        val client = TraceProcessorClient(0)
        val method = client::class.java.getDeclaredMethod("parseQueryResponse", String::class.java, Int::class.javaPrimitiveType)
        method.isAccessible = true

        val body = """
            "name"|"value"
            "foo"|"123"
            "bar"|"456"
        """.trimIndent()

        val result = method.invoke(client, body, 100) as dev.tracely.core.model.QueryResult
        assertEquals(listOf("name", "value"), result.columns)
        assertEquals(2, result.rowCount)
        assertEquals("foo", result.rows[0]["name"])
        assertEquals("123", result.rows[0]["value"])
        assertEquals("bar", result.rows[1]["name"])
        client.close()
    }

    @Test
    fun `parseQueryResponse respects maxRows`() {
        val client = TraceProcessorClient(0)
        val method = client::class.java.getDeclaredMethod("parseQueryResponse", String::class.java, Int::class.javaPrimitiveType)
        method.isAccessible = true

        val body = """
            "col"
            "a"
            "b"
            "c"
            "d"
        """.trimIndent()

        val result = method.invoke(client, body, 2) as dev.tracely.core.model.QueryResult
        assertEquals(2, result.rowCount)
        assertTrue(result.truncated)
        client.close()
    }

    @Test
    fun `parseQueryResponse handles mismatched columns`() {
        val client = TraceProcessorClient(0)
        val method = client::class.java.getDeclaredMethod("parseQueryResponse", String::class.java, Int::class.javaPrimitiveType)
        method.isAccessible = true

        val body = """
            "a"|"b"
            "1"|"2"|"extra"
            "3"|"4"
        """.trimIndent()

        val result = method.invoke(client, body, 100) as dev.tracely.core.model.QueryResult
        // Row with extra column is skipped (size mismatch)
        assertEquals(1, result.rowCount)
        assertEquals("3", result.rows[0]["a"])
        client.close()
    }
}
