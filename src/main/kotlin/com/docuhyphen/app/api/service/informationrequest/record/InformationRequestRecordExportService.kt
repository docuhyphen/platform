package com.docuhyphen.app.api.service.informationrequest.record

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestRecordExport
import com.docuhyphen.app.api.model.entity.InformationRequestRecordExportKind
import com.docuhyphen.app.api.model.entity.RecordOwnerKind
import com.docuhyphen.app.api.model.entity.RecordTransferDecision
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.audit.CreateInformationRequestRecordExportCommand
import com.docuhyphen.app.api.model.informationrequest.audit.InformationRequestRecordExportView
import com.docuhyphen.app.api.model.informationrequest.noauth.InformationRequestAbuseControl
import com.docuhyphen.app.api.model.informationrequest.noauth.InformationRequestAbuseLimits
import com.docuhyphen.app.api.model.recordpreservation.RecordOwnerRef
import com.docuhyphen.app.api.model.recordpreservation.RecordPreservationResourceTypes
import com.docuhyphen.app.api.model.recordpreservation.RecordTransferVerdict
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.record.InformationRequestRecordExportRepository
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditOwnerScope
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandActorRef
import com.docuhyphen.app.api.service.command.CommandMutationResult
import com.docuhyphen.app.api.service.command.CommandReceiptDecision
import com.docuhyphen.app.api.service.command.CommandReceiptRequest
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.command.CommandResultReference
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.disposal.InformationRequestDisposalEligibility
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestAbuseLog
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestRateLimitedException
import com.docuhyphen.app.api.service.recordpreservation.RecordStorageLocationPolicy
import com.docuhyphen.app.api.service.recordpreservation.RecordTransferPolicy
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.Clock
import java.time.Duration
import java.util.UUID

@ApplicationScoped
class InformationRequestRecordExportService @Inject constructor(
    private val gate: InformationRequestMutationGate,
    private val assembler: InformationRequestRecordAssembler,
    private val exportRepository: InformationRequestRecordExportRepository,
    private val storageLocations: RecordStorageLocationPolicy,
    private val transfers: RecordTransferPolicy,
    private val commandReceiptService: CommandReceiptService,
    private val auditRecorder: AuditRecorder,
    private val requestRepository: InformationRequestRepository,
    private val clock: Clock,
    private val abuseLimits: InformationRequestAbuseLimits = InformationRequestAbuseLimits(),
)
{
    @Transactional
    fun create(command: CreateInformationRequestRecordExportCommand): InformationRequestRecordExportView
    {
        val locked = gate.lock(command.requestId)
        val region = command.transferRegion?.trim()?.ifBlank { null }
        val receipt = CommandReceiptRequest(
            resource = ResourceRef.informationRequest(command.requestId),
            operation = CREATE_OPERATION,
            actor = CommandActorRef.principal(command.access.principal),
            idempotencyKey = command.idempotencyKey,
            requestFingerprint = CommandRequestFingerprint.sha256Hex("$CREATE_OPERATION|${command.requestId}|${region.orEmpty()}"),
        )
        return when (
            val decision = commandReceiptService.runOnce(receipt) {
                val export = freeze(locked.request, command, region)
                CommandMutationResult(export, CommandResultReference(ResourceType.INFORMATION_REQUEST_RECORD_EXPORT, export.id, 1, null))
            }
        )
        {
            is CommandReceiptDecision.Recorded -> view(decision.response, verified = true)
            is CommandReceiptDecision.Replayed ->
            {
                gate.authorizeRequest(command.access, listOf(Action.INFORMATION_REQUEST_EXPORT), command.requestId)
                view(requireExport(command.requestId, decision.result.resourceId), verified = true)
            }
        }
    }

    fun exports(requestId: UUID, access: RequestAccessContext): List<InformationRequestRecordExportView>
    {
        gate.authorizeRequest(access, listOf(Action.INFORMATION_REQUEST_EXPORT), requestId)
        return exportRepository.findForRequest(requestId).map { view(it, verified = verifies(it)) }
    }

    @Transactional(dontRollbackOn = [InformationRequestLifecycleException::class])
    fun read(requestId: UUID, exportId: UUID, access: RequestAccessContext): InformationRequestRecordExportView
    {
        gate.authorizeRequest(access, listOf(Action.INFORMATION_REQUEST_EXPORT), requestId)
        val export = requireExport(requestId, exportId)
        val verified = verifies(export)
        audit(
            AuditEventType.INFORMATION_REQUEST_EXPORT_READ, export, requestId, access.principal,
            mapOf("exportId" to export.id.toString(), "contentHash" to export.contentHash, "verified" to verified.toString()),
            "${AuditEventType.INFORMATION_REQUEST_EXPORT_READ.key}|${export.id}|${access.principal.id}|${clock.instant()}",
            if (verified) AuditOutcome.SUCCESS else AuditOutcome.FAILURE,
        )
        if (!verified)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.EXPORT_INTEGRITY_FAILED,
                "The stored record export no longer matches its recorded hash",
            )
        }
        return view(export, verified = true, withContent = true)
    }

    @Transactional(Transactional.TxType.MANDATORY)
    fun createSubjectExport(
        owner: RecordOwnerRef,
        subjectIdentityRefId: UUID,
        requestIds: List<UUID>,
        principal: PrincipalRef,
        transferRegion: String?,
    ): InformationRequestRecordExportView
    {
        val region = transferRegion?.trim()?.ifBlank { null }
        if (region != null && transfers.decide(owner, region) != RecordTransferVerdict.PERMITTED)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.TRANSFER_NOT_PERMITTED,
                "This record may not be transferred to the requested region",
            )
        }
        val requests = requestIds.mapNotNull(requestRepository::findById)
        val content = buildJsonObject {
            put("schemaVersion", InformationRequestRecordAssembler.SCHEMA_VERSION)
            put("subjectIdentityRefId", subjectIdentityRefId.toString())
            put("requests", buildJsonArray { requests.forEach { add(assembler.assemble(it)) } })
        }.toString()
        val bytes = content.toByteArray(Charsets.UTF_8)
        val export = exportRepository.save(
            InformationRequestRecordExport().apply {
                exportKind = InformationRequestRecordExportKind.SUBJECT_RECORD
                this.subjectIdentityRefId = subjectIdentityRefId
                ownerKind = owner.kind
                ownerId = requireNotNull(owner.id)
                schemaVersion = InformationRequestRecordAssembler.SCHEMA_VERSION
                contentJson = content
                contentHash = sha256(bytes)
                contentLength = bytes.size.toLong()
                storageLocation = storageLocations.locationFor(owner, RecordPreservationResourceTypes.INFORMATION_REQUEST)
                this.transferRegion = region
                transferDecision = if (region == null) RecordTransferDecision.NOT_REQUESTED else RecordTransferDecision.PERMITTED
                requestedByPrincipalKind = principal.kind
                requestedByPrincipalId = principal.id
                requestedAt = Timestamp.from(clock.instant())
            },
        )
        requests.forEach { request ->
            exportRepository.insertSource(export.id, request.id)
            audit(
                AuditEventType.INFORMATION_REQUEST_EXPORT, export, request.id, principal,
                mapOf(
                    "exportId" to export.id.toString(),
                    "contentHash" to export.contentHash,
                    "schemaVersion" to export.schemaVersion.toString(),
                    "storageLocation" to export.storageLocation,
                    "transferDecision" to export.transferDecision.name,
                    "subjectIdentityRefId" to subjectIdentityRefId.toString(),
                ),
                "${AuditEventType.INFORMATION_REQUEST_EXPORT.key}|${export.id}|${request.id}",
            )
        }
        return view(export, verified = true)
    }

    private fun freeze(request: InformationRequest, command: CreateInformationRequestRecordExportCommand, region: String?): InformationRequestRecordExport
    {
        gate.authorizeRequest(command.access, listOf(Action.INFORMATION_REQUEST_EXPORT), request.id)
        val owner = InformationRequestDisposalEligibility.ownerOf(request)
        requireWithinDailyCeiling(owner)
        if (region != null && transfers.decide(owner, region) != RecordTransferVerdict.PERMITTED)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.TRANSFER_NOT_PERMITTED,
                "This record may not be transferred to the requested region",
            )
        }
        val content = assembler.assemble(request).toString()
        val bytes = content.toByteArray(Charsets.UTF_8)
        val export = exportRepository.save(
            InformationRequestRecordExport().apply {
                exportKind = InformationRequestRecordExportKind.REQUEST_RECORD
                informationRequestId = request.id
                ownerKind = owner.kind
                ownerId = requireNotNull(owner.id)
                schemaVersion = InformationRequestRecordAssembler.SCHEMA_VERSION
                contentJson = content
                contentHash = sha256(bytes)
                contentLength = bytes.size.toLong()
                storageLocation = storageLocations.locationFor(owner, RecordPreservationResourceTypes.INFORMATION_REQUEST)
                transferRegion = region
                transferDecision = if (region == null) RecordTransferDecision.NOT_REQUESTED else RecordTransferDecision.PERMITTED
                requestedByPrincipalKind = command.access.principal.kind
                requestedByPrincipalId = command.access.principal.id
                requestedAt = Timestamp.from(clock.instant())
            },
        )
        exportRepository.insertSource(export.id, request.id)
        audit(
            AuditEventType.INFORMATION_REQUEST_EXPORT, export, request.id, command.access.principal,
            mapOf(
                "exportId" to export.id.toString(),
                "contentHash" to export.contentHash,
                "schemaVersion" to export.schemaVersion.toString(),
                "storageLocation" to export.storageLocation,
                "transferDecision" to export.transferDecision.name,
            ),
            "${AuditEventType.INFORMATION_REQUEST_EXPORT.key}|${export.id}",
        )
        return export
    }

    private fun requireWithinDailyCeiling(owner: RecordOwnerRef)
    {
        val now = clock.instant()
        val window = exportRepository.windowSince(owner.kind, requireNotNull(owner.id), Timestamp.from(now.minus(EXPORT_WINDOW)))
        if (window.count < abuseLimits.exportDailyCeiling) return
        val reopensAt = (window.oldestRequestedAt ?: now).plus(EXPORT_WINDOW)
        InformationRequestAbuseLog.refused(InformationRequestAbuseControl.EXPORT_DAILY_CEILING, "owner=${owner.kind}:${owner.id}")
        throw InformationRequestRateLimitedException(
            InformationRequestErrorCatalog.EXPORT_LIMIT_REACHED,
            maxOf(1L, Duration.between(now, reopensAt).seconds),
            "This owner has made ${abuseLimits.exportDailyCeiling} record exports in the last day. Try again later.",
        )
    }

    @Suppress("LongParameterList")
    private fun audit(
        eventType: AuditEventType,
        export: InformationRequestRecordExport,
        requestId: UUID,
        principal: PrincipalRef,
        payload: Map<String, String>,
        idempotencyKey: String,
        outcome: AuditOutcome = AuditOutcome.SUCCESS,
    )
    {
        auditRecorder.record(
            AuditEventDraft(
                owner = auditOwnerOf(export),
                eventTypeKey = eventType.key,
                outcome = outcome,
                actorId = principal.id,
                actorKind = AuditActorKind.forPrincipal(principal.kind),
                targetType = "INFORMATION_REQUEST",
                targetId = requestId.toString(),
                payload = payload,
                idempotencyKey = idempotencyKey,
                businessTransactionId = export.id.toString(),
            ),
        )
    }

    private fun auditOwnerOf(export: InformationRequestRecordExport): AuditOwnerScope = when (export.ownerKind)
    {
        RecordOwnerKind.ORGANIZATION -> AuditOwnerScope.Organization(export.ownerId)
        RecordOwnerKind.USER -> AuditOwnerScope.Personal(export.ownerId)
        RecordOwnerKind.PLATFORM -> AuditOwnerScope.Platform
    }

    private fun requireExport(requestId: UUID, exportId: UUID): InformationRequestRecordExport =
        exportRepository.findForRequest(requestId).firstOrNull { it.id == exportId }
            ?: throw InformationRequestLifecycleException(InformationRequestErrorCatalog.NOT_FOUND, "Record export not found")

    private fun verifies(export: InformationRequestRecordExport): Boolean =
        sha256(export.contentJson.toByteArray(Charsets.UTF_8)) == export.contentHash

    private fun view(export: InformationRequestRecordExport, verified: Boolean, withContent: Boolean = false) =
        InformationRequestRecordExportView(export, exportRepository.sourcesOf(export.id), verified, if (withContent) export.contentJson else null)

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object
    {
        val EXPORT_WINDOW: Duration = Duration.ofDays(1)
        const val CREATE_OPERATION = "information_request.record_export.create"
    }
}
