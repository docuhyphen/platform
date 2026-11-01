package com.docuhyphen.app.api.service.informationrequest.submission

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.entity.InformationRequestItemCorrection
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.informationrequest.privacy.InformationRequestItemCorrectionInput
import com.docuhyphen.app.api.model.recordpreservation.RecordOwnerRef
import com.docuhyphen.app.api.model.recordpreservation.RecordPreservationResourceTypes
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.privacy.InformationRequestItemCorrectionRepository
import com.docuhyphen.app.api.repository.informationrequest.privacy.InformationRequestPrivacyRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.submission.InformationRequestSubmissionItemRepository
import com.docuhyphen.app.api.model.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestAuditActorLabelResolver
import com.docuhyphen.app.api.service.informationrequest.audit.informationRequestAuditOwner
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.recordpreservation.RecordDisposalService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Clock
import java.util.*

@ApplicationScoped
class InformationRequestItemCorrectionService @Inject constructor(
    private val itemRepository: InformationRequestSubmissionItemRepository,
    private val requestRepository: InformationRequestRepository,
    private val privacyRepository: InformationRequestPrivacyRequestRepository,
    private val correctionRepository: InformationRequestItemCorrectionRepository,
    private val auditRecorder: AuditRecorder,
    private val actorLabels: InformationRequestAuditActorLabelResolver,
    private val clock: Clock,
    private val disposals: RecordDisposalService,
)
{
    @Transactional(Transactional.TxType.MANDATORY)
    fun correct(
        owner: RecordOwnerRef,
        subjectIdentityRefId: UUID,
        privacyRequestId: UUID,
        input: InformationRequestItemCorrectionInput,
        actor: InformationRequestAuditActor,
    ): InformationRequestItemCorrection
    {
        val reason = requireWellFormed(input)
        val item = itemRepository.findById(input.submissionItemId)
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.NOT_FOUND,
                "Submitted item not found"
            )
        val subjectRequests =
            privacyRepository.subjectRequestIds(owner.kind, requireNotNull(owner.id), subjectIdentityRefId)
        if (item.informationRequestId !in subjectRequests)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.NOT_FOUND,
                "Submitted item not found"
            )
        }
        if (disposals.underDisposal(
                RecordPreservationResourceTypes.INFORMATION_REQUEST,
                listOf(item.informationRequestId)
            ).isNotEmpty()
        ) {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.DISPOSAL_IN_PROGRESS,
                "This request's record is being disposed; try again once its disposal finishes",
            )
        }
        val request = requireNotNull(requestRepository.findById(item.informationRequestId))
        val correction = correctionRepository.save(
            InformationRequestItemCorrection().apply {
                informationRequestId = item.informationRequestId
                packageId = item.packageId
                submissionItemId = item.id
                this.privacyRequestId = privacyRequestId
                correctedValueJson = input.value?.toString()
                correctedNarrative = input.narrative?.trim()?.ifBlank { null }
                reasonCode = reason
                recordedByPrincipalKind = actor.principal.kind
                recordedByPrincipalId = actor.principal.id
                recordedAt = Timestamp.from(clock.instant())
            },
        )
        val attribution = actorLabels.attribute(actor)
        auditRecorder.record(
            AuditEventDraft(
                owner = informationRequestAuditOwner(request),
                eventTypeKey = AuditEventType.INFORMATION_REQUEST_ITEM_CORRECT.key,
                outcome = AuditOutcome.SUCCESS,
                actorId = attribution.actorId,
                actorKind = attribution.actorKind,
                actorLabel = attribution.actorLabel,
                sessionId = attribution.sessionId,
                targetType = "INFORMATION_REQUEST",
                targetId = request.id.toString(),
                payload = mapOf(
                    "correctionId" to correction.id.toString(),
                    "submissionPackageId" to item.packageId.toString(),
                    "submissionItemId" to item.id.toString(),
                    "privacyRequestId" to privacyRequestId.toString(),
                    "reasonCode" to reason,
                ),
                idempotencyKey = "${AuditEventType.INFORMATION_REQUEST_ITEM_CORRECT.key}|${correction.id}",
                businessTransactionId = correction.id.toString(),
            ),
        )
        return correction
    }

    fun requireWellFormed(input: InformationRequestItemCorrectionInput): String {
        val reason = input.reasonCode.trim()
            .ifBlank { throw InformationRequestCommandRequestException("A correction states its reason") }
        if (reason.length > REASON_LENGTH) {
            throw InformationRequestCommandRequestException("A correction reason is at most $REASON_LENGTH characters")
        }
        if (input.value == null && input.narrative.isNullOrBlank()) {
            throw InformationRequestCommandRequestException("A correction states the corrected value or narrative")
        }
        return reason
    }

    fun correctionsFor(requestId: UUID): List<InformationRequestItemCorrection> =
        correctionRepository.findForRequest(requestId)

    private companion object {
        const val REASON_LENGTH = 128
    }
}
