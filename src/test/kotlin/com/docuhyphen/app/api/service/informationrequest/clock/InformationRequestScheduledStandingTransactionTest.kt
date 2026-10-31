package com.docuhyphen.app.api.service.informationrequest.clock

import com.docuhyphen.app.api.migration.ClockSqlFixture
import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.clock.StartInformationRequestClockCommand
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.amendment.InformationRequestNoticeIntentRepository
import com.docuhyphen.app.api.repository.informationrequest.clock.InformationRequestClockEventRepository
import com.docuhyphen.app.api.repository.informationrequest.clock.InformationRequestClockRepository
import com.docuhyphen.app.api.repository.informationrequest.lifecycle.InformationRequestTransitionRepository
import com.docuhyphen.app.api.repository.informationrequest.notice.InformationRequestNoticeDeliveryAttemptRepository
import com.docuhyphen.app.api.repository.informationrequest.notice.InformationRequestOutboundNoticeRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.clock.model.ScheduledStandingScenario
import com.docuhyphen.app.api.service.informationrequest.notice.InformationRequestNoticeDispatcher
import com.docuhyphen.app.api.service.informationrequest.notice.InformationRequestNoticeWorker
import com.docuhyphen.app.api.service.informationrequest.notice.RecordingInformationRequestNoticeSender
import com.docuhyphen.app.api.service.informationrequest.oversight.InformationRequestOperationsPostgreSQLResource
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(InformationRequestOperationsPostgreSQLResource::class)
class InformationRequestScheduledStandingTransactionTest {
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
    lateinit var requestRepository: InformationRequestRepository

    @Inject
    lateinit var transitionRepository: InformationRequestTransitionRepository

    @Inject
    lateinit var intentRepository: InformationRequestNoticeIntentRepository

    @Inject
    lateinit var worker: InformationRequestNoticeWorker

    @Inject
    lateinit var dispatcher: InformationRequestNoticeDispatcher

    @Inject
    lateinit var noticeRepository: InformationRequestOutboundNoticeRepository

    @Inject
    lateinit var attemptRepository: InformationRequestNoticeDeliveryAttemptRepository

    @Inject
    lateinit var sender: RecordingInformationRequestNoticeSender

    @BeforeEach
    fun resetSender() {
        sender.failuresRemaining.set(0)
    }

    @Test
    fun `an active issued request records its reminder and sends its notice`() {
        val fixture = fixture()

        process(fixture, REMINDER_POINT)
        val sentBefore = sender.sent.size
        worker.dispatchForRequest(fixture.requestId)

        assertEquals(
            listOf(InformationRequestClockEventKind.STARTED, InformationRequestClockEventKind.REMINDED),
            events(fixture)
        )
        assertTrue(InformationRequestMutation.RECORD_REMINDER in mutations(fixture))
        assertTrue(sender.sent.size > sentBefore)
    }

    @Test
    fun `reminder, overdue, escalation and expiry points all wait while the owner is operationally suspended`() {
        val fixture = fixture(dueEffect = "EXPIRE_REQUEST")
        suspendOwner(fixture)

        HELD_PASSES.forEach { minutes -> process(fixture, minutes) }

        assertNothingScheduledHappened(fixture)
        assertTrue(
            clock(fixture).nextPointAt!!.toInstant().isAfter(fixture.start.plusSeconds(HELD_PASSES.last() * MINUTE))
        )
    }

    @Test
    fun `a reminder that waited through a suspension goes out at the next pass while the request is not yet due`() {
        val fixture = fixture()
        suspendOwner(fixture)
        process(fixture, REMINDER_POINT)
        assertEquals(listOf(InformationRequestClockEventKind.STARTED), events(fixture))

        restoreOwner(fixture)
        process(fixture, REMINDER_POINT + DEFERRAL_MINUTES)

        assertEquals(
            listOf(InformationRequestClockEventKind.STARTED, InformationRequestClockEventKind.REMINDED),
            events(fixture)
        )
        assertEquals(setOf(InformationRequestNoticeKind.RESPONSE_REMINDER), intentKinds(fixture))
    }

    @Test
    fun `a deadline that passed during a suspension takes effect at the first pass after it ends`() {
        val fixture = fixture()
        suspendOwner(fixture)
        HELD_PASSES.forEach { minutes -> process(fixture, minutes) }

        restoreOwner(fixture)
        process(fixture, HELD_PASSES.last() + DEFERRAL_MINUTES)

        assertEquals(
            listOf(
                InformationRequestClockEventKind.STARTED,
                InformationRequestClockEventKind.OVERDUE,
                InformationRequestClockEventKind.ESCALATED,
            ),
            events(fixture),
        )
        assertEquals(setOf(InformationRequestNoticeKind.RESPONSE_OVERDUE), intentKinds(fixture))
    }

    @Test
    fun `an expiring policy whose deadline passed during a suspension expires the request once the suspension ends`() {
        val fixture = fixture(dueEffect = "EXPIRE_REQUEST")
        suspendOwner(fixture)
        HELD_PASSES.forEach { minutes -> process(fixture, minutes) }
        assertEquals(InformationRequestState.ISSUED, state(fixture))

        restoreOwner(fixture)
        process(fixture, HELD_PASSES.last() + DEFERRAL_MINUTES)

        assertEquals(InformationRequestState.EXPIRED, state(fixture))
        assertEquals(InformationRequestClockState.STOPPED, clock(fixture).state)
    }

    @Test
    fun `a revoked grant stops every scheduled point of its request for good`() {
        val fixture = fixture(dueEffect = "EXPIRE_REQUEST")
        revokeGrant(fixture)

        (HELD_PASSES + (HELD_PASSES.last() + DEFERRAL_MINUTES)).forEach { minutes -> process(fixture, minutes) }

        assertNothingScheduledHappened(fixture)
    }

    @Test
    fun `a grant that does not list sending reminders holds its request's scheduled points`() {
        val fixture = fixture(dueEffect = "EXPIRE_REQUEST", continuationActions = WITHOUT_REMINDERS)

        HELD_PASSES.forEach { minutes -> process(fixture, minutes) }

        assertNothingScheduledHappened(fixture)
    }

    @Test
    fun `queued reminder notices are skipped and nothing is sent while the owner is suspended`() {
        val fixture = fixture()
        process(fixture, REMINDER_POINT)
        suspendOwner(fixture)
        val sentBefore = sender.sent.size

        worker.dispatchForRequest(fixture.requestId)

        assertEquals(sentBefore, sender.sent.size)
        assertEquals(setOf("OPERATIONALLY_SUSPENDED"), failureCodes(fixture))
    }

    @Test
    fun `a failed notice is not retried while the owner is suspended`() {
        val fixture = fixture()
        process(fixture, REMINDER_POINT)
        sender.failuresRemaining.set(FAILURES)
        worker.dispatchForRequest(fixture.requestId)
        sender.failuresRemaining.set(0)
        suspendOwner(fixture)
        val sentBefore = sender.sent.size

        val outcomes = notices(fixture).map { dispatcher.deliver(it, Instant.now().plusSeconds(RETRY_AFTER_SECONDS)) }

        assertEquals(setOf(InformationRequestNoticeAttemptOutcome.SKIPPED), outcomes.toSet())
        assertEquals(sentBefore, sender.sent.size)
        assertEquals(setOf("OPERATIONALLY_SUSPENDED"), failureCodes(fixture) - DELIVERY_FAILURE)
    }

    @Test
    fun `queued notices of a request whose grant was revoked are skipped`() {
        val fixture = fixture()
        process(fixture, REMINDER_POINT)
        revokeGrant(fixture)
        val sentBefore = sender.sent.size

        worker.dispatchForRequest(fixture.requestId)

        assertEquals(sentBefore, sender.sent.size)
        assertEquals(setOf("EXECUTION_GRANT_REVOKED"), failureCodes(fixture))
    }

    @Test
    fun `an expiring deadline still sends the overdue notice it owes after the request expires`() {
        val fixture = fixture(dueEffect = "EXPIRE_REQUEST")
        process(fixture, DUE_MINUTES)
        assertEquals(InformationRequestState.EXPIRED, state(fixture))
        val sentBefore = sender.sent.size

        worker.dispatchForRequest(fixture.requestId)

        assertEquals(RESPONDING_PARTIES, sender.sent.size - sentBefore)
        assertEquals(mapOf(InformationRequestNoticeKind.RESPONSE_OVERDUE to setOf(DELIVERED)), lastAttempts(fixture))
    }

    @Test
    fun `an expiring deadline that passed during a suspension sends its overdue notice once the suspension ends`() {
        val fixture = fixture(dueEffect = "EXPIRE_REQUEST")
        suspendOwner(fixture)
        HELD_PASSES.forEach { minutes -> process(fixture, minutes) }
        restoreOwner(fixture)
        process(fixture, HELD_PASSES.last() + DEFERRAL_MINUTES)
        assertEquals(InformationRequestState.EXPIRED, state(fixture))
        val sentBefore = sender.sent.size

        worker.dispatchForRequest(fixture.requestId)

        assertEquals(RESPONDING_PARTIES, sender.sent.size - sentBefore)
        assertEquals(mapOf(InformationRequestNoticeKind.RESPONSE_OVERDUE to setOf(DELIVERED)), lastAttempts(fixture))
    }

    @Test
    fun `a failed overdue notice of an expired request is retried until it is delivered`() {
        val fixture = fixture(dueEffect = "EXPIRE_REQUEST")
        process(fixture, DUE_MINUTES)
        sender.failuresRemaining.set(RESPONDING_PARTIES)
        worker.dispatchForRequest(fixture.requestId)
        assertEquals(
            mapOf(InformationRequestNoticeKind.RESPONSE_OVERDUE to setOf(DELIVERY_FAILURE)),
            lastAttempts(fixture),
        )
        val sentBefore = sender.sent.size

        val outcomes = notices(fixture).map { dispatcher.deliver(it, Instant.now().plusSeconds(RETRY_AFTER_SECONDS)) }

        assertEquals(setOf(InformationRequestNoticeAttemptOutcome.DELIVERED), outcomes.toSet())
        assertEquals(RESPONDING_PARTIES, sender.sent.size - sentBefore)
    }

    @Test
    fun `a reminder still queued when the request expired is skipped as finished while its overdue notice goes out`() {
        val fixture = fixture(dueEffect = "EXPIRE_REQUEST")
        process(fixture, REMINDER_POINT)
        process(fixture, DUE_MINUTES)
        val sentBefore = sender.sent.size

        worker.dispatchForRequest(fixture.requestId)

        assertEquals(RESPONDING_PARTIES, sender.sent.size - sentBefore)
        assertEquals(
            mapOf(
                InformationRequestNoticeKind.RESPONSE_REMINDER to setOf("REQUEST_FINISHED"),
                InformationRequestNoticeKind.RESPONSE_OVERDUE to setOf(DELIVERED),
            ),
            lastAttempts(fixture),
        )
    }

    @Test
    fun `the overdue notice of an expired request still stops for a removed party and while the owner is suspended`() {
        val fixture = fixture(dueEffect = "EXPIRE_REQUEST")
        process(fixture, DUE_MINUTES)
        deactivateParty(fixture.contributorPartyId)
        suspendOwner(fixture)
        val sentBefore = sender.sent.size

        worker.dispatchForRequest(fixture.requestId)

        assertEquals(sentBefore, sender.sent.size)
        assertEquals(
            mapOf(InformationRequestNoticeKind.RESPONSE_OVERDUE to setOf("PARTY_INACTIVE", "OPERATIONALLY_SUSPENDED")),
            lastAttempts(fixture),
        )
    }

    @Test
    fun `an overdue notice is not sent once its request was cancelled or expired by another deadline`() {
        listOf(
            "UPDATE information_request SET state = 'CANCELLED', cancelled_at = now() WHERE id = ?",
            "UPDATE information_request SET state = 'EXPIRED', expired_at = now() WHERE id = ?",
        ).forEach { finish ->
            val fixture = fixture()
            process(fixture, DUE_MINUTES)
            dataSource.connection.use { connection -> execute(connection, finish, fixture.requestId) }
            val sentBefore = sender.sent.size

            worker.dispatchForRequest(fixture.requestId)

            assertEquals(sentBefore, sender.sent.size, finish)
            assertEquals(
                mapOf(InformationRequestNoticeKind.RESPONSE_OVERDUE to setOf("REQUEST_FINISHED")),
                lastAttempts(fixture),
                finish,
            )
        }
    }

    private fun assertNothingScheduledHappened(fixture: ScheduledStandingScenario) {
        assertEquals(listOf(InformationRequestClockEventKind.STARTED), events(fixture))
        assertEquals(InformationRequestClockState.RUNNING, clock(fixture).state)
        assertEquals(InformationRequestState.ISSUED, state(fixture))
        assertEquals(emptySet<InformationRequestNoticeKind>(), intentKinds(fixture))
        val scheduledMutations = setOf(
            InformationRequestMutation.RECORD_REMINDER,
            InformationRequestMutation.RECORD_OVERDUE,
            InformationRequestMutation.RECORD_ESCALATION,
            InformationRequestMutation.EXPIRE,
        )
        assertFalse(mutations(fixture).any { it in scheduledMutations }, mutations(fixture).toString())
    }

    private fun fixture(
        dueEffect: String = "MARK_OVERDUE",
        continuationActions: String? = null,
    ): ScheduledStandingScenario {
        val versionId = UUID.randomUUID()
        val runtimeFixture = dataSource.connection.use { connection ->
            val runtimeFixture = SubmissionRuntimeSqlFixture(connection)
            val clocks = ClockSqlFixture(connection, runtimeFixture)
            clocks.insertVersion(
                versionId,
                versionNumber = 2,
                clockType = "CALENDAR",
                standard = DUE_MINUTES.toInt(),
                urgent = DUE_MINUTES.toInt(),
                escalation = ESCALATION_MINUTES,
                dueEffect = dueEffect,
            )
            clocks.insertReminder(versionId, ordinal = 1, minutes = (DUE_MINUTES - REMINDER_POINT).toInt())
            execute(
                connection,
                """
                INSERT INTO request_execution_grant
                    (id, request_id, owner_type, owner_organization_id, plan_code, subscription_status,
                     enforcement_mode, acting_party_cap, issued_at, created_at, continuation_actions)
                VALUES (?, ?, 'ORGANIZATION', ?, 'BUSINESS', 'ACTIVE', 'ENFORCE', 10, now(), now(),
                        COALESCE(CAST(? AS varchar(64)[]),
                                 ARRAY['RESPOND', 'SUBMIT', 'ATTEST', 'REVIEW', 'ASSIGN_PARTY_WITHIN_CAP',
                                       'UPLOAD_WITHIN_ALLOWANCE', 'NON_EXPANDING_AMENDMENT', 'SEND_REMINDER',
                                       'CANCEL', 'SUPERSEDE']::varchar(64)[]))
                """.trimIndent(),
                UUID.randomUUID(),
                runtimeFixture.requestId,
                runtimeFixture.template.organizationId,
                continuationActions,
            )
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
                        AuthorizationContext(sessionRef = "owner"),
                    ),
                    idempotencyKey = "start-response",
                ),
            )
        }
        return ScheduledStandingScenario(
            runtimeFixture.requestId,
            runtimeFixture.template.organizationId,
            started.clock.id,
            start,
            runtimeFixture.contributorPartyId,
        )
    }

    private fun process(fixture: ScheduledStandingScenario, minutes: Long) {
        processor.process(fixture.clockId, fixture.start.plusSeconds(minutes * MINUTE))
    }

    private fun suspendOwner(fixture: ScheduledStandingScenario) = setOwnerStatus(fixture, "SUSPENDED")

    private fun restoreOwner(fixture: ScheduledStandingScenario) = setOwnerStatus(fixture, "ACTIVE")

    private fun setOwnerStatus(fixture: ScheduledStandingScenario, status: String) {
        dataSource.connection.use { connection ->
            execute(
                connection,
                "DELETE FROM organization_subscription_policy WHERE organization_id = ?",
                fixture.organizationId
            )
            execute(
                connection,
                """
                INSERT INTO organization_subscription_policy
                    (id, organization_id, tier_code, subscription_status, max_users, current_period_end, created_date, updated_date)
                VALUES (?, ?, 'BUSINESS', ?, 5, now() + interval '30 days', now(), now())
                """.trimIndent(),
                UUID.randomUUID(),
                fixture.organizationId,
                status,
            )
        }
    }

    private fun revokeGrant(fixture: ScheduledStandingScenario) {
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE request_execution_grant SET revoked_at = now(), revoked_reason = 'process.stopped' WHERE request_id = ?",
                fixture.requestId,
            )
        }
    }

    private fun deactivateParty(partyId: UUID) {
        dataSource.connection.use { connection ->
            execute(connection, "UPDATE information_request_party SET active = FALSE WHERE id = ?", partyId)
        }
    }

    private fun events(fixture: ScheduledStandingScenario): List<InformationRequestClockEventKind> =
        QuarkusTransaction.requiringNew().call { eventRepository.findForClock(fixture.clockId).map { it.eventKind } }

    private fun clock(fixture: ScheduledStandingScenario) =
        QuarkusTransaction.requiringNew().call { requireNotNull(clockRepository.findById(fixture.clockId)) }

    private fun state(fixture: ScheduledStandingScenario): InformationRequestState =
        QuarkusTransaction.requiringNew().call { requireNotNull(requestRepository.findById(fixture.requestId)).state }

    private fun mutations(fixture: ScheduledStandingScenario): List<InformationRequestMutation> =
        QuarkusTransaction.requiringNew()
            .call { transitionRepository.findForRequest(fixture.requestId).map { it.mutation } }

    private fun intentKinds(fixture: ScheduledStandingScenario): Set<InformationRequestNoticeKind> =
        QuarkusTransaction.requiringNew()
            .call { intentRepository.findForRequest(fixture.requestId).map { it.noticeKind }.toSet() }

    private fun notices(fixture: ScheduledStandingScenario): List<UUID> =
        QuarkusTransaction.requiringNew().call { noticeRepository.findForRequest(fixture.requestId).map { it.id } }

    private fun failureCodes(fixture: ScheduledStandingScenario): Set<String> =
        QuarkusTransaction.requiringNew().call {
            noticeRepository.findForRequest(fixture.requestId)
                .flatMap { notice -> attemptRepository.findForNotice(notice.id) }
                .mapNotNull { it.failureCode }
                .toSet()
        }

    private fun lastAttempts(fixture: ScheduledStandingScenario): Map<InformationRequestNoticeKind, Set<String>> =
        QuarkusTransaction.requiringNew().call {
            val kinds = intentRepository.findForRequest(fixture.requestId).associate { it.id to it.noticeKind }
            noticeRepository.findForRequest(fixture.requestId)
                .groupBy({ notice -> kinds.getValue(notice.noticeIntentId) }) { notice ->
                    attemptRepository.findForNotice(notice.id).maxByOrNull { it.attemptNumber }
                        ?.let { it.failureCode ?: it.outcome.name }
                        ?: QUEUED
                }
                .mapValues { it.value.toSet() }
        }

    private companion object {
        const val MINUTE = 60L
        const val DUE_MINUTES = 240L
        const val REMINDER_POINT = 121L
        const val ESCALATION_MINUTES = 30
        const val DEFERRAL_MINUTES = 60L
        const val FAILURES = 10
        const val RETRY_AFTER_SECONDS = 3600L
        const val DELIVERY_FAILURE = "IllegalStateException"
        const val DELIVERED = "DELIVERED"
        const val QUEUED = "QUEUED"
        const val RESPONDING_PARTIES = 2
        const val WITHOUT_REMINDERS =
            "{RESPOND,SUBMIT,ATTEST,REVIEW,ASSIGN_PARTY_WITHIN_CAP,UPLOAD_WITHIN_ALLOWANCE,NON_EXPANDING_AMENDMENT,CANCEL,SUPERSEDE}"
        val HELD_PASSES = listOf(REMINDER_POINT, REMINDER_POINT + 60, REMINDER_POINT + 120, REMINDER_POINT + 180)
    }
}
