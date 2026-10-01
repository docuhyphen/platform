package com.docuhyphen.app.api.service.informationrequest.clock

import com.docuhyphen.app.api.migration.ClockSqlFixture
import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.model.entity.InformationRequestClockEventKind
import com.docuhyphen.app.api.model.entity.InformationRequestClockState
import com.docuhyphen.app.api.model.entity.InformationRequestClockUrgency
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.clock.ChangeInformationRequestClockCommand
import com.docuhyphen.app.api.model.informationrequest.clock.InformationRequestClockChange
import com.docuhyphen.app.api.model.informationrequest.clock.StartInformationRequestClockCommand
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.clock.InformationRequestClockEventRepository
import com.docuhyphen.app.api.repository.informationrequest.clock.InformationRequestClockRepository
import com.docuhyphen.app.api.repository.informationrequest.lifecycle.InformationRequestTransitionRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandPrecondition
import com.docuhyphen.app.api.service.command.CommandPreconditionException
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.oversight.InformationRequestOperationsPostgreSQLResource
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
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

    private fun fixture(): ClockFixture =
        dataSource.connection.use { connection ->
            val runtimeFixture = SubmissionRuntimeSqlFixture(connection)
            val clocks = ClockSqlFixture(connection, runtimeFixture)
            ClockFixture(runtimeFixture, clocks.policyId, clocks.versionId)
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

    private data class ClockFixture(
        val runtime: SubmissionRuntimeSqlFixture,
        val policyId: UUID,
        val versionId: UUID,
    )
    {
        fun clocks(connection: java.sql.Connection) = ClockSqlFixture(connection, runtime, policyId, versionId, seed = false)
    }

    private companion object
    {
        const val MINUTE = 60L
        const val HOUR = 3600L
        val FRIDAY_AFTERNOON: Instant = Instant.parse("2026-09-25T14:00:00Z")
    }
}
