package com.docuhyphen.app.api.service.recordpreservation

import com.docuhyphen.app.api.model.entity.RecordPreservationScope
import com.docuhyphen.app.api.model.recordpreservation.RecordOwnerRef
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.subscription.OrganizationFeatureSubscriptionGuard
import com.docuhyphen.app.api.service.subscription.PlanFeature
import com.docuhyphen.app.api.service.subscription.SubscriptionAccessService
import com.docuhyphen.app.api.service.subscription.SubscriptionContext
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.util.UUID

class RecordPreservationEntitlementTest
{
    private val organizationId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val access = mock<RecordOwnerScopeAccess>()
    private val holds = mock<RecordPreservationHoldService>()
    private val schedules = mock<RecordRetentionScheduleService>()
    private val disposals = mock<RecordDisposalService>()
    private val organizationFeatures = mock<OrganizationFeatureSubscriptionGuard>()
    private val subscriptions = mock<SubscriptionAccessService>()
    private val administration = RecordPreservationAdministration(
        access, holds, schedules, disposals, RecordPreservationEntitlementGuard(organizationFeatures, subscriptions),
    )

    @Test
    fun `an organization changes holds and retention only with audit governance while reads stay open`()
    {
        val owner = RecordOwnerRef.organization(organizationId)
        whenever(access.currentOwner()).thenReturn(owner)
        whenever(access.requireAccess(any(), any())).thenReturn(PrincipalRef.user(userId))
        doThrow(IllegalStateException("audit governance required"))
            .whenever(organizationFeatures).requireMutation(organizationId, PlanFeature.AUDIT_GOVERNANCE)

        assertThrows(IllegalStateException::class.java) {
            administration.placeHold("INFORMATION_REQUEST", UUID.randomUUID().toString(), RecordPreservationScope.RESOURCE, "records review", null, null)
        }
        assertThrows(IllegalStateException::class.java) { administration.changeHoldScope(UUID.randomUUID(), RecordPreservationScope.RESOURCE, "narrowed") }
        assertThrows(IllegalStateException::class.java) { administration.releaseHold(UUID.randomUUID(), "review closed") }
        assertThrows(IllegalStateException::class.java) { administration.publishSchedule("INFORMATION_REQUEST", 30, 90) }
        verifyNoInteractions(holds, schedules)

        administration.holds(emptySet())
        verify(holds).holds(eq(owner), any())
        verifyNoInteractions(subscriptions)
    }

    @Test
    fun `a personal owner changes holds and retention only while their Information Requests entitlement allows it`()
    {
        whenever(access.currentOwner()).thenReturn(RecordOwnerRef.user(userId))
        whenever(access.requireAccess(any(), any())).thenReturn(PrincipalRef.user(userId))
        doThrow(IllegalStateException("not included in the plan"))
            .whenever(subscriptions).requireFeature(SubscriptionContext.forUser(userId), PlanFeature.INFORMATION_REQUESTS)

        assertThrows(IllegalStateException::class.java) { administration.publishSchedule("INFORMATION_REQUEST", 0, null) }
        assertThrows(IllegalStateException::class.java) {
            administration.placeHold("INFORMATION_REQUEST", UUID.randomUUID().toString(), RecordPreservationScope.RESOURCE, "records review", null, null)
        }

        verify(subscriptions, never()).requireFeature(SubscriptionContext.forUser(userId), PlanFeature.AUDIT_GOVERNANCE)
        verifyNoInteractions(holds, schedules, organizationFeatures)
    }
}
