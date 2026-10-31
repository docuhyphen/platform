package com.docuhyphen.app.api.service.informationrequest.notice

import com.docuhyphen.app.api.migration.*
import com.docuhyphen.app.api.model.InformationRequestNoticeDtoMapper
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.access.InformationRequestOwnerRef
import com.docuhyphen.app.api.model.informationrequest.amendment.AmendInformationRequestCommand
import com.docuhyphen.app.api.model.informationrequest.clock.StartInformationRequestClockCommand
import com.docuhyphen.app.api.model.informationrequest.noauth.InformationRequestAbuseLimits
import com.docuhyphen.app.api.model.informationrequest.notice.SendInformationRequestRemindersCommand
import com.docuhyphen.app.api.model.informationrequest.party.ReassignInformationRequestPartyCommand
import com.docuhyphen.app.api.model.notification.DomainEventConsumptionOutcome
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.amendment.InformationRequestNoticeIntentRepository
import com.docuhyphen.app.api.repository.informationrequest.clock.InformationRequestClockEventRepository
import com.docuhyphen.app.api.repository.informationrequest.clock.InformationRequestClockRepository
import com.docuhyphen.app.api.repository.informationrequest.notice.InformationRequestNoticeDeliveryAttemptRepository
import com.docuhyphen.app.api.repository.informationrequest.notice.InformationRequestNoticeSequenceAllocationRepository
import com.docuhyphen.app.api.repository.informationrequest.notice.InformationRequestOutboundNoticeRepository
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.repository.notification.DomainEventConsumptionRepository
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.OverlappingCommandRace
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestOwnerScopeAccess
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestReadAudit
import com.docuhyphen.app.api.service.informationrequest.clock.InformationRequestClockPointProcessor
import com.docuhyphen.app.api.service.informationrequest.clock.InformationRequestClockPolicyService
import com.docuhyphen.app.api.service.informationrequest.clock.InformationRequestClockRecorder
import com.docuhyphen.app.api.service.informationrequest.clock.InformationRequestClockService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import com.docuhyphen.app.api.service.informationrequest.notice.model.AttributedNotice
import com.docuhyphen.app.api.service.informationrequest.notice.model.NoticeFixture
import com.docuhyphen.app.api.service.informationrequest.oversight.InformationRequestOperationsPostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestPartyRevocationService
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestRespondentWorkDoubles
import com.docuhyphen.app.api.service.notification.DomainEvent
import com.docuhyphen.app.api.service.notification.DomainEventJson
import com.docuhyphen.app.api.service.notification.EventRouter
import io.quarkus.arc.Arc
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(InformationRequestOperationsPostgreSQLResource::class)
class InformationRequestNoticeTransactionTest
{
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var partyRevocations: InformationRequestPartyRevocationService

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var clockRepository: InformationRequestClockRepository

    @Inject
    lateinit var eventRepository: InformationRequestClockEventRepository

    @Inject
    lateinit var policies: InformationRequestClockPolicyService

    @Inject
    lateinit var recorder: InformationRequestClockRecorder

    @Inject
    lateinit var processor: InformationRequestClockPointProcessor

    @Inject
    lateinit var worker: InformationRequestNoticeWorker

    @Inject
    lateinit var dispatcher: InformationRequestNoticeDispatcher

    @Inject
    lateinit var states: InformationRequestNoticeStateReader

    @Inject
    lateinit var noticeRepository: InformationRequestOutboundNoticeRepository

    @Inject
    lateinit var allocationRepository: InformationRequestNoticeSequenceAllocationRepository

    @Inject
    lateinit var attemptRepository: InformationRequestNoticeDeliveryAttemptRepository

    @Inject
    lateinit var sender: RecordingInformationRequestNoticeSender

    @Inject
    lateinit var eventRouter: EventRouter

    @Inject
    lateinit var consumption: DomainEventConsumptionRepository

    @Inject
    lateinit var intentRepository: InformationRequestNoticeIntentRepository

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    @Inject
    lateinit var readAudit: InformationRequestReadAudit

    @Inject
    lateinit var scheduler: InformationRequestNoticeScheduler

    @Inject
    lateinit var partyRepository: InformationRequestPartyRepository

    @Inject
    lateinit var transitionHistory: InformationRequestTransitionHistoryService

    @BeforeEach
    fun resetSender()
    {
        sender.failuresRemaining.set(0)
    }

    @Test
    fun `an overdue point owes each responding party one notice rendered once with one sequence value shared by subject and body`()
    {
        val fixture = overdueFixture()
        val sentBefore = sender.sent.size

        val first = worker.dispatchForRequest(fixture.requestId)
        val second = worker.dispatchForRequest(fixture.requestId)

        assertEquals(2, first.rendered)
        assertEquals(2, first.delivered)
        assertEquals(0, second.rendered + second.delivered)
        assertEquals(sentBefore + 2, sender.sent.size)
        QuarkusTransaction.requiringNew().run {
            val notices = noticeRepository.findForRequest(fixture.requestId)
            assertEquals(2, notices.size)
            val values = notices.map { notice ->
                val allocation = allocationRepository.findForNotice(notice.id).single()
                assertEquals("case-number", allocation.sequenceKey)
                assertTrue(notice.renderedSubject.contains(allocation.renderedValue))
                assertTrue(notice.renderedBody.contains(allocation.renderedValue))
                assertEquals(InformationRequestNoticeSourceKind.COMMUNICATION, notice.sourceKind)
                assertTrue(notice.renderedBody.contains("Process collection"))
                allocation.allocatedValue
            }
            assertEquals(setOf(1L, 2L), values.toSet())
            assertEquals(1, notices.map { it.sourceContentHash }.toSet().size)
            assertNotEquals(notices[0].renderedContentHash, notices[1].renderedContentHash)
            assertEquals(
                setOf(InformationRequestNoticeDeliveryState.DELIVERED),
                states.states(fixture.requestId).values.toSet()
            )
        }
        assertEquals(2, sequenceValue(fixture))
        assertEquals(2, auditCount(fixture, "information_request.notice.render"))
        assertEquals(2, auditCount(fixture, "information_request.notice.deliver"))
    }

    @Test
    fun `a reminder sent from the operations queue states when the request's running clock is due`() {
        val fixture = overdueFixture(process = false)

        val notices = remindAndRender(fixture.requestId, fixture.organizationId, fixture.ownerUserId)

        assertTrue(notices.isNotEmpty())
        notices.forEach { notice ->
            assertFalse(
                notice.renderedSubject.contains("{{") || notice.renderedBody.contains("{{"),
                notice.renderedBody
            )
            assertTrue(notice.renderedSubject.contains("2026-09-25"), notice.renderedSubject)
            assertTrue(notice.renderedBody.contains("2026-09-25"), notice.renderedBody)
        }
    }

    @Test
    fun `a reminder sent from the operations queue for a request with no running clock states no due time`() {
        val runtimeFixture = dataSource.connection.use { connection -> SubmissionRuntimeSqlFixture(connection) }

        val notices = remindAndRender(
            runtimeFixture.requestId,
            runtimeFixture.template.organizationId,
            runtimeFixture.template.userId
        )

        assertTrue(notices.isNotEmpty())
        notices.forEach { notice ->
            assertFalse(
                notice.renderedSubject.contains("{{") || notice.renderedBody.contains("{{"),
                notice.renderedBody
            )
            assertFalse(notice.renderedBody.contains("due"), notice.renderedBody)
            assertTrue(notice.renderedBody.contains("Process collection"), notice.renderedBody)
        }
    }

    @Test
    fun `a failed delivery is retried with the stored render and never allocates a sequence value again`()
    {
        val fixture = overdueFixture()
        sender.failuresRemaining.set(1)

        val first = worker.dispatchForRequest(fixture.requestId)

        assertEquals(1, first.failed)
        assertEquals(1, first.delivered)
        val failedNotice = QuarkusTransaction.requiringNew().call {
            noticeRepository.findForRequest(fixture.requestId).single { notice ->
                attemptRepository.findForNotice(notice.id)
                    .single().outcome == InformationRequestNoticeAttemptOutcome.FAILED
            }
        }
        assertEquals(null, dispatcher.deliver(failedNotice.id, Instant.now()))
        assertEquals(
            InformationRequestNoticeAttemptOutcome.DELIVERED,
            dispatcher.deliver(failedNotice.id, Instant.now().plusSeconds(360))
        )
        QuarkusTransaction.requiringNew().run {
            val retried = requireNotNull(noticeRepository.findById(failedNotice.id))
            assertEquals(failedNotice.renderedSubject, retried.renderedSubject)
            assertEquals(failedNotice.renderedContentHash, retried.renderedContentHash)
            assertEquals(listOf(1, 2), attemptRepository.findForNotice(failedNotice.id).map { it.attemptNumber })
        }
        assertEquals(2, sequenceValue(fixture))
    }

    @Test
    fun `a party without a reachable endpoint is recorded as undeliverable and nothing is sent to it`()
    {
        val fixture = overdueFixture(unreachableParty = true)
        val sentBefore = sender.sent.size

        val result = worker.dispatchForRequest(fixture.requestId)

        assertEquals(3, result.rendered)
        assertEquals(2, result.delivered)
        assertEquals(sentBefore + 2, sender.sent.size)
        QuarkusTransaction.requiringNew().run {
            val missing = noticeRepository.findForRequest(fixture.requestId)
                .single { it.endpointState == InformationRequestNoticeEndpointState.MISSING }
            assertEquals(null, missing.recipientEndpoint)
            assertEquals("NO_ENDPOINT", attemptRepository.findForNotice(missing.id).single().failureCode)
            assertEquals(
                InformationRequestNoticeDeliveryState.UNDELIVERABLE,
                states.states(fixture.requestId).getValue(missing.noticeIntentId)
            )
        }
    }

    @Test
    fun `a notice owed to a group party is sent to each of its active members as a notice of their own`() {
        val fixture = overdueFixture(groupMembers = 2)
        val sentBefore = sender.sent.size

        val result = worker.dispatchForRequest(fixture.requestId)

        assertEquals(4, result.delivered)
        assertEquals(sentBefore + 4, sender.sent.size)
        QuarkusTransaction.requiringNew().run {
            val groupPartyId = UUID.fromString(
                dataSource.connection.use { connection ->
                    queryString(
                        connection,
                        "SELECT id::text FROM information_request_party WHERE information_request_id = ? AND principal_kind = 'PRINCIPAL_GROUP'",
                        fixture.requestId,
                    )
                },
            )
            val memberNotices = noticeRepository.findForRequest(fixture.requestId).filter { it.partyId == groupPartyId }
            assertEquals(2, memberNotices.size)
            assertEquals(setOf(PrincipalKind.USER), memberNotices.map { it.recipientPrincipalKind }.toSet())
            assertEquals(2, memberNotices.mapNotNull { it.recipientEndpoint }.toSet().size)
            assertEquals(1, memberNotices.map { it.noticeIntentId }.toSet().size)
            memberNotices.forEach { notice ->
                assertEquals(
                    InformationRequestNoticeAttemptOutcome.DELIVERED,
                    attemptRepository.findForNotice(notice.id).single().outcome,
                )
            }
            assertEquals(
                InformationRequestNoticeDeliveryState.DELIVERED,
                states.states(fixture.requestId).getValue(memberNotices.first().noticeIntentId),
            )
            val memberHistory = states.views(fixture.requestId)
                .filter { it.intent.partyId == groupPartyId }
                .map { InformationRequestNoticeDtoMapper.toDto(it, emptyList()) }
            assertEquals(2, memberHistory.size)
            assertTrue(memberHistory.all { it.recipientWithheld && it.maskedEndpoint == null })
        }
    }

    @Test
    fun `a queued notice for a party revoked since is recorded as skipped and nothing is sent to it`() {
        val fixture = overdueFixture()
        val revoked = dataSource.connection.use { connection ->
            val partyId = UUID.fromString(
                queryString(
                    connection,
                    "SELECT id::text FROM information_request_party WHERE information_request_id = ? AND role_key = 'CONTRIBUTOR'",
                    fixture.requestId,
                ),
            )
            execute(
                connection,
                "UPDATE information_request_party SET active = FALSE, revoked_at = now() WHERE id = ?",
                partyId
            )
            partyId
        }
        val sentBefore = sender.sent.size

        worker.dispatchForRequest(fixture.requestId)

        assertEquals(sentBefore + 1, sender.sent.size)
        QuarkusTransaction.requiringNew().run {
            val skipped = noticeRepository.findForRequest(fixture.requestId).single { it.partyId == revoked }
            assertEquals(null, skipped.recipientEndpoint)
            assertEquals("PARTY_INACTIVE", attemptRepository.findForNotice(skipped.id).single().failureCode)
            assertEquals(
                InformationRequestNoticeDeliveryState.UNDELIVERABLE,
                states.states(fixture.requestId).getValue(skipped.noticeIntentId)
            )
        }
        assertEquals(1, sequenceValue(fixture))
    }

    @Test
    fun `a rendered notice is not delivered once its request has finished or its Exchange was rescinded`() {
        val finished = overdueFixture()
        sender.failuresRemaining.set(2)
        worker.dispatchForRequest(finished.requestId)
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE information_request SET state = 'CANCELLED', cancelled_at = now() WHERE id = ?",
                finished.requestId
            )
        }
        val rescinded = overdueFixture()
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE exchange SET status = 'RESCINDED' WHERE id = (SELECT exchange_id FROM information_request WHERE id = ?)",
                rescinded.requestId,
            )
        }
        val sentBefore = sender.sent.size

        val retried = QuarkusTransaction.requiringNew().call { noticeRepository.findForRequest(finished.requestId) }
            .map { notice ->
                dispatcher.deliver(notice.id, Instant.now().plusSeconds(360))
            }
        worker.dispatchForRequest(rescinded.requestId)

        assertEquals(sentBefore, sender.sent.size)
        assertEquals(
            listOf(
                InformationRequestNoticeAttemptOutcome.SKIPPED,
                InformationRequestNoticeAttemptOutcome.SKIPPED
            ), retried
        )
        QuarkusTransaction.requiringNew().run {
            assertEquals(
                setOf("REQUEST_FINISHED"),
                noticeRepository.findForRequest(finished.requestId)
                    .map { attemptRepository.findForNotice(it.id).last().failureCode }.toSet(),
            )
            assertEquals(
                setOf("PARENT_CLOSED"),
                noticeRepository.findForRequest(rescinded.requestId)
                    .map { attemptRepository.findForNotice(it.id).single().failureCode }.toSet(),
            )
            assertEquals(
                setOf(InformationRequestNoticeDeliveryState.UNDELIVERABLE),
                (states.states(finished.requestId).values + states.states(rescinded.requestId).values).toSet(),
            )
        }
    }

    @Test
    fun `the overdue event itself dispatches the request's notices once through its consumer`()
    {
        val fixture = overdueFixture()
        val event = QuarkusTransaction.requiringNew().call { overdueEvent(fixture) }

        eventRouter.routeDurable(event)
        eventRouter.routeDurable(event)

        QuarkusTransaction.requiringNew().run {
            assertEquals(2, noticeRepository.findForRequest(fixture.requestId).size)
            assertEquals(
                DomainEventConsumptionOutcome.APPLIED,
                consumption.outcomeOf(InformationRequestNoticeConsumer.CONSUMER_KEY, event.eventUuid()),
            )
        }
        assertEquals(2, sequenceValue(fixture))
    }

    @Test
    fun `reading a request's notice history records one access event per read with counts and ids only`() {
        val fixture = overdueFixture()
        worker.dispatchForRequest(fixture.requestId)
        val queries = noticeQueries(fixture)

        val first = QuarkusTransaction.requiringNew().call { queries.notices(fixture.requestId, owner(fixture)) }
        QuarkusTransaction.requiringNew().call { queries.notices(fixture.requestId, owner(fixture)) }

        assertEquals(2, auditCount(fixture, "information_request.notice.read"))
        val payload = Json.parseToJsonElement(
            requireNotNull(
                dataSource.connection.use { connection ->
                    queryString(
                        connection,
                        "SELECT payload_json FROM audit_outbox WHERE event_type_key = 'information_request.notice.read' AND target_id = ? LIMIT 1",
                        fixture.requestId.toString(),
                    )
                },
            ),
        ).jsonObject
        assertEquals(setOf("noticeCount", "noticeIntentIds"), payload.keys)
        assertEquals(first.size.toString(), payload.getValue("noticeCount").jsonPrimitive.content)
        assertEquals(
            first.map { it.intent.id.toString() }.toSet(),
            payload.getValue("noticeIntentIds").jsonPrimitive.content.split(",").toSet(),
        )
        assertFalse(payload.toString().contains("@"))
    }

    @Test
    fun `a clock point and its notices are attributed to the service principal without a session, and owner commands to the owner and session`() {
        val fixture = overdueFixture()
        worker.dispatchForRequest(fixture.requestId)
        QuarkusTransaction.requiringNew().call { noticeQueries(fixture).notices(fixture.requestId, owner(fixture)) }

        val ownerEmail = dataSource.connection.use {
            queryString(
                it,
                "SELECT email FROM app_user WHERE id = ?",
                fixture.ownerUserId
            )
        }
        val owner = AttributedNotice("HUMAN", ownerEmail, "owner", fixture.ownerUserId)
        val service =
            AttributedNotice("SYSTEM", "service-account:00000000-0000-0000-0000-000000000000", null, UUID(0, 0))
        assertEquals(setOf(owner), attributed(fixture, AuditEventType.INFORMATION_REQUEST_CLOCK_START.key))
        assertEquals(setOf(service), attributed(fixture, AuditEventType.INFORMATION_REQUEST_OVERDUE.key))
        assertEquals(setOf(service), attributed(fixture, AuditEventType.INFORMATION_REQUEST_NOTICE_RENDER.key))
        assertEquals(setOf(service), attributed(fixture, AuditEventType.INFORMATION_REQUEST_NOTICE_DELIVER.key))
        assertEquals(setOf(owner), attributed(fixture, AuditEventType.INFORMATION_REQUEST_NOTICE_READ.key))
        dataSource.connection.use { connection ->
            val startReceipt = queryString(
                connection,
                "SELECT id::text FROM command_receipt WHERE resource_id = ? AND idempotency_key = 'start-response'",
                fixture.requestId,
            )
            assertEquals(
                "start-response|$startReceipt",
                queryString(
                    connection,
                    "SELECT idempotency_key || '|' || command_receipt_id FROM information_request_transition WHERE information_request_id = ? AND mutation = 'START_CLOCK'",
                    fixture.requestId,
                ),
            )
            val overdueSequence = queryInt(
                connection,
                "SELECT sequence_number FROM information_request_transition WHERE information_request_id = ? AND mutation = 'RECORD_OVERDUE'",
                fixture.requestId,
            )
            assertEquals(
                "system|record_overdue|${fixture.requestId}|$overdueSequence|",
                queryString(
                    connection,
                    "SELECT idempotency_key || '|' || COALESCE(command_receipt_id::text, '') FROM information_request_transition WHERE information_request_id = ? AND mutation = 'RECORD_OVERDUE'",
                    fixture.requestId,
                ),
            )
        }
    }

    @Test
    fun `two workers claiming one notice intent at once render, number, and send it exactly once`() {
        val fixture = overdueFixture()
        val intentId = QuarkusTransaction.requiringNew()
            .call { intentRepository.findUnclaimedIdsForRequest(fixture.requestId).first() }
        val start = CountDownLatch(1)
        val attempts = (1..2).map {
            CompletableFuture.supplyAsync {
                withRequestContext {
                    start.await(10, TimeUnit.SECONDS)
                    dispatcher.claimAndRender(intentId, Instant.now())
                }
            }
        }
        start.countDown()

        val rendered = attempts.map { it.get(30, TimeUnit.SECONDS) }

        assertEquals(1, rendered.count { it != null })
        val notice = requireNotNull(rendered.single { it != null })
        dataSource.connection.use { connection ->
            assertEquals(
                1,
                queryInt(
                    connection,
                    "SELECT count(*) FROM information_request_notice_claim WHERE notice_intent_id = ?",
                    intentId
                )
            )
            assertEquals(
                1,
                queryInt(
                    connection,
                    "SELECT count(*) FROM information_request_outbound_notice WHERE notice_intent_id = ?",
                    intentId
                )
            )
            assertEquals(
                1,
                queryInt(
                    connection,
                    "SELECT count(*) FROM information_request_notice_sequence_allocation WHERE outbound_notice_id = ?",
                    notice.id
                )
            )
        }
        assertEquals(1, sequenceValue(fixture))
        worker.dispatchForRequest(fixture.requestId)
        assertEquals(1, sender.sent.count { it == notice.id })
        assertEquals(2, sequenceValue(fixture))
    }

    @Test
    fun `a request's notice history lists its reminders before its overdue notices and a failed attempt before its retry`() {
        val fixture = overdueFixture(reminderMinutes = 30, process = false)
        assertTrue(processor.process(fixture.clockId, fixture.start.plusSeconds(31 * 60)))
        assertTrue(processor.process(fixture.clockId, fixture.start.plusSeconds(61 * 60)))
        sender.failuresRemaining.set(1)

        val dispatched = worker.dispatchForRequest(fixture.requestId)
        val failedNotice = QuarkusTransaction.requiringNew().call {
            noticeRepository.findForRequest(fixture.requestId).single { notice ->
                attemptRepository.findForNotice(notice.id)
                    .any { it.outcome == InformationRequestNoticeAttemptOutcome.FAILED }
            }
        }
        assertEquals(
            InformationRequestNoticeAttemptOutcome.DELIVERED,
            dispatcher.deliver(failedNotice.id, Instant.now().plusSeconds(360))
        )
        val history =
            QuarkusTransaction.requiringNew().call { noticeQueries(fixture).notices(fixture.requestId, owner(fixture)) }

        assertEquals(4, dispatched.rendered)
        assertEquals(
            listOf(
                InformationRequestNoticeKind.RESPONSE_REMINDER,
                InformationRequestNoticeKind.RESPONSE_REMINDER,
                InformationRequestNoticeKind.RESPONSE_OVERDUE,
                InformationRequestNoticeKind.RESPONSE_OVERDUE,
            ),
            history.map { it.intent.noticeKind },
        )
        val retried = history.single { it.notice?.id == failedNotice.id }
        assertEquals(listOf(1, 2), retried.attempts.map { it.attemptNumber })
        assertEquals(
            listOf(InformationRequestNoticeAttemptOutcome.FAILED, InformationRequestNoticeAttemptOutcome.DELIVERED),
            retried.attempts.map { it.outcome },
        )
        assertEquals(setOf(InformationRequestNoticeDeliveryState.DELIVERED), history.map { it.deliveryState }.toSet())
    }

    @Test
    fun `an amendment's notices are dispatched by the pending notice pass even when its event was consumed before any notice consumer`() {
        val runtimeFixture = dataSource.connection.use { connection ->
            val created = SubmissionRuntimeSqlFixture(connection)
            created to created.publishNextVersion { version ->
                execute(
                    connection,
                    "UPDATE information_request_template_requirement_binding SET prompt = 'Upload the recorded item' WHERE id = ?",
                    version.documentBindingId,
                )
            }
        }
        val (request, next) = runtimeFixture
        val owner =
            RequestAccessContext(PrincipalRef.user(request.template.userId), AuthorizationContext(sessionRef = "owner"))
        val etag = QuarkusTransaction.requiringNew()
            .call { InformationRequestETag.aggregateOf(requireNotNull(requestRepository.findById(request.requestId))) }
        val amended = QuarkusTransaction.requiringNew().call {
            runtime.build(request.requestId).amendments.amend(
                AmendInformationRequestCommand(
                    requestId = request.requestId,
                    targetTemplateVersionId = next.versionId,
                    reasonCode = "revised-collection",
                    access = owner,
                    precondition = CommandPrecondition.ExpectedRevision(etag),
                    idempotencyKey = "amend-${UUID.randomUUID()}",
                ),
            )
        }
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE workflow_event_outbox SET status = 'DELIVERED', delivered_at = now() WHERE ordering_key = ?",
                "information_request:${request.requestId}",
            )
        }
        assertEquals(
            setOf(InformationRequestNoticeDeliveryState.PENDING),
            QuarkusTransaction.requiringNew().call { states.states(request.requestId).values.toSet() },
        )

        repeat(10) {
            if (QuarkusTransaction.requiringNew()
                    .call { states.states(request.requestId).values.all { it == InformationRequestNoticeDeliveryState.DELIVERED } }
            ) return@repeat
            scheduler.tick()
        }

        val delivered = QuarkusTransaction.requiringNew().call { states.views(request.requestId) }
        assertEquals(amended.amendment.notices.map { it.id }.toSet(), delivered.map { it.intent.id }.toSet())
        assertEquals(
            setOf(InformationRequestNoticeKind.REQUIREMENTS_AMENDED),
            delivered.map { it.intent.noticeKind }.toSet()
        )
        assertEquals(setOf(InformationRequestNoticeDeliveryState.DELIVERED), delivered.map { it.deliveryState }.toSet())
        assertTrue(sender.sent.containsAll(delivered.mapNotNull { it.notice?.id }))
    }

    private fun noticeQueries(fixture: NoticeFixture) = InformationRequestNoticeQueryService(
        runtime.build(fixture.requestId).gate, states, allocationRepository, requestRepository, readAudit,
    )

    private fun owner(fixture: NoticeFixture) =
        RequestAccessContext(PrincipalRef.user(fixture.ownerUserId), AuthorizationContext(sessionRef = "owner"))

    private fun <T> withRequestContext(block: () -> T): T {
        val context = Arc.container().requestContext()
        context.activate()
        try {
            return block()
        } finally {
            context.terminate()
        }
    }

    private fun overdueFixture(
        unreachableParty: Boolean = false,
        reminderMinutes: Int? = null,
        process: Boolean = true,
        groupMembers: Int = 0,
    ): NoticeFixture
    {
        val communicationId = UUID.randomUUID()
        val versionId = UUID.randomUUID()
        val runtimeFixture = dataSource.connection.use { connection ->
            val runtimeFixture = SubmissionRuntimeSqlFixture(connection)
            val clocks = ClockSqlFixture(connection, runtimeFixture)
            execute(
                connection,
                """
                INSERT INTO communication (id, name, scope, organization_id, created_by_app_user_id, subject, body,
                                           general_tags, is_active, is_published, is_deleted, is_template, created_at, updated_at)
                VALUES (?, 'Overdue notice', 'ORG', ?, ?, 'Record {{SEQ:case-number}} is overdue',
                        'Record {{SEQ:case-number}} in {{EXCHANGE_NAME}} was due {{DUE_AT}}.', '[]', TRUE, TRUE, FALSE, FALSE, now(), now())
                """.trimIndent(),
                communicationId,
                runtimeFixture.template.organizationId,
                runtimeFixture.template.userId,
            )
            execute(
                connection,
                """
                INSERT INTO sequence_definition (id, organization_id, name, key, current_value, pad_width, prefix,
                                                 reset_period, is_active, is_deleted, created_by_app_user_id, created_at)
                VALUES (?, ?, 'Record numbers', 'case-number', 0, 4, 'N-', 'NEVER', TRUE, FALSE, ?, now())
                """.trimIndent(),
                UUID.randomUUID(),
                runtimeFixture.template.organizationId,
                runtimeFixture.template.userId,
            )
            clocks.insertVersion(
                versionId, versionNumber = 2, clockType = "CALENDAR", standard = 60, urgent = 60, escalation = null,
                overdueCommunicationId = communicationId,
            )
            reminderMinutes?.let { clocks.insertReminder(versionId, ordinal = 1, minutes = it) }
            if (unreachableParty)
            {
                execute(
                    connection,
                    """
                    INSERT INTO information_request_party
                        (id, information_request_id, role_key, principal_kind, principal_id, active, party_revision,
                         assigned_at, created_at, updated_at)
                    VALUES (?, ?, 'CONTRIBUTOR', 'PRINCIPAL_GROUP', ?, TRUE, 1, now(), now(), now())
                    """.trimIndent(),
                    UUID.randomUUID(),
                    runtimeFixture.requestId,
                    UUID.randomUUID(),
                )
            }
            if (groupMembers > 0) groupParty(connection, runtimeFixture, groupMembers)
            runtimeFixture
        }
        val start = Instant.parse("2026-09-25T08:00:00Z")
        val service = InformationRequestClockService(
            runtime.build(runtimeFixture.requestId).gate, clockRepository, eventRepository, policies, recorder,
            runtime.commandReceiptService, Clock.fixed(start, ZoneOffset.UTC),
        )
        val started = QuarkusTransaction.requiringNew().call {
            service.start(
                StartInformationRequestClockCommand(
                    requestId = runtimeFixture.requestId,
                    clockKey = "response",
                    policyVersionId = versionId,
                    urgency = InformationRequestClockUrgency.STANDARD,
                    receivedAt = start,
                    access = RequestAccessContext(
                        PrincipalRef.user(runtimeFixture.template.userId),
                        AuthorizationContext(sessionRef = "owner")
                    ),
                    idempotencyKey = "start-response",
                ),
            )
        }
        if (process) assertTrue(processor.process(started.clock.id, start.plusSeconds(3660)))
        return NoticeFixture(
            runtimeFixture.requestId,
            runtimeFixture.template.organizationId,
            runtimeFixture.template.userId,
            started.clock.id,
            start
        )
    }

    private fun groupParty(connection: java.sql.Connection, runtimeFixture: SubmissionRuntimeSqlFixture, members: Int) {
        val groupId = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO principal_group (id, name, scope, owner_organization_id, externally_published, is_active, created_date)
            VALUES (?, 'Notice group', 'ORG', ?, FALSE, TRUE, now())
            """.trimIndent(),
            groupId,
            runtimeFixture.template.organizationId,
        )
        repeat(members) {
            val memberId = UUID.randomUUID()
            execute(
                connection,
                """
                INSERT INTO app_user
                    (id, is_active, created_date, email, email_verification_completed, is_temporary,
                     sign_in_attempts, exchange_version, multifactor_authentication_type,
                     is_password_temporary, email_mfa_fallback_enabled)
                VALUES (?, TRUE, now(), ?, TRUE, FALSE, 0, 0, 'EMAIL', FALSE, FALSE)
                """.trimIndent(),
                memberId,
                "group-member-${memberId.toString().take(8)}@process.test",
            )
            execute(
                connection,
                """
                INSERT INTO principal_group_member (id, principal_group_id, principal_kind, principal_id, group_role, added_at, is_active)
                VALUES (?, ?, 'USER', ?, 'MEMBER', now(), TRUE)
                """.trimIndent(),
                UUID.randomUUID(),
                groupId,
                memberId,
            )
        }
        execute(
            connection,
            """
            INSERT INTO information_request_party
                (id, information_request_id, role_key, principal_kind, principal_id, active, party_revision,
                 assigned_at, created_at, updated_at)
            VALUES (?, ?, 'CONTRIBUTOR', 'PRINCIPAL_GROUP', ?, TRUE, 1, now(), now(), now())
            """.trimIndent(),
            UUID.randomUUID(),
            runtimeFixture.requestId,
            groupId,
        )
    }

    @Test
    fun `a rendered notice is kept and recorded as skipped once its party is reassigned to another holder`() {
        val fixture = overdueFixture()
        val notice = renderOne(fixture)
        val replacement = reassignToAnotherHolder(fixture, notice, "reassign-before-delivery")
        val sentBefore = sender.sent.size

        val outcome = dispatcher.deliver(notice.id, Instant.now())

        assertEquals(InformationRequestNoticeAttemptOutcome.SKIPPED, outcome)
        assertEquals(sentBefore, sender.sent.size)
        assertFrozenAndSkipped(fixture, notice, replacement, listOf(InformationRequestNoticeAttemptOutcome.SKIPPED))
    }

    @Test
    fun `a failed notice is not retried to the holder its party was reassigned away from`() {
        val fixture = overdueFixture()
        val notice = renderOne(fixture)
        sender.failuresRemaining.set(1)
        assertEquals(InformationRequestNoticeAttemptOutcome.FAILED, dispatcher.deliver(notice.id, Instant.now()))
        val replacement = reassignToAnotherHolder(fixture, notice, "reassign-before-retry")
        val sentBefore = sender.sent.size

        val outcome = dispatcher.deliver(notice.id, Instant.now().plusSeconds(360))

        assertEquals(InformationRequestNoticeAttemptOutcome.SKIPPED, outcome)
        assertEquals(sentBefore, sender.sent.size)
        assertFrozenAndSkipped(
            fixture,
            notice,
            replacement,
            listOf(InformationRequestNoticeAttemptOutcome.FAILED, InformationRequestNoticeAttemptOutcome.SKIPPED),
        )
    }

    @Test
    fun `a delivery waits for a reassignment of its party that is still being recorded and then skips the former holder`() {
        val fixture = overdueFixture()
        val notice = renderOne(fixture)
        val replacement = UUID.randomUUID()
        val race = OverlappingCommandRace(dataSource)
        val sentBefore = sender.sent.size

        val (overlapped, outcome) = dataSource.connection.use { holder ->
            holder.autoCommit = false
            queryString(
                holder,
                "SELECT CAST(id AS text) FROM information_request_party WHERE id = ? FOR UPDATE",
                notice.partyId
            )
            execute(
                holder,
                "UPDATE information_request_party SET principal_kind = 'PARTICIPANT', principal_id = ?, party_revision = party_revision + 1 WHERE id = ?",
                replacement,
                notice.partyId,
            )
            val delivery = CompletableFuture.supplyAsync {
                withRequestContext { dispatcher.deliver(notice.id, Instant.now()) }
            }
            val overlapped = race.awaitLockWaiters(1, listOf(delivery))
            holder.commit()
            overlapped to delivery.get(30, TimeUnit.SECONDS)
        }

        assertTrue(overlapped, "The delivery did not wait for the party being reassigned")
        assertEquals(InformationRequestNoticeAttemptOutcome.SKIPPED, outcome)
        assertEquals(sentBefore, sender.sent.size)
        assertFrozenAndSkipped(fixture, notice, replacement, listOf(InformationRequestNoticeAttemptOutcome.SKIPPED))
    }

    @Test
    fun `a rendered notice whose party still names its recipient is delivered to that recipient`() {
        val fixture = overdueFixture()
        val notice = renderOne(fixture)

        assertEquals(InformationRequestNoticeAttemptOutcome.DELIVERED, dispatcher.deliver(notice.id, Instant.now()))

        assertEquals(1, sender.sent.count { it == notice.id })
    }

    private fun remindAndRender(
        requestId: UUID,
        organizationId: UUID,
        ownerUserId: UUID
    ): List<InformationRequestOutboundNotice> {
        val owner = InformationRequestOwnerRef(InformationRequestOwnerType.ORGANIZATION, organizationId)
        val ownerAccess = mock<InformationRequestOwnerScopeAccess>()
        whenever(ownerAccess.currentOwner()).thenReturn(owner)
        whenever(ownerAccess.requireAccess(owner, Action.INFORMATION_REQUEST_SEND_REMINDERS)).thenReturn(
            RequestAccessContext(PrincipalRef.user(ownerUserId), AuthorizationContext(sessionRef = "owner-session")),
        )
        val reminders = InformationRequestReminderService(
            ownerAccess,
            requestRepository,
            runtime.build(requestId).gate,
            partyRepository,
            intentRepository,
            transitionHistory,
            runtime.commandReceiptService,
            InformationRequestAbuseLimits(reminderCooldown = Duration.ofHours(24)),
            Clock.systemUTC(),
            InformationRequestRespondentWorkDoubles.everyRespondingRoleOutstanding(),
        )
        QuarkusTransaction.requiringNew().call {
            reminders.send(SendInformationRequestRemindersCommand(listOf(requestId), "remind-${UUID.randomUUID()}"))
        }
        val intentIds =
            QuarkusTransaction.requiringNew().call { intentRepository.findUnclaimedIdsForRequest(requestId) }
        return intentIds.mapNotNull { dispatcher.claimAndRender(it, Instant.now()) }
    }

    @Test
    fun `a failed delivery in backoff does not consume the pending delivery batch`() {
        val fixture = overdueFixture()
        val retrying = renderOne(fixture)
        sender.failuresRemaining.set(1)
        assertEquals(InformationRequestNoticeAttemptOutcome.FAILED, dispatcher.deliver(retrying.id, Instant.now()))
        val fresh = renderOne(fixture)
        val selected = QuarkusTransaction.requiringNew().call {
            noticeRepository.findAwaitingDeliveryIds(5, 1, fixture.requestId)
        }
        assertEquals(listOf(fresh.id), selected)
    }

    @Test
    fun `a retry skips an overdue notice after its due cycle is superseded`() {
        val fixture = overdueFixture()
        val notice = renderOne(fixture)
        val now = Instant.now()
        sender.failuresRemaining.set(1)
        assertEquals(InformationRequestNoticeAttemptOutcome.FAILED, dispatcher.deliver(notice.id, now))
        dataSource.connection.use {
            execute(
                it,
                "UPDATE information_request_clock SET due_cycle = due_cycle + 1, due_at = due_at + interval '1 day' WHERE id = ?",
                fixture.clockId
            )
        }
        assertEquals(
            InformationRequestNoticeAttemptOutcome.SKIPPED,
            dispatcher.deliver(notice.id, now.plusSeconds(301))
        )
    }

    private fun renderOne(fixture: NoticeFixture): InformationRequestOutboundNotice {
        val intentId = QuarkusTransaction.requiringNew().call {
            intentRepository.findUnclaimedIdsForRequest(fixture.requestId).first()
        }
        val notice = requireNotNull(dispatcher.claimAndRender(intentId, Instant.now()))
        assertEquals(InformationRequestNoticeEndpointState.RESOLVED, notice.endpointState)
        return notice
    }

    private fun reassignToAnotherHolder(
        fixture: NoticeFixture,
        notice: InformationRequestOutboundNotice,
        key: String
    ): UUID {
        val replacement = dataSource.connection.use { connection ->
            val holder = UUID.fromString(
                requireNotNull(
                    queryString(
                        connection,
                        """
                        SELECT principal_id::text FROM information_request_party
                        WHERE information_request_id = ? AND id <> ? AND principal_id <> ? AND principal_kind = 'USER'
                        LIMIT 1
                        """.trimIndent(),
                        fixture.requestId,
                        notice.partyId,
                        notice.recipientPrincipalId,
                    ),
                ),
            )
            execute(
                connection,
                """
                INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
                SELECT ?, 'EXCHANGE', exchange_id, 'USER', ?, 'OWNER', 'DIRECT', 'ACTIVE', now()
                FROM information_request WHERE id = ?
                """.trimIndent(),
                UUID.randomUUID(),
                fixture.ownerUserId,
                fixture.requestId,
            )
            execute(
                connection,
                """
                INSERT INTO request_execution_grant
                    (id, request_id, owner_type, owner_organization_id, plan_code, subscription_status,
                     enforcement_mode, acting_party_cap, issued_at, created_at)
                VALUES (?, ?, 'ORGANIZATION', ?, 'BUSINESS', 'ACTIVE', 'ENFORCE', 10, now(), now())
                """.trimIndent(),
                UUID.randomUUID(),
                fixture.requestId,
                fixture.organizationId,
            )
            holder
        }
        val precondition = QuarkusTransaction.requiringNew().call {
            CommandPrecondition.ExpectedRevision(
                InformationRequestETag.partiesOf(requireNotNull(requestRepository.findById(fixture.requestId))),
            )
        }
        partyRevocations.reassign(
            ReassignInformationRequestPartyCommand(
                requestId = fixture.requestId,
                partyId = notice.partyId,
                principal = PrincipalRef.user(replacement),
                access = owner(fixture),
                precondition = precondition,
                idempotencyKey = key,
            ),
        )
        return replacement
    }

    private fun assertFrozenAndSkipped(
        fixture: NoticeFixture,
        notice: InformationRequestOutboundNotice,
        replacement: UUID,
        outcomes: List<InformationRequestNoticeAttemptOutcome>,
    ) {
        QuarkusTransaction.requiringNew().run {
            val kept = requireNotNull(noticeRepository.findById(notice.id))
            assertEquals(notice.recipientPrincipalId, kept.recipientPrincipalId)
            assertNotEquals(replacement, kept.recipientPrincipalId)
            assertEquals(notice.recipientEndpoint, kept.recipientEndpoint)
            assertEquals(notice.renderedContentHash, kept.renderedContentHash)
            val attempts = attemptRepository.findForNotice(notice.id)
            assertEquals(outcomes, attempts.map { it.outcome })
            assertEquals("RECIPIENT_REASSIGNED", attempts.last().failureCode)
            assertEquals(
                InformationRequestNoticeDeliveryState.UNDELIVERABLE,
                states.states(fixture.requestId).getValue(notice.noticeIntentId),
            )
        }
    }

    private fun overdueEvent(fixture: NoticeFixture): DomainEvent =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT envelope_json FROM workflow_event_outbox WHERE ordering_key = ? AND event_type = 'information_request.request.overdue'",
            ).use { statement ->
                statement.setString(1, "information_request:${fixture.requestId}")
                statement.executeQuery().use { rows ->
                    check(rows.next())
                    DomainEventJson.instance.decodeFromString(DomainEvent.serializer(), rows.getString(1))
                }
            }
        }

    private fun sequenceValue(fixture: NoticeFixture): Int =
        dataSource.connection.use { connection ->
            queryInt(
                connection,
                "SELECT current_value FROM sequence_definition WHERE organization_id = ? AND key = 'case-number'",
                fixture.organizationId
            )
        }

    private fun auditCount(fixture: NoticeFixture, eventType: String): Int =
        dataSource.connection.use { connection ->
            queryInt(
                connection,
                "SELECT count(*) FROM audit_outbox WHERE event_type_key = ? AND target_id = ?",
                eventType,
                fixture.requestId.toString(),
            )
        }

    private fun attributed(fixture: NoticeFixture, eventType: String): Set<AttributedNotice> =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT actor_kind, actor_label, session_id, actor_id FROM audit_outbox WHERE event_type_key = ? AND target_id = ?",
            ).use { statement ->
                statement.setString(1, eventType)
                statement.setString(2, fixture.requestId.toString())
                statement.executeQuery().use { rows ->
                    generateSequence {
                        if (rows.next()) AttributedNotice(
                            rows.getString(1),
                            rows.getString(2),
                            rows.getString(3),
                            rows.getObject(4, UUID::class.java)
                        )
                        else null
                    }.toSet()
                }
            }
        }
}
