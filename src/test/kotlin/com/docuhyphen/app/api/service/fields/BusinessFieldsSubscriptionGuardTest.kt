package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.FieldScopeKind
import com.docuhyphen.app.api.service.subscription.PlanFeature
import com.docuhyphen.app.api.service.subscription.SubscriptionAccessService
import com.docuhyphen.app.api.service.subscription.SubscriptionContext
import com.docuhyphen.app.api.service.subscription.SubscriptionEnforcementMode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.util.*

class BusinessFieldsSubscriptionGuardTest
{
    private val accessService = mock<SubscriptionAccessService>()
    private val guard = BusinessFieldsSubscriptionGuard(accessService)

    @Test
    fun `off mode does not resolve a subscription owner`()
    {
        whenever(accessService.enforcementMode()).thenReturn(SubscriptionEnforcementMode.OFF)

        guard.requireConfigurationMutation(FieldScopeKind.ORGANIZATION, null, null)

        verify(accessService, never()).requireMutationAllowed(any(), any())
    }

    @Test
    fun `platform configuration is exempt from commercial checks`()
    {
        whenever(accessService.enforcementMode()).thenReturn(SubscriptionEnforcementMode.ENFORCE)

        guard.requireConfigurationMutation(FieldScopeKind.PLATFORM, null, null)

        verify(accessService, never()).requireMutationAllowed(any(), any())
    }

    @Test
    fun `organization configuration checks status and Business Fields entitlement`()
    {
        val organizationId = UUID.randomUUID()
        val context = SubscriptionContext.forOrganization(organizationId)
        whenever(accessService.enforcementMode()).thenReturn(SubscriptionEnforcementMode.ENFORCE)

        guard.requireConfigurationMutation(FieldScopeKind.ORGANIZATION, organizationId, null)

        verify(accessService).requireMutationAllowed(org.mockito.kotlin.eq(context), any())
        verify(accessService).requireFeature(context, PlanFeature.BUSINESS_FIELDS_AND_SCHEMAS)
    }

    @Test
    fun `personal configuration bills the person who owns it`()
    {
        val ownerUserId = UUID.randomUUID()
        val context = SubscriptionContext.forUser(ownerUserId)
        whenever(accessService.enforcementMode()).thenReturn(SubscriptionEnforcementMode.ENFORCE)

        guard.requireConfigurationMutation(FieldScopeKind.PERSONAL, null, ownerUserId)

        verify(accessService).requireMutationAllowed(org.mockito.kotlin.eq(context), any())
        verify(accessService).requireFeature(context, PlanFeature.BUSINESS_FIELDS_AND_SCHEMAS)
    }

    @Test
    fun `each scope kind is billed to its own owner and to nobody else's`()
    {
        val organizationId = UUID.randomUUID()
        val ownerUserId = UUID.randomUUID()
        whenever(accessService.enforcementMode()).thenReturn(SubscriptionEnforcementMode.ENFORCE)

        assertThrows<IllegalArgumentException> {
            guard.requireConfigurationMutation(FieldScopeKind.PERSONAL, organizationId, null)
        }
        assertThrows<IllegalArgumentException> {
            guard.requireConfigurationMutation(FieldScopeKind.ORGANIZATION, null, ownerUserId)
        }
        verify(accessService, never()).requireMutationAllowed(any(), any())
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
