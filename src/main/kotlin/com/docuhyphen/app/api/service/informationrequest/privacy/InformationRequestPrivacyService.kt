package com.docuhyphen.app.api.service.informationrequest.privacy

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.command.*
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.audit.CreateInformationRequestSubjectExportCommand
import com.docuhyphen.app.api.model.informationrequest.audit.InformationRequestRecordExportView
import com.docuhyphen.app.api.model.informationrequest.privacy.InformationRequestPrivacyOutcome
import com.docuhyphen.app.api.model.informationrequest.privacy.InformationRequestPrivacyRefusal
import com.docuhyphen.app.api.model.informationrequest.privacy.InformationRequestPrivacyRequestView
import com.docuhyphen.app.api.model.informationrequest.privacy.RecordInformationRequestPrivacyRequestCommand
import com.docuhyphen.app.api.model.recordpreservation.RecordOwnerRef
import com.docuhyphen.app.api.repository.informationrequest.privacy.InformationRequestPrivacyRequestRepository
import com.docuhyphen.app.api.model.audit.AuditEventDraft
import com.docuhyphen.app.api.model.audit.AuditOwnerScope
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestOwnerScopeAccess
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestAuditActorLabelResolver
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestAuditTargets
import com.docuhyphen.app.api.service.informationrequest.disposal.InformationRequestDisposalRefusedException
import com.docuhyphen.app.api.service.informationrequest.disposal.InformationRequestDisposalService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.record.InformationRequestRecordExportService
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestItemCorrectionService
import com.docuhyphen.app.api.service.recordpreservation.RecordOwnerReceiptScope
import io.quarkus.narayana.jta.QuarkusTransaction
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Clock
import java.util.*

@ApplicationScoped
class InformationRequestPrivacyService @Inject constructor(
    private val ownerAccess: InformationRequestOwnerScopeAccess,
    private val privacyRepository: InformationRequestPrivacyRequestRepository,
    private val exports: InformationRequestRecordExportService,
    private val restrictions: InformationRequestSubjectRestrictionService,
    private val corrections: InformationRequestItemCorrectionService,
    private val disposals: InformationRequestDisposalService,
    private val commandReceiptService: CommandReceiptService,
    private val auditRecorder: AuditRecorder,
    private val actorLabels: InformationRequestAuditActorLabelResolver,
    private val clock: Clock,
)
{
    fun submit(command: RecordInformationRequestPrivacyRequestCommand): InformationRequestPrivacyRequestView
    {
        val owner = InformationRequestOwnerScopeAccess.recordOwnerOf(ownerAccess.currentOwner())
        val principal =
            ownerAccess.requireAccess(ownerAccess.currentOwner(), Action.INFORMATION_REQUEST_MANAGE_PRIVACY).principal
        val actor = InformationRequestAuditActor.of(RequestAccessContext(principal, command.authorization))
        val normalized = normalized(command)
        requireSubject(owner, normalized.subjectIdentityRefId)
        val receipt = CommandReceiptRequest(
            resource = RecordOwnerReceiptScope.of(ResourceType.INFORMATION_REQUEST_PRIVACY_REQUEST, owner),
            operation = SUBMIT_OPERATION,
            actor = CommandActorRef.principal(principal),
            idempotencyKey = normalized.idempotencyKey,
            requestFingerprint = fingerprintOf(normalized),
        )
        val outcome = try {
            QuarkusTransaction.requiringNew().call { recordAndProcess(receipt, owner, actor, normalized) }
        } catch (failure: RuntimeException) {
            val refusal = refusalOf(failure) ?: throw failure
            return QuarkusTransaction.requiringNew().call { recordRefusal(receipt, owner, actor, normalized, refusal) }
        }
        outcome.claimIds.forEach(::processClaim)
        return outcome.view
    }

    fun privacyRequests(subjectIdentityRefId: UUID?): List<InformationRequestPrivacyRequestView> {
        val owner = InformationRequestOwnerScopeAccess.recordOwnerOf(ownerAccess.currentOwner())
        ownerAccess.requireAccess(ownerAccess.currentOwner(), Action.INFORMATION_REQUEST_MANAGE_PRIVACY)
        return privacyRepository.findForOwner(owner.kind, requireNotNull(owner.id), subjectIdentityRefId).map(::view)
    }

    fun privacyRequest(id: UUID): InformationRequestPrivacyRequestView {
        val owner = InformationRequestOwnerScopeAccess.recordOwnerOf(ownerAccess.currentOwner())
        ownerAccess.requireAccess(ownerAccess.currentOwner(), Action.INFORMATION_REQUEST_MANAGE_PRIVACY)
        val request = privacyRepository.findById(id)?.takeIf { it.ownerKind == owner.kind && it.ownerId == owner.id }
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.NOT_FOUND,
                "Privacy request not found"
            )
        return view(request)
    }

    fun subjectExport(id: UUID): InformationRequestRecordExportView {
        val owner = InformationRequestOwnerScopeAccess.recordOwnerOf(ownerAccess.currentOwner())
        val access = ownerAccess.requireAccess(ownerAccess.currentOwner(), Action.INFORMATION_REQUEST_MANAGE_PRIVACY)
        val exportId = privacyRepository.findById(id)
            ?.takeIf { it.ownerKind == owner.kind && it.ownerId == owner.id }
            ?.recordExportId
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.NOT_FOUND,
                "Record export not found"
            )
        return exports.readSubjectExport(exportId, owner, access)
    }

    private fun recordAndProcess(
        receipt: CommandReceiptRequest,
        owner: RecordOwnerRef,
        actor: InformationRequestAuditActor,
        command: RecordInformationRequestPrivacyRequestCommand,
    ): InformationRequestPrivacyOutcome =
        when (
            val decision = commandReceiptService.runOnce(receipt) {
                val recorded = record(owner, actor, command)
                val outcome = process(recorded, owner, actor, command)
                CommandMutationResult(outcome, resultOf(outcome.view.request))
            }
        ) {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed -> InformationRequestPrivacyOutcome(
                replayed(decision.result),
                emptyList()
            )
        }

    private fun process(
        recorded: InformationRequestPrivacyRequest,
        owner: RecordOwnerRef,
        actor: InformationRequestAuditActor,
        command: RecordInformationRequestPrivacyRequestCommand,
    ): InformationRequestPrivacyOutcome = when (command.requestKind) {
        InformationRequestPrivacyRequestKind.ACCESS, InformationRequestPrivacyRequestKind.EXPORT -> {
            val export = exports.createSubjectExport(
                CreateInformationRequestSubjectExportCommand(
                    owner = owner,
                    subjectIdentityRefId = command.subjectIdentityRefId,
                    requestIds = subjectRequestIds(owner, command.subjectIdentityRefId),
                    access = RequestAccessContext(actor.principal, command.authorization),
                    transferRegion = command.transferRegion,
                ),
            )
            export.sourceRequestIds.forEach {
                privacyRepository.insertTarget(
                    recorded.id,
                    it,
                    InformationRequestPrivacyTargetOutcome.EXPORTED,
                    null,
                    null
                )
            }
            InformationRequestPrivacyOutcome(
                complete(recorded.id, actor) { it.recordExportId = export.export.id },
                emptyList()
            )
        }

        InformationRequestPrivacyRequestKind.CORRECTION -> {
            val correction = corrections.correct(
                owner,
                command.subjectIdentityRefId,
                recorded.id,
                requireNotNull(command.correction),
                actor,
            )
            privacyRepository.insertTarget(
                recorded.id,
                correction.informationRequestId,
                InformationRequestPrivacyTargetOutcome.CORRECTED,
                null,
                null
            )
            InformationRequestPrivacyOutcome(complete(recorded.id, actor), emptyList())
        }

        InformationRequestPrivacyRequestKind.RESTRICTION -> {
            val restriction = restrictions.restrict(owner, command.subjectIdentityRefId, recorded.id)
            subjectRequestIds(owner, command.subjectIdentityRefId).forEach { requestId ->
                privacyRepository.insertTarget(
                    recorded.id,
                    requestId,
                    InformationRequestPrivacyTargetOutcome.RESTRICTED,
                    null,
                    null
                )
            }
            InformationRequestPrivacyOutcome(
                complete(recorded.id, actor) { it.restrictionId = restriction.id },
                emptyList()
            )
        }

        InformationRequestPrivacyRequestKind.DELETION -> delete(recorded, owner, actor)
    }

    private fun delete(
        recorded: InformationRequestPrivacyRequest,
        owner: RecordOwnerRef,
        actor: InformationRequestAuditActor,
    ): InformationRequestPrivacyOutcome {
        val batch = disposals.claimFinished(
            subjectRequestIds(owner, recorded.subjectIdentityRefId),
            RecordDisposalBasis.PRIVACY_DELETION,
            recorded.id,
            actor.principal,
            actor.sessionRef,
        )
        batch.claimed.forEach {
            privacyRepository.insertTarget(
                recorded.id,
                it.claim.resourceId,
                InformationRequestPrivacyTargetOutcome.DISPOSAL_CLAIMED,
                null,
                it.claim.id,
            )
        }
        batch.open.forEach {
            privacyRepository.insertTarget(
                recorded.id,
                it,
                InformationRequestPrivacyTargetOutcome.SKIPPED_OPEN,
                null,
                null
            )
        }
        return InformationRequestPrivacyOutcome(complete(recorded.id, actor), batch.claimed.map { it.claim.id })
    }

    private fun recordRefusal(
        receipt: CommandReceiptRequest,
        owner: RecordOwnerRef,
        actor: InformationRequestAuditActor,
        command: RecordInformationRequestPrivacyRequestCommand,
        refusal: InformationRequestPrivacyRefusal,
    ): InformationRequestPrivacyRequestView =
        when (
            val decision = commandReceiptService.runOnce(receipt) {
                val recorded = record(owner, actor, command)
                refusal.targets.toSortedMap().forEach { (requestId, reasonCode) ->
                    privacyRepository.insertTarget(
                        recorded.id,
                        requestId,
                        InformationRequestPrivacyTargetOutcome.REFUSED,
                        reasonCode,
                        null
                    )
                }
                if (command.requestKind == InformationRequestPrivacyRequestKind.DELETION) {
                    disposals.recordPrivacyDenials(refusal.targets, recorded.id, actor.principal, actor.sessionRef)
                }
                val view = refuse(recorded.id, actor, refusal.reasonCode, refusal.detail)
                CommandMutationResult(view, resultOf(view.request))
            }
        ) {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed -> replayed(decision.result)
        }

    private fun refusalOf(failure: RuntimeException): InformationRequestPrivacyRefusal? = when (failure) {
        is InformationRequestDisposalRefusedException -> {
            val first = failure.refusals.toSortedMap().values.first()
            InformationRequestPrivacyRefusal(
                first.reasonCode,
                first.detail,
                failure.refusals.mapValues { it.value.reasonCode },
            )
        }

        is InformationRequestLifecycleException -> InformationRequestPrivacyRefusal(
            failure.reasonCode,
            failure.message,
            emptyMap()
        )

        else -> null
    }

    private fun processClaim(claimId: UUID) {
        runCatching { disposals.process(claimId) }
            .onFailure { logger.warn("Privacy deletion claim {} did not complete; it will be retried", claimId, it) }
    }

    private fun record(
        owner: RecordOwnerRef,
        actor: InformationRequestAuditActor,
        command: RecordInformationRequestPrivacyRequestCommand,
    ): InformationRequestPrivacyRequest {
        val request = privacyRepository.save(
            InformationRequestPrivacyRequest().apply {
                ownerKind = owner.kind
                ownerId = requireNotNull(owner.id)
                subjectIdentityRefId = command.subjectIdentityRefId
                requestKind = command.requestKind
                purposeKey = command.purposeKey
                policyBasisKey = command.policyBasisKey
                recordedByPrincipalKind = actor.principal.kind
                recordedByPrincipalId = actor.principal.id
                recordedAt = Timestamp.from(clock.instant())
            },
        )
        audit(AuditEventType.INFORMATION_REQUEST_PRIVACY_RECORD, request, actor)
        return request
    }

    private fun requireSubject(owner: RecordOwnerRef, subjectIdentityRefId: UUID) {
        val owned = QuarkusTransaction.requiringNew().call {
            privacyRepository.subjectOwnedBy(subjectIdentityRefId, owner.kind, requireNotNull(owner.id))
        }
        if (!owned) throw InformationRequestLifecycleException(
            InformationRequestErrorCatalog.NOT_FOUND,
            "Subject not found"
        )
    }

    private fun subjectRequestIds(owner: RecordOwnerRef, subjectIdentityRefId: UUID): List<UUID> =
        privacyRepository.subjectRequestIds(owner.kind, requireNotNull(owner.id), subjectIdentityRefId)

    private fun complete(
        privacyRequestId: UUID,
        actor: InformationRequestAuditActor,
        adjust: (InformationRequestPrivacyRequest) -> Unit = {},
    ): InformationRequestPrivacyRequestView
    {
        val request = requireNotNull(privacyRepository.findForUpdate(privacyRequestId))
        adjust(request)
        request.state = InformationRequestPrivacyRequestState.COMPLETED
        request.completedAt = Timestamp.from(clock.instant())
        request.requestRevision += 1
        privacyRepository.update(request)
        audit(AuditEventType.INFORMATION_REQUEST_PRIVACY_COMPLETE, request, actor)
        return view(request)
    }

    private fun refuse(
        privacyRequestId: UUID,
        actor: InformationRequestAuditActor,
        reasonCode: String,
        detail: String
    ): InformationRequestPrivacyRequestView
    {
        val request = requireNotNull(privacyRepository.findForUpdate(privacyRequestId))
        request.state = InformationRequestPrivacyRequestState.REFUSED
        request.refusalCode = reasonCode
        request.refusalDetail = detail.take(DETAIL_LENGTH)
        request.completedAt = Timestamp.from(clock.instant())
        request.requestRevision += 1
        privacyRepository.update(request)
        audit(AuditEventType.INFORMATION_REQUEST_PRIVACY_REFUSE, request, actor, AuditOutcome.DENIED)
        return view(request)
    }

    private fun replayed(result: CommandResultReference): InformationRequestPrivacyRequestView =
        view(requireNotNull(privacyRepository.findById(result.resourceId)))

    private fun resultOf(request: InformationRequestPrivacyRequest) =
        CommandResultReference(ResourceType.INFORMATION_REQUEST_PRIVACY_REQUEST, request.id, request.requestRevision)

    private fun view(request: InformationRequestPrivacyRequest): InformationRequestPrivacyRequestView
    {
        val targets = privacyRepository.targetsOf(request.id)
        return InformationRequestPrivacyRequestView(
            request = request,
            targets = targets,
            restriction = restrictions.recordedFor(request),
            correction = targets.firstOrNull { it.outcome == InformationRequestPrivacyTargetOutcome.CORRECTED }
                ?.let { target ->
                    corrections.correctionsFor(target.requestId).firstOrNull { it.privacyRequestId == request.id }
                },
            recordExportAvailable = request.recordExportId?.let { it in exports.existingExports(listOf(it)) } == true,
        )
    }

    private fun audit(
        eventType: AuditEventType,
        request: InformationRequestPrivacyRequest,
        actor: InformationRequestAuditActor,
        outcome: AuditOutcome = AuditOutcome.SUCCESS,
    )
    {
        val attribution = actorLabels.attribute(actor)
        auditRecorder.record(
            AuditEventDraft(
                owner = if (request.ownerKind == RecordOwnerKind.USER) AuditOwnerScope.Personal(request.ownerId)
                else AuditOwnerScope.Organization(request.ownerId),
                eventTypeKey = eventType.key,
                outcome = outcome,
                actorId = attribution.actorId,
                actorKind = attribution.actorKind,
                actorLabel = attribution.actorLabel,
                sessionId = attribution.sessionId,
                targetType = InformationRequestAuditTargets.PRIVACY_REQUEST,
                targetId = request.id.toString(),
                payload = buildMap {
                    put("privacyRequestId", request.id.toString())
                    put("requestKind", request.requestKind.name)
                    put("subjectIdentityRefId", request.subjectIdentityRefId.toString())
                    put("purposeKey", request.purposeKey)
                    put("policyBasisKey", request.policyBasisKey)
                    put("state", request.state.name)
                    request.restrictionId?.let { put("restrictionId", it.toString()) }
                    request.refusalCode?.let { put("reasonCode", it) }
                },
                idempotencyKey = "${eventType.key}|${request.id}|${request.requestRevision}",
            ),
        )
    }

    private fun normalized(command: RecordInformationRequestPrivacyRequestCommand): RecordInformationRequestPrivacyRequestCommand {
        val idempotencyKey = command.idempotencyKey.trim()
        if (idempotencyKey.isEmpty()) throw InformationRequestCommandRequestException("Idempotency-Key is required")
        val correction =
            command.correction.takeIf { command.requestKind == InformationRequestPrivacyRequestKind.CORRECTION }
        if (command.requestKind == InformationRequestPrivacyRequestKind.CORRECTION) {
            corrections.requireWellFormed(
                correction
                    ?: throw InformationRequestCommandRequestException("A correction names the item and its corrected content"),
            )
        }
        return command.copy(
            purposeKey = keyOf(command.purposeKey, "purpose"),
            policyBasisKey = keyOf(command.policyBasisKey, "policy basis"),
            idempotencyKey = idempotencyKey,
            transferRegion = command.transferRegion?.trim()?.ifBlank { null }
                .takeIf { command.requestKind == InformationRequestPrivacyRequestKind.EXPORT },
            correction = correction,
        )
    }

    private fun fingerprintOf(command: RecordInformationRequestPrivacyRequestCommand): String =
        CommandRequestFingerprint.of(
            SUBMIT_OPERATION,
            command.subjectIdentityRefId,
            command.requestKind,
            command.purposeKey,
            command.policyBasisKey,
            command.transferRegion.orEmpty(),
            command.correction?.submissionItemId,
            command.correction?.value != null,
            command.correction?.value,
            command.correction?.narrative.orEmpty(),
            command.correction?.reasonCode?.trim().orEmpty(),
        )

    private fun keyOf(raw: String, name: String): String
    {
        val key = raw.trim().lowercase()
        if (!KEY.matches(key)) throw InformationRequestCommandRequestException("A $name is a lowercase machine key")
        return key
    }

    private companion object
    {
        const val DETAIL_LENGTH = 512
        const val SUBMIT_OPERATION = "information_request.privacy_request.submit"
        val KEY = Regex("^[a-z][a-z0-9_.-]{0,127}$")
        val logger = LoggerFactory.getLogger(InformationRequestPrivacyService::class.java)
    }
}
