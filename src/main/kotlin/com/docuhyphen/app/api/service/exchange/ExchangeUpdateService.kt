package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.exception.NoAuthOtpException
import com.docuhyphen.app.api.exception.ExchangeNotFoundException
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.entity.RoleName
import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.realtime.RealtimeEventService
import com.docuhyphen.app.api.realtime.RealtimeMessage
import com.docuhyphen.app.api.realtime.RealtimeMessageType
import com.docuhyphen.app.api.repository.ShareRepository
import com.docuhyphen.app.api.repository.ExchangeRepository
import com.docuhyphen.app.api.resource.model.UpdateExchangeRequest
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
class ExchangeUpdateService @Inject constructor(
    private val exchangeRepository: ExchangeRepository,
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
        private val logger = LoggerFactory.getLogger(ExchangeUpdateService::class.java)
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
    fun updateExchange(
        exchangeId: String,
        request: UpdateExchangeRequest?
    )
    {
        val sessionUUID = UUID.fromString(exchangeId)

        exchangeRepository.findById(sessionUUID)
            ?: throw ExchangeNotFoundException("Exchange not found")

        request?.name?.let {
            exchangeRepository.updateSessionName(sessionUUID, it)
        }

        request?.description?.let {
            exchangeRepository.updateDescription(sessionUUID, it)
        }

        request?.status?.let {

            exchangeRepository.updateStatus(sessionUUID, it)

            if (it == ExchangeStatus.ENDED)
            {
                exchangeRepository.updateEndDate(sessionUUID, Timestamp.from(Instant.now()))
            }

            // Dual-write: a terminal session status revokes the mirrored shares so the new
            // model reflects that access has ended.
            if (it == ExchangeStatus.ENDED || it == ExchangeStatus.REJECTED)
            {
                shareService.revokeAllForResource(ResourceType.EXCHANGE, sessionUUID)
            }
        }

        request?.rejectionReason?.let {
            exchangeRepository.updateRejectionReason(sessionUUID, it)
        }

        request?.requireRecipientSignIn?.let {
            exchangeRepository.updateRequireRecipientSignIn(sessionUUID, it)
        }

        // Per-document permissions are stored as constraints on the recipient's Share row.
        // When the initiator updates them, rebuild the constraints JSON and propagate to all
        // recipient shares (and their inherited children).
        if (request?.allowDocumentAddition != null ||
            request?.allowDocumentDeletion != null ||
            request?.allowDocumentDownload != null ||
            request?.allowDocumentUpdate != null ||
            request?.allowDocumentUpload != null ||
            request?.allowedDownloadFormats != null)
        {
            // Read the current constraints to preserve flags that aren't being changed.
            val currentJson = shareService.recipientConstraintsJson(sessionUUID) ?: "{}"
            val currentConstraints = com.docuhyphen.app.api.service.auth.authz.ShareConstraints.parse(currentJson)
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

            val parts = mutableListOf(
                """"can_download":$download""",
                """"allow_document_addition":$addition""",
                """"allow_document_deletion":$deletion""",
                """"allow_document_update":$update""",
                """"allow_document_upload":$upload""",
                """"require_recipient_sign_in":$requireSignIn""",
            )

            // Preserve or update allowed_download_formats
            // Empty list = explicitly clear restriction; null = preserve current value
            val formats = if (request?.allowedDownloadFormats != null) request.allowedDownloadFormats else currentConstraints.allowedDownloadFormats
            if (formats != null && formats.isNotEmpty())
            {
                val formatsArray = formats.joinToString(",") { "\"$it\"" }
                parts.add(""""allowed_download_formats":[$formatsArray]""")
            }

            val constraintsJson = parts.joinToString(prefix = "{", postfix = "}", separator = ",")

            shareService.updateRecipientConstraints(sessionUUID, constraintsJson)
        }

        request?.noAuthAccessValidityDays?.let {
            if (it !in MIN_NO_AUTH_ACCESS_VALIDITY_DAYS..MAX_NO_AUTH_ACCESS_VALIDITY_DAYS)
            {
                throw IllegalArgumentException("No-auth access validity must be between $MIN_NO_AUTH_ACCESS_VALIDITY_DAYS and $MAX_NO_AUTH_ACCESS_VALIDITY_DAYS days")
            }
            exchangeRepository.updateNoAuthAccessValidityDays(sessionUUID, it)
        }

        if (request?.requireRecipientSignIn == true)
        {
            // Force a fresh verification window if no-auth access is re-enabled later.
            exchangeRepository.updateNoAuthAccessVerifiedAt(sessionUUID, null)
        }

        exchangeRepository.updateLastActivity(sessionUUID, Timestamp.from(Instant.now()))

        val updatedSession = exchangeRepository.findById(sessionUUID)!!

        request?.status?.let {
            sendStatusChangeEmails(updatedSession, it, request.rejectionReason)
        }

        request?.status?.let { newStatus ->
            broadcastStatusChange(updatedSession, newStatus)
        }

        if (request?.status == ExchangeStatus.ACCEPTED_STARTED)
        {
            val initiator = updatedSession.initiator
            if (initiator != null)
            {
                // Recipients are the session's direct USER shares (excluding the owner and
                // pure participants). Record a mutual contact between initiator and each.
                shareRepository.findActiveByResource(ResourceType.EXCHANGE, sessionUUID)
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

        logger.info("Sharing session ${updatedSession.name} completed")
    }

    fun deleteExchange(exchangeId: String?)
    {
        if (exchangeId == null)
        {
            throw IllegalArgumentException("Session ID cannot be null")
        }

        val sessionUUID = UUID.fromString(exchangeId)

        var session = exchangeRepository.findById(sessionUUID)?.apply {

            isDeleted = true
            dateDeleted = Timestamp.from(Instant.now())

        } ?: throw ExchangeNotFoundException("Exchange not found")

        exchangeRepository.update(session)

        shareService.revokeAllForResource(ResourceType.EXCHANGE, sessionUUID)

        logger.info("Sharing session ${session.name} deleted")
    }

    fun updateNoAuthExchange(
        exchangeId: String,
        sessionStatus: ExchangeStatus?,
        otp: String?,
        rejectReason: String?
    ): Exchange
    {
        val sessionUUID = UUID.fromString(exchangeId)

        val session = exchangeRepository.findById(sessionUUID)
            ?: throw ExchangeNotFoundException("Exchange not found")

        if (session.requireRecipientSignIn)
        {
            logger.error("Attempted to access a exchange that requires recipient sign-in")
            throw ForbiddenException("Exchange not found")
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

        if (session.status != ExchangeStatus.ACCEPTED_STARTED && session.status != ExchangeStatus.INITIATED)
        {
            logger.error("Attempted to update a exchange with an invalid status")
            throw NoAuthOtpException(
                message = "Sharing session is not in a state that can be updated",
                reasonCode = "INVALID_STATUS_TRANSITION",
            )
        }

        if (session.status == ExchangeStatus.ENDED || session.status == ExchangeStatus.REJECTED)
        {
            logger.error("Attempted to update a exchange that has ended or rejected: $sessionStatus")
            throw NoAuthOtpException(
                message = "Sharing session has already ended",
                reasonCode = "INVALID_STATUS_TRANSITION",
            )
        }

        val isValidTransition = when (session.status)
        {
            ExchangeStatus.INITIATED ->
                requestedStatus == ExchangeStatus.ACCEPTED_STARTED || requestedStatus == ExchangeStatus.REJECTED

            ExchangeStatus.ACCEPTED_STARTED ->
                requestedStatus == ExchangeStatus.REJECTED

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

        if (requestedStatus == ExchangeStatus.ACCEPTED_STARTED)
        {
            exchangeRepository.updateNoAuthAccessVerifiedAt(sessionUUID, Timestamp.from(Instant.now()))
        }

        sessionStatus?.let {
            exchangeRepository.updateStatus(sessionUUID, it)

            if (it == ExchangeStatus.REJECTED)
            {
                rejectReason?.let {
                    exchangeRepository.updateRejectionReason(sessionUUID, it)
                }

                exchangeRepository.updateEndDate(sessionUUID, Timestamp.from(Instant.now()))
            }
        }

        exchangeRepository.updateLastActivity(sessionUUID, Timestamp.from(Instant.now()))

        val refreshedSession = exchangeRepository.findById(sessionUUID)!!

        sessionStatus?.let {
            sendStatusChangeEmails(refreshedSession, it, rejectReason)
            broadcastStatusChange(refreshedSession, it)
        }

        if (sessionStatus == ExchangeStatus.ACCEPTED_STARTED)
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

        logger.info("Sharing session ${session.name} updated")

        return refreshedSession
    }

    @Transactional
    fun verifyNoAuthAccessCode(exchangeId: String, otp: String?): Exchange
    {
        val sessionUUID = UUID.fromString(exchangeId)
        val session = exchangeRepository.findById(sessionUUID)
            ?: throw ExchangeNotFoundException("Exchange not found")

        if (session.requireRecipientSignIn)
        {
            throw ForbiddenException("Exchange not found")
        }

        if (session.status == ExchangeStatus.ENDED || session.status == ExchangeStatus.REJECTED)
        {
            throw IllegalArgumentException("Sharing session has already ended")
        }

        verifyRecipientOtp(session, otp)
        exchangeRepository.updateNoAuthAccessVerifiedAt(sessionUUID, Timestamp.from(Instant.now()))
        exchangeRepository.updateLastActivity(sessionUUID, Timestamp.from(Instant.now()))

        return exchangeRepository.findById(sessionUUID)
            ?: throw ExchangeNotFoundException("Exchange not found")
    }

    @Transactional
    fun issueRecipientOtp(exchangeId: String): Exchange
    {
        val sessionUUID = UUID.fromString(exchangeId)
        val session = exchangeRepository.findById(sessionUUID)
            ?: throw ExchangeNotFoundException("Exchange not found")

        if (session.requireRecipientSignIn)
        {
            // OTP is only relevant for the no-auth recipient flow.
            throw ForbiddenException("Exchange not found")
        }

        throwIfOtpLocked(sessionUUID)

        val retryAfterSeconds = recipientOtpResendRetryAfterSeconds(session)
        if (retryAfterSeconds > 0)
        {
            logger.info("noAuthOtp.issue.rejected exchangeId={} reason=OTP_RATE_LIMITED retryAfterSeconds={}", exchangeId, retryAfterSeconds)
            throw NoAuthOtpException(
                message = "Please wait before requesting another verification code",
                reasonCode = "OTP_RATE_LIMITED",
                retryAfterSeconds = retryAfterSeconds,
            )
        }

        if (session.status == ExchangeStatus.ENDED || session.status == ExchangeStatus.REJECTED)
        {
            throw IllegalArgumentException("Sharing session has already ended")
        }
        val recipientEmail = resolveRecipientEmail(session.id)
            ?: throw IllegalArgumentException("Sharing session has no recipient email")

        val otp = otpService.generateEmailOtp()
        session.recipientOtpHash = otpService.hashOtp(otp)
        session.recipientOtpExpiry = Timestamp.from(Instant.now().plusSeconds(OTP_VALIDITY_SECONDS))
        exchangeRepository.update(session)
        otpAttemptStates.remove(sessionUUID)
        logger.info("noAuthOtp.issue.success exchangeId={} recipientEmail={}", exchangeId, recipientEmail)

        val renderedOtpEmail = emailTemplateService.renderNoAuthExchangeOtpEmail(
            exchangeId = sessionUUID.toString(),
            name = session.name.orEmpty(),
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

    private fun verifyRecipientOtp(session: Exchange, providedOtp: String?)
    {
        val exchangeId = session.id

        throwIfOtpLocked(exchangeId)

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
            registerOtpFailure(exchangeId)
            logger.info("noAuthOtp.verify.failed exchangeId={} reason=OTP_INVALID", exchangeId)
            throw NoAuthOtpException("Invalid verification code", "OTP_INVALID")
        }

        // Single-use: clear once accepted/rejected to prevent replay.
        session.recipientOtpHash = null
        session.recipientOtpExpiry = null
        exchangeRepository.update(session)
        otpAttemptStates.remove(exchangeId)
        logger.info("noAuthOtp.verify.success exchangeId={}", exchangeId)
    }

    private fun throwIfOtpLocked(exchangeId: UUID)
    {
        val now = Instant.now()
        val state = otpAttemptStates[exchangeId] ?: return
        val lockedUntil = state.lockedUntil
        if (lockedUntil == null)
        {
            return
        }
        if (lockedUntil.isAfter(now))
        {
            val secondsLeft = java.time.Duration.between(now, lockedUntil).seconds.coerceAtLeast(1)
            logger.info("noAuthOtp.verify.rejected exchangeId={} reason=OTP_LOCKED retryAfterSeconds={}", exchangeId, secondsLeft)
            throw NoAuthOtpException(
                message = "Too many invalid verification attempts. Try again later.",
                reasonCode = "OTP_LOCKED",
                retryAfterSeconds = secondsLeft,
            )
        }

        otpAttemptStates.remove(exchangeId)
    }

    private fun registerOtpFailure(exchangeId: UUID)
    {
        val now = Instant.now()
        val state = otpAttemptStates.computeIfAbsent(exchangeId) { OtpAttemptState() }
        if (state.lockedUntil?.isAfter(now) == true)
        {
            return
        }

        state.failedAttempts += 1
        if (state.failedAttempts >= OTP_MAX_FAILED_ATTEMPTS)
        {
            state.failedAttempts = 0
            state.lockedUntil = now.plusSeconds(OTP_LOCKOUT_SECONDS)
            logger.info("noAuthOtp.verify.locked exchangeId={} lockSeconds={}", exchangeId, OTP_LOCKOUT_SECONDS)
        }
    }

    private fun recipientOtpResendRetryAfterSeconds(session: Exchange): Long
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

    private fun broadcastStatusChange(session: Exchange, newStatus: ExchangeStatus)
    {
        runCatching {
            val exchangeId = session.id
                ?: throw IllegalArgumentException("Sharing session id is required")

            val message = RealtimeMessage(
                type = RealtimeMessageType.EXCHANGE_STATUS_CHANGED,
                exchangeId = exchangeId.toString(),
                status = newStatus.name,
            )

            // 1) Existing behavior: update devices actively viewing/subscribed to this session.
            realtimeEventService.broadcastToExchange(
                exchangeId,
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
    private fun resolveRecipientEmail(exchangeId: UUID): String? =
        shareService.primaryRecipientUserId(exchangeId)?.let { appUserService.getById(it)?.email }

    private enum class EmailAudience
    {
        INITIATOR,
        RECIPIENT,
    }

    private fun sendStatusChangeEmails(
        session: Exchange,
        status: ExchangeStatus,
        rejectionReasonOverride: String? = null,
    )
    {
        when (status)
        {
            ExchangeStatus.INITIATED -> {
                sendStatusChangeEmailForAudience(
                    session = session,
                    status = status,
                    audience = EmailAudience.RECIPIENT,
                    rejectionReasonOverride = rejectionReasonOverride,
                )
            }

            ExchangeStatus.ACCEPTED_STARTED,
            ExchangeStatus.REJECTED -> {
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

            ExchangeStatus.ENDED -> {
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
        session: Exchange,
        status: ExchangeStatus,
        audience: EmailAudience,
        rejectionReasonOverride: String? = null,
    )
    {
        val destination = when (audience)
        {
            EmailAudience.INITIATOR -> session.initiator?.email
            EmailAudience.RECIPIENT -> resolveRecipientEmail(session.id)
        } ?: return

        val template = emailTemplateService.renderExchangeStatusEmail(
            status = status,
            audience = when (audience)
            {
                EmailAudience.INITIATOR -> EmailTemplateService.ExchangeStatusEmailAudience.INITIATOR
                EmailAudience.RECIPIENT -> EmailTemplateService.ExchangeStatusEmailAudience.RECIPIENT
            },
            exchangeId = session.id.toString(),
            name = session.name?.ifBlank { "Untitled session" } ?: "Untitled session",
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

    private fun statusText(status: ExchangeStatus): String
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