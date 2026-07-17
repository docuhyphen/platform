package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.ExchangeRecipient
import com.docuhyphen.app.api.model.entity.ExchangeRecipientAcceptanceStatus
import com.docuhyphen.app.api.model.entity.ExchangeRecipientPurpose
import com.docuhyphen.app.api.model.entity.ExchangeRecipientSelectionType
import com.docuhyphen.app.api.model.entity.ExchangeShareRoleName
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.entity.Share
import com.docuhyphen.app.api.model.entity.ShareSource
import com.docuhyphen.app.api.model.entity.ShareStatus
import com.docuhyphen.app.api.repository.ExchangeRecipientRepository
import com.docuhyphen.app.api.service.organization.OrganizationGroupService
import com.docuhyphen.app.api.service.organization.TrustedRecipientValidationService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
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
            require(acceptanceStatus == ExchangeRecipientAcceptanceStatus.NOT_REQUIRED) {
                "Participants cannot decide Exchange acceptance"
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

    fun findByDirectShareId(directShareId: UUID): ExchangeRecipient? =
        exchangeRecipientRepository.findByDirectShareId(directShareId)

    fun getById(recipientId: UUID): ExchangeRecipient? = exchangeRecipientRepository.findById(recipientId)

    fun recordPrimaryDecision(
        exchangeId: UUID,
        appUserId: UUID,
        accepted: Boolean,
    ): ExchangeRecipient
    {
        val recipient = pendingPrimaryForUpdate(exchangeId)
        val share = eligibleDirectShare(recipient, exchangeId)
        if (!canDecide(share, appUserId))
        {
            throw IllegalArgumentException("Only the primary recipient may decide this Exchange")
        }
        // Accepting a trusted recipient activates access, so it must revalidate current trust,
        // policy, organization, and subject eligibility and fail closed if any has changed.
        // Rejecting only declines the invitation and never activates a Share, so it stays permitted
        // even when activation eligibility has lapsed.
        if (accepted)
        {
            when (recipient.selectionType)
            {
                ExchangeRecipientSelectionType.TRUSTED_GROUP ->
                {
                    val attestation = attestationService.findForRecipient(recipient.id)
                        ?: throw IllegalArgumentException("Trusted group attestation was not found")
                    trustedRecipientValidationService.validateGroupAttestation(attestation)
                    attestationService.markAcceptanceVerified(attestation)
                }
                ExchangeRecipientSelectionType.TRUSTED_PERSON ->
                {
                    val attestation = attestationService.findForRecipient(recipient.id)
                        ?: throw IllegalArgumentException("Trusted member attestation was not found")
                    trustedRecipientValidationService.validatePersonAttestation(attestation)
                    attestationService.markAcceptanceVerified(attestation)
                }
                else -> Unit
            }
        }

        return recordDecision(recipient, appUserId, accepted)
    }

    fun recordExternalEmailPrimaryDecision(
        exchangeId: UUID,
        accepted: Boolean,
    ): ExchangeRecipient
    {
        val recipient = pendingPrimaryForUpdate(exchangeId)
        require(recipient.selectionType == ExchangeRecipientSelectionType.EXTERNAL_EMAIL) {
            "No-auth acceptance requires an external email recipient"
        }
        val share = eligibleDirectShare(recipient, exchangeId)
        require(share.principalKind == PrincipalKind.USER) {
            "No-auth primary recipient must resolve to a user Share"
        }
        return recordDecision(recipient, share.principalId, accepted)
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
                organizationGroupService.isActiveOwnerOrManager(share.principalId, appUserId)
            else -> false
        }
}
