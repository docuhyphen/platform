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
import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.repository.ExternalParticipantRepository
import com.docuhyphen.app.api.repository.ShareRepository
import com.docuhyphen.app.api.repository.ExchangeRepository
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.communication.EmailService
import com.docuhyphen.app.api.service.communication.EmailTemplateService
import com.docuhyphen.app.api.service.config.ConfigurationService
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.auth.authz.ShareConstraints
import com.docuhyphen.app.api.service.audit.AuditCaptureFailedException
import com.docuhyphen.app.api.service.audit.AuditDraftInvalidException
import com.docuhyphen.app.api.service.audit.AuditEventDraft
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
    private val authTokenContext: AuthTokenContext,
    private val authorizationService: AuthorizationService,
    private val authorizationContextFactory: AuthorizationContextFactory,
    private val emailService: EmailService,
    private val emailTemplateService: EmailTemplateService,
    private val configurationService: ConfigurationService,
    private val auditRecorder: AuditRecorder,
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

        val requestedKind = parsePrincipalKind(principalKind)
        val role = roleName

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
        // Reject malformed/contradictory constraints up front and store a canonical form.
        val normalizedConstraints = ShareConstraints.normalizeForStorage(constraintsJson)

        shareService.grant(
            resourceType = ResourceType.EXCHANGE,
            resourceId = exchangeId,
            principalKind = kind,
            principalId = principalUuid,
            roleName = role,
            grantedByAppUserId = callerAppUserId,
            constraintsJson = normalizedConstraints,
            expiresAt = expiresAtEpochMillis?.let { Timestamp(it) },
            resourceLabel = session.name,
        )

        // Send invitation email to the newly added person.
        sendAccessGrantedNotification(session, kind, principalUuid, principalId.trim())
    }

    @Transactional
    fun changeRole(exchangeId: UUID, shareId: UUID, roleName: ExchangeShareRoleName, constraintsJson: String? = null)
    {
        val session = requireSessionOwnerAndReturn(exchangeId)
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
            recordAuthorizationDenied(exchangeId, principal.id, Action.EXCHANGE_MANAGE_ACCESS.name, session.name)
            throw ExchangeNotFoundException("Exchange not found")
        }
        return shareQueryService.getSessionAccessView(exchangeId)
    }

    // -------------------------------------------------------------------------

    /**
     * Send an invitation/notification email to a person who was just granted access.
     * For USER principals, looks up the user's email; for PARTICIPANT principals,
     * looks up the ExternalParticipant's email; for other kinds, the original
     * [rawPrincipalId] is used if it looks like an email.
     */
    private fun sendAccessGrantedNotification(
        session: Exchange,
        kind: PrincipalKind,
        principalUuid: UUID,
        rawPrincipalId: String,
    )
    {
        val recipientEmail: String? = when (kind)
        {
            PrincipalKind.USER -> appUserService.getById(principalUuid)?.email
            PrincipalKind.PARTICIPANT -> externalParticipantRepository.findById(principalUuid)?.email
            else -> rawPrincipalId.normalizeEmailOrNull()
        }

        if (recipientEmail.isNullOrBlank()) return

        try
        {
            val initiator = authTokenContext.authToken.appUser
            val initiatorName = initiator?.person?.let { "${it.firstName} ${it.lastName}" }
                ?: initiator?.email ?: "Someone"

            val body = emailTemplateService.renderExchangeCreatedRecipientEmail(
                exchangeId = session.id.toString(),
                name = session.name.orEmpty(),
                initiatorName = initiatorName,
                initiatorOrganization = null,
                sessionMessage = "You have been added to this exchange.",
                documents = session.documents.map { it.title },
            )
            emailService.sendEmail(
                to = recipientEmail,
                subject = "${configurationService.emailSubjectTitle} | You've been added to a exchange",
                body = body,
                useHtml = true,
            )
        }
        catch (e: Exception)
        {
            logger.error("Failed to send access-granted email to {}", recipientEmail, e)
        }
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
            recordAuthorizationDenied(exchangeId, principal.id, Action.EXCHANGE_MANAGE_ACCESS.name, session.name)
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
            recordAuthorizationDenied(exchangeId, principal.id, Action.EXCHANGE_MANAGE_ACCESS.name, session.name)
            throw ForbiddenException("Not authorized to manage access on this session")
        }
        return session
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

    private fun parseUuid(value: String, field: String): UUID =
        runCatching { UUID.fromString(value.trim()) }
            .getOrElse { throw IllegalArgumentException("Invalid $field") }

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
            runCatching { UUID.fromString(trimmed) }.getOrNull()?.let { return kind to it }
            val normalizedEmail = trimmed.normalizeEmailOrNull()
                ?: throw IllegalArgumentException("Invalid principalId")
            appUserService.getAppUserByEmail(normalizedEmail)?.let { return kind to it.id }
            // No registered user, promote to an ExternalParticipant so the invite is durable.
            val participantId = findOrCreateExternalParticipant(normalizedEmail).id
            return PrincipalKind.PARTICIPANT to participantId
        }
        return kind to parseUuid(trimmed, "principalId")
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
    private fun recordAuthorizationDenied(exchangeId: UUID, actorId: UUID, action: String, exchangeName: String? = null)
    {
        try
        {
            auditRecorder.record(
                AuditEventDraft(
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
