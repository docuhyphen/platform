package com.docuhyphen.app.api.service.sharingsession

import com.docuhyphen.app.api.exception.SharingSessionNotFoundException
import com.docuhyphen.app.api.model.entity.SharingSession
import com.docuhyphen.app.api.model.entity.SharingSessionStatus
import com.docuhyphen.app.api.realtime.RealtimeEventService
import com.docuhyphen.app.api.realtime.RealtimeMessage
import com.docuhyphen.app.api.realtime.RealtimeMessageType
import com.docuhyphen.app.api.repository.SharingSessionRepository
import com.docuhyphen.app.api.resource.model.UpdateSharingSessionRequest
import com.docuhyphen.app.api.service.UserContactService
import com.docuhyphen.app.api.service.communication.EmailService
import com.docuhyphen.app.api.service.communication.EmailTemplateService
import com.docuhyphen.app.api.service.communication.OtpService
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

@ApplicationScoped
class SharingSessionUpdateService @Inject constructor(
    private val sharingSessionRepository: SharingSessionRepository,
    private val emailService: EmailService,
    private val emailTemplateService: EmailTemplateService,
    private val realtimeEventService: RealtimeEventService,
    private val otpService: OtpService,
    private val userContactService: UserContactService,
)
{
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    companion object
    {
        private val logger = LoggerFactory.getLogger(SharingSessionUpdateService::class.java)
        private const val OTP_VALIDITY_SECONDS: Long = 600
        private val EMAIL_DATE_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z")
    }

    @Transactional
    fun updateSharingSession(
        sessionId: String,
        request: UpdateSharingSessionRequest?
    )
    {
        val sessionUUID = UUID.fromString(sessionId)

        sharingSessionRepository.findById(sessionUUID)
            ?: throw SharingSessionNotFoundException("Sharing session not found")

        request?.sessionName?.let {
            sharingSessionRepository.updateSessionName(sessionUUID, it)
        }

        request?.description?.let {
            sharingSessionRepository.updateDescription(sessionUUID, it)
        }

        request?.status?.let {

            sharingSessionRepository.updateStatus(sessionUUID, it)

            if (it == SharingSessionStatus.ENDED)
            {
                sharingSessionRepository.updateEndDate(sessionUUID, Timestamp.from(Instant.now()))
            }
        }

        request?.rejectionReason?.let {
            sharingSessionRepository.updateRejectionReason(sessionUUID, it)
        }

        request?.requireRecipientSignIn?.let {
            sharingSessionRepository.updateRequireRecipientSignIn(sessionUUID, it)
        }

        request?.allowDocumentAddition?.let {
            sharingSessionRepository.updateAllowDocumentAddition(sessionUUID, it)
        }

        request?.allowDocumentDeletion?.let {
            sharingSessionRepository.updateAllowDocumentDeletion(sessionUUID, it)
        }

        request?.allowDocumentDownload?.let {
            sharingSessionRepository.updateAllowDocumentDownload(sessionUUID, it)
        }

        request?.allowDocumentUpdate?.let {
            sharingSessionRepository.updateAllowDocumentUpdate(sessionUUID, it)
        }

        request?.allowDocumentUpload?.let {
            sharingSessionRepository.updateAllowDocumentUpload(sessionUUID, it)
        }

        sharingSessionRepository.updateLastActivity(sessionUUID, Timestamp.from(Instant.now()))

        val updatedSession = sharingSessionRepository.findById(sessionUUID)!!

        request?.status?.let {
            sendStatusChangeEmails(updatedSession, it, request.rejectionReason)
        }

        request?.status?.let { newStatus ->
            broadcastStatusChange(updatedSession, newStatus)
        }

        if (request?.status == SharingSessionStatus.ACCEPTED_STARTED)
        {
            val initiator = updatedSession.initiator
            val recipient = updatedSession.recipient
            if (initiator != null && recipient != null)
            {
                userContactService.recordMutualOnAccept(initiator, recipient, sessionUUID)
            }
        }

        logger.info("Sharing session ${updatedSession.sessionName} completed")
    }

    fun deleteSharingSession(sessionId: String?)
    {
        if (sessionId == null)
        {
            throw IllegalArgumentException("Session ID cannot be null")
        }

        val sessionUUID = UUID.fromString(sessionId)

        var session = sharingSessionRepository.findById(sessionUUID)?.apply {

            isDeleted = true
            dateDeleted = Timestamp.from(Instant.now())

        } ?: throw SharingSessionNotFoundException("Sharing session not found")

        sharingSessionRepository.update(session)

        logger.info("Sharing session ${session.sessionName} deleted")
    }

    fun updateNoAuthSharingSession(
        sessionId: String,
        sessionStatus: SharingSessionStatus?,
        otp: String?,
        rejectReason: String?
    ): SharingSession
    {
        val sessionUUID = UUID.fromString(sessionId)

        val session = sharingSessionRepository.findById(sessionUUID)
            ?: throw SharingSessionNotFoundException("Sharing session not found")

        if (session.requireRecipientSignIn)
        {
            logger.error("Attempted to access a sharing session that requires recipient sign-in")
            throw ForbiddenException("Sharing session not found")
        }

        if (session.status != SharingSessionStatus.ACCEPTED_STARTED && session.status != SharingSessionStatus.INITIATED)
        {
            logger.error("Attempted to update a sharing session with an invalid status")
            throw IllegalArgumentException("Sharing session not found")
        }

        if (session.status == SharingSessionStatus.ENDED || session.status == SharingSessionStatus.REJECTED)
        {
            logger.error("Attempted to update a sharing session that has ended or rejected: $sessionStatus")
            throw IllegalArgumentException("Sharing session not found")
        }

        verifyRecipientOtp(session, otp)

        sessionStatus?.let {
            sharingSessionRepository.updateStatus(sessionUUID, it)

            if (it == SharingSessionStatus.REJECTED)
            {
                rejectReason?.let {
                    sharingSessionRepository.updateRejectionReason(sessionUUID, it)
                }

                sharingSessionRepository.updateEndDate(sessionUUID, Timestamp.from(Instant.now()))
            }
        }

        sharingSessionRepository.updateLastActivity(sessionUUID, Timestamp.from(Instant.now()))

        val refreshedSession = sharingSessionRepository.findById(sessionUUID)!!

        sessionStatus?.let {
            sendStatusChangeEmails(refreshedSession, it, rejectReason)
            broadcastStatusChange(refreshedSession, it)
        }

        if (sessionStatus == SharingSessionStatus.ACCEPTED_STARTED)
        {
            val initiator = refreshedSession.initiator
            val recipient = refreshedSession.recipient
            if (initiator != null && recipient != null)
            {
                userContactService.recordMutualOnAccept(initiator, recipient, sessionUUID)
            }
        }

        logger.info("Sharing session ${session.sessionName} updated")

        return refreshedSession
    }

    @Transactional
    fun issueRecipientOtp(sessionId: String): SharingSession
    {
        val sessionUUID = UUID.fromString(sessionId)
        val session = sharingSessionRepository.findById(sessionUUID)
            ?: throw SharingSessionNotFoundException("Sharing session not found")

        if (session.requireRecipientSignIn)
        {
            // OTP is only relevant for the no-auth recipient flow.
            throw ForbiddenException("Sharing session not found")
        }
        if (session.status == SharingSessionStatus.ENDED || session.status == SharingSessionStatus.REJECTED)
        {
            throw IllegalArgumentException("Sharing session has already ended")
        }
        val recipientEmail = session.recipient?.email
            ?: throw IllegalArgumentException("Sharing session has no recipient email")

        val otp = otpService.generateEmailOtp()
        session.recipientOtpHash = otpService.hashOtp(otp)
        session.recipientOtpExpiry = Timestamp.from(Instant.now().plusSeconds(OTP_VALIDITY_SECONDS))
        sharingSessionRepository.update(session)

        emailService.sendEmail(
            recipientEmail,
            "Sharing Session Verification Code",
            "Your verification code for the sharing session \"${session.sessionName ?: ""}\" is: $otp. " +
                "It expires in ${OTP_VALIDITY_SECONDS / 60} minutes."
        )

        return session
    }

    private fun verifyRecipientOtp(session: SharingSession, providedOtp: String?)
    {
        if (providedOtp.isNullOrBlank())
        {
            throw IllegalArgumentException("Verification code is required")
        }
        val storedHash = session.recipientOtpHash
            ?: throw IllegalArgumentException("No verification code has been issued for this session")
        val expiry = session.recipientOtpExpiry
            ?: throw IllegalArgumentException("Verification code has expired")
        if (expiry.before(Timestamp.from(Instant.now())))
        {
            throw IllegalArgumentException("Verification code has expired")
        }
        if (!otpService.verifyEmailOtp(providedOtp, storedHash))
        {
            throw IllegalArgumentException("Invalid verification code")
        }

        // Single-use: clear once accepted/rejected to prevent replay.
        session.recipientOtpHash = null
        session.recipientOtpExpiry = null
        sharingSessionRepository.update(session)
    }

    private fun broadcastStatusChange(session: SharingSession, newStatus: SharingSessionStatus)
    {
        runCatching {
            val sharingSessionId = session.id
                ?: throw IllegalArgumentException("Sharing session id is required")

            val message = RealtimeMessage(
                type = RealtimeMessageType.SHARING_SESSION_STATUS_CHANGED,
                sharingSessionId = sharingSessionId.toString(),
                status = newStatus.name,
            )

            // 1) Existing behavior: update devices actively viewing/subscribed to this session.
            realtimeEventService.broadcastToSharingSession(
                sharingSessionId,
                message,
            )

            // 2) Also fan out to all devices of both participants so list/count UIs update
            // even when they are not currently subscribed to this session channel.
            session.initiator?.id?.let { realtimeEventService.broadcastToUser(it, message) }
            session.recipient?.id?.let { realtimeEventService.broadcastToUser(it, message) }
        }.onFailure { e ->
            logger.warn("Failed to broadcast status change for session={} status={}", session.id, newStatus, e)
        }
    }

    private enum class EmailAudience
    {
        INITIATOR,
        RECIPIENT,
    }

    private fun sendStatusChangeEmails(
        session: SharingSession,
        status: SharingSessionStatus,
        rejectionReasonOverride: String? = null,
    )
    {
        when (status)
        {
            SharingSessionStatus.INITIATED -> {
                sendStatusChangeEmailForAudience(
                    session = session,
                    status = status,
                    audience = EmailAudience.RECIPIENT,
                    rejectionReasonOverride = rejectionReasonOverride,
                )
            }

            SharingSessionStatus.ACCEPTED_STARTED,
            SharingSessionStatus.REJECTED -> {
                sendStatusChangeEmailForAudience(
                    session = session,
                    status = status,
                    audience = EmailAudience.INITIATOR,
                    rejectionReasonOverride = rejectionReasonOverride,
                )
                sendStatusChangeEmailForAudience(
                    session = session,
                    status = status,
                    audience = EmailAudience.RECIPIENT,
                    rejectionReasonOverride = rejectionReasonOverride,
                )
            }

            SharingSessionStatus.ENDED -> {
                sendStatusChangeEmailForAudience(
                    session = session,
                    status = status,
                    audience = EmailAudience.INITIATOR,
                    rejectionReasonOverride = rejectionReasonOverride,
                )
                sendStatusChangeEmailForAudience(
                    session = session,
                    status = status,
                    audience = EmailAudience.RECIPIENT,
                    rejectionReasonOverride = rejectionReasonOverride,
                )
            }
        }
    }

    private fun sendStatusChangeEmailForAudience(
        session: SharingSession,
        status: SharingSessionStatus,
        audience: EmailAudience,
        rejectionReasonOverride: String? = null,
    )
    {
        val destination = when (audience)
        {
            EmailAudience.INITIATOR -> session.initiator?.email
            EmailAudience.RECIPIENT -> session.recipient?.email
        } ?: return

        val template = emailTemplateService.renderSharingSessionStatusEmail(
            status = status,
            audience = when (audience)
            {
                EmailAudience.INITIATOR -> EmailTemplateService.SharingSessionStatusEmailAudience.INITIATOR
                EmailAudience.RECIPIENT -> EmailTemplateService.SharingSessionStatusEmailAudience.RECIPIENT
            },
            sessionId = session.id.toString(),
            sessionName = session.sessionName?.ifBlank { "Untitled session" } ?: "Untitled session",
            statusText = statusText(status),
            initiatorEmail = session.initiator?.email ?: "Unknown",
            recipientEmail = session.recipient?.email ?: "Unknown",
            documents = session.documents.map { it.title.trim() }.filter { it.isNotBlank() },
            lastActivity = formatTimestamp(session.lastActivity),
            rejectionReason = rejectionReasonOverride ?: session.rejectionReason,
            endedAt = formatTimestamp(session.endDate),
        )
            ?: return

        emailService.sendEmail(destination, template.subject, template.body, useHtml = true)
    }

    private fun statusText(status: SharingSessionStatus): String
    {
        return status.name
            .replace('_', ' ')
            .lowercase()
            .split(" ")
            .joinToString(" ") { token -> token.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }
    }

    private fun formatTimestamp(timestamp: Timestamp?): String
    {
        if (timestamp == null)
        {
            return "Not available"
        }

        return EMAIL_DATE_FORMAT.format(timestamp.toInstant().atZone(ZoneId.systemDefault()))
    }
}