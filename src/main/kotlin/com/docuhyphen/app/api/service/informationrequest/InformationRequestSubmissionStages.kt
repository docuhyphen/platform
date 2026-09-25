package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionMode
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateSection
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateVersion
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateSectionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateVersionRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

@ApplicationScoped
class InformationRequestSubmissionStages @Inject constructor(
    private val templateVersionRepository: InformationRequestTemplateVersionRepository,
    private val sectionRepository: InformationRequestTemplateSectionRepository,
    private val bindingRepository: InformationRequestTemplateRequirementBindingRepository,
)
{
    fun versionOf(request: InformationRequest): InformationRequestTemplateVersion =
        templateVersionRepository.findById(request.templateVersionId)
            ?: throw IllegalStateException("Information Request Template Version not found")

    fun stageOrder(request: InformationRequest): List<String>
    {
        val version = versionOf(request)
        return stageOrderOf(version, sectionRepository.findOrdered(version.id))
    }

    fun stageOrderOf(version: InformationRequestTemplateVersion, sections: List<InformationRequestTemplateSection>): List<String> =
        if (version.submissionMode == InformationRequestSubmissionMode.STAGED)
            sections.sortedBy { it.displayOrder }.mapNotNull { it.submissionStageKey }.distinct()
        else
            emptyList()

    fun stageByBinding(version: InformationRequestTemplateVersion): Map<UUID, String>
    {
        if (version.submissionMode != InformationRequestSubmissionMode.STAGED) return emptyMap()
        val stageBySection = sectionRepository.findOrdered(version.id).associate { it.id to it.submissionStageKey }
        return bindingRepository.findOrdered(version.id).mapNotNull { binding ->
            stageBySection[binding.templateSectionId]?.let { binding.id to it }
        }.toMap()
    }

    fun scopesOf(request: InformationRequest, bindingIds: Collection<UUID>): Set<String?>
    {
        val version = versionOf(request)
        if (version.submissionMode != InformationRequestSubmissionMode.STAGED) return setOf(null)
        val stages = stageByBinding(version)
        return bindingIds.mapNotNull { stages[it] }.toSet()
    }
}
