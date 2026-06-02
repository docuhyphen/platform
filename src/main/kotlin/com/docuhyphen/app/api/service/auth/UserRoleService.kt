package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.model.entity.RoleName
import com.docuhyphen.app.api.model.entity.RoleScopeType
import com.docuhyphen.app.api.repository.OrganizationMembershipRepository
import com.docuhyphen.app.api.repository.RoleAssignmentRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

/**
 * Resolves a user's effective roles from the unified model — `role_assignment` (APP scope)
 * and `organization_membership.role_name` (ORG scope). Replaces the retired single
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
    private val roleAssignmentRepository: RoleAssignmentRepository,
)
{
    /** Application-level administrator (replaces legacy PLATFORM_ADMIN / APPLICATION). */
    fun isAppAdmin(appUserId: UUID): Boolean =
        roleAssignmentRepository.findActiveForUserInScope(appUserId, RoleScopeType.APP, null)
            .any { it.roleName == RoleName.APP_ADMIN.name }

    /** Org role on the user's primary (or first active) membership, or null if none. */
    fun primaryOrgRole(appUserId: UUID): RoleName?
    {
        val membership = membershipRepository.findPrimaryForUser(appUserId)
            ?: membershipRepository.findActiveByUser(appUserId).firstOrNull()
        return membership?.roleName?.toRoleNameOrNull()
    }

    /** Org role the user holds in a specific organization, or null if not a member. */
    fun orgRoleIn(appUserId: UUID, organizationId: UUID): RoleName? =
        membershipRepository.findActiveByUserAndOrg(appUserId, organizationId)?.roleName?.toRoleNameOrNull()

    fun isOrgAdmin(appUserId: UUID): Boolean = primaryOrgRole(appUserId).isAdminRole()

    fun isOrgAdminIn(appUserId: UUID, organizationId: UUID): Boolean =
        orgRoleIn(appUserId, organizationId).isAdminRole()

    /** True if the user holds any active org membership (admin or member). */
    fun isOrgMember(appUserId: UUID): Boolean =
        membershipRepository.findActiveByUser(appUserId).isNotEmpty()

    private fun RoleName?.isAdminRole(): Boolean =
        this == RoleName.ORG_ADMIN || this == RoleName.ORG_OWNER

    private fun String.toRoleNameOrNull(): RoleName? =
        runCatching { RoleName.valueOf(this) }.getOrNull()
}
