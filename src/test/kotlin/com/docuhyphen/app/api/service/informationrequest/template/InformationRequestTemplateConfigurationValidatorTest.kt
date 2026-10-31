package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.model.dto.*
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.fields.FieldOperator
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.assertTimeoutPreemptively
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Duration
import java.util.*

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
    fun `a condition rule cannot read a confirmation`() {
        val refusal = refusalFor(
            ruleReadingSource(
                requirement(
                    "recorded-assertion",
                    InformationRequestRequirementType.RESPONSE_ATTESTATION
                )
            )
        )

        assertTrue(
            refusal.contains("when-recorded-note-applies") && refusal.contains("recorded-assertion") &&
                    refusal.contains("confirmation"),
            "The refusal names the rule and the confirmation it reads: $refusal",
        )
    }

    @Test
    fun `a condition rule may read a requested document`() {
        val normalized = validator.normalize(ruleReadingSource(documentRequirement("supporting-record")))

        assertEquals("supporting-record", normalized.conditionRules.single().predicates.single().sourceRequirementKey)
    }

    private fun ruleReadingSource(source: InformationRequestTemplateRequirementRequest) =
        configuration(
            section(
                "collected-data",
                source,
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
                            sourceRequirementKey = source.requirementKey,
                            operator = FieldOperator.IS_NOT_EMPTY,
                        ),
                    ),
                ),
            ),
        )

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
    fun `a deep layered condition graph without a cycle is checked in linear time`() {
        val layered = layeredConditions(layers = 60)

        val normalized = assertTimeoutPreemptively(Duration.ofSeconds(10)) { validator.normalize(layered) }

        assertEquals(121, normalized.sections.single().requirements.size)
    }

    @Test
    fun `a cycle through a deep layered condition graph is still refused`() {
        val layered = layeredConditions(layers = 60)
        val closed = layered.copy(
            conditionRules = layered.conditionRules.map { rule ->
                if (rule.ruleKey != "layer-0-applies") rule
                else rule.copy(
                    predicates = rule.predicates + InformationRequestTemplateConditionPredicateRequest(
                        fieldDefinitionId = fieldOf("layer-59-first"),
                        valueType = FieldValueType.SHORT_TEXT,
                        operator = FieldOperator.IS_NOT_EMPTY,
                    ),
                )
            },
        )

        val refusal = assertTimeoutPreemptively(Duration.ofSeconds(10)) { refusalFor(closed) }

        assertTrue(refusal.contains("depend on each other"), refusal)
        assertTrue(refusal.contains("layer-59-first") && refusal.contains("layer-0-first"), refusal)
    }

    @Test
    fun `a template version states a bounded number of requirements, condition rules and predicates`() {
        val crowded = configuration(
            section(
                "collected-data",
                *(1..501).map { requirement("record-$it", InformationRequestRequirementType.FIELD) }.toTypedArray()
            ),
        )
        assertTrue(refusalFor(crowded).contains("at most 500 requirements"), refusalFor(crowded))
        validator.normalize(
            configuration(
                section(
                    "collected-data",
                    *(1..500).map { requirement("record-$it", InformationRequestRequirementType.FIELD) }.toTypedArray()
                ),
            ),
        )

        val ruled = configuration(
            section(
                "collected-data",
                requirement("recorded-note", InformationRequestRequirementType.FIELD)
            )
        )
            .copy(conditionRules = (1..501).map { rule("rule-$it", "recorded-note") })
        assertTrue(refusalFor(ruled).contains("at most 500 condition rules"), refusalFor(ruled))

        val predicated = configuration(
            section(
                "collected-data",
                requirement("recorded-note", InformationRequestRequirementType.FIELD)
            )
        )
            .copy(conditionRules = listOf(rule("crowded-rule", *Array(51) { "recorded-note" })))
        val refusal = assertThrows<InformationRequestTemplateValidationException> { validator.normalize(predicated) }
        assertTrue(refusal.message.contains("at most 50 predicates"), refusal.message)
        assertEquals("crowded-rule", refusal.conditionRuleKey)
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

    @ParameterizedTest(name = "{0}")
    @ValueSource(
        strings = [
            "no-files", "fewer-allowed", "empty-file", "empty-total", "small-total", "no-pages", "empty-page-limit",
            "fewer-pages", "short-age", "uncaptured-issue", "negative-validity", "uncaptured-expiry", "short-coverage",
            "uncaptured-coverage", "uncaptured-continuity",
        ],
    )
    fun `evidence bounds the database refuses are refused with the section and requirement they belong to`(case: String) {
        val refusal = assertThrows<InformationRequestTemplateValidationException> {
            validator.normalize(
                configuration(
                    section(
                        "supporting-data",
                        documentRequirement("supporting-record").copy(evidencePolicy = unboundedPolicy(case))
                    ),
                ),
            )
        }

        assertEquals("supporting-data", refusal.sectionKey)
        assertEquals("supporting-record", refusal.requirementKey)
    }

    @Test
    fun `evidence bounds within every database rule are kept`() {
        val normalized = validator.normalize(
            configuration(
                section(
                    "supporting-data",
                    documentRequirement("supporting-record").copy(evidencePolicy = boundedPolicy())
                )
            ),
        )

        assertEquals(boundedPolicy(), normalized.sections.single().requirements.single().evidencePolicy)
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

    @ParameterizedTest
    @ValueSource(strings = ["PDF", ".pdf", "application", "application/", "image/*"])
    fun `a file type that is not a media type is refused with the requirement it restricts`(value: String) {
        val refusal = refusalFor(configuration(section("supporting-data", fileTypeRestricted(value))))

        assertTrue(
            refusal.contains("supporting-record") && refusal.contains(value),
            "The refusal names the requirement and the value: $refusal"
        )
    }

    @Test
    fun `a file type with parameters is refused and the refusal names its section`() {
        val refusal = assertThrows<InformationRequestTemplateValidationException> {
            validator.normalize(
                configuration(
                    section(
                        "supporting-data",
                        fileTypeRestricted("application/pdf; charset=binary")
                    )
                )
            )
        }

        assertEquals("supporting-data", refusal.sectionKey)
        assertEquals("supporting-record", refusal.requirementKey)
    }

    @Test
    fun `a file type is kept in its canonical lower case form`() {
        val normalized =
            validator.normalize(configuration(section("supporting-data", fileTypeRestricted(" Application/PDF "))))

        assertEquals(
            listOf("application/pdf"),
            normalized.sections.single().requirements.single().evidencePolicy?.acceptedValues?.map { it.acceptedValue },
        )
    }

    private fun fileTypeRestricted(value: String) = documentRequirement("supporting-record").copy(
        evidencePolicy = InformationRequestTemplateEvidencePolicyRequest(
            acceptedValues = listOf(
                InformationRequestTemplateAcceptedValueRequest(
                    InformationRequestEvidenceAttribute.CONTENT_TYPE,
                    value
                )
            ),
        ),
    )

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
    fun `a reference that resolves to nothing or to no document is refused against the Requirement that holds it`() {
        val holding = requirement("recorded-note", InformationRequestRequirementType.FIELD)
        val refusals = listOf(
            configuration(
                section(
                    "collected-data",
                    holding.copy(supportingEvidenceRequirementKeys = listOf("absent-record"))
                )
            ),
            configuration(
                section(
                    "collected-data",
                    holding.copy(supportingEvidenceRequirementKeys = listOf("recorded-assertion")),
                    requirement("recorded-assertion", InformationRequestRequirementType.RESPONSE_ATTESTATION),
                ),
            ),
        ).map { assertThrows<InformationRequestTemplateValidationException> { validator.normalize(it) } }

        refusals.forEach { assertEquals("recorded-note", it.requirementKey) }
        assertTrue(refusals[0].message.contains("absent-record"), refusals[0].message)
        assertTrue(refusals[1].message.contains("recorded-assertion"), refusals[1].message)
    }

    @ParameterizedTest
    @ValueSource(strings = ["section-title", "section-help", "prompt", "requirement-help", "requirement-key", "accepted-value"])
    fun `text longer than the record keeps is refused with where it is and how long it may be`(case: String) {
        val document = documentRequirement("supporting-record")
        val long = { length: Int -> "a".repeat(length) }
        val section = when (case) {
            "section-title" -> section("supporting-data", document).copy(title = long(256))
            "section-help" -> section("supporting-data", document).copy(helpText = long(2049))
            "prompt" -> section("supporting-data", document.copy(prompt = long(1025)))
            "requirement-help" -> section("supporting-data", document.copy(helpText = long(2049)))
            "requirement-key" -> section("supporting-data", document.copy(requirementKey = long(129)))
            else -> section(
                "supporting-data",
                document.copy(
                    evidencePolicy = boundedPolicy().copy(
                        issuerRequirement = InformationRequestEvidenceAttributeRequirement.REQUIRED,
                        acceptedValues = listOf(
                            InformationRequestTemplateAcceptedValueRequest(
                                InformationRequestEvidenceAttribute.ISSUER,
                                long(256)
                            ),
                        ),
                    ),
                ),
            )
        }

        val refusal =
            assertThrows<InformationRequestTemplateValidationException> { validator.normalize(configuration(section)) }

        assertEquals("supporting-data", refusal.sectionKey)
        val requirementCase = case !in setOf("section-title", "section-help")
        assertEquals(
            if (requirementCase) section.requirements.single().requirementKey else null,
            refusal.requirementKey
        )
        assertTrue(Regex("is longer than [0-9]+ characters$").containsMatchIn(refusal.message), refusal.message)
    }

    @Test
    fun `text at the length the record keeps is accepted`() {
        val document =
            documentRequirement("supporting-record").copy(prompt = "a".repeat(1024), helpText = "b".repeat(2048))

        val normalized = validator.normalize(
            configuration(
                section("supporting-data", document).copy(
                    title = "c".repeat(255),
                    helpText = "d".repeat(2048)
                )
            ),
        )

        assertEquals(1024, normalized.sections.single().requirements.single().prompt.length)
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
        val refusal =
            refusalFor(configuration(InformationRequestTemplateSectionRequest("collected-data", "Collected data")))

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
    fun `a repeatable group whose subtree anchors no requirement is refused by name`() {
        val refusal = refusalFor(
            InformationRequestTemplateConfigurationRequest(
                groups = listOf(
                    InformationRequestTemplateGroupRequest(groupKey = "reported-item"),
                    InformationRequestTemplateGroupRequest(groupKey = "unanswered-group"),
                ),
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

        assertTrue(refusal.contains("unanswered-group"), "The refusal names the empty group: $refusal")
    }

    @Test
    fun `a group that anchors nothing itself is accepted when a nested group beneath it anchors a requirement`() {
        val normalized = validator.normalize(
            InformationRequestTemplateConfigurationRequest(
                groups = listOf(
                    InformationRequestTemplateGroupRequest(groupKey = "reported-item"),
                    InformationRequestTemplateGroupRequest(
                        groupKey = "reported-detail",
                        parentGroupKey = "reported-item"
                    ),
                ),
                sections = listOf(
                    section(
                        "collected-data",
                        requirement("recorded-note", InformationRequestRequirementType.FIELD).copy(
                            occurrenceAnchorKey = "reported-detail",
                        ),
                    ),
                ),
            ),
        )

        assertEquals(setOf("reported-item", "reported-detail"), normalized.groups.map { it.groupKey }.toSet())
    }

    @Test
    fun `a condition rule on a requirement that is not conditional is refused by name`() {
        listOf(
            InformationRequestRequiredness.REQUIRED,
            InformationRequestRequiredness.OPTIONAL
        ).forEach { requiredness ->
            val refusal = refusalFor(
                configuration(
                    section(
                        "collected-data",
                        requirement("recorded-control", InformationRequestRequirementType.FIELD),
                        requirement("recorded-note", InformationRequestRequirementType.FIELD).copy(
                            requiredness = requiredness,
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

            assertTrue(
                refusal.contains("recorded-note") && refusal.contains("when-recorded-note-applies"),
                "A $requiredness requirement naming a rule should be refused by name: $refusal",
            )
        }
    }

    @Test
    fun `a template version states at most one hundred repeatable groups`() {
        val refusal = refusalFor(
            InformationRequestTemplateConfigurationRequest(
                groups = (0..100).map { InformationRequestTemplateGroupRequest(groupKey = "reported-item-$it") },
                sections = listOf(
                    section(
                        "collected-data",
                        requirement("recorded-note", InformationRequestRequirementType.FIELD)
                    )
                ),
            ),
        )

        assertTrue(refusal.contains("at most 100 repeatable groups"), refusal)
    }

    @Test
    fun `repeatable groups nest at most five deep`() {
        val accepted = validator.normalize(nestedGroups(5, "reported-level"))
        assertEquals(5, accepted.groups.size)

        val refusal = refusalFor(nestedGroups(6, "reported-level"))

        assertTrue(refusal.contains("reported-level-5") && refusal.contains("five"), refusal)
    }

    @Test
    fun `a nesting whose longest occurrence path would not fit is refused naming the deepest group`() {
        val refusal = refusalFor(nestedGroups(4, "a".repeat(118)))

        assertTrue(refusal.contains("a".repeat(118) + "-3") && refusal.contains("512"), refusal)
    }

    @Test
    fun `occurrence counts are bounded so creating a request materializes a bounded number of occurrences`() {
        val tooManyRequired = refusalFor(nestedGroups(1, "reported-item", minimum = 101))
        val tooManyAllowed = refusalFor(nestedGroups(1, "reported-item", maximum = 1001))
        val tooManyAtCreation = refusalFor(nestedGroups(3, "reported-item", minimum = 10))

        assertTrue(tooManyRequired.contains("at most 100"), tooManyRequired)
        assertTrue(tooManyAllowed.contains("at most 1000"), tooManyAllowed)
        assertTrue(tooManyAtCreation.contains("500"), tooManyAtCreation)
    }

    @Test
    fun `a requirement's rule key and occurrence anchor are lowercased like the keys they name`() {
        val normalized = validator.normalize(
            InformationRequestTemplateConfigurationRequest(
                groups = listOf(InformationRequestTemplateGroupRequest(groupKey = "Reported-Item")),
                sections = listOf(
                    section(
                        "collected-data",
                        requirement("recorded-control", InformationRequestRequirementType.FIELD),
                        requirement("recorded-note", InformationRequestRequirementType.FIELD).copy(
                            requiredness = InformationRequestRequiredness.CONDITIONAL,
                            conditionalRuleKey = " When-Note-Applies ",
                        ),
                        requirement("recorded-entry", InformationRequestRequirementType.FIELD).copy(
                            occurrenceAnchorKey = " Reported-Item ",
                        ),
                    ),
                ),
                conditionRules = listOf(
                    InformationRequestTemplateConditionRuleRequest(
                        ruleKey = "When-Note-Applies",
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

        val requirements = normalized.sections.single().requirements.associateBy { it.requirementKey }
        assertEquals("when-note-applies", requirements.getValue("recorded-note").conditionalRuleKey)
        assertEquals("reported-item", requirements.getValue("recorded-entry").occurrenceAnchorKey)
    }

    @Test
    fun `a rule reads only answers at the top level or in the decided requirement's own or enclosing group`() {
        fun ruleOn(decidedAnchor: String?, sourceAnchor: String?) = InformationRequestTemplateConfigurationRequest(
            groups = listOf(
                InformationRequestTemplateGroupRequest(groupKey = "reported-item"),
                InformationRequestTemplateGroupRequest(groupKey = "reported-detail", parentGroupKey = "reported-item"),
                InformationRequestTemplateGroupRequest(groupKey = "reported-other"),
            ),
            sections = listOf(
                section(
                    "collected-data",
                    requirement(
                        "recorded-control",
                        InformationRequestRequirementType.FIELD
                    ).copy(occurrenceAnchorKey = sourceAnchor),
                    requirement("recorded-note", InformationRequestRequirementType.FIELD).copy(
                        requiredness = InformationRequestRequiredness.CONDITIONAL,
                        conditionalRuleKey = "when-control-is-stated",
                        occurrenceAnchorKey = decidedAnchor,
                    ),
                    requirement(
                        "recorded-item",
                        InformationRequestRequirementType.FIELD
                    ).copy(occurrenceAnchorKey = "reported-item"),
                    requirement(
                        "recorded-detail",
                        InformationRequestRequirementType.FIELD
                    ).copy(occurrenceAnchorKey = "reported-detail"),
                    requirement(
                        "recorded-other",
                        InformationRequestRequirementType.FIELD
                    ).copy(occurrenceAnchorKey = "reported-other"),
                ),
            ),
            conditionRules = listOf(
                InformationRequestTemplateConditionRuleRequest(
                    ruleKey = "when-control-is-stated",
                    predicates = listOf(
                        InformationRequestTemplateConditionPredicateRequest(
                            fieldDefinitionId = fieldOf("recorded-control"),
                            valueType = FieldValueType.SHORT_TEXT,
                            operator = FieldOperator.IS_NOT_EMPTY,
                        ),
                    ),
                ),
            ),
        )

        validator.normalize(ruleOn(decidedAnchor = null, sourceAnchor = null))
        validator.normalize(ruleOn(decidedAnchor = "reported-detail", sourceAnchor = "reported-item"))
        validator.normalize(ruleOn(decidedAnchor = "reported-detail", sourceAnchor = null))
        val fromGroupToTop = refusalFor(ruleOn(decidedAnchor = null, sourceAnchor = "reported-item"))
        val fromSibling = refusalFor(ruleOn(decidedAnchor = "reported-detail", sourceAnchor = "reported-other"))

        listOf(fromGroupToTop, fromSibling).forEach { refusal ->
            assertTrue(refusal.contains("when-control-is-stated") && refusal.contains("recorded-note"), refusal)
        }
    }

    @Test
    fun `a substitute is found only at the top level or in the document's own group`() {
        fun substituted(documentAnchor: String?, substituteAnchor: String?) =
            InformationRequestTemplateConfigurationRequest(
                groups = listOf(
                    InformationRequestTemplateGroupRequest(groupKey = "reported-item"),
                    InformationRequestTemplateGroupRequest(
                        groupKey = "reported-detail",
                        parentGroupKey = "reported-item"
                    ),
                ),
                sections = listOf(
                    section(
                        "collected-data",
                        documentRequirement("supporting-record").copy(
                            substituteRequirementKeys = listOf("alternative-record"),
                            occurrenceAnchorKey = documentAnchor,
                        ),
                        documentRequirement("alternative-record").copy(occurrenceAnchorKey = substituteAnchor),
                        requirement(
                            "recorded-item",
                            InformationRequestRequirementType.FIELD
                        ).copy(occurrenceAnchorKey = "reported-item"),
                        requirement(
                            "recorded-detail",
                            InformationRequestRequirementType.FIELD
                        ).copy(occurrenceAnchorKey = "reported-detail"),
                    ),
                ),
            )

        validator.normalize(substituted(documentAnchor = null, substituteAnchor = null))
        validator.normalize(substituted(documentAnchor = "reported-item", substituteAnchor = "reported-item"))
        validator.normalize(substituted(documentAnchor = "reported-item", substituteAnchor = null))
        val fromGroupToTop = refusalFor(substituted(documentAnchor = null, substituteAnchor = "reported-item"))
        val fromParent = refusalFor(substituted(documentAnchor = "reported-detail", substituteAnchor = "reported-item"))

        listOf(fromGroupToTop, fromParent).forEach { refusal ->
            assertTrue(refusal.contains("supporting-record") && refusal.contains("alternative-record"), refusal)
        }
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
                sections = listOf(
                    section(
                        "collected-data",
                        requirement("recorded-note", InformationRequestRequirementType.FIELD).copy(
                            occurrenceAnchorKey = "reported-detail",
                        ),
                    ),
                ),
            ),
        )

        assertEquals(
            mapOf("reported-item" to null, "reported-detail" to "reported-item"),
            normalized.groups.associate { it.groupKey to it.parentGroupKey },
        )
    }

    private fun layeredConditions(layers: Int): InformationRequestTemplateConfigurationRequest {
        val root = requirement("layer-root", InformationRequestRequirementType.FIELD)
        val conditional = (0 until layers).flatMap { layer ->
            listOf("first", "second").map { position ->
                requirement("layer-$layer-$position", InformationRequestRequirementType.FIELD).copy(
                    requiredness = InformationRequestRequiredness.CONDITIONAL,
                    conditionalRuleKey = "layer-$layer-applies",
                )
            }
        }
        val rules = (0 until layers).map { layer ->
            if (layer == 0) rule("layer-0-applies", "layer-root")
            else rule("layer-$layer-applies", "layer-${layer - 1}-first", "layer-${layer - 1}-second")
        }
        return configuration(section("collected-data", root, *conditional.toTypedArray())).copy(conditionRules = rules)
    }

    private fun rule(key: String, vararg sourceRequirementKeys: String) =
        InformationRequestTemplateConditionRuleRequest(
            ruleKey = key,
            predicates = sourceRequirementKeys.map { source ->
                InformationRequestTemplateConditionPredicateRequest(
                    fieldDefinitionId = fieldOf(source),
                    valueType = FieldValueType.SHORT_TEXT,
                    operator = FieldOperator.IS_NOT_EMPTY,
                )
            },
        )

    private fun nestedGroups(
        levels: Int,
        keyStem: String,
        minimum: Int = 0,
        maximum: Int? = null,
    ): InformationRequestTemplateConfigurationRequest {
        val keys = (0 until levels).map { "$keyStem-$it" }
        return InformationRequestTemplateConfigurationRequest(
            groups = keys.mapIndexed { level, key ->
                InformationRequestTemplateGroupRequest(
                    groupKey = key,
                    parentGroupKey = keys.getOrNull(level - 1),
                    minOccurrences = minimum,
                    maxOccurrences = maximum,
                )
            },
            sections = listOf(
                section(
                    "collected-data",
                    requirement(
                        "recorded-note",
                        InformationRequestRequirementType.FIELD
                    ).copy(occurrenceAnchorKey = keys.last()),
                ),
            ),
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

    private fun boundedPolicy() = InformationRequestTemplateEvidencePolicyRequest(
        minimumFileCount = 1,
        maximumFileCount = 3,
        maximumFileSizeBytes = 1024,
        maximumTotalSizeBytes = 1024,
        minimumPageCount = 1,
        maximumPageCount = 1,
        issueDateRequirement = InformationRequestEvidenceAttributeRequirement.REQUIRED,
        expiryDateRequirement = InformationRequestEvidenceAttributeRequirement.REQUIRED,
        coveragePeriodRequirement = InformationRequestEvidenceAttributeRequirement.REQUIRED,
        maximumIssueAgeDays = 1,
        minimumRemainingValidityDays = 0,
        minimumCoverageDays = 1,
        coverageContinuityRequired = true,
    )

    private fun unboundedPolicy(case: String): InformationRequestTemplateEvidencePolicyRequest {
        val required = InformationRequestEvidenceAttributeRequirement.REQUIRED
        val optional = InformationRequestEvidenceAttributeRequirement.OPTIONAL
        return when (case) {
            "no-files" -> InformationRequestTemplateEvidencePolicyRequest(minimumFileCount = 0)
            "fewer-allowed" -> InformationRequestTemplateEvidencePolicyRequest(
                minimumFileCount = 2,
                maximumFileCount = 1
            )

            "empty-file" -> InformationRequestTemplateEvidencePolicyRequest(maximumFileSizeBytes = 0)
            "empty-total" -> InformationRequestTemplateEvidencePolicyRequest(maximumTotalSizeBytes = 0)
            "small-total" -> InformationRequestTemplateEvidencePolicyRequest(
                maximumFileSizeBytes = 2048,
                maximumTotalSizeBytes = 1024
            )

            "no-pages" -> InformationRequestTemplateEvidencePolicyRequest(minimumPageCount = 0)
            "empty-page-limit" -> InformationRequestTemplateEvidencePolicyRequest(maximumPageCount = 0)
            "fewer-pages" -> InformationRequestTemplateEvidencePolicyRequest(minimumPageCount = 3, maximumPageCount = 2)
            "short-age" -> InformationRequestTemplateEvidencePolicyRequest(
                maximumIssueAgeDays = 0,
                issueDateRequirement = required
            )

            "uncaptured-issue" -> InformationRequestTemplateEvidencePolicyRequest(
                maximumIssueAgeDays = 30,
                issueDateRequirement = optional
            )

            "negative-validity" ->
                InformationRequestTemplateEvidencePolicyRequest(
                    minimumRemainingValidityDays = -1,
                    expiryDateRequirement = required
                )

            "uncaptured-expiry" -> InformationRequestTemplateEvidencePolicyRequest(minimumRemainingValidityDays = 10)
            "short-coverage" -> InformationRequestTemplateEvidencePolicyRequest(
                minimumCoverageDays = 0,
                coveragePeriodRequirement = required
            )

            "uncaptured-coverage" ->
                InformationRequestTemplateEvidencePolicyRequest(
                    minimumCoverageDays = 30,
                    coveragePeriodRequirement = optional
                )

            else -> InformationRequestTemplateEvidencePolicyRequest(coverageContinuityRequired = true)
        }
    }
}
