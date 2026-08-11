package com.docuhyphen.app.api.service.audit.export

import com.docuhyphen.app.api.model.entity.AuditExport
import com.docuhyphen.app.api.model.entity.AuditExportApproval
import com.docuhyphen.app.api.model.entity.AuditExportStatus
import com.docuhyphen.app.api.repository.AuditExportApprovalRepository
import com.docuhyphen.app.api.repository.AuditExportRepository
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.audit.AuditCaptureFailedException
import com.docuhyphen.app.api.service.audit.AuditDraftInvalidException
import com.docuhyphen.app.api.service.audit.AuditDeniedAttemptService
import com.docuhyphen.app.api.service.audit.AuditEngagementService
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditOwnerScope
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.AuditSearchProjectionService.AuditAccessActor
import com.docuhyphen.app.api.service.audit.archive.AuditArchiveStorage
import com.docuhyphen.app.api.service.audit.archive.MerkleTree
import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.service.audit.catalog.AuditCategory
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import com.docuhyphen.app.api.service.audit.requiresEngagementAccess
import com.docuhyphen.app.api.service.auth.authz.Capability
import com.docuhyphen.app.api.service.config.AuditExportConfigService
import com.docuhyphen.app.api.service.organization.OrganizationService
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

class AuditExportNotFoundException(message: String) : RuntimeException(message)

class AuditExportAccessException(message: String) : RuntimeException(message)

/**
 * State-machine and lifecycle-audit service for [AuditExport]: `REQUESTED` -> (`APPROVAL_PENDING` if dual control is
 * required) -> `BUILDING` -> `READY`, with `FAILED`/`EXPIRED`/`REVOKED` off-ramps. The actual
 * bundle construction is [AuditExportBuilder]'s concern; this service only owns the request/
 * approve/build-transition/download/revoke/expire lifecycle and its own audit trail - every
 * lifecycle transition is itself an audit event.
 */
@ApplicationScoped
class AuditExportService @Inject constructor(
    private val auditExportRepository: AuditExportRepository,
    private val auditExportApprovalRepository: AuditExportApprovalRepository,
    private val auditExportBuilder: AuditExportBuilder,
    private val archiveStorage: AuditArchiveStorage,
    private val configService: AuditExportConfigService,
    private val organizationService: OrganizationService,
    private val appUserService: AppUserService,
    private val auditEngagementService: AuditEngagementService,
    private val auditRecorder: AuditRecorder,
    private val auditDeniedAttemptService: AuditDeniedAttemptService,
    private val subscriptionGuard: OrganizationFeatureSubscriptionGuard,
)
{
    data class ExportRequest(
        val organizationId: UUID?,
        val categories: Set<AuditCategory>,
        val occurredAfter: Instant,
        val occurredBefore: Instant,
        val purpose: String,
        val caseReference: String? = null,
        val legalBasis: String? = null,
        val downloadLimit: Int? = null,
    )

    companion object
    {
        private val logger = LoggerFactory.getLogger(AuditExportService::class.java)
    }

    @Transactional
    fun requestExport(request: ExportRequest, actor: AuditAccessActor): AuditExport
    {
        subscriptionGuard.requireMutation(request.organizationId, PlanFeature.AUDIT_GOVERNANCE)
        validateRequest(request)
        val requestedByUserId = actor.principal.id
        appUserService.getById(requestedByUserId) ?: throw IllegalArgumentException("Requester not found")
        request.organizationId?.let { organizationService.getOrganizationById(it) }
        requireEngagementPermitsExport(actor, request.organizationId, request.categories, request.occurredAfter, request.occurredBefore)

        val dualControlRequired = configService.isDualControlRequired()
        val now = Timestamp.from(Instant.now())
        val export = AuditExport().apply {
            organizationId = request.organizationId
            this.requestedByUserId = requestedByUserId
            requestedAt = now
            categoriesCsv = request.categories.joinToString(",") { it.name }
            occurredAfter = Timestamp.from(request.occurredAfter)
            occurredBefore = Timestamp.from(request.occurredBefore)
            purpose = request.purpose.trim()
            caseReference = request.caseReference?.trim()?.takeIf { it.isNotBlank() }
            legalBasis = request.legalBasis?.trim()?.takeIf { it.isNotBlank() }
            downloadLimit = request.downloadLimit ?: configService.getDefaultDownloadLimit()
            requiredApprovals = if (dualControlRequired) configService.getRequiredApprovals() else 0
            status = if (dualControlRequired) AuditExportStatus.APPROVAL_PENDING else AuditExportStatus.BUILDING
            createdAt = now
            updatedAt = now
        }
        val saved = auditExportRepository.insert(export)
        recordLifecycleEvent(AuditEventType.AUDIT_EXPORT_REQUESTED, requestedByUserId, saved, AuditOutcome.SUCCESS)
        return saved
    }

    /**
     * Locks the export row (`SELECT ... FOR UPDATE`) before checking and advancing its state, so
     * two concurrent approvals cannot race on the `APPROVAL_PENDING -> BUILDING` transition. The
     * approval count itself is recomputed from the append-only [AuditExportApproval] table rather
     * than incremented in memory, so it reflects every approval already committed under the lock -
     * not just the one this call is adding - and cannot regress from a stale in-memory read.
     */
    @Transactional
    fun approveExport(exportId: UUID, approvedByUserId: UUID, note: String? = null): AuditExport
    {
        val export = requireExportForUpdate(exportId)
        subscriptionGuard.requireMutation(export.organizationId, PlanFeature.AUDIT_GOVERNANCE)
        if (export.status != AuditExportStatus.APPROVAL_PENDING)
        {
            deny(approvedByUserId, export, "EXPORT_NOT_APPROVAL_PENDING")
            throw IllegalArgumentException("Only APPROVAL_PENDING exports can be approved")
        }
        if (export.requestedByUserId == approvedByUserId)
        {
            deny(approvedByUserId, export, "EXPORT_SELF_APPROVAL")
            throw AuditExportAccessException("The requester cannot approve their own export")
        }
        appUserService.getById(approvedByUserId) ?: throw IllegalArgumentException("Approver not found")
        if (auditExportApprovalRepository.hasApprovalFrom(exportId, approvedByUserId))
        {
            return export
        }

        auditExportApprovalRepository.insert(
            AuditExportApproval().apply {
                this.exportId = exportId
                this.approvedByUserId = approvedByUserId
                approvedAt = Timestamp.from(Instant.now())
                this.note = note
            },
        )
        export.approvalCount = auditExportApprovalRepository.countByExport(exportId).toInt()
        if (export.approvalCount >= export.requiredApprovals)
        {
            export.status = AuditExportStatus.BUILDING
        }
        export.updatedAt = Timestamp.from(Instant.now())
        val saved = auditExportRepository.update(export)
        recordLifecycleEvent(AuditEventType.AUDIT_EXPORT_APPROVED, approvedByUserId, saved, AuditOutcome.SUCCESS)
        return saved
    }

    @Transactional
    fun revokeExport(exportId: UUID, revokedByUserId: UUID): AuditExport
    {
        val export = requireExport(exportId)
        require(
            export.status in setOf(
                AuditExportStatus.REQUESTED, AuditExportStatus.APPROVAL_PENDING,
                AuditExportStatus.BUILDING, AuditExportStatus.READY,
            ),
        ) { "Export is already in a terminal state" }
        appUserService.getById(revokedByUserId) ?: throw IllegalArgumentException("Revoker not found")

        export.status = AuditExportStatus.REVOKED
        export.revokedByUserId = revokedByUserId
        export.revokedAt = Timestamp.from(Instant.now())
        export.updatedAt = Timestamp.from(Instant.now())
        val saved = auditExportRepository.update(export)
        recordLifecycleEvent(AuditEventType.AUDIT_EXPORT_REVOKED, revokedByUserId, saved, AuditOutcome.SUCCESS)
        return saved
    }

    /**
     * Attempts to claim exclusive ownership of one `BUILDING` export's archive-build work for
     * [leaseDuration], under the identity [workerId]. Locks the row, then re-checks under the lock
     * that it is still `BUILDING` and that any existing lease has already expired (or never
     * existed) before writing a new lease - so two application nodes racing the same export never
     * both proceed, and a node that dies mid-build does not block the export forever once its
     * lease expires. Returns null, with no state change, when the export is no longer `BUILDING` or
     * another worker's lease is still live.
     */
    @Transactional
    fun claimForBuilding(exportId: UUID, workerId: String, leaseDuration: Duration): AuditExport?
    {
        val export = auditExportRepository.findByIdForUpdate(exportId) ?: return null
        if (export.status != AuditExportStatus.BUILDING)
        {
            return null
        }
        val existingLease = export.buildLeaseExpiresAt
        if (existingLease != null && existingLease.toInstant().isAfter(Instant.now()))
        {
            return null
        }

        export.buildWorkerId = workerId
        export.buildLeaseExpiresAt = Timestamp.from(Instant.now().plus(leaseDuration))
        export.updatedAt = Timestamp.from(Instant.now())
        return auditExportRepository.update(export)
    }

    /** Processes exactly one `BUILDING` export: builds the bundle, or transitions to `FAILED` without throwing on integrity/storage failure. Called by [AuditExportScheduler]. */
    fun processBuilding(export: AuditExport)
    {
        val workerId = export.buildWorkerId
        try
        {
            val result = auditExportBuilder.build(export)
            completeBuild(export.id, workerId, result)?.let { completed ->
                recordLifecycleEvent(AuditEventType.AUDIT_EXPORT_READY, null, completed, AuditOutcome.SUCCESS)
            }
        }
        catch (e: Exception)
        {
            logger.error("audit export build failed exportId={}: {}", export.id, e.message, e)
            failBuild(export.id, workerId, e.message ?: e.javaClass.simpleName)?.let { failed ->
                recordLifecycleEvent(AuditEventType.AUDIT_EXPORT_FAILED, null, failed, AuditOutcome.FAILURE)
            }
        }
    }

    @Transactional
    fun completeBuild(
        exportId: UUID,
        workerId: String?,
        result: AuditExportBundleResult,
    ): AuditExport?
    {
        val export = auditExportRepository.findByIdForUpdate(exportId) ?: return null
        if (export.status != AuditExportStatus.BUILDING || export.buildWorkerId != workerId)
        {
            return null
        }
        val now = Instant.now()
        export.status = AuditExportStatus.READY
        export.builtAt = Timestamp.from(now)
        export.readyAt = Timestamp.from(now)
        export.expiresAt = Timestamp.from(now.plusSeconds(configService.getDownloadLifetimeHours() * 3600L))
        export.eventCount = result.eventCount
        export.bundleObjectKey = result.bundleObjectKey
        export.bundleDigest = result.bundleDigest
        export.signingKeyId = result.signingKeyId
        export.buildLeaseExpiresAt = null
        export.updatedAt = Timestamp.from(now)
        return auditExportRepository.update(export)
    }

    @Transactional
    fun failBuild(exportId: UUID, workerId: String?, reason: String): AuditExport?
    {
        val export = auditExportRepository.findByIdForUpdate(exportId) ?: return null
        if (export.status != AuditExportStatus.BUILDING || export.buildWorkerId != workerId)
        {
            return null
        }
        export.status = AuditExportStatus.FAILED
        export.failedAt = Timestamp.from(Instant.now())
        export.failureReason = reason
        export.buildLeaseExpiresAt = null
        export.updatedAt = Timestamp.from(Instant.now())
        return auditExportRepository.update(export)
    }

    @Transactional
    fun expireDue(now: Instant = Instant.now()): Int
    {
        val due = auditExportRepository.findDueForExpiryForUpdate(Timestamp.from(now))
        due.forEach { export ->
            export.status = AuditExportStatus.EXPIRED
            export.updatedAt = Timestamp.from(now)
            auditExportRepository.update(export)
            recordLifecycleEvent(AuditEventType.AUDIT_EXPORT_EXPIRED, null, export, AuditOutcome.SUCCESS)
        }
        return due.size
    }

    /**
     * Authorizes and records one bundle download, or throws if the export is not downloadable
     * right now. Locks the export row before checking the download limit so two concurrent
     * downloads of the same export cannot both pass a stale limit check and exceed it.
     */
    @Transactional
    fun recordDownload(exportId: UUID, actor: AuditAccessActor): AuditExport
    {
        val export = requireExportForUpdate(exportId)
        if (export.status != AuditExportStatus.READY)
        {
            deny(actor.principal.id, export, "EXPORT_NOT_READY")
            throw AuditExportAccessException("Export is not ready for download")
        }
        val expiresAt = export.expiresAt
        if (expiresAt != null && expiresAt.toInstant().isBefore(Instant.now()))
        {
            deny(actor.principal.id, export, "EXPORT_EXPIRED")
            throw AuditExportAccessException("Export download window has expired")
        }
        val limit = export.downloadLimit
        if (limit != null && export.downloadCount >= limit)
        {
            deny(actor.principal.id, export, "EXPORT_DOWNLOAD_LIMIT_REACHED")
            throw AuditExportAccessException("Export download limit has been reached")
        }
        requireDownloadAuthorized(export, actor)
        requireEngagementPermitsExport(actor, export.organizationId, parseCategories(export.categoriesCsv), occurredAfter = null, occurredBefore = null)

        export.downloadCount += 1
        export.updatedAt = Timestamp.from(Instant.now())
        val saved = auditExportRepository.update(export)
        recordLifecycleEvent(AuditEventType.AUDIT_EXPORT_DOWNLOADED, actor.principal.id, saved, AuditOutcome.SUCCESS)
        return saved
    }

    /**
     * Validates + records the download ([recordDownload]) then returns the bundle bytes, refusing
     * to serve an object whose current digest no longer matches the digest recorded when the
     * bundle was built - the last check before evidence leaves the system. Never caches bytes on
     * the entity - the object lives only in [AuditArchiveStorage]. Deliberately not itself
     * `@Transactional`: [recordDownload] and [recordDownloadIntegrityFailure] each commit their own
     * audit trail row in an independent transaction, so a rejected download still leaves a durable
     * record of the rejection rather than rolling it back along with the (already-recorded) download
     * count increment.
     */
    fun downloadBundle(exportId: UUID, actor: AuditAccessActor): ByteArray
    {
        val export = recordDownload(exportId, actor)
        val key = export.bundleObjectKey ?: throw AuditExportAccessException("Export bundle is not available")
        val bytes = archiveStorage.getObject(key)
        val expectedDigest = export.bundleDigest
        if (expectedDigest != null && MerkleTree.sha256Hex(bytes) != expectedDigest)
        {
            recordDownloadIntegrityFailure(exportId, actor.principal.id)
            throw AuditExportIntegrityFailedException(
                "Downloaded bundle object does not match its recorded digest; refusing to serve possibly tampered evidence",
            )
        }
        return bytes
    }

    @Transactional
    fun recordDownloadIntegrityFailure(exportId: UUID, actorId: UUID)
    {
        val export = requireExport(exportId)
        recordLifecycleEvent(AuditEventType.AUDIT_EXPORT_DOWNLOAD_INTEGRITY_FAILED, actorId, export, AuditOutcome.FAILURE)
    }

    fun getExport(exportId: UUID): AuditExport = requireExport(exportId)

    fun listForOrganization(organizationId: UUID?, platformOnly: Boolean): List<AuditExport> =
        auditExportRepository.listForOrganization(organizationId, platformOnly)

    fun listApprovals(exportId: UUID): List<AuditExportApproval> = auditExportApprovalRepository.findByExport(exportId)

    private fun requireExport(exportId: UUID): AuditExport =
        auditExportRepository.findById(exportId) ?: throw AuditExportNotFoundException("Audit export not found")

    private fun requireExportForUpdate(exportId: UUID): AuditExport =
        auditExportRepository.findByIdForUpdate(exportId) ?: throw AuditExportNotFoundException("Audit export not found")

    /**
     * Every export is built at full fidelity ([AuditExportBuilder] applies no redaction), so
     * every export request or download requires either a sensitive-level export-permitted
     * engagement, or a fresh step-up when the caller's capabilities already bypass the engagement
     * requirement through direct organizational governance or platform scope
     * ([requiresEngagementAccess]). Re-run at both request time and download time because an
     * engagement can expire or be revoked, and a step-up session can go stale, between the two.
     */
    private fun requireEngagementPermitsExport(
        actor: AuditAccessActor,
        organizationId: UUID?,
        categories: Set<AuditCategory>,
        occurredAfter: Instant?,
        occurredBefore: Instant?,
    )
    {
        if (!requiresEngagementAccess(actor.capabilities, organizationId, platformOnly = false))
        {
            if (!actor.context.mfaSatisfied)
            {
                recordDeniedAttempt(
                    actor.principal.id,
                    organizationId,
                    organizationId?.toString() ?: "platform",
                    "EXPORT_STEP_UP_REQUIRED",
                )
                throw AuditExportAccessException(
                    "Recent step-up authentication is required to export full-fidelity audit evidence",
                )
            }
            return
        }
        val requestedRange = if (occurredAfter != null && occurredBefore != null)
        {
            Duration.between(occurredAfter, occurredBefore)
        }
        else
        {
            null
        }
        categories.forEach { category ->
            val access = auditEngagementService.resolveAccess(
                principalUserId = actor.principal.id,
                organizationId = organizationId,
                resourceType = null,
                resourceId = null,
                category = category,
                requireSensitive = true,
                recentStepUpSatisfied = actor.context.mfaSatisfied,
                requestedRange = requestedRange,
            )
            if (access == null || !access.exportPermitted)
            {
                recordDeniedAttempt(
                    actor.principal.id,
                    organizationId,
                    organizationId?.toString() ?: "platform",
                    "EXPORT_ENGAGEMENT_REQUIRED",
                )
                throw AuditExportAccessException(
                    "No active export-permitted sensitive engagement covers category ${category.name}",
                )
            }
        }
    }

    /**
     * A download is requester-only by default: the original requester always may download their
     * own export. An organization export additionally allows an organization governance principal
     * ([Capability.ORG_POLICY_MANAGE]) to download on behalf of the organization; a platform export
     * additionally allows [Capability.APP_ADMIN]. This is revalidated on every download, not just
     * at request time, so a capability revoked between request and download takes effect
     * immediately.
     */
    private fun requireDownloadAuthorized(export: AuditExport, actor: AuditAccessActor)
    {
        if (actor.principal.id == export.requestedByUserId)
        {
            return
        }
        val isCustodian = if (export.organizationId != null)
        {
            Capability.ORG_POLICY_MANAGE in actor.capabilities
        }
        else
        {
            Capability.APP_ADMIN in actor.capabilities
        }
        if (!isCustodian)
        {
            deny(actor.principal.id, export, "EXPORT_DOWNLOAD_FORBIDDEN")
            throw AuditExportAccessException(
                "Only the original requester or an authorized evidence custodian can download this export",
            )
        }
    }

    fun recordDeniedAttempt(actorId: UUID, organizationId: UUID?, targetId: String, reasonCode: String)
    {
        auditDeniedAttemptService.record(actorId, organizationId, "AUDIT_EXPORT", targetId, reasonCode)
    }

    private fun deny(actorId: UUID, export: AuditExport, reasonCode: String)
    {
        recordDeniedAttempt(actorId, export.organizationId, export.id.toString(), reasonCode)
    }

    private fun parseCategories(csv: String): Set<AuditCategory> =
        csv.split(",")
            .mapNotNull { raw -> raw.trim().takeIf { it.isNotBlank() }?.let(AuditCategory::valueOf) }
            .toSet()

    private fun validateRequest(request: ExportRequest)
    {
        require(request.categories.isNotEmpty()) { "At least one audit category is required" }
        require(request.purpose.isNotBlank()) { "Purpose is required" }
        require(request.occurredBefore.isAfter(request.occurredAfter)) { "occurredBefore must be after occurredAfter" }
        val requestedRange = java.time.Duration.between(request.occurredAfter, request.occurredBefore)
        require(requestedRange <= java.time.Duration.ofDays(configService.getMaxRangeDays().toLong())) {
            "Requested range exceeds the maximum of ${configService.getMaxRangeDays()} days"
        }
        request.downloadLimit?.let { require(it > 0) { "Download limit must be positive" } }
    }

    private fun recordLifecycleEvent(
        eventType: AuditEventType,
        actorId: UUID?,
        export: AuditExport,
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
                    owner = export.organizationId?.let(AuditOwnerScope::Organization) ?: AuditOwnerScope.Platform,
                    targetType = "AUDIT_EXPORT",
                    targetId = export.id.toString(),
                    targetLabel = export.caseReference?.let { "${export.purpose} ($it)" } ?: export.purpose,
                    payload = buildMap {
                        put("status", export.status.name)
                        put("categories", export.categoriesCsv)
                        export.eventCount?.let { put("event_count", it.toString()) }
                        export.failureReason?.let { put("failure_reason", it) }
                    },
                ),
            )
        }
        catch (e: AuditDraftInvalidException)
        {
            logger.warn("AuditExportService: AuditRecorder rejected lifecycle event {}: {}", eventType, e.message)
        }
        catch (e: AuditCaptureFailedException)
        {
            logger.error("AuditExportService: AuditRecorder capture failed for lifecycle event {}: {}", eventType, e.message, e)
        }
    }
}
