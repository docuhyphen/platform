package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.service.auth.authz.Capability
import com.docuhyphen.app.api.service.auth.authz.ParentGrantInheritancePolicy
import com.docuhyphen.app.api.service.auth.authz.ResourceKind
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class InformationRequestRequirementParentGrantInheritancePolicy : ParentGrantInheritancePolicy
{
    override val supportedKind: ResourceKind = ResourceKind.INFORMATION_REQUEST_REQUIREMENT

    override fun inheritedCapabilities(parentCapabilities: Set<Capability>): Set<Capability> =
        parentCapabilities.intersect(inheritable)

    private companion object
    {
        val inheritable = setOf(
            Capability.INFORMATION_REQUEST_READ,
            Capability.INFORMATION_REQUEST_RESPOND,
            Capability.INFORMATION_REQUEST_ATTEST,
            Capability.INFORMATION_REQUEST_REVIEW,
            Capability.INFORMATION_REQUEST_EVIDENCE_READ,
            Capability.INFORMATION_REQUEST_EVIDENCE_WRITE,
            Capability.INFORMATION_REQUEST_EVIDENCE_ADMIN,
        )
    }
}
