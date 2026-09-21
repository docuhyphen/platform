package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestContributorRole
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.entity.InformationRequestParty
import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestResponseMode
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirementBinding
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestGroupOccurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestPartyRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRevisionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.repository.informationrequest.ParticipantAccountLinkRepository
import com.docuhyphen.app.api.repository.organization.PrincipalGroupMemberRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.exchange.ExchangeLifecycleStateService
import io.quarkus.security.ForbiddenException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class InformationRequestGroupAuthorizationServiceTest
{
    @Test
    fun `authorizeOccurrenceScope authorizes only the Requirements addressed by the given occurrence paths`()
    {
        val fixture = Fixture()
        val inScope = fixture.requirement("items[0]")
        val outOfScope = fixture.requirement("items[1]")
        whenever(fixture.requirementRepository.findForRequest(fixture.request.id))
            .thenReturn(listOf(inScope, outOfScope))
        whenever(fixture.authorizationService.authorize(any(), any(), any(), any())).thenReturn(Decision.Allow())

        fixture.service.authorizeOccurrenceScope(fixture.access, fixture.request.id, setOf("items[0]"))

        verify(fixture.authorizationService).authorize(
            fixture.access.principal,
            Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND,
            ResourceRef.informationRequestRequirement(inScope.id),
            fixture.access.authorization,
        )
        verify(fixture.authorizationService, never()).authorize(
            any(), any(), eq(ResourceRef.informationRequestRequirement(outOfScope.id)), any(),
        )
    }

    @Test
    fun `authorizeOccurrenceScope refuses when any addressed Requirement denies the actor`()
    {
        val fixture = Fixture()
        val inScope = fixture.requirement("items[0]")
        whenever(fixture.requirementRepository.findForRequest(fixture.request.id)).thenReturn(listOf(inScope))
        whenever(fixture.authorizationService.authorize(any(), any(), any(), any()))
            .thenReturn(Decision.Deny(InformationRequestErrorCatalog.PARTY_NOT_ASSIGNED, "denied"))

        assertThrows<ForbiddenException> {
            fixture.service.authorizeOccurrenceScope(fixture.access, fixture.request.id, setOf("items[0]"))
        }
    }

    @Test
    fun `authorizeMaterializedBindings evaluates authored creation policy even when the binding already exists`()
    {
        val fixture = Fixture()
        val existing = fixture.requirement("items[0]", bindingId = fixture.binding.id)
        whenever(fixture.requirementRepository.findForRequest(fixture.request.id)).thenReturn(listOf(existing))
        whenever(fixture.authorizationService.authorize(any(), any(), any(), any()))
            .thenReturn(Decision.Deny(InformationRequestErrorCatalog.PARTY_NOT_ASSIGNED, "denied"))
        fixture.assignParty(InformationRequestShareRoleKey.PREPARER, fixture.access.principal)

        fixture.service.authorizeMaterializedBindings(fixture.access, fixture.request, listOf(fixture.binding))

        verify(fixture.authorizationService, never()).authorize(any(), any(), any(), any())
    }

    @Test
    fun `authorizeMaterializedBindings refuses an exact Requirement delegate when creating a new occurrence`()
    {
        val fixture = Fixture()
        val existing = fixture.requirement("items[0]", bindingId = fixture.binding.id)
        whenever(fixture.requirementRepository.findForRequest(fixture.request.id)).thenReturn(listOf(existing))
        whenever(fixture.authorizationService.authorize(any(), any(), any(), any())).thenReturn(Decision.Allow())
        fixture.assignParty(InformationRequestShareRoleKey.PREPARER, PrincipalRef.user(UUID.randomUUID()))

        assertThrows<ForbiddenException> {
            fixture.service.authorizeMaterializedBindings(fixture.access, fixture.request, listOf(fixture.binding))
        }

        verify(fixture.authorizationService, never()).authorize(any(), any(), any(), any())
    }

    @Test
    fun `authorizeMaterializedBindings permits the assigned party to recreate a removed zero minimum occurrence`()
    {
        val fixture = Fixture()
        val removed = fixture.requirement("items[0]", bindingId = fixture.binding.id)
        whenever(fixture.requirementRepository.findForRequest(fixture.request.id)).thenReturn(listOf(removed))
        whenever(fixture.authorizationService.authorize(any(), any(), any(), any()))
            .thenReturn(Decision.Deny(InformationRequestErrorCatalog.GROUP_OCCURRENCE_REMOVED, "removed"))
        fixture.assignParty(InformationRequestShareRoleKey.PREPARER, fixture.access.principal)

        fixture.service.authorizeMaterializedBindings(fixture.access, fixture.request, listOf(fixture.binding))

        verify(fixture.authorizationService, never()).authorize(any(), any(), any(), any())
    }

    @Test
    fun `authorizeMaterializedBindings permits the assigned party for a binding with no materialized occurrence yet`()
    {
        val fixture = Fixture()
        whenever(fixture.requirementRepository.findForRequest(fixture.request.id)).thenReturn(emptyList())
        fixture.assignParty(InformationRequestShareRoleKey.PREPARER, fixture.access.principal)

        fixture.service.authorizeMaterializedBindings(fixture.access, fixture.request, listOf(fixture.binding))

        verify(fixture.authorizationService, never()).authorize(any(), any(), any(), any())
    }

    @Test
    fun `authorizeMaterializedBindings refuses an actor not assigned to the binding's authored party role`()
    {
        val fixture = Fixture()
        whenever(fixture.requirementRepository.findForRequest(fixture.request.id)).thenReturn(emptyList())
        fixture.assignParty(InformationRequestShareRoleKey.PREPARER, PrincipalRef.user(UUID.randomUUID()))

        val failure = assertThrows<ForbiddenException> {
            fixture.service.authorizeMaterializedBindings(fixture.access, fixture.request, listOf(fixture.binding))
        }

        assertEquals("Access denied to change Information Request group occurrences", failure.message)
    }

    @Test
    fun `authorizeMaterializedBindings refuses a not disclosed binding even for its own assigned party`()
    {
        val fixture = Fixture()
        fixture.binding.responseMode = InformationRequestResponseMode.NOT_DISCLOSED
        whenever(fixture.requirementRepository.findForRequest(fixture.request.id)).thenReturn(emptyList())
        fixture.assignParty(InformationRequestShareRoleKey.PREPARER, fixture.access.principal)

        assertThrows<ForbiddenException> {
            fixture.service.authorizeMaterializedBindings(fixture.access, fixture.request, listOf(fixture.binding))
        }
    }

    @Test
    fun `authorizeMaterializedBindings refuses a protected binding without a stepped up session`()
    {
        val fixture = Fixture()
        fixture.binding.confidentialityCompartmentKey = "restricted-response"
        whenever(fixture.requirementRepository.findForRequest(fixture.request.id)).thenReturn(emptyList())
        fixture.assignParty(InformationRequestShareRoleKey.PREPARER, fixture.access.principal)

        assertThrows<ForbiddenException> {
            fixture.service.authorizeMaterializedBindings(fixture.access, fixture.request, listOf(fixture.binding))
        }
    }

    @Test
    fun `authorizeMaterializedBindings permits a protected binding once the session is stepped up`()
    {
        val fixture = Fixture(mfaSatisfied = true)
        fixture.binding.confidentialityCompartmentKey = "restricted-response"
        whenever(fixture.requirementRepository.findForRequest(fixture.request.id)).thenReturn(emptyList())
        fixture.assignParty(InformationRequestShareRoleKey.PREPARER, fixture.access.principal)

        fixture.service.authorizeMaterializedBindings(fixture.access, fixture.request, listOf(fixture.binding))
    }

    @Test
    fun `authorizeMaterializedBindings fails closed when the authored policy context cannot be resolved`()
    {
        val fixture = Fixture()
        whenever(fixture.requirementRepository.findForRequest(fixture.request.id)).thenReturn(emptyList())
        whenever(fixture.parentState.snapshot(fixture.request.exchangeId)).thenReturn(null)

        assertThrows<ForbiddenException> {
            fixture.service.authorizeMaterializedBindings(fixture.access, fixture.request, listOf(fixture.binding))
        }
    }

    private class Fixture(mfaSatisfied: Boolean = false)
    {
        val organizationId: UUID = UUID.randomUUID()
        val participantId: UUID = UUID.randomUUID()
        val access = RequestAccessContext(
            principal = PrincipalRef.participant(participantId),
            authorization = AuthorizationContext(activeOrgId = organizationId, mfaSatisfied = mfaSatisfied),
        )
        val request = InformationRequest().apply {
            id = UUID.randomUUID()
            exchangeId = UUID.randomUUID()
            templateVersionId = UUID.randomUUID()
            ownerType = InformationRequestOwnerType.ORGANIZATION
            ownerOrganizationId = organizationId
            state = InformationRequestState.ISSUED
        }
        val binding = InformationRequestTemplateRequirementBinding().apply {
            id = UUID.randomUUID()
            templateVersionId = request.templateVersionId
            templateDefinitionId = UUID.randomUUID()
            templateRequirementId = UUID.randomUUID()
            templateSectionId = UUID.randomUUID()
            prompt = "Provide the requested item"
            responseMode = InformationRequestResponseMode.PROVIDE
            contributorRole = InformationRequestContributorRole.PREPARER
            occurrenceAnchorKey = "items"
        }

        val authorizationService = mock<AuthorizationService>()
        val requirementRepository = mock<InformationRequestRequirementRepository>()
        private val requestRepository = mock<InformationRequestRepository> {
            on { findById(request.id) }.thenReturn(request)
        }
        private val revisionRepository = mock<InformationRequestRequirementRevisionRepository>()
        private val bindingRepository = mock<InformationRequestTemplateRequirementBindingRepository>()
        private val partyRepository = mock<InformationRequestPartyRepository> {
            on { findActiveForRequestRole(any(), any()) }.thenReturn(emptyList())
        }
        private val delegatedAuthorityFactSource = mock<InformationRequestDelegatedAuthorityFactSource> {
            on { factsFor(any(), any(), any()) }.thenReturn(emptyList())
        }
        val parentState = mock<ExchangeLifecycleStateService> {
            on { snapshot(request.exchangeId) }.thenReturn(InformationRequestParentSnapshot(ExchangeStatus.ACCEPTED_STARTED))
        }
        private val contextProvider = InformationRequestRequirementAuthorizationContextProvider(
            requirementRepository = requirementRepository,
            requestRepository = requestRepository,
            revisionRepository = revisionRepository,
            bindingRepository = bindingRepository,
            partyRepository = partyRepository,
            delegatedAuthorityFactSource = delegatedAuthorityFactSource,
            parentState = parentState,
            occurrenceRepository = mock<InformationRequestGroupOccurrenceRepository>(),
            participantAccountLinkRepository = mock<ParticipantAccountLinkRepository>().also {
                whenever(it.findByParticipantId(any())).thenReturn(null)
            },
            principalGroupMemberRepository = mock<PrincipalGroupMemberRepository>().also {
                whenever(it.findActiveMembers(any())).thenReturn(emptyList())
            },
        )
        private val requirementPolicyEvaluator = InformationRequestRequirementPolicyEvaluator()
        val service = InformationRequestGroupAuthorizationService(
            authorizationService = authorizationService,
            requirementRepository = requirementRepository,
            contextProvider = contextProvider,
            requirementPolicyEvaluator = requirementPolicyEvaluator,
        )

        fun requirement(occurrencePath: String, bindingId: UUID = UUID.randomUUID()) =
            InformationRequestRequirement().apply {
                id = UUID.randomUUID()
                informationRequestId = request.id
                sourceTemplateVersionId = request.templateVersionId
                sourceTemplateRequirementId = UUID.randomUUID()
                sourceTemplateBindingId = bindingId
                this.occurrencePath = occurrencePath
            }

        fun assignParty(roleKey: InformationRequestShareRoleKey, principal: PrincipalRef)
        {
            whenever(partyRepository.findActiveForRequestRole(request.id, roleKey)).thenReturn(
                listOf(
                    InformationRequestParty().apply {
                        id = UUID.randomUUID()
                        informationRequestId = request.id
                        this.roleKey = roleKey
                        principalKind = principal.kind
                        principalId = principal.id
                    },
                ),
            )
        }
    }
}
