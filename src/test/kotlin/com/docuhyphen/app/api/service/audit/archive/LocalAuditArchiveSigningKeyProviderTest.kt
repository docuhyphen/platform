package com.docuhyphen.app.api.service.audit.archive

import com.docuhyphen.app.api.service.config.AuditArchiveConfigService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.file.Path

/**
 * Phase 4 gate for [LocalAuditArchiveSigningKeyProvider]: signs are verifiable with the same
 * provider, a tampered payload fails verification, and the key persists across a fresh instance
 * pointed at the same directory (bootstrap-once semantics, not a new key every restart).
 */
class LocalAuditArchiveSigningKeyProviderTest
{
    private fun provider(tempDir: Path): LocalAuditArchiveSigningKeyProvider
    {
        val config = mock<AuditArchiveConfigService>()
        whenever(config.getLocalSigningDirectory()).thenReturn(tempDir.resolve("keys").toString())
        return LocalAuditArchiveSigningKeyProvider(config)
    }

    @Test
    fun `a signature verifies against the same data and key id`(@TempDir tempDir: Path)
    {
        val provider = provider(tempDir)
        val data = "hello world".toByteArray()
        val signature = provider.sign(data)

        assertTrue(provider.verify(data, signature, provider.keyId()))
    }

    @Test
    fun `verification fails for tampered data`(@TempDir tempDir: Path)
    {
        val provider = provider(tempDir)
        val signature = provider.sign("original".toByteArray())

        assertFalse(provider.verify("tampered".toByteArray(), signature, provider.keyId()))
    }

    @Test
    fun `verification fails for an unknown key id`(@TempDir tempDir: Path)
    {
        val provider = provider(tempDir)
        val data = "hello".toByteArray()
        val signature = provider.sign(data)

        assertFalse(provider.verify(data, signature, "some-other-key"))
    }

    @Test
    fun `the key pair persists across a fresh provider instance pointed at the same directory`(@TempDir tempDir: Path)
    {
        val first = provider(tempDir)
        val data = "persisted-key-check".toByteArray()
        val signature = first.sign(data)

        val second = provider(tempDir)
        assertTrue(second.verify(data, signature, second.keyId()))
        assertEquals(first.activePublicKeyPem(), second.activePublicKeyPem())
    }
}
