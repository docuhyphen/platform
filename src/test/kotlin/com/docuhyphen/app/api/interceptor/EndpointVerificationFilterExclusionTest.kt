package com.docuhyphen.app.api.interceptor

import com.docuhyphen.app.api.repository.organization.OrganizationMembershipRepository
import com.docuhyphen.app.api.service.application.ApplicationService
import com.docuhyphen.app.api.service.auth.ApplicationTokenBoundaryService
import com.docuhyphen.app.api.service.auth.AuthAuditService
import com.docuhyphen.app.api.service.auth.AuthSessionPolicyService
import com.docuhyphen.app.api.service.auth.AuthenticationService
import com.docuhyphen.app.api.service.auth.DpopValidationService
import com.docuhyphen.app.api.service.auth.OrganizationMembershipValidationService
import com.docuhyphen.app.api.service.auth.SessionRevocationCache
import com.docuhyphen.app.api.service.auth.UserSessionService
import com.docuhyphen.app.api.service.config.ConfigurationService
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class EndpointVerificationFilterExclusionTest
{
    @Test
    fun `information request template administration is not excluded from access-token verification`()
    {
        val excluded = excludedEndpoints()

        assertFalse(
            excluded.any { "/information-request-templates".startsWith(it) },
            "Template administration must stay behind the access-token verification filter",
        )
    }

    @Test
    fun `information request runtime resources are not excluded from access-token verification`()
    {
        val excluded = excludedEndpoints()

        assertFalse(
            excluded.any { "/information-requests".startsWith(it) },
            "Information Request runtime resources must stay behind the access-token verification filter",
        )
    }

    @Test
    fun `information request no-auth access resource is excluded from access-token verification`()
    {
        val excluded = excludedEndpoints()

        assertTrue(
            excluded.any { "/no-auth/information-request-access-links/challenges".startsWith(it) },
            "The no-auth Information Request contact-proof adapter must be reachable without an access token",
        )
        assertTrue(
            excluded.any { "/no-auth/information-request-access-links/sessions".startsWith(it) },
            "The no-auth Information Request session adapter must be reachable without an access token",
        )
    }

    @Test
    fun `information request no-auth read resource is excluded from access-token verification`()
    {
        val excluded = excludedEndpoints()
        val requestId = "3f9c0e0a-3e8a-4a4e-9b0e-0c9e1c9a0a01"

        assertTrue(
            excluded.any { "/no-auth/information-requests/$requestId".startsWith(it) },
            "The no-auth Information Request detail read must be reachable without an access token",
        )
        assertTrue(
            excluded.any { "/no-auth/information-requests/$requestId/parties".startsWith(it) },
            "The no-auth Information Request party read must be reachable without an access token",
        )
    }

    @Test
    fun `a look-alike path outside the exact no-auth information request prefix is rejected`()
    {
        val excluded = excludedEndpoints()

        assertFalse(
            excluded.any { "/no-auth/information-requests-templates".startsWith(it) },
            "A path that merely shares a prefix with the no-auth Information Request adapter, but is not " +
                "actually under it, must stay behind the access-token verification filter",
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun excludedEndpoints(): List<String>
    {
        val filter = EndpointVerificationFilter(
            authenticationService = mock<AuthenticationService>(),
            applicationTokenBoundaryService = mock<ApplicationTokenBoundaryService>(),
            applicationService = mock<ApplicationService>(),
            appUserService = mock(),
            userSessionService = mock<UserSessionService>(),
            sessionRevocationCache = mock<SessionRevocationCache>(),
            dpopValidationService = mock<DpopValidationService>(),
            organizationMembershipValidationService = mock<OrganizationMembershipValidationService>(),
            organizationMembershipRepository = mock<OrganizationMembershipRepository>(),
            configurationService = mock<ConfigurationService>(),
            authSessionPolicyService = mock<AuthSessionPolicyService>(),
            authAuditService = mock<AuthAuditService>(),
        )
        val field = EndpointVerificationFilter::class.java.getDeclaredField("excludedEndpoints")
        field.isAccessible = true
        return field.get(filter) as List<String>
    }
}
