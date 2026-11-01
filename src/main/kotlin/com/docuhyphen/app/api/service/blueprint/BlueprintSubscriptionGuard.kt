package com.docuhyphen.app.api.service.blueprint

import com.docuhyphen.app.api.model.entity.BlueprintScope
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.subscription.PlanFeature
import com.docuhyphen.app.api.service.subscription.SubscriptionAccessService
import com.docuhyphen.app.api.model.subscription.SubscriptionContext
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class BlueprintSubscriptionGuard @Inject constructor(
    private val subscriptionAccessService: SubscriptionAccessService,
    private val userRoleService: UserRoleService,
)
{
    fun requireBlueprintUse(appUserId: UUID, activeOrganizationId: UUID?)
    {
        if (!subscriptionAccessService.enforcementMode().evaluatesDecisions)
        {
            return
        }

        if (userRoleService.isAppAdmin(appUserId))
        {
            return
        }

        val context = activeOrganizationId
            ?.let { SubscriptionContext.forOrganization(it) }
            ?: SubscriptionContext.forUser(appUserId)

        subscriptionAccessService.requireFeature(context, PlanFeature.BLUEPRINT_USE)
    }

    fun requireExistingBlueprintUse(
        appUserId: UUID,
        scope: BlueprintScope,
        createdByAppUserId: UUID?,
        organizationId: UUID?,
        activeOrganizationId: UUID?,
    )
    {
        if (!subscriptionAccessService.enforcementMode().evaluatesDecisions)
        {
            return
        }

        if (userRoleService.isAppAdmin(appUserId))
        {
            return
        }

        val context = when (scope)
        {
            BlueprintScope.PERSONAL -> SubscriptionContext.forUser(
                createdByAppUserId ?: throw IllegalStateException("Personal Blueprint owner is missing"),
            )

            BlueprintScope.ORG -> SubscriptionContext.forOrganization(
                organizationId ?: throw IllegalStateException("Organization Blueprint owner is missing"),
            )

            BlueprintScope.APP -> activeOrganizationId
                ?.let(SubscriptionContext::forOrganization)
                ?: SubscriptionContext.forUser(appUserId)
        }

        subscriptionAccessService.requireFeature(context, PlanFeature.BLUEPRINT_USE)
    }

    fun requireBlueprintManagement(appUserId: UUID, scope: BlueprintScope, organizationId: UUID?)
    {
        if (!subscriptionAccessService.enforcementMode().evaluatesDecisions)
        {
            return
        }

        if (scope == BlueprintScope.APP || userRoleService.isAppAdmin(appUserId))
        {
            return
        }

        val context = if (scope == BlueprintScope.ORG && organizationId != null)
        {
            SubscriptionContext.forOrganization(organizationId)
        }
        else
        {
            SubscriptionContext.forUser(appUserId)
        }

        subscriptionAccessService.requireMutationAllowed(context)
        subscriptionAccessService.requireFeature(context, PlanFeature.BLUEPRINT_MANAGE)
    }
}

