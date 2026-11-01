package com.docuhyphen.app.api.service.user

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.DetailedEntityToDtoTransformer
import com.docuhyphen.app.api.model.dto.AppUserDetailedDto
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.organization.OrganizationMembershipService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class SignedInAppUserService @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val membershipService: OrganizationMembershipService,
    private val userRoleService: UserRoleService,
)
{
    fun currentUserDetails(): AppUserDetailedDto?
    {
        val appUser = authTokenContext.authToken.appUser ?: return null
        val organizationRoles = membershipService.primaryOrganizationId(appUser.id)
            ?.let { membershipService.rolesOf(appUser.id, it) }
            ?: emptySet()
        return DetailedEntityToDtoTransformer.toDto(
            appUser,
            userRoleService.appRoles(appUser.id),
            organizationRoles,
        )
    }
}
