package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.ExchangeRecipient
import com.docuhyphen.app.api.model.entity.ExchangeRecipientAttestation
import com.docuhyphen.app.api.model.entity.ExchangeRecipientAttestationSubjectType
import com.docuhyphen.app.api.repository.ExchangeRecipientAttestationRepository
import com.docuhyphen.app.api.service.organization.ExternalIdentityResolutionService.PreparedPersonResolution
import com.docuhyphen.app.api.service.organization.TrustedGroupValidation
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.inject.Provider
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@ApplicationScoped
class ExchangeRecipientAttestationService @Inject constructor(
    private val repository: ExchangeRecipientAttestationRepository,
    private val recipientServiceProvider: Provider<ExchangeRecipientService>,
)
{
    fun createGroupAttestation(
        recipient: ExchangeRecipient,
        validation: TrustedGroupValidation,
        verifiedAt: Instant = Instant.now(),
    ): ExchangeRecipientAttestation
    {
        require(recipient.targetOrganizationId == validation.targetOrganization.id) {
            "Recipient target organization does not match the trusted group"
        }
        require(repository.findByExchangeRecipientId(recipient.id) == null) {
            "Recipient attestation already exists"
        }
        return repository.save(
            ExchangeRecipientAttestation().apply {
                exchangeRecipientId = recipient.id
                relationshipId = validation.relationship.id
                callerOrganizationId = validation.callerOrganization.id
                targetOrganizationId = validation.targetOrganization.id
                senderPolicyRevision = validation.senderPolicy.revision
                targetPolicyRevision = validation.targetPolicy.revision
                subjectType = ExchangeRecipientAttestationSubjectType.GROUP
                subjectGroupId = validation.group.id
                displayNameSnapshot = validation.group.name
                organizationNameSnapshot = validation.targetOrganization.name
                this.verifiedAt = Timestamp.from(verifiedAt)
                verificationExpiresAt = Timestamp.from(validation.verificationExpiresAt)
            },
        )
    }

    fun createPersonAttestation(
        recipient: ExchangeRecipient,
        prepared: PreparedPersonResolution,
        verifiedAt: Instant = Instant.now(),
    ): ExchangeRecipientAttestation
    {
        val validation = prepared.validation
        require(recipient.targetOrganizationId == validation.targetOrganization.id) {
            "Recipient target organization does not match the trusted member"
        }
        require(repository.findByExchangeRecipientId(recipient.id) == null) {
            "Recipient attestation already exists"
        }
        return repository.save(
            ExchangeRecipientAttestation().apply {
                exchangeRecipientId = recipient.id
                relationshipId = validation.relationship.id
                callerOrganizationId = validation.callerOrganization.id
                targetOrganizationId = validation.targetOrganization.id
                senderPolicyRevision = validation.senderPolicy.revision
                targetPolicyRevision = validation.targetPolicy.revision
                subjectType = ExchangeRecipientAttestationSubjectType.PERSON
                subjectAppUserId = prepared.appUser.id
                subjectMembershipId = prepared.membership.id
                invitedEmailSnapshot = prepared.resolution.normalizedEmail
                displayNameSnapshot = prepared.resolution.displayNameSnapshot
                organizationNameSnapshot = validation.targetOrganization.name
                this.verifiedAt = Timestamp.from(verifiedAt)
                verificationExpiresAt = Timestamp.from(validation.verificationExpiresAt)
            },
        )
    }

    fun findForRecipient(exchangeRecipientId: UUID): ExchangeRecipientAttestation? =
        repository.findByExchangeRecipientId(exchangeRecipientId)

    fun findForDirectShare(directShareId: UUID): ExchangeRecipientAttestation?
    {
        val recipient = recipientServiceProvider.get().findByDirectShareId(directShareId) ?: return null
        return findForRecipient(recipient.id)
    }

    fun findForRelationship(relationshipId: UUID): List<ExchangeRecipientAttestation> =
        repository.findByRelationshipId(relationshipId)

    fun directShareId(attestation: ExchangeRecipientAttestation): UUID? =
        recipientServiceProvider.get().getById(attestation.exchangeRecipientId)?.directShareId

    fun markAcceptanceVerified(attestation: ExchangeRecipientAttestation, verifiedAt: Instant = Instant.now())
    {
        attestation.acceptanceVerifiedAt = Timestamp.from(verifiedAt)
        repository.update(attestation)
    }

    /** Removes the attestation for a recipient, if one exists. Used when a recipient binding is replaced. */
    fun deleteForRecipient(exchangeRecipientId: UUID)
    {
        repository.findByExchangeRecipientId(exchangeRecipientId)?.let { repository.delete(it) }
    }
}
