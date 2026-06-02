// src/main/kotlin/com/dochyphen/app/api/service/sharingsession/SharingSessionDocumentCommentsService.kt
package com.docuhyphen.app.api.service.sharingsession

import com.docuhyphen.app.api.exception.SharingSessionNotFoundException
import com.docuhyphen.app.api.exception.AppUserNotFoundException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.dto.NotificationDto
import com.docuhyphen.app.api.model.dto.NotificationType
import com.docuhyphen.app.api.model.entity.DocumentAuditLogAction
import com.docuhyphen.app.api.model.entity.SharingSessionDocumentComment
import com.docuhyphen.app.api.repository.AppUserRepository
import com.docuhyphen.app.api.repository.DocumentCommentRepository
import com.docuhyphen.app.api.repository.SharingSessionDocumentRepository
import com.docuhyphen.app.api.realtime.RealtimeEventService
import com.docuhyphen.app.api.repository.SharingSessionRepository
import com.docuhyphen.app.api.service.AppUserService
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
    private val realtimeEventService: RealtimeEventService,
    private val sharingSessionDocumentAuditService: SharingSessionDocumentAuditService,
    private val authTokenContext: AuthTokenContext,
    private val documentCommentRepository: DocumentCommentRepository,
    private val shareService: ShareService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(SharingSessionDocumentCommentsService::class.java)
    }

    @Transactional
    fun addDocumentComment(
        sessionId: String,
        documentId: String,
        commentText: String,
        commentedBy: String
    ): SharingSessionDocumentComment
    {
        //ToDo: link comment to document version
        val document = sharingSessionDocumentRepository.findById(UUID.fromString(documentId))
            ?: throw SharingSessionNotFoundException("Document not found")

        val user = appUserRepository.findByEmail(commentedBy)
            ?: throw AppUserNotFoundException("User not found")

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

        val notification = NotificationDto(
            id = UUID.randomUUID().toString(),
            type = NotificationType.NEW_COMMENT,
            message = "${user.person?.firstName ?: ""} ${user.person?.lastName ?: ""} added a comment on a document",
            timestamp = Timestamp.from(Instant.now()),
            sessionId = sessionId,
            documentId = documentId,
            commentId = comment.id.toString(),
            userId = user.id.toString()
        )

        val session = sharingSessionRepository.findById(UUID.fromString(sessionId))
        val initiatorId = session!!.initiator!!.id
        val recipientId = shareService.primaryRecipientUserId(UUID.fromString(sessionId))
        // Notify the "other party": if the commenter is the recipient, notify the initiator; else the recipient.
        val targetUserId = if (user.id == recipientId) initiatorId else (recipientId ?: initiatorId)
        realtimeEventService.broadcastNotificationToUser(targetUserId, notification)
        // Anyone viewing this sharing session sees the new comment live regardless of
        // whether they're the comment target.
        realtimeEventService.broadcastToSharingSession(
            UUID.fromString(sessionId),
            com.docuhyphen.app.api.realtime.RealtimeMessage(
                type = com.docuhyphen.app.api.realtime.RealtimeMessageType.NOTIFICATION,
                notification = notification,
            )
        )

        return comment
    }

    fun getDocumentComments(documentId: String): List<SharingSessionDocumentComment>
    {
        return documentCommentRepository.findByDocumentId(UUID.fromString(documentId))
    }
}