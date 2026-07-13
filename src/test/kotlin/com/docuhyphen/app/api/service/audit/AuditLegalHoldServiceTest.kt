package com.docuhyphen.app.api.service.audit

import com.docuhyphen.app.api.model.entity.AuditLegalHold
import com.docuhyphen.app.api.model.entity.AuditLegalHoldStatus
import com.docuhyphen.app.api.repository.AuditLegalHoldRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * Verifies that placing a hold makes [AuditLegalHoldService.isUnderHold] true for that resource;
 * releasing it makes it false again; only an ACTIVE hold can be released.
 */
class AuditLegalHoldServiceTest
{
    private fun service(repo: AuditLegalHoldRepository = mock()): AuditLegalHoldService =
        AuditLegalHoldService(repo, mock(), mock())

    @Test
    fun `placing a hold marks the resource as under hold`()
    {
        val repo = mock<AuditLegalHoldRepository>()
        whenever(repo.save(any())).thenAnswer { it.getArgument(0) }
        whenever(repo.findActiveForResource(any(), any(), any())).thenReturn(
            listOf(AuditLegalHold().apply { resourceType = "EXCHANGE"; resourceId = "abc" })
        )

        val svc = service(repo)
        val orgId = UUID.randomUUID()
        val hold = svc.placeHold(orgId, "EXCHANGE", "abc", "litigation hold", null, UUID.randomUUID())

        assertEquals(AuditLegalHoldStatus.ACTIVE, hold.status)
        assertTrue(svc.isUnderHold(orgId, "EXCHANGE", "abc"))
    }

    @Test
    fun `releasing a hold clears isUnderHold`()
    {
        val repo = mock<AuditLegalHoldRepository>()
        val hold = AuditLegalHold().apply {
            id = UUID.randomUUID()
            resourceType = "EXCHANGE"
            resourceId = "abc"
            status = AuditLegalHoldStatus.ACTIVE
        }
        whenever(repo.findById(hold.id)).thenReturn(hold)
        whenever(repo.update(any())).thenAnswer { it.getArgument(0) }
        whenever(repo.findActiveForResource(any(), any(), any())).thenReturn(emptyList())

        val svc = service(repo)
        val released = svc.releaseHold(hold.id, null, UUID.randomUUID())

        assertEquals(AuditLegalHoldStatus.RELEASED, released.status)
        assertFalse(svc.isUnderHold(null, "EXCHANGE", "abc"))
    }

    @Test
    fun `a non-active hold cannot be released again`()
    {
        val repo = mock<AuditLegalHoldRepository>()
        val hold = AuditLegalHold().apply {
            id = UUID.randomUUID()
            status = AuditLegalHoldStatus.RELEASED
        }
        whenever(repo.findById(hold.id)).thenReturn(hold)

        val svc = service(repo)
        assertThrows(IllegalArgumentException::class.java) { svc.releaseHold(hold.id, null, UUID.randomUUID()) }
    }

    @Test
    fun `a hold outside the expected organization cannot be released or mutated`()
    {
        val repo = mock<AuditLegalHoldRepository>()
        val hold = AuditLegalHold().apply {
            id = UUID.randomUUID()
            organizationId = UUID.randomUUID()
            status = AuditLegalHoldStatus.ACTIVE
        }
        whenever(repo.findById(hold.id)).thenReturn(hold)

        val originalStatus = hold.status
        assertThrows(IllegalArgumentException::class.java) {
            service(repo).releaseHold(hold.id, UUID.randomUUID(), UUID.randomUUID())
        }

        assertEquals(originalStatus, hold.status)
        verify(repo, never()).update(any())
    }
}
