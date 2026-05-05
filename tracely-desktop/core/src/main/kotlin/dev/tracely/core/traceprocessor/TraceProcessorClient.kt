package dev.tracely.core.traceprocessor

import dev.tracely.core.model.QueryResult
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import java.io.File

/**
 * HTTP client for a trace_processor_shell instance.
 * Each loaded trace gets its own trace_processor_shell subprocess.
 */
class TraceProcessorClient(private val port: Int) {

    private val client = HttpClient(CIO)
    private val baseUrl = "http://127.0.0.1:$port"

    /**
     * Wait for the trace processor to be ready.
     */
    suspend fun waitForReady(timeoutMs: Long = 10_000) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            try {
                val response = client.get("$baseUrl/status")
                if (response.status == HttpStatusCode.OK) return
            } catch (_: Exception) {
                // Not ready yet
            }
            delay(200)
        }
        throw RuntimeException("trace_processor_shell did not start within ${timeoutMs}ms")
    }

    /**
     * Execute a SQL query and return parsed results.
     */
    suspend fun query(sql: String, maxRows: Int = 5000): QueryResult {
        return try {
            val response = client.post("$baseUrl/query") {
                contentType(ContentType.Text.Plain)
                setBody(sql)
            }

            val body = response.bodyAsText()
            parseQueryResponse(body, maxRows)
        } catch (e: Exception) {
            QueryResult(error = "Query failed: ${e.message}")
        }
    }

    /**
     * Parse the raw query response into a QueryResult.
     * trace_processor_shell returns a custom text format.
     */
    private fun parseQueryResponse(body: String, maxRows: Int): QueryResult {
        val lines = body.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return QueryResult(error = "Empty response")

        // First line is column headers separated by |
        val columns = lines.first().split("|").map { it.trim().trim('"') }
        val rows = mutableListOf<Map<String, String>>()

        for (line in lines.drop(1)) {
            if (rows.size >= maxRows) break
            val values = line.split("|").map { it.trim().trim('"') }
            if (values.size == columns.size) {
                rows.add(columns.zip(values).toMap())
            }
        }

        return QueryResult(
            columns = columns,
            rows = rows,
            rowCount = rows.size,
            truncated = lines.size - 1 > maxRows,
        )
    }

    fun close() {
        client.close()
    }
}
