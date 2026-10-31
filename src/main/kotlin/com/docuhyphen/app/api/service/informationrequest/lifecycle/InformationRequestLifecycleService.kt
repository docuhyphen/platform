package com.docuhyphen.app.api.service.informationrequest.lifecycle

import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.command.*
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.informationrequest.LockedInformationRequest
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestContinuationAction
import com.docuhyphen.app.api.model.informationrequest.lifecycle.*
import com.docuhyphen.app.api.model.informationrequest.noauth.InformationRequestCredentialCutoff
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.exchange.ExchangeLookupService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestContinuationGuard
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestReadAuthorization
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestTemplateCapabilityGate
import com.docuhyphen.app.api.service.informationrequest.clock.InformationRequestClockIssueRebaser
import com.docuhyphen.app.api.service.informationrequest.clock.InformationRequestClockStopper
import com.docuhyphen.app.api.service.informationrequest.execution.InformationRequestExecutionGrantService
import com.docuhyphen.app.api.service.informationrequest.execution.InformationRequestExecutionUsageReservationService
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestBootstrapShareLinkService
import com.docuhyphen.app.api.service.informationrequest.parent.lockParentExchangeOf
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestTrustedRecipientEligibilityService
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestFieldAudienceGuard
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.util.*

open class InformationRequestLifecycleException(
    val reasonCode: String,
    override val message: String,
) : RuntimeException(message)

@ApplicationScoped
class InformationRequestLifecycleService @Inject constructor(
    private val requestRepository: InformationRequestRepository,
    private val exchangeLookup: ExchangeLookupService,
    private val authorizationService: AuthorizationService,
    private val commandReceiptService: CommandReceiptService,
    private val capabilityGate: InformationRequestTemplateCapabilityGate,
    private val transitionHistory: InformationRequestTransitionHistoryService,
    private val continuationGuard: InformationRequestContinuationGuard,
    private val executionGrantService: InformationRequestExecutionGrantService,
    private val partyRepository: InformationRequestPartyRepository,
    private val executionUsageReservationService: InformationRequestExecutionUsageReservationService,
    private val clockStopper: InformationRequestClockStopper,
    private val clockRebaser: InformationRequestClockIssueRebaser,
    private val trustedRecipients: InformationRequestTrustedRecipientEligibilityService,
    private val bootstrapLinks: InformationRequestBootstrapShareLinkService,
    private val fieldAudience: InformationRequestFieldAudienceGuard,
)
{
    @Transactional
    fun issue(command: IssueInformationRequestCommand): InformationRequestLifecycleResult {
        InformationRequestReadAuthorization.requireVisibleTarget(
            authorizationService,
            command.requestId,
            command.access
        )
        val locked = lock(command.requestId)
        return runOnce(
            locked = locked,
            operation = ISSUE_OPERATION,
            actor = command.access,
            idempotencyKey = command.idempotencyKey,
            fingerprint = fingerprint(ISSUE_OPERATION, command.requestId),
        ) {
            mutate(
                locked = locked,
                access = command.access,
                precondition = command.precondition,
                action = Action.INFORMATION_REQUEST_ISSUE,
                mutation = InformationRequestMutation.ISSUE,
                reasonCode = null,
                idempotencyKey = command.idempotencyKey,
                entitlement = continuationGuard::requireIssuance,
            ) { request, exchange, now ->
                capabilityGate.requireInstalledCapabilities(request.templateVersionId)
                trustedRecipients.requireIssuable(request)
                fieldAudience.requireAnswerableByParties(request)
                request.issuedAt = now
                if (exchange.status != ExchangeStatus.INITIATED)
                    clockRebaser.rebaseOnIssue(request, now.toInstant(), command.access.principal)
                val grant = executionGrantService.issueGrant(request, exchange)
                consumeActiveActingPartyCapacity(grant)
            }
        }
    }

    @Transactional
    fun cancel(command: CancelInformationRequestCommand): InformationRequestLifecycleResult {
        val reasonCode = InformationRequestReasonCodes.requireMachineCode(command.reasonCode)
        InformationRequestReadAuthorization.requireVisibleTarget(
            authorizationService,
            command.requestId,
            command.access
        )
        val locked = lock(command.requestId)
        return runOnce(
            locked = locked,
            operation = CANCEL_OPERATION,
            actor = command.access,
            idempotencyKey = command.idempotencyKey,
            fingerprint = fingerprint(CANCEL_OPERATION, command.requestId, reasonCode.orEmpty()),
        ) {
            mutate(
                locked = locked,
                access = command.access,
                precondition = command.precondition,
                action = Action.INFORMATION_REQUEST_CANCEL,
                mutation = InformationRequestMutation.CANCEL,
                reasonCode = reasonCode,
                idempotencyKey = command.idempotencyKey,
                entitlement = { continuationGuard.requireOwnerWithdrawal(it) },
            ) { request, _, now ->
                request.cancelledAt = now
            }.also {
                revokeAccessLinks(
                    it.request,
                    command.access,
                    InformationRequestCredentialCutoff.REQUEST_CANCELLED
                )
            }
        }
    }

    @Transactional
    fun supersede(command: SupersedeInformationRequestCommand): InformationRequestLifecycleResult {
        val reasonCode = InformationRequestReasonCodes.requireMachineCode(command.reasonCode)
        InformationRequestReadAuthorization.requireVisibleTarget(
            authorizationService,
            command.requestId,
            command.access
        )
        val locked = lock(command.requestId)
        return runOnce(
            locked = locked,
            operation = SUPERSEDE_OPERATION,
            actor = command.access,
            idempotencyKey = command.idempotencyKey,
            fingerprint = fingerprint(
                SUPERSEDE_OPERATION,
                command.requestId,
                command.supersededByRequestId,
                reasonCode.orEmpty(),
            ),
        ) {
            mutate(
                locked = locked,
                access = command.access,
                precondition = command.precondition,
                action = Action.INFORMATION_REQUEST_SUPERSEDE,
                mutation = InformationRequestMutation.SUPERSEDE,
                reasonCode = reasonCode,
                idempotencyKey = command.idempotencyKey,
                entitlement = {
                    continuationGuard.requireOwnerRecovery(
                        it,
                        InformationRequestContinuationAction.SUPERSEDE
                    )
                },
            ) { request, _, now ->
                val replacement = replacementRequest(command, request)
                request.supersededAt = now
                request.supersededByRequestId = replacement.id
            }.also {
                revokeAccessLinks(
                    it.request,
                    command.access,
                    InformationRequestCredentialCutoff.REQUEST_SUPERSEDED
                )
            }
        }
    }

    private fun revokeAccessLinks(request: InformationRequest, access: RequestAccessContext, reasonCode: String) {
        bootstrapLinks.revokeLinksKeepingSessions(
            request,
            InformationRequestCredentialCutoff(InformationRequestAuditActor.of(access), reasonCode),
        )
    }

    private fun lock(requestId: UUID): LockedInformationRequest {
        val exchange = lockParentExchangeOf(requestId, requestRepository, exchangeLookup)
        val request = requestRepository.findRequestByIdForUpdate(requestId)
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.NOT_FOUND,
                "Information Request not found",
            )
        return LockedInformationRequest(exchange, request)
    }

    private fun runOnce(
        locked: LockedInformationRequest,
        operation: String,
        actor: RequestAccessContext,
        idempotencyKey: String,
        fingerprint: String,
        mutation: () -> InformationRequestLifecycleResult,
    ): InformationRequestLifecycleResult
    {
        val receiptRequest = CommandReceiptRequest(
            resource = ResourceRef.informationRequest(locked.request.id),
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
        locked: LockedInformationRequest,
        access: RequestAccessContext,
        precondition: CommandPrecondition,
        action: Action,
        mutation: InformationRequestMutation,
        reasonCode: String?,
        idempotencyKey: String,
        entitlement: (LockedInformationRequest) -> Unit,
        beforeUpdate: (InformationRequest, Exchange, Timestamp) -> Unit,
    ): InformationRequestLifecycleResult
    {
        val exchange = locked.exchange
        val request = locked.request
        authorize(access, action, request.id)
        precondition.requireSatisfiedBy(InformationRequestETag.aggregateOf(request))
        entitlement(locked)
        val nextState = nextState(exchange, request.state, mutation)
        val previousState = request.state
        val now = Timestamp.from(Instant.now())
        beforeUpdate(request, exchange, now)
        request.state = nextState
        request.aggregateRevision += 1
        request.updatedAt = now
        requestRepository.update(request)
        if (nextState.isTerminal) {
            clockStopper.stopForFinishedRequest(
                request,
                access.principal,
                now.toInstant(),
                InformationRequestClockStopper.REQUEST_FINISHED,
            )
        }
        transitionHistory.record(
            InformationRequestTransitionHistoryCommand(
                request = request,
                fromState = previousState,
                toState = nextState,
                mutation = mutation,
                actor = InformationRequestAuditActor.of(access),
                reasonCode = reasonCode,
                auditIdempotencyKey = historyIdempotencyKey(mutation, request.id, idempotencyKey),
                requireAuditCapture = true,
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
            RequestExecutionUsageKind.ACTING_PARTY,
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
        authorize(
            command.access,
            Action.INFORMATION_REQUEST_SUPERSEDE,
            command.supersededByRequestId,
            "Access denied to the replacement Information Request",
        )
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

    private fun authorize(
        access: RequestAccessContext,
        action: Action,
        requestId: UUID,
        refusal: String = "Access denied to mutate Information Request lifecycle",
    )
    {
        val decision = authorizationService.authorize(
            access.principal,
            action,
            ResourceRef.informationRequest(requestId),
            access.authorization,
        )
        if (decision is Decision.Deny)
        {
            throw ForbiddenException(refusal)
        }
    }

    private fun replayLifecycleResult(
        result: CommandResultReference,
        access: RequestAccessContext
    ): InformationRequestLifecycleResult
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
            requestETag = InformationRequestETag.aggregateOf(request)
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
        CommandRequestFingerprint.of(operation, *fields.map { if (it is String) it.trim() else it }.toTypedArray())

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
