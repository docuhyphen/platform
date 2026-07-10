package com.docuhyphen.app.api.service.audit

import com.docuhyphen.app.api.repository.AuditAnalyticsFactRepository
import com.docuhyphen.app.api.repository.AuditLedgerEventRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * Phase 8 gate: reconciliation reports a match when the ledger and analytics-fact counts agree
 * for the requested scope, and reports the discrepancy (with a bounded sample of missing ids)
 * when they do not.
 */
class AuditAnalyticsReconciliationServiceTest
{
    @Test
    fun `matching counts report matches`()
    {
        val ledgerRepo = mock<AuditLedgerEventRepository>()
        val factRepo = mock<AuditAnalyticsFactRepository>()
        val orgId = UUID.randomUUID()
        whenever(ledgerRepo.countByOrganization(orgId, false)).thenReturn(5L)
        whenever(factRepo.countByOrganization(orgId, false)).thenReturn(5L)

        val report = AuditAnalyticsReconciliationService(ledgerRepo, factRepo, mock())
            .reconcile(orgId, platformOnly = false, requestedByUserId = UUID.randomUUID())

        assertTrue(report.matches)
        assertEquals(0, report.missingLedgerEventIds.size)
    }

    @Test
    fun `a fact deficit is reported with a sample of missing ledger event ids`()
    {
        val ledgerRepo = mock<AuditLedgerEventRepository>()
        val factRepo = mock<AuditAnalyticsFactRepository>()
        val orgId = UUID.randomUUID()
        val missing = listOf(UUID.randomUUID(), UUID.randomUUID())
        whenever(ledgerRepo.countByOrganization(orgId, false)).thenReturn(5L)
        whenever(factRepo.countByOrganization(orgId, false)).thenReturn(3L)
        whenever(factRepo.findMissingLedgerEventIds(any(), any(), any())).thenReturn(missing)

        val report = AuditAnalyticsReconciliationService(ledgerRepo, factRepo, mock())
            .reconcile(orgId, platformOnly = false, requestedByUserId = null)

        assertFalse(report.matches)
        assertEquals(missing, report.missingLedgerEventIds)
    }
}
