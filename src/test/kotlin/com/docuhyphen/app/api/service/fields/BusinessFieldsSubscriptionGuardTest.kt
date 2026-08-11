package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.FieldScopeKind
import com.docuhyphen.app.api.service.subscription.PlanFeature
import com.docuhyphen.app.api.service.subscription.SubscriptionAccessService
import com.docuhyphen.app.api.service.subscription.SubscriptionContext
import com.docuhyphen.app.api.service.subscription.SubscriptionEnforcementMode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class BusinessFieldsSubscriptionGuardTest
{
    private val accessService = mock<SubscriptionAccessService>()
    private val guard = BusinessFieldsSubscriptionGuard(accessService)

    @Test
    fun `off mode does not resolve a subscription owner`()
    {
        whenever(accessService.enforcementMode()).thenReturn(SubscriptionEnforcementMode.OFF)

        guard.requireConfigurationMutation(FieldScopeKind.ORGANIZATION, null)

        verify(accessService, never()).requireMutationAllowed(any(), any())
    }

    @Test
    fun `platform configuration is exempt from commercial checks`()
    {
        whenever(accessService.enforcementMode()).thenReturn(SubscriptionEnforcementMode.ENFORCE)

        guard.requireConfigurationMutation(FieldScopeKind.PLATFORM, null)

        verify(accessService, never()).requireMutationAllowed(any(), any())
    }

    @Test
    fun `organization configuration checks status and Business Fields entitlement`()
    {
        val organizationId = UUID.randomUUID()
        val context = SubscriptionContext.forOrganization(organizationId)
        whenever(accessService.enforcementMode()).thenReturn(SubscriptionEnforcementMode.ENFORCE)

        guard.requireConfigurationMutation(FieldScopeKind.ORGANIZATION, organizationId)

        verify(accessService).requireMutationAllowed(org.mockito.kotlin.eq(context), any())
        verify(accessService).requireFeature(context, PlanFeature.BUSINESS_FIELDS_AND_SCHEMAS)
    }

    @Test
    fun `resource mutation requires a persisted subscription owner`()
    {
        whenever(accessService.enforcementMode()).thenReturn(SubscriptionEnforcementMode.ENFORCE)

        assertThrows<IllegalArgumentException> {
            guard.requireResourceMutation(null)
        }
    }
}
