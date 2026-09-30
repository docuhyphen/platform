package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.RequestExecutionGrant
import com.docuhyphen.app.api.model.informationrequest.InformationRequestExecutionStanding
import com.docuhyphen.app.api.model.informationrequest.InformationRequestExecutionStandingKind
import com.docuhyphen.app.api.model.informationrequest.InformationRequestStandingReason
import com.docuhyphen.app.api.service.subscription.EffectiveSubscription
import com.docuhyphen.app.api.service.subscription.PlanCatalog
import com.docuhyphen.app.api.service.subscription.PlanCode
import com.docuhyphen.app.api.service.subscription.PlanFeature
import com.docuhyphen.app.api.service.subscription.SubscriptionAccessService
import com.docuhyphen.app.api.service.subscription.SubscriptionContext
import com.docuhyphen.app.api.service.subscription.SubscriptionEnforcementMode
import com.docuhyphen.app.api.service.subscription.SubscriptionOwnerType
import com.docuhyphen.app.api.service.subscription.SubscriptionStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class InformationRequestExecutionStandingServiceTest
{
    private val ownerUserId = UUID.randomUUID()
    private val exchange = Exchange().apply { ownerUserId = this@InformationRequestExecutionStandingServiceTest.ownerUserId }
    private val subscriptionAccessService = mock<SubscriptionAccessService>()
    private val executionGrantService = mock<InformationRequestExecutionGrantService>()
    private val service = InformationRequestExecutionStandingService(subscriptionAccessService, executionGrantService)
    private val draft = InformationRequest().apply { state = InformationRequestState.DRAFT }
    private val issued = InformationRequest().apply { state = InformationRequestState.ISSUED }

    @Test
    fun `a draft of an owner whose plan allows new work is active`()
    {
        owner(SubscriptionStatus.ACTIVE)

        assertEquals(standing(InformationRequestExecutionStandingKind.ACTIVE), standingOf(draft))
    }

    @Test
    fun `a draft of an owner without the feature cannot proceed and says why`()
    {
        owner(SubscriptionStatus.ACTIVE, plan = PlanCode.FREE)

        assertEquals(
            standing(InformationRequestExecutionStandingKind.NEW_WORK_UNAVAILABLE, InformationRequestStandingReason.FEATURE_NOT_INCLUDED),
            standingOf(draft),
        )
    }

    @Test
    fun `issued work continues after a lapse and names the lapse`()
    {
        owner(SubscriptionStatus.PAST_DUE, graceEnd = Instant.now().minus(1, ChronoUnit.DAYS))
        grant(revoked = false)

        assertEquals(
            standing(InformationRequestExecutionStandingKind.CONTINUING_AFTER_LAPSE, InformationRequestStandingReason.SUBSCRIPTION_PAST_DUE),
            standingOf(issued),
        )
    }

    @Test
    fun `an ended trial is a lapse, never an operational suspension`()
    {
        owner(SubscriptionStatus.TRIALING, periodEnd = Instant.now().minus(1, ChronoUnit.DAYS))
        grant(revoked = false)

        assertEquals(
            standing(InformationRequestExecutionStandingKind.CONTINUING_AFTER_LAPSE, InformationRequestStandingReason.TRIAL_ENDED),
            standingOf(issued),
        )
        assertFalse(service.ownerStanding(exchange).operationallySuspended)
    }

    @Test
    fun `a revoked execution grant is stated even while the owner is active`()
    {
        owner(SubscriptionStatus.ACTIVE)
        grant(revoked = true)

        assertEquals(
            standing(InformationRequestExecutionStandingKind.EXECUTION_GRANT_REVOKED, InformationRequestStandingReason.EXECUTION_GRANT_REVOKED),
            standingOf(issued),
        )
    }

    @Test
    fun `an operational suspension is stated for drafts and issued work whatever the enforcement mode`()
    {
        SubscriptionEnforcementMode.entries.forEach { mode ->
            owner(SubscriptionStatus.SUSPENDED, mode = mode)
            val suspended = standing(
                InformationRequestExecutionStandingKind.OPERATIONALLY_SUSPENDED,
                InformationRequestStandingReason.SUBSCRIPTION_SUSPENDED,
            )

            grant(revoked = false)
            assertEquals(suspended, standingOf(issued), mode.name)
            whenever(executionGrantService.findForRequest(any())).thenReturn(null)
            assertEquals(suspended, standingOf(draft), mode.name)
            assertTrue(service.ownerStanding(exchange).operationallySuspended, mode.name)
        }
    }

    @Test
    fun `a report only deployment reports no commercial refusal for new work`()
    {
        owner(SubscriptionStatus.ACTIVE, plan = PlanCode.FREE, mode = SubscriptionEnforcementMode.REPORT_ONLY)

        assertEquals(standing(InformationRequestExecutionStandingKind.ACTIVE), standingOf(draft))
        assertTrue(service.ownerStanding(exchange).newWorkAvailable)
    }

    private fun standingOf(request: InformationRequest): InformationRequestExecutionStanding =
        service.standingOf(request, service.ownerStanding(exchange))

    private fun standing(kind: InformationRequestExecutionStandingKind, reason: InformationRequestStandingReason? = null) =
        InformationRequestExecutionStanding(kind, reason)

    private fun grant(revoked: Boolean)
    {
        whenever(executionGrantService.findForRequest(any())).thenReturn(
            RequestExecutionGrant().apply {
                if (revoked)
                {
                    revokedAt = Timestamp.from(Instant.now())
                    revokedReason = "record review"
                }
            },
        )
    }

    private fun owner(
        status: SubscriptionStatus,
        plan: PlanCode = PlanCode.PERSONAL,
        mode: SubscriptionEnforcementMode = SubscriptionEnforcementMode.ENFORCE,
        periodEnd: Instant? = null,
        graceEnd: Instant? = null,
    )
    {
        val definition = PlanCatalog.definitionOf(plan)
        whenever(subscriptionAccessService.enforcementMode()).thenReturn(mode)
        whenever(subscriptionAccessService.resolve(SubscriptionContext.forUser(ownerUserId))).thenReturn(
            EffectiveSubscription(
                planCode = plan,
                ownerType = SubscriptionOwnerType.USER,
                ownerId = ownerUserId,
                status = status,
                features = definition.features,
                limits = definition.limits,
                billingFrequency = null,
                currentPeriodStart = null,
                currentPeriodEnd = periodEnd,
                gracePeriodEnd = graceEnd,
                purchasedSeats = null,
                upgradePlanCode = definition.upgradePlanCode,
            ),
        )
        assertEquals(plan != PlanCode.FREE, definition.features.contains(PlanFeature.INFORMATION_REQUESTS))
    }
}
