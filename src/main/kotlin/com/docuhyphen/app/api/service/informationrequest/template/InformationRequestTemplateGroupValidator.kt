package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateGroupRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementRequest
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationChecks.machineKeyRefusal
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationChecks.normalizeMachineKey
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationChecks.optionalContent
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationChecks.refuse
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationChecks.refuseGroup
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationChecks.requireDistinct

internal object InformationRequestTemplateGroupValidator {
    fun normalizeGroups(
        groups: List<InformationRequestTemplateGroupRequest>,
    ): List<InformationRequestTemplateGroupRequest> {
        val normalized = groups.map(::normalizeGroup)
        requireDistinct(normalized.map { it.groupKey }) { key ->
            refuseGroup("Two repeatable groups state the same group key: $key", key)
        }

        val byKey = normalized.associateBy { it.groupKey }
        normalized.forEach { group ->
            val parentKey = group.parentGroupKey ?: return@forEach
            if (parentKey == group.groupKey) {
                refuseGroup(
                    "Repeatable group ${group.groupKey} names itself as its own parent",
                    group.groupKey,
                )
            }
            if (parentKey !in byKey) {
                refuseGroup(
                    "Repeatable group ${group.groupKey} names parent group $parentKey, which this " +
                            "template version does not define",
                    group.groupKey,
                )
            }
        }
        detectGroupNestingCycles(normalized, byKey)
        requireBoundedNesting(normalized, byKey)

        return normalized
    }

    fun validateOccurrenceAnchor(
        requirement: InformationRequestTemplateRequirementRequest,
        groupKeys: Set<String>,
    ) {
        val anchor = requirement.occurrenceAnchorKey ?: return
        if (anchor !in groupKeys) {
            refuse(
                "Requirement ${requirement.requirementKey} is answered once per $anchor, which this " +
                        "template version does not define as a repeatable group",
                requirementKey = requirement.requirementKey,
            )
        }
    }

    fun validateEveryGroupAnchorsARequirement(
        groups: List<InformationRequestTemplateGroupRequest>,
        requirements: List<InformationRequestTemplateRequirementRequest>,
    ) {
        val anchoredKeys = requirements.mapNotNull { it.occurrenceAnchorKey }.toSet()
        val childrenByParent = groups.groupBy { it.parentGroupKey }
        fun anchorsBeneath(group: InformationRequestTemplateGroupRequest): Boolean =
            group.groupKey in anchoredKeys || childrenByParent[group.groupKey].orEmpty().any(::anchorsBeneath)
        groups.firstOrNull { !anchorsBeneath(it) }?.let { empty ->
            refuseGroup(
                "Repeatable group ${empty.groupKey} holds no requirement at any depth, so nobody could ever be " +
                        "allowed to add an occurrence of it",
                empty.groupKey,
            )
        }
    }

    private fun normalizeGroup(
        group: InformationRequestTemplateGroupRequest,
    ): InformationRequestTemplateGroupRequest {
        val groupKey = normalizeMachineKey(group.groupKey) {
            refuseGroup(machineKeyRefusal("repeatable group key", group.groupKey), group.groupKey)
        }
        requireOccurrenceBounds(groupKey, group.minOccurrences, group.maxOccurrences)
        return group.copy(
            groupKey = groupKey,
            parentGroupKey = optionalContent(group.parentGroupKey)?.let { candidate ->
                normalizeMachineKey(candidate) {
                    refuseGroup(
                        "Repeatable group $groupKey names '$candidate' as its parent, which is not " +
                                "a usable group key",
                        groupKey,
                    )
                }
            },
        )
    }

    private fun detectGroupNestingCycles(
        groups: List<InformationRequestTemplateGroupRequest>,
        byKey: Map<String, InformationRequestTemplateGroupRequest>,
    ) {
        groups.forEach { group ->
            val visited = mutableSetOf(group.groupKey)
            var parentKey = group.parentGroupKey
            while (parentKey != null) {
                if (!visited.add(parentKey)) {
                    refuseGroup(
                        "Repeatable group ${group.groupKey} nests inside itself through $parentKey",
                        group.groupKey,
                    )
                }
                parentKey = byKey[parentKey]?.parentGroupKey
            }
        }
    }

    private fun requireOccurrenceBounds(groupKey: String, minimum: Int, maximum: Int?) {
        if (minimum < 0) {
            refuseGroup("Repeatable group $groupKey cannot require a negative number of occurrences", groupKey)
        }
        if (maximum != null && maximum < 1) {
            refuseGroup("Repeatable group $groupKey must allow at least one occurrence", groupKey)
        }
        if (maximum != null && maximum < minimum) {
            refuseGroup("Repeatable group $groupKey allows fewer occurrences than it requires", groupKey)
        }
        if (minimum > MAXIMUM_REQUIRED_OCCURRENCES) {
            refuseGroup(
                "Repeatable group $groupKey requires at most $MAXIMUM_REQUIRED_OCCURRENCES occurrences",
                groupKey
            )
        }
        if (maximum != null && maximum > MAXIMUM_ALLOWED_OCCURRENCES) {
            refuseGroup("Repeatable group $groupKey allows at most $MAXIMUM_ALLOWED_OCCURRENCES occurrences", groupKey)
        }
    }

    private fun requireBoundedNesting(
        groups: List<InformationRequestTemplateGroupRequest>,
        byKey: Map<String, InformationRequestTemplateGroupRequest>,
    ) {
        var initialOccurrences = 0L
        groups.forEach { group ->
            val chain = generateSequence(group) { level -> level.parentGroupKey?.let(byKey::get) }.toList()
            if (chain.size > MAXIMUM_DEPTH) {
                refuseGroup(
                    "Repeatable group ${group.groupKey} nests ${chain.size} levels deep; groups nest at most five deep",
                    group.groupKey,
                )
            }
            val longestPath = chain.sumOf { it.groupKey.length + OCCURRENCE_INDEX_CHARACTERS } + chain.size - 1
            if (longestPath > MAXIMUM_OCCURRENCE_PATH) {
                refuseGroup(
                    "Repeatable group ${group.groupKey} could need an occurrence path of $longestPath characters, " +
                            "more than the $MAXIMUM_OCCURRENCE_PATH an occurrence path holds; shorten its key or a parent's",
                    group.groupKey,
                )
            }
            initialOccurrences += chain.fold(1L) { product, level -> product * level.minOccurrences }
        }
        if (initialOccurrences > MAXIMUM_INITIAL_OCCURRENCES) {
            refuse(
                "Creating a request would add $initialOccurrences required repeatable group occurrences; " +
                        "a template version requires at most $MAXIMUM_INITIAL_OCCURRENCES",
            )
        }
    }

    private const val MAXIMUM_DEPTH = 5
    private const val MAXIMUM_OCCURRENCE_PATH = 512
    private const val OCCURRENCE_INDEX_CHARACTERS = 8
    private const val MAXIMUM_REQUIRED_OCCURRENCES = 100
    private const val MAXIMUM_ALLOWED_OCCURRENCES = 1000
    private const val MAXIMUM_INITIAL_OCCURRENCES = 500L
}
