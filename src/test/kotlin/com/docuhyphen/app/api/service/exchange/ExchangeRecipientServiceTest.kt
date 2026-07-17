package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.exception.OrganizationTrustNotFoundException
import com.docuhyphen.app.api.model.entity.ExchangeRecipient
import com.docuhyphen.app.api.model.entity.ExchangeRecipientAttestation
import com.docuhyphen.app.api.model.entity.ExchangeRecipientAcceptanceStatus
import com.docuhyphen.app.api.model.entity.ExchangeRecipientPurpose
import com.docuhyphen.app.api.model.entity.ExchangeRecipientSelectionType
import com.docuhyphen.app.api.model.entity.ExchangeShareRoleName
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.entity.Share
import com.docuhyphen.app.api.model.entity.ShareSource
import com.docuhyphen.app.api.repository.ExchangeRecipientRepository
import com.docuhyphen.app.api.service.organization.OrganizationGroupService
import com.docuhyphen.app.api.service.organization.TrustedRecipientValidationService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class ExchangeRecipientServiceTest
{
    private val exchangeId = UUID.randomUUID()
    private val repository = mock<ExchangeRecipientRepository>()
    private val shareService = mock<ShareService>()
    private val organizationGroupService = mock<OrganizationGroupService>()
    private val attestationService = mock<ExchangeRecipientAttestationService>()
    private val validationService = mock<TrustedRecipientValidationService>()
    private val service = ExchangeRecipientService(
        repository,
        shareService,
        organizationGroupService,
        attestationService,
        validationService,
    )

    @Test
    fun `creates one primary recipient binding for a direct non-owner Share`()
    {
        val share = directShare(PrincipalKind.USER, UUID.randomUUID())
        whenever(repository.findPrimary(exchangeId)).thenReturn(null)
        whenever(repository.save(any())).thenAnswer { it.getArgument(0) }

        val recipient = service.createBinding(
            exchangeId = exchangeId,
            directShare = share,
            purpose = ExchangeRecipientPurpose.PRIMARY,
            selectionType = ExchangeRecipientSelectionType.REGISTERED_USER,
            targetOrganizationId = null,
            acceptanceStatus = ExchangeRecipientAcceptanceStatus.PENDING,
        )

        assertEquals(share.id, recipient.directShareId)
        assertEquals(ExchangeRecipientPurpose.PRIMARY, recipient.purpose)
        verify(repository).save(recipient)
    }

    @Test
    fun `returns the existing binding when an idempotent grant reuses its direct Share`()
    {
        val share = directShare(PrincipalKind.USER, UUID.randomUUID())
        val existing = pendingRecipient(share.id)
        whenever(repository.findByDirectShareId(share.id)).thenReturn(existing)

        val recipient = service.createBinding(
            exchangeId = exchangeId,
            directShare = share,
            purpose = ExchangeRecipientPurpose.PRIMARY,
            selectionType = ExchangeRecipientSelectionType.REGISTERED_USER,
            targetOrganizationId = null,
            acceptanceStatus = ExchangeRecipientAcceptanceStatus.PENDING,
        )

        assertEquals(existing, recipient)
    }

    @Test
    fun `rejects an owner Share as a recipient binding`()
    {
        val ownerShare = directShare(PrincipalKind.USER, UUID.randomUUID()).apply {
            roleName = ExchangeShareRoleName.OWNER
        }

        assertThrows(IllegalArgumentException::class.java) {
            service.createBinding(
                exchangeId,
                ownerShare,
                ExchangeRecipientPurpose.PRIMARY,
                ExchangeRecipientSelectionType.REGISTERED_USER,
                null,
                ExchangeRecipientAcceptanceStatus.PENDING,
            )
        }
    }

    @Test
    fun `records acceptance only for the bound primary user`()
    {
        val appUserId = UUID.randomUUID()
        val share = directShare(PrincipalKind.USER, appUserId)
        val recipient = pendingRecipient(share.id)
        whenever(repository.findPrimaryForUpdate(exchangeId)).thenReturn(recipient)
        whenever(shareService.getById(share.id)).thenReturn(share)
        whenever(repository.update(any())).thenAnswer { it.getArgument(0) }

        val updated = service.recordPrimaryDecision(exchangeId, appUserId, accepted = true)

        assertEquals(ExchangeRecipientAcceptanceStatus.ACCEPTED, updated.acceptanceStatus)
        assertEquals(appUserId, updated.acceptedOrRejectedByAppUserId)
    }

    @Test
    fun `rejects a different user from deciding for the primary recipient`()
    {
        val share = directShare(PrincipalKind.USER, UUID.randomUUID())
        whenever(repository.findPrimaryForUpdate(exchangeId)).thenReturn(pendingRecipient(share.id))
        whenever(shareService.getById(share.id)).thenReturn(share)

        assertThrows(IllegalArgumentException::class.java) {
            service.recordPrimaryDecision(exchangeId, UUID.randomUUID(), accepted = true)
        }
    }

    @Test
    fun `allows an active group manager to decide for a group recipient`()
    {
        val groupId = UUID.randomUUID()
        val managerId = UUID.randomUUID()
        val share = directShare(PrincipalKind.PRINCIPAL_GROUP, groupId)
        whenever(repository.findPrimaryForUpdate(exchangeId)).thenReturn(pendingRecipient(share.id))
        whenever(shareService.getById(share.id)).thenReturn(share)
        whenever(organizationGroupService.isActiveOwnerOrManager(groupId, managerId)).thenReturn(true)
        whenever(repository.update(any())).thenAnswer { it.getArgument(0) }

        val updated = service.recordPrimaryDecision(exchangeId, managerId, accepted = false)

        assertEquals(ExchangeRecipientAcceptanceStatus.REJECTED, updated.acceptanceStatus)
    }

    @Test
    fun `trusted group manager acceptance revalidates trust and records verification`()
    {
        val groupId = UUID.randomUUID()
        val managerId = UUID.randomUUID()
        val share = directShare(PrincipalKind.PRINCIPAL_GROUP, groupId)
        val recipient = pendingRecipient(share.id).apply {
            selectionType = ExchangeRecipientSelectionType.TRUSTED_GROUP
        }
        val attestation = ExchangeRecipientAttestation()
        whenever(repository.findPrimaryForUpdate(exchangeId)).thenReturn(recipient)
        whenever(shareService.getById(share.id)).thenReturn(share)
        whenever(organizationGroupService.isActiveOwnerOrManager(groupId, managerId)).thenReturn(true)
        whenever(attestationService.findForRecipient(recipient.id)).thenReturn(attestation)
        whenever(validationService.validateGroupAttestation(eq(attestation), any())).thenReturn(mock())
        whenever(repository.update(any())).thenAnswer { it.getArgument(0) }

        val updated = service.recordPrimaryDecision(exchangeId, managerId, accepted = true)

        assertEquals(ExchangeRecipientAcceptanceStatus.ACCEPTED, updated.acceptanceStatus)
        verify(validationService).validateGroupAttestation(eq(attestation), any())
        verify(attestationService).markAcceptanceVerified(eq(attestation), any())
    }

    @Test
    fun `trusted group rejection is permitted even when activation eligibility has lapsed`()
    {
        val groupId = UUID.randomUUID()
        val managerId = UUID.randomUUID()
        val share = directShare(PrincipalKind.PRINCIPAL_GROUP, groupId)
        val recipient = pendingRecipient(share.id).apply {
            selectionType = ExchangeRecipientSelectionType.TRUSTED_GROUP
        }
        whenever(repository.findPrimaryForUpdate(exchangeId)).thenReturn(recipient)
        whenever(shareService.getById(share.id)).thenReturn(share)
        whenever(organizationGroupService.isActiveOwnerOrManager(groupId, managerId)).thenReturn(true)
        whenever(repository.update(any())).thenAnswer { it.getArgument(0) }

        val updated = service.recordPrimaryDecision(exchangeId, managerId, accepted = false)

        assertEquals(ExchangeRecipientAcceptanceStatus.REJECTED, updated.acceptanceStatus)
        verify(validationService, never()).validateGroupAttestation(any(), any())
        verify(attestationService, never()).markAcceptanceVerified(any(), any())
    }

    @Test
    fun `trusted group acceptance fails closed when trust eligibility has lapsed`()
    {
        val groupId = UUID.randomUUID()
        val managerId = UUID.randomUUID()
        val share = directShare(PrincipalKind.PRINCIPAL_GROUP, groupId)
        val recipient = pendingRecipient(share.id).apply {
            selectionType = ExchangeRecipientSelectionType.TRUSTED_GROUP
        }
        val attestation = ExchangeRecipientAttestation()
        whenever(repository.findPrimaryForUpdate(exchangeId)).thenReturn(recipient)
        whenever(shareService.getById(share.id)).thenReturn(share)
        whenever(organizationGroupService.isActiveOwnerOrManager(groupId, managerId)).thenReturn(true)
        whenever(attestationService.findForRecipient(recipient.id)).thenReturn(attestation)
        whenever(validationService.validateGroupAttestation(eq(attestation), any()))
            .thenThrow(OrganizationTrustNotFoundException("Published trusted group is unavailable"))

        assertThrows(OrganizationTrustNotFoundException::class.java) {
            service.recordPrimaryDecision(exchangeId, managerId, accepted = true)
        }
        verify(attestationService, never()).markAcceptanceVerified(any(), any())
        verify(repository, never()).update(any())
    }

    @Test
    fun `trusted group member without manager role cannot decide`()
    {
        val groupId = UUID.randomUUID()
        val memberId = UUID.randomUUID()
        val share = directShare(PrincipalKind.PRINCIPAL_GROUP, groupId)
        val recipient = pendingRecipient(share.id).apply {
            selectionType = ExchangeRecipientSelectionType.TRUSTED_GROUP
        }
        whenever(repository.findPrimaryForUpdate(exchangeId)).thenReturn(recipient)
        whenever(shareService.getById(share.id)).thenReturn(share)
        whenever(organizationGroupService.isActiveOwnerOrManager(groupId, memberId)).thenReturn(false)

        assertThrows(IllegalArgumentException::class.java) {
            service.recordPrimaryDecision(exchangeId, memberId, accepted = true)
        }
        verify(validationService, never()).validateGroupAttestation(any(), any())
    }

    @Test
    fun `trusted person acceptance revalidates the attestation and records verification`()
    {
        val appUserId = UUID.randomUUID()
        val share = directShare(PrincipalKind.USER, appUserId)
        val recipient = pendingRecipient(share.id).apply {
            selectionType = ExchangeRecipientSelectionType.TRUSTED_PERSON
        }
        val attestation = ExchangeRecipientAttestation()
        whenever(repository.findPrimaryForUpdate(exchangeId)).thenReturn(recipient)
        whenever(shareService.getById(share.id)).thenReturn(share)
        whenever(attestationService.findForRecipient(recipient.id)).thenReturn(attestation)
        whenever(validationService.validatePersonAttestation(eq(attestation), any())).thenReturn(mock())
        whenever(repository.update(any())).thenAnswer { it.getArgument(0) }

        val updated = service.recordPrimaryDecision(exchangeId, appUserId, accepted = true)

        assertEquals(ExchangeRecipientAcceptanceStatus.ACCEPTED, updated.acceptanceStatus)
        verify(validationService).validatePersonAttestation(eq(attestation), any())
        verify(attestationService).markAcceptanceVerified(eq(attestation), any())
    }

    @Test
    fun `trusted person acceptance fails closed when the attested membership is no longer eligible`()
    {
        val appUserId = UUID.randomUUID()
        val share = directShare(PrincipalKind.USER, appUserId)
        val recipient = pendingRecipient(share.id).apply {
            selectionType = ExchangeRecipientSelectionType.TRUSTED_PERSON
        }
        val attestation = ExchangeRecipientAttestation()
        whenever(repository.findPrimaryForUpdate(exchangeId)).thenReturn(recipient)
        whenever(shareService.getById(share.id)).thenReturn(share)
        whenever(attestationService.findForRecipient(recipient.id)).thenReturn(attestation)
        whenever(validationService.validatePersonAttestation(eq(attestation), any()))
            .thenThrow(OrganizationTrustNotFoundException("Trusted member is unavailable"))

        assertThrows(OrganizationTrustNotFoundException::class.java) {
            service.recordPrimaryDecision(exchangeId, appUserId, accepted = true)
        }
        verify(attestationService, never()).markAcceptanceVerified(any(), any())
        verify(repository, never()).update(any())
    }

    @Test
    fun `trusted person rejection is permitted without revalidating the attestation`()
    {
        val appUserId = UUID.randomUUID()
        val share = directShare(PrincipalKind.USER, appUserId)
        val recipient = pendingRecipient(share.id).apply {
            selectionType = ExchangeRecipientSelectionType.TRUSTED_PERSON
        }
        whenever(repository.findPrimaryForUpdate(exchangeId)).thenReturn(recipient)
        whenever(shareService.getById(share.id)).thenReturn(share)
        whenever(repository.update(any())).thenAnswer { it.getArgument(0) }

        val updated = service.recordPrimaryDecision(exchangeId, appUserId, accepted = false)

        assertEquals(ExchangeRecipientAcceptanceStatus.REJECTED, updated.acceptanceStatus)
        verify(validationService, never()).validatePersonAttestation(any(), any())
    }

    @Test
    fun `records no-auth acceptance only for an external email primary recipient`()
    {
        val recipientUserId = UUID.randomUUID()
        val share = directShare(PrincipalKind.USER, recipientUserId)
        val recipient = pendingRecipient(share.id).apply {
            selectionType = ExchangeRecipientSelectionType.EXTERNAL_EMAIL
        }
        whenever(repository.findPrimaryForUpdate(exchangeId)).thenReturn(recipient)
        whenever(shareService.getById(share.id)).thenReturn(share)
        whenever(repository.update(any())).thenAnswer { it.getArgument(0) }

        val updated = service.recordExternalEmailPrimaryDecision(exchangeId, accepted = true)

        assertEquals(ExchangeRecipientAcceptanceStatus.ACCEPTED, updated.acceptanceStatus)
        assertEquals(recipientUserId, updated.acceptedOrRejectedByAppUserId)
    }

    private fun directShare(kind: PrincipalKind, principalId: UUID) = Share().apply {
        resourceType = ResourceType.EXCHANGE
        resourceId = exchangeId
        principalKind = kind
        this.principalId = principalId
        roleName = ExchangeShareRoleName.VIEWER
        source = ShareSource.DIRECT
        status = com.docuhyphen.app.api.model.entity.ShareStatus.ACTIVE
    }

    private fun pendingRecipient(shareId: UUID) = ExchangeRecipient().apply {
        exchangeId = this@ExchangeRecipientServiceTest.exchangeId
        directShareId = shareId
        purpose = ExchangeRecipientPurpose.PRIMARY
        selectionType = ExchangeRecipientSelectionType.REGISTERED_USER
        acceptanceStatus = ExchangeRecipientAcceptanceStatus.PENDING
    }
}
