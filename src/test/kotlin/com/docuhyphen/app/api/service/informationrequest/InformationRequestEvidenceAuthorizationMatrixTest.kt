package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestContributorRole
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
import com.docuhyphen.app.api.service.auth.authz.ParentGrantInheritancePolicyRegistry
import com.docuhyphen.app.api.service.auth.authz.ParentGrantInheritanceResolver
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceAuthorizationContextRegistry
import com.docuhyphen.app.api.service.auth.authz.ResourceContextResolution
import com.docuhyphen.app.api.service.auth.authz.ResourceKind
import com.docuhyphen.app.api.service.auth.authz.ResourcePolicyEvaluatorRegistry
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.exchange.ExchangeLifecycleStateService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

class InformationRequestEvidenceAuthorizationMatrixTest
{
    private val evidenceActions = listOf(
        Action.INFORMATION_REQUEST_EVIDENCE_VIEW,
        Action.INFORMATION_REQUEST_EVIDENCE_UPLOAD,
        Action.INFORMATION_REQUEST_EVIDENCE_WITHDRAW,
        Action.INFORMATION_REQUEST_EVIDENCE_MANAGE,
    )

    @Test
    fun `only the assigned party reads and changes its own occurrence's evidence`()
    {
        val matrix = Matrix()

        assertEquals(
            setOf(
                Action.INFORMATION_REQUEST_EVIDENCE_VIEW,
                Action.INFORMATION_REQUEST_EVIDENCE_UPLOAD,
                Action.INFORMATION_REQUEST_EVIDENCE_WITHDRAW,
            ),
            matrix.allowed(matrix.contributor),
        )
    }

    @Test
    fun `a co-party that holds evidence capabilities but is not assigned to the occurrence is refused`()
    {
        val matrix = Matrix()

        assertEquals(emptySet<Action>(), matrix.allowed(matrix.preparer))
        assertEquals(
            InformationRequestErrorCatalog.PARTY_NOT_ASSIGNED,
            matrix.denial(matrix.preparer, Action.INFORMATION_REQUEST_EVIDENCE_VIEW),
        )
        assertEquals(emptySet<Action>(), matrix.allowed(matrix.reviewer))
    }

    @Test
    fun `an evidence administrator reads through the manage permission but never uploads`()
    {
        val matrix = Matrix()

        assertEquals(setOf(Action.INFORMATION_REQUEST_EVIDENCE_MANAGE), matrix.allowed(matrix.decisionMaker))
    }

    @Test
    fun `a caller with no party on the request is refused every evidence action`()
    {
        val matrix = Matrix()

        assertEquals(emptySet<Action>(), matrix.allowed(matrix.unrelated))
    }

    @Test
    fun `the assigned party acting through its verified request session reads its evidence`()
    {
        val matrix = Matrix()

        assertTrue(Action.INFORMATION_REQUEST_EVIDENCE_VIEW in matrix.allowed(matrix.participant, matrix.sessionContext))
        assertTrue(Action.INFORMATION_REQUEST_EVIDENCE_UPLOAD in matrix.allowed(matrix.participant, matrix.sessionContext))
    }

    @Test
    fun `a confidential occurrence refuses evidence to a session without step-up`()
    {
        val matrix = Matrix(compartment = "restricted-response")

        assertEquals(emptySet<Action>(), matrix.allowed(matrix.participant, matrix.sessionContext))
        assertEquals(
            InformationRequestErrorCatalog.CONFIDENTIALITY_DENIED,
            matrix.denial(matrix.participant, Action.INFORMATION_REQUEST_EVIDENCE_VIEW, matrix.sessionContext),
        )
    }

    @Test
    fun `an occurrence that is not disclosed to its respondent hides its evidence from that respondent`()
    {
        val matrix = Matrix(responseMode = InformationRequestResponseMode.NOT_DISCLOSED)

        assertEquals(
            InformationRequestErrorCatalog.CONFIDENTIALITY_DENIED,
            matrix.denial(matrix.contributor, Action.INFORMATION_REQUEST_EVIDENCE_VIEW),
        )
        assertEquals(setOf(Action.INFORMATION_REQUEST_EVIDENCE_MANAGE), matrix.allowed(matrix.decisionMaker))
    }

    private inner class Matrix(
        compartment: String? = null,
        responseMode: InformationRequestResponseMode = InformationRequestResponseMode.PROVIDE,
    )
    {
        val contributor = PrincipalRef.user(UUID.randomUUID())
        val participant = PrincipalRef.participant(UUID.randomUUID())
        val preparer = PrincipalRef.user(UUID.randomUUID())
        val reviewer = PrincipalRef.user(UUID.randomUUID())
        val decisionMaker = PrincipalRef.user(UUID.randomUUID())
        val unrelated = PrincipalRef.user(UUID.randomUUID())
        val userContext = AuthorizationContext(mfaSatisfied = true, sessionRef = "user-session")
        val sessionContext = AuthorizationContext(sessionRef = "verified-request-session")

        private val request = InformationRequest().apply {
            exchangeId = UUID.randomUUID()
            templateVersionId = UUID.randomUUID()
            ownerType = InformationRequestOwnerType.ORGANIZATION
            ownerOrganizationId = UUID.randomUUID()
            state = InformationRequestState.ISSUED
        }
        private val requirement = InformationRequestRequirement().apply {
            informationRequestId = request.id
            sourceTemplateVersionId = request.templateVersionId
            sourceTemplateRequirementId = UUID.randomUUID()
            sourceTemplateBindingId = UUID.randomUUID()
            occurrencePath = "root"
        }
        private val revision = InformationRequestRequirementRevision().apply {
            informationRequestRequirementId = requirement.id
            informationRequestId = request.id
            sourceTemplateVersionId = request.templateVersionId
            sourceTemplateRequirementId = requirement.sourceTemplateRequirementId
            sourceTemplateBindingId = requirement.sourceTemplateBindingId
            occurrencePath = "root"
            configurationHashSha256 = "0".repeat(64)
        }
        private val binding = InformationRequestTemplateRequirementBinding().apply {
            id = requirement.sourceTemplateBindingId
            templateVersionId = request.templateVersionId
            templateDefinitionId = UUID.randomUUID()
            templateRequirementId = requirement.sourceTemplateRequirementId
            templateSectionId = UUID.randomUUID()
            prompt = "Provide the supporting record"
            this.responseMode = responseMode
            contributorRole = InformationRequestContributorRole.CONTRIBUTOR
            confidentialityCompartmentKey = compartment
        }

        private val parties = listOf(
            party(contributor, InformationRequestShareRoleKey.CONTRIBUTOR),
            party(participant, InformationRequestShareRoleKey.CONTRIBUTOR),
            party(preparer, InformationRequestShareRoleKey.PREPARER),
            party(reviewer, InformationRequestShareRoleKey.REVIEWER),
            party(decisionMaker, InformationRequestShareRoleKey.DECISION_MAKER),
        )
        private val shares = parties.map { share(PrincipalRef(requireNotNull(it.principalKind), requireNotNull(it.principalId)), it.roleKey) }

        private val requestRepository = mock<InformationRequestRepository>()
        private val requirementRepository = mock<InformationRequestRequirementRepository>()
        private val revisionRepository = mock<InformationRequestRequirementRevisionRepository>()
        private val bindingRepository = mock<InformationRequestTemplateRequirementBindingRepository>()
        private val partyRepository = mock<InformationRequestPartyRepository>()
        private val shareRepository = mock<ShareRepository>()
        private val contextRegistry = mock<ResourceAuthorizationContextRegistry>()
        private val policyRegistry = mock<ParentGrantInheritancePolicyRegistry>()
        private val evaluatorRegistry = mock<ResourcePolicyEvaluatorRegistry>()

        private val authorization = DefaultAuthorizationService(
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

        init
        {
            val parentState = mock<ExchangeLifecycleStateService> {
                on { snapshot(any()) }.thenReturn(InformationRequestParentSnapshot(ExchangeStatus.ACCEPTED_STARTED))
            }
            val requirementProvider = InformationRequestRequirementAuthorizationContextProvider(
                requirementRepository = requirementRepository,
                requestRepository = requestRepository,
                revisionRepository = revisionRepository,
                bindingRepository = bindingRepository,
                partyRepository = partyRepository,
                delegatedAuthorityFactSource = InformationRequestDelegatedAuthorityFactSource(
                    mock<InformationRequestDelegatedAuthorityRepository>().also {
                        whenever(it.findForRequest(any())).thenReturn(emptyList())
                    },
                ),
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
            whenever(requirementRepository.findById(requirement.id)).thenReturn(requirement)
            whenever(revisionRepository.findForRequirement(requirement.id)).thenReturn(listOf(revision))
            whenever(bindingRepository.findById(binding.id)).thenReturn(binding)
            whenever(partyRepository.findActiveForRequestRole(any(), any())).thenAnswer { invocation ->
                parties.filter { it.informationRequestId == invocation.arguments[0] && it.roleKey == invocation.arguments[1] }
            }
            whenever(shareRepository.findActiveForPrincipalOnResource(any(), any(), any(), any())).thenAnswer { invocation ->
                shares.filter { share ->
                    share.principalKind == invocation.arguments[0] &&
                        share.principalId == invocation.arguments[1] &&
                        share.resourceType == invocation.arguments[2] &&
                        share.resourceId == invocation.arguments[3]
                }
            }
            whenever(shareRepository.findById(any())).thenAnswer { invocation ->
                shares.firstOrNull { it.id == invocation.arguments[0] }
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

        fun allowed(principal: PrincipalRef, context: AuthorizationContext = userContext): Set<Action> =
            evidenceActions.filter { decide(principal, it, context) !is Decision.Deny }.toSet()

        fun denial(principal: PrincipalRef, action: Action, context: AuthorizationContext = userContext): String? =
            (decide(principal, action, context) as? Decision.Deny)?.reasonCode

        private fun decide(principal: PrincipalRef, action: Action, context: AuthorizationContext): Decision =
            authorization.authorize(principal, action, ResourceRef.informationRequestRequirement(requirement.id), context)

        private fun party(principal: PrincipalRef, role: InformationRequestShareRoleKey) =
            InformationRequestParty().apply {
                informationRequestId = request.id
                roleKey = role
                principalKind = principal.kind
                principalId = principal.id
            }

        private fun share(principal: PrincipalRef, role: InformationRequestShareRoleKey): Share =
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
