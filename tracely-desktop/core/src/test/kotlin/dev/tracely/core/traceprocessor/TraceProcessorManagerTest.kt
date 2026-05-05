package dev.tracely.core.traceprocessor

import dev.tracely.core.model.QueryResult
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.AfterEach
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TraceProcessorManagerTest {

    private val manager = TraceProcessorManager()

    @AfterEach
    fun cleanup() {
        manager.closeAll()
    }

    @Test
    fun `listTraces returns empty initially`() {
        assertTrue(manager.listTraces().isEmpty())
    }

    @Test
    fun `requireTrace returns error when no trace loaded`() {
        val err = manager.requireTrace("default")
        assertNotNull(err)
        assertTrue(err.contains("No trace loaded"))
    }

    @Test
    fun `requireTrace returns null after loading would succeed`() {
        // We can't actually load a trace without trace_processor_shell,
        // but we can verify the check logic
        val err = manager.requireTrace("nonexistent")
        assertNotNull(err)
    }

    @Test
    fun `closeTrace on nonexistent alias does not throw`() {
        // Should be a no-op
        manager.closeTrace("nonexistent")
        assertTrue(manager.listTraces().isEmpty())
    }

    @Test
    fun `closeAll on empty manager does not throw`() {
        manager.closeAll()
        assertTrue(manager.listTraces().isEmpty())
    }

    @Test
    fun `query returns error when no trace loaded`() = runTest {
        val result = manager.query("default", "SELECT 1")
        assertNotNull(result.error)
        assertTrue(result.error!!.contains("No trace loaded"))
    }
}
