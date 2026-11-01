package com.docuhyphen.app.api.model.informationrequest

import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import java.util.*

data class RequestAccessContext(
    val principal: PrincipalRef,
    val authorization: AuthorizationContext,
    val accessSessionId: UUID? = null,
)
