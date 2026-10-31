package com.docuhyphen.app.api.service.informationrequest.party

import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.command.*
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.informationrequest.LockedInformationRequest
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestParentSnapshot
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestPolicyDecision
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestTransitionHistoryCommand
import com.docuhyphen.app.api.model.informationrequest.party.InformationRequestPartyAssignmentResult
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.repository.informationrequest.party.SubjectIdentityRefRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.exchange.ExchangeLookupService
import com.docuhyphen.app.api.service.exchange.ExchangeRecipientService
import com.docuhyphen.app.api.service.exchange.ShareService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestReadAuthorization
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionMatrix
import com.docuhyphen.app.api.service.informationrequest.parent.lockParentExchangeOf
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestFieldAudienceGuard
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.sql.Timestamp
import java.util.*

@ApplicationScoped
class InformationRequestPartyCommandSupport @Inject constructor(
    private val requestRepository: InformationRequestRepository,
    private val partyRepository: InformationRequestPartyRepository,
    private val subjectIdentityRefRepository: SubjectIdentityRefRepository,
    private val exchangeRecipientService: ExchangeRecipientService,
    private val shareService: ShareService,
    private val authorizationService: AuthorizationService,
    private val commandReceiptService: CommandReceiptService,
    private val exchangeLookup: ExchangeLookupService,
    private val transitionHistory: InformationRequestTransitionHistoryService,
    private val fieldAudience: InformationRequestFieldAudienceGuard,
) {
    fun runOnce(
        requestId: UUID,
        operation: String,
        access: RequestAccessContext,
        idempotencyKey: String,
        fingerprint: String,
        mutation: () -> InformationRequestPartyAssignmentResult,
    ): InformationRequestPartyAssignmentResult {
        InformationRequestReadAuthorization.requireVisibleTarget(authorizationService, requestId, access)
        val receiptRequest = CommandReceiptRequest(
            resource = ResourceRef.informationRequest(requestId),
            operation = operation,
            actor = CommandActorRef.principal(access.principal),
            idempotencyKey = idempotencyKey,
            requestFingerprint = fingerprint,
        )
        return when (
            val decision = commandReceiptService.runOnce(receiptRequest) {
                val result = mutation()
                CommandMutationResult(result, result.commandResultReference())
            }
        ) {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed -> replayPartyResult(decision.result, access)
        }
    }

    fun lock(requestId: UUID): LockedInformationRequest {
        val exchange = lockParentExchangeOf(requestId, requestRepository, exchangeLookup)
        val request = requestRepository.findRequestByIdForUpdate(requestId)
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.NOT_FOUND,
                "Information Request not found",
            )
        return LockedInformationRequest(exchange, request)
    }

    fun authorizeManage(access: RequestAccessContext, requestId: UUID) {
        val decision = authorizationService.authorize(
            access.principal,
            Action.INFORMATION_REQUEST_MANAGE_PARTIES,
            ResourceRef.informationRequest(requestId),
            access.authorization,
        )
        if (decision is Decision.Deny) {
            throw ForbiddenException("Access denied to manage Information Request parties")
        }
    }

    fun requireChangeAllowed(locked: LockedInformationRequest, mutation: InformationRequestMutation) {
        val decision = InformationRequestTransitionMatrix.canMutate(
            InformationRequestParentSnapshot(
                status = locked.exchange.status,
                deleted = locked.exchange.isDeleted,
                lockedForUpdate = true,
            ),
            locked.request.state,
            mutation,
        )
        if (decision is InformationRequestPolicyDecision.Deny) {
            throw InformationRequestLifecycleException(
                decision.reasonCode,
                "This Information Request party change is not allowed",
            )
        }
    }

    fun grantShare(
        request: InformationRequest,
        principal: PrincipalRef,
        roleKey: InformationRequestShareRoleKey,
        grantedBy: PrincipalRef,
    ): Share =
        shareService.grantAdditionalRoleKeyWithPrincipalProvenance(
            resourceType = ResourceType.INFORMATION_REQUEST,
            resourceId = request.id,
            principalKind = principal.kind,
            principalId = principal.id,
            roleName = roleKey.name,
            grantedByPrincipal = grantedBy,
            resourceLabel = RESOURCE_LABEL,
        )

    fun revokeShare(shareId: UUID, revokedBy: PrincipalRef) {
        shareService.revoke(shareId = shareId, revokedBy = revokedBy, resourceLabel = RESOURCE_LABEL)
    }

    fun advancePartyRevision(request: InformationRequest, now: Timestamp) {
        request.partyRevision += 1
        request.updatedAt = now
        requestRepository.update(request)
    }

    fun recordHistory(
        request: InformationRequest,
        party: InformationRequestParty,
        mutation: InformationRequestMutation,
        actor: InformationRequestAuditActor,
        idempotencyKey: String,
        reasonCode: String? = null,
    ) {
        transitionHistory.record(
            InformationRequestTransitionHistoryCommand(
                request = request,
                fromState = request.state,
                toState = request.state,
                mutation = mutation,
                actor = actor,
                reasonCode = reasonCode,
                partyId = party.id,
                auditIdempotencyKey = "information_request.${mutation.name.lowercase()}|${request.id}|${party.id}|$idempotencyKey",
                requireAuditCapture = true,
            ),
        )
    }

    fun result(request: InformationRequest, party: InformationRequestParty): InformationRequestPartyAssignmentResult =
        InformationRequestPartyAssignmentResult(
            party = party,
            partiesETag = InformationRequestETag.partiesOf(request),
            partyETag = InformationRequestETag.partyOf(party),
        )

    fun requireSubjectOwnedByRequest(subjectIdentityRefId: UUID, request: InformationRequest) {
        val subject = when (request.ownerType) {
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

    fun requireNotAlreadyAssigned(
        request: InformationRequest,
        roleKey: InformationRequestShareRoleKey,
        principal: PrincipalRef,
        excludingPartyId: UUID = NO_PARTY,
    ) {
        if (partyRepository.existsActiveAssignment(
                request.id,
                roleKey,
                principal.kind,
                principal.id,
                excludingPartyId
            )
        ) {
            throw alreadyAssigned(roleKey)
        }
    }

    fun requireAnswerable(
        request: InformationRequest,
        roleKey: InformationRequestShareRoleKey,
        principal: PrincipalRef
    ) {
        fieldAudience.requireAnswerableBy(request, roleKey, principal)
    }

    fun requireSubjectNotAlreadyAssigned(request: InformationRequest, subjectIdentityRefId: UUID) {
        if (partyRepository.existsActiveSubject(request.id, subjectIdentityRefId)) {
            throw alreadyAssigned(InformationRequestShareRoleKey.SUBJECT)
        }
    }

    fun isAlreadyAssigned(
        request: InformationRequest,
        roleKey: InformationRequestShareRoleKey,
        principal: PrincipalRef,
    ): Boolean = partyRepository.existsActiveAssignment(request.id, roleKey, principal.kind, principal.id, NO_PARTY)

    private fun alreadyAssigned(roleKey: InformationRequestShareRoleKey) =
        InformationRequestLifecycleException(
            InformationRequestErrorCatalog.PARTY_ALREADY_ASSIGNED,
            "This party already holds the $roleKey role on this Information Request",
        )

    fun requireSupportedActingPrincipal(principal: PrincipalRef) {
        require(principal.kind in SUPPORTED_ACTING_PRINCIPAL_KINDS) {
            "Information Request acting parties must be users, participants, or groups"
        }
    }

    fun requireSignedInReviewer(roleKey: InformationRequestShareRoleKey, principalKind: PrincipalKind) {
        if (roleKey == InformationRequestShareRoleKey.REVIEWER && principalKind == PrincipalKind.PARTICIPANT) {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.REVIEWER_SIGN_IN_REQUIRED,
                "A reviewer reviews while signed in, so name a person with an account or a group as Reviewer",
            )
        }
    }

    fun trustedRecipientOf(request: InformationRequest, principal: PrincipalRef): UUID? =
        shareService.findDirectForPrincipalOnResource(
            principal.kind,
            principal.id,
            ResourceType.EXCHANGE,
            request.exchangeId
        )
            ?.let { exchangeRecipientService.findByDirectShareId(it.id) }
            ?.takeIf { it.selectionType in TRUSTED_SELECTIONS }
            ?.id

    private fun replayPartyResult(
        result: CommandResultReference,
        access: RequestAccessContext,
    ): InformationRequestPartyAssignmentResult {
        require(result.resourceType == ResourceType.INFORMATION_REQUEST_PARTY) {
            "Command receipt does not reference an Information Request party"
        }
        val party = partyRepository.findById(result.resourceId)
            ?: throw IllegalArgumentException("Information Request party receipt target not found")
        authorizeManage(access, party.informationRequestId)
        val request = requestRepository.findById(party.informationRequestId)
            ?: throw IllegalArgumentException("Information Request party receipt target not found")
        return result(request, party)
    }

    private fun InformationRequestPartyAssignmentResult.commandResultReference(): CommandResultReference =
        CommandResultReference(
            resourceType = ResourceType.INFORMATION_REQUEST_PARTY,
            resourceId = party.id,
            revision = party.partyRevision,
            etag = partiesETag,
        )

    private companion object {
        const val RESOURCE_LABEL = "Information Request"
        val NO_PARTY = UUID(0, 0)
        val TRUSTED_SELECTIONS = setOf(
            ExchangeRecipientSelectionType.TRUSTED_PERSON,
            ExchangeRecipientSelectionType.TRUSTED_GROUP,
        )
        val SUPPORTED_ACTING_PRINCIPAL_KINDS = setOf(
            PrincipalKind.USER,
            PrincipalKind.PARTICIPANT,
            PrincipalKind.PRINCIPAL_GROUP,
        )
    }
}
