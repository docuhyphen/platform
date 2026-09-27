package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.document.DocumentVersionDeletionOutcome
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.RecordDisposalBasis
import com.docuhyphen.app.api.model.entity.RecordDisposalDeletionOutcome
import com.docuhyphen.app.api.model.entity.RecordDisposalObject
import com.docuhyphen.app.api.model.entity.RecordDisposalState
import com.docuhyphen.app.api.model.informationrequest.InformationRequestDisposalAssessment
import com.docuhyphen.app.api.model.informationrequest.InformationRequestDisposalOutcome
import com.docuhyphen.app.api.model.recordpreservation.OpenRecordDisposalClaimCommand
import com.docuhyphen.app.api.model.recordpreservation.RecordDisposalView
import com.docuhyphen.app.api.model.recordpreservation.RecordOwnerRef
import com.docuhyphen.app.api.model.recordpreservation.RecordPreservationResourceTypes
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.recordpreservation.RecordDisposalService
import com.docuhyphen.app.api.service.recordpreservation.RecordPreservationAudit
import com.docuhyphen.app.api.service.storage.DocumentVersionContentService
import io.quarkus.narayana.jta.QuarkusTransaction
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

@ApplicationScoped
class InformationRequestDisposalService @Inject constructor(
    private val requestRepository: InformationRequestRepository,
    private val eligibility: InformationRequestDisposalEligibility,
    private val disposals: RecordDisposalService,
    private val content: DocumentVersionContentService,
    private val audit: RecordPreservationAudit,
    private val clock: Clock,
)
{
    fun claim(requestId: UUID, basis: RecordDisposalBasis, privacyRequestId: UUID?, actor: PrincipalRef): InformationRequestDisposalOutcome =
        QuarkusTransaction.requiringNew().call { assessAndOpen(requestId, basis, privacyRequestId, actor) }

    private fun assessAndOpen(requestId: UUID, basis: RecordDisposalBasis, privacyRequestId: UUID?, actor: PrincipalRef): InformationRequestDisposalOutcome
    {
        val request = requestRepository.findRequestByIdForUpdate(requestId)
            ?: return InformationRequestDisposalOutcome.Refused(InformationRequestErrorCatalog.NOT_FOUND, "Information Request not found")
        return when (val assessment = eligibility.assess(request, basis, clock.instant()))
        {
            is InformationRequestDisposalAssessment.Refused ->
            {
                audit.disposal(
                    AuditEventType.RECORD_DISPOSAL_DENIED, assessment.owner, TARGET, request.id.toString(), actor,
                    mapOf("reasonCode" to assessment.reasonCode, "basis" to basis.name, "holdCount" to assessment.holds.size.toString()),
                    "${AuditEventType.RECORD_DISPOSAL_DENIED.key}|${request.id}|${basis.name}|${assessment.reasonCode}|${clock.instant()}",
                    AuditOutcome.DENIED,
                )
                InformationRequestDisposalOutcome.Refused(assessment.reasonCode, assessment.detail)
            }
            is InformationRequestDisposalAssessment.Eligible ->
            {
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
                )
                InformationRequestDisposalOutcome.Claimed(view)
            }
        }
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
                        logger.warn("Disposal claim {} could not delete a stored object; it will be retried", claimId, failure)
                        QuarkusTransaction.requiringNew().run { disposals.recordAttemptFailure(claimId, failure.javaClass.simpleName) }
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
                    QuarkusTransaction.requiringNew().run { disposals.recordAttemptFailure(claimId, failure.javaClass.simpleName) }
                    return RecordDisposalState.OBJECTS_DELETED
                }
        }
        return RecordDisposalState.FINALIZED
    }

    private fun finalize(claimId: UUID, owner: RecordOwnerRef, view: RecordDisposalView)
    {
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

    fun claimAndProcess(requestId: UUID, basis: RecordDisposalBasis, privacyRequestId: UUID?, actor: PrincipalRef): InformationRequestDisposalOutcome
    {
        val outcome = claim(requestId, basis, privacyRequestId, actor)
        if (outcome is InformationRequestDisposalOutcome.Claimed) process(outcome.view.claim.id)
        return outcome
    }

    private fun recordDeleted(view: RecordDisposalView, owner: RecordOwnerRef, stored: RecordDisposalObject, outcome: DocumentVersionDeletionOutcome)
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

    companion object
    {
        const val TARGET = RecordPreservationResourceTypes.INFORMATION_REQUEST
        val SYSTEM = PrincipalRef(PrincipalKind.SERVICE_ACCOUNT, UUID(0, 0))
        private val logger = LoggerFactory.getLogger(InformationRequestDisposalService::class.java)
    }
}
