package com.docuhyphen.app.api.service.informationrequest.noauth

import com.docuhyphen.app.api.model.command.*
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestTransitionHistoryCommand
import com.docuhyphen.app.api.model.informationrequest.noauth.InformationRequestCredentialCutoff
import com.docuhyphen.app.api.model.informationrequest.noauth.InformationRequestParticipantAccountUpgrade
import com.docuhyphen.app.api.model.informationrequest.noauth.UpgradeInformationRequestParticipantAccountCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.noauth.ParticipantAccountLinkRepository
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.model.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.exchange.ExternalParticipantService
import com.docuhyphen.app.api.service.exchange.ShareLinkService
import com.docuhyphen.app.api.service.exchange.ShareService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestParticipantOwner
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestRespondentKey
import com.docuhyphen.app.api.service.organization.OrganizationExchangePolicyService
import com.docuhyphen.app.api.service.organization.OrganizationMembershipService
import com.docuhyphen.app.api.service.organization.TrustedRecipientValidationService
import com.docuhyphen.app.api.service.user.AppUserService
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@ApplicationScoped
class InformationRequestParticipantAccountUpgradeService @Inject constructor(
    private val requestRepository: InformationRequestRepository,
    private val partyRepository: InformationRequestPartyRepository,
    private val shareLinks: ShareLinkService,
    private val externalParticipantService: ExternalParticipantService,
    private val appUserService: AppUserService,
    private val shareService: ShareService,
    private val participantAccountLinkRepository: ParticipantAccountLinkRepository,
    private val requestAccessSessionService: RequestAccessSessionService,
    private val commandReceiptService: CommandReceiptService,
    private val bootstrapShareLinkService: InformationRequestBootstrapShareLinkService,
    private val transitionHistory: InformationRequestTransitionHistoryService,
    private val gate: InformationRequestMutationGate,
    private val trustValidation: TrustedRecipientValidationService,
    private val membershipService: OrganizationMembershipService,
    private val sharingPolicy: OrganizationExchangePolicyService,
)
{
    @Transactional
    fun upgrade(
        command: UpgradeInformationRequestParticipantAccountCommand,
    ): InformationRequestParticipantAccountUpgrade
    {
        if (command.access.principal.kind != PrincipalKind.USER) {
            throw ForbiddenException("Only a signed-in account holder can complete a registration upgrade")
        }
        val session = requestAccessSessionService.verifyCredential(command.sessionCredential)
        if (session.id != command.sessionId) {
            throw ForbiddenException("The session credential does not match the named session")
        }
        val sessionParty =
            shareLinks.findShareLink(session.shareLinkId)?.let { partyRepository.findByShareId(it.shareId) }
        if (sessionParty?.informationRequestId != command.requestId) {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.NOT_FOUND,
                "Information Request not found"
            )
        }
        val receiptRequest = CommandReceiptRequest(
            resource = ResourceRef.informationRequest(command.requestId),
            operation = UPGRADE_OPERATION,
            actor = CommandActorRef.accessSession(session.id),
            idempotencyKey = command.idempotencyKey,
            requestFingerprint = upgradeFingerprint(command),
        )
        return when (
            val decision = commandReceiptService.runOnce(receiptRequest) {
                val result = upgradeMutation(command)
                CommandMutationResult(result, result.commandResultReference())
            }
        )
        {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed -> replayResult(decision.result, session)
        }
    }

    private fun upgradeMutation(
        command: UpgradeInformationRequestParticipantAccountCommand,
    ): InformationRequestParticipantAccountUpgrade
    {
        val locked = gate.lock(command.requestId)
        val request = locked.request
        gate.requireMutation(locked, InformationRequestMutation.LINK_PARTICIPANT_ACCOUNT)
        gate.requireNotOperationallySuspended(locked)

        val session = requestAccessSessionService.requireActive(command.sessionId)
        val shareLink = shareLinks.lockShareLinkForUpdate(session.shareLinkId)
            ?: throw IllegalArgumentException("Information Request access link not found")
        require(shareLink.linkMode == ShareLinkMode.VERIFICATION_BOOTSTRAP) {
            "Only a bootstrap-mode access link session can complete a registration upgrade"
        }
        requireUsableShareLink(shareLink)

        val party = partyRepository.findByShareId(shareLink.shareId)
            ?: throw IllegalArgumentException("Information Request party not found")
        require(party.informationRequestId == request.id) { "Information Request party not found" }
        command.precondition.requireSatisfiedBy(InformationRequestETag.partyOf(party))
        require(party.active) { "Information Request party is already revoked" }
        require(party.principalKind == PrincipalKind.PARTICIPANT) {
            "Only a Participant-held request party can complete a registration upgrade"
        }
        require(
            session.participantPrincipalKind == party.principalKind &&
                    session.participantPrincipalId == party.principalId,
        ) { "Session is not bound to this request party" }

        val participantId = requireNotNull(party.principalId)
        val participant =
            externalParticipantService.findOwned(participantId, InformationRequestParticipantOwner.of(request))
                ?: throw IllegalArgumentException("Information Request participant not found")
        if (participant.emailVerifiedAt == null) {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.CONTACT_PROOF_REQUIRED,
                "This participant's contact address has not been verified",
            )
        }
        val appUser = appUserService.getById(command.access.principal.id)
            ?: throw IllegalArgumentException("App User not found")
        if (participant.emailLower != appUser.email.trim().lowercase())
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.PARTICIPANT_ACCOUNT_EMAIL_MISMATCH,
                "The registered account's email does not match this participant's verified contact address",
            )
        }

        val existingLink = participantAccountLinkRepository.findByParticipantId(participantId)
        if (existingLink != null && existingLink.appUserId != appUser.id)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.PARTICIPANT_ACCOUNT_ALREADY_LINKED,
                "This participant has already completed a verified registration upgrade to a different account",
            )
        }
        val participantParties = partyRepository.findActiveForRequest(request.id)
            .filter { it.principalKind == PrincipalKind.PARTICIPANT && it.principalId == participantId }
        requireNoLiveAccountAccess(request, appUser.id, participantParties)
        requireTrustNotSuspended(request, appUser.id)
        requireSharingPermitted(request, party, appUser.id)
        val link = existingLink ?: run {
            val now = Timestamp.from(Instant.now())
            participantAccountLinkRepository.save(
                ParticipantAccountLink().apply {
                    this.participantId = participantId
                    appUserId = appUser.id
                    linkedViaInformationRequestId = request.id
                    linkedViaShareLinkId = shareLink.id
                    linkedAt = now
                    createdAt = now
                },
            )
        }

        val registeringAccount = InformationRequestAuditActor.of(PrincipalRef.user(appUser.id), session)
        val grantedShares = partiesToLink(party, participantParties).associate { linked ->
            linked.id to linkToAccount(request, linked, appUser.id, link, shareLink, session, registeringAccount)
        }

        return InformationRequestParticipantAccountUpgrade(
            link,
            grantedShares.getValue(party.id),
            InformationRequestETag.partyOf(party),
            InformationRequestRespondentKey.of(PrincipalRef.user(appUser.id)),
        )
    }

    private fun partiesToLink(
        sessionParty: InformationRequestParty,
        participantParties: List<InformationRequestParty>,
    ): List<InformationRequestParty> =
        listOf(sessionParty) + participantParties
            .filter { it.id != sessionParty.id && it.linkedUserShareId == null }
            .sortedWith(compareBy<InformationRequestParty>({ it.createdAt }, { it.id }))

    private fun linkToAccount(
        request: InformationRequest,
        party: InformationRequestParty,
        appUserId: UUID,
        link: ParticipantAccountLink,
        shareLink: ShareLink,
        session: RequestAccessSession,
        registeringAccount: InformationRequestAuditActor,
    ): Share {
        val grantedShare = shareService.grantAdditionalRoleKeyWithPrincipalProvenance(
            resourceType = ResourceType.INFORMATION_REQUEST,
            resourceId = request.id,
            principalKind = PrincipalKind.USER,
            principalId = appUserId,
            roleName = party.roleKey.name,
            grantedByPrincipal = PrincipalRef.user(appUserId),
            resourceLabel = RESOURCE_LABEL,
        )
        linkAccountShare(request, party, grantedShare)
        bootstrapShareLinkService.revokeAllForParty(
            request,
            party,
            InformationRequestCredentialCutoff(
                actor = registeringAccount,
                reasonCode = InformationRequestCredentialCutoff.PARTICIPANT_ACCOUNT_LINKED,
            ),
        )
        recordAccountLink(request, party, link, shareLink, session, registeringAccount)
        return grantedShare
    }

    private fun requireNoLiveAccountAccess(
        request: InformationRequest,
        appUserId: UUID,
        participantParties: List<InformationRequestParty>,
    ) {
        val ownLinkedShares = participantParties.mapNotNull { it.linkedUserShareId }.toSet()
        val conflicting = shareService.findDirectSharesForPrincipalOnResource(
            PrincipalKind.USER,
            appUserId,
            ResourceType.INFORMATION_REQUEST,
            request.id,
        ).any { it.status in LIVE_SHARE_STATUSES && it.id !in ownLinkedShares }
        if (conflicting) {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.PARTICIPANT_ACCOUNT_ACCESS_CONFLICT,
                "The registered account already has access to this Information Request",
            )
        }
    }

    private fun requireTrustNotSuspended(request: InformationRequest, appUserId: UUID) {
        val ownerOrganizationId = request.ownerOrganizationId ?: return
        val suspended = membershipService.activeOrganizationIds(appUserId)
            .filter { it != ownerOrganizationId }
            .mapNotNull { trustValidation.relationshipIdForLookup(ownerOrganizationId, it) }
            .any(trustValidation::isRelationshipSuspended)
        if (suspended) {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.TRUST_SUSPENDED,
                "The account's organization has a suspended trust relationship with this request's owner",
            )
        }
    }

    private fun requireSharingPermitted(request: InformationRequest, party: InformationRequestParty, appUserId: UUID) {
        val owningOrganizationId =
            request.ownerOrganizationId.takeIf { request.ownerType == InformationRequestOwnerType.ORGANIZATION }
                ?: return
        val sharedBy =
            party.assignedByPrincipalId.takeIf { party.assignedByPrincipalKind == PrincipalKind.USER } ?: appUserId
        try {
            sharingPolicy.assertCanShareWithUser(owningOrganizationId, sharedBy, appUserId)
        } catch (refusal: IllegalArgumentException) {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.PARTY_SHARING_NOT_PERMITTED,
                refusal.message ?: "The request owner's sharing rules do not permit this account",
            )
        }
    }

    private fun linkAccountShare(request: InformationRequest, party: InformationRequestParty, share: Share) {
        val now = Timestamp.from(Instant.now())
        party.linkedUserShareId = share.id
        party.partyRevision += 1
        party.updatedAt = now
        partyRepository.update(party)
        request.partyRevision += 1
        request.updatedAt = now
        requestRepository.update(request)
    }

    private fun recordAccountLink(
        request: InformationRequest,
        party: InformationRequestParty,
        link: ParticipantAccountLink,
        shareLink: ShareLink,
        session: RequestAccessSession,
        actor: InformationRequestAuditActor,
    ) {
        val mutation = InformationRequestMutation.LINK_PARTICIPANT_ACCOUNT
        transitionHistory.record(
            InformationRequestTransitionHistoryCommand(
                request = request,
                fromState = request.state,
                toState = request.state,
                mutation = mutation,
                actor = actor,
                partyId = party.id,
                auditIdempotencyKey = "information_request.${mutation.name.lowercase()}|${request.id}|${party.id}|${session.id}",
                details = linkedMapOf(
                    "participantAccountLinkId" to link.id.toString(),
                    "shareLinkId" to shareLink.id.toString(),
                ),
                requireAuditCapture = true,
            ),
        )
    }

    private fun requireUsableShareLink(shareLink: ShareLink)
    {
        if (shareLink.status == ShareLinkStatus.REVOKED)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.ACCESS_LINK_REVOKED,
                "This access link has been revoked",
            )
        }
        val now = Timestamp.from(Instant.now())
        val expiresAt = shareLink.expiresAt
        if (shareLink.status == ShareLinkStatus.EXPIRED || (expiresAt != null && !expiresAt.after(now)))
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.ACCESS_LINK_EXPIRED,
                "This access link has expired",
            )
        }
    }

    private fun replayResult(
        result: CommandResultReference,
        session: RequestAccessSession,
    ): InformationRequestParticipantAccountUpgrade
    {
        require(result.resourceType == ResourceType.INFORMATION_REQUEST_PARTICIPANT_ACCOUNT_LINK) {
            "Command receipt does not reference a participant account link"
        }
        val link = participantAccountLinkRepository.findById(result.resourceId)
            ?: throw IllegalArgumentException("Participant account link receipt target not found")
        val party = shareLinks.findShareLink(session.shareLinkId)
            ?.let { partyRepository.findByShareId(it.shareId) }
            ?: throw IllegalArgumentException("Participant account link's granted Share not found")
        val grantedShare = party.linkedUserShareId
            ?.let(shareService::getById)
            ?: throw IllegalArgumentException("Participant account link's granted Share not found")
        if (!party.active || grantedShare.status != ShareStatus.ACTIVE) {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.PARTY_ALREADY_REVOKED,
                "This party's access to the request has ended since the account was linked",
            )
        }
        return InformationRequestParticipantAccountUpgrade(
            link,
            grantedShare,
            InformationRequestETag.partyOf(party),
            InformationRequestRespondentKey.of(PrincipalRef.user(link.appUserId)),
        )
    }

    private fun upgradeFingerprint(command: UpgradeInformationRequestParticipantAccountCommand): String =
        CommandRequestFingerprint.of(
            UPGRADE_OPERATION,
            command.requestId,
            command.sessionId,
            command.access.principal.kind,
            command.access.principal.id,
        )

    private fun InformationRequestParticipantAccountUpgrade.commandResultReference(): CommandResultReference =
        CommandResultReference(
            resourceType = ResourceType.INFORMATION_REQUEST_PARTICIPANT_ACCOUNT_LINK,
            resourceId = participantAccountLink.id,
            etag = partyETag,
        )

    private companion object
    {
        const val RESOURCE_LABEL = "Information Request"
        const val UPGRADE_OPERATION = "upgrade-information-request-participant-account"
        val LIVE_SHARE_STATUSES = setOf(ShareStatus.ACTIVE, ShareStatus.PENDING_APPROVAL)
    }
}
