package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateReviewStageRequest
import com.docuhyphen.app.api.model.entity.*

internal object InformationRequestTemplateReviewPlanValidator
{
    private val PURPOSE_PATTERN = Regex("^[a-z0-9][a-z0-9._-]{0,127}$")

    fun normalize(document: InformationRequestTemplateConfigurationRequest): InformationRequestTemplateConfigurationRequest
    {
        val sectionKeys = document.sections.map { it.sectionKey }.toSet()
        val routed = document.sections.flatMap { section ->
            section.requirements.filter(::routesReview).map { section.sectionKey to it }
        }
        val stages = document.reviewStages.map { normalizeStage(it, sectionKeys) }
        val seen = mutableSetOf<String>()
        stages.forEach { stage ->
            if (!seen.add(stage.stageKey))
            {
                refuse("Two review stages state the same stage key: ${stage.stageKey}", stage.stageKey)
            }
        }
        if (routed.isEmpty() && stages.isNotEmpty())
        {
            refuse(
                "This template version routes no work to a reviewer, so it states no review stage",
                stages.first().stageKey,
            )
        }
        if (stages.isNotEmpty())
        {
            routed.forEach { (sectionKey, requirement) ->
                if (stages.none { it.sectionKeys.isEmpty() || sectionKey in it.sectionKeys })
                {
                    throw InformationRequestTemplateValidationException(
                        "Requirement ${requirement.requirementKey} routes work to a reviewer, but no review stage covers " +
                                "section $sectionKey",
                        sectionKey = sectionKey,
                        requirementKey = requirement.requirementKey,
                    )
                }
            }
        }
        return document.copy(
            reviewStages = stages,
            factReusePurposeKey = normalizePurpose(document.factReusePurposeKey),
        )
    }

    fun routesReview(requirement: InformationRequestTemplateRequirementRequest): Boolean =
        requirement.reviewPolicy != InformationRequestReviewPolicy.NOT_REQUIRED ||
                requirement.evidencePolicy?.conformancePolicy == InformationRequestEvidenceConformancePolicy.DEFICIENCY_REVIEWABLE ||
                requirement.evidencePolicy?.waiverPolicy == InformationRequestEvidenceWaiverPolicy.REVIEW_APPROVAL_REQUIRED

    private fun normalizeStage(
        stage: InformationRequestTemplateReviewStageRequest,
        sectionKeys: Set<String>,
    ): InformationRequestTemplateReviewStageRequest
    {
        val key = InformationRequestTemplateKey.normalizeOrNull(stage.stageKey)
            ?: throw InformationRequestTemplateValidationException(
                InformationRequestTemplateKey.refusalFor("review stage key", stage.stageKey),
            )
        val title = stage.title.trim().ifBlank { refuse("Review stage $key needs a title", key) }
        if (stage.minimumReviewerCount < 1)
        {
            refuse("Review stage $key needs at least one reviewer", key)
        }
        val quorum = stage.quorumCount
        when
        {
            stage.aggregation == InformationRequestReviewAggregation.QUORUM && quorum == null ->
                refuse("Review stage $key decides by quorum, so it states how many reviewers make one", key)

            stage.aggregation != InformationRequestReviewAggregation.QUORUM && quorum != null ->
                refuse("Review stage $key does not decide by quorum, so it states no quorum count", key)

            quorum != null && (quorum < 1 || quorum > stage.minimumReviewerCount) ->
                refuse("Review stage $key needs a quorum between one and its minimum reviewer count", key)
        }
        if (stage.tieResolution == InformationRequestReviewTieResolution.REQUIRE_OVERRIDE && !stage.overridePermitted)
        {
            refuse("Review stage $key resolves a tie by override, so it permits an authorized override", key)
        }
        val covered = stage.sectionKeys.map { candidate ->
            val sectionKey = InformationRequestTemplateKey.normalizeOrNull(candidate)
            if (sectionKey == null || sectionKey !in sectionKeys)
            {
                refuse("Review stage $key covers section $candidate, which this template version does not state", key)
            }
            sectionKey
        }.distinct()
        return stage.copy(stageKey = key, title = title, sectionKeys = covered)
    }

    private fun normalizePurpose(candidate: String?): String?
    {
        val purpose = candidate?.trim()?.lowercase()?.ifBlank { null } ?: return null
        if (!PURPOSE_PATTERN.matches(purpose))
        {
            throw InformationRequestTemplateValidationException(
                "A fact reuse purpose must be lowercase letters, digits, dots, hyphens, and underscores, " +
                        "so '$candidate' cannot be one",
            )
        }
        return purpose
    }

    private fun refuse(message: String, stageKey: String): Nothing =
        throw InformationRequestTemplateValidationException(message, reviewStageKey = stageKey)
}
