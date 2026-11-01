package com.docuhyphen.app.api.service.audit

import com.docuhyphen.app.api.model.audit.AuditOrganizationAccess
import com.docuhyphen.app.api.service.auth.authz.Action
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class AuditLegalHoldAccessService @Inject constructor(
    private val auditAccessAuthorizer: AuditAccessAuthorizer,
    private val auditLegalHoldService: AuditLegalHoldService,
)
{
    fun organization(organizationId: UUID, action: Action): AuditOrganizationAccess =
        auditAccessAuthorizer.authorizeOrganization(organizationId, action) { denial ->
            auditLegalHoldService.recordDeniedAttempt(denial.actor.principal.id, organizationId, denial.reasonCode)
        }
}
