package com.docuhyphen.app.api.model.auth.authz

import com.docuhyphen.app.api.service.auth.authz.ResourceAuthorizationContext

sealed interface ResourceContextResolution
{
    data class Resolved(val context: ResourceAuthorizationContext) : ResourceContextResolution

    data object Unresolved : ResourceContextResolution

    data object NotGoverned : ResourceContextResolution
}
