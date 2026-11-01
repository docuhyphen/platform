package com.docuhyphen.app.api.service.informationrequest.lifecycle

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.model.auth.authz.ResourceRef
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.command.CommandReceiptRequest
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.capability.InformationRequestCapability
import com.docuhyphen.app.api.model.informationrequest.capability.InformationRequestCapabilityRequirement
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestContinuationAction
import com.docuhyphen.app.api.model.informationrequest.lifecycle.*
import com.docuhyphen.app.api.model.informationrequest.noauth.InformationRequestCredentialCutoff
import com.docuhyphen.app.api.model.subscription.SubscriptionContext
import com.docuhyphen.app.api.model.subscription.SubscriptionDenial
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.lifecycle.InformationRequestTransitionRepository
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.model.audit.AuditCaptureResult
import com.docuhyphen.app.api.model.audit.AuditEventDraft
import com.docuhyphen.app.api.model.audit.AuditOwnerScope
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.auth.authz.*
import com.docuhyphen.app.api.service.command.*
import com.docuhyphen.app.api.service.exchange.ExchangeLookupService
import com.docuhyphen.app.api.service.identity.PrincipalDisplayService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestContinuationGuard
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestEntitlementGuard
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestAuditActorLabelResolver
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestCapabilityExecutorRegistry
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestCapabilityNotInstalledException
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestTemplateCapabilityGate
import com.docuhyphen.app.api.service.informationrequest.clock.InformationRequestClockIssueRebaser
import com.docuhyphen.app.api.service.informationrequest.clock.InformationRequestClockStopper
import com.docuhyphen.app.api.service.informationrequest.execution.InformationRequestExecutionGrantService
import com.docuhyphen.app.api.service.informationrequest.execution.InformationRequestExecutionUsageReservationService
import com.docuhyphen.app.api.service.informationrequest.execution.RequestExecutionUsageExhaustedException
import com.docuhyphen.app.api.service.informationrequest.execution.issuedExecutionGrant
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestBootstrapShareLinkService
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestTrustedRecipientEligibilityService
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestFieldAudienceGuard
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestResponseStart
import com.docuhyphen.app.api.model.notification.DomainEvent
import com.docuhyphen.app.api.service.notification.DomainEventPublisher
import com.docuhyphen.app.api.service.subscription.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.*

class InformationRequestLifecycleServiceTest
{
    @Test
    fun `cancelling a draft records one transition advances the aggregate revision and replays safely`()
    {
        val fixture = Fixture()
        val command = CancelInformationRequestCommand(
            requestId = fixture.request.id,
            reasonCode = "author-withdrew-request",
            access = fixture.access,
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.aggregateOf(fixture.request)),
            idempotencyKey = "cancel-request-once",
        )

        val first = fixture.service.cancel(command)
        val replay = fixture.service.cancel(command)

        assertEquals(fixture.request.id, first.request.id)
        assertEquals(first.request.id, replay.request.id)
        assertEquals(first.requestETag, replay.requestETag)
        assertEquals(InformationRequestState.CANCELLED, fixture.request.state)
        assertEquals(2, fixture.request.aggregateRevision)
        assertTrue(fixture.request.cancelledAt != null)
        assertEquals(1, fixture.savedTransitions.size)
        val transition = fixture.savedTransitions.single()
        assertEquals(1, transition.sequenceNumber)
        assertEquals(InformationRequestState.DRAFT, transition.fromState)
        assertEquals(InformationRequestState.CANCELLED, transition.toState)
        assertEquals(InformationRequestMutation.CANCEL, transition.mutation)
        assertEquals("author-withdrew-request", transition.reasonCode)

        val receipt = fixture.receiptStore.receipts.single()
        assertEquals(ResourceType.INFORMATION_REQUEST, receipt.resourceType)
        assertEquals("cancel-information-request", receipt.operationName)
        assertEquals(ResourceType.INFORMATION_REQUEST, receipt.resultResourceType)
        assertEquals(fixture.request.id, receipt.resultResourceId)
        assertEquals(2, receipt.resultRevision)

        val audit = argumentCaptor<AuditEventDraft>()
        verify(fixture.auditRecorder).record(audit.capture())
        assertEquals(AuditEventType.INFORMATION_REQUEST_CANCEL.key, audit.firstValue.eventTypeKey)
        assertEquals(AuditOwnerScope.Organization(fixture.organizationId), audit.firstValue.owner)
        assertEquals(1, fixture.events.size)
        assertEquals(AuditEventType.INFORMATION_REQUEST_CANCEL.key, fixture.events.single().type)
        verify(fixture.authorizationService).authorize(
            fixture.access.principal,
            Action.INFORMATION_REQUEST_CANCEL,
            ResourceRef.informationRequest(fixture.request.id),
            fixture.access.authorization,
        )
    }

    @Test
    fun `a retried lifecycle command answers the request with the ETag it has now after a later change`() {
        val fixture = Fixture()
        val command = CancelInformationRequestCommand(
            requestId = fixture.request.id,
            reasonCode = "author-withdrew-request",
            access = fixture.access,
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.aggregateOf(fixture.request)),
            idempotencyKey = "cancel-request-before-a-later-change",
        )
        val first = fixture.service.cancel(command)
        fixture.request.aggregateRevision += 1

        val replay = fixture.service.cancel(command)

        assertNotEquals(first.requestETag, replay.requestETag)
        assertEquals(InformationRequestETag.aggregateOf(fixture.request), replay.requestETag)
        assertEquals(1, fixture.savedTransitions.size)
    }

    @Test
    fun `a lifecycle mutation locks the parent Exchange before the request row`()
    {
        val fixture = Fixture()

        fixture.service.cancel(
            CancelInformationRequestCommand(
                requestId = fixture.request.id,
                reasonCode = "author-withdrew-request",
                access = fixture.access,
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.aggregateOf(fixture.request)),
                idempotencyKey = "cancel-lock-order",
            ),
        )

        val locks = inOrder(fixture.exchangeLookup, fixture.requestRepository)
        locks.verify(fixture.exchangeLookup).lockExchangeForUpdate(fixture.request.exchangeId)
        locks.verify(fixture.requestRepository).findRequestByIdForUpdate(fixture.request.id)
    }

    @Test
    fun `every lifecycle command holds the parent Exchange and request locks before it looks up its receipt`() {
        val commands = mapOf<String, (Fixture) -> Unit>(
            "issue" to { fixture ->
                fixture.service.issue(
                    IssueInformationRequestCommand(
                        requestId = fixture.request.id,
                        access = fixture.access,
                        precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.aggregateOf(fixture.request)),
                        idempotencyKey = "issue-lock-before-receipt",
                    ),
                )
            },
            "cancel" to { fixture ->
                fixture.service.cancel(
                    CancelInformationRequestCommand(
                        requestId = fixture.request.id,
                        reasonCode = "author-withdrew-request",
                        access = fixture.access,
                        precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.aggregateOf(fixture.request)),
                        idempotencyKey = "cancel-lock-before-receipt",
                    ),
                )
            },
            "supersede" to { fixture ->
                fixture.service.supersede(
                    SupersedeInformationRequestCommand(
                        requestId = fixture.request.id,
                        supersededByRequestId = fixture.replacementRequest().id,
                        reasonCode = "replacement-request-created",
                        access = fixture.access,
                        precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.aggregateOf(fixture.request)),
                        idempotencyKey = "supersede-lock-before-receipt",
                    ),
                )
            },
        )

        commands.forEach { (name, run) ->
            val fixture = Fixture()
            val lockedAtLookup = mutableListOf<Boolean>()
            fixture.receiptStore.onLookup = {
                lockedAtLookup += fixture.invoked(fixture.exchangeLookup, "lockExchangeForUpdate") &&
                        fixture.invoked(fixture.requestRepository, "findRequestByIdForUpdate")
            }

            run(fixture)

            assertEquals(listOf(true), lockedAtLookup, "$name looked up its receipt before locking")
        }
    }

    @Test
    fun `cancelling rechecks the locked parent Exchange before writing history`()
    {
        val fixture = Fixture(parentStatus = ExchangeStatus.ENDED)
        val command = CancelInformationRequestCommand(
            requestId = fixture.request.id,
            reasonCode = "parent-ended",
            access = fixture.access,
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.aggregateOf(fixture.request)),
            idempotencyKey = "cancel-after-parent-end",
        )

        val failure = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.cancel(command)
        }

        assertEquals(InformationRequestErrorCatalog.PARENT_STATE_INVALID, failure.reasonCode)
        assertEquals(InformationRequestState.DRAFT, fixture.request.state)
        assertEquals(1, fixture.request.aggregateRevision)
        assertTrue(fixture.savedTransitions.isEmpty())
        verify(fixture.requestRepository, never()).update(any())
    }

    @Test
    fun `cancelling requires a current aggregate ETag before writing history`()
    {
        val fixture = Fixture()
        val command = CancelInformationRequestCommand(
            requestId = fixture.request.id,
            reasonCode = "stale-edit",
            access = fixture.access,
            precondition = CommandPrecondition.ExpectedRevision("\"aggregate:${fixture.request.id}:0\""),
            idempotencyKey = "cancel-with-stale-etag",
        )

        val failure = assertThrows(CommandPreconditionException::class.java) {
            fixture.service.cancel(command)
        }

        assertEquals(CommandPreconditionException.Kind.STALE, failure.kind)
        assertEquals(InformationRequestState.DRAFT, fixture.request.state)
        assertTrue(fixture.savedTransitions.isEmpty())
        verify(fixture.requestRepository, never()).update(any())
    }

    @Test
    fun `reusing a lifecycle command idempotency key with a different body is rejected before mutation`()
    {
        val fixture = Fixture()
        val original = CancelInformationRequestCommand(
            requestId = fixture.request.id,
            reasonCode = "first-reason",
            access = fixture.access,
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.aggregateOf(fixture.request)),
            idempotencyKey = "cancel-conflict-key",
        )
        fixture.service.cancel(original)

        val conflictingReplay = original.copy(reasonCode = "second-reason")

        assertThrows(CommandReceiptConflictException::class.java) {
            fixture.service.cancel(conflictingReplay)
        }
        assertEquals(InformationRequestState.CANCELLED, fixture.request.state)
        assertEquals(1, fixture.savedTransitions.size)
        verify(fixture.requestRepository).update(any())
    }

    @Test
    fun `superseding a request points to the replacement and records a named transition`()
    {
        val fixture = Fixture()
        val replacement = fixture.replacementRequest()
        val command = SupersedeInformationRequestCommand(
            requestId = fixture.request.id,
            supersededByRequestId = replacement.id,
            reasonCode = "replacement-request-created",
            access = fixture.access,
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.aggregateOf(fixture.request)),
            idempotencyKey = "supersede-request-once",
        )

        val result = fixture.service.supersede(command)

        assertEquals(fixture.request.id, result.request.id)
        assertEquals(InformationRequestState.SUPERSEDED, fixture.request.state)
        assertEquals(replacement.id, fixture.request.supersededByRequestId)
        assertEquals(2, fixture.request.aggregateRevision)
        assertEquals(1, fixture.savedTransitions.size)
        assertEquals(InformationRequestMutation.SUPERSEDE, fixture.savedTransitions.single().mutation)
        assertEquals(InformationRequestState.SUPERSEDED, fixture.savedTransitions.single().toState)
        assertEquals(AuditEventType.INFORMATION_REQUEST_SUPERSEDE.key, fixture.events.single().type)
        verify(fixture.authorizationService).authorize(
            fixture.access.principal,
            Action.INFORMATION_REQUEST_SUPERSEDE,
            ResourceRef.informationRequest(fixture.request.id),
            fixture.access.authorization,
        )
    }

    @Test
    fun `cancelling a draft request with no execution grant checks only for an operational suspension`()
    {
        val fixture = Fixture()
        val command = CancelInformationRequestCommand(
            requestId = fixture.request.id,
            access = fixture.access,
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.aggregateOf(fixture.request)),
            idempotencyKey = "cancel-draft-checks-suspension-only",
        )

        fixture.service.cancel(command)

        verify(fixture.entitlementGuard).requireNotOperationallySuspended(fixture.exchange)
        verify(fixture.entitlementGuard, never()).requireRequestMutation(any())
    }

    @Test
    fun `cancelling an already-issued request answers to its frozen grant instead of rechecking the live subscription`()
    {
        val fixture = Fixture()
        fixture.request.state = InformationRequestState.ISSUED
        whenever(fixture.executionGrantService.findForRequest(fixture.request.id)).thenReturn(
            issuedExecutionGrant().apply { requestId = fixture.request.id },
        )
        val command = CancelInformationRequestCommand(
            requestId = fixture.request.id,
            access = fixture.access,
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.aggregateOf(fixture.request)),
            idempotencyKey = "cancel-issued-request-skips-live-subscription",
        )

        val result = fixture.service.cancel(command)

        assertEquals(InformationRequestState.CANCELLED, result.request.state)
        verify(fixture.entitlementGuard, never()).requireRequestMutation(any())
    }

    @Test
    fun `cancelling an already-issued request still checks for an emergency operational suspension`()
    {
        val fixture = Fixture()
        fixture.request.state = InformationRequestState.ISSUED
        whenever(fixture.executionGrantService.findForRequest(fixture.request.id)).thenReturn(
            issuedExecutionGrant().apply { requestId = fixture.request.id },
        )
        val command = CancelInformationRequestCommand(
            requestId = fixture.request.id,
            access = fixture.access,
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.aggregateOf(fixture.request)),
            idempotencyKey = "cancel-issued-request-checks-suspension",
        )

        fixture.service.cancel(command)

        verify(fixture.entitlementGuard).requireNotOperationallySuspended(fixture.exchange)
    }

    @Test
    fun `an emergency operational suspension blocks a continuation mutation even though a grant already exists`()
    {
        val fixture = Fixture()
        fixture.request.state = InformationRequestState.ISSUED
        whenever(fixture.executionGrantService.findForRequest(fixture.request.id)).thenReturn(
            issuedExecutionGrant().apply { requestId = fixture.request.id },
        )
        val denial = SubscriptionDenial(
            reason = SubscriptionDenialReason.SUBSCRIPTION_SUSPENDED,
            planCode = PlanCode.BUSINESS,
            ownerType = SubscriptionOwnerType.ORGANIZATION,
            message = "This subscription is suspended.",
        )
        whenever(fixture.entitlementGuard.requireNotOperationallySuspended(fixture.exchange))
            .thenThrow(SubscriptionDenialException(denial))
        val command = CancelInformationRequestCommand(
            requestId = fixture.request.id,
            access = fixture.access,
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.aggregateOf(fixture.request)),
            idempotencyKey = "cancel-issued-request-suspended",
        )

        assertThrows(SubscriptionDenialException::class.java) { fixture.service.cancel(command) }
        assertEquals(InformationRequestState.ISSUED, fixture.request.state)
        verify(fixture.requestRepository, never()).update(any())
    }

    @Test
    fun `an owner may still cancel a request whose execution grant has been revoked`() {
        val fixture = Fixture()
        fixture.request.state = InformationRequestState.ISSUED
        fixture.revokeGrant()
        val command = CancelInformationRequestCommand(
            requestId = fixture.request.id,
            reasonCode = "issued-in-error",
            access = fixture.access,
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.aggregateOf(fixture.request)),
            idempotencyKey = "cancel-issued-request-revoked",
        )

        val result = fixture.service.cancel(command)

        assertEquals(InformationRequestState.CANCELLED, result.request.state)
        assertEquals(InformationRequestMutation.CANCEL, fixture.savedTransitions.single().mutation)
        verify(fixture.entitlementGuard).requireNotOperationallySuspended(fixture.exchange)
    }

    @Test
    fun `an owner may still supersede a request whose execution grant has been revoked`() {
        val fixture = Fixture()
        fixture.request.state = InformationRequestState.ISSUED
        fixture.revokeGrant()
        val replacement = fixture.replacementRequest()

        val result = fixture.service.supersede(
            SupersedeInformationRequestCommand(
                requestId = fixture.request.id,
                supersededByRequestId = replacement.id,
                reasonCode = "replaced",
                access = fixture.access,
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.aggregateOf(fixture.request)),
                idempotencyKey = "supersede-issued-request-revoked",
            ),
        )

        assertEquals(InformationRequestState.SUPERSEDED, result.request.state)
        assertEquals(replacement.id, fixture.request.supersededByRequestId)
    }

    @Test
    fun `an owner cancellation is refused when the request's execution grant does not list it`()
    {
        val fixture = Fixture()
        fixture.request.state = InformationRequestState.ISSUED
        whenever(fixture.executionGrantService.findForRequest(fixture.request.id)).thenReturn(
            issuedExecutionGrant().apply {
                requestId = fixture.request.id
                continuationActions = arrayOf(InformationRequestContinuationAction.SUPERSEDE.name)
            },
        )
        val command = CancelInformationRequestCommand(
            requestId = fixture.request.id,
            access = fixture.access,
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.aggregateOf(fixture.request)),
            idempotencyKey = "cancel-not-granted",
        )

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) { fixture.service.cancel(command) }

        assertEquals(InformationRequestErrorCatalog.CONTINUATION_NOT_GRANTED, refusal.reasonCode)
        assertEquals(InformationRequestState.ISSUED, fixture.request.state)
        verify(fixture.requestRepository, never()).update(any())
    }

    @Test
    fun `a revoked execution grant still refuses issuing and an operational suspension still refuses cancelling`() {
        val revoked = Fixture()
        revoked.revokeGrant()

        val issue = assertThrows(InformationRequestLifecycleException::class.java) {
            revoked.service.issue(
                IssueInformationRequestCommand(
                    requestId = revoked.request.id,
                    access = revoked.access,
                    precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.aggregateOf(revoked.request)),
                    idempotencyKey = "issue-under-revoked-grant",
                ),
            )
        }
        assertEquals(InformationRequestErrorCatalog.EXECUTION_GRANT_REVOKED, issue.reasonCode)
        assertEquals(InformationRequestState.DRAFT, revoked.request.state)

        val suspended = Fixture()
        suspended.request.state = InformationRequestState.ISSUED
        suspended.revokeGrant()
        whenever(suspended.entitlementGuard.requireNotOperationallySuspended(suspended.exchange)).thenThrow(
            SubscriptionDenialException(
                SubscriptionDenial(
                    reason = SubscriptionDenialReason.SUBSCRIPTION_SUSPENDED,
                    planCode = PlanCode.BUSINESS,
                    ownerType = SubscriptionOwnerType.ORGANIZATION,
                    message = "This subscription is suspended.",
                ),
            ),
        )
        assertThrows(SubscriptionDenialException::class.java) {
            suspended.service.cancel(
                CancelInformationRequestCommand(
                    requestId = suspended.request.id,
                    access = suspended.access,
                    precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.aggregateOf(suspended.request)),
                    idempotencyKey = "cancel-suspended-revoked",
                ),
            )
        }
        assertEquals(InformationRequestState.ISSUED, suspended.request.state)
        verify(suspended.requestRepository, never()).update(any())
    }

    @Test
    fun `a cancellation or supersession reason that is not a short machine code is refused before anything is recorded`() {
        val fixture = Fixture()
        val replacement = fixture.replacementRequest()
        val refused = listOf(
            "Customer asked us to stop",
            "Recorded-Item",
            "-leading-dash",
            "a".repeat(InformationRequestReasonCodes.MAXIMUM_LENGTH + 1),
        )

        refused.forEachIndexed { index, reason ->
            val cancel = assertThrows(InformationRequestCommandRequestException::class.java) {
                fixture.service.cancel(
                    CancelInformationRequestCommand(
                        requestId = fixture.request.id,
                        reasonCode = reason,
                        access = fixture.access,
                        precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.aggregateOf(fixture.request)),
                        idempotencyKey = "cancel-free-text-$index",
                    ),
                )
            }
            val supersede = assertThrows(InformationRequestCommandRequestException::class.java) {
                fixture.service.supersede(
                    SupersedeInformationRequestCommand(
                        requestId = fixture.request.id,
                        supersededByRequestId = replacement.id,
                        reasonCode = reason,
                        access = fixture.access,
                        precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.aggregateOf(fixture.request)),
                        idempotencyKey = "supersede-free-text-$index",
                    ),
                )
            }
            assertEquals(InformationRequestErrorCatalog.REASON_CODE_INVALID, cancel.reasonCode)
            assertEquals(InformationRequestErrorCatalog.REASON_CODE_INVALID, supersede.reasonCode)
        }

        assertEquals(InformationRequestState.DRAFT, fixture.request.state)
        assertTrue(fixture.savedTransitions.isEmpty())
        assertTrue(fixture.receiptStore.receipts.isEmpty())
        verify(fixture.auditRecorder, never()).record(any())
    }

    @Test
    fun `a machine reason code of the maximum length is recorded without surrounding space`() {
        val fixture = Fixture()
        val code = "x".repeat(InformationRequestReasonCodes.MAXIMUM_LENGTH)

        fixture.service.cancel(
            CancelInformationRequestCommand(
                requestId = fixture.request.id,
                reasonCode = " $code ",
                access = fixture.access,
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.aggregateOf(fixture.request)),
                idempotencyKey = "cancel-longest-code",
            ),
        )

        assertEquals(code, fixture.savedTransitions.single().reasonCode)
    }

    @Test
    fun `issuing, cancelling, and superseding are refused when their audit record cannot be captured`() {
        val issued = Fixture().also { it.degradeAudit() }
        val cancelled = Fixture().also { it.degradeAudit() }
        val superseded = Fixture().also { it.degradeAudit() }

        assertThrows(com.docuhyphen.app.api.service.audit.AuditCaptureFailedException::class.java) {
            issued.service.issue(
                IssueInformationRequestCommand(
                    requestId = issued.request.id,
                    access = issued.access,
                    precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.aggregateOf(issued.request)),
                    idempotencyKey = "issue-without-audit",
                ),
            )
        }
        assertThrows(com.docuhyphen.app.api.service.audit.AuditCaptureFailedException::class.java) {
            cancelled.service.cancel(
                CancelInformationRequestCommand(
                    requestId = cancelled.request.id,
                    access = cancelled.access,
                    precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.aggregateOf(cancelled.request)),
                    idempotencyKey = "cancel-without-audit",
                ),
            )
        }
        assertThrows(com.docuhyphen.app.api.service.audit.AuditCaptureFailedException::class.java) {
            superseded.service.supersede(
                SupersedeInformationRequestCommand(
                    requestId = superseded.request.id,
                    supersededByRequestId = superseded.replacementRequest().id,
                    access = superseded.access,
                    precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.aggregateOf(superseded.request)),
                    idempotencyKey = "supersede-without-audit",
                ),
            )
        }
    }

    @Test
    fun `cancelling and superseding revoke the request's access links after the transition and leave live sessions to expire`() {
        val cancelled = Fixture()
        val cancelledRevocations = cancelled.recordLinkRevocations()
        val cancel = CancelInformationRequestCommand(
            requestId = cancelled.request.id,
            reasonCode = "author-withdrew-request",
            access = cancelled.access,
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.aggregateOf(cancelled.request)),
            idempotencyKey = "cancel-revokes-links",
        )
        cancelled.service.cancel(cancel)
        cancelled.service.cancel(cancel)
        verify(cancelled.bootstrapLinks).revokeLinksKeepingSessions(
            cancelled.request,
            InformationRequestCredentialCutoff(
                InformationRequestAuditActor.of(cancelled.access),
                InformationRequestCredentialCutoff.REQUEST_CANCELLED,
            ),
        )
        assertEquals(listOf("CANCELLED after CANCEL"), cancelledRevocations)

        val superseded = Fixture()
        val supersededRevocations = superseded.recordLinkRevocations()
        superseded.service.supersede(
            SupersedeInformationRequestCommand(
                requestId = superseded.request.id,
                supersededByRequestId = superseded.replacementRequest().id,
                access = superseded.access,
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.aggregateOf(superseded.request)),
                idempotencyKey = "supersede-revokes-links",
            ),
        )
        verify(superseded.bootstrapLinks).revokeLinksKeepingSessions(
            superseded.request,
            InformationRequestCredentialCutoff(
                InformationRequestAuditActor.of(superseded.access),
                InformationRequestCredentialCutoff.REQUEST_SUPERSEDED,
            ),
        )
        assertEquals(listOf("SUPERSEDED after SUPERSEDE"), supersededRevocations)

        val issued = Fixture()
        issued.service.issue(
            IssueInformationRequestCommand(
                requestId = issued.request.id,
                access = issued.access,
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.aggregateOf(issued.request)),
                idempotencyKey = "issue-keeps-links",
            ),
        )
        verifyNoInteractions(issued.bootstrapLinks)
    }

    @Test
    fun `cancelling and superseding stop the request's clocks at the finishing instant on behalf of the caller`() {
        val cancelled = Fixture()
        cancelled.service.cancel(
            CancelInformationRequestCommand(
                requestId = cancelled.request.id,
                access = cancelled.access,
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.aggregateOf(cancelled.request)),
                idempotencyKey = "cancel-stops-clocks",
            ),
        )
        verify(cancelled.clockStopper).stopForFinishedRequest(
            cancelled.request,
            cancelled.access.principal,
            requireNotNull(cancelled.request.cancelledAt).toInstant(),
            InformationRequestClockStopper.REQUEST_FINISHED,
        )

        val superseded = Fixture()
        val replacement = superseded.replacementRequest()
        superseded.service.supersede(
            SupersedeInformationRequestCommand(
                requestId = superseded.request.id,
                supersededByRequestId = replacement.id,
                access = superseded.access,
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.aggregateOf(superseded.request)),
                idempotencyKey = "supersede-stops-clocks",
            ),
        )
        verify(superseded.clockStopper).stopForFinishedRequest(
            superseded.request,
            superseded.access.principal,
            requireNotNull(superseded.request.supersededAt).toInstant(),
            InformationRequestClockStopper.REQUEST_FINISHED,
        )

        val issued = Fixture()
        issued.service.issue(
            IssueInformationRequestCommand(
                requestId = issued.request.id,
                access = issued.access,
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.aggregateOf(issued.request)),
                idempotencyKey = "issue-keeps-clocks",
            ),
        )
        verify(issued.clockStopper, never()).stopForFinishedRequest(any(), any(), any(), any())
    }

    @Test
    fun `issuing is refused while a party's trusted recipient is suspended, before any grant is issued`() {
        val fixture = Fixture()
        whenever(fixture.trustedRecipients.requireIssuable(fixture.request)).thenThrow(
            InformationRequestLifecycleException(InformationRequestErrorCatalog.TRUST_SUSPENDED, "suspended"),
        )
        val command = IssueInformationRequestCommand(
            requestId = fixture.request.id,
            access = fixture.access,
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.aggregateOf(fixture.request)),
            idempotencyKey = "issue-request-trust-suspended",
        )

        val refused = assertThrows(InformationRequestLifecycleException::class.java) { fixture.service.issue(command) }

        assertEquals(InformationRequestErrorCatalog.TRUST_SUSPENDED, refused.reasonCode)
        assertEquals(InformationRequestState.DRAFT, fixture.request.state)
        verify(fixture.executionGrantService, never()).issueGrant(any(), any())
    }

    @Test
    fun `issuing is refused while a party could not answer a collected Field, before any grant is issued`() {
        val fixture = Fixture()
        whenever(
            fixture.fieldAudience.requireAnswerableByParties(
                fixture.request,
                fixture.request.templateVersionId
            )
        ).thenThrow(
            InformationRequestLifecycleException(InformationRequestErrorCatalog.FIELD_OUTSIDE_AUDIENCE, "outside"),
        )
        val command = IssueInformationRequestCommand(
            requestId = fixture.request.id,
            access = fixture.access,
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.aggregateOf(fixture.request)),
            idempotencyKey = "issue-request-field-outside-audience",
        )

        val refused = assertThrows(InformationRequestLifecycleException::class.java) { fixture.service.issue(command) }

        assertEquals(InformationRequestErrorCatalog.FIELD_OUTSIDE_AUDIENCE, refused.reasonCode)
        assertEquals(InformationRequestState.DRAFT, fixture.request.state)
        verify(fixture.executionGrantService, never()).issueGrant(any(), any())
    }

    @Test
    fun `issuing a request freezes an execution grant against the parent Exchange`()
    {
        val fixture = Fixture()
        val command = IssueInformationRequestCommand(
            requestId = fixture.request.id,
            access = fixture.access,
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.aggregateOf(fixture.request)),
            idempotencyKey = "issue-request-freezes-grant",
        )

        val result = fixture.service.issue(command)

        assertEquals(InformationRequestState.ISSUED, result.request.state)
        verify(fixture.executionGrantService).issueGrant(fixture.request, fixture.exchange)
        verify(fixture.clockRebaser).rebaseOnIssue(
            fixture.request,
            requireNotNull(fixture.request.issuedAt).toInstant(),
            fixture.access.principal,
        )
    }

    @Test
    fun `issuing a request on an Exchange that awaits acceptance leaves its clocks to count from the acceptance`() {
        val fixture = Fixture(parentStatus = ExchangeStatus.INITIATED)
        val command = IssueInformationRequestCommand(
            requestId = fixture.request.id,
            access = fixture.access,
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.aggregateOf(fixture.request)),
            idempotencyKey = "issue-request-awaiting-acceptance",
        )

        val result = fixture.service.issue(command)

        assertEquals(InformationRequestState.ISSUED, result.request.state)
        verify(fixture.clockRebaser, never()).rebaseOnIssue(any(), any(), any())
    }

    @Test
    fun `issuing a request consumes frozen recipient capacity for active acting parties`()
    {
        val fixture = Fixture()
        val contributor = fixture.activeActingParty(InformationRequestShareRoleKey.CONTRIBUTOR)
        val reviewer = fixture.activeActingParty(InformationRequestShareRoleKey.REVIEWER)
        val subject = fixture.subjectParty()
        whenever(fixture.partyRepository.findActiveForRequest(fixture.request.id))
            .thenReturn(listOf(contributor, reviewer, subject))
        val grant = RequestExecutionGrant().apply {
            id = UUID.randomUUID()
            requestId = fixture.request.id
            actingPartyCap = 2
        }
        whenever(fixture.executionGrantService.issueGrant(fixture.request, fixture.exchange)).thenReturn(grant)
        val contributorReservation = RequestExecutionUsageReservation().apply { id = UUID.randomUUID() }
        val reviewerReservation = RequestExecutionUsageReservation().apply { id = UUID.randomUUID() }
        whenever(
            fixture.executionUsageReservationService.reserve(
                grant.id,
                RequestExecutionUsageKind.ACTING_PARTY,
                "information_request.party|${contributor.id}",
                1L,
            ),
        ).thenReturn(contributorReservation)
        whenever(
            fixture.executionUsageReservationService.reserve(
                grant.id,
                RequestExecutionUsageKind.ACTING_PARTY,
                "information_request.party|${reviewer.id}",
                1L,
            ),
        ).thenReturn(reviewerReservation)
        val command = IssueInformationRequestCommand(
            requestId = fixture.request.id,
            access = fixture.access,
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.aggregateOf(fixture.request)),
            idempotencyKey = "issue-request-consumes-party-capacity",
        )

        fixture.service.issue(command)

        verify(fixture.executionUsageReservationService).consume(contributorReservation.id)
        verify(fixture.executionUsageReservationService).consume(reviewerReservation.id)
        verify(fixture.executionUsageReservationService, never()).reserve(
            grant.id,
            RequestExecutionUsageKind.ACTING_PARTY,
            "information_request.party|${subject.id}",
            1L,
        )
    }

    @Test
    fun `issuing rolls back consumed recipient capacity when a later party exceeds the frozen cap`()
    {
        val fixture = Fixture()
        val contributor = fixture.activeActingParty(InformationRequestShareRoleKey.CONTRIBUTOR)
        val reviewer = fixture.activeActingParty(InformationRequestShareRoleKey.REVIEWER)
        whenever(fixture.partyRepository.findActiveForRequest(fixture.request.id))
            .thenReturn(listOf(contributor, reviewer))
        val grant = RequestExecutionGrant().apply {
            id = UUID.randomUUID()
            requestId = fixture.request.id
            actingPartyCap = 1
        }
        whenever(fixture.executionGrantService.issueGrant(fixture.request, fixture.exchange)).thenReturn(grant)
        val contributorReservation = RequestExecutionUsageReservation().apply { id = UUID.randomUUID() }
        whenever(
            fixture.executionUsageReservationService.reserve(
                grant.id,
                RequestExecutionUsageKind.ACTING_PARTY,
                "information_request.party|${contributor.id}",
                1L,
            ),
        ).thenReturn(contributorReservation)
        whenever(
            fixture.executionUsageReservationService.reserve(
                grant.id,
                RequestExecutionUsageKind.ACTING_PARTY,
                "information_request.party|${reviewer.id}",
                1L,
            ),
        ).thenThrow(
            RequestExecutionUsageExhaustedException(
                grantId = grant.id,
                usageKind = RequestExecutionUsageKind.ACTING_PARTY,
                cap = 1L,
                activeUsage = 1L,
                requested = 1L,
            ),
        )
        val command = IssueInformationRequestCommand(
            requestId = fixture.request.id,
            access = fixture.access,
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.aggregateOf(fixture.request)),
            idempotencyKey = "issue-request-rolls-back-party-capacity",
        )

        assertThrows(RequestExecutionUsageExhaustedException::class.java) {
            fixture.service.issue(command)
        }

        verify(fixture.executionUsageReservationService).consume(contributorReservation.id)
        verify(fixture.executionUsageReservationService).rollback(contributorReservation.id)
        verify(fixture.requestRepository, never()).update(any())
    }

    @Test
    fun `issuing remains denied when the Template Version has unserved runtime capabilities`()
    {
        val fixture = Fixture()
        fixture.unservedCapability(InformationRequestCapability.RESPONSE_REVIEW)
        val command = IssueInformationRequestCommand(
            requestId = fixture.request.id,
            access = fixture.access,
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.aggregateOf(fixture.request)),
            idempotencyKey = "issue-request-once",
        )

        assertThrows(InformationRequestCapabilityNotInstalledException::class.java) {
            fixture.service.issue(command)
        }

        assertEquals(InformationRequestState.DRAFT, fixture.request.state)
        assertNull(fixture.request.issuedAt)
        assertTrue(fixture.savedTransitions.isEmpty())
        verify(fixture.requestRepository, never()).update(any())
    }

    @Test
    fun `issuing is denied and freezes no execution grant when the entitlement guard refuses it`()
    {
        val deniedGuard = mock<InformationRequestEntitlementGuard>()
        whenever(deniedGuard.requireRequestMutation(any())).thenThrow(
            SubscriptionDenialException(
                SubscriptionDenial(
                    reason = SubscriptionDenialReason.FEATURE_NOT_INCLUDED,
                    planCode = PlanCode.BUSINESS,
                    ownerType = SubscriptionOwnerType.ORGANIZATION,
                    message = "This capability is not yet released.",
                ),
            ),
        )
        val fixture = Fixture(entitlementGuardFactory = { deniedGuard })
        val command = IssueInformationRequestCommand(
            requestId = fixture.request.id,
            access = fixture.access,
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.aggregateOf(fixture.request)),
            idempotencyKey = "issue-denied-by-entitlement-guard",
        )

        assertThrows(SubscriptionDenialException::class.java) { fixture.service.issue(command) }

        assertEquals(InformationRequestState.DRAFT, fixture.request.state)
        assertNull(fixture.request.issuedAt)
        verify(fixture.executionGrantService, never()).issueGrant(any(), any())
        verify(fixture.requestRepository, never()).update(any())
    }

    @Test
    fun `base plan permits issuing and the issued request remains cancellable without an admin override`()
    {
        val fixture = Fixture(
            entitlementGuardFactory = { organizationId -> realGuard(organizationId, commercialGrant = false) },
        )

        assertIssuanceAndCancellationAllowed(fixture)
    }

    private fun assertIssuanceAndCancellationAllowed(fixture: Fixture)
    {
        val issueCommand = IssueInformationRequestCommand(
            requestId = fixture.request.id,
            access = fixture.access,
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.aggregateOf(fixture.request)),
            idempotencyKey = "issue-without-admin-override",
        )

        val issueResult = fixture.service.issue(issueCommand)

        assertEquals(InformationRequestState.ISSUED, issueResult.request.state)
        verify(fixture.executionGrantService).issueGrant(fixture.request, fixture.exchange)

        whenever(fixture.executionGrantService.findForRequest(fixture.request.id)).thenReturn(
            issuedExecutionGrant().apply { requestId = fixture.request.id },
        )
        val cancelCommand = CancelInformationRequestCommand(
            requestId = fixture.request.id,
            access = fixture.access,
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.aggregateOf(fixture.request)),
            idempotencyKey = "cancel-issued-without-admin-override",
        )

        val result = fixture.service.cancel(cancelCommand)

        assertEquals(InformationRequestState.CANCELLED, result.request.state)
    }

    private fun realGuard(
        organizationId: UUID,
        commercialGrant: Boolean,
    ): InformationRequestEntitlementGuard
    {
        val policyService = mock<SubscriptionPolicyService>()
        whenever(policyService.findOrganizationPolicy(organizationId)).thenReturn(
            OrganizationSubscriptionPolicy().apply {
                tierCode = PlanCode.BUSINESS.name
                subscriptionStatus = SubscriptionStatus.ACTIVE.name
            },
        )
        whenever(policyService.featureOverrides(SubscriptionContext.forOrganization(organizationId))).thenReturn(
            if (commercialGrant) mapOf(PlanFeature.INFORMATION_REQUESTS to true) else emptyMap(),
        )
        return InformationRequestEntitlementGuard(
            requestRepository = mock(),
            subscriptionAccessService = SubscriptionAccessService(
                subscriptionPolicyService = policyService,
                subscriptionUsageService = SubscriptionUsageService(
                    mock<ExchangeUsageCounter>(),
                    mock<OrganizationSeatCounter>(),
                ),
                enforcementConfigService = SubscriptionEnforcementConfigService(SubscriptionEnforcementMode.ENFORCE.name),
            ),
        )
    }

    private class Fixture(
        parentStatus: ExchangeStatus = ExchangeStatus.ACCEPTED_STARTED,
        entitlementGuardFactory: (UUID) -> InformationRequestEntitlementGuard = { mock() },
    )
    {
        val organizationId: UUID = UUID.randomUUID()
        private val actorId: UUID = UUID.randomUUID()
        val access = RequestAccessContext(
            PrincipalRef.user(actorId),
            AuthorizationContext(activeOrgId = organizationId),
        )
        val request = InformationRequest().apply {
            id = UUID.randomUUID()
            exchangeId = UUID.randomUUID()
            templateVersionId = UUID.randomUUID()
            ownerType = InformationRequestOwnerType.ORGANIZATION
            ownerOrganizationId = organizationId
            state = InformationRequestState.DRAFT
            aggregateRevision = 1
        }
        val exchange = Exchange().apply {
            id = request.exchangeId
            ownerOrganizationId = organizationId
            status = parentStatus
            isDeleted = false
        }
        private val savedRequests = mutableMapOf(request.id to request)
        val savedTransitions = mutableListOf<InformationRequestTransition>()
        val events = mutableListOf<DomainEvent>()
        val requestRepository = mock<InformationRequestRepository>()
        val exchangeLookup = mock<ExchangeLookupService>()
        private val transitionRepository = mock<InformationRequestTransitionRepository>()
        private val capabilityRepository = mock<InformationRequestTemplateVersionCapabilityRepository>()
        private val executorRegistry = mock<InformationRequestCapabilityExecutorRegistry>()
        val authorizationService = mock<AuthorizationService>()
        val auditRecorder = mock<AuditRecorder>()
        val executionGrantService = mock<InformationRequestExecutionGrantService>()
        val partyRepository = mock<InformationRequestPartyRepository>()
        val executionUsageReservationService = mock<InformationRequestExecutionUsageReservationService>()
        val clockStopper = mock<InformationRequestClockStopper>()
        val clockRebaser = mock<InformationRequestClockIssueRebaser>()
        val trustedRecipients = mock<InformationRequestTrustedRecipientEligibilityService>()
        val bootstrapLinks = mock<InformationRequestBootstrapShareLinkService>()
        val fieldAudience = mock<InformationRequestFieldAudienceGuard>()
        val entitlementGuard = entitlementGuardFactory(organizationId)
        val receiptStore = InMemoryLifecycleReceiptStore()
        private val eventPublisher = CapturingDomainEventPublisher(events)
        private val capabilityGate = InformationRequestTemplateCapabilityGate(executorRegistry, capabilityRepository)
        private val transitionHistory = InformationRequestTransitionHistoryService(
            transitionRepository = transitionRepository,
            auditRecorder = auditRecorder,
            domainEventPublisher = eventPublisher,
            responseStart = InformationRequestResponseStart(requestRepository, java.time.Clock.systemUTC()),
            actorLabels = InformationRequestAuditActorLabelResolver(mock<PrincipalDisplayService>()),
            receiptScope = CommandReceiptScope(),
        )
        val service = InformationRequestLifecycleService(
            requestRepository = requestRepository,
            exchangeLookup = exchangeLookup,
            authorizationService = authorizationService,
            commandReceiptService = CommandReceiptService(receiptStore, CommandReceiptScope()),
            capabilityGate = capabilityGate,
            transitionHistory = transitionHistory,
            continuationGuard = InformationRequestContinuationGuard(executionGrantService, entitlementGuard),
            executionGrantService = executionGrantService,
            partyRepository = partyRepository,
            executionUsageReservationService = executionUsageReservationService,
            clockStopper = clockStopper,
            clockRebaser = clockRebaser,
            trustedRecipients = trustedRecipients,
            bootstrapLinks = bootstrapLinks,
            fieldAudience = fieldAudience,
        )

        init
        {
            whenever(exchangeLookup.lockExchangeForUpdate(request.exchangeId)).thenReturn(exchange)
            whenever(requestRepository.findRequestByIdForUpdate(any())).thenAnswer { savedRequests[it.getArgument(0)] }
            whenever(requestRepository.findById(any())).thenAnswer { savedRequests[it.getArgument(0)] }
            whenever(requestRepository.update(any())).thenAnswer {
                it.getArgument<InformationRequest>(0).also { updated -> savedRequests[updated.id] = updated }
            }
            whenever(transitionRepository.nextSequenceNumber(request.id)).thenAnswer { savedTransitions.size + 1 }
            whenever(transitionRepository.save(any())).thenAnswer {
                it.getArgument<InformationRequestTransition>(0).also { transition -> savedTransitions += transition }
            }
            whenever(capabilityRepository.findForVersion(request.templateVersionId)).thenReturn(emptyList())
            whenever(executorRegistry.unserved(any())).thenReturn(emptyList())
            whenever(
                authorizationService.authorize(
                    any(),
                    any(),
                    any<ResourceRef>(),
                    any(),
                ),
            ).thenReturn(Decision.Allow())
            whenever(auditRecorder.record(any())).thenReturn(
                AuditCaptureResult.Captured(UUID.randomUUID(), UUID.randomUUID()),
            )
            whenever(executionGrantService.findForRequest(any())).thenReturn(null)
            whenever(executionGrantService.issueGrant(any(), any())).thenAnswer {
                RequestExecutionGrant().apply { requestId = it.getArgument<InformationRequest>(0).id }
            }
            whenever(partyRepository.findActiveForRequest(request.id)).thenReturn(emptyList())
        }

        fun degradeAudit() {
            whenever(auditRecorder.record(any())).thenReturn(
                AuditCaptureResult.Degraded(
                    UUID.randomUUID(),
                    "unavailable"
                )
            )
        }

        fun recordLinkRevocations(): List<String> = mutableListOf<String>().also { seen ->
            whenever(bootstrapLinks.revokeLinksKeepingSessions(any(), any())).thenAnswer {
                val revoked = it.getArgument<InformationRequest>(0)
                seen += "${revoked.state} after ${savedTransitions.joinToString { transition -> transition.mutation.name }}"
                emptyList<ShareLink>()
            }
        }

        fun revokeGrant() {
            whenever(executionGrantService.findForRequest(request.id)).thenReturn(
                issuedExecutionGrant().apply {
                    requestId = request.id
                    revokedAt = java.sql.Timestamp.from(java.time.Instant.now())
                    revokedReason = "record-review"
                },
            )
        }

        fun activeActingParty(roleKey: InformationRequestShareRoleKey): InformationRequestParty =
            InformationRequestParty().apply {
                informationRequestId = request.id
                this.roleKey = roleKey
                principalKind = PrincipalKind.PARTICIPANT
                principalId = UUID.randomUUID()
            }

        fun subjectParty(): InformationRequestParty =
            InformationRequestParty().apply {
                informationRequestId = request.id
                roleKey = InformationRequestShareRoleKey.SUBJECT
                subjectIdentityRefId = UUID.randomUUID()
            }

        fun invoked(collaborator: Any, method: String): Boolean =
            mockingDetails(collaborator).invocations.any { it.method.name == method }

        fun replacementRequest(): InformationRequest =
            InformationRequest().apply {
                id = UUID.randomUUID()
                exchangeId = request.exchangeId
                templateVersionId = request.templateVersionId
                ownerType = InformationRequestOwnerType.ORGANIZATION
                ownerOrganizationId = organizationId
                state = InformationRequestState.DRAFT
            }.also { savedRequests[it.id] = it }

        fun unservedCapability(capability: InformationRequestCapability)
        {
            whenever(capabilityRepository.findForVersion(request.templateVersionId)).thenReturn(
                listOf(
                    InformationRequestTemplateVersionCapability().apply {
                        templateVersionId = request.templateVersionId
                        capabilityKey = capability
                        requiredContractVersion = capability.contractVersion
                    },
                ),
            )
            whenever(executorRegistry.unserved(any())).thenReturn(
                listOf(
                    InformationRequestCapabilityRequirement(
                        capability = capability,
                        requiredContractVersion = capability.contractVersion,
                    ),
                ),
            )
        }
    }
}

private class InMemoryLifecycleReceiptStore : CommandReceiptStore
{
    val receipts = mutableListOf<CommandReceipt>()
    var onLookup: () -> Unit = {}

    override fun lockCommand(request: CommandReceiptRequest) = Unit

    override fun findForCommand(request: CommandReceiptRequest): CommandReceipt? =
        receipts.also { onLookup() }.firstOrNull {
            it.resourceType == request.resource.type &&
                    it.resourceId == request.resource.id &&
                    it.operationName == request.operation &&
                    it.actorKind == request.actor.kind &&
                    it.actorId == request.actor.id &&
                    it.idempotencyKey == request.idempotencyKey
        }

    override fun insert(receipt: CommandReceipt): CommandReceipt
    {
        receipts += receipt
        return receipt
    }
}

private class CapturingDomainEventPublisher(
    private val events: MutableList<DomainEvent>,
) : DomainEventPublisher
{
    override fun publish(event: DomainEvent)
    {
        events += event
    }
}
