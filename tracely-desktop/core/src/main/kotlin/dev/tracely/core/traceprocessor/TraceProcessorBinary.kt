package dev.tracely.core.traceprocessor

import java.io.File
import java.net.URI
import java.security.MessageDigest

/**
 * Manages the trace_processor_shell binary.
 * Downloads the prebuilt binary if not already present.
 */
object TraceProcessorBinary {

    private const val VERSION = "v49.0"
    private val TRACELY_HOME = File(System.getProperty("user.home"), ".tracely")
    private val BINARY_DIR = File(TRACELY_HOME, "bin")

    private val DOWNLOAD_URLS = mapOf(
        "mac-amd64" to "https://commondatastorage.googleapis.com/perfetto-luci-artifacts/$VERSION/mac-amd64/trace_processor_shell",
        "mac-arm64" to "https://commondatastorage.googleapis.com/perfetto-luci-artifacts/$VERSION/mac-arm64/trace_processor_shell",
        "linux-amd64" to "https://commondatastorage.googleapis.com/perfetto-luci-artifacts/$VERSION/linux-amd64/trace_processor_shell",
        "windows-amd64" to "https://commondatastorage.googleapis.com/perfetto-luci-artifacts/$VERSION/windows-amd64/trace_processor_shell.exe",
    )

    // SHA-256 checksums for each platform binary (v49.0)
    // Update these when changing VERSION
    private val EXPECTED_SHA256 = mapOf(
        "mac-amd64" to "", // populate on first verified download
        "mac-arm64" to "",
        "linux-amd64" to "",
        "windows-amd64" to "",
    )

    /**
     * Get the path to the trace_processor_shell binary.
     * Downloads it if not present.
     */
    fun getBinaryPath(): String {
        // First check if it's already on PATH
        val onPath = findOnPath()
        if (onPath != null) return onPath

        // Check our cached binary
        val cached = getCachedBinaryPath()
        if (cached.exists() && cached.canExecute()) return cached.absolutePath

        // Download it
        download()
        return cached.absolutePath
    }

    /**
     * Check if we need to download the binary.
     */
    fun needsDownload(): Boolean {
        return findOnPath() == null && !getCachedBinaryPath().let { it.exists() && it.canExecute() }
    }

    /**
     * Download the binary for the current platform.
     */
    fun download() {
        val platform = detectPlatform()
        val url = DOWNLOAD_URLS[platform]
            ?: throw RuntimeException("Unsupported platform: $platform")

        BINARY_DIR.mkdirs()
        val target = getCachedBinaryPath()

        println("Downloading trace_processor_shell for $platform...")
        val tempFile = File(BINARY_DIR, "trace_processor_shell.tmp")
        try {
            URI(url).toURL().openStream().use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            // Verify SHA-256 if checksum is available
            val expectedHash = EXPECTED_SHA256[platform] ?: ""
            if (expectedHash.isNotEmpty()) {
                val actualHash = sha256(tempFile)
                if (actualHash != expectedHash) {
                    tempFile.delete()
                    throw RuntimeException(
                        "SHA-256 mismatch for $platform binary. " +
                        "Expected: $expectedHash, Got: $actualHash. " +
                        "The download may be corrupted or tampered with."
                    )
                }
            }

            tempFile.renameTo(target)
            target.setExecutable(true)
            println("Downloaded to ${target.absolutePath}")
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }

    private fun getCachedBinaryPath(): File {
        val ext = if (System.getProperty("os.name").lowercase().contains("windows")) ".exe" else ""
        return File(BINARY_DIR, "trace_processor_shell$ext")
    }

    private fun findOnPath(): String? {
        return try {
            val process = ProcessBuilder("which", "trace_processor_shell")
                .redirectErrorStream(true)
                .start()
            val result = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            if (process.exitValue() == 0 && result.isNotEmpty()) result else null
        } catch (_: Exception) {
            null
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun detectPlatform(): String {
        val os = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()

        val osKey = when {
            os.contains("mac") || os.contains("darwin") -> "mac"
            os.contains("linux") -> "linux"
            os.contains("windows") -> "windows"
            else -> throw RuntimeException("Unsupported OS: $os")
        }

        val archKey = when {
            arch.contains("aarch64") || arch.contains("arm64") -> "arm64"
            arch.contains("x86_64") || arch.contains("amd64") -> "amd64"
            else -> "amd64"
        }

        return "$osKey-$archKey"
    }
}
