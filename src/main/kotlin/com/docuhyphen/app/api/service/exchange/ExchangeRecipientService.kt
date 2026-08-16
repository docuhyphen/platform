package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.ExchangeRecipient
import com.docuhyphen.app.api.model.entity.ExchangeRecipientAttestation
import com.docuhyphen.app.api.model.entity.ExchangeRecipientAcceptanceStatus
import com.docuhyphen.app.api.model.entity.ExchangeRecipientPurpose
import com.docuhyphen.app.api.model.entity.ExchangeRecipientSelectionType
import com.docuhyphen.app.api.model.entity.ExchangeShareRoleName
import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.entity.Share
import com.docuhyphen.app.api.model.entity.ShareSource
import com.docuhyphen.app.api.model.entity.ShareStatus
import com.docuhyphen.app.api.repository.exchange.ExchangeRecipientRepository
import com.docuhyphen.app.api.service.organization.OrganizationGroupService
import com.docuhyphen.app.api.service.organization.TrustedRecipientAuditService
import com.docuhyphen.app.api.service.organization.TrustedRecipientValidationService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@ApplicationScoped
class ExchangeRecipientService @Inject constructor(
    private val exchangeRecipientRepository: ExchangeRecipientRepository,
    private val shareService: ShareService,
    private val organizationGroupService: OrganizationGroupService,
    private val attestationService: ExchangeRecipientAttestationService,
    private val trustedRecipientValidationService: TrustedRecipientValidationService,
    private val externalEmailAcceptancePolicyService: ExternalEmailAcceptancePolicyService,
    private val trustedRecipientAuditService: TrustedRecipientAuditService,
)
{
    fun createBinding(
        exchangeId: UUID,
        directShare: Share,
        purpose: ExchangeRecipientPurpose,
        selectionType: ExchangeRecipientSelectionType,
        targetOrganizationId: UUID?,
        acceptanceStatus: ExchangeRecipientAcceptanceStatus,
    ): ExchangeRecipient
    {
        require(directShare.resourceType == ResourceType.EXCHANGE && directShare.resourceId == exchangeId) {
            "Recipient Share must belong to the Exchange"
        }
        require(directShare.source == ShareSource.DIRECT && directShare.sourceShareId == null) {
            "Recipient binding requires a direct Share"
        }
        require(directShare.roleName != ExchangeShareRoleName.OWNER) {
            "Exchange owner Share cannot be a recipient"
        }
        validateSelectionPrincipal(selectionType, directShare.principalKind)
        exchangeRecipientRepository.findByDirectShareId(directShare.id)?.let { existing ->
            require(existing.exchangeId == exchangeId &&
                existing.purpose == purpose &&
                existing.selectionType == selectionType &&
                existing.targetOrganizationId == targetOrganizationId) {
                "Recipient Share already has a different binding"
            }
            return existing
        }
        if (purpose == ExchangeRecipientPurpose.PRIMARY)
        {
            require(exchangeRecipientRepository.findPrimary(exchangeId) == null) {
                "Exchange already has a primary recipient"
            }
        }
        if (purpose == ExchangeRecipientPurpose.PARTICIPANT)
        {
            val trustedParticipant = selectionType == ExchangeRecipientSelectionType.TRUSTED_PERSON ||
                selectionType == ExchangeRecipientSelectionType.TRUSTED_GROUP
            require(
                acceptanceStatus == if (trustedParticipant)
                    ExchangeRecipientAcceptanceStatus.PENDING
                else
                    ExchangeRecipientAcceptanceStatus.NOT_REQUIRED,
            ) {
                "Only trusted participants can have a pending invitation decision"
            }
        }

        return exchangeRecipientRepository.save(
            ExchangeRecipient().apply {
                this.exchangeId = exchangeId
                this.directShareId = directShare.id
                this.purpose = purpose
                this.selectionType = selectionType
                this.targetOrganizationId = targetOrganizationId
                this.acceptanceStatus = acceptanceStatus
            },
        )
    }

    fun findPrimary(exchangeId: UUID): ExchangeRecipient? =
        exchangeRecipientRepository.findPrimary(exchangeId)

    fun findByExchangeId(exchangeId: UUID): List<ExchangeRecipient> =
        exchangeRecipientRepository.findByExchangeId(exchangeId)

    /**
     * Removes a recipient binding and its attestation, if any. Used when an Exchange owner replaces
     * a pending primary recipient so the single-primary and Share-uniqueness constraints stay
     * satisfied. The bound Share is revoked separately by the caller.
     */
    fun deleteBinding(recipient: ExchangeRecipient)
    {
        attestationService.deleteForRecipient(recipient.id)
        exchangeRecipientRepository.delete(recipient)
    }

    fun findByDirectShareId(directShareId: UUID): ExchangeRecipient? =
        exchangeRecipientRepository.findByDirectShareId(directShareId)

    fun getById(recipientId: UUID): ExchangeRecipient? = exchangeRecipientRepository.findById(recipientId)

    fun pendingTrustedParticipantShareIds(exchangeId: UUID): Set<UUID> =
        exchangeRecipientRepository.findPendingTrustedParticipants(exchangeId)
            .mapTo(mutableSetOf()) { it.directShareId }

    fun pendingTrustedParticipantInvitationsFor(appUserId: UUID): List<ExchangeRecipient> =
        exchangeRecipientRepository.findPendingTrustedParticipantsFor(appUserId)

    fun canViewPendingPrimaryInvitation(exchangeId: UUID, appUserId: UUID): Boolean
    {
        val recipient = exchangeRecipientRepository.findPrimary(exchangeId) ?: return false
        if (recipient.acceptanceStatus != ExchangeRecipientAcceptanceStatus.PENDING) return false
        val share = shareService.getById(recipient.directShareId) ?: return false
        if (share.status != ShareStatus.PENDING_APPROVAL) return false
        return canDecide(share, appUserId)
    }

    fun recordPrimaryDecision(
        exchange: Exchange,
        appUserId: UUID,
        accepted: Boolean,
    ): ExchangeRecipient
    {
        val exchangeId = exchange.id
        val recipient = pendingPrimaryForUpdate(exchangeId)
        if (accepted && recipient.isTrusted())
        {
            val ownerOrganizationId = exchange.ownerOrganizationId
            val (updated, auditOwnerOrganizationId) = try
            {
                val share = eligibleDirectShare(recipient, exchangeId)
                require(canDecide(share, appUserId)) {
                    "Only the primary recipient may decide this Exchange"
                }
                val attestation = attestationService.findForRecipient(recipient.id)
                    ?: throw IllegalArgumentException("Trusted recipient attestation was not found")
                validateTrustedAttestation(recipient, attestation)
                attestationService.markAcceptanceVerified(attestation)
                recordDecision(recipient, appUserId, accepted = true) to
                    (ownerOrganizationId ?: attestation.callerOrganizationId)
            }
            catch (exception: Exception)
            {
                trustedRecipientAuditService.recordAcceptanceDenied(
                    appUserId,
                    ownerOrganizationId,
                    exchangeId,
                    recipient,
                )
                throw exception
            }
            trustedRecipientAuditService.recordAcceptanceAllowed(
                appUserId,
                auditOwnerOrganizationId,
                exchangeId,
                recipient,
            )
            return updated
        }

        val share = eligibleDirectShare(recipient, exchangeId)
        if (!canDecide(share, appUserId))
        {
            throw IllegalArgumentException("Only the primary recipient may decide this Exchange")
        }
        if (accepted)
        {
            when (recipient.selectionType)
            {
                ExchangeRecipientSelectionType.EXTERNAL_EMAIL ->
                    externalEmailAcceptancePolicyService.validate(exchange, share, appUserId)
                else -> Unit
            }
        }

        return recordDecision(recipient, appUserId, accepted)
    }

    fun recordExternalEmailPrimaryDecision(
        exchange: Exchange,
        accepted: Boolean,
    ): ExchangeRecipient
    {
        val exchangeId = exchange.id
        val recipient = pendingPrimaryForUpdate(exchangeId)
        require(recipient.selectionType == ExchangeRecipientSelectionType.EXTERNAL_EMAIL) {
            "No-auth acceptance requires an external email recipient"
        }
        val share = eligibleDirectShare(recipient, exchangeId)
        require(share.principalKind == PrincipalKind.USER) {
            "No-auth primary recipient must resolve to a user Share"
        }
        if (accepted)
        {
            externalEmailAcceptancePolicyService.validate(exchange, share, authenticatedAppUserId = null)
        }
        return recordDecision(recipient, share.principalId, accepted)
    }

    @Transactional
    fun recordTrustedParticipantDecision(
        recipientId: UUID,
        appUserId: UUID,
        accepted: Boolean,
    ): ExchangeRecipient
    {
        val recipient = exchangeRecipientRepository.findByIdForUpdate(recipientId)
            ?: throw IllegalArgumentException("Trusted participant invitation was not found")
        val exchangeId = recipient.exchangeId
        require(recipient.purpose == ExchangeRecipientPurpose.PARTICIPANT) {
            "The primary recipient must use the Exchange acceptance decision"
        }
        require(
            recipient.selectionType == ExchangeRecipientSelectionType.TRUSTED_PERSON ||
                recipient.selectionType == ExchangeRecipientSelectionType.TRUSTED_GROUP,
        ) {
            "Only trusted participant invitations require an independent decision"
        }
        require(recipient.acceptanceStatus == ExchangeRecipientAcceptanceStatus.PENDING) {
            "Trusted participant invitation decision is not pending"
        }

        if (accepted)
        {
            var ownerOrganizationId: UUID? = null
            val updated = try
            {
                val attestation = attestationService.findForRecipient(recipient.id)
                    ?: throw IllegalArgumentException("Trusted participant attestation was not found")
                ownerOrganizationId = attestation.callerOrganizationId
                val share = eligibleDirectShare(recipient, exchangeId)
                require(canDecide(share, appUserId)) {
                    "Only the invited trusted participant may decide this invitation"
                }
                validateTrustedAttestation(recipient, attestation)
                attestationService.markAcceptanceVerified(attestation)
                val acceptedRecipient = recordDecision(recipient, appUserId, accepted = true)
                shareService.activate(share.id)
                acceptedRecipient
            }
            catch (exception: Exception)
            {
                trustedRecipientAuditService.recordAcceptanceDenied(
                    appUserId,
                    ownerOrganizationId,
                    exchangeId,
                    recipient,
                )
                throw exception
            }
            trustedRecipientAuditService.recordAcceptanceAllowed(
                appUserId,
                requireNotNull(ownerOrganizationId),
                exchangeId,
                recipient,
            )
            return updated
        }

        val share = eligibleDirectShare(recipient, exchangeId)
        if (!canDecide(share, appUserId))
        {
            throw IllegalArgumentException("Only the invited trusted participant may decide this invitation")
        }

        val updated = recordDecision(recipient, appUserId, accepted = false)
        shareService.revoke(share.id, appUserId)
        return updated
    }

    private fun pendingPrimaryForUpdate(exchangeId: UUID): ExchangeRecipient
    {
        val recipient = exchangeRecipientRepository.findPrimaryForUpdate(exchangeId)
            ?: throw IllegalArgumentException("Exchange primary recipient is not configured")
        require(recipient.acceptanceStatus == ExchangeRecipientAcceptanceStatus.PENDING) {
            "Exchange recipient decision is not pending"
        }
        return recipient
    }

    private fun eligibleDirectShare(recipient: ExchangeRecipient, exchangeId: UUID): Share
    {
        val share = shareService.getById(recipient.directShareId)
            ?: throw IllegalArgumentException("Exchange recipient Share was not found")
        require(share.resourceType == ResourceType.EXCHANGE && share.resourceId == exchangeId) {
            "Exchange recipient Share does not belong to the Exchange"
        }
        require(share.source == ShareSource.DIRECT && share.sourceShareId == null) {
            "Exchange recipient Share is not direct"
        }
        require(share.status == ShareStatus.ACTIVE || share.status == ShareStatus.PENDING_APPROVAL) {
            "Exchange recipient Share is not eligible for acceptance"
        }
        return share
    }

    private fun recordDecision(
        recipient: ExchangeRecipient,
        appUserId: UUID,
        accepted: Boolean,
    ): ExchangeRecipient
    {
        recipient.acceptanceStatus = if (accepted)
            ExchangeRecipientAcceptanceStatus.ACCEPTED
        else
            ExchangeRecipientAcceptanceStatus.REJECTED
        recipient.acceptedOrRejectedByAppUserId = appUserId
        recipient.acceptedOrRejectedAt = Timestamp.from(Instant.now())
        return exchangeRecipientRepository.update(recipient)
    }

    private fun validateTrustedAttestation(
        recipient: ExchangeRecipient,
        attestation: ExchangeRecipientAttestation,
    )
    {
        when (recipient.selectionType)
        {
            ExchangeRecipientSelectionType.TRUSTED_PERSON ->
                trustedRecipientValidationService.validatePersonAttestation(attestation)
            ExchangeRecipientSelectionType.TRUSTED_GROUP ->
                trustedRecipientValidationService.validateGroupAttestation(attestation)
            else -> error("Trusted recipient selection is required")
        }
    }

    private fun ExchangeRecipient.isTrusted(): Boolean =
        selectionType == ExchangeRecipientSelectionType.TRUSTED_PERSON ||
            selectionType == ExchangeRecipientSelectionType.TRUSTED_GROUP

    private fun validateSelectionPrincipal(
        selectionType: ExchangeRecipientSelectionType,
        principalKind: PrincipalKind,
    )
    {
        when (selectionType)
        {
            ExchangeRecipientSelectionType.REGISTERED_USER,
            ExchangeRecipientSelectionType.TRUSTED_PERSON ->
                require(principalKind == PrincipalKind.USER) {
                    "$selectionType requires a user Share"
                }
            ExchangeRecipientSelectionType.EXTERNAL_EMAIL ->
                require(principalKind == PrincipalKind.USER || principalKind == PrincipalKind.PARTICIPANT) {
                    "EXTERNAL_EMAIL requires a user or participant Share"
                }
            ExchangeRecipientSelectionType.INTERNAL_GROUP,
            ExchangeRecipientSelectionType.PERSONAL_GROUP,
            ExchangeRecipientSelectionType.TRUSTED_GROUP ->
                require(principalKind == PrincipalKind.PRINCIPAL_GROUP) {
                    "$selectionType requires a group Share"
                }
        }
    }

    private fun canDecide(share: Share, appUserId: UUID): Boolean =
        when (share.principalKind)
        {
            PrincipalKind.USER -> share.principalId == appUserId
            PrincipalKind.PRINCIPAL_GROUP ->
                organizationGroupService.isActiveDecisionMaker(share.principalId, appUserId)
            else -> false
        }
}
