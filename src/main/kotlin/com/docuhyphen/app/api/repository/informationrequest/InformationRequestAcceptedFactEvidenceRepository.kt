package com.docuhyphen.app.api.repository.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestAcceptedFactEvidence
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class InformationRequestAcceptedFactEvidenceRepository :
    BaseRepository<InformationRequestAcceptedFactEvidence>(InformationRequestAcceptedFactEvidence::class.java)
{
    fun findForFacts(factIds: Collection<UUID>): List<InformationRequestAcceptedFactEvidence>
    {
        if (factIds.isEmpty()) return emptyList()
        return entityManager.createQuery(
            "SELECT reference FROM InformationRequestAcceptedFactEvidence reference WHERE reference.factId IN :factIds ORDER BY reference.evidenceVersionId",
            InformationRequestAcceptedFactEvidence::class.java,
        )
            .setParameter("factIds", factIds)
            .resultList
    }
}
