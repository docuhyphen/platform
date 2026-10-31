package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.fields.FieldOperator
import com.docuhyphen.app.api.model.informationrequest.capability.InformationRequestCapability
import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.sql.Timestamp
import java.util.*

@Serializable
data class InformationRequestTemplateDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val scopeKind: InformationRequestTemplateScopeKind,
    @Serializable(with = UUIDSerializer::class) val scopeOrgId: UUID? = null,
    @Serializable(with = UUIDSerializer::class) val scopeUserId: UUID? = null,
    val namespace: String,
    val templateKey: String,
    val displayName: String,
    val description: String? = null,
    val status: InformationRequestTemplateStatus,
    val draftVersion: InformationRequestTemplateVersionDto? = null,
    val draftETag: String? = null,
    val latestPublishedVersion: InformationRequestTemplateVersionDto? = null,
    @Serializable(with = TimestampSerializer::class) val createdAt: Timestamp,
    @Serializable(with = TimestampSerializer::class) val updatedAt: Timestamp,
    val versions: List<InformationRequestTemplateVersionStandingDto> = emptyList(),
)

@Serializable
data class InformationRequestTemplateVersionStandingDto(
    val versionNumber: Int,
    val status: InformationRequestTemplateStatus,
    @Serializable(with = TimestampSerializer::class) val publishedAt: Timestamp? = null,
    @Serializable(with = TimestampSerializer::class) val retiredAt: Timestamp? = null,
)

@Serializable
data class InformationRequestTemplateSummaryDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val scopeKind: InformationRequestTemplateScopeKind,
    @Serializable(with = UUIDSerializer::class) val scopeOrgId: UUID? = null,
    @Serializable(with = UUIDSerializer::class) val scopeUserId: UUID? = null,
    val namespace: String,
    val templateKey: String,
    val displayName: String,
    val description: String? = null,
    val status: InformationRequestTemplateStatus,
    val hasEditableVersion: Boolean,
    val latestPublishedVersionNumber: Int? = null,
    @Serializable(with = UUIDSerializer::class) val latestPublishedVersionId: UUID? = null,
    @Serializable(with = TimestampSerializer::class) val createdAt: Timestamp,
    @Serializable(with = TimestampSerializer::class) val updatedAt: Timestamp,
)

@Serializable
data class InformationRequestTemplateVersionDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    @Serializable(with = UUIDSerializer::class) val templateDefinitionId: UUID,
    val versionNumber: Int,
    val status: InformationRequestTemplateStatus,
    @Serializable(with = UUIDSerializer::class) val schemaVersionId: UUID? = null,
    val submissionMode: InformationRequestSubmissionMode = InformationRequestSubmissionMode.WHOLE_PACKAGE,
    val submissionStageOrdering: InformationRequestSubmissionStageOrdering =
        InformationRequestSubmissionStageOrdering.ANY_ORDER,
    val reviewStageOrdering: InformationRequestReviewStageOrdering = InformationRequestReviewStageOrdering.SEQUENTIAL,
    val reviewStages: List<InformationRequestTemplateReviewStageDto> = emptyList(),
    val factReusePurposeKey: String? = null,
    val sections: List<InformationRequestTemplateSectionDto> = emptyList(),
    val groups: List<InformationRequestTemplateGroupDto> = emptyList(),
    val conditionRules: List<InformationRequestTemplateConditionRuleDto> = emptyList(),
    val requiredCapabilities: List<InformationRequestTemplateCapabilityDto> = emptyList(),
    val contentHashSha256: String? = null,
    @Serializable(with = TimestampSerializer::class) val publishedAt: Timestamp? = null,
    @Serializable(with = TimestampSerializer::class) val retiredAt: Timestamp? = null,
    @Serializable(with = TimestampSerializer::class) val createdAt: Timestamp,
)

@Serializable
data class InformationRequestTemplateGroupDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val groupKey: String,
    val parentGroupKey: String? = null,
    val minOccurrences: Int,
    val maxOccurrences: Int? = null,
)

@Serializable
data class InformationRequestTemplateConditionRuleDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val ruleKey: String,
    val expressionVersion: Int,
    val hiddenDataPolicy: InformationRequestConditionHiddenDataPolicy =
        InformationRequestConditionHiddenDataPolicy.RETAIN_SECURELY,
    val predicates: List<InformationRequestTemplateConditionPredicateDto> = emptyList(),
)

@Serializable
data class InformationRequestTemplateConditionPredicateDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val sourceRequirementKey: String? = null,
    @Serializable(with = UUIDSerializer::class) val fieldDefinitionId: UUID? = null,
    val valueType: FieldValueType? = null,
    val operator: FieldOperator,
    val value: JsonElement? = null,
    val expectedDisposition: InformationRequestResponseDisposition? = null,
)

@Serializable
data class InformationRequestTemplateSectionDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val sectionKey: String,
    val title: String,
    val helpText: String? = null,
    val submissionStageKey: String? = null,
    val requirements: List<InformationRequestTemplateRequirementDto> = emptyList(),
)

@Serializable
data class InformationRequestTemplateRequirementDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    @Serializable(with = UUIDSerializer::class) val templateRequirementId: UUID,
    val requirementKey: String,
    val requirementType: InformationRequestRequirementType,
    val prompt: String,
    val helpText: String? = null,
    val responseMode: InformationRequestResponseMode,
    val requiredness: InformationRequestRequiredness,
    val contributorRole: InformationRequestContributorRole,
    val reviewPolicy: InformationRequestReviewPolicy,
    val confidentialityCompartmentKey: String? = null,
    val conditionalRuleKey: String? = null,
    val occurrenceAnchorKey: String? = null,
    @Serializable(with = UUIDSerializer::class) val collectedFieldDefinitionId: UUID? = null,
    val permittedDispositions: List<InformationRequestResponseDisposition> = emptyList(),
    val evidencePolicy: InformationRequestTemplateEvidencePolicyDto? = null,
    val substituteRequirementKeys: List<String> = emptyList(),
    val supportingEvidenceRequirementKeys: List<String> = emptyList(),
    val attestationPolicy: InformationRequestTemplateAttestationPolicyDto? = null,
)

@Serializable
data class InformationRequestTemplateReviewStageDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val stageKey: String,
    val title: String,
    val aggregation: InformationRequestReviewAggregation,
    val quorumCount: Int? = null,
    val minimumReviewerCount: Int,
    val tieResolution: InformationRequestReviewTieResolution,
    val overridePermitted: Boolean,
    val excludesResponseParties: Boolean,
    val excludesPriorReviewers: Boolean,
    val sectionKeys: List<String> = emptyList(),
)

@Serializable
data class InformationRequestTemplateAttestationPolicyDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val requiredRoles: List<InformationRequestContributorRole>,
    val ordering: InformationRequestAttestationOrdering,
    val minimumAssentCount: Int,
    val minimumAuthenticationStrength: InformationRequestAuthenticationStrength,
    val validityHours: Int? = null,
    val externalSignatureReference: InformationRequestExternalSignatureReferencePolicy,
)

@Serializable
data class InformationRequestTemplateEvidencePolicyDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val minimumFileCount: Int,
    val maximumFileCount: Int? = null,
    val maximumFileSizeBytes: Long? = null,
    val maximumTotalSizeBytes: Long? = null,
    val minimumPageCount: Int? = null,
    val maximumPageCount: Int? = null,
    val issuerRequirement: InformationRequestEvidenceAttributeRequirement,
    val jurisdictionRequirement: InformationRequestEvidenceAttributeRequirement,
    val languageRequirement: InformationRequestEvidenceAttributeRequirement,
    val issueDateRequirement: InformationRequestEvidenceAttributeRequirement,
    val expiryDateRequirement: InformationRequestEvidenceAttributeRequirement,
    val coveragePeriodRequirement: InformationRequestEvidenceAttributeRequirement,
    val certificationRequirement: InformationRequestEvidenceAttributeRequirement,
    val signatureRequirement: InformationRequestEvidenceAttributeRequirement,
    val maximumIssueAgeDays: Int? = null,
    val minimumRemainingValidityDays: Int? = null,
    val minimumCoverageDays: Int? = null,
    val coverageContinuityRequired: Boolean,
    val waiverPolicy: InformationRequestEvidenceWaiverPolicy,
    val conformancePolicy: InformationRequestEvidenceConformancePolicy,
    val acceptedValues: List<InformationRequestTemplateAcceptedValueDto> = emptyList(),
)

@Serializable
data class InformationRequestTemplateAcceptedValueDto(
    val attribute: InformationRequestEvidenceAttribute,
    val acceptedValue: String,
)

@Serializable
data class InformationRequestTemplateCapabilityDto(
    val capability: InformationRequestCapability,
    val requiredContractVersion: Int,
)

@Serializable
data class InformationRequestTemplateRefusalDto(
    val errorMessage: String,
    val reasonCode: String,
    val sectionKey: String? = null,
    val requirementKey: String? = null,
    val groupKey: String? = null,
    val conditionRuleKey: String? = null,
    val reviewStageKey: String? = null,
)

@Serializable
data class CreateInformationRequestTemplateRequest(
    val namespace: String,
    val templateKey: String,
    val displayName: String,
    val description: String? = null,
    val scopeKind: InformationRequestTemplateScopeKind? = null,
)

@Serializable
data class InformationRequestTemplateConfigurationRequest(
    @Serializable(with = UUIDSerializer::class) val schemaVersionId: UUID? = null,
    val sections: List<InformationRequestTemplateSectionRequest> = emptyList(),
    val groups: List<InformationRequestTemplateGroupRequest> = emptyList(),
    val conditionRules: List<InformationRequestTemplateConditionRuleRequest> = emptyList(),
    val submissionMode: InformationRequestSubmissionMode = InformationRequestSubmissionMode.WHOLE_PACKAGE,
    val submissionStageOrdering: InformationRequestSubmissionStageOrdering =
        InformationRequestSubmissionStageOrdering.ANY_ORDER,
    val reviewStageOrdering: InformationRequestReviewStageOrdering = InformationRequestReviewStageOrdering.SEQUENTIAL,
    val reviewStages: List<InformationRequestTemplateReviewStageRequest> = emptyList(),
    val factReusePurposeKey: String? = null,
)

@Serializable
data class InformationRequestTemplateReviewStageRequest(
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
data class InformationRequestTemplateConditionRuleRequest(
    val ruleKey: String,
    val expressionVersion: Int = 1,
    val hiddenDataPolicy: InformationRequestConditionHiddenDataPolicy =
        InformationRequestConditionHiddenDataPolicy.RETAIN_SECURELY,
    val predicates: List<InformationRequestTemplateConditionPredicateRequest> = emptyList(),
)

@Serializable
data class InformationRequestTemplateConditionPredicateRequest(
    val sourceRequirementKey: String? = null,
    @Serializable(with = UUIDSerializer::class) val fieldDefinitionId: UUID? = null,
    val valueType: FieldValueType? = null,
    val operator: FieldOperator,
    val value: JsonElement? = null,
    val expectedDisposition: InformationRequestResponseDisposition? = null,
)

@Serializable
data class InformationRequestTemplateGroupRequest(
    val groupKey: String,
    val parentGroupKey: String? = null,
    val minOccurrences: Int = 0,
    val maxOccurrences: Int? = null,
)

@Serializable
data class InformationRequestTemplateSectionRequest(
    val sectionKey: String,
    val title: String,
    val helpText: String? = null,
    val requirements: List<InformationRequestTemplateRequirementRequest> = emptyList(),
    val submissionStageKey: String? = null,
)

@Serializable
data class InformationRequestTemplateRequirementRequest(
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
    val evidencePolicy: InformationRequestTemplateEvidencePolicyRequest? = null,
    val substituteRequirementKeys: List<String> = emptyList(),
    val supportingEvidenceRequirementKeys: List<String> = emptyList(),
    val attestationPolicy: InformationRequestTemplateAttestationPolicyRequest? = null,
)

@Serializable
data class InformationRequestTemplateAttestationPolicyRequest(
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
data class InformationRequestTemplateEvidencePolicyRequest(
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
    val acceptedValues: List<InformationRequestTemplateAcceptedValueRequest> = emptyList(),
)

@Serializable
data class InformationRequestTemplateAcceptedValueRequest(
    val attribute: InformationRequestEvidenceAttribute,
    val acceptedValue: String,
)
