package com.docuhyphen.app.api.service.auth.authz

/**
 * Resolved authorization context for one specific resource instance. Produced by
 * [ResourceAuthorizationContextProvider] and consumed by [DefaultAuthorizationService]
 * to make ownership-accurate authorization decisions without guessing from the caller's
 * session.
 *
 * A null result from the provider always fails closed.
 */
data class ResourceAuthorizationContext(
    /** Stable owner of the resource — never inferred from the caller's active organization. */
    val ownerContext: OwnerContext,
    /**
     * True when the resource is in a terminal state (e.g. rescinded, ended, deleted).
     * Non-admin write operations are denied on archived resources.
     */
    val isArchived: Boolean = false,
    /**
     * True when the resource has been administratively suspended.
     * Non-admin write operations are denied on suspended resources.
     */
    val isSuspended: Boolean = false,
    /** Parent resource from which this resource inherits authorization, if applicable. */
    val parentRef: ResourceRef? = null,
)
