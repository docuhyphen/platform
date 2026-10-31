package com.docuhyphen.app.api.service.informationrequest.externalsource

import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.externalsource.*
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.externalsource.InformationRequestConnectorExchangeRepository
import com.docuhyphen.app.api.repository.informationrequest.lifecycle.InformationRequestTransitionRepository
import com.docuhyphen.app.api.service.audit.*
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.command.CommandReceiptScope
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestOwnerScopeAccess
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestAuditActorLabelResolver
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestAuditService
import com.docuhyphen.app.api.service.informationrequest.conformance.ConformanceRequestSupport
import com.docuhyphen.app.api.service.informationrequest.externalsource.model.ConnectorAuditEvent
import com.docuhyphen.app.api.service.informationrequest.externalsource.model.ConnectorSetup
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestRuntimeServices
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestResponseStart
import com.docuhyphen.app.api.service.notification.DomainEventPublisher
import com.docuhyphen.app.api.service.notification.TransactionalEventSink
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.sql.DataSource
import kotlin.collections.ArrayDeque

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestConnectorExchangeHistoryTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    @Inject
    lateinit var transitionRepository: InformationRequestTransitionRepository

    @Inject
    lateinit var exchangeRepository: InformationRequestConnectorExchangeRepository

    @Inject
    lateinit var authorizationService: AuthorizationService

    @Inject
    lateinit var auditHistory: AuditTargetHistoryService

    @Inject
    lateinit var actorLabels: InformationRequestAuditActorLabelResolver

    @Inject
    lateinit var receiptScope: CommandReceiptScope

    @Inject
    @TransactionalEventSink
    lateinit var publisher: DomainEventPublisher

    private val support by lazy { ConformanceRequestSupport(dataSource, runtime, requestRepository) }

    @Test
    fun `a polled exchange records each attempt and outcome as its own system event beside its history`() {
        val connector = ScriptedConnector()
        val setup = setup(connector)
        val requestedAt = setup.clock.instant()
        connector.next { InformationRequestConnectorOutcome.Pending("external-1", Duration.ofSeconds(30)) }
        connector.next { completed("external-1", requestedAt.minus(Duration.ofMinutes(1))) }
        val exchange = request(setup, "polled", lookup = " record-1 ")

        assertEquals(
            InformationRequestConnectorExchangeState.PENDING,
            setup.services.connectorWorker.process(exchange.id)
        )
        setup.clock.instant = requestedAt.plusSeconds(31)
        assertEquals(
            InformationRequestConnectorExchangeState.COMPLETED,
            setup.services.connectorWorker.process(exchange.id)
        )

        val events = events(setup)
        assertEquals(
            listOf("request", "attempt", "pending", "attempt", "complete", "record"),
            events.map { it.eventTypeKey?.removePrefix(EXTERNAL_PREFIX) },
        )
        val progress = events.subList(1, 5)
        assertEquals(
            listOf(
                "START_EXTERNAL_SOURCE_ATTEMPT",
                "RECORD_EXTERNAL_SOURCE_PENDING",
                "START_EXTERNAL_SOURCE_ATTEMPT",
                "COMPLETE_EXTERNAL_SOURCE",
            ),
            progress.map { it.mutation },
        )
        assertEquals(listOf("1", "1", "2", "2"), progress.map { it.payload["attemptCount"] })
        assertEquals(listOf("REQUESTED", "PENDING", "PENDING", "COMPLETED"), progress.map { it.payload["state"] })
        assertEquals(
            listOf(
                requestedAt.plus(LEASE).toString(),
                requestedAt.plusSeconds(30).toString(),
                requestedAt.plusSeconds(31).plus(LEASE).toString(),
                null,
            ),
            progress.map { it.payload["nextAttemptAt"] },
        )
        progress.forEach { event ->
            assertEquals(exchange.id.toString(), event.payload["connectorExchangeId"])
            assertEquals(CONTRACT.key, event.payload["connectorKey"])
            assertEquals(setup.request.answers.requirementId.toString(), event.payload["requirementId"])
            assertEquals("SUCCESS", event.outcome)
            assertSystemAttributed(event)
        }
        assertEquals(events.size, events.mapNotNull { it.idempotencyKey }.toSet().size)
        val payloads = events.joinToString(" ") { it.payload.toString() }
        assertTrue(listOf("record-1", "external-1", "Recorded answer").none { payloads.contains(it) }, payloads)

        val reconciliation = QuarkusTransaction.requiringNew()
            .call { auditService(setup).reconciliation(setup.request.requestId, setup.owner) }
        val recorded = events.map { UUID.fromString(it.transitionId) }.toSet()
        assertTrue(reconciliation.missing.none { it.transitionId in recorded }, reconciliation.missing.toString())
        assertTrue(
            reconciliation.unmatched.none { it.eventTypeKey.startsWith(EXTERNAL_PREFIX) },
            reconciliation.unmatched.toString(),
        )
    }

    @Test
    fun `a refusal, a rejected result, a missing connector and a stopped request each end the exchange with one failure event`() {
        val connector = ScriptedConnector()
        val setup = setup(connector)

        connector.next { InformationRequestConnectorOutcome.Failed("record.not_found") }
        val refused = request(setup, "refused")
        assertEquals(
            InformationRequestConnectorExchangeState.FAILED,
            setup.services.connectorWorker.process(refused.id)
        )
        assertProgress(setup, refused.id, listOf("attempt" to "1", "fail" to "1"))
        assertFailure(setup, refused.id, "record.not_found")

        connector.next { completed("external-1", setup.clock.instant(), resultKey = "undeclared-key") }
        val rejected = request(setup, "rejected")
        assertEquals(
            InformationRequestConnectorExchangeState.FAILED,
            setup.services.connectorWorker.process(rejected.id)
        )
        assertProgress(setup, rejected.id, listOf("attempt" to "1", "fail" to "1"))
        assertFailure(setup, rejected.id, "result_rejected")

        val unavailable = request(setup, "unavailable")
        val withoutConnector = runtime.build(
            setup.request.requestId,
            centralAuthorization = authorizationService,
            externalClock = setup.clock,
        )
        assertEquals(
            InformationRequestConnectorExchangeState.FAILED,
            withoutConnector.connectorWorker.process(unavailable.id)
        )
        assertProgress(setup, unavailable.id, listOf("fail" to "0"))
        assertFailure(setup, unavailable.id, "connector_unavailable")

        val stopped = request(setup, "stopped")
        dataSource.connection.use { connection ->
            execute(
                connection,
                "UPDATE information_request SET state = 'CANCELLED', cancelled_at = now() WHERE id = ?",
                setup.request.requestId,
            )
        }
        assertEquals(
            InformationRequestConnectorExchangeState.FAILED,
            setup.services.connectorWorker.process(stopped.id)
        )
        assertEquals(2, connector.calls.size, "Only the refused and the rejected exchange call the connector")
        assertProgress(setup, stopped.id, listOf("fail" to "0"))
        assertFailure(setup, stopped.id, "request_not_accepting_values")
        assertEquals(0, importedValues(setup))
    }

    @Test
    fun `each failed call records its retry until the attempts run out, and an exchange not yet due or finished records nothing`() {
        val connector = ScriptedConnector()
        val setup = setup(connector)
        val exchange = request(setup, "retried")
        val expected = mutableListOf<Pair<String, String>>()
        repeat(InformationRequestConnectorWorker.MAXIMUM_ATTEMPTS - 1) { index ->
            val attempt = (index + 1).toString()
            connector.next { throw IllegalStateException("remote unavailable") }
            assertEquals(
                InformationRequestConnectorExchangeState.REQUESTED,
                setup.services.connectorWorker.process(exchange.id)
            )
            expected += "attempt" to attempt
            expected += "retry" to attempt
            assertProgress(setup, exchange.id, expected)
            val due = requireNotNull(stored(exchange.id).nextAttemptAt).toInstant()
            assertEquals(setup.clock.instant().plus(Duration.ofMinutes(index + 1L)), due)
            val retry = progressOf(setup, exchange.id).last()
            assertEquals("FAILURE", retry.outcome)
            assertEquals("REQUESTED", retry.payload["state"])
            assertEquals(due.toString(), retry.payload["nextAttemptAt"])
            assertNull(
                setup.services.connectorWorker.process(exchange.id),
                "an exchange is not called again before it is due"
            )
            setup.clock.instant = due
        }
        connector.next { throw IllegalStateException("remote unavailable") }
        assertEquals(
            InformationRequestConnectorExchangeState.FAILED,
            setup.services.connectorWorker.process(exchange.id)
        )
        assertNull(setup.services.connectorWorker.process(exchange.id), "a finished exchange is not processed again")

        expected += "attempt" to InformationRequestConnectorWorker.MAXIMUM_ATTEMPTS.toString()
        expected += "fail" to InformationRequestConnectorWorker.MAXIMUM_ATTEMPTS.toString()
        assertProgress(setup, exchange.id, expected)
        assertFailure(setup, exchange.id, "attempts_exhausted")
        assertEquals(InformationRequestConnectorWorker.MAXIMUM_ATTEMPTS, connector.calls.size)
    }

    @Test
    fun `an attempt overtaken while its connector answers records nothing for its late outcome`() {
        val connector = ScriptedConnector()
        val setup = setup(connector)
        val exchange = request(setup, "overtaken")
        connector.next {
            setup.clock.instant = setup.clock.instant().plus(LEASE).plusSeconds(1)
            assertEquals(
                InformationRequestConnectorExchangeState.PENDING,
                setup.services.connectorWorker.process(exchange.id),
                "a lease that ran out lets the next attempt start",
            )
            completed("external-1", setup.clock.instant())
        }
        connector.next { InformationRequestConnectorOutcome.Pending("external-2", Duration.ofSeconds(30)) }

        assertEquals(
            InformationRequestConnectorExchangeState.PENDING,
            setup.services.connectorWorker.process(exchange.id)
        )

        assertProgress(setup, exchange.id, listOf("attempt" to "1", "attempt" to "2", "pending" to "2"))
        val overtaken = stored(exchange.id)
        assertEquals(2, overtaken.attemptCount)
        assertEquals("external-2", overtaken.externalReference)
        assertEquals(0, importedValues(setup), "The overtaken attempt's late result is not recorded")
    }

    @Test
    fun `an attempt whose audit event cannot be captured is not started and its connector is not called`() {
        val connector = ScriptedConnector()
        val setup = setup(connector)
        val exchange = request(setup, "unaudited-attempt")
        connector.next { InformationRequestConnectorOutcome.Failed("record.not_found") }
        val unaudited = servicesFailingOn(setup, connector, ATTEMPT_EVENT)

        assertThrows(AuditCaptureFailedException::class.java) { unaudited.connectorWorker.process(exchange.id) }

        val untouched = stored(exchange.id)
        assertEquals(InformationRequestConnectorExchangeState.REQUESTED, untouched.state)
        assertEquals(0, untouched.attemptCount)
        assertEquals(exchange.nextAttemptAt?.toInstant(), untouched.nextAttemptAt?.toInstant())
        assertTrue(connector.calls.isEmpty(), "The connector is never called for an attempt that was not recorded")
        assertEquals(emptyList<String>(), progressMutations(setup))
    }

    @Test
    fun `an outcome whose audit event cannot be captured is not recorded and the exchange waits for its next attempt`() {
        val connector = ScriptedConnector()
        val setup = setup(connector)
        val exchange = request(setup, "unaudited-outcome")
        connector.next { InformationRequestConnectorOutcome.Failed("record.not_found") }
        val unaudited = servicesFailingOn(setup, connector, FAIL_EVENT)

        assertThrows(AuditCaptureFailedException::class.java) { unaudited.connectorWorker.process(exchange.id) }

        val waiting = stored(exchange.id)
        assertEquals(InformationRequestConnectorExchangeState.REQUESTED, waiting.state)
        assertEquals(1, waiting.attemptCount)
        assertNull(waiting.failureCode)
        assertNull(waiting.completedAt)
        assertEquals(listOf("START_EXTERNAL_SOURCE_ATTEMPT"), progressMutations(setup))

        setup.clock.instant = requireNotNull(waiting.nextAttemptAt).toInstant()
        connector.next { InformationRequestConnectorOutcome.Failed("record.not_found") }
        assertEquals(
            InformationRequestConnectorExchangeState.FAILED,
            setup.services.connectorWorker.process(exchange.id)
        )
        assertEquals(
            listOf("START_EXTERNAL_SOURCE_ATTEMPT", "START_EXTERNAL_SOURCE_ATTEMPT", "FAIL_EXTERNAL_SOURCE"),
            progressMutations(setup),
        )
        assertFailure(setup, exchange.id, "record.not_found")
        assertEquals("2", progressOf(setup, exchange.id).last().payload["attemptCount"])
    }

    @Test
    fun `a completion whose imported value cannot be audited records neither the completion nor the value`() {
        val connector = ScriptedConnector()
        val setup = setup(connector)
        val exchange = request(setup, "unaudited-value")
        connector.next { completed("external-1", setup.clock.instant()) }
        val unaudited = servicesFailingOn(setup, connector, RECORD_EVENT)

        assertThrows(AuditCaptureFailedException::class.java) { unaudited.connectorWorker.process(exchange.id) }

        val waiting = stored(exchange.id)
        assertEquals(InformationRequestConnectorExchangeState.REQUESTED, waiting.state)
        assertNull(waiting.externalReference)
        assertEquals(0, importedValues(setup))
        assertEquals(listOf("START_EXTERNAL_SOURCE_ATTEMPT"), progressMutations(setup))
    }

    private fun setup(connector: ScriptedConnector): ConnectorSetup {
        val request = support.fieldRequest(prepare = { connection, prepared ->
            execute(
                connection,
                """
                INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
                VALUES (?, 'EXCHANGE', ?, 'USER', ?, 'OWNER', 'DIRECT', 'ACTIVE', now())
                """.trimIndent(),
                UUID.randomUUID(),
                prepared.runtime.exchangeId,
                prepared.runtime.template.userId,
            )
        })
        val clock = MutableClock(Instant.now().truncatedTo(ChronoUnit.MICROS))
        val services = runtime.build(
            request.requestId,
            centralAuthorization = authorizationService,
            connectors = listOf(connector),
            externalClock = clock,
        )
        return ConnectorSetup(request, services, clock, support.owner(request))
    }

    private fun servicesFailingOn(
        setup: ConnectorSetup,
        connector: ScriptedConnector,
        eventTypeKey: String,
    ): InformationRequestRuntimeServices =
        runtime.build(
            setup.request.requestId,
            history = historyFailingOn(eventTypeKey),
            centralAuthorization = authorizationService,
            connectors = listOf(connector),
            externalClock = setup.clock,
        )

    private fun historyFailingOn(eventTypeKey: String): InformationRequestTransitionHistoryService {
        val recorder = mock<AuditRecorder>().also {
            whenever(it.record(any())).thenAnswer { invocation ->
                val draft = invocation.getArgument<AuditEventDraft>(0)
                if (draft.eventTypeKey == eventTypeKey) AuditCaptureResult.Degraded(
                    draft.eventId,
                    "Audit outbox unavailable"
                )
                else AuditCaptureResult.Captured(UUID.randomUUID(), draft.eventId)
            }
        }
        return InformationRequestTransitionHistoryService(
            transitionRepository,
            recorder,
            publisher,
            InformationRequestResponseStart(requestRepository, Clock.systemUTC()),
            actorLabels,
            receiptScope,
        )
    }

    private fun auditService(setup: ConnectorSetup) = InformationRequestAuditService(
        runtime.build(setup.request.requestId).gate,
        mock<InformationRequestOwnerScopeAccess>(),
        auditHistory,
        transitionRepository,
    )

    private fun request(
        setup: ConnectorSetup,
        key: String,
        lookup: String? = null
    ): InformationRequestConnectorExchange =
        QuarkusTransaction.requiringNew().call {
            setup.services.connectorExchanges.request(
                RequestInformationRequestConnectorExchangeCommand(
                    setup.request.requestId,
                    setup.request.answers.requirementId,
                    CONTRACT.key,
                    lookup,
                    setup.owner,
                    key,
                ),
            )
        }

    private fun completed(
        reference: String,
        verifiedAt: Instant,
        resultKey: String = "recorded-value",
    ): InformationRequestConnectorOutcome =
        InformationRequestConnectorOutcome.Completed(
            reference,
            InformationRequestConnectorResult(
                source = "verification-service",
                confidence = InformationRequestSourceConfidence.VERIFIED,
                verifiedAt = verifiedAt,
                expiresAt = null,
                provenanceReference = "verification-2026-10",
                values = listOf(
                    InformationRequestConnectorValue(
                        resultKey,
                        FieldValueType.SHORT_TEXT,
                        JsonPrimitive("Recorded answer")
                    ),
                ),
            ),
        )

    private fun assertProgress(setup: ConnectorSetup, exchangeId: UUID, expected: List<Pair<String, String>>) {
        val progress = progressOf(setup, exchangeId)
        assertEquals(
            expected,
            progress.map {
                it.eventTypeKey.orEmpty().removePrefix(EXTERNAL_PREFIX) to it.payload["attemptCount"].orEmpty()
            },
        )
        progress.forEach { assertSystemAttributed(it) }
    }

    private fun assertFailure(setup: ConnectorSetup, exchangeId: UUID, failureCode: String) {
        val failure = progressOf(setup, exchangeId).last()
        assertEquals(FAIL_EVENT, failure.eventTypeKey)
        assertEquals("FAIL_EXTERNAL_SOURCE", failure.mutation)
        assertEquals("FAILURE", failure.outcome)
        assertEquals("FAILED", failure.payload["state"])
        assertEquals(failureCode, failure.payload["failureCode"])
        assertNull(failure.payload["nextAttemptAt"])
        assertEquals(failureCode, stored(exchangeId).failureCode)
    }

    private fun assertSystemAttributed(event: ConnectorAuditEvent) {
        assertEquals("SERVICE_ACCOUNT", event.transitionActorKind)
        assertEquals("SYSTEM", event.actorKind)
        assertEquals(UUID(0, 0).toString(), event.actorId)
    }

    private fun progressOf(setup: ConnectorSetup, exchangeId: UUID): List<ConnectorAuditEvent> =
        events(setup).filter { it.mutation in PROGRESS_MUTATIONS && it.payload["connectorExchangeId"] == exchangeId.toString() }

    private fun progressMutations(setup: ConnectorSetup): List<String> =
        events(setup).map { it.mutation }.filter { it in PROGRESS_MUTATIONS }

    private fun events(setup: ConnectorSetup): List<ConnectorAuditEvent> =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT transition.id::text, transition.mutation, transition.actor_kind, outbox.event_type_key,
                       outbox.outcome, outbox.actor_kind, outbox.actor_id::text, outbox.idempotency_key, outbox.payload_json
                FROM information_request_transition transition
                LEFT JOIN audit_outbox outbox ON outbox.business_transaction_id = transition.id::text
                WHERE transition.information_request_id = ?
                  AND transition.sequence_number >= (
                      SELECT MIN(earliest.sequence_number)
                      FROM information_request_transition earliest
                      WHERE earliest.information_request_id = transition.information_request_id
                        AND earliest.mutation = 'REQUEST_EXTERNAL_SOURCE'
                  )
                ORDER BY transition.sequence_number
                """.trimIndent(),
            ).use { statement ->
                statement.setObject(1, setup.request.requestId)
                statement.executeQuery().use { rows ->
                    buildList {
                        while (rows.next()) {
                            add(
                                ConnectorAuditEvent(
                                    transitionId = rows.getString(1),
                                    mutation = rows.getString(2),
                                    transitionActorKind = rows.getString(3),
                                    eventTypeKey = rows.getString(4),
                                    outcome = rows.getString(5),
                                    actorKind = rows.getString(6),
                                    actorId = rows.getString(7),
                                    idempotencyKey = rows.getString(8),
                                    payload = rows.getString(9)?.let { payloadOf(it) }.orEmpty(),
                                ),
                            )
                        }
                    }
                }
            }
        }

    private fun payloadOf(json: String): Map<String, String> =
        Json.parseToJsonElement(json).jsonObject.mapValues { (_, value) -> value.jsonPrimitive.content }

    private fun stored(exchangeId: UUID): InformationRequestConnectorExchange =
        QuarkusTransaction.requiringNew().call { requireNotNull(exchangeRepository.findById(exchangeId)) }

    private fun importedValues(setup: ConnectorSetup): Int =
        dataSource.connection.use { connection ->
            queryInt(
                connection,
                "SELECT COUNT(*) FROM information_request_imported_value WHERE information_request_id = ?",
                setup.request.requestId,
            )
        }

    private class ScriptedConnector : InformationRequestConnector {
        private val script = ArrayDeque<(InformationRequestConnectorCall) -> InformationRequestConnectorOutcome>()
        val calls = mutableListOf<InformationRequestConnectorCall>()

        override val contract: InformationRequestConnectorContract = CONTRACT

        fun next(step: (InformationRequestConnectorCall) -> InformationRequestConnectorOutcome) {
            script.addLast(step)
        }

        override fun request(call: InformationRequestConnectorCall): InformationRequestConnectorOutcome = answer(call)

        override fun poll(call: InformationRequestConnectorCall): InformationRequestConnectorOutcome = answer(call)

        private fun answer(call: InformationRequestConnectorCall): InformationRequestConnectorOutcome {
            calls.add(call)
            return script.removeFirst().invoke(call)
        }
    }

    private companion object {
        val CONTRACT = InformationRequestConnectorContract(
            key = "history-verification",
            kind = InformationRequestConnectorKind.EXTERNAL_VERIFICATION,
            contractVersion = 1,
            resultKeys = setOf("recorded-value"),
        )
        val LEASE: Duration = Duration.ofMinutes(5)
        const val EXTERNAL_PREFIX = "information_request.external."
        const val ATTEMPT_EVENT = "information_request.external.attempt"
        const val FAIL_EVENT = "information_request.external.fail"
        const val RECORD_EVENT = "information_request.external.record"
        val PROGRESS_MUTATIONS = setOf(
            "START_EXTERNAL_SOURCE_ATTEMPT",
            "SCHEDULE_EXTERNAL_SOURCE_RETRY",
            "RECORD_EXTERNAL_SOURCE_PENDING",
            "COMPLETE_EXTERNAL_SOURCE",
            "FAIL_EXTERNAL_SOURCE",
        )
    }
}
