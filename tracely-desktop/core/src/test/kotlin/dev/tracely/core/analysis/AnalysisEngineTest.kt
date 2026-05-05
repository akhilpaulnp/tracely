package dev.tracely.core.analysis

import dev.tracely.core.traceprocessor.TraceProcessorManager
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AnalysisEngineTest {

    private val tpManager = TraceProcessorManager()
    private val engine = AnalysisEngine(tpManager)

    @Test
    fun `tools registry has all 18 tools`() {
        assertEquals(18, engine.tools.size)
    }

    @Test
    fun `tools cover all expected categories`() {
        val categories = engine.tools.values.map { it.category }.toSet()
        assertTrue("rendering" in categories)
        assertTrue("startup" in categories)
        assertTrue("memory" in categories)
        assertTrue("cpu" in categories)
        assertTrue("ipc" in categories)
        assertTrue("stability" in categories)
        assertTrue("concurrency" in categories)
        assertTrue("network" in categories)
    }

    @Test
    fun `listTools returns all tools with required fields`() {
        val tools = engine.listTools()
        assertEquals(18, tools.size)
        tools.forEach { tool ->
            assertNotNull(tool["name"])
            assertNotNull(tool["description"])
            assertNotNull(tool["category"])
            assertTrue(tool["name"]!!.isNotEmpty())
            assertTrue(tool["description"]!!.isNotEmpty())
        }
    }

    @Test
    fun `run returns error for unknown tool`() = runTest {
        val result = engine.run("nonexistent-tool")
        assertNotNull(result.error)
        assertTrue(result.error!!.contains("Unknown tool"))
    }

    @Test
    fun `run returns error when no trace loaded`() = runTest {
        val result = engine.run("jank")
        assertNotNull(result.error)
        assertTrue(result.error!!.contains("No trace loaded"))
    }

    @Test
    fun `pkgFilter escapes SQL wildcards`() {
        // Access the pkgFilter via the tool's sqlTemplate
        // The jank tool uses pkgFilter internally
        val jankTool = engine.tools["jank"]!!
        val sql = jankTool.sqlTemplate("com.test%app")

        // Verify the wildcard % is escaped
        assertTrue(sql.contains("com.test\\%app"), "Wildcard % should be escaped in: $sql")
        assertTrue(sql.contains("ESCAPE"), "SQL should contain ESCAPE clause")
    }

    @Test
    fun `pkgFilter escapes underscore wildcard`() {
        val jankTool = engine.tools["jank"]!!
        val sql = jankTool.sqlTemplate("com.test_app")

        assertTrue(sql.contains("com.test\\_app"), "Underscore _ should be escaped in: $sql")
    }

    @Test
    fun `pkgFilter escapes single quotes`() {
        val jankTool = engine.tools["jank"]!!
        val sql = jankTool.sqlTemplate("com.test'app")

        assertTrue(sql.contains("com.test''app"), "Single quote should be doubled in: $sql")
    }

    @Test
    fun `pkgFilter returns empty for empty package`() {
        val jankTool = engine.tools["jank"]!!
        val sql = jankTool.sqlTemplate("")

        // Should not contain AND ... LIKE when package is empty
        assertTrue(!sql.contains("LIKE"), "Empty package should not add LIKE filter")
    }

    @Test
    fun `all tools have valid SQL templates`() {
        engine.tools.forEach { (name, tool) ->
            // Generate SQL with empty package (should not fail)
            val sqlEmpty = tool.sqlTemplate("")
            assertTrue(sqlEmpty.contains("SELECT"), "Tool '$name' should produce a SELECT query")

            // Generate SQL with a package
            val sqlPkg = tool.sqlTemplate("com.example.app")
            assertTrue(sqlPkg.contains("SELECT"), "Tool '$name' with package should produce a SELECT query")
        }
    }

    @Test
    fun `tool names are kebab-case`() {
        engine.tools.keys.forEach { name ->
            assertTrue(
                name.matches(Regex("^[a-z][a-z0-9-]*$")),
                "Tool name '$name' should be kebab-case"
            )
        }
    }
}
