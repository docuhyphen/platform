package com.docuhyphen.app.api.service.auth.authz

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.StepUpAuthService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class AuthorizationContextFactory @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val stepUpAuthService: StepUpAuthService,
)
{
    fun currentPrincipal(): PrincipalRef?
    {
        val token = authTokenContext.authToken
        return token.appUser?.id?.let(PrincipalRef::user)
            ?: token.applicationId?.let(PrincipalRef::application)
    }

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
