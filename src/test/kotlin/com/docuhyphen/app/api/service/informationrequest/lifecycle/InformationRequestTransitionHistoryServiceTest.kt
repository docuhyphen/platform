package com.docuhyphen.app.api.service.informationrequest.lifecycle

import com.docuhyphen.app.api.model.command.CommandReceiptReference
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.entity.InformationRequestTransition
import com.docuhyphen.app.api.model.entity.RequestAccessSession
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestTransitionHistoryCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.lifecycle.InformationRequestTransitionRepository
import com.docuhyphen.app.api.service.audit.AuditCaptureResult
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandReceiptScope
import com.docuhyphen.app.api.service.identity.PrincipalDisplayService
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestAuditActorLabelResolver
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestResponseStart
import com.docuhyphen.app.api.service.notification.DomainEventPublisher
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.util.*

class InformationRequestTransitionHistoryServiceTest {
    private val transitions = mock<InformationRequestTransitionRepository>()
    private val auditRecorder = mock<AuditRecorder>()
    private val requestRepository = mock<InformationRequestRepository>()
    private val displays = mock<PrincipalDisplayService>()
    private val receipts = CommandReceiptScope()
    private val saved = mutableListOf<InformationRequestTransition>()
    private val history = InformationRequestTransitionHistoryService(
        transitions,
        auditRecorder,
        mock<DomainEventPublisher>(),
        InformationRequestResponseStart(requestRepository, Clock.systemUTC()),
        InformationRequestAuditActorLabelResolver(displays),
        receipts,
    )
    private val request = InformationRequest().apply {
        exchangeId = UUID.randomUUID()
        templateVersionId = UUID.randomUUID()
        ownerType = InformationRequestOwnerType.ORGANIZATION
        ownerOrganizationId = UUID.randomUUID()
        state = InformationRequestState.IN_PROGRESS
        issuedAt = Timestamp.from(Instant.now())
    }

    init {
        whenever(transitions.nextSequenceNumber(request.id)).thenAnswer { saved.size + 1 }
        whenever(transitions.save(any())).thenAnswer {
            it.getArgument<InformationRequestTransition>(0).also(saved::add)
        }
        whenever(requestRepository.update(any())).thenAnswer { it.getArgument<InformationRequest>(0) }
        whenever(auditRecorder.record(any())).thenAnswer {
            AuditCaptureResult.Captured(UUID.randomUUID(), it.getArgument<AuditEventDraft>(0).eventId)
        }
    }

    @Test
    fun `an owner's mutation is audited under the user's label and the user session it arrived on`() {
        val owner = PrincipalRef.user(UUID.randomUUID())
        val userSession = UUID.randomUUID().toString()
        whenever(displays.userLabel(owner.id)).thenReturn("Process Owner <owner@process.test>")

        history.record(
            command(
                InformationRequestMutation.AMEND,
                InformationRequestAuditActor.of(access(owner, userSession))
            )
        )

        val draft = drafts().single()
        assertEquals(owner.id, draft.actorId)
        assertEquals(AuditActorKind.HUMAN, draft.actorKind)
        assertEquals("Process Owner <owner@process.test>", draft.actorLabel)
        assertEquals(userSession, draft.sessionId)
    }

    @Test
    fun `a respondent's save through an access link is audited as the public link with its participant identifier and session`() {
        val respondent = PrincipalRef.participant(UUID.randomUUID())
        val accessSession = UUID.randomUUID().toString()

        history.record(
            command(
                InformationRequestMutation.SAVE_RESPONSE,
                InformationRequestAuditActor.of(access(respondent, accessSession))
            )
        )

        val draft = drafts().single()
        assertEquals(respondent.id, draft.actorId)
        assertEquals(AuditActorKind.PUBLIC_LINK, draft.actorKind)
        assertEquals("participant:${respondent.id}", draft.actorLabel)
        assertEquals(accessSession, draft.sessionId)
    }

    @Test
    fun `a respondent's first save audits the response start and the save under the same participant and session`() {
        request.state = InformationRequestState.ISSUED
        val respondent = PrincipalRef.participant(UUID.randomUUID())
        val accessSession = UUID.randomUUID().toString()

        history.record(
            command(
                InformationRequestMutation.SAVE_RESPONSE,
                InformationRequestAuditActor.of(access(respondent, accessSession))
            )
        )

        assertEquals(
            listOf(InformationRequestMutation.START_RESPONSE, InformationRequestMutation.SAVE_RESPONSE),
            saved.map { it.mutation })
        assertEquals(
            List(2) { Triple(AuditActorKind.PUBLIC_LINK, "participant:${respondent.id}", accessSession) },
            drafts().map { Triple(it.actorKind, it.actorLabel, it.sessionId) },
        )
    }

    @Test
    fun `a credential event before any session is audited as the public link with no session`() {
        val respondent = PrincipalRef.participant(UUID.randomUUID())

        history.record(
            command(
                InformationRequestMutation.ISSUE_CONTACT_CHALLENGE,
                InformationRequestAuditActor.beforeSession(respondent)
            )
                .copy(requireAuditCapture = true),
        )

        val draft = drafts().single()
        assertEquals(AuditActorKind.PUBLIC_LINK, draft.actorKind)
        assertEquals("participant:${respondent.id}", draft.actorLabel)
        assertNull(draft.sessionId)
    }

    @Test
    fun `an account holder proving a request access session is audited under that session`() {
        val account = PrincipalRef.user(UUID.randomUUID())
        val session = RequestAccessSession().apply {
            credentialHash = "stored-credential-digest"
            expiresAt = Timestamp.from(Instant.now().plusSeconds(3600))
        }
        whenever(displays.userLabel(account.id)).thenReturn("account@process.test")

        history.record(
            command(
                InformationRequestMutation.LINK_PARTICIPANT_ACCOUNT,
                InformationRequestAuditActor.of(account, session)
            )
                .copy(requireAuditCapture = true),
        )

        val draft = drafts().single()
        assertEquals(AuditActorKind.HUMAN, draft.actorKind)
        assertEquals("account@process.test", draft.actorLabel)
        assertEquals(session.id.toString(), draft.sessionId)
    }

    @Test
    fun `a system mutation is audited under the service principal with an explicit label and no session`() {
        val service = PrincipalRef.service(UUID(0, 0))

        history.record(command(InformationRequestMutation.RECORD_OVERDUE, InformationRequestAuditActor.system(service)))

        val draft = drafts().single()
        assertEquals(service.id, draft.actorId)
        assertEquals(AuditActorKind.SYSTEM, draft.actorKind)
        assertEquals("service-account:00000000-0000-0000-0000-000000000000", draft.actorLabel)
        assertNull(draft.sessionId)
    }

    @Test
    fun `a transition written inside a command records that command's receipt and idempotency key`() {
        val receipt = CommandReceiptReference(UUID.randomUUID(), "client-save-key")
        val respondent = PrincipalRef.participant(UUID.randomUUID())

        receipts.within(receipt) {
            history.record(
                command(
                    InformationRequestMutation.SAVE_RESPONSE,
                    InformationRequestAuditActor.of(access(respondent, "access-session"))
                )
            )
        }

        val transition = saved.single()
        assertEquals(receipt.receiptId, transition.commandReceiptId)
        assertEquals("client-save-key", transition.idempotencyKey)
    }

    @Test
    fun `a response start and the save that caused it share the command's receipt and idempotency key`() {
        request.state = InformationRequestState.ISSUED
        val receipt = CommandReceiptReference(UUID.randomUUID(), "first-save")
        val respondent = PrincipalRef.participant(UUID.randomUUID())

        receipts.within(receipt) {
            history.record(
                command(
                    InformationRequestMutation.SAVE_RESPONSE,
                    InformationRequestAuditActor.of(access(respondent, "access-session"))
                )
            )
        }

        assertEquals(
            List(2) { receipt.receiptId to "first-save" },
            saved.map { it.commandReceiptId to it.idempotencyKey })
    }

    @Test
    fun `a transition written outside any command records a system key from its mutation, request, and sequence and no receipt`() {
        val service = PrincipalRef.service(UUID(0, 0))

        history.record(command(InformationRequestMutation.RECORD_OVERDUE, InformationRequestAuditActor.system(service)))
        history.record(command(InformationRequestMutation.EXPIRE, InformationRequestAuditActor.system(service)))

        assertEquals(
            listOf(
                null to "system|record_overdue|${request.id}|1",
                null to "system|expire|${request.id}|2",
            ),
            saved.map { it.commandReceiptId to it.idempotencyKey },
        )
    }

    @Test
    fun `transitions written by different commands keep distinct audit keys when their client keys and caller keys match`() {
        val respondent = PrincipalRef.participant(UUID.randomUUID())
        val sharedCallerKey = "information_request.response|${request.id}|shared-client-key"

        listOf(UUID.randomUUID(), UUID.randomUUID()).forEach { receiptId ->
            receipts.within(CommandReceiptReference(receiptId, "shared-client-key")) {
                history.record(
                    command(
                        InformationRequestMutation.SAVE_RESPONSE,
                        InformationRequestAuditActor.of(access(respondent, "access-session"))
                    ).copy(auditIdempotencyKey = sharedCallerKey),
                )
            }
        }

        val keys = drafts().map { it.idempotencyKey }
        assertEquals(2, keys.toSet().size)
        assertTrue(keys.none { it == sharedCallerKey })
    }

    @Test
    fun `a response start and the save that caused it keep their own audit keys inside one command`() {
        request.state = InformationRequestState.ISSUED
        val receipt = CommandReceiptReference(UUID.randomUUID(), "first-save")
        val respondent = PrincipalRef.participant(UUID.randomUUID())

        receipts.within(receipt) {
            history.record(
                command(
                    InformationRequestMutation.SAVE_RESPONSE,
                    InformationRequestAuditActor.of(access(respondent, "access-session"))
                ),
            )
        }

        val drafts = drafts()
        assertEquals(saved.map { it.id.toString() }, drafts.map { it.businessTransactionId })
        assertEquals(2, drafts.mapNotNull { it.idempotencyKey }.toSet().size)
        assertTrue(drafts.all { requireNotNull(it.idempotencyKey).contains(receipt.receiptId.toString()) })
    }

    @Test
    fun `a command's audit key fits the audit outbox whatever the length of its client key`() {
        val clientKey = "k".repeat(256)
        val respondent = PrincipalRef.participant(UUID.randomUUID())

        receipts.within(CommandReceiptReference(UUID.randomUUID(), clientKey)) {
            history.record(
                command(
                    InformationRequestMutation.SAVE_RESPONSE,
                    InformationRequestAuditActor.of(access(respondent, "access-session"))
                ).copy(auditIdempotencyKey = "information_request.response|${request.id}|$clientKey"),
            )
        }

        assertTrue(requireNotNull(drafts().single().idempotencyKey).length <= 256)
    }

    @Test
    fun `a transition written outside any command keeps the audit key its caller chose`() {
        val service = PrincipalRef.service(UUID(0, 0))
        val systemKey = "information_request.clock_overdue|${UUID.randomUUID()}|1"

        history.record(
            command(InformationRequestMutation.RECORD_OVERDUE, InformationRequestAuditActor.system(service))
                .copy(auditIdempotencyKey = systemKey),
        )

        assertEquals(systemKey, drafts().single().idempotencyKey)
    }

    private fun command(mutation: InformationRequestMutation, actor: InformationRequestAuditActor) =
        InformationRequestTransitionHistoryCommand(
            request = request,
            fromState = request.state,
            toState = request.state,
            mutation = mutation,
            actor = actor,
            auditIdempotencyKey = "information_request.test|${mutation.name}|${UUID.randomUUID()}",
        )

    private fun access(principal: PrincipalRef, sessionRef: String) =
        RequestAccessContext(principal, AuthorizationContext(sessionRef = sessionRef))

    private fun drafts(): List<AuditEventDraft> {
        val captured = argumentCaptor<AuditEventDraft>()
        verify(auditRecorder, atLeastOnce()).record(captured.capture())
        return captured.allValues
    }
}
