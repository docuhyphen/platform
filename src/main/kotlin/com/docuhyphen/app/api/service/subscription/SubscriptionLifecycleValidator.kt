package com.docuhyphen.app.api.service.subscription

import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class SubscriptionLifecycleValidator
{
    fun validate(
        ownerType: SubscriptionOwnerType,
        update: SubscriptionLifecycleUpdate,
    )
    {
        PlanCatalog.requireAssignableTo(update.planCode, ownerType)
        require(update.changeReason.isNotBlank()) { "Change reason is required" }
        require(update.changeReason.length <= 1024) { "Change reason must be at most 1024 characters" }

        val periodStart = update.currentPeriodStart
        val periodEnd = update.currentPeriodEnd
        require((periodStart == null) == (periodEnd == null)) {
            "Current period start and end must be provided together"
        }
        if (periodStart != null && periodEnd != null)
        {
            require(periodStart.isBefore(periodEnd)) { "Current period end must be after its start" }
        }

        when (update.status)
        {
            SubscriptionStatus.TRIALING ->
                require(periodEnd != null) { "A trial must have a current period end" }
            SubscriptionStatus.PAST_DUE ->
            {
                val graceEnd = update.gracePeriodEnd
                    ?: throw IllegalArgumentException("A past-due subscription must have a grace period end")
                if (periodEnd != null)
                {
                    require(!graceEnd.isBefore(periodEnd)) {
                        "Grace period end cannot be before the current period end"
                    }
                }
            }
            SubscriptionStatus.CANCELED ->
                require(periodEnd != null) { "A canceled subscription must retain its paid-through period end" }
            SubscriptionStatus.ACTIVE,
            SubscriptionStatus.SUSPENDED,
            -> require(update.gracePeriodEnd == null) {
                "Grace period end is only valid for a past-due subscription"
            }
        }

        if (update.status != SubscriptionStatus.PAST_DUE)
        {
            require(update.gracePeriodEnd == null) {
                "Grace period end is only valid for a past-due subscription"
            }
        }
    }
}
