package dev.tracely.core.device

import dev.tracely.core.model.DeviceInfo
import java.io.File

/**
 * ADB device detection and management.
 */
object DeviceManager {

    /**
     * Find the adb binary, checking ANDROID_HOME if not on PATH.
     */
    fun findAdb(): String {
        // Check PATH first
        try {
            val p = ProcessBuilder("which", "adb").redirectErrorStream(true).start()
            val result = p.inputStream.bufferedReader().readText().trim()
            p.waitFor()
            if (p.exitValue() == 0 && result.isNotEmpty()) return result
        } catch (_: Exception) {}

        // Check ANDROID_HOME / ANDROID_SDK_ROOT
        for (envVar in listOf("ANDROID_HOME", "ANDROID_SDK_ROOT")) {
            val sdk = System.getenv(envVar) ?: continue
            val candidate = File(sdk, "platform-tools/adb")
            if (candidate.isFile) return candidate.absolutePath
        }

        return "adb" // fallback, will fail with clear error
    }

    /**
     * Check if adb is available.
     */
    fun checkAdb(): String? {
        return try {
            val p = ProcessBuilder(findAdb(), "version")
                .redirectErrorStream(true).start()
            p.waitFor()
            if (p.exitValue() == 0) null else "adb error"
        } catch (_: Exception) {
            "adb not found. Install Android SDK platform-tools or set ANDROID_HOME."
        }
    }

    /**
     * List connected Android devices.
     */
    fun listDevices(): List<DeviceInfo> {
        return try {
            val adb = findAdb()
            val p = ProcessBuilder(adb, "devices", "-l")
                .redirectErrorStream(true).start()
            val output = p.inputStream.bufferedReader().readText()
            p.waitFor()

            output.lines().drop(1)
                .filter { line ->
                    val parts = line.trim().split("\\s+".toRegex())
                    parts.size >= 2 && parts[1] == "device"
                }
                .map { line ->
                    val parts = line.trim().split("\\s+".toRegex())
                    val serial = parts[0]
                    val props = parts.drop(2)
                        .filter { it.contains(":") }
                        .associate { it.split(":", limit = 2).let { kv -> kv[0] to kv[1] } }

                    val info = getDeviceProps(adb, serial)
                    DeviceInfo(
                        serial = serial,
                        model = info["model"] ?: props["model"] ?: "unknown",
                        apiLevel = info["api_level"] ?: "unknown",
                        androidVersion = info["android_version"] ?: "unknown",
                        manufacturer = info["manufacturer"] ?: "unknown",
                    )
                }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun getDeviceProps(adb: String, serial: String): Map<String, String> {
        val props = mapOf(
            "model" to "ro.product.model",
            "api_level" to "ro.build.version.sdk",
            "android_version" to "ro.build.version.release",
            "manufacturer" to "ro.product.manufacturer",
        )
        return props.mapValues { (_, prop) ->
            try {
                val p = ProcessBuilder(adb, "-s", serial, "shell", "getprop", prop)
                    .redirectErrorStream(true).start()
                val result = p.inputStream.bufferedReader().readText().trim()
                p.waitFor()
                result
            } catch (_: Exception) {
                "unknown"
            }
        }
    }

    /**
     * Run an adb shell command and return stdout.
     */
    fun adbShell(vararg args: String, serial: String = ""): String {
        val cmd = mutableListOf(findAdb())
        if (serial.isNotEmpty()) cmd.addAll(listOf("-s", serial))
        cmd.addAll(args)

        return try {
            val p = ProcessBuilder(cmd).redirectErrorStream(true).start()
            val output = p.inputStream.bufferedReader().readText()
            p.waitFor()
            output.trim()
        } catch (_: Exception) {
            ""
        }
    }
}
