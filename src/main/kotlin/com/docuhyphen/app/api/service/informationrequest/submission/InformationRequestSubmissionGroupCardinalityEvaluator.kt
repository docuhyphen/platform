package com.docuhyphen.app.api.service.informationrequest.submission

import com.docuhyphen.app.api.model.entity.InformationRequestGroupOccurrence
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirementGroup
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.submission.InformationRequestSubmissionContent
import com.docuhyphen.app.api.model.informationrequest.submission.InformationRequestSubmissionGroupProblem
import com.docuhyphen.app.api.model.informationrequest.submission.InformationRequestSubmissionGroupProblems
import com.docuhyphen.app.api.repository.informationrequest.occurrence.InformationRequestGroupOccurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateRequirementGroupRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateSectionRepository
import com.docuhyphen.app.api.service.informationrequest.occurrence.InformationRequestGroupAuthorizationService
import com.docuhyphen.app.api.service.informationrequest.occurrence.InformationRequestGroupOccurrenceMaterializer
import com.docuhyphen.app.api.service.informationrequest.occurrence.InformationRequestGroupOccurrenceStructure
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class InformationRequestSubmissionGroupCardinalityEvaluator @Inject constructor(
    private val groupRepository: InformationRequestTemplateRequirementGroupRepository,
    private val bindingRepository: InformationRequestTemplateRequirementBindingRepository,
    private val sectionRepository: InformationRequestTemplateSectionRepository,
    private val occurrenceRepository: InformationRequestGroupOccurrenceRepository,
    private val occurrenceMaterializer: InformationRequestGroupOccurrenceMaterializer,
    private val structure: InformationRequestGroupOccurrenceStructure,
    private val groupAuthorization: InformationRequestGroupAuthorizationService,
) {
    fun assess(
        content: InformationRequestSubmissionContent,
        access: RequestAccessContext,
    ): InformationRequestSubmissionGroupProblems {
        val problems = problemsOf(content)
        if (problems.isEmpty()) return InformationRequestSubmissionGroupProblems()
        val template = occurrenceMaterializer.templateOf(content.request)
        val (disclosed, undisclosed) = problems.partition { problem ->
            val group = template.groupsByKey[problem.groupKey] ?: return@partition false
            groupAuthorization.permitsGroupCreation(
                access,
                content.request,
                structure.materializedBindings(template, group),
                structure.bindingsUnder(template, group),
            )
        }
        return InformationRequestSubmissionGroupProblems(disclosed, undisclosed.size)
    }

    private fun problemsOf(content: InformationRequestSubmissionContent): List<InformationRequestSubmissionGroupProblem> {
        val groups = groupRepository.findForVersion(content.version.id)
        if (groups.isEmpty()) return emptyList()
        val scoped = groupsInScope(content, groups)
        if (scoped.isEmpty()) return emptyList()
        val active = occurrenceRepository.findForRequest(content.request.id)
        val siblingsByParent = active.groupBy { it.sourceTemplateGroupId to it.parentOccurrenceId }
        return groups.filter { it.id in scoped }.flatMap { group ->
            parentsOf(group, active).mapNotNull { parent ->
                val count = siblingsByParent[group.id to parent?.id].orEmpty().size
                val withinMaximum = group.maxOccurrences?.let { count <= it } ?: true
                if (count >= group.minOccurrences && withinMaximum) null
                else InformationRequestSubmissionGroupProblem(
                    groupKey = group.groupKey,
                    parentOccurrencePath = parent?.occurrencePath,
                    minOccurrences = group.minOccurrences,
                    maxOccurrences = group.maxOccurrences,
                    occurrenceCount = count,
                )
            }
        }
    }

    private fun groupsInScope(
        content: InformationRequestSubmissionContent,
        groups: List<InformationRequestTemplateRequirementGroup>,
    ): Set<UUID> {
        val stageBySection =
            sectionRepository.findOrdered(content.version.id).associate { it.id to it.submissionStageKey }
        val groupsByKey = groups.associateBy { it.groupKey }
        val groupsById = groups.associateBy { it.id }
        val scoped = mutableSetOf<UUID>()
        bindingRepository.findOrdered(content.version.id)
            .filter { content.stageKey == null || stageBySection[it.templateSectionId] == content.stageKey }
            .mapNotNull { binding -> binding.occurrenceAnchorKey?.let(groupsByKey::get) }
            .forEach { anchor ->
                var group: InformationRequestTemplateRequirementGroup? = anchor
                while (group != null && scoped.add(group.id)) group = group.parentGroupId?.let(groupsById::get)
            }
        return scoped
    }

    private fun parentsOf(
        group: InformationRequestTemplateRequirementGroup,
        active: List<InformationRequestGroupOccurrence>,
    ): List<InformationRequestGroupOccurrence?> =
        group.parentGroupId?.let { parentGroupId -> active.filter { it.sourceTemplateGroupId == parentGroupId } }
            ?: listOf(null)
}
