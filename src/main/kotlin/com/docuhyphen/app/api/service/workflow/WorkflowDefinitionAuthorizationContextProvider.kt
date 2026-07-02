package com.docuhyphen.app.api.service.workflow

import com.docuhyphen.app.api.model.entity.WorkflowScope
import com.docuhyphen.app.api.repository.WorkflowDefinitionRepository
import com.docuhyphen.app.api.service.auth.authz.OwnerContext
import com.docuhyphen.app.api.service.auth.authz.ResourceAuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.ResourceAuthorizationContextProvider
import com.docuhyphen.app.api.service.auth.authz.ResourceKind
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

@ApplicationScoped
class WorkflowDefinitionAuthorizationContextProvider : ResourceAuthorizationContextProvider
{
    @Inject
    private lateinit var repository: WorkflowDefinitionRepository

    override val supportedKind: ResourceKind = ResourceKind.WORKFLOW_DEFINITION

    override fun resolve(resourceId: UUID): ResourceAuthorizationContext?
    {
        val definition = repository.findById(resourceId) ?: return null

        val ownerContext = when (definition.scope)
        {
            WorkflowScope.PERSONAL -> OwnerContext.Personal(definition.createdByAppUserId ?: return null)
            WorkflowScope.ORG     -> OwnerContext.Organization(definition.organizationId ?: return null)
            WorkflowScope.APP     -> OwnerContext.Platform
        }

        return ResourceAuthorizationContext(
            ownerContext = ownerContext,
            isArchived = definition.isDeleted,
            isSuspended = false,
        )
    }
}
