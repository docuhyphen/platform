package com.docuhyphen.app.api.service.informationrequest.party

import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.command.CommandReceiptRequest
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.party.ReassignInformationRequestPartyCommand
import com.docuhyphen.app.api.model.informationrequest.party.RevokeInformationRequestPartyCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.lifecycle.InformationRequestTransitionRepository
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.repository.informationrequest.party.SubjectIdentityRefRepository
import com.docuhyphen.app.api.service.audit.AuditCaptureResult
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.auth.authz.*
import com.docuhyphen.app.api.service.command.CommandReceiptScope
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandReceiptStore
import com.docuhyphen.app.api.service.exchange.ExchangeLookupService
import com.docuhyphen.app.api.service.exchange.ExchangeRecipientService
import com.docuhyphen.app.api.service.exchange.ExternalParticipantService
import com.docuhyphen.app.api.service.exchange.ShareService
import com.docuhyphen.app.api.service.identity.PrincipalDisplayService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestAuditActorLabelResolver
import com.docuhyphen.app.api.service.informationrequest.execution.InformationRequestExecutionGrantService
import com.docuhyphen.app.api.service.informationrequest.execution.InformationRequestExecutionUsageReservationService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestBootstrapShareLinkService
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestFieldAudienceGuard
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestResponseStart
import com.docuhyphen.app.api.service.notification.DomainEvent
import com.docuhyphen.app.api.service.notification.DomainEventPublisher
import com.docuhyphen.app.api.service.user.AppUserService
import org.mockito.kotlin.*
import java.util.*

internal class InformationRequestPartyFixture(parentStatus: ExchangeStatus = ExchangeStatus.ACCEPTED_STARTED) {
    private val savedParties = mutableMapOf<UUID, InformationRequestParty>()
    val request = InformationRequest().apply {
        id = UUID.randomUUID()
        exchangeId = UUID.randomUUID()
        templateVersionId = UUID.randomUUID()
        ownerType = InformationRequestOwnerType.ORGANIZATION
        ownerOrganizationId = UUID.randomUUID()
        partyRevision = 1
    }
    val share = Share().apply {
        id = UUID.randomUUID()
        resourceType = ResourceType.INFORMATION_REQUEST
        resourceId = request.id
        principalKind = PrincipalKind.PARTICIPANT
        principalId = UUID.randomUUID()
        roleName = InformationRequestShareRoleKey.CONTRIBUTOR.name
    }
    private val exchange = Exchange().apply {
        id = request.exchangeId
        ownerOrganizationId = request.ownerOrganizationId
        status = parentStatus
        isDeleted = false
    }

    fun requireRecipientSignIn() {
        exchange.requireRecipientSignIn = true
    }

    val authorizationContext = AuthorizationContext(
        activeOrgId = request.ownerOrganizationId,
    )
    val requestRepository = mock<InformationRequestRepository>()
    val partyRepository = mock<InformationRequestPartyRepository>()
    val subjectIdentityRefRepository = mock<SubjectIdentityRefRepository>()
    val externalParticipantService = mock<ExternalParticipantService>()
    val appUserService = mock<AppUserService>()
    val exchangeRecipientService = mock<ExchangeRecipientService>()
    val shareService = mock<ShareService>()
    val bootstrapShareLinkService = mock<InformationRequestBootstrapShareLinkService>()
    val authorizationService = mock<AuthorizationService>()
    val exchangeLookup = mock<ExchangeLookupService>()
    val transitionRepository = mock<InformationRequestTransitionRepository>()
    val executionGrantService = mock<InformationRequestExecutionGrantService>()
    val executionUsageReservationService = mock<InformationRequestExecutionUsageReservationService>()
    val mutationGate = mock<InformationRequestMutationGate>()
    val eligibility = mock<InformationRequestPartyEligibilityService>()
    val delegatedAuthorities = mock<InformationRequestDelegatedAuthorityService>()
    val auditRecorder = mock<AuditRecorder>()
    val fieldAudience = mock<InformationRequestFieldAudienceGuard>()
    val receiptStore = InMemoryCommandReceiptStore()
    val commandReceiptService = CommandReceiptService(receiptStore, CommandReceiptScope())
    val savedTransitions = mutableListOf<InformationRequestTransition>()
    val events = mutableListOf<DomainEvent>()
    private val eventPublisher = CapturingPartyDomainEventPublisher(events)
    private val transitionHistory = InformationRequestTransitionHistoryService(
        transitionRepository = transitionRepository,
        auditRecorder = auditRecorder,
        domainEventPublisher = eventPublisher,
        responseStart = InformationRequestResponseStart(requestRepository, java.time.Clock.systemUTC()),
        actorLabels = InformationRequestAuditActorLabelResolver(mock<PrincipalDisplayService>()),
        receiptScope = CommandReceiptScope(),
    )
    val initiator = AppUser().apply {
        id = UUID.randomUUID()
        email = "initiator@example.test"
    }
    private val support = InformationRequestPartyCommandSupport(
        requestRepository = requestRepository,
        partyRepository = partyRepository,
        subjectIdentityRefRepository = subjectIdentityRefRepository,
        exchangeRecipientService = exchangeRecipientService,
        shareService = shareService,
        authorizationService = authorizationService,
        commandReceiptService = commandReceiptService,
        exchangeLookup = exchangeLookup,
        transitionHistory = transitionHistory,
        fieldAudience = fieldAudience,
    )
    private val capacity = InformationRequestPartyCapacity(executionGrantService, executionUsageReservationService)
    private val trustedRecipients =
        InformationRequestTrustedRecipientEligibilityService(exchangeRecipientService, partyRepository)
    val assignments = InformationRequestPartyAssignmentService(
        partyRepository = partyRepository,
        support = support,
        capacity = capacity,
        mutationGate = mutationGate,
        eligibility = eligibility,
        trustedRecipients = trustedRecipients,
        externalParticipantService = externalParticipantService,
        recipientAddresses = InformationRequestRecipientAddressResolver(
            shareService,
            exchangeRecipientService,
            appUserService
        ),
    )
    val revocations = InformationRequestPartyRevocationService(
        partyRepository = partyRepository,
        support = support,
        capacity = capacity,
        mutationGate = mutationGate,
        eligibility = eligibility,
        trustedRecipients = trustedRecipients,
        delegatedAuthorities = delegatedAuthorities,
        bootstrapShareLinkService = bootstrapShareLinkService,
        transitionHistory = transitionHistory,
        reviewAssignments = mock(),
    )
    val materialization = InformationRequestPartyMaterializationService(
        partyRepository,
        support,
        capacity,
        eligibility,
        trustedRecipients
    )

    init {
        whenever(exchangeLookup.lockExchangeForUpdate(request.exchangeId)).thenReturn(exchange)
        whenever(transitionRepository.nextSequenceNumber(request.id)).thenAnswer { savedTransitions.size + 1 }
        whenever(transitionRepository.save(any())).thenAnswer {
            it.getArgument<InformationRequestTransition>(0).also { transition -> savedTransitions += transition }
        }
        whenever(auditRecorder.record(any())).thenReturn(
            AuditCaptureResult.Captured(UUID.randomUUID(), UUID.randomUUID()),
        )
        whenever(requestRepository.findById(request.id)).thenReturn(request)
        whenever(requestRepository.findRequestByIdForUpdate(request.id)).thenReturn(request)
        whenever(requestRepository.update(any())).thenAnswer { it.getArgument(0) }
        whenever(partyRepository.save(any())).thenAnswer {
            it.getArgument<InformationRequestParty>(0).also { party ->
                savedParties[party.id] = party
            }
        }
        whenever(partyRepository.update(any())).thenAnswer {
            it.getArgument<InformationRequestParty>(0).also { party ->
                savedParties[party.id] = party
            }
        }
        whenever(partyRepository.findById(any())).thenAnswer {
            savedParties[it.getArgument(0)]
        }
        whenever(partyRepository.findByIdForUpdate(any())).thenAnswer {
            savedParties[it.getArgument(0)]
        }
        whenever(
            shareService.grantAdditionalRoleKeyWithPrincipalProvenance(
                any(),
                any(),
                any(),
                any(),
                any(),
                anyOrNull(),
                anyOrNull(),
            ),
        )
            .thenReturn(share)
        whenever(
            authorizationService.authorize(
                any(),
                eq(Action.INFORMATION_REQUEST_MANAGE_PARTIES),
                any<ResourceRef>(),
                any(),
            ),
        ).thenReturn(Decision.Allow())
        whenever(subjectIdentityRefRepository.findOwned(any(), any(), any())).thenReturn(mock())
    }

    fun activeActingParty(): InformationRequestParty =
        InformationRequestParty().apply {
            informationRequestId = request.id
            roleKey = InformationRequestShareRoleKey.CONTRIBUTOR
            principalKind = PrincipalKind.PARTICIPANT
            principalId = UUID.randomUUID()
            shareId = share.id
        }.also { party ->
            savedParties[party.id] = party
        }

    fun reassignCommand(
        party: InformationRequestParty,
        holder: PrincipalRef,
        key: String,
        actor: PrincipalRef = PrincipalRef.user(UUID.randomUUID()),
        exchangeRecipientId: UUID? = null,
    ) = ReassignInformationRequestPartyCommand(
        requestId = request.id,
        partyId = party.id,
        principal = holder,
        exchangeRecipientId = exchangeRecipientId,
        access = RequestAccessContext(actor, authorizationContext),
        precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(request)),
        idempotencyKey = key,
    )

    fun revokeCommand(party: InformationRequestParty, actor: PrincipalRef, key: String) =
        RevokeInformationRequestPartyCommand(
            requestId = request.id,
            partyId = party.id,
            access = RequestAccessContext(actor, authorizationContext),
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(request)),
            idempotencyKey = key,
        )

    fun exchangeRecipient(
        directShareId: UUID = UUID.randomUUID(),
        exchangeId: UUID = request.exchangeId,
    ): ExchangeRecipient =
        ExchangeRecipient().apply {
            this.exchangeId = exchangeId
            this.directShareId = directShareId
        }.also { recipient ->
            whenever(exchangeRecipientService.getById(recipient.id)).thenReturn(recipient)
            whenever(exchangeRecipientService.findByDirectShareId(directShareId)).thenReturn(recipient)
            whenever(
                exchangeRecipientService.requireAssignablePartyRecipient(
                    eq(recipient.id),
                    eq(exchangeId),
                    any<PrincipalRef>(),
                ),
            ).thenReturn(recipient)
        }
}

internal class InMemoryCommandReceiptStore : CommandReceiptStore {
    val receipts = mutableListOf<CommandReceipt>()

    override fun lockCommand(request: CommandReceiptRequest) = Unit

    override fun findForCommand(request: CommandReceiptRequest): CommandReceipt? =
        receipts.firstOrNull {
            it.resourceType == request.resource.type &&
                    it.resourceId == request.resource.id &&
                    it.operationName == request.operation &&
                    it.actorKind == request.actor.kind &&
                    it.actorId == request.actor.id &&
                    it.idempotencyKey == request.idempotencyKey
        }

    override fun insert(receipt: CommandReceipt): CommandReceipt {
        receipts += receipt
        return receipt
    }
}

internal class CapturingPartyDomainEventPublisher(
    private val events: MutableList<DomainEvent>,
) : DomainEventPublisher {
    override fun publish(event: DomainEvent) {
        events += event
    }
}
