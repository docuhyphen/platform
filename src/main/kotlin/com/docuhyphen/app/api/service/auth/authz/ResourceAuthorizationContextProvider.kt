package com.docuhyphen.app.api.service.auth.authz

import java.util.*

interface ResourceAuthorizationContextProvider
{
    val supportedKind: ResourceKind

    val actionsWhileArchived: Set<Action>
        get() = emptySet()

    fun resolve(resourceId: UUID): ResourceAuthorizationContext?
}
