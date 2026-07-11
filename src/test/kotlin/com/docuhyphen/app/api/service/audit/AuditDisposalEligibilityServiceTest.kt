package com.docuhyphen.app.api.service.audit

import com.docuhyphen.app.api.service.audit.catalog.AuditCategory
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

/** Verifies that legal hold always overrides retention-driven disposal eligibility. */
class AuditDisposalEligibilityServiceTest
{
    @Test
    fun `retention-expired but held resource is not eligible for disposal`()
    {
        val retentionService = mock<AuditRetentionPolicyService>()
        val holdService = mock<AuditLegalHoldService>()
        whenever(retentionService.isLedgerRetentionExpired(any(), any(), any(), any())).thenReturn(true)
        whenever(holdService.isUnderHold(any(), any(), any())).thenReturn(true)

        val svc = AuditDisposalEligibilityService(retentionService, holdService)
        val eligible = svc.isEligibleForDisposal(UUID.randomUUID(), AuditCategory.EXCHANGE, Instant.now(), "EXCHANGE", "abc")

        assertFalse(eligible)
    }

    @Test
    fun `retention-expired and not held resource is eligible for disposal`()
    {
        val retentionService = mock<AuditRetentionPolicyService>()
        val holdService = mock<AuditLegalHoldService>()
        whenever(retentionService.isLedgerRetentionExpired(any(), any(), any(), any())).thenReturn(true)
        whenever(holdService.isUnderHold(any(), any(), any())).thenReturn(false)

        val svc = AuditDisposalEligibilityService(retentionService, holdService)
        val eligible = svc.isEligibleForDisposal(UUID.randomUUID(), AuditCategory.EXCHANGE, Instant.now(), "EXCHANGE", "abc")

        assertTrue(eligible)
    }

    @Test
    fun `retention not yet expired is never eligible regardless of hold`()
    {
        val retentionService = mock<AuditRetentionPolicyService>()
        val holdService = mock<AuditLegalHoldService>()
        whenever(retentionService.isLedgerRetentionExpired(any(), any(), any(), any())).thenReturn(false)

        val svc = AuditDisposalEligibilityService(retentionService, holdService)
        val eligible = svc.isEligibleForDisposal(UUID.randomUUID(), AuditCategory.EXCHANGE, Instant.now())

        assertFalse(eligible)
    }
}
