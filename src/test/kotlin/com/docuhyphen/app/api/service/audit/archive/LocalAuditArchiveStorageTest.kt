package com.docuhyphen.app.api.service.audit.archive

import com.docuhyphen.app.api.service.config.AuditArchiveConfigService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.file.Files
import java.nio.file.Path

class LocalAuditArchiveStorageTest {
    private fun storage(tempDir: Path): LocalAuditArchiveStorage {
        val config = mock<AuditArchiveConfigService>()
        whenever(config.getLocalDirectory()).thenReturn(tempDir.resolve("archive-root").toString())
        return LocalAuditArchiveStorage(config)
    }

    @Test
    fun `keys containing colons round trip through put, exists, get, and list`(@TempDir tempDir: Path) {
        val storage = storage(tempDir)
        val segmentKey = "archive/platform:2026-07/1-500.jsonl"
        val manifestKey = "archive/platform:2026-07/1-500.manifest.json"

        storage.putObject(segmentKey, "segment".toByteArray())
        storage.putObject(manifestKey, "manifest".toByteArray())

        assertTrue(storage.objectExists(segmentKey))
        assertArrayEquals("segment".toByteArray(), storage.getObject(segmentKey))
        assertEquals(
            setOf(segmentKey, manifestKey),
            storage.listKeysWithPrefix("archive/platform:2026-07/").toSet(),
        )
    }

    @Test
    fun `keys with several colons are stored without reserved file name characters`(@TempDir tempDir: Path) {
        val storage = storage(tempDir)
        val key = "archive/user:subject-1:2026-07/1-500.jsonl"

        storage.putObject(key, "segment".toByteArray())

        val storedNames = Files.walk(tempDir.resolve("archive-root")).use { paths ->
            paths.map { it.fileName.toString() }.toList()
        }
        assertTrue(storedNames.none { it.contains(':') })
        assertEquals(listOf(key), storage.listKeysWithPrefix("archive/user:subject-1:2026-07/"))
    }

    @Test
    fun `a literal percent sequence stays distinct from an encoded reserved character`(@TempDir tempDir: Path) {
        val storage = storage(tempDir)
        val colonKey = "archive/stream:a/1-1.jsonl"
        val percentKey = "archive/stream%3Aa/1-1.jsonl"

        storage.putObject(colonKey, "colon".toByteArray())
        storage.putObject(percentKey, "percent".toByteArray())

        assertArrayEquals("colon".toByteArray(), storage.getObject(colonKey))
        assertArrayEquals("percent".toByteArray(), storage.getObject(percentKey))
        assertEquals(listOf(percentKey), storage.listKeysWithPrefix("archive/stream%3Aa/"))
    }

    @Test
    fun `an existing key is never overwritten`(@TempDir tempDir: Path) {
        val storage = storage(tempDir)
        val key = "archive/platform:2026-07/1-500.jsonl"
        storage.putObject(key, "original".toByteArray())

        assertThrows(AuditArchiveObjectAlreadyExistsException::class.java) {
            storage.putObject(key, "replacement".toByteArray())
        }
        assertArrayEquals("original".toByteArray(), storage.getObject(key))
    }

    @Test
    fun `listing a prefix that was never written returns no keys`(@TempDir tempDir: Path) {
        val storage = storage(tempDir)

        assertFalse(storage.objectExists("archive/platform:2026-08/1-500.jsonl"))
        assertEquals(emptyList<String>(), storage.listKeysWithPrefix("archive/platform:2026-08/"))
    }
}
