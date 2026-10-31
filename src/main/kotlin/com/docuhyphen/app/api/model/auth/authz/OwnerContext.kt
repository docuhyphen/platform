package com.docuhyphen.app.api.model.auth.authz

import com.docuhyphen.app.api.service.auth.authz.ResourceKind
import java.util.*

sealed class OwnerContext
{
    data object Platform : OwnerContext()
    data class Organization(val organizationId: UUID) : OwnerContext()
    data class Personal(val userId: UUID) : OwnerContext()
}

sealed class ScopeReference
{
    data object Platform : ScopeReference()
    data class Organization(val organizationId: UUID) : ScopeReference()
    data class Personal(val userId: UUID) : ScopeReference()
}

data class ResourceReference(
    val kind: ResourceKind,
    val id: UUID,
)
