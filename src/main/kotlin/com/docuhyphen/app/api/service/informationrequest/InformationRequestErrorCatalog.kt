package com.docuhyphen.app.api.service.informationrequest

/**
 * Stable machine reasons for refusing a runtime Information Request operation.
 *
 * A refusal has to survive translation, prose changes, and a client that has to decide what to
 * do next, so each reason is a code rather than a message. Codes are additive: an existing one is
 * never renamed or reused for a different meaning once a client can observe it.
 *
 * Configuration-time refusals keep their own codes on the Template exceptions, because an author
 * choosing a Version and a respondent answering an issued request are different audiences.
 */
object InformationRequestErrorCatalog
{
    /** No request with that identity is visible to the caller. */
    const val NOT_FOUND = "INFORMATION_REQUEST_NOT_FOUND"

    /** The caller holds no capability for the operation on this request or occurrence. */
    const val FORBIDDEN = "INFORMATION_REQUEST_FORBIDDEN"

    /**
     * The authorization facts for the resource could not be resolved, so the decision was
     * refused rather than guessed.
     */
    const val AUTHORIZATION_UNAVAILABLE = "INFORMATION_REQUEST_AUTHORIZATION_UNAVAILABLE"

    /** The request's own collection state forbids the operation. */
    const val STATE_INVALID = "INFORMATION_REQUEST_STATE_INVALID"

    /** The parent Exchange's state forbids the operation. */
    const val PARENT_STATE_INVALID = "INFORMATION_REQUEST_PARENT_STATE_INVALID"

    /** A parent Exchange state was not locked or rechecked in the authoritative mutation boundary. */
    const val PARENT_LOCK_REQUIRED = "INFORMATION_REQUEST_PARENT_LOCK_REQUIRED"

    /** A configured Exchange completion gate has not been satisfied. */
    const val COMPLETION_GATES_UNSATISFIED = "INFORMATION_REQUEST_COMPLETION_GATES_UNSATISFIED"

    /** Nongating requests must be explicitly ended before their parent Exchange ends. */
    const val REMAINING_REQUESTS_REQUIRE_CANCELLATION =
        "INFORMATION_REQUEST_REMAINING_REQUESTS_REQUIRE_CANCELLATION"

    /** The caller is not the party this occurrence nominates to act. */
    const val PARTY_NOT_ASSIGNED = "INFORMATION_REQUEST_PARTY_NOT_ASSIGNED"

    /** The occurrence's response mode does not permit the requested operation. */
    const val RESPONSE_MODE_DENIED = "INFORMATION_REQUEST_RESPONSE_MODE_DENIED"

    /** The pinned Template Version does not permit the supplied response disposition. */
    const val RESPONSE_DISPOSITION_NOT_PERMITTED = "INFORMATION_REQUEST_RESPONSE_DISPOSITION_NOT_PERMITTED"

    /** The supplied response disposition needs a respondent narrative before it can be saved. */
    const val RESPONSE_NARRATIVE_REQUIRED = "INFORMATION_REQUEST_RESPONSE_NARRATIVE_REQUIRED"

    /** A condition would clear response data, but the caller did not explicitly confirm that loss. */
    const val HIDDEN_RESPONSE_CLEAR_CONFIRMATION_REQUIRED =
        "INFORMATION_REQUEST_HIDDEN_RESPONSE_CLEAR_CONFIRMATION_REQUIRED"

    const val STRUCTURED_RESPONSE_VALIDATION_FAILED =
        "INFORMATION_REQUEST_STRUCTURED_RESPONSE_VALIDATION_FAILED"

    /** A repeatable group would fall outside the Template's authored minimum or maximum count. */
    const val GROUP_OCCURRENCE_CARDINALITY_INVALID =
        "INFORMATION_REQUEST_GROUP_OCCURRENCE_CARDINALITY_INVALID"

    /** A nested group occurrence was supplied without the active parent its Template requires. */
    const val GROUP_OCCURRENCE_PARENT_INVALID = "INFORMATION_REQUEST_GROUP_OCCURRENCE_PARENT_INVALID"

    /** A reorder command did not name exactly the active sibling occurrences for one group. */
    const val GROUP_OCCURRENCE_ORDER_INVALID = "INFORMATION_REQUEST_GROUP_OCCURRENCE_ORDER_INVALID"

    /** The occurrence falls into a confidentiality compartment the caller cannot reach. */
    const val CONFIDENTIALITY_DENIED = "INFORMATION_REQUEST_CONFIDENTIALITY_DENIED"

    /** The occurrence is not inside the open correction scope. */
    const val CORRECTION_SCOPE_DENIED = "INFORMATION_REQUEST_CORRECTION_SCOPE_DENIED"

    /** A mutation arrived without the expected-revision precondition it requires. */
    const val PRECONDITION_REQUIRED = "INFORMATION_REQUEST_PRECONDITION_REQUIRED"

    /** The supplied expected revision is no longer current. */
    const val PRECONDITION_FAILED = "INFORMATION_REQUEST_PRECONDITION_FAILED"

    /** The idempotency key was reused with a different request body. */
    const val IDEMPOTENCY_CONFLICT = "INFORMATION_REQUEST_IDEMPOTENCY_CONFLICT"

    /** The owner does not hold the commercial entitlement the operation needs. */
    const val ENTITLEMENT_REQUIRED = "INFORMATION_REQUEST_ENTITLEMENT_REQUIRED"

    /** This runtime cannot serve a capability the pinned Template Version requires. */
    const val CAPABILITY_NOT_INSTALLED = "INFORMATION_REQUEST_CAPABILITY_NOT_INSTALLED"

    /**
     * The parent Exchange requires recipients to sign in, so a no-auth bootstrap access link
     * cannot be issued for it.
     */
    const val RECIPIENT_SIGN_IN_REQUIRED = "INFORMATION_REQUEST_RECIPIENT_SIGN_IN_REQUIRED"

    /**
     * A retried access-link issuance matched a previous receipt. The original secret was never
     * persisted and cannot be recovered; rotate the link instead of retrying issuance.
     */
    const val ACCESS_LINK_ALREADY_ISSUED = "INFORMATION_REQUEST_ACCESS_LINK_ALREADY_ISSUED"

    /** The presented bootstrap token does not resolve to a usable access link. */
    const val ACCESS_LINK_INVALID = "INFORMATION_REQUEST_ACCESS_LINK_INVALID"

    /** The presented bootstrap token's access link has been revoked. */
    const val ACCESS_LINK_REVOKED = "INFORMATION_REQUEST_ACCESS_LINK_REVOKED"

    /** The presented bootstrap token's access link has expired. */
    const val ACCESS_LINK_EXPIRED = "INFORMATION_REQUEST_ACCESS_LINK_EXPIRED"

    /** The presented bootstrap token's access link has reached its usage limit. */
    const val ACCESS_LINK_EXHAUSTED = "INFORMATION_REQUEST_ACCESS_LINK_EXHAUSTED"

    /**
     * A retried access-link rotation matched a previous receipt. The new secret was never
     * persisted and cannot be recovered; rotate the link again instead of retrying.
     */
    const val ACCESS_LINK_ALREADY_ROTATED = "INFORMATION_REQUEST_ACCESS_LINK_ALREADY_ROTATED"

    /**
     * A retried access-link replacement matched a previous receipt. The new secret was never
     * persisted and cannot be recovered; replace the link again instead of retrying.
     */
    const val ACCESS_LINK_ALREADY_REPLACED = "INFORMATION_REQUEST_ACCESS_LINK_ALREADY_REPLACED"

    /** No outstanding contact-proof code exists to verify, or none was supplied. */
    const val CONTACT_PROOF_REQUIRED = "INFORMATION_REQUEST_CONTACT_PROOF_REQUIRED"

    /** The presented contact-proof code does not match the one issued. */
    const val CONTACT_PROOF_INVALID = "INFORMATION_REQUEST_CONTACT_PROOF_INVALID"

    /** Too many invalid contact-proof codes have been presented for the outstanding challenge. */
    const val CONTACT_PROOF_LOCKED = "INFORMATION_REQUEST_CONTACT_PROOF_LOCKED"

    const val CONTACT_PROOF_CHALLENGE_LIMIT = "INFORMATION_REQUEST_CONTACT_PROOF_CHALLENGE_LIMIT"

    /** The outstanding contact-proof code has expired. */
    const val CONTACT_PROOF_EXPIRED = "INFORMATION_REQUEST_CONTACT_PROOF_EXPIRED"

    /** The presented request access session has been revoked. */
    const val ACCESS_SESSION_REVOKED = "INFORMATION_REQUEST_ACCESS_SESSION_REVOKED"

    /** The presented request access session has expired. */
    const val ACCESS_SESSION_EXPIRED = "INFORMATION_REQUEST_ACCESS_SESSION_EXPIRED"

    /**
     * The presented bootstrap token's access link is otherwise usable, but no request access
     * session has ever been minted from it, or every session minted from it is no longer usable.
     * Contact-proof verification must succeed again before this link can read request content.
     */
    const val ACCESS_SESSION_REQUIRED = "INFORMATION_REQUEST_ACCESS_SESSION_REQUIRED"

    /**
     * The registering App User's email does not match the verified contact address on file for
     * the participant it claims to upgrade.
     */
    const val PARTICIPANT_ACCOUNT_EMAIL_MISMATCH = "INFORMATION_REQUEST_PARTICIPANT_ACCOUNT_EMAIL_MISMATCH"

    /** This participant is already linked to a different App User account. */
    const val PARTICIPANT_ACCOUNT_ALREADY_LINKED = "INFORMATION_REQUEST_PARTICIPANT_ACCOUNT_ALREADY_LINKED"

    /**
     * The request's execution grant has been explicitly revoked. Distinct from an operational
     * suspension or a lapsed subscription: this targets one specific request rather than every
     * request its owner has open, and it is never lifted by a later change to the owner's plan.
     */
    const val EXECUTION_GRANT_REVOKED = "INFORMATION_REQUEST_EXECUTION_GRANT_REVOKED"

    /** A response Field patch entry names a Field the addressed Requirement does not collect. */
    const val FIELD_ENTRY_NOT_BOUND = "INFORMATION_REQUEST_FIELD_ENTRY_NOT_BOUND"

    const val GROUP_OCCURRENCE_REMOVED = "INFORMATION_REQUEST_GROUP_OCCURRENCE_REMOVED"

    /** Every declared code, used to prove the catalog stays unique and namespaced. */
    fun allCodes(): List<String> = listOf(
        NOT_FOUND,
        FORBIDDEN,
        AUTHORIZATION_UNAVAILABLE,
        STATE_INVALID,
        PARENT_STATE_INVALID,
        PARENT_LOCK_REQUIRED,
        COMPLETION_GATES_UNSATISFIED,
        REMAINING_REQUESTS_REQUIRE_CANCELLATION,
        PARTY_NOT_ASSIGNED,
        RESPONSE_MODE_DENIED,
        RESPONSE_DISPOSITION_NOT_PERMITTED,
        RESPONSE_NARRATIVE_REQUIRED,
        HIDDEN_RESPONSE_CLEAR_CONFIRMATION_REQUIRED,
        STRUCTURED_RESPONSE_VALIDATION_FAILED,
        GROUP_OCCURRENCE_CARDINALITY_INVALID,
        GROUP_OCCURRENCE_PARENT_INVALID,
        GROUP_OCCURRENCE_ORDER_INVALID,
        CONFIDENTIALITY_DENIED,
        CORRECTION_SCOPE_DENIED,
        PRECONDITION_REQUIRED,
        PRECONDITION_FAILED,
        IDEMPOTENCY_CONFLICT,
        ENTITLEMENT_REQUIRED,
        CAPABILITY_NOT_INSTALLED,
        RECIPIENT_SIGN_IN_REQUIRED,
        ACCESS_LINK_ALREADY_ISSUED,
        ACCESS_LINK_INVALID,
        ACCESS_LINK_REVOKED,
        ACCESS_LINK_EXPIRED,
        ACCESS_LINK_EXHAUSTED,
        ACCESS_LINK_ALREADY_ROTATED,
        ACCESS_LINK_ALREADY_REPLACED,
        CONTACT_PROOF_REQUIRED,
        CONTACT_PROOF_INVALID,
        CONTACT_PROOF_LOCKED,
        CONTACT_PROOF_CHALLENGE_LIMIT,
        CONTACT_PROOF_EXPIRED,
        ACCESS_SESSION_REVOKED,
        ACCESS_SESSION_EXPIRED,
        ACCESS_SESSION_REQUIRED,
        PARTICIPANT_ACCOUNT_EMAIL_MISMATCH,
        PARTICIPANT_ACCOUNT_ALREADY_LINKED,
        EXECUTION_GRANT_REVOKED,
        FIELD_ENTRY_NOT_BOUND,
        GROUP_OCCURRENCE_REMOVED,
    )
}

