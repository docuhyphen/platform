package com.docuhyphen.app.api.service.informationrequest.audit

import com.docuhyphen.app.api.model.audit.AuditTargetQuery
import com.docuhyphen.app.api.model.audit.AuditTargetRecord
import com.docuhyphen.app.api.model.audit.AuditTargetRecordPage
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.access.InformationRequestOwnerRef
import com.docuhyphen.app.api.model.informationrequest.audit.InformationRequestAuditSearch
import com.docuhyphen.app.api.repository.informationrequest.lifecycle.InformationRequestTransitionRepository
import com.docuhyphen.app.api.service.audit.AuditTargetHistoryService
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestOwnerScopeAccess
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.time.Instant
import java.util.*

class InformationRequestAuditServiceTest {
    private val gate = mock<InformationRequestMutationGate>()
    private val ownerAccess = mock<InformationRequestOwnerScopeAccess>()
    private val history = mock<AuditTargetHistoryService>()
    private val transitions = mock<InformationRequestTransitionRepository>()
    private val service = InformationRequestAuditService(gate, ownerAccess, history, transitions)
    private val requestId = UUID.randomUUID()
    private val access = RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), AuthorizationContext())

    init {
        whenever(history.recordsForTarget(any(), any(), any())).thenReturn(AuditTargetRecordPage(emptyList(), 0))
    }

    @Test
    fun `a request's audit history is read newest first and pages by the requested limit and offset`() {
        val page = service.events(requestId, access, InformationRequestAuditSearch(limit = 25, offset = 50))

        val query = argumentCaptor<AuditTargetQuery>()
        verify(history).recordsForTarget(eq("INFORMATION_REQUEST"), eq(requestId.toString()), query.capture())
        assertTrue(query.firstValue.newestFirst)
        assertEquals(25, query.firstValue.limit)
        assertEquals(50, query.firstValue.offset)
        assertEquals(25, page.limit)
        assertEquals(50, page.offset)
    }

    @Test
    fun `reconciliation still reads the request's transition events in recorded order`() {
        service.reconciliation(requestId, access)

        val query = argumentCaptor<AuditTargetQuery>()
        verify(history).recordsForTarget(eq("INFORMATION_REQUEST"), eq(requestId.toString()), query.capture())
        assertFalse(query.firstValue.newestFirst)
    }

    @Test
    fun `a request's audit history names a follow-up or replacement only to a caller who may view it, deciding each once`() {
        val hidden = UUID.randomUUID()
        val visible = UUID.randomUUID()
        val records = listOf(
            record("information_request.request.successor", "successorRequestId" to hidden.toString()),
            record("information_request.request.supersede", "supersededByRequestId" to hidden.toString()),
            record("information_request.request.successor", "successorRequestId" to visible.toString()),
        )
        whenever(history.recordsForTarget(any(), any(), any())).thenReturn(AuditTargetRecordPage(records, 3))
        whenever(gate.permitsRequest(access, Action.INFORMATION_REQUEST_VIEW, visible)).thenReturn(true)

        val events = service.events(requestId, access, InformationRequestAuditSearch()).events

        assertEquals(
            listOf(mapOf("mutation" to "RECORDED"), mapOf("mutation" to "RECORDED")),
            events.take(2).map { it.payload })
        assertEquals(listOf(1, 1), events.take(2).map { it.withheldKeyCount })
        assertEquals(mapOf("mutation" to "RECORDED", "successorRequestId" to visible.toString()), events[2].payload)
        assertEquals(0, events[2].withheldKeyCount)
        verify(gate, times(1)).permitsRequest(access, Action.INFORMATION_REQUEST_VIEW, hidden)
        verify(gate, times(1)).permitsRequest(access, Action.INFORMATION_REQUEST_VIEW, visible)
    }

    @Test
    fun `the owner audit search names related requests, which its owner-scope readers may list`() {
        val owner = InformationRequestOwnerRef(InformationRequestOwnerType.ORGANIZATION, UUID.randomUUID())
        val related = UUID.randomUUID()
        whenever(ownerAccess.currentOwner()).thenReturn(owner)
        whenever(ownerAccess.requireAccess(owner, Action.INFORMATION_REQUEST_VIEW_OPERATIONS_QUEUE)).thenReturn(access)
        whenever(history.recordsForOwner(any(), any(), any())).thenReturn(
            AuditTargetRecordPage(
                listOf(
                    record(
                        "information_request.request.successor",
                        "successorRequestId" to related.toString()
                    )
                ), 1
            ),
        )

        val event = service.search(InformationRequestAuditSearch()).events.single()

        assertEquals(related.toString(), event.payload["successorRequestId"])
        assertEquals(0, event.withheldKeyCount)
        verify(gate, never()).permitsRequest(any(), any(), any())
    }

    private fun record(eventTypeKey: String, related: Pair<String, String>) = AuditTargetRecord(
        eventId = UUID.randomUUID(),
        eventTypeKey = eventTypeKey,
        category = "INFORMATION_REQUEST",
        outcome = "SUCCESS",
        occurredAt = Instant.parse("2026-10-25T09:00:00Z"),
        recordedAt = Instant.parse("2026-10-25T09:00:00Z"),
        actorKind = "HUMAN",
        actorId = access.principal.id,
        targetType = "INFORMATION_REQUEST",
        targetId = requestId.toString(),
        businessTransactionId = UUID.randomUUID().toString(),
        payload = mapOf("mutation" to "RECORDED", related),
        sealed = true,
        eventHash = "0".repeat(64),
        streamSequence = 1,
    )
}
