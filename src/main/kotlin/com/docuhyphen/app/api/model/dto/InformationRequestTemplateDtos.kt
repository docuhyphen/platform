package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.model.entity.InformationRequestAttestationOrdering
import com.docuhyphen.app.api.model.entity.InformationRequestAuthenticationStrength
import com.docuhyphen.app.api.model.entity.InformationRequestContributorRole
import com.docuhyphen.app.api.model.entity.InformationRequestConditionHiddenDataPolicy
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceAttribute
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceAttributeRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceConformancePolicy
import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceWaiverPolicy
import com.docuhyphen.app.api.model.entity.InformationRequestExternalSignatureReferencePolicy
import com.docuhyphen.app.api.model.entity.InformationRequestRequiredness
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.entity.InformationRequestResponseMode
import com.docuhyphen.app.api.model.entity.InformationRequestReviewPolicy
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionMode
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionStageOrdering
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateScopeKind
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateStatus
import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import com.docuhyphen.app.api.service.informationrequest.InformationRequestCapability
import com.docuhyphen.app.api.service.fields.FieldOperator
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.util.UUID

/**
 * Contracts for reusable, versioned Information Request Template configuration.
 *
 * The read and write shapes are deliberately the same tree: an author states one document made of
 * ordered sections, each holding the requirements it asks for in the order it asks them, and reads
 * back the same shape. Positions are therefore never authored. They are the order of the lists, so
 * a document cannot state a gap, a duplicate, or two different orders for one thing.
 *
 * Requirements are named by their stable key rather than by identifier, because that key is what
 * survives across Versions and what a recorded response means. A Version that asks about a
 * requirement for the first time creates the identity; a later one reuses it by naming the same key.
 */

// ── Read shape ───────────────────────────────────────────────────────────────

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
    val latestPublishedVersion: InformationRequestTemplateVersionDto? = null,
    val unsupportedPolicyControls: List<InformationRequestTemplateUnsupportedPolicyControlDto> = emptyList(),
    @Serializable(with = TimestampSerializer::class) val createdAt: Timestamp,
    @Serializable(with = TimestampSerializer::class) val updatedAt: Timestamp,
)

@Serializable
data class InformationRequestTemplateUnsupportedPolicyControlDto(
    val controlKey: String,
    val label: String,
    val reason: String,
)

/**
 * One Template as a list of them reads. Deliberately carries no configuration: a list answers which
 * Templates exist and whether each can still be edited, and reading every Version of every Template
 * to answer that would cost a whole configuration projection per row.
 */
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
    @Serializable(with = TimestampSerializer::class) val createdAt: Timestamp,
    @Serializable(with = TimestampSerializer::class) val updatedAt: Timestamp,
)

@Serializable
data class InformationRequestTemplateVersionDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    @Serializable(with = UUIDSerializer::class) val templateDefinitionId: UUID,
    val versionNumber: Int,
    val status: InformationRequestTemplateStatus,
    /** The exact published Schema Version typed requirements resolve against, when any are asked. */
    @Serializable(with = UUIDSerializer::class) val schemaVersionId: UUID? = null,
    val submissionMode: InformationRequestSubmissionMode = InformationRequestSubmissionMode.WHOLE_PACKAGE,
    val submissionStageOrdering: InformationRequestSubmissionStageOrdering =
        InformationRequestSubmissionStageOrdering.ANY_ORDER,
    val sections: List<InformationRequestTemplateSectionDto> = emptyList(),
    /** The repeatable and nested groups an occurrence-anchored requirement may answer once per. */
    val groups: List<InformationRequestTemplateGroupDto> = emptyList(),
    /** The versioned conditions a conditional requirement's binding may name as its rule. */
    val conditionRules: List<InformationRequestTemplateConditionRuleDto> = emptyList(),
    /** What this Version needs a runtime to supply. Recorded when the Version freezes. */
    val requiredCapabilities: List<InformationRequestTemplateCapabilityDto> = emptyList(),
    @Serializable(with = TimestampSerializer::class) val publishedAt: Timestamp? = null,
    @Serializable(with = TimestampSerializer::class) val retiredAt: Timestamp? = null,
    @Serializable(with = TimestampSerializer::class) val createdAt: Timestamp,
)

/**
 * One repeatable or nested grouping a Version defines. A requirement whose binding names this
 * group's key as its occurrence anchor is answered once per runtime occurrence of the group rather
 * than once for the whole request. [parentGroupKey] nests one group's occurrences inside another's,
 * null meaning the group repeats directly under the request.
 */
@Serializable
data class InformationRequestTemplateGroupDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val groupKey: String,
    val parentGroupKey: String? = null,
    val minOccurrences: Int,
    val maxOccurrences: Int? = null,
)

/**
 * One versioned condition a Version defines, read back the same shape it was authored as. A
 * requirement whose binding names this rule's key as its conditional rule is asked only while
 * every predicate of the rule evaluates true. See [InformationRequestTemplateConditionRuleRequest].
 */
@Serializable
data class InformationRequestTemplateConditionRuleDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val ruleKey: String,
    val expressionVersion: Int,
    val hiddenDataPolicy: InformationRequestConditionHiddenDataPolicy =
        InformationRequestConditionHiddenDataPolicy.RETAIN_SECURELY,
    val predicates: List<InformationRequestTemplateConditionPredicateDto> = emptyList(),
)

/** One term of a [InformationRequestTemplateConditionRuleDto], read back the way it was authored. */
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

/** One stable requirement as a single Version asks for it. */
@Serializable
data class InformationRequestTemplateRequirementDto(
    /** The binding, which is what this Version states about the requirement. */
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
    /** The stable Field Definition a typed answer is recorded against. Present only for `FIELD`. */
    @Serializable(with = UUIDSerializer::class) val collectedFieldDefinitionId: UUID? = null,
    val permittedDispositions: List<InformationRequestResponseDisposition> = emptyList(),
    val evidencePolicy: InformationRequestTemplateEvidencePolicyDto? = null,
    /** Requirement keys of requested Documents that may stand in for this one. */
    val substituteRequirementKeys: List<String> = emptyList(),
    /** Requirement keys of requested Documents that support the answer given here. */
    val supportingEvidenceRequirementKeys: List<String> = emptyList(),
    val attestationPolicy: InformationRequestTemplateAttestationPolicyDto? = null,
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

// ── Write shape ──────────────────────────────────────────────────────────────

@Serializable
data class CreateInformationRequestTemplateRequest(
    val namespace: String,
    val templateKey: String,
    val displayName: String,
    val description: String? = null,
    /** Omitted means the caller's active organization, or the caller acting personally. */
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

/** Authors one repeatable or nested group. See [InformationRequestTemplateGroupDto]. */
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
    /**
     * The stable Field Definition this requirement collects. Required when the requirement asks for
     * typed data and refused otherwise, because nothing else records an answer against a Field.
     */
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
