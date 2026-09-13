package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestDelegatedAuthority
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestDelegatedAuthorityRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestPartyRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
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

data class GrantInformationRequestDelegatedAuthorityCommand(
    val requestId: UUID,
    val assignedPartyId: UUID,
    val delegatePrincipal: PrincipalRef,
    val requirementId: UUID? = null,
    val authorityInstrumentRef: String? = null,
    val effectiveAt: Timestamp? = null,
    val expiresAt: Timestamp? = null,
    val access: RequestAccessContext,
    val precondition: CommandPrecondition,
    val idempotencyKey: String,
)

data class RevokeInformationRequestDelegatedAuthorityCommand(
    val requestId: UUID,
    val authorityId: UUID,
    val reason: String? = null,
    val access: RequestAccessContext,
    val precondition: CommandPrecondition,
    val idempotencyKey: String,
)

data class InformationRequestDelegatedAuthorityResult(
    val authority: InformationRequestDelegatedAuthority,
    val authoritiesETag: String,
)

/**
 * Command-safe lifecycle for the minimal delegated-authority fact rows the central Requirement
 * policy evaluator consumes. Grants and revocations reuse the same party-revision list ETag that
 * party assignment already advances, because a delegated authority only ever narrows or restores
 * who may act for an existing assigned party.
 */
@ApplicationScoped
class InformationRequestDelegatedAuthorityService @Inject constructor(
    private val requestRepository: InformationRequestRepository,
    private val partyRepository: InformationRequestPartyRepository,
    private val requirementRepository: InformationRequestRequirementRepository,
    private val authorityRepository: InformationRequestDelegatedAuthorityRepository,
    private val authorizationService: AuthorizationService,
    private val commandReceiptService: CommandReceiptService,
)
{
    @Transactional
    fun grant(
        command: GrantInformationRequestDelegatedAuthorityCommand,
    ): InformationRequestDelegatedAuthorityResult
    {
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
        val request = requestRepository.findRequestByIdForUpdate(command.requestId)
            ?: throw IllegalArgumentException("Information Request not found")
        command.precondition.requireSatisfiedBy(InformationRequestETag.partiesOf(request))
        authorize(command.access, request.id)

        val party = partyRepository.findByIdForUpdate(command.assignedPartyId)
            ?: throw IllegalArgumentException("Information Request party not found")
        require(party.informationRequestId == request.id) { "Information Request party not found" }
        require(party.active) { "Delegated authority cannot be granted for a revoked party" }
        require(party.roleKey != InformationRequestShareRoleKey.SUBJECT) {
            "Delegated authority cannot be granted for a subject party"
        }
        requireSupportedDelegatePrincipal(command.delegatePrincipal)
        command.requirementId?.also { requirementId ->
            val requirement = requirementRepository.findById(requirementId)
                ?: throw IllegalArgumentException("Information Request Requirement not found")
            require(requirement.informationRequestId == request.id) {
                "Requirement does not belong to this Information Request"
            }
        }

        val now = Timestamp.from(Instant.now())
        val effectiveAt = command.effectiveAt ?: now
        command.expiresAt?.also { expiresAt ->
            require(expiresAt.after(effectiveAt)) {
                "Delegated authority expiry must be after its effective date"
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

        return InformationRequestDelegatedAuthorityResult(
            authority = saved,
            authoritiesETag = InformationRequestETag.partiesOf(request),
        )
    }

    private fun revokeMutation(
        command: RevokeInformationRequestDelegatedAuthorityCommand,
    ): InformationRequestDelegatedAuthorityResult
    {
        val request = requestRepository.findRequestByIdForUpdate(command.requestId)
            ?: throw IllegalArgumentException("Information Request not found")
        command.precondition.requireSatisfiedBy(InformationRequestETag.partiesOf(request))
        authorize(command.access, request.id)

        val authority = authorityRepository.findByIdForUpdate(command.authorityId)
            ?: throw IllegalArgumentException("Delegated authority not found")
        require(authority.informationRequestId == request.id) { "Delegated authority not found" }
        require(authority.active) { "Delegated authority is already revoked" }

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

    private fun requireSupportedDelegatePrincipal(principal: PrincipalRef)
    {
        require(principal.kind in supportedDelegatePrincipalKinds) {
            "Delegated authority may only be granted to users, participants, or groups"
        }
    }

    private fun replayResult(result: CommandResultReference, access: RequestAccessContext): InformationRequestDelegatedAuthorityResult
    {
        require(result.resourceType == ResourceType.INFORMATION_REQUEST_DELEGATED_AUTHORITY) {
            "Command receipt does not reference a delegated authority grant"
        }
        val authority = authorityRepository.findById(result.resourceId)
            ?: throw IllegalArgumentException("Delegated authority receipt target not found")
        InformationRequestReadAuthorization.requireView(authorizationService, authority.informationRequestId, access)
        return InformationRequestDelegatedAuthorityResult(
            authority = authority,
            authoritiesETag = requireNotNull(result.etag) {
                "Delegated authority receipt did not record an aggregate ETag"
            },
        )
    }

    private fun InformationRequestDelegatedAuthorityResult.commandResultReference(): CommandResultReference =
        CommandResultReference(
            resourceType = ResourceType.INFORMATION_REQUEST_DELEGATED_AUTHORITY,
            resourceId = authority.id,
            etag = authoritiesETag,
        )

    private fun grantFingerprint(command: GrantInformationRequestDelegatedAuthorityCommand): String =
        CommandRequestFingerprint.sha256Hex(
            listOf(
                GRANT_OPERATION,
                command.requestId.toString(),
                command.assignedPartyId.toString(),
                command.delegatePrincipal.kind.name,
                command.delegatePrincipal.id.toString(),
                command.requirementId?.toString().orEmpty(),
                command.authorityInstrumentRef.orEmpty(),
                command.effectiveAt?.toString().orEmpty(),
                command.expiresAt?.toString().orEmpty(),
            ).joinToString("|"),
        )

    private fun revokeFingerprint(command: RevokeInformationRequestDelegatedAuthorityCommand): String =
        CommandRequestFingerprint.sha256Hex(
            listOf(
                REVOKE_OPERATION,
                command.requestId.toString(),
                command.authorityId.toString(),
                command.reason.orEmpty(),
            ).joinToString("|"),
        )

    private companion object
    {
        const val GRANT_OPERATION = "grant-information-request-delegated-authority"
        const val REVOKE_OPERATION = "revoke-information-request-delegated-authority"
        val supportedDelegatePrincipalKinds = setOf(
            PrincipalKind.USER,
            PrincipalKind.PARTICIPANT,
            PrincipalKind.PRINCIPAL_GROUP,
        )
    }
}
