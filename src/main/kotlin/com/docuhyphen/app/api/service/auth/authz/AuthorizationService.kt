package com.docuhyphen.app.api.service.auth.authz

import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.auth.authz.Grant
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.model.auth.authz.ResourceRef

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

    fun grantsOn(
        principal: PrincipalRef,
        resource: ResourceRef,
        context: AuthorizationContext = AuthorizationContext.ANONYMOUS,
    ): List<Grant>
}

