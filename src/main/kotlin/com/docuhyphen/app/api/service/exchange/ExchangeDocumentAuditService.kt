package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.exception.ExchangeDocumentNotFoundException
import com.docuhyphen.app.api.exception.ExchangeNotFoundException
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.Document
import com.docuhyphen.app.api.model.entity.DocumentAuditLog
import com.docuhyphen.app.api.model.entity.DocumentAuditLogAction
import com.docuhyphen.app.api.repository.DocumentAuditRepository
import com.docuhyphen.app.api.repository.ExchangeDocumentRepository
import com.docuhyphen.app.api.repository.ExchangeRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@ApplicationScoped
class ExchangeDocumentAuditService @Inject constructor(
    private val exchangeRepository: ExchangeRepository,
    private val exchangeDocumentRepository: ExchangeDocumentRepository,
    private val documentAuditRepository: DocumentAuditRepository,
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

        documentAuditRepository.save(auditLog)
    }

    fun logAction(document: Document, action: DocumentAuditLogAction, performedByEmail: String)
    {
        val auditLog = DocumentAuditLog().apply {
            this.document = document
            this.action = action
            this.timestamp = Timestamp.from(Instant.now())
            this.performedByEmail = performedByEmail
        }

        documentAuditRepository.save(auditLog)
    }

    fun getDocumentAuditLogs(exchangeId: String?, documentId: String): List<DocumentAuditLog>
    {
        exchangeRepository.findById(UUID.fromString(exchangeId))
            ?: throw ExchangeNotFoundException("Exchange not found")

        val document = exchangeDocumentRepository.findByDocumentId(UUID.fromString(documentId))
            ?: throw ExchangeDocumentNotFoundException("Document not found")

        return documentAuditRepository.findByDocumentId(document.id)
    }
}