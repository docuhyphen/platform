package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.entity.InformationRequestConnectorExchange
import com.docuhyphen.app.api.model.entity.InformationRequestConnectorExchangeState
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.informationrequest.RequestInformationRequestConnectorExchangeCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestConnectorExchangeRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandActorRef
import com.docuhyphen.app.api.service.command.CommandMutationResult
import com.docuhyphen.app.api.service.command.CommandReceiptDecision
import com.docuhyphen.app.api.service.command.CommandReceiptRequest
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.command.CommandResultReference
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Clock
import java.util.UUID

@ApplicationScoped
class InformationRequestConnectorService @Inject constructor(
    private val gate: InformationRequestMutationGate,
    private val queryService: InformationRequestQueryService,
    private val registry: InformationRequestConnectorRegistry,
    private val exchangeRepository: InformationRequestConnectorExchangeRepository,
    private val requirementRepository: InformationRequestRequirementRepository,
    private val commandReceiptService: CommandReceiptService,
    private val transitionHistory: InformationRequestTransitionHistoryService,
    private val clock: Clock,
)
{
    @Transactional
    fun request(command: RequestInformationRequestConnectorExchangeCommand): InformationRequestConnectorExchange
    {
        val connectorKey = command.connectorKey.trim()
        val lookupReference = command.lookupReference?.trim()
        val receipt = CommandReceiptRequest(
            resource = ResourceRef.informationRequest(command.requestId),
            operation = OPERATION,
            actor = CommandActorRef.principal(command.access.principal),
            idempotencyKey = command.idempotencyKey,
            requestFingerprint = CommandRequestFingerprint.sha256Hex(
                "$OPERATION|${command.requestId}|${command.requirementId}|$connectorKey|${lookupReference.orEmpty()}",
            ),
        )
        return when (
            val decision = commandReceiptService.runOnce(receipt) {
                val exchange = requestOnce(command, connectorKey, lookupReference)
                CommandMutationResult(exchange, CommandResultReference(ResourceType.INFORMATION_REQUEST_EXTERNAL_SOURCE, exchange.id, 1, null))
            }
        )
        {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed ->
            {
                gate.authorizeRequest(command.access, listOf(Action.INFORMATION_REQUEST_MANAGE_EXTERNAL_SOURCES), command.requestId)
                requireNotNull(exchangeRepository.findById(decision.result.resourceId))
            }
        }
    }

    fun exchanges(requestId: UUID, access: RequestAccessContext): List<InformationRequestConnectorExchange>
    {
        queryService.findById(requestId, access)
        requireRequestingSide(gate, access, requestId)
        return exchangeRepository.findForRequest(requestId)
    }

    private fun requestOnce(
        command: RequestInformationRequestConnectorExchangeCommand,
        connectorKey: String,
        lookupReference: String?,
    ): InformationRequestConnectorExchange
    {
        val locked = gate.lock(command.requestId)
        gate.requireMutation(locked, InformationRequestMutation.REQUEST_EXTERNAL_SOURCE)
        gate.authorizeRequest(command.access, listOf(Action.INFORMATION_REQUEST_MANAGE_EXTERNAL_SOURCES), command.requestId)
        gate.requireContinuationEntitlement(locked)
        if (!MACHINE_KEY.matches(connectorKey)) throw InformationRequestCommandRequestException("A connector is named by its lowercase key")
        if (lookupReference != null && (lookupReference.isEmpty() || lookupReference.length > REFERENCE_LENGTH))
        {
            throw InformationRequestCommandRequestException("A lookup reference is text of at most $REFERENCE_LENGTH characters")
        }
        if (requirementRepository.findForRequest(command.requestId).none { it.id == command.requirementId })
        {
            throw InformationRequestLifecycleException(InformationRequestErrorCatalog.NOT_FOUND, "Information Request Requirement not found")
        }
        val connector = registry.find(connectorKey)
            ?: throw InformationRequestLifecycleException(InformationRequestErrorCatalog.CONNECTOR_UNAVAILABLE, "No connector with this key is installed")
        val open = exchangeRepository.findForRequest(command.requestId).any { exchange ->
            exchange.connectorKey == connectorKey && exchange.informationRequestRequirementId == command.requirementId &&
                exchange.state in OPEN_STATES
        }
        if (open)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.CONNECTOR_EXCHANGE_OPEN,
                "This connector is already working on this Requirement",
            )
        }
        val now = Timestamp.from(clock.instant())
        val exchange = exchangeRepository.save(
            InformationRequestConnectorExchange().apply {
                informationRequestId = command.requestId
                informationRequestRequirementId = command.requirementId
                this.connectorKey = connector.contract.key
                connectorKind = connector.contract.kind
                contractVersion = connector.contract.contractVersion
                this.lookupReference = lookupReference
                state = InformationRequestConnectorExchangeState.REQUESTED
                nextAttemptAt = now
                requestedByPrincipalKind = command.access.principal.kind
                requestedByPrincipalId = command.access.principal.id
                requestedAt = now
            },
        )
        transitionHistory.record(
            InformationRequestTransitionHistoryCommand(
                request = locked.request,
                fromState = locked.request.state,
                toState = locked.request.state,
                mutation = InformationRequestMutation.REQUEST_EXTERNAL_SOURCE,
                actor = command.access.principal,
                idempotencyKey = "information_request.external|exchange|${exchange.id}",
                details = mapOf(
                    "connectorExchangeId" to exchange.id.toString(),
                    "connectorKey" to exchange.connectorKey,
                    "requirementId" to command.requirementId.toString(),
                ),
            ),
        )
        return exchange
    }

    companion object
    {
        private const val OPERATION = "request-information-request-connector-exchange"
        private const val REFERENCE_LENGTH = 256
        private val MACHINE_KEY = Regex("^[a-z0-9][a-z0-9._-]{0,127}$")
        private val OPEN_STATES = setOf(InformationRequestConnectorExchangeState.REQUESTED, InformationRequestConnectorExchangeState.PENDING)

        fun requireRequestingSide(gate: InformationRequestMutationGate, access: RequestAccessContext, requestId: UUID)
        {
            val permitted = gate.permitsRequest(access, Action.INFORMATION_REQUEST_MANAGE_EXTERNAL_SOURCES, requestId) ||
                gate.permitsRequest(access, Action.INFORMATION_REQUEST_DECIDE_EXTERNAL_VALUES, requestId)
            if (!permitted) throw ForbiddenException("Access denied to external source records")
        }
    }
}
