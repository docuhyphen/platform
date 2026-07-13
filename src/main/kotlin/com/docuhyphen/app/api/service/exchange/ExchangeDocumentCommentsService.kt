package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.exception.ExchangeNotFoundException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.dto.NotificationDto
import com.docuhyphen.app.api.model.dto.NotificationType
import com.docuhyphen.app.api.model.entity.DocumentAuditAction
import com.docuhyphen.app.api.model.entity.ExchangeDocumentComment
import com.docuhyphen.app.api.repository.DocumentCommentRepository
import com.docuhyphen.app.api.repository.ExchangeDocumentRepository
import com.docuhyphen.app.api.realtime.RealtimeEventService
import com.docuhyphen.app.api.repository.ExchangeRepository
import com.docuhyphen.app.api.service.organization.OrganizationMembershipService
import jakarta.ws.rs.BadRequestException
import jakarta.ws.rs.ForbiddenException
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
    private val realtimeEventService: RealtimeEventService,
    private val exchangeDocumentAuditService: ExchangeDocumentAuditService,
    private val authTokenContext: AuthTokenContext,
    private val documentCommentRepository: DocumentCommentRepository,
    private val organizationMembershipService: OrganizationMembershipService,
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
        isInternal: Boolean
    ): ExchangeDocumentComment
    {
        //ToDo: link comment to document version
        val document = exchangeDocumentRepository.findById(UUID.fromString(documentId))
            ?: throw ExchangeNotFoundException("Document not found")

        val user = authTokenContext.authToken.appUser
            ?: throw ForbiddenException("A user account is required to add a document note")
        val internalOrganizationId = if (isInternal)
        {
            authTokenContext.activeOrganizationId
                ?: throw BadRequestException("Select an organization before posting an internal note")
        }
        else
        {
            null
        }

        val comment = ExchangeDocumentComment().apply {
            this.commentText = commentText
            this.document = document
            this.commentedBy = user
            this.internalOrganizationId = internalOrganizationId
            this.createdDate = Timestamp.from(Instant.now())
        }

        documentCommentRepository.save(comment)

        exchangeDocumentAuditService.logAction(
            document, DocumentAuditAction.COMMENT, user
        )

        if (!isInternal)
        {
            publishCommentNotification(exchangeId, documentId, comment, user)
        }

        return comment
    }

    private fun publishCommentNotification(
        exchangeId: String,
        documentId: String,
        comment: ExchangeDocumentComment,
        user: com.docuhyphen.app.api.model.entity.AppUser,
    )
    {
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

    }

    fun getDocumentComments(exchangeId: String, documentId: String): List<ExchangeDocumentComment>
    {
        exchangeRepository.findById(UUID.fromString(exchangeId))
            ?: throw ExchangeNotFoundException("Exchange not found")

        val user = authTokenContext.authToken.appUser
            ?: throw ForbiddenException("A user account is required to view document notes")
        val visibleOrganizationIds = organizationMembershipService.activeOrganizationIds(user.id)

        return documentCommentRepository.findByDocumentId(UUID.fromString(documentId)).filter { comment ->
            comment.internalOrganizationId == null || comment.internalOrganizationId in visibleOrganizationIds
        }
    }
}
