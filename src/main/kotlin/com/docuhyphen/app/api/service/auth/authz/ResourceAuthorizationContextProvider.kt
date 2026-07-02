package com.docuhyphen.app.api.service.auth.authz

import java.util.UUID

/**
 * Resolves authorization-relevant context for one [ResourceKind].
 *
 * Each governed resource type registers exactly one implementation via CDI. Implementations
 * delegate to their owning service domain and must not access another service's repository
 * directly. An unresolvable resource (not found, owner missing) returns null, which fails
 * closed in [DefaultAuthorizationService].
 */
interface ResourceAuthorizationContextProvider
{
    val supportedKind: ResourceKind

    fun resolve(resourceId: UUID): ResourceAuthorizationContext?
}
