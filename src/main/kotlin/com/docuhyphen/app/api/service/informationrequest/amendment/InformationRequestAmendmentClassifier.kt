package com.docuhyphen.app.api.service.informationrequest.amendment

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConditionRuleDto
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateGroupDto
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateVersionDto
import com.docuhyphen.app.api.model.entity.InformationRequestAmendmentChangeKind
import com.docuhyphen.app.api.model.informationrequest.amendment.*
import java.util.*

object InformationRequestAmendmentClassifier
{
    private val NO_ID = UUID(0, 0)

    fun classify(
        from: InformationRequestTemplateVersionDto,
        to: InformationRequestTemplateVersionDto
    ): InformationRequestAmendmentPlan
    {
        val earlier = placed(from)
        val later = placed(to)
        val changes = (earlier.keys + later.keys).sorted().mapNotNull { key ->
            val before = earlier[key]
            val after = later[key]
            when
            {
                before == null -> change(
                    key,
                    requireNotNull(after),
                    InformationRequestAmendmentChangeKind.ADDED,
                    null,
                    after
                )

                after == null -> change(key, before, InformationRequestAmendmentChangeKind.REMOVED, before, null)
                meaningOf(before, from) != meaningOf(after, to) ->
                    change(key, after, InformationRequestAmendmentChangeKind.MEANING_CHANGED, before, after)

                presentationOf(before) != presentationOf(after) ->
                    change(key, after, InformationRequestAmendmentChangeKind.PRESENTATION_CHANGED, before, after)

                else -> null
            }
        }
        return InformationRequestAmendmentPlan(
            changes = changes,
            groupChanges = groupChanges(from.groups, to.groups),
            schemaChanged = from.schemaVersionId != to.schemaVersionId,
            submissionPolicyChanged = from.submissionMode != to.submissionMode ||
                    from.submissionStageOrdering != to.submissionStageOrdering,
        )
    }

    private fun change(
        key: String,
        placed: InformationRequestAmendmentPlacedRequirement,
        kind: InformationRequestAmendmentChangeKind,
        before: InformationRequestAmendmentPlacedRequirement?,
        after: InformationRequestAmendmentPlacedRequirement?,
    ) = InformationRequestAmendmentRequirementChange(
        requirementKey = key,
        templateRequirementId = placed.requirement.templateRequirementId,
        kind = kind,
        fromBindingId = before?.requirement?.id,
        toBindingId = after?.requirement?.id,
        fromStageKey = before?.section?.submissionStageKey,
        toStageKey = after?.section?.submissionStageKey,
        anchorChanged = before != null && after != null &&
                before.requirement.occurrenceAnchorKey != after.requirement.occurrenceAnchorKey,
    )

    private fun groupChanges(
        from: List<InformationRequestTemplateGroupDto>,
        to: List<InformationRequestTemplateGroupDto>,
    ): List<InformationRequestAmendmentGroupChange>
    {
        val later = to.associateBy { it.groupKey }
        return from.mapNotNull { group ->
            val after = later[group.groupKey]
            when
            {
                after == null -> InformationRequestAmendmentGroupChange(
                    group.groupKey,
                    removed = true,
                    parentChanged = false,
                    null
                )

                after.parentGroupKey != group.parentGroupKey ||
                        after.maxOccurrences != group.maxOccurrences ||
                        after.minOccurrences != group.minOccurrences ->
                    InformationRequestAmendmentGroupChange(
                        group.groupKey,
                        removed = false,
                        parentChanged = after.parentGroupKey != group.parentGroupKey,
                        maximumOccurrences = after.maxOccurrences,
                    )

                else -> null
            }
        }
    }

    private fun placed(version: InformationRequestTemplateVersionDto): Map<String, InformationRequestAmendmentPlacedRequirement> =
        version.sections.flatMapIndexed { sectionIndex, section ->
            section.requirements.mapIndexed { index, requirement ->
                requirement.requirementKey to InformationRequestAmendmentPlacedRequirement(
                    requirement,
                    section,
                    sectionIndex,
                    index
                )
            }
        }.toMap()

    private fun meaningOf(
        placed: InformationRequestAmendmentPlacedRequirement,
        version: InformationRequestTemplateVersionDto
    ): InformationRequestAmendmentRequirementMeaning
    {
        val requirement = placed.requirement
        return InformationRequestAmendmentRequirementMeaning(
            requirement = requirement.copy(
                id = NO_ID,
                prompt = "",
                helpText = null,
                permittedDispositions = requirement.permittedDispositions.sortedBy { it.name },
                evidencePolicy = requirement.evidencePolicy?.let { policy ->
                    policy.copy(
                        id = NO_ID,
                        acceptedValues = policy.acceptedValues.sortedBy { "${it.attribute}:${it.acceptedValue}" })
                },
                substituteRequirementKeys = requirement.substituteRequirementKeys.sorted(),
                supportingEvidenceRequirementKeys = requirement.supportingEvidenceRequirementKeys.sorted(),
                attestationPolicy = requirement.attestationPolicy?.copy(id = NO_ID),
            ),
            stageKey = placed.section.submissionStageKey,
            condition = requirement.conditionalRuleKey?.let { key -> version.conditionRules.firstOrNull { it.ruleKey == key } }
                ?.let(::normalized),
            anchor = requirement.occurrenceAnchorKey?.let { key -> version.groups.firstOrNull { it.groupKey == key } }
                ?.copy(id = NO_ID),
        )
    }

    private fun normalized(rule: InformationRequestTemplateConditionRuleDto): InformationRequestTemplateConditionRuleDto =
        rule.copy(id = NO_ID, predicates = rule.predicates.map { it.copy(id = NO_ID) }.sortedBy { it.toString() })

    private fun presentationOf(placed: InformationRequestAmendmentPlacedRequirement): InformationRequestAmendmentRequirementPresentation =
        InformationRequestAmendmentRequirementPresentation(
            prompt = placed.requirement.prompt,
            helpText = placed.requirement.helpText,
            sectionKey = placed.section.sectionKey,
            sectionPosition = placed.sectionIndex,
            position = placed.index,
        )
}
