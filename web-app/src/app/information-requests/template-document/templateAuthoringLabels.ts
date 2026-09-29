import {
    FieldOperator,
    InformationRequestAttestationOrdering,
    InformationRequestAuthenticationStrength,
    InformationRequestConditionHiddenDataPolicy,
    InformationRequestContributorRole,
    InformationRequestEvidenceAttribute,
    InformationRequestEvidenceAttributeRequirement,
    InformationRequestEvidenceConformancePolicy,
    InformationRequestEvidenceWaiverPolicy,
    InformationRequestExternalSignatureReferencePolicy,
    InformationRequestRequiredness,
    InformationRequestRequirementType,
    InformationRequestResponseDisposition,
    InformationRequestResponseMode,
    InformationRequestReviewAggregation,
    InformationRequestReviewPolicy,
    InformationRequestReviewStageOrdering,
    InformationRequestReviewTieResolution,
    InformationRequestSubmissionMode,
    InformationRequestSubmissionStageOrdering,
} from "../../models/models.tsx";

export const requirementTypeLabels: Record<InformationRequestRequirementType, string> = {
    [InformationRequestRequirementType.FIELD]: "Typed answer",
    [InformationRequestRequirementType.DOCUMENT]: "Document",
    [InformationRequestRequirementType.RESPONSE_ATTESTATION]: "Confirmation",
};

export const responseModeLabels: Record<InformationRequestResponseMode, string> = {
    [InformationRequestResponseMode.PROVIDE]: "The party answers and may change the answer",
    [InformationRequestResponseMode.PROVIDE_ONCE]: "The party answers once",
    [InformationRequestResponseMode.VIEW_ONLY]: "The party only sees it",
    [InformationRequestResponseMode.NOT_DISCLOSED]: "Hidden from the party",
};

export const requirednessLabels: Record<InformationRequestRequiredness, string> = {
    [InformationRequestRequiredness.REQUIRED]: "Required",
    [InformationRequestRequiredness.OPTIONAL]: "Optional",
    [InformationRequestRequiredness.CONDITIONAL]: "Only when a condition is true",
};

export const contributorRoleLabels: Record<InformationRequestContributorRole, string> = {
    [InformationRequestContributorRole.CONTRIBUTOR]: "Contributor",
    [InformationRequestContributorRole.PREPARER]: "Preparer",
    [InformationRequestContributorRole.ATTESTOR]: "Attestor",
    [InformationRequestContributorRole.SUBJECT]: "Subject",
};

export const reviewPolicyLabels: Record<InformationRequestReviewPolicy, string> = {
    [InformationRequestReviewPolicy.NOT_REQUIRED]: "Not reviewed",
    [InformationRequestReviewPolicy.REQUIRED]: "Always reviewed",
    [InformationRequestReviewPolicy.REQUIRED_ON_EXCEPTION]: "Reviewed when the answer is an exception",
};

export const dispositionLabels: Record<InformationRequestResponseDisposition, string> = {
    [InformationRequestResponseDisposition.NOT_ANSWERED]: "Not answered",
    [InformationRequestResponseDisposition.PROVIDED]: "Provided",
    [InformationRequestResponseDisposition.PARTIALLY_PROVIDED]: "Partly provided",
    [InformationRequestResponseDisposition.NOT_APPLICABLE]: "Not applicable",
    [InformationRequestResponseDisposition.UNAVAILABLE]: "Not available",
    [InformationRequestResponseDisposition.EXCEPTION_REQUESTED]: "Exception requested",
    [InformationRequestResponseDisposition.SATISFIED_BY_REFERENCE]: "Satisfied by a reference",
    [InformationRequestResponseDisposition.WAIVED]: "Waived",
};

export const attributeRequirementLabels: Record<InformationRequestEvidenceAttributeRequirement, string> = {
    [InformationRequestEvidenceAttributeRequirement.NOT_CAPTURED]: "Not asked",
    [InformationRequestEvidenceAttributeRequirement.OPTIONAL]: "Optional",
    [InformationRequestEvidenceAttributeRequirement.REQUIRED]: "Required",
};

export const evidenceAttributeLabels: Record<InformationRequestEvidenceAttribute, string> = {
    [InformationRequestEvidenceAttribute.CONTENT_TYPE]: "File type",
    [InformationRequestEvidenceAttribute.ISSUER]: "Issuer",
    [InformationRequestEvidenceAttribute.JURISDICTION]: "Jurisdiction",
    [InformationRequestEvidenceAttribute.LANGUAGE]: "Language",
};

export const waiverPolicyLabels: Record<InformationRequestEvidenceWaiverPolicy, string> = {
    [InformationRequestEvidenceWaiverPolicy.NOT_PERMITTED]: "No waiver",
    [InformationRequestEvidenceWaiverPolicy.RESPONDENT_DECLARED]: "The party may declare a waiver",
    [InformationRequestEvidenceWaiverPolicy.REVIEW_APPROVAL_REQUIRED]: "A waiver needs a reviewer's approval",
};

export const conformancePolicyLabels: Record<InformationRequestEvidenceConformancePolicy, string> = {
    [InformationRequestEvidenceConformancePolicy.CONFORMANCE_REQUIRED]: "Files must meet the policy",
    [InformationRequestEvidenceConformancePolicy.DEFICIENCY_REVIEWABLE]: "A reviewer may accept a deficient file",
};

export const attestationOrderingLabels: Record<InformationRequestAttestationOrdering, string> = {
    [InformationRequestAttestationOrdering.ANY_ORDER]: "In any order",
    [InformationRequestAttestationOrdering.ROLE_SEQUENCE]: "In the order of the roles",
};

export const authenticationStrengthLabels: Record<InformationRequestAuthenticationStrength, string> = {
    [InformationRequestAuthenticationStrength.VERIFIED_CONTACT]: "A verified contact code",
    [InformationRequestAuthenticationStrength.ACCOUNT_SIGN_IN]: "A signed-in account",
    [InformationRequestAuthenticationStrength.MULTI_FACTOR]: "Multi-factor sign-in",
};

export const signatureReferenceLabels: Record<InformationRequestExternalSignatureReferencePolicy, string> = {
    [InformationRequestExternalSignatureReferencePolicy.NOT_ACCEPTED]: "Not asked",
    [InformationRequestExternalSignatureReferencePolicy.OPTIONAL]: "Optional",
    [InformationRequestExternalSignatureReferencePolicy.REQUIRED]: "Required",
};

export const submissionModeLabels: Record<InformationRequestSubmissionMode, string> = {
    [InformationRequestSubmissionMode.WHOLE_PACKAGE]: "Everything at once",
    [InformationRequestSubmissionMode.STAGED]: "In stages",
};

export const submissionStageOrderingLabels: Record<InformationRequestSubmissionStageOrdering, string> = {
    [InformationRequestSubmissionStageOrdering.ANY_ORDER]: "Stages in any order",
    [InformationRequestSubmissionStageOrdering.SEQUENTIAL]: "Stages in section order",
};

export const reviewStageOrderingLabels: Record<InformationRequestReviewStageOrdering, string> = {
    [InformationRequestReviewStageOrdering.SEQUENTIAL]: "One stage after another",
    [InformationRequestReviewStageOrdering.PARALLEL]: "All stages at the same time",
};

export const aggregationLabels: Record<InformationRequestReviewAggregation, string> = {
    [InformationRequestReviewAggregation.ANY]: "Any one reviewer decides",
    [InformationRequestReviewAggregation.ALL]: "Every reviewer must agree",
    [InformationRequestReviewAggregation.QUORUM]: "A quorum of reviewers decides",
    [InformationRequestReviewAggregation.CONSENSUS]: "Reviewers must reach the same outcome",
};

export const tieResolutionLabels: Record<InformationRequestReviewTieResolution, string> = {
    [InformationRequestReviewTieResolution.MOST_SEVERE_OUTCOME]: "The most severe outcome wins",
    [InformationRequestReviewTieResolution.REQUIRE_OVERRIDE]: "An authorized override decides",
};

export const hiddenDataPolicyLabels: Record<InformationRequestConditionHiddenDataPolicy, string> = {
    [InformationRequestConditionHiddenDataPolicy.RETAIN_SECURELY]: "Keep hidden answers securely",
    [InformationRequestConditionHiddenDataPolicy.CLEAR_WITH_CONFIRMATION]: "Clear hidden answers after confirmation",
    [InformationRequestConditionHiddenDataPolicy.ARCHIVE_OUTSIDE_ACTIVE_RESPONSE]: "Archive hidden answers outside the response",
};

export const operatorLabels: Record<FieldOperator, string> = {
    [FieldOperator.EQUALS]: "is",
    [FieldOperator.NOT_EQUALS]: "is not",
    [FieldOperator.LESS_THAN]: "is less than",
    [FieldOperator.LESS_THAN_OR_EQUAL]: "is at most",
    [FieldOperator.GREATER_THAN]: "is more than",
    [FieldOperator.GREATER_THAN_OR_EQUAL]: "is at least",
    [FieldOperator.CONTAINS]: "contains",
    [FieldOperator.STARTS_WITH]: "starts with",
    [FieldOperator.IN]: "is one of",
    [FieldOperator.NOT_IN]: "is none of",
    [FieldOperator.IS_EMPTY]: "is empty",
    [FieldOperator.IS_NOT_EMPTY]: "is not empty",
};
