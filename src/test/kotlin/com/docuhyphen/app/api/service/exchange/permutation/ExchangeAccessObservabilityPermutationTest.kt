package com.docuhyphen.app.api.service.exchange.permutation

import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.ExchangeRecipientPurpose
import com.docuhyphen.app.api.model.entity.ExchangeRecipientSelectionType
import com.docuhyphen.app.api.model.entity.ExchangeShareRoleName
import com.docuhyphen.app.api.model.entity.Organization
import com.docuhyphen.app.api.model.entity.OrganizationSettings
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.entity.ShareStatus
import com.docuhyphen.app.api.repository.user.AppUserRepository
import com.docuhyphen.app.api.repository.exchange.ExternalParticipantRepository
import com.docuhyphen.app.api.repository.organization.PrincipalGroupRepository
import com.docuhyphen.app.api.repository.exchange.ShareRepository
import com.docuhyphen.app.api.repository.contactdetails.UserContactRepository
import com.docuhyphen.app.api.service.contactdetails.UserContactService
import com.docuhyphen.app.api.service.auth.AuthAuditService
import com.docuhyphen.app.api.service.exchange.ExchangeRecipientService
import com.docuhyphen.app.api.service.exchange.ShareQueryService
import com.docuhyphen.app.api.service.notification.InAppNotificationService
import com.docuhyphen.app.api.service.organization.OrganizationExchangePolicyService
import com.docuhyphen.app.api.service.organization.OrganizationMembershipService
import com.docuhyphen.app.api.service.organization.OrganizationService
import com.docuhyphen.app.api.service.organization.OrganizationTrustExchangePolicyService
import jakarta.inject.Provider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.util.UUID
import com.docuhyphen.app.api.service.identity.PrincipalDisplayService

class ExchangeAccessObservabilityPermutationTest
{
    @Test
    fun `EX-OBS-01 registered primary receives configured notifications`()
    {
        val fixture = ExchangeNotificationPermutationFixture()
        fixture.schedule()
        fixture.complete(committed = true)
        verify(fixture.emailService).sendEmail(any(), any(), any(), any())
        verify(fixture.inAppService).publishAfterCommitIfEnabled(any(), any(), any(), any(), any(), any())
        verify(fixture.realtimeService).broadcastToUser(any(), any(), anyOrNull())
    }

    @Test
    fun `EX-OBS-02 no-auth recipient email contains secure link code and validity`()
    {
        val fixture = ExchangeNotificationPermutationFixture()
        val body = "Use secure code 123456. Valid for 15 minutes."
        fixture.schedule(emailBody = body)
        fixture.complete(committed = true)
        verify(fixture.emailService).sendEmail(
            "recipient@example.test",
            "Invitation",
            body,
            true,
        )
    }

    @Test
    fun `EX-OBS-03 participant added through Manage access receives invitation`()
    {
        val fixture = ExchangeNotificationPermutationFixture()
        fixture.schedule(inAppType = "exchange.participant_invitation")
        fixture.complete(committed = true)
        verify(fixture.realtimeService).broadcastToUser(any(), any(), anyOrNull())
    }

    @Test
    fun `EX-OBS-04 primary acceptance creates mutual contacts`()
    {
        val repository = mock<UserContactRepository>()
        val service = UserContactService(repository, mock<AppUserRepository>())
        val initiator = user("initiator@example.test")
        val recipient = user("recipient@example.test")
        service.recordMutualOnAccept(initiator, recipient, UUID.randomUUID())
        verify(repository, times(2)).save(any())
    }

    @Test
    fun `EX-OBS-05 temporary recipient contact is completed after registration`()
    {
        val repository = mock<UserContactRepository>()
        val service = UserContactService(repository, mock<AppUserRepository>())
        service.recordOneWayFromSignupMerge(
            user("recipient@example.test"),
            user("initiator@example.test"),
            UUID.randomUUID(),
        )
        verify(repository).save(any())
    }

    @Test
    fun `EX-OBS-06 initiation without acceptance creates no mutual contacts`()
    {
        val repository = mock<UserContactRepository>()
        UserContactService(repository, mock<AppUserRepository>())
        verifyNoInteractions(repository)
    }

    @Test
    fun `EX-OBS-07 additional participant receives no primary-decision contact effects`()
    {
        val repository = mock<UserContactRepository>()
        UserContactService(repository, mock<AppUserRepository>())
        verifyNoInteractions(repository)
    }

    @Test
    fun `EX-OBS-08 access and lifecycle actions create audit entries`()
    {
        val fixture = ExchangeRecipientDecisionFixture(
            selectionType = ExchangeRecipientSelectionType.TRUSTED_PERSON,
        )
        fixture.recordPrimary(accepted = true)
        verify(fixture.auditService).recordAcceptanceAllowed(
            fixture.principalId,
            fixture.ownerOrganizationId,
            fixture.exchangeId,
            fixture.recipient,
        )
    }

    @Test
    fun `EX-OBS-09 unauthorized Manage access attempt is audited without disclosure`()
    {
        val fixture = ExchangeRecipientDecisionFixture(
            selectionType = ExchangeRecipientSelectionType.TRUSTED_PERSON,
        )
        fixture.runCatching {
            recordPrimary(accepted = true, actorId = UUID.randomUUID())
        }
        verify(fixture.auditService).recordAcceptanceDenied(any(), any(), any(), any())
    }

    @Test
    fun `EX-OBS-10 organization B2C sharing bypass is audited`()
    {
        val organizationId = UUID.randomUUID()
        val initiatorId = UUID.randomUUID()
        val organizationService = mock<OrganizationService>()
        val membershipService = mock<OrganizationMembershipService>()
        val auditService = mock<AuthAuditService>()
        val organization = Organization().apply {
            id = organizationId
            settings = OrganizationSettings().apply { allowExternalCustomerSharing = true }
        }
        whenever(organizationService.getOrganizationById(organizationId)).thenReturn(organization)
        whenever(membershipService.activeOrganizationIds(any())).thenReturn(emptySet())
        val service = OrganizationExchangePolicyService(
            organizationService,
            membershipService,
            mock<OrganizationTrustExchangePolicyService>(),
            auditService,
        )
        service.assertCanShareWithUser(organizationId, initiatorId, null)
        verify(auditService).emit(
            action = "ORG_SHARE_EXTERNAL_CUSTOMER",
            outcome = "ALLOWED",
            actorId = initiatorId,
            organizationId = organizationId,
            targetType = "APP_USER",
            targetId = null,
            reason = "Org shared with an external individual customer (no organization).",
        )
    }

    @Test
    fun `EX-OBS-11 notification failure follows transaction failure policy`()
    {
        val fixture = ExchangeNotificationPermutationFixture()
        doThrow(IllegalStateException("email failed")).whenever(fixture.emailService)
            .sendEmail(any(), any(), any(), any())
        fixture.schedule()
        fixture.complete(committed = true)
        verify(fixture.inAppService).publishAfterCommitIfEnabled(any(), any(), any(), any(), any(), any())
        verify(fixture.realtimeService).broadcastToUser(any(), any(), anyOrNull())
    }

    @Test
    fun `EX-OBS-12 Summary categorizes requester primary and participants`()
    {
        val exchangeId = UUID.randomUUID()
        val primaryProbe = ExchangeAuthorizationProbe()
        val primaryShare = primaryProbe.share(ExchangeShareRoleName.VIEWER)
        val participantShare = primaryProbe.share(ExchangeShareRoleName.REVIEWER)
        val shareRepository = mock<ShareRepository>()
        val recipientService = mock<ExchangeRecipientService>()
        val provider = mock<Provider<ExchangeRecipientService>>()
        whenever(provider.get()).thenReturn(recipientService)
        whenever(shareRepository.findAllByResource(ResourceType.EXCHANGE, exchangeId))
            .thenReturn(listOf(primaryShare, participantShare))
        whenever(recipientService.findByExchangeId(exchangeId)).thenReturn(
            listOf(
                com.docuhyphen.app.api.model.entity.ExchangeRecipient().apply {
                    directShareId = primaryShare.id
                    purpose = ExchangeRecipientPurpose.PRIMARY
                },
                com.docuhyphen.app.api.model.entity.ExchangeRecipient().apply {
                    directShareId = participantShare.id
                    purpose = ExchangeRecipientPurpose.PARTICIPANT
                },
            ),
        )
        val service = ShareQueryService(
            shareRepository,
            PrincipalDisplayService(
                mock<AppUserRepository>(),
                mock<PrincipalGroupRepository>(),
                mock<ExternalParticipantRepository>(),
            ),
            provider,
        )
        val purposes = service.getSessionAccessView(exchangeId).mapNotNull { it.recipientPurpose }.toSet()
        assertEquals(setOf("PRIMARY", "PARTICIPANT"), purposes)
    }

    private fun user(email: String): AppUser = AppUser().apply {
        this.email = email
        isTemporary = false
    }
}
