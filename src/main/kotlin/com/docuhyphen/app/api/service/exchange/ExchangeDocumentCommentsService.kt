// src/main/kotlin/com/dochyphen/app/api/service/sharingsession/ExchangeDocumentCommentsService.kt
package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.exception.ExchangeNotFoundException
import com.docuhyphen.app.api.exception.AppUserNotFoundException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.dto.NotificationDto
import com.docuhyphen.app.api.model.dto.NotificationType
import com.docuhyphen.app.api.model.entity.DocumentAuditLogAction
import com.docuhyphen.app.api.model.entity.ExchangeDocumentComment
import com.docuhyphen.app.api.repository.AppUserRepository
import com.docuhyphen.app.api.repository.DocumentCommentRepository
import com.docuhyphen.app.api.repository.ExchangeDocumentRepository
import com.docuhyphen.app.api.realtime.RealtimeEventService
import com.docuhyphen.app.api.repository.ExchangeRepository
import com.docuhyphen.app.api.service.AppUserService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@ApplicationScoped
class ExchangeDocumentCommentsService @Inject constructor(
    private val exchangeRepository: ExchangeRepository,
    private val exchangeDocumentRepository: ExchangeDocumentRepository,
    private val appUserRepository: AppUserRepository,
    private val appUserService: AppUserService,
    private val realtimeEventService: RealtimeEventService,
    private val exchangeDocumentAuditService: ExchangeDocumentAuditService,
    private val authTokenContext: AuthTokenContext,
    private val documentCommentRepository: DocumentCommentRepository,
    private val shareService: ShareService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(ExchangeDocumentCommentsService::class.java)
    }

    @Transactional
    fun addDocumentComment(
        exchangeId: String,
        documentId: String,
        commentText: String,
        commentedBy: String
    ): ExchangeDocumentComment
    {
        //ToDo: link comment to document version
        val document = exchangeDocumentRepository.findById(UUID.fromString(documentId))
            ?: throw ExchangeNotFoundException("Document not found")

        val user = appUserRepository.findByEmail(commentedBy)
            ?: throw AppUserNotFoundException("User not found")

        val comment = ExchangeDocumentComment().apply {
            this.commentText = commentText
            this.document = document
            this.commentedBy = user
            this.createdDate = Timestamp.from(Instant.now())
        }

        documentCommentRepository.save(comment)

        exchangeDocumentAuditService.logAction(
            document, DocumentAuditLogAction.COMMENT, user
        )

        val notification = NotificationDto(
            id = UUID.randomUUID().toString(),
            type = NotificationType.NEW_COMMENT,
            message = "${user.person?.firstName ?: ""} ${user.person?.lastName ?: ""} added a comment on a document",
            timestamp = Timestamp.from(Instant.now()),
            exchangeId = exchangeId,
            documentId = documentId,
            commentId = comment.id.toString(),
            userId = user.id.toString()
        )

        val session = exchangeRepository.findById(UUID.fromString(exchangeId))
        val initiatorId = session!!.initiator!!.id
        val recipientId = shareService.primaryRecipientUserId(UUID.fromString(exchangeId))
        // Notify the "other party": if the commenter is the recipient, notify the initiator; else the recipient.
        val targetUserId = if (user.id == recipientId) initiatorId else (recipientId ?: initiatorId)
        realtimeEventService.broadcastNotificationToUser(targetUserId, notification)
        // Anyone viewing this exchange sees the new comment live regardless of
        // whether they're the comment target.
        realtimeEventService.broadcastToExchange(
            UUID.fromString(exchangeId),
            com.docuhyphen.app.api.realtime.RealtimeMessage(
                type = com.docuhyphen.app.api.realtime.RealtimeMessageType.NOTIFICATION,
                notification = notification,
            )
        )

        return comment
    }

    fun getDocumentComments(documentId: String): List<ExchangeDocumentComment>
    {
        return documentCommentRepository.findByDocumentId(UUID.fromString(documentId))
    }
}