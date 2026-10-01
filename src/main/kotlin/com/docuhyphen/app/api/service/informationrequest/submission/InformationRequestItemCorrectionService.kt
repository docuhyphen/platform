package com.docuhyphen.app.api.service.informationrequest.submission

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.entity.InformationRequestItemCorrection
import com.docuhyphen.app.api.model.informationrequest.privacy.InformationRequestItemCorrectionInput
import com.docuhyphen.app.api.model.recordpreservation.RecordOwnerRef
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.privacy.InformationRequestItemCorrectionRepository
import com.docuhyphen.app.api.repository.informationrequest.privacy.InformationRequestPrivacyRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.submission.InformationRequestSubmissionItemRepository
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.audit.informationRequestAuditOwner
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Clock
import java.util.UUID

@ApplicationScoped
class InformationRequestItemCorrectionService @Inject constructor(
    private val itemRepository: InformationRequestSubmissionItemRepository,
    private val requestRepository: InformationRequestRepository,
    private val privacyRepository: InformationRequestPrivacyRequestRepository,
    private val correctionRepository: InformationRequestItemCorrectionRepository,
    private val auditRecorder: AuditRecorder,
    private val clock: Clock,
)
{
    @Transactional(Transactional.TxType.MANDATORY)
    fun correct(
        owner: RecordOwnerRef,
        subjectIdentityRefId: UUID,
        privacyRequestId: UUID,
        input: InformationRequestItemCorrectionInput,
        principal: PrincipalRef,
    ): InformationRequestItemCorrection
    {
        val reason = input.reasonCode.trim().ifBlank { throw InformationRequestCommandRequestException("A correction states its reason") }
        if (input.value == null && input.narrative.isNullOrBlank())
        {
            throw InformationRequestCommandRequestException("A correction states the corrected value or narrative")
        }
        val item = itemRepository.findById(input.submissionItemId)
            ?: throw InformationRequestLifecycleException(InformationRequestErrorCatalog.NOT_FOUND, "Submitted item not found")
        val subjectRequests = privacyRepository.subjectRequestIds(owner.kind, requireNotNull(owner.id), subjectIdentityRefId)
        if (item.informationRequestId !in subjectRequests)
        {
            throw InformationRequestLifecycleException(InformationRequestErrorCatalog.NOT_FOUND, "Submitted item not found for this subject")
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
                recordedByPrincipalKind = principal.kind
                recordedByPrincipalId = principal.id
                recordedAt = Timestamp.from(clock.instant())
            },
        )
        auditRecorder.record(
            AuditEventDraft(
                owner = informationRequestAuditOwner(request),
                eventTypeKey = AuditEventType.INFORMATION_REQUEST_ITEM_CORRECT.key,
                outcome = AuditOutcome.SUCCESS,
                actorId = principal.id,
                actorKind = AuditActorKind.forPrincipal(principal.kind),
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
}
