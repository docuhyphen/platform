package com.docuhyphen.app.api.service.audit

import com.docuhyphen.app.api.model.entity.AuditIdentityTreatment
import com.docuhyphen.app.api.model.entity.AuditRetentionPolicy
import com.docuhyphen.app.api.repository.audit.AuditRetentionPolicyRepository
import com.docuhyphen.app.api.service.audit.catalog.AuditCategory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

/**
 * Verifies [AuditRetentionPolicyService]: an organization with no override row gets the
 * platform-default catalog unchanged; an override narrows/widens exactly one category; and
 * validation rejects non-positive retention windows.
 */
class AuditRetentionPolicyServiceTest
{
    private fun service(repo: AuditRetentionPolicyRepository = mock()): AuditRetentionPolicyService =
        AuditRetentionPolicyService(repo, AuditRetentionCatalogService(), mock(), mock())

    @Test
    fun `no override returns the platform default`()
    {
        val repo = mock<AuditRetentionPolicyRepository>()
        whenever(repo.findByOrganizationAndCategory(any(), any())).thenReturn(null)

        val spec = service(repo).getEffectivePolicy(UUID.randomUUID(), AuditCategory.EXCHANGE)

        assertFalse(spec.isOverride)
        assertEquals(AuditRetentionCatalogService.DEFAULT_LEDGER_RETENTION_DAYS, spec.ledgerRetentionDays)
        assertEquals(AuditRetentionCatalogService.DEFAULT_ARCHIVE_RETENTION_DAYS, spec.archiveRetentionDays)
    }

    @Test
    fun `override narrows the effective policy for its category only`()
    {
        val orgId = UUID.randomUUID()
        val repo = mock<AuditRetentionPolicyRepository>()
        whenever(repo.save(any())).thenAnswer { it.getArgument(0) }

        val svc = service(repo)
        svc.upsertOverride(orgId, AuditCategory.EXCHANGE, 30, 400, false, AuditIdentityTreatment.MASKED, UUID.randomUUID())

        val saved = AuditRetentionPolicy().apply {
            organizationId = orgId
            category = AuditCategory.EXCHANGE.name
            ledgerRetentionDays = 30
            archiveRetentionDays = 400
            legalHoldEligible = false
            identityTreatment = AuditIdentityTreatment.MASKED
        }
        whenever(repo.findByOrganizationAndCategory(orgId, AuditCategory.EXCHANGE.name)).thenReturn(saved)
        whenever(repo.findByOrganizationAndCategory(orgId, AuditCategory.DOCUMENT.name)).thenReturn(null)

        val overridden = svc.getEffectivePolicy(orgId, AuditCategory.EXCHANGE)
        assertTrue(overridden.isOverride)
        assertEquals(30, overridden.ledgerRetentionDays)
        assertFalse(overridden.legalHoldEligible)

        val untouched = svc.getEffectivePolicy(orgId, AuditCategory.DOCUMENT)
        assertFalse(untouched.isOverride)
    }

    @Test
    fun `non-positive retention days are rejected`()
    {
        val svc = service()
        assertThrows(IllegalArgumentException::class.java) {
            svc.upsertOverride(UUID.randomUUID(), AuditCategory.EXCHANGE, 0, 400, true, AuditIdentityTreatment.READABLE, UUID.randomUUID())
        }
    }

    @Test
    fun `ledger retention expiry is measured against the effective policy`()
    {
        val repo = mock<AuditRetentionPolicyRepository>()
        val orgId = UUID.randomUUID()
        val overridden = AuditRetentionPolicy().apply {
            organizationId = orgId
            category = AuditCategory.EXCHANGE.name
            ledgerRetentionDays = 10
            archiveRetentionDays = 400
        }
        whenever(repo.findByOrganizationAndCategory(orgId, AuditCategory.EXCHANGE.name)).thenReturn(overridden)

        val svc = service(repo)
        val occurredAt = Instant.parse("2026-01-01T00:00:00Z")
        val notYetExpired = Instant.parse("2026-01-05T00:00:00Z")
        val expired = Instant.parse("2026-01-20T00:00:00Z")

        assertFalse(svc.isLedgerRetentionExpired(orgId, AuditCategory.EXCHANGE, occurredAt, notYetExpired))
        assertTrue(svc.isLedgerRetentionExpired(orgId, AuditCategory.EXCHANGE, occurredAt, expired))
    }
}
