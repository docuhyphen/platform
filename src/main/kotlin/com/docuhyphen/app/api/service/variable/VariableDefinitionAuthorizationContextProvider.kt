package com.docuhyphen.app.api.service.variable

import com.docuhyphen.app.api.model.entity.VariableScope
import com.docuhyphen.app.api.repository.variable.VariableDefinitionRepository
import com.docuhyphen.app.api.service.auth.authz.OwnerContext
import com.docuhyphen.app.api.service.auth.authz.ResourceAuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.ResourceAuthorizationContextProvider
import com.docuhyphen.app.api.service.auth.authz.ResourceKind
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

@ApplicationScoped
class VariableDefinitionAuthorizationContextProvider : ResourceAuthorizationContextProvider
{
    @Inject
    private lateinit var repository: VariableDefinitionRepository

    override val supportedKind: ResourceKind = ResourceKind.VARIABLE_DEFINITION

    override fun resolve(resourceId: UUID): ResourceAuthorizationContext?
    {
        val variable = repository.findById(resourceId) ?: return null

        val ownerContext = when (variable.scope)
        {
            VariableScope.PERSONAL -> OwnerContext.Personal(variable.createdByAppUserId)
            VariableScope.ORG     -> OwnerContext.Organization(variable.organizationId ?: return null)
        }

        return ResourceAuthorizationContext(
            ownerContext = ownerContext,
            isArchived = variable.isDeleted,
            isSuspended = false,
        )
    }
}
