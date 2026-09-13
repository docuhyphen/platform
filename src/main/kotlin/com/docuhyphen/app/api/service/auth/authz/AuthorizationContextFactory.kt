package com.docuhyphen.app.api.service.auth.authz

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.service.auth.StepUpAuthService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

/**
 * Builds the [PrincipalRef] and [AuthorizationContext] for the current request from the
 * authenticated [AuthTokenContext].
 *
 * Active org and membership come exclusively from [AuthTokenContext.activeOrganizationId] and
 * [AuthTokenContext.activeMembershipId], which are populated by [EndpointVerificationFilter]
 * only when the caller provides a valid X-Active-Organization-Id header backed by an active
 * membership. No primary-organization fallback is performed; a request with no header produces
 * a context with null active org (personal-product mode).
 */
@ApplicationScoped
class AuthorizationContextFactory @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val stepUpAuthService: StepUpAuthService,
)
{
    /** The current caller as a USER principal, or null if unauthenticated. */
    fun currentPrincipal(): PrincipalRef?
    {
        val token = authTokenContext.authToken
        return token.appUser?.id?.let(PrincipalRef::user)
            ?: token.applicationId?.let(PrincipalRef::application)
    }

    /** Best-effort authorization context for the current request. */
    fun currentContext(): AuthorizationContext
    {
        val token = authTokenContext.authToken

        val application = token.application
        if (application != null)
        {
            return AuthorizationContext(
                applicationId = application.id,
                clientIp = authTokenContext.clientIp,
                shareLinkTokenHash = authTokenContext.shareLinkTokenHash,
            )
        }

        val user = token.appUser ?: return AuthorizationContext.ANONYMOUS

        return AuthorizationContext(
            actingUser = user,
            activeMembershipId = authTokenContext.activeMembershipId,
            activeOrgId = authTokenContext.activeOrganizationId,
            mfaSatisfied = runCatching { stepUpAuthService.isFresh() }.getOrDefault(false),
            clientIp = authTokenContext.clientIp,
            shareLinkTokenHash = authTokenContext.shareLinkTokenHash,
            sessionRef = authTokenContext.userSessionId?.toString(),
        )
    }
}
