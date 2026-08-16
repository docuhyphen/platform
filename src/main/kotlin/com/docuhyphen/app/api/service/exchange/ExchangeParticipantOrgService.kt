package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.service.organization.OrganizationMembershipService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

/**
 * Resolves the set of recipient-org IDs for a given exchange.
 *
 * Used by [ExchangeApprovalEventHandler] to fire recipient-side workflow trigger events
 * in each recipient org's context when exchange lifecycle events fire.
 */
@ApplicationScoped
class ExchangeParticipantOrgService @Inject constructor(
    private val shareService: ShareService,
    private val exchangeRepository: ExchangeRepository,
    private val organizationMembershipService: OrganizationMembershipService,
)
{
    /**
     * Returns distinct org IDs of all active USER-principal recipients of [exchangeId],
     * excluding the initiator's own org to avoid double-triggering initiator-side workflows.
     */
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
