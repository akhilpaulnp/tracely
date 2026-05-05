package dev.tracely.mcp

import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered

/**
 * Entry point for MCP stdio mode.
 * Used when Claude Code spawns: tracely-desktop --mcp
 */
fun main() {
    val tracelyServer = TracelyMcpServer()
    val server = tracelyServer.createServer()

    val transport = StdioServerTransport(
        inputStream = System.`in`.asSource().buffered(),
        outputStream = System.out.asSink().buffered(),
    )

    runBlocking {
        server.connect(transport)
        // Keep running until the transport closes
        (server as? Job)?.join() ?: run {
            // Block until stdin is closed
            while (System.`in`.available() >= 0) {
                kotlinx.coroutines.delay(1000)
            }
        }
    }
}
