package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.exception.OrganizationTrustAuthorizationException
import com.docuhyphen.app.api.exception.OrganizationTrustConflictException
import com.docuhyphen.app.api.exception.OrganizationTrustNotFoundException
import com.docuhyphen.app.api.exception.OrganizationTrustStaleVersionException
import com.docuhyphen.app.api.exception.OrganizationTrustValidationException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.OrganizationTrustDecision
import com.docuhyphen.app.api.model.entity.OrganizationTrustRelationship
import com.docuhyphen.app.api.model.entity.OrganizationTrustRelationshipStatus
import com.docuhyphen.app.api.model.entity.OrganizationTrustSuspension
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.repository.organization.OrganizationTrustRelationshipRepository
import com.docuhyphen.app.api.repository.organization.OrganizationTrustSuspensionRepository
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
import com.docuhyphen.app.api.service.exchange.TrustedGroupAccessReconciliationService
import com.docuhyphen.app.api.service.subscription.OrganizationFeatureSubscriptionGuard
import com.docuhyphen.app.api.service.subscription.PlanFeature
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.inject.Provider
import jakarta.persistence.OptimisticLockException
import jakarta.persistence.PersistenceException
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@ApplicationScoped
class OrganizationTrustRelationshipService @Inject constructor(
    private val relationshipRepository: OrganizationTrustRelationshipRepository,
    private val suspensionRepository: OrganizationTrustSuspensionRepository,
    private val organizationService: OrganizationService,
    private val policyService: OrganizationTrustPolicyService,
    private val authorizationService: AuthorizationService,
    private val authorizationContextFactory: AuthorizationContextFactory,
    private val authTokenContext: AuthTokenContext,
    private val trustConfig: OrganizationTrustConfigService,
    private val auditRecorder: AuditRecorder,
    private val trustedGroupAccessReconciliationService: Provider<TrustedGroupAccessReconciliationService>,
    private val subscriptionGuard: OrganizationFeatureSubscriptionGuard,
)
{
    data class RequestOutcome(
        val relationship: OrganizationTrustRelationship,
        val eventType: AuditEventType,
    )

    private data class Caller(val appUserId: UUID, val organizationId: UUID)
    private data class CanonicalOrganizations(val organizationAId: UUID, val organizationBId: UUID)

    @Transactional(dontRollbackOn = [OrganizationTrustConflictException::class])
    fun requestRelationship(
        targetOrganizationId: UUID,
        requestMessage: String?,
        now: Instant = Instant.now(),
    ): RequestOutcome
    {
        val caller = requireCaller(Action.ORG_TRUST_REQUEST)
        subscriptionGuard.requireMutation(caller.organizationId, PlanFeature.IDENTITY_AND_INTEGRATIONS)
        if (caller.organizationId == targetOrganizationId)
        {
            throw OrganizationTrustValidationException("An organization cannot trust itself")
        }
        requireEligibleOrganization(caller.organizationId)
        requireEligibleOrganization(targetOrganizationId)

        val (organizationAId, organizationBId) = canonicalOrganizations(caller.organizationId, targetOrganizationId)
        relationshipRepository.lockOrganizations(organizationAId, organizationBId)
        val current = relationshipRepository.findCurrentForOrganizations(organizationAId, organizationBId)
        val latestTerminal = when
        {
            current?.status == OrganizationTrustRelationshipStatus.PENDING &&
                !current.requestExpiresAt.toInstant().isAfter(now) ->
            {
                expireRelationship(current, now)
                current
            }
            current != null -> throw OrganizationTrustConflictException(
                "A pending or active Trusted Organization relationship already exists",
            )
            else -> relationshipRepository.findLatestTerminalForOrganizations(organizationAId, organizationBId)
        }

        latestTerminal?.let { terminal ->
            val cooldownEndsAt = terminalTransitionAt(terminal).plus(trustConfig.requestCooldown)
            if (now.isBefore(cooldownEndsAt))
            {
                throw OrganizationTrustConflictException("A new trust request cannot be sent during the cooldown period")
            }
        }

        val requestedAt = Timestamp.from(now)
        val relationship = OrganizationTrustRelationship().apply {
            this.organizationAId = organizationAId
            this.organizationBId = organizationBId
            requestedByOrganizationId = caller.organizationId
            requestedByAppUserId = caller.appUserId
            status = OrganizationTrustRelationshipStatus.PENDING
            this.requestMessage = normalizeOptional(requestMessage, "Request message")
            this.requestedAt = requestedAt
            requestExpiresAt = Timestamp.from(now.plus(trustConfig.requestExpiry))
            latestTransitionByAppUserId = caller.appUserId
        }

        val saved = try
        {
            relationshipRepository.insertAndFlush(relationship)
        }
        catch (exception: PersistenceException)
        {
            throw OrganizationTrustConflictException(
                "A concurrent trust request already established these organizations",
                exception,
            )
        }

        policyService.initializeDefaultPolicies(saved, now)
        val eventType = if (latestTerminal == null)
            AuditEventType.ORG_TRUST_REQUESTED
        else
            AuditEventType.ORG_TRUST_REREQUESTED
        recordForBothOrganizations(
            relationship = saved,
            eventType = eventType,
            actorId = caller.appUserId,
            reason = null,
            operationId = saved.id.toString(),
        )
        return RequestOutcome(saved, eventType)
    }

    @Transactional(dontRollbackOn = [OrganizationTrustConflictException::class])
    fun decide(
        relationshipId: UUID,
        decision: OrganizationTrustDecision,
        reason: String?,
        expectedVersion: Long,
        now: Instant = Instant.now(),
    ): OrganizationTrustRelationship
    {
        val caller = requireCaller(Action.ORG_TRUST_DECIDE)
        subscriptionGuard.requireMutation(caller.organizationId, PlanFeature.IDENTITY_AND_INTEGRATIONS)
        val relationship = requireRelationship(relationshipId)
        requireParty(relationship, caller.organizationId)
        requireExpectedVersion(relationship, expectedVersion)
        if (relationship.status != OrganizationTrustRelationshipStatus.PENDING)
        {
            throw OrganizationTrustConflictException("Only a pending trust request can be decided")
        }
        if (relationship.requestedByOrganizationId == caller.organizationId)
        {
            throw OrganizationTrustAuthorizationException("The requesting organization cannot decide its own request")
        }
        if (!relationship.requestExpiresAt.toInstant().isAfter(now))
        {
            expireRelationship(relationship, now)
            throw OrganizationTrustConflictException("The trust request has expired")
        }

        val transitionAt = Timestamp.from(now)
        val normalizedReason = normalizeOptional(reason, "Decision reason")
        val eventType = when (decision)
        {
            OrganizationTrustDecision.ACCEPT ->
            {
                requireEligibleOrganization(relationship.organizationAId)
                requireEligibleOrganization(relationship.organizationBId)
                relationship.status = OrganizationTrustRelationshipStatus.ACTIVE
                relationship.activatedAt = transitionAt
                relationship.reviewDueAt = Timestamp.from(now.plus(trustConfig.reviewPeriod))
                AuditEventType.ORG_TRUST_ACCEPTED
            }
            OrganizationTrustDecision.REJECT ->
            {
                relationship.status = OrganizationTrustRelationshipStatus.REJECTED
                relationship.rejectedAt = transitionAt
                AuditEventType.ORG_TRUST_REJECTED
            }
        }
        relationship.latestTransitionByAppUserId = caller.appUserId
        relationship.latestTransitionReason = normalizedReason
        val saved = updateRelationship(relationship)
        recordForBothOrganizations(saved, eventType, caller.appUserId, normalizedReason, saved.id.toString())
        return saved
    }

    @Transactional(dontRollbackOn = [OrganizationTrustConflictException::class])
    fun withdraw(
        relationshipId: UUID,
        reason: String?,
        expectedVersion: Long,
        now: Instant = Instant.now(),
    ): OrganizationTrustRelationship
    {
        val caller = requireCaller(Action.ORG_TRUST_REQUEST)
        subscriptionGuard.requireMutation(caller.organizationId, PlanFeature.IDENTITY_AND_INTEGRATIONS)
        val relationship = requireRelationship(relationshipId)
        requireParty(relationship, caller.organizationId)
        requireExpectedVersion(relationship, expectedVersion)
        if (relationship.status != OrganizationTrustRelationshipStatus.PENDING)
        {
            throw OrganizationTrustConflictException("Only a pending trust request can be withdrawn")
        }
        if (!relationship.requestExpiresAt.toInstant().isAfter(now))
        {
            expireRelationship(relationship, now)
            throw OrganizationTrustConflictException("The trust request has expired")
        }
        if (relationship.requestedByOrganizationId != caller.organizationId)
        {
            throw OrganizationTrustAuthorizationException("Only the requesting organization can withdraw the request")
        }

        val normalizedReason = normalizeOptional(reason, "Withdrawal reason")
        relationship.status = OrganizationTrustRelationshipStatus.WITHDRAWN
        relationship.withdrawnAt = Timestamp.from(now)
        relationship.latestTransitionByAppUserId = caller.appUserId
        relationship.latestTransitionReason = normalizedReason
        val saved = updateRelationship(relationship)
        recordForBothOrganizations(
            saved,
            AuditEventType.ORG_TRUST_WITHDRAWN,
            caller.appUserId,
            normalizedReason,
            saved.id.toString(),
        )
        return saved
    }

    @Transactional
    fun terminate(
        relationshipId: UUID,
        reason: String?,
        expectedVersion: Long,
        now: Instant = Instant.now(),
    ): OrganizationTrustRelationship
    {
        val caller = requireCaller(Action.ORG_TRUST_SUSPEND)
        subscriptionGuard.requireMutation(caller.organizationId, PlanFeature.IDENTITY_AND_INTEGRATIONS)
        val relationship = requireRelationship(relationshipId)
        requireParty(relationship, caller.organizationId)
        requireExpectedVersion(relationship, expectedVersion)
        if (relationship.status != OrganizationTrustRelationshipStatus.ACTIVE)
        {
            throw OrganizationTrustConflictException("Only an active trust relationship can be ended")
        }

        val normalizedReason = normalizeOptional(reason, "Termination reason")
        relationship.status = OrganizationTrustRelationshipStatus.ENDED
        relationship.endedAt = Timestamp.from(now)
        relationship.latestTransitionByAppUserId = caller.appUserId
        relationship.latestTransitionReason = normalizedReason
        val saved = updateRelationship(relationship)
        recordForBothOrganizations(
            saved,
            AuditEventType.ORG_TRUST_ENDED,
            caller.appUserId,
            normalizedReason,
            saved.id.toString(),
        )
        return saved
    }

    @Transactional
    fun suspend(
        relationshipId: UUID,
        reason: String,
        now: Instant = Instant.now(),
    ): OrganizationTrustSuspension
    {
        val caller = requireCaller(Action.ORG_TRUST_SUSPEND)
        subscriptionGuard.requireMutation(caller.organizationId, PlanFeature.IDENTITY_AND_INTEGRATIONS)
        val relationship = requireRelationship(relationshipId)
        requireParty(relationship, caller.organizationId)
        if (relationship.status != OrganizationTrustRelationshipStatus.ACTIVE)
        {
            throw OrganizationTrustConflictException("Only an active trust relationship can be suspended")
        }
        if (suspensionRepository.findActiveByOwner(relationshipId, caller.organizationId) != null)
        {
            throw OrganizationTrustConflictException("This organization has already suspended the relationship")
        }

        val normalizedReason = normalizeRequired(reason, "Suspension reason")
        val suspension = OrganizationTrustSuspension().apply {
            this.relationshipId = relationshipId
            suspendingOrganizationId = caller.organizationId
            this.reason = normalizedReason
            suspendedByAppUserId = caller.appUserId
            suspendedAt = Timestamp.from(now)
        }
        val saved = try
        {
            suspensionRepository.insertAndFlush(suspension)
        }
        catch (exception: PersistenceException)
        {
            throw OrganizationTrustConflictException("A concurrent suspension already exists", exception)
        }
        recordForBothOrganizations(
            relationship,
            AuditEventType.ORG_TRUST_SUSPENDED,
            caller.appUserId,
            normalizedReason,
            saved.id.toString(),
        )
        return saved
    }

    @Transactional
    fun resume(
        relationshipId: UUID,
        suspensionId: UUID,
        now: Instant = Instant.now(),
    ): OrganizationTrustSuspension
    {
        val caller = requireCaller(Action.ORG_TRUST_SUSPEND)
        subscriptionGuard.requireMutation(caller.organizationId, PlanFeature.IDENTITY_AND_INTEGRATIONS)
        val relationship = requireRelationship(relationshipId)
        requireParty(relationship, caller.organizationId)
        if (relationship.status != OrganizationTrustRelationshipStatus.ACTIVE)
        {
            throw OrganizationTrustConflictException("Only an active trust relationship can be resumed")
        }
        // Serialize concurrent resume transactions for this relationship so that clearing the
        // last active suspension always observes the other party's committed state. Without this,
        // two parties clearing their suspensions concurrently could each still see the other's
        // suspension and skip the final group access reconciliation.
        relationshipRepository.lockOrganizations(relationship.organizationAId, relationship.organizationBId)
        val suspension = suspensionRepository.findById(suspensionId)
            ?: throw OrganizationTrustNotFoundException("Trust suspension not found")
        if (suspension.relationshipId != relationshipId)
        {
            throw OrganizationTrustNotFoundException("Trust suspension not found")
        }
        if (suspension.suspendingOrganizationId != caller.organizationId)
        {
            throw OrganizationTrustAuthorizationException("An organization can clear only its own suspension")
        }
        if (!suspension.isActive())
        {
            throw OrganizationTrustConflictException("The trust suspension has already been cleared")
        }

        suspension.clearedByAppUserId = caller.appUserId
        suspension.clearedAt = Timestamp.from(now)
        val saved = suspensionRepository.updateAndFlush(suspension)
        recordForBothOrganizations(
            relationship,
            AuditEventType.ORG_TRUST_RESUMED,
            caller.appUserId,
            null,
            saved.id.toString(),
        )
        if (suspensionRepository.findActive(relationshipId).isEmpty())
        {
            trustedGroupAccessReconciliationService.get().reconcileRelationship(relationshipId)
        }
        return saved
    }

    @Transactional
    fun expireDueRequests(now: Instant = Instant.now(), limit: Int = 100): Int
        = expireDueRequestRecords(now, limit).size

    @Transactional
    fun expireDueRequestRecords(
        now: Instant = Instant.now(),
        limit: Int = 100,
    ): List<OrganizationTrustRelationship>
    {
        val due = relationshipRepository.findDueForExpiryForUpdate(Timestamp.from(now), limit)
        due.forEach { expireRelationship(it, now) }
        return due
    }

    fun getById(relationshipId: UUID): OrganizationTrustRelationship = requireRelationship(relationshipId)

    fun listForParty(organizationId: UUID): List<OrganizationTrustRelationship> =
        relationshipRepository.findForParty(organizationId)

    fun findCurrentForOrganizations(
        firstOrganizationId: UUID,
        secondOrganizationId: UUID,
    ): OrganizationTrustRelationship?
    {
        if (firstOrganizationId == secondOrganizationId)
        {
            return null
        }
        val canonical = canonicalOrganizations(firstOrganizationId, secondOrganizationId)
        return relationshipRepository.findCurrentForOrganizations(
            canonical.organizationAId,
            canonical.organizationBId,
        )
    }

    fun activeSuspensions(relationshipId: UUID): List<OrganizationTrustSuspension> =
        suspensionRepository.findActive(relationshipId)

    fun isEffectivelySuspended(relationshipId: UUID): Boolean =
        requireRelationship(relationshipId).status == OrganizationTrustRelationshipStatus.ACTIVE &&
            activeSuspensions(relationshipId).isNotEmpty()

    private fun expireRelationship(relationship: OrganizationTrustRelationship, now: Instant)
    {
        relationship.status = OrganizationTrustRelationshipStatus.EXPIRED
        relationship.expiredAt = Timestamp.from(now)
        relationship.latestTransitionByAppUserId = null
        relationship.latestTransitionReason = "Trust request expired"
        val saved = updateRelationship(relationship)
        recordForBothOrganizations(
            saved,
            AuditEventType.ORG_TRUST_EXPIRED,
            null,
            saved.latestTransitionReason,
            saved.id.toString(),
        )
    }

    private fun updateRelationship(relationship: OrganizationTrustRelationship): OrganizationTrustRelationship = try
    {
        relationshipRepository.updateAndFlush(relationship)
    }
    catch (exception: PersistenceException)
    {
        if (hasOptimisticLockCause(exception))
        {
            throw OrganizationTrustStaleVersionException()
        }
        throw exception
    }

    private fun requireCaller(action: Action): Caller
    {
        val activeOrganizationId = authTokenContext.activeOrganizationId
            ?: throw OrganizationTrustAuthorizationException("An active organization is required")
        val principal = authorizationContextFactory.currentPrincipal()
            ?: throw OrganizationTrustAuthorizationException("Authentication is required")
        if (principal.kind != PrincipalKind.USER)
        {
            throw OrganizationTrustAuthorizationException("Only an organization member can manage trust")
        }
        val decision = authorizationService.authorize(
            principal = principal,
            action = action,
            resource = ResourceRef.organization(activeOrganizationId),
            context = authorizationContextFactory.currentContext(),
        )
        if (decision is Decision.Deny)
        {
            throw OrganizationTrustAuthorizationException()
        }
        return Caller(principal.id, activeOrganizationId)
    }

    private fun requireEligibleOrganization(organizationId: UUID)
    {
        val organization = organizationService.getOrganizationById(organizationId)
        if (!organization.isActive || !organization.verificationComplete)
        {
            throw OrganizationTrustValidationException("Trusted Organizations must be active and verified")
        }
    }

    private fun requireRelationship(relationshipId: UUID): OrganizationTrustRelationship =
        relationshipRepository.findById(relationshipId) ?: throw OrganizationTrustNotFoundException()

    private fun requireParty(relationship: OrganizationTrustRelationship, organizationId: UUID)
    {
        if (!relationship.includes(organizationId))
        {
            throw OrganizationTrustNotFoundException()
        }
    }

    private fun requireExpectedVersion(relationship: OrganizationTrustRelationship, expectedVersion: Long)
    {
        if (expectedVersion < 0 || relationship.version != expectedVersion)
        {
            throw OrganizationTrustStaleVersionException()
        }
    }

    private fun canonicalOrganizations(first: UUID, second: UUID): CanonicalOrganizations =
        if (first.toString() < second.toString())
            CanonicalOrganizations(first, second)
        else
            CanonicalOrganizations(second, first)

    private fun terminalTransitionAt(relationship: OrganizationTrustRelationship): Instant = when (relationship.status)
    {
        OrganizationTrustRelationshipStatus.REJECTED -> relationship.rejectedAt
        OrganizationTrustRelationshipStatus.WITHDRAWN -> relationship.withdrawnAt
        OrganizationTrustRelationshipStatus.EXPIRED -> relationship.expiredAt
        OrganizationTrustRelationshipStatus.ENDED -> relationship.endedAt
        else -> null
    }?.toInstant() ?: relationship.requestedAt.toInstant()

    private fun normalizeOptional(value: String?, fieldName: String): String? =
        value?.trim()?.takeIf { it.isNotEmpty() }?.also {
            if (it.length > MAX_TEXT_LENGTH)
            {
                throw OrganizationTrustValidationException("$fieldName cannot exceed $MAX_TEXT_LENGTH characters")
            }
        }

    private fun normalizeRequired(value: String, fieldName: String): String =
        normalizeOptional(value, fieldName)
            ?: throw OrganizationTrustValidationException("$fieldName is required")

    private fun recordForBothOrganizations(
        relationship: OrganizationTrustRelationship,
        eventType: AuditEventType,
        actorId: UUID?,
        reason: String?,
        operationId: String,
    )
    {
        val businessTransactionId = UUID.randomUUID().toString()
        listOf(relationship.organizationAId, relationship.organizationBId).forEach { ownerOrganizationId ->
            auditRecorder.record(
                AuditEventDraft(
                    owner = AuditOwnerScope.Organization(ownerOrganizationId),
                    eventTypeKey = eventType.key,
                    outcome = AuditOutcome.SUCCESS,
                    actorId = actorId,
                    actorKind = if (actorId == null) AuditActorKind.SYSTEM else AuditActorKind.HUMAN,
                    targetType = TRUST_RELATIONSHIP_TARGET_TYPE,
                    targetId = relationship.id.toString(),
                    reason = reason,
                    payload = mapOf(
                        "relationshipStatus" to relationship.status.name,
                        "requestingOrganizationId" to relationship.requestedByOrganizationId.toString(),
                        "partnerOrganizationId" to relationship.partnerOf(ownerOrganizationId).toString(),
                    ),
                    idempotencyKey = "organization-trust:$operationId:${eventType.key}:$ownerOrganizationId",
                    businessTransactionId = businessTransactionId,
                ),
            )
        }
    }

    private fun hasOptimisticLockCause(throwable: Throwable): Boolean
    {
        var current: Throwable? = throwable
        while (current != null)
        {
            if (current is OptimisticLockException)
            {
                return true
            }
            current = current.cause
        }
        return false
    }

    companion object
    {
        private const val MAX_TEXT_LENGTH = 2000
        private const val TRUST_RELATIONSHIP_TARGET_TYPE = "ORGANIZATION_TRUST_RELATIONSHIP"
    }
}
