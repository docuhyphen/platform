package com.docuhyphen.app.api.service.exchange.permutation

import com.docuhyphen.app.api.exception.OrganizationTrustNotFoundException
import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.ExchangeRecipient
import com.docuhyphen.app.api.model.entity.ExchangeRecipientAcceptanceStatus
import com.docuhyphen.app.api.model.entity.ExchangeRecipientAttestation
import com.docuhyphen.app.api.model.entity.ExchangeRecipientPurpose
import com.docuhyphen.app.api.model.entity.ExchangeRecipientSelectionType
import com.docuhyphen.app.api.model.entity.ExchangeShareRoleName
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.PrincipalGroupMember
import com.docuhyphen.app.api.model.entity.PrincipalGroupRoleName
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.entity.Share
import com.docuhyphen.app.api.model.entity.ShareSource
import com.docuhyphen.app.api.model.entity.ShareStatus
import com.docuhyphen.app.api.repository.exchange.ExchangeRecipientRepository
import com.docuhyphen.app.api.service.exchange.ExchangeRecipientAttestationService
import com.docuhyphen.app.api.service.exchange.ExchangeRecipientService
import com.docuhyphen.app.api.service.exchange.ExternalEmailAcceptancePolicyService
import com.docuhyphen.app.api.service.exchange.ShareService
import com.docuhyphen.app.api.service.organization.OrganizationGroupService
import com.docuhyphen.app.api.service.organization.TrustedGroupValidation
import com.docuhyphen.app.api.service.organization.TrustedPersonAcceptanceValidation
import com.docuhyphen.app.api.service.organization.TrustedRecipientAuditService
import com.docuhyphen.app.api.service.organization.TrustedRecipientValidationService
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

internal class ExchangeRecipientDecisionFixture(
    val selectionType: ExchangeRecipientSelectionType = ExchangeRecipientSelectionType.REGISTERED_USER,
    val purpose: ExchangeRecipientPurpose = ExchangeRecipientPurpose.PRIMARY,
    principalKind: PrincipalKind = PrincipalKind.USER,
    val principalId: UUID = UUID.randomUUID(),
    groupRole: PrincipalGroupRoleName? = null,
    val trustLifecycle: AcceptanceTrustLifecycle = AcceptanceTrustLifecycle.ACTIVE,
    val initialAcceptanceStatus: ExchangeRecipientAcceptanceStatus = ExchangeRecipientAcceptanceStatus.PENDING,
    val initialShareStatus: ShareStatus = ShareStatus.PENDING_APPROVAL,
)
{
    val exchangeId: UUID = UUID.randomUUID()
    val ownerOrganizationId: UUID = UUID.randomUUID()
    val exchange: Exchange = Exchange().apply {
        id = exchangeId
        ownerOrganizationId = this@ExchangeRecipientDecisionFixture.ownerOrganizationId
    }
    val share: Share = Share().apply {
        resourceType = ResourceType.EXCHANGE
        resourceId = exchangeId
        this.principalKind = principalKind
        this.principalId = this@ExchangeRecipientDecisionFixture.principalId
        roleName = ExchangeShareRoleName.VIEWER
        source = ShareSource.DIRECT
        status = initialShareStatus
    }
    val recipient: ExchangeRecipient = ExchangeRecipient().apply {
        exchangeId = this@ExchangeRecipientDecisionFixture.exchangeId
        directShareId = share.id
        purpose = this@ExchangeRecipientDecisionFixture.purpose
        selectionType = this@ExchangeRecipientDecisionFixture.selectionType
        acceptanceStatus = initialAcceptanceStatus
        targetOrganizationId = this@ExchangeRecipientDecisionFixture.ownerOrganizationId
    }
    val groupMember: PrincipalGroupMember? = groupRole?.let { role ->
        PrincipalGroupMember().apply {
            principalGroupId = share.principalId
            this.principalKind = PrincipalKind.USER
            this.principalId = this@ExchangeRecipientDecisionFixture.principalId
            this.groupRole = role
        }
    }
    val repository: ExchangeRecipientRepository = mock()
    val shareService: ShareService = mock()
    val organizationGroupService: OrganizationGroupService = mock()
    val attestationService: ExchangeRecipientAttestationService = mock()
    val validationService: TrustedRecipientValidationService = mock()
    val externalEmailPolicyService: ExternalEmailAcceptancePolicyService = mock()
    val auditService: TrustedRecipientAuditService = mock()
    val service: ExchangeRecipientService = ExchangeRecipientService(
        repository,
        shareService,
        organizationGroupService,
        attestationService,
        validationService,
        externalEmailPolicyService,
        auditService,
    )

    init
    {
        whenever(repository.findPrimaryForUpdate(exchangeId)).thenReturn(recipient)
        whenever(repository.findByIdForUpdate(recipient.id)).thenReturn(recipient)
        whenever(repository.save(any())).thenAnswer { it.getArgument(0) }
        whenever(repository.update(any())).thenAnswer { it.getArgument(0) }
        whenever(shareService.getById(share.id)).thenReturn(share)
        whenever(organizationGroupService.isActiveDecisionMaker(share.principalId, principalId))
            .thenReturn(
                groupMember?.groupRole == PrincipalGroupRoleName.OWNER ||
                    groupMember?.groupRole == PrincipalGroupRoleName.MANAGER,
            )

        if (selectionType == ExchangeRecipientSelectionType.TRUSTED_GROUP ||
            selectionType == ExchangeRecipientSelectionType.TRUSTED_PERSON)
        {
            val attestation = ExchangeRecipientAttestation().apply {
                callerOrganizationId = ownerOrganizationId
                targetOrganizationId = UUID.randomUUID()
            }
            whenever(attestationService.findForRecipient(recipient.id)).thenReturn(attestation)
            if (trustLifecycle == AcceptanceTrustLifecycle.ACTIVE)
            {
                whenever(validationService.validateGroupAttestation(eq(attestation), any()))
                    .thenReturn(mock<TrustedGroupValidation>())
                whenever(validationService.validatePersonAttestation(eq(attestation), any()))
                    .thenReturn(mock<TrustedPersonAcceptanceValidation>())
            }
            else
            {
                val message = when (trustLifecycle)
                {
                    AcceptanceTrustLifecycle.SUSPENDED -> "Trust relationship is suspended"
                    AcceptanceTrustLifecycle.ENDED -> "Trust relationship has ended"
                    AcceptanceTrustLifecycle.ACTIVE -> error("Active trust was handled above")
                }
                whenever(validationService.validateGroupAttestation(eq(attestation), any()))
                    .thenThrow(OrganizationTrustNotFoundException(message))
                whenever(validationService.validatePersonAttestation(eq(attestation), any()))
                    .thenThrow(OrganizationTrustNotFoundException(message))
            }
        }
    }

    fun recordPrimary(accepted: Boolean, actorId: UUID = principalId): ExchangeRecipient =
        service.recordPrimaryDecision(exchange, actorId, accepted)

    fun recordParticipant(accepted: Boolean, actorId: UUID = principalId): ExchangeRecipient =
        service.recordTrustedParticipantDecision(recipient.id, actorId, accepted)

    fun verifyParticipantActivated()
    {
        verify(shareService).activate(share.id)
    }

    fun verifyParticipantNotActivated()
    {
        verify(shareService, never()).activate(any())
    }

    fun verifyPrimaryLockCount(expected: Int)
    {
        verify(repository, times(expected)).findPrimaryForUpdate(exchangeId)
    }

    fun verifyParticipantLockCount(expected: Int)
    {
        verify(repository, times(expected)).findByIdForUpdate(recipient.id)
    }

    fun verifyDecisionUpdateCount(expected: Int)
    {
        verify(repository, times(expected)).update(any())
    }

    fun verifyParticipantNotRevoked()
    {
        verify(shareService, never()).revoke(any(), any(), any())
    }
}

internal enum class AcceptanceTrustLifecycle
{
    ACTIVE,
    SUSPENDED,
    ENDED,
}
