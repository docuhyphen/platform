package com.docuhyphen.app.api.model.informationrequest.lifecycle

import com.docuhyphen.app.api.model.entity.ExchangeStatus

enum class InformationRequestState
{
    DRAFT,
    ISSUED,
    IN_PROGRESS,
    CLOSED,
    CANCELLED,
    SUPERSEDED,
    EXPIRED,
    ;

    val isTerminal: Boolean
        get() = this in TERMINAL

    companion object
    {
        private val TERMINAL = setOf(CLOSED, CANCELLED, SUPERSEDED, EXPIRED)
    }
}

enum class InformationRequestMutation
{
    CREATE_DRAFT,
    ISSUE,
    RECORD_FIRST_VIEW,
    START_RESPONSE,
    SAVE_RESPONSE,
    RECERTIFY_FACT,
    ATTEST_RESPONSE,
    ADMINISTER_EVIDENCE,
    SUBMIT,
    START_REVIEW,
    REQUEST_CORRECTION,
    CLOSE,
    AMEND,
    REASSIGN,
    CANCEL,
    SUPERSEDE,
    EXPIRE,
    WITHDRAW_SUBMISSION,
    CREATE_SUCCESSOR,
    SCHEDULE_FOLLOW_UP,
    ASSIGN_REVIEWER,
    SAVE_REVIEW_DRAFT,
    RECORD_REVIEW_DECISION,
    RECORD_FINDING,
    RECORD_REVIEW_COMMENT,
    SETTLE_REVIEW,
    PROMOTE_FACT,
    REVOKE_FACT,
    RECORD_BUSINESS_DECISION,
    CHANGE_COMPLETION_GATE,
    START_CLOCK,
    PAUSE_CLOCK,
    RESUME_CLOCK,
    EXTEND_CLOCK,
    RECORD_REMINDER,
    RECORD_OVERDUE,
    RECORD_ESCALATION,
    ASSIGN_PARTY,
    REVOKE_PARTY,
    SEND_REMINDER,
    REQUEST_EXTERNAL_SOURCE,
    RECORD_EXTERNAL_VALUE,
    DECIDE_EXTERNAL_VALUE,
    RECORD_GENERATED_OUTPUT,
}

enum class InformationRequestReadActor
{
    OWNER,
    PARTY,
    EXTERNAL_SESSION,
    RECOVERY,
}

enum class InformationRequestExternalSessionEffect
{
    PRESERVE,
    EXPIRE_NATURALLY,
    REVOKE_NON_OWNER,
    REVOKE_ALL,
}

enum class InformationRequestNonTerminalEffect
{
    NONE,
    CANCEL,
    READ_ONLY,
}

data class InformationRequestParentSnapshot(
    val status: ExchangeStatus,
    val deleted: Boolean = false,
    val lockedForUpdate: Boolean = false,
)

data class InformationRequestParentEffects(
    val ownerRead: Boolean,
    val partyRead: Boolean,
    val externalSessionRead: Boolean,
    val recoveryRead: Boolean,
    val externalSessionEffect: InformationRequestExternalSessionEffect,
    val nonTerminalRequestEffect: InformationRequestNonTerminalEffect,
)

data class InformationRequestCompletionCandidate(
    val state: InformationRequestState,
    val gatesExchangeClosure: Boolean,
)

sealed interface InformationRequestPolicyDecision
{
    data class Allow(val nextState: InformationRequestState? = null) : InformationRequestPolicyDecision

    data class Deny(val reasonCode: String) : InformationRequestPolicyDecision
}
