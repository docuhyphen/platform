package com.docuhyphen.app.api.service.informationrequest.externalsource

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.migration.queryString
import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.entity.InformationRequestConnectorExchangeState
import com.docuhyphen.app.api.model.entity.InformationRequestConnectorKind
import com.docuhyphen.app.api.model.entity.InformationRequestImportedValueSource
import com.docuhyphen.app.api.model.entity.InformationRequestSourceConfidence
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.externalsource.InformationRequestConnectorCall
import com.docuhyphen.app.api.model.informationrequest.externalsource.InformationRequestConnectorContract
import com.docuhyphen.app.api.model.informationrequest.externalsource.InformationRequestConnectorOutcome
import com.docuhyphen.app.api.model.informationrequest.externalsource.InformationRequestConnectorResult
import com.docuhyphen.app.api.model.informationrequest.externalsource.InformationRequestConnectorValue
import com.docuhyphen.app.api.model.informationrequest.externalsource.RequestInformationRequestConnectorExchangeCommand
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.externalsource.InformationRequestConnectorExchangeRepository
import com.docuhyphen.app.api.repository.informationrequest.lifecycle.InformationRequestTransitionRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeServices
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.conformance.ConformanceRequest
import com.docuhyphen.app.api.service.informationrequest.conformance.ConformanceRequestSupport
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.security.ForbiddenException
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.common.QuarkusTestResource
import jakarta.inject.Inject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestConnectorTransactionTest
{
    @Inject lateinit var dataSource: DataSource
    @Inject lateinit var runtime: InformationRequestRuntimeTestServices
    @Inject lateinit var requestRepository: InformationRequestRepository
    @Inject lateinit var transitionRepository: InformationRequestTransitionRepository
    @Inject lateinit var exchangeRepository: InformationRequestConnectorExchangeRepository
    @Inject lateinit var authorizationService: AuthorizationService

    private val support by lazy { ConformanceRequestSupport(dataSource, runtime, requestRepository) }

    @Test
    fun `a requested verification is polled until it completes and its values arrive untrusted with full provenance`()
    {
        val connector = ScriptedConnector(VERIFICATION)
        val setup = setup(connector)
        support.answerField(setup.services, setup.request, "Recorded answer", "answer")
        val answerBefore = answer(setup.request)
        val verifiedAt = setup.clock.instant().minus(Duration.ofMinutes(5))
        connector.next { InformationRequestConnectorOutcome.Pending("external-1", Duration.ofSeconds(30)) }
        connector.next {
            InformationRequestConnectorOutcome.Completed(
                "external-1",
                InformationRequestConnectorResult(
                    source = "verification-service",
                    confidence = InformationRequestSourceConfidence.VERIFIED,
                    verifiedAt = verifiedAt,
                    expiresAt = verifiedAt.plus(Duration.ofDays(90)),
                    provenanceReference = "verification-2026-09",
                    values = listOf(
                        InformationRequestConnectorValue("recorded-value", FieldValueType.SHORT_TEXT, JsonPrimitive("Recorded answer")),
                        InformationRequestConnectorValue("verified-status", FieldValueType.SHORT_TEXT, JsonPrimitive("current")),
                    ),
                ),
            )
        }

        val exchange = QuarkusTransaction.requiringNew().call { setup.services.connectorExchanges.request(request(setup, "verify", lookup = " record-1 ")) }
        assertEquals(InformationRequestConnectorExchangeState.REQUESTED, exchange.state)
        assertEquals("record-1", exchange.lookupReference)
        assertEquals(InformationRequestConnectorKind.EXTERNAL_VERIFICATION, exchange.connectorKind)
        assertEquals(1, exchange.contractVersion)

        assertEquals(InformationRequestConnectorExchangeState.PENDING, setup.services.connectorWorker.process(exchange.id))
        val first = connector.calls.single()
        assertEquals("record-1", first.lookupReference)
        assertNull(first.externalReference)
        assertEquals(1, first.contractVersion)
        assertEquals(setup.request.answers.requirementId, first.requirementId)
        val pending = stored(exchange.id)
        assertEquals("external-1", pending.externalReference)
        assertEquals(setup.clock.instant().plusSeconds(30), pending.nextAttemptAt?.toInstant())

        assertNull(setup.services.connectorWorker.process(exchange.id), "an exchange is not called again before it is due")
        setup.clock.instant = setup.clock.instant().plusSeconds(31)
        assertEquals(InformationRequestConnectorExchangeState.COMPLETED, setup.services.connectorWorker.process(exchange.id))
        assertEquals("external-1", connector.calls.last().externalReference)
        assertEquals(2, connector.calls.size)

        val values = QuarkusTransaction.requiringNew().call { setup.services.importedValues.values(setup.request.requestId, setup.owner) }
        assertEquals(setOf("recorded-value", "verified-status"), values.map { it.value.resultKey }.toSet())
        values.forEach { view ->
            val value = view.value
            assertEquals(InformationRequestImportedValueSource.CONNECTOR, value.sourceKind)
            assertEquals(exchange.id, value.connectorExchangeId)
            assertEquals("verification-service", value.sourceReference)
            assertEquals(InformationRequestSourceConfidence.VERIFIED, value.confidence)
            assertEquals(verifiedAt, value.verifiedAt?.toInstant())
            assertEquals(verifiedAt.plus(Duration.ofDays(90)), value.expiresAt?.toInstant())
            assertEquals("verification-2026-09", value.provenanceReference)
            assertEquals(setup.request.runtime.template.userId, value.recordedByPrincipalId)
            assertNull(view.decision)
        }
        assertEquals(answerBefore, answer(setup.request))
        QuarkusTransaction.requiringNew().run {
            val mutations = transitionRepository.findForRequest(setup.request.requestId).map { it.mutation }
            assertEquals(1, mutations.count { it == InformationRequestMutation.REQUEST_EXTERNAL_SOURCE })
            assertEquals(2, mutations.count { it == InformationRequestMutation.RECORD_EXTERNAL_VALUE })
        }
        dataSource.connection.use { connection ->
            val payloads = queryString(
                connection,
                "SELECT COALESCE(string_agg(payload_json, ' '), '') FROM audit_outbox WHERE target_id = ? AND event_type_key LIKE 'information_request.external.%'",
                setup.request.requestId.toString(),
            ).orEmpty()
            assertTrue(payloads.contains("connectorKey") && payloads.contains("importedValueId"), payloads)
            assertTrue(!payloads.contains("record-1") && !payloads.contains("Recorded answer"), payloads)
        }
    }

    @Test
    fun `a result that breaks its declared contract is rejected and records nothing`()
    {
        val dated = VERIFICATION.copy(maximumResultAge = Duration.ofDays(1))
        val connector = ScriptedConnector(dated)
        val setup = setup(connector)
        val now = setup.clock.instant()
        listOf(
            result(now, InformationRequestConnectorValue("undeclared-key", FieldValueType.SHORT_TEXT, JsonPrimitive("value"))),
            result(now.minus(Duration.ofDays(2)), InformationRequestConnectorValue("recorded-value", FieldValueType.SHORT_TEXT, JsonPrimitive("value"))),
            result(now, InformationRequestConnectorValue("recorded-value", FieldValueType.DECIMAL, JsonPrimitive("12.5"))),
            result(now.plus(Duration.ofDays(1)), InformationRequestConnectorValue("recorded-value", FieldValueType.SHORT_TEXT, JsonPrimitive("value"))),
            result(now),
        ).forEachIndexed { index, outcome ->
            connector.next { outcome }
            val exchange = QuarkusTransaction.requiringNew().call { setup.services.connectorExchanges.request(request(setup, "rejected-$index")) }
            assertEquals(InformationRequestConnectorExchangeState.FAILED, setup.services.connectorWorker.process(exchange.id), "result $index")
            assertEquals("result_rejected", stored(exchange.id).failureCode, "result $index")
        }
        assertEquals(0, importedValues(setup.request))
    }

    @Test
    fun `a failing call is retried with backoff until its attempts run out, and a refusal ends the exchange`()
    {
        val connector = ScriptedConnector(VERIFICATION)
        val setup = setup(connector)
        val exchange = QuarkusTransaction.requiringNew().call { setup.services.connectorExchanges.request(request(setup, "retried")) }
        repeat(InformationRequestConnectorWorker.MAXIMUM_ATTEMPTS - 1) { attempt ->
            connector.next { throw IllegalStateException("remote unavailable") }
            assertEquals(InformationRequestConnectorExchangeState.REQUESTED, setup.services.connectorWorker.process(exchange.id))
            val stored = stored(exchange.id)
            assertEquals(attempt + 1, stored.attemptCount)
            assertEquals(setup.clock.instant().plus(Duration.ofMinutes(attempt + 1L)), stored.nextAttemptAt?.toInstant())
            setup.clock.instant = stored.nextAttemptAt!!.toInstant()
        }
        connector.next { throw IllegalStateException("remote unavailable") }
        assertEquals(InformationRequestConnectorExchangeState.FAILED, setup.services.connectorWorker.process(exchange.id))
        assertEquals("attempts_exhausted", stored(exchange.id).failureCode)

        val refused = QuarkusTransaction.requiringNew().call { setup.services.connectorExchanges.request(request(setup, "refused")) }
        connector.next { InformationRequestConnectorOutcome.Failed("record.not_found") }
        assertEquals(InformationRequestConnectorExchangeState.FAILED, setup.services.connectorWorker.process(refused.id))
        assertEquals("record.not_found", stored(refused.id).failureCode)
    }

    @Test
    fun `an exchange whose connector is gone or changed, or whose request stopped taking values, ends failed`()
    {
        val connector = ScriptedConnector(VERIFICATION)
        val setup = setup(connector)
        val missing = QuarkusTransaction.requiringNew().call { setup.services.connectorExchanges.request(request(setup, "missing")) }
        val changed = QuarkusTransaction.requiringNew().call { setup.services.connectorExchanges.request(request(setup, "changed", requirement = setup.request.runtime.documentRequirementId)) }
        assertEquals(
            InformationRequestConnectorExchangeState.FAILED,
            runtime.build(setup.request.requestId, centralAuthorization = authorizationService, externalClock = setup.clock).connectorWorker.process(missing.id),
        )
        assertEquals("connector_unavailable", stored(missing.id).failureCode)
        val upgraded = runtime.build(
            setup.request.requestId,
            centralAuthorization = authorizationService,
            connectors = listOf(ScriptedConnector(VERIFICATION.copy(contractVersion = 2))),
            externalClock = setup.clock,
        )
        assertEquals(InformationRequestConnectorExchangeState.FAILED, upgraded.connectorWorker.process(changed.id))
        assertEquals("contract_version_changed", stored(changed.id).failureCode)

        val cancelled = QuarkusTransaction.requiringNew().call { setup.services.connectorExchanges.request(request(setup, "cancelled")) }
        dataSource.connection.use { connection ->
            execute(connection, "UPDATE information_request SET state = 'CANCELLED', cancelled_at = now() WHERE id = ?", setup.request.requestId)
        }
        connector.next { result(setup.clock.instant()) }
        assertEquals(InformationRequestConnectorExchangeState.FAILED, setup.services.connectorWorker.process(cancelled.id))
        assertEquals("request_not_accepting_values", stored(cancelled.id).failureCode)
        assertEquals(0, connector.calls.size)
    }

    @Test
    fun `requesting refuses unknown connectors, foreign Requirements, blank lookups, a second open exchange, and the responding side`()
    {
        val setup = setup(ScriptedConnector(VERIFICATION))
        val unknown = assertThrows(InformationRequestLifecycleException::class.java) {
            QuarkusTransaction.requiringNew().call { setup.services.connectorExchanges.request(request(setup, "unknown", connectorKey = "other-connector")) }
        }
        assertEquals(InformationRequestErrorCatalog.CONNECTOR_UNAVAILABLE, unknown.reasonCode)
        assertThrows(InformationRequestCommandRequestException::class.java) {
            QuarkusTransaction.requiringNew().call { setup.services.connectorExchanges.request(request(setup, "bad-key", connectorKey = "Other Connector")) }
        }
        assertThrows(InformationRequestCommandRequestException::class.java) {
            QuarkusTransaction.requiringNew().call { setup.services.connectorExchanges.request(request(setup, "blank-lookup", lookup = " ")) }
        }
        val foreign = assertThrows(InformationRequestLifecycleException::class.java) {
            QuarkusTransaction.requiringNew().call { setup.services.connectorExchanges.request(request(setup, "foreign", requirement = UUID.randomUUID())) }
        }
        assertEquals(InformationRequestErrorCatalog.NOT_FOUND, foreign.reasonCode)
        assertThrows(ForbiddenException::class.java) {
            QuarkusTransaction.requiringNew().call {
                setup.services.connectorExchanges.request(request(setup, "contributor").copy(access = support.contributor(setup.request)))
            }
        }

        val first = QuarkusTransaction.requiringNew().call { setup.services.connectorExchanges.request(request(setup, "first")) }
        assertEquals(first.id, QuarkusTransaction.requiringNew().call { setup.services.connectorExchanges.request(request(setup, "first")) }.id)
        val open = assertThrows(InformationRequestLifecycleException::class.java) {
            QuarkusTransaction.requiringNew().call { setup.services.connectorExchanges.request(request(setup, "second")) }
        }
        assertEquals(InformationRequestErrorCatalog.CONNECTOR_EXCHANGE_OPEN, open.reasonCode)
        assertThrows(ForbiddenException::class.java) {
            QuarkusTransaction.requiringNew().call { setup.services.connectorExchanges.exchanges(setup.request.requestId, support.contributor(setup.request)) }
        }
        assertEquals(listOf(first.id), QuarkusTransaction.requiringNew().call { setup.services.connectorExchanges.exchanges(setup.request.requestId, setup.owner) }.map { it.id })
    }

    private fun setup(connector: ScriptedConnector): ConnectorSetup
    {
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
        val services = runtime.build(request.requestId, centralAuthorization = authorizationService, connectors = listOf(connector), externalClock = clock)
        return ConnectorSetup(request, services, clock, support.owner(request))
    }

    private fun request(
        setup: ConnectorSetup,
        key: String,
        connectorKey: String = VERIFICATION.key,
        requirement: UUID = setup.request.answers.requirementId,
        lookup: String? = null,
    ) = RequestInformationRequestConnectorExchangeCommand(setup.request.requestId, requirement, connectorKey, lookup, setup.owner, key)

    private fun result(verifiedAt: Instant, vararg values: InformationRequestConnectorValue): InformationRequestConnectorOutcome =
        InformationRequestConnectorOutcome.Completed(
            "external",
            InformationRequestConnectorResult(
                source = "verification-service",
                confidence = InformationRequestSourceConfidence.VERIFIED,
                verifiedAt = verifiedAt,
                expiresAt = null,
                provenanceReference = "verification-2026-09",
                values = values.toList(),
            ),
        )

    private fun stored(exchangeId: UUID) = QuarkusTransaction.requiringNew().call { requireNotNull(exchangeRepository.findById(exchangeId)) }

    private fun answer(request: ConformanceRequest): String? =
        dataSource.connection.use { connection ->
            queryString(connection, "SELECT text_value FROM field_value WHERE resource_type = 'INFORMATION_REQUEST' AND resource_id = ?", request.requestId)
        }

    private fun importedValues(request: ConformanceRequest): Int =
        dataSource.connection.use { connection ->
            queryInt(connection, "SELECT COUNT(*) FROM information_request_imported_value WHERE information_request_id = ?", request.requestId)
        }

    private data class ConnectorSetup(
        val request: ConformanceRequest,
        val services: InformationRequestRuntimeServices,
        val clock: MutableClock,
        val owner: RequestAccessContext,
    )

    private class ScriptedConnector(override val contract: InformationRequestConnectorContract) : InformationRequestConnector
    {
        private val script = ArrayDeque<(InformationRequestConnectorCall) -> InformationRequestConnectorOutcome>()
        val calls = mutableListOf<InformationRequestConnectorCall>()

        fun next(step: (InformationRequestConnectorCall) -> InformationRequestConnectorOutcome)
        {
            script.addLast(step)
        }

        override fun request(call: InformationRequestConnectorCall): InformationRequestConnectorOutcome = answer(call)

        override fun poll(call: InformationRequestConnectorCall): InformationRequestConnectorOutcome = answer(call)

        private fun answer(call: InformationRequestConnectorCall): InformationRequestConnectorOutcome
        {
            calls.add(call)
            return script.removeFirst().invoke(call)
        }
    }

    private companion object
    {
        val VERIFICATION = InformationRequestConnectorContract(
            key = "record-verification",
            kind = InformationRequestConnectorKind.EXTERNAL_VERIFICATION,
            contractVersion = 1,
            resultKeys = setOf("recorded-value", "verified-status"),
        )
    }
}
