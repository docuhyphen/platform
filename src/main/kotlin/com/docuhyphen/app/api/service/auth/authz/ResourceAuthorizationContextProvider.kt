package com.docuhyphen.app.api.service.auth.authz

import com.docuhyphen.app.api.model.auth.authz.ResourceAuthorizationContext
import java.util.*

interface ResourceAuthorizationContextProvider
{
    val supportedKind: ResourceKind

    val actionsWhileArchived: Set<Action>
        get() = emptySet()

    fun resolve(resourceId: UUID): ResourceAuthorizationContext?
}
