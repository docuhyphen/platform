package com.docuhyphen.app.api.service.documentlibrary

import com.docuhyphen.app.api.model.entity.BlueprintScope
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.subscription.PlanFeature
import com.docuhyphen.app.api.service.subscription.SubscriptionAccessService
import com.docuhyphen.app.api.model.subscription.SubscriptionContext
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class DocumentLibrarySubscriptionGuard @Inject constructor(
    private val subscriptionAccessService: SubscriptionAccessService,
    private val userRoleService: UserRoleService,
)
{
    fun requireLibraryUse(appUserId: UUID, activeOrganizationId: UUID?)
    {
        if (!evaluates() || userRoleService.isAppAdmin(appUserId))
        {
            return
        }

        val context = activeOrganizationId
            ?.let { SubscriptionContext.forOrganization(it) }
            ?: SubscriptionContext.forUser(appUserId)

        subscriptionAccessService.requireFeature(context, PlanFeature.DOCUMENT_LIBRARY_USE)
    }

    fun requireEntryUse(appUserId: UUID, scope: BlueprintScope, organizationId: UUID?)
    {
        val context = ownerContext(appUserId, scope, organizationId) ?: return

        subscriptionAccessService.requireFeature(context, PlanFeature.DOCUMENT_LIBRARY_USE)
    }

    fun requireEntryManagement(appUserId: UUID, scope: BlueprintScope, organizationId: UUID?)
    {
        val context = ownerContext(appUserId, scope, organizationId) ?: return

        subscriptionAccessService.requireMutationAllowed(context)
        subscriptionAccessService.requireFeature(context, PlanFeature.DOCUMENT_LIBRARY_MANAGE)
    }

    private fun ownerContext(
        appUserId: UUID,
        scope: BlueprintScope,
        organizationId: UUID?,
    ): SubscriptionContext?
    {
        if (!evaluates() || scope == BlueprintScope.APP || userRoleService.isAppAdmin(appUserId))
        {
            return null
        }

        if (scope == BlueprintScope.ORG && organizationId != null)
        {
            return SubscriptionContext.forOrganization(organizationId)
        }

        return SubscriptionContext.forUser(appUserId)
    }

    private fun evaluates(): Boolean = subscriptionAccessService.enforcementMode().evaluatesDecisions
}

