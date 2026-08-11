package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.AuthToken
import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.ExchangeShareRoleName
import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.entity.Share
import com.docuhyphen.app.api.model.entity.ShareSource
import com.docuhyphen.app.api.repository.ExchangeRepository
import com.docuhyphen.app.api.repository.ShareRepository
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.communication.EmailTemplateService
import com.docuhyphen.app.api.service.communication.OtpService
import com.docuhyphen.app.api.service.config.ConfigurationService
import com.docuhyphen.app.api.service.organization.ExternalIdentityResolutionService
import com.docuhyphen.app.api.service.organization.OrganizationExchangePolicyService
import com.docuhyphen.app.api.service.organization.OrganizationGroupService
import io.quarkus.security.ForbiddenException
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.util.UUID

class ExchangeAccessManagementMutationTest
{
    @ParameterizedTest
    @EnumSource(
        value = ExchangeShareRoleName::class,
        names = ["OWNER"],
        mode = EnumSource.Mode.EXCLUDE,
    )
    fun `every assignable role can be applied to a mutable direct Share`(role: ExchangeShareRoleName)
    {
        val fixture = Fixture()

        fixture.service.changeRole(fixture.exchange.id, fixture.share.id, role)

        verify(fixture.shareService).updateRoleAndConstraints(
            shareId = fixture.share.id,
            roleName = role,
            constraintsJson = null,
            applyConstraints = false,
            resourceLabel = fixture.exchange.name,
        )
    }

    @Test
    fun `role change normalizes and applies constraints through ShareService`()
    {
        val fixture = Fixture()

        fixture.service.changeRole(
            fixture.exchange.id,
            fixture.share.id,
            ExchangeShareRoleName.EDITOR,
            """{"can_download":false,"watermark":true}""",
        )

        verify(fixture.shareService).updateRoleAndConstraints(
            shareId = fixture.share.id,
            roleName = ExchangeShareRoleName.EDITOR,
            constraintsJson = """{"can_download":false,"watermark":true}""",
            applyConstraints = true,
            resourceLabel = fixture.exchange.name,
        )
    }

    @Test
    fun `revoke access delegates the exact Share and actor to ShareService`()
    {
        val fixture = Fixture()

        fixture.service.revokeAccess(fixture.exchange.id, fixture.share.id)

        verify(fixture.shareService).revoke(
            fixture.share.id,
            fixture.caller.id,
            fixture.exchange.name,
        )
    }

    @Test
    fun `owner role cannot be assigned`()
    {
        val fixture = Fixture()

        assertThrows(IllegalArgumentException::class.java) {
            fixture.service.changeRole(
                fixture.exchange.id,
                fixture.share.id,
                ExchangeShareRoleName.OWNER,
            )
        }

        verifyNoInteractions(fixture.shareRepository, fixture.shareService)
    }

    @Test
    fun `owner Share cannot be changed or revoked`()
    {
        val fixture = Fixture(
            sharePrincipalId = null,
            shareRole = ExchangeShareRoleName.OWNER,
        )
        fixture.share.principalId = fixture.caller.id

        assertThrows(ForbiddenException::class.java) {
            fixture.service.changeRole(
                fixture.exchange.id,
                fixture.share.id,
                ExchangeShareRoleName.EDITOR,
            )
        }
        assertThrows(ForbiddenException::class.java) {
            fixture.service.revokeAccess(fixture.exchange.id, fixture.share.id)
        }

        verify(fixture.shareService, never()).updateRoleAndConstraints(any(), any(), any(), any(), any())
        verify(fixture.shareService, never()).revoke(any(), any(), any())
    }

    @Test
    fun `caller cannot change or revoke their own non-owner Share`()
    {
        val fixture = Fixture(sharePrincipalId = null)
        fixture.share.principalId = fixture.caller.id

        assertThrows(ForbiddenException::class.java) {
            fixture.service.changeRole(
                fixture.exchange.id,
                fixture.share.id,
                ExchangeShareRoleName.REVIEWER,
            )
        }
        assertThrows(ForbiddenException::class.java) {
            fixture.service.revokeAccess(fixture.exchange.id, fixture.share.id)
        }

        verify(fixture.shareService, never()).updateRoleAndConstraints(any(), any(), any(), any(), any())
        verify(fixture.shareService, never()).revoke(any(), any(), any())
    }

    @Test
    fun `Share from another Exchange cannot be changed or revoked`()
    {
        val fixture = Fixture()
        fixture.share.resourceId = UUID.randomUUID()

        assertThrows(IllegalArgumentException::class.java) {
            fixture.service.changeRole(
                fixture.exchange.id,
                fixture.share.id,
                ExchangeShareRoleName.REVIEWER,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            fixture.service.revokeAccess(fixture.exchange.id, fixture.share.id)
        }

        verify(fixture.shareService, never()).updateRoleAndConstraints(any(), any(), any(), any(), any())
        verify(fixture.shareService, never()).revoke(any(), any(), any())
    }

    @Test
    fun `non-owner authorization denial occurs before Share mutation`()
    {
        val fixture = Fixture(allowed = false)

        assertThrows(ForbiddenException::class.java) {
            fixture.service.changeRole(
                fixture.exchange.id,
                fixture.share.id,
                ExchangeShareRoleName.REVIEWER,
            )
        }
        assertThrows(ForbiddenException::class.java) {
            fixture.service.revokeAccess(fixture.exchange.id, fixture.share.id)
        }

        verify(fixture.shareRepository, never()).findById(any())
        verify(fixture.shareService, never()).updateRoleAndConstraints(any(), any(), any(), any(), any())
        verify(fixture.shareService, never()).revoke(any(), any(), any())
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(
        value = ExchangeStatus::class,
        names = ["ENDED", "REJECTED", "RESCINDED"],
    )
    fun `authorized Manage access mutation currently has no terminal Exchange guard`(status: ExchangeStatus)
    {
        val fixture = Fixture()
        fixture.exchange.status = status

        fixture.service.changeRole(
            fixture.exchange.id,
            fixture.share.id,
            ExchangeShareRoleName.REVIEWER,
        )

        verify(fixture.shareService).updateRoleAndConstraints(
            shareId = fixture.share.id,
            roleName = ExchangeShareRoleName.REVIEWER,
            constraintsJson = null,
            applyConstraints = false,
            resourceLabel = fixture.exchange.name,
        )
    }

    private class Fixture(
        allowed: Boolean = true,
        sharePrincipalId: UUID? = UUID.randomUUID(),
        shareRole: ExchangeShareRoleName = ExchangeShareRoleName.VIEWER,
    )
    {
        val caller = AppUser().apply {
            id = UUID.randomUUID()
            email = "owner@example.test"
        }
        val exchange = Exchange().apply {
            initiator = caller
            ownerUserId = caller.id
            name = "Manage access mutation"
        }
        val share = Share().apply {
            resourceType = ResourceType.EXCHANGE
            resourceId = exchange.id
            principalKind = PrincipalKind.USER
            principalId = sharePrincipalId ?: UUID.randomUUID()
            roleName = shareRole
            source = ShareSource.DIRECT
        }
        val shareRepository = mock<ShareRepository>()
        val shareService = mock<ShareService>()
        private val exchangeRepository = mock<ExchangeRepository>()
        val service: ExchangeAccessManagementService

        init
        {
            whenever(exchangeRepository.findById(exchange.id)).thenReturn(exchange)
            whenever(shareRepository.findById(share.id)).thenReturn(share)
            val authorizationService = mock<AuthorizationService>()
            whenever(authorizationService.authorize(any(), any(), any(), any())).thenReturn(
                if (allowed) Decision.Allow() else Decision.Deny("DENIED", "Denied in test"),
            )
            val authorizationContextFactory = mock<AuthorizationContextFactory>()
            whenever(authorizationContextFactory.currentPrincipal()).thenReturn(PrincipalRef.user(caller.id))
            whenever(authorizationContextFactory.currentContext()).thenReturn(AuthorizationContext.ANONYMOUS)
            val authTokenContext = AuthTokenContext().apply {
                authToken = AuthToken().apply { appUser = caller }
            }
            service = ExchangeAccessManagementService(
                exchangeRepository = exchangeRepository,
                shareRepository = shareRepository,
                shareService = shareService,
                shareQueryService = mock<ShareQueryService>(),
                appUserService = mock<AppUserService>(),
                externalParticipantRepository = mock(),
                organizationGroupService = mock<OrganizationGroupService>(),
                exchangeRecipientService = mock<ExchangeRecipientService>(),
                exchangeRecipientSelectionResolver = mock<ExchangeRecipientSelectionResolver>(),
                exchangeRecipientAttestationService = mock<ExchangeRecipientAttestationService>(),
                externalIdentityResolutionService = mock<ExternalIdentityResolutionService>(),
                authTokenContext = authTokenContext,
                authorizationService = authorizationService,
                authorizationContextFactory = authorizationContextFactory,
                organizationExchangePolicyService = mock<OrganizationExchangePolicyService>(),
                emailTemplateService = mock<EmailTemplateService>(),
                otpService = mock<OtpService>(),
                noAuthExchangeAccessTokenService = mock<NoAuthExchangeAccessTokenService>(),
                configurationService = mock<ConfigurationService>(),
                auditRecorder = mock<AuditRecorder>(),
                exchangeNotificationDeliveryService = mock<ExchangeNotificationDeliveryService>(),
                exchangeFeatureSubscriptionGuard = mock<ExchangeFeatureSubscriptionGuard>(),
            )
        }
    }
}
