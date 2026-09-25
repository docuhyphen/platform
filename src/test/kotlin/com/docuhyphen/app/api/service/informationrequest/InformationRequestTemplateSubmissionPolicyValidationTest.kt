package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateAttestationPolicyRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConditionPredicateRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConditionRuleRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateEvidencePolicyRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateGroupRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateSectionRequest
import com.docuhyphen.app.api.model.entity.InformationRequestAttestationOrdering
import com.docuhyphen.app.api.model.entity.InformationRequestAuthenticationStrength
import com.docuhyphen.app.api.model.entity.InformationRequestContributorRole
import com.docuhyphen.app.api.model.entity.InformationRequestExternalSignatureReferencePolicy
import com.docuhyphen.app.api.model.entity.InformationRequestRequiredness
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.entity.InformationRequestResponseMode
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionMode
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionStageOrdering
import com.docuhyphen.app.api.service.fields.FieldOperator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class InformationRequestTemplateSubmissionPolicyValidationTest
{
    private val validator = InformationRequestTemplateConfigurationValidator()

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
    fun `a condition only reads answers its conditional requirement's stage cannot outlive`()
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

        val earlierSequential = validator.normalize(
            configuration(
                section("records", document("supporting-record"), stage = "record-stage"),
                section("confirmations", conditional, stage = "confirmation-stage"),
                mode = InformationRequestSubmissionMode.STAGED,
                ordering = InformationRequestSubmissionStageOrdering.SEQUENTIAL,
                rules = listOf(rule),
            ),
        )
        assertEquals(2, earlierSequential.sections.size)

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
