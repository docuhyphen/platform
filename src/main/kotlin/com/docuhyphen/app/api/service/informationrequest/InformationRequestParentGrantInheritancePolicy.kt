package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.service.auth.authz.Capability
import com.docuhyphen.app.api.service.auth.authz.ParentGrantInheritancePolicy
import com.docuhyphen.app.api.service.auth.authz.ResourceKind
import jakarta.enterprise.context.ApplicationScoped

/**
 * The Exchange owner's Share carries the owner's runtime request authoring capabilities
 * (see RoleCapabilities.EXCHANGE_SHARE), but that Share is scoped to the Exchange resource, not to
 * any one request. This lets exactly those capabilities, and nothing else the owner's Exchange
 * Share carries, reach a decision made directly against one request.
 */
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
