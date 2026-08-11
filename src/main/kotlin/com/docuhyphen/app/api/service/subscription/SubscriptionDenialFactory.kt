package com.docuhyphen.app.api.service.subscription

/**
 * Builds the refusals produced by commercial plan checks.
 *
 * Wording explains the current allowance and the plan that lifts it rather than implying the
 * caller lacks permission, because a plan refusal is a commercial outcome and not an
 * authorization failure.
 */
object SubscriptionDenialFactory
{
    fun featureNotIncluded(
        subscription: EffectiveSubscription,
        feature: PlanFeature,
    ): SubscriptionDenial
    {
        return SubscriptionDenial(
            reason = SubscriptionDenialReason.FEATURE_NOT_INCLUDED,
            planCode = subscription.planCode,
            ownerType = subscription.ownerType,
            feature = feature,
            upgradePlanCode = subscription.upgradePlanCode,
            message = "${describeFeature(feature)} is not included in the " +
                "${describePlan(subscription.planCode)} plan.${upgradeHint(subscription)}",
        )
    }

    fun limitReached(
        subscription: EffectiveSubscription,
        feature: PlanFeature?,
        allowanceDescription: String,
        currentValue: Long,
        limit: Long,
    ): SubscriptionDenial
    {
        return SubscriptionDenial(
            reason = SubscriptionDenialReason.PLAN_LIMIT_REACHED,
            planCode = subscription.planCode,
            ownerType = subscription.ownerType,
            feature = feature,
            currentValue = currentValue,
            limit = limit,
            upgradePlanCode = subscription.upgradePlanCode,
            message = "The ${describePlan(subscription.planCode)} plan includes " +
                "$allowanceDescription.${upgradeHint(subscription)}",
        )
    }

    fun seatLimitReached(
        subscription: EffectiveSubscription,
        activeSeats: Long,
        purchasedSeats: Long,
    ): SubscriptionDenial
    {
        return SubscriptionDenial(
            reason = SubscriptionDenialReason.SEAT_LIMIT_REACHED,
            planCode = subscription.planCode,
            ownerType = subscription.ownerType,
            currentValue = activeSeats,
            limit = purchasedSeats,
            message = "All $purchasedSeats purchased seats are in use. Free a seat or purchase " +
                "more seats before adding another member.",
        )
    }

    fun mutationsNotAllowed(subscription: EffectiveSubscription): SubscriptionDenial
    {
        val reason = when (subscription.status)
        {
            SubscriptionStatus.PAST_DUE -> SubscriptionDenialReason.SUBSCRIPTION_PAST_DUE
            SubscriptionStatus.SUSPENDED -> SubscriptionDenialReason.SUBSCRIPTION_SUSPENDED
            SubscriptionStatus.CANCELED -> SubscriptionDenialReason.SUBSCRIPTION_CANCELED
            else -> SubscriptionDenialReason.SUBSCRIPTION_SUSPENDED
        }

        val message = when (reason)
        {
            SubscriptionDenialReason.SUBSCRIPTION_PAST_DUE ->
                "Payment for this subscription is overdue, so new changes are paused. Existing " +
                    "content stays available to read and export."

            SubscriptionDenialReason.SUBSCRIPTION_CANCELED ->
                "This subscription has been canceled, so new changes are paused. Existing " +
                    "content stays available to read and export."

            else ->
                "This subscription is suspended, so new changes are paused. Existing content " +
                    "stays available to read and export."
        }

        return SubscriptionDenial(
            reason = reason,
            planCode = subscription.planCode,
            ownerType = subscription.ownerType,
            message = message,
        )
    }

    fun organizationSubscriptionRequired(feature: PlanFeature): SubscriptionDenial
    {
        return SubscriptionDenial(
            reason = SubscriptionDenialReason.ORGANIZATION_SUBSCRIPTION_REQUIRED,
            planCode = PlanCode.BUSINESS,
            ownerType = SubscriptionOwnerType.ORGANIZATION,
            feature = feature,
            upgradePlanCode = PlanCode.BUSINESS,
            message = "${describeFeature(feature)} belongs to an organization. Select an " +
                "organization with a Business plan to continue.",
        )
    }

    private fun upgradeHint(subscription: EffectiveSubscription): String
    {
        val upgrade = subscription.upgradePlanCode ?: return ""
        return " Upgrade to ${describePlan(upgrade)} to continue."
    }

    private fun describePlan(planCode: PlanCode): String = when (planCode)
    {
        PlanCode.FREE -> "Free"
        PlanCode.PERSONAL -> "Personal"
        PlanCode.BUSINESS -> "Business"
    }

    private fun describeFeature(feature: PlanFeature): String = when (feature)
    {
        PlanFeature.EXCHANGE_CREATE -> "Creating an Exchange"
        PlanFeature.MULTIPLE_PARTICIPANTS -> "Adding more participants to an Exchange"
        PlanFeature.BLUEPRINT_USE -> "Starting an Exchange from a Blueprint"
        PlanFeature.BLUEPRINT_MANAGE -> "Creating and managing Blueprints"
        PlanFeature.DOCUMENT_LIBRARY_USE -> "Using the Document Library"
        PlanFeature.DOCUMENT_LIBRARY_MANAGE -> "Managing the Document Library"
        PlanFeature.DOCUMENT_COMMENTS -> "Commenting on documents"
        PlanFeature.DOCUMENT_VERSION_HISTORY -> "Document version history"
        PlanFeature.ADVANCED_ACCESS_CONTROLS -> "Advanced access controls"
        PlanFeature.VARIABLES_AND_SEQUENCES -> "Variables and sequences"
        PlanFeature.PERSONAL_REMINDERS -> "Reminders"
        PlanFeature.BUSINESS_FIELDS_AND_SCHEMAS -> "Business Fields and schemas"
        PlanFeature.WORKFLOW_AUTOMATION -> "Workflow automation"
        PlanFeature.ORGANIZATION_ADMINISTRATION -> "Organization administration"
        PlanFeature.AUDIT_GOVERNANCE -> "The audit and governance workspace"
        PlanFeature.IDENTITY_AND_INTEGRATIONS -> "Identity and integrations"
    }
}

