package dev.tracely.core.traceprocessor

import org.junit.jupiter.api.Test
import java.io.File
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TraceProcessorBinaryTest {

    @Test
    fun `detectPlatform returns valid platform string`() {
        val method = TraceProcessorBinary::class.java.getDeclaredMethod("detectPlatform")
        method.isAccessible = true
        val platform = method.invoke(TraceProcessorBinary) as String

        assertTrue(platform.contains("-"), "Platform should be os-arch format: $platform")
        val (os, arch) = platform.split("-")
        assertTrue(os in listOf("mac", "linux", "windows"), "Unknown OS: $os")
        assertTrue(arch in listOf("amd64", "arm64"), "Unknown arch: $arch")
    }

    @Test
    fun `sha256 produces correct hash`() {
        val method = TraceProcessorBinary::class.java.getDeclaredMethod("sha256", File::class.java)
        method.isAccessible = true

        // Create a temp file with known content
        val tmp = File.createTempFile("tracely-test-", ".bin")
        try {
            tmp.writeText("hello tracely")
            val hash = method.invoke(TraceProcessorBinary, tmp) as String

            // Verify against Java's MessageDigest directly
            val expected = MessageDigest.getInstance("SHA-256")
                .digest("hello tracely".toByteArray())
                .joinToString("") { "%02x".format(it) }

            assertEquals(expected, hash)
            assertEquals(64, hash.length, "SHA-256 should be 64 hex chars")
        } finally {
            tmp.delete()
        }
    }

    @Test
    fun `getCachedBinaryPath returns path under tracely home`() {
        val method = TraceProcessorBinary::class.java.getDeclaredMethod("getCachedBinaryPath")
        method.isAccessible = true
        val path = method.invoke(TraceProcessorBinary) as File

        assertTrue(path.absolutePath.contains(".tracely"))
        assertTrue(path.name.startsWith("trace_processor_shell"))
    }
}
