package com.docuhyphen.app.api.service.audit

import com.docuhyphen.app.api.service.auth.authz.Capability
import java.util.UUID

/**
 * Shared rule for whether evidence access to [organizationId] must be justified by an active
 * [AuditEngagementService] match rather than a caller's direct governance capability. Used by
 * both read-side projection and export authorization so the two surfaces cannot drift apart into
 * two different audit authorization models.
 */
fun requiresEngagementAccess(capabilities: Set<Capability>, organizationId: UUID?, platformOnly: Boolean): Boolean
{
    if (platformOnly || organizationId == null)
    {
        return false
    }
    if (Capability.APP_ADMIN in capabilities)
    {
        return false
    }
    return Capability.ORG_POLICY_MANAGE !in capabilities
}
