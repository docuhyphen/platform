package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.repository.exchange.ExternalParticipantRepository
import com.docuhyphen.app.api.repository.exchange.ShareRepository
import com.docuhyphen.app.api.service.audit.AuditOwnerScope
import com.docuhyphen.app.api.service.audit.AuditOwnerScopeResolver
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.auth.authz.*
import com.docuhyphen.app.api.service.communication.EmailTemplateService
import com.docuhyphen.app.api.service.communication.OtpService
import com.docuhyphen.app.api.service.config.ConfigurationService
import com.docuhyphen.app.api.service.organization.OrganizationExchangePolicyService
import com.docuhyphen.app.api.service.organization.OrganizationGroupService
import com.docuhyphen.app.api.service.user.AppUserService
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.*

class ExchangeAccessManagementRecipientBindingTest
{
    @Test
    fun `granting additional user access creates a participant recipient binding`()
    {
        val exchangeId = UUID.randomUUID()
        val caller = AppUser().apply {
            id = UUID.randomUUID()
            email = "owner@example.test"
        }
        val recipient = AppUser().apply {
            id = UUID.randomUUID()
            email = "recipient@example.test"
        }
        val ownerOrganizationId = UUID.randomUUID()
        val exchange = Exchange().apply {
            id = exchangeId
            this.ownerOrganizationId = ownerOrganizationId
            initiator = caller
            name = "Recipient binding test"
        }
        val directShare = Share().apply {
            resourceType = ResourceType.EXCHANGE
            resourceId = exchangeId
            principalKind = PrincipalKind.USER
            principalId = recipient.id
            roleName = ExchangeShareRoleName.VIEWER.name
            source = ShareSource.DIRECT
        }

        val exchangeRepository = mock<ExchangeRepository>()
        val shareService = mock<ShareService>()
        val appUserService = mock<AppUserService>()
        val authorizationService = mock<AuthorizationService>()
        val authorizationContextFactory = mock<AuthorizationContextFactory>()
        val exchangeRecipientService = mock<ExchangeRecipientService>()
        val organizationExchangePolicyService = mock<OrganizationExchangePolicyService>()
        val authTokenContext = AuthTokenContext().apply {
            authToken = AuthToken().apply { appUser = caller }
            activeOrganizationId = ownerOrganizationId
        }
        whenever(exchangeRepository.findById(exchangeId)).thenReturn(exchange)
        whenever(appUserService.getById(recipient.id)).thenReturn(recipient)
        whenever(authorizationContextFactory.currentPrincipal()).thenReturn(PrincipalRef.user(caller.id))
        whenever(authorizationContextFactory.currentContext()).thenReturn(AuthorizationContext.ANONYMOUS)
        whenever(authorizationService.authorize(any(), any(), any(), any())).thenReturn(Decision.Allow())
        val emailTemplateService = mock<EmailTemplateService>()
        whenever(
            emailTemplateService.renderExchangeCreatedRecipientEmail(
                any(), any(), any(), anyOrNull(), anyOrNull(), any(), any(), anyOrNull(), anyOrNull(),
            ),
        ).thenReturn("email body")
        whenever(
            shareService.grant(
                resourceType = eq(ResourceType.EXCHANGE),
                resourceId = eq(exchangeId),
                principalKind = eq(PrincipalKind.USER),
                principalId = eq(recipient.id),
                roleName = eq(ExchangeShareRoleName.VIEWER),
                grantedBy = eq(PrincipalRef.user(caller.id)),
                source = eq(ShareSource.DIRECT),
                constraintsJson = isNull(),
                expiresAt = isNull(),
                status = eq(ShareStatus.ACTIVE),
                resourceLabel = eq(exchange.name),
            ),
        ).thenReturn(directShare)

        val service = ExchangeAccessManagementService(
            exchangeRepository = exchangeRepository,
            shareRepository = mock<ShareRepository>(),
            shareService = shareService,
            shareQueryService = mock<ShareQueryService>(),
            appUserService = appUserService,
            externalParticipantRepository = mock<ExternalParticipantRepository>(),
            organizationGroupService = mock<OrganizationGroupService>(),
            exchangeRecipientService = exchangeRecipientService,
            exchangeRecipientSelectionResolver = mock<ExchangeRecipientSelectionResolver>(),
            exchangeRecipientAttestationService = mock<ExchangeRecipientAttestationService>(),
            externalIdentityResolutionService = mock<com.docuhyphen.app.api.service.identity.ExternalIdentityResolutionService>(),
            authTokenContext = authTokenContext,
            authorizationService = authorizationService,
            authorizationContextFactory = authorizationContextFactory,
            organizationExchangePolicyService = organizationExchangePolicyService,
            emailTemplateService = emailTemplateService,
            otpService = mock<OtpService>(),
            noAuthExchangeAccessTokenService = mock<NoAuthExchangeAccessTokenService>(),
            configurationService = mock<ConfigurationService>(),
            auditRecorder = mock<AuditRecorder>(),
            auditOwnerScopeResolver = mock<AuditOwnerScopeResolver>().also {
                whenever(it.resolve(any(), any())).thenReturn(AuditOwnerScope.Platform)
            },
            exchangeNotificationDeliveryService = mock<ExchangeNotificationDeliveryService>(),
            exchangeFeatureSubscriptionGuard = mock<ExchangeFeatureSubscriptionGuard>(),
        )

        service.grantAccess(
            exchangeId = exchangeId,
            principalKind = PrincipalKind.USER.name,
            principalId = recipient.id.toString(),
            roleName = ExchangeShareRoleName.VIEWER,
        )

        verify(exchangeRecipientService).createBinding(
            exchangeId = eq(exchangeId),
            directShare = eq(directShare),
            purpose = eq(ExchangeRecipientPurpose.PARTICIPANT),
            selectionType = eq(ExchangeRecipientSelectionType.REGISTERED_USER),
            targetOrganizationId = isNull(),
            acceptanceStatus = eq(ExchangeRecipientAcceptanceStatus.NOT_REQUIRED),
        )
        verify(organizationExchangePolicyService).assertCanShareWithUser(
            ownerOrganizationId,
            caller.id,
            recipient.id,
        )
    }
}
