package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.dto.CurrentSessionDto
import com.docuhyphen.app.api.service.auth.authz.Capability
import com.docuhyphen.app.api.service.auth.authz.RoleCapabilities
import io.quarkus.security.UnauthorizedException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

/**
 * Computes the effective session contract for the authenticated caller.
 *
 * The returned [CurrentSessionDto] exposes the caller's identity, the explicitly selected
 * organization (null for personal-product sessions), all applicable scoped roles, and the union
 * of effective capabilities derived from those roles. Capabilities are recomputed on every call
 * from live role assignments, so role changes are reflected without reissuing tokens.
 */
@ApplicationScoped
class SessionService @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val userRoleService: UserRoleService,
)
{
    fun currentSession(): CurrentSessionDto
    {
        val user = authTokenContext.authToken.appUser
            ?: throw UnauthorizedException("Not authenticated")

        val appRoles = userRoleService.appRoles(user.id!!)
        val activeOrgId = authTokenContext.activeOrganizationId
        val orgRoles = if (activeOrgId != null)
        {
            userRoleService.orgRolesIn(user.id, activeOrgId)
        }
        else
        {
            emptySet()
        }

        val capabilities = mutableSetOf<Capability>()
        appRoles.forEach { capabilities += RoleCapabilities.forAppRole(it) }
        orgRoles.forEach { capabilities += RoleCapabilities.forOrganizationRole(it) }

        return CurrentSessionDto(
            userId = user.id,
            email = user.email,
            appRoles = appRoles.map { it.name },
            activeOrganizationId = activeOrgId,
            organizationRoles = orgRoles.map { it.name },
            capabilities = capabilities.map { it.name }.sorted(),
        )
    }
}
