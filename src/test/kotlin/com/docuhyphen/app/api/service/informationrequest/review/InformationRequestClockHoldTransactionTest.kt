package com.docuhyphen.app.api.service.informationrequest.review

import com.docuhyphen.app.api.migration.ClockSqlFixture
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.clock.ChangeInformationRequestClockCommand
import com.docuhyphen.app.api.model.informationrequest.clock.InformationRequestClockChange
import com.docuhyphen.app.api.model.informationrequest.clock.InformationRequestClockView
import com.docuhyphen.app.api.model.informationrequest.clock.StartInformationRequestClockCommand
import com.docuhyphen.app.api.model.informationrequest.submission.WithdrawInformationRequestPackageCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.amendment.InformationRequestNoticeIntentRepository
import com.docuhyphen.app.api.repository.informationrequest.clock.InformationRequestClockEventRepository
import com.docuhyphen.app.api.repository.informationrequest.clock.InformationRequestClockRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewRepository
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.clock.InformationRequestClockPointProcessor
import com.docuhyphen.app.api.service.informationrequest.clock.InformationRequestClockPolicyService
import com.docuhyphen.app.api.service.informationrequest.clock.InformationRequestClockRecorder
import com.docuhyphen.app.api.service.informationrequest.clock.InformationRequestClockService
import com.docuhyphen.app.api.service.informationrequest.review.model.ReviewFixture
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestClockHoldTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    @Inject
    lateinit var reviewRepository: InformationRequestReviewRepository

    @Inject
    lateinit var clockRepository: InformationRequestClockRepository

    @Inject
    lateinit var eventRepository: InformationRequestClockEventRepository

    @Inject
    lateinit var intentRepository: InformationRequestNoticeIntentRepository

    @Inject
    lateinit var policies: InformationRequestClockPolicyService

    @Inject
    lateinit var recorder: InformationRequestClockRecorder

    @Inject
    lateinit var processor: InformationRequestClockPointProcessor

    private val support by lazy {
        InformationRequestReviewTestSupport(
            dataSource,
            runtime,
            requestRepository,
            reviewRepository
        )
    }

    @Test
    fun `a complete submission holds running clocks until a returned item resumes them with the time left, and a manual pause stays`() {
        val fixture = support.fixture()
        val versionId = calendarVersion(fixture)
        val services = runtime.build(fixture.requestId)
        val running = start(fixture, versionId, "response")
        val manual = start(fixture, versionId, "manual")
        pause(fixture, manual)

        val submittedFrom = Instant.now()
        val firstPackage = support.submitWhole(services, fixture)
        val submittedTo = Instant.now()

        val held = clockOf(running.clock.id)
        assertEquals(InformationRequestClockState.PAUSED, held.state)
        assertFalse(requireNotNull(held.remainingSeconds) > secondsUntil(running, submittedFrom) + 1)
        assertFalse(requireNotNull(held.remainingSeconds) < secondsUntil(running, submittedTo) - 1)
        assertEquals("SUBMISSION_COMPLETE", lastEventReason(running.clock.id, InformationRequestClockEventKind.PAUSED))
        assertEquals(InformationRequestClockState.PAUSED, clockOf(manual.clock.id).state)

        val review = support.reviewsOf(fixture).single()
        val assigned = support.assign(services, fixture, review)
        support.finding(
            services, fixture, review, support.item(fixture, firstPackage, fixture.documentRequirementId),
            InformationRequestFindingCorrectionScope.RESPONSE,
        )
        val returnedFrom = Instant.now()
        val returned = support.record(
            services,
            fixture,
            assigned,
            support.draft(services, fixture, assigned, InformationRequestReviewOutcome.CHANGES_REQUIRED),
            "return-held-clock",
        )
        val returnedTo = Instant.now()

        assertEquals(InformationRequestReviewState.CHANGES_REQUESTED, returned.review.state)
        val resumed = clockOf(running.clock.id)
        assertEquals(InformationRequestClockState.RUNNING, resumed.state)
        assertFalse(
            resumed.dueAt.toInstant()
                .isBefore(returnedFrom.minusMillis(1).plusSeconds(requireNotNull(held.remainingSeconds)))
        )
        assertFalse(
            resumed.dueAt.toInstant()
                .isAfter(returnedTo.plusMillis(1).plusSeconds(requireNotNull(held.remainingSeconds)))
        )
        assertEquals("RESPONSE_REOPENED", lastEventReason(running.clock.id, InformationRequestClockEventKind.RESUMED))
        assertEquals(InformationRequestClockState.PAUSED, clockOf(manual.clock.id).state)
        assertEquals("respondent.requested", lastEventReason(manual.clock.id, InformationRequestClockEventKind.PAUSED))

        processor.process(running.clock.id, resumed.dueAt.toInstant().minusSeconds(REMINDER_MINUTES * MINUTE / 2))

        assertEquals(
            setOf(fixture.runtime.contributorPartyId),
            QuarkusTransaction.requiringNew()
                .call { intentRepository.findForRequest(fixture.requestId).map { it.partyId }.toSet() },
        )
    }

    @Test
    fun `withdrawing a complete submission before its review starts resumes the held clock`() {
        val fixture = support.fixture()
        val versionId = calendarVersion(fixture)
        val services = runtime.build(fixture.requestId)
        val running = start(fixture, versionId, "response")
        val packageId = support.submitWhole(services, fixture)
        assertEquals(InformationRequestClockState.PAUSED, clockOf(running.clock.id).state)

        QuarkusTransaction.requiringNew().run {
            services.submissions.withdraw(
                WithdrawInformationRequestPackageCommand(
                    requestId = fixture.requestId,
                    packageId = packageId,
                    access = support.contributor(fixture),
                    precondition = CommandPrecondition.ExpectedRevision(support.responseETag(fixture)),
                    idempotencyKey = "withdraw-held-clock",
                ),
            )
        }

        assertEquals(InformationRequestClockState.RUNNING, clockOf(running.clock.id).state)
        assertEquals("RESPONSE_REOPENED", lastEventReason(running.clock.id, InformationRequestClockEventKind.RESUMED))
    }

    private fun calendarVersion(fixture: ReviewFixture): UUID =
        dataSource.connection.use { connection ->
            val version = UUID.randomUUID()
            val clocks = ClockSqlFixture(connection, fixture.runtime)
            clocks.insertVersion(
                version,
                versionNumber = 2,
                clockType = "CALENDAR",
                standard = 600,
                urgent = 300,
                escalation = null
            )
            clocks.insertReminder(version, ordinal = 1, minutes = REMINDER_MINUTES.toInt())
            version
        }

    private fun clockService(fixture: ReviewFixture) = InformationRequestClockService(
        runtime.build(fixture.requestId).gate, clockRepository, eventRepository, policies, recorder,
        runtime.commandReceiptService, Clock.systemUTC(),
    )

    private fun start(fixture: ReviewFixture, versionId: UUID, key: String): InformationRequestClockView =
        QuarkusTransaction.requiringNew().call {
            clockService(fixture).start(
                StartInformationRequestClockCommand(
                    requestId = fixture.requestId,
                    clockKey = key,
                    policyVersionId = versionId,
                    urgency = InformationRequestClockUrgency.STANDARD,
                    receivedAt = Instant.now(),
                    access = support.owner(fixture),
                    idempotencyKey = "start-$key",
                ),
            )
        }

    private fun pause(fixture: ReviewFixture, started: InformationRequestClockView) {
        QuarkusTransaction.requiringNew().run {
            clockService(fixture).change(
                ChangeInformationRequestClockCommand(
                    requestId = fixture.requestId,
                    clockId = started.clock.id,
                    change = InformationRequestClockChange.PAUSE,
                    extensionMinutes = null,
                    reasonCode = "respondent.requested",
                    access = support.owner(fixture),
                    precondition = CommandPrecondition.ExpectedRevision(started.clockETag),
                    idempotencyKey = "pause-${started.clock.clockKey}",
                ),
            )
        }
    }

    private fun clockOf(clockId: UUID) =
        QuarkusTransaction.requiringNew().call { requireNotNull(clockRepository.findById(clockId)) }

    private fun lastEventReason(clockId: UUID, kind: InformationRequestClockEventKind): String? =
        QuarkusTransaction.requiringNew()
            .call { eventRepository.findForClock(clockId).last { it.eventKind == kind }.reasonCode }

    private fun secondsUntil(started: InformationRequestClockView, at: Instant): Long =
        started.clock.dueAt.toInstant().epochSecond - at.epochSecond

    private companion object {
        const val MINUTE = 60L
        const val REMINDER_MINUTES = 60L
    }
}
