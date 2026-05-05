package dev.tracely.core.traceprocessor

import dev.tracely.core.model.QueryResult
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TraceProcessorClientTest {

    /**
     * Build a QueryResult protobuf for testing the parser.
     * Schema:
     *   QueryResult { repeated string column_names = 1; repeated CellsBatch batch = 3; }
     *   CellsBatch {
     *     repeated CellType cells = 1;       // packed varints
     *     repeated int64 varint_cells = 2;   // packed varints
     *     optional string string_cells = 5;  // NUL-separated
     *   }
     */
    private fun buildQueryResult(
        columns: List<String>,
        cellTypes: List<Int>,  // 1=null, 2=varint, 4=blob, 5=string
        varints: List<Long> = emptyList(),
        strings: List<String> = emptyList(),
    ): ByteArray {
        val w = ProtoWriter()
        // Column names (field 1)
        columns.forEach { w.writeString(1, it) }

        // Build the CellsBatch
        val batch = ProtoWriter()
        // Field 1 packed cells
        if (cellTypes.isNotEmpty()) {
            val cellsW = ProtoWriter()
            cellTypes.forEach { cellsW.writeVarintField(0, it.toLong()) }
            // Hack: writeVarintField writes tag too; we want raw varints
            // Better: encode manually
        }
        // Just use raw bytes - encode packed varints by hand
        val packedCells = encodePackedVarints(cellTypes.map { it.toLong() })
        batch.writeBytes(1, packedCells)

        if (varints.isNotEmpty()) {
            batch.writeBytes(2, encodePackedVarints(varints))
        }
        if (strings.isNotEmpty()) {
            // NUL-separated, with trailing NUL
            val concat = strings.joinToString(separator = Char(0).toString(), postfix = Char(0).toString())
            batch.writeString(5, concat)
        }

        w.writeBytes(3, batch.toByteArray())
        return w.toByteArray()
    }

    private fun encodePackedVarints(values: List<Long>): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        for (v in values) {
            var x = v
            while (x and 0x7FL.inv() != 0L) {
                out.write(((x and 0x7FL) or 0x80L).toInt())
                x = x ushr 7
            }
            out.write(x.toInt())
        }
        return out.toByteArray()
    }

    @Test
    fun `parseQueryResult handles single varint column`() {
        val client = TraceProcessorClient(0)
        val bytes = buildQueryResult(
            columns = listOf("cnt"),
            cellTypes = listOf(2), // VARINT
            varints = listOf(81591L),
        )
        val result = client.parseQueryResult(bytes, 100)
        assertEquals(listOf("cnt"), result.columns)
        assertEquals(1, result.rowCount)
        assertEquals("81591", result.rows[0]["cnt"])
    }

    @Test
    fun `parseQueryResult handles multi-row varint column`() {
        val client = TraceProcessorClient(0)
        val bytes = buildQueryResult(
            columns = listOf("val"),
            cellTypes = listOf(2, 2, 2),
            varints = listOf(10L, 20L, 30L),
        )
        val result = client.parseQueryResult(bytes, 100)
        assertEquals(3, result.rowCount)
        assertEquals("10", result.rows[0]["val"])
        assertEquals("30", result.rows[2]["val"])
    }

    @Test
    fun `parseQueryResult handles mixed columns`() {
        val client = TraceProcessorClient(0)
        val bytes = buildQueryResult(
            columns = listOf("pid", "name"),
            cellTypes = listOf(2, 4, 2, 4), // VARINT, STRING, VARINT, STRING
            varints = listOf(7855L, 5235L),
            strings = listOf("com.example", "surfaceflinger"),
        )
        val result = client.parseQueryResult(bytes, 100)
        assertEquals(listOf("pid", "name"), result.columns)
        assertEquals(2, result.rowCount)
        assertEquals("7855", result.rows[0]["pid"])
        assertEquals("com.example", result.rows[0]["name"])
        assertEquals("surfaceflinger", result.rows[1]["name"])
    }

    @Test
    fun `parseQueryResult handles NULL cells`() {
        val client = TraceProcessorClient(0)
        val bytes = buildQueryResult(
            columns = listOf("col"),
            cellTypes = listOf(1), // NULL
        )
        val result = client.parseQueryResult(bytes, 100)
        assertEquals(1, result.rowCount)
        assertEquals("", result.rows[0]["col"])
    }

    @Test
    fun `parseQueryResult respects maxRows`() {
        val client = TraceProcessorClient(0)
        val bytes = buildQueryResult(
            columns = listOf("v"),
            cellTypes = listOf(2, 2, 2, 2),
            varints = listOf(1L, 2L, 3L, 4L),
        )
        val result = client.parseQueryResult(bytes, 2)
        assertEquals(2, result.rowCount)
        assertTrue(result.truncated)
    }
}
