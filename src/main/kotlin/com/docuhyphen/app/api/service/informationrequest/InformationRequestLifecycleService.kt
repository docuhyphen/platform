package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestParty
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.entity.RequestExecutionGrant
import com.docuhyphen.app.api.model.entity.RequestExecutionUsageKind
import com.docuhyphen.app.api.model.entity.RequestExecutionUsageReservation
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestPartyRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandActorRef
import com.docuhyphen.app.api.service.command.CommandMutationResult
import com.docuhyphen.app.api.service.command.CommandPrecondition
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
import java.time.Instant
import java.util.UUID

data class IssueInformationRequestCommand(
    val requestId: UUID,
    val access: RequestAccessContext,
    val precondition: CommandPrecondition,
    val idempotencyKey: String,
)

data class CancelInformationRequestCommand(
    val requestId: UUID,
    val reasonCode: String? = null,
    val access: RequestAccessContext,
    val precondition: CommandPrecondition,
    val idempotencyKey: String,
)

data class SupersedeInformationRequestCommand(
    val requestId: UUID,
    val supersededByRequestId: UUID,
    val reasonCode: String? = null,
    val access: RequestAccessContext,
    val precondition: CommandPrecondition,
    val idempotencyKey: String,
)

data class InformationRequestLifecycleResult(
    val request: InformationRequest,
    val requestETag: String,
)

class InformationRequestLifecycleException(
    val reasonCode: String,
    override val message: String,
) : RuntimeException(message)

@ApplicationScoped
class InformationRequestLifecycleService @Inject constructor(
    private val requestRepository: InformationRequestRepository,
    private val exchangeRepository: ExchangeRepository,
    private val authorizationService: AuthorizationService,
    private val commandReceiptService: CommandReceiptService,
    private val capabilityGate: InformationRequestTemplateCapabilityGate,
    private val transitionHistory: InformationRequestTransitionHistoryService,
    private val entitlementGuard: InformationRequestEntitlementGuard,
    private val executionGrantService: InformationRequestExecutionGrantService,
    private val partyRepository: InformationRequestPartyRepository,
    private val executionUsageReservationService: InformationRequestExecutionUsageReservationService,
)
{
    @Transactional
    fun issue(command: IssueInformationRequestCommand): InformationRequestLifecycleResult =
        runOnce(
            requestId = command.requestId,
            operation = ISSUE_OPERATION,
            actor = command.access,
            idempotencyKey = command.idempotencyKey,
            fingerprint = fingerprint(ISSUE_OPERATION, command.requestId),
        ) {
            mutate(
                requestId = command.requestId,
                access = command.access,
                precondition = command.precondition,
                action = Action.INFORMATION_REQUEST_ISSUE,
                mutation = InformationRequestMutation.ISSUE,
                reasonCode = null,
                idempotencyKey = command.idempotencyKey,
            ) { request, exchange, now ->
                capabilityGate.requireInstalledCapabilities(request.templateVersionId)
                request.issuedAt = now
                val grant = executionGrantService.issueGrant(request, exchange)
                consumeActiveActingPartyCapacity(grant)
            }
        }

    @Transactional
    fun cancel(command: CancelInformationRequestCommand): InformationRequestLifecycleResult =
        runOnce(
            requestId = command.requestId,
            operation = CANCEL_OPERATION,
            actor = command.access,
            idempotencyKey = command.idempotencyKey,
            fingerprint = fingerprint(CANCEL_OPERATION, command.requestId, command.reasonCode.orEmpty()),
        ) {
            mutate(
                requestId = command.requestId,
                access = command.access,
                precondition = command.precondition,
                action = Action.INFORMATION_REQUEST_CANCEL,
                mutation = InformationRequestMutation.CANCEL,
                reasonCode = command.reasonCode,
                idempotencyKey = command.idempotencyKey,
            ) { request, _, now ->
                request.cancelledAt = now
            }
        }

    @Transactional
    fun supersede(command: SupersedeInformationRequestCommand): InformationRequestLifecycleResult =
        runOnce(
            requestId = command.requestId,
            operation = SUPERSEDE_OPERATION,
            actor = command.access,
            idempotencyKey = command.idempotencyKey,
            fingerprint = fingerprint(
                SUPERSEDE_OPERATION,
                command.requestId,
                command.supersededByRequestId,
                command.reasonCode.orEmpty(),
            ),
        ) {
            mutate(
                requestId = command.requestId,
                access = command.access,
                precondition = command.precondition,
                action = Action.INFORMATION_REQUEST_SUPERSEDE,
                mutation = InformationRequestMutation.SUPERSEDE,
                reasonCode = command.reasonCode,
                idempotencyKey = command.idempotencyKey,
            ) { request, _, now ->
                val replacement = replacementRequest(command, request)
                request.supersededAt = now
                request.supersededByRequestId = replacement.id
            }
        }

    private fun runOnce(
        requestId: UUID,
        operation: String,
        actor: RequestAccessContext,
        idempotencyKey: String,
        fingerprint: String,
        mutation: () -> InformationRequestLifecycleResult,
    ): InformationRequestLifecycleResult
    {
        val receiptRequest = CommandReceiptRequest(
            resource = ResourceRef.informationRequest(requestId),
            operation = operation,
            actor = CommandActorRef.principal(actor.principal),
            idempotencyKey = idempotencyKey,
            requestFingerprint = fingerprint,
        )
        return when (
            val decision = commandReceiptService.runOnce(receiptRequest) {
                val result = mutation()
                CommandMutationResult(result, result.commandResultReference())
            }
        )
        {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed -> replayLifecycleResult(decision.result, actor)
        }
    }

    private fun mutate(
        requestId: UUID,
        access: RequestAccessContext,
        precondition: CommandPrecondition,
        action: Action,
        mutation: InformationRequestMutation,
        reasonCode: String?,
        idempotencyKey: String,
        beforeUpdate: (InformationRequest, Exchange, Timestamp) -> Unit,
    ): InformationRequestLifecycleResult
    {
        val exchange = lockParentExchangeOf(requestId, requestRepository, exchangeRepository)
        val request = requestRepository.findRequestByIdForUpdate(requestId)
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.NOT_FOUND,
                "Information Request not found",
            )
        precondition.requireSatisfiedBy(InformationRequestETag.aggregateOf(request))
        authorize(access, action, request.id)

        // A request that already holds a frozen execution grant answers to it rather than the
        // owner's live plan: only a request that has never been issued still has no grant to
        // consult, so this is exactly the pre-issuance mutation path (issuance itself included).
        val grant = executionGrantService.findForRequest(request.id)
        if (grant == null)
        {
            entitlementGuard.requireRequestMutation(exchange)
        }
        else
        {
            // Neither decision is a live commercial recheck: an emergency operational suspension
            // is checked unconditionally because it is the one live decision meant to reach a
            // request whose grant is already frozen, and an explicit revocation targets this one
            // request rather than the owner's plan at all.
            entitlementGuard.requireNotOperationallySuspended(exchange)
            requireGrantNotRevoked(grant)
        }
        val nextState = nextState(exchange, request.state, mutation)
        val previousState = request.state
        val now = Timestamp.from(Instant.now())
        beforeUpdate(request, exchange, now)
        request.state = nextState
        request.aggregateRevision += 1
        request.updatedAt = now
        requestRepository.update(request)
        transitionHistory.record(
            InformationRequestTransitionHistoryCommand(
                request = request,
                fromState = previousState,
                toState = nextState,
                mutation = mutation,
                actor = access.principal,
                reasonCode = reasonCode,
                idempotencyKey = historyIdempotencyKey(mutation, request.id, idempotencyKey),
            ),
        )
        return InformationRequestLifecycleResult(
            request = request,
            requestETag = InformationRequestETag.aggregateOf(request),
        )
    }

    private fun nextState(
        exchange: Exchange,
        currentState: InformationRequestState,
        mutation: InformationRequestMutation,
    ): InformationRequestState
    {
        val decision = InformationRequestTransitionMatrix.canMutate(
            InformationRequestParentSnapshot(
                status = exchange.status,
                deleted = exchange.isDeleted,
                lockedForUpdate = true,
            ),
            currentState,
            mutation,
        )
        return when (decision)
        {
            is InformationRequestPolicyDecision.Allow -> requireNotNull(decision.nextState) {
                "Lifecycle mutation $mutation did not name a next state"
            }
            is InformationRequestPolicyDecision.Deny -> throw InformationRequestLifecycleException(
                decision.reasonCode,
                "Information Request lifecycle mutation $mutation is not allowed",
            )
        }
    }

    private fun requireGrantNotRevoked(grant: RequestExecutionGrant)
    {
        if (grant.revokedAt != null)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.EXECUTION_GRANT_REVOKED,
                "This request's execution grant has been revoked",
            )
        }
    }

    private fun consumeActiveActingPartyCapacity(grant: RequestExecutionGrant)
    {
        val reservations = mutableListOf<RequestExecutionUsageReservation>()
        val consumed = mutableListOf<RequestExecutionUsageReservation>()
        try
        {
            partyRepository.findActiveForRequest(grant.requestId)
                .filter { it.isActingParty() }
                .forEach { party ->
                    val reservation = reservePartyCapacity(grant, party)
                    reservations += reservation
                    executionUsageReservationService.consume(reservation.id)
                    consumed += reservation
                }
        }
        catch (exception: RuntimeException)
        {
            compensateCapacityReservations(reservations, consumed)
            throw exception
        }
    }

    private fun reservePartyCapacity(
        grant: RequestExecutionGrant,
        party: InformationRequestParty,
    ): RequestExecutionUsageReservation =
        executionUsageReservationService.reserve(
            grant.id,
            RequestExecutionUsageKind.ADDITIONAL_RECIPIENT,
            partyCapacityReservationKey(party.id),
            1L,
        )

    private fun compensateCapacityReservations(
        reservations: List<RequestExecutionUsageReservation>,
        consumed: List<RequestExecutionUsageReservation>,
    )
    {
        val consumedIds = consumed.map { it.id }.toSet()
        consumed.asReversed().forEach { runCatching { executionUsageReservationService.rollback(it.id) } }
        reservations.asReversed()
            .filterNot { it.id in consumedIds }
            .forEach { runCatching { executionUsageReservationService.release(it.id) } }
    }

    private fun InformationRequestParty.isActingParty(): Boolean =
        roleKey != InformationRequestShareRoleKey.SUBJECT

    private fun replacementRequest(
        command: SupersedeInformationRequestCommand,
        request: InformationRequest,
    ): InformationRequest
    {
        if (command.supersededByRequestId == request.id)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.STATE_INVALID,
                "An Information Request cannot supersede itself",
            )
        }
        val replacement = requestRepository.findById(command.supersededByRequestId)
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.NOT_FOUND,
                "Replacement Information Request not found",
            )
        if (replacement.exchangeId != request.exchangeId || replacement.state.isTerminal)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.STATE_INVALID,
                "Replacement Information Request is not eligible for supersession",
            )
        }
        return replacement
    }

    private fun authorize(access: RequestAccessContext, action: Action, requestId: UUID)
    {
        val decision = authorizationService.authorize(
            access.principal,
            action,
            ResourceRef.informationRequest(requestId),
            access.authorization,
        )
        if (decision is Decision.Deny)
        {
            throw ForbiddenException("Access denied to mutate Information Request lifecycle")
        }
    }

    private fun replayLifecycleResult(result: CommandResultReference, access: RequestAccessContext): InformationRequestLifecycleResult
    {
        require(result.resourceType == ResourceType.INFORMATION_REQUEST) {
            "Command receipt does not reference an Information Request"
        }
        val request = requestRepository.findById(result.resourceId)
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.NOT_FOUND,
                "Information Request receipt target not found",
            )
        InformationRequestReadAuthorization.requireView(authorizationService, request.id, access)
        return InformationRequestLifecycleResult(
            request = request,
            requestETag = requireNotNull(result.etag) {
                "Information Request lifecycle receipt did not record an aggregate ETag"
            },
        )
    }

    private fun InformationRequestLifecycleResult.commandResultReference(): CommandResultReference =
        CommandResultReference(
            resourceType = ResourceType.INFORMATION_REQUEST,
            resourceId = request.id,
            revision = request.aggregateRevision,
            etag = requestETag,
        )

    private fun fingerprint(operation: String, vararg fields: Any): String =
        CommandRequestFingerprint.sha256Hex(
            (listOf(operation) + fields.map { it.toString().trim() }).joinToString("|"),
        )

    private fun historyIdempotencyKey(
        mutation: InformationRequestMutation,
        requestId: UUID,
        idempotencyKey: String,
    ): String = "information_request.lifecycle|$mutation|$requestId|$idempotencyKey"

    private companion object
    {
        const val ISSUE_OPERATION = "issue-information-request"
        const val CANCEL_OPERATION = "cancel-information-request"
        const val SUPERSEDE_OPERATION = "supersede-information-request"

        fun partyCapacityReservationKey(partyId: UUID): String = "information_request.party|$partyId"
    }
}
