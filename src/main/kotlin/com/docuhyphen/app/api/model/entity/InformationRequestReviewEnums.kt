package com.docuhyphen.app.api.model.entity

enum class InformationRequestReviewKind
{
    INITIAL,
    RESUBMISSION,
    RECONSIDERATION,
    APPEAL,
}

enum class InformationRequestReviewState(val settled: Boolean)
{
    PENDING(false),
    IN_REVIEW(false),
    CHANGES_REQUESTED(true),
    REJECTED(true),
    SATISFIED(true),
    SATISFIED_WITH_EXCEPTION(true),
    WITHDRAWN(true),
    ;

    val accepted: Boolean
        get() = this == SATISFIED || this == SATISFIED_WITH_EXCEPTION
}

enum class InformationRequestReviewAssignmentState
{
    ACTIVE,
    RECUSED,
    DELEGATED,
    REVOKED,
}

enum class InformationRequestReviewOutcome(val severity: Int)
{
    SATISFIED(0),
    SATISFIED_WITH_EXCEPTION(1),
    WAIVED(1),
    CHANGES_REQUIRED(2),
    REJECTED(3),
    ;

    val passing: Boolean
        get() = severity <= 1
}

enum class InformationRequestReviewDecisionKind
{
    REVIEWER,
    OVERRIDE,
    CARRIED,
}

enum class InformationRequestFindingSeverity
{
    OBSERVATION,
    MINOR,
    MAJOR,
    CRITICAL,
}

enum class InformationRequestReviewVisibility
{
    RESPONDENT_VISIBLE,
    REVIEWERS_ONLY,
}

enum class InformationRequestFindingCorrectionScope
{
    NONE,
    RESPONSE,
    EVIDENCE_VERSION,
    ADDITIONAL_EVIDENCE,
}

enum class InformationRequestRetestResult
{
    RESOLVED,
    UNRESOLVED,
}

enum class InformationRequestCorrectionState
{
    OPEN,
    RESUBMITTED,
    SUPERSEDED,
}

enum class InformationRequestReviewCommentRole
{
    REVIEWER,
    RESPONDENT,
    ADMINISTRATOR,
}

enum class InformationRequestAcceptedFactVisibility
{
    REQUESTING_SIDE,
    RESPONDING_PARTIES,
}

enum class InformationRequestAcceptedFactConfidence
{
    DECLARED,
    REVIEWED,
}

enum class InformationRequestAcceptedFactConflictState
{
    NONE,
    CONFLICTING,
}

enum class InformationRequestBusinessDecisionKind
{
    ORIGINAL,
    RECONSIDERATION,
    APPEAL,
}
