package com.docuhyphen.app.api.service.recordpreservation

import com.docuhyphen.app.api.model.entity.RecordOwnerKind
import com.docuhyphen.app.api.model.recordpreservation.RecordOwnerRef
import com.docuhyphen.app.api.service.subscription.OrganizationFeatureSubscriptionGuard
import com.docuhyphen.app.api.service.subscription.PlanFeature
import com.docuhyphen.app.api.service.subscription.SubscriptionAccessService
import com.docuhyphen.app.api.service.subscription.SubscriptionContext
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class RecordPreservationEntitlementGuard @Inject constructor(
    private val organizationFeatures: OrganizationFeatureSubscriptionGuard,
    private val subscriptions: SubscriptionAccessService,
)
{
    fun requireMutation(owner: RecordOwnerRef)
    {
        when (owner.kind)
        {
            RecordOwnerKind.ORGANIZATION -> organizationFeatures.requireMutation(owner.id, PlanFeature.AUDIT_GOVERNANCE)
            RecordOwnerKind.USER ->
            {
                val context = SubscriptionContext.forUser(requireNotNull(owner.id))
                subscriptions.requireMutationAllowed(context)
                subscriptions.requireFeature(context, PlanFeature.INFORMATION_REQUESTS)
            }
            RecordOwnerKind.PLATFORM -> Unit
        }
    }
}
