package com.docuhyphen.app.api.model.auth.authz

import com.docuhyphen.app.api.service.auth.authz.ParentGrantInheritancePolicy
import com.docuhyphen.app.api.service.auth.authz.ResourceRef

sealed interface ParentGrantInheritance
{
    data object None : ParentGrantInheritance

    data class Inherited(
        val parent: ResourceRef,
        val parentResolution: ResourceContextResolution,
        val policy: ParentGrantInheritancePolicy,
    ) : ParentGrantInheritance

    data class Refused(val reasonCode: String, val message: String) : ParentGrantInheritance
}
