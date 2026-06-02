package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.OrganizationMembership
import com.docuhyphen.app.api.model.entity.OrganizationMembershipStatus
import com.docuhyphen.app.api.model.entity.RoleName
import com.docuhyphen.app.api.repository.AppUserRepository
import com.docuhyphen.app.api.repository.OrganizationMembershipRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

/**
 * Write-side for `organization_membership` — the unified replacement for the retired
 * single `AppUser.role` enum. Provisioning paths (org registration, JIT/SCIM/OAuth, admin
 * role assignment) call [assignOrgRole] to record a user's role within an organization.
 *
 * Idempotent: re-assigning updates the existing active membership's role rather than
 * inserting a duplicate (the table is unique on `(app_user_id, organization_id)`).
 */
@ApplicationScoped
class OrganizationMembershipService @Inject constructor(
    private val membershipRepository: OrganizationMembershipRepository,
    private val appUserRepository: AppUserRepository,
)
{
    /**
     * Users belonging to the organization (ACTIVE memberships). Replaces reads of the retired
     * `Organization.appUsers` relationship. Includes users whose own `isActive` flag is false
     * (membership stays ACTIVE on deactivation), so callers that need only enabled users must
     * filter on [AppUser.isActive] themselves.
     */
    fun membersOf(organizationId: UUID): List<AppUser> =
        appUserRepository.findByOrganizationId(organizationId)

    /** True if the user holds an ACTIVE membership of the organization. */
    fun isMember(appUserId: UUID, organizationId: UUID): Boolean =
        membershipRepository.findActiveByUserAndOrg(appUserId, organizationId) != null

    /** Count of ACTIVE memberships of the organization. */
    fun activeMemberCount(organizationId: UUID): Long =
        membershipRepository.countActiveMembersOfOrg(organizationId)

    /** The user's role name within an org (from their ACTIVE membership), or null if not a member. */
    fun roleOf(appUserId: UUID, organizationId: UUID): String? =
        membershipRepository.findActiveByUserAndOrg(appUserId, organizationId)?.roleName

    /**
     * The org the user primarily belongs to (their primary membership, else their first active
     * one), or null if they hold no active membership. Used by org-scoped policies (e.g. the
     * cross-org sharing gate) that need a single org context for a user.
     */
    fun primaryOrganizationId(appUserId: UUID): UUID? =
        (membershipRepository.findPrimaryForUser(appUserId)
            ?: membershipRepository.findActiveByUser(appUserId).firstOrNull())?.organizationId

    /**
     * Map of appUserId → role name for every ACTIVE member of the org, in one query.
     * Used to decorate member-listing DTOs without an N+1 per-user lookup.
     */
    fun rolesOf(organizationId: UUID): Map<UUID, String> =
        membershipRepository.findActiveMembersOfOrg(organizationId)
            .associate { it.appUserId to it.roleName }

    /** Removes the user's membership of an org (used when an org admin hard-deletes the user). */
    fun removeMember(appUserId: UUID, organizationId: UUID) =
        membershipRepository.deleteByUserAndOrg(appUserId, organizationId).let { }

    /**
     * Enabled administrators (ORG_ADMIN / ORG_OWNER) of the org: ACTIVE members whose own
     * account is enabled (not deactivated, not deprovisioned). Used to enforce the
     * "an organization must always retain at least one usable admin" invariant — a deactivated
     * or deprovisioned admin can't actually administer, so they don't count toward the floor.
     */
    fun activeAdmins(organizationId: UUID): List<AppUser>
    {
        val roles = rolesOf(organizationId)
        return membersOf(organizationId).filter { user ->
            user.isActive && user.deprovisionedAt == null &&
                roles[user.id].let { it == RoleName.ORG_ADMIN.name || it == RoleName.ORG_OWNER.name }
        }
    }

    /**
     * True when [appUserId] is the *only* enabled administrator of the org — i.e. demoting,
     * deactivating, or removing them would leave the organization with no usable admin.
     */
    fun isLastActiveAdmin(appUserId: UUID, organizationId: UUID): Boolean
    {
        val admins = activeAdmins(organizationId)
        return admins.size == 1 && admins.first().id == appUserId
    }

    fun assignOrgRole(
        appUserId: UUID,
        organizationId: UUID,
        role: RoleName,
        isPrimary: Boolean = false,
        invitedByAppUserId: UUID? = null,
    ): OrganizationMembership
    {
        val existing = membershipRepository.findActiveByUserAndOrg(appUserId, organizationId)
        val membership = (existing ?: OrganizationMembership().apply {
            this.appUserId = appUserId
            this.organizationId = organizationId
            this.invitedByAppUserId = invitedByAppUserId
            this.isPrimary = isPrimary
        }).apply {
            this.roleName = role.name
            this.status = OrganizationMembershipStatus.ACTIVE
        }
        return if (existing == null) membershipRepository.save(membership)
        else membershipRepository.update(membership)
    }
}
