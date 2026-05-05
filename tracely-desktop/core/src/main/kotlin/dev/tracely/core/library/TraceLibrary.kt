package dev.tracely.core.library

import dev.tracely.core.model.RepoInfo
import dev.tracely.core.model.TraceInfo
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Scans ~/.tracely/ for repositories and trace files.
 */
object TraceLibrary {

    private val TRACELY_HOME = File(System.getProperty("user.home"), ".tracely")

    /**
     * List all repositories that have traces.
     */
    fun listRepos(): List<RepoInfo> {
        val repos = mutableListOf<RepoInfo>()
        if (!TRACELY_HOME.isDirectory) return repos

        TRACELY_HOME.listFiles()?.sorted()?.forEach { entry ->
            val tracesDir = File(entry, "traces")
            if (tracesDir.isDirectory) {
                val traces = listTraceFiles(tracesDir)
                if (traces.isNotEmpty()) {
                    val totalSize = traces.sumOf { it.sizeBytes }
                    repos.add(RepoInfo(
                        name = entry.name,
                        path = tracesDir.absolutePath,
                        traceCount = traces.size,
                        totalSizeMb = totalSize / (1024.0 * 1024.0),
                        latest = traces.firstOrNull()?.timestamp,
                    ))
                }
            }
        }

        // Fallback: traces directly in ~/.tracely/traces/
        val fallback = File(TRACELY_HOME, "traces")
        if (fallback.isDirectory) {
            val traces = listTraceFiles(fallback)
            if (traces.isNotEmpty()) {
                val totalSize = traces.sumOf { it.sizeBytes }
                repos.add(RepoInfo(
                    name = "(no repo)",
                    path = fallback.absolutePath,
                    traceCount = traces.size,
                    totalSizeMb = totalSize / (1024.0 * 1024.0),
                    latest = traces.firstOrNull()?.timestamp,
                ))
            }
        }

        return repos
    }

    /**
     * List trace files for a given repo name.
     */
    fun listTraces(repoName: String): List<TraceInfo> {
        val dir = if (repoName == "(no repo)") {
            File(TRACELY_HOME, "traces")
        } else {
            File(TRACELY_HOME, "$repoName/traces")
        }
        return if (dir.isDirectory) listTraceFiles(dir) else emptyList()
    }

    private fun listTraceFiles(directory: File): List<TraceInfo> {
        return directory.listFiles()
            ?.filter { it.name.endsWith(".perfetto-trace") }
            ?.map { file ->
                TraceInfo(
                    filename = file.name,
                    path = file.absolutePath,
                    sizeBytes = file.length(),
                    sizeMb = file.length() / (1024.0 * 1024.0),
                    timestamp = parseTimestamp(file.name),
                    packageName = parsePackage(file.name),
                )
            }
            ?.sortedByDescending { it.timestamp ?: "" }
            ?: emptyList()
    }

    private fun parseTimestamp(filename: String): String? {
        // trace_20260505_165012.perfetto-trace
        return try {
            val parts = filename.removeSuffix(".perfetto-trace").split("_")
            if (parts.size >= 3 && parts[0] == "trace") {
                val dt = LocalDateTime.parse(
                    "${parts[1]}${parts[2]}",
                    DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                )
                dt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun parsePackage(filename: String): String? {
        val name = filename.removeSuffix(".perfetto-trace")
        val parts = name.split("_")
        return if (parts.size >= 4 && parts[0] == "trace") {
            parts.drop(3).joinToString("_")
        } else null
    }
}
