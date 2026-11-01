package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.model.auth.authz.ResourceRef
import com.docuhyphen.app.api.model.auth.authz.ScopeReference
import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.service.auth.authz.*
import com.docuhyphen.app.api.service.fields.FieldResourceAdapter
import com.docuhyphen.app.api.model.subscription.SubscriptionContext
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

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
        if (decision is Decision.Deny) throw ForbiddenException("Access denied to Exchange fields")
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
        if (decision is Decision.Deny) throw ForbiddenException("Access denied to manage Exchange fields")
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
        if (decision is Decision.Deny) throw ForbiddenException("Access denied to manage the Exchange schema")
    }

    override fun valuesEditable(resourceId: UUID): Boolean
    {
        val exchange = exchangeRepository.findById(resourceId) ?: return false
        return !exchange.isDeleted && exchange.status == ExchangeStatus.INITIATED
    }
}
