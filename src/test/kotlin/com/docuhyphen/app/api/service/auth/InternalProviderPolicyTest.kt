package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.model.entity.IdentityProviderType
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.communication.EmailService
import com.docuhyphen.app.api.service.communication.EmailTemplateService
import com.docuhyphen.app.api.service.communication.MfaService
import com.docuhyphen.app.api.service.communication.OtpService
import com.docuhyphen.app.api.service.config.ConfigurationService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class InternalProviderPolicyTest
{
    @Test
    fun `INTERNAL sign-in is denied before credential lookup when organization disables it`()
    {
        val appUserService = mock<AppUserService>()
        val identityPolicyService = mock<OrganizationIdentityPolicyService>()
        whenever(
            identityPolicyService.assertProviderAllowedForEmail("user@example.com", IdentityProviderType.INTERNAL)
        ).thenThrow(IdentityProviderNotAllowedException("Provider is not enabled for this organization"))
        val service = SignInService(
            mock<AuthenticationService>(),
            mock<TokenIssuanceService>(),
            mock<MfaService>(),
            appUserService,
            mock<OtpService>(),
            mock<ConfigurationService>(),
            mock<EmailService>(),
            mock<EmailTemplateService>(),
            identityPolicyService,
        )

        assertThrows<IdentityProviderNotAllowedException> {
            service.initiateSignIn("user@example.com", "Password1!", "127.0.0.1")
        }
        verifyNoInteractions(appUserService)
    }
}
