package com.docuhyphen.app.api.service.informationrequest.clock

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.migration.ClockSqlFixture
import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.InformationRequestClockEventKind
import com.docuhyphen.app.api.model.entity.InformationRequestClockState
import com.docuhyphen.app.api.model.entity.InformationRequestClockUrgency
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.clock.ChangeInformationRequestClockCommand
import com.docuhyphen.app.api.model.informationrequest.clock.InformationRequestClockChange
import com.docuhyphen.app.api.model.informationrequest.clock.InformationRequestClockView
import com.docuhyphen.app.api.model.informationrequest.clock.StartInformationRequestClockCommand
import com.docuhyphen.app.api.repository.informationrequest.clock.InformationRequestClockEventRepository
import com.docuhyphen.app.api.repository.informationrequest.clock.InformationRequestClockRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.clock.model.ClockSuspensionScenario
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.oversight.InformationRequestOperationsPostgreSQLResource
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.security.ForbiddenException
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(InformationRequestOperationsPostgreSQLResource::class)
class InformationRequestClockSuspensionTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var gate: InformationRequestMutationGate

    @Inject
    lateinit var clockRepository: InformationRequestClockRepository

    @Inject
    lateinit var eventRepository: InformationRequestClockEventRepository

    @Inject
    lateinit var policies: InformationRequestClockPolicyService

    @Inject
    lateinit var recorder: InformationRequestClockRecorder

    @Inject
    lateinit var commandReceiptService: CommandReceiptService

    @Test
    fun `pausing a running clock is refused while the owner is operationally suspended`() {
        val fixture = fixture()
        suspendOwner(fixture)

        assertThrows(SubscriptionDenialException::class.java) {
            change(fixture, InformationRequestClockChange.PAUSE, "pause-while-suspended")
        }

        val clock = clock(fixture)
        assertEquals(InformationRequestClockState.RUNNING, clock.state)
        assertNull(clock.remainingSeconds)
        assertNothingChanged(fixture)
    }

    @Test
    fun `extending a clock is refused while the owner is operationally suspended`() {
        val fixture = fixture()
        val dueBefore = clock(fixture).dueAt.toInstant()
        suspendOwner(fixture)

        assertThrows(SubscriptionDenialException::class.java) {
            change(fixture, InformationRequestClockChange.EXTEND, "extend-while-suspended", minutes = 60)
        }

        val clock = clock(fixture)
        assertEquals(dueBefore, clock.dueAt.toInstant())
        assertEquals(0, clock.dueCycle)
        assertNothingChanged(fixture)
    }

    @Test
    fun `resuming a paused clock is refused while the owner is operationally suspended`() {
        val fixture = fixture()
        change(fixture, InformationRequestClockChange.PAUSE, "pause-before-suspension")
        suspendOwner(fixture)

        assertThrows(SubscriptionDenialException::class.java) {
            change(fixture, InformationRequestClockChange.RESUME, "resume-while-suspended")
        }

        assertEquals(InformationRequestClockState.PAUSED, clock(fixture).state)
        assertEquals(
            listOf(InformationRequestClockEventKind.STARTED, InformationRequestClockEventKind.PAUSED),
            events(fixture),
        )
    }

    @Test
    fun `a change recorded before the suspension replays during it without writing again`() {
        val fixture = fixture()
        val etag = InformationRequestETag.clockOf(clock(fixture))
        val recorded = change(fixture, InformationRequestClockChange.PAUSE, "pause-then-suspend", etag = etag)
        suspendOwner(fixture)

        val replayed = change(fixture, InformationRequestClockChange.PAUSE, "pause-then-suspend", etag = etag)

        assertEquals(recorded.clockETag, replayed.clockETag)
        assertEquals(InformationRequestClockState.PAUSED, replayed.clock.state)
        assertEquals(
            listOf(InformationRequestClockEventKind.STARTED, InformationRequestClockEventKind.PAUSED),
            events(fixture),
        )
        assertEquals(1, receipts(fixture))
    }

    @Test
    fun `pausing and extending stay possible after the execution grant is revoked while resuming does not`() {
        val fixture = fixture()
        revokeGrant(fixture)

        change(fixture, InformationRequestClockChange.PAUSE, "pause-after-revocation")
        val extended = change(fixture, InformationRequestClockChange.EXTEND, "extend-after-revocation", minutes = 30)
        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            change(fixture, InformationRequestClockChange.RESUME, "resume-after-revocation")
        }

        assertEquals(InformationRequestErrorCatalog.EXECUTION_GRANT_REVOKED, refusal.reasonCode)
        assertEquals(InformationRequestClockState.PAUSED, extended.clock.state)
        assertEquals(
            listOf(
                InformationRequestClockEventKind.STARTED,
                InformationRequestClockEventKind.PAUSED,
                InformationRequestClockEventKind.EXTENDED,
            ),
            events(fixture),
        )
    }

    @Test
    fun `once the suspension ends the owner pauses, extends and resumes again`() {
        val fixture = fixture()
        suspendOwner(fixture)
        assertThrows(SubscriptionDenialException::class.java) {
            change(fixture, InformationRequestClockChange.PAUSE, "pause-refused")
        }

        setOwnerStatus(fixture, "ACTIVE")
        change(fixture, InformationRequestClockChange.PAUSE, "pause-after-suspension")
        change(fixture, InformationRequestClockChange.EXTEND, "extend-after-suspension", minutes = 15)
        val resumed = change(fixture, InformationRequestClockChange.RESUME, "resume-after-suspension")

        assertEquals(InformationRequestClockState.RUNNING, resumed.clock.state)
        assertEquals(1, resumed.clock.dueCycle)
    }

    @Test
    fun `a caller without clock authority is refused before the owner's suspension is disclosed`() {
        val fixture = fixture()
        change(fixture, InformationRequestClockChange.PAUSE, "pause-for-outsider-check")
        suspendOwner(fixture)
        val outsider = PrincipalRef.user(UUID.randomUUID())

        InformationRequestClockChange.entries.forEach { requested ->
            assertThrows(
                ForbiddenException::class.java,
                {
                    change(
                        fixture,
                        requested,
                        "outsider-${requested.name.lowercase()}",
                        minutes = 15,
                        caller = outsider
                    )
                },
                "$requested by an outsider",
            )
        }
    }

    private fun assertNothingChanged(fixture: ClockSuspensionScenario) {
        assertEquals(listOf(InformationRequestClockEventKind.STARTED), events(fixture))
        assertEquals(0, receipts(fixture))
        assertEquals(0, clockTransitions(fixture))
    }

    private fun fixture(): ClockSuspensionScenario {
        val versionId = UUID.randomUUID()
        val runtime = dataSource.connection.use { connection ->
            val runtime = SubmissionRuntimeSqlFixture(connection)
            val clocks = ClockSqlFixture(connection, runtime)
            clocks.insertVersion(versionId, versionNumber = 2, clockType = "CALENDAR", standard = 240, urgent = 240)
            execute(
                connection,
                """
                INSERT INTO request_execution_grant
                    (id, request_id, owner_type, owner_organization_id, plan_code, subscription_status,
                     enforcement_mode, acting_party_cap, issued_at, created_at, continuation_actions)
                VALUES (?, ?, 'ORGANIZATION', ?, 'BUSINESS', 'ACTIVE', 'ENFORCE', 10, now(), now(),
                        ARRAY['RESPOND', 'SUBMIT', 'ATTEST', 'REVIEW', 'ASSIGN_PARTY_WITHIN_CAP',
                              'UPLOAD_WITHIN_ALLOWANCE', 'NON_EXPANDING_AMENDMENT', 'SEND_REMINDER',
                              'CANCEL', 'SUPERSEDE']::varchar(64)[])
                """.trimIndent(),
                UUID.randomUUID(),
                runtime.requestId,
                runtime.template.organizationId,
            )
            execute(
                connection,
                """
                INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
                VALUES (?, 'EXCHANGE', ?, 'USER', ?, 'OWNER', 'DIRECT', 'ACTIVE', now())
                """.trimIndent(),
                UUID.randomUUID(),
                runtime.exchangeId,
                runtime.template.userId,
            )
            runtime
        }
        val scenario = ClockSuspensionScenario(
            runtime.requestId,
            runtime.template.organizationId,
            runtime.template.userId,
            UUID(0, 0),
        )
        setOwnerStatus(scenario, "ACTIVE")
        val started = QuarkusTransaction.requiringNew().call {
            service().start(
                StartInformationRequestClockCommand(
                    requestId = runtime.requestId,
                    clockKey = "response",
                    policyVersionId = versionId,
                    urgency = InformationRequestClockUrgency.STANDARD,
                    receivedAt = START,
                    access = access(PrincipalRef.user(runtime.template.userId)),
                    idempotencyKey = "start-response",
                ),
            )
        }
        return scenario.copy(clockId = started.clock.id)
    }

    @Suppress("LongParameterList")
    private fun change(
        fixture: ClockSuspensionScenario,
        change: InformationRequestClockChange,
        key: String,
        minutes: Int? = null,
        etag: String = InformationRequestETag.clockOf(clock(fixture)),
        caller: PrincipalRef = PrincipalRef.user(fixture.ownerUserId),
    ): InformationRequestClockView =
        QuarkusTransaction.requiringNew().call {
            service().change(
                ChangeInformationRequestClockCommand(
                    requestId = fixture.requestId,
                    clockId = fixture.clockId,
                    change = change,
                    extensionMinutes = if (change == InformationRequestClockChange.EXTEND) minutes else null,
                    reasonCode = "schedule-adjusted",
                    access = access(caller),
                    precondition = CommandPrecondition.ExpectedRevision(etag),
                    idempotencyKey = key,
                ),
            )
        }

    private fun service() = InformationRequestClockService(
        gate, clockRepository, eventRepository, policies, recorder, commandReceiptService,
        Clock.fixed(START.plusSeconds(CHANGE_AFTER_SECONDS), ZoneOffset.UTC),
    )

    private fun suspendOwner(fixture: ClockSuspensionScenario) = setOwnerStatus(fixture, "SUSPENDED")

    private fun setOwnerStatus(fixture: ClockSuspensionScenario, status: String) {
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

    private fun revokeGrant(fixture: ClockSuspensionScenario) {
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE request_execution_grant SET revoked_at = now(), revoked_reason = 'process.stopped' WHERE request_id = ?",
                fixture.requestId,
            )
        }
    }

    private fun clock(fixture: ClockSuspensionScenario) =
        QuarkusTransaction.requiringNew().call { requireNotNull(clockRepository.findById(fixture.clockId)) }

    private fun events(fixture: ClockSuspensionScenario): List<InformationRequestClockEventKind> =
        QuarkusTransaction.requiringNew().call { eventRepository.findForClock(fixture.clockId).map { it.eventKind } }

    private fun receipts(fixture: ClockSuspensionScenario): Int = dataSource.connection.use { connection ->
        queryInt(
            connection,
            "SELECT count(*) FROM command_receipt WHERE resource_id = ? AND operation_name = 'change-information-request-clock'",
            fixture.requestId,
        )
    }

    private fun clockTransitions(fixture: ClockSuspensionScenario): Int = dataSource.connection.use { connection ->
        queryInt(
            connection,
            """
            SELECT count(*) FROM information_request_transition
            WHERE information_request_id = ? AND mutation IN ('PAUSE_CLOCK', 'RESUME_CLOCK', 'EXTEND_CLOCK')
            """.trimIndent(),
            fixture.requestId,
        )
    }

    private fun access(principal: PrincipalRef) =
        RequestAccessContext(principal, AuthorizationContext(sessionRef = "clock-suspension"))

    private companion object {
        val START: Instant = Instant.parse("2026-09-25T08:00:00Z")
        const val CHANGE_AFTER_SECONDS = 1800L
    }
}
