package com.securedocsshare.app.api.service

import com.securedocsshare.app.api.model.AppUser
import com.securedocsshare.app.api.model.Document
import com.securedocsshare.app.api.model.DocumentAuditLog
import com.securedocsshare.app.api.model.DocumentAuditLogAction
import com.securedocsshare.app.api.repository.DocumentAuditLogRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.sql.Timestamp
import java.time.Instant

@ApplicationScoped
class DocumentAuditService @Inject constructor(
    private val documentAuditLogRepository: DocumentAuditLogRepository
)
{
    fun logAction(document: Document, action: DocumentAuditLogAction, performedBy: AppUser)
    {
        val auditLog = DocumentAuditLog().apply {
            this.document = document
            this.action = action
            this.performedBy = performedBy
            this.performedEmail = performedBy.email
            this.timestamp = Timestamp.from(Instant.now())
        }
        documentAuditLogRepository.save(auditLog)
    }
}