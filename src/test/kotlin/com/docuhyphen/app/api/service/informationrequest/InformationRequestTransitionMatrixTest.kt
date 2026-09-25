package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.ExchangeStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InformationRequestTransitionMatrixTest
{
    @Test
    fun `initiated Exchange can hold drafts and issued requests but cannot accept responses or reviews`()
    {
        val parent = InformationRequestParentSnapshot(
            status = ExchangeStatus.INITIATED,
            lockedForUpdate = true,
        )

        assertAllowed(
            InformationRequestTransitionMatrix.canMutate(parent, null, InformationRequestMutation.CREATE_DRAFT),
            InformationRequestState.DRAFT,
        )
        assertAllowed(
            InformationRequestTransitionMatrix.canMutate(
                parent,
                InformationRequestState.DRAFT,
                InformationRequestMutation.ISSUE,
            ),
            InformationRequestState.ISSUED,
        )

        val response = InformationRequestTransitionMatrix.canMutate(
            parent,
            InformationRequestState.ISSUED,
            InformationRequestMutation.SAVE_RESPONSE,
        )
        val review = InformationRequestTransitionMatrix.canMutate(
            parent,
            InformationRequestState.SUBMITTED,
            InformationRequestMutation.START_REVIEW,
        )

        assertDenied(InformationRequestErrorCatalog.PARENT_STATE_INVALID, response)
        assertDenied(InformationRequestErrorCatalog.PARENT_STATE_INVALID, review)
    }

    @Test
    fun `accepted Exchange permits the planned nonterminal request transitions`()
    {
        val parent = InformationRequestParentSnapshot(
            status = ExchangeStatus.ACCEPTED_STARTED,
            lockedForUpdate = true,
        )

        assertAllowed(
            InformationRequestTransitionMatrix.canMutate(
                parent,
                InformationRequestState.ISSUED,
                InformationRequestMutation.RECORD_FIRST_VIEW,
            ),
            InformationRequestState.IN_PROGRESS,
        )
        assertAllowed(
            InformationRequestTransitionMatrix.canMutate(
                parent,
                InformationRequestState.IN_PROGRESS,
                InformationRequestMutation.SUBMIT,
            ),
        )
        assertAllowed(
            InformationRequestTransitionMatrix.canMutate(
                parent,
                InformationRequestState.SUBMITTED,
                InformationRequestMutation.START_REVIEW,
            ),
            InformationRequestState.UNDER_REVIEW,
        )
        assertAllowed(
            InformationRequestTransitionMatrix.canMutate(
                parent,
                InformationRequestState.UNDER_REVIEW,
                InformationRequestMutation.REQUEST_CORRECTION,
            ),
            InformationRequestState.CHANGES_REQUESTED,
        )
        assertAllowed(
            InformationRequestTransitionMatrix.canMutate(
                parent,
                InformationRequestState.UNDER_REVIEW,
                InformationRequestMutation.CLOSE,
            ),
            InformationRequestState.CLOSED,
        )
    }

    @Test
    fun `terminal request states are read only even while the parent is active`()
    {
        val parent = InformationRequestParentSnapshot(
            status = ExchangeStatus.ACCEPTED_STARTED,
            lockedForUpdate = true,
        )

        InformationRequestState.entries
            .filter { it.isTerminal }
            .forEach { state ->
                (InformationRequestMutation.entries - LINEAGE_MUTATIONS).forEach { mutation ->
                    assertDenied(
                        InformationRequestErrorCatalog.STATE_INVALID,
                        InformationRequestTransitionMatrix.canMutate(parent, state, mutation),
                    )
                }
            }
    }

    @Test
    fun `a submission and its withdrawal keep the collection state and a no-review closure closes an active request`()
    {
        val parent = InformationRequestParentSnapshot(status = ExchangeStatus.ACCEPTED_STARTED, lockedForUpdate = true)
        listOf(
            InformationRequestState.ISSUED,
            InformationRequestState.IN_PROGRESS,
            InformationRequestState.CHANGES_REQUESTED,
        ).forEach { state ->
            assertAllowed(InformationRequestTransitionMatrix.canMutate(parent, state, InformationRequestMutation.SUBMIT))
            assertAllowed(
                InformationRequestTransitionMatrix.canMutate(parent, state, InformationRequestMutation.WITHDRAW_SUBMISSION),
            )
            assertAllowed(
                InformationRequestTransitionMatrix.canMutate(parent, state, InformationRequestMutation.CLOSE),
                InformationRequestState.CLOSED,
            )
        }
        assertDenied(
            InformationRequestErrorCatalog.STATE_INVALID,
            InformationRequestTransitionMatrix.canMutate(parent, InformationRequestState.DRAFT, InformationRequestMutation.SUBMIT),
        )
        val initiated = InformationRequestParentSnapshot(status = ExchangeStatus.INITIATED, lockedForUpdate = true)
        assertDenied(
            InformationRequestErrorCatalog.PARENT_STATE_INVALID,
            InformationRequestTransitionMatrix.canMutate(
                initiated,
                InformationRequestState.ISSUED,
                InformationRequestMutation.WITHDRAW_SUBMISSION,
            ),
        )
    }

    @Test
    fun `a successor and a follow-up are recorded against issued or finished work but never a draft or abandoned one`()
    {
        val parent = InformationRequestParentSnapshot(status = ExchangeStatus.ACCEPTED_STARTED, lockedForUpdate = true)
        listOf(
            InformationRequestState.ISSUED,
            InformationRequestState.IN_PROGRESS,
            InformationRequestState.CLOSED,
            InformationRequestState.EXPIRED,
        ).forEach { state ->
            assertAllowed(InformationRequestTransitionMatrix.canMutate(parent, state, InformationRequestMutation.CREATE_SUCCESSOR))
        }
        listOf(InformationRequestState.DRAFT, InformationRequestState.CANCELLED, InformationRequestState.SUPERSEDED)
            .forEach { state ->
                assertDenied(
                    InformationRequestErrorCatalog.STATE_INVALID,
                    InformationRequestTransitionMatrix.canMutate(parent, state, InformationRequestMutation.CREATE_SUCCESSOR),
                )
            }
        listOf(
            InformationRequestState.DRAFT,
            InformationRequestState.ISSUED,
            InformationRequestState.IN_PROGRESS,
            InformationRequestState.CLOSED,
        ).forEach { state ->
            assertAllowed(InformationRequestTransitionMatrix.canMutate(parent, state, InformationRequestMutation.SCHEDULE_FOLLOW_UP))
        }
        listOf(InformationRequestState.CANCELLED, InformationRequestState.SUPERSEDED, InformationRequestState.EXPIRED)
            .forEach { state ->
                assertDenied(
                    InformationRequestErrorCatalog.STATE_INVALID,
                    InformationRequestTransitionMatrix.canMutate(parent, state, InformationRequestMutation.SCHEDULE_FOLLOW_UP),
                )
            }
        val deleted = InformationRequestParentSnapshot(status = ExchangeStatus.ACCEPTED_STARTED, deleted = true, lockedForUpdate = true)
        assertDenied(
            InformationRequestErrorCatalog.PARENT_STATE_INVALID,
            InformationRequestTransitionMatrix.canMutate(deleted, InformationRequestState.CLOSED, InformationRequestMutation.CREATE_SUCCESSOR),
        )
    }

    @Test
    fun `every parent state has explicit read visibility and external session effects`()
    {
        assertEquals(
            InformationRequestParentEffects(
                ownerRead = true,
                partyRead = true,
                externalSessionRead = true,
                recoveryRead = false,
                externalSessionEffect = InformationRequestExternalSessionEffect.PRESERVE,
                nonTerminalRequestEffect = InformationRequestNonTerminalEffect.NONE,
            ),
            InformationRequestTransitionMatrix.parentEffects(parent(ExchangeStatus.INITIATED)),
        )
        assertEquals(
            InformationRequestExternalSessionEffect.EXPIRE_NATURALLY,
            InformationRequestTransitionMatrix.parentEffects(parent(ExchangeStatus.ENDED)).externalSessionEffect,
        )
        assertTrue(InformationRequestTransitionMatrix.parentEffects(parent(ExchangeStatus.ENDED)).externalSessionRead)

        listOf(ExchangeStatus.REJECTED, ExchangeStatus.RESCINDED).forEach { status ->
            val effects = InformationRequestTransitionMatrix.parentEffects(parent(status))
            assertTrue(effects.ownerRead)
            assertFalse(effects.partyRead)
            assertFalse(effects.externalSessionRead)
            assertSame(InformationRequestExternalSessionEffect.REVOKE_NON_OWNER, effects.externalSessionEffect)
            assertSame(InformationRequestNonTerminalEffect.CANCEL, effects.nonTerminalRequestEffect)
        }

        val deleted = InformationRequestTransitionMatrix.parentEffects(
            InformationRequestParentSnapshot(
                status = ExchangeStatus.ACCEPTED_STARTED,
                deleted = true,
            ),
        )
        assertTrue(deleted.ownerRead)
        assertTrue(deleted.recoveryRead)
        assertFalse(deleted.externalSessionRead)
        assertSame(InformationRequestExternalSessionEffect.REVOKE_ALL, deleted.externalSessionEffect)
        assertSame(InformationRequestNonTerminalEffect.READ_ONLY, deleted.nonTerminalRequestEffect)
    }

    @Test
    fun `read visibility is actor specific for each parent lifecycle state`()
    {
        val ended = parent(ExchangeStatus.ENDED)
        val rejected = parent(ExchangeStatus.REJECTED)
        val deleted = InformationRequestParentSnapshot(
            status = ExchangeStatus.ACCEPTED_STARTED,
            deleted = true,
        )

        assertAllowed(InformationRequestTransitionMatrix.canRead(ended, InformationRequestReadActor.OWNER))
        assertAllowed(InformationRequestTransitionMatrix.canRead(ended, InformationRequestReadActor.EXTERNAL_SESSION))
        assertDenied(
            InformationRequestErrorCatalog.PARENT_STATE_INVALID,
            InformationRequestTransitionMatrix.canRead(rejected, InformationRequestReadActor.EXTERNAL_SESSION),
        )
        assertAllowed(InformationRequestTransitionMatrix.canRead(deleted, InformationRequestReadActor.OWNER))
        assertAllowed(InformationRequestTransitionMatrix.canRead(deleted, InformationRequestReadActor.RECOVERY))
        assertDenied(
            InformationRequestErrorCatalog.PARENT_STATE_INVALID,
            InformationRequestTransitionMatrix.canRead(deleted, InformationRequestReadActor.EXTERNAL_SESSION),
        )
    }

    @Test
    fun `parent lock is required before a request mutation can trust the parent state`()
    {
        val unlocked = InformationRequestParentSnapshot(status = ExchangeStatus.ACCEPTED_STARTED)

        assertDenied(
            InformationRequestErrorCatalog.PARENT_LOCK_REQUIRED,
            InformationRequestTransitionMatrix.canMutate(
                unlocked,
                InformationRequestState.ISSUED,
                InformationRequestMutation.SAVE_RESPONSE,
            ),
        )
    }

    @Test
    fun `ending an Exchange requires a locked parent all configured gates and no open nongating requests`()
    {
        val activeLocked = InformationRequestParentSnapshot(
            status = ExchangeStatus.ACCEPTED_STARTED,
            lockedForUpdate = true,
        )
        val activeUnlocked = InformationRequestParentSnapshot(status = ExchangeStatus.ACCEPTED_STARTED)

        assertDenied(
            InformationRequestErrorCatalog.PARENT_LOCK_REQUIRED,
            InformationRequestTransitionMatrix.canEndExchange(
                activeUnlocked,
                listOf(InformationRequestCompletionCandidate(InformationRequestState.CLOSED, gatesExchangeClosure = true)),
            ),
        )
        assertDenied(
            InformationRequestErrorCatalog.COMPLETION_GATES_UNSATISFIED,
            InformationRequestTransitionMatrix.canEndExchange(
                activeLocked,
                listOf(InformationRequestCompletionCandidate(InformationRequestState.SUBMITTED, gatesExchangeClosure = true)),
            ),
        )
        assertDenied(
            InformationRequestErrorCatalog.REMAINING_REQUESTS_REQUIRE_CANCELLATION,
            InformationRequestTransitionMatrix.canEndExchange(
                activeLocked,
                listOf(InformationRequestCompletionCandidate(InformationRequestState.ISSUED, gatesExchangeClosure = false)),
            ),
        )
        assertAllowed(
            InformationRequestTransitionMatrix.canEndExchange(
                activeLocked,
                listOf(
                    InformationRequestCompletionCandidate(InformationRequestState.CLOSED, gatesExchangeClosure = true),
                    InformationRequestCompletionCandidate(InformationRequestState.CANCELLED, gatesExchangeClosure = false),
                ),
            ),
        )
    }

    @Test
    fun `the matrix answers every planned state and mutation combination`()
    {
        val parent = InformationRequestParentSnapshot(
            status = ExchangeStatus.ACCEPTED_STARTED,
            lockedForUpdate = true,
        )

        InformationRequestState.entries.forEach { state ->
            InformationRequestMutation.entries.forEach { mutation ->
                val decision = InformationRequestTransitionMatrix.canMutate(parent, state, mutation)
                assertTrue(decision is InformationRequestPolicyDecision.Allow ||
                    decision is InformationRequestPolicyDecision.Deny)
            }
        }
    }

    private fun parent(status: ExchangeStatus) = InformationRequestParentSnapshot(status = status)

    private companion object
    {
        val LINEAGE_MUTATIONS = setOf(
            InformationRequestMutation.CREATE_SUCCESSOR,
            InformationRequestMutation.SCHEDULE_FOLLOW_UP,
        )
    }

    private fun assertAllowed(
        decision: InformationRequestPolicyDecision,
        nextState: InformationRequestState? = null,
    )
    {
        assertTrue(decision is InformationRequestPolicyDecision.Allow) { "Expected allow but was $decision" }
        assertEquals(nextState, (decision as InformationRequestPolicyDecision.Allow).nextState)
    }

    private fun assertDenied(reasonCode: String, decision: InformationRequestPolicyDecision)
    {
        assertTrue(decision is InformationRequestPolicyDecision.Deny) { "Expected deny but was $decision" }
        assertEquals(reasonCode, (decision as InformationRequestPolicyDecision.Deny).reasonCode)
    }
}
