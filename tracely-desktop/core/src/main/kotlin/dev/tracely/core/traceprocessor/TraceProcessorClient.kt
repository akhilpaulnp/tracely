package dev.tracely.core.traceprocessor

import dev.tracely.core.model.QueryResult
import java.net.HttpURLConnection
import java.net.URI

/**
 * HTTP+Protobuf client for trace_processor_shell --httpd.
 * Speaks the official Perfetto RPC protocol.
 */
class TraceProcessorClient(private val port: Int) {

    private val baseUrl = "http://127.0.0.1:$port"

    fun waitForReady(timeoutMs: Long = 30_000) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            try {
                val conn = URI("$baseUrl/status").toURL().openConnection() as HttpURLConnection
                conn.connectTimeout = 1000
                conn.readTimeout = 1000
                if (conn.responseCode == 200) {
                    conn.inputStream.close()
                    return
                }
            } catch (_: Exception) {
                // Not ready yet
            }
            Thread.sleep(200)
        }
        throw RuntimeException("trace_processor_shell did not start within ${timeoutMs}ms")
    }

    fun query(sql: String, maxRows: Int = 5000): QueryResult {
        return try {
            val writer = ProtoWriter()
            writer.writeString(1, sql)
            val requestBytes = writer.toByteArray()

            val conn = URI("$baseUrl/query").toURL().openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-protobuf")
            conn.outputStream.use { it.write(requestBytes) }

            if (conn.responseCode != 200) {
                return QueryResult(error = "HTTP ${conn.responseCode}: ${conn.responseMessage}")
            }

            val responseBytes = conn.inputStream.use { it.readBytes() }
            parseQueryResult(responseBytes, maxRows)
        } catch (e: Exception) {
            QueryResult(error = "Query failed: ${e.message}")
        }
    }

    /**
     * Parse a QueryResult protobuf response.
     * Schema:
     *   message QueryResult {
     *     repeated string column_names = 1;
     *     optional string error = 2;
     *     repeated CellsBatch batch = 3;
     *   }
     *   message CellsBatch {
     *     repeated CellType cells = 1 [packed];  // 1=null, 2=varint, 3=float, 4=blob, 5=string
     *     repeated int64 varint_cells = 2 [packed];
     *     repeated double float64_cells = 3 [packed];
     *     repeated bytes blob_cells = 4;
     *     optional string string_cells = 5;  // NUL-separated cells
     *     optional bool is_last_batch = 6;
     *   }
     */
    internal fun parseQueryResult(bytes: ByteArray, maxRows: Int): QueryResult {
        val reader = ProtoReader(bytes)
        val columnNames = mutableListOf<String>()
        var error: String? = null
        val rows = mutableListOf<Map<String, String>>()

        val pendingCells = ArrayDeque<Long>()
        val pendingVarints = ArrayDeque<Long>()
        val pendingFloats = ArrayDeque<Double>()
        val pendingStrings = ArrayDeque<String>()
        val pendingBlobs = ArrayDeque<ByteArray>()

        fun consumeRow(): Map<String, String>? {
            if (pendingCells.size < columnNames.size) return null
            val row = mutableMapOf<String, String>()
            for (col in columnNames) {
                val cellType = pendingCells.removeFirst().toInt()
                row[col] = when (cellType) {
                    1 -> "" // NULL
                    2 -> pendingVarints.removeFirstOrNull()?.toString() ?: ""
                    3 -> {
                        val d = pendingFloats.removeFirstOrNull() ?: 0.0
                        if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
                    }
                    4 -> pendingStrings.removeFirstOrNull() ?: ""
                    5 -> pendingBlobs.removeFirstOrNull()?.toString(Charsets.UTF_8) ?: ""
                    else -> ""
                }
            }
            return row
        }

        while (reader.hasMore()) {
            val (fieldNum, wireType) = reader.readTag()
            when (fieldNum) {
                1 -> columnNames.add(reader.readString())
                2 -> error = reader.readString()
                3 -> {
                    val batch = reader.readMessage()
                    while (batch.hasMore()) {
                        val (bfn, bwt) = batch.readTag()
                        when (bfn) {
                            1 -> pendingCells.addAll(batch.readPackedVarints())
                            2 -> pendingVarints.addAll(batch.readPackedVarints())
                            3 -> pendingFloats.addAll(batch.readPackedDoubles())
                            4 -> pendingBlobs.add(batch.readBytes())
                            5 -> {
                                // NUL-separated strings (cell5: 0x00 byte)
                                val concat = batch.readString()
                                if (concat.isNotEmpty()) {
                                    val parts = concat.split(Char(0))
                                    val toAdd = if (parts.last().isEmpty()) parts.dropLast(1) else parts
                                    pendingStrings.addAll(toAdd)
                                }
                            }
                            6 -> batch.readVarint() // is_last_batch
                            else -> batch.skipField(bwt)
                        }
                    }

                    // Drain complete rows
                    while (pendingCells.size >= columnNames.size && rows.size < maxRows) {
                        val row = consumeRow() ?: break
                        rows.add(row)
                    }
                }
                else -> reader.skipField(wireType)
            }
        }

        return QueryResult(
            columns = columnNames,
            rows = rows,
            rowCount = rows.size,
            truncated = pendingCells.size >= columnNames.size,
            error = error,
        )
    }

    fun close() {
        // Nothing to close
    }
}
