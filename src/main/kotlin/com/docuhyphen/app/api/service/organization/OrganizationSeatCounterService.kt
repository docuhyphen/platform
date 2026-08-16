package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.service.subscription.OrganizationSeatCounter
import com.docuhyphen.app.api.repository.organization.OrganizationMembershipRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

/**
 * Reports how many purchased seats an organization is currently consuming.
 *
 * Membership remains the single source of truth for who occupies a seat, so this delegates to
 * the membership service rather than counting rows itself. Only unique active, provisioned
 * memberships are counted, which keeps external recipients, no-account recipients, deactivated
 * members, and pending invitations out of the seat total.
 */
@ApplicationScoped
class OrganizationSeatCounterService @Inject constructor(
    private val organizationMembershipRepository: OrganizationMembershipRepository,
) : OrganizationSeatCounter
{
    override fun countActiveSeats(organizationId: UUID): Long
    {
        return organizationMembershipRepository.countActiveProvisionedMembers(listOf(organizationId))[organizationId] ?: 0
    }
}

