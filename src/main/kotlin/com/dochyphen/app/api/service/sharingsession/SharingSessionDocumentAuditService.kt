package com.dochyphen.app.api.service.sharingsession

import com.dochyphen.app.api.exception.SharingSessionDocumentNotFoundException
import com.dochyphen.app.api.exception.SharingSessionNotFoundException
import com.dochyphen.app.api.model.entity.AppUser
import com.dochyphen.app.api.model.entity.Document
import com.dochyphen.app.api.model.entity.DocumentAuditLog
import com.dochyphen.app.api.model.entity.DocumentAuditLogAction
import com.dochyphen.app.api.repository.DocumentAuditLogRepository
import com.dochyphen.app.api.repository.DocumentRepository
import com.dochyphen.app.api.repository.SharingSessionRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@ApplicationScoped
class SharingSessionDocumentAuditService @Inject constructor(
    private val sharingSessionRepository: SharingSessionRepository,
    private val documentRepository: DocumentRepository,
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
            this.performedByEmail = managedPerformedBy.email
            this.timestamp = Timestamp.from(Instant.now())
        }

        documentAuditLogRepository.save(auditLog)
    }

    fun logAction(document: Document, action: DocumentAuditLogAction, performedByEmail: String)
    {
        val auditLog = DocumentAuditLog().apply {
            this.document = document
            this.action = action
            this.timestamp = Timestamp.from(Instant.now())
            this.performedByEmail = performedByEmail
        }

        documentAuditLogRepository.save(auditLog)
    }

    fun getDocumentAuditLogs(sessionId: String?, documentId: String): List<DocumentAuditLog>
    {
        sharingSessionRepository.findById(UUID.fromString(sessionId))
            ?: throw SharingSessionNotFoundException("Sharing session not found")

        val document = documentRepository.findByDocumentId(UUID.fromString(documentId))
            ?: throw SharingSessionDocumentNotFoundException("Document not found")

        return documentAuditLogRepository.findByDocumentId(document.id)
    }
}