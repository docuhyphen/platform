package com.docuhyphen.app.api.service.informationrequest.clock

import com.docuhyphen.app.api.migration.ClockSqlFixture
import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.model.entity.InformationRequestClockEventKind
import com.docuhyphen.app.api.model.entity.InformationRequestClockState
import com.docuhyphen.app.api.model.entity.InformationRequestClockUrgency
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.clock.ChangeInformationRequestClockCommand
import com.docuhyphen.app.api.model.informationrequest.clock.InformationRequestClockChange
import com.docuhyphen.app.api.model.informationrequest.clock.StartInformationRequestClockCommand
import com.docuhyphen.app.api.model.informationrequest.lifecycle.CancelInformationRequestCommand
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.model.informationrequest.oversight.InformationRequestSlaClock
import com.docuhyphen.app.api.model.informationrequest.oversight.InformationRequestSlaStatus
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.clock.InformationRequestClockEventRepository
import com.docuhyphen.app.api.repository.informationrequest.clock.InformationRequestClockRepository
import com.docuhyphen.app.api.repository.informationrequest.lifecycle.InformationRequestTransitionRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandPreconditionException
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.clock.model.ClockFixture
import com.docuhyphen.app.api.service.informationrequest.oversight.InformationRequestOperationsPostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.parent.InformationRequestParentLifecycleService
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(InformationRequestOperationsPostgreSQLResource::class)
class InformationRequestClockTransactionTest
{
    @Inject lateinit var dataSource: DataSource
    @Inject lateinit var runtime: InformationRequestRuntimeTestServices
    @Inject lateinit var requestRepository: InformationRequestRepository
    @Inject lateinit var clockRepository: InformationRequestClockRepository
    @Inject lateinit var eventRepository: InformationRequestClockEventRepository
    @Inject lateinit var transitionRepository: InformationRequestTransitionRepository
    @Inject lateinit var policies: InformationRequestClockPolicyService
    @Inject lateinit var recorder: InformationRequestClockRecorder
    @Inject lateinit var processor: InformationRequestClockPointProcessor
    @Inject lateinit var scheduler: InformationRequestClockScheduler

    @Inject
    lateinit var parentLifecycle: InformationRequestParentLifecycleService

    @Inject
    lateinit var issueRebaser: InformationRequestClockIssueRebaser

    @Test
    fun `a business clock is due after its working budget from the received instant and states its frozen inputs`()
    {
        val fixture = fixture()
        val time = TestClock(FRIDAY_AFTERNOON)
        val service = service(fixture, time)

        val standard = start(service, fixture, "response", fixture.versionId, InformationRequestClockUrgency.STANDARD)
        val urgent = start(service, fixture, "urgent-response", fixture.versionId, InformationRequestClockUrgency.URGENT)

        assertEquals(Instant.parse("2026-10-02T14:00:00Z"), standard.clock.dueAt.toInstant())
        assertEquals(Instant.parse("2026-10-01T14:00:00Z"), standard.clock.nextPointAt?.toInstant())
        assertEquals(Instant.parse("2026-09-28T14:00:00Z"), urgent.clock.dueAt.toInstant())
        val started = standard.events.single()
        assertEquals(InformationRequestClockEventKind.STARTED, started.eventKind)
        assertTrue(started.inputsJson.contains("\"budgetMinutes\":\"2400\""))
        assertTrue(started.inputsJson.contains("\"businessTimezone\":\"Africa/Johannesburg\""))
        val replayed = QuarkusTransaction.requiringNew().call {
            service.start(startCommand(fixture, "response", fixture.versionId, InformationRequestClockUrgency.STANDARD))
        }
        assertEquals(standard.clock.id, replayed.clock.id)
        assertTrue(mutationsOf(fixture).count { it == InformationRequestMutation.START_CLOCK } == 2)
    }

    @Test
    fun `pausing and resuming move the due time by the working time lost while paused, and an extension opens a new due cycle`()
    {
        val fixture = fixture()
        val time = TestClock(FRIDAY_AFTERNOON)
        val service = service(fixture, time)
        val started = start(service, fixture, "response", fixture.versionId, InformationRequestClockUrgency.STANDARD)

        time.instant = Instant.parse("2026-09-25T14:30:00Z")
        val paused = change(service, fixture, started.clock.id, InformationRequestClockChange.PAUSE, started.clockETag)
        assertEquals(InformationRequestClockState.PAUSED, paused.clock.state)
        assertEquals(39 * HOUR + 30 * MINUTE, paused.clock.remainingSeconds)
        assertNull(paused.clock.nextPointAt)

        assertThrows(CommandPreconditionException::class.java) {
            change(service, fixture, started.clock.id, InformationRequestClockChange.RESUME, started.clockETag)
        }
        assertThrows(CommandPreconditionException::class.java) {
            QuarkusTransaction.requiringNew().call {
                service.change(changeCommand(fixture, started.clock.id, InformationRequestClockChange.RESUME, CommandPrecondition.Absent))
            }
        }

        time.instant = Instant.parse("2026-09-28T07:00:00Z")
        val resumed = change(service, fixture, started.clock.id, InformationRequestClockChange.RESUME, paused.clockETag)
        assertEquals(Instant.parse("2026-10-02T14:30:00Z"), resumed.clock.dueAt.toInstant())

        val extended = change(service, fixture, started.clock.id, InformationRequestClockChange.EXTEND, resumed.clockETag, 120)
        assertEquals(Instant.parse("2026-10-05T08:30:00Z"), extended.clock.dueAt.toInstant())
        assertEquals(1, extended.clock.dueCycle)
        assertEquals(
            listOf(
                InformationRequestClockEventKind.STARTED,
                InformationRequestClockEventKind.PAUSED,
                InformationRequestClockEventKind.RESUMED,
                InformationRequestClockEventKind.EXTENDED,
            ),
            extended.events.map { it.eventKind },
        )
        assertTrue(
            mutationsOf(fixture).containsAll(
                listOf(
                    InformationRequestMutation.PAUSE_CLOCK,
                    InformationRequestMutation.RESUME_CLOCK,
                    InformationRequestMutation.EXTEND_CLOCK,
                ),
            ),
        )
    }

    @Test
    fun `pausing and resuming at once inside a spring daylight saving gap keep the remaining working time and the due time`() {
        val fixture = fixture()
        val version = sundayVersion(fixture, 120 to 150, 180 to 240)
        val service = service(fixture, TestClock(SPRING_GAP))
        val started = start(service, fixture, "response", version, InformationRequestClockUrgency.STANDARD, SPRING_GAP)
        assertEquals(Instant.parse("2026-03-29T02:00:00Z"), started.clock.dueAt.toInstant())

        val paused = change(service, fixture, started.clock.id, InformationRequestClockChange.PAUSE, started.clockETag)
        assertEquals(HOUR, paused.clock.remainingSeconds)
        val resumed = change(service, fixture, started.clock.id, InformationRequestClockChange.RESUME, paused.clockETag)

        assertEquals(started.clock.dueAt.toInstant(), resumed.clock.dueAt.toInstant())
    }

    @Test
    fun `pausing and resuming at once keep the due time of split periods on an ordinary date and one period on the transition date`() {
        listOf(
            Instant.parse("2026-03-22T01:00:00Z") to listOf(120 to 150, 180 to 240),
            SPRING_GAP to listOf(180 to 240),
        ).forEach { (received, periods) ->
            val fixture = fixture()
            val version = sundayVersion(fixture, *periods.toTypedArray())
            val service = service(fixture, TestClock(received))
            val started =
                start(service, fixture, "response", version, InformationRequestClockUrgency.STANDARD, received)

            val paused =
                change(service, fixture, started.clock.id, InformationRequestClockChange.PAUSE, started.clockETag)
            assertEquals(HOUR, paused.clock.remainingSeconds)
            val resumed =
                change(service, fixture, started.clock.id, InformationRequestClockChange.RESUME, paused.clockETag)
            assertEquals(started.clock.dueAt.toInstant(), resumed.clock.dueAt.toInstant())
        }
    }

    @Test
    fun `reminder, overdue, and escalation points are each recorded once and overdue publishes its trigger`()
    {
        val fixture = fixture()
        val calendarVersion = UUID.randomUUID()
        dataSource.connection.use { connection ->
            fixture.clocks(connection).insertVersion(calendarVersion, versionNumber = 2, clockType = "CALENDAR", standard = 120, urgent = 60)
            fixture.clocks(connection).insertReminder(calendarVersion, ordinal = 1, minutes = 60)
        }
        val start = Instant.parse("2026-09-25T08:00:00Z")
        val service = service(fixture, TestClock(start))
        val started = start(service, fixture, "response", calendarVersion, InformationRequestClockUrgency.STANDARD, start)
        val clockId = started.clock.id

        assertTrue(processor.process(clockId, start.plusSeconds(61 * MINUTE)))
        assertFalse(processor.process(clockId, start.plusSeconds(62 * MINUTE)))
        assertTrue(processor.process(clockId, start.plusSeconds(121 * MINUTE)))
        assertFalse(processor.process(clockId, start.plusSeconds(122 * MINUTE)))
        assertTrue(processor.process(clockId, start.plusSeconds(400 * MINUTE)))

        QuarkusTransaction.requiringNew().run {
            val clock = requireNotNull(clockRepository.findById(clockId))
            assertNotNull(clock.overdueAt)
            assertNull(clock.nextPointAt)
            assertEquals(
                listOf(
                    InformationRequestClockEventKind.STARTED,
                    InformationRequestClockEventKind.REMINDED,
                    InformationRequestClockEventKind.OVERDUE,
                    InformationRequestClockEventKind.ESCALATED,
                ),
                eventRepository.findForClock(clockId).map { it.eventKind },
            )
        }
        assertEquals(
            listOf(
                InformationRequestMutation.RECORD_REMINDER,
                InformationRequestMutation.RECORD_OVERDUE,
                InformationRequestMutation.RECORD_ESCALATION,
            ),
            mutationsOf(fixture).filter { it.name.startsWith("RECORD_") && it != InformationRequestMutation.RECORD_FIRST_VIEW },
        )
        val overdue = outboxPayload(fixture, "information_request.request.overdue")
        assertTrue(overdue.contains(clockId.toString()))
        assertTrue(overdue.contains("\"clockKey\":\"response\""))
    }

    @Test
    fun `reminders a backdated start leaves behind send only the latest one and record the earlier ones as superseded`() {
        val fixture = fixture()
        val calendarVersion = UUID.randomUUID()
        dataSource.connection.use { connection ->
            fixture.clocks(connection)
                .insertVersion(calendarVersion, versionNumber = 2, clockType = "CALENDAR", standard = 600, urgent = 300)
            fixture.clocks(connection).insertReminder(calendarVersion, ordinal = 1, minutes = 180)
            fixture.clocks(connection).insertReminder(calendarVersion, ordinal = 2, minutes = 60)
        }
        val received = Instant.parse("2026-09-25T08:00:00Z")
        val now = received.plusSeconds(570 * MINUTE)
        val started = start(
            service(fixture, TestClock(now)),
            fixture,
            "response",
            calendarVersion,
            InformationRequestClockUrgency.STANDARD,
            received
        )

        assertTrue(processor.process(started.clock.id, now))

        QuarkusTransaction.requiringNew().run {
            val reminders = eventRepository.findForClock(started.clock.id)
                .filter { it.eventKind == InformationRequestClockEventKind.REMINDED }
            assertEquals(listOf(1, 2), reminders.map { it.pointOrdinal })
            assertEquals(listOf("REMINDER_SUPERSEDED", null), reminders.map { it.reasonCode })
        }
        assertEquals(1, mutationsOf(fixture).count { it == InformationRequestMutation.RECORD_REMINDER })
    }

    @Test
    fun `a clock started on a draft counts its budget from the moment the request is issued`() {
        val fixture = fixture()
        val calendarVersion = UUID.randomUUID()
        dataSource.connection.use { connection ->
            fixture.clocks(connection)
                .insertVersion(calendarVersion, versionNumber = 2, clockType = "CALENDAR", standard = 240, urgent = 120)
            execute(
                connection,
                "UPDATE information_request SET state = 'DRAFT', issued_at = NULL WHERE id = ?",
                fixture.runtime.requestId
            )
        }
        val drafted = Instant.parse("2026-09-24T18:00:00Z")
        val backdated = start(
            service(fixture, TestClock(drafted)),
            fixture,
            "backdated",
            calendarVersion,
            InformationRequestClockUrgency.STANDARD,
            drafted.minusSeconds(HOUR)
        )
        val started = QuarkusTransaction.requiringNew().call {
            service(fixture, TestClock(drafted))
                .start(
                    startCommand(fixture, "response", calendarVersion, InformationRequestClockUrgency.STANDARD).copy(
                        receivedAt = null
                    )
                )
        }
        val issuedAt = Instant.parse("2026-09-25T08:00:00Z")

        QuarkusTransaction.requiringNew().run {
            issueRebaser.rebaseOnIssue(
                requireNotNull(requestRepository.findById(fixture.runtime.requestId)),
                issuedAt,
                owner(fixture).principal
            )
        }

        QuarkusTransaction.requiringNew().run {
            val clock = requireNotNull(clockRepository.findById(started.clock.id))
            assertEquals(drafted, clock.receivedAt.toInstant())
            assertEquals(issuedAt.plusSeconds(240 * MINUTE), clock.dueAt.toInstant())
            assertEquals(
                listOf(InformationRequestClockEventKind.STARTED, InformationRequestClockEventKind.RESUMED),
                eventRepository.findForClock(clock.id).map { it.eventKind },
            )
            assertEquals(backdated.clock.dueAt, requireNotNull(clockRepository.findById(backdated.clock.id)).dueAt)
        }
    }

    @Test
    fun `a clock records nothing while its Exchange awaits acceptance and then counts its budget from the acceptance`() {
        val fixture = fixture()
        val expiringVersion = UUID.randomUUID()
        val issuedAt = Instant.parse("2026-09-25T08:00:00Z")
        dataSource.connection.use { connection ->
            fixture.clocks(connection).insertVersion(
                expiringVersion,
                versionNumber = 2,
                clockType = "CALENDAR",
                standard = 120,
                urgent = 60,
                escalation = null,
                dueEffect = "EXPIRE_REQUEST",
            )
            fixture.clocks(connection).insertReminder(expiringVersion, ordinal = 1, minutes = 60)
            execute(
                connection,
                "UPDATE information_request SET issued_at = ? WHERE id = ?",
                Timestamp.from(issuedAt),
                fixture.runtime.requestId
            )
            execute(connection, "UPDATE exchange SET status = 'INITIATED' WHERE id = ?", fixture.runtime.exchangeId)
        }
        val defaulted = QuarkusTransaction.requiringNew().call {
            service(fixture, TestClock(issuedAt))
                .start(
                    startCommand(fixture, "response", expiringVersion, InformationRequestClockUrgency.STANDARD).copy(
                        receivedAt = null
                    )
                )
        }
        val backdated = start(
            service(fixture, TestClock(issuedAt)),
            fixture,
            "backdated",
            expiringVersion,
            InformationRequestClockUrgency.STANDARD,
            issuedAt.minusSeconds(HOUR),
        )

        assertTrue(processor.process(defaulted.clock.id, issuedAt.plusSeconds(600 * MINUTE)))

        QuarkusTransaction.requiringNew().run {
            assertEquals(
                InformationRequestState.ISSUED,
                requireNotNull(requestRepository.findById(fixture.runtime.requestId)).state
            )
            assertEquals(
                listOf(InformationRequestClockEventKind.STARTED),
                eventRepository.findForClock(defaulted.clock.id).map { it.eventKind })
        }
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE exchange SET status = 'ACCEPTED_STARTED' WHERE id = ?",
                fixture.runtime.exchangeId
            )
        }
        val acceptedFrom = Instant.now()
        QuarkusTransaction.requiringNew().run {
            parentLifecycle.apply(
                fixture.runtime.exchangeId,
                ExchangeStatus.ACCEPTED_STARTED,
                false,
                InformationRequestAuditActor.of(owner(fixture)),
                ExchangeStatus.INITIATED,
            )
        }
        val acceptedTo = Instant.now()

        QuarkusTransaction.requiringNew().run {
            val clock = requireNotNull(clockRepository.findById(defaulted.clock.id))
            assertFalse(clock.dueAt.toInstant().isBefore(acceptedFrom.minusMillis(1).plusSeconds(120 * MINUTE)))
            assertFalse(clock.dueAt.toInstant().isAfter(acceptedTo.plusMillis(1).plusSeconds(120 * MINUTE)))
            val resumed = eventRepository.findForClock(clock.id).last()
            assertEquals(InformationRequestClockEventKind.RESUMED, resumed.eventKind)
            assertEquals("EXCHANGE_ACCEPTED", resumed.reasonCode)
            assertEquals(backdated.clock.dueAt, requireNotNull(clockRepository.findById(backdated.clock.id)).dueAt)
        }
    }

    @Test
    fun `an expiring policy expires the request at its due time and a finished request only stops its clock`()
    {
        val expiring = fixture()
        val expiringVersion = UUID.randomUUID()
        dataSource.connection.use { connection ->
            expiring.clocks(connection).insertVersion(
                expiringVersion, versionNumber = 2, clockType = "CALENDAR", standard = 60, urgent = 60, escalation = null,
                dueEffect = "EXPIRE_REQUEST",
            )
        }
        val start = Instant.parse("2026-09-25T08:00:00Z")
        val expiringClock = start(service(expiring, TestClock(start)), expiring, "response", expiringVersion, InformationRequestClockUrgency.STANDARD, start)

        assertTrue(processor.process(expiringClock.clock.id, start.plusSeconds(61 * MINUTE)))

        QuarkusTransaction.requiringNew().run {
            val request = requireNotNull(requestRepository.findById(expiring.runtime.requestId))
            assertEquals(InformationRequestState.EXPIRED, request.state)
            assertNotNull(request.expiredAt)
            assertEquals("CLOCK_DUE", transitionRepository.findForRequest(request.id).single { it.mutation == InformationRequestMutation.EXPIRE }.reasonCode)
            assertEquals(InformationRequestClockState.STOPPED, requireNotNull(clockRepository.findById(expiringClock.clock.id)).state)
            assertEquals(
                listOf(
                    InformationRequestClockEventKind.STARTED,
                    InformationRequestClockEventKind.OVERDUE,
                    InformationRequestClockEventKind.EXPIRED,
                    InformationRequestClockEventKind.STOPPED,
                ),
                eventRepository.findForClock(expiringClock.clock.id).map { it.eventKind },
            )
        }

        val finished = fixture()
        val finishedClock = start(service(finished, TestClock(FRIDAY_AFTERNOON)), finished, "response", finished.versionId, InformationRequestClockUrgency.URGENT)
        dataSource.connection.use { connection ->
            execute(connection, "UPDATE information_request SET state = 'CANCELLED', cancelled_at = now() WHERE id = ?", finished.runtime.requestId)
        }
        assertTrue(processor.process(finishedClock.clock.id, Instant.parse("2026-12-01T08:00:00Z")))
        QuarkusTransaction.requiringNew().run {
            val events = eventRepository.findForClock(finishedClock.clock.id)
            assertEquals(listOf(InformationRequestClockEventKind.STARTED, InformationRequestClockEventKind.STOPPED), events.map { it.eventKind })
            assertEquals("REQUEST_FINISHED", events.last().reasonCode)
        }
    }

    @Test
    fun `a clock with no further points and a paused clock both stop once their request finishes`()
    {
        val fixture = fixture()
        val calendarVersion = UUID.randomUUID()
        dataSource.connection.use { connection ->
            fixture.clocks(connection).insertVersion(calendarVersion, versionNumber = 2, clockType = "CALENDAR", standard = 60, urgent = 60, escalation = null)
        }
        val start = Instant.parse("2026-09-25T08:00:00Z")
        val service = service(fixture, TestClock(start))
        val overdue = start(service, fixture, "response", calendarVersion, InformationRequestClockUrgency.STANDARD, start)
        val paused = start(service, fixture, "second-response", calendarVersion, InformationRequestClockUrgency.STANDARD, start)
        change(service, fixture, paused.clock.id, InformationRequestClockChange.PAUSE, paused.clockETag)
        assertTrue(processor.process(overdue.clock.id, start.plusSeconds(61 * MINUTE)))
        QuarkusTransaction.requiringNew().run { assertNull(requireNotNull(clockRepository.findById(overdue.clock.id)).nextPointAt) }
        dataSource.connection.use { connection ->
            execute(connection, "UPDATE information_request SET state = 'CANCELLED', cancelled_at = now() WHERE id = ?", fixture.runtime.requestId)
        }

        scheduler.processDuePoints(start.plusSeconds(120 * MINUTE))

        QuarkusTransaction.requiringNew().run {
            listOf(overdue.clock.id, paused.clock.id).forEach { clockId ->
                assertEquals(InformationRequestClockState.STOPPED, requireNotNull(clockRepository.findById(clockId)).state)
                assertEquals("REQUEST_FINISHED", eventRepository.findForClock(clockId).last().reasonCode)
            }
        }
    }

    @Test
    fun `cancelling a request stops its running and paused clocks at the cancellation instant on behalf of the canceller`() {
        val fixture = fixture()
        val service = service(fixture, TestClock(FRIDAY_AFTERNOON))
        val running = start(service, fixture, "response", fixture.versionId, InformationRequestClockUrgency.STANDARD)
        val paused =
            start(service, fixture, "second-response", fixture.versionId, InformationRequestClockUrgency.STANDARD)
        change(service, fixture, paused.clock.id, InformationRequestClockChange.PAUSE, paused.clockETag)
        val services = runtime.build(fixture.runtime.requestId)
        val etag = QuarkusTransaction.requiringNew().call {
            InformationRequestETag.aggregateOf(requireNotNull(requestRepository.findById(fixture.runtime.requestId)))
        }

        QuarkusTransaction.requiringNew().call {
            services.lifecycle.cancel(
                CancelInformationRequestCommand(
                    requestId = fixture.runtime.requestId,
                    reasonCode = "no-longer-needed",
                    access = owner(fixture),
                    precondition = CommandPrecondition.ExpectedRevision(etag),
                    idempotencyKey = "cancel-with-clocks",
                ),
            )
        }

        QuarkusTransaction.requiringNew().run {
            val cancelledAt =
                requireNotNull(requestRepository.findById(fixture.runtime.requestId)).cancelledAt?.toInstant()
            listOf(running.clock.id, paused.clock.id).forEach { clockId ->
                val stored = requireNotNull(clockRepository.findById(clockId))
                assertEquals(InformationRequestClockState.STOPPED, stored.state)
                assertEquals(cancelledAt, stored.stoppedAt?.toInstant())
                val stopped = eventRepository.findForClock(clockId).last()
                assertEquals(InformationRequestClockEventKind.STOPPED, stopped.eventKind)
                assertEquals("REQUEST_FINISHED", stopped.reasonCode)
                assertEquals(fixture.runtime.template.userId, stopped.recordedByPrincipalId)
            }
        }
    }

    @Test
    fun `a request cancelled because its Exchange was rejected stops its clocks in the same transaction`() {
        val fixture = fixture()
        val service = service(fixture, TestClock(FRIDAY_AFTERNOON))
        val running = start(service, fixture, "response", fixture.versionId, InformationRequestClockUrgency.STANDARD)
        val paused =
            start(service, fixture, "second-response", fixture.versionId, InformationRequestClockUrgency.STANDARD)
        change(service, fixture, paused.clock.id, InformationRequestClockChange.PAUSE, paused.clockETag)
        val actor = owner(fixture)

        QuarkusTransaction.requiringNew().run {
            parentLifecycle.apply(
                fixture.runtime.exchangeId,
                ExchangeStatus.REJECTED,
                false,
                InformationRequestAuditActor.of(actor)
            )
        }

        QuarkusTransaction.requiringNew().run {
            val request = requireNotNull(requestRepository.findById(fixture.runtime.requestId))
            assertEquals(InformationRequestState.CANCELLED, request.state)
            listOf(running.clock.id, paused.clock.id).forEach { clockId ->
                val stored = requireNotNull(clockRepository.findById(clockId))
                assertEquals(InformationRequestClockState.STOPPED, stored.state)
                assertEquals(request.cancelledAt?.toInstant(), stored.stoppedAt?.toInstant())
                val stopped = eventRepository.findForClock(clockId).last()
                assertEquals(InformationRequestClockEventKind.STOPPED, stopped.eventKind)
                assertEquals(InformationRequestClockStopper.PARENT_FINISHED, stopped.reasonCode)
                assertEquals(actor.principal.id, stopped.recordedByPrincipalId)
            }
        }
    }

    @Test
    fun `a request finished outside its own transaction has its clocks stopped at the instant it finished`() {
        val fixture = fixture()
        val started = start(
            service(fixture, TestClock(FRIDAY_AFTERNOON)),
            fixture,
            "response",
            fixture.versionId,
            InformationRequestClockUrgency.URGENT
        )
        val finishedAt = Instant.parse("2026-09-28T09:00:00Z")
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE information_request SET state = 'CLOSED', closed_at = ? WHERE id = ?",
                Timestamp.from(finishedAt),
                fixture.runtime.requestId,
            )
        }
        val processedAt = Instant.parse("2026-12-01T08:00:00Z")

        assertTrue(processor.process(started.clock.id, processedAt))

        QuarkusTransaction.requiringNew().run {
            val stored = requireNotNull(clockRepository.findById(started.clock.id))
            assertEquals(finishedAt, stored.stoppedAt?.toInstant())
            val stopped = eventRepository.findForClock(started.clock.id).last()
            assertEquals(InformationRequestClockEventKind.STOPPED, stopped.eventKind)
            assertEquals(finishedAt, stopped.occurredAt.toInstant())
            assertTrue(stopped.inputsJson.contains("\"processedAt\":\"$processedAt\""), stopped.inputsJson)
            assertEquals(
                InformationRequestSlaStatus.MET,
                InformationRequestSlaCalculator.statusOf(
                    InformationRequestSlaClock(
                        stored,
                        eventRepository.findForClock(stored.id),
                        null
                    ), processedAt
                ),
            )
        }
    }

    @Test
    fun `a later policy version never recalculates a running clock`()
    {
        val fixture = fixture()
        val started = start(service(fixture, TestClock(FRIDAY_AFTERNOON)), fixture, "response", fixture.versionId, InformationRequestClockUrgency.STANDARD)
        dataSource.connection.use { connection ->
            fixture.clocks(connection).insertVersion(UUID.randomUUID(), versionNumber = 2, standard = 60, urgent = 30)
        }

        val listed = QuarkusTransaction.requiringNew().call { service(fixture, TestClock(FRIDAY_AFTERNOON)).clocks(fixture.runtime.requestId, owner(fixture)) }.single()

        assertEquals(fixture.versionId, listed.clock.policyVersionId)
        assertEquals(started.clock.dueAt, listed.clock.dueAt)
        assertEquals(1, listed.policyVersion.version.versionNumber)
    }

    @Test
    fun `scheduler deferral keeps the user visible clock revision`() {
        val fixture = fixture()
        val started = start(
            service(fixture, TestClock(FRIDAY_AFTERNOON)),
            fixture,
            "deferred",
            fixture.versionId,
            InformationRequestClockUrgency.STANDARD
        )
        dataSource.connection.use {
            execute(
                it,
                "UPDATE information_request SET state = 'DRAFT' WHERE id = ?",
                fixture.runtime.requestId
            )
        }
        assertTrue(processor.process(started.clock.id, started.clock.dueAt.toInstant().plusSeconds(60)))
        QuarkusTransaction.requiringNew().run {
            assertEquals(
                started.clock.clockRevision,
                requireNotNull(clockRepository.findById(started.clock.id)).clockRevision
            )
        }
    }

    @Test
    fun `overdue standing records the passed due instant rather than scheduler detection time`() {
        val fixture = fixture()
        val started = start(
            service(fixture, TestClock(FRIDAY_AFTERNOON)),
            fixture,
            "late-detection",
            fixture.versionId,
            InformationRequestClockUrgency.STANDARD
        )
        assertTrue(processor.process(started.clock.id, started.clock.dueAt.toInstant().plusSeconds(HOUR)))
        QuarkusTransaction.requiringNew().run {
            assertEquals(started.clock.dueAt, requireNotNull(clockRepository.findById(started.clock.id)).overdueAt)
        }
    }

    @Test
    fun `resuming an overdue clock preserves the due instant already passed`() {
        val fixture = fixture()
        val time = TestClock(FRIDAY_AFTERNOON)
        val service = service(fixture, time)
        val started =
            start(service, fixture, "overdue-pause", fixture.versionId, InformationRequestClockUrgency.STANDARD)
        time.instant = started.clock.dueAt.toInstant().plusSeconds(HOUR)
        assertTrue(processor.process(started.clock.id, time.instant))
        val paused = change(
            service, fixture, started.clock.id, InformationRequestClockChange.PAUSE,
            QuarkusTransaction.requiringNew()
                .call { InformationRequestETag.clockOf(requireNotNull(clockRepository.findById(started.clock.id))) })
        time.instant = time.instant.plusSeconds(HOUR)
        val resumed = change(service, fixture, started.clock.id, InformationRequestClockChange.RESUME, paused.clockETag)
        assertEquals(started.clock.dueAt, resumed.clock.dueAt)
    }

    @Test
    fun `a clock with no received instant on an issued request counts from the explicit start command`() {
        val fixture = fixture()
        val version = UUID.randomUUID()
        dataSource.connection.use {
            fixture.clocks(it)
                .insertVersion(version, versionNumber = 2, clockType = "CALENDAR", standard = 60, urgent = 30)
        }
        val now = Instant.parse("2026-10-27T08:00:00Z")
        val started = QuarkusTransaction.requiringNew().call {
            service(fixture, TestClock(now)).start(
                startCommand(
                    fixture,
                    "start-now",
                    version,
                    InformationRequestClockUrgency.STANDARD
                ).copy(receivedAt = null)
            )
        }
        assertEquals(now.plusSeconds(HOUR), started.clock.dueAt.toInstant())
    }

    @Test
    fun `a paused draft clock keeps its full budget when issued`() {
        val fixture = fixture()
        val version = UUID.randomUUID()
        dataSource.connection.use {
            fixture.clocks(it)
                .insertVersion(version, versionNumber = 2, clockType = "CALENDAR", standard = 240, urgent = 120)
            execute(
                it,
                "UPDATE information_request SET state = 'DRAFT', issued_at = NULL WHERE id = ?",
                fixture.runtime.requestId
            )
        }
        val drafted = Instant.parse("2026-09-24T18:00:00Z")
        val time = TestClock(drafted)
        val service = service(fixture, time)
        val started = QuarkusTransaction.requiringNew().call {
            service.start(
                startCommand(fixture, "paused-draft", version, InformationRequestClockUrgency.STANDARD).copy(
                    receivedAt = null
                )
            )
        }
        time.instant = drafted.plusSeconds(HOUR)
        change(service, fixture, started.clock.id, InformationRequestClockChange.PAUSE, started.clockETag)
        QuarkusTransaction.requiringNew().run {
            issueRebaser.rebaseOnIssue(
                requireNotNull(requestRepository.findById(fixture.runtime.requestId)),
                drafted.plusSeconds(24 * HOUR),
                owner(fixture).principal
            )
        }
        QuarkusTransaction.requiringNew().run {
            val clock = requireNotNull(clockRepository.findById(started.clock.id))
            assertEquals(InformationRequestClockState.PAUSED, clock.state)
            assertEquals(4 * HOUR, clock.remainingSeconds)
        }
    }

    @Test
    fun `draft rebase never revives a reminder before the effective issued start`() {
        val fixture = fixture()
        val version = UUID.randomUUID()
        dataSource.connection.use {
            fixture.clocks(it)
                .insertVersion(version, versionNumber = 2, clockType = "CALENDAR", standard = 60, urgent = 30)
            fixture.clocks(it).insertReminder(version, ordinal = 1, minutes = 120)
            execute(
                it,
                "UPDATE information_request SET state = 'DRAFT', issued_at = NULL WHERE id = ?",
                fixture.runtime.requestId
            )
        }
        val drafted = Instant.parse("2026-09-24T18:00:00Z")
        val started = QuarkusTransaction.requiringNew().call {
            service(fixture, TestClock(drafted)).start(
                startCommand(
                    fixture,
                    "issued-floor",
                    version,
                    InformationRequestClockUrgency.STANDARD
                ).copy(receivedAt = null)
            )
        }
        QuarkusTransaction.requiringNew().run {
            issueRebaser.rebaseOnIssue(
                requireNotNull(requestRepository.findById(fixture.runtime.requestId)),
                drafted.plusSeconds(24 * HOUR),
                owner(fixture).principal
            )
        }
        QuarkusTransaction.requiringNew().run {
            val clock = requireNotNull(clockRepository.findById(started.clock.id))
            assertEquals(clock.dueAt, clock.nextPointAt)
        }
    }

    @Test
    fun `two overlapping scheduler calls record one overdue point`() {
        val fixture = fixture()
        val started = start(
            service(fixture, TestClock(FRIDAY_AFTERNOON)),
            fixture,
            "overlapping-point",
            fixture.versionId,
            InformationRequestClockUrgency.STANDARD
        )
        val processedAt = started.clock.dueAt.toInstant().plusSeconds(60)
        val raced = com.docuhyphen.app.api.service.command.OverlappingCommandRace(dataSource)
            .whileRowLocked(
                "information_request",
                fixture.runtime.requestId,
                List(2) { { processor.process(started.clock.id, processedAt) } })
        assertTrue(raced.overlapped)
        raced.results.forEach { it.getOrThrow() }
        QuarkusTransaction.requiringNew().run {
            assertEquals(
                1,
                eventRepository.findForClock(started.clock.id)
                    .count { it.eventKind == InformationRequestClockEventKind.OVERDUE })
        }
    }
    private fun fixture(): ClockFixture =
        dataSource.connection.use { connection ->
            val runtimeFixture = SubmissionRuntimeSqlFixture(connection)
            val clocks = ClockSqlFixture(connection, runtimeFixture)
            ClockFixture(runtimeFixture, clocks.policyId, clocks.versionId)
        }

    private fun sundayVersion(fixture: ClockFixture, vararg periods: Pair<Int, Int>): UUID =
        dataSource.connection.use { connection ->
            val version = UUID.randomUUID()
            val clocks = fixture.clocks(connection)
            clocks.insertVersion(
                version,
                versionNumber = 2,
                standard = 60,
                urgent = 30,
                escalation = null,
                businessTimezone = "Europe/Berlin"
            )
            periods.forEach { (start, end) -> clocks.insertPeriod(version, SUNDAY, start, end) }
            version
        }

    private fun service(fixture: ClockFixture, time: Clock) = InformationRequestClockService(
        runtime.build(fixture.runtime.requestId).gate, clockRepository, eventRepository, policies, recorder,
        runtime.commandReceiptService, time,
    )

    @Suppress("LongParameterList")
    private fun start(
        service: InformationRequestClockService,
        fixture: ClockFixture,
        key: String,
        versionId: UUID,
        urgency: InformationRequestClockUrgency,
        received: Instant = FRIDAY_AFTERNOON,
    ) = QuarkusTransaction.requiringNew().call { service.start(startCommand(fixture, key, versionId, urgency, received)) }

    private fun startCommand(
        fixture: ClockFixture,
        key: String,
        versionId: UUID,
        urgency: InformationRequestClockUrgency,
        received: Instant = FRIDAY_AFTERNOON,
    ) = StartInformationRequestClockCommand(
        requestId = fixture.runtime.requestId,
        clockKey = key,
        policyVersionId = versionId,
        urgency = urgency,
        receivedAt = received,
        access = owner(fixture),
        idempotencyKey = "start-$key",
    )

    @Suppress("LongParameterList")
    private fun change(
        service: InformationRequestClockService,
        fixture: ClockFixture,
        clockId: UUID,
        change: InformationRequestClockChange,
        etag: String,
        minutes: Int? = null,
    ) = QuarkusTransaction.requiringNew().call {
        service.change(changeCommand(fixture, clockId, change, CommandPrecondition.ExpectedRevision(etag), minutes))
    }

    private fun changeCommand(
        fixture: ClockFixture,
        clockId: UUID,
        change: InformationRequestClockChange,
        precondition: CommandPrecondition,
        minutes: Int? = null,
    ) = ChangeInformationRequestClockCommand(
        requestId = fixture.runtime.requestId,
        clockId = clockId,
        change = change,
        extensionMinutes = minutes,
        reasonCode = "respondent.requested",
        access = owner(fixture),
        precondition = precondition,
        idempotencyKey = "change-${UUID.randomUUID()}",
    )

    private fun owner(fixture: ClockFixture) =
        RequestAccessContext(PrincipalRef.user(fixture.runtime.template.userId), AuthorizationContext(sessionRef = "owner"))

    private fun mutationsOf(fixture: ClockFixture): List<InformationRequestMutation> =
        QuarkusTransaction.requiringNew().call { transitionRepository.findForRequest(fixture.runtime.requestId).map { it.mutation } }

    private fun outboxPayload(fixture: ClockFixture, eventType: String): String =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT envelope_json FROM workflow_event_outbox WHERE ordering_key = ? AND event_type = ? ORDER BY sequence_number DESC LIMIT 1",
            ).use { statement ->
                statement.setString(1, "information_request:${fixture.runtime.requestId}")
                statement.setString(2, eventType)
                statement.executeQuery().use { rows ->
                    check(rows.next())
                    rows.getString(1)
                }
            }
        }

    private class TestClock(var instant: Instant) : Clock()
    {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = instant
    }

    private companion object
    {
        const val MINUTE = 60L
        const val HOUR = 3600L
        const val SUNDAY = 7
        val FRIDAY_AFTERNOON: Instant = Instant.parse("2026-09-25T14:00:00Z")
        val SPRING_GAP: Instant = Instant.parse("2026-03-29T01:00:00Z")
    }
}
