package com.docuhyphen.app.api.repository.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirement
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

/**
 * Persistence for [InformationRequestTemplateRequirement]. A requirement key is unique within one
 * definition, so every lookup states which definition it is resolving against.
 */
@ApplicationScoped
class InformationRequestTemplateRequirementRepository :
    BaseRepository<InformationRequestTemplateRequirement>(InformationRequestTemplateRequirement::class.java)
{
    fun findByKey(definitionId: UUID, requirementKey: String): InformationRequestTemplateRequirement? =
        entityManager.createQuery(
            """
            SELECT requirement
            FROM InformationRequestTemplateRequirement requirement
            WHERE requirement.templateDefinitionId = :definitionId
              AND requirement.requirementKey = :requirementKey
            """.trimIndent(),
            InformationRequestTemplateRequirement::class.java,
        )
            .setParameter("definitionId", definitionId)
            .setParameter("requirementKey", requirementKey)
            .resultList
            .firstOrNull()

    fun findAllByType(
        definitionId: UUID,
        requirementType: InformationRequestRequirementType,
    ): List<InformationRequestTemplateRequirement> =
        entityManager.createQuery(
            """
            SELECT requirement
            FROM InformationRequestTemplateRequirement requirement
            WHERE requirement.templateDefinitionId = :definitionId
              AND requirement.requirementType = :requirementType
            ORDER BY requirement.requirementKey
            """.trimIndent(),
            InformationRequestTemplateRequirement::class.java,
        )
            .setParameter("definitionId", definitionId)
            .setParameter("requirementType", requirementType)
            .resultList

    fun findAllByDefinition(definitionId: UUID): List<InformationRequestTemplateRequirement> =
        entityManager.createQuery(
            """
            SELECT requirement
            FROM InformationRequestTemplateRequirement requirement
            WHERE requirement.templateDefinitionId = :definitionId
            ORDER BY requirement.requirementKey
            """.trimIndent(),
            InformationRequestTemplateRequirement::class.java,
        )
            .setParameter("definitionId", definitionId)
            .resultList
}
