package com.docuhyphen.app.api.repository.informationrequest.acceptedfact

import com.docuhyphen.app.api.model.entity.InformationRequestAcceptedFact
import com.docuhyphen.app.api.model.entity.InformationRequestAcceptedFactRevocation
import com.docuhyphen.app.api.model.entity.InformationRequestBusinessDecision
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.*

@ApplicationScoped
class InformationRequestAcceptedFactRepository :
    BaseRepository<InformationRequestAcceptedFact>(InformationRequestAcceptedFact::class.java)
{
    fun findFromRequest(requestId: UUID): List<InformationRequestAcceptedFact> =
        entityManager.createQuery(
            """
            SELECT fact
            FROM InformationRequestAcceptedFact fact
            WHERE fact.sourceInformationRequestId = :requestId
            ORDER BY fact.promotedAt, fact.id
            """.trimIndent(),
            InformationRequestAcceptedFact::class.java,
        )
            .setParameter("requestId", requestId)
            .resultList

    @Suppress("LongParameterList")
    fun findForKey(
        ownerType: InformationRequestOwnerType,
        ownerId: UUID,
        subjectIdentityRefId: UUID,
        fieldDefinitionIds: Collection<UUID>,
        purposeKey: String,
    ): List<InformationRequestAcceptedFact>
    {
        if (fieldDefinitionIds.isEmpty()) return emptyList()
        val ownerColumn =
            if (ownerType == InformationRequestOwnerType.ORGANIZATION) "ownerOrganizationId" else "ownerUserId"
        return entityManager.createQuery(
            """
            SELECT fact
            FROM InformationRequestAcceptedFact fact
            WHERE fact.ownerType = :ownerType
              AND fact.$ownerColumn = :ownerId
              AND fact.subjectIdentityRefId = :subjectId
              AND fact.fieldDefinitionId IN :fieldDefinitionIds
              AND fact.purposeKey = :purposeKey
            ORDER BY fact.promotedAt, fact.id
            """.trimIndent(),
            InformationRequestAcceptedFact::class.java,
        )
            .setParameter("ownerType", ownerType)
            .setParameter("ownerId", ownerId)
            .setParameter("subjectId", subjectIdentityRefId)
            .setParameter("fieldDefinitionIds", fieldDefinitionIds)
            .setParameter("purposeKey", purposeKey)
            .resultList
    }

    fun findSupersedingIds(factIds: Collection<UUID>): Map<UUID, UUID>
    {
        if (factIds.isEmpty()) return emptyMap()
        return entityManager.createNativeQuery(
            "SELECT predecessor_fact_id, successor_fact_id FROM information_request_fact_supersession WHERE predecessor_fact_id IN :factIds",
        ).setParameter("factIds", factIds).resultList.associate { result ->
            val row = result as Array<*>
            row[0] as UUID to row[1] as UUID
        }
    }

    fun reusableSourceRequestIds(requestIds: Collection<UUID>): Set<UUID> {
        if (requestIds.isEmpty()) return emptySet()
        return entityManager.createQuery(
            """
            SELECT request.id FROM InformationRequest request, Exchange exchange
            WHERE request.id IN :requestIds AND exchange.id = request.exchangeId AND exchange.isDeleted = false
            """.trimIndent(),
            UUID::class.java,
        ).setParameter("requestIds", requestIds).resultList.toSet()
    }

    fun correctedSourceItemIds(itemIds: Collection<UUID>): Set<UUID> {
        if (itemIds.isEmpty()) return emptySet()
        return entityManager.createQuery(
            "SELECT correction.submissionItemId FROM InformationRequestItemCorrection correction WHERE correction.submissionItemId IN :itemIds",
            UUID::class.java,
        ).setParameter("itemIds", itemIds).resultList.toSet()
    }


}

@ApplicationScoped
class InformationRequestAcceptedFactRevocationRepository :
    BaseRepository<InformationRequestAcceptedFactRevocation>(InformationRequestAcceptedFactRevocation::class.java)
{
    fun findForFacts(factIds: Collection<UUID>): List<InformationRequestAcceptedFactRevocation>
    {
        if (factIds.isEmpty()) return emptyList()
        return entityManager.createQuery(
            "SELECT revocation FROM InformationRequestAcceptedFactRevocation revocation WHERE revocation.factId IN :factIds",
            InformationRequestAcceptedFactRevocation::class.java,
        )
            .setParameter("factIds", factIds)
            .resultList
    }
}

@ApplicationScoped
class InformationRequestBusinessDecisionRepository :
    BaseRepository<InformationRequestBusinessDecision>(InformationRequestBusinessDecision::class.java)
{
    fun findForRequest(requestId: UUID): List<InformationRequestBusinessDecision> =
        entityManager.createQuery(
            """
            SELECT decision
            FROM InformationRequestBusinessDecision decision
            WHERE decision.informationRequestId = :requestId
            ORDER BY decision.owningProcessKey, decision.decisionRevision
            """.trimIndent(),
            InformationRequestBusinessDecision::class.java,
        )
            .setParameter("requestId", requestId)
            .resultList
}
