package com.docuhyphen.app.api.service.informationrequest.record

import com.docuhyphen.app.api.exception.StepUpRequiredException
import com.docuhyphen.app.api.model.command.*
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.audit.CreateInformationRequestRecordExportCommand
import com.docuhyphen.app.api.model.informationrequest.audit.CreateInformationRequestSubjectExportCommand
import com.docuhyphen.app.api.model.informationrequest.audit.InformationRequestAuditedRead
import com.docuhyphen.app.api.model.informationrequest.audit.InformationRequestRecordExportView
import com.docuhyphen.app.api.model.informationrequest.noauth.InformationRequestAbuseControl
import com.docuhyphen.app.api.model.informationrequest.noauth.InformationRequestAbuseLimits
import com.docuhyphen.app.api.model.recordpreservation.RecordOwnerRef
import com.docuhyphen.app.api.model.recordpreservation.RecordPreservationResourceTypes
import com.docuhyphen.app.api.model.recordpreservation.RecordTransferVerdict
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.record.InformationRequestRecordExportRepository
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditOwnerScope
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestRelatedRequestVisibility
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestAuditActorLabelResolver
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestAuditTargets
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestReadAudit
import com.docuhyphen.app.api.service.informationrequest.disposal.InformationRequestDisposalEligibility
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestEvidenceSizes
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestAbuseLog
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestRateLimitedException
import com.docuhyphen.app.api.service.recordpreservation.RecordDisposalService
import com.docuhyphen.app.api.service.recordpreservation.RecordStorageLocationPolicy
import com.docuhyphen.app.api.service.recordpreservation.RecordTransferPolicy
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import kotlinx.serialization.json.*
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.Clock
import java.time.Duration
import java.util.*

@ApplicationScoped
class InformationRequestRecordExportService @Inject constructor(
    private val gate: InformationRequestMutationGate,
    private val assembler: InformationRequestRecordAssembler,
    private val exportRepository: InformationRequestRecordExportRepository,
    private val requirementRepository: InformationRequestRequirementRepository,
    private val storageLocations: RecordStorageLocationPolicy,
    private val transfers: RecordTransferPolicy,
    private val disposals: RecordDisposalService,
    private val commandReceiptService: CommandReceiptService,
    private val auditRecorder: AuditRecorder,
    private val actorLabels: InformationRequestAuditActorLabelResolver,
    private val readAudit: InformationRequestReadAudit,
    private val clock: Clock,
    private val abuseLimits: InformationRequestAbuseLimits = InformationRequestAbuseLimits(),
)
{
    @Transactional
    fun create(command: CreateInformationRequestRecordExportCommand): InformationRequestRecordExportView
    {
        gate.requireVisibleTarget(command.access, command.requestId)
        val locked = gate.lock(command.requestId)
        val region = command.transferRegion?.trim()?.ifBlank { null }
        val receipt = CommandReceiptRequest(
            resource = ResourceRef.informationRequest(command.requestId),
            operation = CREATE_OPERATION,
            actor = CommandActorRef.principal(command.access.principal),
            idempotencyKey = command.idempotencyKey,
            requestFingerprint = CommandRequestFingerprint.of(CREATE_OPERATION, command.requestId, region),
        )
        return when (
            val decision = commandReceiptService.runOnce(receipt) {
                val export = freeze(locked.request, command, region)
                CommandMutationResult(
                    export,
                    CommandResultReference(ResourceType.INFORMATION_REQUEST_RECORD_EXPORT, export.id, 1, null)
                )
            }
        )
        {
            is CommandReceiptDecision.Recorded -> view(decision.response, verified = true)
            is CommandReceiptDecision.Replayed ->
            {
                gate.authorizeRequest(command.access, listOf(Action.INFORMATION_REQUEST_EXPORT), command.requestId)
                val export = requireExport(command.requestId, decision.result.resourceId, command.access)
                view(export, verified = verifies(export)).also { requireVerificationFor(it, command.access) }
            }
        }
    }

    fun exports(requestId: UUID, access: RequestAccessContext): List<InformationRequestRecordExportView>
    {
        gate.authorizeRequest(access, listOf(Action.INFORMATION_REQUEST_EXPORT), requestId)
        return exportRepository.findForRequest(requestId)
            .filter { readableThroughRequest(it, access) }
            .map { view(it, verified = verifies(it)) }
    }

    @Transactional(dontRollbackOn = [InformationRequestLifecycleException::class])
    fun read(requestId: UUID, exportId: UUID, access: RequestAccessContext): InformationRequestRecordExportView
    {
        gate.authorizeRequest(access, listOf(Action.INFORMATION_REQUEST_EXPORT), requestId)
        val export = requireExport(requestId, exportId, access)
        return withoutHiddenRelatedRequests(
            shownWithContent(export, access, exportRepository.sourcesOf(export.id)),
            access,
        )
    }

    @Transactional(dontRollbackOn = [InformationRequestLifecycleException::class])
    fun readSubjectExport(
        exportId: UUID,
        owner: RecordOwnerRef,
        access: RequestAccessContext
    ): InformationRequestRecordExportView {
        val export = exportRepository.findById(exportId)
            ?.takeIf {
                it.exportKind == InformationRequestRecordExportKind.SUBJECT_RECORD &&
                        it.ownerKind == owner.kind &&
                        it.ownerId == owner.id
            }
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.NOT_FOUND,
                "Record export not found"
            )
        return shownWithContent(export, access, exportRepository.sourcesOf(export.id))
    }

    private fun shownWithContent(
        export: InformationRequestRecordExport,
        access: RequestAccessContext,
        auditedRequestIds: List<UUID>,
    ): InformationRequestRecordExportView {
        val verified = verifies(export)
        val shown = view(export, verified = verified, withContent = true)
        requireVerificationFor(shown, access)
        auditedRequestIds.forEach { requestId ->
            readAudit.record(
                InformationRequestAuditedRead(
                    owner = auditOwnerOf(export),
                    eventType = AuditEventType.INFORMATION_REQUEST_EXPORT_READ,
                    requestId = requestId,
                    actor = InformationRequestAuditActor.of(access),
                    payload = mapOf(
                        "exportId" to export.id.toString(),
                        "contentHash" to export.contentHash,
                        "verified" to verified.toString()
                    ),
                    businessTransactionId = export.id.toString(),
                    outcome = if (verified) AuditOutcome.SUCCESS else AuditOutcome.FAILURE,
                ),
            )
        }
        if (!verified)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.EXPORT_INTEGRITY_FAILED,
                "The stored record export no longer matches its recorded hash",
            )
        }
        return shown
    }

    private fun withoutHiddenRelatedRequests(
        view: InformationRequestRecordExportView,
        access: RequestAccessContext,
    ): InformationRequestRecordExportView {
        val content = view.content ?: return view
        val projection = InformationRequestRecordRelatedRequests.withheld(
            Json.parseToJsonElement(content).jsonObject,
            InformationRequestRelatedRequestVisibility.of(gate, access),
        )
        if (projection.withheldCount == 0) return view
        return view.copy(
            content = projection.content.toString(),
            withheldRelatedRequestCount = projection.withheldCount
        )
    }

    @Transactional(Transactional.TxType.MANDATORY)
    fun createSubjectExport(command: CreateInformationRequestSubjectExportCommand): InformationRequestRecordExportView
    {
        val owner = command.owner
        val principal = command.access.principal
        val region = command.transferRegion?.trim()?.ifBlank { null }
        if (region != null && transfers.decide(owner, region) != RecordTransferVerdict.PERMITTED)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.TRANSFER_NOT_PERMITTED,
                "This record may not be transferred to the requested region",
            )
        }
        val requests = gate.lockExisting(command.requestIds).map { it.request }
        requireNotUnderDisposal(requests.map { it.id })
        requireWithinDailyCeiling(owner)
        val compartmented = requirementRepository.findCompartmentedIds(requests.map { it.id })
        val verifiedCaller = command.access.authorization.mfaSatisfied
        val content = buildJsonObject {
            put("schemaVersion", InformationRequestRecordAssembler.SCHEMA_VERSION)
            put("subjectIdentityRefId", command.subjectIdentityRefId.toString())
            put(
                "requests",
                buildJsonArray {
                    requests.forEach { request ->
                        add(assembler.assemble(request) { verifiedCaller || it !in compartmented })
                    }
                },
            )
        }.toString()
        val bytes = content.toByteArray(Charsets.UTF_8)
        requireBoundedContent(bytes)
        val export = exportRepository.save(
            InformationRequestRecordExport().apply {
                exportKind = InformationRequestRecordExportKind.SUBJECT_RECORD
                this.subjectIdentityRefId = command.subjectIdentityRefId
                ownerKind = owner.kind
                ownerId = requireNotNull(owner.id)
                schemaVersion = InformationRequestRecordAssembler.SCHEMA_VERSION
                contentJson = content
                contentHash = sha256(bytes)
                contentLength = bytes.size.toLong()
                storageLocation =
                    storageLocations.locationFor(owner, RecordPreservationResourceTypes.INFORMATION_REQUEST)
                this.transferRegion = region
                transferDecision =
                    if (region == null) RecordTransferDecision.NOT_REQUESTED else RecordTransferDecision.PERMITTED
                requestedByPrincipalKind = principal.kind
                requestedByPrincipalId = principal.id
                requestedAt = Timestamp.from(clock.instant())
            },
        )
        requests.forEach { request ->
            exportRepository.insertSource(export.id, request.id)
            audit(
                AuditEventType.INFORMATION_REQUEST_EXPORT,
                export,
                request.id,
                InformationRequestAuditActor.of(command.access),
                mapOf(
                    "exportId" to export.id.toString(),
                    "contentHash" to export.contentHash,
                    "schemaVersion" to export.schemaVersion.toString(),
                    "storageLocation" to export.storageLocation,
                    "transferDecision" to export.transferDecision.name,
                    "subjectIdentityRefId" to command.subjectIdentityRefId.toString(),
                ),
                "${AuditEventType.INFORMATION_REQUEST_EXPORT.key}|${export.id}|${request.id}",
            )
        }
        return view(export, verified = true)
    }

    private fun freeze(
        request: InformationRequest,
        command: CreateInformationRequestRecordExportCommand,
        region: String?
    ): InformationRequestRecordExport
    {
        gate.authorizeRequest(command.access, listOf(Action.INFORMATION_REQUEST_EXPORT), request.id)
        requireNotUnderDisposal(listOf(request.id))
        val owner = InformationRequestDisposalEligibility.ownerOf(request)
        requireWithinDailyCeiling(owner)
        if (region != null && transfers.decide(owner, region) != RecordTransferVerdict.PERMITTED)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.TRANSFER_NOT_PERMITTED,
                "This record may not be transferred to the requested region",
            )
        }
        val content = InformationRequestRecordRelatedRequests.withheld(
            assembler.assemble(request) {
                gate.permitsRequirement(command.access, Action.INFORMATION_REQUEST_REQUIREMENT_EXPORT, it)
            },
            InformationRequestRelatedRequestVisibility.of(gate, command.access),
        ).content.toString()
        val bytes = content.toByteArray(Charsets.UTF_8)
        requireBoundedContent(bytes)
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
                storageLocation =
                    storageLocations.locationFor(owner, RecordPreservationResourceTypes.INFORMATION_REQUEST)
                transferRegion = region
                transferDecision =
                    if (region == null) RecordTransferDecision.NOT_REQUESTED else RecordTransferDecision.PERMITTED
                requestedByPrincipalKind = command.access.principal.kind
                requestedByPrincipalId = command.access.principal.id
                requestedAt = Timestamp.from(clock.instant())
            },
        )
        exportRepository.insertSource(export.id, request.id)
        audit(
            AuditEventType.INFORMATION_REQUEST_EXPORT,
            export,
            request.id,
            InformationRequestAuditActor.of(command.access),
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

    fun existingExports(exportIds: Collection<UUID>): Set<UUID> = exportRepository.existing(exportIds)

    private fun requireNotUnderDisposal(requestIds: Collection<UUID>) {
        if (disposals.underDisposal(RecordPreservationResourceTypes.INFORMATION_REQUEST, requestIds).isNotEmpty()) {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.DISPOSAL_IN_PROGRESS,
                "A record this export covers is being disposed; try again once its disposal finishes",
            )
        }
    }

    private fun requireVerificationFor(view: InformationRequestRecordExportView, access: RequestAccessContext) {
        if (view.containsConfidentialContent && !access.authorization.mfaSatisfied) {
            throw StepUpRequiredException(action = CONFIDENTIAL_READ_ACTION)
        }
    }

    private fun requireBoundedContent(bytes: ByteArray) {
        if (bytes.size.toLong() > abuseLimits.exportMaxBytes) {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.RECORD_EXPORT_TOO_LARGE,
                "A record export must fit within ${InformationRequestEvidenceSizes.describe(abuseLimits.exportMaxBytes)}",
            )
        }
    }

    private fun requireWithinDailyCeiling(owner: RecordOwnerRef)
    {
        val ownerId = requireNotNull(owner.id)
        exportRepository.lockOwnerAllowance(owner.kind, ownerId)
        val now = clock.instant()
        val window = exportRepository.windowSince(owner.kind, ownerId, Timestamp.from(now.minus(EXPORT_WINDOW)))
        if (window.count < abuseLimits.exportDailyCeiling) return
        val reopensAt = (window.oldestRequestedAt ?: now).plus(EXPORT_WINDOW)
        InformationRequestAbuseLog.refused(
            InformationRequestAbuseControl.EXPORT_DAILY_CEILING,
            "owner=${owner.kind}:${owner.id}"
        )
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
        actor: InformationRequestAuditActor,
        payload: Map<String, String>,
        idempotencyKey: String,
    )
    {
        val attribution = actorLabels.attribute(actor)
        auditRecorder.record(
            AuditEventDraft(
                owner = auditOwnerOf(export),
                eventTypeKey = eventType.key,
                outcome = AuditOutcome.SUCCESS,
                actorId = attribution.actorId,
                actorKind = attribution.actorKind,
                actorLabel = attribution.actorLabel,
                sessionId = attribution.sessionId,
                targetType = InformationRequestAuditTargets.REQUEST,
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

    private fun requireExport(
        requestId: UUID,
        exportId: UUID,
        access: RequestAccessContext
    ): InformationRequestRecordExport =
        exportRepository.findForRequest(requestId)
            .firstOrNull { it.id == exportId && readableThroughRequest(it, access) }
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.NOT_FOUND,
                "Record export not found"
            )

    private fun readableThroughRequest(export: InformationRequestRecordExport, access: RequestAccessContext): Boolean =
        export.exportKind != InformationRequestRecordExportKind.SUBJECT_RECORD ||
                exportRepository.sourcesOf(export.id)
                    .all { gate.permitsRequest(access, Action.INFORMATION_REQUEST_EXPORT, it) }

    private fun verifies(export: InformationRequestRecordExport): Boolean =
        sha256(export.contentJson.toByteArray(Charsets.UTF_8)) == export.contentHash

    private fun view(
        export: InformationRequestRecordExport,
        verified: Boolean,
        withContent: Boolean = false
    ): InformationRequestRecordExportView {
        val sources = exportRepository.sourcesOf(export.id)
        return InformationRequestRecordExportView(
            export,
            sources,
            verified,
            if (withContent) export.contentJson else null,
            containsConfidentialContent(export, sources),
        )
    }

    private fun containsConfidentialContent(export: InformationRequestRecordExport, sources: List<UUID>): Boolean {
        val disclosed = InformationRequestRecordContent.disclosedRequirementIds(export.contentJson) ?: return true
        return disclosed.isNotEmpty() && requirementRepository.findCompartmentedIds(sources).any { it in disclosed }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object
    {
        val EXPORT_WINDOW: Duration = Duration.ofDays(1)
        const val CREATE_OPERATION = "information_request.record_export.create"
        const val CONFIDENTIAL_READ_ACTION = "read a record export that includes confidential information"
    }
}
