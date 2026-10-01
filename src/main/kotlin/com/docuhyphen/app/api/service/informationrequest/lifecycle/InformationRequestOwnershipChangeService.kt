package com.docuhyphen.app.api.service.informationrequest.lifecycle

import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.recordpreservation.RecordOwnerRef
import com.docuhyphen.app.api.model.recordpreservation.RecordOwnershipChange
import com.docuhyphen.app.api.model.recordpreservation.RecordOwnershipChangeDecision
import com.docuhyphen.app.api.model.recordpreservation.RecordOwnershipChangeOutcome
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestPartyService
import com.docuhyphen.app.api.service.recordpreservation.RecordOwnershipChangePolicy
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.util.UUID

@ApplicationScoped
class InformationRequestOwnershipChangeService @Inject constructor(
    private val partyRepository: InformationRequestPartyRepository,
    private val requestRepository: InformationRequestRepository,
    private val policy: RecordOwnershipChangePolicy,
    private val partyService: InformationRequestPartyService,
)
{
    @Transactional
    fun memberRemoved(organizationId: UUID, appUserId: UUID): List<RecordOwnershipChangeOutcome> =
        partyRepository.findActiveForPrincipal(PrincipalKind.USER, appUserId)
            .filter { party -> requestRepository.findById(party.informationRequestId)?.ownerOrganizationId == organizationId }
            .map { party ->
                val decision = policy.decide(
                    RecordOwnershipChange(
                        owner = RecordOwnerRef.organization(organizationId),
                        appUserId = appUserId,
                        resourceType = PARTY,
                        resourceId = party.id,
                        parentResourceId = party.informationRequestId,
                        roleKey = party.roleKey.name,
                    ),
                )
                if (decision == RecordOwnershipChangeDecision.REVOKE)
                {
                    partyService.revokeForOwnershipChange(party.informationRequestId, party.id)
                }
                RecordOwnershipChangeOutcome(party.id, party.informationRequestId, decision)
            }

    private companion object
    {
        const val PARTY = "INFORMATION_REQUEST_PARTY"
    }
}
