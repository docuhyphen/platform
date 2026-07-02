package com.docuhyphen.app.api.service.variable

import com.docuhyphen.app.api.repository.SequenceDefinitionRepository
import com.docuhyphen.app.api.service.auth.authz.OwnerContext
import com.docuhyphen.app.api.service.auth.authz.ResourceAuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.ResourceAuthorizationContextProvider
import com.docuhyphen.app.api.service.auth.authz.ResourceKind
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

@ApplicationScoped
class SequenceDefinitionAuthorizationContextProvider : ResourceAuthorizationContextProvider
{
    @Inject
    private lateinit var repository: SequenceDefinitionRepository

    override val supportedKind: ResourceKind = ResourceKind.SEQUENCE_DEFINITION

    override fun resolve(resourceId: UUID): ResourceAuthorizationContext?
    {
        val sequence = repository.findById(resourceId) ?: return null

        return ResourceAuthorizationContext(
            ownerContext = OwnerContext.Organization(sequence.organizationId),
            isArchived = sequence.isDeleted,
            isSuspended = false,
        )
    }
}
