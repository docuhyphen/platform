package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.OrganizationSubscriptionPolicy
import com.docuhyphen.app.api.model.entity.UserSubscriptionPolicy
import com.docuhyphen.app.api.service.subscription.ExchangeUsageCounter
import com.docuhyphen.app.api.service.subscription.OrganizationSeatCounter
import com.docuhyphen.app.api.service.subscription.PlanCode
import com.docuhyphen.app.api.service.subscription.PlanFeature
import com.docuhyphen.app.api.service.subscription.SubscriptionAccessService
import com.docuhyphen.app.api.service.subscription.SubscriptionDenialReason
import com.docuhyphen.app.api.service.subscription.SubscriptionEnforcementConfigService
import com.docuhyphen.app.api.service.subscription.SubscriptionEnforcementMode
import com.docuhyphen.app.api.service.subscription.SubscriptionPolicyService
import com.docuhyphen.app.api.service.subscription.SubscriptionStatus
import com.docuhyphen.app.api.service.subscription.SubscriptionUsageService
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * Covers the allowances for the features consumed inside an existing Exchange, and in particular
 * that they are charged to the Exchange owner rather than to whoever happens to be calling.
 */
class ExchangeFeatureSubscriptionGuardTest
{
    private val ownerUserId: UUID = UUID.randomUUID()
    private val organizationId: UUID = UUID.randomUUID()

    private val policyService: SubscriptionPolicyService = mock()

    private fun guard(
        mode: SubscriptionEnforcementMode = SubscriptionEnforcementMode.ENFORCE,
    ): ExchangeFeatureSubscriptionGuard
    {
        val accessService = SubscriptionAccessService(
            subscriptionPolicyService = policyService,
            subscriptionUsageService = SubscriptionUsageService(
                mock<ExchangeUsageCounter>(),
                mock<OrganizationSeatCounter>(),
            ),
            enforcementConfigService = SubscriptionEnforcementConfigService(mode.name),
        )

        return ExchangeFeatureSubscriptionGuard(accessService)
    }

    private fun givenOwnerPlan(planCode: PlanCode, status: SubscriptionStatus = SubscriptionStatus.ACTIVE)
    {
        whenever(policyService.findUserPolicy(ownerUserId)).thenReturn(
            UserSubscriptionPolicy().apply {
                this.appUserId = ownerUserId
                this.planCode = planCode.name
                this.subscriptionStatus = status.name
            },
        )
    }

    private fun givenOrganizationPlan()
    {
        whenever(policyService.findOrganizationPolicy(organizationId)).thenReturn(
            OrganizationSubscriptionPolicy().apply {
                this.tierCode = PlanCode.BUSINESS.name
                this.subscriptionStatus = SubscriptionStatus.ACTIVE.name
            },
        )
        whenever(policyService.organizationFeatureOverrides(organizationId)).thenReturn(emptyMap())
    }

    private fun personallyOwnedExchange() = Exchange().apply {
        this.ownerUserId = this@ExchangeFeatureSubscriptionGuardTest.ownerUserId
    }

    private fun organizationOwnedExchange() = Exchange().apply {
        this.ownerOrganizationId = organizationId
    }

    @Test
    fun `a new version on a free owner's exchange is refused`()
    {
        givenOwnerPlan(PlanCode.FREE)

        val denial = assertThrows<SubscriptionDenialException> {
            guard().requireDocumentVersionHistory(personallyOwnedExchange())
        }.denial

        assertEquals(PlanFeature.DOCUMENT_VERSION_HISTORY, denial.feature)
    }

    @Test
    fun `an organization owned exchange uses the organization plan`()
    {
        givenOwnerPlan(PlanCode.FREE)
        givenOrganizationPlan()

        assertDoesNotThrow { guard().requireDocumentVersionHistory(organizationOwnedExchange()) }
    }

    @Test
    fun `a plain role grant carries no advanced control and stays available`()
    {
        givenOwnerPlan(PlanCode.FREE)

        assertDoesNotThrow {
            guard().requireAdvancedAccessControls(personallyOwnedExchange(), null, null)
        }
        assertDoesNotThrow {
            guard().requireAdvancedAccessControls(personallyOwnedExchange(), "  ", null)
        }
    }

    @Test
    fun `constraining a grant is refused on a free owner's exchange`()
    {
        givenOwnerPlan(PlanCode.FREE)

        val denial = assertThrows<SubscriptionDenialException> {
            guard().requireAdvancedAccessControls(
                personallyOwnedExchange(),
                """{"watermark":true}""",
                null,
            )
        }.denial

        assertEquals(PlanFeature.ADVANCED_ACCESS_CONTROLS, denial.feature)
    }

    @Test
    fun `an access window is refused on a free owner's exchange`()
    {
        givenOwnerPlan(PlanCode.FREE)

        assertThrows<SubscriptionDenialException> {
            guard().requireAdvancedAccessControls(personallyOwnedExchange(), null, 1L)
        }
    }

    @Test
    fun `constraining a grant is allowed on a personal owner's exchange`()
    {
        givenOwnerPlan(PlanCode.PERSONAL)

        assertDoesNotThrow {
            guard().requireAdvancedAccessControls(
                personallyOwnedExchange(),
                """{"require_mfa":true}""",
                null,
            )
        }
    }

    @Test
    fun `a suspended owner may not add a new document version`()
    {
        givenOwnerPlan(PlanCode.PERSONAL, SubscriptionStatus.SUSPENDED)

        val denial = assertThrows<SubscriptionDenialException> {
            guard().requireDocumentVersionHistory(personallyOwnedExchange())
        }.denial

        assertEquals(SubscriptionDenialReason.SUBSCRIPTION_SUSPENDED, denial.reason)
    }

    @Test
    fun `an exchange with no recorded owner is left alone`()
    {
        assertDoesNotThrow { guard().requireDocumentVersionHistory(Exchange()) }

        verifyNoInteractions(policyService)
    }

    @Test
    fun `report only mode records the refusal and lets the operation through`()
    {
        givenOwnerPlan(PlanCode.FREE)

        assertDoesNotThrow {
            guard(SubscriptionEnforcementMode.REPORT_ONLY)
                .requireDocumentVersionHistory(personallyOwnedExchange())
        }
    }

    @Test
    fun `disabled enforcement performs no plan work at all`()
    {
        val disabled = guard(SubscriptionEnforcementMode.OFF)

        disabled.requireDocumentVersionHistory(personallyOwnedExchange())
        disabled.requireAdvancedAccessControls(
            personallyOwnedExchange(),
            """{"watermark":true}""",
            null,
        )

        verifyNoInteractions(policyService)
    }
}



