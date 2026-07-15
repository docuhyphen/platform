package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.exception.ExchangeNotFoundException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.DocumentAuditAction
import com.docuhyphen.app.api.model.entity.Document
import com.docuhyphen.app.api.model.entity.ExchangeDocumentComment
import com.docuhyphen.app.api.repository.DocumentCommentRepository
import com.docuhyphen.app.api.repository.ExchangeRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.organization.OrganizationMembershipService
import com.docuhyphen.app.api.service.notification.InAppNotificationService
import com.docuhyphen.app.api.service.notification.UserNotificationPreference
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
    private val inAppNotificationService: InAppNotificationService,
    private val exchangeDocumentAuditService: ExchangeDocumentAuditService,
    private val authTokenContext: AuthTokenContext,
    private val documentCommentRepository: DocumentCommentRepository,
    private val organizationMembershipService: OrganizationMembershipService,
    private val shareService: ShareService,
    private val authorizationService: AuthorizationService,
    private val authorizationContextFactory: AuthorizationContextFactory,
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
        val document = requireDocumentAccess(exchangeId, documentId, Action.DOCUMENT_COMMENT)

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
            publishCommentNotification(exchangeId, document, comment, user)
        }

        return comment
    }

    private fun publishCommentNotification(
        exchangeId: String,
        document: Document,
        comment: ExchangeDocumentComment,
        user: com.docuhyphen.app.api.model.entity.AppUser,
    )
    {
        val exchangeUuid = UUID.fromString(exchangeId)
        val exchange = exchangeRepository.findById(exchangeUuid) ?: return
        val recipients = buildSet {
            exchange.initiator?.id?.let(::add)
            addAll(shareService.recipientUserIds(exchangeUuid))
        }.filterNot { it == user.id }
        val author = listOfNotNull(user.person?.firstName, user.person?.lastName)
            .joinToString(" ")
            .ifBlank { user.email }
        val documentName = document.title.trim().takeIf { it.isNotBlank() }
        val exchangeName = exchange.name?.trim()?.takeIf { it.isNotBlank() }
        recipients.forEach { appUserId ->
            inAppNotificationService.publishIfEnabled(
                appUserId = appUserId,
                preference = UserNotificationPreference.DOCUMENT_COMMENTED,
                type = "document.commented",
                title = "Document comment",
                message = buildDocumentCommentNotificationMessage(author, documentName, exchangeName),
                data = buildMap {
                    put("exchangeId", exchangeId)
                    put("documentId", document.id.toString())
                    put("commentId", comment.id.toString())
                    put("userId", user.id.toString())
                    put("commenterName", author)
                    documentName?.let { put("documentName", it) }
                    exchangeName?.let { put("exchangeName", it) }
                },
            )
    }
}

internal fun buildDocumentCommentNotificationMessage(
    commenterName: String,
    documentName: String?,
    exchangeName: String?,
): String = when
{
    documentName != null && exchangeName != null ->
        "$commenterName commented on \"$documentName\" in Exchange \"$exchangeName\"."
    documentName != null ->
        "$commenterName commented on \"$documentName\" in an Exchange shared with you."
    exchangeName != null ->
        "$commenterName commented on a document in Exchange \"$exchangeName\"."
    else -> "$commenterName added a document comment. Open it to view the document and Exchange."
}

    fun getDocumentComments(exchangeId: String, documentId: String): List<ExchangeDocumentComment>
    {
        requireDocumentAccess(exchangeId, documentId, Action.DOCUMENT_VIEW)

        val user = authTokenContext.authToken.appUser
            ?: throw ForbiddenException("A user account is required to view document notes")
        val visibleOrganizationIds = organizationMembershipService.activeOrganizationIds(user.id)

        return documentCommentRepository.findByDocumentId(UUID.fromString(documentId)).filter { comment ->
            comment.internalOrganizationId == null || comment.internalOrganizationId in visibleOrganizationIds
        }
    }

    private fun requireDocumentAccess(exchangeId: String, documentId: String, action: Action) =
        exchangeRepository.findDocumentBySessionIdAndDocumentId(
            UUID.fromString(exchangeId),
            UUID.fromString(documentId),
        )?.also {
            val principal = authorizationContextFactory.currentPrincipal()
                ?: throw ForbiddenException("Authentication is required")
            val decision = authorizationService.authorize(
                principal = principal,
                action = action,
                resource = ResourceRef.exchange(UUID.fromString(exchangeId)),
                context = authorizationContextFactory.currentContext(),
            )
            if (decision is Decision.Deny)
            {
                throw ForbiddenException("Permission to access document notes was not granted")
            }
        } ?: throw ExchangeNotFoundException("Document not found")
}
