package com.docuhyphen.app.api.service.informationrequest.party

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestParty
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestContinuationAction
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.party.AssignExternalParticipantInformationRequestPartyCommand
import com.docuhyphen.app.api.model.informationrequest.party.AssignInformationRequestPartyCommand
import com.docuhyphen.app.api.model.informationrequest.party.InformationRequestPartyAssignmentResult
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.exchange.ExternalParticipantService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Instant

@ApplicationScoped
class InformationRequestPartyAssignmentService @Inject constructor(
    private val partyRepository: InformationRequestPartyRepository,
    private val support: InformationRequestPartyCommandSupport,
    private val capacity: InformationRequestPartyCapacity,
    private val mutationGate: InformationRequestMutationGate,
    private val eligibility: InformationRequestPartyEligibilityService,
    private val trustedRecipients: InformationRequestTrustedRecipientEligibilityService,
    private val externalParticipantService: ExternalParticipantService,
    private val recipientAddresses: InformationRequestRecipientAddressResolver,
) {
    @Transactional
    fun assign(command: AssignInformationRequestPartyCommand): InformationRequestPartyAssignmentResult =
        support.runOnce(
            command.requestId,
            ASSIGN_OPERATION,
            command.access,
            command.idempotencyKey,
            assignFingerprint(command)
        ) {
            assignMutation(command)
        }

    @Transactional
    fun assignExternalParticipant(
        command: AssignExternalParticipantInformationRequestPartyCommand,
    ): InformationRequestPartyAssignmentResult =
        support.runOnce(
            command.requestId,
            ASSIGN_EXTERNAL_PARTICIPANT_OPERATION,
            command.access,
            command.idempotencyKey,
            assignExternalParticipantFingerprint(command),
        ) {
            assignExternalParticipantMutation(command)
        }

    private fun assignMutation(command: AssignInformationRequestPartyCommand): InformationRequestPartyAssignmentResult {
        requirePartyRole(command.roleKey)
        val locked = support.lock(command.requestId)
        val request = locked.request
        support.authorizeManage(command.access, request.id)
        command.precondition.requireSatisfiedBy(InformationRequestETag.partiesOf(request))
        support.requireChangeAllowed(locked, InformationRequestMutation.ASSIGN_PARTY)
        mutationGate.requireContinuation(locked, InformationRequestContinuationAction.ASSIGN_PARTY_WITHIN_CAP)

        val now = Timestamp.from(Instant.now())
        val party = newParty(request, command.roleKey, command.access.principal, now)

        if (command.roleKey == InformationRequestShareRoleKey.SUBJECT) {
            val subjectIdentityRefId = command.subjectIdentityRefId
                ?: throw malformed("A subject party must name a Subject Identity")
            if (command.principal != null) throw malformed("A subject party cannot name an acting principal")
            support.requireSubjectOwnedByRequest(subjectIdentityRefId, request)
            support.requireSubjectNotAlreadyAssigned(request, subjectIdentityRefId)
            party.subjectIdentityRefId = subjectIdentityRefId
            val saved = partyRepository.save(party)
            support.advancePartyRevision(request, now)
            support.recordHistory(
                request,
                saved,
                InformationRequestMutation.ASSIGN_PARTY,
                InformationRequestAuditActor.of(command.access),
                command.idempotencyKey,
            )
            return support.result(request, saved)
        }

        val principal = command.principal ?: throw malformed("An acting party must name a principal")
        support.requireSupportedActingPrincipal(principal)
        support.requireSignedInReviewer(command.roleKey, principal.kind)
        if (command.subjectIdentityRefId != null) throw malformed("An acting party cannot name a Subject Identity")
        eligibility.requireEligible(request, command.access.principal, principal)
        support.requireNotAlreadyAssigned(request, command.roleKey, principal)
        support.requireAnswerable(request, command.roleKey, principal)
        party.principalKind = principal.kind
        party.principalId = principal.id
        party.exchangeRecipientId =
            (command.exchangeRecipientId ?: support.trustedRecipientOf(request, principal))?.also {
                trustedRecipients.requireAssignable(it, request.exchangeId, principal)
            }
        return storeActingParty(request, party, principal, command.roleKey, command.access, command.idempotencyKey, now)
    }

    private fun assignExternalParticipantMutation(
        command: AssignExternalParticipantInformationRequestPartyCommand,
    ): InformationRequestPartyAssignmentResult {
        requirePartyRole(command.roleKey)
        val locked = support.lock(command.requestId)
        val request = locked.request
        support.authorizeManage(command.access, request.id)
        command.precondition.requireSatisfiedBy(InformationRequestETag.partiesOf(request))
        support.requireChangeAllowed(locked, InformationRequestMutation.ASSIGN_PARTY)
        mutationGate.requireContinuation(locked, InformationRequestContinuationAction.ASSIGN_PARTY_WITHIN_CAP)
        if (command.roleKey == InformationRequestShareRoleKey.SUBJECT) {
            throw InformationRequestCommandRequestException(
                "An external participant cannot be assigned as the subject party",
                InformationRequestErrorCatalog.PARTY_ASSIGNMENT_INVALID,
            )
        }
        eligibility.requireUntrustedAddress(request, command.email)

        val principal = if (locked.exchange.requireRecipientSignIn) {
            val recipient = recipientAddresses.recipientAccountOf(request.exchangeId, command.email)
                ?: throw InformationRequestLifecycleException(
                    InformationRequestErrorCatalog.PARTY_EMAIL_NOT_RECIPIENT,
                    "On an Exchange that requires sign-in, a person added by email must already be one of its recipients",
                )
            recipient.also { eligibility.requireEligible(request, command.access.principal, it) }
        } else {
            support.requireSignedInReviewer(command.roleKey, PrincipalKind.PARTICIPANT)
            eligibility.requireAddressEligible(request, command.access.principal, command.email)
            val participant = externalParticipantService.findOrCreate(
                owner = InformationRequestParticipantOwner.of(request),
                email = command.email,
                displayName = command.displayName,
            )
            PrincipalRef.participant(participant.id)
        }

        support.requireNotAlreadyAssigned(request, command.roleKey, principal)
        support.requireAnswerable(request, command.roleKey, principal)
        val now = Timestamp.from(Instant.now())
        val party = newParty(request, command.roleKey, command.access.principal, now).apply {
            principalKind = principal.kind
            principalId = principal.id
        }
        return storeActingParty(request, party, principal, command.roleKey, command.access, command.idempotencyKey, now)
    }

    private fun storeActingParty(
        request: InformationRequest,
        party: InformationRequestParty,
        principal: PrincipalRef,
        roleKey: InformationRequestShareRoleKey,
        access: RequestAccessContext,
        idempotencyKey: String,
        now: Timestamp,
    ): InformationRequestPartyAssignmentResult =
        capacity.withReservedSlot(request, party) { consumeSlot ->
            party.shareId = support.grantShare(request, principal, roleKey, access.principal).id
            val saved = partyRepository.save(party)
            consumeSlot()
            support.advancePartyRevision(request, now)
            support.recordHistory(
                request,
                saved,
                InformationRequestMutation.ASSIGN_PARTY,
                InformationRequestAuditActor.of(access),
                idempotencyKey,
            )
            support.result(request, saved)
        }

    private fun newParty(
        request: InformationRequest,
        roleKey: InformationRequestShareRoleKey,
        assignedBy: PrincipalRef,
        now: Timestamp,
    ): InformationRequestParty =
        InformationRequestParty().apply {
            informationRequestId = request.id
            this.roleKey = roleKey
            assignedByPrincipalKind = assignedBy.kind
            assignedByPrincipalId = assignedBy.id
            assignedAt = now
            createdAt = now
            updatedAt = now
        }

    private fun requirePartyRole(roleKey: InformationRequestShareRoleKey) {
        if (roleKey !in InformationRequestShareRoleKey.PARTY_ROLES) {
            throw InformationRequestCommandRequestException("$roleKey is not a party role")
        }
    }

    private fun assignFingerprint(command: AssignInformationRequestPartyCommand): String =
        CommandRequestFingerprint.of(
            ASSIGN_OPERATION,
            command.requestId,
            command.roleKey,
            command.principal?.kind,
            command.principal?.id,
            command.subjectIdentityRefId,
            command.exchangeRecipientId,
        )

    private fun assignExternalParticipantFingerprint(
        command: AssignExternalParticipantInformationRequestPartyCommand,
    ): String =
        CommandRequestFingerprint.of(
            ASSIGN_EXTERNAL_PARTICIPANT_OPERATION,
            command.requestId,
            command.roleKey,
            command.email.trim().lowercase(),
            command.displayName?.trim().orEmpty(),
        )

    private fun malformed(message: String) =
        InformationRequestCommandRequestException(message, InformationRequestErrorCatalog.PARTY_ASSIGNMENT_INVALID)

    private companion object {
        const val ASSIGN_OPERATION = "assign-information-request-party"
        const val ASSIGN_EXTERNAL_PARTICIPANT_OPERATION = "assign-external-participant-information-request-party"
    }
}
