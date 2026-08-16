package com.docuhyphen.app.api.service.blueprint

import com.docuhyphen.app.api.model.entity.BlueprintScope
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.subscription.PlanFeature
import com.docuhyphen.app.api.service.subscription.SubscriptionAccessService
import com.docuhyphen.app.api.service.subscription.SubscriptionContext
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

/**
 * Applies the commercial allowance that governs working with Blueprints.
 *
 * Starting an Exchange from a Blueprint is driven entirely by reading the Blueprint, so the read
 * is where the allowance has to be applied. Blocking it at the point of reading also closes the
 * direct API route that would otherwise let a caller assemble the same Exchange by hand.
 *
 * Curating the platform-supplied Blueprint catalogue is a platform administration duty rather
 * than something a personal plan buys, so it is left to the existing authorization checks.
 */
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

    /**
     * Refuses authoring a Blueprint when the paying subject has not bought the allowance.
     *
     * The subject comes from the scope the Blueprint lives in rather than from the request, so an
     * organization Blueprint is always charged to that organization and a personal one to the
     * person who owns it, whichever organization happens to be selected at the time.
     *
     * @param appUserId the authenticated user performing the change
     * @param scope the scope the Blueprint belongs to, or the scope it is being created into
     * @param organizationId the owning organization, meaningful only for an organization Blueprint
     */
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




