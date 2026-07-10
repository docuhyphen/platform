package com.docuhyphen.app.api.service.audit

import com.docuhyphen.app.api.model.entity.AuditLedgerEvent
import com.docuhyphen.app.api.repository.AuditLedgerEventRepository
import com.docuhyphen.app.api.service.audit.catalog.AuditCategory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.Capability
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.exchange.ExchangeRetrievalService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class AuditSearchProjectionServiceTest
{
    private val ledgerRepository: AuditLedgerEventRepository = mock()
    private val engagementService: AuditEngagementService = mock()
    private val exchangeRetrievalService: ExchangeRetrievalService = mock()
    private val auditRecorder: AuditRecorder = mock()

    private fun service(): AuditSearchProjectionService = AuditSearchProjectionService(
        auditLedgerEventRepository = ledgerRepository,
        auditEngagementService = engagementService,
        exchangeRetrievalService = exchangeRetrievalService,
        auditRecorder = auditRecorder,
    )

    private fun actor(vararg capabilities: Capability): AuditSearchProjectionService.AuditAccessActor =
        AuditSearchProjectionService.AuditAccessActor(
            principal = PrincipalRef.user(UUID.randomUUID()),
            context = AuthorizationContext(mfaSatisfied = true),
            capabilities = capabilities.toSet(),
        )

    private fun organizationEvent(
        organizationId: UUID,
        targetType: String = "DOCUMENT",
        targetId: String = UUID.randomUUID().toString(),
    ): AuditLedgerEvent = AuditLedgerEvent().apply {
        eventId = UUID.randomUUID()
        category = AuditCategory.DOCUMENT.name
        eventTypeKey = "document.download"
        outcome = "SUCCESS"
        occurredAt = Timestamp.from(Instant.parse("2026-07-01T10:15:30Z"))
        recordedAt = Timestamp.from(Instant.parse("2026-07-01T10:15:31Z"))
        ledgerTime = Timestamp.from(Instant.parse("2026-07-01T10:15:32Z"))
        streamId = "org:$organizationId"
        streamSequence = 44L
        actorKind = "HUMAN"
        this.organizationId = organizationId
        this.targetType = targetType
        this.targetId = targetId
        payloadJson = """{"document_title":"Quarterly.pdf","actor_email":"auditor@example.com"}"""
        eventHash = "hash"
    }

    @Test
    fun `platform auditor cannot read customer content without a separate engagement grant`()
    {
        val organizationId = UUID.randomUUID()
        val actor = actor(Capability.APP_AUDIT_READ, Capability.ORG_AUDIT_READ)
        val event = organizationEvent(organizationId)
        whenever(ledgerRepository.search(eq(organizationId), eq(false), any(), any(), any(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(50)))
            .thenReturn(listOf(event))
        whenever(auditRecorder.record(any())).thenReturn(AuditCaptureResult.Captured(UUID.randomUUID(), UUID.randomUUID()))
        whenever(
            engagementService.resolveAccess(
                principalUserId = eq(actor.principal.id),
                organizationId = eq(organizationId),
                resourceType = eq(event.targetType),
                resourceId = eq(event.targetId),
                category = eq(AuditCategory.DOCUMENT),
                requireSensitive = eq(false),
                at = any(),
                recentStepUpSatisfied = eq(true),
                requestedRange = isNull(),
            )
        ).thenReturn(null)

        val page = service().listOrganizationEvents(actor, organizationId, emptySet(), null, 50)

        assertTrue(page.items.isEmpty())
        val drafts = argumentCaptor<AuditEventDraft>()
        verify(auditRecorder).record(drafts.capture())
        assertEquals("audit.search.performed", drafts.firstValue.eventTypeKey)
    }

    @Test
    fun `detail denial records an audit access denied event`()
    {
        val organizationId = UUID.randomUUID()
        val actor = actor(Capability.ORG_AUDIT_READ)
        val event = organizationEvent(organizationId)
        whenever(ledgerRepository.findByEventIdScoped(event.eventId, organizationId, false)).thenReturn(event)
        whenever(auditRecorder.record(any())).thenReturn(AuditCaptureResult.Captured(UUID.randomUUID(), UUID.randomUUID()))
        whenever(
            engagementService.resolveAccess(
                principalUserId = eq(actor.principal.id),
                organizationId = eq(organizationId),
                resourceType = eq(event.targetType),
                resourceId = eq(event.targetId),
                category = eq(AuditCategory.DOCUMENT),
                requireSensitive = eq(false),
                at = any(),
                recentStepUpSatisfied = eq(true),
                requestedRange = isNull(),
            )
        ).thenReturn(null)

        assertThrows(AuditProjectionAccessDeniedException::class.java) {
            service().getOrganizationEvent(actor, organizationId, event.eventId)
        }

        val drafts = argumentCaptor<AuditEventDraft>()
        verify(auditRecorder).record(drafts.capture())
        assertEquals("audit.access.denied", drafts.firstValue.eventTypeKey)
    }

    @Test
    fun `contextual document search enforces exact resource scope and filter`()
    {
        val organizationId = UUID.randomUUID()
        val exchangeId = UUID.randomUUID()
        val documentId = UUID.randomUUID()
        val actor = actor(Capability.ORG_POLICY_MANAGE, Capability.ORG_AUDIT_READ, Capability.ORG_AUDIT_VIEW_SENSITIVE)
        val event = organizationEvent(organizationId, targetType = "DOCUMENT", targetId = documentId.toString())
        whenever(exchangeRetrievalService.hasDocumentInExchange(exchangeId, documentId)).thenReturn(true)
        whenever(auditRecorder.record(any())).thenReturn(AuditCaptureResult.Captured(UUID.randomUUID(), UUID.randomUUID()))
        whenever(ledgerRepository.search(eq(organizationId), eq(false), any(), eq(setOf("DOCUMENT", "Document")), eq(setOf(documentId.toString())), isNull(), isNull(), isNull(), isNull(), isNull(), eq(25)))
            .thenReturn(listOf(event))

        val page = service().listExchangeDocumentEvents(actor, organizationId, exchangeId, documentId, null, 25)

        assertEquals(1, page.items.size)
        assertEquals(documentId.toString(), page.items.first().targetId)
        verify(exchangeRetrievalService).hasDocumentInExchange(exchangeId, documentId)
        verify(ledgerRepository).search(
            eq(organizationId),
            eq(false),
            any(),
            eq(setOf("DOCUMENT", "Document")),
            eq(setOf(documentId.toString())),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            eq(25),
        )
    }

    @Test
    fun `exchange-wide search widens the filter to every document in the exchange plus the exchange target`()
    {
        val organizationId = UUID.randomUUID()
        val exchangeId = UUID.randomUUID()
        val documentId = UUID.randomUUID()
        val actor = actor(Capability.ORG_POLICY_MANAGE, Capability.ORG_AUDIT_READ, Capability.ORG_AUDIT_VIEW_SENSITIVE)
        val event = organizationEvent(organizationId, targetType = "DOCUMENT", targetId = documentId.toString())
        val expectedTargetIds = setOf(documentId.toString(), exchangeId.toString())
        whenever(exchangeRetrievalService.getDocumentIdsForExchange(exchangeId)).thenReturn(listOf(documentId))
        whenever(auditRecorder.record(any())).thenReturn(AuditCaptureResult.Captured(UUID.randomUUID(), UUID.randomUUID()))
        whenever(
            ledgerRepository.search(
                eq(organizationId),
                eq(false),
                any(),
                eq(setOf("DOCUMENT", "Document", "EXCHANGE", "Exchange")),
                eq(expectedTargetIds),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                eq(25),
            )
        ).thenReturn(listOf(event))

        val page = service().listExchangeEvents(actor, organizationId, exchangeId, null, 25)

        assertEquals(1, page.items.size)
        assertEquals(documentId.toString(), page.items.first().targetId)
        verify(exchangeRetrievalService).getDocumentIdsForExchange(exchangeId)
        verify(ledgerRepository).search(
            eq(organizationId),
            eq(false),
            any(),
            eq(setOf("DOCUMENT", "Document", "EXCHANGE", "Exchange")),
            eq(expectedTargetIds),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            eq(25),
        )
    }
}
