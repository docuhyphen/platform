package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.service.organization.OrganizationMembershipService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class ExchangeParticipantOrgService @Inject constructor(
    private val shareService: ShareService,
    private val exchangeRepository: ExchangeRepository,
    private val organizationMembershipService: OrganizationMembershipService,
)
{
    fun findRecipientOrgIds(exchangeId: UUID): List<UUID>
    {
        val exchange = exchangeRepository.findById(exchangeId) ?: return emptyList()
        val initiatorOrgId = exchange.ownerOrganizationId

        return shareService.recipientUserIds(exchangeId)
            .flatMap { recipientUserId ->
                organizationMembershipService.activeOrganizationIds(recipientUserId)
            }
            .distinct()
            .filter { it != initiatorOrgId }
    }
}
