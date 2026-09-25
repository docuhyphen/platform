package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.exception.ExchangeRecipientEligibilityException
import com.docuhyphen.app.api.exception.OrganizationTrustNotFoundException
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.repository.exchange.ExchangeRecipientRepository
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.organization.OrganizationGroupService
import com.docuhyphen.app.api.service.organization.TrustedRecipientAuditService
import com.docuhyphen.app.api.service.organization.TrustedRecipientValidationService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.*

class ExchangeRecipientServiceTest
{
    private val exchangeId = UUID.randomUUID()
    private val ownerOrganizationId = UUID.randomUUID()
    private val repository = mock<ExchangeRecipientRepository>()
    private val shareService = mock<ShareService>()
    private val organizationGroupService = mock<OrganizationGroupService>()
    private val attestationService = mock<ExchangeRecipientAttestationService>()
    private val validationService = mock<TrustedRecipientValidationService>()
    private val externalEmailAcceptancePolicyService = mock<ExternalEmailAcceptancePolicyService>()
    private val trustedRecipientAuditService = mock<TrustedRecipientAuditService>()
    private val exchange = Exchange().apply {
        id = exchangeId
        this.ownerOrganizationId = this@ExchangeRecipientServiceTest.ownerOrganizationId
    }
    private val service = ExchangeRecipientService(
        repository,
        shareService,
        organizationGroupService,
        attestationService,
        validationService,
        externalEmailAcceptancePolicyService,
        trustedRecipientAuditService,
    )

    @Test
    fun `pending trusted participant invitations use the scoped repository query`()
    {
        val appUserId = UUID.randomUUID()
        val invitations = listOf(
            pendingRecipient(UUID.randomUUID()).apply {
                purpose = ExchangeRecipientPurpose.PARTICIPANT
                selectionType = ExchangeRecipientSelectionType.TRUSTED_PERSON
            },
        )
        whenever(repository.findPendingTrustedParticipantsFor(appUserId)).thenReturn(invitations)

        assertEquals(invitations, service.pendingTrustedParticipantInvitationsFor(appUserId))

        verify(repository).findPendingTrustedParticipantsFor(appUserId)
        verify(shareService, never()).getById(any())
    }

    @Test
    fun `request party recipient linkage requires the direct Share principal to match`()
    {
        val recipient = pendingRecipient(UUID.randomUUID()).apply {
            acceptanceStatus = ExchangeRecipientAcceptanceStatus.NOT_REQUIRED
        }
        val share = directShare(PrincipalKind.USER, UUID.randomUUID())
        whenever(repository.findById(recipient.id)).thenReturn(recipient)
        whenever(shareService.getById(recipient.directShareId)).thenReturn(share)

        assertThrows(IllegalArgumentException::class.java) {
            service.requireAssignablePartyRecipient(
                recipient.id,
                exchangeId,
                PrincipalRef.user(UUID.randomUUID()),
            )
        }
        verify(attestationService, never()).findForRecipient(any())
    }

    @Test
    fun `request party recipient linkage refuses pending trusted group invitations`()
    {
        val groupId = UUID.randomUUID()
        val share = directShare(PrincipalKind.PRINCIPAL_GROUP, groupId).apply {
            status = com.docuhyphen.app.api.model.entity.ShareStatus.PENDING_APPROVAL
        }
        val recipient = pendingRecipient(share.id).apply {
            purpose = ExchangeRecipientPurpose.PARTICIPANT
            selectionType = ExchangeRecipientSelectionType.TRUSTED_GROUP
        }
        whenever(repository.findById(recipient.id)).thenReturn(recipient)
        whenever(shareService.getById(share.id)).thenReturn(share)

        assertThrows(IllegalArgumentException::class.java) {
            service.requireAssignablePartyRecipient(
                recipient.id,
                exchangeId,
                PrincipalRef.group(groupId),
            )
        }
        verify(attestationService, never()).findForRecipient(any())
    }

    @Test
    fun `request party recipient linkage revalidates accepted trusted group and reconciles access`()
    {
        val groupId = UUID.randomUUID()
        val share = directShare(PrincipalKind.PRINCIPAL_GROUP, groupId)
        val recipient = pendingRecipient(share.id).apply {
            purpose = ExchangeRecipientPurpose.PARTICIPANT
            selectionType = ExchangeRecipientSelectionType.TRUSTED_GROUP
            acceptanceStatus = ExchangeRecipientAcceptanceStatus.ACCEPTED
        }
        val attestation = trustedAttestation()
        whenever(repository.findById(recipient.id)).thenReturn(recipient)
        whenever(shareService.getById(share.id)).thenReturn(share)
        whenever(attestationService.findForRecipient(recipient.id)).thenReturn(attestation)
        whenever(validationService.validateGroupAttestation(eq(attestation), any())).thenReturn(mock())

        val result = service.requireAssignablePartyRecipient(
            recipient.id,
            exchangeId,
            PrincipalRef.group(groupId),
        )

        assertEquals(recipient, result)
        verify(validationService).validateGroupAttestation(eq(attestation), any())
        verify(shareService).reconcileGroupShare(share.id)
    }

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
    fun `pending primary user can view the invitation while its Share is inactive`()
    {
        val appUserId = UUID.randomUUID()
        val share = directShare(PrincipalKind.USER, appUserId).apply {
            status = com.docuhyphen.app.api.model.entity.ShareStatus.PENDING_APPROVAL
        }
        whenever(repository.findPrimary(exchangeId)).thenReturn(pendingRecipient(share.id))
        whenever(shareService.getById(share.id)).thenReturn(share)

        assertTrue(service.canViewPendingPrimaryInvitation(exchangeId, appUserId))
        assertFalse(service.canViewPendingPrimaryInvitation(exchangeId, UUID.randomUUID()))
    }

    @Test
    fun `pending participant cannot use primary invitation visibility`()
    {
        val appUserId = UUID.randomUUID()
        val share = directShare(PrincipalKind.USER, appUserId).apply {
            status = com.docuhyphen.app.api.model.entity.ShareStatus.PENDING_APPROVAL
        }
        val participant = pendingRecipient(share.id).apply { purpose = ExchangeRecipientPurpose.PARTICIPANT }
        whenever(repository.findPrimary(exchangeId)).thenReturn(null)
        whenever(repository.findByDirectShareId(share.id)).thenReturn(participant)

        assertFalse(service.canViewPendingPrimaryInvitation(exchangeId, appUserId))
    }

    @Test
    fun `pending primary group is visible to an active owner or manager (ORG group) or decision maker (Personal Group)`()
    {
        val groupId = UUID.randomUUID()
        val decisionMakerId = UUID.randomUUID()
        val share = directShare(PrincipalKind.PRINCIPAL_GROUP, groupId).apply {
            status = com.docuhyphen.app.api.model.entity.ShareStatus.PENDING_APPROVAL
        }
        whenever(repository.findPrimary(exchangeId)).thenReturn(pendingRecipient(share.id))
        whenever(shareService.getById(share.id)).thenReturn(share)
        whenever(organizationGroupService.isActiveDecisionMaker(groupId, decisionMakerId)).thenReturn(true)

        assertTrue(service.canViewPendingPrimaryInvitation(exchangeId, decisionMakerId))
    }

    @Test
    fun `pending primary group stays hidden from members former members and unrelated users`()
    {
        val groupId = UUID.randomUUID()
        val share = directShare(PrincipalKind.PRINCIPAL_GROUP, groupId).apply {
            status = com.docuhyphen.app.api.model.entity.ShareStatus.PENDING_APPROVAL
        }
        whenever(repository.findPrimary(exchangeId)).thenReturn(pendingRecipient(share.id))
        whenever(shareService.getById(share.id)).thenReturn(share)

        listOf(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()).forEach { ineligibleUserId ->
            whenever(organizationGroupService.isActiveDecisionMaker(groupId, ineligibleUserId)).thenReturn(false)
            assertFalse(service.canViewPendingPrimaryInvitation(exchangeId, ineligibleUserId))
        }
    }

    @Test
    fun `rejects an owner Share as a recipient binding`()
    {
        val ownerShare = directShare(PrincipalKind.USER, UUID.randomUUID()).apply {
            roleName = ExchangeShareRoleName.OWNER.name
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

        val updated = service.recordPrimaryDecision(exchange, appUserId, accepted = true)

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
            service.recordPrimaryDecision(exchange, UUID.randomUUID(), accepted = true)
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
        whenever(organizationGroupService.isActiveDecisionMaker(groupId, managerId)).thenReturn(true)
        whenever(repository.update(any())).thenAnswer { it.getArgument(0) }

        val updated = service.recordPrimaryDecision(exchange, managerId, accepted = false)

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
        val attestation = trustedAttestation()
        whenever(repository.findPrimaryForUpdate(exchangeId)).thenReturn(recipient)
        whenever(shareService.getById(share.id)).thenReturn(share)
        whenever(organizationGroupService.isActiveDecisionMaker(groupId, managerId)).thenReturn(true)
        whenever(attestationService.findForRecipient(recipient.id)).thenReturn(attestation)
        whenever(validationService.validateGroupAttestation(eq(attestation), any())).thenReturn(mock())
        whenever(repository.update(any())).thenAnswer { it.getArgument(0) }

        val updated = service.recordPrimaryDecision(exchange, managerId, accepted = true)

        assertEquals(ExchangeRecipientAcceptanceStatus.ACCEPTED, updated.acceptanceStatus)
        verify(validationService).validateGroupAttestation(eq(attestation), any())
        verify(attestationService).markAcceptanceVerified(eq(attestation), any())
        verify(trustedRecipientAuditService).recordAcceptanceAllowed(
            managerId,
            ownerOrganizationId,
            exchangeId,
            recipient,
        )
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
        whenever(organizationGroupService.isActiveDecisionMaker(groupId, managerId)).thenReturn(true)
        whenever(repository.update(any())).thenAnswer { it.getArgument(0) }

        val updated = service.recordPrimaryDecision(exchange, managerId, accepted = false)

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
        val attestation = trustedAttestation()
        whenever(repository.findPrimaryForUpdate(exchangeId)).thenReturn(recipient)
        whenever(shareService.getById(share.id)).thenReturn(share)
        whenever(organizationGroupService.isActiveDecisionMaker(groupId, managerId)).thenReturn(true)
        whenever(attestationService.findForRecipient(recipient.id)).thenReturn(attestation)
        whenever(validationService.validateGroupAttestation(eq(attestation), any()))
            .thenThrow(OrganizationTrustNotFoundException("Published trusted group is unavailable"))

        assertThrows(OrganizationTrustNotFoundException::class.java) {
            service.recordPrimaryDecision(exchange, managerId, accepted = true)
        }
        verify(attestationService, never()).markAcceptanceVerified(any(), any())
        verify(repository, never()).update(any())
        verify(trustedRecipientAuditService).recordAcceptanceDenied(
            managerId,
            ownerOrganizationId,
            exchangeId,
            recipient,
        )
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
        whenever(organizationGroupService.isActiveDecisionMaker(groupId, memberId)).thenReturn(false)

        assertThrows(IllegalArgumentException::class.java) {
            service.recordPrimaryDecision(exchange, memberId, accepted = true)
        }
        verify(validationService, never()).validateGroupAttestation(any(), any())
        verify(trustedRecipientAuditService).recordAcceptanceDenied(
            memberId,
            ownerOrganizationId,
            exchangeId,
            recipient,
        )
    }

    @Test
    fun `trusted person acceptance revalidates the attestation and records verification`()
    {
        val appUserId = UUID.randomUUID()
        val share = directShare(PrincipalKind.USER, appUserId)
        val recipient = pendingRecipient(share.id).apply {
            selectionType = ExchangeRecipientSelectionType.TRUSTED_PERSON
        }
        val attestation = trustedAttestation()
        whenever(repository.findPrimaryForUpdate(exchangeId)).thenReturn(recipient)
        whenever(shareService.getById(share.id)).thenReturn(share)
        whenever(attestationService.findForRecipient(recipient.id)).thenReturn(attestation)
        whenever(validationService.validatePersonAttestation(eq(attestation), any())).thenReturn(mock())
        whenever(repository.update(any())).thenAnswer { it.getArgument(0) }

        val updated = service.recordPrimaryDecision(exchange, appUserId, accepted = true)

        assertEquals(ExchangeRecipientAcceptanceStatus.ACCEPTED, updated.acceptanceStatus)
        verify(validationService).validatePersonAttestation(eq(attestation), any())
        verify(attestationService).markAcceptanceVerified(eq(attestation), any())
        verify(trustedRecipientAuditService).recordAcceptanceAllowed(
            appUserId,
            ownerOrganizationId,
            exchangeId,
            recipient,
        )
    }

    @Test
    fun `trusted person acceptance fails closed when the attested membership is no longer eligible`()
    {
        val appUserId = UUID.randomUUID()
        val share = directShare(PrincipalKind.USER, appUserId)
        val recipient = pendingRecipient(share.id).apply {
            selectionType = ExchangeRecipientSelectionType.TRUSTED_PERSON
        }
        val attestation = trustedAttestation()
        whenever(repository.findPrimaryForUpdate(exchangeId)).thenReturn(recipient)
        whenever(shareService.getById(share.id)).thenReturn(share)
        whenever(attestationService.findForRecipient(recipient.id)).thenReturn(attestation)
        whenever(validationService.validatePersonAttestation(eq(attestation), any()))
            .thenThrow(OrganizationTrustNotFoundException("Trusted member is unavailable"))

        assertThrows(OrganizationTrustNotFoundException::class.java) {
            service.recordPrimaryDecision(exchange, appUserId, accepted = true)
        }
        verify(attestationService, never()).markAcceptanceVerified(any(), any())
        verify(repository, never()).update(any())
        verify(trustedRecipientAuditService).recordAcceptanceDenied(
            appUserId,
            ownerOrganizationId,
            exchangeId,
            recipient,
        )
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

        val updated = service.recordPrimaryDecision(exchange, appUserId, accepted = false)

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

        val updated = service.recordExternalEmailPrimaryDecision(exchange, accepted = true)

        assertEquals(ExchangeRecipientAcceptanceStatus.ACCEPTED, updated.acceptanceStatus)
        assertEquals(recipientUserId, updated.acceptedOrRejectedByAppUserId)
        verify(externalEmailAcceptancePolicyService).validate(exchange, share, authenticatedAppUserId = null)
    }

    @Test
    fun `participant principal cannot use the no-auth primary recipient decision path`()
    {
        val share = directShare(PrincipalKind.PARTICIPANT, UUID.randomUUID())
        val recipient = pendingRecipient(share.id).apply {
            selectionType = ExchangeRecipientSelectionType.EXTERNAL_EMAIL
        }
        whenever(repository.findPrimaryForUpdate(exchangeId)).thenReturn(recipient)
        whenever(shareService.getById(share.id)).thenReturn(share)

        assertThrows(IllegalArgumentException::class.java) {
            service.recordExternalEmailPrimaryDecision(exchange, accepted = true)
        }

        assertEquals(ExchangeRecipientAcceptanceStatus.PENDING, recipient.acceptanceStatus)
        verify(externalEmailAcceptancePolicyService, never()).validate(any(), any(), any())
        verify(repository, never()).update(any())
    }

    @Test
    fun `external email authenticated acceptance revalidates current policy before recording`()
    {
        val recipientUserId = UUID.randomUUID()
        val share = directShare(PrincipalKind.USER, recipientUserId)
        val recipient = pendingRecipient(share.id).apply {
            selectionType = ExchangeRecipientSelectionType.EXTERNAL_EMAIL
        }
        whenever(repository.findPrimaryForUpdate(exchangeId)).thenReturn(recipient)
        whenever(shareService.getById(share.id)).thenReturn(share)
        whenever(repository.update(any())).thenAnswer { it.getArgument(0) }

        val updated = service.recordPrimaryDecision(exchange, recipientUserId, accepted = true)

        assertEquals(ExchangeRecipientAcceptanceStatus.ACCEPTED, updated.acceptanceStatus)
        verify(externalEmailAcceptancePolicyService).validate(exchange, share, recipientUserId)
    }

    @Test
    fun `external email rejection remains available without policy revalidation`()
    {
        val recipientUserId = UUID.randomUUID()
        val share = directShare(PrincipalKind.USER, recipientUserId)
        val recipient = pendingRecipient(share.id).apply {
            selectionType = ExchangeRecipientSelectionType.EXTERNAL_EMAIL
        }
        whenever(repository.findPrimaryForUpdate(exchangeId)).thenReturn(recipient)
        whenever(shareService.getById(share.id)).thenReturn(share)
        whenever(repository.update(any())).thenAnswer { it.getArgument(0) }

        val updated = service.recordPrimaryDecision(exchange, recipientUserId, accepted = false)

        assertEquals(ExchangeRecipientAcceptanceStatus.REJECTED, updated.acceptanceStatus)
        verify(externalEmailAcceptancePolicyService, never()).validate(any(), any(), any())
    }

    @Test
    fun `external email stale policy failure leaves authenticated decision pending`()
    {
        val recipientUserId = UUID.randomUUID()
        val share = directShare(PrincipalKind.USER, recipientUserId)
        val recipient = pendingRecipient(share.id).apply {
            selectionType = ExchangeRecipientSelectionType.EXTERNAL_EMAIL
        }
        whenever(repository.findPrimaryForUpdate(exchangeId)).thenReturn(recipient)
        whenever(shareService.getById(share.id)).thenReturn(share)
        whenever(externalEmailAcceptancePolicyService.validate(exchange, share, recipientUserId))
            .thenThrow(ExchangeRecipientEligibilityException())

        assertThrows(ExchangeRecipientEligibilityException::class.java) {
            service.recordPrimaryDecision(exchange, recipientUserId, accepted = true)
        }

        assertEquals(ExchangeRecipientAcceptanceStatus.PENDING, recipient.acceptanceStatus)
        verify(repository, never()).update(any())
    }

    @Test
    fun `external email stale policy failure leaves no-auth decision pending`()
    {
        val recipientUserId = UUID.randomUUID()
        val share = directShare(PrincipalKind.USER, recipientUserId)
        val recipient = pendingRecipient(share.id).apply {
            selectionType = ExchangeRecipientSelectionType.EXTERNAL_EMAIL
        }
        whenever(repository.findPrimaryForUpdate(exchangeId)).thenReturn(recipient)
        whenever(shareService.getById(share.id)).thenReturn(share)
        whenever(externalEmailAcceptancePolicyService.validate(exchange, share, authenticatedAppUserId = null))
            .thenThrow(ExchangeRecipientEligibilityException())

        assertThrows(ExchangeRecipientEligibilityException::class.java) {
            service.recordExternalEmailPrimaryDecision(exchange, accepted = true)
        }

        assertEquals(ExchangeRecipientAcceptanceStatus.PENDING, recipient.acceptanceStatus)
        verify(repository, never()).update(any())
    }

    @Test
    fun `deleteBinding removes the attestation and the recipient row`()
    {
        val recipient = pendingRecipient(UUID.randomUUID())

        service.deleteBinding(recipient)

        verify(attestationService).deleteForRecipient(recipient.id)
        verify(repository).delete(recipient)
    }

    @Test
    fun `trusted participant binding must be pending while ordinary participant cannot be pending`()
    {
        val trustedShare = directShare(PrincipalKind.USER, UUID.randomUUID())
        whenever(repository.save(any())).thenAnswer { it.getArgument(0) }

        val trusted = service.createBinding(
            exchangeId,
            trustedShare,
            ExchangeRecipientPurpose.PARTICIPANT,
            ExchangeRecipientSelectionType.TRUSTED_PERSON,
            UUID.randomUUID(),
            ExchangeRecipientAcceptanceStatus.PENDING,
        )

        assertEquals(ExchangeRecipientAcceptanceStatus.PENDING, trusted.acceptanceStatus)
        assertThrows(IllegalArgumentException::class.java) {
            service.createBinding(
                exchangeId,
                directShare(PrincipalKind.USER, UUID.randomUUID()),
                ExchangeRecipientPurpose.PARTICIPANT,
                ExchangeRecipientSelectionType.REGISTERED_USER,
                null,
                ExchangeRecipientAcceptanceStatus.PENDING,
            )
        }
    }

    @Test
    fun `trusted person participant acceptance revalidates and activates only its Share`()
    {
        val appUserId = UUID.randomUUID()
        val share = directShare(PrincipalKind.USER, appUserId).apply {
            status = com.docuhyphen.app.api.model.entity.ShareStatus.PENDING_APPROVAL
        }
        val recipient = pendingRecipient(share.id).apply {
            purpose = ExchangeRecipientPurpose.PARTICIPANT
            selectionType = ExchangeRecipientSelectionType.TRUSTED_PERSON
        }
        val attestation = trustedAttestation()
        whenever(repository.findByIdForUpdate(recipient.id)).thenReturn(recipient)
        whenever(shareService.getById(share.id)).thenReturn(share)
        whenever(attestationService.findForRecipient(recipient.id)).thenReturn(attestation)
        whenever(validationService.validatePersonAttestation(eq(attestation), any())).thenReturn(mock())
        whenever(repository.update(any())).thenAnswer { it.getArgument(0) }

        val updated = service.recordTrustedParticipantDecision(recipient.id, appUserId, accepted = true)

        assertEquals(ExchangeRecipientAcceptanceStatus.ACCEPTED, updated.acceptanceStatus)
        verify(validationService).validatePersonAttestation(eq(attestation), any())
        verify(attestationService).markAcceptanceVerified(eq(attestation), any())
        verify(shareService).activate(share.id)
        verify(shareService, never()).revoke(any(), anyOrNull(), anyOrNull())
        verify(trustedRecipientAuditService).recordAcceptanceAllowed(
            appUserId,
            attestation.callerOrganizationId,
            exchangeId,
            recipient,
        )
    }

    @Test
    fun `trusted group participant rejection revokes only its Share without trust revalidation`()
    {
        val groupId = UUID.randomUUID()
        val managerId = UUID.randomUUID()
        val share = directShare(PrincipalKind.PRINCIPAL_GROUP, groupId).apply {
            status = com.docuhyphen.app.api.model.entity.ShareStatus.PENDING_APPROVAL
        }
        val recipient = pendingRecipient(share.id).apply {
            purpose = ExchangeRecipientPurpose.PARTICIPANT
            selectionType = ExchangeRecipientSelectionType.TRUSTED_GROUP
        }
        whenever(repository.findByIdForUpdate(recipient.id)).thenReturn(recipient)
        whenever(shareService.getById(share.id)).thenReturn(share)
        whenever(organizationGroupService.isActiveDecisionMaker(groupId, managerId)).thenReturn(true)
        whenever(repository.update(any())).thenAnswer { it.getArgument(0) }

        val updated = service.recordTrustedParticipantDecision(recipient.id, managerId, accepted = false)

        assertEquals(ExchangeRecipientAcceptanceStatus.REJECTED, updated.acceptanceStatus)
        verify(validationService, never()).validateGroupAttestation(any(), any())
        verify(shareService).revoke(share.id, PrincipalRef.user(managerId))
        verify(shareService, never()).activate(any())
    }

    @Test
    fun `participant decision rejects primary-recipient substitution before Share access`()
    {
        val recipient = pendingRecipient(UUID.randomUUID()).apply {
            purpose = ExchangeRecipientPurpose.PARTICIPANT
            selectionType = ExchangeRecipientSelectionType.TRUSTED_PERSON
        }
        whenever(repository.findByIdForUpdate(recipient.id)).thenReturn(recipient)

        recipient.purpose = ExchangeRecipientPurpose.PRIMARY
        assertThrows(IllegalArgumentException::class.java) {
            service.recordTrustedParticipantDecision(recipient.id, UUID.randomUUID(), accepted = true)
        }
        verify(shareService, never()).getById(any())
    }

    @Test
    fun `trusted participant acceptance stale trust leaves decision pending and Share inactive`()
    {
        val appUserId = UUID.randomUUID()
        val share = directShare(PrincipalKind.USER, appUserId).apply {
            status = com.docuhyphen.app.api.model.entity.ShareStatus.PENDING_APPROVAL
        }
        val recipient = pendingRecipient(share.id).apply {
            purpose = ExchangeRecipientPurpose.PARTICIPANT
            selectionType = ExchangeRecipientSelectionType.TRUSTED_PERSON
        }
        val attestation = trustedAttestation()
        whenever(repository.findByIdForUpdate(recipient.id)).thenReturn(recipient)
        whenever(shareService.getById(share.id)).thenReturn(share)
        whenever(attestationService.findForRecipient(recipient.id)).thenReturn(attestation)
        whenever(validationService.validatePersonAttestation(eq(attestation), any()))
            .thenThrow(OrganizationTrustNotFoundException("Trust is no longer eligible"))

        assertThrows(OrganizationTrustNotFoundException::class.java) {
            service.recordTrustedParticipantDecision(recipient.id, appUserId, accepted = true)
        }

        assertEquals(ExchangeRecipientAcceptanceStatus.PENDING, recipient.acceptanceStatus)
        verify(repository, never()).update(any())
        verify(shareService, never()).activate(any())
        verify(trustedRecipientAuditService).recordAcceptanceDenied(
            appUserId,
            attestation.callerOrganizationId,
            exchangeId,
            recipient,
        )
    }

    @Test
    fun `unrelated user cannot decide a trusted participant invitation`()
    {
        val invitedAppUserId = UUID.randomUUID()
        val share = directShare(PrincipalKind.USER, invitedAppUserId).apply {
            status = com.docuhyphen.app.api.model.entity.ShareStatus.PENDING_APPROVAL
        }
        val recipient = pendingRecipient(share.id).apply {
            purpose = ExchangeRecipientPurpose.PARTICIPANT
            selectionType = ExchangeRecipientSelectionType.TRUSTED_PERSON
        }
        whenever(repository.findByIdForUpdate(recipient.id)).thenReturn(recipient)
        whenever(shareService.getById(share.id)).thenReturn(share)

        assertThrows(IllegalArgumentException::class.java) {
            service.recordTrustedParticipantDecision(recipient.id, UUID.randomUUID(), accepted = true)
        }

        assertEquals(ExchangeRecipientAcceptanceStatus.PENDING, recipient.acceptanceStatus)
        verify(repository, never()).update(any())
        verify(shareService, never()).activate(any())
    }

    @Test
    fun `Share activation failure propagates so participant decision transaction rolls back`()
    {
        val appUserId = UUID.randomUUID()
        val share = directShare(PrincipalKind.USER, appUserId).apply {
            status = com.docuhyphen.app.api.model.entity.ShareStatus.PENDING_APPROVAL
        }
        val recipient = pendingRecipient(share.id).apply {
            purpose = ExchangeRecipientPurpose.PARTICIPANT
            selectionType = ExchangeRecipientSelectionType.TRUSTED_PERSON
        }
        val attestation = trustedAttestation()
        whenever(repository.findByIdForUpdate(recipient.id)).thenReturn(recipient)
        whenever(shareService.getById(share.id)).thenReturn(share)
        whenever(attestationService.findForRecipient(recipient.id)).thenReturn(attestation)
        whenever(validationService.validatePersonAttestation(eq(attestation), any())).thenReturn(mock())
        whenever(repository.update(any())).thenAnswer { it.getArgument(0) }
        doThrow(IllegalStateException("activation failed")).whenever(shareService).activate(share.id)

        assertThrows(IllegalStateException::class.java) {
            service.recordTrustedParticipantDecision(recipient.id, appUserId, accepted = true)
        }
        verify(repository).update(recipient)
        verify(shareService).activate(share.id)
    }

    private fun directShare(kind: PrincipalKind, principalId: UUID) = Share().apply {
        resourceType = ResourceType.EXCHANGE
        resourceId = exchangeId
        principalKind = kind
        this.principalId = principalId
        roleName = ExchangeShareRoleName.VIEWER.name
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

    private fun trustedAttestation() = ExchangeRecipientAttestation().apply {
        callerOrganizationId = ownerOrganizationId
    }
}
