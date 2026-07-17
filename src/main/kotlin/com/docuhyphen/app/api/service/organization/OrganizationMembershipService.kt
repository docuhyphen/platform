package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.OrganizationMembership
import com.docuhyphen.app.api.model.entity.OrganizationMembershipStatus
import com.docuhyphen.app.api.model.entity.OrganizationRoleName
import com.docuhyphen.app.api.repository.AppUserRepository
import com.docuhyphen.app.api.repository.OrganizationMembershipRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

/**
 * Write-side for `organization_membership`, the unified replacement for the retired
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

    fun findActiveMembershipsByOrganizationAndExactEmail(
        organizationId: UUID,
        normalizedEmail: String,
    ): List<OrganizationMembership> =
        membershipRepository.findActiveMembershipsByOrganizationAndExactEmail(organizationId, normalizedEmail)

    fun activeOrganizationIds(appUserId: UUID): Set<UUID> =
        membershipRepository.findActiveByUser(appUserId)
            .map { it.organizationId }
            .toSet()

    /** Count of ACTIVE memberships of the organization. */
    fun activeMemberCount(organizationId: UUID): Long =
        membershipRepository.countActiveMembersOfOrg(organizationId)

    fun rolesOf(appUserId: UUID, organizationId: UUID): Set<OrganizationRoleName> =
        membershipRepository.findActiveByUserAndOrg(appUserId, organizationId)?.roles?.toSet().orEmpty()

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
    fun rolesOf(organizationId: UUID): Map<UUID, Set<OrganizationRoleName>> =
        membershipRepository.findActiveMembersOfOrg(organizationId)
            .associate { it.appUserId to it.roles.toSet() }

    /** Removes the user's membership of an org (used when an org admin hard-deletes the user). */
    fun removeMember(appUserId: UUID, organizationId: UUID) =
        membershipRepository.deleteByUserAndOrg(appUserId, organizationId).let { }

    /**
     * Enabled administrators (ORG_ADMIN / ORG_OWNER) of the org: ACTIVE members whose own
     * account is enabled (not deactivated, not deprovisioned). Used to enforce the
     * "an organization must always retain at least one usable admin" invariant, a deactivated
     * or deprovisioned admin can't actually administer, so they don't count toward the floor.
     */
    fun activeAdmins(organizationId: UUID): List<AppUser>
    {
        val roles = rolesOf(organizationId)
        return membersOf(organizationId).filter { user ->
            user.isActive && user.deprovisionedAt == null &&
                roles[user.id].orEmpty().let {
                    OrganizationRoleName.ORG_ADMIN in it || OrganizationRoleName.ORG_OWNER in it
                }
        }
    }

    /**
     * True when [appUserId] is the *only* enabled administrator of the org, i.e. demoting,
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
        role: OrganizationRoleName,
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
            this.roles += role
            this.status = OrganizationMembershipStatus.ACTIVE
        }
        return if (existing == null) membershipRepository.save(membership)
        else membershipRepository.update(membership)
    }

    fun removeOrgRole(
        appUserId: UUID,
        organizationId: UUID,
        role: OrganizationRoleName,
    ): OrganizationMembership
    {
        val membership = membershipRepository.findActiveByUserAndOrg(appUserId, organizationId)
            ?: throw IllegalArgumentException("Active organization membership not found")
        require(role in membership.roles) { "Role $role is not assigned to this membership" }
        require(membership.roles.size > 1) { "An active organization membership must retain at least one role" }
        membership.roles -= role
        return membershipRepository.update(membership)
    }
}
