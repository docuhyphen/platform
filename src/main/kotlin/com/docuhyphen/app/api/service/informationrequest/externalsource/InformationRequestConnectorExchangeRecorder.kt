package com.docuhyphen.app.api.service.informationrequest.externalsource

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestConnectorExchange
import com.docuhyphen.app.api.model.entity.InformationRequestConnectorExchangeState
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestTransitionHistoryCommand
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@ApplicationScoped
class InformationRequestConnectorExchangeRecorder @Inject constructor(
    private val transitionHistory: InformationRequestTransitionHistoryService,
) {
    fun startAttempt(request: InformationRequest, exchange: InformationRequestConnectorExchange, leaseEndsAt: Instant) {
        exchange.attemptCount += 1
        exchange.nextAttemptAt = Timestamp.from(leaseEndsAt)
        record(request, exchange, InformationRequestMutation.START_EXTERNAL_SOURCE_ATTEMPT, AuditOutcome.SUCCESS)
    }

    fun scheduleRetry(request: InformationRequest, exchange: InformationRequestConnectorExchange, retryAt: Instant) {
        exchange.nextAttemptAt = Timestamp.from(retryAt)
        record(request, exchange, InformationRequestMutation.SCHEDULE_EXTERNAL_SOURCE_RETRY, AuditOutcome.FAILURE)
    }

    fun pending(
        request: InformationRequest,
        exchange: InformationRequestConnectorExchange,
        externalReference: String,
        pollAt: Instant,
    ) {
        exchange.state = InformationRequestConnectorExchangeState.PENDING
        exchange.externalReference = externalReference
        exchange.nextAttemptAt = Timestamp.from(pollAt)
        record(request, exchange, InformationRequestMutation.RECORD_EXTERNAL_SOURCE_PENDING, AuditOutcome.SUCCESS)
    }

    fun complete(
        request: InformationRequest,
        exchange: InformationRequestConnectorExchange,
        externalReference: String,
        completedAt: Instant,
    ) {
        exchange.state = InformationRequestConnectorExchangeState.COMPLETED
        exchange.externalReference = externalReference
        exchange.completedAt = Timestamp.from(completedAt)
        exchange.nextAttemptAt = null
        record(request, exchange, InformationRequestMutation.COMPLETE_EXTERNAL_SOURCE, AuditOutcome.SUCCESS)
    }

    fun fail(
        request: InformationRequest,
        exchange: InformationRequestConnectorExchange,
        failureCode: String,
        failedAt: Instant,
    ) {
        exchange.state = InformationRequestConnectorExchangeState.FAILED
        exchange.failureCode = failureCode.take(FAILURE_CODE_LENGTH)
        exchange.completedAt = Timestamp.from(failedAt)
        exchange.nextAttemptAt = null
        record(request, exchange, InformationRequestMutation.FAIL_EXTERNAL_SOURCE, AuditOutcome.FAILURE)
    }

    private fun record(
        request: InformationRequest,
        exchange: InformationRequestConnectorExchange,
        mutation: InformationRequestMutation,
        outcome: AuditOutcome,
    ) {
        transitionHistory.record(
            InformationRequestTransitionHistoryCommand(
                request = request,
                fromState = request.state,
                toState = request.state,
                mutation = mutation,
                actor = WORKER,
                auditIdempotencyKey = "information_request.external|exchange|${exchange.id}|attempt|" +
                        "${exchange.attemptCount}|${mutation.name.lowercase()}",
                details = detailsOf(exchange),
                requireAuditCapture = true,
                auditOutcome = outcome,
            ),
        )
    }

    private fun detailsOf(exchange: InformationRequestConnectorExchange): Map<String, String> = buildMap {
        put("connectorExchangeId", exchange.id.toString())
        put("connectorKey", exchange.connectorKey)
        put("requirementId", exchange.informationRequestRequirementId.toString())
        put("state", exchange.state.name)
        put("attemptCount", exchange.attemptCount.toString())
        exchange.nextAttemptAt?.let { put("nextAttemptAt", it.toInstant().toString()) }
        exchange.failureCode?.let { put("failureCode", it) }
    }

    private companion object {
        const val FAILURE_CODE_LENGTH = 128
        val WORKER = InformationRequestAuditActor.system(PrincipalRef(PrincipalKind.SERVICE_ACCOUNT, UUID(0, 0)))
    }
}
