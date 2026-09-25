package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateVersionDto
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAmendmentPlan
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestGroupOccurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class InformationRequestAmendmentGuard @Inject constructor(
    private val requirementRepository: InformationRequestRequirementRepository,
    private val occurrenceRepository: InformationRequestGroupOccurrenceRepository,
    private val lockService: InformationRequestSubmissionLockService,
)
{
    fun requireAmendable(request: InformationRequest, from: InformationRequestTemplateVersionDto, plan: InformationRequestAmendmentPlan)
    {
        if (plan.schemaChanged)
        {
            refuse(InformationRequestErrorCatalog.AMENDMENT_SCHEMA_CHANGED, "A later Version with another Schema Version needs a supplemental or superseding request")
        }
        requireOccurrenceStructureKept(request, from, plan)
        requireSubmittedScopesKept(request, plan)
    }

    private fun requireOccurrenceStructureKept(
        request: InformationRequest,
        from: InformationRequestTemplateVersionDto,
        plan: InformationRequestAmendmentPlan,
    )
    {
        val groupKeys = from.groups.associate { it.id to it.groupKey }
        val occurrences = occurrenceRepository.findForRequest(request.id)
        val largestSiblingCount = occurrences
            .groupBy { groupKeys[it.sourceTemplateGroupId] to it.parentOccurrenceId }
            .entries
            .groupBy({ it.key.first }, { it.value.size })
            .mapValues { (_, counts) -> counts.max() }
        val narrowed = plan.groupChanges.any { change ->
            val count = largestSiblingCount[change.groupKey] ?: return@any false
            change.removed || change.parentChanged || (change.maximumOccurrences?.let { count > it } ?: false)
        }
        val answered = requirementRepository.findForRequest(request.id).map { it.sourceTemplateRequirementId }.toSet()
        if (narrowed || plan.changes.any { it.anchorChanged && it.templateRequirementId in answered })
        {
            refuse(
                InformationRequestErrorCatalog.AMENDMENT_OCCURRENCE_STRUCTURE_CHANGED,
                "A later Version that moves or narrows existing occurrences needs a supplemental or superseding request",
            )
        }
    }

    private fun requireSubmittedScopesKept(request: InformationRequest, plan: InformationRequestAmendmentPlan)
    {
        val packages = lockService.activePackages(request.id)
        if (packages.isEmpty()) return
        val submittedStages = packages.map { it.stageKey }.toSet()
        val locked = lockService.lockedRequirementIds(request.id)
        val lockedTemplateRequirements = requirementRepository.findForRequest(request.id)
            .filter { it.id in locked }
            .map { it.sourceTemplateRequirementId }
            .toSet()
        val reachesSubmission = plan.submissionPolicyChanged || plan.changes.any { change ->
            null in submittedStages ||
                change.templateRequirementId in lockedTemplateRequirements ||
                (change.fromStageKey != null && change.fromStageKey in submittedStages) ||
                (change.toStageKey != null && change.toStageKey in submittedStages)
        }
        if (reachesSubmission)
        {
            refuse(
                InformationRequestErrorCatalog.AMENDMENT_SUBMITTED_SCOPE_CHANGED,
                "This change reaches a submission that was not withdrawn",
            )
        }
    }

    private fun refuse(reasonCode: String, message: String): Nothing =
        throw InformationRequestLifecycleException(reasonCode, message)
}
