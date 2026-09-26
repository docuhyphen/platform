package com.docuhyphen.app.api.repository.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestTemplateReviewStage
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateReviewStageSection
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class InformationRequestTemplateReviewStageRepository :
    BaseRepository<InformationRequestTemplateReviewStage>(InformationRequestTemplateReviewStage::class.java)
{
    fun findForVersion(templateVersionId: UUID): List<InformationRequestTemplateReviewStage> =
        entityManager.createQuery(
            """
            SELECT stage
            FROM InformationRequestTemplateReviewStage stage
            WHERE stage.templateVersionId = :versionId
            ORDER BY stage.position
            """.trimIndent(),
            InformationRequestTemplateReviewStage::class.java,
        )
            .setParameter("versionId", templateVersionId)
            .resultList

    fun deleteForVersion(templateVersionId: UUID): Int =
        entityManager.createQuery(
            "DELETE FROM InformationRequestTemplateReviewStage stage WHERE stage.templateVersionId = :versionId",
        )
            .setParameter("versionId", templateVersionId)
            .executeUpdate()
}

@ApplicationScoped
class InformationRequestTemplateReviewStageSectionRepository :
    BaseRepository<InformationRequestTemplateReviewStageSection>(InformationRequestTemplateReviewStageSection::class.java)
{
    fun findForVersion(templateVersionId: UUID): List<InformationRequestTemplateReviewStageSection> =
        entityManager.createQuery(
            """
            SELECT covered
            FROM InformationRequestTemplateReviewStageSection covered
            WHERE covered.templateVersionId = :versionId
            """.trimIndent(),
            InformationRequestTemplateReviewStageSection::class.java,
        )
            .setParameter("versionId", templateVersionId)
            .resultList

    fun deleteForVersion(templateVersionId: UUID): Int =
        entityManager.createQuery(
            "DELETE FROM InformationRequestTemplateReviewStageSection covered WHERE covered.templateVersionId = :versionId",
        )
            .setParameter("versionId", templateVersionId)
            .executeUpdate()
}
