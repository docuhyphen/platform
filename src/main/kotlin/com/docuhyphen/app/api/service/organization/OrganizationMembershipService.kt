package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.OrganizationMembership
import com.docuhyphen.app.api.model.entity.OrganizationMembershipStatus
import com.docuhyphen.app.api.model.entity.OrganizationRoleName
import com.docuhyphen.app.api.repository.organization.OrganizationMembershipRepository
import com.docuhyphen.app.api.repository.user.AppUserRepository
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestOwnershipChangeService
import com.docuhyphen.app.api.service.subscription.OrganizationFeatureSubscriptionGuard
import com.docuhyphen.app.api.service.subscription.PlanFeature
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.util.*

@ApplicationScoped
class OrganizationMembershipService @Inject constructor(
    private val membershipRepository: OrganizationMembershipRepository,
    private val appUserRepository: AppUserRepository,
    private val organizationSeatGuard: OrganizationSeatGuard,
    private val subscriptionGuard: OrganizationFeatureSubscriptionGuard,
    private val requestOwnershipChanges: InformationRequestOwnershipChangeService,
)
{
    fun membersOf(organizationId: UUID): List<AppUser> =
        appUserRepository.findByOrganizationId(organizationId)

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

    @Transactional
    fun enforceSeatsForAccountActivation(appUserId: UUID)
    {
        activeOrganizationIds(appUserId)
            .sortedBy(UUID::toString)
            .forEach { organizationId ->
                subscriptionGuard.requireMutation(organizationId, PlanFeature.ORGANIZATION_ADMINISTRATION)
                organizationSeatGuard.enforceAvailableSeat(organizationId)
            }
    }

    fun activeMemberCount(organizationId: UUID): Long =
        membershipRepository.countActiveMembersOfOrg(organizationId)

    fun activeProvisionedMemberCount(organizationId: UUID): Long =
        activeProvisionedMemberCounts(listOf(organizationId))[organizationId] ?: 0

    fun activeProvisionedMemberCounts(organizationIds: Collection<UUID>): Map<UUID, Long> =
        membershipRepository.countActiveProvisionedMembers(organizationIds)

    fun rolesOf(appUserId: UUID, organizationId: UUID): Set<OrganizationRoleName> =
        membershipRepository.findActiveByUserAndOrg(appUserId, organizationId)?.roles?.toSet().orEmpty()

    fun primaryOrganizationId(appUserId: UUID): UUID? =
        (membershipRepository.findPrimaryForUser(appUserId)
            ?: membershipRepository.findActiveByUser(appUserId).firstOrNull())?.organizationId

    fun rolesOf(organizationId: UUID): Map<UUID, Set<OrganizationRoleName>> =
        membershipRepository.findActiveMembersOfOrg(organizationId)
            .associate { it.appUserId to it.roles.toSet() }

    fun removeMember(appUserId: UUID, organizationId: UUID)
    {
        subscriptionGuard.requireMutation(organizationId, PlanFeature.ORGANIZATION_ADMINISTRATION)
        requestOwnershipChanges.memberRemoved(organizationId, appUserId)
        membershipRepository.deleteByUserAndOrg(appUserId, organizationId)
    }

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

    fun isLastActiveAdmin(appUserId: UUID, organizationId: UUID): Boolean
    {
        val admins = activeAdmins(organizationId)
        return admins.size == 1 && admins.first().id == appUserId
    }

    @Transactional
    fun assignOrgRole(
        appUserId: UUID,
        organizationId: UUID,
        role: OrganizationRoleName,
        isPrimary: Boolean = false,
        invitedByAppUserId: UUID? = null,
    ): OrganizationMembership
    {
        subscriptionGuard.requireMutation(organizationId, PlanFeature.ORGANIZATION_ADMINISTRATION)
        val existing = membershipRepository.findByUserAndOrg(appUserId, organizationId)
        val appUser = appUserRepository.findById(appUserId)
            ?: throw IllegalArgumentException("App user not found")
        val consumesNewSeat = existing?.status != OrganizationMembershipStatus.ACTIVE &&
                appUser.isActive && appUser.deprovisionedAt == null && !appUser.isTemporary
        if (consumesNewSeat)
        {
            organizationSeatGuard.enforceAvailableSeat(organizationId)
        }
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
        subscriptionGuard.requireMutation(organizationId, PlanFeature.ORGANIZATION_ADMINISTRATION)
        val membership = membershipRepository.findActiveByUserAndOrg(appUserId, organizationId)
            ?: throw IllegalArgumentException("Active organization membership not found")
        require(role in membership.roles) { "Role $role is not assigned to this membership" }
        require(membership.roles.size > 1) { "An active organization membership must retain at least one role" }
        membership.roles -= role
        return membershipRepository.update(membership)
    }
}
