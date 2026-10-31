package com.docuhyphen.app.api.service.informationrequest.party

import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.util.*

@ApplicationScoped
class InformationRequestGroupRemovalService @Inject constructor(
    private val partyRepository: InformationRequestPartyRepository,
    private val partyRevocations: InformationRequestPartyRevocationService,
    private val gate: InformationRequestMutationGate,
) {
    @Transactional
    fun groupRemoved(groupId: UUID): List<UUID> {
        val parties = partyRepository.findActiveForPrincipal(PrincipalKind.PRINCIPAL_GROUP, groupId)
            .sortedWith(compareBy({ it.informationRequestId }, { it.id }))
        gate.lockExisting(parties.map { it.informationRequestId }.distinct())
        return parties.mapNotNull { party ->
            partyRevocations.revokeForGroupRemoval(
                party.informationRequestId,
                party.id
            )?.id
        }
    }
}
