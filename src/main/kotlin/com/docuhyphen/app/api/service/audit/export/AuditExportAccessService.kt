package com.docuhyphen.app.api.service.audit.export

import com.docuhyphen.app.api.model.audit.AuditAccessActor
import com.docuhyphen.app.api.model.audit.AuditOrganizationAccess
import com.docuhyphen.app.api.model.entity.AuditExport
import com.docuhyphen.app.api.service.audit.AuditAccessAuthorizer
import com.docuhyphen.app.api.service.auth.authz.Action
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class AuditExportAccessService @Inject constructor(
    private val auditAccessAuthorizer: AuditAccessAuthorizer,
    private val auditExportService: AuditExportService,
)
{
    companion object
    {
        private const val PLATFORM_TARGET_ID = "platform"
        private const val EXPORT_NOT_FOUND_MESSAGE = "Audit export not found"
    }

    fun organization(organizationId: UUID, action: Action): AuditOrganizationAccess =
        auditAccessAuthorizer.authorizeOrganization(organizationId, action) { denial ->
            auditExportService.recordDeniedAttempt(
                denial.actor.principal.id,
                organizationId,
                organizationId.toString(),
                denial.reasonCode,
            )
        }

    fun platform(action: Action): AuditAccessActor =
        auditAccessAuthorizer.authorizePlatform(action) { denial ->
            auditExportService.recordDeniedAttempt(denial.actor.principal.id, null, PLATFORM_TARGET_ID, denial.reasonCode)
        }

    fun organizationExport(exportId: UUID, organizationId: UUID): AuditExport
    {
        val export = auditExportService.getExport(exportId)
        if (export.organizationId != organizationId)
        {
            throw AuditExportNotFoundException(EXPORT_NOT_FOUND_MESSAGE)
        }
        return export
    }
}
