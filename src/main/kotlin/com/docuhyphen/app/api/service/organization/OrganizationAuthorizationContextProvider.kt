package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.repository.organization.OrganizationRepository
import com.docuhyphen.app.api.service.auth.authz.OwnerContext
import com.docuhyphen.app.api.service.auth.authz.ResourceAuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.ResourceAuthorizationContextProvider
import com.docuhyphen.app.api.service.auth.authz.ResourceKind
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

@ApplicationScoped
class OrganizationAuthorizationContextProvider : ResourceAuthorizationContextProvider
{
    @Inject
    private lateinit var organizationRepository: OrganizationRepository

    override val supportedKind: ResourceKind = ResourceKind.ORGANIZATION

    override fun resolve(resourceId: UUID): ResourceAuthorizationContext?
    {
        organizationRepository.findById(resourceId) ?: return null
        return ResourceAuthorizationContext(
            ownerContext = OwnerContext.Organization(resourceId),
        )
    }
}
