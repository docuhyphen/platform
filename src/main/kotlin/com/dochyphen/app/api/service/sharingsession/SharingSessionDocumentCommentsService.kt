// src/main/kotlin/com/dochyphen/app/api/service/sharingsession/SharingSessionDocumentCommentsService.kt
package com.dochyphen.app.api.service.sharingsession

import com.dochyphen.app.api.exception.SharingSessionNotFoundException
import com.dochyphen.app.api.exception.UserNotFoundException
import com.dochyphen.app.api.interceptor.AuthTokenContext
import com.dochyphen.app.api.model.entity.DocumentAuditLogAction
import com.dochyphen.app.api.model.entity.SharingSessionDocumentComment
import com.dochyphen.app.api.repository.AppUserRepository
import com.dochyphen.app.api.repository.DocumentCommentRepository
import com.dochyphen.app.api.repository.SharingSessionDocumentRepository
import com.dochyphen.app.api.repository.SharingSessionRepository
import com.dochyphen.app.api.service.AppUserService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@ApplicationScoped
class SharingSessionDocumentCommentsService @Inject constructor(
    private val sharingSessionRepository: SharingSessionRepository,
    private val sharingSessionDocumentRepository: SharingSessionDocumentRepository,
    private val appUserRepository: AppUserRepository,
    private val appUserService: AppUserService,
    private val sharingSessionDocumentAuditService: SharingSessionDocumentAuditService,
    private val authTokenContext: AuthTokenContext,
    private val documentCommentRepository: DocumentCommentRepository,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(SharingSessionDocumentCommentsService::class.java)
    }

    @Transactional
    fun addDocumentComment(
        documentId: String,
        commentText: String,
        commentedBy: String
    ): SharingSessionDocumentComment
    {
        val document = sharingSessionDocumentRepository.findById(UUID.fromString(documentId))
            ?: throw SharingSessionNotFoundException("Document not found")

        val user = appUserRepository.findByEmail(commentedBy)
            ?: throw UserNotFoundException("User not found")

        val comment = SharingSessionDocumentComment().apply {
            this.commentText = commentText
            this.document = document
            this.commentedBy = user
            this.createdDate = Timestamp.from(Instant.now())
        }

        documentCommentRepository.save(comment)

        sharingSessionDocumentAuditService.logAction(
            document, DocumentAuditLogAction.COMMENT, user
        )

        return comment
    }

    fun getDocumentComments(documentId: String): List<SharingSessionDocumentComment>
    {
        return documentCommentRepository.findByDocumentId(UUID.fromString(documentId))
    }
}