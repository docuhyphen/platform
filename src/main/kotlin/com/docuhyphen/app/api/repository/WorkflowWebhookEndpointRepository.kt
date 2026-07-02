package com.docuhyphen.app.api.repository

import com.docuhyphen.app.api.model.entity.WorkflowWebhookEndpoint
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class WorkflowWebhookEndpointRepository : BaseRepository<WorkflowWebhookEndpoint>(WorkflowWebhookEndpoint::class.java)
{
    fun findByOrganization(organizationId: UUID): List<WorkflowWebhookEndpoint> =
        entityManager.createQuery(
            "SELECT w FROM WorkflowWebhookEndpoint w WHERE w.ownerOrganizationId = :orgId ORDER BY w.createdDate DESC",
            WorkflowWebhookEndpoint::class.java,
        )
            .setParameter("orgId", organizationId)
            .resultList

    fun findActiveByWorkflowDefinition(workflowDefinitionId: UUID): List<WorkflowWebhookEndpoint> =
        entityManager.createQuery(
            "SELECT w FROM WorkflowWebhookEndpoint w WHERE w.workflowDefinitionId = :wfId AND w.isEnabled = true",
            WorkflowWebhookEndpoint::class.java,
        )
            .setParameter("wfId", workflowDefinitionId)
            .resultList
}
