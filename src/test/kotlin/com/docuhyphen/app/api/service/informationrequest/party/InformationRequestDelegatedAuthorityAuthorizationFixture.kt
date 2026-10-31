package com.docuhyphen.app.api.service.informationrequest.party

import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.auth.authz.ResourceContextResolution
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.command.CommandReceiptRequest
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.LockedInformationRequest
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.access.InformationRequestRequirementCorrectionScope
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestParentSnapshot
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.model.informationrequest.party.GrantInformationRequestDelegatedAuthorityCommand
import com.docuhyphen.app.api.model.informationrequest.party.InformationRequestDelegatedAuthorityResult
import com.docuhyphen.app.api.model.informationrequest.party.RevokeInformationRequestDelegatedAuthorityCommand
import com.docuhyphen.app.api.repository.application.AppRoleAssignmentRepository
import com.docuhyphen.app.api.repository.exchange.ShareLinkRepository
import com.docuhyphen.app.api.repository.exchange.ShareRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRevisionRepository
import com.docuhyphen.app.api.repository.informationrequest.noauth.ParticipantAccountLinkRepository
import com.docuhyphen.app.api.repository.informationrequest.occurrence.InformationRequestGroupOccurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestDelegatedAuthorityRepository
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.repository.organization.OrganizationMembershipRepository
import com.docuhyphen.app.api.repository.organization.PrincipalGroupMemberRepository
import com.docuhyphen.app.api.repository.organization.PrincipalGroupRepository
import com.docuhyphen.app.api.service.application.ApplicationService
import com.docuhyphen.app.api.service.auth.authz.*
import com.docuhyphen.app.api.service.command.CommandReceiptScope
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandReceiptStore
import com.docuhyphen.app.api.service.exchange.ExchangeLifecycleStateService
import com.docuhyphen.app.api.service.exchange.ShareService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAuthorizationContextProvider
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestDelegatedAuthorityGrantSource
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestRequirementAuthorizationContextProvider
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestRequirementPolicyEvaluator
import com.docuhyphen.app.api.service.informationrequest.attestation.InformationRequestAttestationPolicyLoader
import com.docuhyphen.app.api.service.informationrequest.parent.InformationRequestRequirementParentGrantInheritancePolicy
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionLockService
import com.docuhyphen.app.api.service.organization.PrincipalGroupService
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.util.*

internal class InformationRequestDelegatedAuthorityAuthorizationFixture {
    val organizationId: UUID = UUID.randomUUID()
    val manager: PrincipalRef = PrincipalRef.user(UUID.randomUUID())
    val assignedPrincipal: PrincipalRef = PrincipalRef.participant(UUID.randomUUID())
    val delegate: PrincipalRef = PrincipalRef.participant(UUID.randomUUID())
    val authorizationContext = AuthorizationContext(activeOrgId = organizationId, mfaSatisfied = true)
    val request = InformationRequest().apply {
        id = UUID.randomUUID()
        exchangeId = UUID.randomUUID()
        templateVersionId = UUID.randomUUID()
        ownerType = InformationRequestOwnerType.ORGANIZATION
        ownerOrganizationId = organizationId
        state = InformationRequestState.IN_PROGRESS
        partyRevision = 1
    }
    private val binding = InformationRequestTemplateRequirementBinding().apply {
        id = UUID.randomUUID()
        templateVersionId = request.templateVersionId
        templateDefinitionId = UUID.randomUUID()
        templateRequirementId = UUID.randomUUID()
        templateSectionId = UUID.randomUUID()
        prompt = "Provide the requested response"
        responseMode = InformationRequestResponseMode.PROVIDE
        contributorRole = InformationRequestContributorRole.CONTRIBUTOR
    }
    private val occurrences = listOf(occurrence(), occurrence())
    val requirement: InformationRequestRequirement = occurrences[0].first
    val otherRequirement: InformationRequestRequirement = occurrences[1].first
    val assignedParty = InformationRequestParty().apply {
        id = UUID.randomUUID()
        informationRequestId = request.id
        roleKey = InformationRequestShareRoleKey.CONTRIBUTOR
        principalKind = assignedPrincipal.kind
        principalId = assignedPrincipal.id
        exchangeRecipientId = UUID.randomUUID()
        shareId = UUID.randomUUID()
        active = true
    }

    private val authorities = mutableMapOf<UUID, InformationRequestDelegatedAuthority>()
    private val memberships = mutableListOf<PrincipalGroupMember>()
    private val accountLinks = mutableMapOf<UUID, UUID>()
    val shares = mutableListOf(
        requestShareFor(manager, InformationRequestShareRoleKey.DECISION_MAKER),
    )

    private val requestRepository = mock<InformationRequestRepository>()
    private val partyRepository = mock<InformationRequestPartyRepository>()
    private val requirementRepository = mock<InformationRequestRequirementRepository>()
    private val requirementRevisionRepository = mock<InformationRequestRequirementRevisionRepository>()
    private val bindingRepository = mock<InformationRequestTemplateRequirementBindingRepository>()
    private val authorityRepository = mock<InformationRequestDelegatedAuthorityRepository>()
    private val shareRepository = mock<ShareRepository>()
    private val contextRegistry = mock<ResourceAuthorizationContextRegistry>()
    private val policyRegistry = mock<ParentGrantInheritancePolicyRegistry>()
    private val evaluatorRegistry = mock<ResourcePolicyEvaluatorRegistry>()
    private val receiptStore = InMemoryReceiptStore()
    private val parentState = mock<ExchangeLifecycleStateService> {
        on { snapshot(any()) }.thenReturn(InformationRequestParentSnapshot(ExchangeStatus.ACCEPTED_STARTED))
    }
    val requirementProvider = InformationRequestRequirementAuthorizationContextProvider(
        requirementRepository = requirementRepository,
        requestRepository = requestRepository,
        revisionRepository = requirementRevisionRepository,
        bindingRepository = bindingRepository,
        partyRepository = partyRepository,
        delegatedAuthorityFactSource = InformationRequestDelegatedAuthorityFactSource(authorityRepository),
        parentState = parentState,
        occurrenceRepository = mock<InformationRequestGroupOccurrenceRepository>(),
        participantAccountLinkRepository = mock<ParticipantAccountLinkRepository>().also {
            whenever(it.findByParticipantId(any())).thenAnswer { invocation ->
                accountLinks[invocation.getArgument<UUID>(0)]?.let { userId ->
                    ParticipantAccountLink().apply {
                        participantId = invocation.getArgument(0)
                        appUserId = userId
                        linkedViaInformationRequestId = request.id
                        linkedViaShareLinkId = UUID.randomUUID()
                    }
                }
            }
        },
        principalGroups = mock<PrincipalGroupService>().also {
            whenever(it.activeMembers(any())).thenAnswer { invocation ->
                memberships.filter { membership ->
                    membership.isActive && membership.principalGroupId == invocation.getArgument<UUID>(0)
                }
            }
        },
        attestationPolicies = mock<InformationRequestAttestationPolicyLoader>(),
        lockService = mock<InformationRequestSubmissionLockService>().also {
            whenever(
                it.correctionScopeOf(
                    any(),
                    any()
                )
            ).thenReturn(InformationRequestRequirementCorrectionScope.NORMAL_RESPONSE)
        },
        shareService = mock<ShareService>(),
    )
    private val centralAuthorization = DefaultAuthorizationService(
        shareRepository = shareRepository,
        shareLinkRepository = mock<ShareLinkRepository>(),
        appRoleAssignmentRepository = mock<AppRoleAssignmentRepository>().also {
            whenever(it.findActiveForUser(any())).thenReturn(emptyList())
        },
        principalGroupMemberRepository = mock<PrincipalGroupMemberRepository>().also {
            whenever(it.findGroupsForPrincipal(any(), any())).thenAnswer { invocation ->
                memberships.filter { membership ->
                    membership.isActive &&
                            membership.principalKind == invocation.getArgument<PrincipalKind>(0) &&
                            membership.principalId == invocation.getArgument<UUID>(1)
                }
            }
        },
        organizationMembershipRepository = mock<OrganizationMembershipRepository>().also {
            whenever(it.findActiveByUserAndOrg(any(), any())).thenReturn(null)
        },
        principalGroupRepository = mock<PrincipalGroupRepository>(),
        applicationService = mock<ApplicationService>(),
        resourceContextRegistry = contextRegistry,
        parentGrantInheritanceResolver = ParentGrantInheritanceResolver(policyRegistry, contextRegistry),
        resourcePolicyEvaluatorRegistry = evaluatorRegistry,
        resourceGrantSources = ResourceGrantSourceRegistry.of(
            InformationRequestDelegatedAuthorityGrantSource(authorityRepository, requirementProvider),
        ),
    )
    val authorityService = InformationRequestDelegatedAuthorityService(
        requestRepository = requestRepository,
        partyRepository = partyRepository,
        requirementRepository = requirementRepository,
        authorityRepository = authorityRepository,
        authorizationService = centralAuthorization,
        commandReceiptService = CommandReceiptService(receiptStore, CommandReceiptScope()),
        gate = mock<InformationRequestMutationGate>().also { gate ->
            whenever(gate.lock(request.id)).thenAnswer {
                LockedInformationRequest(
                    Exchange().apply {
                        id = request.exchangeId
                        status = ExchangeStatus.ACCEPTED_STARTED
                        isDeleted = false
                    },
                    request,
                )
            }
        },
        authorityRecorder = mock<InformationRequestDelegatedAuthorityRecorder>(),
        eligibility = mock<InformationRequestPartyEligibilityService>(),
        requirementRoles = requirementProvider,
    )

    init {
        val requestProvider = InformationRequestAuthorizationContextProvider(requestRepository, parentState)

        whenever(requestRepository.findById(request.id)).thenReturn(request)
        whenever(requestRepository.findRequestByIdForUpdate(request.id)).thenReturn(request)
        whenever(requestRepository.update(any())).thenAnswer { it.getArgument(0) }
        whenever(bindingRepository.findById(binding.id)).thenReturn(binding)
        occurrences.forEach { (occurrence, revision) ->
            whenever(requirementRepository.findById(occurrence.id)).thenReturn(occurrence)
            whenever(requirementRevisionRepository.findForRequirement(occurrence.id)).thenReturn(listOf(revision))
            whenever(requirementRevisionRepository.findCurrentForRequirement(occurrence.id)).thenReturn(revision)
        }
        whenever(partyRepository.findByIdForUpdate(assignedParty.id)).thenReturn(assignedParty)
        whenever(
            partyRepository.findActiveForRequestRole(
                eq(request.id),
                eq(InformationRequestShareRoleKey.CONTRIBUTOR)
            )
        )
            .thenReturn(listOf(assignedParty))
        whenever(authorityRepository.findForRequest(request.id)).thenAnswer { authorities.values.toList() }
        whenever(authorityRepository.findById(any())).thenAnswer { authorities[it.getArgument(0)] }
        whenever(authorityRepository.findByIdForUpdate(any())).thenAnswer { authorities[it.getArgument(0)] }
        whenever(authorityRepository.save(any())).thenAnswer {
            it.getArgument<InformationRequestDelegatedAuthority>(0)
                .also { authority -> authorities[authority.id] = authority }
        }
        whenever(authorityRepository.update(any())).thenAnswer {
            it.getArgument<InformationRequestDelegatedAuthority>(0)
                .also { authority -> authorities[authority.id] = authority }
        }
        whenever(shareRepository.findActiveForPrincipalOnResource(any(), any(), any(), any())).thenAnswer {
            shares.filter { share ->
                share.principalKind == it.getArgument<PrincipalKind>(0) &&
                        share.principalId == it.getArgument<UUID>(1) &&
                        share.resourceType == it.getArgument<ResourceType>(2) &&
                        share.resourceId == it.getArgument<UUID>(3) &&
                        share.status == ShareStatus.ACTIVE
            }
        }
        whenever(shareRepository.findById(any())).thenAnswer { shareId ->
            shares.firstOrNull {
                it.id == shareId.getArgument<UUID>(
                    0
                )
            }
        }
        whenever(contextRegistry.kindOf(any())).thenAnswer {
            when (it.getArgument<ResourceRef>(0).type) {
                ResourceType.INFORMATION_REQUEST -> ResourceKind.INFORMATION_REQUEST
                ResourceType.INFORMATION_REQUEST_REQUIREMENT -> ResourceKind.INFORMATION_REQUEST_REQUIREMENT
                else -> null
            }
        }
        whenever(contextRegistry.resolution(any())).thenAnswer {
            val ref = it.getArgument<ResourceRef>(0)
            when {
                ref == ResourceRef.informationRequest(request.id) -> ResourceContextResolution.Resolved(
                    requestProvider.resolve(
                        ref.id
                    )!!
                )

                ref.type == ResourceType.INFORMATION_REQUEST_REQUIREMENT && ref.id in setOf(
                    requirement.id,
                    otherRequirement.id
                ) ->
                    ResourceContextResolution.Resolved(requirementProvider.resolve(ref.id)!!)

                else -> ResourceContextResolution.Unresolved
            }
        }
        whenever(policyRegistry.policyFor(ResourceKind.INFORMATION_REQUEST_REQUIREMENT))
            .thenReturn(InformationRequestRequirementParentGrantInheritancePolicy())
        whenever(evaluatorRegistry.evaluatorFor(ResourceKind.INFORMATION_REQUEST_REQUIREMENT))
            .thenReturn(InformationRequestRequirementPolicyEvaluator())
    }

    fun authorizeDelegateToRespond(): Decision = authorize(delegate, Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND)

    fun authorize(principal: PrincipalRef, action: Action, requirementId: UUID = requirement.id): Decision =
        centralAuthorization.authorize(
            principal,
            action,
            ResourceRef.informationRequestRequirement(requirementId),
            authorizationContext,
        )

    fun grant(
        delegatePrincipal: PrincipalRef,
        idempotencyKey: String,
        requirementId: UUID? = requirement.id,
        effectiveAt: Timestamp? = null,
        expiresAt: Timestamp? = null,
    ): InformationRequestDelegatedAuthorityResult =
        authorityService.grant(
            GrantInformationRequestDelegatedAuthorityCommand(
                requestId = request.id,
                assignedPartyId = assignedParty.id,
                delegatePrincipal = delegatePrincipal,
                requirementId = requirementId,
                effectiveAt = effectiveAt,
                expiresAt = expiresAt,
                access = RequestAccessContext(manager, authorizationContext),
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(request)),
                idempotencyKey = idempotencyKey,
            ),
        )

    fun revoke(grant: InformationRequestDelegatedAuthorityResult, idempotencyKey: String) =
        authorityService.revoke(
            RevokeInformationRequestDelegatedAuthorityCommand(
                requestId = request.id,
                authorityId = grant.authority.id,
                access = RequestAccessContext(manager, authorizationContext),
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(request)),
                idempotencyKey = idempotencyKey,
            ),
        )

    fun addMember(group: PrincipalRef, member: PrincipalRef, active: Boolean = true) {
        memberships += PrincipalGroupMember().apply {
            principalGroupId = group.id
            principalKind = member.kind
            principalId = member.id
            isActive = active
        }
    }

    fun removeMember(group: PrincipalRef, member: PrincipalRef) {
        memberships.filter { it.principalGroupId == group.id && it.principalId == member.id }
            .forEach { it.isActive = false }
    }

    fun linkAccount(participant: PrincipalRef, user: PrincipalRef) {
        accountLinks[participant.id] = user.id
    }

    private fun occurrence(): Pair<InformationRequestRequirement, InformationRequestRequirementRevision> {
        val occurrence = InformationRequestRequirement().apply {
            id = UUID.randomUUID()
            informationRequestId = request.id
            sourceTemplateVersionId = request.templateVersionId
            sourceTemplateRequirementId = binding.templateRequirementId
            sourceTemplateBindingId = binding.id
            occurrencePath = "root"
        }
        val revision = InformationRequestRequirementRevision().apply {
            id = UUID.randomUUID()
            informationRequestRequirementId = occurrence.id
            informationRequestId = request.id
            sourceTemplateVersionId = request.templateVersionId
            sourceTemplateRequirementId = occurrence.sourceTemplateRequirementId
            sourceTemplateBindingId = binding.id
            revisionNumber = 1
            occurrencePath = "root"
            configurationHashSha256 = "1".repeat(64)
            optimisticVersion = 1
        }
        return occurrence to revision
    }

    private fun requestShareFor(principal: PrincipalRef, role: InformationRequestShareRoleKey): Share =
        Share().apply {
            id = UUID.randomUUID()
            resourceType = ResourceType.INFORMATION_REQUEST
            resourceId = request.id
            principalKind = principal.kind
            principalId = principal.id
            roleName = role.name
            source = ShareSource.DIRECT
            status = ShareStatus.ACTIVE
        }
}

internal class InMemoryReceiptStore : CommandReceiptStore {
    private val receipts = mutableListOf<CommandReceipt>()

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
