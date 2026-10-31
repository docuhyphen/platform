package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.repository.audit.AuditLedgerEventRepository
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.service.audit.*
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.sql.Timestamp
import java.util.*

class ExchangeDocumentAuditServiceTest
{
    @Test
    fun `a document without an Exchange still resolves its true audit owner`() {
        val document = Document().apply { title = "Recorded evidence.txt" }
        val user = UUID.randomUUID()
        val resolver = mock<AuditOwnerScopeResolver>()
        whenever(resolver.resolve(ResourceType.DOCUMENT, document.id)).thenReturn(AuditOwnerScope.Personal(user))
        val recorder = mock<AuditRecorder>()
        service(auditRecorder = recorder, auditOwnerScopeResolver = resolver)
            .logAction(document, DocumentAuditAction.VIEW, "recipient@process.test")
        val drafts = argumentCaptor<AuditEventDraft>()
        verify(recorder).record(drafts.capture())
        assertEquals(AuditOwnerScope.Personal(user), drafts.firstValue.owner)
    }

    private fun service(
        auditRecorder: AuditRecorder = mock(),
        auditLedgerEventRepository: AuditLedgerEventRepository = mock(),
        exchangeRepository: ExchangeRepository = mock(),
        auditOwnerScopeResolver: AuditOwnerScopeResolver = mock<AuditOwnerScopeResolver>().also {
            whenever(it.resolve(any(), any())).thenReturn(AuditOwnerScope.Platform)
        },
    ): ExchangeDocumentAuditService
    {
        return ExchangeDocumentAuditService(
            exchangeRepository,
            auditRecorder,
            auditOwnerScopeResolver,
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
        val exchange = Exchange().apply { ownerOrganizationId = organizationId }
        val document = Document().apply { title = "Contract.pdf" }
        val exchangeRepository = mock<ExchangeRepository>()
        whenever(exchangeRepository.findByDocumentId(document.id)).thenReturn(exchange)
        val auditOwnerScopeResolver = mock<AuditOwnerScopeResolver>()
        whenever(auditOwnerScopeResolver.resolve(ResourceType.EXCHANGE, exchange.id))
            .thenReturn(AuditOwnerScope.Organization(organizationId))
        val auditRecorder = mock<AuditRecorder>()
        whenever(auditRecorder.record(any())).thenReturn(AuditCaptureResult.Captured(UUID.randomUUID(), UUID.randomUUID()))

        service(
            auditRecorder = auditRecorder,
            exchangeRepository = exchangeRepository,
            auditOwnerScopeResolver = auditOwnerScopeResolver,
        ).logAction(document, DocumentAuditAction.VIEW, "recipient@example.com")

        val captor = argumentCaptor<AuditEventDraft>()
        verify(auditRecorder).record(captor.capture())
        assertEquals(AuditOwnerScope.Organization(organizationId), captor.firstValue.owner)
    }

    @Test
    fun `logAction resolves a personally owned Exchange to a Personal audit owner, never Platform`()
    {
        val ownerUserId = UUID.randomUUID()
        val exchange = Exchange()
        val document = Document().apply { title = "Contract.pdf" }
        val exchangeRepository = mock<ExchangeRepository>()
        whenever(exchangeRepository.findByDocumentId(document.id)).thenReturn(exchange)
        val auditOwnerScopeResolver = mock<AuditOwnerScopeResolver>()
        whenever(auditOwnerScopeResolver.resolve(ResourceType.EXCHANGE, exchange.id))
            .thenReturn(AuditOwnerScope.Personal(ownerUserId))
        val auditRecorder = mock<AuditRecorder>()
        whenever(auditRecorder.record(any())).thenReturn(
            AuditCaptureResult.Captured(
                UUID.randomUUID(),
                UUID.randomUUID()
            )
        )

        service(
            auditRecorder = auditRecorder,
            exchangeRepository = exchangeRepository,
            auditOwnerScopeResolver = auditOwnerScopeResolver,
        ).logAction(document, DocumentAuditAction.VIEW, "recipient@example.com")

        val captor = argumentCaptor<AuditEventDraft>()
        verify(auditRecorder).record(captor.capture())
        assertEquals(AuditOwnerScope.Personal(ownerUserId), captor.firstValue.owner)
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
