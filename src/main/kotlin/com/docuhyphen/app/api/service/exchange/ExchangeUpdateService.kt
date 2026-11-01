package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.exception.ExchangeNotFoundException
import com.docuhyphen.app.api.exception.NoAuthOtpException
import com.docuhyphen.app.api.exception.WorkflowConflictException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.DetailedEntityToDtoTransformer
import com.docuhyphen.app.api.model.NoAuthExchangeDtoMapper
import com.docuhyphen.app.api.model.audit.AuditEventDraft
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.model.auth.authz.ResourceRef
import com.docuhyphen.app.api.model.auth.authz.ShareConstraints
import com.docuhyphen.app.api.model.dto.ExchangeDetailedDto
import com.docuhyphen.app.api.model.dto.NoAuthExchangeBasicDto
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.exchange.ExchangeLifecycleActor
import com.docuhyphen.app.api.model.exchange.ExchangeOtpAttemptState
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.workflow.TriggerRequest
import com.docuhyphen.app.api.service.realtime.RealtimeEventService
import com.docuhyphen.app.api.model.realtime.RealtimeMessage
import com.docuhyphen.app.api.model.realtime.RealtimeMessageType
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.repository.exchange.ExternalParticipantRepository
import com.docuhyphen.app.api.repository.exchange.ShareRepository
import com.docuhyphen.app.api.repository.organization.PrincipalGroupRepository
import com.docuhyphen.app.api.repository.workflow.WorkflowInstanceRepository
import com.docuhyphen.app.api.repository.workflow.WorkflowStepInstanceRepository
import com.docuhyphen.app.api.resource.model.UpdateExchangeRequest
import com.docuhyphen.app.api.service.audit.*
import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import com.docuhyphen.app.api.service.auth.authz.*
import com.docuhyphen.app.api.service.communication.EmailService
import com.docuhyphen.app.api.service.communication.EmailTemplateService
import com.docuhyphen.app.api.service.communication.OtpService
import com.docuhyphen.app.api.service.contactdetails.UserContactService
import com.docuhyphen.app.api.service.informationrequest.parent.InformationRequestExchangeCompletionService
import com.docuhyphen.app.api.service.informationrequest.parent.InformationRequestParentLifecycleService
import com.docuhyphen.app.api.service.user.AppUserService
import com.docuhyphen.app.api.service.workflow.Decision
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
import com.docuhyphen.app.api.model.auth.authz.Decision as AuthDecision

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
    private val auditOwnerScopeResolver: AuditOwnerScopeResolver,
    private val noAuthExchangeAccessTokenService: NoAuthExchangeAccessTokenService,
    private val noAuthExchangeAccessWindowService: NoAuthExchangeAccessWindowService,
    private val lifecycleNotificationService: ExchangeLifecycleNotificationService,
    private val documentThumbnailService: DocumentThumbnailService,
    private val requestParentLifecycle: InformationRequestParentLifecycleService,
    private val requestCompletion: InformationRequestExchangeCompletionService,
    private val endingIntents: ExchangeEndingIntentService,
    private val lifecycleAudit: ExchangeLifecycleAudit = ExchangeLifecycleAudit(auditRecorder, auditOwnerScopeResolver),
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

    private val otpAttemptStates: MutableMap<UUID, ExchangeOtpAttemptState> = ConcurrentHashMap()

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
        if (request?.status == ExchangeStatus.INITIATED) {
            throw IllegalArgumentException("An Exchange cannot be returned to INITIATED")
        }
        if (request?.rejectionReason != null) {
            throw IllegalArgumentException("A rejection reason is given with the acceptance decision")
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
                    is AuthDecision.Deny
        )
        {
            if (recipientDecision)
            {
                throw ForbiddenException("Only the primary recipient may decide this Exchange")
            }
            throw ExchangeNotFoundException("Exchange not found")
        }

        val hasOwnerMutation = request?.name != null || request?.description != null ||
                request?.requireRecipientSignIn != null || request?.noAuthAccessValidityDays != null ||
                request?.allowDocumentAddition != null || request?.allowDocumentDeletion != null ||
                request?.allowDocumentDownload != null || request?.allowDocumentUpdate != null ||
                request?.allowDocumentUpload != null || request?.allowedDownloadFormats != null ||
                request?.status == ExchangeStatus.ENDED
        if (hasOwnerMutation)
        {
            if (authorizationService.authorize(
                    principal,
                    Action.EXCHANGE_EDIT,
                    ResourceRef.exchange(sessionUUID),
                    authCtx
                )
                        is AuthDecision.Deny
            )
            {
                throw ForbiddenException("Not authorized to edit this Exchange")
            }
        }

        val existingExchange = exchangeRepository.findByIdForUpdate(sessionUUID)
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
                throw IllegalArgumentException("Use the rescind action to cancel an outgoing Exchange")
            }
            ExchangeStatusTransitions.requirePermitted(previousStatus, newStatus)

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

            if (newStatus == ExchangeStatus.ACCEPTED_STARTED || newStatus == ExchangeStatus.REJECTED)
            {
                val runningAcceptance = workflowInstanceRepository
                    .findActiveForSubjectAndTrigger(sessionUUID, "exchange.acceptance_pending")
                if (runningAcceptance != null)
                {
                    val currentStep = workflowStepRepository
                        .findCurrent(runningAcceptance.id, runningAcceptance.currentStepIndex)
                        ?: throw IllegalStateException("Acceptance workflow has no current step for Exchange $sessionUUID")
                    val currentUser = authTokenContext.authToken.appUser
                        ?: throw IllegalStateException("No authenticated user in context")
                    val decision =
                        if (newStatus == ExchangeStatus.ACCEPTED_STARTED) Decision.APPROVE else Decision.REJECT
                    workflowEngineService.recordDecision(
                        stepInstanceId = currentStep.id,
                        decider = PrincipalRef(PrincipalKind.USER, currentUser.id),
                        decision = decision,
                        reason = request.rejectionReason,
                    )
                    exchangeRepository.updateStatus(sessionUUID, newStatus)
                    requestParentLifecycle.apply(
                        sessionUUID,
                        newStatus,
                        false,
                        requestActor(principal, authCtx),
                        previousStatus
                    )
                    if (newStatus == ExchangeStatus.REJECTED)
                    {
                        exchangeRepository.updateEndDate(sessionUUID, Timestamp.from(Instant.now()))
                        shareService.revokeAllForResource(
                            ResourceType.EXCHANGE,
                            sessionUUID,
                            resourceLabel = existingExchange.name
                        )
                    }
                    request.rejectionReason?.let { exchangeRepository.updateRejectionReason(sessionUUID, it) }
                    exchangeRepository.updateLastActivity(sessionUUID, Timestamp.from(Instant.now()))
                    val updatedSession = exchangeRepository.findById(sessionUUID)!!
                    recordLifecycleTransition(sessionUUID, existingExchange.name, previousStatus, newStatus)
                    sendStatusChangeEmails(updatedSession, newStatus, request.rejectionReason)
                    broadcastStatusChange(updatedSession, newStatus)
                    logger.info(
                        "Exchange {}: routed {} decision through acceptance workflow",
                        sessionUUID, newStatus,
                    )
                    return updatedSession
                }
            }

            if (newStatus == ExchangeStatus.ENDED)
            {
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
                        "A completion workflow is already active. The Exchange will be closed when it completes.",
                        WorkflowConflictException.EXCHANGE_ENDING_HELD_BY_WORKFLOW,
                    )
                }
                val cancelRemaining = request.cancelRemainingInformationRequests == true
                val endingAccess = RequestAccessContext(principal, authCtx)
                requestCompletion.checkEnding(exchange, cancelRemaining, endingAccess)
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
                    endingIntents.record(sessionUUID, triggerResult.instanceId, cancelRemaining, principal)
                    logger.info(
                        "Exchange {}: ending workflow {} started; holding ENDED write",
                        sessionUUID, triggerResult.instanceId,
                    )
                    throw WorkflowConflictException(
                        "A completion workflow has been started. The Exchange will be closed when it completes.",
                        WorkflowConflictException.EXCHANGE_ENDING_HELD_BY_WORKFLOW,
                    )
                }
                requestCompletion.prepareEnding(
                    exchange,
                    requestActor(principal, authCtx),
                    cancelRemaining,
                    endingAccess
                )
            }

            if (recipientDecision && newStatus == ExchangeStatus.ACCEPTED_STARTED)
            {
                shareService.activatePendingForResource(
                    ResourceType.EXCHANGE,
                    sessionUUID,
                    exchangeRecipientService.pendingTrustedParticipantShareIds(sessionUUID),
                )
            }
            exchangeRepository.updateStatus(sessionUUID, newStatus)
            requestParentLifecycle.apply(
                sessionUUID,
                newStatus,
                false,
                requestActor(principal, authCtx),
                previousStatus
            )

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
                shareService.revokeAllForResource(
                    ResourceType.EXCHANGE,
                    sessionUUID,
                    resourceLabel = existingExchange.name
                )
            }

            recordLifecycleTransition(sessionUUID, existingExchange.name, previousStatus, newStatus)
        }

        request?.rejectionReason?.let {
            exchangeRepository.updateRejectionReason(sessionUUID, it)
        }

        request?.requireRecipientSignIn?.let {
            exchangeRepository.updateRequireRecipientSignIn(sessionUUID, it)
        }

        if (request?.allowDocumentAddition != null ||
            request?.allowDocumentDeletion != null ||
            request?.allowDocumentDownload != null ||
            request?.allowDocumentUpdate != null ||
            request?.allowDocumentUpload != null ||
            request?.allowedDownloadFormats != null
        )
        {
            val currentJson = shareService.recipientConstraintsJson(sessionUUID) ?: "{}"
            val currentConstraints = ShareConstraints.parse(currentJson)
                ?: ShareConstraints.PERMISSIVE
            val addition = request.allowDocumentAddition
                ?: currentJson.contains("\"allow_document_addition\":true")
            val deletion = request.allowDocumentDeletion
                ?: currentJson.contains("\"allow_document_deletion\":true")
            val download = request.allowDocumentDownload
                ?: currentJson.contains("\"can_download\":true")
            val update = request.allowDocumentUpdate
                ?: currentJson.contains("\"allow_document_update\":true")
            val upload = request.allowDocumentUpload
                ?: currentJson.contains("\"allow_document_upload\":true")
            val requireSignIn = request.requireRecipientSignIn
                ?: currentJson.contains("\"require_recipient_sign_in\":true")

            val parts = mutableListOf(
                """"can_download":$download""",
                """"allow_document_addition":$addition""",
                """"allow_document_deletion":$deletion""",
                """"allow_document_update":$update""",
                """"allow_document_upload":$upload""",
                """"require_recipient_sign_in":$requireSignIn""",
            )

            val formats =
                if (request.allowedDownloadFormats != null) request.allowedDownloadFormats else currentConstraints.allowedDownloadFormats
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

        exchangeRepository.refresh(existingExchange)

        if (request?.requireRecipientSignIn == true)
        {
            noAuthExchangeAccessWindowService.clearVerification(existingExchange)
            requestParentLifecycle.enforceRecipientSignIn(sessionUUID, requestActor(principal, authCtx))
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
                shareRepository.findActiveByResource(ResourceType.EXCHANGE, sessionUUID)
                    .filter {
                        it.principalKind == PrincipalKind.USER &&
                                it.principalId != initiator.id &&
                                it.roleName != ExchangeShareRoleName.OWNER.name &&
                                it.roleName != ExchangeShareRoleName.PARTICIPANT.name
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
        val principal = authorizationContextFactory.currentPrincipal()
            ?: throw ForbiddenException("Authentication required to rescind this Exchange")
        val authorization = authorizationContextFactory.currentContext()
        if (authorizationService.authorize(
                principal, Action.EXCHANGE_RESCIND, ResourceRef.exchange(exchangeUuid), authorization,
            ) is AuthDecision.Deny
        )
        {
            if (authorizationService.authorize(
                    principal, Action.EXCHANGE_VIEW, ResourceRef.exchange(exchangeUuid), authorization,
                ) is AuthDecision.Allow
            ) {
                throw ForbiddenException("Only the initiator can rescind this Exchange")
            }
            throw ExchangeNotFoundException("Exchange not found")
        }

        val exchange = exchangeRepository.findByIdForUpdate(exchangeUuid)
            ?: throw ExchangeNotFoundException("Exchange not found")

        if (exchange.status == ExchangeStatus.RESCINDED)
        {
            return exchange
        }

        if (exchange.status != ExchangeStatus.INITIATED && exchange.status != ExchangeStatus.ACCEPTED_STARTED)
        {
            throw IllegalArgumentException("Only initiated or active Exchanges can be rescinded")
        }

        val rescindedAt = Timestamp.from(Instant.now())
        val previousStatus = exchange.status
        exchangeRepository.updateStatus(exchangeUuid, ExchangeStatus.RESCINDED)
        requestParentLifecycle.apply(
            exchangeUuid,
            ExchangeStatus.RESCINDED,
            false,
            requestActor(principal, authorizationContextFactory.currentContext()),
        )
        exchangeRepository.updateEndDate(exchangeUuid, rescindedAt)
        exchangeRepository.updateLastActivity(exchangeUuid, rescindedAt)

        auditRecorder.record(
            AuditEventDraft(
                eventTypeKey = AuditEventType.EXCHANGE_RESCINDED.key,
                outcome = AuditOutcome.SUCCESS,
                actorId = authTokenContext.authToken.appUser?.id,
                targetType = ResourceType.EXCHANGE.name,
                targetId = exchangeUuid.toString(),
                targetLabel = exchange.name,
                owner = auditOwnerScopeResolver.resolve(ResourceType.EXCHANGE, exchangeUuid),
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

    @Transactional
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
                authorizationContextFactory.currentContext()
            ) is AuthDecision.Deny
        )
        {
            throw ExchangeNotFoundException("Exchange not found")
        }

        var session = exchangeRepository.findByIdForUpdate(sessionUUID)?.apply {

            isDeleted = true
            dateDeleted = Timestamp.from(Instant.now())

        } ?: throw ExchangeNotFoundException("Exchange not found")

        exchangeRepository.update(session)
        requestParentLifecycle.apply(
            sessionUUID,
            session.status,
            true,
            requestActor(principal, authorizationContextFactory.currentContext()),
        )
        session.documents.forEach { document ->
            documentThumbnailService.scheduleDeletion(document.id.toString())
        }

        workflowInstanceRepository.findAllActiveForSubject(ResourceType.EXCHANGE.name, sessionUUID)
            .forEach { instance ->
                workflowEngineService.cancel(instance.id, "Exchange deleted")
            }

        shareService.revokeAllForResource(ResourceType.EXCHANGE, sessionUUID, resourceLabel = session.name)

        recordExchangeDeleted(sessionUUID, session.name)

        logger.info("Exchange ${session.name} deleted")
    }

    private fun requestActor(principal: PrincipalRef, authorization: AuthorizationContext) =
        InformationRequestAuditActor.of(RequestAccessContext(principal, authorization))

    private fun recordLifecycleTransition(
        exchangeId: UUID,
        exchangeName: String?,
        previousStatus: ExchangeStatus,
        newStatus: ExchangeStatus,
        actor: ExchangeLifecycleActor = ExchangeLifecycleActor.person(authTokenContext.authToken.appUser?.id),
    ) = lifecycleAudit.recordTransition(exchangeId, exchangeName, previousStatus, newStatus, actor)

    private fun recordExchangeDeleted(exchangeId: UUID, exchangeName: String?)
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
                    owner = auditOwnerScopeResolver.resolve(ResourceType.EXCHANGE, exchangeId),
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

        noAuthExchangeAccessTokenService.requireValid(
            exchangeRepository.findById(sessionUUID) ?: throw ExchangeNotFoundException("Exchange not found"),
            noAuthAccessToken,
        )
        val session = exchangeRepository.findByIdForUpdate(sessionUUID)
            ?: throw ExchangeNotFoundException("Exchange not found")
        noAuthExchangeAccessTokenService.requireValid(session, noAuthAccessToken)

        if (session.requireRecipientSignIn)
        {
            logger.error("Attempted to access an Exchange that requires recipient sign-in")
            throw ForbiddenException("Exchange not found")
        }

        val requestedStatus = sessionStatus
            ?: throw NoAuthOtpException(
                message = "Exchange status is required",
                reasonCode = "INVALID_STATUS_TRANSITION",
            )

        if (requestedStatus == session.status)
        {
            return noAuthExchangeView(session, sessionUUID)
        }

        if (session.status != ExchangeStatus.ACCEPTED_STARTED && session.status != ExchangeStatus.INITIATED)
        {
            logger.error("Attempted to update an Exchange with an invalid status")
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
            logger.error("Attempted to update an Exchange that has ended or rejected: $sessionStatus")
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
        val previousStatus = session.status
        val decidingRecipientId = shareService.primaryRecipientUserId(sessionUUID)
        val decisionActor = ExchangeLifecycleActor.accessLinkRecipient(decidingRecipientId)
        val recipientActor = InformationRequestAuditActor.beforeSession(
            decidingRecipientId?.let(PrincipalRef::user) ?: PrincipalRef(PrincipalKind.SERVICE_ACCOUNT, UUID(0, 0)),
        )

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
                    val decision =
                        if (requestedStatus == ExchangeStatus.ACCEPTED_STARTED) Decision.APPROVE else Decision.REJECT
                    workflowEngineService.recordDecision(
                        stepInstanceId = currentStep.id,
                        decider = PrincipalRef(PrincipalKind.USER, recipientUserId),
                        decision = decision,
                        reason = rejectReason,
                    )
                    exchangeRepository.updateStatus(sessionUUID, requestedStatus)
                    requestParentLifecycle.apply(
                        sessionUUID,
                        requestedStatus,
                        false,
                        recipientActor,
                        previousStatus,
                    )
                    if (requestedStatus == ExchangeStatus.REJECTED)
                    {
                        exchangeRepository.updateEndDate(sessionUUID, Timestamp.from(Instant.now()))
                        shareService.revokeAllForResource(
                            ResourceType.EXCHANGE,
                            sessionUUID,
                            resourceLabel = session.name
                        )
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
                    recordLifecycleTransition(sessionUUID, session.name, previousStatus, requestedStatus, decisionActor)
                    val refreshedSession = exchangeRepository.findById(sessionUUID)!!
                    sendStatusChangeEmails(refreshedSession, requestedStatus, rejectReason)
                    broadcastStatusChange(refreshedSession, requestedStatus)
                    logger.info(
                        "No-auth Exchange {}: routed {} through acceptance workflow",
                        sessionUUID, requestedStatus,
                    )
                    return noAuthExchangeView(refreshedSession, sessionUUID)
                }
            }
        }

        if (requestedStatus == ExchangeStatus.ACCEPTED_STARTED)
        {
            noAuthExchangeAccessWindowService.markVerified(session)
        }

        sessionStatus.let {
            exchangeRepository.updateStatus(sessionUUID, it)
            requestParentLifecycle.apply(
                sessionUUID,
                it,
                false,
                recipientActor,
                previousStatus,
            )

            if (it == ExchangeStatus.REJECTED)
            {
                rejectReason?.let {
                    exchangeRepository.updateRejectionReason(sessionUUID, it)
                }

                exchangeRepository.updateEndDate(sessionUUID, Timestamp.from(Instant.now()))
            }
            recordLifecycleTransition(sessionUUID, session.name, previousStatus, it, decisionActor)
        }

        exchangeRepository.updateLastActivity(sessionUUID, Timestamp.from(Instant.now()))

        val refreshedSession = exchangeRepository.findById(sessionUUID)!!

        sessionStatus.let {
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

        return noAuthExchangeView(refreshedSession, sessionUUID)
    }

    @Transactional
    fun verifyNoAuthAccessCode(
        exchangeId: String,
        otp: String?,
        noAuthAccessToken: String?,
    ): NoAuthExchangeBasicDto
    {
        val sessionUUID = UUID.fromString(exchangeId)
        val session = exchangeRepository.findByIdForUpdate(sessionUUID)
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
        return noAuthExchangeView(refreshed, sessionUUID)
    }

    @Transactional
    fun issueRecipientOtp(exchangeId: String, noAuthAccessToken: String? = null): Exchange
    {
        val sessionUUID = UUID.fromString(exchangeId)

        val session = exchangeRepository.findByIdForUpdate(sessionUUID)
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
            if (noAuthAccessToken != null)
            {
                throw ForbiddenException("Exchange not found")
            }
            throw IllegalArgumentException("Save changes before sending an access code.")
        }

        if (session.status == ExchangeStatus.ACCEPTED_STARTED && noAuthAccessToken != null)
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

        throwIfOtpLocked(sessionUUID)

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
            logger.info(
                "noAuthOtp.verify.rejected exchangeId={} reason=OTP_LOCKED retryAfterSeconds={}",
                exchangeId,
                secondsLeft
            )
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
        val state = otpAttemptStates.computeIfAbsent(exchangeId) { ExchangeOtpAttemptState() }
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

            val message = RealtimeMessage(
                type = RealtimeMessageType.EXCHANGE_STATUS_CHANGED,
                exchangeId = exchangeId.toString(),
                status = newStatus.name,
            )

            realtimeEventService.broadcastToExchange(
                exchangeId,
                message,
            )

            session.initiator?.id?.let { realtimeEventService.broadcastToUser(it, message) }
            shareService.recipientUserIds(session.id).forEach { realtimeEventService.broadcastToUser(it, message) }
            lifecycleNotificationService.publish(session, newStatus)
        }.onFailure { e ->
            logger.warn("Failed to broadcast status change for session={} status={}", session.id, newStatus, e)
        }
    }


    private fun resolveRecipientEmail(exchangeId: UUID, includeInactive: Boolean = false): String? =
        resolvePrimaryRecipientUser(exchangeId, includeInactive)?.email
            ?: resolvePrimaryRecipientParticipantEmail(exchangeId, includeInactive)

    private fun resolvePrimaryRecipientUser(exchangeId: UUID, includeInactive: Boolean = false) =
        (if (includeInactive) shareService.primaryRecipientUserIdForDisplay(exchangeId)
        else shareService.primaryRecipientUserId(
            exchangeId
        ))
            ?.let { appUserService.getById(it) }

    private fun resolvePrimaryRecipientGroupLabel(exchangeId: UUID, includeInactive: Boolean = false): String? =
        (if (includeInactive) shareService.primaryRecipientGroupIdForDisplay(exchangeId)
        else shareService.primaryRecipientGroupId(
            exchangeId
        ))
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
                        it.roleName != ExchangeShareRoleName.OWNER.name &&
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

    private fun noAuthExchangeView(exchange: Exchange, exchangeId: UUID): NoAuthExchangeBasicDto
    {
        val constraintsJson = shareService.recipientConstraintsJson(exchangeId)
        val constraints = ShareConstraints.parse(constraintsJson)
        val downloadAllowed = constraints?.canDownload != false &&
                (constraintsJson?.contains("\"allow_document_download\":true") == true ||
                        constraints?.canDownload == true)
        val accessVerificationRequired = exchange.status == ExchangeStatus.ACCEPTED_STARTED &&
                !noAuthExchangeAccessWindowService.isActive(exchange)
        return NoAuthExchangeDtoMapper.toDto(exchange, downloadAllowed, accessVerificationRequired)
            ?: throw ExchangeNotFoundException("Exchange not found")
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
            ExchangeStatus.INITIATED ->
            {
                sendStatusChangeEmailForAudience(
                    session = session,
                    status = status,
                    audience = EmailAudience.RECIPIENT,
                    rejectionReasonOverride = rejectionReasonOverride,
                )
            }

            ExchangeStatus.ACCEPTED_STARTED,
            ExchangeStatus.REJECTED ->
            {
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

            ExchangeStatus.ENDED ->
            {
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

            ExchangeStatus.RESCINDED ->
            {
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
