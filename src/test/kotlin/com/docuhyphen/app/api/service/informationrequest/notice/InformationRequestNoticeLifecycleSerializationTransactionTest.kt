package com.docuhyphen.app.api.service.informationrequest.notice

import com.docuhyphen.app.api.migration.*
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.clock.StartInformationRequestClockCommand
import com.docuhyphen.app.api.model.informationrequest.disposal.InformationRequestDisposalOutcome
import com.docuhyphen.app.api.model.informationrequest.lifecycle.CancelInformationRequestCommand
import com.docuhyphen.app.api.model.informationrequest.lifecycle.SupersedeInformationRequestCommand
import com.docuhyphen.app.api.model.informationrequest.party.ReassignInformationRequestPartyCommand
import com.docuhyphen.app.api.model.informationrequest.submission.RecordInformationRequestSubmissionAttestationCommand
import com.docuhyphen.app.api.model.informationrequest.submission.SubmitInformationRequestPackageCommand
import com.docuhyphen.app.api.model.recordpreservation.PublishRecordRetentionScheduleCommand
import com.docuhyphen.app.api.model.recordpreservation.RecordOwnerRef
import com.docuhyphen.app.api.model.recordpreservation.RecordPreservationResourceTypes
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.amendment.InformationRequestNoticeIntentRepository
import com.docuhyphen.app.api.repository.informationrequest.clock.InformationRequestClockEventRepository
import com.docuhyphen.app.api.repository.informationrequest.clock.InformationRequestClockRepository
import com.docuhyphen.app.api.repository.informationrequest.notice.InformationRequestNoticeDeliveryAttemptRepository
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.OverlappingCommandRace
import com.docuhyphen.app.api.service.exchange.ExchangeLookupService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.clock.InformationRequestClockPointProcessor
import com.docuhyphen.app.api.service.informationrequest.clock.InformationRequestClockPolicyService
import com.docuhyphen.app.api.service.informationrequest.clock.InformationRequestClockRecorder
import com.docuhyphen.app.api.service.informationrequest.clock.InformationRequestClockService
import com.docuhyphen.app.api.service.informationrequest.disposal.InformationRequestDisposalService
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestRuntimeServices
import com.docuhyphen.app.api.service.informationrequest.notice.model.NoticeRaceFixture
import com.docuhyphen.app.api.service.informationrequest.oversight.InformationRequestOperationsPostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestPartyRevocationService
import com.docuhyphen.app.api.service.recordpreservation.RecordRetentionScheduleService
import io.quarkus.arc.Arc
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.sql.Connection
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(InformationRequestOperationsPostgreSQLResource::class)
class InformationRequestNoticeLifecycleSerializationTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

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
    lateinit var dispatcher: InformationRequestNoticeDispatcher

    @Inject
    lateinit var attemptRepository: InformationRequestNoticeDeliveryAttemptRepository

    @Inject
    lateinit var intentRepository: InformationRequestNoticeIntentRepository

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    @Inject
    lateinit var exchangeLookup: ExchangeLookupService

    @Inject
    lateinit var exchangeRepository: ExchangeRepository

    @Inject
    lateinit var partyRevocations: InformationRequestPartyRevocationService

    @Inject
    lateinit var disposals: InformationRequestDisposalService

    @Inject
    lateinit var schedules: RecordRetentionScheduleService

    @Inject
    lateinit var sender: RecordingInformationRequestNoticeSender

    private lateinit var executor: ExecutorService

    @BeforeEach
    fun resetDelivery() {
        sender.failuresRemaining.set(0)
        executor = Executors.newCachedThreadPool()
    }

    @AfterEach
    fun stopThreads() {
        executor.shutdownNow()
    }

    @ParameterizedTest
    @EnumSource(NoticeCompetitor::class)
    fun `a change arriving while a notice is being delivered either waits for that delivery or makes it skip, never both`(
        competitor: NoticeCompetitor,
    ) {
        val race = prepared(competitor)

        val (changeCommittedFirst, outcome) = dataSource.connection.use { holder ->
            holder.autoCommit = false
            lockRow(holder, "information_request_party", race.notice.partyId)
            val delivery = delivering(race.notice, Instant.now())
            assertTrue(waitedForLocks(delivery, 1), "The delivery did not wait for its party")
            val change = changing(race)
            val committedFirst = committedOrWaiting(change, 2)
            holder.commit()
            change.get(30, TimeUnit.SECONDS)
            committedFirst to delivery.get(30, TimeUnit.SECONDS)
        }

        assertEquals(
            if (changeCommittedFirst) InformationRequestNoticeAttemptOutcome.SKIPPED else InformationRequestNoticeAttemptOutcome.DELIVERED,
            outcome,
            "A delivery must follow the change it saw committed, or hold that change back until it has sent",
        )
        assertEquals(if (outcome == InformationRequestNoticeAttemptOutcome.DELIVERED) 1 else 0, sentCount(race.notice))
        if (outcome == InformationRequestNoticeAttemptOutcome.SKIPPED) assertEquals(
            race.skipReason,
            lastFailureCode(race.notice)
        )
        race.assertApplied()
    }

    @ParameterizedTest
    @EnumSource(NoticeCompetitor::class)
    fun `a delivery arriving while a change is being recorded waits for it and skips the notice`(competitor: NoticeCompetitor) {
        val race = prepared(competitor)

        val (deliveryWaited, outcome) = dataSource.connection.use { blocker ->
            blocker.autoCommit = false
            lockRow(blocker, "exchange", race.exchangeId)
            val change = changing(race)
            assertTrue(waitedForLocks(change, 1), "The change did not wait for its Exchange")
            val delivery = delivering(race.notice, Instant.now())
            val waited = waitedForLocks(delivery, 2)
            blocker.commit()
            change.get(30, TimeUnit.SECONDS)
            waited to delivery.get(30, TimeUnit.SECONDS)
        }

        assertTrue(deliveryWaited, "The delivery went ahead of a change recorded before it")
        assertEquals(InformationRequestNoticeAttemptOutcome.SKIPPED, outcome)
        assertEquals(0, sentCount(race.notice))
        assertEquals(race.skipReason, lastFailureCode(race.notice))
        race.assertApplied()
    }

    @Test
    fun `a notice of a request cancelled before its delivery begins is skipped`() {
        val race = prepared(NoticeCompetitor.CANCELLATION)
        QuarkusTransaction.requiringNew().call { race.change() }

        assertEquals(InformationRequestNoticeAttemptOutcome.SKIPPED, dispatcher.deliver(race.notice.id, Instant.now()))
        assertEquals(0, sentCount(race.notice))
        assertEquals("REQUEST_FINISHED", lastFailureCode(race.notice))
    }

    @Test
    fun `a notice of an unchanged request is delivered once after waiting for its party`() {
        val fixture = reminderFixture()
        val notice = renderOne(fixture)

        val outcome = dataSource.connection.use { holder ->
            holder.autoCommit = false
            lockRow(holder, "information_request_party", notice.partyId)
            val delivery = delivering(notice, Instant.now())
            assertTrue(waitedForLocks(delivery, 1))
            holder.commit()
            delivery.get(30, TimeUnit.SECONDS)
        }

        assertEquals(InformationRequestNoticeAttemptOutcome.DELIVERED, outcome)
        assertEquals(1, sentCount(notice))
    }

    @Test
    fun `a disposal arriving while a finished request's notice is being delivered waits for that delivery and disposes the request at once`() {
        val race = prepared(NoticeCompetitor.CANCELLATION)
        sender.failuresRemaining.set(1)
        assertEquals(InformationRequestNoticeAttemptOutcome.FAILED, dispatcher.deliver(race.notice.id, Instant.now()))
        QuarkusTransaction.requiringNew().call { race.change() }
        QuarkusTransaction.requiringNew().run {
            schedules.publish(
                PublishRecordRetentionScheduleCommand(
                    RecordOwnerRef.organization(race.fixture.runtime.template.organizationId),
                    RecordPreservationResourceTypes.INFORMATION_REQUEST,
                    0,
                    0,
                    race.fixture.owner,
                ),
            )
        }
        val claim = disposals.claim(
            race.fixture.requestId,
            RecordDisposalBasis.RETENTION_SCHEDULE,
            null,
            race.fixture.owner,
        ) as InformationRequestDisposalOutcome.Claimed

        val (outcome, disposal) = dataSource.connection.use { holder ->
            holder.autoCommit = false
            lockRow(holder, "information_request_party", race.notice.partyId)
            val delivery = delivering(race.notice, Instant.now().plusSeconds(360))
            assertTrue(waitedForLocks(delivery, 1), "The delivery did not wait for its party")
            val disposing = CompletableFuture.supplyAsync(
                { withRequestContext { disposals.process(claim.view.claim.id) } },
                executor,
            )
            assertTrue(waitedForLocks(disposing, 2), "The disposal did not wait for the delivery")
            holder.commit()
            delivery.get(30, TimeUnit.SECONDS) to disposing.get(30, TimeUnit.SECONDS)
        }

        assertEquals(InformationRequestNoticeAttemptOutcome.SKIPPED, outcome)
        assertEquals(RecordDisposalState.FINALIZED, disposal)
        assertEquals(0, rowCount("information_request", race.fixture.requestId))
        assertEquals(0, rowCount("information_request_outbound_notice", race.notice.id))
    }

    private fun prepared(competitor: NoticeCompetitor): NoticeRace {
        val fixture = reminderFixture(
            if (competitor == NoticeCompetitor.EXPIRY) "EXPIRE_REQUEST" else "MARK_OVERDUE",
        )
        val notice = renderOne(fixture)
        val services = runtime.build(fixture.requestId)
        val exchangeId = requireNotNull(
            storedText(
                "SELECT exchange_id::text FROM information_request WHERE id = ?",
                fixture.requestId
            )
        )
            .let(UUID::fromString)
        return when (competitor) {
            NoticeCompetitor.CANCELLATION -> NoticeRace(fixture, notice, exchangeId, REQUEST_FINISHED, {
                services.lifecycle.cancel(
                    CancelInformationRequestCommand(
                        requestId = fixture.requestId,
                        reasonCode = "stopped.by.owner",
                        access = fixture.ownerAccess,
                        precondition = aggregatePrecondition(fixture),
                        idempotencyKey = "notice-race-cancel-${UUID.randomUUID()}",
                    ),
                )
            }) { assertEquals("CANCELLED", storedState(fixture)) }

            NoticeCompetitor.EXPIRY -> NoticeRace(fixture, notice, exchangeId, REQUEST_FINISHED, {
                processor.process(fixture.clockId, fixture.start.plus(Duration.ofMinutes(61)))
            }) { assertEquals("EXPIRED", storedState(fixture)) }

            NoticeCompetitor.SATISFACTION -> {
                val etag = attested(services, fixture)
                NoticeRace(fixture, notice, exchangeId, REQUEST_FINISHED, {
                    services.submissions.submit(
                        SubmitInformationRequestPackageCommand(
                            requestId = fixture.requestId,
                            access = party(fixture.runtime.contributorUserId),
                            precondition = CommandPrecondition.ExpectedRevision(etag),
                            idempotencyKey = "notice-race-submit-${UUID.randomUUID()}",
                        ),
                    )
                }) { assertEquals("CLOSED", storedState(fixture)) }
            }

            NoticeCompetitor.SUPERSESSION -> {
                val replacementId = replacementRequest(fixture)
                NoticeRace(fixture, notice, exchangeId, REQUEST_FINISHED, {
                    services.lifecycle.supersede(
                        SupersedeInformationRequestCommand(
                            requestId = fixture.requestId,
                            supersededByRequestId = replacementId,
                            reasonCode = "replaced.by.owner",
                            access = fixture.ownerAccess,
                            precondition = aggregatePrecondition(fixture),
                            idempotencyKey = "notice-race-supersede-${UUID.randomUUID()}",
                        ),
                    )
                }) { assertEquals("SUPERSEDED", storedState(fixture)) }
            }

            NoticeCompetitor.PARENT_TERMINATION -> NoticeRace(fixture, notice, exchangeId, PARENT_CLOSED, {
                requireNotNull(exchangeLookup.lockExchangeForUpdate(exchangeId))
                exchangeRepository.updateStatus(exchangeId, ExchangeStatus.RESCINDED)
            }) {
                assertEquals("RESCINDED", storedText("SELECT status FROM exchange WHERE id = ?", exchangeId))
                assertEquals("ISSUED", storedState(fixture))
            }

            NoticeCompetitor.REASSIGNMENT -> {
                val replacement = reassignable(fixture, notice)
                NoticeRace(fixture, notice, exchangeId, RECIPIENT_REASSIGNED, {
                    partyRevocations.reassign(
                        ReassignInformationRequestPartyCommand(
                            requestId = fixture.requestId,
                            partyId = notice.partyId,
                            principal = PrincipalRef.user(replacement),
                            access = fixture.ownerAccess,
                            precondition = CommandPrecondition.ExpectedRevision(
                                InformationRequestETag.partiesOf(requireNotNull(requestRepository.findById(fixture.requestId))),
                            ),
                            idempotencyKey = "notice-race-reassign-${UUID.randomUUID()}",
                        ),
                    )
                }) {
                    assertEquals(
                        replacement.toString(),
                        storedText(
                            "SELECT principal_id::text FROM information_request_party WHERE id = ?",
                            notice.partyId
                        ),
                    )
                }
            }
        }
    }

    private fun reminderFixture(dueEffect: String = "MARK_OVERDUE"): NoticeRaceFixture {
        val versionId = UUID.randomUUID()
        val runtimeFixture = dataSource.connection.use { connection ->
            SubmissionRuntimeSqlFixture(connection).also { fixture ->
                ClockSqlFixture(connection, fixture).apply {
                    insertVersion(
                        versionId,
                        versionNumber = 2,
                        clockType = "CALENDAR",
                        standard = 60,
                        urgent = 60,
                        escalation = null,
                        dueEffect = dueEffect,
                    )
                    insertReminder(versionId, ordinal = 1, minutes = 30)
                }
            }
        }
        val start = Instant.parse("2026-09-25T08:00:00Z")
        val owner = RequestAccessContext(
            PrincipalRef.user(runtimeFixture.template.userId),
            AuthorizationContext(sessionRef = "owner")
        )
        val clocks = InformationRequestClockService(
            runtime.build(runtimeFixture.requestId).gate, clockRepository, eventRepository, policies, recorder,
            runtime.commandReceiptService, Clock.fixed(start, ZoneOffset.UTC),
        )
        val started = QuarkusTransaction.requiringNew().call {
            clocks.start(
                StartInformationRequestClockCommand(
                    requestId = runtimeFixture.requestId,
                    clockKey = "response",
                    policyVersionId = versionId,
                    urgency = InformationRequestClockUrgency.STANDARD,
                    receivedAt = start,
                    access = owner,
                    idempotencyKey = "notice-race-clock",
                ),
            )
        }
        assertTrue(processor.process(started.clock.id, start.plus(Duration.ofMinutes(31))))
        return NoticeRaceFixture(runtimeFixture, started.clock.id, start, owner)
    }

    private fun renderOne(fixture: NoticeRaceFixture): InformationRequestOutboundNotice {
        val intentId = QuarkusTransaction.requiringNew().call {
            intentRepository.findUnclaimedIdsForRequest(fixture.requestId).first()
        }
        val notice = requireNotNull(dispatcher.claimAndRender(intentId, Instant.now()))
        assertEquals(InformationRequestNoticeEndpointState.RESOLVED, notice.endpointState)
        return notice
    }

    private fun attested(services: InformationRequestRuntimeServices, fixture: NoticeRaceFixture): String =
        QuarkusTransaction.requiringNew().call {
            services.attestations.record(
                RecordInformationRequestSubmissionAttestationCommand(
                    requestId = fixture.requestId,
                    requirementId = fixture.runtime.attestationRequirementId,
                    decision = InformationRequestAttestationDecision.ASSENTED,
                    access = party(fixture.runtime.attestorUserId),
                    precondition = CommandPrecondition.ExpectedRevision(
                        QuarkusTransaction.requiringNew().call {
                            val request = requireNotNull(requestRepository.findById(fixture.requestId))
                            InformationRequestETag.submissionOf(
                                null,
                                runtime.contentCollector.collect(request, null).contentHash
                            )
                        },
                    ),
                    idempotencyKey = "notice-race-assent-${UUID.randomUUID()}",
                ),
            )
        }.submissionETag

    private fun replacementRequest(fixture: NoticeRaceFixture): UUID {
        val replacementId = UUID.randomUUID()
        dataSource.connection.use { connection ->
            execute(
                connection,
                """
                INSERT INTO information_request
                    (id, exchange_id, template_version_id, owner_type, owner_organization_id, state,
                     gates_exchange_closure, aggregate_revision, party_revision, created_at, updated_at, issued_at)
                SELECT ?, exchange_id, template_version_id, owner_type, owner_organization_id, 'ISSUED',
                       TRUE, 1, 1, now(), now(), now()
                FROM information_request WHERE id = ?
                """.trimIndent(),
                replacementId,
                fixture.requestId,
            )
        }
        return replacementId
    }

    private fun reassignable(fixture: NoticeRaceFixture, notice: InformationRequestOutboundNotice): UUID =
        dataSource.connection.use { connection ->
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
                fixture.runtime.template.userId,
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
                fixture.runtime.template.organizationId,
            )
            holder
        }

    private fun aggregatePrecondition(fixture: NoticeRaceFixture) =
        CommandPrecondition.ExpectedRevision(
            InformationRequestETag.aggregateOf(requireNotNull(requestRepository.findById(fixture.requestId))),
        )

    private fun delivering(notice: InformationRequestOutboundNotice, at: Instant) =
        CompletableFuture.supplyAsync({ withRequestContext { dispatcher.deliver(notice.id, at) } }, executor)

    private fun changing(race: NoticeRace) =
        CompletableFuture.supplyAsync({ OverlappingCommandRace(dataSource).inNewTransaction(race.change) }, executor)

    private fun waitedForLocks(pending: CompletableFuture<*>, waiters: Int): Boolean =
        OverlappingCommandRace(dataSource).awaitLockWaiters(waiters, listOf(pending))

    private fun committedOrWaiting(change: CompletableFuture<*>, waiters: Int): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (!change.isDone && System.nanoTime() < deadline && lockWaiters() < waiters) {
            Thread.sleep(25)
        }
        return change.isDone
    }

    private fun lockWaiters(): Int =
        dataSource.connection.use {
            queryInt(
                it,
                "SELECT COUNT(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock'"
            )
        }

    private fun lockRow(connection: Connection, table: String, id: UUID) {
        queryString(connection, "SELECT CAST(id AS text) FROM $table WHERE id = ? FOR UPDATE", id)
    }

    private fun <T> withRequestContext(block: () -> T): T {
        val context = Arc.container().requestContext()
        context.activate()
        try {
            return block()
        } finally {
            context.terminate()
        }
    }

    private fun sentCount(notice: InformationRequestOutboundNotice): Int = sender.sent.count { it == notice.id }

    private fun lastFailureCode(notice: InformationRequestOutboundNotice): String? =
        QuarkusTransaction.requiringNew().call { attemptRepository.findForNotice(notice.id).last().failureCode }

    private fun storedState(fixture: NoticeRaceFixture): String? =
        storedText("SELECT state FROM information_request WHERE id = ?", fixture.requestId)

    private fun storedText(sql: String, id: UUID): String? =
        dataSource.connection.use { queryString(it, sql, id) }

    private fun rowCount(table: String, id: UUID): Int =
        dataSource.connection.use { queryInt(it, "SELECT count(*) FROM $table WHERE id = ?", id) }

    private fun party(userId: UUID) =
        RequestAccessContext(PrincipalRef.user(userId), AuthorizationContext(sessionRef = "party-session"))

    enum class NoticeCompetitor {
        CANCELLATION,
        EXPIRY,
        SATISFACTION,
        SUPERSESSION,
        PARENT_TERMINATION,
        REASSIGNMENT,
    }

    private class NoticeRace(
        val fixture: NoticeRaceFixture,
        val notice: InformationRequestOutboundNotice,
        val exchangeId: UUID,
        val skipReason: String,
        val change: () -> Any?,
        val assertApplied: () -> Unit,
    )

    private companion object {
        const val REQUEST_FINISHED = "REQUEST_FINISHED"
        const val PARENT_CLOSED = "PARENT_CLOSED"
        const val RECIPIENT_REASSIGNED = "RECIPIENT_REASSIGNED"
    }
}
