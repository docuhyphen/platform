package com.docuhyphen.app.api.service.audit.export

import com.docuhyphen.app.api.model.entity.AuditExport
import com.docuhyphen.app.api.model.entity.AuditExportApproval
import com.docuhyphen.app.api.model.entity.AuditExportStatus
import com.docuhyphen.app.api.repository.AuditExportApprovalRepository
import com.docuhyphen.app.api.repository.AuditExportRepository
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.audit.AuditCaptureFailedException
import com.docuhyphen.app.api.service.audit.AuditDraftInvalidException
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.archive.AuditArchiveStorage
import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.service.audit.catalog.AuditCategory
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import com.docuhyphen.app.api.service.config.AuditExportConfigService
import com.docuhyphen.app.api.service.organization.OrganizationService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class AuditExportNotFoundException(message: String) : RuntimeException(message)

class AuditExportAccessException(message: String) : RuntimeException(message)

/**
 * State-machine + lifecycle-audit service for [AuditExport] (Phase 6 of
 * `AUDIT-ARCHITECTURE-IMPLEMENTATION.md`): `REQUESTED` -> (`APPROVAL_PENDING` if dual control is
 * required) -> `BUILDING` -> `READY`, with `FAILED`/`EXPIRED`/`REVOKED` off-ramps. The actual
 * bundle construction is [AuditExportBuilder]'s concern; this service only owns the request/
 * approve/build-transition/download/revoke/expire lifecycle and its own audit trail - every
 * lifecycle transition is itself an audit event (task 4).
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
    private val auditRecorder: AuditRecorder,
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
    fun requestExport(request: ExportRequest, requestedByUserId: UUID): AuditExport
    {
        validateRequest(request)
        appUserService.getById(requestedByUserId) ?: throw IllegalArgumentException("Requester not found")
        request.organizationId?.let { organizationService.getOrganizationById(it) }

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

    @Transactional
    fun approveExport(exportId: UUID, approvedByUserId: UUID, note: String? = null): AuditExport
    {
        val export = requireExport(exportId)
        require(export.status == AuditExportStatus.APPROVAL_PENDING) { "Only APPROVAL_PENDING exports can be approved" }
        require(export.requestedByUserId != approvedByUserId) { "The requester cannot approve their own export (dual control)" }
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
        export.approvalCount += 1
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

    /** Processes exactly one `BUILDING` export: builds the bundle, or transitions to `FAILED` without throwing on integrity/storage failure. Called by [AuditExportScheduler]. */
    @Transactional
    fun processBuilding(export: AuditExport)
    {
        try
        {
            auditExportBuilder.build(export, configService.getDownloadLifetimeHours())
            recordLifecycleEvent(AuditEventType.AUDIT_EXPORT_READY, null, export, AuditOutcome.SUCCESS)
        }
        catch (e: Exception)
        {
            logger.error("audit export build failed exportId={}: {}", export.id, e.message, e)
            export.status = AuditExportStatus.FAILED
            export.failedAt = Timestamp.from(Instant.now())
            export.failureReason = e.message ?: e.javaClass.simpleName
            export.updatedAt = Timestamp.from(Instant.now())
            auditExportRepository.update(export)
            recordLifecycleEvent(AuditEventType.AUDIT_EXPORT_FAILED, null, export, AuditOutcome.FAILURE)
        }
    }

    @Transactional
    fun expireDue(now: Instant = Instant.now()): Int
    {
        val due = auditExportRepository.findDueForExpiry(Timestamp.from(now))
        due.forEach { export ->
            export.status = AuditExportStatus.EXPIRED
            export.updatedAt = Timestamp.from(now)
            auditExportRepository.update(export)
            recordLifecycleEvent(AuditEventType.AUDIT_EXPORT_EXPIRED, null, export, AuditOutcome.SUCCESS)
        }
        return due.size
    }

    /** Authorizes and records one bundle download, or throws if the export is not downloadable right now. */
    @Transactional
    fun recordDownload(exportId: UUID, downloadedByUserId: UUID): AuditExport
    {
        val export = requireExport(exportId)
        if (export.status != AuditExportStatus.READY)
        {
            throw AuditExportAccessException("Export is not ready for download")
        }
        val expiresAt = export.expiresAt
        if (expiresAt != null && expiresAt.toInstant().isBefore(Instant.now()))
        {
            throw AuditExportAccessException("Export download window has expired")
        }
        val limit = export.downloadLimit
        if (limit != null && export.downloadCount >= limit)
        {
            throw AuditExportAccessException("Export download limit has been reached")
        }

        export.downloadCount += 1
        export.updatedAt = Timestamp.from(Instant.now())
        val saved = auditExportRepository.update(export)
        recordLifecycleEvent(AuditEventType.AUDIT_EXPORT_DOWNLOADED, downloadedByUserId, saved, AuditOutcome.SUCCESS)
        return saved
    }

    /** Validates + records the download ([recordDownload]) then returns the bundle bytes. Never caches bytes on the entity - the object lives only in [AuditArchiveStorage]. */
    @Transactional
    fun downloadBundle(exportId: UUID, downloadedByUserId: UUID): ByteArray
    {
        val export = recordDownload(exportId, downloadedByUserId)
        val key = export.bundleObjectKey ?: throw AuditExportAccessException("Export bundle is not available")
        return archiveStorage.getObject(key)
    }

    fun getExport(exportId: UUID): AuditExport = requireExport(exportId)

    fun listForOrganization(organizationId: UUID?, platformOnly: Boolean): List<AuditExport> =
        auditExportRepository.listForOrganization(organizationId, platformOnly)

    fun listApprovals(exportId: UUID): List<AuditExportApproval> = auditExportApprovalRepository.findByExport(exportId)

    private fun requireExport(exportId: UUID): AuditExport =
        auditExportRepository.findById(exportId) ?: throw AuditExportNotFoundException("Audit export not found")

    private fun validateRequest(request: ExportRequest)
    {
        require(request.categories.isNotEmpty()) { "At least one audit category is required" }
        require(request.purpose.isNotBlank()) { "Purpose is required" }
        require(request.occurredBefore.isAfter(request.occurredAfter)) { "occurredBefore must be after occurredAfter" }
        val rangeDays = java.time.Duration.between(request.occurredAfter, request.occurredBefore).toDays()
        require(rangeDays <= configService.getMaxRangeDays()) {
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
                    organizationId = export.organizationId,
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
