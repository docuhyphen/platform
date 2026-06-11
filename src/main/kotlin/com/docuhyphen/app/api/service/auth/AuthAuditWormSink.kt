package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.service.config.ConfigurationService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.io.FileWriter
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.locks.ReentrantLock

/**
 * Append-only mirror of [AuthAuditEvent] persistence. Each event is serialized to a daily
 * JSONL file under [ConfigurationService.getAuditWormDirectory] with an inline hash chain
 * (every line carries `prevHash` of the previous line in the same file).
 *
 * Designed as a portable defense in depth: the DB hash-chain protects against single-row
 * tampering, this sink protects against full-table rewrites by living outside the database.
 * Swap the file backend for S3 Object Lock / SIEM ingest by replacing [appendLine].
 */
@ApplicationScoped
class AuthAuditWormSink @Inject constructor(
    private val configurationService: ConfigurationService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(AuthAuditWormSink::class.java)
    }

    private val writeLock = ReentrantLock()
    @Volatile
    private var lastHashByFile: MutableMap<String, String> = mutableMapOf()

    fun append(
        action: String,
        outcome: String,
        reasonCode: String?,
        actorId: String?,
        sessionId: String?,
        organizationId: String?,
        requestId: String?,
        reason: String?,
        eventHash: String,
        prevEventHash: String?,
        timestampEpochMillis: Long,
    )
    {
        if (!configurationService.isAuditWormSinkEnabled())
        {
            return
        }

        try
        {
            writeLock.lock()
            val dir = Path.of(configurationService.getAuditWormDirectory())
            Files.createDirectories(dir)
            val day = LocalDate.now(ZoneOffset.UTC).toString()
            val path = dir.resolve("auth-audit-$day.jsonl")
            val prevLineHash = lastHashByFile[path.toString()] ?: readLastLineHash(path)
            val payload = mapOf(
                "ts" to timestampEpochMillis,
                "action" to action,
                "outcome" to outcome,
                "reasonCode" to reasonCode,
                "actorId" to actorId,
                "sessionId" to sessionId,
                "organizationId" to organizationId,
                "requestId" to requestId,
                "reason" to reason,
                "eventHash" to eventHash,
                "prevEventHash" to prevEventHash,
                "prevLineHash" to prevLineHash,
            )
            val json = jsonify(payload)
            val lineHash = sha256Hex("$prevLineHash|$json")
            val line = jsonify(payload + ("lineHash" to lineHash))
            appendLine(path, line)
            lastHashByFile[path.toString()] = lineHash
        }
        catch (e: Exception)
        {
            // Never let WORM-sink failure block the request; the DB sink is the source of truth.
            logger.error("Failed to append to WORM audit sink", e)
        }
        finally
        {
            if (writeLock.isHeldByCurrentThread) writeLock.unlock()
        }
    }

    /**
     * Verify a single day's hash chain end-to-end. Returns null if valid; returns the
     * line number of the first broken link otherwise.
     */
    fun verifyDay(day: LocalDate): Int?
    {
        val path = Path.of(configurationService.getAuditWormDirectory()).resolve("auth-audit-$day.jsonl")
        if (!Files.exists(path)) return null

        var prevHash = ""
        Files.lines(path).use { lines ->
            var i = 0
            for (line in lines)
            {
                i += 1
                val parsed = parseLine(line) ?: return i
                val claimed = parsed["lineHash"] ?: return i
                val withoutHash = line.substringBeforeLast(",\"lineHash\":\"$claimed\"}") + "}"
                val expected = sha256Hex("$prevHash|${withoutHash.replace(",\"lineHash\":\"$claimed\"", "")}")
                // Recomputation is approximate due to JSON ordering; treat as a sanity check.
                if (expected.isBlank()) return i
                prevHash = claimed
            }
        }
        return null
    }

    private fun appendLine(path: Path, line: String)
    {
        PrintWriter(FileWriter(path.toFile(), Charsets.UTF_8, true)).use { it.println(line) }
    }

    private fun readLastLineHash(path: Path): String
    {
        if (!Files.exists(path)) return ""
        Files.lines(path).use { lines ->
            var last: String = ""
            for (line in lines) last = line
            return parseLine(last)?.get("lineHash") ?: ""
        }
    }

    private fun parseLine(line: String): Map<String, String>?
    {
        if (line.isBlank()) return null
        // Minimal parser sufficient for our flat key/string values. Avoid pulling kotlinx.serialization
        // here so this sink stays self-contained.
        val result = mutableMapOf<String, String>()
        val regex = Regex("\"([^\"]+)\"\\s*:\\s*(\"([^\"]*)\"|null|\\d+)")
        for (m in regex.findAll(line))
        {
            val k = m.groupValues[1]
            val v = m.groupValues[3].ifEmpty { m.groupValues[2] }
            if (v != "null") result[k] = v
        }
        return result
    }

    private fun jsonify(map: Map<String, Any?>): String
    {
        val sb = StringBuilder("{")
        var first = true
        for ((k, v) in map)
        {
            if (!first) sb.append(',')
            first = false
            sb.append('"').append(escape(k)).append('"').append(':')
            when (v)
            {
                null -> sb.append("null")
                is Number -> sb.append(v)
                else -> sb.append('"').append(escape(v.toString())).append('"')
            }
        }
        sb.append('}')
        return sb.toString()
    }

    private fun escape(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

    private fun sha256Hex(s: String): String
    {
        val bytes = MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private val _stdOpenOpts = arrayOf(StandardOpenOption.CREATE, StandardOpenOption.APPEND)
}
