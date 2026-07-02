package com.docuhyphen.app.api.service.communication

import com.docuhyphen.app.api.model.entity.CommunicationScope
import com.docuhyphen.app.api.repository.CommunicationRepository
import com.docuhyphen.app.api.service.auth.authz.OwnerContext
import com.docuhyphen.app.api.service.auth.authz.ResourceAuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.ResourceAuthorizationContextProvider
import com.docuhyphen.app.api.service.auth.authz.ResourceKind
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

@ApplicationScoped
class CommunicationAuthorizationContextProvider : ResourceAuthorizationContextProvider
{
    @Inject
    private lateinit var repository: CommunicationRepository

    override val supportedKind: ResourceKind = ResourceKind.COMMUNICATION

    override fun resolve(resourceId: UUID): ResourceAuthorizationContext?
    {
        val communication = repository.findById(resourceId) ?: return null

        val ownerContext = when (communication.scope)
        {
            CommunicationScope.PERSONAL  -> OwnerContext.Personal(communication.createdByAppUserId ?: return null)
            CommunicationScope.ORG      -> OwnerContext.Organization(communication.organizationId ?: return null)
            CommunicationScope.PLATFORM -> OwnerContext.Platform
        }

        return ResourceAuthorizationContext(
            ownerContext = ownerContext,
            isArchived = communication.isDeleted,
            isSuspended = false,
        )
    }
}
