package com.docuhyphen.app.api.service.subscription

import com.docuhyphen.app.api.model.subscription.PlanDefinition
import com.docuhyphen.app.api.model.subscription.PlanLimits

object PlanCatalog
{
    const val POLICY_VERSION = "1"

    private const val MEBIBYTE = 1024L * 1024L
    private const val GIBIBYTE = 1024L * MEBIBYTE

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
            maxOpenInformationRequests = 0,
            maxActingPartiesPerInformationRequest = 0,
            maxEvidenceFilesPerInformationRequest = 0,
            maxEvidenceBytesPerInformationRequest = 0,
            maxCommittedEvidenceBytes = 0,
        ),
        upgradePlanCode = PlanCode.PERSONAL,
    )

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
            maxOpenInformationRequests = 25,
            maxActingPartiesPerInformationRequest = 10,
            maxEvidenceFilesPerInformationRequest = 100,
            maxEvidenceBytesPerInformationRequest = 250 * MEBIBYTE,
            maxCommittedEvidenceBytes = 5 * GIBIBYTE,
        ),
        upgradePlanCode = PlanCode.BUSINESS,
    )

    private val BUSINESS = PlanDefinition(
        planCode = PlanCode.BUSINESS,
        ownerType = SubscriptionOwnerType.ORGANIZATION,
        features = PERSONAL.features + setOf(
            PlanFeature.BUSINESS_FIELDS_AND_SCHEMAS,
            PlanFeature.WORKFLOW_AUTOMATION,
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
            maxOpenInformationRequests = null,
            maxActingPartiesPerInformationRequest = 100,
            maxEvidenceFilesPerInformationRequest = 200,
            maxEvidenceBytesPerInformationRequest = 500 * MEBIBYTE,
            maxCommittedEvidenceBytes = 100 * GIBIBYTE,
        ),
        upgradePlanCode = null,
    )

    private val DEFINITIONS: Map<PlanCode, PlanDefinition> = mapOf(
        PlanCode.FREE to FREE,
        PlanCode.PERSONAL to PERSONAL,
        PlanCode.BUSINESS to BUSINESS,
    )

    val DEFAULT_USER_PLAN: PlanCode = PlanCode.FREE

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



