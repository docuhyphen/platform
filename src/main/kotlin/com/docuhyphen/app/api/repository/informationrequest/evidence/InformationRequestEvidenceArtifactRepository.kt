package com.docuhyphen.app.api.repository.informationrequest.evidence

import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceArtifact
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.*

@ApplicationScoped
class InformationRequestEvidenceArtifactRepository :
    BaseRepository<InformationRequestEvidenceArtifact>(InformationRequestEvidenceArtifact::class.java)
{
    fun findForRequest(requestId: UUID): List<InformationRequestEvidenceArtifact> =
        entityManager.createQuery(
            """
            SELECT artifact
            FROM InformationRequestEvidenceArtifact artifact
            WHERE artifact.informationRequestId = :requestId
            ORDER BY artifact.createdAt, artifact.artifactKey
            """.trimIndent(),
            InformationRequestEvidenceArtifact::class.java,
        )
            .setParameter("requestId", requestId)
            .resultList

    fun findForRequirement(requirementId: UUID): List<InformationRequestEvidenceArtifact> =
        entityManager.createQuery(
            """
            SELECT artifact
            FROM InformationRequestEvidenceArtifact artifact
            WHERE artifact.informationRequestRequirementId = :requirementId
            ORDER BY artifact.createdAt, artifact.artifactKey
            """.trimIndent(),
            InformationRequestEvidenceArtifact::class.java,
        )
            .setParameter("requirementId", requirementId)
            .resultList
}
