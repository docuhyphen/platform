package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestAcceptedFact
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAcceptedFactFreshness
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAcceptedFactView
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestAcceptedFactRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestAcceptedFactRevocationRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.time.Clock
import java.util.UUID

@ApplicationScoped
class InformationRequestAcceptedFactStanding @Inject constructor(
    private val factRepository: InformationRequestAcceptedFactRepository,
    private val revocationRepository: InformationRequestAcceptedFactRevocationRepository,
    private val clock: Clock,
)
{
    fun view(fact: InformationRequestAcceptedFact): InformationRequestAcceptedFactView = views(listOf(fact)).single()

    fun views(facts: List<InformationRequestAcceptedFact>): List<InformationRequestAcceptedFactView>
    {
        val ids = facts.map { it.id }
        val revocations = revocationRepository.findForFacts(ids).associateBy { it.factId }
        val superseding = factRepository.findSuperseding(ids).associateBy { requireNotNull(it.supersedesFactId) }
        return facts.map { fact ->
            InformationRequestAcceptedFactView(
                fact = fact,
                revocation = revocations[fact.id],
                supersededByFactId = superseding[fact.id]?.id,
                freshness = freshnessOf(fact),
            )
        }
    }

    fun activeForKey(
        ownerType: InformationRequestOwnerType,
        ownerId: UUID,
        subjectIdentityRefId: UUID,
        fieldDefinitionIds: Collection<UUID>,
        purposeKey: String,
    ): List<InformationRequestAcceptedFact> =
        views(factRepository.findForKey(ownerType, ownerId, subjectIdentityRefId, fieldDefinitionIds, purposeKey))
            .filter { it.revocation == null && it.supersededByFactId == null }
            .map { it.fact }

    private fun freshnessOf(fact: InformationRequestAcceptedFact): InformationRequestAcceptedFactFreshness
    {
        val now = clock.instant()
        return when
        {
            fact.expiresAt?.toInstant()?.isBefore(now) == true -> InformationRequestAcceptedFactFreshness.EXPIRED
            fact.validFrom.toInstant().isAfter(now) || fact.validTo?.toInstant()?.isBefore(now) == true ->
                InformationRequestAcceptedFactFreshness.OUTSIDE_VALID_PERIOD
            else -> InformationRequestAcceptedFactFreshness.CURRENT
        }
    }
}
