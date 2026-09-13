package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateAcceptedValueRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConditionPredicateRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConditionRuleRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateEvidencePolicyRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateGroupRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementRequest
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateSectionRequest
import com.docuhyphen.app.api.model.entity.InformationRequestContributorRole
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceAttribute
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceAttributeRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceConformancePolicy
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceWaiverPolicy
import com.docuhyphen.app.api.model.entity.InformationRequestRequiredness
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.entity.InformationRequestResponseMode
import com.docuhyphen.app.api.model.entity.InformationRequestReviewPolicy
import com.docuhyphen.app.api.service.fields.FieldOperator
import java.util.UUID

data class InformationRequestTemplateWalkingSkeletonFixture(
    val fixtureKey: String,
    val templateKey: String,
    val fixtureContractVersion: Int,
    val configuration: InformationRequestTemplateConfigurationRequest,
    val expectedRequirementKeys: List<String>,
    val expectedCapabilities: List<InformationRequestCapability>,
)

object InformationRequestTemplateWalkingSkeletonFixtures
{
    fun basicFieldDocumentResponseAttestationRequest(
        schemaVersionId: UUID,
        recordedSummaryFieldId: UUID,
    ): InformationRequestTemplateWalkingSkeletonFixture
    {
        val configuration = InformationRequestTemplateConfigurationRequest(
            schemaVersionId = schemaVersionId,
            sections = listOf(
                InformationRequestTemplateSectionRequest(
                    sectionKey = "requested-data",
                    title = "Requested data",
                    helpText = "Information and evidence supplied for one response package",
                    requirements = listOf(
                        fieldRequirement(
                            key = "recorded-summary",
                            prompt = "Provide the recorded summary",
                            fieldId = recordedSummaryFieldId,
                            supportingEvidence = listOf("supporting-record"),
                        ),
                        documentRequirement(
                            key = "supporting-record",
                            prompt = "Provide the supporting record",
                            policy = basicEvidencePolicy(),
                        ),
                        attestationRequirement(
                            key = "response-confirmation",
                            prompt = "Confirm the response package",
                        ),
                    ),
                ),
            ),
        )

        return InformationRequestTemplateWalkingSkeletonFixture(
            fixtureKey = "basic_field_document_response_attestation_request",
            templateKey = "basic-field-document-response-attestation-request",
            fixtureContractVersion = CURRENT_FIXTURE_CONTRACT_VERSION,
            configuration = configuration,
            expectedRequirementKeys = listOf(
                "recorded-summary",
                "supporting-record",
                "response-confirmation",
            ),
            expectedCapabilities = listOf(
                InformationRequestCapability.STRUCTURED_RESPONSE,
                InformationRequestCapability.DOCUMENT_EVIDENCE,
                InformationRequestCapability.RESPONSE_ATTESTATION,
                InformationRequestCapability.SUPPORTING_EVIDENCE,
                InformationRequestCapability.RESPONSE_SUBMISSION,
            ),
        )
    }

    fun multiPartyStagedEvidenceRequest(
        schemaVersionId: UUID,
        subjectStatusFieldId: UUID,
        delegateNoteFieldId: UUID,
    ): InformationRequestTemplateWalkingSkeletonFixture
    {
        val configuration = InformationRequestTemplateConfigurationRequest(
            schemaVersionId = schemaVersionId,
            groups = listOf(
                InformationRequestTemplateGroupRequest(
                    groupKey = "reported-item",
                    maxOccurrences = 3,
                ),
            ),
            conditionRules = listOf(
                InformationRequestTemplateConditionRuleRequest(
                    ruleKey = "when-subject-is-active",
                    predicates = listOf(
                        InformationRequestTemplateConditionPredicateRequest(
                            sourceRequirementKey = "delegate-note",
                            operator = FieldOperator.IS_NOT_EMPTY,
                        ),
                    ),
                ),
            ),
            sections = listOf(
                InformationRequestTemplateSectionRequest(
                    sectionKey = "participant-responses",
                    title = "Participant responses",
                    helpText = "Responses gathered from the nominated parties",
                    requirements = listOf(
                        fieldRequirement(
                            key = "subject-status",
                            prompt = "Provide the subject status",
                            fieldId = subjectStatusFieldId,
                            contributorRole = InformationRequestContributorRole.SUBJECT,
                            requiredness = InformationRequestRequiredness.CONDITIONAL,
                            conditionalRuleKey = "when-subject-is-active",
                            occurrenceAnchorKey = "reported-item",
                            reviewPolicy = InformationRequestReviewPolicy.REQUIRED,
                            confidentialityCompartmentKey = "restricted-response",
                            permittedDispositions = listOf(
                                InformationRequestResponseDisposition.PROVIDED,
                                InformationRequestResponseDisposition.PARTIALLY_PROVIDED,
                                InformationRequestResponseDisposition.EXCEPTION_REQUESTED,
                            ),
                            supportingEvidence = listOf("primary-evidence-record"),
                        ),
                        fieldRequirement(
                            key = "delegate-note",
                            prompt = "Provide the delegated note",
                            fieldId = delegateNoteFieldId,
                            contributorRole = InformationRequestContributorRole.PREPARER,
                            occurrenceAnchorKey = "reported-item",
                            permittedDispositions = listOf(
                                InformationRequestResponseDisposition.PROVIDED,
                                InformationRequestResponseDisposition.NOT_APPLICABLE,
                            ),
                        ),
                    ),
                ),
                InformationRequestTemplateSectionRequest(
                    sectionKey = "evidence-package",
                    title = "Evidence package",
                    requirements = listOf(
                        documentRequirement(
                            key = "primary-evidence-record",
                            prompt = "Provide the primary evidence record",
                            permittedDispositions = listOf(
                                InformationRequestResponseDisposition.PROVIDED,
                                InformationRequestResponseDisposition.WAIVED,
                            ),
                            substitutes = listOf("alternate-evidence-record"),
                            policy = reviewableEvidencePolicy(),
                        ),
                        documentRequirement(
                            key = "alternate-evidence-record",
                            prompt = "Provide the alternate evidence record",
                            policy = basicEvidencePolicy(),
                        ),
                    ),
                ),
                InformationRequestTemplateSectionRequest(
                    sectionKey = "response-confirmations",
                    title = "Response confirmations",
                    requirements = listOf(
                        attestationRequirement(
                            key = "submitter-attestation",
                            prompt = "Confirm the submitted response package",
                            contributorRole = InformationRequestContributorRole.ATTESTOR,
                            reviewPolicy = InformationRequestReviewPolicy.REQUIRED_ON_EXCEPTION,
                            permittedDispositions = listOf(
                                InformationRequestResponseDisposition.PROVIDED,
                                InformationRequestResponseDisposition.EXCEPTION_REQUESTED,
                            ),
                        ),
                    ),
                ),
            ),
        )

        return InformationRequestTemplateWalkingSkeletonFixture(
            fixtureKey = "multi_party_staged_evidence_request",
            templateKey = "multi-party-staged-evidence-request",
            fixtureContractVersion = CURRENT_FIXTURE_CONTRACT_VERSION,
            configuration = configuration,
            expectedRequirementKeys = listOf(
                "subject-status",
                "delegate-note",
                "primary-evidence-record",
                "alternate-evidence-record",
                "submitter-attestation",
            ),
            expectedCapabilities = InformationRequestCapability.entries.toList(),
        )
    }

    private fun fieldRequirement(
        key: String,
        prompt: String,
        fieldId: UUID,
        contributorRole: InformationRequestContributorRole = InformationRequestContributorRole.CONTRIBUTOR,
        requiredness: InformationRequestRequiredness = InformationRequestRequiredness.REQUIRED,
        conditionalRuleKey: String? = null,
        occurrenceAnchorKey: String? = null,
        reviewPolicy: InformationRequestReviewPolicy = InformationRequestReviewPolicy.NOT_REQUIRED,
        confidentialityCompartmentKey: String? = null,
        permittedDispositions: List<InformationRequestResponseDisposition> =
            listOf(InformationRequestResponseDisposition.PROVIDED),
        supportingEvidence: List<String> = emptyList(),
    ) = InformationRequestTemplateRequirementRequest(
        requirementKey = key,
        requirementType = InformationRequestRequirementType.FIELD,
        prompt = prompt,
        responseMode = InformationRequestResponseMode.PROVIDE,
        requiredness = requiredness,
        contributorRole = contributorRole,
        reviewPolicy = reviewPolicy,
        confidentialityCompartmentKey = confidentialityCompartmentKey,
        conditionalRuleKey = conditionalRuleKey,
        occurrenceAnchorKey = occurrenceAnchorKey,
        collectedFieldDefinitionId = fieldId,
        permittedDispositions = permittedDispositions,
        supportingEvidenceRequirementKeys = supportingEvidence,
    )

    private fun documentRequirement(
        key: String,
        prompt: String,
        permittedDispositions: List<InformationRequestResponseDisposition> =
            listOf(InformationRequestResponseDisposition.PROVIDED),
        substitutes: List<String> = emptyList(),
        policy: InformationRequestTemplateEvidencePolicyRequest,
    ) = InformationRequestTemplateRequirementRequest(
        requirementKey = key,
        requirementType = InformationRequestRequirementType.DOCUMENT,
        prompt = prompt,
        responseMode = InformationRequestResponseMode.PROVIDE,
        requiredness = InformationRequestRequiredness.REQUIRED,
        permittedDispositions = permittedDispositions,
        evidencePolicy = policy,
        substituteRequirementKeys = substitutes,
    )

    private fun attestationRequirement(
        key: String,
        prompt: String,
        contributorRole: InformationRequestContributorRole = InformationRequestContributorRole.ATTESTOR,
        reviewPolicy: InformationRequestReviewPolicy = InformationRequestReviewPolicy.NOT_REQUIRED,
        permittedDispositions: List<InformationRequestResponseDisposition> =
            listOf(InformationRequestResponseDisposition.PROVIDED),
    ) = InformationRequestTemplateRequirementRequest(
        requirementKey = key,
        requirementType = InformationRequestRequirementType.RESPONSE_ATTESTATION,
        prompt = prompt,
        responseMode = InformationRequestResponseMode.PROVIDE_ONCE,
        requiredness = InformationRequestRequiredness.REQUIRED,
        contributorRole = contributorRole,
        reviewPolicy = reviewPolicy,
        permittedDispositions = permittedDispositions,
    )

    private fun basicEvidencePolicy() = InformationRequestTemplateEvidencePolicyRequest(
        minimumFileCount = 1,
        maximumFileCount = 2,
        maximumFileSizeBytes = 1_000_000,
        maximumTotalSizeBytes = 2_000_000,
        acceptedValues = listOf(
            InformationRequestTemplateAcceptedValueRequest(
                InformationRequestEvidenceAttribute.CONTENT_TYPE,
                "application/pdf",
            ),
        ),
    )

    private fun reviewableEvidencePolicy() = InformationRequestTemplateEvidencePolicyRequest(
        minimumFileCount = 2,
        maximumFileCount = 5,
        maximumFileSizeBytes = 2_000_000,
        maximumTotalSizeBytes = 6_000_000,
        minimumPageCount = 1,
        maximumPageCount = 80,
        issuerRequirement = InformationRequestEvidenceAttributeRequirement.REQUIRED,
        jurisdictionRequirement = InformationRequestEvidenceAttributeRequirement.OPTIONAL,
        languageRequirement = InformationRequestEvidenceAttributeRequirement.REQUIRED,
        issueDateRequirement = InformationRequestEvidenceAttributeRequirement.REQUIRED,
        expiryDateRequirement = InformationRequestEvidenceAttributeRequirement.REQUIRED,
        coveragePeriodRequirement = InformationRequestEvidenceAttributeRequirement.REQUIRED,
        certificationRequirement = InformationRequestEvidenceAttributeRequirement.REQUIRED,
        signatureRequirement = InformationRequestEvidenceAttributeRequirement.OPTIONAL,
        maximumIssueAgeDays = 120,
        minimumRemainingValidityDays = 15,
        minimumCoverageDays = 30,
        coverageContinuityRequired = true,
        waiverPolicy = InformationRequestEvidenceWaiverPolicy.REVIEW_APPROVAL_REQUIRED,
        conformancePolicy = InformationRequestEvidenceConformancePolicy.DEFICIENCY_REVIEWABLE,
        acceptedValues = listOf(
            InformationRequestTemplateAcceptedValueRequest(
                InformationRequestEvidenceAttribute.CONTENT_TYPE,
                "application/pdf",
            ),
            InformationRequestTemplateAcceptedValueRequest(
                InformationRequestEvidenceAttribute.ISSUER,
                "recording-party",
            ),
            InformationRequestTemplateAcceptedValueRequest(
                InformationRequestEvidenceAttribute.LANGUAGE,
                "process-language",
            ),
        ),
    )

    private const val CURRENT_FIXTURE_CONTRACT_VERSION = 1
}
