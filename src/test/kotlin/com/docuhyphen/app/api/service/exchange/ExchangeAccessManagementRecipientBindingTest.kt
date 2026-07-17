package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.AuthToken
import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.ExchangeRecipientAcceptanceStatus
import com.docuhyphen.app.api.model.entity.ExchangeRecipientPurpose
import com.docuhyphen.app.api.model.entity.ExchangeRecipientSelectionType
import com.docuhyphen.app.api.model.entity.ExchangeShareRoleName
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.entity.Share
import com.docuhyphen.app.api.model.entity.ShareSource
import com.docuhyphen.app.api.model.entity.ShareStatus
import com.docuhyphen.app.api.repository.ExchangeRepository
import com.docuhyphen.app.api.repository.ExternalParticipantRepository
import com.docuhyphen.app.api.repository.ShareRepository
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.communication.EmailService
import com.docuhyphen.app.api.service.communication.EmailTemplateService
import com.docuhyphen.app.api.service.config.ConfigurationService
import com.docuhyphen.app.api.service.organization.OrganizationExchangePolicyService
import com.docuhyphen.app.api.service.organization.OrganizationGroupService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

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
        val exchange = Exchange().apply {
            id = exchangeId
            ownerUserId = caller.id
            initiator = caller
            name = "Recipient binding test"
        }
        val directShare = Share().apply {
            resourceType = ResourceType.EXCHANGE
            resourceId = exchangeId
            principalKind = PrincipalKind.USER
            principalId = recipient.id
            roleName = ExchangeShareRoleName.VIEWER
            source = ShareSource.DIRECT
        }

        val exchangeRepository = mock<ExchangeRepository>()
        val shareService = mock<ShareService>()
        val appUserService = mock<AppUserService>()
        val authorizationService = mock<AuthorizationService>()
        val authorizationContextFactory = mock<AuthorizationContextFactory>()
        val exchangeRecipientService = mock<ExchangeRecipientService>()
        val authTokenContext = AuthTokenContext().apply {
            authToken = AuthToken().apply { appUser = caller }
        }
        whenever(exchangeRepository.findById(exchangeId)).thenReturn(exchange)
        whenever(appUserService.getById(recipient.id)).thenReturn(recipient)
        whenever(authorizationContextFactory.currentPrincipal()).thenReturn(PrincipalRef.user(caller.id))
        whenever(authorizationContextFactory.currentContext()).thenReturn(AuthorizationContext.ANONYMOUS)
        whenever(authorizationService.authorize(any(), any(), any(), any())).thenReturn(Decision.Allow())
        whenever(
            shareService.grant(
                resourceType = eq(ResourceType.EXCHANGE),
                resourceId = eq(exchangeId),
                principalKind = eq(PrincipalKind.USER),
                principalId = eq(recipient.id),
                roleName = eq(ExchangeShareRoleName.VIEWER),
                grantedByAppUserId = eq(caller.id),
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
            authTokenContext = authTokenContext,
            authorizationService = authorizationService,
            authorizationContextFactory = authorizationContextFactory,
            organizationExchangePolicyService = mock<OrganizationExchangePolicyService>(),
            emailService = mock<EmailService>(),
            emailTemplateService = mock<EmailTemplateService>(),
            configurationService = mock<ConfigurationService>(),
            auditRecorder = mock<AuditRecorder>(),
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
    }
}
