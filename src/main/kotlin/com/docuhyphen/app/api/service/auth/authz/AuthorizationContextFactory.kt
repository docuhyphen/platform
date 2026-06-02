package com.docuhyphen.app.api.service.auth.authz

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.repository.OrganizationMembershipRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

/**
 * Builds the [PrincipalRef] and [AuthorizationContext] for the current request from the
 * authenticated [AuthTokenContext].
 *
 * Until the JWT carries an explicit `active_membership_id` claim (the multi-org context work),
 * the active org/membership is derived from the user's primary [com.docuhyphen.app.api.model.entity.OrganizationMembership]
 * (falling back to their first active membership). `mfaSatisfied` and `clientIp` are not yet
 * threaded through the request pipeline and default to conservative values; they become
 * meaningful once the auth filter populates them.
 */
@ApplicationScoped
class AuthorizationContextFactory @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val organizationMembershipRepository: OrganizationMembershipRepository,
)
{
    /** The current caller as a USER principal, or null if unauthenticated. */
    fun currentPrincipal(): PrincipalRef? =
        authTokenContext.authToken.appUser?.id?.let { PrincipalRef.user(it) }

    /** Best-effort authorization context for the current request. */
    fun currentContext(): AuthorizationContext
    {
        val user = authTokenContext.authToken.appUser ?: return AuthorizationContext.ANONYMOUS

        val membership = organizationMembershipRepository.findPrimaryForUser(user.id)
            ?: organizationMembershipRepository.findActiveByUser(user.id).firstOrNull()

        return AuthorizationContext(
            actingUser = user,
            activeMembershipId = membership?.id,
            activeOrgId = membership?.organizationId,
            mfaSatisfied = false,
            clientIp = null,
        )
    }
}
