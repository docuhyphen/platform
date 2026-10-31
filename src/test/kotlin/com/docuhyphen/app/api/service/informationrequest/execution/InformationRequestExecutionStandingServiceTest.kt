package com.docuhyphen.app.api.service.informationrequest.execution

import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.entity.RequestExecutionGrant
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestExecutionStanding
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestExecutionStandingKind
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestPlanQuotas
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestStandingReason
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.subscription.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

class InformationRequestExecutionStandingServiceTest
{
    private val ownerUserId = UUID.randomUUID()
    private val exchange = Exchange().apply { ownerUserId = this@InformationRequestExecutionStandingServiceTest.ownerUserId }
    private val subscriptionAccessService = mock<SubscriptionAccessService>()
    private val executionGrantService = mock<InformationRequestExecutionGrantService>()
    private val requestRepository = mock<InformationRequestRepository>()
    private val service =
        InformationRequestExecutionStandingService(subscriptionAccessService, executionGrantService, requestRepository)
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

    @Test
    fun `an owner whose open requests fill the plan allowance cannot start new work and states its quotas`() {
        owner(SubscriptionStatus.ACTIVE)
        openRequests(25)
        whenever(executionGrantService.committedEvidenceBytes(SubscriptionContext.forUser(ownerUserId))).thenReturn(
            4_096L
        )

        val creation = service.creationStanding(exchange)

        assertEquals(InformationRequestStandingReason.OPEN_REQUEST_LIMIT_REACHED, creation.unavailableReason)
        assertFalse(creation.available)
        assertEquals(null, creation.owner.newWorkUnavailableReason)
        val limits = PlanCatalog.definitionOf(PlanCode.PERSONAL).limits
        assertEquals(
            InformationRequestPlanQuotas(
                maxOpenRequests = limits.maxOpenInformationRequests,
                openRequests = 25,
                maxActingPartiesPerRequest = limits.maxActingPartiesPerInformationRequest,
                maxEvidenceFilesPerRequest = limits.maxEvidenceFilesPerInformationRequest,
                maxEvidenceBytesPerRequest = limits.maxEvidenceBytesPerInformationRequest,
                maxCommittedEvidenceBytes = limits.maxCommittedEvidenceBytes,
                committedEvidenceBytes = 4_096L,
            ),
            creation.quotas,
        )
        grant(revoked = false)
        assertEquals(standing(InformationRequestExecutionStandingKind.ACTIVE), standingOf(issued))
    }

    @Test
    fun `new work stays available below the open request allowance and while enforcement only reports`() {
        owner(SubscriptionStatus.ACTIVE)
        openRequests(24)
        assertEquals(null, service.creationStanding(exchange).unavailableReason)

        owner(SubscriptionStatus.ACTIVE, mode = SubscriptionEnforcementMode.REPORT_ONLY)
        openRequests(25)
        assertEquals(null, service.creationStanding(exchange).unavailableReason)
    }

    @Test
    fun `a lapse is the reason new work is unavailable before the open request allowance is counted`() {
        owner(SubscriptionStatus.ACTIVE, plan = PlanCode.FREE)
        openRequests(0)

        assertEquals(
            InformationRequestStandingReason.FEATURE_NOT_INCLUDED,
            service.creationStanding(exchange).unavailableReason
        )
    }

    private fun openRequests(count: Long) {
        whenever(requestRepository.countOpenForOwner(InformationRequestOwnerType.USER, ownerUserId)).thenReturn(count)
    }

    @Test
    fun `standings for several requests read the owner's subscription once and every grant in one query`() {
        owner(SubscriptionStatus.PAST_DUE, graceEnd = Instant.now().minus(1, ChronoUnit.DAYS))
        val continuing = ownedIssued()
        val stopped = ownedIssued()
        val unissued = ownedIssued()
        whenever(executionGrantService.findForRequests(setOf(continuing.id, stopped.id, unissued.id))).thenReturn(
            mapOf(
                continuing.id to RequestExecutionGrant().apply { requestId = continuing.id },
                stopped.id to RequestExecutionGrant().apply {
                    requestId = stopped.id
                    revokedAt = Timestamp.from(Instant.now())
                    revokedReason = "record review"
                },
            ),
        )

        val standings = service.standingsOf(listOf(continuing, stopped, unissued))

        assertEquals(
            mapOf(
                continuing.id to standing(
                    InformationRequestExecutionStandingKind.CONTINUING_AFTER_LAPSE,
                    InformationRequestStandingReason.SUBSCRIPTION_PAST_DUE
                ),
                stopped.id to standing(
                    InformationRequestExecutionStandingKind.EXECUTION_GRANT_REVOKED,
                    InformationRequestStandingReason.EXECUTION_GRANT_REVOKED
                ),
                unissued.id to standing(
                    InformationRequestExecutionStandingKind.NEW_WORK_UNAVAILABLE,
                    InformationRequestStandingReason.SUBSCRIPTION_PAST_DUE
                ),
            ),
            standings,
        )
        verify(subscriptionAccessService, times(1)).resolve(SubscriptionContext.forUser(ownerUserId))
        verify(executionGrantService, never()).findForRequest(any())
    }

    private fun ownedIssued() = InformationRequest().apply {
        id = UUID.randomUUID()
        state = InformationRequestState.ISSUED
        ownerType = InformationRequestOwnerType.USER
        ownerUserId = this@InformationRequestExecutionStandingServiceTest.ownerUserId
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
