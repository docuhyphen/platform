package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.dto.OrgMemberCapacityDto
import com.docuhyphen.app.api.repository.OrganizationSubscriptionPolicyRepository
import com.docuhyphen.app.api.service.auth.PlatformOrganizationSubscriptionPolicyService
import com.docuhyphen.app.api.service.auth.UserRoleService
import io.quarkus.security.UnauthorizedException
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import java.util.UUID

@RequestScoped
class OrganizationMemberCapacityService @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val organizationService: OrganizationService,
    private val organizationSubscriptionPolicyRepository: OrganizationSubscriptionPolicyRepository,
    private val userRoleService: UserRoleService,
    private val organizationMembershipService: OrganizationMembershipService,
)
{
    fun getForOrganization(organizationId: UUID): OrgMemberCapacityDto
    {
        val appUser = authTokenContext.authToken.appUser
            ?: throw UnauthorizedException("User is not authenticated")
        if (
            authTokenContext.activeOrganizationId != organizationId ||
            !userRoleService.isOrgAdminIn(appUser.id, organizationId)
        )
        {
            throw UnauthorizedException("User does not have permission to view organization member capacity")
        }

        organizationService.getOrganizationById(organizationId)
        val policy = organizationSubscriptionPolicyRepository.findByOrganizationId(organizationId)
        val tierCode = policy?.tierCode ?: PlatformOrganizationSubscriptionPolicyService.FREE_TIER_CODE
        val maxUsers = policy?.maxUsers
            ?: if (tierCode.equals(PlatformOrganizationSubscriptionPolicyService.FREE_TIER_CODE, ignoreCase = true))
            {
                PlatformOrganizationSubscriptionPolicyService.FREE_TIER_MAX_USERS
            }
            else
            {
                null
            }
        val activeUsers = organizationMembershipService.activeProvisionedMemberCount(organizationId)

        return OrgMemberCapacityDto(
            organizationId = organizationId,
            tierCode = tierCode,
            maxUsers = maxUsers,
            activeUsers = activeUsers,
        )
    }
}
