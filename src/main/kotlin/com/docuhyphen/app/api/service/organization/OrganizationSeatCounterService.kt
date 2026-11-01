package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.repository.organization.OrganizationMembershipRepository
import com.docuhyphen.app.api.service.subscription.OrganizationSeatCounter
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class OrganizationSeatCounterService @Inject constructor(
    private val organizationMembershipRepository: OrganizationMembershipRepository,
) : OrganizationSeatCounter
{
    override fun countActiveSeats(organizationId: UUID): Long
    {
        return organizationMembershipRepository.countActiveProvisionedMembers(listOf(organizationId))[organizationId]
            ?: 0
    }
}

