package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.exception.InvalidEmailException
import com.docuhyphen.app.api.extension.normalizeEmailOrNull
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.entity.ExchangeRecipientType.APP_USER
import com.docuhyphen.app.api.model.entity.ExchangeRecipientType.EMAIL
import com.docuhyphen.app.api.model.entity.ExchangeRecipientType.GROUP
import com.docuhyphen.app.api.repository.AppUserRepository
import com.docuhyphen.app.api.repository.ExchangeRepository
import com.docuhyphen.app.api.resource.model.ExchangeInitiationDto
import com.docuhyphen.app.api.resource.model.ExternalEmailRecipientSelectionRequest
import com.docuhyphen.app.api.resource.model.InternalGroupRecipientSelectionRequest
import com.docuhyphen.app.api.resource.model.PersonalGroupRecipientSelectionRequest
import com.docuhyphen.app.api.resource.model.RegisteredUserRecipientSelectionRequest
import com.docuhyphen.app.api.resource.model.TrustedGroupRecipientSelectionRequest
import com.docuhyphen.app.api.resource.model.TrustedPersonRecipientSelectionRequest
import com.docuhyphen.app.api.service.organization.ExternalIdentityResolutionService
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.auth.AuthAuditService
import com.docuhyphen.app.api.service.auth.AuthRateLimitService
import com.docuhyphen.app.api.service.auth.AuthenticationService
import com.docuhyphen.app.api.service.auth.RevocationReasonCode
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision as AuthorizationDecision
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.communication.EmailTemplateService
import com.docuhyphen.app.api.service.communication.OtpService
import com.docuhyphen.app.api.service.config.ConfigurationService
import com.docuhyphen.app.api.service.organization.OrganizationGroupService
import com.docuhyphen.app.api.service.organization.OrganizationService
import com.docuhyphen.app.api.service.documentlibrary.DocumentLibraryService
import com.docuhyphen.app.api.service.storage.FileStorageService
import com.docuhyphen.app.api.service.variable.TemplateVariableInterpolator
import com.docuhyphen.app.api.service.variable.VariableResolutionContext
import com.docuhyphen.app.api.service.workflow.TriggerRequest
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import jakarta.transaction.Transactional
import io.quarkus.security.ForbiddenException
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@ApplicationScoped
class ExchangeInitiationService @Inject constructor(
    private val exchangeRepository: ExchangeRepository,
    private val appUserRepository: AppUserRepository,
    private val appUserService: AppUserService,
    private val emailTemplateService: EmailTemplateService,
    private val otpService: OtpService,
    private val authTokenContext: AuthTokenContext,
    private val authorizationService: AuthorizationService,
    private val authorizationContextFactory: AuthorizationContextFactory,
    private val authenticationService: AuthenticationService,
    private val authRateLimitService: AuthRateLimitService,
    private val configurationService: ConfigurationService,
    private val authAuditService: AuthAuditService,
    private val shareService: ShareService,
    private val exchangeRecipientService: ExchangeRecipientService,
    private val exchangeRecipientAttestationService: ExchangeRecipientAttestationService,
    private val externalIdentityResolutionService: ExternalIdentityResolutionService,
    private val exchangeRecipientSelectionResolver: ExchangeRecipientSelectionResolver,
    private val organizationGroupService: OrganizationGroupService,
    private val workflowEngineService: com.docuhyphen.app.api.service.workflow.WorkflowEngineService,
    private val organizationService: OrganizationService,
    private val templateVariableInterpolator: TemplateVariableInterpolator,
    private val documentLibraryService: DocumentLibraryService,
    private val fileStorageService: FileStorageService,
    private val schemaAssignmentService: com.docuhyphen.app.api.service.fields.SchemaAssignmentService,
    private val noAuthExchangeAccessTokenService: NoAuthExchangeAccessTokenService,
    private val exchangeNotificationDeliveryService: ExchangeNotificationDeliveryService,
)
{
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    companion object
    {
        private val logger = LoggerFactory.getLogger(ExchangeInitiationService::class.java)
    }

    private data class ResolvedParticipant(
        val selection: ResolvedExchangeRecipientSelection,
        val role: ExchangeShareRoleName,
    )

    @Transactional
    fun initiateExchange(sessionInitiationDto: ExchangeInitiationDto): Exchange
    {
        val initiator = authTokenContext.authToken.appUser!!
        authorizeActiveOrganizationInitiation()
        val primarySelectionRequest = sessionInitiationDto.primaryRecipient
            ?: throw IllegalArgumentException("Primary recipient is required")

        if (primarySelectionRequest is ExternalEmailRecipientSelectionRequest)
        {
            val limited = authRateLimitService.isLimited(
                key = "directory:recipient-resolve:${initiator.id}",
                maxPerMinute = configurationService.getAuthRateLimitLookupPerMinute(),
            )
            if (limited)
            {
                authAuditService.emit(
                    action = "RECIPIENT_RESOLVE",
                    outcome = "DENY",
                    reasonCode = RevocationReasonCode.SECURITY_POLICY,
                    actorId = initiator.id,
                )
                throw IllegalArgumentException("Too many recipient lookup requests. Please try again later.")
            }
        }

        validateExchangeFields(initiator, sessionInitiationDto)
        val activeOrganizationId = authTokenContext.activeOrganizationId
        val resolvedPrimary = exchangeRecipientSelectionResolver.resolve(
            primarySelectionRequest,
            initiator,
            activeOrganizationId,
        )
        val recipientGroupId = resolvedPrimary.group?.id
        val recipientGroup = resolvedPrimary.group
        val resolvedRecipient = resolvedPrimary.appUser
        val resolvedParticipants = sessionInitiationDto.participants.map { participant ->
            require(participant.role != ExchangeShareRoleName.OWNER) {
                "Exchange owner cannot be added as a participant"
            }
            val resolved = exchangeRecipientSelectionResolver.resolve(
                participant.selection,
                initiator,
                activeOrganizationId,
            )
            require(resolved.selectionType != ExchangeRecipientSelectionType.EXTERNAL_EMAIL) {
                "External email participants are not supported"
            }
            ResolvedParticipant(resolved, participant.role)
        }
        val primaryPrincipal = resolvedPrimary.principalKind to resolvedPrimary.principalId
        val participantPrincipals = resolvedParticipants.map {
            it.selection.principalKind to it.selection.principalId
        }
        require(primaryPrincipal !in participantPrincipals) {
            "The primary recipient cannot also be an additional participant"
        }
        require(participantPrincipals.distinct().size == participantPrincipals.size) {
            "An additional participant cannot be selected more than once"
        }

        entityManager.detach(initiator)
        resolvedRecipient?.let { entityManager.detach(it) }

        val appUserRecipient = resolvedRecipient?.let { entityManager.merge(it) }

        // Interpolate template variables in all string fields before entity creation.
        val orgId0 = authTokenContext.activeOrganizationId
        val orgForInterpolation = orgId0?.let { organizationService.getOrganizationById(it) }
        val interpolationContext = VariableResolutionContext(
            user = initiator,
            organization = orgForInterpolation,
            timestamp = java.time.Instant.now(),
            overrides = sessionInitiationDto.variableOverrides ?: emptyMap(),
        )
        val resolvedName = sessionInitiationDto.name?.let {
            templateVariableInterpolator.interpolateWithSequences(it, interpolationContext).resolved
        }
        val resolvedDescription = sessionInitiationDto.description?.let {
            templateVariableInterpolator.interpolate(it, interpolationContext).resolved
        }
        val resolvedShareMessage = sessionInitiationDto.initialShareMessage?.let {
            templateVariableInterpolator.interpolate(it, interpolationContext).resolved
        }
        val resolvedDocTitles: Map<Int, String> = sessionInitiationDto.exchangeDocuments
            ?.mapIndexed { i, doc ->
                i to templateVariableInterpolator.interpolate(doc.title, interpolationContext).resolved
            }?.toMap() ?: emptyMap()

        val exchange = Exchange().apply {
            this.initiator = entityManager.merge(initiator)
            this.name = (resolvedName ?: sessionInitiationDto.name)!!.trim()
            this.initialShareMessage = resolvedShareMessage?.trim()
            this.description = resolvedDescription?.trim()
            this.status = ExchangeStatus.INITIATED
            this.createdDate = Timestamp.from(Instant.now())
            this.lastActivity = Timestamp.from(Instant.now())
            this.requireRecipientSignIn = sessionInitiationDto.requestRecipientSignIn == true ||
                resolvedPrimary.selectionType == ExchangeRecipientSelectionType.TRUSTED_GROUP ||
                resolvedPrimary.selectionType == ExchangeRecipientSelectionType.TRUSTED_PERSON
            if (orgId0 != null) {
                this.ownerOrganizationId = orgId0
            } else {
                this.ownerUserId = initiator.id
            }
        }

        val libraryFilesToCopy: MutableList<Pair<Document, UUID>> = mutableListOf()

        sessionInitiationDto.exchangeDocuments?.forEachIndexed { index, doc ->
            val document = Document().apply {
                this.title = resolvedDocTitles[index] ?: doc.title
                this.createdDate = Timestamp.from(Instant.now())
                this.updateDate = Timestamp.from(Instant.now())
                this.uploadDate = null
                this.isDeleted = false
                this.type = null // Type is set on the document upload method
                this.restrictedType = doc.restrictedType
                this.restrictType = doc.restrictType
                this.required = doc.required
            }
            exchange.documents.add(document)
            doc.libraryDocumentId?.let { libId -> libraryFilesToCopy.add(document to libId) }
        }

        val savedExchange = exchangeRepository.save(exchange)

        libraryFilesToCopy.forEach { (document, libId) ->
            try
            {
                val libFile = documentLibraryService.resolveLibraryFileForBlueprintDocument(libId)
                if (libFile != null)
                {
                    val ext = libFile.name.substringAfterLast('.', "")
                    val storageKey = "${document.id}.$ext"
                    fileStorageService.uploadDocument(libFile, storageKey)
                    document.type = DocumentType.fromFileExtension(".$ext")
                    document.uploadDate = Timestamp.from(Instant.now())
                    document.hash = "hash"
                    exchangeRepository.update(savedExchange)
                }
            }
            catch (e: Exception)
            {
                logger.warn("Failed to pre-populate exchange document {} from library entry {}: {}", document.id, libId, e.message)
            }
        }

        // Grant the initiator's OWNER share before any authorization-gated operation runs in this
        // transaction (e.g. applyCreationTimeFields below requires EXCHANGE_WRITE, which is only
        // granted via an active Share on the exchange).
        grantInitiatorOwnerShare(savedExchange, initiator)

        // Apply an optional business schema + typed field values chosen at creation time, inside
        // this same transaction and BEFORE any workflow fires, so workflow applicability (and
        // assignee logic) can observe the values. Validation failures roll back the whole Exchange.
        applyCreationTimeFields(savedExchange.id, sessionInitiationDto)

        // Use the caller's validated active organization for Exchange settings and workflows.
        val orgId = authTokenContext.activeOrganizationId
        val orgSettings = orgId?.let { organizationService.getOrganizationById(it) }?.settings

        // 1. Fire exchange.draft_submitted (optional pre-send internal-approval gate).
        //    If an active workflow picks this up, the recipient share must be held until
        //    EVENT_DRAFT_APPROVED fires (which then progresses to the acceptance phase).
        //    Capturing the result here prevents the acceptance_pending trigger from firing
        //    prematurely at creation time, and avoids the duplicate workflow that would
        //    otherwise occur when both a draft and an acceptance workflow are configured.
        val draftResult = workflowEngineService.trigger(
            TriggerRequest(
                triggerEvent = "exchange.draft_submitted",
                subjectResourceType = ResourceType.EXCHANGE.name,
                subjectResourceId = savedExchange.id,
                organizationId = orgId,
                subjectData = buildMap {
                    put("initiatorId", initiator.id.toString())
                    orgId?.let { put("orgId", it.toString()) }
                },
                initiatedByAppUserId = initiator.id,
            )
        )
        val draftApprovalPending = draftResult != null

        // 2. Determine whether the recipient's share must be held PENDING_APPROVAL.
        //
        //    • Draft approval pending: hold share unconditionally; acceptance_pending is deferred
        //      to EVENT_DRAFT_APPROVED (ExchangeApprovalEventHandler), which already handles it.
        //    • No draft approval, requireRecipientAcceptance = true: fire acceptance_pending now;
        //      a matching WorkflowDefinition holds the share until "exchange.activated" is emitted.
        //    • No draft approval, requireRecipientAcceptance = false: auto-advance immediately.
        // Trusted person and trusted group selections always require recipient sign-in and
        // acceptance, even when the initiating organization normally auto-starts Exchanges. This
        // preserves the trusted assurance contract: their direct Share is held until the attested
        // recipient decides.
        val trustedRecipient = resolvedPrimary.selectionType == ExchangeRecipientSelectionType.TRUSTED_GROUP ||
            resolvedPrimary.selectionType == ExchangeRecipientSelectionType.TRUSTED_PERSON
        val requireAcceptance = trustedRecipient || (orgSettings?.requireRecipientAcceptance ?: true)
        val recipientNeedsApproval: Boolean

        if (draftApprovalPending)
        {
            recipientNeedsApproval = true
            logger.info(
                "Exchange {} has draft approval pending (workflow instance {}); share held PENDING_APPROVAL",
                savedExchange.id, draftResult!!.instanceId,
            )
        }
        else if (requireAcceptance)
        {
            val subjectData = buildMap<String, String> {
                put("initiatorId", initiator.id.toString())
                put("recipientType", resolvedPrimary.selectionType.name)
                put("exchangeName", savedExchange.name ?: "")
                initiator.person?.let { p ->
                    listOfNotNull(p.firstName, p.lastName)
                        .joinToString(" ")
                        .takeIf { it.isNotBlank() }
                        ?.let { put("initiatorName", it) }
                }
                orgId?.let { put("orgId", it.toString()) }
                when (resolvedPrimary.recipientType)
                {
                    GROUP -> recipientGroupId?.let { put("recipientGroupId", it.toString()) }
                    else -> appUserRecipient?.let { put("recipientId", it.id.toString()) }
                }
            }

            val acceptanceResult = workflowEngineService.trigger(
                TriggerRequest(
                    triggerEvent = "exchange.acceptance_pending",
                    subjectResourceType = ResourceType.EXCHANGE.name,
                    subjectResourceId = savedExchange.id,
                    organizationId = orgId,
                    subjectData = subjectData,
                    initiatedByAppUserId = initiator.id,
                )
            )

            recipientNeedsApproval = trustedRecipient || acceptanceResult != null
            if (acceptanceResult != null)
            {
                logger.info(
                    "Exchange {} requires recipient acceptance (workflow instance {}); share held PENDING_APPROVAL",
                    savedExchange.id, acceptanceResult.instanceId,
                )
            }
        }
        else
        {
            // Org-level auto-accept: exchange is immediately active.
            savedExchange.status = ExchangeStatus.ACCEPTED_STARTED
            exchangeRepository.update(savedExchange)
            workflowEngineService.trigger(
                TriggerRequest(
                    triggerEvent = "exchange.activated",
                    subjectResourceType = ResourceType.EXCHANGE.name,
                    subjectResourceId = savedExchange.id,
                    organizationId = orgId,
                    subjectData = buildMap {
                        put("initiatorId", initiator.id.toString())
                        orgId?.let { put("orgId", it.toString()) }
                    },
                    initiatedByAppUserId = initiator.id,
                )
            )
            recipientNeedsApproval = false
            logger.info("Exchange {} created with auto-accept (requireRecipientAcceptance=false)", savedExchange.id)
        }

        // Recipients/participants/permissions live in the unified Share model. The initiator's
        // OWNER share was already granted above (before applyCreationTimeFields); the recipient
        // gets a role derived from the requested document permissions, and each participant a
        // PARTICIPANT share (groups fan out to members).
        val primaryRecipientShare = grantRecipientShare(
            session = savedExchange,
            recipientType = resolvedPrimary.recipientType,
            recipientAppUser = appUserRecipient,
            recipientGroupId = recipientGroupId,
            initiator = initiator,
            dto = sessionInitiationDto,
            pendingApproval = recipientNeedsApproval,
        )
        grantParticipantShares(savedExchange, resolvedParticipants, initiator)

        val externalEmailSelection = primarySelectionRequest as? ExternalEmailRecipientSelectionRequest
        val primaryRecipient = exchangeRecipientService.createBinding(
            exchangeId = savedExchange.id,
            directShare = primaryRecipientShare,
            purpose = ExchangeRecipientPurpose.PRIMARY,
            selectionType = resolvedPrimary.selectionType,
            targetOrganizationId = resolvedPrimary.targetOrganizationId,
            acceptanceStatus = if (requireAcceptance)
                ExchangeRecipientAcceptanceStatus.PENDING
            else
                ExchangeRecipientAcceptanceStatus.NOT_REQUIRED,
        )
        resolvedPrimary.trustedGroupValidation?.let { validation ->
            exchangeRecipientAttestationService.createGroupAttestation(primaryRecipient, validation)
        }
        resolvedPrimary.preparedPersonResolution?.let { prepared ->
            consumeTrustedPersonResolution(prepared, initiator.id, activeOrganizationId, savedExchange.id)
            exchangeRecipientAttestationService.createPersonAttestation(primaryRecipient, prepared)
        }

        scheduleNotificationsAfterCommit(
            exchange = savedExchange,
            initiator = initiator,
            primarySelectionType = resolvedPrimary.selectionType,
            recipientType = resolvedPrimary.recipientType,
            recipientAppUser = appUserRecipient,
            recipientGroupId = recipientGroupId,
            participants = resolvedParticipants,
            externalEmailSelection = externalEmailSelection,
            draftApprovalPending = draftApprovalPending,
            recipientNeedsApproval = recipientNeedsApproval,
        )

        logger.info("Sharing Exchange Initiated ID: ${exchange.id}")
        return savedExchange
    }

    private fun authorizeActiveOrganizationInitiation()
    {
        val activeOrganizationId = authTokenContext.activeOrganizationId ?: return
        val principal = authorizationContextFactory.currentPrincipal()
            ?: throw ForbiddenException("Authentication is required to initiate an Exchange")
        val decision = authorizationService.authorize(
            principal = principal,
            action = Action.EXCHANGE_INITIATE,
            resource = ResourceRef.organization(activeOrganizationId),
            context = authorizationContextFactory.currentContext(),
        )
        if (decision is AuthorizationDecision.Deny)
        {
            throw ForbiddenException("Not authorized to initiate an Exchange for the active organization")
        }
    }

    /**
     * Creates the direct Share that grants the selected recipient access to the Exchange.
     * Group Shares fan out to active group members through [ShareService].
     */
    private fun grantRecipientShare(
        session: Exchange,
        recipientType: ExchangeRecipientType,
        recipientAppUser: AppUser?,
        recipientGroupId: UUID?,
        initiator: AppUser,
        dto: ExchangeInitiationDto,
        pendingApproval: Boolean = false,
    ): Share
    {
        val (principalKind, principalId) = when (recipientType)
        {
            GROUP -> recipientGroupId?.let { PrincipalKind.PRINCIPAL_GROUP to it }
                ?: throw IllegalArgumentException("Recipient group is required")
            else -> recipientAppUser?.let { PrincipalKind.USER to it.id }
                ?: throw IllegalArgumentException("Recipient user is required")
        }

        return shareService.grant(
            resourceType = ResourceType.EXCHANGE,
            resourceId = session.id,
            principalKind = principalKind,
            principalId = principalId,
            roleName = recipientRoleFor(dto),
            grantedByAppUserId = initiator.id,
            source = ShareSource.DIRECT,
            constraintsJson = sessionConstraintsJson(dto),
            status = if (pendingApproval) ShareStatus.PENDING_APPROVAL else ShareStatus.ACTIVE,
        )
    }


    /** Grant the initiator an OWNER [Share] (OWNER carries EXCHANGE_OWNER / EXCHANGE_SHARE). */
    private fun grantInitiatorOwnerShare(session: Exchange, initiator: AppUser)
    {
        shareService.grant(
            resourceType = ResourceType.EXCHANGE,
            resourceId = session.id,
            principalKind = PrincipalKind.USER,
            principalId = initiator.id,
            roleName = ExchangeShareRoleName.OWNER,
            grantedByAppUserId = initiator.id,
            source = ShareSource.DIRECT,
        )
    }

    /**
     * Grant each participant the requested non-owner Share role. Trusted participant Shares remain
     * pending until that participant independently accepts its attested invitation.
     */
    private fun grantParticipantShares(
        session: Exchange,
        participants: List<ResolvedParticipant>,
        initiator: AppUser,
    )
    {
        for (participant in participants)
        {
            val selection = participant.selection
            val trusted = selection.trustedGroupValidation != null || selection.preparedPersonResolution != null
            val participantShare = shareService.grant(
                resourceType = ResourceType.EXCHANGE,
                resourceId = session.id,
                principalKind = selection.principalKind,
                principalId = selection.principalId,
                roleName = participant.role,
                grantedByAppUserId = initiator.id,
                source = ShareSource.DIRECT,
                status = if (trusted) ShareStatus.PENDING_APPROVAL else ShareStatus.ACTIVE,
            )
            val recipient = exchangeRecipientService.createBinding(
                exchangeId = session.id,
                directShare = participantShare,
                purpose = ExchangeRecipientPurpose.PARTICIPANT,
                selectionType = selection.selectionType,
                targetOrganizationId = selection.targetOrganizationId,
                acceptanceStatus = if (trusted)
                    ExchangeRecipientAcceptanceStatus.PENDING
                else
                    ExchangeRecipientAcceptanceStatus.NOT_REQUIRED,
            )
            selection.trustedGroupValidation?.let { validation ->
                exchangeRecipientAttestationService.createGroupAttestation(recipient, validation)
            }
            selection.preparedPersonResolution?.let { prepared ->
                consumeTrustedPersonResolution(prepared, initiator.id, authTokenContext.activeOrganizationId, session.id)
                exchangeRecipientAttestationService.createPersonAttestation(recipient, prepared)
            }
        }
    }

    /**
     * Consumes a trusted-member verification under a row lock inside this Exchange transaction so it
     * cannot be replayed. Consumption rolls back if Exchange creation rolls back.
     */
    private fun consumeTrustedPersonResolution(
        prepared: ExternalIdentityResolutionService.PreparedPersonResolution,
        actorAppUserId: UUID,
        callerOrganizationId: UUID?,
        exchangeId: UUID,
    )
    {
        val organizationId = callerOrganizationId
            ?: throw IllegalArgumentException("An active organization is required for a trusted person")
        externalIdentityResolutionService.consumeForExchange(
            resolutionId = prepared.resolution.id,
            actorAppUserId = actorAppUserId,
            callerOrganizationId = organizationId,
            targetOrganizationId = prepared.resolution.targetOrganizationId,
            exchangeId = exchangeId,
        )
    }

    /**
     * Applies a caller-selected business schema and typed field values to a freshly created
     * Exchange, delegating to [SchemaAssignmentService] (service-to-service, never cross-repository).
     * Called inside [initiateExchange]'s transaction and before any workflow trigger fires, so a
     * field-conditioned workflow can observe the values. Any validation failure propagates and rolls
     * back the whole Exchange creation. The assignment source defaults to `MANUAL` and is set to
     * `BLUEPRINT` when the Exchange is started from a blueprint that pre-seeds the schema.
     */
    internal fun applyCreationTimeFields(exchangeId: UUID, dto: ExchangeInitiationDto)
    {
        val rawSchemaId = dto.schemaDefinitionId?.trim()?.takeIf { it.isNotBlank() }
        val fieldValues = dto.fieldValues ?: emptyList()

        if (rawSchemaId == null)
        {
            if (fieldValues.isNotEmpty())
                throw IllegalArgumentException("Field values were supplied without a schema; select a schema first")
            return
        }

        val schemaDefinitionId = try
        {
            UUID.fromString(rawSchemaId)
        }
        catch (e: IllegalArgumentException)
        {
            throw IllegalArgumentException("Invalid schema id: $rawSchemaId")
        }

        val assignmentSource = dto.schemaAssignmentSource
            ?: com.docuhyphen.app.api.model.entity.SchemaAssignmentSource.MANUAL
        schemaAssignmentService.assignSchema(ResourceType.EXCHANGE.name, exchangeId, schemaDefinitionId, assignmentSource)
        if (fieldValues.isNotEmpty())
            schemaAssignmentService.setValues(ResourceType.EXCHANGE.name, exchangeId, fieldValues)
    }

    /**
     * Maps the requested document permissions to a resource role: any write-style permission
     * (add/delete/update/upload) implies EDITOR, otherwise VIEWER. The full flag set is
     * preserved verbatim in the share's constraints JSON (see [sessionConstraintsJson]).
     *
     * When the caller supplies an explicit `recipientRoleName`, honor it (after
     * validating it against [ExchangeShareRoleName]). Lets the UI offer PARTICIPANT / VIEWER /
     * COMMENTER / SIGNER / REVIEWER at initiation, not just EDITOR/VIEWER.
     */
    private fun recipientRoleFor(dto: ExchangeInitiationDto): ExchangeShareRoleName
    {
        dto.recipientRoleName?.trim()?.takeIf { it.isNotBlank() }?.let { explicit ->
            return runCatching { ExchangeShareRoleName.valueOf(explicit.uppercase()) }
                .getOrElse { throw IllegalArgumentException("Invalid Exchange Share role: $explicit") }
        }
        val canWrite = dto.allowDocumentAddition == true || dto.allowDocumentDeletion == true ||
            dto.allowDocumentUpdate == true || dto.allowDocumentUpload == true
        return if (canWrite) ExchangeShareRoleName.EDITOR else ExchangeShareRoleName.VIEWER
    }

    /**
     * Build the constraints JSON for the recipient share. Always emits the legacy
     * `can_download` + `allow_document_*` keys derived from the per-permission flags; when
     * the caller supplies an explicit `recipientConstraintsJson` blob, its keys
     * are merged on top (explicit wins). The result is parsed by
     * [com.docuhyphen.app.api.service.auth.authz.ShareConstraints] at read time and powers
     * the access panel + viewer obligations.
     */
    private fun sessionConstraintsJson(dto: ExchangeInitiationDto): String
    {
        val legacy: Map<String, Any> = mapOf(
            "can_download" to (dto.allowDocumentDownload == true),
            "allow_document_addition" to (dto.allowDocumentAddition == true),
            "allow_document_deletion" to (dto.allowDocumentDeletion == true),
            "allow_document_update" to (dto.allowDocumentUpdate == true),
            "allow_document_upload" to (dto.allowDocumentUpload == true),
            "require_recipient_sign_in" to (dto.requestRecipientSignIn == true),
        )
        val explicit = parseExplicitConstraints(dto.recipientConstraintsJson)
        val merged = legacy + explicit
        val parts = merged.entries.map { (k, v) ->
            "\"$k\":${renderConstraintValue(v)}"
        }.toMutableList()

        // Add allowed_download_formats as a JSON array if present
        if (dto.allowedDownloadFormats != null)
        {
            val formatsArray = dto.allowedDownloadFormats!!.joinToString(",") { "\"$it\"" }
            parts.add("\"allowed_download_formats\":[$formatsArray]")
        }

        return parts.joinToString(prefix = "{", postfix = "}", separator = ",")
    }

    private fun parseExplicitConstraints(jsonStr: String?): Map<String, Any>
    {
        val trimmed = jsonStr?.trim()?.takeIf { it.isNotBlank() && it != "{}" } ?: return emptyMap()
        return try
        {
            val element = kotlinx.serialization.json.Json.parseToJsonElement(trimmed)
            val obj = element as? kotlinx.serialization.json.JsonObject ?: return emptyMap()
            obj.entries.mapNotNull { (k, v) ->
                val prim = v as? kotlinx.serialization.json.JsonPrimitive ?: return@mapNotNull null
                val coerced: Any? = when
                {
                    prim.isString -> prim.content
                    prim.content == "true" -> true
                    prim.content == "false" -> false
                    prim.content == "null" -> null
                    else -> prim.content.toIntOrNull() ?: prim.content.toLongOrNull() ?: prim.content
                }
                coerced?.let { k to it }
            }.toMap()
        }
        catch (e: Exception)
        {
            logger.warn("Ignoring malformed recipientConstraintsJson on session initiation: {}", e.message)
            emptyMap()
        }
    }

    private fun renderConstraintValue(v: Any): String = when (v)
    {
        is Boolean -> v.toString()
        is Number -> v.toString()
        else -> "\"${v.toString().replace("\\", "\\\\").replace("\"", "\\\"")}\""
    }

    fun validateExchangeFields(
        initiator: AppUser,
        sessionInitiationDto: ExchangeInitiationDto
    )
    {
        if (sessionInitiationDto.name.isNullOrBlank())
        {
            throw IllegalArgumentException("Session name is required")
        }

        when (val selection = sessionInitiationDto.primaryRecipient)
        {
            is ExternalEmailRecipientSelectionRequest ->
            {
                if (selection.email.trim().length < 5)
                {
                    throw InvalidEmailException("Recipient email is invalid")
                }

                if (initiator.email.normalizeEmailOrNull() == selection.email.normalizeEmailOrNull())
                {
                    throw IllegalArgumentException("Recipient and Initiator cannot be the same")
                }

                if (authenticationService.isEmailInvalid(selection.email))
                {
                    throw InvalidEmailException("Recipient email is invalid")
                }
                if (selection.firstName.isBlank() || selection.lastName.isBlank())
                {
                    throw IllegalArgumentException("Recipient first and last name are required")
                }
            }

            is RegisteredUserRecipientSelectionRequest ->
            {
                val recipientId = UUID.fromString(selection.appUserId)
                if (recipientId == initiator.id)
                {
                    throw IllegalArgumentException("Recipient and Initiator cannot be the same")
                }
            }

            is InternalGroupRecipientSelectionRequest -> UUID.fromString(selection.groupId)
            is PersonalGroupRecipientSelectionRequest -> UUID.fromString(selection.groupId)
            is TrustedGroupRecipientSelectionRequest ->
            {
                UUID.fromString(selection.organizationId)
                UUID.fromString(selection.groupId)
            }

            is TrustedPersonRecipientSelectionRequest -> UUID.fromString(selection.resolutionId)

            null -> throw IllegalArgumentException("Primary recipient is required")
        }

        if (sessionInitiationDto.exchangeDocuments.isNullOrEmpty())
        {
            throw IllegalArgumentException("Session documents cannot be empty")
        }

        sessionInitiationDto.participants.forEach {
            val selection = it.selection
            if (selection !is RegisteredUserRecipientSelectionRequest)
            {
                return@forEach
            }

            val participantId = runCatching { UUID.fromString(selection.appUserId) }.getOrNull()
                ?: throw IllegalArgumentException("Participant id is invalid")

            if (participantId == initiator.id)
            {
                throw IllegalArgumentException("Initiator cannot be added as a participant")
            }
        }
    }

    private fun scheduleNotificationsAfterCommit(
        exchange: Exchange,
        initiator: AppUser,
        primarySelectionType: ExchangeRecipientSelectionType,
        recipientType: ExchangeRecipientType,
        recipientAppUser: AppUser?,
        recipientGroupId: UUID?,
        participants: List<ResolvedParticipant>,
        externalEmailSelection: ExternalEmailRecipientSelectionRequest?,
        draftApprovalPending: Boolean,
        recipientNeedsApproval: Boolean,
    )
    {
        val initiatorName = listOfNotNull(
            initiator.person?.firstName?.trim()?.takeIf { it.isNotBlank() },
            initiator.person?.lastName?.trim()?.takeIf { it.isNotBlank() },
        ).joinToString(" ").ifBlank { initiator.email }
        val documentTitles = exchange.documents.map { it.title }
        val exchangeIdStr = exchange.id.toString()
        val subjectTitle = configurationService.emailSubjectTitle
        val trustedPrimary = primarySelectionType == ExchangeRecipientSelectionType.TRUSTED_PERSON ||
            primarySelectionType == ExchangeRecipientSelectionType.TRUSTED_GROUP
        val notifyPrimary = !draftApprovalPending && (trustedPrimary || !recipientNeedsApproval)
        val primaryAudience = if (notifyPrimary)
            notificationAudience(
                selectionType = primarySelectionType,
                principalKind = if (recipientType == GROUP) PrincipalKind.PRINCIPAL_GROUP else PrincipalKind.USER,
                principalId = recipientGroupId ?: recipientAppUser?.id,
            ).filter { it.id != initiator.id }
        else
            emptyList()
        val trustedParticipantAudience = participants
            .filter {
                it.selection.selectionType == ExchangeRecipientSelectionType.TRUSTED_PERSON ||
                    it.selection.selectionType == ExchangeRecipientSelectionType.TRUSTED_GROUP
            }
            .flatMap { participant ->
                notificationAudience(
                    selectionType = participant.selection.selectionType,
                    principalKind = participant.selection.principalKind,
                    principalId = participant.selection.principalId,
                )
            }
            .filter { it.id != initiator.id }
            .distinctBy { it.id }
        val ordinaryParticipantAudience = participants
            .filterNot {
                it.selection.selectionType == ExchangeRecipientSelectionType.TRUSTED_PERSON ||
                    it.selection.selectionType == ExchangeRecipientSelectionType.TRUSTED_GROUP
            }
            .flatMap { participant ->
                notificationAudience(
                    selectionType = participant.selection.selectionType,
                    principalKind = participant.selection.principalKind,
                    principalId = participant.selection.principalId,
                )
            }
            .filter { it.id != initiator.id }
            .distinctBy { it.id }

        val recipientLabel = when (recipientType)
        {
            GROUP -> recipientGroupId?.let { organizationGroupService.getById(it.toString())?.name }
                ?.let { "Group: $it" } ?: "Group"
            else -> recipientPersonLabel(recipientAppUser, externalEmailSelection)
        }

        val requireSignInForRecipient = recipientType == EMAIL &&
            recipientAppUser?.isTemporary == true &&
            exchange.requireRecipientSignIn

        val isNoAuthTempRecipient = recipientType == EMAIL &&
            recipientAppUser?.isTemporary == true &&
            !exchange.requireRecipientSignIn
        val emails = mutableListOf<ExchangeEmailDelivery>()

        if (notifyPrimary)
        {
            if (isNoAuthTempRecipient)
            {
                val recipientEmailAddr = recipientAppUser!!.email
                if (!recipientEmailAddr.isNullOrBlank())
                {
                    val otp = otpService.generateEmailOtp()
                    val accessToken = noAuthExchangeAccessTokenService.issue(exchange)
                    val validityDays = exchange.noAuthAccessValidityDays.toLong()
                    exchange.recipientOtpHash = otpService.hashOtp(otp)
                    exchange.recipientOtpExpiry = Timestamp.from(
                        Instant.now().plusSeconds(validityDays * 24 * 3600),
                    )
                    val expiryLabel = if (validityDays == 1L) "1 day" else "$validityDays days"
                    val rendered = emailTemplateService.renderExchangeCreatedNoAuthRecipientEmail(
                        exchangeId = exchangeIdStr,
                        name = exchange.name.orEmpty(),
                        initiatorName = initiatorName,
                        initiatorOrganization = null,
                        sessionMessage = exchange.initialShareMessage,
                        documents = documentTitles,
                        otp = otp,
                        accessToken = accessToken,
                        expiryLabel = expiryLabel,
                    )
                    emails += ExchangeEmailDelivery(
                        to = recipientEmailAddr,
                        subject = rendered.subject,
                        body = rendered.body,
                    )
                }
            }
            else
            {
                val body = emailTemplateService.renderExchangeCreatedRecipientEmail(
                    exchangeId = exchangeIdStr,
                    name = exchange.name.orEmpty(),
                    initiatorName = initiatorName,
                    initiatorOrganization = null,
                    sessionMessage = exchange.initialShareMessage,
                    documents = documentTitles,
                    requireSignIn = requireSignInForRecipient || trustedPrimary,
                )
                primaryAudience.forEach { appUser ->
                    emails += ExchangeEmailDelivery(
                        to = appUser.email,
                        subject = "$subjectTitle | Exchange request from $initiatorName",
                        body = body,
                        preferenceAppUserId = appUser.id,
                    )
                }
            }
        }

        if (trustedParticipantAudience.isNotEmpty())
        {
            val body = emailTemplateService.renderExchangeCreatedRecipientEmail(
                exchangeId = exchangeIdStr,
                name = exchange.name.orEmpty(),
                initiatorName = initiatorName,
                initiatorOrganization = null,
                sessionMessage = "You have been invited as a trusted participant.",
                documents = documentTitles,
                requireSignIn = true,
            )
            trustedParticipantAudience.forEach { appUser ->
                emails += ExchangeEmailDelivery(
                    to = appUser.email,
                    subject = "$subjectTitle | Trusted participant invitation from $initiatorName",
                    body = body,
                    preferenceAppUserId = appUser.id,
                )
            }
        }

        emails += ExchangeEmailDelivery(
            to = initiator.email,
            subject = "$subjectTitle | Exchange request sent",
            body = emailTemplateService.renderExchangeCreatedInitiatorEmail(
                exchangeId = exchangeIdStr,
                name = exchange.name.orEmpty(),
                recipientLabel = recipientLabel,
                documents = documentTitles,
            ),
            preferenceAppUserId = initiator.id,
        )

        val exchangeLabel = exchange.name.orEmpty().ifBlank { exchangeIdStr }
        val inAppNotifications = mutableListOf<ExchangeInAppDelivery>()
        primaryAudience.forEach { appUser ->
            inAppNotifications += ExchangeInAppDelivery(
                appUserId = appUser.id,
                type = "exchange.initiated",
                title = "Exchange invitation",
                message = "A new Exchange, $exchangeLabel, is waiting for your decision.",
                data = mapOf("exchangeId" to exchangeIdStr),
            )
        }
        ordinaryParticipantAudience.forEach { appUser ->
            inAppNotifications += ExchangeInAppDelivery(
                appUserId = appUser.id,
                type = "exchange.initiated",
                title = "Exchange initiated",
                message = "A new Exchange, $exchangeLabel, was shared with you.",
                data = mapOf("exchangeId" to exchangeIdStr),
            )
        }
        trustedParticipantAudience.forEach { appUser ->
            inAppNotifications += ExchangeInAppDelivery(
                appUserId = appUser.id,
                type = "exchange.recipient_invitation",
                title = "Trusted participant invitation",
                message = "You were invited to access Exchange $exchangeLabel.",
                data = mapOf("exchangeId" to exchangeIdStr),
            )
        }
        val refreshAppUserIds = buildSet {
            add(initiator.id)
            addAll(primaryAudience.map { it.id })
            addAll(ordinaryParticipantAudience.map { it.id })
            addAll(trustedParticipantAudience.map { it.id })
        }
        exchangeNotificationDeliveryService.scheduleAfterCommit(
            exchangeId = exchange.id,
            exchangeStatus = exchange.status.name,
            emails = emails.distinctBy { it.to.lowercase() to it.subject },
            inAppNotifications = inAppNotifications.distinctBy { it.appUserId to it.type },
            refreshAppUserIds = refreshAppUserIds,
        )
    }

    private fun notificationAudience(
        selectionType: ExchangeRecipientSelectionType,
        principalKind: PrincipalKind,
        principalId: UUID?,
    ): List<AppUser>
    {
        val resolvedPrincipalId = principalId ?: return emptyList()
        val appUserIds = if (principalKind == PrincipalKind.PRINCIPAL_GROUP)
        {
            if (selectionType == ExchangeRecipientSelectionType.TRUSTED_GROUP)
                organizationGroupService.activeOwnerOrManagerUserIds(resolvedPrincipalId)
            else
                organizationGroupService.activeUserIds(resolvedPrincipalId)
        }
        else
        {
            setOf(resolvedPrincipalId)
        }
        return appUserIds.mapNotNull(appUserService::getById)
    }

    private fun recipientPersonLabel(
        recipientAppUser: AppUser?,
        externalEmailSelection: ExternalEmailRecipientSelectionRequest?,
    ): String
    {
        val resolvedUserName = personLabel(
            recipientAppUser?.person?.firstName,
            recipientAppUser?.person?.lastName,
        )
        val requestedExternalName = personLabel(
            externalEmailSelection?.firstName,
            externalEmailSelection?.lastName,
        )
        return resolvedUserName
            ?: requestedExternalName
            ?: recipientAppUser?.email
            ?: externalEmailSelection?.email
            ?: "Recipient"
    }

    private fun personLabel(firstName: String?, lastName: String?): String? =
        listOfNotNull(
            firstName?.trim()?.takeIf { it.isNotBlank() },
            lastName?.trim()?.takeIf { it.isNotBlank() },
        ).joinToString(" ").takeIf { it.isNotBlank() }

    /**
     * Sends the initial "document request" invite email to the recipient(s) after the exchange
     * has been activated (i.e. after any approval workflows have completed). The initiator email
     * is intentionally omitted here because it was already sent at creation time.
     *
     * Resolves the recipient from the now-active Share rows so there is no dependency on the
     * original initiation call-site context.
     */
    fun notifyRecipientOnActivation(exchangeId: UUID)
    {
        val exchange = exchangeRepository.findById(exchangeId) ?: return
        val initiator = exchange.initiator ?: return
        val initiatorName = initiator.person?.let { "${it.firstName} ${it.lastName}" } ?: initiator.email
        val documentTitles = exchange.documents.map { it.title }
        val subjectTitle = configurationService.emailSubjectTitle

        val recipientGroupId = shareService.primaryRecipientGroupId(exchangeId)
        val recipientUserId = if (recipientGroupId == null) shareService.primaryRecipientUserId(exchangeId) else null

        val recipientAppUsers: List<AppUser> = when
        {
            recipientGroupId != null ->
                organizationGroupService.activeUserIds(recipientGroupId)
                    .mapNotNull(appUserService::getById)
                    .filter { it.id != initiator.id }

            recipientUserId != null ->
                appUserService.getById(recipientUserId)
                    ?.let { listOf(it) }
                    ?: emptyList()

            else -> emptyList()
        }

        val body = emailTemplateService.renderExchangeCreatedRecipientEmail(
            exchangeId = exchangeId.toString(),
            name = exchange.name.orEmpty(),
            initiatorName = initiatorName,
            initiatorOrganization = null,
            sessionMessage = exchange.initialShareMessage,
            documents = documentTitles,
            requireSignIn = false,
        )
        val emails = recipientAppUsers
            .filter { it.settings?.notifyShareStart != false }
            .mapNotNull { appUser ->
                appUser.email?.let { email ->
                    ExchangeEmailDelivery(
                        to = email,
                        subject = "$subjectTitle | Exchange request from $initiatorName",
                        body = body,
                        preferenceAppUserId = appUser.id,
                    )
                }
            }
        val notificationAppUserIds = shareService.recipientUserIds(exchangeId)
            .filterNot { it == initiator.id }
            .toSet()
        val exchangeLabel = exchange.name?.takeIf { it.isNotBlank() } ?: "Exchange"
        val inAppNotifications = notificationAppUserIds.map { appUserId ->
            ExchangeInAppDelivery(
                appUserId = appUserId,
                type = "exchange.initiated",
                title = "Exchange initiated",
                message = "A new Exchange, $exchangeLabel, was shared with you.",
                data = mapOf("exchangeId" to exchangeId.toString()),
            )
        }
        exchangeNotificationDeliveryService.scheduleAfterCommit(
            exchangeId = exchangeId,
            exchangeStatus = exchange.status.name,
            emails = emails,
            inAppNotifications = inAppNotifications,
            refreshAppUserIds = notificationAppUserIds,
        )
    }
}
