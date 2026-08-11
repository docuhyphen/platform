package com.docuhyphen.app.api.service.documentlibrary

import com.docuhyphen.app.api.model.entity.BlueprintScope
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.subscription.PlanFeature
import com.docuhyphen.app.api.service.subscription.SubscriptionAccessService
import com.docuhyphen.app.api.service.subscription.SubscriptionContext
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

/**
 * Applies the commercial allowance that governs the Document Library.
 *
 * Reading a stored document is how the library is actually consumed, so reads are charged as
 * well as changes. Reading is charged to whoever owns the entry being read, which keeps a
 * personal entry on the reader's own plan and an organization entry on that organization
 * regardless of which organization happens to be selected at the time.
 *
 * Curating the platform-supplied catalogue is a platform administration duty rather than
 * something a personal plan buys, so it is left to the existing authorization checks.
 */
@ApplicationScoped
class DocumentLibrarySubscriptionGuard @Inject constructor(
    private val subscriptionAccessService: SubscriptionAccessService,
    private val userRoleService: UserRoleService,
)
{
    /** Browsing the library the caller is currently working in. */
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

    /** Reading one stored entry, charged to whoever owns that entry. */
    fun requireEntryUse(appUserId: UUID, scope: BlueprintScope, organizationId: UUID?)
    {
        val context = ownerContext(appUserId, scope, organizationId) ?: return

        subscriptionAccessService.requireFeature(context, PlanFeature.DOCUMENT_LIBRARY_USE)
    }

    /** Creating or changing an entry, charged to whoever owns it. */
    fun requireEntryManagement(appUserId: UUID, scope: BlueprintScope, organizationId: UUID?)
    {
        val context = ownerContext(appUserId, scope, organizationId) ?: return

        subscriptionAccessService.requireMutationAllowed(context)
        subscriptionAccessService.requireFeature(context, PlanFeature.DOCUMENT_LIBRARY_MANAGE)
    }

    /**
     * Resolves the paying subject for an entry, or null when nothing should be charged because
     * the entry belongs to the platform catalogue or the caller administers the platform.
     */
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



