package com.docuhyphen.app.api.service.informationrequest.disposal

import com.docuhyphen.app.api.model.entity.RecordDisposalBasis
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.disposal.InformationRequestDisposalStanding
import com.docuhyphen.app.api.model.informationrequest.disposal.InformationRequestWithheldHold
import com.docuhyphen.app.api.model.recordpreservation.RecordOwnerRef
import com.docuhyphen.app.api.model.recordpreservation.RecordPreservationResourceTypes
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.recordpreservation.RecordDisposalService
import com.docuhyphen.app.api.service.recordpreservation.RecordOwnerScopeAccess
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.time.Clock
import java.util.*

@ApplicationScoped
class InformationRequestDisposalQueryService @Inject constructor(
    private val gate: InformationRequestMutationGate,
    private val requestRepository: InformationRequestRepository,
    private val eligibility: InformationRequestDisposalEligibility,
    private val disposals: RecordDisposalService,
    private val holdReaders: RecordOwnerScopeAccess,
    private val clock: Clock,
)
{
    fun standing(requestId: UUID, access: RequestAccessContext): InformationRequestDisposalStanding
    {
        gate.authorizeRequest(access, listOf(Action.INFORMATION_REQUEST_VIEW_OPERATIONS), requestId)
        val request = requestRepository.findById(requestId)
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.NOT_FOUND,
                "Information Request not found"
            )
        val disposal = disposals.viewFor(RecordPreservationResourceTypes.INFORMATION_REQUEST, requestId)
        val assessment = if (disposal == null) eligibility.assess(
            request,
            RecordDisposalBasis.RETENTION_SCHEDULE,
            clock.instant()
        )
        else null
        val (readable, withheld) = eligibility.coveringHolds(request).partition { hold ->
            holdReaders.permits(
                access.principal,
                RecordOwnerRef(hold.ownerKind, hold.ownerId),
                Action.ORG_READ_AUDIT,
                access.authorization
            )
        }
        return InformationRequestDisposalStanding(
            requestId = requestId,
            assessment = assessment,
            disposal = disposal,
            readableHolds = readable,
            withheldHolds = withheld.map { hold ->
                InformationRequestWithheldHold(
                    hold.resourceType,
                    hold.scope,
                    hold.status,
                    hold.effectiveFrom.toInstant()
                )
            },
        )
    }
}
