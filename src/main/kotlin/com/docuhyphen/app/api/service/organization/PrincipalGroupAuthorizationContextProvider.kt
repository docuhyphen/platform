package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.model.entity.PrincipalGroupScope
import com.docuhyphen.app.api.repository.organization.PrincipalGroupRepository
import com.docuhyphen.app.api.service.auth.authz.OwnerContext
import com.docuhyphen.app.api.service.auth.authz.ResourceAuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.ResourceAuthorizationContextProvider
import com.docuhyphen.app.api.service.auth.authz.ResourceKind
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

/**
 * Resolves the owner of a Principal Group so a decision about the group rests on the group's own
 * ownership rather than on the caller's selected organization.
 *
 * An organization-scoped group is owned by one organization and a personal group by one user. A
 * co-owned group names no single owner in its own row, so it resolves to nothing and is refused
 * until a multi-owner context exists; nothing creates such a group today.
 *
 * Group deactivation is not reported as archival here: deactivation is a membership and listing
 * concern that the group services already enforce, and reporting it as archival would change who
 * may act on an inactive group.
 */
@ApplicationScoped
class PrincipalGroupAuthorizationContextProvider : ResourceAuthorizationContextProvider
{
    @Inject
    private lateinit var principalGroupRepository: PrincipalGroupRepository

    override val supportedKind: ResourceKind = ResourceKind.PRINCIPAL_GROUP

    override fun resolve(resourceId: UUID): ResourceAuthorizationContext?
    {
        val group = principalGroupRepository.findById(resourceId) ?: return null

        val ownerContext = when (group.scope)
        {
            PrincipalGroupScope.ORG ->
                OwnerContext.Organization(group.ownerOrganizationId ?: return null)
            PrincipalGroupScope.PERSONAL ->
                OwnerContext.Personal(group.ownerAppUserId ?: return null)
            PrincipalGroupScope.SHARED_PROJECT -> return null
        }

        return ResourceAuthorizationContext(
            ownerContext = ownerContext,
            isArchived = false,
            isSuspended = false,
        )
    }
}

