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
 * Verifies active signing, tamper rejection, key persistence, and historical-key lookup.
 */
class LocalAuditArchiveSigningKeyProviderTest
{
    private fun provider(tempDir: Path, keyId: String = "local-dev-key-1"): LocalAuditArchiveSigningKeyProvider
    {
        val config = mock<AuditArchiveConfigService>()
        whenever(config.getLocalSigningDirectory()).thenReturn(tempDir.resolve("keys").toString())
        whenever(config.getSigningKeyId()).thenReturn(keyId)
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

    @Test
    fun `a rotated provider verifies a signature made by a historical key`(@TempDir tempDir: Path)
    {
        val original = provider(tempDir, "audit-key-1")
        val data = "historical-signature".toByteArray()
        val signature = original.sign(data)

        val rotated = provider(tempDir, "audit-key-2")
        rotated.sign("initialize-active-key".toByteArray())

        assertTrue(rotated.verify(data, signature, "audit-key-1"))
        assertEquals("audit-key-2", rotated.keyId())
    }
}
