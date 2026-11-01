package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.model.auth.authz.OwnerContext
import com.docuhyphen.app.api.model.entity.PrincipalGroupScope
import com.docuhyphen.app.api.repository.organization.PrincipalGroupRepository
import com.docuhyphen.app.api.model.auth.authz.ResourceAuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.ResourceAuthorizationContextProvider
import com.docuhyphen.app.api.service.auth.authz.ResourceKind
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

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

