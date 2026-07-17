package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.exception.ExternalIdentityResolutionUnavailableException
import com.docuhyphen.app.api.exception.OrganizationTrustAuthorizationException
import com.docuhyphen.app.api.exception.OrganizationTrustNotFoundException
import com.docuhyphen.app.api.exception.OrganizationTrustValidationException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.ExternalIdentityResolutionDtoTransformer
import com.docuhyphen.app.api.model.dto.ExternalIdentityResolutionDto
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.ExternalIdentityResolution
import com.docuhyphen.app.api.model.entity.OrganizationMembership
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.repository.ExternalIdentityResolutionRepository
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditOwnerScope
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.util.Locale
import java.util.UUID

@ApplicationScoped
class ExternalIdentityResolutionService @Inject constructor(
    private val resolutionRepository: ExternalIdentityResolutionRepository,
    private val validationService: TrustedRecipientValidationService,
    private val membershipService: OrganizationMembershipService,
    private val appUserService: AppUserService,
    private val lookupGuardService: ExternalIdentityLookupGuardService,
    private val configService: OrganizationTrustConfigService,
    private val transformer: ExternalIdentityResolutionDtoTransformer,
    private val authorizationService: AuthorizationService,
    private val authorizationContextFactory: AuthorizationContextFactory,
    private val authTokenContext: AuthTokenContext,
    private val auditRecorder: AuditRecorder,
)
{
    fun resolve(
        targetOrganizationId: UUID,
        email: String?,
        now: Instant = Instant.now(),
    ): ExternalIdentityResolutionDto
    {
        val caller = currentCaller()
        try
        {
            requireAuthorized(caller)
            val normalizedEmail = normalizeExactEmail(email)
            val rateLimitRelationshipId = validationService.relationshipIdForLookup(
                caller.organizationId,
                targetOrganizationId,
            ) ?: syntheticRelationshipKey(caller.organizationId, targetOrganizationId)
            lookupGuardService.enforce(
                caller.appUserId,
                caller.organizationId,
                targetOrganizationId,
                rateLimitRelationshipId,
                normalizedEmail,
                authTokenContext.serverTraceId,
            )
            val validation = validationService.validatePersonResolution(
                caller.organizationId,
                targetOrganizationId,
                now,
            )
            val membership = eligibleMembership(targetOrganizationId, normalizedEmail, now)
            val appUser = appUserService.getById(membership.appUserId) ?: unavailable()
            if (!appUser.isActive || appUser.deprovisionedAt != null ||
                appUser.email.trim().lowercase(Locale.ROOT) != normalizedEmail)
            {
                unavailable()
            }

            val createdAt = Timestamp.from(now)
            val expiresAt = listOfNotNull(
                now.plus(configService.identityResolutionExpiry),
                validation.verificationExpiresAt,
                membership.expiresAt?.toInstant(),
            ).minOrNull() ?: unavailable()
            if (!expiresAt.isAfter(now))
            {
                unavailable()
            }
            val resolution = resolutionRepository.save(
                ExternalIdentityResolution().apply {
                    actorAppUserId = caller.appUserId
                    callerOrganizationId = caller.organizationId
                    this.targetOrganizationId = targetOrganizationId
                    relationshipId = validation.relationship.id
                    senderPolicyRevision = validation.senderPolicy.revision
                    targetPolicyRevision = validation.targetPolicy.revision
                    resolvedAppUserId = appUser.id
                    resolvedMembershipId = membership.id
                    this.normalizedEmail = normalizedEmail
                    displayNameSnapshot = if (validation.targetPolicy.shareMemberDisplayName)
                        displayName(appUser.person?.firstName, appUser.person?.lastName)
                    else null
                    this.createdAt = createdAt
                    this.expiresAt = Timestamp.from(expiresAt)
                },
            )
            record(resolution, AuditEventType.ORG_TRUST_IDENTITY_RESOLUTION_ALLOWED, AuditOutcome.SUCCESS)
            return transformer.toDto(resolution, validation.targetOrganization.name)
        }
        catch (exception: OrganizationTrustNotFoundException)
        {
            recordDenied(caller, targetOrganizationId)
            throw ExternalIdentityResolutionUnavailableException()
        }
        catch (exception: ExternalIdentityResolutionUnavailableException)
        {
            recordDenied(caller, targetOrganizationId)
            throw exception
        }
        catch (exception: RuntimeException)
        {
            recordDenied(caller, targetOrganizationId)
            throw exception
        }
    }

    /**
     * Revalidates a stored resolution for use as a trusted-person Exchange recipient without
     * consuming it. Confirms the resolution belongs to the initiating actor, caller organization,
     * and its target organization, is unconsumed and unexpired, and that the current relationship,
     * both party policies, the resolved account, and the exact target membership are still eligible.
     * The single-use consumption happens later, under a row lock, in the Exchange transaction.
     */
    fun prepareForInitiation(
        resolutionId: UUID,
        actorAppUserId: UUID,
        callerOrganizationId: UUID,
        now: Instant = Instant.now(),
    ): PreparedPersonResolution
    {
        val resolution = resolutionRepository.findById(resolutionId) ?: unavailable()
        if (resolution.consumedAt != null || resolution.consumedByExchangeId != null ||
            !resolution.expiresAt.toInstant().isAfter(now) ||
            resolution.actorAppUserId != actorAppUserId ||
            resolution.callerOrganizationId != callerOrganizationId)
        {
            unavailable()
        }
        val validation = validationService.validatePersonResolution(
            resolution.callerOrganizationId,
            resolution.targetOrganizationId,
            now,
        )
        if (validation.relationship.id != resolution.relationshipId)
        {
            unavailable()
        }
        val membership = eligibleMembership(resolution.targetOrganizationId, resolution.normalizedEmail, now)
        if (membership.id != resolution.resolvedMembershipId || membership.appUserId != resolution.resolvedAppUserId)
        {
            unavailable()
        }
        val appUser = appUserService.getById(resolution.resolvedAppUserId) ?: unavailable()
        if (!appUser.isActive || appUser.deprovisionedAt != null ||
            appUser.email.trim().lowercase(Locale.ROOT) != resolution.normalizedEmail)
        {
            unavailable()
        }
        return PreparedPersonResolution(resolution, appUser, membership, validation)
    }

    @Transactional
    fun consumeForExchange(
        resolutionId: UUID,
        actorAppUserId: UUID,
        callerOrganizationId: UUID,
        targetOrganizationId: UUID,
        exchangeId: UUID,
        now: Instant = Instant.now(),
    ): ExternalIdentityResolution
    {
        val resolution = resolutionRepository.findForUpdate(resolutionId) ?: unavailable()
        if (resolution.consumedAt != null || resolution.consumedByExchangeId != null)
        {
            record(resolution, AuditEventType.ORG_TRUST_IDENTITY_RESOLUTION_REPLAY_DENIED, AuditOutcome.DENIED)
            unavailable()
        }
        if (!resolution.expiresAt.toInstant().isAfter(now))
        {
            record(resolution, AuditEventType.ORG_TRUST_IDENTITY_RESOLUTION_EXPIRED, AuditOutcome.DENIED)
            unavailable()
        }
        if (resolution.actorAppUserId != actorAppUserId ||
            resolution.callerOrganizationId != callerOrganizationId ||
            resolution.targetOrganizationId != targetOrganizationId)
        {
            record(resolution, AuditEventType.ORG_TRUST_IDENTITY_RESOLUTION_DENIED, AuditOutcome.DENIED)
            unavailable()
        }
        resolution.consumedAt = Timestamp.from(now)
        resolution.consumedByExchangeId = exchangeId
        val consumed = resolutionRepository.update(resolution)
        record(consumed, AuditEventType.ORG_TRUST_IDENTITY_RESOLUTION_CONSUMED, AuditOutcome.SUCCESS)
        return consumed
    }

    @Transactional
    fun cleanupExpired(now: Instant = Instant.now()): Int
    {
        val cutoff = now.minus(configService.identityResolutionRetention)
        val expired = resolutionRepository.findExpiredBefore(cutoff)
        expired.forEach { resolution ->
            record(resolution, AuditEventType.ORG_TRUST_IDENTITY_RESOLUTION_EXPIRED, AuditOutcome.SUCCESS)
        }
        resolutionRepository.deleteAll(expired)
        return expired.size
    }

    private fun currentCaller(): Caller
    {
        val organizationId = authTokenContext.activeOrganizationId
            ?: throw OrganizationTrustAuthorizationException("An active organization is required")
        val principal = authorizationContextFactory.currentPrincipal()
            ?: throw OrganizationTrustAuthorizationException("Authentication is required")
        if (principal.kind != PrincipalKind.USER)
        {
            throw OrganizationTrustAuthorizationException("Only an organization member can verify a trusted member")
        }
        return Caller(principal.id, organizationId)
    }

    private fun requireAuthorized(caller: Caller)
    {
        val decision = authorizationService.authorize(
            principal = com.docuhyphen.app.api.service.auth.authz.PrincipalRef.user(caller.appUserId),
            action = Action.EXTERNAL_IDENTITY_RESOLVE,
            resource = ResourceRef.organization(caller.organizationId),
            context = authorizationContextFactory.currentContext(),
        )
        if (decision is Decision.Deny)
        {
            throw OrganizationTrustAuthorizationException()
        }
    }

    private fun normalizeExactEmail(email: String?): String
    {
        val normalized = email?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (normalized.length > 320 || !EMAIL_PATTERN.matches(normalized))
        {
            throw OrganizationTrustValidationException("Enter a complete valid email address")
        }
        return normalized
    }

    private fun eligibleMembership(
        targetOrganizationId: UUID,
        normalizedEmail: String,
        now: Instant,
    ): OrganizationMembership
    {
        val membership = membershipService.findActiveMembershipsByOrganizationAndExactEmail(
            targetOrganizationId,
            normalizedEmail,
        ).singleOrNull() ?: unavailable()
        if (membership.deprovisionedAt != null || membership.expiresAt?.toInstant()?.isAfter(now) == false)
        {
            unavailable()
        }
        return membership
    }

    private fun displayName(firstName: String?, lastName: String?): String? =
        listOfNotNull(firstName?.trim(), lastName?.trim())
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { null }

    private fun syntheticRelationshipKey(callerOrganizationId: UUID, targetOrganizationId: UUID): UUID
    {
        val ordered = listOf(callerOrganizationId.toString(), targetOrganizationId.toString()).sorted()
        return UUID.nameUUIDFromBytes(ordered.joinToString(":").toByteArray(Charsets.UTF_8))
    }

    private fun recordDenied(caller: Caller, targetOrganizationId: UUID)
    {
        auditRecorder.record(
            AuditEventDraft(
                owner = AuditOwnerScope.Organization(caller.organizationId),
                eventTypeKey = AuditEventType.ORG_TRUST_IDENTITY_RESOLUTION_DENIED.key,
                outcome = AuditOutcome.DENIED,
                actorId = caller.appUserId,
                actorKind = AuditActorKind.HUMAN,
                targetType = "ORGANIZATION",
                targetId = targetOrganizationId.toString(),
                payload = mapOf("targetOrganizationId" to targetOrganizationId.toString()),
            ),
        )
    }

    private fun record(
        resolution: ExternalIdentityResolution,
        eventType: AuditEventType,
        outcome: AuditOutcome,
    )
    {
        auditRecorder.record(
            AuditEventDraft(
                owner = AuditOwnerScope.Organization(resolution.callerOrganizationId),
                eventTypeKey = eventType.key,
                outcome = outcome,
                actorId = resolution.actorAppUserId,
                actorKind = AuditActorKind.HUMAN,
                targetType = "EXTERNAL_IDENTITY_RESOLUTION",
                targetId = resolution.id.toString(),
                payload = mapOf(
                    "targetOrganizationId" to resolution.targetOrganizationId.toString(),
                    "relationshipId" to resolution.relationshipId.toString(),
                ),
                idempotencyKey = "${eventType.key}:${resolution.id}",
            ),
        )
    }

    private fun unavailable(): Nothing = throw ExternalIdentityResolutionUnavailableException()

    data class PreparedPersonResolution(
        val resolution: ExternalIdentityResolution,
        val appUser: AppUser,
        val membership: OrganizationMembership,
        val validation: TrustedExchangePolicyValidation,
    )

    private data class Caller(val appUserId: UUID, val organizationId: UUID)

    companion object
    {
        private val EMAIL_PATTERN = Regex("^[a-z0-9+_.-]+@[a-z0-9.-]+\\.[a-z]{2,}$")
    }
}
