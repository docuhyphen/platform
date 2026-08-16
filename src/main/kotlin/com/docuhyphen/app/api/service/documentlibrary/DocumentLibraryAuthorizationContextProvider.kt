package com.docuhyphen.app.api.service.documentlibrary

import com.docuhyphen.app.api.model.entity.BlueprintScope
import com.docuhyphen.app.api.repository.documentlibrary.DocumentLibraryRepository
import com.docuhyphen.app.api.service.auth.authz.OwnerContext
import com.docuhyphen.app.api.service.auth.authz.ResourceAuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.ResourceAuthorizationContextProvider
import com.docuhyphen.app.api.service.auth.authz.ResourceKind
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

@ApplicationScoped
class DocumentLibraryAuthorizationContextProvider : ResourceAuthorizationContextProvider
{
    @Inject
    private lateinit var repository: DocumentLibraryRepository

    override val supportedKind: ResourceKind = ResourceKind.DOCUMENT_LIBRARY_ENTRY

    override fun resolve(resourceId: UUID): ResourceAuthorizationContext?
    {
        val entry = repository.findById(resourceId) ?: return null

        val ownerContext = when (entry.scope)
        {
            BlueprintScope.PERSONAL -> OwnerContext.Personal(entry.createdByAppUserId ?: return null)
            BlueprintScope.ORG     -> OwnerContext.Organization(entry.organizationId ?: return null)
            BlueprintScope.APP     -> OwnerContext.Platform
        }

        return ResourceAuthorizationContext(
            ownerContext = ownerContext,
            isArchived = entry.isDeleted,
            isSuspended = false,
        )
    }
}
