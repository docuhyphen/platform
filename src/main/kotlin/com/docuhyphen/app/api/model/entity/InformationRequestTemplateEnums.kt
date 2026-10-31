package com.docuhyphen.app.api.model.entity

enum class InformationRequestTemplateScopeKind
{
    PLATFORM,
    ORGANIZATION,
    PERSONAL,
}

enum class InformationRequestTemplateStatus
{
    DRAFT,
    PUBLISHED,
    RETIRED,
}

enum class InformationRequestTemplateOriginKind
{
    REUSABLE,
    AD_HOC_REQUEST,
}

enum class InformationRequestRequirementType
{
    FIELD,
    DOCUMENT,
    RESPONSE_ATTESTATION,
}

enum class InformationRequestResponseMode
{
    PROVIDE,
    PROVIDE_ONCE,
    VIEW_ONLY,
    NOT_DISCLOSED,
}

enum class InformationRequestRequiredness
{
    REQUIRED,
    OPTIONAL,
    CONDITIONAL,
}

enum class InformationRequestConditionHiddenDataPolicy
{
    RETAIN_SECURELY,
    CLEAR_WITH_CONFIRMATION,
    ARCHIVE_OUTSIDE_ACTIVE_RESPONSE,
}

enum class InformationRequestContributorRole
{
    SUBJECT,
    CONTRIBUTOR,
    PREPARER,
    ATTESTOR,
}

enum class InformationRequestReviewPolicy
{
    NOT_REQUIRED,
    REQUIRED,
    REQUIRED_ON_EXCEPTION,
}

enum class InformationRequestEvidenceAttributeRequirement
{
    NOT_CAPTURED,
    OPTIONAL,
    REQUIRED,
}

enum class InformationRequestEvidenceAttribute
{
    CONTENT_TYPE,
    ISSUER,
    JURISDICTION,
    LANGUAGE,
}

enum class InformationRequestEvidenceWaiverPolicy
{
    NOT_PERMITTED,
    RESPONDENT_DECLARED,
    REVIEW_APPROVAL_REQUIRED,
}

enum class InformationRequestEvidenceConformancePolicy
{
    CONFORMANCE_REQUIRED,
    DEFICIENCY_REVIEWABLE,
}

enum class InformationRequestResponseDisposition
{
    NOT_ANSWERED,
    PROVIDED,
    PARTIALLY_PROVIDED,
    NOT_APPLICABLE,
    UNAVAILABLE,
    EXCEPTION_REQUESTED,
    SATISFIED_BY_REFERENCE,
    WAIVED,
}

enum class InformationRequestSubmissionMode
{
    WHOLE_PACKAGE,
    STAGED,
}

enum class InformationRequestSubmissionStageOrdering
{
    ANY_ORDER,
    SEQUENTIAL,
}

enum class InformationRequestAttestationOrdering
{
    ANY_ORDER,
    ROLE_SEQUENCE,
}

enum class InformationRequestAuthenticationStrength(val rank: Int)
{
    VERIFIED_CONTACT(1),
    ACCOUNT_SIGN_IN(2),
    MULTI_FACTOR(3),
}

enum class InformationRequestExternalSignatureReferencePolicy
{
    NOT_ACCEPTED,
    OPTIONAL,
    REQUIRED,
}

enum class InformationRequestReviewStageOrdering
{
    SEQUENTIAL,
    PARALLEL,
}

enum class InformationRequestReviewAggregation
{
    ALL,
    ANY,
    QUORUM,
    CONSENSUS,
}

enum class InformationRequestReviewTieResolution
{
    MOST_SEVERE_OUTCOME,
    REQUIRE_OVERRIDE,
}
