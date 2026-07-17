package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.exception.OrganizationTrustValidationException
import com.docuhyphen.app.api.model.dto.OrganizationTrustPoliciesDto
import com.docuhyphen.app.api.model.dto.OrganizationTrustRelationshipDto
import com.docuhyphen.app.api.model.entity.OrganizationTrustDecision
import com.docuhyphen.app.api.resource.model.OrganizationTrustPolicyUpdateRequest
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.time.Instant
import java.util.UUID

@ApplicationScoped
class OrganizationTrustCommandService @Inject constructor(
    private val relationshipService: OrganizationTrustRelationshipService,
    private val policyService: OrganizationTrustPolicyService,
    private val queryService: OrganizationTrustQueryService,
    private val notificationService: OrganizationTrustNotificationService,
)
{
    fun requestRelationship(
        targetOrganizationId: UUID,
        requestMessage: String?,
    ): OrganizationTrustRelationshipDto
    {
        val outcome = relationshipService.requestRelationship(targetOrganizationId, requestMessage)
        notificationService.publish(outcome.eventType, outcome.relationship)
        return queryService.getRelationship(outcome.relationship.id)
    }

    fun decide(
        relationshipId: UUID,
        decision: OrganizationTrustDecision,
        reason: String?,
        expectedVersion: Long,
    ): OrganizationTrustRelationshipDto
    {
        val relationship = relationshipService.decide(relationshipId, decision, reason, expectedVersion)
        notificationService.publish(
            if (decision == OrganizationTrustDecision.ACCEPT)
                AuditEventType.ORG_TRUST_ACCEPTED
            else
                AuditEventType.ORG_TRUST_REJECTED,
            relationship,
        )
        return queryService.getRelationship(relationship.id)
    }

    fun withdraw(
        relationshipId: UUID,
        reason: String?,
        expectedVersion: Long,
    ): OrganizationTrustRelationshipDto
    {
        val relationship = relationshipService.withdraw(relationshipId, reason, expectedVersion)
        notificationService.publish(AuditEventType.ORG_TRUST_WITHDRAWN, relationship)
        return queryService.getRelationship(relationship.id)
    }

    fun suspend(relationshipId: UUID, reason: String?): OrganizationTrustRelationshipDto
    {
        relationshipService.suspend(
            relationshipId,
            reason ?: throw OrganizationTrustValidationException("Suspension reason is required"),
        )
        val relationship = relationshipService.getById(relationshipId)
        notificationService.publish(AuditEventType.ORG_TRUST_SUSPENDED, relationship)
        return queryService.getRelationship(relationshipId)
    }

    fun resume(relationshipId: UUID, suspensionId: UUID): OrganizationTrustRelationshipDto
    {
        relationshipService.resume(relationshipId, suspensionId)
        val relationship = relationshipService.getById(relationshipId)
        notificationService.publish(AuditEventType.ORG_TRUST_RESUMED, relationship)
        return queryService.getRelationship(relationshipId)
    }

    fun terminate(
        relationshipId: UUID,
        reason: String?,
        expectedVersion: Long,
    ): OrganizationTrustRelationshipDto
    {
        val relationship = relationshipService.terminate(relationshipId, reason, expectedVersion)
        notificationService.publish(AuditEventType.ORG_TRUST_ENDED, relationship)
        return queryService.getRelationship(relationship.id)
    }

    fun updatePolicy(
        relationshipId: UUID,
        policyId: UUID,
        request: OrganizationTrustPolicyUpdateRequest,
    ): OrganizationTrustPoliciesDto
    {
        policyService.updatePolicy(
            relationshipId,
            policyId,
            request.expectedRevision,
            OrganizationTrustPolicyUpdate(
                allowExchangesToPartner = request.allowExchangesToPartner,
                allowExchangesFromPartner = request.allowExchangesFromPartner,
                allowPartnerMemberResolution = request.allowPartnerMemberResolution,
                allowPartnerGroupDiscovery = request.allowPartnerGroupDiscovery,
                shareMemberDisplayName = request.shareMemberDisplayName,
                expiresAt = request.expiresAt?.let(Instant::ofEpochMilli),
                reviewDueAt = request.reviewDueAt?.let(Instant::ofEpochMilli),
            ),
        )
        val relationship = relationshipService.getById(relationshipId)
        notificationService.publish(AuditEventType.ORG_TRUST_POLICY_UPDATED, relationship)
        return queryService.getPolicies(relationshipId)
    }
}
