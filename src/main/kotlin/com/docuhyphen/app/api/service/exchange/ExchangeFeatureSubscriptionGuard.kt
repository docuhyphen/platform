package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.service.subscription.PlanFeature
import com.docuhyphen.app.api.service.subscription.SubscriptionAccessService
import com.docuhyphen.app.api.service.subscription.SubscriptionContext

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

/**
 * Applies the commercial allowances for the features that are consumed inside an existing
 * Exchange: document version history and advanced access controls.
 *
 * The paying subject always comes from the owner columns persisted on the Exchange, never from
 * the active-organization request header. That keeps the charge with whoever pays for the
 * Exchange and means a recipient is never charged for a feature the sender bought. An Exchange
 * with no recorded owner is left alone because there is nobody to charge.
 *
 * Only the operations that consume a paid feature are checked. Document comments are shared
 * collaboration across tiers, while internal-note visibility is controlled by organization
 * membership. Reading versions that already exist is deliberately untouched, so a plan change
 * never hides content a participant has already been given.
 */
@ApplicationScoped
class ExchangeFeatureSubscriptionGuard @Inject constructor(
    private val subscriptionAccessService: SubscriptionAccessService,
)
{
    /** Refuses adding a new document version when the Exchange owner has no history allowance. */
    fun requireDocumentVersionHistory(exchange: Exchange)
    {
        val context = ownerContext(exchange) ?: return

        subscriptionAccessService.requireMutationAllowed(context)
        subscriptionAccessService.requireFeature(context, PlanFeature.DOCUMENT_VERSION_HISTORY)
    }

    /**
     * Refuses a share that carries more than a role.
     *
     * Granting somebody a role is basic sharing and stays available on every plan. Narrowing
     * that grant with constraints such as download rules, watermarking, an MFA requirement, an
     * address allowlist, or an access window is the advanced control being sold, so the check
     * only runs when the request actually asks for one.
     */
    fun requireAdvancedAccessControls(
        exchange: Exchange,
        constraintsJson: String?,
        expiresAtEpochMillis: Long?,
    )
    {
        if (constraintsJson.isNullOrBlank() && expiresAtEpochMillis == null)
        {
            return
        }

        val context = ownerContext(exchange) ?: return

        subscriptionAccessService.requireMutationAllowed(context)
        subscriptionAccessService.requireFeature(context, PlanFeature.ADVANCED_ACCESS_CONTROLS)
    }

    private fun ownerContext(exchange: Exchange): SubscriptionContext?
    {
        if (!subscriptionAccessService.enforcementMode().evaluatesDecisions)
        {
            return null
        }

        return SubscriptionContext.forOwner(exchange.ownerUserId, exchange.ownerOrganizationId)
    }
}



