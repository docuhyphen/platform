package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.CommandReceipt
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestContributorRole
import com.docuhyphen.app.api.model.entity.InformationRequestDelegatedAuthority
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.entity.InformationRequestParty
import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementRevision
import com.docuhyphen.app.api.model.entity.InformationRequestResponseMode
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirementBinding
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.entity.Share
import com.docuhyphen.app.api.model.entity.ShareSource
import com.docuhyphen.app.api.model.entity.ShareStatus
import com.docuhyphen.app.api.repository.application.AppRoleAssignmentRepository
import com.docuhyphen.app.api.repository.exchange.ShareLinkRepository
import com.docuhyphen.app.api.repository.exchange.ShareRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestDelegatedAuthorityRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestGroupOccurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestPartyRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRevisionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.repository.informationrequest.ParticipantAccountLinkRepository
import com.docuhyphen.app.api.repository.organization.OrganizationMembershipRepository
import com.docuhyphen.app.api.repository.organization.PrincipalGroupMemberRepository
import com.docuhyphen.app.api.repository.organization.PrincipalGroupRepository
import com.docuhyphen.app.api.service.application.ApplicationService
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.DefaultAuthorizationService
import com.docuhyphen.app.api.service.auth.authz.OwnerContext
import com.docuhyphen.app.api.service.auth.authz.ParentGrantInheritancePolicyRegistry
import com.docuhyphen.app.api.service.auth.authz.ParentGrantInheritanceResolver
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceAuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.ResourceAuthorizationContextRegistry
import com.docuhyphen.app.api.service.auth.authz.ResourceContextResolution
import com.docuhyphen.app.api.service.auth.authz.ResourceKind
import com.docuhyphen.app.api.service.auth.authz.ResourcePolicyEvaluatorRegistry
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandActorRef
import com.docuhyphen.app.api.service.command.CommandPrecondition
import com.docuhyphen.app.api.service.command.CommandReceiptRequest
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandReceiptStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID
import com.docuhyphen.app.api.service.exchange.ExchangeLifecycleStateService

class InformationRequestDelegatedAuthorityAuthorizationTest
{
    @Test
    fun `grant and revoke flow through central Requirement authorization policy facts`()
    {
        val fixture = Fixture()

        val beforeGrant = fixture.authorizeDelegateToRespond()

        assertEquals(InformationRequestErrorCatalog.PARTY_NOT_ASSIGNED, (beforeGrant as Decision.Deny).reasonCode)

        val grant = fixture.authorityService.grant(
            GrantInformationRequestDelegatedAuthorityCommand(
                requestId = fixture.request.id,
                assignedPartyId = fixture.assignedParty.id,
                delegatePrincipal = fixture.delegate,
                requirementId = fixture.requirement.id,
                access = RequestAccessContext(fixture.manager, fixture.authorizationContext),
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
                idempotencyKey = "grant-delegate-for-response",
            ),
        )

        val afterGrant = fixture.authorizeDelegateToRespond()

        assertTrue(afterGrant.isAllowed)

        fixture.authorityService.revoke(
            RevokeInformationRequestDelegatedAuthorityCommand(
                requestId = fixture.request.id,
                authorityId = grant.authority.id,
                access = RequestAccessContext(fixture.manager, fixture.authorizationContext),
                precondition = CommandPrecondition.ExpectedRevision(grant.authoritiesETag),
                idempotencyKey = "revoke-delegate-for-response",
            ),
        )

        val afterRevoke = fixture.authorizeDelegateToRespond()

        assertEquals(InformationRequestErrorCatalog.PARTY_NOT_ASSIGNED, (afterRevoke as Decision.Deny).reasonCode)
    }

    private class Fixture
    {
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
        val requirement = InformationRequestRequirement().apply {
            id = UUID.randomUUID()
            informationRequestId = request.id
            sourceTemplateVersionId = request.templateVersionId
            sourceTemplateRequirementId = UUID.randomUUID()
            sourceTemplateBindingId = UUID.randomUUID()
            occurrencePath = "root"
        }
        val currentRevision = InformationRequestRequirementRevision().apply {
            id = UUID.randomUUID()
            informationRequestRequirementId = requirement.id
            informationRequestId = request.id
            sourceTemplateVersionId = request.templateVersionId
            sourceTemplateRequirementId = requirement.sourceTemplateRequirementId
            sourceTemplateBindingId = UUID.randomUUID()
            revisionNumber = 1
            occurrencePath = "root"
            configurationHashSha256 = "1".repeat(64)
            optimisticVersion = 1
        }
        val binding = InformationRequestTemplateRequirementBinding().apply {
            id = currentRevision.sourceTemplateBindingId
            templateVersionId = request.templateVersionId
            templateDefinitionId = UUID.randomUUID()
            templateRequirementId = requirement.sourceTemplateRequirementId
            templateSectionId = UUID.randomUUID()
            prompt = "Provide the requested response"
            responseMode = InformationRequestResponseMode.PROVIDE
            contributorRole = InformationRequestContributorRole.CONTRIBUTOR
        }
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
        private val shares = mutableListOf(
            requestShareFor(manager, InformationRequestShareRoleKey.DECISION_MAKER),
            requestShareFor(delegate, InformationRequestShareRoleKey.CONTRIBUTOR),
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
        private val centralAuthorization = DefaultAuthorizationService(
            shareRepository = shareRepository,
            shareLinkRepository = mock<ShareLinkRepository>(),
            appRoleAssignmentRepository = mock<AppRoleAssignmentRepository>().also {
                whenever(it.findActiveForUser(any())).thenReturn(emptyList())
            },
            principalGroupMemberRepository = mock<PrincipalGroupMemberRepository>().also {
                whenever(it.findGroupsForPrincipal(any(), any())).thenReturn(emptyList())
            },
            organizationMembershipRepository = mock<OrganizationMembershipRepository>().also {
                whenever(it.findActiveByUserAndOrg(any(), any())).thenReturn(null)
            },
            principalGroupRepository = mock<PrincipalGroupRepository>(),
            applicationService = mock<ApplicationService>(),
            resourceContextRegistry = contextRegistry,
            parentGrantInheritanceResolver = ParentGrantInheritanceResolver(policyRegistry, contextRegistry),
            resourcePolicyEvaluatorRegistry = evaluatorRegistry,
        )
        val authorityService = InformationRequestDelegatedAuthorityService(
            requestRepository = requestRepository,
            partyRepository = partyRepository,
            requirementRepository = requirementRepository,
            authorityRepository = authorityRepository,
            authorizationService = centralAuthorization,
            commandReceiptService = CommandReceiptService(receiptStore),
        )

        init
        {
        val parentState = mock<ExchangeLifecycleStateService> {
            on { snapshot(org.mockito.kotlin.any()) }.thenReturn(InformationRequestParentSnapshot(com.docuhyphen.app.api.model.entity.ExchangeStatus.ACCEPTED_STARTED))
        }
            val factSource = InformationRequestDelegatedAuthorityFactSource(authorityRepository)
            val requirementProvider = InformationRequestRequirementAuthorizationContextProvider(
                requirementRepository = requirementRepository,
                requestRepository = requestRepository,
                revisionRepository = requirementRevisionRepository,
                bindingRepository = bindingRepository,
                partyRepository = partyRepository,
                delegatedAuthorityFactSource = factSource,
                parentState = parentState,
                occurrenceRepository = mock<InformationRequestGroupOccurrenceRepository>(),
                participantAccountLinkRepository = mock<ParticipantAccountLinkRepository>().also {
                    whenever(it.findByParticipantId(any())).thenReturn(null)
                },
                principalGroupMemberRepository = mock<PrincipalGroupMemberRepository>().also {
                    whenever(it.findActiveMembers(any())).thenReturn(emptyList())
                },
                attestationPolicies = mock<InformationRequestAttestationPolicyLoader>(),
            )
            val requestProvider = InformationRequestAuthorizationContextProvider(requestRepository, parentState)

            whenever(requestRepository.findById(request.id)).thenReturn(request)
            whenever(requestRepository.findRequestByIdForUpdate(request.id)).thenReturn(request)
            whenever(requestRepository.update(any())).thenAnswer { it.getArgument(0) }
            whenever(requirementRepository.findById(requirement.id)).thenReturn(requirement)
            whenever(requirementRevisionRepository.findForRequirement(requirement.id)).thenReturn(listOf(currentRevision))
            whenever(bindingRepository.findById(currentRevision.sourceTemplateBindingId)).thenReturn(binding)
            whenever(partyRepository.findByIdForUpdate(assignedParty.id)).thenReturn(assignedParty)
            whenever(
                partyRepository.findActiveForRequestRole(
                    eq(request.id),
                    eq(InformationRequestShareRoleKey.CONTRIBUTOR),
                ),
            ).thenReturn(listOf(assignedParty))
            whenever(authorityRepository.findForRequest(request.id)).thenAnswer { authorities.values.toList() }
            whenever(authorityRepository.findById(any())).thenAnswer { authorities[it.getArgument(0)] }
            whenever(authorityRepository.findByIdForUpdate(any())).thenAnswer { authorities[it.getArgument(0)] }
            whenever(authorityRepository.save(any())).thenAnswer {
                it.getArgument<InformationRequestDelegatedAuthority>(0).also { authority ->
                    authorities[authority.id] = authority
                }
            }
            whenever(authorityRepository.update(any())).thenAnswer {
                it.getArgument<InformationRequestDelegatedAuthority>(0).also { authority ->
                    authorities[authority.id] = authority
                }
            }

            whenever(shareRepository.findActiveForPrincipalOnResource(any(), any(), any(), any()))
                .thenAnswer {
                    shares.filter { share ->
                        share.principalKind == it.getArgument<PrincipalKind>(0) &&
                            share.principalId == it.getArgument<UUID>(1) &&
                            share.resourceType == it.getArgument<ResourceType>(2) &&
                            share.resourceId == it.getArgument<UUID>(3) &&
                            share.status == ShareStatus.ACTIVE
                    }
                }
            whenever(shareRepository.findById(any())).thenAnswer { shareId ->
                shares.firstOrNull { it.id == shareId.getArgument<UUID>(0) }
            }

            whenever(contextRegistry.kindOf(any())).thenAnswer {
                when (it.getArgument<ResourceRef>(0).type)
                {
                    ResourceType.INFORMATION_REQUEST -> ResourceKind.INFORMATION_REQUEST
                    ResourceType.INFORMATION_REQUEST_REQUIREMENT -> ResourceKind.INFORMATION_REQUEST_REQUIREMENT
                    else -> null
                }
            }
            whenever(contextRegistry.resolution(any())).thenAnswer {
                when (val ref = it.getArgument<ResourceRef>(0))
                {
                    ResourceRef.informationRequest(request.id) ->
                        ResourceContextResolution.Resolved(requestProvider.resolve(ref.id)!!)
                    ResourceRef.informationRequestRequirement(requirement.id) ->
                        ResourceContextResolution.Resolved(requirementProvider.resolve(ref.id)!!)
                    else -> ResourceContextResolution.Unresolved
                }
            }
            whenever(policyRegistry.policyFor(ResourceKind.INFORMATION_REQUEST_REQUIREMENT))
                .thenReturn(InformationRequestRequirementParentGrantInheritancePolicy())
            whenever(evaluatorRegistry.evaluatorFor(ResourceKind.INFORMATION_REQUEST_REQUIREMENT))
                .thenReturn(InformationRequestRequirementPolicyEvaluator())
        }

        fun authorizeDelegateToRespond(): Decision =
            centralAuthorization.authorize(
                delegate,
                Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND,
                ResourceRef.informationRequestRequirement(requirement.id),
                authorizationContext,
            )

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
}

private class InMemoryReceiptStore : CommandReceiptStore
{
    private val receipts = mutableListOf<CommandReceipt>()

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
