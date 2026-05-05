package dev.tracely.core.capture

import dev.tracely.core.model.CaptureState
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CaptureManagerTest {

    private val manager = CaptureManager()

    @Test
    fun `initial status is IDLE`() {
        assertEquals(CaptureState.IDLE, manager.status.state)
    }

    @Test
    fun `PACKAGE_NAME_REGEX accepts valid Android package names`() {
        val regex = CaptureManager::class.java.getDeclaredField("PACKAGE_NAME_REGEX")
        regex.isAccessible = true
        val pattern = regex.get(manager) as Regex

        assertTrue(pattern.matches("com.example.app"))
        assertTrue(pattern.matches("com.entri.app"))
        assertTrue(pattern.matches("me.entri.entrime"))
        assertTrue(pattern.matches("com.android.systemui"))
        assertTrue(pattern.matches("a.b.c"))
    }

    @Test
    fun `PACKAGE_NAME_REGEX rejects injection attempts`() {
        val regex = CaptureManager::class.java.getDeclaredField("PACKAGE_NAME_REGEX")
        regex.isAccessible = true
        val pattern = regex.get(manager) as Regex

        assertTrue(!pattern.matches("com.example\nmalicious"))
        assertTrue(!pattern.matches("com.example\"inject"))
        assertTrue(!pattern.matches("com.example;rm -rf"))
        assertTrue(!pattern.matches("com.example app"))
        assertTrue(!pattern.matches(""))
    }

    @Test
    fun `buildConfig sanitizes package name`() {
        val method = CaptureManager::class.java.getDeclaredMethod("buildConfig", Int::class.javaPrimitiveType, String::class.java)
        method.isAccessible = true

        // Valid package name should appear in config
        val config = method.invoke(manager, 5, "com.example.app") as String
        assertTrue(config.contains("com.example.app"))
        assertTrue(config.contains("atrace_apps"))

        // Injection attempt should be stripped (safePkg becomes empty)
        val configBad = method.invoke(manager, 5, "com.example\"\nmalicious") as String
        assertTrue(!configBad.contains("atrace_apps"), "Malicious package should not produce atrace_apps line")
    }

    @Test
    fun `buildConfig includes required data sources`() {
        val method = CaptureManager::class.java.getDeclaredMethod("buildConfig", Int::class.javaPrimitiveType, String::class.java)
        method.isAccessible = true

        val config = method.invoke(manager, 10, "") as String
        assertTrue(config.contains("duration_ms: 10000"))
        assertTrue(config.contains("linux.process_stats"))
        assertTrue(config.contains("linux.sys_stats"))
        assertTrue(config.contains("linux.ftrace"))
        assertTrue(config.contains("sched/sched_switch"))
    }

    @Test
    fun `buildConfig omits atrace_apps when package is empty`() {
        val method = CaptureManager::class.java.getDeclaredMethod("buildConfig", Int::class.javaPrimitiveType, String::class.java)
        method.isAccessible = true

        val config = method.invoke(manager, 5, "") as String
        assertTrue(!config.contains("atrace_apps"))
    }
}
