package com.docuhyphen.app.api.service.informationrequest.oversight

import com.docuhyphen.app.api.migration.ClockSqlFixture
import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.model.entity.InformationRequestClockUrgency
import com.docuhyphen.app.api.model.entity.InformationRequestNoticeDeliveryState
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.access.InformationRequestOwnerRef
import com.docuhyphen.app.api.model.informationrequest.clock.StartInformationRequestClockCommand
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.model.informationrequest.noauth.InformationRequestAbuseLimits
import com.docuhyphen.app.api.model.informationrequest.notice.InformationRequestReminderResult
import com.docuhyphen.app.api.model.informationrequest.notice.SendInformationRequestRemindersCommand
import com.docuhyphen.app.api.model.informationrequest.oversight.InformationRequestOperationsException
import com.docuhyphen.app.api.model.informationrequest.oversight.InformationRequestOperationsFilter
import com.docuhyphen.app.api.model.informationrequest.oversight.InformationRequestSlaStatus
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.amendment.InformationRequestNoticeIntentRepository
import com.docuhyphen.app.api.repository.informationrequest.clock.InformationRequestClockEventRepository
import com.docuhyphen.app.api.repository.informationrequest.clock.InformationRequestClockRepository
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.identity.PrincipalDisplayService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.InformationRequestTitleReader
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestOwnerScopeAccess
import com.docuhyphen.app.api.service.informationrequest.clock.InformationRequestClockPointProcessor
import com.docuhyphen.app.api.service.informationrequest.clock.InformationRequestClockPolicyService
import com.docuhyphen.app.api.service.informationrequest.clock.InformationRequestClockRecorder
import com.docuhyphen.app.api.service.informationrequest.clock.InformationRequestClockService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import com.docuhyphen.app.api.service.informationrequest.notice.InformationRequestNoticeStateReader
import com.docuhyphen.app.api.service.informationrequest.notice.InformationRequestNoticeWorker
import com.docuhyphen.app.api.service.informationrequest.notice.InformationRequestReminderService
import com.docuhyphen.app.api.service.notification.DomainEventDeliveryStandingService
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.security.ForbiddenException
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.*
import javax.sql.DataSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

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
    @Inject
    lateinit var titleReader: InformationRequestTitleReader
    @Inject
    lateinit var partyRepository: InformationRequestPartyRepository
    @Inject
    lateinit var principalDisplayService: PrincipalDisplayService
    @Inject
    lateinit var gate: InformationRequestMutationGate
    @Inject
    lateinit var intentRepository: InformationRequestNoticeIntentRepository
    @Inject
    lateinit var transitionHistory: InformationRequestTransitionHistoryService
    @Inject
    lateinit var commandReceiptService: CommandReceiptService

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
    fun `the queue names each request, lists its acting assignees, and searches by title, request id prefix, and assignee`()
    {
        val fixture = fixture()
        val service = service(fixture)

        fun queue(filter: InformationRequestOperationsFilter) =
            QuarkusTransaction.requiringNew().call { service.queue(filter) }

        val page = queue(InformationRequestOperationsFilter())
        assertEquals(
            listOf("Collection pattern", "Collection pattern", "Collection pattern"),
            page.rows.map { it.title })
        assertEquals(
            setOf(
                InformationRequestShareRoleKey.ATTESTOR to fixture.attestorUserId,
                InformationRequestShareRoleKey.CONTRIBUTOR to fixture.contributorUserId,
                InformationRequestShareRoleKey.PREPARER to fixture.preparerGroupId,
            ),
            page.rows.single { it.request.id == fixture.overdueId }.assignees.map { it.roleKey to it.principalId }
                .toSet(),
        )
        val member = page.rows.single { it.request.id == fixture.dueSoonId }.assignees.single()
        assertEquals(fixture.userId, member.principalId)
        assertEquals(true, member.label?.endsWith("@process.test"))
        assertEquals(emptyList<Any>(), page.rows.single { it.request.id == fixture.quietId }.assignees)
        assertEquals(3, queue(InformationRequestOperationsFilter(search = "COLLECTION")).total)
        assertEquals(0, queue(InformationRequestOperationsFilter(search = "unrelated")).total)
        assertEquals(
            listOf(fixture.dueSoonId),
            queue(
                InformationRequestOperationsFilter(
                    search = fixture.dueSoonId.toString().take(8).uppercase()
                )
            ).rows.map { it.request.id },
        )
        assertEquals(
            listOf(fixture.overdueId),
            queue(InformationRequestOperationsFilter(assigneeId = fixture.preparerGroupId)).rows.map { it.request.id })
        assertEquals(
            listOf(fixture.dueSoonId),
            queue(InformationRequestOperationsFilter(assigneeId = fixture.userId)).rows.map { it.request.id })
    }

    @Test
    fun `a sent reminder owes each responding party a notice the worker delivers and a batch naming unopen work sends nothing`()
    {
        val fixture = fixture()
        val draftId = generateSequence { UUID.randomUUID() }.first { it > fixture.dueSoonId }
        dataSource.connection.use { insertRequest(it, fixture.runtime, draftId, "DRAFT", START) }
        val reminders = reminderService(fixture)

        fun send(requestIds: List<UUID>, key: String) =
            QuarkusTransaction.requiringNew()
                .call { reminders.send(SendInformationRequestRemindersCommand(requestIds, key)) }

        val sent = send(listOf(fixture.overdueId), "remind-overdue")
        worker.dispatchForRequest(fixture.overdueId)
        val replayed = send(listOf(fixture.overdueId), "remind-overdue")
        val coolingDown = send(listOf(fixture.overdueId), "remind-overdue-again").single()
        val refused = assertThrows(InformationRequestLifecycleException::class.java) {
            send(
                listOf(draftId, fixture.dueSoonId),
                "remind-mixed"
            )
        }

        assertEquals(listOf(InformationRequestReminderResult(fixture.overdueId, 3)), sent)
        assertEquals(sent, replayed)
        assertEquals(0, coolingDown.noticeCount)
        assertTrue(requireNotNull(coolingDown.cooldownUntil).isAfter(Instant.now().plus(Duration.ofHours(23))))
        assertEquals(
            mapOf(
                InformationRequestNoticeDeliveryState.DELIVERED to 4,
                InformationRequestNoticeDeliveryState.UNDELIVERABLE to 2
            ),
            QuarkusTransaction.requiringNew().call { noticeStates.statesForRequests(listOf(fixture.overdueId)) }
                .getValue(fixture.overdueId).groupingBy { it }.eachCount(),
        )
        assertEquals(
            3,
            count(
                "SELECT count(*) FROM information_request_notice_intent WHERE transition_id IS NOT NULL AND information_request_id = ?",
                fixture.overdueId
            )
        )
        assertEquals(
            1,
            count(
                "SELECT count(*) FROM information_request_transition WHERE mutation = 'SEND_REMINDER' AND information_request_id = ?",
                fixture.overdueId
            )
        )
        assertEquals(InformationRequestErrorCatalog.STATE_INVALID, refused.reasonCode)
        assertEquals(
            0,
            count(
                "SELECT count(*) FROM information_request_transition WHERE mutation = 'SEND_REMINDER' AND information_request_id = ?",
                fixture.dueSoonId
            )
        )
        assertEquals(
            0,
            count(
                "SELECT count(*) FROM information_request_notice_intent WHERE information_request_id = ?",
                fixture.dueSoonId
            )
        )
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
            titleReader,
            partyRepository,
            principalDisplayService,
        )

        assertThrows(ForbiddenException::class.java) { QuarkusTransaction.requiringNew().call { service.queue(InformationRequestOperationsFilter()) } }
    }

    private fun fixture(): OperationsFixture
    {
        val calendarVersion = UUID.randomUUID()
        val reminderVersion = UUID.randomUUID()
        val dueSoonId = UUID.randomUUID()
        val quietId = UUID.randomUUID()
        val preparerGroupId = UUID.randomUUID()
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
                preparerGroupId,
            )
            insertRequest(connection, runtimeFixture, dueSoonId, "ISSUED", START.minusSeconds(HOUR))
            execute(
                connection,
                """
                INSERT INTO information_request_party
                    (id, information_request_id, role_key, principal_kind, principal_id, active, party_revision,
                     assigned_at, created_at, updated_at)
                VALUES (?, ?, 'CONTRIBUTOR', 'USER', ?, TRUE, 1, now(), now(), now())
                """.trimIndent(),
                UUID.randomUUID(),
                dueSoonId,
                runtimeFixture.template.userId,
            )
            insertRequest(connection, runtimeFixture, quietId, "IN_PROGRESS", START.plusSeconds(HOUR))
            insertUndeliveredEvent(connection, runtimeFixture, quietId)
            runtimeFixture
        }
        val overdueClock = startClock(runtimeFixture, runtimeFixture.requestId, calendarVersion)
        startClock(runtimeFixture, dueSoonId, reminderVersion)
        processor.process(overdueClock, START.plusSeconds(HOUR + MINUTE))
        worker.dispatchForRequest(runtimeFixture.requestId)
        return OperationsFixture(
            runtime = runtimeFixture,
            owner = InformationRequestOwnerRef(InformationRequestOwnerType.ORGANIZATION, runtimeFixture.template.organizationId),
            userId = runtimeFixture.template.userId,
            contributorUserId = runtimeFixture.contributorUserId,
            attestorUserId = runtimeFixture.attestorUserId,
            preparerGroupId = preparerGroupId,
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
            titleReader,
            partyRepository,
            principalDisplayService,
        )
    }

    private fun reminderService(fixture: OperationsFixture): InformationRequestReminderService
    {
        val access = mock<InformationRequestOwnerScopeAccess>()
        whenever(access.currentOwner()).thenReturn(fixture.owner)
        whenever(
            access.requireAccess(
                fixture.owner,
                Action.INFORMATION_REQUEST_SEND_REMINDERS
            )
        ).thenReturn(PrincipalRef.user(fixture.userId))
        return InformationRequestReminderService(
            access,
            requestRepository,
            gate,
            partyRepository,
            intentRepository,
            transitionHistory,
            commandReceiptService,
            InformationRequestAbuseLimits(reminderCooldown = Duration.ofHours(24)),
            Clock.systemUTC(),
        )
    }

    private fun count(sql: String, requestId: UUID): Int =
        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setObject(1, requestId)
                statement.executeQuery().use { result ->
                    result.next()
                    result.getInt(1)
                }
            }
        }

    private data class OperationsFixture(
        val runtime: SubmissionRuntimeSqlFixture,
        val owner: InformationRequestOwnerRef,
        val userId: UUID,
        val contributorUserId: UUID,
        val attestorUserId: UUID,
        val preparerGroupId: UUID,
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
