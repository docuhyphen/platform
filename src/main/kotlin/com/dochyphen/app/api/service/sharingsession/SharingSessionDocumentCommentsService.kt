package com.dochyphen.app.api.service.sharingsession

import com.dochyphen.app.api.exception.SharingSessionNotFoundException
import com.dochyphen.app.api.exception.UserNotFoundException
import com.dochyphen.app.api.interceptor.AuthTokenContext
import com.dochyphen.app.api.model.entity.DocumentComment
import com.dochyphen.app.api.repository.AppUserRepository
import com.dochyphen.app.api.repository.DocumentCommentRepository
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
    ): DocumentComment
    {
        sharingSessionRepository.findById(UUID.fromString(documentId))
            ?: throw SharingSessionNotFoundException("Document not found")

        val user = appUserRepository.findByEmail(commentedBy)
            ?: throw UserNotFoundException("User not found")

        val comment = DocumentComment().apply {
            this.commentText = commentText
            this.document = document //TODO: Add the document entity
            this.commentedBy = user
            this.createdDate = Timestamp.from(Instant.now())
        }

        documentCommentRepository.save(comment)
        return comment
    }

    fun getDocumentComments(documentId: String): List<DocumentComment>
    {
        return documentCommentRepository.findByDocumentId(UUID.fromString(documentId))
    }
}