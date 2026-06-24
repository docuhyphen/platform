package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.entity.ShareStatus
import com.docuhyphen.app.api.repository.ExchangeRepository
import com.docuhyphen.app.api.repository.OrganizationMembershipRepository
import com.docuhyphen.app.api.repository.ShareRepository
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
    private val shareRepository: ShareRepository,
    private val exchangeRepository: ExchangeRepository,
    private val organizationMembershipRepository: OrganizationMembershipRepository,
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
        val initiatorOrgId = exchange.initiator?.id?.let { organizationMembershipService.primaryOrganizationId(it) }

        val activeUserShares = shareRepository.findAllByResource(ResourceType.EXCHANGE, exchangeId)
            .filter { it.principalKind == PrincipalKind.USER && it.status == ShareStatus.ACTIVE }

        return activeUserShares
            .flatMap { share ->
                organizationMembershipRepository.findActiveByUser(share.principalId)
                    .map { membership -> membership.organizationId }
            }
            .distinct()
            .filter { it != initiatorOrgId }
    }
}
