package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateAcceptedValueRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConditionPredicateRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConditionRuleRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateEvidencePolicyRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateGroupRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateSectionRequest
import com.docuhyphen.app.api.model.entity.InformationRequestConditionHiddenDataPolicy
import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceAttribute
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceWaiverPolicy
import com.docuhyphen.app.api.model.entity.InformationRequestRequiredness
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.entity.InformationRequestResponseMode
import com.docuhyphen.app.api.model.entity.InformationRequestReviewPolicy
import com.docuhyphen.app.api.service.fields.FieldOperator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

/**
 * A draft is authored as one document, so the document has to hold together on its own terms before
 * any of it reaches storage.
 *
 * The database refuses an incoherent write, but it refuses it as a constraint name in the middle of
 * a multi-table transaction, which tells an author nothing about which requirement they got wrong.
 * These are the refusals that name the offending key, plus the normalization that makes a document
 * mean exactly one thing: positions come from the order things are listed in rather than from
 * authored numbers, a repeated answer is one answer, and surrounding whitespace never becomes part
 * of a machine identifier.
 *
 * Bounds that contradict each other are not checked here. Those are single-column and pair-column
 * facts the stored policy already refuses, and duplicating them would create a second, drifting
 * statement of the same rule.
 */
class InformationRequestTemplateConfigurationValidatorTest
{
    private val validator = InformationRequestTemplateConfigurationValidator()

    @Test
    fun `normalization preserves the order sections and requirements are listed in`()
    {
        val normalized = validator.normalize(
            configuration(
                section(
                    "collected-data",
                    requirement("recorded-note", InformationRequestRequirementType.FIELD),
                    requirement("recorded-assertion", InformationRequestRequirementType.RESPONSE_ATTESTATION),
                ),
                section("supporting-data", documentRequirement("supporting-record")),
            ),
        )

        assertEquals(
            listOf("collected-data", "supporting-data"),
            normalized.sections.map { it.sectionKey },
        )
        assertEquals(
            listOf("recorded-note", "recorded-assertion"),
            normalized.sections.first().requirements.map { it.requirementKey },
        )
    }

    @Test
    fun `a machine identifier is trimmed and lowercased so one key never becomes two`()
    {
        val normalized = validator.normalize(
            configuration(
                InformationRequestTemplateSectionRequest(
                    sectionKey = "  Collected-Data  ",
                    title = "  Collected data  ",
                    helpText = "   ",
                    requirements = listOf(
                        InformationRequestTemplateRequirementRequest(
                            requirementKey = " Recorded-Note ",
                            requirementType = InformationRequestRequirementType.FIELD,
                            prompt = "  State the recorded note  ",
                            helpText = "  ",
                            collectedFieldDefinitionId = fieldOf("recorded-note"),
                        ),
                    ),
                ),
            ),
        )

        val section = normalized.sections.single()
        assertEquals("collected-data", section.sectionKey)
        assertEquals("Collected data", section.title)
        assertNull(section.helpText)

        val requirement = section.requirements.single()
        assertEquals("recorded-note", requirement.requirementKey)
        assertEquals("State the recorded note", requirement.prompt)
        assertNull(requirement.helpText)
    }

    @Test
    fun `a repeated permitted answer is one answer and is ordered deterministically`()
    {
        val normalized = validator.normalize(
            configuration(
                section(
                    "collected-data",
                    requirement("recorded-note", InformationRequestRequirementType.FIELD).copy(
                        permittedDispositions = listOf(
                            InformationRequestResponseDisposition.NOT_APPLICABLE,
                            InformationRequestResponseDisposition.PROVIDED,
                            InformationRequestResponseDisposition.NOT_APPLICABLE,
                        ),
                    ),
                ),
            ),
        )

        assertEquals(
            listOf(
                InformationRequestResponseDisposition.PROVIDED,
                InformationRequestResponseDisposition.NOT_APPLICABLE,
            ),
            normalized.sections.single().requirements.single().permittedDispositions,
        )
    }

    @Test
    fun `an unanswered requirement is a starting state rather than a permitted answer`()
    {
        val refusal = refusalFor(
            configuration(
                section(
                    "collected-data",
                    requirement("recorded-note", InformationRequestRequirementType.FIELD).copy(
                        permittedDispositions = listOf(InformationRequestResponseDisposition.NOT_ANSWERED),
                    ),
                ),
            ),
        )

        assertTrue(
            refusal.contains("NOT_ANSWERED") && refusal.contains("recorded-note"),
            "The refusal names the answer and the requirement it was stated on: $refusal",
        )
    }

    @Test
    fun `one document states one thing about one requirement`()
    {
        val refusal = refusalFor(
            configuration(
                section("collected-data", requirement("recorded-note", InformationRequestRequirementType.FIELD)),
                section("supporting-data", requirement("recorded-note", InformationRequestRequirementType.FIELD)),
            ),
        )

        assertTrue(refusal.contains("recorded-note"), "The refusal names the repeated key: $refusal")
    }

    @Test
    fun `two sections cannot share one key`()
    {
        val refusal = refusalFor(
            configuration(
                section("collected-data", requirement("recorded-note", InformationRequestRequirementType.FIELD)),
                section("collected-data", requirement("recorded-assertion", InformationRequestRequirementType.FIELD)),
            ),
        )

        assertTrue(refusal.contains("collected-data"), "The refusal names the repeated key: $refusal")
    }

    @Test
    fun `a machine identifier that is not usable as one is refused`()
    {
        listOf("", "  ", "-leading", "spaced key", "under_score", "trailing-").forEach { candidate ->
            val refusal = refusalFor(
                configuration(
                    section(
                        "collected-data",
                        requirement(candidate, InformationRequestRequirementType.FIELD),
                    ),
                ),
            )
            assertTrue(
                refusal.contains("requirement", ignoreCase = true),
                "A requirement key of '$candidate' should be refused as a requirement key: $refusal",
            )
        }
    }

    @Test
    fun `a requirement that only sometimes applies has to say what decides it`()
    {
        val refusal = refusalFor(
            configuration(
                section(
                    "collected-data",
                    requirement("recorded-note", InformationRequestRequirementType.FIELD).copy(
                        requiredness = InformationRequestRequiredness.CONDITIONAL,
                    ),
                ),
            ),
        )

        assertTrue(refusal.contains("recorded-note"), "The refusal names the requirement: $refusal")
    }

    @Test
    fun `a conditional requirement has to name a rule this document defines`()
    {
        val refusal = refusalFor(
            configuration(
                section(
                    "collected-data",
                    requirement("recorded-note", InformationRequestRequirementType.FIELD).copy(
                        requiredness = InformationRequestRequiredness.CONDITIONAL,
                        conditionalRuleKey = "when-recorded-note-applies",
                    ),
                ),
            ),
        )

        assertTrue(
            refusal.contains("recorded-note") && refusal.contains("when-recorded-note-applies"),
            "The refusal names the requirement and unresolved rule: $refusal",
        )
    }

    @Test
    fun `a condition rule is normalized and may read the field a requirement collects`()
    {
        val normalized = validator.normalize(
            configuration(
                section(
                    "collected-data",
                    requirement("recorded-control", InformationRequestRequirementType.FIELD),
                    requirement("recorded-note", InformationRequestRequirementType.FIELD).copy(
                        requiredness = InformationRequestRequiredness.CONDITIONAL,
                        conditionalRuleKey = " when-recorded-note-applies ",
                    ),
                ),
            ).copy(
                conditionRules = listOf(
                    InformationRequestTemplateConditionRuleRequest(
                        ruleKey = " When-Recorded-Note-Applies ",
                        predicates = listOf(
                            InformationRequestTemplateConditionPredicateRequest(
                                fieldDefinitionId = fieldOf("recorded-control"),
                                valueType = FieldValueType.SHORT_TEXT,
                                operator = FieldOperator.IS_NOT_EMPTY,
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertEquals("when-recorded-note-applies", normalized.conditionRules.single().ruleKey)
        assertEquals(
            "when-recorded-note-applies",
            normalized.sections.single().requirements.last().conditionalRuleKey,
        )
    }

    @Test
    fun `a condition rule defaults hidden response data policy to retain securely`()
    {
        val normalized = validator.normalize(
            configuration(
                section(
                    "collected-data",
                    requirement("recorded-control", InformationRequestRequirementType.FIELD),
                    requirement("recorded-note", InformationRequestRequirementType.FIELD).copy(
                        requiredness = InformationRequestRequiredness.CONDITIONAL,
                        conditionalRuleKey = "when-recorded-note-applies",
                    ),
                ),
            ).copy(
                conditionRules = listOf(
                    InformationRequestTemplateConditionRuleRequest(
                        ruleKey = "when-recorded-note-applies",
                        predicates = listOf(
                            InformationRequestTemplateConditionPredicateRequest(
                                fieldDefinitionId = fieldOf("recorded-control"),
                                valueType = FieldValueType.SHORT_TEXT,
                                operator = FieldOperator.IS_NOT_EMPTY,
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(
            InformationRequestConditionHiddenDataPolicy.RETAIN_SECURELY,
            normalized.conditionRules.single().hiddenDataPolicy,
        )
    }

    @Test
    fun `conditional requirements cannot depend on each other in a cycle`()
    {
        val refusal = refusalFor(
            configuration(
                section(
                    "collected-data",
                    requirement("recorded-note", InformationRequestRequirementType.FIELD).copy(
                        requiredness = InformationRequestRequiredness.CONDITIONAL,
                        conditionalRuleKey = "when-recorded-count-applies",
                    ),
                    requirement("recorded-count", InformationRequestRequirementType.FIELD).copy(
                        requiredness = InformationRequestRequiredness.CONDITIONAL,
                        conditionalRuleKey = "when-recorded-note-applies",
                    ),
                ),
            ).copy(
                conditionRules = listOf(
                    InformationRequestTemplateConditionRuleRequest(
                        ruleKey = "when-recorded-count-applies",
                        predicates = listOf(
                            InformationRequestTemplateConditionPredicateRequest(
                                fieldDefinitionId = fieldOf("recorded-count"),
                                valueType = FieldValueType.SHORT_TEXT,
                                operator = FieldOperator.IS_NOT_EMPTY,
                            ),
                        ),
                    ),
                    InformationRequestTemplateConditionRuleRequest(
                        ruleKey = "when-recorded-note-applies",
                        predicates = listOf(
                            InformationRequestTemplateConditionPredicateRequest(
                                fieldDefinitionId = fieldOf("recorded-note"),
                                valueType = FieldValueType.SHORT_TEXT,
                                operator = FieldOperator.IS_NOT_EMPTY,
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertTrue(
            refusal.contains("recorded-note") || refusal.contains("recorded-count"),
            "The refusal names a requirement in the cycle: $refusal",
        )
    }

    @Test
    fun `an evidence policy belongs to a requested document`()
    {
        val refusal = refusalFor(
            configuration(
                section(
                    "collected-data",
                    requirement("recorded-note", InformationRequestRequirementType.FIELD).copy(
                        evidencePolicy = InformationRequestTemplateEvidencePolicyRequest(),
                    ),
                ),
            ),
        )

        assertTrue(refusal.contains("recorded-note"), "The refusal names the requirement: $refusal")
    }

    @Test
    fun `an attribute that is never captured cannot restrict which values are accepted`()
    {
        val refusal = refusalFor(
            configuration(
                section(
                    "supporting-data",
                    documentRequirement("supporting-record").copy(
                        evidencePolicy = InformationRequestTemplateEvidencePolicyRequest(
                            acceptedValues = listOf(
                                InformationRequestTemplateAcceptedValueRequest(
                                    InformationRequestEvidenceAttribute.ISSUER,
                                    "recording-body",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertTrue(
            refusal.contains("ISSUER") && refusal.contains("supporting-record"),
            "The refusal names the attribute and the requirement: $refusal",
        )
    }

    @Test
    fun `a file type is read from the file, so restricting it needs no captured attribute`()
    {
        val normalized = validator.normalize(
            configuration(
                section(
                    "supporting-data",
                    documentRequirement("supporting-record").copy(
                        evidencePolicy = InformationRequestTemplateEvidencePolicyRequest(
                            acceptedValues = listOf(
                                InformationRequestTemplateAcceptedValueRequest(
                                    InformationRequestEvidenceAttribute.CONTENT_TYPE,
                                    "application/pdf",
                                ),
                                InformationRequestTemplateAcceptedValueRequest(
                                    InformationRequestEvidenceAttribute.CONTENT_TYPE,
                                    "application/pdf",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(
            listOf(
                InformationRequestTemplateAcceptedValueRequest(
                    InformationRequestEvidenceAttribute.CONTENT_TYPE,
                    "application/pdf",
                ),
            ),
            normalized.sections.single().requirements.single().evidencePolicy?.acceptedValues,
        )
    }

    @Test
    fun `supporting evidence has to be a requested document stated in the same document`()
    {
        val missing = refusalFor(
            configuration(
                section(
                    "collected-data",
                    requirement("recorded-note", InformationRequestRequirementType.FIELD).copy(
                        supportingEvidenceRequirementKeys = listOf("absent-record"),
                    ),
                ),
            ),
        )
        assertTrue(missing.contains("absent-record"), "The refusal names the key that resolves to nothing: $missing")

        val notADocument = refusalFor(
            configuration(
                section(
                    "collected-data",
                    requirement("recorded-note", InformationRequestRequirementType.FIELD).copy(
                        supportingEvidenceRequirementKeys = listOf("recorded-assertion"),
                    ),
                    requirement("recorded-assertion", InformationRequestRequirementType.RESPONSE_ATTESTATION),
                ),
            ),
        )
        assertTrue(
            notADocument.contains("recorded-assertion"),
            "The refusal names the key that is not a requested document: $notADocument",
        )
    }

    @Test
    fun `a requested document does not itself declare supporting evidence`()
    {
        val refusal = refusalFor(
            configuration(
                section(
                    "supporting-data",
                    documentRequirement("supporting-record").copy(
                        evidencePolicy = InformationRequestTemplateEvidencePolicyRequest(),
                        supportingEvidenceRequirementKeys = listOf("alternate-record"),
                    ),
                    documentRequirement("alternate-record").copy(
                        evidencePolicy = InformationRequestTemplateEvidencePolicyRequest(),
                    ),
                ),
            ),
        )

        assertTrue(
            refusal.contains("supporting-record"),
            "The refusal names the requested document that declared support: $refusal",
        )
    }

    @Test
    fun `only a requested document declares substitutes, and only a document may be one`()
    {
        val notADocument = refusalFor(
            configuration(
                section(
                    "collected-data",
                    requirement("recorded-note", InformationRequestRequirementType.FIELD).copy(
                        substituteRequirementKeys = listOf("supporting-record"),
                    ),
                    documentRequirement("supporting-record").copy(
                        evidencePolicy = InformationRequestTemplateEvidencePolicyRequest(),
                    ),
                ),
            ),
        )
        assertTrue(
            notADocument.contains("recorded-note"),
            "The refusal names the requirement that is not a requested document: $notADocument",
        )

        val substituteIsNotADocument = refusalFor(
            configuration(
                section(
                    "supporting-data",
                    documentRequirement("supporting-record").copy(
                        evidencePolicy = InformationRequestTemplateEvidencePolicyRequest(),
                        substituteRequirementKeys = listOf("recorded-assertion"),
                    ),
                    requirement("recorded-assertion", InformationRequestRequirementType.RESPONSE_ATTESTATION),
                ),
            ),
        )
        assertTrue(
            substituteIsNotADocument.contains("recorded-assertion"),
            "The refusal names the key that cannot stand in: $substituteIsNotADocument",
        )
    }

    @Test
    fun `substitution stays flat so resolving alternatives never follows a second hop`()
    {
        val refusal = refusalFor(
            configuration(
                section(
                    "supporting-data",
                    documentRequirement("supporting-record").copy(
                        evidencePolicy = InformationRequestTemplateEvidencePolicyRequest(),
                        substituteRequirementKeys = listOf("alternate-record"),
                    ),
                    documentRequirement("alternate-record").copy(
                        evidencePolicy = InformationRequestTemplateEvidencePolicyRequest(),
                        substituteRequirementKeys = listOf("further-record"),
                    ),
                    documentRequirement("further-record").copy(
                        evidencePolicy = InformationRequestTemplateEvidencePolicyRequest(),
                    ),
                ),
            ),
        )

        assertTrue(
            refusal.contains("alternate-record"),
            "The refusal names the requirement standing in the middle of the chain: $refusal",
        )
    }

    @Test
    fun `nothing stands in for itself and nothing supports itself`()
    {
        val substitute = refusalFor(
            configuration(
                section(
                    "supporting-data",
                    documentRequirement("supporting-record").copy(
                        evidencePolicy = InformationRequestTemplateEvidencePolicyRequest(),
                        substituteRequirementKeys = listOf("supporting-record"),
                    ),
                ),
            ),
        )
        assertTrue(substitute.contains("supporting-record"), "The refusal names the requirement: $substitute")

        val supporting = refusalFor(
            configuration(
                section(
                    "supporting-data",
                    documentRequirement("supporting-record").copy(
                        evidencePolicy = InformationRequestTemplateEvidencePolicyRequest(),
                        supportingEvidenceRequirementKeys = listOf("supporting-record"),
                    ),
                ),
            ),
        )
        assertTrue(supporting.contains("supporting-record"), "The refusal names the requirement: $supporting")
    }

    @Test
    fun `a party that cannot answer is never the party an answer is owed by`()
    {
        listOf(
            InformationRequestResponseMode.VIEW_ONLY to InformationRequestRequiredness.REQUIRED,
            InformationRequestResponseMode.NOT_DISCLOSED to InformationRequestRequiredness.REQUIRED,
            InformationRequestResponseMode.VIEW_ONLY to InformationRequestRequiredness.CONDITIONAL,
        ).forEach { (mode, requiredness) ->
            val refusal = refusalFor(
                configuration(
                    section(
                        "collected-data",
                        requirement("recorded-note", InformationRequestRequirementType.FIELD).copy(
                            responseMode = mode,
                            requiredness = requiredness,
                            conditionalRuleKey = "recorded-note-applies",
                        ),
                    ),
                ),
            )

            assertTrue(
                refusal.contains("recorded-note"),
                "A $mode requirement that is $requiredness should be refused by name: $refusal",
            )
        }
    }

    @Test
    fun `a party that cannot answer is offered no answers to choose from`()
    {
        val refusal = refusalFor(
            configuration(
                section(
                    "collected-data",
                    requirement("recorded-note", InformationRequestRequirementType.FIELD).copy(
                        responseMode = InformationRequestResponseMode.VIEW_ONLY,
                        permittedDispositions = listOf(InformationRequestResponseDisposition.PROVIDED),
                    ),
                ),
            ),
        )

        assertTrue(refusal.contains("recorded-note"), "The refusal names the requirement: $refusal")
    }

    @Test
    fun `a waiver the responding party declares needs a party that can answer`()
    {
        val refusal = refusalFor(
            configuration(
                section(
                    "supporting-data",
                    documentRequirement("supporting-record").copy(
                        responseMode = InformationRequestResponseMode.NOT_DISCLOSED,
                        evidencePolicy = InformationRequestTemplateEvidencePolicyRequest(
                            waiverPolicy = InformationRequestEvidenceWaiverPolicy.RESPONDENT_DECLARED,
                        ),
                    ),
                ),
            ),
        )

        assertTrue(refusal.contains("supporting-record"), "The refusal names the requirement: $refusal")
    }

    @Test
    fun `a requirement the responding party never sees may still be tracked by the requesting side`()
    {
        val normalized = validator.normalize(
            configuration(
                section(
                    "collected-data",
                    requirement("recorded-note", InformationRequestRequirementType.FIELD).copy(
                        responseMode = InformationRequestResponseMode.NOT_DISCLOSED,
                        requiredness = InformationRequestRequiredness.OPTIONAL,
                    ),
                ),
            ),
        )

        assertEquals(
            InformationRequestResponseMode.NOT_DISCLOSED,
            normalized.sections.single().requirements.single().responseMode,
        )
    }

    @Test
    fun `review that only happens on an exception needs an exception to be reachable`()
    {
        val refusal = refusalFor(
            configuration(
                section(
                    "collected-data",
                    requirement("recorded-note", InformationRequestRequirementType.FIELD).copy(
                        reviewPolicy = InformationRequestReviewPolicy.REQUIRED_ON_EXCEPTION,
                        permittedDispositions = listOf(InformationRequestResponseDisposition.PROVIDED),
                    ),
                ),
            ),
        )

        assertTrue(refusal.contains("recorded-note"), "The refusal names the requirement: $refusal")

        val reachable = validator.normalize(
            configuration(
                section(
                    "collected-data",
                    requirement("recorded-note", InformationRequestRequirementType.FIELD).copy(
                        reviewPolicy = InformationRequestReviewPolicy.REQUIRED_ON_EXCEPTION,
                        permittedDispositions = listOf(
                            InformationRequestResponseDisposition.PROVIDED,
                            InformationRequestResponseDisposition.UNAVAILABLE,
                        ),
                    ),
                ),
            ),
        )
        assertEquals(2, reachable.sections.single().requirements.single().permittedDispositions.size)
    }

    @Test
    fun `an empty draft is a document an author has not finished rather than a broken one`()
    {
        val normalized = validator.normalize(InformationRequestTemplateConfigurationRequest())

        assertEquals(emptyList<InformationRequestTemplateSectionRequest>(), normalized.sections)
        assertNull(normalized.schemaVersionId)
    }

    @Test
    fun `a section with no requirements is refused because nothing would ever render it`()
    {
        val refusal = refusalFor(configuration(InformationRequestTemplateSectionRequest("collected-data", "Collected data")))

        assertTrue(refusal.contains("collected-data"), "The refusal names the empty section: $refusal")
    }

    @Test
    fun `a typed requirement names the field it collects and every other kind names none`()
    {
        val unnamed = refusalFor(
            configuration(
                section(
                    "collected-data",
                    requirement("recorded-note", InformationRequestRequirementType.FIELD)
                        .copy(collectedFieldDefinitionId = null),
                ),
            ),
        )
        assertTrue(
            unnamed.contains("recorded-note"),
            "The refusal names the typed requirement that collects nothing: $unnamed",
        )

        val named = refusalFor(
            configuration(
                section(
                    "collected-data",
                    requirement("recorded-assertion", InformationRequestRequirementType.RESPONSE_ATTESTATION)
                        .copy(collectedFieldDefinitionId = fieldOf("recorded-note")),
                ),
            ),
        )
        assertTrue(
            named.contains("recorded-assertion"),
            "The refusal names the requirement that collects a field it cannot record one in: $named",
        )
    }

    @Test
    fun `one version collects one field once`()
    {
        val refusal = refusalFor(
            configuration(
                section(
                    "collected-data",
                    requirement("recorded-note", InformationRequestRequirementType.FIELD),
                    requirement("restated-note", InformationRequestRequirementType.FIELD)
                        .copy(collectedFieldDefinitionId = fieldOf("recorded-note")),
                ),
            ),
        )

        assertTrue(
            refusal.contains("recorded-note") && refusal.contains("restated-note"),
            "The refusal names both requirements that resolve to one field: $refusal",
        )
    }

    @Test
    fun `an empty permitted set is the plain provided answer rather than every answer`()
    {
        val answerable = validator.normalize(
            configuration(
                section("collected-data", requirement("recorded-note", InformationRequestRequirementType.FIELD)),
            ),
        )
        assertEquals(
            listOf(InformationRequestResponseDisposition.PROVIDED),
            answerable.sections.single().requirements.single().permittedDispositions,
            "The stored waiver rule reads the set as an allowlist, so the reading is stored rather " +
                "than left to whoever asks next",
        )

        val unanswerable = validator.normalize(
            configuration(
                section(
                    "collected-data",
                    requirement("recorded-note", InformationRequestRequirementType.FIELD).copy(
                        responseMode = InformationRequestResponseMode.NOT_DISCLOSED,
                    ),
                ),
            ),
        )
        assertEquals(
            emptyList<InformationRequestResponseDisposition>(),
            unanswerable.sections.single().requirements.single().permittedDispositions,
            "A party that cannot answer is offered nothing",
        )
    }

    @Test
    fun `review that only happens on an exception is refused when nothing was stated either`()
    {
        val refusal = refusalFor(
            configuration(
                section(
                    "collected-data",
                    requirement("recorded-note", InformationRequestRequirementType.FIELD).copy(
                        reviewPolicy = InformationRequestReviewPolicy.REQUIRED_ON_EXCEPTION,
                    ),
                ),
            ),
        )

        assertTrue(refusal.contains("recorded-note"), "The refusal names the requirement: $refusal")
    }

    @Test
    fun `a requirement answered once per occurrence has to name a group this document defines`()
    {
        val refusal = refusalFor(
            configuration(
                section(
                    "collected-data",
                    requirement("recorded-note", InformationRequestRequirementType.FIELD).copy(
                        occurrenceAnchorKey = "undefined-group",
                    ),
                ),
            ),
        )

        assertTrue(
            refusal.contains("recorded-note") && refusal.contains("undefined-group"),
            "The refusal names the requirement and the group it could not resolve: $refusal",
        )
    }

    @Test
    fun `a requirement answered once per occurrence succeeds once its group is defined`()
    {
        val normalized = validator.normalize(
            InformationRequestTemplateConfigurationRequest(
                groups = listOf(InformationRequestTemplateGroupRequest(groupKey = "reported-item")),
                sections = listOf(
                    section(
                        "collected-data",
                        requirement("recorded-note", InformationRequestRequirementType.FIELD).copy(
                            occurrenceAnchorKey = "reported-item",
                        ),
                    ),
                ),
            ),
        )

        assertEquals(
            "reported-item",
            normalized.sections.single().requirements.single().occurrenceAnchorKey,
        )
    }

    @Test
    fun `a group key is trimmed and lowercased the same as any other machine identifier`()
    {
        val normalized = validator.normalize(
            InformationRequestTemplateConfigurationRequest(
                groups = listOf(InformationRequestTemplateGroupRequest(groupKey = "  Reported-Item  ")),
                sections = listOf(
                    section(
                        "collected-data",
                        requirement("recorded-note", InformationRequestRequirementType.FIELD).copy(
                            occurrenceAnchorKey = "reported-item",
                        ),
                    ),
                ),
            ),
        )

        assertEquals("reported-item", normalized.groups.single().groupKey)
    }

    @Test
    fun `two repeatable groups cannot share one key`()
    {
        val refusal = assertThrows<InformationRequestTemplateValidationException> {
            validator.normalize(
                InformationRequestTemplateConfigurationRequest(
                    groups = listOf(
                        InformationRequestTemplateGroupRequest(groupKey = "reported-item"),
                        InformationRequestTemplateGroupRequest(groupKey = "reported-item"),
                    ),
                ),
            )
        }.message

        assertTrue(refusal.contains("reported-item"), "The refusal names the repeated key: $refusal")
    }

    @Test
    fun `a repeatable group nests only inside a group this document defines`()
    {
        val refusal = assertThrows<InformationRequestTemplateValidationException> {
            validator.normalize(
                InformationRequestTemplateConfigurationRequest(
                    groups = listOf(
                        InformationRequestTemplateGroupRequest(
                            groupKey = "reported-detail",
                            parentGroupKey = "absent-group",
                        ),
                    ),
                ),
            )
        }.message

        assertTrue(
            refusal.contains("reported-detail") && refusal.contains("absent-group"),
            "The refusal names the group and the parent it could not resolve: $refusal",
        )
    }

    @Test
    fun `a repeatable group does not nest inside itself`()
    {
        val refusal = assertThrows<InformationRequestTemplateValidationException> {
            validator.normalize(
                InformationRequestTemplateConfigurationRequest(
                    groups = listOf(
                        InformationRequestTemplateGroupRequest(
                            groupKey = "reported-item",
                            parentGroupKey = "reported-item",
                        ),
                    ),
                ),
            )
        }.message

        assertTrue(refusal.contains("reported-item"), "The refusal names the group: $refusal")
    }

    @Test
    fun `repeatable group nesting cannot form a longer cycle`()
    {
        val refusal = assertThrows<InformationRequestTemplateValidationException> {
            validator.normalize(
                InformationRequestTemplateConfigurationRequest(
                    groups = listOf(
                        InformationRequestTemplateGroupRequest(groupKey = "group-a", parentGroupKey = "group-c"),
                        InformationRequestTemplateGroupRequest(groupKey = "group-b", parentGroupKey = "group-a"),
                        InformationRequestTemplateGroupRequest(groupKey = "group-c", parentGroupKey = "group-b"),
                    ),
                ),
            )
        }.message

        assertTrue(
            refusal.contains("group-a") || refusal.contains("group-b") || refusal.contains("group-c"),
            "The refusal names a group in the cycle: $refusal",
        )
    }

    @Test
    fun `nested repeatable groups normalize together`()
    {
        val normalized = validator.normalize(
            InformationRequestTemplateConfigurationRequest(
                groups = listOf(
                    InformationRequestTemplateGroupRequest(groupKey = "reported-item", maxOccurrences = 10),
                    InformationRequestTemplateGroupRequest(
                        groupKey = "reported-detail",
                        parentGroupKey = "reported-item",
                        minOccurrences = 1,
                    ),
                ),
            ),
        )

        assertEquals(
            mapOf("reported-item" to null, "reported-detail" to "reported-item"),
            normalized.groups.associate { it.groupKey to it.parentGroupKey },
        )
    }

    private fun refusalFor(request: InformationRequestTemplateConfigurationRequest): String =
        assertThrows<InformationRequestTemplateValidationException> { validator.normalize(request) }.message

    private fun configuration(vararg sections: InformationRequestTemplateSectionRequest) =
        InformationRequestTemplateConfigurationRequest(sections = sections.toList())

    private fun section(key: String, vararg requirements: InformationRequestTemplateRequirementRequest) =
        InformationRequestTemplateSectionRequest(
            sectionKey = key,
            title = "Collected data",
            requirements = requirements.toList(),
        )

    /**
     * A typed requirement has to name the field it collects, so the fixture derives one field per
     * requirement key. Two requirements only collide when a test says they do.
     */
    private fun requirement(key: String, type: InformationRequestRequirementType) =
        InformationRequestTemplateRequirementRequest(
            requirementKey = key,
            requirementType = type,
            prompt = "State the recorded note",
            collectedFieldDefinitionId =
                if (type == InformationRequestRequirementType.FIELD) fieldOf(key) else null,
        )

    private fun fieldOf(requirementKey: String): UUID =
        UUID.nameUUIDFromBytes("collected-field:$requirementKey".toByteArray())

    private fun documentRequirement(key: String) =
        requirement(key, InformationRequestRequirementType.DOCUMENT)
}
