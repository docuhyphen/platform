package com.docuhyphen.app.api.service.informationrequest.acceptedfact

import com.docuhyphen.app.api.model.entity.InformationRequestAcceptedFact
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.informationrequest.acceptedfact.InformationRequestAcceptedFactFreshness
import com.docuhyphen.app.api.model.informationrequest.acceptedfact.InformationRequestAcceptedFactView
import com.docuhyphen.app.api.repository.informationrequest.acceptedfact.InformationRequestAcceptedFactEvidenceRepository
import com.docuhyphen.app.api.repository.informationrequest.acceptedfact.InformationRequestAcceptedFactRepository
import com.docuhyphen.app.api.repository.informationrequest.acceptedfact.InformationRequestAcceptedFactRevocationRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.time.Clock
import java.util.*

@ApplicationScoped
class InformationRequestAcceptedFactStanding @Inject constructor(
    private val factRepository: InformationRequestAcceptedFactRepository,
    private val revocationRepository: InformationRequestAcceptedFactRevocationRepository,
    private val evidenceRepository: InformationRequestAcceptedFactEvidenceRepository,
    private val clock: Clock,
)
{
    fun view(fact: InformationRequestAcceptedFact): InformationRequestAcceptedFactView = views(listOf(fact)).single()

    fun views(facts: List<InformationRequestAcceptedFact>): List<InformationRequestAcceptedFactView>
    {
        val ids = facts.map { it.id }
        val revocations = revocationRepository.findForFacts(ids).associateBy { it.factId }
        val superseding = factRepository.findSuperseding(ids).associateBy { requireNotNull(it.supersedesFactId) }
        val evidence = evidenceRepository.findForFacts(ids).groupBy { it.factId }
        return facts.map { fact ->
            InformationRequestAcceptedFactView(
                fact = fact,
                revocation = revocations[fact.id],
                supersededByFactId = superseding[fact.id]?.id,
                freshness = freshnessOf(fact),
                evidenceVersionIds = evidence[fact.id].orEmpty().map { it.evidenceVersionId },
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

    fun eligibleForReuse(
        ownerType: InformationRequestOwnerType,
        ownerId: UUID,
        subjectIdentityRefId: UUID,
        fieldDefinitionIds: Collection<UUID>,
        purposeKey: String,
    ): List<InformationRequestAcceptedFact> =
        views(factRepository.findForKey(ownerType, ownerId, subjectIdentityRefId, fieldDefinitionIds, purposeKey))
            .filter {
                it.revocation == null && it.supersededByFactId == null &&
                        it.freshness == InformationRequestAcceptedFactFreshness.CURRENT
            }
            .map { it.fact }

    private fun freshnessOf(fact: InformationRequestAcceptedFact): InformationRequestAcceptedFactFreshness
    {
        val now = clock.instant()
        return when
        {
            fact.expiresAt?.toInstant()
                ?.let { !it.isAfter(now) } == true -> InformationRequestAcceptedFactFreshness.EXPIRED

            fact.validFrom.toInstant().isAfter(now) || fact.validTo?.toInstant()?.let { !it.isAfter(now) } == true ->
                InformationRequestAcceptedFactFreshness.OUTSIDE_VALID_PERIOD

            else -> InformationRequestAcceptedFactFreshness.CURRENT
        }
    }
}
