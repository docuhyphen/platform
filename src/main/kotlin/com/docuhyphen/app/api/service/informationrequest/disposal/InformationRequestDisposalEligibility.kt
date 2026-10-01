package com.docuhyphen.app.api.service.informationrequest.disposal

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.entity.RecordDisposalBasis
import com.docuhyphen.app.api.model.entity.RecordPreservationHold
import com.docuhyphen.app.api.model.informationrequest.disposal.InformationRequestDisposalAssessment
import com.docuhyphen.app.api.model.recordpreservation.RecordDisposalObjectCandidate
import com.docuhyphen.app.api.model.recordpreservation.RecordOwnerRef
import com.docuhyphen.app.api.model.recordpreservation.RecordPreservationKey
import com.docuhyphen.app.api.model.recordpreservation.RecordPreservationResourceTypes
import com.docuhyphen.app.api.repository.informationrequest.disposal.InformationRequestDisposalRepository
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.recordpreservation.RecordDisposalService
import com.docuhyphen.app.api.service.recordpreservation.RecordPreservationHoldService
import com.docuhyphen.app.api.service.recordpreservation.RecordRetentionScheduleService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.time.Duration
import java.time.Instant

@ApplicationScoped
class InformationRequestDisposalEligibility @Inject constructor(
    private val disposalRepository: InformationRequestDisposalRepository,
    private val schedules: RecordRetentionScheduleService,
    private val holds: RecordPreservationHoldService,
    private val disposals: RecordDisposalService,
)
{
    fun assess(request: InformationRequest, basis: RecordDisposalBasis, now: Instant): InformationRequestDisposalAssessment
    {
        val owner = ownerOf(request)
        val schedule = schedules.current(owner, RecordPreservationResourceTypes.INFORMATION_REQUEST)
        fun refused(code: String, detail: String, covering: List<RecordPreservationHold> = emptyList(), from: Instant? = null) =
            InformationRequestDisposalAssessment.Refused(owner, schedule, covering, code, detail, from)

        if (disposals.viewFor(RecordPreservationResourceTypes.INFORMATION_REQUEST, request.id) != null)
        {
            return refused(InformationRequestErrorCatalog.DISPOSAL_IN_PROGRESS, "This request is already claimed for disposal")
        }
        val finishedAt = finishedAt(request)
            ?: return refused(InformationRequestErrorCatalog.RECORD_NOT_FINISHED, "Only a finished request is disposed")
        schedule?.let {
            val minimum = finishedAt.plus(Duration.ofDays(it.minimumRetentionDays.toLong()))
            if (now.isBefore(minimum))
            {
                return refused(InformationRequestErrorCatalog.RETENTION_REQUIRED, "The minimum retention has not elapsed", from = minimum)
            }
        }
        if (basis == RecordDisposalBasis.RETENTION_SCHEDULE)
        {
            val disposalDays = schedule?.disposalAfterDays
                ?: return refused(InformationRequestErrorCatalog.RETENTION_REQUIRED, "No retention schedule disposes this request automatically")
            val due = finishedAt.plus(Duration.ofDays(disposalDays.toLong()))
            if (now.isBefore(due))
            {
                return refused(InformationRequestErrorCatalog.RETENTION_REQUIRED, "The disposal age has not elapsed", from = due)
            }
        }
        val objects = disposalRepository.storedObjects(request.id)
        val keys = scopeKeys(request, objects)
        val covering = holds.coveringHolds(owner, keys)
        if (covering.isNotEmpty())
        {
            return refused(InformationRequestErrorCatalog.RECORD_HELD, "A record preservation hold covers this request", covering)
        }
        val references = disposalRepository.liveReferences(request.id)
        if (references.isNotEmpty())
        {
            return refused(
                InformationRequestErrorCatalog.RECORD_REFERENCED,
                "Another live record still refers to this request: ${references.joinToString()}",
            )
        }
        return InformationRequestDisposalAssessment.Eligible(owner, schedule, keys, objects)
    }

    private fun scopeKeys(request: InformationRequest, objects: List<RecordDisposalObjectCandidate>): List<RecordPreservationKey> =
        buildList {
            add(RecordPreservationKey(RecordPreservationResourceTypes.INFORMATION_REQUEST, request.id.toString(), direct = true))
            add(RecordPreservationKey(RecordPreservationResourceTypes.EXCHANGE, request.exchangeId.toString(), direct = false))
            request.ownerOrganizationId?.let { add(RecordPreservationKey(RecordPreservationResourceTypes.ORGANIZATION, it.toString(), direct = false)) }
            request.ownerUserId?.let { add(RecordPreservationKey(RecordPreservationResourceTypes.APP_USER, it.toString(), direct = false)) }
            disposalRepository.subjectsOf(request.id).forEach {
                add(RecordPreservationKey(RecordPreservationResourceTypes.SUBJECT_IDENTITY, it.toString(), direct = false))
            }
            objects.filter { it.retainedReason == null }.forEach {
                add(RecordPreservationKey(RecordPreservationResourceTypes.DOCUMENT, it.documentId.toString(), direct = true))
                add(RecordPreservationKey(RecordPreservationResourceTypes.DOCUMENT_VERSION, it.documentVersionId.toString(), direct = true))
            }
        }.distinct()

    companion object
    {
        fun ownerOf(request: InformationRequest): RecordOwnerRef = when (request.ownerType)
        {
            InformationRequestOwnerType.ORGANIZATION -> RecordOwnerRef.organization(requireNotNull(request.ownerOrganizationId))
            InformationRequestOwnerType.USER -> RecordOwnerRef.user(requireNotNull(request.ownerUserId))
        }

        fun finishedAt(request: InformationRequest): Instant?
        {
            if (!request.state.isTerminal) return null
            return listOfNotNull(request.closedAt, request.cancelledAt, request.supersededAt, request.expiredAt)
                .minOfOrNull { it.toInstant() }
        }
    }
}
