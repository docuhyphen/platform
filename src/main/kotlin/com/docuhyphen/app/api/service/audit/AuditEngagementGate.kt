package com.docuhyphen.app.api.service.audit

import com.docuhyphen.app.api.service.auth.authz.Capability
import java.util.*

fun requiresEngagementAccess(capabilities: Set<Capability>, organizationId: UUID?, platformOnly: Boolean): Boolean
{
    if (platformOnly || organizationId == null)
    {
        return false
    }
    return Capability.ORG_POLICY_MANAGE !in capabilities
}
