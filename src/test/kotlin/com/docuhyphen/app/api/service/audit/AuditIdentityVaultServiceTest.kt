package com.docuhyphen.app.api.service.audit

import com.docuhyphen.app.api.model.entity.AuditIdentityVaultKey
import com.docuhyphen.app.api.repository.AuditIdentityVaultKeyRepository
import com.docuhyphen.app.api.service.audit.identity.AuditIdentityVaultMasterKeyProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.SecureRandom

/**
 * Verifies the crypto-shredding primitive: a subject's data key round-trips through
 * wrap/unwrap, and [AuditIdentityVaultService.shred] permanently makes it unrecoverable (twice,
 * idempotently) without deleting the row itself.
 */
class AuditIdentityVaultServiceTest
{
    private fun fixedMasterKeyProvider(): AuditIdentityVaultMasterKeyProvider
    {
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val provider = mock<AuditIdentityVaultMasterKeyProvider>()
        whenever(provider.keyId()).thenReturn("test-key")
        whenever(provider.keyBytes()).thenReturn(key)
        return provider
    }

    private class InMemoryVaultBacking
    {
        var stored: AuditIdentityVaultKey? = null
    }

    private fun repository(backing: InMemoryVaultBacking): AuditIdentityVaultKeyRepository
    {
        val repo = mock<AuditIdentityVaultKeyRepository>()
        whenever(repo.findBySubject(any(), any())).thenAnswer { backing.stored }
        whenever(repo.save(any())).thenAnswer {
            val entry = it.getArgument<AuditIdentityVaultKey>(0)
            backing.stored = entry
            entry
        }
        whenever(repo.update(any())).thenAnswer {
            val entry = it.getArgument<AuditIdentityVaultKey>(0)
            backing.stored = entry
            entry
        }
        return repo
    }

    @Test
    fun `a subject's data key round-trips through wrap and unwrap`()
    {
        val backing = InMemoryVaultBacking()
        val svc = AuditIdentityVaultService(repository(backing), fixedMasterKeyProvider(), mock())

        svc.ensureKey("USER", "subject-1")
        val dataKey = svc.unwrapDataKey("USER", "subject-1")

        assertNotNull(dataKey)
        assertEquals(32, dataKey!!.size)
    }

    @Test
    fun `ensureKey is idempotent for the same subject`()
    {
        val backing = InMemoryVaultBacking()
        val svc = AuditIdentityVaultService(repository(backing), fixedMasterKeyProvider(), mock())

        val firstId = svc.ensureKey("USER", "subject-1")
        val secondId = svc.ensureKey("USER", "subject-1")

        assertEquals(firstId, secondId)
    }

    @Test
    fun `shredding a subject makes its data key permanently unrecoverable`()
    {
        val backing = InMemoryVaultBacking()
        val svc = AuditIdentityVaultService(repository(backing), fixedMasterKeyProvider(), mock())

        svc.ensureKey("USER", "subject-1")
        assertNotNull(svc.unwrapDataKey("USER", "subject-1"))

        assertTrue(svc.shred("USER", "subject-1", null))
        assertNull(svc.unwrapDataKey("USER", "subject-1"))
        assertTrue(svc.isShredded("USER", "subject-1"))

        // Shredding again is a safe no-op, not an error.
        assertTrue(svc.shred("USER", "subject-1", null))
    }
}
