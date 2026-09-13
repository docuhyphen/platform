package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef

/**
 * The caller a runtime Information Request application service acts for.
 *
 * [principal] is the only authorization identity, the same canonical [PrincipalRef] the rest of the
 * central authorization stack uses. [authorization] carries the surrounding facts a decision needs,
 * including the active organization scope, MFA freshness, and the non-secret session reference
 * recorded as provenance on the records a request changes.
 *
 * Both access surfaces build this exact shape so a shared application service never has to ask which
 * one it is talking to. The authenticated surface builds it from the caller's existing principal and
 * authorization context. A later no-auth surface validates a recipient-bound bootstrap credential and
 * session, resolves the participant [PrincipalRef] that session is bound to, and builds the same
 * shape from that. Neither a raw bearer token nor a raw credential is ever carried here; a credential
 * or session identity may only reach [AuthorizationContext.sessionRef] as non-secret lineage.
 */
data class RequestAccessContext(
    val principal: PrincipalRef,
    val authorization: AuthorizationContext,
)

