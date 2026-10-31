package com.docuhyphen.app.api.service.informationrequest.disposal

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.disposal.InformationRequestDisposalAssessment
import com.docuhyphen.app.api.model.recordpreservation.RecordDisposalObjectCandidate
import com.docuhyphen.app.api.model.recordpreservation.RecordOwnerRef
import com.docuhyphen.app.api.model.recordpreservation.RecordPreservationKey
import com.docuhyphen.app.api.model.recordpreservation.RecordPreservationResourceTypes
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.disposal.InformationRequestDisposalRepository
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.recordpreservation.RecordDisposalService
import com.docuhyphen.app.api.service.recordpreservation.RecordPreservationHoldService
import com.docuhyphen.app.api.service.recordpreservation.RecordRetentionScheduleService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.time.Duration
import java.time.Instant
import java.util.*

@ApplicationScoped
class InformationRequestDisposalEligibility @Inject constructor(
    private val disposalRepository: InformationRequestDisposalRepository,
    private val requestRepository: InformationRequestRepository,
    private val schedules: RecordRetentionScheduleService,
    private val holds: RecordPreservationHoldService,
    private val disposals: RecordDisposalService,
)
{
    fun lockForClaim(request: InformationRequest) {
        disposalRepository.lockClaimBoundary(request.id)
    }

    fun referrers(requestId: UUID): Set<UUID> =
        disposalRepository.references(requestId).mapNotNull { it.referrer }.toSet()

    fun assess(
        request: InformationRequest,
        basis: RecordDisposalBasis,
        now: Instant,
        companions: Set<UUID> = emptySet(),
    ): InformationRequestDisposalAssessment
    {
        val owner = ownerOf(request)
        val schedule = schedules.current(owner, RecordPreservationResourceTypes.INFORMATION_REQUEST)
        fun refused(
            code: String,
            detail: String,
            covering: List<RecordPreservationHold> = emptyList(),
            from: Instant? = null,
            references: List<String> = emptyList(),
        ) =
            InformationRequestDisposalAssessment.Refused(owner, schedule, covering, code, detail, from, references)

        if (disposals.viewFor(RecordPreservationResourceTypes.INFORMATION_REQUEST, request.id) != null)
        {
            return refused(
                InformationRequestErrorCatalog.DISPOSAL_IN_PROGRESS,
                "This request is already claimed for disposal"
            )
        }
        val finishedAt = finishedAt(request)
            ?: return refused(InformationRequestErrorCatalog.RECORD_NOT_FINISHED, "Only a finished request is disposed")
        schedule?.let {
            val minimum = finishedAt.plus(Duration.ofDays(it.minimumRetentionDays.toLong()))
            if (now.isBefore(minimum))
            {
                return refused(
                    InformationRequestErrorCatalog.RETENTION_REQUIRED,
                    "The minimum retention has not elapsed",
                    from = minimum
                )
            }
        }
        if (basis == RecordDisposalBasis.RETENTION_SCHEDULE)
        {
            val disposalDays = schedule?.disposalAfterDays
                ?: return refused(
                    InformationRequestErrorCatalog.RETENTION_REQUIRED,
                    "No retention schedule disposes this request automatically"
                )
            val due = finishedAt.plus(Duration.ofDays(disposalDays.toLong()))
            if (now.isBefore(due))
            {
                return refused(
                    InformationRequestErrorCatalog.RETENTION_REQUIRED,
                    "The disposal age has not elapsed",
                    from = due
                )
            }
        }
        val objects = disposalRepository.storedObjects(request.id)
        val coSources = disposalRepository.exportCoSources(request.id).mapNotNull(requestRepository::findById)
        val keys = scopeKeysOf(request, objects, coSources)
        val covering = holds.coveringHolds(owner, keys)
        if (covering.isNotEmpty())
        {
            return refused(
                InformationRequestErrorCatalog.RECORD_HELD,
                "A record preservation hold covers this request",
                covering
            )
        }
        val references = disposalRepository.references(request.id)
            .filter { it.referrer == null || it.referrer !in companions }
            .map { it.kind }
            .distinct() + listOfNotNull(
            SHARED_RECORD_EXPORT.takeIf { coSources.any { needsSharedExport(it, owner, schedule, now) } },
        )
        if (references.isNotEmpty())
        {
            return refused(
                InformationRequestErrorCatalog.RECORD_REFERENCED,
                "Another live record still refers to this request: ${references.joinToString()}",
                references = references,
            )
        }
        return InformationRequestDisposalAssessment.Eligible(owner, schedule, keys, objects)
    }

    fun coveringHolds(request: InformationRequest): List<RecordPreservationHold> {
        val coSources = disposalRepository.exportCoSources(request.id).mapNotNull(requestRepository::findById)
        return holds.coveringHolds(
            ownerOf(request),
            scopeKeysOf(request, disposalRepository.storedObjects(request.id), coSources),
        )
    }

    private fun scopeKeysOf(
        request: InformationRequest,
        objects: List<RecordDisposalObjectCandidate>,
        coSources: List<InformationRequest>,
    ): List<RecordPreservationKey> =
        (scopeKeys(request, objects) + coSources.flatMap { scopeKeys(it, disposalRepository.storedObjects(it.id)) })
            .distinct()

    private fun needsSharedExport(
        coSource: InformationRequest,
        owner: RecordOwnerRef,
        schedule: RecordRetentionSchedule?,
        now: Instant,
    ): Boolean {
        if (ownerOf(coSource) != owner) return true
        if (disposals.viewFor(RecordPreservationResourceTypes.INFORMATION_REQUEST, coSource.id) != null) return false
        val finishedAt = finishedAt(coSource) ?: return true
        val minimumDays = schedule?.minimumRetentionDays ?: return false
        return now.isBefore(finishedAt.plus(Duration.ofDays(minimumDays.toLong())))
    }

    private fun scopeKeys(
        request: InformationRequest,
        objects: List<RecordDisposalObjectCandidate>
    ): List<RecordPreservationKey> =
        buildList {
            add(
                RecordPreservationKey(
                    RecordPreservationResourceTypes.INFORMATION_REQUEST,
                    request.id.toString(),
                    direct = true
                )
            )
            add(
                RecordPreservationKey(
                    RecordPreservationResourceTypes.EXCHANGE,
                    request.exchangeId.toString(),
                    direct = false
                )
            )
            request.ownerOrganizationId?.let {
                add(
                    RecordPreservationKey(
                        RecordPreservationResourceTypes.ORGANIZATION,
                        it.toString(),
                        direct = false
                    )
                )
            }
            request.ownerUserId?.let {
                add(
                    RecordPreservationKey(
                        RecordPreservationResourceTypes.APP_USER,
                        it.toString(),
                        direct = false
                    )
                )
            }
            disposalRepository.subjectsOf(request.id).forEach {
                add(
                    RecordPreservationKey(
                        RecordPreservationResourceTypes.SUBJECT_IDENTITY,
                        it.toString(),
                        direct = false
                    )
                )
            }
            disposalRepository.ownedRecords(request.id).forEach { (resourceType, resourceId) ->
                add(RecordPreservationKey(resourceType, resourceId, direct = true))
            }
            objects.filter { it.retainedReason == null }.forEach {
                add(
                    RecordPreservationKey(
                        RecordPreservationResourceTypes.DOCUMENT,
                        it.documentId.toString(),
                        direct = true
                    )
                )
                add(
                    RecordPreservationKey(
                        RecordPreservationResourceTypes.DOCUMENT_VERSION,
                        it.documentVersionId.toString(),
                        direct = true
                    )
                )
            }
        }.distinct()

    companion object
    {
        const val SHARED_RECORD_EXPORT = "SHARED_RECORD_EXPORT"

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
