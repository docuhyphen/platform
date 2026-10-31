package com.docuhyphen.app.api.service.informationrequest.disposal

import com.docuhyphen.app.api.exception.RecordPreservationErrorCatalog
import com.docuhyphen.app.api.exception.RecordPreservationException
import com.docuhyphen.app.api.model.document.DocumentVersionDeletionOutcome
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.disposal.InformationRequestDisposalAssessment
import com.docuhyphen.app.api.model.informationrequest.disposal.InformationRequestDisposalBatch
import com.docuhyphen.app.api.model.informationrequest.disposal.InformationRequestDisposalOutcome
import com.docuhyphen.app.api.model.recordpreservation.OpenRecordDisposalClaimCommand
import com.docuhyphen.app.api.model.recordpreservation.RecordDisposalView
import com.docuhyphen.app.api.model.recordpreservation.RecordOwnerRef
import com.docuhyphen.app.api.model.recordpreservation.RecordPreservationResourceTypes
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.recordpreservation.RecordDisposalService
import com.docuhyphen.app.api.service.recordpreservation.RecordPreservationAudit
import com.docuhyphen.app.api.service.storage.DocumentVersionContentService
import io.quarkus.narayana.jta.QuarkusTransaction
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.time.Clock
import java.util.*

@ApplicationScoped
class InformationRequestDisposalService @Inject constructor(
    private val requestRepository: InformationRequestRepository,
    private val gate: InformationRequestMutationGate,
    private val eligibility: InformationRequestDisposalEligibility,
    private val disposals: RecordDisposalService,
    private val content: DocumentVersionContentService,
    private val audit: RecordPreservationAudit,
    private val clock: Clock,
)
{
    fun claim(
        requestId: UUID,
        basis: RecordDisposalBasis,
        privacyRequestId: UUID?,
        actor: PrincipalRef
    ): InformationRequestDisposalOutcome =
        QuarkusTransaction.requiringNew().call { assessAndOpen(requestId, basis, privacyRequestId, actor) }

    @Transactional(Transactional.TxType.MANDATORY)
    fun claimFinished(
        requestIds: Collection<UUID>,
        basis: RecordDisposalBasis,
        privacyRequestId: UUID?,
        actor: PrincipalRef,
        sessionRef: String? = null,
    ): InformationRequestDisposalBatch {
        val now = clock.instant()
        val requests = gate.lockExisting(requestIds).map { it.request }
        val (finished, open) = requests.partition { InformationRequestDisposalEligibility.finishedAt(it) != null }
        val companions = finished.map { it.id }.toSet()
        val existing =
            finished.mapNotNull { request -> disposals.viewFor(TARGET, request.id)?.let { request.id to it } }.toMap()
        val assessments = finished.filter { it.id !in existing }.associateWith {
            eligibility.lockForClaim(it)
            eligibility.assess(it, basis, now, companions)
        }
        val refusals = assessments.entries
            .mapNotNull { (request, assessment) ->
                (assessment as? InformationRequestDisposalAssessment.Refused)
                    ?.let { request.id to InformationRequestDisposalOutcome.Refused(it.reasonCode, it.detail) }
            }
            .toMap()
        if (refusals.isNotEmpty()) throw InformationRequestDisposalRefusedException(refusals)
        val claimed = referrersFirst(finished).map { request ->
            existing[request.id]?.let { return@map it }
            try {
                open(
                    request,
                    basis,
                    privacyRequestId,
                    actor,
                    assessments.getValue(request) as InformationRequestDisposalAssessment.Eligible,
                    sessionRef,
                )
            } catch (failure: RuntimeException) {
                throw claimRefusalOf(request.id, failure) ?: failure
            }
        }
        return InformationRequestDisposalBatch(claimed, open.map { it.id })
    }

    private fun referrersFirst(requests: List<InformationRequest>): List<InformationRequest> {
        val ids = requests.map { it.id }.toSet()
        val waitsFor =
            requests.associate { request -> request.id to eligibility.referrers(request.id).filter { it in ids } }
        val ordered = mutableListOf<InformationRequest>()
        var remaining = requests
        while (remaining.isNotEmpty()) {
            val placed = ordered.map { it.id }.toSet()
            val ready =
                remaining.filter { request -> waitsFor.getValue(request.id).all { it in placed } }.ifEmpty { remaining }
            ordered += ready
            remaining = remaining - ready.toSet()
        }
        return ordered
    }

    private fun assessAndOpen(
        requestId: UUID,
        basis: RecordDisposalBasis,
        privacyRequestId: UUID?,
        actor: PrincipalRef
    ): InformationRequestDisposalOutcome
    {
        val request = requestRepository.findRequestByIdForUpdate(requestId)
            ?: return InformationRequestDisposalOutcome.Refused(
                InformationRequestErrorCatalog.NOT_FOUND,
                "Information Request not found"
            )
        eligibility.lockForClaim(request)
        return when (val assessment = eligibility.assess(request, basis, clock.instant()))
        {
            is InformationRequestDisposalAssessment.Refused ->
            {
                audit.disposal(
                    AuditEventType.RECORD_DISPOSAL_DENIED, assessment.owner, TARGET, request.id.toString(), actor,
                    mapOf(
                        "reasonCode" to assessment.reasonCode,
                        "basis" to basis.name,
                        "holdCount" to assessment.holds.size.toString()
                    ),
                    denialKey(request.id, basis, assessment, actor),
                    AuditOutcome.DENIED,
                )
                InformationRequestDisposalOutcome.Refused(assessment.reasonCode, assessment.detail)
            }

            is InformationRequestDisposalAssessment.Eligible ->
                InformationRequestDisposalOutcome.Claimed(open(request, basis, privacyRequestId, actor, assessment))
        }
    }

    private fun open(
        request: InformationRequest,
        basis: RecordDisposalBasis,
        privacyRequestId: UUID?,
        actor: PrincipalRef,
        assessment: InformationRequestDisposalAssessment.Eligible,
        sessionRef: String? = null,
    ): RecordDisposalView {
        val view = disposals.open(
            OpenRecordDisposalClaimCommand(
                resourceType = TARGET,
                resourceId = request.id,
                owner = assessment.owner,
                basis = basis,
                retentionScheduleId = assessment.schedule?.id.takeIf { basis == RecordDisposalBasis.RETENTION_SCHEDULE },
                privacyRequestId = privacyRequestId,
                principal = actor,
                scopeKeys = assessment.scopeKeys,
                objects = assessment.objects,
            ),
        )
        audit.disposal(
            AuditEventType.RECORD_DISPOSAL_CLAIMED, assessment.owner, TARGET, request.id.toString(), actor,
            mapOf(
                "claimId" to view.claim.id.toString(),
                "basis" to basis.name,
                "objectCount" to view.objects.size.toString(),
                "retainedObjectCount" to view.objects.count { it.retained }.toString(),
            ),
            "${AuditEventType.RECORD_DISPOSAL_CLAIMED.key}|${view.claim.id}",
            sessionRef = sessionRef,
        )
        return view
    }

    fun process(claimId: UUID): RecordDisposalState
    {
        val view = QuarkusTransaction.requiringNew().call { disposals.view(claimId) }
        val owner = RecordOwnerRef(view.claim.ownerKind, view.claim.ownerId)
        if (view.claim.state == RecordDisposalState.CLAIMED)
        {
            view.objects.filter { !it.retained && it.deletedAt == null }.forEach { stored ->
                val outcome = runCatching { content.delete(stored.storageLocatorKind, stored.storageLocator) }
                    .getOrElse { failure ->
                        logger.warn(
                            "Disposal claim {} could not delete a stored object; it will be retried",
                            claimId,
                            failure
                        )
                        QuarkusTransaction.requiringNew()
                            .run { recordFailure(view, owner, OBJECT_DELETION_STAGE, failure) }
                        return RecordDisposalState.CLAIMED
                    }
                QuarkusTransaction.requiringNew().run { recordDeleted(view, owner, stored, outcome) }
            }
            QuarkusTransaction.requiringNew().run { disposals.markObjectsDeleted(claimId) }
        }
        if (view.claim.state != RecordDisposalState.FINALIZED)
        {
            runCatching { QuarkusTransaction.requiringNew().run { finalize(claimId, owner, view) } }
                .onFailure { failure ->
                    logger.warn("Disposal claim {} could not be finalized; it will be retried", claimId, failure)
                    QuarkusTransaction.requiringNew().run { recordFailure(view, owner, FINALIZATION_STAGE, failure) }
                    return RecordDisposalState.OBJECTS_DELETED
                }
        }
        return RecordDisposalState.FINALIZED
    }

    private fun recordFailure(view: RecordDisposalView, owner: RecordOwnerRef, stage: String, failure: Throwable) {
        val errorCode = failure.javaClass.simpleName
        val claim = disposals.recordAttemptFailure(view.claim.id, errorCode)
        audit.disposal(
            AuditEventType.RECORD_DISPOSAL_ATTEMPT_FAILED, owner, TARGET, view.claim.resourceId.toString(), SYSTEM,
            mapOf(
                "claimId" to view.claim.id.toString(),
                "stage" to stage,
                "errorCode" to errorCode,
                "attemptCount" to claim.attemptCount.toString(),
            ),
            "${AuditEventType.RECORD_DISPOSAL_ATTEMPT_FAILED.key}|${view.claim.id}|${claim.attemptCount}",
            AuditOutcome.FAILURE,
        )
    }

    private fun finalize(claimId: UUID, owner: RecordOwnerRef, view: RecordDisposalView)
    {
        gate.lockExisting(listOf(view.claim.resourceId))
        val finalized = disposals.finalize(claimId)
        audit.disposal(
            AuditEventType.RECORD_DISPOSAL_FINALIZED, owner, TARGET, view.claim.resourceId.toString(), SYSTEM,
            mapOf(
                "claimId" to claimId.toString(),
                "deletedObjectCount" to (finalized.tombstone?.deletedObjectCount ?: 0).toString(),
                "retainedObjectCount" to (finalized.tombstone?.retainedObjectCount ?: 0).toString(),
            ),
            "${AuditEventType.RECORD_DISPOSAL_FINALIZED.key}|$claimId",
        )
    }

    fun claimAndProcess(
        requestId: UUID,
        basis: RecordDisposalBasis,
        privacyRequestId: UUID?,
        actor: PrincipalRef
    ): InformationRequestDisposalOutcome
    {
        val outcome = claim(requestId, basis, privacyRequestId, actor)
        if (outcome is InformationRequestDisposalOutcome.Claimed) process(outcome.view.claim.id)
        return outcome
    }

    private fun recordDeleted(
        view: RecordDisposalView,
        owner: RecordOwnerRef,
        stored: RecordDisposalObject,
        outcome: DocumentVersionDeletionOutcome
    )
    {
        disposals.recordObjectDeleted(
            stored.id,
            if (outcome == DocumentVersionDeletionOutcome.DELETED) RecordDisposalDeletionOutcome.DELETED else RecordDisposalDeletionOutcome.ABSENT,
        )
        audit.disposal(
            AuditEventType.RECORD_DISPOSAL_OBJECT_DELETED, owner, TARGET, view.claim.resourceId.toString(), SYSTEM,
            mapOf(
                "claimId" to view.claim.id.toString(),
                "documentVersionId" to stored.documentVersionId.toString(),
                "deletionOutcome" to outcome.name,
            ),
            "${AuditEventType.RECORD_DISPOSAL_OBJECT_DELETED.key}|${stored.id}",
        )
    }

    private fun denialKey(
        requestId: UUID,
        basis: RecordDisposalBasis,
        assessment: InformationRequestDisposalAssessment.Refused,
        actor: PrincipalRef,
    ): String {
        if (actor != SYSTEM) {
            return "${AuditEventType.RECORD_DISPOSAL_DENIED.key}|$requestId|${basis.name}|${assessment.reasonCode}|${clock.instant()}"
        }
        val material = listOf(
            basis.name,
            assessment.reasonCode,
            assessment.schedule?.id?.toString().orEmpty(),
            assessment.holds.map { "${it.id}:${it.holdRevision}" }.sorted().joinToString(","),
            assessment.references.sorted().joinToString(","),
        ).joinToString("|")
        return "${AuditEventType.RECORD_DISPOSAL_DENIED.key}|$requestId|${sha256(material)}"
    }

    private fun claimRefusalOf(requestId: UUID, failure: Throwable): InformationRequestDisposalRefusedException? =
        when {
            failure is RecordPreservationException && failure.reasonCode == RecordPreservationErrorCatalog.DISPOSAL_IN_PROGRESS ->
                InformationRequestDisposalRefusedException(
                    mapOf(
                        requestId to InformationRequestDisposalOutcome.Refused(
                            InformationRequestErrorCatalog.DISPOSAL_IN_PROGRESS,
                            "This request is already claimed for disposal",
                        ),
                    ),
                )

            generateSequence(failure) { it.cause?.takeIf { cause -> cause !== it } }
                .any { it.message.orEmpty().contains(HELD_CLAIM_REFUSAL) } ->
                InformationRequestDisposalRefusedException(
                    mapOf(
                        requestId to InformationRequestDisposalOutcome.Refused(
                            InformationRequestErrorCatalog.RECORD_HELD,
                            "A record preservation hold covers this request",
                        ),
                    ),
                )

            else -> null
        }

    @Transactional(Transactional.TxType.MANDATORY)
    fun recordPrivacyDenials(
        refusals: Map<UUID, String>,
        privacyRequestId: UUID,
        actor: PrincipalRef,
        sessionRef: String? = null
    ) {
        refusals.toSortedMap().forEach { (requestId, reasonCode) ->
            val request = requestRepository.findById(requestId) ?: return@forEach
            audit.disposal(
                AuditEventType.RECORD_DISPOSAL_DENIED,
                InformationRequestDisposalEligibility.ownerOf(request),
                TARGET,
                requestId.toString(),
                actor,
                mapOf(
                    "reasonCode" to reasonCode,
                    "basis" to RecordDisposalBasis.PRIVACY_DELETION.name,
                    "privacyRequestId" to privacyRequestId.toString(),
                ),
                "${AuditEventType.RECORD_DISPOSAL_DENIED.key}|$privacyRequestId|$requestId",
                AuditOutcome.DENIED,
                sessionRef = sessionRef,
            )
        }
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    companion object
    {
        const val TARGET = RecordPreservationResourceTypes.INFORMATION_REQUEST
        val SYSTEM = PrincipalRef(PrincipalKind.SERVICE_ACCOUNT, UUID(0, 0))
        private const val OBJECT_DELETION_STAGE = "OBJECT_DELETION"
        private const val FINALIZATION_STAGE = "FINALIZATION"
        private const val HELD_CLAIM_REFUSAL = "a held record cannot be claimed for disposal"
        private val logger = LoggerFactory.getLogger(InformationRequestDisposalService::class.java)
    }
}
