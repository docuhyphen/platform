package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.entity.InformationRequestParty
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.RequestExecutionGrant
import com.docuhyphen.app.api.model.entity.RequestExecutionUsageKind
import com.docuhyphen.app.api.model.entity.RequestExecutionUsageReservation
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.entity.SubjectIdentityOwnerType
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.ExchangeRecipientSelectionType
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestPartyRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.SubjectIdentityRefRepository
import com.docuhyphen.app.api.resource.model.ExchangeRecipientSelectionRequest
import com.docuhyphen.app.api.resource.model.TrustedGroupRecipientSelectionRequest
import com.docuhyphen.app.api.resource.model.TrustedPersonRecipientSelectionRequest
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandPrecondition
import com.docuhyphen.app.api.service.command.CommandReceiptDecision
import com.docuhyphen.app.api.service.command.CommandReceiptRequest
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandActorRef
import com.docuhyphen.app.api.service.command.CommandMutationResult
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.command.CommandResultReference
import com.docuhyphen.app.api.service.exchange.ExternalParticipantOwner
import com.docuhyphen.app.api.service.exchange.ExternalParticipantService
import com.docuhyphen.app.api.service.exchange.ExchangeRecipientService
import com.docuhyphen.app.api.service.exchange.ExchangeRecipientSelectionResolver
import com.docuhyphen.app.api.service.exchange.ResolvedExchangeRecipientSelection
import com.docuhyphen.app.api.service.exchange.ShareService
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

data class AssignInformationRequestPartyCommand(
    val requestId: UUID,
    val roleKey: InformationRequestShareRoleKey,
    val principal: PrincipalRef? = null,
    val subjectIdentityRefId: UUID? = null,
    val exchangeRecipientId: UUID? = null,
    val access: RequestAccessContext,
    val precondition: CommandPrecondition,
    val idempotencyKey: String,
)

data class AssignExternalParticipantInformationRequestPartyCommand(
    val requestId: UUID,
    val roleKey: InformationRequestShareRoleKey,
    val email: String,
    val displayName: String? = null,
    val access: RequestAccessContext,
    val precondition: CommandPrecondition,
    val idempotencyKey: String,
)

data class AssignTrustedRecipientInformationRequestPartyCommand(
    val requestId: UUID,
    val roleKey: InformationRequestShareRoleKey,
    val selection: ExchangeRecipientSelectionRequest,
    val initiator: AppUser,
    val access: RequestAccessContext,
    val precondition: CommandPrecondition,
    val idempotencyKey: String,
)

data class RevokeInformationRequestPartyCommand(
    val requestId: UUID,
    val partyId: UUID,
    val access: RequestAccessContext,
    val precondition: CommandPrecondition,
    val idempotencyKey: String,
)

data class ReassignInformationRequestPartyCommand(
    val requestId: UUID,
    val partyId: UUID,
    val principal: PrincipalRef,
    val exchangeRecipientId: UUID? = null,
    val access: RequestAccessContext,
    val precondition: CommandPrecondition,
    val idempotencyKey: String,
)

data class InformationRequestPartyAssignmentResult(
    val party: InformationRequestParty,
    val partiesETag: String,
    val partyETag: String,
)

@ApplicationScoped
class InformationRequestPartyService @Inject constructor(
    private val requestRepository: InformationRequestRepository,
    private val partyRepository: InformationRequestPartyRepository,
    private val subjectIdentityRefRepository: SubjectIdentityRefRepository,
    private val externalParticipantService: ExternalParticipantService,
    private val exchangeRecipientService: ExchangeRecipientService,
    private val exchangeRecipientSelectionResolver: ExchangeRecipientSelectionResolver,
    private val shareService: ShareService,
    private val bootstrapShareLinkService: InformationRequestBootstrapShareLinkService,
    private val authorizationService: AuthorizationService,
    private val commandReceiptService: CommandReceiptService,
    private val exchangeRepository: ExchangeRepository,
    private val transitionHistory: InformationRequestTransitionHistoryService,
    private val executionGrantService: InformationRequestExecutionGrantService,
    private val executionUsageReservationService: InformationRequestExecutionUsageReservationService,
)
{
    @Transactional
    fun assign(command: AssignInformationRequestPartyCommand): InformationRequestPartyAssignmentResult
    {
        val receiptRequest = CommandReceiptRequest(
            resource = ResourceRef.informationRequest(command.requestId),
            operation = ASSIGN_OPERATION,
            actor = CommandActorRef.principal(command.access.principal),
            idempotencyKey = command.idempotencyKey,
            requestFingerprint = assignFingerprint(command),
        )
        return when (
            val decision = commandReceiptService.runOnce(receiptRequest) {
                val result = assignMutation(command)
                CommandMutationResult(result, result.commandResultReference())
            }
        )
        {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed -> replayPartyResult(decision.result, command.access)
        }
    }

    @Transactional
    fun assignExternalParticipant(
        command: AssignExternalParticipantInformationRequestPartyCommand,
    ): InformationRequestPartyAssignmentResult
    {
        val receiptRequest = CommandReceiptRequest(
            resource = ResourceRef.informationRequest(command.requestId),
            operation = ASSIGN_EXTERNAL_PARTICIPANT_OPERATION,
            actor = CommandActorRef.principal(command.access.principal),
            idempotencyKey = command.idempotencyKey,
            requestFingerprint = assignExternalParticipantFingerprint(command),
        )
        return when (
            val decision = commandReceiptService.runOnce(receiptRequest) {
                val result = assignExternalParticipantMutation(command)
                CommandMutationResult(result, result.commandResultReference())
            }
        )
        {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed -> replayPartyResult(decision.result, command.access)
        }
    }

    @Transactional
    fun assignTrustedRecipientSelection(
        command: AssignTrustedRecipientInformationRequestPartyCommand,
    ): InformationRequestPartyAssignmentResult
    {
        val receiptRequest = CommandReceiptRequest(
            resource = ResourceRef.informationRequest(command.requestId),
            operation = ASSIGN_TRUSTED_RECIPIENT_OPERATION,
            actor = CommandActorRef.principal(command.access.principal),
            idempotencyKey = command.idempotencyKey,
            requestFingerprint = assignTrustedRecipientFingerprint(command),
        )
        return when (
            val decision = commandReceiptService.runOnce(receiptRequest) {
                val result = assignTrustedRecipientSelectionMutation(command)
                CommandMutationResult(result, result.commandResultReference())
            }
        )
        {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed -> replayPartyResult(decision.result, command.access)
        }
    }

    @Transactional
    fun revoke(command: RevokeInformationRequestPartyCommand): InformationRequestPartyAssignmentResult
    {
        val receiptRequest = CommandReceiptRequest(
            resource = ResourceRef.informationRequest(command.requestId),
            operation = REVOKE_OPERATION,
            actor = CommandActorRef.principal(command.access.principal),
            idempotencyKey = command.idempotencyKey,
            requestFingerprint = revokeFingerprint(command),
        )
        return when (
            val decision = commandReceiptService.runOnce(receiptRequest) {
                val result = revokeMutation(command)
                CommandMutationResult(result, result.commandResultReference())
            }
        )
        {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed -> replayPartyResult(decision.result, command.access)
        }
    }

    @Transactional
    fun reassign(command: ReassignInformationRequestPartyCommand): InformationRequestPartyAssignmentResult
    {
        val receiptRequest = CommandReceiptRequest(
            resource = ResourceRef.informationRequest(command.requestId),
            operation = REASSIGN_OPERATION,
            actor = CommandActorRef.principal(command.access.principal),
            idempotencyKey = command.idempotencyKey,
            requestFingerprint = reassignFingerprint(command),
        )
        return when (
            val decision = commandReceiptService.runOnce(receiptRequest) {
                val result = reassignMutation(command)
                CommandMutationResult(result, result.commandResultReference())
            }
        )
        {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed -> replayPartyResult(decision.result, command.access)
        }
    }

    fun materializeBlueprintDefaultParties(
        request: InformationRequest,
        defaults: List<BlueprintInformationRequestPartyDefault>,
        access: RequestAccessContext,
    ): List<InformationRequestParty> =
        defaults.map { default ->
            createActingParty(
                request = request,
                roleKey = default.roleKey,
                principal = default.principal,
                exchangeRecipientId = null,
                assignedBy = access.principal,
            ).party
        }

    private fun assignMutation(command: AssignInformationRequestPartyCommand): InformationRequestPartyAssignmentResult
    {
        val (_, request) = lockPartyMutationRequest(command.requestId)
        command.precondition.requireSatisfiedBy(InformationRequestETag.partiesOf(request))
        authorize(command.access, request.id)

        val now = Timestamp.from(Instant.now())
        val party = InformationRequestParty().apply {
            informationRequestId = request.id
            roleKey = command.roleKey
            assignedByAppUserId = command.access.principal.id.takeIf {
                command.access.principal.kind == PrincipalKind.USER
            }
            assignedAt = now
            createdAt = now
            updatedAt = now
        }

        if (command.roleKey == InformationRequestShareRoleKey.SUBJECT)
        {
            val subjectIdentityRefId = requireNotNull(command.subjectIdentityRefId) {
                "A subject party must name a Subject Identity"
            }
            require(command.principal == null) { "A subject party cannot name an acting principal" }
            requireSubjectOwnedByRequest(subjectIdentityRefId, request)
            party.subjectIdentityRefId = subjectIdentityRefId
        }
        else
        {
            val principal = requireNotNull(command.principal) {
                "An acting party must name a principal"
            }
            requireSupportedActingPrincipal(principal)
            require(command.subjectIdentityRefId == null) {
                "An acting party cannot name a Subject Identity"
            }
            party.principalKind = principal.kind
            party.principalId = principal.id
            party.exchangeRecipientId = command.exchangeRecipientId?.also {
                exchangeRecipientService.requireAssignablePartyRecipient(it, request.exchangeId, principal)
            }
            val reservation = reserveRecipientCapacityIfIssued(request, party)
            var consumedReservation = false
            try
            {
                val share = shareService.grantRoleKeyWithPrincipalProvenance(
                    resourceType = ResourceType.INFORMATION_REQUEST,
                    resourceId = request.id,
                    principalKind = principal.kind,
                    principalId = principal.id,
                    roleName = command.roleKey.name,
                    grantedByPrincipal = command.access.principal,
                    resourceLabel = RESOURCE_LABEL,
                )
                party.shareId = share.id
                val saved = partyRepository.save(party)
                consumeRecipientCapacity(reservation)
                consumedReservation = reservation != null
                request.partyRevision += 1
                request.updatedAt = now
                requestRepository.update(request)
                return InformationRequestPartyAssignmentResult(
                    party = saved,
                    partiesETag = InformationRequestETag.partiesOf(request),
                    partyETag = InformationRequestETag.partyOf(saved),
                )
            }
            catch (exception: RuntimeException)
            {
                compensateRecipientCapacity(reservation, consumedReservation)
                throw exception
            }
        }

        val saved = partyRepository.save(party)
        request.partyRevision += 1
        request.updatedAt = now
        requestRepository.update(request)
        return InformationRequestPartyAssignmentResult(
            party = saved,
            partiesETag = InformationRequestETag.partiesOf(request),
            partyETag = InformationRequestETag.partyOf(saved),
        )
    }

    private fun assignExternalParticipantMutation(
        command: AssignExternalParticipantInformationRequestPartyCommand,
    ): InformationRequestPartyAssignmentResult
    {
        val (_, request) = lockPartyMutationRequest(command.requestId)
        command.precondition.requireSatisfiedBy(InformationRequestETag.partiesOf(request))
        authorize(command.access, request.id)
        require(command.roleKey != InformationRequestShareRoleKey.SUBJECT) {
            "An external participant cannot be assigned as the subject party"
        }

        val participant = externalParticipantService.findOrCreate(
            owner = participantOwnerFor(request),
            email = command.email,
            displayName = command.displayName,
        )
        val principal = PrincipalRef.participant(participant.id)

        val now = Timestamp.from(Instant.now())
        val party = InformationRequestParty().apply {
            informationRequestId = request.id
            roleKey = command.roleKey
            principalKind = principal.kind
            principalId = principal.id
            assignedByAppUserId = command.access.principal.id.takeIf {
                command.access.principal.kind == PrincipalKind.USER
            }
            assignedAt = now
            createdAt = now
            updatedAt = now
        }
        val reservation = reserveRecipientCapacityIfIssued(request, party)
        var consumedReservation = false
        try
        {
            val share = shareService.grantRoleKeyWithPrincipalProvenance(
                resourceType = ResourceType.INFORMATION_REQUEST,
                resourceId = request.id,
                principalKind = principal.kind,
                principalId = principal.id,
                roleName = command.roleKey.name,
                grantedByPrincipal = command.access.principal,
                resourceLabel = RESOURCE_LABEL,
            )
            party.shareId = share.id

            val saved = partyRepository.save(party)
            consumeRecipientCapacity(reservation)
            consumedReservation = reservation != null
            request.partyRevision += 1
            request.updatedAt = now
            requestRepository.update(request)
            return InformationRequestPartyAssignmentResult(
                party = saved,
                partiesETag = InformationRequestETag.partiesOf(request),
                partyETag = InformationRequestETag.partyOf(saved),
            )
        }
        catch (exception: RuntimeException)
        {
            compensateRecipientCapacity(reservation, consumedReservation)
            throw exception
        }
    }

    private fun assignTrustedRecipientSelectionMutation(
        command: AssignTrustedRecipientInformationRequestPartyCommand,
    ): InformationRequestPartyAssignmentResult
    {
        val (_, request) = lockPartyMutationRequest(command.requestId)
        command.precondition.requireSatisfiedBy(InformationRequestETag.partiesOf(request))
        authorize(command.access, request.id)
        require(command.roleKey != InformationRequestShareRoleKey.SUBJECT) {
            "A trusted recipient selection cannot be assigned as the subject party"
        }
        require(request.ownerType == InformationRequestOwnerType.ORGANIZATION) {
            "Trusted recipient selections require an organization-owned Information Request"
        }

        val resolved = resolveTrustedSelection(
            command.selection,
            command.initiator,
            requireNotNull(request.ownerOrganizationId),
        )
        val principal = PrincipalRef(resolved.principalKind, resolved.principalId)
        val directShare = shareService.findDirectForPrincipalOnResource(
            resolved.principalKind,
            resolved.principalId,
            ResourceType.EXCHANGE,
            request.exchangeId,
        ) ?: throw IllegalArgumentException("Trusted recipient must be invited on the parent Exchange before assignment")
        val recipient = exchangeRecipientService.findByDirectShareId(directShare.id)
            ?: throw IllegalArgumentException("Trusted recipient Exchange binding was not found")
        exchangeRecipientService.requireAssignablePartyRecipient(recipient.id, request.exchangeId, principal)

        return createActingParty(
            request = request,
            roleKey = command.roleKey,
            principal = principal,
            exchangeRecipientId = recipient.id,
            assignedBy = command.access.principal,
        )
    }

    private fun reassignMutation(command: ReassignInformationRequestPartyCommand): InformationRequestPartyAssignmentResult
    {
        val (exchange, request) = lockPartyMutationRequest(command.requestId)
        command.precondition.requireSatisfiedBy(InformationRequestETag.partiesOf(request))
        authorize(command.access, request.id)
        requireSupportedActingPrincipal(command.principal)

        requireReassignable(exchange, request.state)

        val party = partyRepository.findByIdForUpdate(command.partyId)
            ?: throw IllegalArgumentException("Information Request party not found")
        require(party.informationRequestId == request.id) { "Information Request party not found" }
        require(party.active) { "Information Request party is already revoked" }
        require(party.roleKey != InformationRequestShareRoleKey.SUBJECT) {
            "A subject party cannot be reassigned to an acting principal"
        }

        command.exchangeRecipientId?.also {
            exchangeRecipientService.requireAssignablePartyRecipient(it, request.exchangeId, command.principal)
        }
        party.shareId?.let { shareId ->
            bootstrapShareLinkService.revokeAllForShare(shareId)
            shareService.revokeWithPrincipalProvenance(
                shareId = shareId,
                revokedByPrincipal = command.access.principal,
                resourceLabel = RESOURCE_LABEL,
            )
        }
        val share = shareService.grantRoleKeyWithPrincipalProvenance(
            resourceType = ResourceType.INFORMATION_REQUEST,
            resourceId = request.id,
            principalKind = command.principal.kind,
            principalId = command.principal.id,
            roleName = party.roleKey.name,
            grantedByPrincipal = command.access.principal,
            resourceLabel = RESOURCE_LABEL,
        )

        val now = Timestamp.from(Instant.now())
        party.principalKind = command.principal.kind
        party.principalId = command.principal.id
        party.exchangeRecipientId = command.exchangeRecipientId
        party.shareId = share.id
        party.assignedByAppUserId = command.access.principal.id.takeIf {
            command.access.principal.kind == PrincipalKind.USER
        }
        party.assignedAt = now
        party.partyRevision += 1
        party.updatedAt = now
        val saved = partyRepository.update(party)

        request.partyRevision += 1
        request.updatedAt = now
        requestRepository.update(request)
        transitionHistory.record(
            InformationRequestTransitionHistoryCommand(
                request = request,
                fromState = request.state,
                toState = request.state,
                mutation = InformationRequestMutation.REASSIGN,
                actor = command.access.principal,
                partyId = saved.id,
                idempotencyKey = reassignHistoryIdempotencyKey(request.id, saved.id, command.idempotencyKey),
            ),
        )
        return InformationRequestPartyAssignmentResult(
            party = saved,
            partiesETag = InformationRequestETag.partiesOf(request),
            partyETag = InformationRequestETag.partyOf(saved),
        )
    }

    private fun revokeMutation(command: RevokeInformationRequestPartyCommand): InformationRequestPartyAssignmentResult
    {
        val (_, request) = lockPartyMutationRequest(command.requestId)
        command.precondition.requireSatisfiedBy(InformationRequestETag.partiesOf(request))
        authorize(command.access, request.id)

        val party = partyRepository.findByIdForUpdate(command.partyId)
            ?: throw IllegalArgumentException("Information Request party not found")
        require(party.informationRequestId == request.id) { "Information Request party not found" }
        require(party.active) { "Information Request party is already revoked" }

        val now = Timestamp.from(Instant.now())
        party.active = false
        party.revokedAt = now
        party.partyRevision += 1
        party.updatedAt = now
        val saved = partyRepository.update(party)
        rollbackRecipientCapacityIfIssued(request, party)
        party.shareId?.let { shareId ->
            bootstrapShareLinkService.revokeAllForShare(shareId)
            shareService.revokeWithPrincipalProvenance(
                shareId = shareId,
                revokedByPrincipal = command.access.principal,
                resourceLabel = RESOURCE_LABEL,
            )
        }

        request.partyRevision += 1
        request.updatedAt = now
        requestRepository.update(request)
        return InformationRequestPartyAssignmentResult(
            party = saved,
            partiesETag = InformationRequestETag.partiesOf(request),
            partyETag = InformationRequestETag.partyOf(saved),
        )
    }

    private fun lockPartyMutationRequest(requestId: UUID): Pair<Exchange, InformationRequest>
    {
        val exchange = lockParentExchangeOf(requestId, requestRepository, exchangeRepository)
        val request = requestRepository.findRequestByIdForUpdate(requestId)
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.NOT_FOUND,
                "Information Request not found",
            )
        return exchange to request
    }

    private fun createActingParty(
        request: InformationRequest,
        roleKey: InformationRequestShareRoleKey,
        principal: PrincipalRef,
        exchangeRecipientId: UUID?,
        assignedBy: PrincipalRef,
    ): InformationRequestPartyAssignmentResult
    {
        val now = Timestamp.from(Instant.now())
        val party = InformationRequestParty().apply {
            informationRequestId = request.id
            this.roleKey = roleKey
            principalKind = principal.kind
            principalId = principal.id
            this.exchangeRecipientId = exchangeRecipientId
            assignedByAppUserId = assignedBy.id.takeIf {
                assignedBy.kind == PrincipalKind.USER
            }
            assignedAt = now
            createdAt = now
            updatedAt = now
        }
        val reservation = reserveRecipientCapacityIfIssued(request, party)
        var consumedReservation = false
        try
        {
            val share = shareService.grantRoleKeyWithPrincipalProvenance(
                resourceType = ResourceType.INFORMATION_REQUEST,
                resourceId = request.id,
                principalKind = principal.kind,
                principalId = principal.id,
                roleName = roleKey.name,
                grantedByPrincipal = assignedBy,
                resourceLabel = RESOURCE_LABEL,
            )
            party.shareId = share.id

            val saved = partyRepository.save(party)
            consumeRecipientCapacity(reservation)
            consumedReservation = reservation != null
            request.partyRevision += 1
            request.updatedAt = now
            requestRepository.update(request)
            return InformationRequestPartyAssignmentResult(
                party = saved,
                partiesETag = InformationRequestETag.partiesOf(request),
                partyETag = InformationRequestETag.partyOf(saved),
            )
        }
        catch (exception: RuntimeException)
        {
            compensateRecipientCapacity(reservation, consumedReservation)
            throw exception
        }
    }

    private fun reserveRecipientCapacityIfIssued(
        request: InformationRequest,
        party: InformationRequestParty,
    ): RequestExecutionUsageReservation?
    {
        val grant = executionGrantService.findForRequest(request.id) ?: return null
        return reserveRecipientCapacity(grant, party)
    }

    private fun rollbackRecipientCapacityIfIssued(request: InformationRequest, party: InformationRequestParty)
    {
        val grant = executionGrantService.findForRequest(request.id) ?: return
        val reservation = reserveRecipientCapacity(grant, party)
        executionUsageReservationService.rollback(reservation.id)
    }

    private fun reserveRecipientCapacity(
        grant: RequestExecutionGrant,
        party: InformationRequestParty,
    ): RequestExecutionUsageReservation =
        executionUsageReservationService.reserve(
            grant.id,
            RequestExecutionUsageKind.ADDITIONAL_RECIPIENT,
            partyCapacityReservationKey(party.id),
            1L,
        )

    private fun consumeRecipientCapacity(reservation: RequestExecutionUsageReservation?)
    {
        reservation?.let { executionUsageReservationService.consume(it.id) }
    }

    private fun compensateRecipientCapacity(
        reservation: RequestExecutionUsageReservation?,
        consumed: Boolean,
    )
    {
        if (reservation == null)
        {
            return
        }
        if (consumed)
        {
            runCatching { executionUsageReservationService.rollback(reservation.id) }
        }
        else
        {
            runCatching { executionUsageReservationService.release(reservation.id) }
        }
    }

    private fun requireReassignable(exchange: Exchange, requestState: InformationRequestState)
    {
        val decision = InformationRequestTransitionMatrix.canMutate(
            InformationRequestParentSnapshot(
                status = exchange.status,
                deleted = exchange.isDeleted,
                lockedForUpdate = true,
            ),
            requestState,
            InformationRequestMutation.REASSIGN,
        )
        if (decision is InformationRequestPolicyDecision.Deny)
        {
            throw InformationRequestLifecycleException(
                decision.reasonCode,
                "Information Request party reassignment is not allowed",
            )
        }
    }

    private fun reassignHistoryIdempotencyKey(requestId: UUID, partyId: UUID, idempotencyKey: String): String =
        "information_request.party_reassign|$requestId|$partyId|$idempotencyKey"

    private fun authorize(access: RequestAccessContext, requestId: UUID)
    {
        val decision = authorizationService.authorize(
            access.principal,
            Action.INFORMATION_REQUEST_MANAGE_PARTIES,
            ResourceRef.informationRequest(requestId),
            access.authorization,
        )
        if (decision is Decision.Deny)
        {
            throw ForbiddenException("Access denied to manage Information Request parties")
        }
    }

    private fun resolveTrustedSelection(
        selection: ExchangeRecipientSelectionRequest,
        initiator: AppUser,
        ownerOrganizationId: UUID,
    ): ResolvedExchangeRecipientSelection
    {
        if (selection !is TrustedPersonRecipientSelectionRequest &&
            selection !is TrustedGroupRecipientSelectionRequest)
        {
            throw IllegalArgumentException(
                "A trusted request party must be a verified member or published group from a Trusted Organization",
            )
        }
        val resolved = exchangeRecipientSelectionResolver.resolve(selection, initiator, ownerOrganizationId)
        when (selection)
        {
            is TrustedPersonRecipientSelectionRequest ->
            {
                require(resolved.selectionType == ExchangeRecipientSelectionType.TRUSTED_PERSON) {
                    "The resolved selection does not match the requested trusted person"
                }
                requireNotNull(resolved.preparedPersonResolution) {
                    "The trusted person selection has no verification evidence"
                }
            }

            is TrustedGroupRecipientSelectionRequest ->
            {
                require(resolved.selectionType == ExchangeRecipientSelectionType.TRUSTED_GROUP) {
                    "The resolved selection does not match the requested trusted group"
                }
                requireNotNull(resolved.trustedGroupValidation) {
                    "The trusted group selection has no verification evidence"
                }
            }

            else -> error("Unsupported trusted request party selection")
        }
        return resolved
    }

    private fun requireSubjectOwnedByRequest(subjectIdentityRefId: UUID, request: InformationRequest)
    {
        val subject = when (request.ownerType)
        {
            InformationRequestOwnerType.ORGANIZATION ->
                subjectIdentityRefRepository.findOwned(
                    subjectIdentityRefId,
                    SubjectIdentityOwnerType.ORGANIZATION,
                    requireNotNull(request.ownerOrganizationId),
                )
            InformationRequestOwnerType.USER ->
                subjectIdentityRefRepository.findOwned(
                    subjectIdentityRefId,
                    SubjectIdentityOwnerType.USER,
                    requireNotNull(request.ownerUserId),
                )
        }
        require(subject != null) { "Subject Identity belongs to a different owner" }
    }

    private fun requireSupportedActingPrincipal(principal: PrincipalRef)
    {
        require(principal.kind in supportedActingPrincipalKinds) {
            "Information Request acting parties must be users, participants, or groups"
        }
    }

    private fun participantOwnerFor(request: InformationRequest): ExternalParticipantOwner =
        when (request.ownerType)
        {
            InformationRequestOwnerType.ORGANIZATION ->
                ExternalParticipantOwner.Organization(requireNotNull(request.ownerOrganizationId))
            InformationRequestOwnerType.USER ->
                ExternalParticipantOwner.Personal(requireNotNull(request.ownerUserId))
        }

    private fun replayPartyResult(result: CommandResultReference, access: RequestAccessContext): InformationRequestPartyAssignmentResult
    {
        require(result.resourceType == ResourceType.INFORMATION_REQUEST_PARTY) {
            "Command receipt does not reference an Information Request party"
        }
        val party = partyRepository.findById(result.resourceId)
            ?: throw IllegalArgumentException("Information Request party receipt target not found")
        InformationRequestReadAuthorization.requireView(authorizationService, party.informationRequestId, access)
        return InformationRequestPartyAssignmentResult(
            party = party,
            partiesETag = requireNotNull(result.etag) {
                "Information Request party receipt did not record an aggregate ETag"
            },
            partyETag = InformationRequestETag.partyOf(party),
        )
    }

    private fun InformationRequestPartyAssignmentResult.commandResultReference(): CommandResultReference =
        CommandResultReference(
            resourceType = ResourceType.INFORMATION_REQUEST_PARTY,
            resourceId = party.id,
            revision = party.partyRevision,
            etag = partiesETag,
        )

    private fun assignFingerprint(command: AssignInformationRequestPartyCommand): String =
        CommandRequestFingerprint.sha256Hex(
            listOf(
                ASSIGN_OPERATION,
                command.requestId.toString(),
                command.roleKey.name,
                command.principal?.kind?.name.orEmpty(),
                command.principal?.id?.toString().orEmpty(),
                command.subjectIdentityRefId?.toString().orEmpty(),
                command.exchangeRecipientId?.toString().orEmpty(),
            ).joinToString("|"),
        )

    private fun assignExternalParticipantFingerprint(
        command: AssignExternalParticipantInformationRequestPartyCommand,
    ): String =
        CommandRequestFingerprint.sha256Hex(
            listOf(
                ASSIGN_EXTERNAL_PARTICIPANT_OPERATION,
                command.requestId.toString(),
                command.roleKey.name,
                command.email.trim().lowercase(),
                command.displayName?.trim().orEmpty(),
            ).joinToString("|"),
        )

    private fun assignTrustedRecipientFingerprint(
        command: AssignTrustedRecipientInformationRequestPartyCommand,
    ): String =
        CommandRequestFingerprint.sha256Hex(
            listOf(
                ASSIGN_TRUSTED_RECIPIENT_OPERATION,
                command.requestId.toString(),
                command.roleKey.name,
                command.initiator.id.toString(),
                trustedSelectionFingerprint(command.selection),
            ).joinToString("|"),
        )

    private fun reassignFingerprint(command: ReassignInformationRequestPartyCommand): String =
        CommandRequestFingerprint.sha256Hex(
            listOf(
                REASSIGN_OPERATION,
                command.requestId.toString(),
                command.partyId.toString(),
                command.principal.kind.name,
                command.principal.id.toString(),
                command.exchangeRecipientId?.toString().orEmpty(),
            ).joinToString("|"),
        )

    private fun revokeFingerprint(command: RevokeInformationRequestPartyCommand): String =
        CommandRequestFingerprint.sha256Hex(
            listOf(
                REVOKE_OPERATION,
                command.requestId.toString(),
                command.partyId.toString(),
            ).joinToString("|"),
        )

    private fun trustedSelectionFingerprint(selection: ExchangeRecipientSelectionRequest): String =
        when (selection)
        {
            is TrustedPersonRecipientSelectionRequest ->
                listOf("TRUSTED_PERSON", selection.resolutionId.trim()).joinToString("|")
            is TrustedGroupRecipientSelectionRequest ->
                listOf(
                    "TRUSTED_GROUP",
                    selection.organizationId.trim(),
                    selection.groupId.trim(),
                ).joinToString("|")
            else -> selection::class.qualifiedName.orEmpty()
        }

    private companion object
    {
        const val RESOURCE_LABEL = "Information Request"
        const val ASSIGN_OPERATION = "assign-information-request-party"
        const val ASSIGN_EXTERNAL_PARTICIPANT_OPERATION = "assign-external-participant-information-request-party"
        const val ASSIGN_TRUSTED_RECIPIENT_OPERATION = "assign-trusted-recipient-information-request-party"
        const val REASSIGN_OPERATION = "reassign-information-request-party"
        const val REVOKE_OPERATION = "revoke-information-request-party"
        fun partyCapacityReservationKey(partyId: UUID): String = "information_request.party|$partyId"
        val supportedActingPrincipalKinds = setOf(
            PrincipalKind.USER,
            PrincipalKind.PARTICIPANT,
            PrincipalKind.PRINCIPAL_GROUP,
        )
    }
}
