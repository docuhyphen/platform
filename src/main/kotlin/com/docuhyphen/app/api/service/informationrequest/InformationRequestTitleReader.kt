package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class InformationRequestTitleReader @Inject constructor(
    private val versionRepository: InformationRequestTemplateVersionRepository,
    private val definitionRepository: InformationRequestTemplateDefinitionRepository,
)
{
    fun titleOf(request: InformationRequest): String = titlesOf(listOf(request)).getValue(request.id)

    fun titlesOf(requests: List<InformationRequest>): Map<UUID, String>
    {
        val versions = versionRepository.findForIds(requests.map { it.templateVersionId }.distinct())
            .associateBy { it.id }
        val definitions = definitionRepository.findForIds(versions.values.map { it.templateDefinitionId }.distinct())
            .associateBy { it.id }
        return requests.associate { request ->
            val definition = versions[request.templateVersionId]?.let { definitions[it.templateDefinitionId] }
                ?: throw IllegalStateException("Information Request ${request.id} has no Template Definition")
            request.id to definition.displayName
        }
    }
}
