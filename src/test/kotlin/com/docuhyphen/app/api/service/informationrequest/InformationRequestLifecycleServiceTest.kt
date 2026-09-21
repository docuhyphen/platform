package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.CommandReceipt
import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.entity.InformationRequestParty
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateVersionCapability
import com.docuhyphen.app.api.model.entity.InformationRequestTransition
import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.entity.OrganizationSubscriptionPolicy
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.RequestExecutionGrant
import com.docuhyphen.app.api.model.entity.RequestExecutionUsageKind
import com.docuhyphen.app.api.model.entity.RequestExecutionUsageReservation
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestPartyRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTransitionRepository
import com.docuhyphen.app.api.service.audit.AuditCaptureResult
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditOwnerScope
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandPrecondition
import com.docuhyphen.app.api.service.command.CommandPreconditionException
import com.docuhyphen.app.api.service.command.CommandReceiptConflictException
import com.docuhyphen.app.api.service.command.CommandReceiptRequest
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandReceiptStore
import com.docuhyphen.app.api.service.notification.DomainEvent
import com.docuhyphen.app.api.service.notification.DomainEventPublisher
import com.docuhyphen.app.api.service.subscription.ExchangeUsageCounter
import com.docuhyphen.app.api.service.subscription.OrganizationSeatCounter
import com.docuhyphen.app.api.service.subscription.PlanCode
import com.docuhyphen.app.api.service.subscription.PlanFeature
import com.docuhyphen.app.api.service.subscription.SubscriptionAccessService
import com.docuhyphen.app.api.service.subscription.SubscriptionContext
import com.docuhyphen.app.api.service.subscription.SubscriptionDenial
import com.docuhyphen.app.api.service.subscription.SubscriptionDenialReason
import com.docuhyphen.app.api.service.subscription.SubscriptionEnforcementConfigService
import com.docuhyphen.app.api.service.subscription.SubscriptionEnforcementMode
import com.docuhyphen.app.api.service.subscription.SubscriptionOwnerType
import com.docuhyphen.app.api.service.subscription.SubscriptionPolicyService
import com.docuhyphen.app.api.service.subscription.SubscriptionStatus
import com.docuhyphen.app.api.service.subscription.SubscriptionUsageService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

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

        val locks = inOrder(fixture.exchangeRepository, fixture.requestRepository)
        locks.verify(fixture.exchangeRepository).findByIdForUpdate(fixture.request.exchangeId)
        locks.verify(fixture.requestRepository).findRequestByIdForUpdate(fixture.request.id)
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
            precondition = CommandPrecondition.ExpectedRevision("\"${fixture.request.id}:0\""),
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
    fun `cancelling a draft request with no execution grant yet still checks the live subscription`()
    {
        val fixture = Fixture()
        val command = CancelInformationRequestCommand(
            requestId = fixture.request.id,
            access = fixture.access,
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.aggregateOf(fixture.request)),
            idempotencyKey = "cancel-draft-checks-live-subscription",
        )

        fixture.service.cancel(command)

        verify(fixture.entitlementGuard).requireRequestMutation(fixture.exchange)
    }

    @Test
    fun `cancelling an already-issued request answers to its frozen grant instead of rechecking the live subscription`()
    {
        val fixture = Fixture()
        fixture.request.state = InformationRequestState.ISSUED
        whenever(fixture.executionGrantService.findForRequest(fixture.request.id)).thenReturn(
            RequestExecutionGrant().apply { requestId = fixture.request.id },
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
            RequestExecutionGrant().apply { requestId = fixture.request.id },
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
            RequestExecutionGrant().apply { requestId = fixture.request.id },
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
    fun `a continuation mutation on a request whose execution grant has been revoked is denied`()
    {
        val fixture = Fixture()
        fixture.request.state = InformationRequestState.ISSUED
        whenever(fixture.executionGrantService.findForRequest(fixture.request.id)).thenReturn(
            RequestExecutionGrant().apply {
                requestId = fixture.request.id
                revokedAt = java.sql.Timestamp.from(java.time.Instant.now())
                revokedReason = "fraud-investigation"
            },
        )
        val command = CancelInformationRequestCommand(
            requestId = fixture.request.id,
            access = fixture.access,
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.aggregateOf(fixture.request)),
            idempotencyKey = "cancel-issued-request-revoked",
        )

        val failure = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.cancel(command)
        }

        assertEquals(InformationRequestErrorCatalog.EXECUTION_GRANT_REVOKED, failure.reasonCode)
        assertEquals(InformationRequestState.ISSUED, fixture.request.state)
        verify(fixture.requestRepository, never()).update(any())
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
            additionalRecipientCap = 2
        }
        whenever(fixture.executionGrantService.issueGrant(fixture.request, fixture.exchange)).thenReturn(grant)
        val contributorReservation = RequestExecutionUsageReservation().apply { id = UUID.randomUUID() }
        val reviewerReservation = RequestExecutionUsageReservation().apply { id = UUID.randomUUID() }
        whenever(
            fixture.executionUsageReservationService.reserve(
                grant.id,
                RequestExecutionUsageKind.ADDITIONAL_RECIPIENT,
                "information_request.party|${contributor.id}",
                1L,
            ),
        ).thenReturn(contributorReservation)
        whenever(
            fixture.executionUsageReservationService.reserve(
                grant.id,
                RequestExecutionUsageKind.ADDITIONAL_RECIPIENT,
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
            RequestExecutionUsageKind.ADDITIONAL_RECIPIENT,
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
            additionalRecipientCap = 1
        }
        whenever(fixture.executionGrantService.issueGrant(fixture.request, fixture.exchange)).thenReturn(grant)
        val contributorReservation = RequestExecutionUsageReservation().apply { id = UUID.randomUUID() }
        whenever(
            fixture.executionUsageReservationService.reserve(
                grant.id,
                RequestExecutionUsageKind.ADDITIONAL_RECIPIENT,
                "information_request.party|${contributor.id}",
                1L,
            ),
        ).thenReturn(contributorReservation)
        whenever(
            fixture.executionUsageReservationService.reserve(
                grant.id,
                RequestExecutionUsageKind.ADDITIONAL_RECIPIENT,
                "information_request.party|${reviewer.id}",
                1L,
            ),
        ).thenThrow(
            RequestExecutionUsageExhaustedException(
                grantId = grant.id,
                usageKind = RequestExecutionUsageKind.ADDITIONAL_RECIPIENT,
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
        fixture.unservedCapability(InformationRequestCapability.RESPONSE_SUBMISSION)
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
            RequestExecutionGrant().apply { requestId = fixture.request.id },
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
            SubscriptionAccessService(
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
        val exchangeRepository = mock<ExchangeRepository>()
        private val transitionRepository = mock<InformationRequestTransitionRepository>()
        private val capabilityRepository = mock<InformationRequestTemplateVersionCapabilityRepository>()
        private val executorRegistry = mock<InformationRequestCapabilityExecutorRegistry>()
        val authorizationService = mock<AuthorizationService>()
        val auditRecorder = mock<AuditRecorder>()
        val executionGrantService = mock<InformationRequestExecutionGrantService>()
        val partyRepository = mock<InformationRequestPartyRepository>()
        val executionUsageReservationService = mock<InformationRequestExecutionUsageReservationService>()
        val entitlementGuard = entitlementGuardFactory(organizationId)
        val receiptStore = InMemoryLifecycleReceiptStore()
        private val eventPublisher = CapturingDomainEventPublisher(events)
        private val capabilityGate = InformationRequestTemplateCapabilityGate(executorRegistry, capabilityRepository)
        private val transitionHistory = InformationRequestTransitionHistoryService(
            transitionRepository = transitionRepository,
            auditRecorder = auditRecorder,
            domainEventPublisher = eventPublisher,
        )
        val service = InformationRequestLifecycleService(
            requestRepository = requestRepository,
            exchangeRepository = exchangeRepository,
            authorizationService = authorizationService,
            commandReceiptService = CommandReceiptService(receiptStore),
            capabilityGate = capabilityGate,
            transitionHistory = transitionHistory,
            entitlementGuard = entitlementGuard,
            executionGrantService = executionGrantService,
            partyRepository = partyRepository,
            executionUsageReservationService = executionUsageReservationService,
        )

        init
        {
            whenever(exchangeRepository.findByIdForUpdate(request.exchangeId)).thenReturn(exchange)
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

    override fun findForCommand(request: CommandReceiptRequest): CommandReceipt? =
        receipts.firstOrNull {
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
