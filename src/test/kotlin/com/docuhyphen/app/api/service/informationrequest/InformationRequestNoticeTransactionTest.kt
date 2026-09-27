package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.migration.ClockSqlFixture
import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.model.entity.InformationRequestClockUrgency
import com.docuhyphen.app.api.model.entity.InformationRequestNoticeAttemptOutcome
import com.docuhyphen.app.api.model.entity.InformationRequestNoticeDeliveryState
import com.docuhyphen.app.api.model.entity.InformationRequestNoticeEndpointState
import com.docuhyphen.app.api.model.entity.InformationRequestNoticeSourceKind
import com.docuhyphen.app.api.model.informationrequest.StartInformationRequestClockCommand
import com.docuhyphen.app.api.model.notification.DomainEventConsumptionOutcome
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestClockEventRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestClockRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestNoticeDeliveryAttemptRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestNoticeSequenceAllocationRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestOutboundNoticeRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.notification.DomainEvent
import com.docuhyphen.app.api.service.notification.DomainEventConsumptionService
import com.docuhyphen.app.api.service.notification.DomainEventJson
import com.docuhyphen.app.api.service.notification.EventRouter
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(InformationRequestOperationsPostgreSQLResource::class)
class InformationRequestNoticeTransactionTest
{
    @Inject lateinit var dataSource: DataSource
    @Inject lateinit var runtime: InformationRequestRuntimeTestServices
    @Inject lateinit var clockRepository: InformationRequestClockRepository
    @Inject lateinit var eventRepository: InformationRequestClockEventRepository
    @Inject lateinit var policies: InformationRequestClockPolicyService
    @Inject lateinit var recorder: InformationRequestClockRecorder
    @Inject lateinit var processor: InformationRequestClockPointProcessor
    @Inject lateinit var worker: InformationRequestNoticeWorker
    @Inject lateinit var dispatcher: InformationRequestNoticeDispatcher
    @Inject lateinit var states: InformationRequestNoticeStateReader
    @Inject lateinit var noticeRepository: InformationRequestOutboundNoticeRepository
    @Inject lateinit var allocationRepository: InformationRequestNoticeSequenceAllocationRepository
    @Inject lateinit var attemptRepository: InformationRequestNoticeDeliveryAttemptRepository
    @Inject lateinit var sender: RecordingInformationRequestNoticeSender
    @Inject lateinit var eventRouter: EventRouter
    @Inject lateinit var consumption: DomainEventConsumptionService

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
            assertEquals(setOf(InformationRequestNoticeDeliveryState.DELIVERED), states.states(fixture.requestId).values.toSet())
        }
        assertEquals(2, sequenceValue(fixture))
        assertEquals(2, auditCount(fixture, "information_request.notice.render"))
        assertEquals(2, auditCount(fixture, "information_request.notice.deliver"))
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
                attemptRepository.findForNotice(notice.id).single().outcome == InformationRequestNoticeAttemptOutcome.FAILED
            }
        }
        assertEquals(null, dispatcher.deliver(failedNotice.id, Instant.now()))
        assertEquals(InformationRequestNoticeAttemptOutcome.DELIVERED, dispatcher.deliver(failedNotice.id, Instant.now().plusSeconds(360)))
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
            val missing = noticeRepository.findForRequest(fixture.requestId).single { it.endpointState == InformationRequestNoticeEndpointState.MISSING }
            assertEquals(null, missing.recipientEndpoint)
            assertEquals("NO_ENDPOINT", attemptRepository.findForNotice(missing.id).single().failureCode)
            assertEquals(InformationRequestNoticeDeliveryState.UNDELIVERABLE, states.states(fixture.requestId).getValue(missing.noticeIntentId))
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
                consumption.outcomeOf(InformationRequestNoticeConsumer.CONSUMER_KEY, event),
            )
        }
        assertEquals(2, sequenceValue(fixture))
    }

    private fun overdueFixture(unreachableParty: Boolean = false): NoticeFixture
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
            if (unreachableParty)
            {
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
            }
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
                    access = RequestAccessContext(PrincipalRef.user(runtimeFixture.template.userId), AuthorizationContext(sessionRef = "owner")),
                    idempotencyKey = "start-response",
                ),
            )
        }
        assertTrue(processor.process(started.clock.id, start.plusSeconds(3660)))
        return NoticeFixture(runtimeFixture.requestId, runtimeFixture.template.organizationId)
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
            queryInt(connection, "SELECT current_value FROM sequence_definition WHERE organization_id = ? AND key = 'case-number'", fixture.organizationId)
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

    private data class NoticeFixture(val requestId: UUID, val organizationId: UUID)
}
