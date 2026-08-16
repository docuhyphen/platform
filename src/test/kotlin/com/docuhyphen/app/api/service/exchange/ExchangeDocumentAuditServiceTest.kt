package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.AuditLedgerEvent
import com.docuhyphen.app.api.model.entity.Document
import com.docuhyphen.app.api.model.entity.DocumentAuditAction
import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.repository.audit.AuditLedgerEventRepository
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.service.audit.AuditCaptureResult
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditOwnerScope
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.util.UUID

/**
 * [ExchangeDocumentAuditService.logAction] writes onto [AuditRecorder], and
 * [ExchangeDocumentAuditService.getExchangeAuditEvents] backs the Exchange audit tab with one
 * ledger query across every document.
 */
class ExchangeDocumentAuditServiceTest
{
    private fun service(
        auditRecorder: AuditRecorder = mock(),
        auditLedgerEventRepository: AuditLedgerEventRepository = mock(),
        exchangeRepository: ExchangeRepository = mock(),
    ): ExchangeDocumentAuditService
    {
        return ExchangeDocumentAuditService(
            exchangeRepository,
            auditRecorder,
            auditLedgerEventRepository,
        )
    }

    @Test
    fun `logAction writes onto AuditRecorder with the mapped event type`()
    {
        val auditRecorder = mock<AuditRecorder>()
        whenever(auditRecorder.record(any())).thenReturn(AuditCaptureResult.Captured(UUID.randomUUID(), UUID.randomUUID()))
        val service = service(auditRecorder = auditRecorder)

        val document = Document().apply { title = "Contract.pdf" }
        service.logAction(document, DocumentAuditAction.UPLOAD, "recipient@example.com")

        val captor = argumentCaptor<AuditEventDraft>()
        verify(auditRecorder).record(captor.capture())
        assertEquals(AuditEventType.DOCUMENT_UPLOAD.key, captor.firstValue.eventTypeKey)
        assertEquals(document.id.toString(), captor.firstValue.targetId)
        assertEquals("Contract.pdf", captor.firstValue.payload["document_title"])
    }

    @Test
    fun `logAction uses the owning Exchange organization without request organization context`()
    {
        val organizationId = UUID.randomUUID()
        val document = Document().apply { title = "Contract.pdf" }
        val exchangeRepository = mock<ExchangeRepository>()
        whenever(exchangeRepository.findByDocumentId(document.id)).thenReturn(
            Exchange().apply { ownerOrganizationId = organizationId }
        )
        val auditRecorder = mock<AuditRecorder>()
        whenever(auditRecorder.record(any())).thenReturn(AuditCaptureResult.Captured(UUID.randomUUID(), UUID.randomUUID()))

        service(auditRecorder = auditRecorder, exchangeRepository = exchangeRepository)
            .logAction(document, DocumentAuditAction.VIEW, "recipient@example.com")

        val captor = argumentCaptor<AuditEventDraft>()
        verify(auditRecorder).record(captor.capture())
        assertEquals(AuditOwnerScope.Organization(organizationId), captor.firstValue.owner)
    }

    @Test
    fun `getExchangeAuditEvents queries the ledger once across every document in the Exchange`()
    {
        val document1 = Document().apply { title = "One.pdf" }
        val document2 = Document().apply { title = "Two.pdf" }
        val exchange = Exchange().apply { documents = mutableListOf(document1, document2) }

        val exchangeRepository = mock<ExchangeRepository>()
        whenever(exchangeRepository.findById(any())).thenReturn(exchange)

        val ledgerEvent = AuditLedgerEvent().apply {
            eventTypeKey = AuditEventType.DOCUMENT_UPLOAD.key
            targetType = "Document"
            targetId = document1.id.toString()
            occurredAt = Timestamp(System.currentTimeMillis())
            payloadJson = """{"actor_email":"a@b.com","document_title":"One.pdf"}"""
        }

        val auditLedgerEventRepository = mock<AuditLedgerEventRepository>()
        whenever(auditLedgerEventRepository.findByTargetTypeAndTargetIds(eq("Document"), any()))
            .thenReturn(listOf(ledgerEvent))

        val service = service(
            auditLedgerEventRepository = auditLedgerEventRepository,
            exchangeRepository = exchangeRepository,
        )

        val results = service.getExchangeAuditEvents(UUID.randomUUID().toString())

        assertEquals(1, results.size)
        assertEquals("One.pdf", results.first().documentTitle)
        assertEquals("a@b.com", results.first().performedByEmail)
        assertEquals("UPLOAD", results.first().action)
        verify(auditLedgerEventRepository).findByTargetTypeAndTargetIds(eq("Document"), any())
    }

    private fun <T> eq(value: T): T = org.mockito.kotlin.eq(value)
}
