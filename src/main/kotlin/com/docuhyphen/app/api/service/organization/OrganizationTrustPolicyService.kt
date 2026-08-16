package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.exception.OrganizationTrustAuthorizationException
import com.docuhyphen.app.api.exception.OrganizationTrustConflictException
import com.docuhyphen.app.api.exception.OrganizationTrustNotFoundException
import com.docuhyphen.app.api.exception.OrganizationTrustStaleVersionException
import com.docuhyphen.app.api.exception.OrganizationTrustValidationException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.OrganizationTrustPartyPolicy
import com.docuhyphen.app.api.model.entity.OrganizationTrustRelationship
import com.docuhyphen.app.api.model.entity.OrganizationTrustRelationshipStatus
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.repository.organization.OrganizationTrustPartyPolicyRepository
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

data class OrganizationTrustPolicyUpdate(
    val allowExchangesToPartner: Boolean,
    val allowExchangesFromPartner: Boolean,
    val allowPartnerMemberResolution: Boolean,
    val allowPartnerGroupDiscovery: Boolean,
    val shareMemberDisplayName: Boolean,
    val expiresAt: Instant?,
    val reviewDueAt: Instant?,
)

@ApplicationScoped
class OrganizationTrustPolicyService @Inject constructor(
    private val policyRepository: OrganizationTrustPartyPolicyRepository,
    private val authorizationService: AuthorizationService,
    private val authorizationContextFactory: AuthorizationContextFactory,
    private val authTokenContext: AuthTokenContext,
    private val auditRecorder: AuditRecorder,
    private val relationshipServiceProvider: Provider<OrganizationTrustRelationshipService>,
    private val subscriptionGuard: OrganizationFeatureSubscriptionGuard,
)
{
    @Transactional
    fun initializeDefaultPolicies(
        relationship: OrganizationTrustRelationship,
        now: Instant = Instant.now(),
    ): List<OrganizationTrustPartyPolicy>
    {
        if (policyRepository.findByRelationship(relationship.id).isNotEmpty())
        {
            throw OrganizationTrustConflictException("Trust relationship policies already exist")
        }
        val updatedAt = Timestamp.from(now)
        val policies = listOf(relationship.organizationAId, relationship.organizationBId).map { organizationId ->
            OrganizationTrustPartyPolicy().apply {
                relationshipId = relationship.id
                policyOwnerOrganizationId = organizationId
                allowExchangesToPartner = false
                allowExchangesFromPartner = false
                allowPartnerMemberResolution = false
                allowPartnerGroupDiscovery = false
                shareMemberDisplayName = false
                this.updatedAt = updatedAt
            }
        }
        return try
        {
            policyRepository.insertAllAndFlush(policies)
        }
        catch (exception: PersistenceException)
        {
            throw OrganizationTrustConflictException("Unable to initialize trust relationship policies", exception)
        }
    }

    fun getPolicies(relationshipId: UUID): List<OrganizationTrustPartyPolicy> =
        policyRepository.findByRelationship(relationshipId)

    fun getPolicy(relationshipId: UUID, ownerOrganizationId: UUID): OrganizationTrustPartyPolicy =
        policyRepository.findByRelationshipAndOwner(relationshipId, ownerOrganizationId)
            ?: throw OrganizationTrustNotFoundException("Trust relationship policy not found")

    @Transactional
    fun updatePolicy(
        relationshipId: UUID,
        policyId: UUID,
        expectedRevision: Long,
        update: OrganizationTrustPolicyUpdate,
        now: Instant = Instant.now(),
    ): OrganizationTrustPartyPolicy
    {
        val relationship = relationshipServiceProvider.get().getById(relationshipId)
        val activeOrganizationId = authTokenContext.activeOrganizationId
            ?: throw OrganizationTrustAuthorizationException("An active organization is required")
        val principal = authorizationContextFactory.currentPrincipal()
            ?: throw OrganizationTrustAuthorizationException("Authentication is required")
        if (principal.kind != PrincipalKind.USER)
        {
            throw OrganizationTrustAuthorizationException("Only an organization member can manage trust policy")
        }
        val authorizationDecision = authorizationService.authorize(
            principal = principal,
            action = Action.ORG_TRUST_MANAGE_POLICY,
            resource = ResourceRef.organization(activeOrganizationId),
            context = authorizationContextFactory.currentContext(),
        )
        if (authorizationDecision is Decision.Deny)
        {
            throw OrganizationTrustAuthorizationException()
        }
        if (!relationship.includes(activeOrganizationId))
        {
            throw OrganizationTrustNotFoundException()
        }
        subscriptionGuard.requireMutation(activeOrganizationId, PlanFeature.IDENTITY_AND_INTEGRATIONS)
        if (relationship.status != OrganizationTrustRelationshipStatus.PENDING &&
            relationship.status != OrganizationTrustRelationshipStatus.ACTIVE)
        {
            throw OrganizationTrustConflictException("A terminal trust relationship policy cannot be changed")
        }
        if (relationship.status == OrganizationTrustRelationshipStatus.PENDING &&
            !relationship.requestExpiresAt.toInstant().isAfter(now))
        {
            throw OrganizationTrustConflictException("An expired trust request policy cannot be changed")
        }

        val policy = policyRepository.findById(policyId)
            ?: throw OrganizationTrustNotFoundException("Trust relationship policy not found")
        if (policy.relationshipId != relationshipId)
        {
            throw OrganizationTrustNotFoundException("Trust relationship policy not found")
        }
        if (policy.policyOwnerOrganizationId != activeOrganizationId)
        {
            throw OrganizationTrustAuthorizationException("An organization can update only its own policy")
        }
        if (expectedRevision < 0 || policy.revision != expectedRevision)
        {
            throw OrganizationTrustStaleVersionException("The trust policy was changed")
        }
        validateDates(update, now)

        val changedFields = changedFields(policy, update)
        policy.allowExchangesToPartner = update.allowExchangesToPartner
        policy.allowExchangesFromPartner = update.allowExchangesFromPartner
        policy.allowPartnerMemberResolution = update.allowPartnerMemberResolution
        policy.allowPartnerGroupDiscovery = update.allowPartnerGroupDiscovery
        policy.shareMemberDisplayName = update.shareMemberDisplayName
        policy.expiresAt = update.expiresAt?.let(Timestamp::from)
        policy.reviewDueAt = update.reviewDueAt?.let(Timestamp::from)
        policy.updatedByAppUserId = principal.id
        policy.updatedAt = Timestamp.from(now)

        val saved = try
        {
            policyRepository.updateAndFlush(policy)
        }
        catch (exception: PersistenceException)
        {
            if (hasOptimisticLockCause(exception))
            {
                throw OrganizationTrustStaleVersionException("The trust policy was changed")
            }
            throw exception
        }
        recordPolicyUpdate(relationship, saved, principal.id, changedFields)
        return saved
    }

    private fun validateDates(update: OrganizationTrustPolicyUpdate, now: Instant)
    {
        if (update.expiresAt != null && !update.expiresAt.isAfter(now))
        {
            throw OrganizationTrustValidationException("Policy expiry must be in the future")
        }
        if (update.reviewDueAt != null && !update.reviewDueAt.isAfter(now))
        {
            throw OrganizationTrustValidationException("Policy review date must be in the future")
        }
    }

    private fun changedFields(
        policy: OrganizationTrustPartyPolicy,
        update: OrganizationTrustPolicyUpdate,
    ): List<String> = buildList {
        if (policy.allowExchangesToPartner != update.allowExchangesToPartner) add("allowExchangesToPartner")
        if (policy.allowExchangesFromPartner != update.allowExchangesFromPartner) add("allowExchangesFromPartner")
        if (policy.allowPartnerMemberResolution != update.allowPartnerMemberResolution)
            add("allowPartnerMemberResolution")
        if (policy.allowPartnerGroupDiscovery != update.allowPartnerGroupDiscovery) add("allowPartnerGroupDiscovery")
        if (policy.shareMemberDisplayName != update.shareMemberDisplayName) add("shareMemberDisplayName")
        if (policy.expiresAt?.toInstant() != update.expiresAt) add("expiresAt")
        if (policy.reviewDueAt?.toInstant() != update.reviewDueAt) add("reviewDueAt")
    }

    private fun recordPolicyUpdate(
        relationship: OrganizationTrustRelationship,
        policy: OrganizationTrustPartyPolicy,
        actorId: UUID,
        changedFields: List<String>,
    )
    {
        val businessTransactionId = UUID.randomUUID().toString()
        listOf(relationship.organizationAId, relationship.organizationBId).forEach { ownerOrganizationId ->
            auditRecorder.record(
                AuditEventDraft(
                    owner = AuditOwnerScope.Organization(ownerOrganizationId),
                    eventTypeKey = AuditEventType.ORG_TRUST_POLICY_UPDATED.key,
                    outcome = AuditOutcome.SUCCESS,
                    actorId = actorId,
                    actorKind = AuditActorKind.HUMAN,
                    targetType = "ORGANIZATION_TRUST_PARTY_POLICY",
                    targetId = policy.id.toString(),
                    payload = mapOf(
                        "relationshipId" to relationship.id.toString(),
                        "policyOwnerOrganizationId" to policy.policyOwnerOrganizationId.toString(),
                        "changedFields" to changedFields.sorted().joinToString(","),
                    ),
                    idempotencyKey = "organization-trust-policy:${policy.id}:${policy.revision}:$ownerOrganizationId",
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
}
