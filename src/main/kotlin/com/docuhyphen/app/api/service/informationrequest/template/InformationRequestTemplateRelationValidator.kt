package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementRequest
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationChecks.refuse

internal object InformationRequestTemplateRelationValidator {
    fun validateRelations(
        requirement: InformationRequestTemplateRequirementRequest,
        typeByKey: Map<String, InformationRequestRequirementType>,
    ) {
        val key = requirement.requirementKey
        val asksForADocument = requirement.requirementType == InformationRequestRequirementType.DOCUMENT

        if (requirement.substituteRequirementKeys.isNotEmpty() && !asksForADocument) {
            refuse(
                "Only a requested document declares substitute evidence, and requirement $key asks " +
                        "for ${requirement.requirementType}",
                requirementKey = key,
            )
        }

        if (requirement.supportingEvidenceRequirementKeys.isNotEmpty() && asksForADocument) {
            refuse(
                "A requested document does not itself declare supporting evidence, and requirement " +
                        "$key does",
                requirementKey = key,
            )
        }

        val related = requirement.substituteRequirementKeys + requirement.supportingEvidenceRequirementKeys
        related.forEach { referenced ->
            if (referenced == key) {
                refuse(
                    "Requirement $key references itself, and nothing stands in for or supports itself",
                    requirementKey = key,
                )
            }

            val referencedType = typeByKey[referenced]
                ?: refuse(
                    "Requirement $key references $referenced, which this template version does not " +
                            "ask for",
                    requirementKey = key,
                )
            if (referencedType != InformationRequestRequirementType.DOCUMENT) {
                refuse(
                    "Requirement $key references $referenced, which asks for $referencedType rather " +
                            "than a document",
                    requirementKey = key,
                )
            }
        }
    }

    fun validateSubstituteScopes(requirements: List<InformationRequestTemplateRequirementRequest>) {
        val anchorByKey = requirements.associate { it.requirementKey to it.occurrenceAnchorKey }
        requirements.forEach { requirement ->
            requirement.substituteRequirementKeys.forEach { substitute ->
                val substituteAnchor = anchorByKey[substitute]
                if (substituteAnchor != null && substituteAnchor != requirement.occurrenceAnchorKey) {
                    refuse(
                        "Requirement ${requirement.requirementKey} names $substitute as its substitute, but $substitute " +
                                "is answered once per $substituteAnchor; a substitute is found only at the top level " +
                                "or in the document's own group",
                        requirementKey = requirement.requirementKey,
                    )
                }
            }
        }
    }

    fun validateSubstitutionIsFlat(requirements: List<InformationRequestTemplateRequirementRequest>) {
        val namedAsSubstitute = requirements.flatMapTo(mutableSetOf()) { it.substituteRequirementKeys }
        requirements.forEach { requirement ->
            if (requirement.substituteRequirementKeys.isNotEmpty() &&
                requirement.requirementKey in namedAsSubstitute
            ) {
                refuse(
                    "Requirement ${requirement.requirementKey} both stands in for another document " +
                            "and declares its own substitutes, which would chain substitution",
                    requirementKey = requirement.requirementKey,
                )
            }
        }
    }
}
