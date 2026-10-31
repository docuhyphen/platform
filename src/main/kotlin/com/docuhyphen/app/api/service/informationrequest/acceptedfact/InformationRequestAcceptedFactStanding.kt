package com.docuhyphen.app.api.service.informationrequest.acceptedfact

import com.docuhyphen.app.api.model.entity.InformationRequestAcceptedFact
import com.docuhyphen.app.api.model.entity.InformationRequestAcceptedFactConflictState
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.informationrequest.acceptedfact.InformationRequestAcceptedFactFreshness
import com.docuhyphen.app.api.model.informationrequest.acceptedfact.InformationRequestAcceptedFactKey
import com.docuhyphen.app.api.model.informationrequest.acceptedfact.InformationRequestAcceptedFactView
import com.docuhyphen.app.api.model.recordpreservation.RecordPreservationResourceTypes
import com.docuhyphen.app.api.repository.informationrequest.acceptedfact.InformationRequestAcceptedFactEvidenceRepository
import com.docuhyphen.app.api.repository.informationrequest.acceptedfact.InformationRequestAcceptedFactRepository
import com.docuhyphen.app.api.repository.informationrequest.acceptedfact.InformationRequestAcceptedFactRevocationRepository
import com.docuhyphen.app.api.service.recordpreservation.RecordDisposalService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.time.Clock
import java.util.*

@ApplicationScoped
class InformationRequestAcceptedFactStanding @Inject constructor(
    private val factRepository: InformationRequestAcceptedFactRepository,
    private val revocationRepository: InformationRequestAcceptedFactRevocationRepository,
    private val evidenceRepository: InformationRequestAcceptedFactEvidenceRepository,
    private val disposals: RecordDisposalService,
    private val clock: Clock,
)
{
    fun view(fact: InformationRequestAcceptedFact): InformationRequestAcceptedFactView = views(listOf(fact)).single()

    fun views(facts: List<InformationRequestAcceptedFact>): List<InformationRequestAcceptedFactView>
    {
        val ids = facts.map { it.id }
        val revocations = revocationRepository.findForFacts(ids).associateBy { it.factId }
        val superseding = factRepository.findSupersedingIds(ids)
        val evidence = evidenceRepository.findForFacts(ids).groupBy { it.factId }
        val conflicts = conflictsAmong(facts)
        return facts.map { fact ->
            val conflicting = conflicts[fact.id]
            InformationRequestAcceptedFactView(
                fact = fact,
                revocation = revocations[fact.id],
                supersededByFactId = superseding[fact.id],
                freshness = freshnessOf(fact),
                evidenceVersionIds = evidence[fact.id].orEmpty().map { it.evidenceVersionId },
                conflictState = if (conflicting == null) InformationRequestAcceptedFactConflictState.NONE
                else InformationRequestAcceptedFactConflictState.CONFLICTING,
                conflictingFactId = conflicting,
            )
        }
    }

    private fun conflictsAmong(facts: List<InformationRequestAcceptedFact>): Map<UUID, UUID> {
        val keyed = facts.mapNotNull { fact -> keyOf(fact)?.let { fact to it } }
        if (keyed.isEmpty()) return emptyMap()
        val candidates = keyed.map { it.second }.distinct()
            .flatMap { key ->
                factRepository.findForKey(
                    key.ownerType, key.ownerId, key.subjectIdentityRefId, listOf(key.fieldDefinitionId), key.purposeKey,
                )
            }
            .distinctBy { it.id }
        val candidateIds = candidates.map { it.id }
        val inactive = revocationRepository.findForFacts(candidateIds).map { it.factId }.toSet() +
                factRepository.findSupersedingIds(candidateIds).keys
        val currentByKey = candidates.filter { it.id !in inactive }.groupBy { keyOf(it) }
        return keyed.mapNotNull { (fact, key) ->
            if (fact.id in inactive) return@mapNotNull null
            currentByKey[key].orEmpty()
                .filter { it.id != fact.id && it.canonicalValue != fact.canonicalValue }
                .maxByOrNull { it.promotedAt }
                ?.let { fact.id to it.id }
        }.toMap()
    }

    private fun keyOf(fact: InformationRequestAcceptedFact): InformationRequestAcceptedFactKey? {
        val ownerId = fact.ownerOrganizationId ?: fact.ownerUserId ?: return null
        return InformationRequestAcceptedFactKey(
            fact.ownerType, ownerId, fact.subjectIdentityRefId, fact.fieldDefinitionId, fact.purposeKey,
        )
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
    ): List<InformationRequestAcceptedFact> {
        val current =
            views(factRepository.findForKey(ownerType, ownerId, subjectIdentityRefId, fieldDefinitionIds, purposeKey))
                .filter {
                    it.revocation == null && it.supersededByFactId == null &&
                            it.freshness == InformationRequestAcceptedFactFreshness.CURRENT
                }
                .map { it.fact }
        val disposing = disposals.underDisposal(
            RecordPreservationResourceTypes.INFORMATION_REQUEST,
            current.map { it.sourceInformationRequestId },
        )
        val reusable = factRepository.reusableSourceRequestIds(current.map { it.sourceInformationRequestId })
        val corrected = factRepository.correctedSourceItemIds(current.map { it.sourceSubmissionItemId })
        return current.filter {
            it.sourceInformationRequestId !in disposing && it.sourceInformationRequestId in reusable &&
                    it.sourceSubmissionItemId !in corrected
        }
    }

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
