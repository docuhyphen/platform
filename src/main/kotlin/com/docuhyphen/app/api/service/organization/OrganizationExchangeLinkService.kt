package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.model.entity.LinkStatus
import com.docuhyphen.app.api.repository.OrganizationExchangeLinkRepository
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import java.util.UUID

@RequestScoped
class OrganizationExchangeLinkService @Inject constructor(
    private val organizationExchangeLinkRepository: OrganizationExchangeLinkRepository,
)
{
    fun hasAcceptedLink(organizationAId: UUID, organizationBId: UUID): Boolean
    {
        if (organizationAId == organizationBId)
        {
            return false
        }
        return organizationExchangeLinkRepository.findByRequestingOrganization(organizationAId)
            .any {
                it.status == LinkStatus.ACCEPTED &&
                    it.requestedOrganization?.id == organizationBId
            } || organizationExchangeLinkRepository.findByRequestedOrganization(organizationAId)
            .any {
                it.status == LinkStatus.ACCEPTED &&
                    it.requestingOrganization?.id == organizationBId
            }
    }
}
