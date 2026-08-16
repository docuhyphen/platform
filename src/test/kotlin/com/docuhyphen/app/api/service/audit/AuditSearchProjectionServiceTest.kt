package com.docuhyphen.app.api.service.audit

import com.docuhyphen.app.api.model.entity.AuditLedgerEvent
import com.docuhyphen.app.api.repository.audit.AuditLedgerEventRepository
import com.docuhyphen.app.api.service.audit.catalog.AuditCategory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.Capability
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.exchange.ExchangeRetrievalService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Instant
import java.time.Duration
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
    fun `engagement constrained search rejects a partial date range before querying evidence`()
    {
        val organizationId = UUID.randomUUID()
        val actor = actor(Capability.APP_AUDIT_READ, Capability.ORG_AUDIT_READ)

        assertThrows(IllegalArgumentException::class.java) {
            service().listOrganizationEvents(
                actor = actor,
                organizationId = organizationId,
                categories = emptySet(),
                cursor = null,
                limit = 50,
                occurredAfter = Instant.parse("2026-07-01T00:00:00Z"),
                occurredBefore = null,
            )
        }

        verify(ledgerRepository, never()).search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `engagement constrained search rejects a reversed date range before querying evidence`()
    {
        val organizationId = UUID.randomUUID()
        val actor = actor(Capability.APP_AUDIT_READ, Capability.ORG_AUDIT_READ)

        assertThrows(IllegalArgumentException::class.java) {
            service().listOrganizationEvents(
                actor = actor,
                organizationId = organizationId,
                categories = emptySet(),
                cursor = null,
                limit = 50,
                occurredAfter = Instant.parse("2026-07-02T00:00:00Z"),
                occurredBefore = Instant.parse("2026-07-01T00:00:00Z"),
            )
        }

        verify(ledgerRepository, never()).search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `engagement constrained search rejects a zero-length date range before querying evidence`()
    {
        val organizationId = UUID.randomUUID()
        val actor = actor(Capability.APP_AUDIT_READ, Capability.ORG_AUDIT_READ)
        val sameInstant = Instant.parse("2026-07-01T00:00:00Z")

        assertThrows(IllegalArgumentException::class.java) {
            service().listOrganizationEvents(
                actor = actor,
                organizationId = organizationId,
                categories = emptySet(),
                cursor = null,
                limit = 50,
                occurredAfter = sameInstant,
                occurredBefore = sameInstant,
            )
        }

        verify(ledgerRepository, never()).search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `engagement constrained search uses a thirty day range when boundaries are omitted`()
    {
        val organizationId = UUID.randomUUID()
        val actor = actor(Capability.APP_AUDIT_READ, Capability.ORG_AUDIT_READ)
        val event = organizationEvent(organizationId)
        whenever(ledgerRepository.search(eq(organizationId), eq(false), any(), any(), any(), any(), any(), isNull(), isNull(), isNull(), eq(50)))
            .thenReturn(listOf(event))
        whenever(engagementService.resolveAccess(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(
                AuditEngagementService.EngagementAccess(
                    engagementId = UUID.randomUUID(),
                    sensitivityLevel = com.docuhyphen.app.api.model.entity.AuditEngagementSensitivity.STANDARD,
                    exportPermitted = false,
                    maxQueryRangeDays = 30,
                    downloadLimit = null,
                )
            )
        whenever(auditRecorder.record(any())).thenReturn(AuditCaptureResult.Captured(UUID.randomUUID(), UUID.randomUUID()))

        service().listOrganizationEvents(actor, organizationId, emptySet(), null, 50)

        val afterCaptor = argumentCaptor<Timestamp>()
        val beforeCaptor = argumentCaptor<Timestamp>()
        verify(ledgerRepository).search(
            eq(organizationId),
            eq(false),
            any(),
            any(),
            any(),
            isNull(),
            afterCaptor.capture(),
            beforeCaptor.capture(),
            isNull(),
            isNull(),
            eq(50),
        )
        assertEquals(Duration.ofDays(30), Duration.between(afterCaptor.firstValue.toInstant(), beforeCaptor.firstValue.toInstant()))
    }

    @Test
    fun `APP_ADMIN does not amplify limited organization audit access`()
    {
        val organizationId = UUID.randomUUID()
        val actor = actor(Capability.APP_ADMIN, Capability.APP_AUDIT_READ, Capability.ORG_AUDIT_READ)
        val event = organizationEvent(organizationId).apply {
            actorId = UUID.randomUUID()
            actorRole = "ORG_MEMBER"
            actorLabel = "Tenant user"
            targetLabel = "Quarterly.pdf"
        }
        whenever(ledgerRepository.search(eq(organizationId), eq(false), any(), any(), any(), isNull(), any(), any(), isNull(), isNull(), eq(50)))
            .thenReturn(listOf(event))
        whenever(engagementService.resolveAccess(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(
                AuditEngagementService.EngagementAccess(
                    engagementId = UUID.randomUUID(),
                    sensitivityLevel = com.docuhyphen.app.api.model.entity.AuditEngagementSensitivity.STANDARD,
                    exportPermitted = false,
                    maxQueryRangeDays = 30,
                    downloadLimit = null,
                )
            )
        whenever(auditRecorder.record(any())).thenReturn(AuditCaptureResult.Captured(UUID.randomUUID(), UUID.randomUUID()))

        val projection = service().listOrganizationEvents(actor, organizationId, emptySet(), null, 50).items.single()

        verify(engagementService).resolveAccess(any(), eq(organizationId), any(), any(), any(), any(), any(), any())
        assertNull(projection.actorId)
        assertNull(projection.actorRole)
        assertNull(projection.actorLabel)
        assertNull(projection.targetLabel)
        assertFalse("actor_email" in projection.payload)
    }

    @Test
    fun `listPlatformEvents passes an explicit occurred range through to the ledger search`()
    {
        val actor = actor(Capability.APP_AUDIT_READ)
        whenever(
            ledgerRepository.search(
                isNull(), eq(true), any(), any(), any(), isNull(), any(), any(), isNull(), isNull(), eq(50),
            )
        ).thenReturn(emptyList())
        whenever(auditRecorder.record(any())).thenReturn(AuditCaptureResult.Captured(UUID.randomUUID(), UUID.randomUUID()))

        service().listPlatformEvents(
            actor = actor,
            categories = emptySet(),
            cursor = null,
            limit = 50,
            occurredAfter = Instant.parse("2026-07-01T00:00:00Z"),
            occurredBefore = Instant.parse("2026-07-02T00:00:00Z"),
        )

        val afterCaptor = argumentCaptor<Timestamp>()
        val beforeCaptor = argumentCaptor<Timestamp>()
        verify(ledgerRepository).search(
            isNull(), eq(true), any(), any(), any(), isNull(),
            afterCaptor.capture(), beforeCaptor.capture(), isNull(), isNull(), eq(50),
        )
        assertEquals(Instant.parse("2026-07-01T00:00:00Z"), afterCaptor.firstValue.toInstant())
        assertEquals(Instant.parse("2026-07-02T00:00:00Z"), beforeCaptor.firstValue.toInstant())
    }

    @Test
    fun `listPlatformEvents rejects a reversed occurred range before querying evidence`()
    {
        val actor = actor(Capability.APP_AUDIT_READ)

        assertThrows(IllegalArgumentException::class.java) {
            service().listPlatformEvents(
                actor = actor,
                categories = emptySet(),
                cursor = null,
                limit = 50,
                occurredAfter = Instant.parse("2026-07-02T00:00:00Z"),
                occurredBefore = Instant.parse("2026-07-01T00:00:00Z"),
            )
        }

        verify(ledgerRepository, never()).search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `platform projection suppresses content fields from content-category events`()
    {
        val event = organizationEvent(UUID.randomUUID()).apply {
            organizationId = null
            targetLabel = "Quarterly customer file.pdf"
            reason = "Raw customer communication body"
            payloadJson =
                """{"document_title":"Quarterly customer file.pdf","message_body":"Private text","status":"SUCCESS"}"""
        }
        val actor = actor(Capability.APP_ADMIN, Capability.APP_AUDIT_READ)
        whenever(
            ledgerRepository.search(
                isNull(), eq(true), any(), any(), any(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(50),
            )
        ).thenReturn(listOf(event))
        whenever(auditRecorder.record(any())).thenReturn(AuditCaptureResult.Captured(UUID.randomUUID(), UUID.randomUUID()))

        val page = service().listPlatformEvents(actor, emptySet(), null, 50)

        val projection = page.items.single()
        assertEquals(null, projection.targetLabel)
        assertEquals(null, projection.reason)
        assertEquals(mapOf("status" to "SUCCESS"), projection.payload)
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
    fun `pagination fills the visible page when hidden rows precede authorized rows`()
    {
        val organizationId = UUID.randomUUID()
        val actor = actor(Capability.APP_AUDIT_READ, Capability.ORG_AUDIT_READ)
        val hiddenOne = organizationEvent(organizationId, targetId = "hidden-1")
        val visibleOne = organizationEvent(organizationId, targetId = "visible-1")
        val hiddenTwo = organizationEvent(organizationId, targetId = "hidden-2")
        val visibleTwo = organizationEvent(organizationId, targetId = "visible-2")
        whenever(
            ledgerRepository.search(
                eq(organizationId), eq(false), any(), any(), any(), isNull(), any(), any(),
                isNull(), isNull(), eq(2),
            )
        ).thenReturn(listOf(hiddenOne, visibleOne))
        whenever(
            ledgerRepository.search(
                eq(organizationId), eq(false), any(), any(), any(), isNull(), any(), any(),
                eq(visibleOne.occurredAt), eq(visibleOne.eventId), eq(2),
            )
        ).thenReturn(listOf(hiddenTwo, visibleTwo))
        whenever(
            engagementService.resolveAccess(
                any(), eq(organizationId), any(), eq("hidden-1"), any(), any(), any(), any(),
            )
        ).thenReturn(null)
        whenever(
            engagementService.resolveAccess(
                any(), eq(organizationId), any(), eq("hidden-2"), any(), any(), any(), any(),
            )
        ).thenReturn(null)
        whenever(
            engagementService.resolveAccess(
                any(), eq(organizationId), any(), eq("visible-1"), any(), any(), any(), any(),
            )
        ).thenReturn(
            AuditEngagementService.EngagementAccess(
                UUID.randomUUID(),
                com.docuhyphen.app.api.model.entity.AuditEngagementSensitivity.STANDARD,
                false,
                30,
                null,
            )
        )
        whenever(
            engagementService.resolveAccess(
                any(), eq(organizationId), any(), eq("visible-2"), any(), any(), any(), any(),
            )
        ).thenReturn(
            AuditEngagementService.EngagementAccess(
                UUID.randomUUID(),
                com.docuhyphen.app.api.model.entity.AuditEngagementSensitivity.STANDARD,
                false,
                30,
                null,
            )
        )
        whenever(auditRecorder.record(any())).thenReturn(AuditCaptureResult.Captured(UUID.randomUUID(), UUID.randomUUID()))

        val page = service().listOrganizationEvents(actor, organizationId, emptySet(), null, 2)

        assertEquals(listOf("visible-1", "visible-2"), page.items.map { it.targetId })
        assertEquals(visibleTwo.eventId, page.nextCursor?.eventId)
        verify(ledgerRepository, times(2)).search(any(), any(), any(), any(), any(), anyOrNull(), any(), any(), anyOrNull(), anyOrNull(), eq(2))
    }

    @Test
    fun `pagination continues past twenty hidden pages until the repository is exhausted`()
    {
        val organizationId = UUID.randomUUID()
        val hidden = organizationEvent(organizationId, targetId = "hidden")
        var searchCalls = 0
        whenever(ledgerRepository.search(any(), any(), any(), any(), any(), anyOrNull(), any(), any(), anyOrNull(), anyOrNull(), eq(1)))
            .thenAnswer { if (searchCalls++ < 20) listOf(hidden) else emptyList<AuditLedgerEvent>() }
        whenever(engagementService.resolveAccess(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(null)

        val page = service().listOrganizationEvents(
            actor(Capability.APP_AUDIT_READ, Capability.ORG_AUDIT_READ),
            organizationId,
            emptySet(),
            null,
            1,
        )

        assertTrue(page.items.isEmpty())
        verify(ledgerRepository, times(21)).search(
            any(), any(), any(), any(), any(), anyOrNull(), any(), any(), anyOrNull(), anyOrNull(), eq(1),
        )
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
    fun `contextual document search rejects a document from another Exchange before querying evidence`()
    {
        val organizationId = UUID.randomUUID()
        val exchangeId = UUID.randomUUID()
        val foreignDocumentId = UUID.randomUUID()
        val actor = actor(Capability.ORG_AUDIT_READ)
        whenever(exchangeRetrievalService.hasDocumentInExchange(exchangeId, foreignDocumentId)).thenReturn(false)
        whenever(auditRecorder.record(any())).thenReturn(AuditCaptureResult.Captured(UUID.randomUUID(), UUID.randomUUID()))

        assertThrows(AuditProjectionNotFoundException::class.java) {
            service().listExchangeDocumentEvents(actor, organizationId, exchangeId, foreignDocumentId, null, 25)
        }

        verify(exchangeRetrievalService).hasDocumentInExchange(exchangeId, foreignDocumentId)
        verify(ledgerRepository, never()).search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
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
