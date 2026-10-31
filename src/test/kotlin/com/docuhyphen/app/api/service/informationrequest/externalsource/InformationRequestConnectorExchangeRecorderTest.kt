package com.docuhyphen.app.api.service.informationrequest.externalsource

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestTransitionHistoryCommand
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.time.Instant
import java.util.*

class InformationRequestConnectorExchangeRecorderTest {
    private val history = mock<InformationRequestTransitionHistoryService>()
    private val recorder = InformationRequestConnectorExchangeRecorder(history)
    private val now: Instant = Instant.parse("2026-10-25T10:00:00Z")
    private val request = InformationRequest().apply {
        exchangeId = UUID.randomUUID()
        templateVersionId = UUID.randomUUID()
        state = InformationRequestState.IN_PROGRESS
    }

    @Test
    fun `starting an attempt counts it, leases the exchange and records the attempt`() {
        val exchange = exchange()

        recorder.startAttempt(request, exchange, now.plusSeconds(300))

        assertEquals(1, exchange.attemptCount)
        assertEquals(now.plusSeconds(300), exchange.nextAttemptAt?.toInstant())
        val command = recorded()
        assertEquals(InformationRequestMutation.START_EXTERNAL_SOURCE_ATTEMPT, command.mutation)
        assertEquals(AuditOutcome.SUCCESS, command.auditOutcome)
        assertEquals(
            "information_request.external|exchange|${exchange.id}|attempt|1|start_external_source_attempt",
            command.auditIdempotencyKey
        )
        assertEquals(
            mapOf(
                "connectorExchangeId" to exchange.id.toString(),
                "connectorKey" to "record-verification",
                "requirementId" to exchange.informationRequestRequirementId.toString(),
                "state" to "REQUESTED",
                "attemptCount" to "1",
                "nextAttemptAt" to "2026-10-25T10:05:00Z",
            ),
            command.details,
        )
    }

    @Test
    fun `a retry, a wait and a completion each record the exchange as it now stands`() {
        val exchange = exchange(attemptCount = 2)

        recorder.scheduleRetry(request, exchange, now.plusSeconds(120))
        val retry = recorded()
        assertEquals(InformationRequestMutation.SCHEDULE_EXTERNAL_SOURCE_RETRY, retry.mutation)
        assertEquals(AuditOutcome.FAILURE, retry.auditOutcome)
        assertEquals("2026-10-25T10:02:00Z", retry.details["nextAttemptAt"])

        recorder.pending(request, exchange, "external-1", now.plusSeconds(30))
        val pending = recorded()
        assertEquals(InformationRequestConnectorExchangeState.PENDING, exchange.state)
        assertEquals("external-1", exchange.externalReference)
        assertEquals(InformationRequestMutation.RECORD_EXTERNAL_SOURCE_PENDING, pending.mutation)
        assertEquals("PENDING", pending.details["state"])
        assertEquals("2026-10-25T10:00:30Z", pending.details["nextAttemptAt"])

        recorder.complete(request, exchange, "external-1", now)
        val complete = recorded()
        assertEquals(InformationRequestConnectorExchangeState.COMPLETED, exchange.state)
        assertEquals(now, exchange.completedAt?.toInstant())
        assertNull(exchange.nextAttemptAt)
        assertEquals(InformationRequestMutation.COMPLETE_EXTERNAL_SOURCE, complete.mutation)
        assertEquals(AuditOutcome.SUCCESS, complete.auditOutcome)
        assertEquals("COMPLETED", complete.details["state"])
        assertNull(complete.details["nextAttemptAt"])
        assertEquals(
            "information_request.external|exchange|${exchange.id}|attempt|2|complete_external_source",
            complete.auditIdempotencyKey
        )
        listOf(retry, pending, complete).forEach { command ->
            assertTrue(command.details.values.none { it == "external-1" }, command.details.toString())
        }
    }

    @Test
    fun `a failure ends the exchange with its bounded code and records it as a failed outcome`() {
        val exchange = exchange(attemptCount = 1)
        val longCode = "c".repeat(200)

        recorder.fail(request, exchange, longCode, now)

        assertEquals(InformationRequestConnectorExchangeState.FAILED, exchange.state)
        assertEquals("c".repeat(128), exchange.failureCode)
        assertEquals(now, exchange.completedAt?.toInstant())
        assertNull(exchange.nextAttemptAt)
        val command = recorded()
        assertEquals(InformationRequestMutation.FAIL_EXTERNAL_SOURCE, command.mutation)
        assertEquals(AuditOutcome.FAILURE, command.auditOutcome)
        assertEquals("c".repeat(128), command.details["failureCode"])
        assertEquals("FAILED", command.details["state"])
        assertNull(command.reasonCode)
    }

    @Test
    fun `every record is a system change of the request in its current state that must be audited`() {
        val exchange = exchange()

        recorder.startAttempt(request, exchange, now)

        val command = recorded()
        assertEquals(request, command.request)
        assertEquals(InformationRequestState.IN_PROGRESS, command.fromState)
        assertEquals(InformationRequestState.IN_PROGRESS, command.toState)
        assertTrue(command.requireAuditCapture)
        assertEquals(PrincipalKind.SERVICE_ACCOUNT, command.actor.principal.kind)
        assertEquals(UUID(0, 0), command.actor.principal.id)
        assertNull(command.actor.sessionRef)
    }

    private fun recorded(): InformationRequestTransitionHistoryCommand {
        val captor = argumentCaptor<InformationRequestTransitionHistoryCommand>()
        verify(history, atLeastOnce()).record(captor.capture())
        return captor.lastValue
    }

    private fun exchange(attemptCount: Int = 0) = InformationRequestConnectorExchange().apply {
        informationRequestId = request.id
        informationRequestRequirementId = UUID.randomUUID()
        connectorKey = "record-verification"
        connectorKind = InformationRequestConnectorKind.EXTERNAL_VERIFICATION
        lookupReference = "record-1"
        this.attemptCount = attemptCount
        requestedByPrincipalKind = PrincipalKind.USER
        requestedByPrincipalId = UUID.randomUUID()
    }
}
