package com.dochyphen.app.api.service

import com.dochyphen.app.api.model.entity.AppUser
import com.dochyphen.app.api.model.entity.Document
import com.dochyphen.app.api.model.entity.DocumentAuditLog
import com.dochyphen.app.api.model.entity.DocumentAuditLogAction
import com.dochyphen.app.api.repository.DocumentAuditLogRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import java.sql.Timestamp
import java.time.Instant

@ApplicationScoped
class DocumentAuditService @Inject constructor(
    private val documentAuditLogRepository: DocumentAuditLogRepository,
    private val entityManager: EntityManager
)
{
    fun logAction(document: Document, action: DocumentAuditLogAction, performedBy: AppUser)
    {
        val managedPerformedBy = entityManager.merge(performedBy)
        val auditLog = DocumentAuditLog().apply {
            this.document = document
            this.action = action
            this.performedBy = managedPerformedBy
            this.performedEmail = managedPerformedBy.email
            this.timestamp = Timestamp.from(Instant.now())
        }
        documentAuditLogRepository.save(auditLog)
    }
}