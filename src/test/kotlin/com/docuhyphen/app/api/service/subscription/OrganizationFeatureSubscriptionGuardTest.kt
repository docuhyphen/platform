package com.docuhyphen.app.api.service.subscription

import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class OrganizationFeatureSubscriptionGuardTest
{
    private val accessService = mock<SubscriptionAccessService>()
    private val guard = OrganizationFeatureSubscriptionGuard(accessService)

    @Test
    fun `off mode does not inspect organization ownership`()
    {
        whenever(accessService.enforcementMode()).thenReturn(SubscriptionEnforcementMode.OFF)

        guard.requireMutation(UUID.randomUUID(), PlanFeature.AUDIT_GOVERNANCE)

        verify(accessService, never()).requireMutationAllowed(any(), any())
    }

    @Test
    fun `platform-owned operation has no customer subscription check`()
    {
        whenever(accessService.enforcementMode()).thenReturn(SubscriptionEnforcementMode.ENFORCE)

        guard.requireMutation(null, PlanFeature.IDENTITY_AND_INTEGRATIONS)

        verify(accessService, never()).requireFeature(any(), any())
    }

    @Test
    fun `organization mutation checks lifecycle before its requested feature`()
    {
        val organizationId = UUID.randomUUID()
        val context = SubscriptionContext.forOrganization(organizationId)
        whenever(accessService.enforcementMode()).thenReturn(SubscriptionEnforcementMode.ENFORCE)

        guard.requireMutation(organizationId, PlanFeature.AUDIT_GOVERNANCE)

        verify(accessService).requireMutationAllowed(eq(context), any())
        verify(accessService).requireFeature(context, PlanFeature.AUDIT_GOVERNANCE)
    }
}
