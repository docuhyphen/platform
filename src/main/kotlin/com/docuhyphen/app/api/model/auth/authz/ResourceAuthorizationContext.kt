package com.docuhyphen.app.api.model.auth.authz

import com.docuhyphen.app.api.model.auth.authz.OwnerContext

interface ResourcePolicyFacts
{
    data object None : ResourcePolicyFacts
}

data class ResourceAuthorizationContext(
    val ownerContext: OwnerContext,
    val isArchived: Boolean = false,
    val isSuspended: Boolean = false,
    val parentRef: ResourceRef? = null,
    val policyFacts: ResourcePolicyFacts = ResourcePolicyFacts.None,
)
