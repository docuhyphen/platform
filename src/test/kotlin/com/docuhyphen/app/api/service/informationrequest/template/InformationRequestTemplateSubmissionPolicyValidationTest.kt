package com.docuhyphen.app.api.service.informationrequest.template

import com.docuhyphen.app.api.model.dto.*
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.fields.FieldOperator
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import java.util.*

class InformationRequestTemplateSubmissionPolicyValidationTest
{
    private val validator = InformationRequestTemplateConfigurationValidator()

    @ParameterizedTest
    @EnumSource(InformationRequestResponseMode::class)
    fun `a subject identity cannot be nominated as a Requirement contributor`(mode: InformationRequestResponseMode) {
        val requirement = document("supporting-record").copy(
            contributorRole = InformationRequestContributorRole.SUBJECT,
            responseMode = mode,
            requiredness = InformationRequestRequiredness.OPTIONAL,
        )

        val refusal = assertThrows<InformationRequestTemplateValidationException> {
            validator.normalize(configuration(section("records", requirement)))
        }

        assertEquals("records", refusal.sectionKey)
        assertEquals("supporting-record", refusal.requirementKey)
        assertTrue(refusal.message.contains("SUBJECT"))
    }

    @ParameterizedTest
    @EnumSource(value = InformationRequestRequirementType::class, names = ["FIELD", "DOCUMENT"])
    fun `the attestor role cannot answer a Requirement that asks for typed data or files`(type: InformationRequestRequirementType) {
        val requirement = document("supporting-record").copy(
            requirementType = type,
            contributorRole = InformationRequestContributorRole.ATTESTOR,
            evidencePolicy = InformationRequestTemplateEvidencePolicyRequest().takeIf { type == InformationRequestRequirementType.DOCUMENT },
            collectedFieldDefinitionId = UUID.randomUUID().takeIf { type == InformationRequestRequirementType.FIELD },
        )

        val refusal = assertThrows<InformationRequestTemplateValidationException> {
            validator.normalize(configuration(section("records", requirement)))
        }

        assertEquals("records", refusal.sectionKey)
        assertEquals("supporting-record", refusal.requirementKey)
        assertTrue(refusal.message.contains("ATTESTOR"))
    }

    @Test
    fun `the attestor role still answers a response attestation`() {
        validator.normalize(configuration(section("confirmations", attestation("recorded-assertion"))))
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `confirmation roles cannot include a subject identity even alongside acting roles`(mixed: Boolean) {
        val roles = if (mixed)
            listOf(InformationRequestContributorRole.ATTESTOR, InformationRequestContributorRole.SUBJECT)
        else
            listOf(InformationRequestContributorRole.SUBJECT)
        val requirement = attestation("recorded-assertion").copy(
            attestationPolicy = InformationRequestTemplateAttestationPolicyRequest(requiredRoles = roles),
        )

        val refusal = assertThrows<InformationRequestTemplateValidationException> {
            validator.normalize(configuration(section("confirmations", requirement)))
        }

        assertEquals("confirmations", refusal.sectionKey)
        assertEquals("recorded-assertion", refusal.requirementKey)
        assertTrue(refusal.message.contains("SUBJECT"))
    }

    @ParameterizedTest
    @EnumSource(value = InformationRequestContributorRole::class, names = ["CONTRIBUTOR", "PREPARER"])
    fun `answering roles can provide documents and confirm recorded information`(role: InformationRequestContributorRole) {
        val normalized = validator.normalize(
            configuration(
                section("records", document("supporting-record").copy(contributorRole = role)),
                section("confirmations", attestation("recorded-assertion").copy(contributorRole = role)),
            )
        )

        assertEquals(role, normalized.sections.first().requirements.single().contributorRole)
        assertEquals(listOf(role), normalized.sections.last().requirements.single().attestationPolicy!!.requiredRoles)
    }

    @Test
    fun `a whole-package version names no stage and has no stage order`()
    {
        val normalized = validator.normalize(configuration(section("records", document("supporting-record"))))
        assertEquals(InformationRequestSubmissionMode.WHOLE_PACKAGE, normalized.submissionMode)
        assertNull(normalized.sections.single().submissionStageKey)

        val stageNamed = assertThrows<InformationRequestTemplateValidationException> {
            validator.normalize(configuration(section("records", document("supporting-record"), stage = "first-stage")))
        }
        assertEquals("records", stageNamed.sectionKey)

        assertThrows<InformationRequestTemplateValidationException> {
            validator.normalize(
                configuration(
                    section("records", document("supporting-record")),
                    ordering = InformationRequestSubmissionStageOrdering.SEQUENTIAL,
                ),
            )
        }
    }

    @Test
    fun `a staged version places every section in a stage named by a machine key`()
    {
        val normalized = validator.normalize(
            configuration(
                section("records", document("supporting-record"), stage = "  Record-Stage "),
                section("confirmations", attestation("recorded-assertion"), stage = "confirmation-stage"),
                mode = InformationRequestSubmissionMode.STAGED,
            ),
        )
        assertEquals(listOf("record-stage", "confirmation-stage"), normalized.sections.map { it.submissionStageKey })

        val unstaged = assertThrows<InformationRequestTemplateValidationException> {
            validator.normalize(
                configuration(
                    section("records", document("supporting-record"), stage = "record-stage"),
                    section("confirmations", attestation("recorded-assertion")),
                    mode = InformationRequestSubmissionMode.STAGED,
                ),
            )
        }
        assertEquals("confirmations", unstaged.sectionKey)

        assertThrows<InformationRequestTemplateValidationException> {
            validator.normalize(
                configuration(
                    section("records", document("supporting-record"), stage = "record stage"),
                    mode = InformationRequestSubmissionMode.STAGED,
                ),
            )
        }
    }

    @Test
    fun `a condition only reads answers from its own stage, whatever the stage order`()
    {
        val rule = InformationRequestTemplateConditionRuleRequest(
            ruleKey = "when-note-applies",
            predicates = listOf(
                InformationRequestTemplateConditionPredicateRequest(
                    sourceRequirementKey = "supporting-record",
                    operator = FieldOperator.IS_NOT_EMPTY,
                ),
            ),
        )
        val conditional = attestation("recorded-assertion").copy(
            requiredness = InformationRequestRequiredness.CONDITIONAL,
            conditionalRuleKey = "when-note-applies",
        )

        val acrossAnyOrder = assertThrows<InformationRequestTemplateValidationException> {
            validator.normalize(
                configuration(
                    section("records", document("supporting-record"), stage = "record-stage"),
                    section("confirmations", conditional, stage = "confirmation-stage"),
                    mode = InformationRequestSubmissionMode.STAGED,
                    rules = listOf(rule),
                ),
            )
        }
        assertEquals("recorded-assertion", acrossAnyOrder.requirementKey)

        val earlierSequential = assertThrows<InformationRequestTemplateValidationException> {
            validator.normalize(
                configuration(
                    section("records", document("supporting-record"), stage = "record-stage"),
                    section("confirmations", conditional, stage = "confirmation-stage"),
                    mode = InformationRequestSubmissionMode.STAGED,
                    ordering = InformationRequestSubmissionStageOrdering.SEQUENTIAL,
                    rules = listOf(rule),
                ),
            )
        }
        assertEquals("recorded-assertion", earlierSequential.requirementKey)

        val sameStage = validator.normalize(
            configuration(
                section("records", document("supporting-record"), conditional, stage = "record-stage"),
                section("confirmations", attestation("closing-assertion"), stage = "confirmation-stage"),
                mode = InformationRequestSubmissionMode.STAGED,
                ordering = InformationRequestSubmissionStageOrdering.SEQUENTIAL,
                rules = listOf(rule),
            ),
        )
        assertEquals(listOf("record-stage", "confirmation-stage"), sameStage.sections.map { it.submissionStageKey })

        assertThrows<InformationRequestTemplateValidationException> {
            validator.normalize(
                configuration(
                    section("confirmations", conditional, stage = "confirmation-stage"),
                    section("records", document("supporting-record"), stage = "record-stage"),
                    mode = InformationRequestSubmissionMode.STAGED,
                    ordering = InformationRequestSubmissionStageOrdering.SEQUENTIAL,
                    rules = listOf(rule),
                ),
            )
        }
    }

    @Test
    fun `every requirement answered once per a group and its nested groups is submitted in one stage`()
    {
        val groups = listOf(
            InformationRequestTemplateGroupRequest(groupKey = "recorded-item"),
            InformationRequestTemplateGroupRequest(groupKey = "item-detail", parentGroupKey = "recorded-item"),
        )
        val refusal = assertThrows<InformationRequestTemplateValidationException> {
            validator.normalize(
                configuration(
                    section("records", document("supporting-record").copy(occurrenceAnchorKey = "recorded-item"), stage = "record-stage"),
                    section(
                        "details",
                        document("detail-record").copy(occurrenceAnchorKey = "item-detail"),
                        stage = "detail-stage",
                    ),
                    mode = InformationRequestSubmissionMode.STAGED,
                    groups = groups,
                ),
            )
        }
        assertEquals("recorded-item", refusal.groupKey)

        val sameStage = validator.normalize(
            configuration(
                section("records", document("supporting-record").copy(occurrenceAnchorKey = "recorded-item"), stage = "record-stage"),
                section("details", document("detail-record").copy(occurrenceAnchorKey = "item-detail"), stage = "record-stage"),
                mode = InformationRequestSubmissionMode.STAGED,
                groups = groups,
            ),
        )
        assertEquals(setOf("record-stage"), sameStage.sections.mapNotNull { it.submissionStageKey }.toSet())
    }

    @Test
    fun `a staged document is completed only by an alternative answered in its own stage`() {
        val primary = document("supporting-record").copy(substituteRequirementKeys = listOf("alternate-record"))
        val alternate = document("alternate-record").copy(requiredness = InformationRequestRequiredness.OPTIONAL)
        val supported =
            attestation("recorded-assertion").copy(supportingEvidenceRequirementKeys = listOf("alternate-record"))

        val substitute = assertThrows<InformationRequestTemplateValidationException> {
            validator.normalize(
                configuration(
                    section("records", primary, stage = "record-stage"),
                    section("alternates", alternate, stage = "alternate-stage"),
                    mode = InformationRequestSubmissionMode.STAGED,
                ),
            )
        }
        assertEquals("supporting-record", substitute.requirementKey)
        assertTrue(substitute.message.contains("alternate-stage"), substitute.message)

        val supportedAcross = validator.normalize(
            configuration(
                section("confirmations", supported, stage = "confirmation-stage"),
                section("alternates", alternate, stage = "alternate-stage"),
                mode = InformationRequestSubmissionMode.STAGED,
            ),
        )
        assertEquals(
            listOf("alternate-record"),
            supportedAcross.sections.first().requirements.single().supportingEvidenceRequirementKeys,
        )

        val sameStage = validator.normalize(
            configuration(
                section("records", primary, supported, stage = "record-stage"),
                section("alternates", alternate, stage = "record-stage"),
                section("closing", attestation("closing-assertion"), stage = "closing-stage"),
                mode = InformationRequestSubmissionMode.STAGED,
            ),
        )
        assertEquals(
            listOf("alternate-record"),
            sameStage.sections.first().requirements.first().substituteRequirementKeys
        )

        val wholePackage = validator.normalize(
            configuration(section("records", primary, supported), section("alternates", alternate)),
        )
        assertEquals(
            listOf("alternate-record"),
            wholePackage.sections.first().requirements.last().supportingEvidenceRequirementKeys,
        )
    }

    @Test
    fun `an assertion with no stated policy needs one assent from its nominated role`()
    {
        val normalized = validator.normalize(configuration(section("confirmations", attestation("recorded-assertion"))))
        val policy = normalized.sections.single().requirements.single().attestationPolicy
            ?: error("An answerable assertion states its attestation policy")

        assertEquals(listOf(InformationRequestContributorRole.ATTESTOR), policy.requiredRoles)
        assertEquals(InformationRequestAttestationOrdering.ANY_ORDER, policy.ordering)
        assertEquals(1, policy.minimumAssentCount)
        assertEquals(InformationRequestAuthenticationStrength.VERIFIED_CONTACT, policy.minimumAuthenticationStrength)
        assertNull(policy.validityHours)
        assertEquals(InformationRequestExternalSignatureReferencePolicy.NOT_ACCEPTED, policy.externalSignatureReference)
    }

    @Test
    fun `a stated attestation policy names distinct roles and needs an assent from each`()
    {
        val stated = validator.normalize(
            configuration(
                section(
                    "confirmations",
                    attestation("recorded-assertion").copy(
                        attestationPolicy = InformationRequestTemplateAttestationPolicyRequest(
                            requiredRoles = listOf(
                                InformationRequestContributorRole.PREPARER,
                                InformationRequestContributorRole.ATTESTOR,
                            ),
                            ordering = InformationRequestAttestationOrdering.ROLE_SEQUENCE,
                            minimumAuthenticationStrength = InformationRequestAuthenticationStrength.ACCOUNT_SIGN_IN,
                            validityHours = 48,
                            externalSignatureReference = InformationRequestExternalSignatureReferencePolicy.OPTIONAL,
                        ),
                    ),
                ),
            ),
        ).sections.single().requirements.single().attestationPolicy!!
        assertEquals(2, stated.minimumAssentCount)
        assertEquals(InformationRequestAttestationOrdering.ROLE_SEQUENCE, stated.ordering)

        listOf(
            InformationRequestTemplateAttestationPolicyRequest(
                requiredRoles = listOf(InformationRequestContributorRole.ATTESTOR, InformationRequestContributorRole.ATTESTOR),
            ),
            InformationRequestTemplateAttestationPolicyRequest(
                requiredRoles = listOf(InformationRequestContributorRole.PREPARER, InformationRequestContributorRole.ATTESTOR),
                minimumAssentCount = 1,
            ),
            InformationRequestTemplateAttestationPolicyRequest(validityHours = 0),
        ).forEach { policy ->
            val refusal = assertThrows<InformationRequestTemplateValidationException> {
                validator.normalize(
                    configuration(section("confirmations", attestation("recorded-assertion").copy(attestationPolicy = policy))),
                )
            }
            assertEquals("recorded-assertion", refusal.requirementKey)
        }
    }

    @Test
    fun `only an answerable assertion states an attestation policy`()
    {
        val onDocument = assertThrows<InformationRequestTemplateValidationException> {
            validator.normalize(
                configuration(
                    section(
                        "records",
                        document("supporting-record").copy(attestationPolicy = InformationRequestTemplateAttestationPolicyRequest()),
                    ),
                ),
            )
        }
        assertEquals("supporting-record", onDocument.requirementKey)

        val viewOnly = attestation("recorded-assertion").copy(
            responseMode = InformationRequestResponseMode.VIEW_ONLY,
            requiredness = InformationRequestRequiredness.OPTIONAL,
            permittedDispositions = emptyList(),
        )
        assertNull(
            validator.normalize(configuration(section("confirmations", viewOnly)))
                .sections.single().requirements.single().attestationPolicy,
        )
        assertTrue(
            assertThrows<InformationRequestTemplateValidationException> {
                validator.normalize(
                    configuration(
                        section(
                            "confirmations",
                            viewOnly.copy(attestationPolicy = InformationRequestTemplateAttestationPolicyRequest()),
                        ),
                    ),
                )
            }.message.contains("recorded-assertion"),
        )
    }

    private fun configuration(
        vararg sections: InformationRequestTemplateSectionRequest,
        mode: InformationRequestSubmissionMode = InformationRequestSubmissionMode.WHOLE_PACKAGE,
        ordering: InformationRequestSubmissionStageOrdering = InformationRequestSubmissionStageOrdering.ANY_ORDER,
        rules: List<InformationRequestTemplateConditionRuleRequest> = emptyList(),
        groups: List<InformationRequestTemplateGroupRequest> = emptyList(),
    ) = InformationRequestTemplateConfigurationRequest(
        sections = sections.toList(),
        groups = groups,
        conditionRules = rules,
        submissionMode = mode,
        submissionStageOrdering = ordering,
    )

    private fun section(
        key: String,
        vararg requirements: InformationRequestTemplateRequirementRequest,
        stage: String? = null,
    ) = InformationRequestTemplateSectionRequest(
        sectionKey = key,
        title = "Recorded items",
        requirements = requirements.toList(),
        submissionStageKey = stage,
    )

    private fun document(key: String) = InformationRequestTemplateRequirementRequest(
        requirementKey = key,
        requirementType = InformationRequestRequirementType.DOCUMENT,
        prompt = "Provide the recorded item",
        requiredness = InformationRequestRequiredness.REQUIRED,
        evidencePolicy = InformationRequestTemplateEvidencePolicyRequest(),
    )

    private fun attestation(key: String) = InformationRequestTemplateRequirementRequest(
        requirementKey = key,
        requirementType = InformationRequestRequirementType.RESPONSE_ATTESTATION,
        prompt = "Confirm the recorded items",
        responseMode = InformationRequestResponseMode.PROVIDE_ONCE,
        requiredness = InformationRequestRequiredness.REQUIRED,
        contributorRole = InformationRequestContributorRole.ATTESTOR,
    )
}
