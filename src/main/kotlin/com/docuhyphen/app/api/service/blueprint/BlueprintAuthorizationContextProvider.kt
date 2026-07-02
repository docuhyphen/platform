package com.docuhyphen.app.api.service.blueprint

import com.docuhyphen.app.api.model.entity.BlueprintScope
import com.docuhyphen.app.api.repository.BlueprintDefinitionRepository
import com.docuhyphen.app.api.service.auth.authz.OwnerContext
import com.docuhyphen.app.api.service.auth.authz.ResourceAuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.ResourceAuthorizationContextProvider
import com.docuhyphen.app.api.service.auth.authz.ResourceKind
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

@ApplicationScoped
class BlueprintAuthorizationContextProvider : ResourceAuthorizationContextProvider
{
    @Inject
    private lateinit var repository: BlueprintDefinitionRepository

    override val supportedKind: ResourceKind = ResourceKind.BLUEPRINT

    override fun resolve(resourceId: UUID): ResourceAuthorizationContext?
    {
        val blueprint = repository.findById(resourceId) ?: return null

        val ownerContext = when (blueprint.scope)
        {
            BlueprintScope.PERSONAL -> OwnerContext.Personal(blueprint.createdByAppUserId ?: return null)
            BlueprintScope.ORG     -> OwnerContext.Organization(blueprint.organizationId ?: return null)
            BlueprintScope.APP     -> OwnerContext.Platform
        }

        return ResourceAuthorizationContext(
            ownerContext = ownerContext,
            isArchived = blueprint.isDeleted,
            isSuspended = false,
        )
    }
}
