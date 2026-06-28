package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.exception.InvalidEmailException
import com.docuhyphen.app.api.exception.AppUserNotFoundException
import com.docuhyphen.app.api.exception.OrganizationGroupNotFoundException
import com.docuhyphen.app.api.extension.normalizeEmailOrNull
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.entity.ExchangeRecipientType.APP_USER
import com.docuhyphen.app.api.model.entity.ExchangeRecipientType.EMAIL
import com.docuhyphen.app.api.model.entity.ExchangeRecipientType.GROUP
import com.docuhyphen.app.api.repository.AppUserRepository
import com.docuhyphen.app.api.repository.ExchangeRepository
import com.docuhyphen.app.api.resource.model.ExchangeInitiationDto
import com.docuhyphen.app.api.repository.OrganizationRepository
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.auth.AuthAuditService
import com.docuhyphen.app.api.service.auth.AuthRateLimitService
import com.docuhyphen.app.api.service.auth.AuthenticationService
import com.docuhyphen.app.api.service.auth.RevocationReasonCode
import com.docuhyphen.app.api.service.communication.AppNotificationService
import com.docuhyphen.app.api.service.communication.EmailService
import com.docuhyphen.app.api.service.communication.EmailTemplateService
import com.docuhyphen.app.api.service.config.ConfigurationService
import com.docuhyphen.app.api.service.organization.OrganizationMembershipService
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
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@ApplicationScoped
class ExchangeInitiationService @Inject constructor(
    private val exchangeRepository: ExchangeRepository,
    private val appUserRepository: AppUserRepository,
    private val appUserService: AppUserService,
    private val emailService: EmailService,
    private val emailTemplateService: EmailTemplateService,
    private val authTokenContext: AuthTokenContext,
    private val authenticationService: AuthenticationService,
    private val authRateLimitService: AuthRateLimitService,
    private val configurationService: ConfigurationService,
    private val authAuditService: AuthAuditService,
    private val appNotificationService: AppNotificationService,
    private val shareService: ShareService,
    private val principalGroupRepository: com.docuhyphen.app.api.repository.PrincipalGroupRepository,
    private val principalGroupMemberRepository: com.docuhyphen.app.api.repository.PrincipalGroupMemberRepository,
    private val organizationExchangePolicyService: com.docuhyphen.app.api.service.organization.OrganizationExchangePolicyService,
    private val workflowEngineService: com.docuhyphen.app.api.service.workflow.WorkflowEngineService,
    private val organizationMembershipService: OrganizationMembershipService,
    private val organizationRepository: OrganizationRepository,
    private val templateVariableInterpolator: TemplateVariableInterpolator,
    private val documentLibraryService: DocumentLibraryService,
    private val fileStorageService: FileStorageService,
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

        if (sessionInitiationDto.recipientType == EMAIL)
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

        val recipientGroupId: UUID? =
            if (sessionInitiationDto.recipientType == GROUP)
                UUID.fromString(sessionInitiationDto.recipientOrgGroupId!!)
            else null

        val resolvedRecipient: AppUser? = when (sessionInitiationDto.recipientType)
        {
            EMAIL ->
                appUserService.getAppUserByEmail(sessionInitiationDto.recipientEmail!!)
                    ?: AppUser().apply {
                        isTemporary = true
                        email = sessionInitiationDto.recipientEmail!!
                        isActive = false
                    }

            APP_USER ->
                appUserService.getById(UUID.fromString(sessionInitiationDto.recipientAppUserId))!!

            GROUP -> null

            else ->
                throw IllegalArgumentException("Unsupported recipient type")
        }

        // Org sharing policy: a closed org (allowShareWithoutPairing = false) may only share with
        // its own members or members of a paired org. GROUP recipients are org-internal groups and
        // are governed by group membership, so the gate applies to direct USER/EMAIL recipients.
        if (sessionInitiationDto.recipientType == EMAIL || sessionInitiationDto.recipientType == APP_USER)
        {
            organizationExchangePolicyService.assertCanShareWithUser(
                initiatorAppUserId = initiator.id,
                recipientAppUserId = resolvedRecipient?.takeIf { it.isTemporary != true }?.id,
            )
        }

        // Participants as (principal kind, id) pairs, group participants fan out to members.
        val participantPrincipals: List<Pair<PrincipalKind, UUID>> = sessionInitiationDto.participants.map { p ->
            if (p.participantType == ExchangeParticipantType.GROUP)
                PrincipalKind.PRINCIPAL_GROUP to UUID.fromString(p.id)
            else
                PrincipalKind.USER to UUID.fromString(p.id)
        }

        entityManager.detach(initiator)
        resolvedRecipient?.let { entityManager.detach(it) }

        val appUserRecipient = resolvedRecipient?.let { entityManager.merge(it) }

        // Interpolate template variables in all string fields before entity creation.
        val orgId0 = when (sessionInitiationDto.recipientType)
        {
            GROUP -> recipientGroupId?.let { principalGroupRepository.findById(it) }?.ownerOrganizationId
            else -> organizationMembershipService.primaryOrganizationId(initiator.id)
        }
        val orgForInterpolation = orgId0?.let { organizationRepository.findById(it) }
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
            this.requireRecipientSignIn = sessionInitiationDto.requestRecipientSignIn == true
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

        // Resolve the initiator's org context once; used for both workflow triggers
        // and the group-specific manager access grant below.
        val orgId: UUID? = when (sessionInitiationDto.recipientType)
        {
            GROUP -> recipientGroupId?.let { principalGroupRepository.findById(it) }?.ownerOrganizationId
            else -> organizationMembershipService.primaryOrganizationId(initiator.id)
        }
        val orgSettings = orgId?.let { organizationRepository.findById(it) }?.settings

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
        val requireAcceptance = orgSettings?.requireRecipientAcceptance ?: true
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
                put("recipientType", sessionInitiationDto.recipientType!!.name)
                put("exchangeName", savedExchange.name ?: "")
                initiator.person?.let { p ->
                    listOfNotNull(p.firstName, p.lastName)
                        .joinToString(" ")
                        .takeIf { it.isNotBlank() }
                        ?.let { put("initiatorName", it) }
                }
                orgId?.let { put("orgId", it.toString()) }
                when (sessionInitiationDto.recipientType)
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

            recipientNeedsApproval = acceptanceResult != null
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

        // Recipients/participants/permissions live in the unified Share model. The initiator
        // gets an OWNER share, the recipient a role derived from the requested document
        // permissions, and each participant a PARTICIPANT share (groups fan out to members).
        grantInitiatorOwnerShare(savedExchange, initiator)
        grantRecipientShare(
            session = savedExchange,
            recipientType = sessionInitiationDto.recipientType!!,
            recipientAppUser = appUserRecipient,
            recipientGroupId = recipientGroupId,
            initiator = initiator,
            dto = sessionInitiationDto,
            pendingApproval = recipientNeedsApproval,
        )
        // For GROUP recipients that need approval: give group OWNERs and MANAGERs an active
        // REVIEWER share so they can see the draft and act on the acceptance workflow step.
        if (recipientNeedsApproval && recipientGroupId != null)
        {
            grantGroupManagerViewerAccess(savedExchange, recipientGroupId, initiator)
        }
        grantParticipantShares(savedExchange, participantPrincipals, initiator)

        sendNotifications(
            recipientType = sessionInitiationDto.recipientType!!,
            initiator = initiator,
            recipientAppUser = appUserRecipient,
            recipientGroupId = recipientGroupId,
            exchange = savedExchange,
            pendingApproval = recipientNeedsApproval,
        )

        logger.info("Sharing Exchange Initiated ID: ${exchange.id}")
        return savedExchange
    }

    /**
     * When a group-recipient exchange requires approval, grant each group OWNER/MANAGER an
     * active REVIEWER share so they can see the draft exchange in their list and act on the
     * approval workflow step assigned to them. Without this the group's share (and all
     * inherited member shares) sits at PENDING_APPROVAL status, which the ACCESSIBLE predicate
     * does not match, leaving the exchange invisible to the approvers and stuck in draft.
     *
     * Regular group MEMBER/OBSERVER principals are intentionally excluded: they only get access
     * once the approval completes and the group's share is activated.
     */
    private fun grantGroupManagerViewerAccess(
        session: Exchange,
        recipientGroupId: UUID,
        initiator: AppUser,
    )
    {
        principalGroupMemberRepository.findActiveMembers(recipientGroupId)
            .filter { it.principalKind == PrincipalKind.USER }
            .filter { it.groupRole == GroupRole.MANAGER || it.groupRole == GroupRole.OWNER }
            .forEach { member ->
                shareService.grant(
                    resourceType = ResourceType.EXCHANGE,
                    resourceId = session.id,
                    principalKind = PrincipalKind.USER,
                    principalId = member.principalId,
                    roleName = RoleName.REVIEWER,
                    grantedByAppUserId = initiator.id,
                    source = ShareSource.DIRECT,
                )
            }
    }

    /**
     * Dual-write: mirror the session's legacy recipient into a unified [Share] row so the new
     * authorization model stays in sync. The legacy recipient columns remain authoritative for
     * reads until cutover. No-op when dual-write is disabled.
     *
     * A GROUP recipient becomes a `PRINCIPAL_GROUP` share, which [ShareService] fans out into
     * `INHERITED_FROM_GROUP` rows per member. USER / EMAIL recipients (the latter already
     * materialised as a temporary [AppUser] by the legacy flow) become `USER` shares.
     *
     * Participants are not mirrored here yet, that is a separate follow-up slice.
     */
    private fun grantRecipientShare(
        session: Exchange,
        recipientType: ExchangeRecipientType,
        recipientAppUser: AppUser?,
        recipientGroupId: UUID?,
        initiator: AppUser,
        dto: ExchangeInitiationDto,
        pendingApproval: Boolean = false,
    )
    {
        val (principalKind, principalId) = when (recipientType)
        {
            GROUP -> recipientGroupId?.let { PrincipalKind.PRINCIPAL_GROUP to it } ?: return
            else -> recipientAppUser?.let { PrincipalKind.USER to it.id } ?: return
        }

        shareService.grant(
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
            roleName = RoleName.OWNER,
            grantedByAppUserId = initiator.id,
            source = ShareSource.DIRECT,
        )
    }

    /**
     * Grant each participant a `PARTICIPANT`-role [Share]. A group participant becomes a
     * `PRINCIPAL_GROUP` share (fanned out to members by [ShareService]); an individual a `USER` share.
     */
    private fun grantParticipantShares(
        session: Exchange,
        participants: List<Pair<PrincipalKind, UUID>>,
        initiator: AppUser,
    )
    {
        for ((principalKind, principalId) in participants)
        {
            shareService.grant(
                resourceType = ResourceType.EXCHANGE,
                resourceId = session.id,
                principalKind = principalKind,
                principalId = principalId,
                roleName = RoleName.PARTICIPANT,
                grantedByAppUserId = initiator.id,
                source = ShareSource.DIRECT,
            )
        }
    }

    /**
     * Maps the requested document permissions to a resource role: any write-style permission
     * (add/delete/update/upload) implies EDITOR, otherwise VIEWER. The full flag set is
     * preserved verbatim in the share's constraints JSON (see [sessionConstraintsJson]).
     *
     * When the caller supplies an explicit `recipientRoleName`, honor it (after
     * validating it's a known [RoleName]). Lets the UI offer PARTICIPANT / VIEWER /
     * COMMENTER / SIGNER / REVIEWER at initiation, not just EDITOR/VIEWER.
     */
    private fun recipientRoleFor(dto: ExchangeInitiationDto): RoleName
    {
        dto.recipientRoleName?.trim()?.takeIf { it.isNotBlank() }?.let { explicit ->
            runCatching { RoleName.valueOf(explicit.uppercase()) }.getOrNull()?.let { return it }
            logger.warn("Ignoring unknown recipientRoleName='{}' on session initiation", explicit)
        }
        val canWrite = dto.allowDocumentAddition == true || dto.allowDocumentDeletion == true ||
            dto.allowDocumentUpdate == true || dto.allowDocumentUpload == true
        return if (canWrite) RoleName.EDITOR else RoleName.VIEWER
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

        when (sessionInitiationDto.recipientType)
        {
            EMAIL ->
            {
                if ((sessionInitiationDto.recipientEmail?.trim()?.length ?: 0) < 5)
                {
                    throw InvalidEmailException("Recipient email is invalid")
                }

                if (initiator.email.normalizeEmailOrNull() == sessionInitiationDto.recipientEmail.normalizeEmailOrNull())
                {
                    throw IllegalArgumentException("Recipient and Initiator cannot be the same")
                }

                sessionInitiationDto.recipientEmail?.let {
                    if (authenticationService.isEmailInvalid(it))
                    {
                        throw InvalidEmailException("Recipient email is invalid")
                    }
                } ?: throw InvalidEmailException("Recipient email is required")
            }

            APP_USER ->
            {
                sessionInitiationDto.recipientAppUserId?.let {

                    val recipientId = UUID.fromString(it)
                    if (recipientId == initiator.id)
                    {
                        throw IllegalArgumentException("Recipient and Initiator cannot be the same")
                    }
                    appUserService.getById(recipientId)
                        ?: throw AppUserNotFoundException("Recipient not found")

                } ?: throw AppUserNotFoundException("Recipient not found")
            }

            GROUP ->
            {
                sessionInitiationDto.recipientOrgGroupId?.let {
                    principalGroupRepository.findById(UUID.fromString(it))
                        ?: throw OrganizationGroupNotFoundException("Recipient group not found")
                } ?: throw OrganizationGroupNotFoundException("Recipient group not found")
            }

            else ->
                throw IllegalArgumentException("Unsupported recipient type")
        }

        if (sessionInitiationDto.exchangeDocuments.isNullOrEmpty())
        {
            throw IllegalArgumentException("Session documents cannot be empty")
        }

        sessionInitiationDto.participants.forEach {
            if (it.participantType == ExchangeParticipantType.GROUP)
            {
                return@forEach
            }

            val participantId = runCatching { UUID.fromString(it.id) }.getOrNull()
                ?: throw IllegalArgumentException("Participant id is invalid")

            if (participantId == initiator.id)
            {
                throw IllegalArgumentException("Initiator cannot be added as a participant")
            }
        }
    }

    fun sendNotifications(
        recipientType: ExchangeRecipientType,
        initiator: AppUser,
        recipientAppUser: AppUser?,
        recipientGroupId: UUID?,
        exchange: Exchange,
        pendingApproval: Boolean = false,
    )
    {
        val initiatorName = initiator.person?.let { "${it.firstName} ${it.lastName}" } ?: initiator.email
        val documentTitles = exchange.documents.map { it.title }
        val exchangeIdStr = exchange.id.toString()
        val subjectTitle = configurationService.emailSubjectTitle

        val recipientEmails: List<Pair<String, String>> = when (recipientType)
        {
            GROUP -> recipientGroupId
                ?.let { groupId ->
                    principalGroupMemberRepository.findActiveMembers(groupId)
                        .filter { it.principalKind == PrincipalKind.USER }
                        .mapNotNull { appUserService.getById(it.principalId) }
                }
                ?.filter { it.id != initiator.id }
                ?.filter { it.settings?.notifyShareStart != false }
                ?.map { it.email to (it.person?.firstName ?: "there") }
                ?: emptyList()
            else -> recipientAppUser
                ?.takeIf { !it.email.isNullOrBlank() && it.settings?.notifyShareStart != false }
                ?.let { listOf(it.email to (it.person?.firstName ?: "there")) }
                ?: emptyList()
        }

        val recipientLabel = when (recipientType)
        {
            GROUP -> recipientGroupId?.let { principalGroupRepository.findById(it)?.name }?.let { "Group: $it" } ?: "Group"
            else -> recipientAppUser?.email ?: "Recipient"
        }

        val requireSignInForRecipient = recipientType == EMAIL && exchange.requireRecipientSignIn

        // Do not notify the recipient while the exchange is awaiting approval — the approval
        // workflow's own NOTIFICATION step (or a post-approval trigger) should deliver that.
        if (!pendingApproval)
        {
            recipientEmails.forEach { (email, _) ->
                try
                {
                    val body = emailTemplateService.renderExchangeCreatedRecipientEmail(
                        exchangeId = exchangeIdStr,
                        name = exchange.name.orEmpty(),
                        initiatorName = initiatorName,
                        initiatorOrganization = null,
                        sessionMessage = exchange.initialShareMessage,
                        documents = documentTitles,
                        requireSignIn = requireSignInForRecipient,
                    )
                    emailService.sendEmail(
                        to = email,
                        subject = "$subjectTitle | Exchange request from $initiatorName",
                        body = body,
                        useHtml = true,
                    )
                }
                catch (e: Exception)
                {
                    logger.error("Failed to send exchange recipient email to {}", email, e)
                }
            }
        }

        if (initiator.settings?.notifyShareStart != false)
        {
            try
            {
                val body = emailTemplateService.renderExchangeCreatedInitiatorEmail(
                    exchangeId = exchangeIdStr,
                    name = exchange.name.orEmpty(),
                    recipientLabel = recipientLabel,
                    documents = documentTitles,
                )
                emailService.sendEmail(
                    to = initiator.email,
                    subject = "$subjectTitle | Exchange request sent",
                    body = body,
                    useHtml = true,
                )
            }
            catch (e: Exception)
            {
                logger.error("Failed to send exchange initiator email to {}", initiator.email, e)
            }
        }
    }

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

        val emails: List<String> = when
        {
            recipientGroupId != null ->
                principalGroupMemberRepository.findActiveMembers(recipientGroupId)
                    .filter { it.principalKind == PrincipalKind.USER }
                    .mapNotNull { appUserService.getById(it.principalId) }
                    .filter { it.id != initiator.id && it.settings?.notifyShareStart != false }
                    .mapNotNull { it.email }

            recipientUserId != null ->
                appUserService.getById(recipientUserId)
                    ?.takeIf { it.settings?.notifyShareStart != false }
                    ?.email
                    ?.let { listOf(it) }
                    ?: emptyList()

            else -> emptyList()
        }

        emails.forEach { email ->
            try
            {
                val body = emailTemplateService.renderExchangeCreatedRecipientEmail(
                    exchangeId = exchangeId.toString(),
                    name = exchange.name.orEmpty(),
                    initiatorName = initiatorName,
                    initiatorOrganization = null,
                    sessionMessage = exchange.initialShareMessage,
                    documents = documentTitles,
                    requireSignIn = false,
                )
                emailService.sendEmail(
                    to = email,
                    subject = "$subjectTitle | Exchange request from $initiatorName",
                    body = body,
                    useHtml = true,
                )
            }
            catch (e: Exception)
            {
                logger.error("Failed to send post-activation invite email to {}", email, e)
            }
        }
    }
}