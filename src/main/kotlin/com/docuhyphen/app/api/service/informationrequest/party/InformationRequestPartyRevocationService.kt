package com.docuhyphen.app.api.service.informationrequest.party

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestParty
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestContinuationAction
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestTransitionHistoryCommand
import com.docuhyphen.app.api.model.informationrequest.noauth.InformationRequestCredentialCutoff
import com.docuhyphen.app.api.model.informationrequest.party.InformationRequestPartyAssignmentResult
import com.docuhyphen.app.api.model.informationrequest.party.ReassignInformationRequestPartyCommand
import com.docuhyphen.app.api.model.informationrequest.party.RevokeInformationRequestPartyCommand
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestBootstrapShareLinkService
import com.docuhyphen.app.api.service.informationrequest.review.InformationRequestReviewPartyRevocation
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@ApplicationScoped
class InformationRequestPartyRevocationService @Inject constructor(
    private val partyRepository: InformationRequestPartyRepository,
    private val support: InformationRequestPartyCommandSupport,
    private val capacity: InformationRequestPartyCapacity,
    private val mutationGate: InformationRequestMutationGate,
    private val eligibility: InformationRequestPartyEligibilityService,
    private val trustedRecipients: InformationRequestTrustedRecipientEligibilityService,
    private val delegatedAuthorities: InformationRequestDelegatedAuthorityService,
    private val bootstrapShareLinkService: InformationRequestBootstrapShareLinkService,
    private val transitionHistory: InformationRequestTransitionHistoryService,
    private val reviewAssignments: InformationRequestReviewPartyRevocation,
) {
    @Transactional
    fun revoke(command: RevokeInformationRequestPartyCommand): InformationRequestPartyAssignmentResult =
        support.runOnce(
            command.requestId,
            REVOKE_OPERATION,
            command.access,
            command.idempotencyKey,
            revokeFingerprint(command)
        ) {
            revokeMutation(command)
        }

    @Transactional
    fun reassign(command: ReassignInformationRequestPartyCommand): InformationRequestPartyAssignmentResult =
        support.runOnce(
            command.requestId,
            REASSIGN_OPERATION,
            command.access,
            command.idempotencyKey,
            reassignFingerprint(command)
        ) {
            reassignMutation(command)
        }

    @Transactional(Transactional.TxType.MANDATORY)
    fun revokeForOwnershipChange(requestId: UUID, partyId: UUID): InformationRequestParty? =
        revokeForPrincipalRemoval(requestId, partyId, InformationRequestCredentialCutoff.ORGANIZATION_MEMBER_REMOVED)

    @Transactional(Transactional.TxType.MANDATORY)
    fun revokeForGroupRemoval(requestId: UUID, partyId: UUID): InformationRequestParty? =
        revokeForPrincipalRemoval(requestId, partyId, InformationRequestCredentialCutoff.GROUP_REMOVED)

    private fun revokeForPrincipalRemoval(
        requestId: UUID,
        partyId: UUID,
        reasonCode: String
    ): InformationRequestParty? {
        val locked = support.lock(requestId)
        val request = locked.request
        val party =
            partyRepository.findByIdForUpdate(partyId)?.takeIf { it.informationRequestId == request.id && it.active }
                ?: return null
        val saved = revokeParty(request, party, InformationRequestCredentialCutoff(SYSTEM_ACTOR, reasonCode))
        support.recordHistory(
            request,
            saved,
            InformationRequestMutation.REVOKE_PARTY,
            SYSTEM_ACTOR,
            reasonCode.lowercase(),
            reasonCode,
        )
        reviewAssignments.revoke(locked, saved.id, SYSTEM_ACTOR, reasonCode, reasonCode.lowercase())
        return saved
    }

    private fun reassignMutation(command: ReassignInformationRequestPartyCommand): InformationRequestPartyAssignmentResult {
        val locked = support.lock(command.requestId)
        val request = locked.request
        support.authorizeManage(command.access, request.id)
        command.precondition.requireSatisfiedBy(InformationRequestETag.partiesOf(request))
        support.requireSupportedActingPrincipal(command.principal)
        eligibility.requireEligible(request, command.access.principal, command.principal)

        support.requireChangeAllowed(locked, InformationRequestMutation.REASSIGN)
        mutationGate.requireContinuation(locked, InformationRequestContinuationAction.ASSIGN_PARTY_WITHIN_CAP)

        val party = activePartyOf(request, command.partyId)
        if (party.roleKey == InformationRequestShareRoleKey.SUBJECT) {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.PARTY_NOT_REASSIGNABLE,
                "A subject party cannot be reassigned to an acting principal",
            )
        }
        support.requireSignedInReviewer(party.roleKey, command.principal.kind)
        support.requireNotAlreadyAssigned(request, party.roleKey, command.principal, excludingPartyId = party.id)
        support.requireAnswerable(request, party.roleKey, command.principal)

        val exchangeRecipientId =
            (command.exchangeRecipientId ?: support.trustedRecipientOf(request, command.principal))?.also {
                trustedRecipients.requireAssignable(it, request.exchangeId, command.principal)
            }
        releasePartyAccess(
            request,
            party,
            InformationRequestCredentialCutoff(
                actor = InformationRequestAuditActor.of(command.access),
                reasonCode = InformationRequestCredentialCutoff.PARTY_REASSIGNED,
            ),
        )
        val share = support.grantShare(request, command.principal, party.roleKey, command.access.principal)

        val holders = listOfNotNull(
            party.principalKind?.let { "previousPrincipalKind" to it.name },
            party.principalId?.let { "previousPrincipalId" to it.toString() },
            "principalKind" to command.principal.kind.name,
            "principalId" to command.principal.id.toString(),
        ).toMap()
        val now = Timestamp.from(Instant.now())
        party.principalKind = command.principal.kind
        party.principalId = command.principal.id
        party.exchangeRecipientId = exchangeRecipientId
        party.shareId = share.id
        party.linkedUserShareId = null
        party.assignedByPrincipalKind = command.access.principal.kind
        party.assignedByPrincipalId = command.access.principal.id
        party.assignedAt = now
        party.partyRevision += 1
        party.updatedAt = now
        val saved = partyRepository.update(party)

        support.advancePartyRevision(request, now)
        transitionHistory.record(
            InformationRequestTransitionHistoryCommand(
                request = request,
                fromState = request.state,
                toState = request.state,
                mutation = InformationRequestMutation.REASSIGN,
                actor = InformationRequestAuditActor.of(command.access),
                partyId = saved.id,
                auditIdempotencyKey = "information_request.party_reassign|${request.id}|${saved.id}|${command.idempotencyKey}",
                details = holders,
                requireAuditCapture = true,
            ),
        )
        return support.result(request, saved)
    }

    private fun revokeMutation(command: RevokeInformationRequestPartyCommand): InformationRequestPartyAssignmentResult {
        val locked = support.lock(command.requestId)
        val request = locked.request
        support.authorizeManage(command.access, request.id)
        command.precondition.requireSatisfiedBy(InformationRequestETag.partiesOf(request))
        support.requireChangeAllowed(locked, InformationRequestMutation.REVOKE_PARTY)

        val party = activePartyOf(request, command.partyId)

        val saved = revokeParty(
            request,
            party,
            InformationRequestCredentialCutoff(
                actor = InformationRequestAuditActor.of(command.access),
                reasonCode = InformationRequestCredentialCutoff.PARTY_REVOKED,
            ),
        )
        support.recordHistory(
            request,
            saved,
            InformationRequestMutation.REVOKE_PARTY,
            InformationRequestAuditActor.of(command.access),
            command.idempotencyKey,
        )
        reviewAssignments.revoke(
            locked,
            saved.id,
            InformationRequestAuditActor.of(command.access),
            InformationRequestCredentialCutoff.PARTY_REVOKED,
            command.idempotencyKey
        )
        return support.result(request, saved)
    }

    private fun activePartyOf(request: InformationRequest, partyId: UUID): InformationRequestParty {
        val party = partyRepository.findByIdForUpdate(partyId)
            ?: throw IllegalArgumentException("Information Request party not found")
        require(party.informationRequestId == request.id) { "Information Request party not found" }
        if (!party.active) {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.PARTY_ALREADY_REVOKED,
                "Information Request party is already revoked",
            )
        }
        return party
    }

    private fun revokeParty(
        request: InformationRequest,
        party: InformationRequestParty,
        cutoff: InformationRequestCredentialCutoff,
    ): InformationRequestParty {
        val now = Timestamp.from(Instant.now())
        party.active = false
        party.revokedAt = now
        party.partyRevision += 1
        party.updatedAt = now
        val saved = partyRepository.update(party)
        capacity.returnSlotIfIssued(request, party)
        releasePartyAccess(request, party, cutoff)
        support.advancePartyRevision(request, now)
        return saved
    }

    private fun releasePartyAccess(
        request: InformationRequest,
        party: InformationRequestParty,
        cutoff: InformationRequestCredentialCutoff,
    ) {
        delegatedAuthorities.revokeAllForParty(request, party, cutoff)
        party.shareId?.takeUnless { heldByAnotherActiveParty(request, party, it) }?.let { shareId ->
            bootstrapShareLinkService.revokeAllForParty(request, party, cutoff)
            support.revokeShare(shareId, cutoff.actor.principal)
        }
        party.linkedUserShareId?.takeUnless { heldByAnotherActiveParty(request, party, it) }?.let { shareId ->
            support.revokeShare(shareId, cutoff.actor.principal)
        }
    }

    private fun heldByAnotherActiveParty(
        request: InformationRequest,
        party: InformationRequestParty,
        shareId: UUID
    ): Boolean =
        partyRepository.existsOtherActivePartyHoldingShare(request.id, shareId, party.id)

    private fun reassignFingerprint(command: ReassignInformationRequestPartyCommand): String =
        CommandRequestFingerprint.of(
            REASSIGN_OPERATION,
            command.requestId,
            command.partyId,
            command.principal.kind,
            command.principal.id,
            command.exchangeRecipientId,
        )

    private fun revokeFingerprint(command: RevokeInformationRequestPartyCommand): String =
        CommandRequestFingerprint.of(REVOKE_OPERATION, command.requestId, command.partyId)

    private companion object {
        val SYSTEM_ACTOR =
            InformationRequestAuditActor.system(PrincipalRef(PrincipalKind.SERVICE_ACCOUNT, UUID(0, 0)))
        const val REASSIGN_OPERATION = "reassign-information-request-party"
        const val REVOKE_OPERATION = "revoke-information-request-party"
    }
}
