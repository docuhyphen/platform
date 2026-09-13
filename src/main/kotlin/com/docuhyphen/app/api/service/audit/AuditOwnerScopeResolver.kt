package com.docuhyphen.app.api.service.audit

import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.service.auth.authz.OwnerContext
import com.docuhyphen.app.api.service.auth.authz.ResourceAuthorizationContextRegistry
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

/**
 * Resolves the [AuditOwnerScope] a mutation on a governed resource should file its audit event
 * under, by asking the central [ResourceAuthorizationContextRegistry] for that resource's real
 * [OwnerContext] rather than assuming an organization-or-platform fallback.
 *
 * A personally owned resource (an [OwnerContext.Personal] owner) must file under
 * [AuditOwnerScope.Personal], never under [AuditOwnerScope.Platform] merely because a caller only
 * ever checked an organization column. Reusing this one resolver keeps every call site consistent
 * with the pattern [com.docuhyphen.app.api.service.exchange.ShareService] already uses, instead of
 * repeating the same owner-context mapping in every service.
 */
@ApplicationScoped
class AuditOwnerScopeResolver @Inject constructor(
    private val resourceAuthorizationContextRegistry: ResourceAuthorizationContextRegistry,
)
{
    /**
     * Resolves the owner scope for [resourceId] of [resourceType]. Falls back to
     * [AuditOwnerScope.Platform] when the resource cannot be located or carries no registered
     * authorization-context provider; that fallback is a last resort, not the expected path for a
     * resource kind that supports personal or organization ownership.
     */
    fun resolve(resourceType: ResourceType, resourceId: UUID): AuditOwnerScope =
        when (val owner = resourceAuthorizationContextRegistry.resolve(ResourceRef(resourceType, resourceId))?.ownerContext)
        {
            is OwnerContext.Organization -> AuditOwnerScope.Organization(owner.organizationId)
            is OwnerContext.Personal -> AuditOwnerScope.Personal(owner.userId)
            else -> AuditOwnerScope.Platform
        }
}

