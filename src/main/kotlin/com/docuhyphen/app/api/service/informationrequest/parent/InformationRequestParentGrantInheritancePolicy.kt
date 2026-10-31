package com.docuhyphen.app.api.service.informationrequest.parent

import com.docuhyphen.app.api.service.auth.authz.Capability
import com.docuhyphen.app.api.service.auth.authz.ParentGrantInheritancePolicy
import com.docuhyphen.app.api.service.auth.authz.ResourceKind
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class InformationRequestParentGrantInheritancePolicy : ParentGrantInheritancePolicy
{
    override val supportedKind: ResourceKind = ResourceKind.INFORMATION_REQUEST

    override fun inheritedCapabilities(parentCapabilities: Set<Capability>): Set<Capability> =
        parentCapabilities.intersect(inheritable)

    private companion object
    {
        val inheritable = setOf(
            Capability.INFORMATION_REQUEST_CREATE,
            Capability.INFORMATION_REQUEST_READ,
            Capability.INFORMATION_REQUEST_CANCEL,
            Capability.INFORMATION_REQUEST_ADMIN,
        )
    }
}
