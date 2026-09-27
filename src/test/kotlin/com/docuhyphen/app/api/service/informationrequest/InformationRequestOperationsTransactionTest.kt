package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.migration.ClockSqlFixture
import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.model.entity.InformationRequestClockUrgency
import com.docuhyphen.app.api.model.entity.InformationRequestNoticeDeliveryState
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.informationrequest.InformationRequestOperationsException
import com.docuhyphen.app.api.model.informationrequest.InformationRequestOperationsFilter
import com.docuhyphen.app.api.model.informationrequest.InformationRequestOwnerRef
import com.docuhyphen.app.api.model.informationrequest.InformationRequestSlaStatus
import com.docuhyphen.app.api.model.informationrequest.StartInformationRequestClockCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestClockEventRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestClockRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.notification.DomainEventDeliveryStandingService
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.security.ForbiddenException
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(InformationRequestOperationsPostgreSQLResource::class)
class InformationRequestOperationsTransactionTest
{
    @Inject lateinit var dataSource: DataSource
    @Inject lateinit var runtime: InformationRequestRuntimeTestServices
    @Inject lateinit var requestRepository: InformationRequestRepository
    @Inject lateinit var clockRepository: InformationRequestClockRepository
    @Inject lateinit var eventRepository: InformationRequestClockEventRepository
    @Inject lateinit var policies: InformationRequestClockPolicyService
    @Inject lateinit var recorder: InformationRequestClockRecorder
    @Inject lateinit var processor: InformationRequestClockPointProcessor
    @Inject lateinit var worker: InformationRequestNoticeWorker
    @Inject lateinit var noticeStates: InformationRequestNoticeStateReader
    @Inject lateinit var deliveries: DomainEventDeliveryStandingService

    @Test
    fun `the queue states each request's service level, notices, and exceptions and orders by the nearest due instant`()
    {
        val fixture = fixture()
        val service = service(fixture)

        val page = QuarkusTransaction.requiringNew().call { service.queue(InformationRequestOperationsFilter()) }

        assertEquals(listOf(fixture.overdueId, fixture.dueSoonId, fixture.quietId), page.rows.map { it.request.id })
        assertEquals(3, page.total)
        val overdue = page.rows[0]
        assertEquals(InformationRequestSlaStatus.OVERDUE, overdue.standing.status)
        assertEquals(START.plusSeconds(HOUR), overdue.standing.nearestDueAt)
        assertEquals(
            mapOf(InformationRequestNoticeDeliveryState.DELIVERED to 2, InformationRequestNoticeDeliveryState.UNDELIVERABLE to 1),
            overdue.noticeCounts,
        )
        assertEquals(mapOf(InformationRequestOperationsException.NOTICE_UNDELIVERABLE to 1), overdue.exceptionCounts)
        val dueSoon = page.rows[1]
        assertEquals(InformationRequestSlaStatus.DUE_SOON, dueSoon.standing.status)
        assertEquals(emptyMap<InformationRequestOperationsException, Int>(), dueSoon.exceptionCounts)
        val quiet = page.rows[2]
        assertEquals(InformationRequestSlaStatus.NO_CLOCK, quiet.standing.status)
        assertEquals(
            mapOf(
                InformationRequestOperationsException.AUTOMATION_SKIPPED to 1,
                InformationRequestOperationsException.EVENT_DELIVERY_FAILING to 1,
            ),
            quiet.exceptionCounts,
        )
        assertEquals(0, quiet.clockCount)
    }

    @Test
    fun `the queue filters by service level, exception, state, and Exchange and pages its results`()
    {
        val fixture = fixture()
        val service = service(fixture)

        fun ids(filter: InformationRequestOperationsFilter) =
            QuarkusTransaction.requiringNew().call { service.queue(filter) }.rows.map { it.request.id }

        assertEquals(listOf(fixture.dueSoonId), ids(InformationRequestOperationsFilter(slaStatuses = setOf(InformationRequestSlaStatus.DUE_SOON))))
        assertEquals(listOf(fixture.overdueId, fixture.quietId), ids(InformationRequestOperationsFilter(exceptionsOnly = true)))
        assertEquals(
            listOf(fixture.quietId),
            ids(InformationRequestOperationsFilter(exceptions = setOf(InformationRequestOperationsException.AUTOMATION_SKIPPED))),
        )
        assertEquals(listOf(fixture.quietId), ids(InformationRequestOperationsFilter(states = setOf(InformationRequestState.IN_PROGRESS))))
        assertEquals(emptyList<UUID>(), ids(InformationRequestOperationsFilter(exchangeId = UUID.randomUUID())))
        val second = QuarkusTransaction.requiringNew().call { service.queue(InformationRequestOperationsFilter(limit = 1, offset = 1)) }
        assertEquals(listOf(fixture.dueSoonId), second.rows.map { it.request.id })
        assertEquals(3, second.total)
    }

    @Test
    fun `a caller who may not administer the owner's requests is refused`()
    {
        val fixture = fixture()
        val access = mock<InformationRequestOwnerScopeAccess>()
        whenever(access.currentOwner()).thenReturn(fixture.owner)
        whenever(access.requireAccess(fixture.owner, Action.INFORMATION_REQUEST_VIEW_OPERATIONS_QUEUE)).thenThrow(ForbiddenException("denied"))
        val service = InformationRequestOperationsService(
            access, requestRepository, clockRepository, eventRepository, policies, noticeStates, deliveries, Clock.fixed(NOW, ZoneOffset.UTC),
        )

        assertThrows(ForbiddenException::class.java) { QuarkusTransaction.requiringNew().call { service.queue(InformationRequestOperationsFilter()) } }
    }

    private fun fixture(): OperationsFixture
    {
        val calendarVersion = UUID.randomUUID()
        val reminderVersion = UUID.randomUUID()
        val dueSoonId = UUID.randomUUID()
        val quietId = UUID.randomUUID()
        val runtimeFixture = dataSource.connection.use { connection ->
            val runtimeFixture = SubmissionRuntimeSqlFixture(connection)
            val clocks = ClockSqlFixture(connection, runtimeFixture)
            clocks.insertVersion(calendarVersion, versionNumber = 2, clockType = "CALENDAR", standard = 60, urgent = 60, escalation = null)
            clocks.insertVersion(reminderVersion, versionNumber = 3, clockType = "CALENDAR", standard = 120, urgent = 120, escalation = null)
            clocks.insertReminder(reminderVersion, ordinal = 1, minutes = 60)
            execute(
                connection,
                """
                INSERT INTO information_request_party
                    (id, information_request_id, role_key, principal_kind, principal_id, active, party_revision,
                     assigned_at, created_at, updated_at)
                VALUES (?, ?, 'PREPARER', 'PRINCIPAL_GROUP', ?, TRUE, 1, now(), now(), now())
                """.trimIndent(),
                UUID.randomUUID(),
                runtimeFixture.requestId,
                UUID.randomUUID(),
            )
            insertRequest(connection, runtimeFixture, dueSoonId, "ISSUED", START.minusSeconds(HOUR))
            insertRequest(connection, runtimeFixture, quietId, "IN_PROGRESS", START.plusSeconds(HOUR))
            insertUndeliveredEvent(connection, runtimeFixture, quietId)
            runtimeFixture
        }
        val overdueClock = startClock(runtimeFixture, runtimeFixture.requestId, calendarVersion)
        startClock(runtimeFixture, dueSoonId, reminderVersion)
        processor.process(overdueClock, START.plusSeconds(HOUR + MINUTE))
        worker.dispatchForRequest(runtimeFixture.requestId)
        return OperationsFixture(
            owner = InformationRequestOwnerRef(InformationRequestOwnerType.ORGANIZATION, runtimeFixture.template.organizationId),
            userId = runtimeFixture.template.userId,
            overdueId = runtimeFixture.requestId,
            dueSoonId = dueSoonId,
            quietId = quietId,
        )
    }

    private fun startClock(runtimeFixture: SubmissionRuntimeSqlFixture, requestId: UUID, versionId: UUID): UUID
    {
        val service = InformationRequestClockService(
            runtime.build(requestId).gate, clockRepository, eventRepository, policies, recorder,
            runtime.commandReceiptService, Clock.fixed(START, ZoneOffset.UTC),
        )
        return QuarkusTransaction.requiringNew().call {
            service.start(
                StartInformationRequestClockCommand(
                    requestId = requestId,
                    clockKey = "response",
                    policyVersionId = versionId,
                    urgency = InformationRequestClockUrgency.STANDARD,
                    receivedAt = START,
                    access = RequestAccessContext(PrincipalRef.user(runtimeFixture.template.userId), AuthorizationContext(sessionRef = "owner")),
                    idempotencyKey = "start-$requestId",
                ),
            )
        }.clock.id
    }

    private fun insertRequest(connection: java.sql.Connection, runtimeFixture: SubmissionRuntimeSqlFixture, id: UUID, state: String, createdAt: Instant)
    {
        execute(
            connection,
            """
            INSERT INTO information_request
                (id, exchange_id, template_version_id, owner_type, owner_organization_id, state,
                 gates_exchange_closure, aggregate_revision, party_revision, created_at, updated_at, issued_at, started_at)
            VALUES (?, ?, ?, 'ORGANIZATION', ?, ?, FALSE, 1, 1, ?, ?, ?, ?)
            """.trimIndent(),
            id,
            runtimeFixture.exchangeId,
            runtimeFixture.template.versionId,
            runtimeFixture.template.organizationId,
            state,
            java.sql.Timestamp.from(createdAt),
            java.sql.Timestamp.from(createdAt),
            java.sql.Timestamp.from(createdAt),
            if (state == "IN_PROGRESS") java.sql.Timestamp.from(createdAt) else null,
        )
    }

    private fun insertUndeliveredEvent(connection: java.sql.Connection, runtimeFixture: SubmissionRuntimeSqlFixture, requestId: UUID)
    {
        val skippedEvent = UUID.randomUUID()
        listOf(skippedEvent to "DELIVERED", UUID.randomUUID() to "PENDING").forEachIndexed { index, (eventId, status) ->
            execute(
                connection,
                """
                INSERT INTO workflow_event_outbox
                    (id, event_id, idempotency_key, event_type, organization_id, owner_kind, owner_id, ordering_key,
                     envelope_json, status, attempt_count, created_at, next_attempt_at)
                VALUES (?, ?, ?, 'information_request.request.view', ?, 'ORGANIZATION', ?, ?, '{}', ?, ?, now(), now() + interval '1 day')
                """.trimIndent(),
                UUID.randomUUID(),
                eventId,
                "operations-$eventId",
                runtimeFixture.template.organizationId,
                runtimeFixture.template.organizationId,
                InformationRequestTransitionHistoryService.orderingKeyOf(requestId),
                status,
                index * 2,
            )
        }
        execute(
            connection,
            "INSERT INTO domain_event_consumption (consumer_key, event_id, outcome, detail) VALUES ('probe-consumer', ?, 'SKIPPED', 'SUBSCRIPTION_DENIED')",
            skippedEvent,
        )
    }

    private fun service(fixture: OperationsFixture): InformationRequestOperationsService
    {
        val access = mock<InformationRequestOwnerScopeAccess>()
        whenever(access.currentOwner()).thenReturn(fixture.owner)
        whenever(access.requireAccess(fixture.owner, Action.INFORMATION_REQUEST_VIEW_OPERATIONS_QUEUE)).thenReturn(PrincipalRef.user(fixture.userId))
        return InformationRequestOperationsService(
            access, requestRepository, clockRepository, eventRepository, policies, noticeStates, deliveries, Clock.fixed(NOW, ZoneOffset.UTC),
        )
    }

    private data class OperationsFixture(
        val owner: InformationRequestOwnerRef,
        val userId: UUID,
        val overdueId: UUID,
        val dueSoonId: UUID,
        val quietId: UUID,
    )

    private companion object
    {
        const val MINUTE = 60L
        const val HOUR = 3600L
        val START: Instant = Instant.parse("2026-09-25T08:00:00Z")
        val NOW: Instant = START.plusSeconds(90 * MINUTE)
    }
}
