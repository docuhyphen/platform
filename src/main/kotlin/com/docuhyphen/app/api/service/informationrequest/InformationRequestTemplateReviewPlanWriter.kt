package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateReviewStageRequest
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateReviewStage
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateReviewStageSection
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateReviewStageRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateReviewStageSectionRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import java.util.UUID

@ApplicationScoped
class InformationRequestTemplateReviewPlanWriter @Inject constructor(
    private val stageRepository: InformationRequestTemplateReviewStageRepository,
    private val sectionRepository: InformationRequestTemplateReviewStageSectionRepository,
)
{
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    fun clear(templateVersionId: UUID)
    {
        sectionRepository.deleteForVersion(templateVersionId)
        stageRepository.deleteForVersion(templateVersionId)
    }

    fun write(
        templateVersionId: UUID,
        stages: List<InformationRequestTemplateReviewStageRequest>,
        sectionIdByKey: Map<String, UUID>,
    )
    {
        if (stages.isEmpty()) return
        val written = stages.mapIndexed { index, stated ->
            stageRepository.save(
                InformationRequestTemplateReviewStage().apply {
                    this.templateVersionId = templateVersionId
                    stageKey = stated.stageKey
                    position = index + 1
                    title = stated.title
                    aggregation = stated.aggregation
                    quorumCount = stated.quorumCount
                    minimumReviewerCount = stated.minimumReviewerCount
                    tieResolution = stated.tieResolution
                    overridePermitted = stated.overridePermitted
                    excludesResponseParties = stated.excludesResponseParties
                    excludesPriorReviewers = stated.excludesPriorReviewers
                },
            ) to stated.sectionKeys
        }
        entityManager.flush()
        written.forEach { (stage, sectionKeys) ->
            sectionKeys.forEach { key ->
                sectionRepository.save(
                    InformationRequestTemplateReviewStageSection().apply {
                        this.templateVersionId = templateVersionId
                        reviewStageId = stage.id
                        templateSectionId = sectionIdByKey.getValue(key)
                    },
                )
            }
        }
    }
}
