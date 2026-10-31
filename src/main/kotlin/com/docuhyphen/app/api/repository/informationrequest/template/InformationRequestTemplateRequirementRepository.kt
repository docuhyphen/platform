package com.docuhyphen.app.api.repository.informationrequest.template

import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirement
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.*

@ApplicationScoped
class InformationRequestTemplateRequirementRepository :
    BaseRepository<InformationRequestTemplateRequirement>(InformationRequestTemplateRequirement::class.java)
{
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
