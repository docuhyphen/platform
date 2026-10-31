package com.docuhyphen.app.api.service.informationrequest.response

import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementRevision
import com.docuhyphen.app.api.model.entity.InformationRequestResponse
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.informationrequest.InformationRequestItemProblem
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestResponsePatch
import com.docuhyphen.app.api.model.informationrequest.response.ResponseNarrativePatch
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateBindingDispositionRepository
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestItemRefusalException
import com.docuhyphen.app.api.service.informationrequest.occurrence.InformationRequestOccurrencePath
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class InformationRequestResponsePatchValidator @Inject constructor(
    private val dispositionRepository: InformationRequestTemplateBindingDispositionRepository,
    private val responseStore: InformationRequestResponseStore,
) {
    fun requirePatchTargetsAvailable(
        patches: List<InformationRequestResponsePatch>,
        allRequirements: Map<UUID, InformationRequestRequirement>,
        activeOccurrencePaths: Set<String>,
    ) {
        val unknown = patches.map { it.requirementId }.distinct().filter { it !in allRequirements }
        if (unknown.isNotEmpty()) {
            throw InformationRequestItemRefusalException(
                InformationRequestErrorCatalog.NOT_FOUND,
                "A Requirement this change addresses was not found",
                unknown.map { requirementId ->
                    InformationRequestItemProblem(
                        code = InformationRequestErrorCatalog.NOT_FOUND,
                        message = "This Requirement was not found",
                        requirementId = requirementId,
                    )
                },
            )
        }
        val removed = patches.map { allRequirements.getValue(it.requirementId) }.distinctBy { it.id }
            .filterNot { InformationRequestOccurrencePath.isActiveOccurrence(it.occurrencePath, activeOccurrencePaths) }
        if (removed.isNotEmpty()) {
            throw InformationRequestItemRefusalException(
                InformationRequestErrorCatalog.GROUP_OCCURRENCE_REMOVED,
                "A group occurrence this change addresses has been removed",
                removed.map {
                    requirementProblem(
                        InformationRequestErrorCatalog.GROUP_OCCURRENCE_REMOVED,
                        "This group occurrence has been removed",
                        it
                    )
                },
            )
        }
    }

    fun requirePermittedDispositions(
        patches: List<InformationRequestResponsePatch>,
        requirements: Map<UUID, InformationRequestRequirement>,
    ) {
        val problems = patches.mapNotNull { patch ->
            val disposition = patch.disposition ?: return@mapNotNull null
            val requirement = requirements.getValue(patch.requirementId)
            when {
                disposition == InformationRequestResponseDisposition.NOT_ANSWERED -> requirementProblem(
                    InformationRequestErrorCatalog.RESPONSE_DISPOSITION_NOT_PERMITTED,
                    "Not answered is the absence of a response, not a disposition to choose",
                    requirement,
                )

                disposition !in permittedDispositions(requirement) -> requirementProblem(
                    InformationRequestErrorCatalog.RESPONSE_DISPOSITION_NOT_PERMITTED,
                    "This Requirement does not permit the chosen disposition",
                    requirement,
                )

                else -> null
            }
        }
        if (problems.isNotEmpty()) {
            throw InformationRequestItemRefusalException(
                InformationRequestErrorCatalog.RESPONSE_DISPOSITION_NOT_PERMITTED,
                "A chosen response disposition is not permitted",
                problems,
            )
        }
    }

    fun requireCurrentRevisions(
        patches: List<InformationRequestResponsePatch>,
        requirements: Map<UUID, InformationRequestRequirement>,
        currentRevisions: Map<UUID, InformationRequestRequirementRevision>,
    ) {
        val problems = patches.map { requirements.getValue(it.requirementId) }.distinctBy { it.id }
            .filter { it.id !in currentRevisions }
            .map {
                requirementProblem(
                    InformationRequestErrorCatalog.STATE_INVALID,
                    "This Requirement has no current revision",
                    it
                )
            }
        if (problems.isNotEmpty()) {
            throw InformationRequestItemRefusalException(
                InformationRequestErrorCatalog.STATE_INVALID,
                "A Requirement this change addresses has no current revision",
                problems,
            )
        }
    }

    fun requireNarratives(
        requestId: UUID,
        patches: List<InformationRequestResponsePatch>,
        requirements: Map<UUID, InformationRequestRequirement>,
    ) {
        val problems = patches.mapNotNull { patch ->
            val current = responseStore.findCurrentForUpdate(requestId, patch.requirementId)
            val disposition =
                patch.disposition ?: current?.disposition ?: InformationRequestResponseDisposition.NOT_ANSWERED
            if (disposition !in NARRATIVE_REQUIRED_DISPOSITIONS || !nextNarrativeOf(patch, current).isNullOrBlank())
                return@mapNotNull null
            requirementProblem(
                InformationRequestErrorCatalog.RESPONSE_NARRATIVE_REQUIRED,
                "This response disposition requires a narrative",
                requirements.getValue(patch.requirementId),
            )
        }
        if (problems.isNotEmpty()) {
            throw InformationRequestItemRefusalException(
                InformationRequestErrorCatalog.RESPONSE_NARRATIVE_REQUIRED,
                "A response disposition requires a narrative",
                problems,
            )
        }
    }

    private fun permittedDispositions(requirement: InformationRequestRequirement): Set<InformationRequestResponseDisposition> =
        dispositionRepository.findForBinding(requirement.sourceTemplateBindingId).map { it.disposition }.toSet()

    private companion object {
        val NARRATIVE_REQUIRED_DISPOSITIONS = setOf(
            InformationRequestResponseDisposition.PARTIALLY_PROVIDED,
            InformationRequestResponseDisposition.NOT_APPLICABLE,
            InformationRequestResponseDisposition.UNAVAILABLE,
            InformationRequestResponseDisposition.EXCEPTION_REQUESTED,
            InformationRequestResponseDisposition.SATISFIED_BY_REFERENCE,
            InformationRequestResponseDisposition.WAIVED,
        )
    }
}

internal fun nextNarrativeOf(patch: InformationRequestResponsePatch, current: InformationRequestResponse?): String? =
    when (val narrative = patch.narrative) {
        ResponseNarrativePatch.Unchanged -> current?.narrative
        ResponseNarrativePatch.Clear -> null
        is ResponseNarrativePatch.Set -> narrative.value
    }

internal fun requirementProblem(
    code: String,
    message: String,
    requirement: InformationRequestRequirement,
    fieldContractId: UUID? = null,
) = InformationRequestItemProblem(
    code = code,
    message = message,
    requirementId = requirement.id,
    occurrencePath = requirement.occurrencePath,
    fieldContractId = fieldContractId,
)
