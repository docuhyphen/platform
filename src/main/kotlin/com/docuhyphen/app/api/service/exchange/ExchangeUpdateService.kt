package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.exception.NoAuthOtpException
import com.docuhyphen.app.api.exception.ExchangeNotFoundException
import com.docuhyphen.app.api.exception.WorkflowConflictException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.BasicEntityToDtoTransformer
import com.docuhyphen.app.api.model.DetailedEntityToDtoTransformer
import com.docuhyphen.app.api.model.dto.ExchangeDetailedDto
import com.docuhyphen.app.api.model.dto.NoAuthExchangeBasicDto
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.entity.ExchangeShareRoleName
import com.docuhyphen.app.api.model.entity.ShareSource
import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.realtime.RealtimeEventService
import com.docuhyphen.app.api.realtime.RealtimeMessage
import com.docuhyphen.app.api.realtime.RealtimeMessageType
import com.docuhyphen.app.api.repository.ExchangeRepository
import com.docuhyphen.app.api.repository.ExternalParticipantRepository
import com.docuhyphen.app.api.repository.PrincipalGroupRepository
import com.docuhyphen.app.api.repository.ShareRepository
import com.docuhyphen.app.api.repository.WorkflowInstanceRepository
import com.docuhyphen.app.api.repository.WorkflowStepInstanceRepository
import com.docuhyphen.app.api.resource.model.UpdateExchangeRequest
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.UserContactService
import com.docuhyphen.app.api.service.audit.AuditCaptureFailedException
import com.docuhyphen.app.api.service.audit.AuditDraftInvalidException
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditOwnerScope
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision as AuthDecision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.auth.authz.ShareConstraints
import com.docuhyphen.app.api.service.communication.EmailService
import com.docuhyphen.app.api.service.communication.EmailTemplateService
import com.docuhyphen.app.api.service.communication.OtpService
import com.docuhyphen.app.api.service.workflow.Decision
import com.docuhyphen.app.api.service.workflow.TriggerRequest
import com.docuhyphen.app.api.service.workflow.WorkflowEngineService
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
    private val exchangeRecipientService: ExchangeRecipientService,
    private val externalParticipantRepository: ExternalParticipantRepository,
    private val principalGroupRepository: PrincipalGroupRepository,
    private val shareRepository: ShareRepository,
    private val appUserService: AppUserService,
    private val workflowInstanceRepository: WorkflowInstanceRepository,
    private val workflowStepRepository: WorkflowStepInstanceRepository,
    private val workflowEngineService: WorkflowEngineService,
    private val authTokenContext: AuthTokenContext,
    private val authorizationService: AuthorizationService,
    private val authorizationContextFactory: AuthorizationContextFactory,
    private val auditRecorder: AuditRecorder,
    private val noAuthExchangeAccessTokenService: NoAuthExchangeAccessTokenService,
    private val noAuthExchangeAccessWindowService: NoAuthExchangeAccessWindowService,
    private val lifecycleNotificationService: ExchangeLifecycleNotificationService,
    private val documentThumbnailService: DocumentThumbnailService,
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

    @Transactional(dontRollbackOn = [WorkflowConflictException::class])
    fun updateExchange(
        exchangeId: String,
        request: UpdateExchangeRequest?
    ): ExchangeDetailedDto
    {
        if (request?.status == ExchangeStatus.ACCEPTED_STARTED || request?.status == ExchangeStatus.REJECTED)
        {
            throw IllegalArgumentException("Use the Exchange acceptance decision resource")
        }
        val updatedExchange = updateExchangeInternal(exchangeId, request)
        return DetailedEntityToDtoTransformer.toDto(updatedExchange)
            ?: throw IllegalStateException("Updated Exchange could not be mapped")
    }

    @Transactional(dontRollbackOn = [WorkflowConflictException::class])
    fun decideAcceptance(
        exchangeId: String,
        accepted: Boolean,
        reason: String?,
    )
    {
        updateExchangeInternal(
            exchangeId,
            UpdateExchangeRequest(
                status = if (accepted) ExchangeStatus.ACCEPTED_STARTED else ExchangeStatus.REJECTED,
                rejectionReason = reason,
            ),
            recipientDecision = true,
        )
    }

    private fun updateExchangeInternal(
        exchangeId: String,
        request: UpdateExchangeRequest?,
        recipientDecision: Boolean = false,
    ): Exchange
    {
        val sessionUUID = UUID.fromString(exchangeId)

        val principal = authorizationContextFactory.currentPrincipal()
            ?: throw ExchangeNotFoundException("Exchange not found")
        val authCtx = authorizationContextFactory.currentContext()
        val initialAction = if (recipientDecision) Action.EXCHANGE_ACCEPT else Action.EXCHANGE_VIEW
        if (authorizationService.authorize(principal, initialAction, ResourceRef.exchange(sessionUUID), authCtx)
                is AuthDecision.Deny)
        {
            if (recipientDecision)
            {
                throw ForbiddenException("Only the primary recipient may decide this Exchange")
            }
            throw ExchangeNotFoundException("Exchange not found")
        }

        // Owner-only mutations and ENDED status require EXCHANGE_WRITE.
        val hasOwnerMutation = request?.name != null || request?.description != null ||
            request?.requireRecipientSignIn != null || request?.noAuthAccessValidityDays != null ||
            request?.allowDocumentAddition != null || request?.allowDocumentDeletion != null ||
            request?.allowDocumentDownload != null || request?.allowDocumentUpdate != null ||
            request?.allowDocumentUpload != null || request?.allowedDownloadFormats != null ||
            request?.status == ExchangeStatus.ENDED
        if (hasOwnerMutation)
        {
            if (authorizationService.authorize(principal, Action.EXCHANGE_EDIT, ResourceRef.exchange(sessionUUID), authCtx)
                    is AuthDecision.Deny)
            {
                throw ForbiddenException("Not authorized to edit this exchange")
            }
        }

        val existingExchange = exchangeRepository.findById(sessionUUID)
            ?: throw ExchangeNotFoundException("Exchange not found")

        request?.name?.let {
            exchangeRepository.updateSessionName(sessionUUID, it)
        }

        request?.description?.let {
            exchangeRepository.updateDescription(sessionUUID, it)
        }

        if (request?.status != null)
        {
            val newStatus = request.status!!
            val previousStatus = existingExchange.status

            if (newStatus == ExchangeStatus.RESCINDED)
            {
                throw IllegalArgumentException("Use the rescind action to cancel an outgoing exchange")
            }

            if (newStatus == ExchangeStatus.ACCEPTED_STARTED || newStatus == ExchangeStatus.REJECTED)
            {
                val runningDraftApproval = workflowInstanceRepository
                    .findActiveForSubjectAndTrigger(sessionUUID, "exchange.draft_submitted")
                if (runningDraftApproval != null)
                {
                    throw WorkflowConflictException(
                        "This Exchange is awaiting sender approval before the recipient may decide."
                    )
                }
                val actorId = authTokenContext.authToken.appUser?.id
                    ?: throw ForbiddenException("Only the primary recipient may decide this Exchange")
                try
                {
                    exchangeRecipientService.recordPrimaryDecision(
                        exchange = existingExchange,
                        appUserId = actorId,
                        accepted = newStatus == ExchangeStatus.ACCEPTED_STARTED,
                    )
                }
                catch (exception: IllegalArgumentException)
                {
                    throw ForbiddenException("Only the primary recipient may decide this Exchange")
                }
            }

            // --- Workflow routing for ACCEPTED_STARTED and REJECTED -----------------------
            // If an acceptance workflow is running for this exchange, route the decision
            // through the engine instead of writing the status directly. The engine's event
            // handler (ExchangeApprovalEventHandler) will apply the status transition.
            if (newStatus == ExchangeStatus.ACCEPTED_STARTED || newStatus == ExchangeStatus.REJECTED)
            {
                val runningAcceptance = workflowInstanceRepository
                    .findActiveForSubjectAndTrigger(sessionUUID, "exchange.acceptance_pending")
                if (runningAcceptance != null)
                {
                    val currentStep = workflowStepRepository
                        .findCurrent(runningAcceptance.id, runningAcceptance.currentStepIndex)
                        ?: throw IllegalStateException("Acceptance workflow has no current step for exchange $sessionUUID")
                    val currentUser = authTokenContext.authToken.appUser
                        ?: throw IllegalStateException("No authenticated user in context")
                    val decision = if (newStatus == ExchangeStatus.ACCEPTED_STARTED) Decision.APPROVE else Decision.REJECT
                    workflowEngineService.recordDecision(
                        stepInstanceId = currentStep.id,
                        decider = PrincipalRef(PrincipalKind.USER, currentUser.id),
                        decision = decision,
                        reason = request.rejectionReason,
                    )
                    // Write the status directly: EVENT_EXCHANGE_ACTIVATED intentionally does not
                    // advance status when requireRecipientAcceptance=true (to let the acceptance
                    // dialog fire first). When the recipient explicitly accepts/rejects via the
                    // dialog and routing lands here, this path owns the status transition.
                    exchangeRepository.updateStatus(sessionUUID, newStatus)
                    if (newStatus == ExchangeStatus.REJECTED)
                    {
                        exchangeRepository.updateEndDate(sessionUUID, Timestamp.from(Instant.now()))
                        shareService.revokeAllForResource(ResourceType.EXCHANGE, sessionUUID, resourceLabel = existingExchange.name)
                    }
                    request.rejectionReason?.let { exchangeRepository.updateRejectionReason(sessionUUID, it) }
                    exchangeRepository.updateLastActivity(sessionUUID, Timestamp.from(Instant.now()))
                    val updatedSession = exchangeRepository.findById(sessionUUID)!!
                    recordLifecycleTransition(sessionUUID, existingExchange.name, existingExchange.ownerOrganizationId, previousStatus, newStatus)
                    sendStatusChangeEmails(updatedSession, newStatus, request.rejectionReason)
                    broadcastStatusChange(updatedSession, newStatus)
                    logger.info(
                        "Exchange {}: routed {} decision through acceptance workflow",
                        sessionUUID, newStatus,
                    )
                    return updatedSession
                }
            }

            // --- Workflow guard for ENDED --------------------------------------------------
            // Fire the exchange.ending trigger. If a matching workflow starts, hold the
            // direct ENDED write and return 409 to the caller. The workflow will apply ENDED
            // via the exchange.ended_confirmed event when its steps complete.
            if (newStatus == ExchangeStatus.ENDED)
            {
                // Serialize ending requests on the Exchange row. A client retry or a concurrent
                // request must observe and reuse the workflow created by the first transaction.
                val exchange = exchangeRepository.findByIdForUpdate(sessionUUID)
                    ?: throw ExchangeNotFoundException("Exchange not found")
                val runningEnding = workflowInstanceRepository
                    .findActiveForSubjectAndTrigger(sessionUUID, "exchange.ending")
                if (runningEnding != null)
                {
                    logger.info(
                        "Exchange {}: ending workflow {} is already active; holding ENDED write",
                        sessionUUID,
                        runningEnding.id,
                    )
                    throw WorkflowConflictException(
                        "A completion workflow is already active. The exchange will be closed when it completes."
                    )
                }
                val orgId = exchange.ownerOrganizationId
                val triggerResult = workflowEngineService.trigger(
                    TriggerRequest(
                        triggerEvent = "exchange.ending",
                        subjectResourceType = ResourceType.EXCHANGE.name,
                        subjectResourceId = sessionUUID,
                        organizationId = orgId,
                        subjectData = buildMap {
                            exchange.initiator?.id?.let { put("initiatorId", it.toString()) }
                            orgId?.let { put("orgId", it.toString()) }
                        },
                    )
                )
                if (triggerResult != null)
                {
                    logger.info(
                        "Exchange {}: ending workflow {} started; holding ENDED write",
                        sessionUUID, triggerResult.instanceId,
                    )
                    throw WorkflowConflictException(
                        "A completion workflow has been started. The exchange will be closed when it completes."
                    )
                }
            }

            // --- Direct status write (no workflow gate) ------------------------------------
            if (recipientDecision && newStatus == ExchangeStatus.ACCEPTED_STARTED)
            {
                shareService.activatePendingForResource(
                    ResourceType.EXCHANGE,
                    sessionUUID,
                    exchangeRecipientService.pendingTrustedParticipantShareIds(sessionUUID),
                )
            }
            exchangeRepository.updateStatus(sessionUUID, newStatus)

            if (newStatus == ExchangeStatus.ENDED || newStatus == ExchangeStatus.REJECTED)
            {
                exchangeRepository.updateEndDate(sessionUUID, Timestamp.from(Instant.now()))
            }

            if (
                newStatus == ExchangeStatus.ENDED ||
                newStatus == ExchangeStatus.REJECTED ||
                newStatus == ExchangeStatus.RESCINDED
            )
            {
                shareService.revokeAllForResource(ResourceType.EXCHANGE, sessionUUID, resourceLabel = existingExchange.name)
            }

            recordLifecycleTransition(sessionUUID, existingExchange.name, existingExchange.ownerOrganizationId, previousStatus, newStatus)
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
                ?: com.docuhyphen.app.api.service.auth.authz.ShareConstraints.PERMISSIVE
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
            exchangeRepository.findById(sessionUUID)?.let { noAuthExchangeAccessWindowService.clearVerification(it) }
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
                // Note: when status was set via workflow routing (engine path), this block is
                // skipped because we returned early. Contact recording on the workflow path
                // is handled by ExchangeApprovalEventHandler when exchange.activated fires.
                shareRepository.findActiveByResource(ResourceType.EXCHANGE, sessionUUID)
                    .filter {
                        it.principalKind == PrincipalKind.USER &&
                            it.principalId != initiator.id &&
                            it.roleName != ExchangeShareRoleName.OWNER &&
                            it.roleName != ExchangeShareRoleName.PARTICIPANT
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

        logger.info("Exchange ${updatedSession.name} completed")
        return updatedSession
    }

    @Transactional
    fun rescindExchange(exchangeId: String): Exchange
    {
        val exchangeUuid = UUID.fromString(exchangeId)
        val exchange = exchangeRepository.findById(exchangeUuid)
            ?: throw ExchangeNotFoundException("Exchange not found")

        val principal = authorizationContextFactory.currentPrincipal()
            ?: throw ForbiddenException("Authentication required to rescind this exchange")
        if (authorizationService.authorize(
                principal, Action.EXCHANGE_RESCIND, ResourceRef.exchange(exchangeUuid),
                authorizationContextFactory.currentContext()) is AuthDecision.Deny)
        {
            throw ForbiddenException("Only the initiator can rescind this exchange")
        }

        if (exchange.status == ExchangeStatus.RESCINDED)
        {
            return exchange
        }

        if (exchange.status != ExchangeStatus.INITIATED && exchange.status != ExchangeStatus.ACCEPTED_STARTED)
        {
            throw IllegalArgumentException("Only initiated or active exchanges can be rescinded")
        }

        val rescindedAt = Timestamp.from(Instant.now())
        val previousStatus = exchange.status
        exchangeRepository.updateStatus(exchangeUuid, ExchangeStatus.RESCINDED)
        exchangeRepository.updateEndDate(exchangeUuid, rescindedAt)
        exchangeRepository.updateLastActivity(exchangeUuid, rescindedAt)

        // Records a lifecycle event while allowing Exchange rescission to complete if audit capture fails: the
        // durable audit intent is written in this same transaction, so it commits/rolls back
        // atomically with the status transition above. idempotencyKey is deterministic per
        // exchange so a retried rescind call writes at most one outbox row for this occurrence.
        auditRecorder.record(
            AuditEventDraft(
                eventTypeKey = AuditEventType.EXCHANGE_RESCINDED.key,
                outcome = AuditOutcome.SUCCESS,
                actorId = authTokenContext.authToken.appUser?.id,
                targetType = ResourceType.EXCHANGE.name,
                targetId = exchangeUuid.toString(),
                targetLabel = exchange.name,
                owner = exchange.ownerOrganizationId?.let(AuditOwnerScope::Organization) ?: AuditOwnerScope.Platform,
                payload = mapOf(
                    "previousStatus" to previousStatus.name,
                    "newStatus" to ExchangeStatus.RESCINDED.name,
                ),
                idempotencyKey = "exchange.rescind:$exchangeUuid",
                businessTransactionId = exchangeUuid.toString(),
            )
        )

        workflowInstanceRepository.findAllActiveForSubject(ResourceType.EXCHANGE.name, exchangeUuid)
            .forEach { instance ->
                workflowEngineService.cancel(instance.id, "Exchange rescinded")
            }

        shareService.revokeAllForResource(ResourceType.EXCHANGE, exchangeUuid, resourceLabel = exchange.name)

        val updatedExchange = exchangeRepository.findById(exchangeUuid)
            ?: throw ExchangeNotFoundException("Exchange not found")

        sendStatusChangeEmails(updatedExchange, ExchangeStatus.RESCINDED)
        broadcastStatusChange(updatedExchange, ExchangeStatus.RESCINDED)

        logger.info("Exchange {} rescinded by initiator {}", exchangeUuid, authTokenContext.authToken.appUser?.id)

        return updatedExchange
    }

    fun deleteExchange(exchangeId: String?)
    {
        if (exchangeId == null)
        {
            throw IllegalArgumentException("Session ID cannot be null")
        }

        val sessionUUID = UUID.fromString(exchangeId)

        val principal = authorizationContextFactory.currentPrincipal()
            ?: throw ExchangeNotFoundException("Exchange not found")
        if (authorizationService.authorize(
                principal, Action.EXCHANGE_DELETE, ResourceRef.exchange(sessionUUID),
                authorizationContextFactory.currentContext()) is AuthDecision.Deny)
        {
            throw ExchangeNotFoundException("Exchange not found")
        }

        var session = exchangeRepository.findById(sessionUUID)?.apply {

            isDeleted = true
            dateDeleted = Timestamp.from(Instant.now())

        } ?: throw ExchangeNotFoundException("Exchange not found")

        exchangeRepository.update(session)
        session.documents.forEach { document ->
            documentThumbnailService.scheduleDeletion(document.id.toString())
        }

        workflowInstanceRepository.findAllActiveForSubject(ResourceType.EXCHANGE.name, sessionUUID)
            .forEach { instance ->
                workflowEngineService.cancel(instance.id, "Exchange deleted")
            }

        shareService.revokeAllForResource(ResourceType.EXCHANGE, sessionUUID, resourceLabel = session.name)

        recordExchangeDeleted(sessionUUID, session.name, session.ownerOrganizationId)

        logger.info("Exchange ${session.name} deleted")
    }

    /**
     * Captures ACCEPTED_STARTED, REJECTED, and ENDED transitions from
     * [updateExchange] onto the ledger, mirroring the [rescindExchange] reference pattern but
     * using catch-and-log so an audit
     * failure here never blocks the underlying status transition. Statuses without a mapped
     * event (e.g. INITIATED, RESCINDED - the latter has its own dedicated capture in
     * [rescindExchange]) are silently skipped.
     */
    private fun recordLifecycleTransition(
        exchangeId: UUID,
        exchangeName: String?,
        organizationId: UUID?,
        previousStatus: ExchangeStatus,
        newStatus: ExchangeStatus,
    )
    {
        val eventType = when (newStatus)
        {
            ExchangeStatus.ACCEPTED_STARTED -> AuditEventType.EXCHANGE_ACCEPTED
            ExchangeStatus.REJECTED -> AuditEventType.EXCHANGE_REJECTED
            ExchangeStatus.ENDED -> AuditEventType.EXCHANGE_ENDED
            else -> return
        }
        try
        {
            auditRecorder.record(
                AuditEventDraft(
                    eventTypeKey = eventType.key,
                    outcome = AuditOutcome.SUCCESS,
                    actorId = authTokenContext.authToken.appUser?.id,
                    actorKind = AuditActorKind.HUMAN,
                    targetType = ResourceType.EXCHANGE.name,
                    targetId = exchangeId.toString(),
                    targetLabel = exchangeName,
                    owner = organizationId?.let(AuditOwnerScope::Organization) ?: AuditOwnerScope.Platform,
                    payload = mapOf(
                        "previousStatus" to previousStatus.name,
                        "newStatus" to newStatus.name,
                    ),
                )
            )
        }
        catch (e: AuditDraftInvalidException)
        {
            logger.warn("ExchangeUpdateService: AuditRecorder rejected {} draft: {}", eventType.key, e.message)
        }
        catch (e: AuditCaptureFailedException)
        {
            logger.error("ExchangeUpdateService: AuditRecorder capture failed for {}: {}", eventType.key, e.message, e)
        }
    }

    /** Captures Exchange deletion onto the ledger. */
    private fun recordExchangeDeleted(exchangeId: UUID, exchangeName: String?, organizationId: UUID?)
    {
        try
        {
            auditRecorder.record(
                AuditEventDraft(
                    eventTypeKey = AuditEventType.EXCHANGE_DELETED.key,
                    outcome = AuditOutcome.SUCCESS,
                    actorId = authTokenContext.authToken.appUser?.id,
                    actorKind = AuditActorKind.HUMAN,
                    targetType = ResourceType.EXCHANGE.name,
                    targetId = exchangeId.toString(),
                    targetLabel = exchangeName,
                    owner = organizationId?.let(AuditOwnerScope::Organization) ?: AuditOwnerScope.Platform,
                )
            )
        }
        catch (e: AuditDraftInvalidException)
        {
            logger.warn("ExchangeUpdateService: AuditRecorder rejected EXCHANGE_DELETED draft: {}", e.message)
        }
        catch (e: AuditCaptureFailedException)
        {
            logger.error("ExchangeUpdateService: AuditRecorder capture failed for EXCHANGE_DELETED: {}", e.message, e)
        }
    }

    @Transactional
    fun updateNoAuthExchange(
        exchangeId: String,
        sessionStatus: ExchangeStatus?,
        otp: String?,
        rejectReason: String?,
        noAuthAccessToken: String?,
    ): NoAuthExchangeBasicDto
    {
        val sessionUUID = UUID.fromString(exchangeId)

        val session = exchangeRepository.findById(sessionUUID)
            ?: throw ExchangeNotFoundException("Exchange not found")
        noAuthExchangeAccessTokenService.requireValid(session, noAuthAccessToken)

        if (session.requireRecipientSignIn)
        {
            logger.error("Attempted to access a exchange that requires recipient sign-in")
            throw ForbiddenException("Exchange not found")
        }

        val requestedStatus = sessionStatus
            ?: throw NoAuthOtpException(
                message = "Exchange status is required",
                reasonCode = "INVALID_STATUS_TRANSITION",
            )

        // Idempotent status updates avoid breaking refresh/retry UX for no-auth recipients.
        if (requestedStatus == session.status)
        {
            return toEnrichedNoAuthDto(session, sessionUUID)
        }

        if (session.status != ExchangeStatus.ACCEPTED_STARTED && session.status != ExchangeStatus.INITIATED)
        {
            logger.error("Attempted to update a exchange with an invalid status")
            throw NoAuthOtpException(
                message = "Exchange is not in a state that can be updated",
                reasonCode = "INVALID_STATUS_TRANSITION",
            )
        }

        if (
            session.status == ExchangeStatus.ENDED ||
            session.status == ExchangeStatus.REJECTED ||
            session.status == ExchangeStatus.RESCINDED
        )
        {
            logger.error("Attempted to update a exchange that has ended or rejected: $sessionStatus")
            throw NoAuthOtpException(
                message = "Exchange has already ended",
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
                message = "Exchange cannot transition from ${session.status} to $requestedStatus",
                reasonCode = "INVALID_STATUS_TRANSITION",
            )
        }

        verifyRecipientOtp(session, otp)
        exchangeRecipientService.recordExternalEmailPrimaryDecision(
            exchange = session,
            accepted = requestedStatus == ExchangeStatus.ACCEPTED_STARTED,
        )

        // If an acceptance workflow is running, route the decision through the engine.
        // The no-auth recipient's user ID (from their Share row) is used as the principal.
        if (requestedStatus == ExchangeStatus.ACCEPTED_STARTED || requestedStatus == ExchangeStatus.REJECTED)
        {
            val runningAcceptance = workflowInstanceRepository
                .findActiveForSubjectAndTrigger(sessionUUID, "exchange.acceptance_pending")
            if (runningAcceptance != null)
            {
                val recipientUserId = shareService.primaryRecipientUserId(sessionUUID)
                val currentStep = workflowStepRepository
                    .findCurrent(runningAcceptance.id, runningAcceptance.currentStepIndex)
                if (recipientUserId != null && currentStep != null)
                {
                    val decision = if (requestedStatus == ExchangeStatus.ACCEPTED_STARTED) Decision.APPROVE else Decision.REJECT
                    workflowEngineService.recordDecision(
                        stepInstanceId = currentStep.id,
                        decider = PrincipalRef(PrincipalKind.USER, recipientUserId),
                        decision = decision,
                        reason = rejectReason,
                    )
                    // Write status directly � EVENT_EXCHANGE_ACTIVATED does not advance status
                    // when requireRecipientAcceptance=true, so this path owns the transition.
                    exchangeRepository.updateStatus(sessionUUID, requestedStatus)
                    if (requestedStatus == ExchangeStatus.REJECTED)
                    {
                        exchangeRepository.updateEndDate(sessionUUID, Timestamp.from(Instant.now()))
                        shareService.revokeAllForResource(ResourceType.EXCHANGE, sessionUUID, resourceLabel = session.name)
                    }
                    if (requestedStatus == ExchangeStatus.ACCEPTED_STARTED)
                    {
                        noAuthExchangeAccessWindowService.markVerified(session)
                    }
                    if (requestedStatus == ExchangeStatus.REJECTED && rejectReason != null)
                    {
                        exchangeRepository.updateRejectionReason(sessionUUID, rejectReason)
                    }
                    exchangeRepository.updateLastActivity(sessionUUID, Timestamp.from(Instant.now()))
                    val refreshedSession = exchangeRepository.findById(sessionUUID)!!
                    sendStatusChangeEmails(refreshedSession, requestedStatus, rejectReason)
                    broadcastStatusChange(refreshedSession, requestedStatus)
                    logger.info(
                        "No-auth exchange {}: routed {} through acceptance workflow",
                        sessionUUID, requestedStatus,
                    )
                    return toEnrichedNoAuthDto(refreshedSession, sessionUUID)
                }
            }
        }

        if (requestedStatus == ExchangeStatus.ACCEPTED_STARTED)
        {
            noAuthExchangeAccessWindowService.markVerified(session)
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

        logger.info("Exchange ${session.name} updated")

        return toEnrichedNoAuthDto(refreshedSession, sessionUUID)
    }

    @Transactional
    fun verifyNoAuthAccessCode(
        exchangeId: String,
        otp: String?,
        noAuthAccessToken: String?,
    ): NoAuthExchangeBasicDto
    {
        val sessionUUID = UUID.fromString(exchangeId)
        val session = exchangeRepository.findById(sessionUUID)
            ?: throw ExchangeNotFoundException("Exchange not found")
        noAuthExchangeAccessTokenService.requireValid(session, noAuthAccessToken)

        if (session.requireRecipientSignIn)
        {
            throw ForbiddenException("Exchange not found")
        }

        if (
            session.status == ExchangeStatus.ENDED ||
            session.status == ExchangeStatus.REJECTED ||
            session.status == ExchangeStatus.RESCINDED
        )
        {
            throw IllegalArgumentException("Exchange has already ended")
        }

        verifyRecipientOtp(session, otp)
        noAuthExchangeAccessWindowService.markVerified(session)
        exchangeRepository.updateLastActivity(sessionUUID, Timestamp.from(Instant.now()))

        val refreshed = exchangeRepository.findById(sessionUUID)
            ?: throw ExchangeNotFoundException("Exchange not found")
        return toEnrichedNoAuthDto(refreshed, sessionUUID)
    }

    @Transactional
    fun issueRecipientOtp(exchangeId: String, noAuthAccessToken: String? = null): Exchange
    {
        val sessionUUID = UUID.fromString(exchangeId)

        val session = exchangeRepository.findById(sessionUUID)
            ?: throw ExchangeNotFoundException("Exchange not found")

        if (noAuthAccessToken != null)
        {
            noAuthExchangeAccessTokenService.requireValid(session, noAuthAccessToken)
        }
        else
        {
            val principal = authorizationContextFactory.currentPrincipal()
                ?: throw ForbiddenException("Exchange not found")
            if (authorizationService.authorize(
                    principal,
                    Action.EXCHANGE_MANAGE_ACCESS,
                    ResourceRef.exchange(sessionUUID),
                    authorizationContextFactory.currentContext(),
                ) is AuthDecision.Deny
            )
            {
                throw ForbiddenException("Exchange not found")
            }
        }

        if (session.requireRecipientSignIn)
        {
            // OTP is only relevant for the no-auth recipient flow.
            if (noAuthAccessToken != null)
            {
                throw ForbiddenException("Exchange not found")
            }
            throw IllegalArgumentException("Save changes before sending an access code.")
        }

        if (session.status == ExchangeStatus.ACCEPTED_STARTED && noAuthAccessToken != null)
        {
            // Recipient self-service OTP re-issuance is not permitted after acceptance.
            // Any re-verification is handled by the initiator from Manage Access.
            throw ForbiddenException("Exchange not found")
        }

        if (
            session.status == ExchangeStatus.ENDED ||
            session.status == ExchangeStatus.REJECTED ||
            session.status == ExchangeStatus.RESCINDED
        )
        {
            throw IllegalArgumentException("Exchange has already ended")
        }

        throwIfOtpLocked(sessionUUID)

        // If a valid OTP already exists for a recipient self-service request, silently succeed.
        // The frontend calls this on page load; returning 204 lets the user enter the code they already received.
        val existingExpiry = session.recipientOtpExpiry?.toInstant()
        if (noAuthAccessToken != null && existingExpiry != null && existingExpiry.isAfter(Instant.now()))
        {
            logger.info("noAuthOtp.issue.skipped exchangeId={} reason=EXISTING_OTP_VALID", exchangeId)
            return session
        }
        val recipientEmail = resolveRecipientEmail(session.id)
            ?: throw IllegalArgumentException("Exchange has no recipient email")

        val otp = otpService.generateEmailOtp()
        val accessToken = noAuthExchangeAccessTokenService.issue(session)
        session.recipientOtpHash = otpService.hashOtp(otp)
        session.recipientOtpExpiry = Timestamp.from(Instant.now().plusSeconds(OTP_VALIDITY_SECONDS))
        exchangeRepository.update(session)
        otpAttemptStates.remove(sessionUUID)
        logger.info("noAuthOtp.issue.success exchangeId={} recipientEmail={}", exchangeId, recipientEmail)

        val renderedOtpEmail = emailTemplateService.renderNoAuthExchangeOtpEmail(
            exchangeId = sessionUUID.toString(),
            name = session.name.orEmpty(),
            otp = otp,
            accessToken = accessToken,
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
                ?: throw IllegalArgumentException("Exchange id is required")

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
            lifecycleNotificationService.publish(session, newStatus)
        }.onFailure { e ->
            logger.warn("Failed to broadcast status change for session={} status={}", session.id, newStatus, e)
        }
    }


    /** Email of the exchange's primary recipient, resolved from its recipient Share. */
    private fun resolveRecipientEmail(exchangeId: UUID, includeInactive: Boolean = false): String? =
        resolvePrimaryRecipientUser(exchangeId, includeInactive)?.email
            ?: resolvePrimaryRecipientParticipantEmail(exchangeId, includeInactive)

    private fun resolvePrimaryRecipientUser(exchangeId: UUID, includeInactive: Boolean = false) =
        (if (includeInactive) shareService.primaryRecipientUserIdForDisplay(exchangeId) else shareService.primaryRecipientUserId(exchangeId))
            ?.let { appUserService.getById(it) }

    private fun resolvePrimaryRecipientGroupLabel(exchangeId: UUID, includeInactive: Boolean = false): String? =
        (if (includeInactive) shareService.primaryRecipientGroupIdForDisplay(exchangeId) else shareService.primaryRecipientGroupId(exchangeId))
            ?.let { groupId -> principalGroupRepository.findById(groupId)?.name }
            ?.takeIf { it.isNotBlank() }
            ?.let { "Group: $it" }

    private fun resolvePrimaryRecipientParticipantLabel(exchangeId: UUID, includeInactive: Boolean = false): String? =
        resolvePrimaryRecipientParticipant(exchangeId, includeInactive)
            ?.let { participant ->
                participant.displayName?.trim()?.takeIf { it.isNotBlank() } ?: participant.email
            }

    private fun resolvePrimaryRecipientParticipantEmail(exchangeId: UUID, includeInactive: Boolean = false): String? =
        resolvePrimaryRecipientParticipant(exchangeId, includeInactive)?.email

    private fun resolvePrimaryRecipientParticipant(exchangeId: UUID, includeInactive: Boolean = false) =
        (if (includeInactive) shareRepository.findAllByResource(ResourceType.EXCHANGE, exchangeId)
        else shareRepository.findActiveByResource(ResourceType.EXCHANGE, exchangeId))
            .firstOrNull {
                it.principalKind == PrincipalKind.PARTICIPANT &&
                    it.roleName != ExchangeShareRoleName.OWNER &&
                    it.source == ShareSource.DIRECT
            }
            ?.principalId
            ?.let { externalParticipantRepository.findById(it) }

    private fun resolveUserLabel(appUserId: UUID?): String? =
        appUserId
            ?.let { appUserService.getById(it) }
            ?.let { appUser ->
                listOfNotNull(
                    appUser.person?.firstName?.trim()?.takeIf { it.isNotBlank() },
                    appUser.person?.lastName?.trim()?.takeIf { it.isNotBlank() },
                ).joinToString(" ").ifBlank { appUser.email }
            }

    private fun resolveRecipientLabel(exchangeId: UUID, includeInactive: Boolean = false): String =
        resolvePrimaryRecipientUser(exchangeId, includeInactive)
            ?.let { appUser ->
                listOfNotNull(
                    appUser.person?.firstName?.trim()?.takeIf { it.isNotBlank() },
                    appUser.person?.lastName?.trim()?.takeIf { it.isNotBlank() },
                ).joinToString(" ").ifBlank { appUser.email }
            }
            ?: resolvePrimaryRecipientParticipantLabel(exchangeId, includeInactive)
            ?: resolvePrimaryRecipientGroupLabel(exchangeId, includeInactive)
            ?: resolveRecipientEmail(exchangeId, includeInactive)
            ?: "Recipient"

    private fun toEnrichedNoAuthDto(exchange: Exchange, exchangeId: UUID): NoAuthExchangeBasicDto
    {
        val dto = BasicEntityToDtoTransformer.toNoAuthDto(exchange)
            ?: throw ExchangeNotFoundException("Exchange not found")
        val constraintsJson = shareService.recipientConstraintsJson(exchangeId)
        val constraints = ShareConstraints.parse(constraintsJson)
        val downloadAllowed = constraints?.canDownload != false &&
            (constraintsJson?.contains("\"allow_document_download\":true") == true ||
                constraints?.canDownload == true)
        dto.allowDocumentDownload = downloadAllowed
        dto.accessVerificationRequired = exchange.status == ExchangeStatus.ACCEPTED_STARTED &&
            !noAuthExchangeAccessWindowService.isActive(exchange)
        return dto
    }

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

            ExchangeStatus.RESCINDED -> {
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
            EmailAudience.RECIPIENT ->
                resolveRecipientEmail(
                    session.id,
                    includeInactive = status == ExchangeStatus.REJECTED || status == ExchangeStatus.RESCINDED,
                )
        } ?: return

        val includeInactiveRecipient =
            status == ExchangeStatus.REJECTED ||
                status == ExchangeStatus.ENDED ||
                status == ExchangeStatus.RESCINDED

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
            initiatorLabel = resolveUserLabel(session.initiator?.id) ?: "Unknown",
            recipientLabel = resolveRecipientLabel(session.id, includeInactive = includeInactiveRecipient),
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
