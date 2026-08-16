package com.docuhyphen.app.api.service.audit

import com.docuhyphen.app.api.model.entity.AuditEngagement
import com.docuhyphen.app.api.model.entity.AuditEngagementSensitivity
import com.docuhyphen.app.api.model.entity.AuditEngagementStatus
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.repository.audit.AuditEngagementRepository
import com.docuhyphen.app.api.service.user.AppUserService
import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.service.audit.catalog.AuditCategory
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import com.docuhyphen.app.api.service.auth.StepUpAuthService
import com.docuhyphen.app.api.service.organization.OrganizationGroupService
import com.docuhyphen.app.api.service.organization.OrganizationMembershipService
import com.docuhyphen.app.api.service.organization.OrganizationService
import com.docuhyphen.app.api.service.organization.PrincipalGroupService
import com.docuhyphen.app.api.service.subscription.OrganizationFeatureSubscriptionGuard
import com.docuhyphen.app.api.service.subscription.PlanFeature
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.UUID

@ApplicationScoped
class AuditEngagementService @Inject constructor(
    private val auditEngagementRepository: AuditEngagementRepository,
    private val organizationService: OrganizationService,
    private val appUserService: AppUserService,
    private val organizationGroupService: OrganizationGroupService,
    private val organizationMembershipService: OrganizationMembershipService,
    private val principalGroupService: PrincipalGroupService,
    private val stepUpAuthService: StepUpAuthService,
    private val auditRecorder: AuditRecorder,
    private val subscriptionGuard: OrganizationFeatureSubscriptionGuard,
)
{
    data class AuditEngagementRequest(
        val organizationId: UUID?,
        val resourceType: String? = null,
        val resourceId: String? = null,
        val auditorUserId: UUID? = null,
        val principalGroupId: UUID? = null,
        val categories: Set<AuditCategory>,
        val sensitivityLevel: AuditEngagementSensitivity,
        val startsAt: Instant,
        val expiresAt: Instant,
        val purpose: String,
        val caseReference: String? = null,
        val legalBasis: String,
        val exportPermitted: Boolean = false,
        val maxQueryRangeDays: Int? = null,
        val downloadLimit: Int? = null,
    )

    data class EngagementAccess(
        val engagementId: UUID,
        val sensitivityLevel: AuditEngagementSensitivity,
        val exportPermitted: Boolean,
        val maxQueryRangeDays: Int?,
        val downloadLimit: Int?,
    )

    companion object
    {
        private val logger = LoggerFactory.getLogger(AuditEngagementService::class.java)
    }

    @Transactional
    fun requestEngagement(
        request: AuditEngagementRequest,
        requestedByUserId: UUID,
        recentStepUpSatisfied: Boolean? = null,
    ): AuditEngagement
    {
        validateRequest(request)
        requireRecentStepUpIfSensitive(request.sensitivityLevel, request.exportPermitted, recentStepUpSatisfied)
        appUserService.getById(requestedByUserId) ?: throw IllegalArgumentException("Requester not found")
        request.organizationId?.let { organizationId ->
            organizationService.getOrganizationById(organizationId)
            subscriptionGuard.requireMutation(organizationId, PlanFeature.AUDIT_GOVERNANCE)
        }
        request.auditorUserId?.let { auditorUserId ->
            appUserService.getById(auditorUserId) ?: throw IllegalArgumentException("Auditor user not found")
            request.organizationId?.let { organizationId ->
                require(organizationMembershipService.isMember(auditorUserId, organizationId)) {
                    "Auditor user does not belong to the engagement organization"
                }
            }
        }
        request.principalGroupId?.let { principalGroupId ->
            val group = organizationGroupService.getById(principalGroupId.toString())
                ?: throw IllegalArgumentException("Principal group not found")
            request.organizationId?.let { organizationId ->
                require(group.ownerOrganizationId == organizationId) {
                    "Principal group does not belong to the engagement organization"
                }
            }
        }

        val now = Timestamp.from(Instant.now())
        val engagement = AuditEngagement().apply {
            organizationId = request.organizationId
            resourceType = request.resourceType?.trim()?.takeIf { it.isNotBlank() }
            resourceId = request.resourceId?.trim()?.takeIf { it.isNotBlank() }
            auditorUserId = request.auditorUserId
            principalGroupId = request.principalGroupId
            categoriesCsv = request.categories.joinToString(",") { it.name }
            sensitivityLevel = request.sensitivityLevel
            startsAt = Timestamp.from(request.startsAt)
            expiresAt = Timestamp.from(request.expiresAt)
            purpose = request.purpose.trim()
            caseReference = request.caseReference?.trim()?.takeIf { it.isNotBlank() }
            legalBasis = request.legalBasis.trim()
            exportPermitted = request.exportPermitted
            maxQueryRangeDays = request.maxQueryRangeDays
            downloadLimit = request.downloadLimit
            this.requestedByUserId = requestedByUserId
            this.requestedAt = now
            this.status = AuditEngagementStatus.REQUESTED
            this.createdAt = now
            this.updatedAt = now
        }
        val saved = auditEngagementRepository.save(engagement)
        recordLifecycleEvent(
            eventType = AuditEventType.AUDIT_ENGAGEMENT_REQUESTED,
            actorId = requestedByUserId,
            engagement = saved,
            outcome = AuditOutcome.SUCCESS,
        )
        return saved
    }

    @Transactional
    fun approveEngagement(
        engagementId: UUID,
        expectedOrganizationId: UUID?,
        approvedByUserId: UUID,
        recentStepUpSatisfied: Boolean? = null,
    ): AuditEngagement
    {
        val engagement = requireEngagement(engagementId, expectedOrganizationId)
        subscriptionGuard.requireMutation(engagement.organizationId, PlanFeature.AUDIT_GOVERNANCE)
        appUserService.getById(approvedByUserId) ?: throw IllegalArgumentException("Approver not found")
        require(engagement.status == AuditEngagementStatus.REQUESTED) {
            "Only REQUESTED engagements can be approved"
        }
        requireRecentStepUpIfSensitive(engagement.sensitivityLevel, engagement.exportPermitted, recentStepUpSatisfied)

        val now = Timestamp.from(Instant.now())
        engagement.status = AuditEngagementStatus.ACTIVE
        engagement.approvedByUserId = approvedByUserId
        engagement.approvedAt = now
        engagement.updatedAt = now
        val saved = auditEngagementRepository.update(engagement)
        recordLifecycleEvent(
            eventType = AuditEventType.AUDIT_ENGAGEMENT_APPROVED,
            actorId = approvedByUserId,
            engagement = saved,
            outcome = AuditOutcome.SUCCESS,
        )
        return saved
    }

    @Transactional
    fun revokeEngagement(
        engagementId: UUID,
        expectedOrganizationId: UUID?,
        revokedByUserId: UUID,
        recentStepUpSatisfied: Boolean? = null,
    ): AuditEngagement
    {
        val engagement = requireEngagement(engagementId, expectedOrganizationId)
        appUserService.getById(revokedByUserId) ?: throw IllegalArgumentException("Revoker not found")
        require(engagement.status == AuditEngagementStatus.ACTIVE || engagement.status == AuditEngagementStatus.REQUESTED) {
            "Only REQUESTED or ACTIVE engagements can be revoked"
        }
        requireRecentStepUpIfSensitive(engagement.sensitivityLevel, engagement.exportPermitted, recentStepUpSatisfied)

        val now = Timestamp.from(Instant.now())
        engagement.status = AuditEngagementStatus.REVOKED
        engagement.revokedByUserId = revokedByUserId
        engagement.revokedAt = now
        engagement.updatedAt = now
        val saved = auditEngagementRepository.update(engagement)
        recordLifecycleEvent(
            eventType = AuditEventType.AUDIT_ENGAGEMENT_REVOKED,
            actorId = revokedByUserId,
            engagement = saved,
            outcome = AuditOutcome.SUCCESS,
        )
        return saved
    }

    @Transactional
    fun expireDue(now: Instant = Instant.now()): Int
    {
        val expiring = auditEngagementRepository.findDueForExpiryForUpdate(Timestamp.from(now))
        expiring.forEach { engagement ->
            engagement.status = AuditEngagementStatus.EXPIRED
            engagement.updatedAt = Timestamp.from(now)
            auditEngagementRepository.update(engagement)
            recordLifecycleEvent(
                eventType = AuditEventType.AUDIT_ENGAGEMENT_EXPIRED,
                actorId = null,
                engagement = engagement,
                outcome = AuditOutcome.SUCCESS,
            )
        }
        return expiring.size
    }

    fun resolveAccess(
        principalUserId: UUID,
        organizationId: UUID?,
        resourceType: String?,
        resourceId: String?,
        category: AuditCategory,
        requireSensitive: Boolean,
        recentStepUpSatisfied: Boolean? = null,
        requestedRange: Duration? = null,
    ): EngagementAccess?
    {
        val now = Instant.now()
        val groupIds = principalGroupService.getActiveGroupIdsForPrincipal(PrincipalKind.USER, principalUserId)
        val matches = auditEngagementRepository.findActiveForPrincipal(
            organizationId = organizationId,
            resourceType = resourceType,
            resourceId = resourceId,
            auditorUserId = principalUserId,
            principalGroupIds = groupIds,
            now = Timestamp.from(now),
        )

        val stepUpSatisfied = recentStepUpSatisfied
            ?: runCatching { stepUpAuthService.isFresh() }.getOrDefault(false)
        val access = matches
            .filter { it.status == AuditEngagementStatus.ACTIVE }
            .filter { !it.startsAt.toInstant().isAfter(now) && it.expiresAt.toInstant().isAfter(now) }
            .filter { matchesRequestedResource(it, resourceType, resourceId) }
            .filter { category in parseCategories(it.categoriesCsv) }
            .filter { !requireSensitive || it.sensitivityLevel == AuditEngagementSensitivity.SENSITIVE }
            .filter { it.sensitivityLevel != AuditEngagementSensitivity.SENSITIVE || stepUpSatisfied }
            .filter {
                requestedRange == null ||
                    it.maxQueryRangeDays == null ||
                    requestedRange <= Duration.ofDays(it.maxQueryRangeDays!!.toLong())
            }
            .maxByOrNull { sensitivityRank(it.sensitivityLevel) }
            ?: return null

        return EngagementAccess(
            engagementId = access.id,
            sensitivityLevel = access.sensitivityLevel,
            exportPermitted = access.exportPermitted,
            maxQueryRangeDays = access.maxQueryRangeDays,
            downloadLimit = access.downloadLimit,
        )
    }

    fun listForOrganization(organizationId: UUID?): List<AuditEngagement> =
        auditEngagementRepository.findByOrganization(organizationId)

    private fun requireEngagement(engagementId: UUID, expectedOrganizationId: UUID?): AuditEngagement
    {
        val engagement = auditEngagementRepository.findById(engagementId)
            ?: throw AuditEngagementNotFoundException()
        if (engagement.organizationId != expectedOrganizationId)
        {
            throw AuditEngagementNotFoundException()
        }
        return engagement
    }

    private fun validateRequest(request: AuditEngagementRequest)
    {
        require(request.auditorUserId != null || request.principalGroupId != null) {
            "At least one auditor principal or group is required"
        }
        require(request.categories.isNotEmpty()) { "At least one audit category is required" }
        require(request.purpose.isNotBlank()) { "Purpose is required" }
        require(request.legalBasis.isNotBlank()) { "Legal basis is required" }
        require(request.expiresAt.isAfter(request.startsAt)) { "Expiry must be after start" }
        request.maxQueryRangeDays?.let { require(it > 0) { "Max query range days must be positive" } }
        request.downloadLimit?.let { require(it > 0) { "Download limit must be positive" } }
    }

    private fun requireRecentStepUpIfSensitive(
        sensitivityLevel: AuditEngagementSensitivity,
        exportPermitted: Boolean,
        recentStepUpSatisfied: Boolean?,
    )
    {
        if (sensitivityLevel != AuditEngagementSensitivity.SENSITIVE && !exportPermitted)
        {
            return
        }

        val stepUpSatisfied = recentStepUpSatisfied ?: runCatching { stepUpAuthService.isFresh() }.getOrDefault(false)
        require(stepUpSatisfied) {
            "Recent step-up authentication is required for sensitive audit evidence or export approval"
        }
    }

    private fun matchesRequestedResource(
        engagement: AuditEngagement,
        resourceType: String?,
        resourceId: String?,
    ): Boolean
    {
        val engagementResourceType = engagement.resourceType ?: return true
        return resourceType != null && resourceId != null &&
            engagementResourceType == resourceType && engagement.resourceId == resourceId
    }

    private fun parseCategories(csv: String): Set<AuditCategory> =
        csv.split(",")
            .mapNotNull { raw -> raw.trim().takeIf { it.isNotBlank() }?.let(AuditCategory::valueOf) }
            .toSet()

    private fun sensitivityRank(sensitivityLevel: AuditEngagementSensitivity): Int = when (sensitivityLevel)
    {
        AuditEngagementSensitivity.METADATA_ONLY -> 0
        AuditEngagementSensitivity.STANDARD      -> 1
        AuditEngagementSensitivity.SENSITIVE     -> 2
    }

    private fun recordLifecycleEvent(
        eventType: AuditEventType,
        actorId: UUID?,
        engagement: AuditEngagement,
        outcome: AuditOutcome,
    )
    {
        try
        {
            auditRecorder.record(
                AuditEventDraft(
                    eventTypeKey = eventType.key,
                    outcome = outcome,
                    actorId = actorId,
                    actorKind = if (actorId == null) AuditActorKind.SYSTEM else AuditActorKind.HUMAN,
                    actorRole = if (actorId == null) "SYSTEM" else "AUDIT_GOVERNANCE",
                    owner = engagement.organizationId?.let(AuditOwnerScope::Organization) ?: AuditOwnerScope.Platform,
                    targetType = "AUDIT_ENGAGEMENT",
                    targetId = engagement.id.toString(),
                    targetLabel = engagement.caseReference?.let { "${engagement.purpose} ($it)" } ?: engagement.purpose,
                    payload = buildMap {
                        put("status", engagement.status.name)
                        put("sensitivity_level", engagement.sensitivityLevel.name)
                        put("categories", engagement.categoriesCsv)
                        engagement.resourceType?.let { put("resource_type", it) }
                        engagement.resourceId?.let { put("resource_id", it) }
                        engagement.caseReference?.let { put("case_reference", it) }
                    },
                )
            )
        }
        catch (e: AuditDraftInvalidException)
        {
            logger.warn("AuditEngagementService: AuditRecorder rejected lifecycle event {}: {}", eventType, e.message)
        }
        catch (e: AuditCaptureFailedException)
        {
            logger.error(
                "AuditEngagementService: AuditRecorder capture failed for lifecycle event {}: {}",
                eventType, e.message, e,
            )
        }
    }
}

class AuditEngagementNotFoundException : IllegalArgumentException("Audit engagement not found")
