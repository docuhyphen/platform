package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.dto.OrgMemberCapacityDto
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.subscription.SubscriptionAccessService
import com.docuhyphen.app.api.model.subscription.SubscriptionContext
import io.quarkus.security.UnauthorizedException
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import java.util.*

@RequestScoped
class OrganizationMemberCapacityService @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val organizationService: OrganizationService,
    private val subscriptionAccessService: SubscriptionAccessService,
    private val userRoleService: UserRoleService,
)
{
    fun getForOrganizationId(organizationId: String): OrgMemberCapacityDto
    {
        val organizationUuid = runCatching { UUID.fromString(organizationId) }
            .getOrElse { throw IllegalArgumentException("Invalid organization ID.") }
        return getForOrganization(organizationUuid)
    }

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
        val subscription = subscriptionAccessService.resolve(SubscriptionContext.forOrganization(organizationId))
        val usage = subscriptionAccessService.measureUsage(subscription)

        return OrgMemberCapacityDto(
            organizationId = organizationId,
            tierCode = subscription.planCode.name,
            maxUsers = subscription.effectiveSeatCapacity(),
            activeUsers = usage.activeSeats ?: 0,
        )
    }
}
