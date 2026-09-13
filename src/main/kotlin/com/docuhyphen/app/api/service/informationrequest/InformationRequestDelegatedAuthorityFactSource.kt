package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.repository.informationrequest.InformationRequestDelegatedAuthorityRepository
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@ApplicationScoped
class InformationRequestDelegatedAuthorityFactSource @Inject constructor(
    private val authorityRepository: InformationRequestDelegatedAuthorityRepository,
)
{
    fun factsFor(
        requestId: UUID,
        requirementId: UUID,
        assignedPartyIds: Set<UUID>,
    ): List<InformationRequestRequirementDelegatedAuthorityFact>
    {
        val now = Timestamp.from(Instant.now())
        return if (assignedPartyIds.isEmpty())
        {
            emptyList()
        }
        else
        {
            authorityRepository.findForRequest(requestId)
                .asSequence()
                .filter { it.informationRequestId == requestId }
                .filter { it.active }
                .filter { !it.effectiveAt.after(now) }
                .filter { it.expiresAt == null || it.expiresAt!!.after(now) }
                .filter { it.assignedPartyId in assignedPartyIds }
                .filter { it.requirementId == null || it.requirementId == requirementId }
                .map {
                    InformationRequestRequirementDelegatedAuthorityFact(
                        authorityId = it.id,
                        assignedPartyId = it.assignedPartyId,
                        delegatePrincipal = PrincipalRef(
                            kind = it.delegatePrincipalKind,
                            id = it.delegatePrincipalId,
                        ),
                        requestId = it.informationRequestId,
                        requirementId = it.requirementId,
                        active = true,
                    )
                }
                .toList()
        }
    }
}
