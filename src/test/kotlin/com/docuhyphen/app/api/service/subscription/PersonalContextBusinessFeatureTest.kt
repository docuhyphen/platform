package com.docuhyphen.app.api.service.subscription

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.entity.UserSubscriptionPolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.*

/**
 * Confirms that the features an organization pays for can never be unlocked by an individual
 * account, whichever individual plan that account holds.
 *
 * These refusals name Business as the plan that lifts them rather than offering an upgrade to
 * Personal, because no individual plan will ever include them.
 */
class PersonalContextBusinessFeatureTest
{
    private val appUserId: UUID = UUID.randomUUID()

    private val policyService: SubscriptionPolicyService = mock()

    private val accessService = SubscriptionAccessService(
        subscriptionPolicyService = policyService,
        subscriptionUsageService = SubscriptionUsageService(
            mock<ExchangeUsageCounter>(),
            mock<OrganizationSeatCounter>(),
        ),
        enforcementConfigService = SubscriptionEnforcementConfigService(
            SubscriptionEnforcementMode.ENFORCE.name,
        ),
        featureRolloutConfigService = FeatureRolloutConfigService(Optional.empty()),
    )

    private fun givenUserPlan(planCode: PlanCode)
    {
        whenever(policyService.findUserPolicy(appUserId)).thenReturn(
            UserSubscriptionPolicy().apply {
                this.appUserId = this@PersonalContextBusinessFeatureTest.appUserId
                this.planCode = planCode.name
                this.subscriptionStatus = SubscriptionStatus.ACTIVE.name
            },
        )
    }

    @Test
    fun `no individual plan can reach a feature an organization pays for`()
    {
        val organizationOnlyFeatures = listOf(
            PlanFeature.BUSINESS_FIELDS_AND_SCHEMAS,
            PlanFeature.WORKFLOW_AUTOMATION,
            PlanFeature.PERSONAL_REMINDERS,
            PlanFeature.ORGANIZATION_ADMINISTRATION,
            PlanFeature.AUDIT_GOVERNANCE,
            PlanFeature.IDENTITY_AND_INTEGRATIONS,
        )

        listOf(PlanCode.FREE, PlanCode.PERSONAL).forEach { planCode ->
            givenUserPlan(planCode)

            organizationOnlyFeatures.forEach { feature ->
                val denial = assertThrows<SubscriptionDenialException> {
                    accessService.requireFeature(SubscriptionContext.forUser(appUserId), feature)
                }.denial

                assertEquals(
                    SubscriptionDenialReason.ORGANIZATION_SUBSCRIPTION_REQUIRED,
                    denial.reason,
                    "$feature on $planCode must point at an organization subscription",
                )
                assertEquals(PlanCode.BUSINESS, denial.upgradePlanCode)
            }
        }
    }
}



