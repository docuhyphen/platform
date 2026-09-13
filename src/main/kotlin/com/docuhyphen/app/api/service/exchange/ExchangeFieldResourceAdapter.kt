package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.service.auth.authz.*
import com.docuhyphen.app.api.service.fields.FieldResourceAdapter
import com.docuhyphen.app.api.service.subscription.SubscriptionContext
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

/**
 * The Exchange domain's implementation of the Fields [FieldResourceAdapter] port. Encapsulates
 * Exchange existence, ownership, authorization (via [AuthorizationService]), and the lifecycle rule
 * that field values may only be edited while an Exchange is INITIATED (Draft) in the first release.
 * The Fields engine depends on this port, never on [ExchangeRepository] directly.
 *
 * What a caller may do with each individual field is decided by [ExchangeFieldBindingPolicy], which
 * is reached through this adapter.
 */
@ApplicationScoped
class ExchangeFieldResourceAdapter @Inject constructor(
    private val exchangeRepository: ExchangeRepository,
    private val authorizationService: AuthorizationService,
    override val bindingPolicy: ExchangeFieldBindingPolicy,
) : FieldResourceAdapter
{
    override val resourceType: String = "EXCHANGE"

    override fun exists(resourceId: UUID): Boolean =
        exchangeRepository.findById(resourceId)?.isDeleted == false

    override fun ownerScope(resourceId: UUID): ScopeReference?
    {
        val exchange = exchangeRepository.findById(resourceId) ?: return null
        // An Exchange an organization holds is governed by that organization. One a person holds
        // alone is governed by that person, who owns their own configuration as an organization owns
        // its own. Where both are recorded the organization wins, matching who pays for the Exchange.
        exchange.ownerOrganizationId?.let { return ScopeReference.Organization(it) }
        return exchange.ownerUserId?.let { ScopeReference.Personal(it) }
    }

    override fun subscriptionContext(resourceId: UUID): SubscriptionContext?
    {
        val exchange = exchangeRepository.findById(resourceId) ?: return null
        return SubscriptionContext.forOwner(exchange.ownerUserId, exchange.ownerOrganizationId)
    }

    override fun authorizeViewFields(
        resourceId: UUID,
        principal: PrincipalRef,
        context: AuthorizationContext,
    )
    {
        val decision = authorizationService.authorize(
            principal, Action.EXCHANGE_VIEW, ResourceRef.exchange(resourceId), context,
        )
        if (decision is Decision.Deny) throw ForbiddenException("Access denied to exchange fields")
    }

    override fun authorizeManageFields(
        resourceId: UUID,
        principal: PrincipalRef,
        context: AuthorizationContext,
    )
    {
        val decision = authorizationService.authorize(
            principal, Action.EXCHANGE_EDIT, ResourceRef.exchange(resourceId), context,
        )
        if (decision is Decision.Deny) throw ForbiddenException("Access denied to manage exchange fields")
    }

    override fun authorizeManageSchema(
        resourceId: UUID,
        principal: PrincipalRef,
        context: AuthorizationContext,
    )
    {
        val decision = authorizationService.authorize(
            principal, Action.EXCHANGE_MANAGE_SCHEMA, ResourceRef.exchange(resourceId), context,
        )
        if (decision is Decision.Deny) throw ForbiddenException("Access denied to manage the exchange schema")
    }

    override fun valuesEditable(resourceId: UUID): Boolean
    {
        val exchange = exchangeRepository.findById(resourceId) ?: return false
        return !exchange.isDeleted && exchange.status == ExchangeStatus.INITIATED
    }
}
