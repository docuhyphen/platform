package com.docuhyphen.app.api.repository.informationrequest.acceptedfact

import com.docuhyphen.app.api.model.entity.InformationRequestFactRecertification
import com.docuhyphen.app.api.model.entity.InformationRequestFactRecertificationEvidence
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class InformationRequestFactRecertificationRepository :
    BaseRepository<InformationRequestFactRecertification>(InformationRequestFactRecertification::class.java)
{
    fun findForRequest(requestId: UUID): List<InformationRequestFactRecertification> =
        entityManager.createQuery(
            """
            SELECT recertification
            FROM InformationRequestFactRecertification recertification
            WHERE recertification.informationRequestId = :requestId
            ORDER BY recertification.assentedAt, recertification.id
            """.trimIndent(),
            InformationRequestFactRecertification::class.java,
        )
            .setParameter("requestId", requestId)
            .resultList
}

@ApplicationScoped
class InformationRequestFactRecertificationEvidenceRepository :
    BaseRepository<InformationRequestFactRecertificationEvidence>(InformationRequestFactRecertificationEvidence::class.java)
{
    fun findForRecertifications(recertificationIds: Collection<UUID>): List<InformationRequestFactRecertificationEvidence>
    {
        if (recertificationIds.isEmpty()) return emptyList()
        return entityManager.createQuery(
            """
            SELECT reference
            FROM InformationRequestFactRecertificationEvidence reference
            WHERE reference.recertificationId IN :ids
            ORDER BY reference.evidenceVersionId
            """.trimIndent(),
            InformationRequestFactRecertificationEvidence::class.java,
        )
            .setParameter("ids", recertificationIds)
            .resultList
    }
}
