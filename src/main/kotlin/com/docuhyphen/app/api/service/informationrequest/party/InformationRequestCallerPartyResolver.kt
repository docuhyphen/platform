package com.docuhyphen.app.api.service.informationrequest.party

import com.docuhyphen.app.api.model.entity.InformationRequestParty
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestRequirementAuthorizationContextProvider
import com.docuhyphen.app.api.service.organization.PrincipalGroupService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class InformationRequestCallerPartyResolver @Inject constructor(
    private val partyRepository: InformationRequestPartyRepository,
    private val requirementContext: InformationRequestRequirementAuthorizationContextProvider,
    private val principalGroups: PrincipalGroupService,
) {
    fun activePartiesActedFor(principal: PrincipalRef): List<InformationRequestParty> {
        val identities = requirementContext.linkedIdentitiesOf(principal)
        val groups = identities
            .flatMap { principalGroups.getActiveGroupIdsForPrincipal(it.kind, it.id) }
            .map { PrincipalRef(PrincipalKind.PRINCIPAL_GROUP, it) }
        return (identities + groups)
            .flatMap { partyRepository.findActiveForPrincipal(it.kind, it.id) }
            .distinctBy { it.id }
            .filter { principal in requirementContext.principalsActingFor(it) }
    }
}
