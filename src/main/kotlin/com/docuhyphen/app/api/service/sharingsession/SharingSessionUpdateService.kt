package com.docuhyphen.app.api.service.sharingsession

import com.docuhyphen.app.api.exception.NoAuthOtpException
import com.docuhyphen.app.api.exception.SharingSessionNotFoundException
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.entity.RoleName
import com.docuhyphen.app.api.model.entity.SharingSession
import com.docuhyphen.app.api.model.entity.SharingSessionStatus
import com.docuhyphen.app.api.realtime.RealtimeEventService
import com.docuhyphen.app.api.realtime.RealtimeMessage
import com.docuhyphen.app.api.realtime.RealtimeMessageType
import com.docuhyphen.app.api.repository.ShareRepository
import com.docuhyphen.app.api.repository.SharingSessionRepository
import com.docuhyphen.app.api.resource.model.UpdateSharingSessionRequest
import com.docuhyphen.app.api.service.AppUserService
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
import java.util.concurrent.ConcurrentHashMap

@ApplicationScoped
class SharingSessionUpdateService @Inject constructor(
    private val sharingSessionRepository: SharingSessionRepository,
    private val emailService: EmailService,
    private val emailTemplateService: EmailTemplateService,
    private val realtimeEventService: RealtimeEventService,
    private val otpService: OtpService,
    private val userContactService: UserContactService,
    private val shareService: ShareService,
    private val shareRepository: ShareRepository,
    private val appUserService: AppUserService,
)
{
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    companion object
    {
        private val logger = LoggerFactory.getLogger(SharingSessionUpdateService::class.java)
        private const val OTP_VALIDITY_SECONDS: Long = 600
        private const val OTP_RESEND_COOLDOWN_SECONDS: Long = 30
        private const val OTP_MAX_FAILED_ATTEMPTS: Int = 5
        private const val OTP_LOCKOUT_SECONDS: Long = 300
        private const val MIN_NO_AUTH_ACCESS_VALIDITY_DAYS: Int = 1
        private const val MAX_NO_AUTH_ACCESS_VALIDITY_DAYS: Int = 30
        private val EMAIL_DATE_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z")
    }

    private data class OtpAttemptState(
        var failedAttempts: Int = 0,
        var lockedUntil: Instant? = null,
    )

    private val otpAttemptStates: MutableMap<UUID, OtpAttemptState> = ConcurrentHashMap()

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

            // Dual-write: a terminal session status revokes the mirrored shares so the new
            // model reflects that access has ended.
            if (it == SharingSessionStatus.ENDED || it == SharingSessionStatus.REJECTED)
            {
                shareService.revokeAllForResource(ResourceType.SHARING_SESSION, sessionUUID)
            }
        }

        request?.rejectionReason?.let {
            sharingSessionRepository.updateRejectionReason(sessionUUID, it)
        }

        request?.requireRecipientSignIn?.let {
            sharingSessionRepository.updateRequireRecipientSignIn(sessionUUID, it)
        }

        // Per-document permissions are stored as constraints on the recipient's Share row.
        // When the initiator updates them, rebuild the constraints JSON and propagate to all
        // recipient shares (and their inherited children).
        if (request?.allowDocumentAddition != null ||
            request?.allowDocumentDeletion != null ||
            request?.allowDocumentDownload != null ||
            request?.allowDocumentUpdate != null ||
            request?.allowDocumentUpload != null)
        {
            // Read the current constraints to preserve flags that aren't being changed.
            val currentJson = shareService.recipientConstraintsJson(sessionUUID) ?: "{}"
            val addition = request?.allowDocumentAddition
                ?: currentJson.contains("\"allow_document_addition\":true")
            val deletion = request?.allowDocumentDeletion
                ?: currentJson.contains("\"allow_document_deletion\":true")
            val download = request?.allowDocumentDownload
                ?: currentJson.contains("\"can_download\":true")
            val update = request?.allowDocumentUpdate
                ?: currentJson.contains("\"allow_document_update\":true")
            val upload = request?.allowDocumentUpload
                ?: currentJson.contains("\"allow_document_upload\":true")
            val requireSignIn = request?.requireRecipientSignIn
                ?: currentJson.contains("\"require_recipient_sign_in\":true")

            val constraintsJson =
                """{"can_download":$download,""" +
                """"allow_document_addition":$addition,""" +
                """"allow_document_deletion":$deletion,""" +
                """"allow_document_update":$update,""" +
                """"allow_document_upload":$upload,""" +
                """"require_recipient_sign_in":$requireSignIn}"""

            shareService.updateRecipientConstraints(sessionUUID, constraintsJson)
        }

        request?.noAuthAccessValidityDays?.let {
            if (it !in MIN_NO_AUTH_ACCESS_VALIDITY_DAYS..MAX_NO_AUTH_ACCESS_VALIDITY_DAYS)
            {
                throw IllegalArgumentException("No-auth access validity must be between $MIN_NO_AUTH_ACCESS_VALIDITY_DAYS and $MAX_NO_AUTH_ACCESS_VALIDITY_DAYS days")
            }
            sharingSessionRepository.updateNoAuthAccessValidityDays(sessionUUID, it)
        }

        if (request?.requireRecipientSignIn == true)
        {
            // Force a fresh verification window if no-auth access is re-enabled later.
            sharingSessionRepository.updateNoAuthAccessVerifiedAt(sessionUUID, null)
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
            if (initiator != null)
            {
                // Recipients are the session's direct USER shares (excluding the owner and
                // pure participants). Record a mutual contact between initiator and each.
                shareRepository.findActiveByResource(ResourceType.SHARING_SESSION, sessionUUID)
                    .filter {
                        it.principalKind == PrincipalKind.USER &&
                            it.principalId != initiator.id &&
                            it.roleName != RoleName.OWNER.name &&
                            it.roleName != RoleName.PARTICIPANT.name
                    }
                    .map { it.principalId }
                    .distinct()
                    .forEach { recipientId ->
                        appUserService.getById(recipientId)?.let { recipient ->
                            userContactService.recordMutualOnAccept(initiator, recipient, sessionUUID)
                        }
                    }
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

        shareService.revokeAllForResource(ResourceType.SHARING_SESSION, sessionUUID)

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

        val requestedStatus = sessionStatus
            ?: throw NoAuthOtpException(
                message = "Sharing session status is required",
                reasonCode = "INVALID_STATUS_TRANSITION",
            )

        // Idempotent status updates avoid breaking refresh/retry UX for no-auth recipients.
        if (requestedStatus == session.status)
        {
            return session
        }

        if (session.status != SharingSessionStatus.ACCEPTED_STARTED && session.status != SharingSessionStatus.INITIATED)
        {
            logger.error("Attempted to update a sharing session with an invalid status")
            throw NoAuthOtpException(
                message = "Sharing session is not in a state that can be updated",
                reasonCode = "INVALID_STATUS_TRANSITION",
            )
        }

        if (session.status == SharingSessionStatus.ENDED || session.status == SharingSessionStatus.REJECTED)
        {
            logger.error("Attempted to update a sharing session that has ended or rejected: $sessionStatus")
            throw NoAuthOtpException(
                message = "Sharing session has already ended",
                reasonCode = "INVALID_STATUS_TRANSITION",
            )
        }

        val isValidTransition = when (session.status)
        {
            SharingSessionStatus.INITIATED ->
                requestedStatus == SharingSessionStatus.ACCEPTED_STARTED || requestedStatus == SharingSessionStatus.REJECTED

            SharingSessionStatus.ACCEPTED_STARTED ->
                requestedStatus == SharingSessionStatus.REJECTED

            else -> false
        }

        if (!isValidTransition)
        {
            throw NoAuthOtpException(
                message = "Sharing session cannot transition from ${session.status} to $requestedStatus",
                reasonCode = "INVALID_STATUS_TRANSITION",
            )
        }

        verifyRecipientOtp(session, otp)

        if (requestedStatus == SharingSessionStatus.ACCEPTED_STARTED)
        {
            sharingSessionRepository.updateNoAuthAccessVerifiedAt(sessionUUID, Timestamp.from(Instant.now()))
        }

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
            if (initiator != null)
            {
                shareService.recipientUserIds(sessionUUID).forEach { recipientId ->
                    appUserService.getById(recipientId)?.let { recipient ->
                        userContactService.recordMutualOnAccept(initiator, recipient, sessionUUID)
                    }
                }
            }
        }

        logger.info("Sharing session ${session.sessionName} updated")

        return refreshedSession
    }

    @Transactional
    fun verifyNoAuthAccessCode(sessionId: String, otp: String?): SharingSession
    {
        val sessionUUID = UUID.fromString(sessionId)
        val session = sharingSessionRepository.findById(sessionUUID)
            ?: throw SharingSessionNotFoundException("Sharing session not found")

        if (session.requireRecipientSignIn)
        {
            throw ForbiddenException("Sharing session not found")
        }

        if (session.status == SharingSessionStatus.ENDED || session.status == SharingSessionStatus.REJECTED)
        {
            throw IllegalArgumentException("Sharing session has already ended")
        }

        verifyRecipientOtp(session, otp)
        sharingSessionRepository.updateNoAuthAccessVerifiedAt(sessionUUID, Timestamp.from(Instant.now()))
        sharingSessionRepository.updateLastActivity(sessionUUID, Timestamp.from(Instant.now()))

        return sharingSessionRepository.findById(sessionUUID)
            ?: throw SharingSessionNotFoundException("Sharing session not found")
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

        throwIfOtpLocked(sessionUUID)

        val retryAfterSeconds = recipientOtpResendRetryAfterSeconds(session)
        if (retryAfterSeconds > 0)
        {
            logger.info("noAuthOtp.issue.rejected sessionId={} reason=OTP_RATE_LIMITED retryAfterSeconds={}", sessionId, retryAfterSeconds)
            throw NoAuthOtpException(
                message = "Please wait before requesting another verification code",
                reasonCode = "OTP_RATE_LIMITED",
                retryAfterSeconds = retryAfterSeconds,
            )
        }

        if (session.status == SharingSessionStatus.ENDED || session.status == SharingSessionStatus.REJECTED)
        {
            throw IllegalArgumentException("Sharing session has already ended")
        }
        val recipientEmail = resolveRecipientEmail(session.id)
            ?: throw IllegalArgumentException("Sharing session has no recipient email")

        val otp = otpService.generateEmailOtp()
        session.recipientOtpHash = otpService.hashOtp(otp)
        session.recipientOtpExpiry = Timestamp.from(Instant.now().plusSeconds(OTP_VALIDITY_SECONDS))
        sharingSessionRepository.update(session)
        otpAttemptStates.remove(sessionUUID)
        logger.info("noAuthOtp.issue.success sessionId={} recipientEmail={}", sessionId, recipientEmail)

        val renderedOtpEmail = emailTemplateService.renderNoAuthSharingSessionOtpEmail(
            sessionId = sessionUUID.toString(),
            sessionName = session.sessionName.orEmpty(),
            otp = otp,
            expiryMinutes = OTP_VALIDITY_SECONDS / 60,
            initiatorName = session.initiator?.person?.let { "${it.firstName ?: ""} ${it.lastName ?: ""}".trim() }
                ?.takeIf { it.isNotBlank() }
                ?: session.initiator?.email,
        )

        emailService.sendEmail(
            recipientEmail,
            renderedOtpEmail.subject,
            renderedOtpEmail.body,
            useHtml = true,
        )

        return session
    }

    private fun verifyRecipientOtp(session: SharingSession, providedOtp: String?)
    {
        val sessionId = session.id

        throwIfOtpLocked(sessionId)

        if (providedOtp.isNullOrBlank())
        {
            throw NoAuthOtpException("Verification code is required", "OTP_REQUIRED")
        }
        val storedHash = session.recipientOtpHash
            ?: throw NoAuthOtpException("No verification code has been issued for this session", "OTP_NOT_ISSUED")
        val expiry = session.recipientOtpExpiry
            ?: throw NoAuthOtpException("Verification code has expired", "OTP_EXPIRED")
        if (expiry.before(Timestamp.from(Instant.now())))
        {
            throw NoAuthOtpException("Verification code has expired", "OTP_EXPIRED")
        }
        if (!otpService.verifyEmailOtp(providedOtp, storedHash))
        {
            registerOtpFailure(sessionId)
            logger.info("noAuthOtp.verify.failed sessionId={} reason=OTP_INVALID", sessionId)
            throw NoAuthOtpException("Invalid verification code", "OTP_INVALID")
        }

        // Single-use: clear once accepted/rejected to prevent replay.
        session.recipientOtpHash = null
        session.recipientOtpExpiry = null
        sharingSessionRepository.update(session)
        otpAttemptStates.remove(sessionId)
        logger.info("noAuthOtp.verify.success sessionId={}", sessionId)
    }

    private fun throwIfOtpLocked(sessionId: UUID)
    {
        val now = Instant.now()
        val state = otpAttemptStates[sessionId] ?: return
        val lockedUntil = state.lockedUntil
        if (lockedUntil == null)
        {
            return
        }
        if (lockedUntil.isAfter(now))
        {
            val secondsLeft = java.time.Duration.between(now, lockedUntil).seconds.coerceAtLeast(1)
            logger.info("noAuthOtp.verify.rejected sessionId={} reason=OTP_LOCKED retryAfterSeconds={}", sessionId, secondsLeft)
            throw NoAuthOtpException(
                message = "Too many invalid verification attempts. Try again later.",
                reasonCode = "OTP_LOCKED",
                retryAfterSeconds = secondsLeft,
            )
        }

        otpAttemptStates.remove(sessionId)
    }

    private fun registerOtpFailure(sessionId: UUID)
    {
        val now = Instant.now()
        val state = otpAttemptStates.computeIfAbsent(sessionId) { OtpAttemptState() }
        if (state.lockedUntil?.isAfter(now) == true)
        {
            return
        }

        state.failedAttempts += 1
        if (state.failedAttempts >= OTP_MAX_FAILED_ATTEMPTS)
        {
            state.failedAttempts = 0
            state.lockedUntil = now.plusSeconds(OTP_LOCKOUT_SECONDS)
            logger.info("noAuthOtp.verify.locked sessionId={} lockSeconds={}", sessionId, OTP_LOCKOUT_SECONDS)
        }
    }

    private fun recipientOtpResendRetryAfterSeconds(session: SharingSession): Long
    {
        val expiry = session.recipientOtpExpiry?.toInstant() ?: return 0
        val issuedAt = expiry.minusSeconds(OTP_VALIDITY_SECONDS)
        val nextAllowedAt = issuedAt.plusSeconds(OTP_RESEND_COOLDOWN_SECONDS)
        val now = Instant.now()
        if (!nextAllowedAt.isAfter(now))
        {
            return 0
        }

        return java.time.Duration.between(now, nextAllowedAt).seconds.coerceAtLeast(1)
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
            shareService.recipientUserIds(session.id).forEach { realtimeEventService.broadcastToUser(it, message) }
        }.onFailure { e ->
            logger.warn("Failed to broadcast status change for session={} status={}", session.id, newStatus, e)
        }
    }

    /** Email of the session's primary recipient, resolved from its recipient Share. */
    private fun resolveRecipientEmail(sessionId: UUID): String? =
        shareService.primaryRecipientUserId(sessionId)?.let { appUserService.getById(it)?.email }

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
            EmailAudience.RECIPIENT -> resolveRecipientEmail(session.id)
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
            recipientEmail = resolveRecipientEmail(session.id) ?: "Unknown",
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