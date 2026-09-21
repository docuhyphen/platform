package com.docuhyphen.app.api.service.subscription

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.entity.OrganizationSubscriptionPolicy
import com.docuhyphen.app.api.model.entity.UserSubscriptionPolicy
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Instant
import java.util.*

class SubscriptionAccessServiceTest
{
    private val appUserId: UUID = UUID.randomUUID()
    private val organizationId: UUID = UUID.randomUUID()
    private val now: Instant = Instant.parse("2026-08-10T12:00:00Z")

    private val policyService: SubscriptionPolicyService = mock()
    private val exchangeUsageCounter: ExchangeUsageCounter = mock()
    private val organizationSeatCounter: OrganizationSeatCounter = mock()
    private val usageService = SubscriptionUsageService(exchangeUsageCounter, organizationSeatCounter)

    private fun service(mode: SubscriptionEnforcementMode): SubscriptionAccessService
    {
        return SubscriptionAccessService(
            subscriptionPolicyService = policyService,
            subscriptionUsageService = usageService,
            enforcementConfigService = SubscriptionEnforcementConfigService(mode.name),
        )
    }

    private fun givenUserPlan(
        planCode: PlanCode,
        status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
        gracePeriodEnd: Instant? = null,
        overrides: Map<PlanFeature, Boolean> = emptyMap(),
    )
    {
        val policy = UserSubscriptionPolicy().apply {
            this.appUserId = this@SubscriptionAccessServiceTest.appUserId
            this.planCode = planCode.name
            this.subscriptionStatus = status.name
            this.gracePeriodEnd = gracePeriodEnd?.let { Timestamp.from(it) }
        }
        whenever(policyService.findUserPolicy(appUserId)).thenReturn(policy)
        whenever(policyService.featureOverrides(SubscriptionContext.forUser(appUserId)))
            .thenReturn(overrides)
    }

    private fun givenOrganizationPlan(
        status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
        purchasedSeats: Long? = null,
        overrides: Map<PlanFeature, Boolean> = emptyMap(),
    )
    {
        val policy = OrganizationSubscriptionPolicy().apply {
            this.tierCode = PlanCode.BUSINESS.name
            this.subscriptionStatus = status.name
            this.maxUsers = purchasedSeats
        }
        whenever(policyService.findOrganizationPolicy(organizationId)).thenReturn(policy)
        whenever(policyService.featureOverrides(SubscriptionContext.forOrganization(organizationId)))
            .thenReturn(overrides)
    }

    private fun userContext() = SubscriptionContext.forUser(appUserId)

    private fun organizationContext() = SubscriptionContext.forOrganization(organizationId)

    @Test
    fun `personal context resolves the individual plan of the authenticated user`()
    {
        givenUserPlan(PlanCode.FREE)

        val resolved = service(SubscriptionEnforcementMode.ENFORCE).resolve(userContext())

        assertEquals(PlanCode.FREE, resolved.planCode)
        assertEquals(SubscriptionOwnerType.USER, resolved.ownerType)
        assertEquals(appUserId, resolved.ownerId)
    }

    @Test
    fun `selecting an organization moves the paying subject to that organization`()
    {
        givenUserPlan(PlanCode.PERSONAL)
        givenOrganizationPlan(purchasedSeats = 10)

        val service = service(SubscriptionEnforcementMode.ENFORCE)

        val personal = service.resolveForSession(appUserId, null)
        assertEquals(PlanCode.PERSONAL, personal.planCode)
        assertEquals(appUserId, personal.ownerId)

        val organization = service.resolveForSession(appUserId, organizationId)
        assertEquals(PlanCode.BUSINESS, organization.planCode)
        assertEquals(organizationId, organization.ownerId)
    }

    @Test
    fun `a missing individual record is provisioned rather than assumed`()
    {
        whenever(policyService.findUserPolicy(appUserId)).thenReturn(null)
        whenever(policyService.ensureUserPolicy(any(), any(), any())).thenReturn(
            UserSubscriptionPolicy().apply {
                this.appUserId = this@SubscriptionAccessServiceTest.appUserId
                this.planCode = PlanCode.FREE.name
                this.subscriptionStatus = SubscriptionStatus.ACTIVE.name
            },
        )

        val resolved = service(SubscriptionEnforcementMode.ENFORCE).resolve(userContext())

        assertEquals(PlanCode.FREE, resolved.planCode)
    }

    @Test
    fun `organization overrides take precedence over the business defaults`()
    {
        givenOrganizationPlan(overrides = mapOf(PlanFeature.WORKFLOW_AUTOMATION to false))

        val service = service(SubscriptionEnforcementMode.ENFORCE)
        val resolved = service.resolve(organizationContext())

        assertFalse(resolved.hasFeature(PlanFeature.WORKFLOW_AUTOMATION))
        assertThrows<SubscriptionDenialException> {
            service.requireFeature(organizationContext(), PlanFeature.WORKFLOW_AUTOMATION)
        }
        assertDoesNotThrow {
            service.requireFeature(organizationContext(), PlanFeature.AUDIT_GOVERNANCE)
        }
    }

    @Test
    fun `an individual grant reaches a feature no individual plan sells`()
    {
        givenUserPlan(
            PlanCode.PERSONAL,
            overrides = mapOf(PlanFeature.WORKFLOW_AUTOMATION to true),
        )

        val service = service(SubscriptionEnforcementMode.ENFORCE)
        val resolved = service.resolve(userContext())

        assertTrue(resolved.hasFeature(PlanFeature.WORKFLOW_AUTOMATION))
        assertDoesNotThrow {
            service.requireFeature(userContext(), PlanFeature.WORKFLOW_AUTOMATION)
        }
    }

    @Test
    fun `an admin information request entitlement is immediately available`()
    {
        givenOrganizationPlan(
            overrides = mapOf(PlanFeature.INFORMATION_REQUESTS to true),
        )

        val service = service(SubscriptionEnforcementMode.ENFORCE)
        val subscription = service.resolve(organizationContext())

        assertTrue(service.isFeatureAvailable(organizationContext(), PlanFeature.INFORMATION_REQUESTS))
        assertTrue(service.availableFeatures(subscription).contains(PlanFeature.INFORMATION_REQUESTS))
        assertDoesNotThrow {
            service.requireFeature(organizationContext(), PlanFeature.INFORMATION_REQUESTS)
        }
    }

    @Test
    fun `an individual withdrawal removes a feature the plan grants`()
    {
        givenUserPlan(
            PlanCode.PERSONAL,
            overrides = mapOf(PlanFeature.BLUEPRINT_MANAGE to false),
        )

        val service = service(SubscriptionEnforcementMode.ENFORCE)

        assertFalse(service.resolve(userContext()).hasFeature(PlanFeature.BLUEPRINT_MANAGE))
        assertThrows<SubscriptionDenialException> {
            service.requireFeature(userContext(), PlanFeature.BLUEPRINT_MANAGE)
        }
    }

    @Test
    fun `the Personal plan includes information requests`()
    {
        givenUserPlan(PlanCode.PERSONAL)

        assertDoesNotThrow {
            service(SubscriptionEnforcementMode.ENFORCE)
                .requireFeature(userContext(), PlanFeature.INFORMATION_REQUESTS)
        }
    }

    @Test
    fun `an excluded feature is refused with an upgrade path`()
    {
        givenUserPlan(PlanCode.FREE)

        val denial = assertThrows<SubscriptionDenialException> {
            service(SubscriptionEnforcementMode.ENFORCE)
                .requireFeature(userContext(), PlanFeature.DOCUMENT_VERSION_HISTORY)
        }.denial

        assertEquals(SubscriptionDenialReason.FEATURE_NOT_INCLUDED, denial.reason)
        assertEquals(PlanCode.FREE, denial.planCode)
        assertEquals(PlanFeature.DOCUMENT_VERSION_HISTORY, denial.feature)
        assertEquals(PlanCode.PERSONAL, denial.upgradePlanCode)
    }

    @Test
    fun `an organization only feature tells an individual to select an organization`()
    {
        givenUserPlan(PlanCode.PERSONAL)

        val denial = assertThrows<SubscriptionDenialException> {
            service(SubscriptionEnforcementMode.ENFORCE)
                .requireFeature(userContext(), PlanFeature.WORKFLOW_AUTOMATION)
        }.denial

        assertEquals(SubscriptionDenialReason.ORGANIZATION_SUBSCRIPTION_REQUIRED, denial.reason)
        assertEquals(PlanCode.BUSINESS, denial.upgradePlanCode)
    }

    @Test
    fun `subscription status clamps mutations without touching reads`()
    {
        givenUserPlan(PlanCode.PERSONAL, SubscriptionStatus.SUSPENDED)
        val service = service(SubscriptionEnforcementMode.ENFORCE)

        val denial = assertThrows<SubscriptionDenialException> {
            service.requireMutationAllowed(userContext(), now)
        }.denial

        assertEquals(SubscriptionDenialReason.SUBSCRIPTION_SUSPENDED, denial.reason)
        assertTrue(service.resolve(userContext()).status.permitsReads)
    }

    @Test
    fun `a past due owner may still work until the grace period ends`()
    {
        givenUserPlan(PlanCode.PERSONAL, SubscriptionStatus.PAST_DUE, now.plusSeconds(3600))
        assertDoesNotThrow {
            service(SubscriptionEnforcementMode.ENFORCE).requireMutationAllowed(userContext(), now)
        }

        givenUserPlan(PlanCode.PERSONAL, SubscriptionStatus.PAST_DUE, now.minusSeconds(1))
        val denial = assertThrows<SubscriptionDenialException> {
            service(SubscriptionEnforcementMode.ENFORCE).requireMutationAllowed(userContext(), now)
        }.denial
        assertEquals(SubscriptionDenialReason.SUBSCRIPTION_PAST_DUE, denial.reason)
    }

    @Test
    fun `the monthly Exchange allowance is refused once it is consumed`()
    {
        givenUserPlan(PlanCode.FREE)
        whenever(exchangeUsageCounter.countCreatedBetween(any(), any(), any())).thenReturn(5)
        whenever(exchangeUsageCounter.countOpen(any())).thenReturn(0)

        val denial = assertThrows<SubscriptionDenialException> {
            service(SubscriptionEnforcementMode.ENFORCE).requireExchangeCapacity(userContext(), now)
        }.denial

        assertEquals(SubscriptionDenialReason.PLAN_LIMIT_REACHED, denial.reason)
        assertEquals(5L, denial.currentValue)
        assertEquals(5L, denial.limit)
    }

    @Test
    fun `the open Exchange allowance is refused even when monthly capacity remains`()
    {
        givenUserPlan(PlanCode.FREE)
        whenever(exchangeUsageCounter.countCreatedBetween(any(), any(), any())).thenReturn(1)
        whenever(exchangeUsageCounter.countOpen(any())).thenReturn(3)

        val denial = assertThrows<SubscriptionDenialException> {
            service(SubscriptionEnforcementMode.ENFORCE).requireExchangeCapacity(userContext(), now)
        }.denial

        assertEquals(SubscriptionDenialReason.PLAN_LIMIT_REACHED, denial.reason)
        assertEquals(3L, denial.currentValue)
        assertEquals(3L, denial.limit)
    }

    @Test
    fun `an uncapped plan is never measured against Exchange allowances`()
    {
        givenUserPlan(PlanCode.PERSONAL)

        assertDoesNotThrow {
            service(SubscriptionEnforcementMode.ENFORCE).requireExchangeCapacity(userContext(), now)
        }
    }

    @Test
    fun `additional participants are refused on a plan that does not include them`()
    {
        givenUserPlan(PlanCode.FREE)

        val denial = assertThrows<SubscriptionDenialException> {
            service(SubscriptionEnforcementMode.ENFORCE).requireParticipantCapacity(userContext(), 1)
        }.denial

        assertEquals(SubscriptionDenialReason.FEATURE_NOT_INCLUDED, denial.reason)
        assertEquals(PlanFeature.MULTIPLE_PARTICIPANTS, denial.feature)
    }

    @Test
    fun `a primary recipient alone never consumes the participant allowance`()
    {
        givenUserPlan(PlanCode.FREE)

        assertDoesNotThrow {
            service(SubscriptionEnforcementMode.ENFORCE).requireParticipantCapacity(userContext(), 0)
        }
    }

    @Test
    fun `seats are refused once every purchased seat is in use`()
    {
        givenOrganizationPlan(purchasedSeats = 3)
        whenever(organizationSeatCounter.countActiveSeats(organizationId)).thenReturn(3)

        val denial = assertThrows<SubscriptionDenialException> {
            service(SubscriptionEnforcementMode.ENFORCE).requireOrganizationSeat(organizationId)
        }.denial

        assertEquals(SubscriptionDenialReason.SEAT_LIMIT_REACHED, denial.reason)
        assertEquals(3L, denial.currentValue)
        assertEquals(3L, denial.limit)
    }

    @Test
    fun `an organization with no assigned seat quantity stays uncapped`()
    {
        givenOrganizationPlan(purchasedSeats = null)

        assertDoesNotThrow {
            service(SubscriptionEnforcementMode.ENFORCE).requireOrganizationSeat(organizationId)
        }
    }

    @Test
    fun `report only mode records the decision without refusing the request`()
    {
        givenUserPlan(PlanCode.FREE)
        whenever(exchangeUsageCounter.countCreatedBetween(any(), any(), any())).thenReturn(99)
        whenever(exchangeUsageCounter.countOpen(any())).thenReturn(99)

        val service = service(SubscriptionEnforcementMode.REPORT_ONLY)

        assertDoesNotThrow { service.requireExchangeCapacity(userContext(), now) }
        assertDoesNotThrow { service.requireFeature(userContext(), PlanFeature.DOCUMENT_VERSION_HISTORY) }
        assertEquals(SubscriptionEnforcementMode.REPORT_ONLY, service.enforcementMode())
    }

    @Test
    fun `report only mode records a consumed seat without refusing activation`()
    {
        givenOrganizationPlan(purchasedSeats = 3)
        whenever(organizationSeatCounter.countActiveSeats(organizationId)).thenReturn(3)

        assertDoesNotThrow {
            service(SubscriptionEnforcementMode.REPORT_ONLY).requireOrganizationSeat(organizationId)
        }
    }

    @Test
    fun `disabled enforcement skips evaluation entirely`()
    {
        val service = service(SubscriptionEnforcementMode.OFF)

        assertDoesNotThrow { service.requireFeature(userContext(), PlanFeature.WORKFLOW_AUTOMATION) }
        assertDoesNotThrow { service.requireExchangeCapacity(userContext(), now) }
        assertDoesNotThrow { service.requireOrganizationSeat(organizationId) }
    }

    @Test
    fun `an unrecognised configured mode falls back to enforced decisions`()
    {
        assertEquals(
            SubscriptionEnforcementMode.ENFORCE,
            SubscriptionEnforcementConfigService("not-a-mode").mode(),
        )
    }
}
