package com.docuhyphen.app.api.model.auth.authz


sealed interface ResourceContextResolution
{
    data class Resolved(val context: ResourceAuthorizationContext) : ResourceContextResolution

    data object Unresolved : ResourceContextResolution

    data object NotGoverned : ResourceContextResolution
}
