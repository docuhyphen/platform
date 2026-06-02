package com.docuhyphen.app.api.service.auth.authz

/**
 * Single entry point for "may this principal perform this action on this resource?".
 *
 * All sharing/collaboration code paths SHOULD route their authorisation checks through
 * this interface rather than hand-rolling boolean checks on entity fields. This is the
 * mechanism that lets us add roles, capabilities, constraints, and deny rules without
 * touching service code.
 *
 * Implementation lives in [DefaultAuthorizationService].
 */
interface AuthorizationService
{
    fun authorize(
        principal: PrincipalRef,
        action: Action,
        resource: ResourceRef,
        context: AuthorizationContext = AuthorizationContext.ANONYMOUS,
    ): Decision

    fun capabilities(
        principal: PrincipalRef,
        resource: ResourceRef,
        context: AuthorizationContext = AuthorizationContext.ANONYMOUS,
    ): Set<Capability>

    /** Returns the per-grant breakdown that produced the union — for UI / audit / debugging. */
    fun grantsOn(
        principal: PrincipalRef,
        resource: ResourceRef,
        context: AuthorizationContext = AuthorizationContext.ANONYMOUS,
    ): List<Grant>
}

