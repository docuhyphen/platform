package com.docuhyphen.app.api.service.informationrequest.lifecycle

import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.recordpreservation.RecordOwnerRef
import com.docuhyphen.app.api.model.recordpreservation.RecordOwnershipChange
import com.docuhyphen.app.api.model.recordpreservation.RecordOwnershipChangeDecision
import com.docuhyphen.app.api.model.recordpreservation.RecordOwnershipChangeOutcome
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestPartyRevocationService
import com.docuhyphen.app.api.service.recordpreservation.RecordOwnershipChangePolicy
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.util.*

@ApplicationScoped
class InformationRequestOwnershipChangeService @Inject constructor(
    private val partyRepository: InformationRequestPartyRepository,
    private val requestRepository: InformationRequestRepository,
    private val policy: RecordOwnershipChangePolicy,
    private val partyRevocations: InformationRequestPartyRevocationService,
    private val gate: InformationRequestMutationGate,
)
{
    @Transactional
    fun memberRemoved(organizationId: UUID, appUserId: UUID): List<RecordOwnershipChangeOutcome> {
        val outcomes = partyRepository.findActiveForPrincipal(PrincipalKind.USER, appUserId)
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
                RecordOwnershipChangeOutcome(party.id, party.informationRequestId, decision)
            }
        val revoked = outcomes.filter { it.decision == RecordOwnershipChangeDecision.REVOKE }
        gate.lockExisting(revoked.map { it.parentResourceId })
        revoked.forEach { partyRevocations.revokeForOwnershipChange(it.parentResourceId, it.resourceId) }
        return outcomes
    }

    private companion object
    {
        const val PARTY = "INFORMATION_REQUEST_PARTY"
    }
}
