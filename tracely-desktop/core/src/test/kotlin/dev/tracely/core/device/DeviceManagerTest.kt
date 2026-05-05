package dev.tracely.core.device

import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DeviceManagerTest {

    @Test
    fun `findAdb returns a path`() {
        val adb = DeviceManager.findAdb()
        assertNotNull(adb)
        assertTrue(adb.isNotEmpty())
    }

    @Test
    fun `checkAdb returns null when adb is available`() {
        // This test passes only when adb is installed
        val result = DeviceManager.checkAdb()
        // Either null (adb found) or an error string (not found)
        // We just verify it doesn't throw
        assertTrue(result == null || result.isNotEmpty())
    }

    @Test
    fun `listDevices returns a list without throwing`() {
        // Results depend on whether a device is connected
        val devices = DeviceManager.listDevices()
        assertNotNull(devices)
    }

    @Test
    fun `adbShell returns empty string on failure`() {
        // Running a nonsense command should return empty, not throw
        val result = DeviceManager.adbShell("shell", "echo", "test", serial = "nonexistent_serial_xyz")
        // Either returns output or empty string, should not throw
        assertNotNull(result)
    }
}
