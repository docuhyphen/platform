package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.model.entity.AppRoleName
import com.docuhyphen.app.api.model.entity.OrganizationRoleName
import com.docuhyphen.app.api.repository.organization.OrganizationMembershipRepository
import com.docuhyphen.app.api.repository.application.AppRoleAssignmentRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

/**
 * Resolves a user's effective roles from `app_role_assignment` and
 * `organization_membership_role`. Replaces the retired single
 * `AppUser.role` enum.
 *
 * Where a call site historically assumed a single org (the legacy single-tenant model), the
 * org role is resolved against the user's primary membership (falling back to their first
 * active membership). Call sites that already have a concrete org in scope should use the
 * `*In(userId, orgId)` variants instead.
 */
@ApplicationScoped
class UserRoleService @Inject constructor(
    private val membershipRepository: OrganizationMembershipRepository,
    private val appRoleAssignmentRepository: AppRoleAssignmentRepository,
)
{
    /** Application-level administrator (replaces legacy PLATFORM_ADMIN / APPLICATION). */
    fun isAppAdmin(appUserId: UUID): Boolean =
        AppRoleName.APP_ADMIN in appRoles(appUserId)

    fun activeAppAdminIds(): Set<UUID> =
        appRoleAssignmentRepository.findActiveAppAdmins().map { it.appUserId }.toSet()

    fun firstActiveAppAdminId(): UUID? =
        appRoleAssignmentRepository.findFirstActiveAppAdmin()?.appUserId

    fun appRoles(appUserId: UUID): Set<AppRoleName> =
        appRoleAssignmentRepository.findActiveForUser(appUserId).map { it.roleName }.toSet()

    /** Org role on the user's primary (or first active) membership, or null if none. */
    fun primaryOrgRoles(appUserId: UUID): Set<OrganizationRoleName>
    {
        val membership = membershipRepository.findPrimaryForUser(appUserId)
            ?: membershipRepository.findActiveByUser(appUserId).firstOrNull()
        return membership?.roles?.toSet().orEmpty()
    }

    /** Org role the user holds in a specific organization, or null if not a member. */
    fun orgRolesIn(appUserId: UUID, organizationId: UUID): Set<OrganizationRoleName> =
        membershipRepository.findActiveByUserAndOrg(appUserId, organizationId)?.roles?.toSet().orEmpty()

    fun isOrgAdmin(appUserId: UUID): Boolean = primaryOrgRoles(appUserId).hasAdminRole()

    fun isOrgAdminIn(appUserId: UUID, organizationId: UUID): Boolean =
        orgRolesIn(appUserId, organizationId).hasAdminRole()

    /** True if the user holds any active org membership (admin or member). */
    fun isOrgMember(appUserId: UUID): Boolean =
        membershipRepository.findActiveByUser(appUserId).isNotEmpty()

    private fun Set<OrganizationRoleName>.hasAdminRole(): Boolean =
        OrganizationRoleName.ORG_ADMIN in this || OrganizationRoleName.ORG_OWNER in this
}
