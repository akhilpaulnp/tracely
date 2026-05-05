package dev.tracely.mcp

import dev.tracely.core.analysis.AnalysisEngine
import dev.tracely.core.capture.CaptureManager
import dev.tracely.core.device.DeviceManager
import dev.tracely.core.library.TraceLibrary
import dev.tracely.core.model.CaptureState
import dev.tracely.core.traceprocessor.TraceProcessorManager
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*

/**
 * MCP server exposing Tracely's analysis tools to Claude Code / Codex.
 */
class TracelyMcpServer {

    val tpManager = TraceProcessorManager()
    val analysisEngine = AnalysisEngine(tpManager)
    val captureManager = CaptureManager()

    private fun textResult(text: String) = CallToolResult(content = listOf(TextContent(text)))

    private fun prop(type: String): JsonElement = buildJsonObject { put("type", type) }

    private fun schema(
        vararg properties: Pair<String, JsonElement>,
        required: List<String> = emptyList(),
    ) = ToolSchema(
        properties = buildJsonObject { properties.forEach { (k, v) -> put(k, v) } },
        required = required,
    )

    fun createServer(): Server {
        val server = Server(
            serverInfo = Implementation(name = "tracely", version = "1.0.0"),
            options = ServerOptions(
                capabilities = ServerCapabilities(
                    tools = ServerCapabilities.Tools(listChanged = true),
                ),
            ),
        )

        registerTraceManagementTools(server)
        registerAnalysisTools(server)
        registerDeviceTools(server)
        registerCaptureTools(server)
        registerLibraryTools(server)

        return server
    }

    private fun registerTraceManagementTools(server: Server) {
        server.addTool(
            name = "load-trace",
            description = "Load a Perfetto trace file for analysis",
            inputSchema = schema("path" to prop("string"), "alias" to prop("string"), required = listOf("path")),
        ) { args ->
            val path = args.arguments?.get("path")?.jsonPrimitive?.content ?: ""
            val alias = args.arguments?.get("alias")?.jsonPrimitive?.content ?: "default"
            val result = runBlocking { tpManager.loadTrace(path, alias) }
            textResult("Loaded trace '$alias' from $path (port: ${result.port})")
        }

        server.addTool(
            name = "close-trace",
            description = "Close a loaded trace to free resources",
            inputSchema = schema("alias" to prop("string")),
        ) { args ->
            val alias = args.arguments?.get("alias")?.jsonPrimitive?.content ?: "default"
            tpManager.closeTrace(alias)
            textResult("Closed trace '$alias'")
        }

        server.addTool(
            name = "list-traces",
            description = "List all currently loaded traces",
            inputSchema = schema(),
        ) { _ ->
            val traces = tpManager.listTraces()
            textResult(Json.encodeToString(traces))
        }

        server.addTool(
            name = "execute-sql",
            description = "Execute a raw PerfettoSQL query on a loaded trace",
            inputSchema = schema("sql" to prop("string"), "alias" to prop("string"), required = listOf("sql")),
        ) { args ->
            val sql = args.arguments?.get("sql")?.jsonPrimitive?.content ?: ""
            val alias = args.arguments?.get("alias")?.jsonPrimitive?.content ?: "default"
            val result = runBlocking { tpManager.query(alias, sql) }
            textResult(Json.encodeToString(result))
        }
    }

    private fun registerAnalysisTools(server: Server) {
        for ((name, tool) in analysisEngine.tools) {
            server.addTool(
                name = "analyze-$name",
                description = tool.description,
                inputSchema = schema("package" to prop("string"), "alias" to prop("string")),
            ) { args ->
                val pkg = args.arguments?.get("package")?.jsonPrimitive?.content ?: ""
                val alias = args.arguments?.get("alias")?.jsonPrimitive?.content ?: "default"
                val result = runBlocking { analysisEngine.run(name, alias, pkg) }
                textResult(Json.encodeToString(result))
            }
        }
    }

    private fun registerDeviceTools(server: Server) {
        server.addTool(
            name = "list-android-devices",
            description = "List connected Android devices with model and API level info",
            inputSchema = schema(),
        ) { _ ->
            val devices = DeviceManager.listDevices()
            textResult(Json.encodeToString(devices))
        }
    }

    private fun registerCaptureTools(server: Server) {
        server.addTool(
            name = "capture-trace",
            description = "Capture a Perfetto trace from a connected Android device",
            inputSchema = schema(
                "duration_s" to prop("integer"),
                "package" to prop("string"),
                "launch_app" to prop("boolean"),
            ),
        ) { args ->
            val durationS = args.arguments?.get("duration_s")?.jsonPrimitive?.intOrNull ?: 10
            val pkg = args.arguments?.get("package")?.jsonPrimitive?.content ?: ""
            val launchApp = args.arguments?.get("launch_app")?.jsonPrimitive?.booleanOrNull ?: false

            val result = runBlocking { captureManager.captureTrace(durationS, pkg, launchApp) }

            if (result.state == CaptureState.DONE && result.resultPath != null) {
                runBlocking { tpManager.loadTrace(result.resultPath!!, "default") }
                textResult("Captured and loaded trace: ${result.resultPath}")
            } else {
                textResult("Capture failed: ${result.error ?: "unknown error"}")
            }
        }
    }

    private fun registerLibraryTools(server: Server) {
        server.addTool(
            name = "list-trace-repos",
            description = "List repositories with saved traces in ~/.tracely/",
            inputSchema = schema(),
        ) { _ ->
            val repos = TraceLibrary.listRepos()
            textResult(Json.encodeToString(repos))
        }

        server.addTool(
            name = "list-repo-traces",
            description = "List trace files for a specific repository",
            inputSchema = schema("repo" to prop("string"), required = listOf("repo")),
        ) { args ->
            val repo = args.arguments?.get("repo")?.jsonPrimitive?.content ?: ""
            val traces = TraceLibrary.listTraces(repo)
            textResult(Json.encodeToString(traces))
        }
    }
}
