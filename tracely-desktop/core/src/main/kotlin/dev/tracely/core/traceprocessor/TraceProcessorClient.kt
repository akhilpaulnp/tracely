package dev.tracely.core.traceprocessor

import dev.tracely.core.model.QueryResult

/**
 * Client for a trace_processor_shell instance.
 * Uses the process's interactive stdin/stdout for queries.
 */
class TraceProcessorClient(
    private val process: Process,
) {
    private val stdin = process.outputStream.bufferedWriter()
    private val stdout = process.inputStream.bufferedReader()

    /**
     * Wait for the trace processor to finish loading the trace.
     * Reads stdout until we see the loading complete message.
     */
    fun waitForReady(timeoutMs: Long = 30_000) {
        val start = System.currentTimeMillis()
        val buffer = StringBuilder()

        // Read until we see the prompt or loading complete indicator
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (stdout.ready()) {
                val char = stdout.read()
                if (char == -1) break
                buffer.append(char.toChar())
                // trace_processor_shell prints "> " prompt when ready in interactive mode
                // or prints "Trace loaded" in stderr (redirected)
                val text = buffer.toString()
                if (text.contains("Trace loaded") || text.endsWith("> ")) {
                    return
                }
            } else {
                Thread.sleep(100)
            }
        }
        // If we get here without seeing the prompt, check if process is still alive
        if (process.isAlive) return // Assume ready
        throw RuntimeException("trace_processor_shell exited unexpectedly")
    }

    /**
     * Execute a SQL query and return parsed results.
     * Sends SQL via stdin, reads CSV-formatted output from stdout.
     */
    fun query(sql: String, maxRows: Int = 5000): QueryResult {
        return try {
            // Send the query
            stdin.write(sql.trimEnd().removeSuffix(";") + ";\n")
            stdin.flush()

            // Read response lines until we see the timing line or prompt
            val lines = mutableListOf<String>()
            var foundResults = false

            while (true) {
                val line = stdout.readLine() ?: break

                // Skip empty lines and separator lines
                if (line.isBlank()) continue
                if (line.matches(Regex("^-+\\s*$"))) continue  // "----" separator
                if (line.startsWith("Query executed in")) break  // End marker
                if (line.trim() == ">") break  // Prompt

                // Skip lines that are just the separator between header and data
                if (line.all { it == '-' || it == ' ' }) continue

                lines.add(line)
            }

            if (lines.isEmpty()) {
                return QueryResult(columns = emptyList(), rows = emptyList(), rowCount = 0)
            }

            parseTableOutput(lines, maxRows)
        } catch (e: Exception) {
            QueryResult(error = "Query failed: ${e.message}")
        }
    }

    /**
     * Parse trace_processor_shell's column-aligned table output.
     * Format:
     *   col1                 col2
     *   -------------------- ----------
     *   value1               value2
     */
    private fun parseTableOutput(lines: List<String>, maxRows: Int): QueryResult {
        if (lines.isEmpty()) return QueryResult()

        // First line is headers (space-separated, right-padded)
        val headerLine = lines[0]
        val columns = headerLine.trim().split(Regex("\\s{2,}")).map { it.trim() }

        if (columns.isEmpty()) return QueryResult(error = "Could not parse columns from: $headerLine")

        // Find column positions from the separator line (if present)
        val dataLines = if (lines.size > 1 && lines[1].contains("---")) {
            lines.drop(2)  // Skip header + separator
        } else {
            lines.drop(1)  // Skip header only
        }

        val rows = mutableListOf<Map<String, String>>()
        for (line in dataLines) {
            if (rows.size >= maxRows) break
            if (line.isBlank()) continue

            // Split by 2+ spaces (column-aligned format)
            val values = line.trim().split(Regex("\\s{2,}")).map { it.trim() }

            if (values.size == columns.size) {
                rows.add(columns.zip(values).toMap())
            } else if (values.size == 1 && columns.size == 1) {
                // Single column, single value
                rows.add(mapOf(columns[0] to values[0]))
            }
        }

        return QueryResult(
            columns = columns,
            rows = rows,
            rowCount = rows.size,
            truncated = dataLines.size > maxRows,
        )
    }

    fun close() {
        try {
            stdin.write(".quit\n")
            stdin.flush()
            stdin.close()
        } catch (_: Exception) {}
        process.destroyForcibly()
    }
}
