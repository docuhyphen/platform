package com.docuhyphen.app.api.service.audit

import com.docuhyphen.app.api.model.audit.AuditOwnerScope
import com.docuhyphen.app.api.model.auth.authz.OwnerContext
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.service.auth.authz.ResourceAuthorizationContextRegistry
import com.docuhyphen.app.api.model.auth.authz.ResourceRef
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class AuditOwnerScopeResolver @Inject constructor(
    private val resourceAuthorizationContextRegistry: ResourceAuthorizationContextRegistry,
)
{
    fun resolve(resourceType: ResourceType, resourceId: UUID): AuditOwnerScope =
        when (val owner =
            resourceAuthorizationContextRegistry.resolve(ResourceRef(resourceType, resourceId))?.ownerContext)
        {
            is OwnerContext.Organization -> AuditOwnerScope.Organization(owner.organizationId)
            is OwnerContext.Personal -> AuditOwnerScope.Personal(owner.userId)
            OwnerContext.Platform -> AuditOwnerScope.Platform
            null -> throw IllegalArgumentException("The audit owner of this resource could not be resolved")
        }
}

