package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.model.entity.AppRoleName
import com.docuhyphen.app.api.model.entity.OrganizationRoleName
import com.docuhyphen.app.api.repository.application.AppRoleAssignmentRepository
import com.docuhyphen.app.api.repository.organization.OrganizationMembershipRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class UserRoleService @Inject constructor(
    private val membershipRepository: OrganizationMembershipRepository,
    private val appRoleAssignmentRepository: AppRoleAssignmentRepository,
)
{
    fun isAppAdmin(appUserId: UUID): Boolean =
        AppRoleName.APP_ADMIN in appRoles(appUserId)

    fun activeAppAdminIds(): Set<UUID> =
        appRoleAssignmentRepository.findActiveAppAdmins().map { it.appUserId }.toSet()

    fun firstActiveAppAdminId(): UUID? =
        appRoleAssignmentRepository.findFirstActiveAppAdmin()?.appUserId

    fun appRoles(appUserId: UUID): Set<AppRoleName> =
        appRoleAssignmentRepository.findActiveForUser(appUserId).map { it.roleName }.toSet()

    fun primaryOrgRoles(appUserId: UUID): Set<OrganizationRoleName>
    {
        val membership = membershipRepository.findPrimaryForUser(appUserId)
            ?: membershipRepository.findActiveByUser(appUserId).firstOrNull()
        return membership?.roles?.toSet().orEmpty()
    }

    fun orgRolesIn(appUserId: UUID, organizationId: UUID): Set<OrganizationRoleName> =
        membershipRepository.findActiveByUserAndOrg(appUserId, organizationId)?.roles?.toSet().orEmpty()

    fun isOrgAdmin(appUserId: UUID): Boolean = primaryOrgRoles(appUserId).hasAdminRole()

    fun isOrgAdminIn(appUserId: UUID, organizationId: UUID): Boolean =
        orgRolesIn(appUserId, organizationId).hasAdminRole()

    fun isOrgMember(appUserId: UUID): Boolean =
        membershipRepository.findActiveByUser(appUserId).isNotEmpty()

    private fun Set<OrganizationRoleName>.hasAdminRole(): Boolean =
        OrganizationRoleName.ORG_ADMIN in this || OrganizationRoleName.ORG_OWNER in this
}
