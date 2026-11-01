package com.docuhyphen.app.api.service.informationrequest.party

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.command.*
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestContinuationAction
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.noauth.InformationRequestCredentialCutoff
import com.docuhyphen.app.api.model.informationrequest.party.GrantInformationRequestDelegatedAuthorityCommand
import com.docuhyphen.app.api.model.informationrequest.party.InformationRequestDelegatedAuthorityList
import com.docuhyphen.app.api.model.informationrequest.party.InformationRequestDelegatedAuthorityResult
import com.docuhyphen.app.api.model.informationrequest.party.RevokeInformationRequestDelegatedAuthorityCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestDelegatedAuthorityRepository
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.model.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestRequirementAuthorizationContextProvider
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@ApplicationScoped
class InformationRequestDelegatedAuthorityService @Inject constructor(
    private val requestRepository: InformationRequestRepository,
    private val partyRepository: InformationRequestPartyRepository,
    private val requirementRepository: InformationRequestRequirementRepository,
    private val authorityRepository: InformationRequestDelegatedAuthorityRepository,
    private val authorizationService: AuthorizationService,
    private val commandReceiptService: CommandReceiptService,
    private val gate: InformationRequestMutationGate,
    private val authorityRecorder: InformationRequestDelegatedAuthorityRecorder,
    private val eligibility: InformationRequestPartyEligibilityService,
    private val requirementRoles: InformationRequestRequirementAuthorizationContextProvider,
)
{
    @Transactional
    fun list(requestId: UUID, access: RequestAccessContext): InformationRequestDelegatedAuthorityList {
        authorizeReading(access, requestId)
        val request = requestRepository.findById(requestId)
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.NOT_FOUND,
                "Information Request not found",
            )
        return InformationRequestDelegatedAuthorityList(
            authorities = authorityRepository.findForRequest(request.id),
            authoritiesETag = InformationRequestETag.partiesOf(request),
        )
    }

    @Transactional(Transactional.TxType.MANDATORY)
    fun revokeAllForParty(
        request: InformationRequest,
        party: InformationRequestParty,
        cutoff: InformationRequestCredentialCutoff,
    ) {
        val now = Timestamp.from(Instant.now())
        authorityRepository.findActiveForPartyForUpdate(party.id).forEach { authority ->
            authority.active = false
            authority.revokedAt = now
            authority.revokedByPrincipalKind = cutoff.actor.principal.kind
            authority.revokedByPrincipalId = cutoff.actor.principal.id
            authority.revocationReason = cutoff.reasonCode
            authority.updatedAt = now
            authorityRecorder.recordCutoff(request, authorityRepository.update(authority), cutoff)
        }
    }

    @Transactional
    fun grant(
        command: GrantInformationRequestDelegatedAuthorityCommand,
    ): InformationRequestDelegatedAuthorityResult
    {
        gate.requireVisibleTarget(command.access, command.requestId)
        if (command.authorityInstrumentRef != null && command.authorityInstrumentRef.length > 512)
            throw InformationRequestCommandRequestException("Authority instrument reference has at most 512 characters")
        val receiptRequest = CommandReceiptRequest(
            resource = ResourceRef.informationRequest(command.requestId),
            operation = GRANT_OPERATION,
            actor = CommandActorRef.principal(command.access.principal),
            idempotencyKey = command.idempotencyKey,
            requestFingerprint = grantFingerprint(command),
        )
        return when (
            val decision = commandReceiptService.runOnce(receiptRequest) {
                val result = grantMutation(command)
                CommandMutationResult(result, result.commandResultReference())
            }
        )
        {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed -> replayResult(decision.result, command.access)
        }
    }

    @Transactional
    fun revoke(
        command: RevokeInformationRequestDelegatedAuthorityCommand,
    ): InformationRequestDelegatedAuthorityResult
    {
        gate.requireVisibleTarget(command.access, command.requestId)
        if (command.reason != null && command.reason.length > 512)
            throw InformationRequestCommandRequestException("Revocation reason has at most 512 characters")
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
            is CommandReceiptDecision.Replayed -> replayResult(decision.result, command.access)
        }
    }

    private fun grantMutation(
        command: GrantInformationRequestDelegatedAuthorityCommand,
    ): InformationRequestDelegatedAuthorityResult
    {
        val locked = gate.lock(command.requestId)
        val request = locked.request
        authorize(command.access, request.id)
        command.precondition.requireSatisfiedBy(InformationRequestETag.partiesOf(request))
        gate.requireMutation(locked, InformationRequestMutation.GRANT_DELEGATED_AUTHORITY)
        gate.requireContinuation(locked, InformationRequestContinuationAction.ASSIGN_PARTY_WITHIN_CAP)

        val party = partyRepository.findByIdForUpdate(command.assignedPartyId)
            ?: throw IllegalArgumentException("Information Request party not found")
        require(party.informationRequestId == request.id) { "Information Request party not found" }
        if (!party.active) throw ineligibleParty("Delegated authority cannot be granted for a revoked party")
        if (party.roleKey == InformationRequestShareRoleKey.SUBJECT) {
            throw ineligibleParty("Delegated authority cannot be granted for a subject party")
        }
        if (party.roleKey !in ANSWERING_ROLES) {
            throw ineligibleParty("Delegated authority can only be granted for a party that answers or confirms")
        }
        requireSupportedDelegatePrincipal(command.delegatePrincipal)
        eligibility.requireEligible(request, command.access.principal, command.delegatePrincipal)
        command.requirementId?.also { requirementId ->
            val requirement = requirementRepository.findById(requirementId)
                ?: throw IllegalArgumentException("Information Request Requirement not found")
            require(requirement.informationRequestId == request.id) {
                "Requirement does not belong to this Information Request"
            }
            if (party.roleKey !in requirementRoles.answeringRoleKeysOf(requirementId)) {
                throw InformationRequestLifecycleException(
                    InformationRequestErrorCatalog.DELEGATED_AUTHORITY_REQUIREMENT_INELIGIBLE,
                    "The named party neither answers nor confirms that Requirement",
                )
            }
        }

        val now = Timestamp.from(Instant.now())
        val effectiveAt = command.effectiveAt ?: now
        command.expiresAt?.also { expiresAt ->
            if (!expiresAt.after(effectiveAt)) {
                throw InformationRequestCommandRequestException(
                    "Delegated authority expiry must be after its effective date",
                    InformationRequestErrorCatalog.DELEGATED_AUTHORITY_WINDOW_INVALID,
                )
            }
        }

        val authority = InformationRequestDelegatedAuthority().apply {
            informationRequestId = request.id
            assignedPartyId = party.id
            delegatePrincipalKind = command.delegatePrincipal.kind
            delegatePrincipalId = command.delegatePrincipal.id
            requirementId = command.requirementId
            active = true
            grantorPrincipalKind = command.access.principal.kind
            grantorPrincipalId = command.access.principal.id
            authorityInstrumentRef = command.authorityInstrumentRef
            this.effectiveAt = effectiveAt
            expiresAt = command.expiresAt
            recordedAt = now
            updatedAt = now
        }
        val saved = authorityRepository.save(authority)

        request.partyRevision += 1
        request.updatedAt = now
        requestRepository.update(request)

        authorityRecorder.record(
            request,
            saved,
            InformationRequestMutation.GRANT_DELEGATED_AUTHORITY,
            command.access,
            command.idempotencyKey
        )

        return InformationRequestDelegatedAuthorityResult(
            authority = saved,
            authoritiesETag = InformationRequestETag.partiesOf(request),
        )
    }

    private fun revokeMutation(
        command: RevokeInformationRequestDelegatedAuthorityCommand,
    ): InformationRequestDelegatedAuthorityResult
    {
        val locked = gate.lock(command.requestId)
        val request = locked.request
        authorize(command.access, request.id)
        command.precondition.requireSatisfiedBy(InformationRequestETag.partiesOf(request))
        gate.requireMutation(locked, InformationRequestMutation.REVOKE_DELEGATED_AUTHORITY)

        val authority = authorityRepository.findByIdForUpdate(command.authorityId)
            ?: throw IllegalArgumentException("Delegated authority not found")
        require(authority.informationRequestId == request.id) { "Delegated authority not found" }
        if (!authority.active) {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.DELEGATED_AUTHORITY_ALREADY_REVOKED,
                "Delegated authority is already revoked",
            )
        }

        val now = Timestamp.from(Instant.now())
        authority.active = false
        authority.revokedAt = now
        authority.revokedByPrincipalKind = command.access.principal.kind
        authority.revokedByPrincipalId = command.access.principal.id
        authority.revocationReason = command.reason
        authority.updatedAt = now
        val saved = authorityRepository.update(authority)

        request.partyRevision += 1
        request.updatedAt = now
        requestRepository.update(request)

        authorityRecorder.record(
            request,
            saved,
            InformationRequestMutation.REVOKE_DELEGATED_AUTHORITY,
            command.access,
            command.idempotencyKey
        )

        return InformationRequestDelegatedAuthorityResult(
            authority = saved,
            authoritiesETag = InformationRequestETag.partiesOf(request),
        )
    }

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
            throw ForbiddenException("Access denied to manage Information Request delegated authority")
        }
    }

    private fun authorizeReading(access: RequestAccessContext, requestId: UUID) {
        val decision = authorizationService.authorize(
            access.principal,
            Action.INFORMATION_REQUEST_VIEW_OPERATIONS,
            ResourceRef.informationRequest(requestId),
            access.authorization,
        )
        if (decision !is Decision.Allow) {
            throw ForbiddenException("Access denied to Information Request delegated authority")
        }
    }

    private fun requireSupportedDelegatePrincipal(principal: PrincipalRef)
    {
        if (principal.kind !in supportedDelegatePrincipalKinds) {
            throw InformationRequestCommandRequestException(
                "Delegated authority may only be granted to users, participants, or groups",
                InformationRequestErrorCatalog.PARTY_ASSIGNMENT_INVALID,
            )
        }
    }

    private fun replayResult(
        result: CommandResultReference,
        access: RequestAccessContext
    ): InformationRequestDelegatedAuthorityResult
    {
        require(result.resourceType == ResourceType.INFORMATION_REQUEST_DELEGATED_AUTHORITY) {
            "Command receipt does not reference a delegated authority grant"
        }
        val authority = authorityRepository.findById(result.resourceId)
            ?: throw IllegalArgumentException("Delegated authority receipt target not found")
        authorize(access, authority.informationRequestId)
        val request = requestRepository.findById(authority.informationRequestId)
            ?: throw IllegalArgumentException("Delegated authority receipt target not found")
        return InformationRequestDelegatedAuthorityResult(
            authority = authority,
            authoritiesETag = InformationRequestETag.partiesOf(request),
        )
    }

    private fun InformationRequestDelegatedAuthorityResult.commandResultReference(): CommandResultReference =
        CommandResultReference(
            resourceType = ResourceType.INFORMATION_REQUEST_DELEGATED_AUTHORITY,
            resourceId = authority.id,
            etag = authoritiesETag,
        )

    private fun grantFingerprint(command: GrantInformationRequestDelegatedAuthorityCommand): String =
        CommandRequestFingerprint.of(
            GRANT_OPERATION,
            command.requestId,
            command.assignedPartyId,
            command.delegatePrincipal.kind,
            command.delegatePrincipal.id,
            command.requirementId,
            command.authorityInstrumentRef.orEmpty(),
            command.effectiveAt,
            command.expiresAt,
        )

    private fun revokeFingerprint(command: RevokeInformationRequestDelegatedAuthorityCommand): String =
        CommandRequestFingerprint.of(
            REVOKE_OPERATION,
            command.requestId,
            command.authorityId,
            command.reason.orEmpty(),
        )

    private fun ineligibleParty(message: String) =
        InformationRequestLifecycleException(
            InformationRequestErrorCatalog.DELEGATED_AUTHORITY_PARTY_INELIGIBLE,
            message
        )

    private companion object
    {
        const val GRANT_OPERATION = "grant-information-request-delegated-authority"
        const val REVOKE_OPERATION = "revoke-information-request-delegated-authority"
        val ANSWERING_ROLES = setOf(
            InformationRequestShareRoleKey.CONTRIBUTOR,
            InformationRequestShareRoleKey.PREPARER,
            InformationRequestShareRoleKey.ATTESTOR,
        )
        val supportedDelegatePrincipalKinds = setOf(
            PrincipalKind.USER,
            PrincipalKind.PARTICIPANT,
            PrincipalKind.PRINCIPAL_GROUP,
        )
    }
}
