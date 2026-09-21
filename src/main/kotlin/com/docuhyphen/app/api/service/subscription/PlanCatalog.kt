package com.docuhyphen.app.api.service.subscription

/**
 * The fixed, code-owned definition of every plan DocuHyphen sells.
 *
 * Plan defaults never change per tenant, so they live here rather than in the database.
 * Subscription status, purchased seat quantity, billing periods, and platform-admin feature
 * overrides are tenant state and are persisted instead.
 */
object PlanCatalog
{
    private val FREE = PlanDefinition(
        planCode = PlanCode.FREE,
        ownerType = SubscriptionOwnerType.USER,
        features = setOf(
            PlanFeature.EXCHANGE_CREATE,
            PlanFeature.DOCUMENT_COMMENTS,
        ),
        limits = PlanLimits(
            maxNewExchangesPerCalendarMonth = 5,
            maxOpenExchanges = 3,
            maxAdditionalParticipantsPerExchange = 0,
            includedSeats = 1,
            seatsArePurchased = false,
        ),
        upgradePlanCode = PlanCode.PERSONAL,
    )

    /**
     * Reminders are deliberately absent from this set.
     *
     * A reminder only exists as an addon on a step of a full workflow definition, so an
     * individual could not be given reminders without also being given the ability to author
     * workflows that run arbitrary actions, call out to external systems, and evaluate
     * organization-scoped conditions. Until a reminder can be expressed on its own terms, an
     * individual plan does not sell one.
     */
    private val PERSONAL = PlanDefinition(
        planCode = PlanCode.PERSONAL,
        ownerType = SubscriptionOwnerType.USER,
        features = setOf(
            PlanFeature.EXCHANGE_CREATE,
            PlanFeature.MULTIPLE_PARTICIPANTS,
            PlanFeature.BLUEPRINT_USE,
            PlanFeature.BLUEPRINT_MANAGE,
            PlanFeature.DOCUMENT_LIBRARY_USE,
            PlanFeature.DOCUMENT_LIBRARY_MANAGE,
            PlanFeature.DOCUMENT_COMMENTS,
            PlanFeature.DOCUMENT_VERSION_HISTORY,
            PlanFeature.ADVANCED_ACCESS_CONTROLS,
            PlanFeature.VARIABLES_AND_SEQUENCES,
            PlanFeature.INFORMATION_REQUESTS,
        ),
        limits = PlanLimits(
            maxNewExchangesPerCalendarMonth = null,
            maxOpenExchanges = null,
            maxAdditionalParticipantsPerExchange = null,
            includedSeats = 1,
            seatsArePurchased = false,
        ),
        upgradePlanCode = PlanCode.BUSINESS,
    )

    private val BUSINESS = PlanDefinition(
        planCode = PlanCode.BUSINESS,
        ownerType = SubscriptionOwnerType.ORGANIZATION,
        features = PERSONAL.features + setOf(
            PlanFeature.BUSINESS_FIELDS_AND_SCHEMAS,
            PlanFeature.WORKFLOW_AUTOMATION,
            // Reminders are step addons on a workflow, so they arrive with workflow authoring.
            PlanFeature.PERSONAL_REMINDERS,
            PlanFeature.ORGANIZATION_ADMINISTRATION,
            PlanFeature.AUDIT_GOVERNANCE,
            PlanFeature.IDENTITY_AND_INTEGRATIONS,
        ),
        limits = PlanLimits(
            maxNewExchangesPerCalendarMonth = null,
            maxOpenExchanges = null,
            maxAdditionalParticipantsPerExchange = null,
            includedSeats = null,
            seatsArePurchased = true,
        ),
        upgradePlanCode = null,
    )

    private val DEFINITIONS: Map<PlanCode, PlanDefinition> = mapOf(
        PlanCode.FREE to FREE,
        PlanCode.PERSONAL to PERSONAL,
        PlanCode.BUSINESS to BUSINESS,
    )

    /** Plan assigned to a newly registered individual account. */
    val DEFAULT_USER_PLAN: PlanCode = PlanCode.FREE

    /** Plan assigned to an organization once it becomes active. */
    val DEFAULT_ORGANIZATION_PLAN: PlanCode = PlanCode.BUSINESS

    fun definitionOf(planCode: PlanCode): PlanDefinition
    {
        return DEFINITIONS.getValue(planCode)
    }

    fun all(): List<PlanDefinition>
    {
        return DEFINITIONS.values.toList()
    }

    fun plansFor(ownerType: SubscriptionOwnerType): List<PlanDefinition>
    {
        return DEFINITIONS.values.filter { it.ownerType == ownerType }
    }

    fun isAssignableTo(planCode: PlanCode, ownerType: SubscriptionOwnerType): Boolean
    {
        return definitionOf(planCode).ownerType == ownerType
    }

    /**
     * Validates that a plan may be assigned to the given kind of owner. Free and Personal are
     * individual plans, Business is the organization plan, and mixing them is rejected here so
     * the same rule applies to migrations, admin tooling, and provisioning code paths.
     */
    fun requireAssignableTo(planCode: PlanCode, ownerType: SubscriptionOwnerType)
    {
        if (!isAssignableTo(planCode, ownerType))
        {
            throw IllegalArgumentException(
                "Plan $planCode cannot be assigned to a subscription owner of type $ownerType",
            )
        }
    }
}



