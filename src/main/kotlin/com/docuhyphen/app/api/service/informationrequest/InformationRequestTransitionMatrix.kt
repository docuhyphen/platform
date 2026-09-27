package com.docuhyphen.app.api.service.informationrequest

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

object InformationRequestTransitionMatrix
{
    private val ACTIVE_RESPONSE_STATES = setOf(
        InformationRequestState.ISSUED,
        InformationRequestState.IN_PROGRESS,
    )

    private val RESPONSE_MUTATIONS = setOf(
        InformationRequestMutation.RECORD_FIRST_VIEW,
        InformationRequestMutation.START_RESPONSE,
        InformationRequestMutation.SAVE_RESPONSE,
        InformationRequestMutation.ATTEST_RESPONSE,
        InformationRequestMutation.ADMINISTER_EVIDENCE,
        InformationRequestMutation.SUBMIT,
        InformationRequestMutation.WITHDRAW_SUBMISSION,
    )

    private val SUCCESSOR_SOURCE_STATES = InformationRequestState.entries.toSet() - setOf(
        InformationRequestState.DRAFT,
        InformationRequestState.CANCELLED,
        InformationRequestState.SUPERSEDED,
    )

    private val FOLLOW_UP_STATES = InformationRequestState.entries.toSet() - setOf(
        InformationRequestState.CANCELLED,
        InformationRequestState.SUPERSEDED,
        InformationRequestState.EXPIRED,
    )

    private val REVIEW_MUTATIONS = setOf(
        InformationRequestMutation.START_REVIEW,
        InformationRequestMutation.ASSIGN_REVIEWER,
        InformationRequestMutation.SAVE_REVIEW_DRAFT,
        InformationRequestMutation.RECORD_REVIEW_DECISION,
        InformationRequestMutation.RECORD_FINDING,
        InformationRequestMutation.RECORD_REVIEW_COMMENT,
        InformationRequestMutation.SETTLE_REVIEW,
        InformationRequestMutation.REQUEST_CORRECTION,
        InformationRequestMutation.CLOSE,
    )

    private val FACT_STATES = setOf(
        InformationRequestState.ISSUED,
        InformationRequestState.IN_PROGRESS,
        InformationRequestState.CLOSED,
    )

    private val RECORD_MUTATIONS = setOf(
        InformationRequestMutation.PROMOTE_FACT,
        InformationRequestMutation.REVOKE_FACT,
        InformationRequestMutation.RECORD_BUSINESS_DECISION,
    )

    fun parentEffects(parent: InformationRequestParentSnapshot): InformationRequestParentEffects
    {
        if (parent.deleted)
        {
            return InformationRequestParentEffects(
                ownerRead = true,
                partyRead = false,
                externalSessionRead = false,
                recoveryRead = true,
                externalSessionEffect = InformationRequestExternalSessionEffect.REVOKE_ALL,
                nonTerminalRequestEffect = InformationRequestNonTerminalEffect.READ_ONLY,
            )
        }

        return when (parent.status)
        {
            ExchangeStatus.INITIATED,
            ExchangeStatus.ACCEPTED_STARTED,
            ->
                InformationRequestParentEffects(
                    ownerRead = true,
                    partyRead = true,
                    externalSessionRead = true,
                    recoveryRead = false,
                    externalSessionEffect = InformationRequestExternalSessionEffect.PRESERVE,
                    nonTerminalRequestEffect = InformationRequestNonTerminalEffect.NONE,
                )

            ExchangeStatus.ENDED ->
                InformationRequestParentEffects(
                    ownerRead = true,
                    partyRead = true,
                    externalSessionRead = true,
                    recoveryRead = false,
                    externalSessionEffect = InformationRequestExternalSessionEffect.EXPIRE_NATURALLY,
                    nonTerminalRequestEffect = InformationRequestNonTerminalEffect.READ_ONLY,
                )

            ExchangeStatus.REJECTED,
            ExchangeStatus.RESCINDED,
            ->
                InformationRequestParentEffects(
                    ownerRead = true,
                    partyRead = false,
                    externalSessionRead = false,
                    recoveryRead = false,
                    externalSessionEffect = InformationRequestExternalSessionEffect.REVOKE_NON_OWNER,
                    nonTerminalRequestEffect = InformationRequestNonTerminalEffect.CANCEL,
                )
        }
    }

    fun canRead(
        parent: InformationRequestParentSnapshot,
        actor: InformationRequestReadActor,
    ): InformationRequestPolicyDecision
    {
        val effects = parentEffects(parent)
        val allowed = when (actor)
        {
            InformationRequestReadActor.OWNER -> effects.ownerRead
            InformationRequestReadActor.PARTY -> effects.partyRead
            InformationRequestReadActor.EXTERNAL_SESSION -> effects.externalSessionRead
            InformationRequestReadActor.RECOVERY -> effects.recoveryRead
        }
        return if (allowed) allow() else deny(InformationRequestErrorCatalog.PARENT_STATE_INVALID)
    }

    fun canMutate(
        parent: InformationRequestParentSnapshot,
        currentState: InformationRequestState?,
        mutation: InformationRequestMutation,
    ): InformationRequestPolicyDecision
    {
        if (!parent.lockedForUpdate)
        {
            return deny(InformationRequestErrorCatalog.PARENT_LOCK_REQUIRED)
        }
        if (!parentAllowsMutation(parent))
        {
            return deny(InformationRequestErrorCatalog.PARENT_STATE_INVALID)
        }
        if (parent.status == ExchangeStatus.INITIATED && mutation in RESPONSE_MUTATIONS + REVIEW_MUTATIONS + RECORD_MUTATIONS)
        {
            return deny(InformationRequestErrorCatalog.PARENT_STATE_INVALID)
        }
        if (mutation == InformationRequestMutation.PROMOTE_FACT || mutation == InformationRequestMutation.REVOKE_FACT)
        {
            return if (currentState != null && currentState in FACT_STATES) allow()
            else deny(InformationRequestErrorCatalog.STATE_INVALID)
        }
        if (mutation == InformationRequestMutation.RECORD_BUSINESS_DECISION)
        {
            return if (currentState != null && currentState != InformationRequestState.DRAFT) allow()
            else deny(InformationRequestErrorCatalog.STATE_INVALID)
        }
        if (mutation == InformationRequestMutation.CREATE_DRAFT)
        {
            return if (currentState == null) allow(InformationRequestState.DRAFT)
            else deny(InformationRequestErrorCatalog.STATE_INVALID)
        }
        if (mutation == InformationRequestMutation.CREATE_SUCCESSOR)
        {
            return if (currentState != null && currentState in SUCCESSOR_SOURCE_STATES) allow()
            else deny(InformationRequestErrorCatalog.STATE_INVALID)
        }
        if (mutation == InformationRequestMutation.SCHEDULE_FOLLOW_UP)
        {
            return if (currentState != null && currentState in FOLLOW_UP_STATES) allow()
            else deny(InformationRequestErrorCatalog.STATE_INVALID)
        }
        if (currentState == null || currentState.isTerminal)
        {
            return deny(InformationRequestErrorCatalog.STATE_INVALID)
        }

        return when (mutation)
        {
            InformationRequestMutation.CREATE_DRAFT -> deny(InformationRequestErrorCatalog.STATE_INVALID)
            InformationRequestMutation.ISSUE -> allowFrom(
                currentState,
                InformationRequestState.DRAFT,
                InformationRequestState.ISSUED,
            )
            InformationRequestMutation.RECORD_FIRST_VIEW -> allowSameFrom(currentState, ACTIVE_RESPONSE_STATES)
            InformationRequestMutation.START_RESPONSE -> allowFrom(
                currentState,
                InformationRequestState.ISSUED,
                InformationRequestState.IN_PROGRESS,
            )
            InformationRequestMutation.SAVE_RESPONSE,
            InformationRequestMutation.ATTEST_RESPONSE,
            InformationRequestMutation.ADMINISTER_EVIDENCE,
            -> allowSameFrom(currentState, ACTIVE_RESPONSE_STATES)
            InformationRequestMutation.SUBMIT,
            InformationRequestMutation.WITHDRAW_SUBMISSION,
            -> allowSameFrom(currentState, ACTIVE_RESPONSE_STATES)
            InformationRequestMutation.START_REVIEW,
            InformationRequestMutation.ASSIGN_REVIEWER,
            InformationRequestMutation.SAVE_REVIEW_DRAFT,
            InformationRequestMutation.RECORD_REVIEW_DECISION,
            InformationRequestMutation.RECORD_FINDING,
            InformationRequestMutation.RECORD_REVIEW_COMMENT,
            InformationRequestMutation.SETTLE_REVIEW,
            InformationRequestMutation.REQUEST_CORRECTION,
            -> allowSameFrom(currentState, ACTIVE_RESPONSE_STATES)
            InformationRequestMutation.CLOSE -> allowFromAny(
                currentState,
                ACTIVE_RESPONSE_STATES,
                InformationRequestState.CLOSED,
            )
            InformationRequestMutation.AMEND,
            InformationRequestMutation.REASSIGN,
            -> allowSameFrom(currentState, nonTerminalStates())
            InformationRequestMutation.CANCEL ->
                allowFromAny(currentState, nonTerminalStates(), InformationRequestState.CANCELLED)
            InformationRequestMutation.SUPERSEDE ->
                allowFromAny(currentState, nonTerminalStates(), InformationRequestState.SUPERSEDED)
            InformationRequestMutation.EXPIRE ->
                allowFromAny(currentState, nonTerminalStates(), InformationRequestState.EXPIRED)
            InformationRequestMutation.CREATE_SUCCESSOR,
            InformationRequestMutation.SCHEDULE_FOLLOW_UP,
            InformationRequestMutation.PROMOTE_FACT,
            InformationRequestMutation.REVOKE_FACT,
            InformationRequestMutation.RECORD_BUSINESS_DECISION,
            -> deny(InformationRequestErrorCatalog.STATE_INVALID)
            InformationRequestMutation.CHANGE_COMPLETION_GATE,
            InformationRequestMutation.START_CLOCK,
            InformationRequestMutation.PAUSE_CLOCK,
            InformationRequestMutation.RESUME_CLOCK,
            InformationRequestMutation.EXTEND_CLOCK,
            -> allowSameFrom(currentState, nonTerminalStates())
            InformationRequestMutation.RECORD_REMINDER,
            InformationRequestMutation.RECORD_OVERDUE,
            InformationRequestMutation.RECORD_ESCALATION,
            -> allowSameFrom(currentState, ACTIVE_RESPONSE_STATES)
        }
    }

    fun canEndExchange(
        parent: InformationRequestParentSnapshot,
        requests: List<InformationRequestCompletionCandidate>,
    ): InformationRequestPolicyDecision
    {
        if (!parent.lockedForUpdate)
        {
            return deny(InformationRequestErrorCatalog.PARENT_LOCK_REQUIRED)
        }
        if (!parentAllowsMutation(parent))
        {
            return deny(InformationRequestErrorCatalog.PARENT_STATE_INVALID)
        }
        if (requests.any { it.gatesExchangeClosure && !it.state.isTerminal })
        {
            return deny(InformationRequestErrorCatalog.COMPLETION_GATES_UNSATISFIED)
        }
        if (requests.any { !it.gatesExchangeClosure && !it.state.isTerminal })
        {
            return deny(InformationRequestErrorCatalog.REMAINING_REQUESTS_REQUIRE_CANCELLATION)
        }
        return allow()
    }

    private fun parentAllowsMutation(parent: InformationRequestParentSnapshot): Boolean =
        !parent.deleted && parent.status in setOf(ExchangeStatus.INITIATED, ExchangeStatus.ACCEPTED_STARTED)

    private fun allowFrom(
        currentState: InformationRequestState,
        expectedState: InformationRequestState,
        nextState: InformationRequestState,
    ): InformationRequestPolicyDecision =
        if (currentState == expectedState) allow(nextState) else deny(InformationRequestErrorCatalog.STATE_INVALID)

    private fun allowFromAny(
        currentState: InformationRequestState,
        expectedStates: Set<InformationRequestState>,
        nextState: InformationRequestState,
    ): InformationRequestPolicyDecision =
        if (currentState in expectedStates) allow(nextState) else deny(InformationRequestErrorCatalog.STATE_INVALID)

    private fun allowSameFrom(
        currentState: InformationRequestState,
        expectedStates: Set<InformationRequestState>,
    ): InformationRequestPolicyDecision =
        if (currentState in expectedStates) allow() else deny(InformationRequestErrorCatalog.STATE_INVALID)

    private fun nonTerminalStates(): Set<InformationRequestState> =
        InformationRequestState.entries.filterNot { it.isTerminal }.toSet()

    private fun allow(nextState: InformationRequestState? = null): InformationRequestPolicyDecision =
        InformationRequestPolicyDecision.Allow(nextState)

    private fun deny(reasonCode: String): InformationRequestPolicyDecision =
        InformationRequestPolicyDecision.Deny(reasonCode)
}
