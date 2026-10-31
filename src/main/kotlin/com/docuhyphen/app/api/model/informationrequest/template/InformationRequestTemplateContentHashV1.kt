package com.docuhyphen.app.api.model.informationrequest.template

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.fields.FieldOperator
import com.docuhyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.util.*

@Serializable
data class InformationRequestTemplateContentHashV1(
    @Serializable(with = UUIDSerializer::class) val schemaVersionId: UUID? = null,
    val sections: List<InformationRequestTemplateSectionHashV1> = emptyList(),
    val groups: List<InformationRequestTemplateGroupHashV1> = emptyList(),
    val conditionRules: List<InformationRequestTemplateConditionRuleHashV1> = emptyList(),
    val submissionMode: InformationRequestSubmissionMode = InformationRequestSubmissionMode.WHOLE_PACKAGE,
    val submissionStageOrdering: InformationRequestSubmissionStageOrdering =
        InformationRequestSubmissionStageOrdering.ANY_ORDER,
    val reviewStageOrdering: InformationRequestReviewStageOrdering = InformationRequestReviewStageOrdering.SEQUENTIAL,
    val reviewStages: List<InformationRequestTemplateReviewStageHashV1> = emptyList(),
    val factReusePurposeKey: String? = null,
)

@Serializable
data class InformationRequestTemplateReviewStageHashV1(
    val stageKey: String,
    val title: String,
    val aggregation: InformationRequestReviewAggregation = InformationRequestReviewAggregation.ANY,
    val quorumCount: Int? = null,
    val minimumReviewerCount: Int = 1,
    val tieResolution: InformationRequestReviewTieResolution = InformationRequestReviewTieResolution.MOST_SEVERE_OUTCOME,
    val overridePermitted: Boolean = false,
    val excludesResponseParties: Boolean = false,
    val excludesPriorReviewers: Boolean = false,
    val sectionKeys: List<String> = emptyList(),
)

@Serializable
data class InformationRequestTemplateConditionRuleHashV1(
    val ruleKey: String,
    val expressionVersion: Int = 1,
    val hiddenDataPolicy: InformationRequestConditionHiddenDataPolicy =
        InformationRequestConditionHiddenDataPolicy.RETAIN_SECURELY,
    val predicates: List<InformationRequestTemplateConditionPredicateHashV1> = emptyList(),
)

@Serializable
data class InformationRequestTemplateConditionPredicateHashV1(
    val sourceRequirementKey: String? = null,
    @Serializable(with = UUIDSerializer::class) val fieldDefinitionId: UUID? = null,
    val valueType: FieldValueType? = null,
    val operator: FieldOperator,
    val value: JsonElement? = null,
    val expectedDisposition: InformationRequestResponseDisposition? = null,
)

@Serializable
data class InformationRequestTemplateGroupHashV1(
    val groupKey: String,
    val parentGroupKey: String? = null,
    val minOccurrences: Int = 0,
    val maxOccurrences: Int? = null,
)

@Serializable
data class InformationRequestTemplateSectionHashV1(
    val sectionKey: String,
    val title: String,
    val helpText: String? = null,
    val requirements: List<InformationRequestTemplateRequirementHashV1> = emptyList(),
    val submissionStageKey: String? = null,
)

@Serializable
data class InformationRequestTemplateRequirementHashV1(
    val requirementKey: String,
    val requirementType: InformationRequestRequirementType,
    val prompt: String,
    val helpText: String? = null,
    val responseMode: InformationRequestResponseMode = InformationRequestResponseMode.PROVIDE,
    val requiredness: InformationRequestRequiredness = InformationRequestRequiredness.OPTIONAL,
    val contributorRole: InformationRequestContributorRole = InformationRequestContributorRole.CONTRIBUTOR,
    val reviewPolicy: InformationRequestReviewPolicy = InformationRequestReviewPolicy.NOT_REQUIRED,
    val confidentialityCompartmentKey: String? = null,
    val conditionalRuleKey: String? = null,
    val occurrenceAnchorKey: String? = null,
    @Serializable(with = UUIDSerializer::class) val collectedFieldDefinitionId: UUID? = null,
    val permittedDispositions: List<InformationRequestResponseDisposition> = emptyList(),
    val evidencePolicy: InformationRequestTemplateEvidencePolicyHashV1? = null,
    val substituteRequirementKeys: List<String> = emptyList(),
    val supportingEvidenceRequirementKeys: List<String> = emptyList(),
    val attestationPolicy: InformationRequestTemplateAttestationPolicyHashV1? = null,
)

@Serializable
data class InformationRequestTemplateAttestationPolicyHashV1(
    val requiredRoles: List<InformationRequestContributorRole> = emptyList(),
    val ordering: InformationRequestAttestationOrdering = InformationRequestAttestationOrdering.ANY_ORDER,
    val minimumAssentCount: Int? = null,
    val minimumAuthenticationStrength: InformationRequestAuthenticationStrength =
        InformationRequestAuthenticationStrength.VERIFIED_CONTACT,
    val validityHours: Int? = null,
    val externalSignatureReference: InformationRequestExternalSignatureReferencePolicy =
        InformationRequestExternalSignatureReferencePolicy.NOT_ACCEPTED,
)

@Serializable
data class InformationRequestTemplateEvidencePolicyHashV1(
    val minimumFileCount: Int = 1,
    val maximumFileCount: Int? = null,
    val maximumFileSizeBytes: Long? = null,
    val maximumTotalSizeBytes: Long? = null,
    val minimumPageCount: Int? = null,
    val maximumPageCount: Int? = null,
    val issuerRequirement: InformationRequestEvidenceAttributeRequirement =
        InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED,
    val jurisdictionRequirement: InformationRequestEvidenceAttributeRequirement =
        InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED,
    val languageRequirement: InformationRequestEvidenceAttributeRequirement =
        InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED,
    val issueDateRequirement: InformationRequestEvidenceAttributeRequirement =
        InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED,
    val expiryDateRequirement: InformationRequestEvidenceAttributeRequirement =
        InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED,
    val coveragePeriodRequirement: InformationRequestEvidenceAttributeRequirement =
        InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED,
    val certificationRequirement: InformationRequestEvidenceAttributeRequirement =
        InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED,
    val signatureRequirement: InformationRequestEvidenceAttributeRequirement =
        InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED,
    val maximumIssueAgeDays: Int? = null,
    val minimumRemainingValidityDays: Int? = null,
    val minimumCoverageDays: Int? = null,
    val coverageContinuityRequired: Boolean = false,
    val waiverPolicy: InformationRequestEvidenceWaiverPolicy =
        InformationRequestEvidenceWaiverPolicy.NOT_PERMITTED,
    val conformancePolicy: InformationRequestEvidenceConformancePolicy =
        InformationRequestEvidenceConformancePolicy.CONFORMANCE_REQUIRED,
    val acceptedValues: List<InformationRequestTemplateAcceptedValueHashV1> = emptyList(),
)

@Serializable
data class InformationRequestTemplateAcceptedValueHashV1(
    val attribute: InformationRequestEvidenceAttribute,
    val acceptedValue: String,
)
