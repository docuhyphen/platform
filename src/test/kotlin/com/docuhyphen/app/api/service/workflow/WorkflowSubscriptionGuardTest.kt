package com.docuhyphen.app.api.service.workflow

import com.docuhyphen.app.api.model.entity.WorkflowDefinition
import com.docuhyphen.app.api.model.entity.WorkflowScope
import com.docuhyphen.app.api.service.subscription.PlanFeature
import com.docuhyphen.app.api.service.subscription.SubscriptionAccessService
import com.docuhyphen.app.api.service.subscription.SubscriptionContext
import com.docuhyphen.app.api.service.subscription.SubscriptionEnforcementMode
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class WorkflowSubscriptionGuardTest
{
    private val accessService = mock<SubscriptionAccessService>()
    private val guard = WorkflowSubscriptionGuard(accessService)

    @Test
    fun `off mode avoids owner resolution`()
    {
        whenever(accessService.enforcementMode()).thenReturn(SubscriptionEnforcementMode.OFF)

        guard.requireDefinitionMutation(WorkflowScope.ORG, null, null)

        verify(accessService, never()).requireMutationAllowed(any(), any())
    }

    @Test
    fun `platform definition authoring remains a platform administration action`()
    {
        whenever(accessService.enforcementMode()).thenReturn(SubscriptionEnforcementMode.ENFORCE)

        guard.requireDefinitionMutation(WorkflowScope.APP, null, null)

        verify(accessService, never()).requireFeature(any(), any())
    }

    @Test
    fun `organization definition mutation uses its persisted organization owner`()
    {
        val organizationId = UUID.randomUUID()
        val context = SubscriptionContext.forOrganization(organizationId)
        whenever(accessService.enforcementMode()).thenReturn(SubscriptionEnforcementMode.ENFORCE)

        guard.requireDefinitionMutation(WorkflowScope.ORG, organizationId, UUID.randomUUID())

        verify(accessService).requireMutationAllowed(eq(context), any())
        verify(accessService).requireFeature(context, PlanFeature.WORKFLOW_AUTOMATION)
    }

    @Test
    fun `starting a platform workflow charges the requesting organization`()
    {
        val organizationId = UUID.randomUUID()
        val context = SubscriptionContext.forOrganization(organizationId)
        val definition = WorkflowDefinition().apply { scope = WorkflowScope.APP }
        whenever(accessService.enforcementMode()).thenReturn(SubscriptionEnforcementMode.ENFORCE)

        guard.requireInstanceStart(definition, organizationId)

        verify(accessService).requireFeature(context, PlanFeature.WORKFLOW_AUTOMATION)
    }

    @Test
    fun `running platform work without a customer organization is not commercially gated`()
    {
        val definition = WorkflowDefinition().apply { scope = WorkflowScope.APP }
        whenever(accessService.enforcementMode()).thenReturn(SubscriptionEnforcementMode.ENFORCE)

        guard.requireInstanceStart(definition, null)

        verify(accessService, never()).requireFeature(any(), any())
    }
}
