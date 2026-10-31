package com.docuhyphen.app.api.service.informationrequest.externalsource

import com.docuhyphen.app.api.model.entity.InformationRequestConnectorExchange
import com.docuhyphen.app.api.model.entity.InformationRequestConnectorExchangeState
import com.docuhyphen.app.api.model.informationrequest.LockedInformationRequest
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestContinuationAction
import com.docuhyphen.app.api.model.informationrequest.externalsource.InformationRequestConnectorCall
import com.docuhyphen.app.api.model.informationrequest.externalsource.InformationRequestConnectorClaim
import com.docuhyphen.app.api.model.informationrequest.externalsource.InformationRequestConnectorContract
import com.docuhyphen.app.api.model.informationrequest.externalsource.InformationRequestConnectorOutcome
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.repository.informationrequest.externalsource.InformationRequestConnectorExchangeRepository
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import io.quarkus.narayana.jta.QuarkusTransaction
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.*

@ApplicationScoped
class InformationRequestConnectorWorker @Inject constructor(
    private val registry: InformationRequestConnectorRegistry,
    private val exchangeRepository: InformationRequestConnectorExchangeRepository,
    private val gate: InformationRequestMutationGate,
    private val importedValueIntake: InformationRequestImportedValueIntakeService,
    private val exchangeRecorder: InformationRequestConnectorExchangeRecorder,
    private val entityManager: EntityManager,
    private val clock: Clock,
)
{
    fun processDue(limit: Int = BATCH_SIZE): Int
    {
        val due = QuarkusTransaction.requiringNew()
            .call { exchangeRepository.findDueIds(Timestamp.from(clock.instant()), limit) }
        return due.count { exchangeId ->
            runCatching { process(exchangeId) }
                .onFailure {
                    logger.warn(
                        "Information Request connector exchange {} could not be processed; it will be retried",
                        exchangeId,
                        it
                    )
                }
                .getOrNull() != null
        }
    }

    fun process(exchangeId: UUID): InformationRequestConnectorExchangeState?
    {
        return when (val claim = QuarkusTransaction.requiringNew().call { claim(exchangeId) })
        {
            null -> null
            is InformationRequestConnectorClaim.Ended -> claim.state
            is InformationRequestConnectorClaim.Due ->
            {
                val outcome = runCatching {
                    if (claim.call.externalReference == null) claim.connector.request(claim.call)
                    else claim.connector.poll(
                        claim.call
                    )
                }
                    .onFailure {
                        logger.warn(
                            "Information Request connector {} call failed for exchange {}",
                            claim.call.connectorKey,
                            exchangeId,
                            it
                        )
                    }
                    .getOrNull()
                QuarkusTransaction.requiringNew()
                    .call { record(exchangeId, claim.call.attempt, claim.connector.contract, outcome) }
            }
        }
    }

    private fun claim(exchangeId: UUID): InformationRequestConnectorClaim?
    {
        val requestId = exchangeRepository.findById(exchangeId)?.informationRequestId ?: return null
        val locked = gate.lock(requestId)
        val exchange = exchangeRepository.findByIdForUpdate(exchangeId) ?: return null
        val now = clock.instant()
        if (!exchange.isOpen() || exchange.nextAttemptAt?.toInstant()?.isAfter(now) == true) return null
        if (exchange.attemptCount >= MAXIMUM_ATTEMPTS) {
            exchangeRecorder.fail(locked.request, exchange, ATTEMPTS_EXHAUSTED, now)
            return InformationRequestConnectorClaim.Ended(exchange.state)
        }
        val connector = registry.find(exchange.connectorKey)
        val ending = when
        {
            !acceptsValues(locked) || !importedValueIntake.acceptsRequirement(exchange) -> REQUEST_NOT_ACCEPTING
            connector == null -> CONNECTOR_UNAVAILABLE
            connector.contract.contractVersion != exchange.contractVersion -> CONTRACT_VERSION_CHANGED
            else -> null
        }
        if (ending != null)
        {
            exchangeRecorder.fail(locked.request, exchange, ending, now)
            return InformationRequestConnectorClaim.Ended(exchange.state)
        }
        exchangeRecorder.startAttempt(locked.request, exchange, now.plus(CLAIM_LEASE))
        return InformationRequestConnectorClaim.Due(
            InformationRequestConnectorCall(
                exchangeId = exchange.id,
                requestId = exchange.informationRequestId,
                requirementId = exchange.informationRequestRequirementId,
                connectorKey = exchange.connectorKey,
                contractVersion = exchange.contractVersion,
                lookupReference = exchange.lookupReference,
                externalReference = exchange.externalReference,
                attempt = exchange.attemptCount,
            ),
            requireNotNull(connector),
        )
    }

    private fun record(
        exchangeId: UUID,
        attempt: Int,
        contract: InformationRequestConnectorContract,
        outcome: InformationRequestConnectorOutcome?,
    ): InformationRequestConnectorExchangeState?
    {
        val requestId = exchangeRepository.findById(exchangeId)?.informationRequestId ?: return null
        val locked = gate.lock(requestId)
        val exchange = exchangeRepository.findByIdForUpdate(exchangeId) ?: return null
        if (!exchange.isOpen() || exchange.attemptCount != attempt) return exchange.state
        val now = clock.instant()
        when
        {
            !acceptsValues(locked) || !importedValueIntake.acceptsRequirement(exchange) ->
                exchangeRecorder.fail(locked.request, exchange, REQUEST_NOT_ACCEPTING, now)
            outcome == null ->
                if (attempt >= MAXIMUM_ATTEMPTS) exchangeRecorder.fail(
                    locked.request,
                    exchange,
                    ATTEMPTS_EXHAUSTED,
                    now
                )
                else exchangeRecorder.scheduleRetry(
                    locked.request,
                    exchange,
                    now.plus(RETRY_DELAY.multipliedBy(attempt.toLong()))
                )

            outcome is InformationRequestConnectorOutcome.Failed ->
                exchangeRecorder.fail(
                    locked.request,
                    exchange,
                    outcome.reasonCode.trim().ifEmpty { CONNECTOR_FAILED },
                    now
                )

            outcome is InformationRequestConnectorOutcome.Pending -> pending(locked, exchange, attempt, outcome, now)
            outcome is InformationRequestConnectorOutcome.Completed -> complete(
                locked,
                exchange,
                contract,
                outcome,
                now
            )
        }
        return exchange.state
    }

    private fun pending(
        locked: LockedInformationRequest,
        exchange: InformationRequestConnectorExchange,
        attempt: Int,
        outcome: InformationRequestConnectorOutcome.Pending,
        now: Instant
    )
    {
        val reference = outcome.externalReference.trim()
        when
        {
            reference.isEmpty() || reference.length > REFERENCE_LENGTH ->
                exchangeRecorder.fail(locked.request, exchange, RESULT_REJECTED, now)

            attempt >= MAXIMUM_ATTEMPTS -> exchangeRecorder.fail(locked.request, exchange, ATTEMPTS_EXHAUSTED, now)
            else -> exchangeRecorder.pending(
                locked.request,
                exchange,
                reference,
                now.plus(maxOf(outcome.retryAfter, MINIMUM_RETRY))
            )
        }
    }

    private fun complete(
        locked: LockedInformationRequest,
        exchange: InformationRequestConnectorExchange,
        contract: InformationRequestConnectorContract,
        outcome: InformationRequestConnectorOutcome.Completed,
        now: Instant,
    )
    {
        val reference = outcome.externalReference.trim()
        val prepared =
            runCatching {
                importedValueIntake.prepareConnectorResult(
                    locked.request,
                    exchange,
                    contract,
                    outcome.result
                )
            }
                .onFailure {
                    logger.warn(
                        "Information Request connector {} result for exchange {} was rejected",
                        exchange.connectorKey,
                        exchange.id,
                        it
                    )
                }
                .getOrNull()
        if (prepared == null || reference.isEmpty() || reference.length > REFERENCE_LENGTH)
        {
            exchangeRecorder.fail(locked.request, exchange, RESULT_REJECTED, now)
            return
        }
        exchangeRecorder.complete(locked.request, exchange, reference, now)
        entityManager.flush()
        importedValueIntake.recordConnectorResult(locked.request, exchange, prepared)
    }

    private fun acceptsValues(locked: LockedInformationRequest): Boolean =
        runCatching {
            gate.requireMutation(locked, InformationRequestMutation.RECORD_EXTERNAL_VALUE)
            gate.requireContinuation(locked, InformationRequestContinuationAction.RESPOND)
        }.isSuccess

    private fun InformationRequestConnectorExchange.isOpen(): Boolean =
        state == InformationRequestConnectorExchangeState.REQUESTED || state == InformationRequestConnectorExchangeState.PENDING

    companion object
    {
        const val MAXIMUM_ATTEMPTS = 10
        private const val BATCH_SIZE = 50
        private const val REFERENCE_LENGTH = 256
        private const val CONNECTOR_UNAVAILABLE = "connector_unavailable"
        private const val CONTRACT_VERSION_CHANGED = "contract_version_changed"
        private const val CONNECTOR_FAILED = "connector_failed"
        private const val ATTEMPTS_EXHAUSTED = "attempts_exhausted"
        private const val REQUEST_NOT_ACCEPTING = "request_not_accepting_values"
        private const val RESULT_REJECTED = "result_rejected"
        private val CLAIM_LEASE: Duration = Duration.ofMinutes(5)
        private val RETRY_DELAY: Duration = Duration.ofMinutes(1)
        private val MINIMUM_RETRY: Duration = Duration.ofSeconds(1)
        private val logger = LoggerFactory.getLogger(InformationRequestConnectorWorker::class.java)
    }
}
