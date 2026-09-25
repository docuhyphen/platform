package com.docuhyphen.app.api.repository.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestSupportingEvidenceLink
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class InformationRequestSupportingEvidenceLinkRepository :
    BaseRepository<InformationRequestSupportingEvidenceLink>(InformationRequestSupportingEvidenceLink::class.java)
{
    fun findForRequest(requestId: UUID): List<InformationRequestSupportingEvidenceLink> =
        entityManager.createQuery(
            """
            SELECT link
            FROM InformationRequestSupportingEvidenceLink link
            WHERE link.informationRequestId = :requestId
            ORDER BY link.createdAt, link.id
            """.trimIndent(),
            InformationRequestSupportingEvidenceLink::class.java,
        )
            .setParameter("requestId", requestId)
            .resultList

    override fun update(entity: InformationRequestSupportingEvidenceLink): InformationRequestSupportingEvidenceLink =
        throw UnsupportedOperationException(REWRITE_REFUSAL)

    override fun delete(entity: InformationRequestSupportingEvidenceLink) = throw UnsupportedOperationException(REWRITE_REFUSAL)

    override fun deleteById(id: UUID) = throw UnsupportedOperationException(REWRITE_REFUSAL)

    private companion object
    {
        const val REWRITE_REFUSAL = "A materialized supporting evidence link is append-only"
    }
}
