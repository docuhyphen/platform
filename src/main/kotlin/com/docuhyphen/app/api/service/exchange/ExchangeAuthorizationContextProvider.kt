package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.repository.ExchangeRepository
import com.docuhyphen.app.api.service.auth.authz.OwnerContext
import com.docuhyphen.app.api.service.auth.authz.ResourceAuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.ResourceAuthorizationContextProvider
import com.docuhyphen.app.api.service.auth.authz.ResourceKind
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

/**
 * Resolves [ResourceAuthorizationContext] for [ResourceKind.EXCHANGE] resources.
 *
 * Owner precedence: organization owner beats personal owner. If neither column is set,
 * resolution returns null and the caller is denied.
 *
 * Terminal statuses (ENDED, RESCINDED) are reported as archived so that [DefaultAuthorizationService]
 * can deny non-admin writes without knowing Exchange-domain status names.
 */
@ApplicationScoped
class ExchangeAuthorizationContextProvider : ResourceAuthorizationContextProvider
{
    @Inject
    private lateinit var exchangeRepository: ExchangeRepository

    override val supportedKind: ResourceKind = ResourceKind.EXCHANGE

    override fun resolve(resourceId: UUID): ResourceAuthorizationContext?
    {
        val exchange = exchangeRepository.findById(resourceId) ?: return null

        val ownerContext = when
        {
            exchange.ownerOrganizationId != null ->
                OwnerContext.Organization(exchange.ownerOrganizationId!!)
            exchange.ownerUserId != null ->
                OwnerContext.Personal(exchange.ownerUserId!!)
            else -> return null
        }

        val isArchived = exchange.isDeleted ||
            exchange.status == ExchangeStatus.ENDED ||
            exchange.status == ExchangeStatus.RESCINDED

        return ResourceAuthorizationContext(
            ownerContext = ownerContext,
            isArchived = isArchived,
            isSuspended = false,
        )
    }
}
