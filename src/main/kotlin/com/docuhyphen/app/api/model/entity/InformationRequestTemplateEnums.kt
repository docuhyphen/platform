package com.docuhyphen.app.api.model.entity

/**
 * Enumerations for reusable Information Request Template configuration. These are persisted as
 * strings; add values only, never repurpose.
 */

/**
 * Governance scope kind that owns Template configuration. Each kind names exactly one owner:
 * PLATFORM names none, ORGANIZATION names an organization, and PERSONAL names a single user. It is
 * deliberately separate from [FieldScopeKind]: the two vocabularies happen to spell the same three
 * owners today, and a Template must not inherit a later change made for typed-data configuration.
 */
enum class InformationRequestTemplateScopeKind
{
    PLATFORM,
    ORGANIZATION,
    PERSONAL,
}

/**
 * Lifecycle of a Template Definition and of one of its Versions. A published Version is frozen: the
 * only later fact recordable about it is retirement, and a retired Version stays readable for the
 * requests already pinned to it.
 */
enum class InformationRequestTemplateStatus
{
    DRAFT,
    PUBLISHED,
    RETIRED,
}

/**
 * Where a Template Definition is meant to be used. Reusable definitions are library entries an
 * author can select again; ad hoc definitions are private support records for one runtime request.
 */
enum class InformationRequestTemplateOriginKind
{
    REUSABLE,
    AD_HOC_REQUEST,
}

/**
 * The kind of thing a Template asks for. This belongs to the stable requirement identity rather
 * than to a Version binding: a requirement that asked for typed data in one Version and a document
 * in the next is not the same requirement, and every response already recorded against it would
 * silently change meaning. RESPONSE_ATTESTATION is a respondent assertion and is unrelated to
 * ExchangeRecipientAttestation, which snapshots a recipient selection.
 */
enum class InformationRequestRequirementType
{
    FIELD,
    DOCUMENT,
    RESPONSE_ATTESTATION,
}

/**
 * What the nominated party may do with one requirement in one Version. This is separate from the
 * sensitivity of the response and from who may review it, and it governs reads as well as writes.
 */
enum class InformationRequestResponseMode
{
    /** The party supplies the response and may revise it until the response is submitted. */
    PROVIDE,

    /** The party supplies the response once; it is not revisable afterwards. */
    PROVIDE_ONCE,

    /** The party sees the requirement and any response to it but cannot answer. */
    VIEW_ONLY,

    /** The requirement is not disclosed to the responding party; only the requesting side works on it. */
    NOT_DISCLOSED,
}

/** Whether one Version owes an answer for a requirement, and under what circumstances. */
enum class InformationRequestRequiredness
{
    REQUIRED,
    OPTIONAL,

    /** Owed only when the binding's named condition rule resolves true. */
    CONDITIONAL,
}

enum class InformationRequestConditionHiddenDataPolicy
{
    RETAIN_SECURELY,
    CLEAR_WITH_CONFIRMATION,
    ARCHIVE_OUTSIDE_ACTIVE_RESPONSE,
}

/**
 * The request party a Template nominates to answer one requirement. Review and decision roles are
 * deliberately absent: a party that reviews an answer is not a party that gives one, and the
 * separation must not be configurable away.
 */
enum class InformationRequestContributorRole
{
    SUBJECT,
    CONTRIBUTOR,
    PREPARER,
    ATTESTOR,
}

/** Whether a reviewer must record a disposition before one requirement counts as satisfied. */
enum class InformationRequestReviewPolicy
{
    NOT_REQUIRED,
    REQUIRED,

    /** Review is needed only when the response is something other than a plain provided answer. */
    REQUIRED_ON_EXCEPTION,
}

/**
 * Whether one attribute of a requested Document has to be stated about the files that answer it.
 * The same three answers apply to every such attribute, so issuer, jurisdiction, language, issue
 * date, expiry date, coverage period, certification, and signature all draw from this vocabulary.
 * Which values are then accepted is a set of
 * [InformationRequestTemplateEvidenceAcceptedValue] rather than a further column.
 */
enum class InformationRequestEvidenceAttributeRequirement
{
    /** Never asked for, so no bound may be placed on it and no value may be restricted. */
    NOT_CAPTURED,

    /** Recorded when the responding party has it, and absent without failing the requirement. */
    OPTIONAL,

    /** Always recorded, which is what makes a bound or a restricted value evaluable. */
    REQUIRED,
}

/**
 * Which attribute of a requested Document one accepted value restricts. `CONTENT_TYPE` is
 * restrictable without being one of the captured attributes, because the type of a file is read from
 * the file rather than stated by the party supplying it.
 */
enum class InformationRequestEvidenceAttribute
{
    CONTENT_TYPE,
    ISSUER,
    JURISDICTION,
    LANGUAGE,
}

/**
 * Whether a requirement may be resolved without the evidence it asks for, and what reaches that
 * outcome. This governs how a waiver is given;
 * [InformationRequestResponseDisposition.WAIVED] is the outcome itself, and a Version that states
 * one without the other is refused when it freezes.
 */
enum class InformationRequestEvidenceWaiverPolicy
{
    NOT_PERMITTED,

    /** The responding party records the waiver and its reason, and it takes effect immediately. */
    RESPONDENT_DECLARED,

    /** The waiver takes effect only once a reviewer has recorded approval of it. */
    REVIEW_APPROVAL_REQUIRED,
}

/**
 * What becomes of a file that fails technical conformance. The concrete technical bounds are the
 * accepted content types and the size and page limits of the same policy; this decides whether
 * falling outside them ends the submission or leaves the judgement to a reviewer.
 */
enum class InformationRequestEvidenceConformancePolicy
{
    CONFORMANCE_REQUIRED,
    DEFICIENCY_REVIEWABLE,
}

/**
 * How a requirement was resolved. A Template restricts which of these a respondent may choose for
 * one requirement, and customer-authored labels and reason codes decorate them without adding
 * platform values or execution branches.
 *
 * NOT_ANSWERED is the state every requirement starts in rather than an answer, so it is never one
 * of the dispositions a Template permits.
 */
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
