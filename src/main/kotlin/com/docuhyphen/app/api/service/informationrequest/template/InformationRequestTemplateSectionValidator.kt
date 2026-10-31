package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateAcceptedValueRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateEvidencePolicyRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateSectionRequest
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceMediaTypes
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationChecks.machineKeyRefusal
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationChecks.normalizeMachineKey
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationChecks.optionalContent
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationChecks.refuse
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateConfigurationChecks.requireContent
import java.util.*

internal object InformationRequestTemplateSectionValidator {
    fun normalizeSection(
        section: InformationRequestTemplateSectionRequest,
    ): InformationRequestTemplateSectionRequest {
        val sectionKey = normalizeMachineKey(section.sectionKey) {
            refuse(machineKeyRefusal("section key", section.sectionKey), sectionKey = section.sectionKey)
        }

        if (section.requirements.isEmpty()) {
            refuse(
                "Section $sectionKey asks for nothing, so nothing would ever render it",
                sectionKey = sectionKey,
            )
        }

        return section.copy(
            sectionKey = sectionKey,
            title = requireContent(section.title) {
                refuse("Section $sectionKey has no title", sectionKey = sectionKey)
            },
            helpText = optionalContent(section.helpText),
            requirements = section.requirements.map { requirement ->
                normalizeRequirement(sectionKey, requirement).also { normalized ->
                    InformationRequestTemplateRoleValidator.validate(
                        sectionKey,
                        normalized.requirementKey,
                        normalized.requirementType,
                        normalized.contributorRole,
                        normalized.attestationPolicy?.requiredRoles.orEmpty(),
                    )
                }
            },
        )
    }

    fun validateEachFieldIsCollectedOnce(
        requirements: List<InformationRequestTemplateRequirementRequest>,
    ) {
        val seen = mutableMapOf<UUID, String>()
        requirements.forEach { requirement ->
            val collected = requirement.collectedFieldDefinitionId ?: return@forEach
            val first = seen.put(collected, requirement.requirementKey) ?: return@forEach
            refuse(
                "Requirements $first and ${requirement.requirementKey} both collect the same field, " +
                        "and one field holds one answer",
                requirementKey = requirement.requirementKey,
            )
        }
    }

    private fun normalizeRequirement(
        sectionKey: String,
        requirement: InformationRequestTemplateRequirementRequest,
    ): InformationRequestTemplateRequirementRequest {
        val requirementKey = normalizeMachineKey(requirement.requirementKey) {
            refuse(
                machineKeyRefusal("requirement key", requirement.requirementKey),
                requirementKey = requirement.requirementKey,
            )
        }

        if (requirement.requiredness == InformationRequestRequiredness.CONDITIONAL &&
            optionalContent(requirement.conditionalRuleKey) == null
        ) {
            refuse(
                "Requirement $requirementKey applies only sometimes but names no rule that decides it",
                requirementKey = requirementKey,
            )
        }

        val permittedDispositions = normalizeDispositions(requirementKey, requirement)
        validateAnswerability(requirementKey, requirement, permittedDispositions)
        validateCollectedField(requirementKey, requirement)
        validateConditionalOnlyRule(requirementKey, requirement)

        return requirement.copy(
            requirementKey = requirementKey,
            prompt = requireContent(requirement.prompt) {
                refuse("Requirement $requirementKey states no prompt", requirementKey = requirementKey)
            },
            helpText = optionalContent(requirement.helpText),
            confidentialityCompartmentKey = optionalContent(requirement.confidentialityCompartmentKey),
            conditionalRuleKey = referencedKey(requirementKey, "rule", requirement.conditionalRuleKey),
            occurrenceAnchorKey = referencedKey(requirementKey, "repeatable group", requirement.occurrenceAnchorKey),
            permittedDispositions = permittedDispositions,
            evidencePolicy = normalizeEvidencePolicy(sectionKey, requirementKey, requirement),
            substituteRequirementKeys = normalizeReferencedKeys(
                requirementKey, "substitute", requirement.substituteRequirementKeys,
            ),
            supportingEvidenceRequirementKeys = normalizeReferencedKeys(
                requirementKey, "supporting evidence", requirement.supportingEvidenceRequirementKeys,
            ),
        )
    }

    private fun referencedKey(requirementKey: String, label: String, candidate: String?): String? =
        optionalContent(candidate)?.let { key ->
            normalizeMachineKey(key) {
                refuse(
                    "Requirement $requirementKey names '$key' as its $label, which is not a usable key",
                    requirementKey = requirementKey,
                )
            }
        }

    private fun validateConditionalOnlyRule(
        requirementKey: String,
        requirement: InformationRequestTemplateRequirementRequest,
    ) {
        val ruleKey = optionalContent(requirement.conditionalRuleKey) ?: return
        if (requirement.requiredness != InformationRequestRequiredness.CONDITIONAL) {
            refuse(
                "Requirement $requirementKey names condition rule $ruleKey but is not conditional, and only a " +
                        "conditional requirement is asked according to a rule",
                requirementKey = requirementKey,
            )
        }
    }

    private fun validateAnswerability(
        requirementKey: String,
        requirement: InformationRequestTemplateRequirementRequest,
        permittedDispositions: List<InformationRequestResponseDisposition>,
    ) {
        val mode = requirement.responseMode
        if (mode !in ANSWERABLE_RESPONSE_MODES) {
            if (requirement.requiredness != InformationRequestRequiredness.OPTIONAL) {
                refuse(
                    "Requirement $requirementKey is $mode to the party it nominates, so an answer " +
                            "cannot be owed by that party",
                    requirementKey = requirementKey,
                )
            }

            if (permittedDispositions.isNotEmpty()) {
                refuse(
                    "Requirement $requirementKey is $mode to the party it nominates, so that party " +
                            "cannot be offered answers to choose from",
                    requirementKey = requirementKey,
                )
            }

            if (requirement.evidencePolicy?.waiverPolicy ==
                InformationRequestEvidenceWaiverPolicy.RESPONDENT_DECLARED
            ) {
                refuse(
                    "Requirement $requirementKey is $mode to the party it nominates, so that party " +
                            "cannot declare a waiver of it",
                    requirementKey = requirementKey,
                )
            }
        }

        if (requirement.reviewPolicy == InformationRequestReviewPolicy.REQUIRED_ON_EXCEPTION &&
            permittedDispositions.all { it == InformationRequestResponseDisposition.PROVIDED }
        ) {
            refuse(
                "Requirement $requirementKey is reviewed only when the answer is an exception, and " +
                        "it permits no answer that is one",
                requirementKey = requirementKey,
            )
        }
    }

    private fun normalizeDispositions(
        requirementKey: String,
        requirement: InformationRequestTemplateRequirementRequest,
    ): List<InformationRequestResponseDisposition> {
        val dispositions = requirement.permittedDispositions
        if (InformationRequestResponseDisposition.NOT_ANSWERED in dispositions) {
            refuse(
                "NOT_ANSWERED is the state a requirement starts in rather than an answer, so " +
                        "requirement $requirementKey cannot permit it",
                requirementKey = requirementKey,
            )
        }

        if (dispositions.isEmpty()) {
            return if (requirement.responseMode in ANSWERABLE_RESPONSE_MODES)
                listOf(InformationRequestResponseDisposition.PROVIDED)
            else
                emptyList()
        }

        return dispositions.distinct().sortedBy { it.ordinal }
    }

    private fun validateCollectedField(
        requirementKey: String,
        requirement: InformationRequestTemplateRequirementRequest,
    ) {
        val collected = requirement.collectedFieldDefinitionId
        if (requirement.requirementType == InformationRequestRequirementType.FIELD) {
            if (collected == null) {
                refuse(
                    "Requirement $requirementKey asks for typed data but names no field to collect " +
                            "it against",
                    requirementKey = requirementKey,
                )
            }
        } else if (collected != null) {
            refuse(
                "Requirement $requirementKey asks for ${requirement.requirementType} rather than " +
                        "typed data, so it collects no field",
                requirementKey = requirementKey,
            )
        }
    }

    private fun normalizeEvidencePolicy(
        sectionKey: String,
        requirementKey: String,
        requirement: InformationRequestTemplateRequirementRequest,
    ): InformationRequestTemplateEvidencePolicyRequest? {
        val policy = requirement.evidencePolicy ?: return null
        if (requirement.requirementType != InformationRequestRequirementType.DOCUMENT) {
            refuse(
                "An evidence policy belongs to a requested document, and requirement " +
                        "$requirementKey asks for ${requirement.requirementType}",
                sectionKey = sectionKey,
                requirementKey = requirementKey,
            )
        }
        InformationRequestTemplateEvidenceBounds.problemOf(policy)?.let { problem ->
            refuse("Requirement $requirementKey $problem", sectionKey = sectionKey, requirementKey = requirementKey)
        }

        return policy.copy(
            acceptedValues = normalizeAcceptedValues(sectionKey, requirementKey, policy),
        )
    }

    private fun normalizeAcceptedValues(
        sectionKey: String,
        requirementKey: String,
        policy: InformationRequestTemplateEvidencePolicyRequest,
    ): List<InformationRequestTemplateAcceptedValueRequest> {
        return policy.acceptedValues
            .map { accepted ->
                val value = requireContent(accepted.acceptedValue) {
                    refuse(
                        "Requirement $requirementKey restricts ${accepted.attribute} to an " +
                                "empty value",
                        sectionKey = sectionKey,
                        requirementKey = requirementKey,
                    )
                }
                accepted.copy(
                    acceptedValue = if (accepted.attribute == InformationRequestEvidenceAttribute.CONTENT_TYPE)
                        mediaTypeOf(sectionKey, requirementKey, value)
                    else
                        value,
                )
            }
            .distinct()
            .onEach { accepted ->
                val capture = capturedRequirementFor(policy, accepted.attribute) ?: return@onEach
                if (capture == InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED) {
                    refuse(
                        "Requirement $requirementKey never captures ${accepted.attribute}, so it " +
                                "cannot restrict which values of it are accepted",
                        sectionKey = sectionKey,
                        requirementKey = requirementKey,
                    )
                }
            }
    }

    private fun mediaTypeOf(sectionKey: String, requirementKey: String, value: String): String =
        InformationRequestEvidenceMediaTypes.mediaTypeOrNull(value) ?: refuse(
            "Requirement $requirementKey accepts '$value' as a file type, which is not a media type such as application/pdf",
            sectionKey = sectionKey,
            requirementKey = requirementKey,
        )

    private fun capturedRequirementFor(
        policy: InformationRequestTemplateEvidencePolicyRequest,
        attribute: InformationRequestEvidenceAttribute,
    ): InformationRequestEvidenceAttributeRequirement? = when (attribute) {
        InformationRequestEvidenceAttribute.CONTENT_TYPE -> null
        InformationRequestEvidenceAttribute.ISSUER -> policy.issuerRequirement
        InformationRequestEvidenceAttribute.JURISDICTION -> policy.jurisdictionRequirement
        InformationRequestEvidenceAttribute.LANGUAGE -> policy.languageRequirement
    }

    private fun normalizeReferencedKeys(
        requirementKey: String,
        relation: String,
        keys: List<String>,
    ): List<String> = keys
        .map { candidate ->
            normalizeMachineKey(candidate) {
                refuse(
                    "Requirement $requirementKey names '$candidate' as $relation, which is not a " +
                            "usable requirement key",
                    requirementKey = requirementKey,
                )
            }
        }
        .distinct()
        .sorted()

    private val ANSWERABLE_RESPONSE_MODES = setOf(
        InformationRequestResponseMode.PROVIDE,
        InformationRequestResponseMode.PROVIDE_ONCE,
    )
}
