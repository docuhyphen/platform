package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.extension.normalizeEmailOrNull
import com.docuhyphen.app.api.exception.ExchangeNotFoundException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.dto.SessionAccessEntryDto
import com.docuhyphen.app.api.model.entity.ExternalParticipant
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.entity.ExchangeShareRoleName
import com.docuhyphen.app.api.model.entity.Share
import com.docuhyphen.app.api.model.entity.ShareSource
import com.docuhyphen.app.api.model.entity.ShareStatus
import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.model.entity.ExchangeRecipientAcceptanceStatus
import com.docuhyphen.app.api.model.entity.ExchangeRecipientPurpose
import com.docuhyphen.app.api.model.entity.ExchangeRecipientSelectionType
import com.docuhyphen.app.api.model.entity.PrincipalGroupScope
import com.docuhyphen.app.api.resource.model.ExchangeRecipientSelectionRequest
import com.docuhyphen.app.api.resource.model.TrustedGroupRecipientSelectionRequest
import com.docuhyphen.app.api.resource.model.TrustedPersonRecipientSelectionRequest
import com.docuhyphen.app.api.service.organization.ExternalIdentityResolutionService
import com.docuhyphen.app.api.repository.ExternalParticipantRepository
import com.docuhyphen.app.api.repository.ShareRepository
import com.docuhyphen.app.api.repository.ExchangeRepository
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.communication.EmailTemplateService
import com.docuhyphen.app.api.service.config.ConfigurationService
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.auth.authz.ShareConstraints
import com.docuhyphen.app.api.service.organization.OrganizationExchangePolicyService
import com.docuhyphen.app.api.service.organization.OrganizationGroupService
import com.docuhyphen.app.api.service.audit.AuditCaptureFailedException
import com.docuhyphen.app.api.service.audit.AuditDraftInvalidException
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditOwnerScope
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.util.UUID

/**
 * Manage-access write API over the unified [com.docuhyphen.app.api.model.entity.Share] model
 * Grants, changes, and revokes access to an Exchange.
 *
 * Guarding: for now only the session **initiator** (owner) may change access. This is the
 * pragmatic guard until [com.docuhyphen.app.api.service.auth.authz.AuthorizationService] is
 * wired with full org-context (the `EXCHANGE_MANAGE_ACCESS` capability needs `AuthorizationContext`,
 * which lands with the JWT active-org work). The check is centralised in [requireSessionOwner]
 * so it can be swapped for the capability check in one place.
 */
@ApplicationScoped
class ExchangeAccessManagementService @Inject constructor(
    private val exchangeRepository: ExchangeRepository,
    private val shareRepository: ShareRepository,
    private val shareService: ShareService,
    private val shareQueryService: ShareQueryService,
    private val appUserService: AppUserService,
    private val externalParticipantRepository: ExternalParticipantRepository,
    private val organizationGroupService: OrganizationGroupService,
    private val exchangeRecipientService: ExchangeRecipientService,
    private val exchangeRecipientSelectionResolver: ExchangeRecipientSelectionResolver,
    private val exchangeRecipientAttestationService: ExchangeRecipientAttestationService,
    private val externalIdentityResolutionService: ExternalIdentityResolutionService,
    private val authTokenContext: AuthTokenContext,
    private val authorizationService: AuthorizationService,
    private val authorizationContextFactory: AuthorizationContextFactory,
    private val organizationExchangePolicyService: OrganizationExchangePolicyService,
    private val emailTemplateService: EmailTemplateService,
    private val configurationService: ConfigurationService,
    private val auditRecorder: AuditRecorder,
    private val exchangeNotificationDeliveryService: ExchangeNotificationDeliveryService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(ExchangeAccessManagementService::class.java)
    }

    @Transactional
    fun grantAccess(
        exchangeId: UUID,
        principalKind: String,
        principalId: String,
        roleName: ExchangeShareRoleName,
        constraintsJson: String? = null,
        expiresAtEpochMillis: Long? = null,
    )
    {
        val session = requireSessionOwnerAndReturn(exchangeId)
        val senderOrganizationId = manageAccessSenderOrganization(session, requireOrganization = false)

        val requestedKind = parsePrincipalKind(principalKind)
        requireAssignableRole(roleName)

        // Prevent the caller from granting themselves a share (they already have OWNER).
        val callerAppUserId = authTokenContext.authToken.appUser?.id
        if (requestedKind == PrincipalKind.USER && principalId.trim() == callerAppUserId?.toString())
        {
            throw IllegalArgumentException("You cannot add yourself to a session you own")
        }
        // Also prevent adding by email if it resolves to the caller.
        val normalizedGrantEmail = principalId.trim().normalizeEmailOrNull()
        if (normalizedGrantEmail != null && callerAppUserId != null)
        {
            val callerEmail = authTokenContext.authToken.appUser?.email
            if (normalizedGrantEmail.equals(callerEmail, ignoreCase = true))
            {
                throw IllegalArgumentException("You cannot add yourself to a session you own")
            }
        }

        val (kind, principalUuid) = resolvePrincipal(requestedKind, principalId)
        val actorId = callerAppUserId
            ?: throw ForbiddenException("A user account is required to manage access")
        enforceSharingPolicy(senderOrganizationId, actorId, kind, principalUuid)
        // Reject malformed/contradictory constraints up front and store a canonical form.
        val normalizedConstraints = ShareConstraints.normalizeForStorage(constraintsJson)

        val directShare = shareService.grant(
            resourceType = ResourceType.EXCHANGE,
            resourceId = exchangeId,
            principalKind = kind,
            principalId = principalUuid,
            roleName = roleName,
            grantedByAppUserId = callerAppUserId,
            constraintsJson = normalizedConstraints,
            expiresAt = expiresAtEpochMillis?.let { Timestamp(it) },
            resourceLabel = session.name,
        )
        val binding = recipientBinding(session, kind, principalUuid)
        exchangeRecipientService.createBinding(
            exchangeId = exchangeId,
            directShare = directShare,
            purpose = ExchangeRecipientPurpose.PARTICIPANT,
            selectionType = binding.selectionType,
            targetOrganizationId = binding.targetOrganizationId,
            acceptanceStatus = ExchangeRecipientAcceptanceStatus.NOT_REQUIRED,
        )

        scheduleAccessGrantedNotification(
            session = session,
            kind = kind,
            principalUuid = principalUuid,
            rawPrincipalId = principalId.trim(),
            trustedInvitation = false,
        )
    }

    /**
     * Adds a verified Trusted Organization person or published group as an additional participant.
     * The direct Share remains inactive until that participant independently accepts the attested
     * invitation.
     */
    @Transactional
    fun inviteTrustedParticipant(
        exchangeId: UUID,
        selection: ExchangeRecipientSelectionRequest,
        roleName: ExchangeShareRoleName,
        constraintsJson: String? = null,
        expiresAtEpochMillis: Long? = null,
    ): List<SessionAccessEntryDto>
    {
        val session = requireSessionOwnerAndReturn(exchangeId)
        val senderOrganizationId = requireNotNull(
            manageAccessSenderOrganization(session, requireOrganization = true),
        )
        requireAssignableRole(roleName)
        val caller = authTokenContext.authToken.appUser
            ?: throw ForbiddenException("A user account is required to manage access")
        val resolved = resolveTrustedSelection(selection, caller, senderOrganizationId)
        require(!(resolved.principalKind == PrincipalKind.USER && resolved.principalId == caller.id)) {
            "You cannot add yourself to an Exchange you own"
        }

        val normalizedConstraints = ShareConstraints.normalizeForStorage(constraintsJson)
        val participantShare = shareService.grant(
            resourceType = ResourceType.EXCHANGE,
            resourceId = exchangeId,
            principalKind = resolved.principalKind,
            principalId = resolved.principalId,
            roleName = roleName,
            grantedByAppUserId = caller.id,
            source = ShareSource.DIRECT,
            constraintsJson = normalizedConstraints,
            expiresAt = expiresAtEpochMillis?.let { Timestamp(it) },
            status = ShareStatus.PENDING_APPROVAL,
            resourceLabel = session.name,
        )
        val participant = exchangeRecipientService.createBinding(
            exchangeId = exchangeId,
            directShare = participantShare,
            purpose = ExchangeRecipientPurpose.PARTICIPANT,
            selectionType = resolved.selectionType,
            targetOrganizationId = resolved.targetOrganizationId,
            acceptanceStatus = ExchangeRecipientAcceptanceStatus.PENDING,
        )
        resolved.trustedGroupValidation?.let { validation ->
            exchangeRecipientAttestationService.createGroupAttestation(participant, validation)
        }
        resolved.preparedPersonResolution?.let { prepared ->
            externalIdentityResolutionService.consumeForExchange(
                resolutionId = prepared.resolution.id,
                actorAppUserId = caller.id,
                callerOrganizationId = senderOrganizationId,
                targetOrganizationId = prepared.resolution.targetOrganizationId,
                exchangeId = exchangeId,
            )
            exchangeRecipientAttestationService.createPersonAttestation(participant, prepared)
        }
        scheduleAccessGrantedNotification(
            session = session,
            kind = resolved.principalKind,
            principalUuid = resolved.principalId,
            rawPrincipalId = resolved.appUser?.email.orEmpty(),
            trustedInvitation = true,
        )
        return shareQueryService.getSessionAccessView(exchangeId)
    }

    @Transactional
    fun changeRole(exchangeId: UUID, shareId: UUID, roleName: ExchangeShareRoleName, constraintsJson: String? = null)
    {
        val session = requireSessionOwnerAndReturn(exchangeId)
        requireAssignableRole(roleName)
        requireMutableAccessShare(exchangeId, shareId)
        val hasConstraintsPayload = constraintsJson != null
        val normalizedConstraints = if (hasConstraintsPayload) ShareConstraints.normalizeForStorage(constraintsJson) else null
        shareService.updateRoleAndConstraints(
            shareId = shareId,
            roleName = roleName,
            constraintsJson = normalizedConstraints,
            applyConstraints = hasConstraintsPayload,
            resourceLabel = session.name,
        )
    }

    @Transactional
    fun revokeAccess(exchangeId: UUID, shareId: UUID)
    {
        val session = requireSessionOwnerAndReturn(exchangeId)
        requireMutableAccessShare(exchangeId, shareId)
        shareService.revoke(shareId, authTokenContext.authToken.appUser?.id, resourceLabel = session.name)
    }

    /**
     * Replaces the pending primary recipient of a draft Exchange with a freshly resolved selection.
     * This lets the Exchange owner recover an invitation whose trusted verification, membership,
     * relationship, or policy is no longer valid. The old primary Share is revoked (cascading to any
     * inherited group-member Shares) and its recipient binding and attestation are removed, then a
     * new pending primary Share, binding, and attestation are created in the same transaction. A
     * trusted selection revalidates through the recipient resolver and always requires sign-in and
     * acceptance. Other recipient paths have separate invitation contracts and cannot use this
     * trusted-recipient recovery operation.
     */
    @Transactional
    fun replacePrimaryRecipient(
        exchangeId: UUID,
        selection: ExchangeRecipientSelectionRequest,
    ): List<SessionAccessEntryDto>
    {
        val session = requireSessionOwnerAndReturn(exchangeId)
        val senderOrganizationId = requireNotNull(
            manageAccessSenderOrganization(session, requireOrganization = true),
        )

        if (session.status != ExchangeStatus.INITIATED)
        {
            throw IllegalArgumentException(
                "The primary recipient can be replaced only while the Exchange is a draft awaiting acceptance",
            )
        }
        if (selection !is TrustedPersonRecipientSelectionRequest &&
            selection !is TrustedGroupRecipientSelectionRequest)
        {
            throw IllegalArgumentException(
                "A primary recipient can be replaced only with a verified member or published group from a Trusted Organization",
            )
        }

        val currentPrimary = exchangeRecipientService.findPrimary(exchangeId)
            ?: throw IllegalArgumentException("The Exchange has no primary recipient to replace")
        require(currentPrimary.acceptanceStatus == ExchangeRecipientAcceptanceStatus.PENDING) {
            "Only a pending primary recipient can be replaced"
        }

        val caller = authTokenContext.authToken.appUser
            ?: throw ForbiddenException("A user account is required to manage access")

        val resolved = resolveTrustedSelection(selection, caller, senderOrganizationId)

        val previousShare = shareService.getById(currentPrimary.directShareId)
            ?: throw IllegalArgumentException("The current primary recipient Share was not found")
        val preservedRole = previousShare.roleName
        val preservedConstraints = previousShare.constraintsJson

        // Remove the old binding and attestation first so the single-primary constraint is free,
        // then revoke the old Share, which cascades to any inherited group-member Shares.
        exchangeRecipientService.deleteBinding(currentPrimary)
        shareService.revoke(previousShare.id, caller.id, resourceLabel = session.name)

        // Trusted selections always require sign-in and hold their Share until the attested recipient accepts.
        if (!session.requireRecipientSignIn)
        {
            session.requireRecipientSignIn = true
            exchangeRepository.update(session)
        }

        val newShare = shareService.grant(
            resourceType = ResourceType.EXCHANGE,
            resourceId = exchangeId,
            principalKind = resolved.principalKind,
            principalId = resolved.principalId,
            roleName = preservedRole,
            grantedByAppUserId = caller.id,
            source = ShareSource.DIRECT,
            constraintsJson = preservedConstraints,
            status = ShareStatus.PENDING_APPROVAL,
            resourceLabel = session.name,
        )

        exchangeRecipientService.findByDirectShareId(newShare.id)?.let { existingBinding ->
            require(
                existingBinding.exchangeId == exchangeId &&
                    existingBinding.purpose == ExchangeRecipientPurpose.PARTICIPANT,
            ) {
                "Replacement recipient has an incompatible Exchange binding"
            }
            exchangeRecipientService.deleteBinding(existingBinding)
        }

        val newPrimary = exchangeRecipientService.createBinding(
            exchangeId = exchangeId,
            directShare = newShare,
            purpose = ExchangeRecipientPurpose.PRIMARY,
            selectionType = resolved.selectionType,
            targetOrganizationId = resolved.targetOrganizationId,
            acceptanceStatus = ExchangeRecipientAcceptanceStatus.PENDING,
        )
        resolved.trustedGroupValidation?.let { validation ->
            exchangeRecipientAttestationService.createGroupAttestation(newPrimary, validation)
        }
        resolved.preparedPersonResolution?.let { prepared ->
            externalIdentityResolutionService.consumeForExchange(
                resolutionId = prepared.resolution.id,
                actorAppUserId = caller.id,
                callerOrganizationId = senderOrganizationId,
                targetOrganizationId = prepared.resolution.targetOrganizationId,
                exchangeId = exchangeId,
            )
            exchangeRecipientAttestationService.createPersonAttestation(newPrimary, prepared)
        }

        scheduleAccessGrantedNotification(
            session = session,
            kind = resolved.principalKind,
            principalUuid = resolved.principalId,
            rawPrincipalId = resolved.appUser?.email.orEmpty(),
            trustedInvitation = true,
        )

        return shareQueryService.getSessionAccessView(exchangeId)
    }

    fun getSessionAccessView(exchangeId: UUID): List<SessionAccessEntryDto>
    {
        val principal = authorizationContextFactory.currentPrincipal()
            ?: throw ExchangeNotFoundException("Exchange not found")
        val session = loadSessionOrThrow(exchangeId)
        val decision = authorizationService.authorize(
            principal = principal,
            action = Action.EXCHANGE_MANAGE_ACCESS,
            resource = ResourceRef.exchange(exchangeId),
            context = authorizationContextFactory.currentContext(),
        )
        if (decision is Decision.Deny)
        {
            recordAuthorizationDenied(exchangeId, principal.id, Action.EXCHANGE_MANAGE_ACCESS.name, session.ownerOrganizationId, session.name)
            throw ExchangeNotFoundException("Exchange not found")
        }
        return shareQueryService.getSessionAccessView(exchangeId)
    }

    fun assertCanManageAccess(exchangeId: UUID)
    {
        requireSessionOwner(exchangeId)
    }

    // -------------------------------------------------------------------------

    private fun scheduleAccessGrantedNotification(
        session: Exchange,
        kind: PrincipalKind,
        principalUuid: UUID,
        rawPrincipalId: String,
        trustedInvitation: Boolean,
    )
    {
        val appUsers = when (kind)
        {
            PrincipalKind.USER -> listOfNotNull(appUserService.getById(principalUuid))
            PrincipalKind.PRINCIPAL_GROUP ->
                (if (trustedInvitation)
                    organizationGroupService.activeOwnerOrManagerUserIds(principalUuid)
                else
                    organizationGroupService.activeUserIds(principalUuid))
                    .mapNotNull(appUserService::getById)
            else -> emptyList()
        }
        val externalEmail: String? = when (kind)
        {
            PrincipalKind.PARTICIPANT -> externalParticipantRepository.findById(principalUuid)?.email
            else -> null
        }
        val fallbackEmail = externalEmail ?: rawPrincipalId.normalizeEmailOrNull()
        if (appUsers.isEmpty() && fallbackEmail.isNullOrBlank())
        {
            return
        }
        val initiator = authTokenContext.authToken.appUser
        val initiatorName = initiator?.person?.let { "${it.firstName} ${it.lastName}" }
            ?: initiator?.email ?: "Someone"
        val sessionMessage = if (trustedInvitation)
            "You have a trusted invitation that remains inactive until you accept."
        else
            "You have been added to this Exchange."
        val body = emailTemplateService.renderExchangeCreatedRecipientEmail(
            exchangeId = session.id.toString(),
            name = session.name.orEmpty(),
            initiatorName = initiatorName,
            initiatorOrganization = null,
            sessionMessage = sessionMessage,
            documents = session.documents.map { it.title },
            requireSignIn = trustedInvitation,
        )
        val subject = if (trustedInvitation)
            "${configurationService.emailSubjectTitle} | Trusted Exchange invitation"
        else
            "${configurationService.emailSubjectTitle} | You've been added to a Document Exchange"
        val emails = appUsers.map { appUser ->
            ExchangeEmailDelivery(
                to = appUser.email,
                subject = subject,
                body = body,
                preferenceAppUserId = appUser.id,
            )
        }.toMutableList()
        if (appUsers.isEmpty() && !fallbackEmail.isNullOrBlank())
        {
            emails += ExchangeEmailDelivery(
                to = fallbackEmail,
                subject = subject,
                body = body,
            )
        }
        val eventType = if (trustedInvitation)
            "exchange.recipient_invitation"
        else
            "exchange.access_granted"
        val title = if (trustedInvitation)
            "Trusted participant invitation"
        else
            "Exchange access granted"
        val message = if (trustedInvitation)
            "You were invited to access Exchange ${session.name.orEmpty().ifBlank { session.id.toString() }}."
        else
            "Exchange ${session.name.orEmpty().ifBlank { session.id.toString() }} was shared with you."
        exchangeNotificationDeliveryService.scheduleAfterCommit(
            exchangeId = session.id,
            exchangeStatus = session.status.name,
            emails = emails,
            inAppNotifications = appUsers.map { appUser ->
                ExchangeInAppDelivery(
                    appUserId = appUser.id,
                    type = eventType,
                    title = title,
                    message = message,
                    data = mapOf("exchangeId" to session.id.toString()),
                )
            },
            refreshAppUserIds = appUsers.mapTo(mutableSetOf()) { it.id },
        )
    }

    /**
     * Authorize the caller to manage access on the session. When the v2 read path is enabled,
     * this delegates to [AuthorizationService] (`EXCHANGE_MANAGE_ACCESS`, satisfied by the
     * initiator's OWNER share or any future role/share granting `EXCHANGE_SHARE`). Otherwise it
     * falls back to the legacy initiator-only check. Either way a missing session is a 404.
     */
    private fun requireSessionOwner(exchangeId: UUID)
    {
        val session = loadSessionOrThrow(exchangeId)

        val principal = authorizationContextFactory.currentPrincipal()
            ?: throw ForbiddenException("Authentication required to manage access")
        val decision = authorizationService.authorize(
            principal = principal,
            action = Action.EXCHANGE_MANAGE_ACCESS,
            resource = ResourceRef.exchange(exchangeId),
            context = authorizationContextFactory.currentContext(),
        )
        if (decision is Decision.Deny)
        {
            recordAuthorizationDenied(exchangeId, principal.id, Action.EXCHANGE_MANAGE_ACCESS.name, session.ownerOrganizationId, session.name)
            throw ForbiddenException("Not authorized to manage access on this session")
        }
    }

    /** Same as [requireSessionOwner] but also returns the loaded session for subsequent use. */
    private fun requireSessionOwnerAndReturn(exchangeId: UUID): Exchange
    {
        val session = loadSessionOrThrow(exchangeId)

        val principal = authorizationContextFactory.currentPrincipal()
            ?: throw ForbiddenException("Authentication required to manage access")
        val decision = authorizationService.authorize(
            principal = principal,
            action = Action.EXCHANGE_MANAGE_ACCESS,
            resource = ResourceRef.exchange(exchangeId),
            context = authorizationContextFactory.currentContext(),
        )
        if (decision is Decision.Deny)
        {
            recordAuthorizationDenied(exchangeId, principal.id, Action.EXCHANGE_MANAGE_ACCESS.name, session.ownerOrganizationId, session.name)
            throw ForbiddenException("Not authorized to manage access on this session")
        }
        return session
    }

    private fun manageAccessSenderOrganization(
        exchange: Exchange,
        requireOrganization: Boolean,
    ): UUID?
    {
        val ownerOrganizationId = exchange.ownerOrganizationId
        if (ownerOrganizationId == null)
        {
            if (requireOrganization)
            {
                throw IllegalArgumentException(
                    "A trusted recipient can be added only to an organization-owned Exchange",
                )
            }
            return null
        }

        if (authTokenContext.activeOrganizationId != ownerOrganizationId)
        {
            throw ForbiddenException("The active organization does not own this Exchange")
        }
        return ownerOrganizationId
    }

    private fun loadSessionOrThrow(exchangeId: UUID) =
        exchangeRepository.findById(exchangeId)
            ?: throw ExchangeNotFoundException("Exchange not found")

    private fun requireMutableAccessShare(exchangeId: UUID, shareId: UUID): Share
    {
        val session = loadSessionOrThrow(exchangeId)
        val share = requireShareBelongsToSession(shareId, exchangeId)
        val callerAppUserId = authTokenContext.authToken.appUser?.id

        // Protect the session initiator's OWNER share, it's structural and must not be changed.
        val initiatorId = session.initiator?.id
        if (
            initiatorId != null &&
            share.principalKind == PrincipalKind.USER &&
            share.principalId == initiatorId &&
            share.roleName == ExchangeShareRoleName.OWNER &&
            share.source == ShareSource.DIRECT
        )
        {
            throw ForbiddenException("Session owner access cannot be changed or revoked")
        }

        // Prevent the caller from modifying/revoking their own access entry.
        if (callerAppUserId != null && share.principalKind == PrincipalKind.USER && share.principalId == callerAppUserId)
        {
            throw ForbiddenException("You cannot modify or revoke your own access")
        }

        return share
    }

    private fun requireShareBelongsToSession(shareId: UUID, exchangeId: UUID): Share
    {
        val share = shareRepository.findById(shareId)
            ?: throw IllegalArgumentException("Share not found")
        if (share.resourceType != ResourceType.EXCHANGE || share.resourceId != exchangeId)
        {
            throw IllegalArgumentException("Share does not belong to this session")
        }
        return share
    }

    private fun parsePrincipalKind(value: String): PrincipalKind =
        runCatching {
            when (value.trim().uppercase())
            {
                "GROUP" -> PrincipalKind.PRINCIPAL_GROUP
                else -> PrincipalKind.valueOf(value.trim().uppercase())
            }
        }
            .getOrElse { throw IllegalArgumentException("Invalid principal kind: $value") }
            .also {
                if (it !in setOf(PrincipalKind.USER, PrincipalKind.PARTICIPANT, PrincipalKind.PRINCIPAL_GROUP))
                {
                    throw IllegalArgumentException("Principal kind is not supported for Exchange access")
                }
            }

    private fun parseUuid(value: String, field: String): UUID =
        runCatching { UUID.fromString(value.trim()) }
            .getOrElse { throw IllegalArgumentException("Invalid $field") }

    private fun resolveTrustedSelection(
        selection: ExchangeRecipientSelectionRequest,
        caller: com.docuhyphen.app.api.model.entity.AppUser,
        senderOrganizationId: UUID,
    ): ResolvedExchangeRecipientSelection
    {
        if (selection !is TrustedPersonRecipientSelectionRequest &&
            selection !is TrustedGroupRecipientSelectionRequest)
        {
            throw IllegalArgumentException(
                "A trusted participant must be a verified member or published group from a Trusted Organization",
            )
        }
        val resolved = exchangeRecipientSelectionResolver.resolve(selection, caller, senderOrganizationId)
        when (selection)
        {
            is TrustedPersonRecipientSelectionRequest ->
            {
                require(resolved.selectionType == ExchangeRecipientSelectionType.TRUSTED_PERSON) {
                    "The resolved selection does not match the requested trusted person"
                }
                requireNotNull(resolved.preparedPersonResolution) {
                    "The trusted person selection has no verification evidence"
                }
            }

            is TrustedGroupRecipientSelectionRequest ->
            {
                require(resolved.selectionType == ExchangeRecipientSelectionType.TRUSTED_GROUP) {
                    "The resolved selection does not match the requested trusted group"
                }
                requireNotNull(resolved.trustedGroupValidation) {
                    "The trusted group selection has no verification evidence"
                }
            }

            else -> error("Unsupported trusted participant selection")
        }
        return resolved
    }

    /**
     * Resolve the effective principal for a manage-access grant. For USER kind the value can be
     * a UUID or an email. If the email matches an existing user, grant as USER; otherwise fall
     * back to a (created-or-reused) [ExternalParticipant] and grant as [PrincipalKind.PARTICIPANT]
     * so future sign-up can be reconciled by email.
     */
    private fun resolvePrincipal(kind: PrincipalKind, value: String): Pair<PrincipalKind, UUID>
    {
        val trimmed = value.trim()
        if (kind == PrincipalKind.USER)
        {
            runCatching { UUID.fromString(trimmed) }.getOrNull()?.let { userId ->
                if (appUserService.getById(userId) == null) throw IllegalArgumentException("User not found")
                return kind to userId
            }
            val normalizedEmail = trimmed.normalizeEmailOrNull()
                ?: throw IllegalArgumentException("Invalid principalId")
            appUserService.getAppUserByEmail(normalizedEmail)?.let { return kind to it.id }
            // No registered user, promote to an ExternalParticipant so the invite is durable.
            val participantId = findOrCreateExternalParticipant(normalizedEmail).id
            return PrincipalKind.PARTICIPANT to participantId
        }
        val principalId = parseUuid(trimmed, "principalId")
        when (kind)
        {
            PrincipalKind.PARTICIPANT -> if (externalParticipantRepository.findById(principalId) == null)
            {
                throw IllegalArgumentException("Participant not found")
            }

            PrincipalKind.PRINCIPAL_GROUP -> if (organizationGroupService.getById(principalId.toString()) == null)
            {
                throw IllegalArgumentException("Group not found")
            }

            else -> Unit
        }
        return kind to principalId
    }

    private fun requireAssignableRole(roleName: ExchangeShareRoleName)
    {
        if (roleName == ExchangeShareRoleName.OWNER)
        {
            throw IllegalArgumentException("The owner role is reserved for the Exchange initiator")
        }
    }

    private fun enforceSharingPolicy(
        senderOrganizationId: UUID?,
        actorId: UUID,
        kind: PrincipalKind,
        principalId: UUID,
    )
    {
        when (kind)
        {
            PrincipalKind.USER -> organizationExchangePolicyService.assertCanShareWithUser(
                senderOrganizationId,
                actorId,
                principalId,
            )
            PrincipalKind.PARTICIPANT -> organizationExchangePolicyService.assertCanShareWithUser(
                senderOrganizationId,
                actorId,
                null,
            )
            PrincipalKind.PRINCIPAL_GROUP ->
            {
                val group = organizationGroupService.getById(principalId.toString())
                    ?: throw IllegalArgumentException("Group not found")
                organizationExchangePolicyService.assertCanShareWithGroup(senderOrganizationId, actorId, group)
            }

            else -> throw IllegalArgumentException("Principal kind is not supported for Exchange access")
        }
    }

    private data class RecipientBinding(
        val selectionType: ExchangeRecipientSelectionType,
        val targetOrganizationId: UUID?,
    )

    private fun recipientBinding(
        exchange: Exchange,
        principalKind: PrincipalKind,
        principalId: UUID,
    ): RecipientBinding =
        when (principalKind)
        {
            PrincipalKind.USER -> RecipientBinding(
                selectionType = ExchangeRecipientSelectionType.REGISTERED_USER,
                targetOrganizationId = null,
            )
            PrincipalKind.PARTICIPANT -> RecipientBinding(
                selectionType = ExchangeRecipientSelectionType.EXTERNAL_EMAIL,
                targetOrganizationId = null,
            )
            PrincipalKind.PRINCIPAL_GROUP ->
            {
                val group = organizationGroupService.getById(principalId.toString())
                    ?: throw IllegalArgumentException("Group not found")
                val selectionType = when (group.scope)
                {
                    PrincipalGroupScope.PERSONAL -> ExchangeRecipientSelectionType.PERSONAL_GROUP
                    PrincipalGroupScope.ORG -> if (group.ownerOrganizationId == exchange.ownerOrganizationId)
                        ExchangeRecipientSelectionType.INTERNAL_GROUP
                    else
                        ExchangeRecipientSelectionType.TRUSTED_GROUP
                    PrincipalGroupScope.SHARED_PROJECT ->
                        throw IllegalArgumentException("Shared project groups cannot be used as Exchange recipients")
                }
                RecipientBinding(
                    selectionType = selectionType,
                    targetOrganizationId = group.ownerOrganizationId,
                )
            }
            else -> throw IllegalArgumentException("Principal kind is not supported for Exchange access")
        }

    private fun findOrCreateExternalParticipant(email: String): ExternalParticipant
    {
        externalParticipantRepository.findByOwnerAndEmail(ownerOrganizationId = null, email = email)
            ?.let { return it }
        val created = ExternalParticipant().apply {
            this.email = email
            this.emailLower = email.lowercase()
        }
        return externalParticipantRepository.save(created)
    }

    /**
     * Records denied `EXCHANGE_MANAGE_ACCESS` authorization as a
     * genuinely sensitive authorization decision (someone tried to view or change who has access
     * to an exchange without permission), so it gets its own AUTHORIZATION_DENIED ledger row.
     * Failures are caught and logged, never propagated.
     */
    private fun recordAuthorizationDenied(
        exchangeId: UUID,
        actorId: UUID,
        action: String,
        ownerOrganizationId: UUID?,
        exchangeName: String? = null,
    )
    {
        try
        {
            auditRecorder.record(
                AuditEventDraft(
                    owner = ownerOrganizationId?.let(AuditOwnerScope::Organization) ?: AuditOwnerScope.Platform,
                    eventTypeKey = AuditEventType.AUTHORIZATION_DENIED.key,
                    outcome = AuditOutcome.DENIED,
                    actorId = actorId,
                    actorKind = AuditActorKind.HUMAN,
                    targetType = ResourceType.EXCHANGE.name,
                    targetId = exchangeId.toString(),
                    targetLabel = exchangeName,
                    payload = mapOf("action" to action),
                )
            )
        }
        catch (e: AuditDraftInvalidException)
        {
            logger.warn("ExchangeAccessManagementService: AuditRecorder rejected AUTHORIZATION_DENIED draft: {}", e.message)
        }
        catch (e: AuditCaptureFailedException)
        {
            logger.error("ExchangeAccessManagementService: AuditRecorder capture failed (fail-closed) for AUTHORIZATION_DENIED: {}", e.message, e)
        }
    }
}
