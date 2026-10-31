package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.exception.InvalidEmailException
import com.docuhyphen.app.api.extension.normalizeEmailOrNull
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.entity.ExchangeRecipientType.EMAIL
import com.docuhyphen.app.api.model.entity.ExchangeRecipientType.GROUP
import com.docuhyphen.app.api.model.exchange.ExchangeResolvedParticipant
import com.docuhyphen.app.api.model.fields.FieldValueWriteCommand
import com.docuhyphen.app.api.model.fields.FieldsResourceRef
import com.docuhyphen.app.api.model.fields.SchemaAssignmentCommand
import com.docuhyphen.app.api.model.fields.SchemaAssignmentOperation
import com.docuhyphen.app.api.model.workflow.TriggerRequest
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.repository.user.AppUserRepository
import com.docuhyphen.app.api.resource.model.*
import com.docuhyphen.app.api.service.auth.AuthAuditService
import com.docuhyphen.app.api.service.auth.AuthRateLimitService
import com.docuhyphen.app.api.service.auth.AuthenticationService
import com.docuhyphen.app.api.service.auth.RevocationReasonCode
import com.docuhyphen.app.api.service.auth.authz.*
import com.docuhyphen.app.api.service.communication.EmailTemplateService
import com.docuhyphen.app.api.service.communication.OtpService
import com.docuhyphen.app.api.service.config.ConfigurationService
import com.docuhyphen.app.api.service.documentlibrary.DocumentLibraryService
import com.docuhyphen.app.api.service.identity.ExternalIdentityResolutionService
import com.docuhyphen.app.api.service.organization.OrganizationGroupService
import com.docuhyphen.app.api.service.organization.OrganizationService
import com.docuhyphen.app.api.service.storage.FileStorageService
import com.docuhyphen.app.api.service.user.AppUserService
import com.docuhyphen.app.api.service.variable.TemplateVariableInterpolator
import com.docuhyphen.app.api.service.variable.VariableResolutionContext
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant
import java.util.*
import com.docuhyphen.app.api.model.auth.authz.Decision as AuthorizationDecision

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
    private val documentContentHashService: DocumentContentHashService,
    private val documentThumbnailService: DocumentThumbnailService,
    private val schemaAssignmentService: com.docuhyphen.app.api.service.fields.SchemaAssignmentService,
    private val fieldsAccessContextFactory: com.docuhyphen.app.api.service.fields.FieldsAccessContextFactory,
    private val noAuthExchangeAccessTokenService: NoAuthExchangeAccessTokenService,
    private val exchangeNotificationDeliveryService: ExchangeNotificationDeliveryService,
    private val exchangeInitiationSubscriptionGuard: ExchangeInitiationSubscriptionGuard,
    private val blueprintInformationRequestStarter: ExchangeBlueprintInformationRequestStarter,
)
{
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    companion object
    {
        private val logger = LoggerFactory.getLogger(ExchangeInitiationService::class.java)
    }

    @Transactional
    fun initiateExchange(sessionInitiationDto: ExchangeInitiationDto): Exchange
    {
        val initiator = authTokenContext.authToken.appUser!!
        authorizeActiveOrganizationInitiation()
        val primarySelectionRequest = sessionInitiationDto.primaryRecipient
            ?: throw IllegalArgumentException("Primary recipient is required")

        exchangeInitiationSubscriptionGuard.enforceInitiation(
            initiatorId = initiator.id,
            activeOrganizationId = authTokenContext.activeOrganizationId,
            additionalParticipants = sessionInitiationDto.participants.size,
            recipientConstraintsJson = sessionInitiationDto.recipientConstraintsJson,
        )

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

        val activeOrganizationId = authTokenContext.activeOrganizationId
        val blueprintInformationRequest = blueprintInformationRequestStarter.prepare(
            blueprintDefinitionId = sessionInitiationDto.blueprintDefinitionId,
            initiatorId = initiator.id,
            activeOrganizationId = activeOrganizationId,
        )
        validateExchangeFields(
            initiator,
            sessionInitiationDto,
            documentsOptional = blueprintInformationRequest != null,
        )
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
            ExchangeResolvedParticipant(resolved, participant.role)
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

        val orgId0 = authTokenContext.activeOrganizationId
        val orgForInterpolation = orgId0?.let { organizationService.getOrganizationById(it) }
        val interpolationContext = VariableResolutionContext(
            user = initiator,
            organization = orgForInterpolation,
            timestamp = Instant.now(),
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
            if (orgId0 != null)
            {
                this.ownerOrganizationId = orgId0
            }
            else
            {
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
                this.type = null
                this.restrictedType = doc.restrictedType
                this.restrictType = doc.restrictType
                this.required = doc.required
            }
            exchange.documents.add(document)
            doc.libraryDocumentId?.let { libId -> libraryFilesToCopy.add(document to libId) }
        }

        val savedExchange = exchangeRepository.save(exchange)

        grantInitiatorOwnerShare(savedExchange, initiator)

        applyCreationTimeFields(savedExchange.id, sessionInitiationDto)

        blueprintInformationRequest?.let {
            val exchangePrincipals =
                (listOf(resolvedPrimary) + resolvedParticipants.map { participant -> participant.selection })
                    .mapNotNull { selection ->
                        (selection.group?.id ?: selection.appUser?.id)?.let { id ->
                            PrincipalRef(
                                selection.principalKind,
                                id
                            )
                        }
                    }
                    .toSet()
            blueprintInformationRequestStarter.start(it, savedExchange.id, initiator.id, exchangePrincipals)
        }

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
                    document.hash = documentContentHashService.sha256(libFile)
                    exchangeRepository.update(savedExchange)
                    documentThumbnailService.scheduleGeneration(document)
                }
            }
            catch (e: Exception)
            {
                logger.warn(
                    "Failed to pre-populate Exchange document {} from library entry {}: {}",
                    document.id,
                    libId,
                    e.message
                )
            }
        }

        val orgId = authTokenContext.activeOrganizationId
        val orgSettings = orgId?.let { organizationService.getOrganizationById(it) }?.settings

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

        val trustedRecipient = resolvedPrimary.selectionType == ExchangeRecipientSelectionType.TRUSTED_GROUP ||
                resolvedPrimary.selectionType == ExchangeRecipientSelectionType.TRUSTED_PERSON
        val requireAcceptance = trustedRecipient || (orgSettings?.requireRecipientAcceptance ?: true)
        val recipientNeedsApproval: Boolean

        if (draftApprovalPending)
        {
            recipientNeedsApproval = true
            logger.info(
                "Exchange {} has draft approval pending (workflow instance {}); share held PENDING_APPROVAL",
                savedExchange.id, draftResult.instanceId,
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
                        orgId.let { put("orgId", it.toString()) }
                    },
                    initiatedByAppUserId = initiator.id,
                )
            )
            recipientNeedsApproval = false
            logger.info("Exchange {} created with auto-accept (requireRecipientAcceptance=false)", savedExchange.id)
        }

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
            grantedBy = PrincipalRef.user(initiator.id),
            source = ShareSource.DIRECT,
            constraintsJson = sessionConstraintsJson(dto),
            status = if (pendingApproval) ShareStatus.PENDING_APPROVAL else ShareStatus.ACTIVE,
        )
    }


    private fun grantInitiatorOwnerShare(session: Exchange, initiator: AppUser)
    {
        shareService.grant(
            resourceType = ResourceType.EXCHANGE,
            resourceId = session.id,
            principalKind = PrincipalKind.USER,
            principalId = initiator.id,
            roleName = ExchangeShareRoleName.OWNER,
            grantedBy = PrincipalRef.user(initiator.id),
            source = ShareSource.DIRECT,
        )
    }

    private fun grantParticipantShares(
        session: Exchange,
        participants: List<ExchangeResolvedParticipant>,
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
                grantedBy = PrincipalRef.user(initiator.id),
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
                consumeTrustedPersonResolution(
                    prepared,
                    initiator.id,
                    authTokenContext.activeOrganizationId,
                    session.id
                )
                exchangeRecipientAttestationService.createPersonAttestation(recipient, prepared)
            }
        }
    }

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
            ?: SchemaAssignmentSource.MANUAL
        val resource = FieldsResourceRef(ResourceType.EXCHANGE.name, exchangeId)
        val access = fieldsAccessContextFactory.current()
        schemaAssignmentService.applySchemaAssignment(
            SchemaAssignmentCommand(
                resource = resource,
                access = access,
                operation = SchemaAssignmentOperation.ASSIGN,
                schemaDefinitionId = schemaDefinitionId,
                source = assignmentSource,
            ),
        )
        if (fieldValues.isNotEmpty())
            schemaAssignmentService.setValues(
                FieldValueWriteCommand(resource = resource, access = access, entries = fieldValues),
            )
    }

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
        sessionInitiationDto: ExchangeInitiationDto,
        documentsOptional: Boolean,
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

        if (!documentsOptional && sessionInitiationDto.exchangeDocuments.isNullOrEmpty())
        {
            throw IllegalArgumentException("Add at least one document")
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
        participants: List<ExchangeResolvedParticipant>,
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
                val recipientEmailAddr = recipientAppUser.email
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
                appUser.email.let { email ->
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
