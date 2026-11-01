package com.docuhyphen.app.api.model.auth.authz

import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.Capability

data class ResourcePolicyRequest(
    val principal: PrincipalRef,
    val action: Action,
    val resource: ResourceRef,
    val resourceContext: ResourceAuthorizationContext,
    val capabilities: Set<Capability>,
    val authorizationContext: AuthorizationContext,
)

sealed interface ResourcePolicyOutcome
{
    data class Permit(val obligations: ShareObligations = ShareObligations()) : ResourcePolicyOutcome

    data class Deny(val reasonCode: String, val message: String) : ResourcePolicyOutcome

    data class FactsUnavailable(val message: String) : ResourcePolicyOutcome
}
