package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.exception.ExchangeNotFoundException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.DocumentAuditAction
import com.docuhyphen.app.api.model.entity.Document
import com.docuhyphen.app.api.model.entity.ExchangeDocumentComment
import com.docuhyphen.app.api.repository.exchange.DocumentCommentRepository
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.organization.OrganizationMembershipService
import com.docuhyphen.app.api.service.notification.InAppNotificationService
import com.docuhyphen.app.api.service.notification.UserNotificationPreference
import com.docuhyphen.app.api.realtime.RealtimeMessage
import com.docuhyphen.app.api.realtime.RealtimeMessageType
import jakarta.ws.rs.BadRequestException
import jakarta.ws.rs.ClientErrorException
import jakarta.ws.rs.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap

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
    private val exchangeDocumentVersionService: ExchangeDocumentVersionService,
    private val documentCommentRealtimeService: DocumentCommentRealtimeService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(ExchangeDocumentCommentsService::class.java)

        private const val MAX_COMMENT_LENGTH = 500

        // A page number this large cannot correspond to a real document page and indicates
        // a malformed or abusive request. The value is a defensive upper bound only; the
        // frontend always sends the page currently being viewed.
        private const val MAX_COMMENT_PAGE_NUMBER = 10_000

        // Simple per-user sliding window: no more than this many comments per window.
        private const val RATE_LIMIT_MAX_COMMENTS = 10
        private const val RATE_LIMIT_WINDOW_MS = 60_000L
    }

    // Tracks recent comment timestamps per user id to throttle rapid posting.
    private val recentCommentTimestamps = ConcurrentHashMap<UUID, ArrayDeque<Long>>()

    @Transactional
    fun addDocumentComment(
        exchangeId: String,
        documentId: String,
        commentText: String,
        isInternal: Boolean,
        pageNumber: Int?,
        documentVersionId: String?,
    ): ExchangeDocumentComment
    {
        val document = requireDocumentAccess(exchangeId, documentId, Action.DOCUMENT_COMMENT)

        val normalizedComment = commentText.trim()
        if (normalizedComment.isEmpty())
        {
            throw BadRequestException("Document comment cannot be empty")
        }
        if (normalizedComment.length > MAX_COMMENT_LENGTH)
        {
            throw BadRequestException("Document comment cannot exceed $MAX_COMMENT_LENGTH characters")
        }
        if (pageNumber != null && pageNumber < 1)
        {
            throw BadRequestException("Document comment page number must be greater than zero")
        }
        if (pageNumber != null && pageNumber > MAX_COMMENT_PAGE_NUMBER)
        {
            throw BadRequestException("Document comment page number is out of range")
        }

        val user = authTokenContext.authToken.appUser
            ?: throw ForbiddenException("A user account is required to add a document note")

        enforceRateLimit(user.id)

        val internalOrganizationId = if (isInternal)
        {
            val activeOrganizationId = authTokenContext.activeOrganizationId
                ?: throw BadRequestException("Select an organization before posting an internal note")
            if (!organizationMembershipService.isMember(user.id, activeOrganizationId))
            {
                throw ForbiddenException("You are not a member of the selected organization")
            }
            activeOrganizationId
        }
        else
        {
            null
        }

        val exchange = exchangeRepository.findById(UUID.fromString(exchangeId))
            ?: throw ExchangeNotFoundException("Exchange not found")

        val comment = ExchangeDocumentComment().apply {
            this.commentText = normalizedComment
            this.document = document
            this.commentedBy = user
            this.internalOrganizationId = internalOrganizationId
            this.pageNumber = pageNumber
            this.documentVersion = exchangeDocumentVersionService.resolveCommentVersion(
                document.id,
                documentVersionId,
            )
            this.createdDate = Timestamp.from(Instant.now())
        }

        documentCommentRepository.save(comment)

        exchangeDocumentAuditService.logAction(
            document, DocumentAuditAction.COMMENT, user
        )

        if (!isInternal)
        {
            publishCommentNotification(exchange, document, comment, user)
        }

        broadcastCommentAdded(exchange, comment, user)

        return comment
    }

    private fun enforceRateLimit(userId: UUID)
    {
        val now = System.currentTimeMillis()
        val timestamps = recentCommentTimestamps.computeIfAbsent(userId) { ArrayDeque() }
        synchronized(timestamps)
        {
            while (timestamps.isNotEmpty() && now - timestamps.peekFirst() > RATE_LIMIT_WINDOW_MS)
            {
                timestamps.pollFirst()
            }
            if (timestamps.size >= RATE_LIMIT_MAX_COMMENTS)
            {
                throw ClientErrorException(
                    "You are posting comments too quickly. Please wait a moment and try again.",
                    429,
                )
            }
            timestamps.addLast(now)
        }
    }

    private fun broadcastCommentAdded(
        exchange: com.docuhyphen.app.api.model.entity.Exchange,
        comment: ExchangeDocumentComment,
        user: com.docuhyphen.app.api.model.entity.AppUser,
    )
    {
        val exchangeUuid = exchange.id
        val message = RealtimeMessage(
            type = RealtimeMessageType.DOCUMENT_COMMENT_ADDED,
            exchangeId = exchangeUuid.toString(),
            documentId = comment.document?.id?.toString(),
            commentId = comment.id.toString(),
            userId = user.id.toString(),
        )

        runCatching {
            val organizationId = comment.internalOrganizationId
            if (organizationId == null)
            {
                documentCommentRealtimeService.scheduleAfterCommit(exchangeUuid, message, null)
                return@runCatching
            }

            val exchangeUserIds = buildSet {
                add(user.id)
                exchange.initiator?.id?.let(::add)
                addAll(shareService.recipientUserIds(exchangeUuid))
            }
            val recipientUserIds = organizationMembershipService.membersOf(organizationId)
                .asSequence()
                .map { it.id }
                .filter { it in exchangeUserIds }
                .toSet()
            documentCommentRealtimeService.scheduleAfterCommit(exchangeUuid, message, recipientUserIds)
        }.onFailure { exception ->
            logger.warn(
                "Failed to broadcast document comment event for Exchange={} document={}",
                exchangeUuid,
                comment.document?.id,
                exception,
            )
        }
    }

    private fun publishCommentNotification(
        exchange: com.docuhyphen.app.api.model.entity.Exchange,
        document: Document,
        comment: ExchangeDocumentComment,
        user: com.docuhyphen.app.api.model.entity.AppUser,
    )
    {
        val exchangeUuid = exchange.id
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
                    put("exchangeId", exchangeUuid.toString())
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
