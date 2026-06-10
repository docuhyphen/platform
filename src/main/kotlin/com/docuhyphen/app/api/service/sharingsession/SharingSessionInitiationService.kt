package com.docuhyphen.app.api.service.sharingsession

import com.docuhyphen.app.api.exception.InvalidEmailException
import com.docuhyphen.app.api.exception.AppUserNotFoundException
import com.docuhyphen.app.api.exception.OrganizationGroupNotFoundException
import com.docuhyphen.app.api.extension.normalizeEmailOrNull
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.entity.SharingSessionRecipientType.APP_USER
import com.docuhyphen.app.api.model.entity.SharingSessionRecipientType.EMAIL
import com.docuhyphen.app.api.model.entity.SharingSessionRecipientType.GROUP
import com.docuhyphen.app.api.repository.AppUserRepository
import com.docuhyphen.app.api.repository.SharingSessionRepository
import com.docuhyphen.app.api.resource.model.SharingSessionInitiationDto
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.auth.AuthAuditService
import com.docuhyphen.app.api.service.auth.AuthRateLimitService
import com.docuhyphen.app.api.service.auth.AuthenticationService
import com.docuhyphen.app.api.service.auth.RevocationReasonCode
import com.docuhyphen.app.api.service.communication.AppNotificationService
import com.docuhyphen.app.api.service.communication.EmailService
import com.docuhyphen.app.api.service.communication.EmailTemplateService
import com.docuhyphen.app.api.service.config.ConfigurationService
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
class SharingSessionInitiationService @Inject constructor(
    private val sharingSessionRepository: SharingSessionRepository,
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
    private val organizationSharingPolicyService: com.docuhyphen.app.api.service.organization.OrganizationSharingPolicyService,
    private val workflowEngineService: com.docuhyphen.app.api.service.workflow.WorkflowEngineService,
)
{
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    companion object
    {
        private val logger = LoggerFactory.getLogger(SharingSessionInitiationService::class.java)
    }

    @Transactional
    fun initiateSharingSession(sessionInitiationDto: SharingSessionInitiationDto): SharingSession
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

        validateSharingSessionFields(initiator, sessionInitiationDto)

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
            organizationSharingPolicyService.assertCanShareWithUser(
                initiatorAppUserId = initiator.id,
                recipientAppUserId = resolvedRecipient?.takeIf { it.isTemporary != true }?.id,
            )
        }

        // Participants as (principal kind, id) pairs, group participants fan out to members.
        val participantPrincipals: List<Pair<PrincipalKind, UUID>> = sessionInitiationDto.participants.map { p ->
            if (p.participantType == SharingSessionParticipantType.GROUP)
                PrincipalKind.PRINCIPAL_GROUP to UUID.fromString(p.id)
            else
                PrincipalKind.USER to UUID.fromString(p.id)
        }

        entityManager.detach(initiator)
        resolvedRecipient?.let { entityManager.detach(it) }

        val appUserRecipient = resolvedRecipient?.let { entityManager.merge(it) }

        val sharingSession = SharingSession().apply {
            this.initiator = entityManager.merge(initiator)
            this.sessionName = sessionInitiationDto.sessionName!!.trim()
            this.initialShareMessage = sessionInitiationDto.initialShareMessage?.trim()
            this.description = sessionInitiationDto.description?.trim()
            this.status = SharingSessionStatus.INITIATED
            this.createdDate = Timestamp.from(Instant.now())
            this.lastActivity = Timestamp.from(Instant.now())
            this.requireRecipientSignIn = sessionInitiationDto.requestRecipientSignIn == true
        }

        sessionInitiationDto.sessionDocuments?.forEach { doc ->
            val document = Document().apply {
                this.title = doc.title
                this.createdDate = Timestamp.from(Instant.now())
                this.updateDate = Timestamp.from(Instant.now())
                this.uploadDate = null
                this.isDeleted = false
                this.type = null // Type is set on the document upload method
                this.restrictedType = doc.restrictedType
            }
            sharingSession.documents.add(document)
        }

        val savedSharingSession = sharingSessionRepository.save(sharingSession)

        // Group-recipient sessions may require approval before the recipient share goes live.
        // Trigger the `session.approval_requested` workflow; if a matching active definition
        // exists (org-scoped, else the app-wide seed), the recipient share is created
        // PENDING_APPROVAL and only flipped to ACTIVE once the workflow emits `session.activated`
        // (see SessionApprovalEventHandler). Otherwise the share is ACTIVE immediately.
        val recipientNeedsApproval = recipientGroupId?.let {
            maybeTriggerGroupApproval(savedSharingSession, it, initiator)
        } ?: false

        // Recipients/participants/permissions live in the unified Share model, the initiator
        // gets an OWNER share, the recipient a role derived from the requested document
        // permissions, and each participant a PARTICIPANT share (groups fan out to members).
        grantInitiatorOwnerShare(savedSharingSession, initiator)
        grantRecipientShare(
            session = savedSharingSession,
            recipientType = sessionInitiationDto.recipientType!!,
            recipientAppUser = appUserRecipient,
            recipientGroupId = recipientGroupId,
            initiator = initiator,
            dto = sessionInitiationDto,
            pendingApproval = recipientNeedsApproval,
        )
        grantParticipantShares(savedSharingSession, participantPrincipals, initiator)

        sendNotifications(
            recipientType = sessionInitiationDto.recipientType!!,
            initiator = initiator,
            recipientAppUser = appUserRecipient,
            recipientGroupId = recipientGroupId,
            sharingSession = savedSharingSession,
        )

        logger.info("Sharing session initiated ID: ${sharingSession.id}")
        return savedSharingSession
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
        session: SharingSession,
        recipientType: SharingSessionRecipientType,
        recipientAppUser: AppUser?,
        recipientGroupId: UUID?,
        initiator: AppUser,
        dto: SharingSessionInitiationDto,
        pendingApproval: Boolean = false,
    )
    {
        val (principalKind, principalId) = when (recipientType)
        {
            GROUP -> recipientGroupId?.let { PrincipalKind.PRINCIPAL_GROUP to it } ?: return
            else -> recipientAppUser?.let { PrincipalKind.USER to it.id } ?: return
        }

        shareService.grant(
            resourceType = ResourceType.SHARING_SESSION,
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

    /**
     * Fire the `session.approval_requested` workflow for a group-recipient session. Returns true
     * when an approval workflow was actually started (the recipient share must then be created
     * PENDING_APPROVAL), false when no active definition matched (proceed un-gated).
     *
     * The subject snapshot carries everything the seeded `session-approval-in-group` workflow
     * references: `recipientGroupId` (whose MANAGERs approve), `orgId` (the group's owning org,
     * SLA escalation targets its ORG_ADMINs), and `initiatorId` (for outcome-event routing).
     */
    private fun maybeTriggerGroupApproval(
        session: SharingSession,
        recipientGroupId: UUID,
        initiator: AppUser,
    ): Boolean
    {
        val group = principalGroupRepository.findById(recipientGroupId)
        val orgId = group?.ownerOrganizationId
        val result = workflowEngineService.trigger(
            com.docuhyphen.app.api.service.workflow.TriggerRequest(
                triggerEvent = "session.approval_requested",
                subjectResourceType = ResourceType.SHARING_SESSION.name,
                subjectResourceId = session.id,
                organizationId = orgId,
                subjectData = buildMap {
                    put("recipientGroupId", recipientGroupId.toString())
                    put("initiatorId", initiator.id.toString())
                    orgId?.let { put("orgId", it.toString()) }
                },
                initiatedByAppUserId = initiator.id,
            )
        )
        if (result != null)
        {
            logger.info(
                "Session {} requires group approval (workflow instance {}); recipient share held PENDING_APPROVAL",
                session.id, result.instanceId,
            )
        }
        return result != null
    }

    /** Grant the initiator an OWNER [Share] (OWNER carries SESSION_OWNER / SESSION_SHARE). */
    private fun grantInitiatorOwnerShare(session: SharingSession, initiator: AppUser)
    {
        shareService.grant(
            resourceType = ResourceType.SHARING_SESSION,
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
        session: SharingSession,
        participants: List<Pair<PrincipalKind, UUID>>,
        initiator: AppUser,
    )
    {
        for ((principalKind, principalId) in participants)
        {
            shareService.grant(
                resourceType = ResourceType.SHARING_SESSION,
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
    private fun recipientRoleFor(dto: SharingSessionInitiationDto): RoleName
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
    private fun sessionConstraintsJson(dto: SharingSessionInitiationDto): String
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

    fun validateSharingSessionFields(
        initiator: AppUser,
        sessionInitiationDto: SharingSessionInitiationDto
    )
    {
        if (sessionInitiationDto.sessionName.isNullOrBlank())
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

        if (sessionInitiationDto.sessionDocuments.isNullOrEmpty())
        {
            throw IllegalArgumentException("Session documents cannot be empty")
        }

        sessionInitiationDto.participants.forEach {
            if (it.participantType == SharingSessionParticipantType.GROUP)
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
        recipientType: SharingSessionRecipientType,
        initiator: AppUser,
        recipientAppUser: AppUser?,
        recipientGroupId: UUID?,
        sharingSession: SharingSession,
    )
    {
        val initiatorName = initiator.person?.let { "${it.firstName} ${it.lastName}" } ?: initiator.email
        val documentTitles = sharingSession.documents.map { it.title }
        val sessionIdStr = sharingSession.id.toString()
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

        recipientEmails.forEach { (email, _) ->
            try
            {
                val body = emailTemplateService.renderSharingSessionCreatedRecipientEmail(
                    sessionId = sessionIdStr,
                    sessionName = sharingSession.sessionName.orEmpty(),
                    initiatorName = initiatorName,
                    initiatorOrganization = null,
                    sessionMessage = sharingSession.initialShareMessage,
                    documents = documentTitles,
                    requireSignIn = sharingSession.requireRecipientSignIn,
                )
                emailService.sendEmail(
                    to = email,
                    subject = "$subjectTitle | Document request from $initiatorName",
                    body = body,
                    useHtml = true,
                )
            }
            catch (e: Exception)
            {
                logger.error("Failed to send sharing-session recipient email to {}", email, e)
            }
        }

        if (initiator.settings?.notifyShareStart != false)
        {
            try
            {
                val body = emailTemplateService.renderSharingSessionCreatedInitiatorEmail(
                    sessionId = sessionIdStr,
                    sessionName = sharingSession.sessionName.orEmpty(),
                    recipientLabel = recipientLabel,
                    documents = documentTitles,
                )
                emailService.sendEmail(
                    to = initiator.email,
                    subject = "$subjectTitle | Document request sent",
                    body = body,
                    useHtml = true,
                )
            }
            catch (e: Exception)
            {
                logger.error("Failed to send sharing-session initiator email to {}", initiator.email, e)
            }
        }
    }
}