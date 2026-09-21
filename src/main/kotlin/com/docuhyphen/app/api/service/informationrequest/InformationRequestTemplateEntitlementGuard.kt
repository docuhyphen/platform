package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestTemplateScopeKind
import com.docuhyphen.app.api.service.subscription.PlanFeature
import com.docuhyphen.app.api.service.subscription.SubscriptionAccessService
import com.docuhyphen.app.api.service.subscription.SubscriptionContext
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

@ApplicationScoped
class InformationRequestTemplateEntitlementGuard @Inject constructor(
    private val subscriptionAccessService: SubscriptionAccessService,
)
{
    fun requireTemplateAccess(
        scopeKind: InformationRequestTemplateScopeKind,
        organizationId: UUID?,
        userId: UUID?,
        actingOrganizationId: UUID?,
    )
    {
        if (!subscriptionAccessService.enforcementMode().evaluatesDecisions) return

        val contexts = entitlementContexts(scopeKind, organizationId, userId, actingOrganizationId)
        if (contexts.any { subscriptionAccessService.isFeatureAvailable(it, PlanFeature.INFORMATION_REQUESTS) }) return

        subscriptionAccessService.requireFeature(contexts.first(), PlanFeature.INFORMATION_REQUESTS)
    }

    fun requireTemplateMutation(
        scopeKind: InformationRequestTemplateScopeKind,
        organizationId: UUID?,
        userId: UUID?,
        actingOrganizationId: UUID?,
    )
    {
        if (!subscriptionAccessService.enforcementMode().evaluatesDecisions) return

        val contexts = entitlementContexts(scopeKind, organizationId, userId, actingOrganizationId)
        val subscriptions = contexts.map { it to subscriptionAccessService.resolve(it) }
        if (subscriptions.any { (_, subscription) ->
                subscription.hasFeature(PlanFeature.INFORMATION_REQUESTS) && subscription.allowsMutations()
            })
        {
            return
        }

        subscriptions.firstOrNull { (_, subscription) ->
            subscription.hasFeature(PlanFeature.INFORMATION_REQUESTS)
        }?.let { (context, _) ->
            subscriptionAccessService.requireMutationAllowed(context)
            return
        }

        subscriptionAccessService.requireFeature(contexts.first(), PlanFeature.INFORMATION_REQUESTS)
    }

    private fun entitlementContexts(
        scopeKind: InformationRequestTemplateScopeKind,
        organizationId: UUID?,
        userId: UUID?,
        actingOrganizationId: UUID?,
    ): List<SubscriptionContext>
    {
        val owner = when (scopeKind)
        {
            InformationRequestTemplateScopeKind.PLATFORM -> throw
                InformationRequestTemplateValidationException(
                    "A PLATFORM information request template has no subscription owner",
                )
            InformationRequestTemplateScopeKind.ORGANIZATION -> SubscriptionContext.forOrganization(
                requireNotNull(organizationId) {
                    "An organization-owned information request template names no organization"
                },
            )
            InformationRequestTemplateScopeKind.PERSONAL -> SubscriptionContext.forUser(
                requireNotNull(userId) {
                    "A personally owned information request template names no person"
                },
            )
        }
        val actingOrganization = actingOrganizationId
            ?.takeIf { scopeKind == InformationRequestTemplateScopeKind.PERSONAL }
            ?.let(SubscriptionContext::forOrganization)
        return listOfNotNull(owner, actingOrganization).distinct()
    }
}
