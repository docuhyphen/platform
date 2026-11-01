package com.docuhyphen.app.api.service.informationrequest.occurrence

import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.model.auth.authz.ResourceRef
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.access.InformationRequestRequirementCorrectionScope
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestParentSnapshot
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRevisionRepository
import com.docuhyphen.app.api.repository.informationrequest.noauth.ParticipantAccountLinkRepository
import com.docuhyphen.app.api.repository.informationrequest.occurrence.InformationRequestGroupOccurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.service.auth.authz.*
import com.docuhyphen.app.api.service.exchange.ExchangeLifecycleStateService
import com.docuhyphen.app.api.service.exchange.ShareService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestRequirementAuthorizationContextProvider
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestRequirementPolicyEvaluator
import com.docuhyphen.app.api.service.informationrequest.attestation.InformationRequestAttestationPolicyLoader
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestDelegatedAuthorityFactSource
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionLockService
import com.docuhyphen.app.api.service.organization.PrincipalGroupService
import io.quarkus.security.ForbiddenException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.util.*

class InformationRequestGroupAuthorizationServiceTest
{
    @Test
    fun `an occurrence is visible only while it holds a Requirement the caller may see, or holds none`() {
        val fixture = Fixture()
        val shown = fixture.requirement("items[0]")
        val hidden = fixture.requirement("items[1]")
        val nested = fixture.requirement("items[3]/entries[0]")
        whenever(fixture.requirementRepository.findForRequest(fixture.request.id)).thenReturn(
            listOf(
                shown,
                hidden,
                nested
            )
        )
        whenever(fixture.authorizationService.authorize(any(), any(), any(), any())).thenReturn(Decision.Allow())
        whenever(
            fixture.authorizationService.authorize(
                any(),
                any(),
                eq(ResourceRef.informationRequestRequirement(hidden.id)),
                any()
            ),
        ).thenReturn(Decision.Deny(Decision.REASON_NO_GRANT, "Assigned to another party"))
        val occurrences = listOf("items[0]", "items[1]", "items[2]", "items[3]").mapIndexed { index, path ->
            InformationRequestGroupOccurrence().apply {
                informationRequestId = fixture.request.id
                sourceTemplateGroupId = UUID.randomUUID()
                occurrenceIndex = index
                occurrencePath = path
            }
        }

        val visible = fixture.service.visibleOccurrences(fixture.access, fixture.request.id, occurrences)

        assertEquals(listOf("items[0]", "items[2]", "items[3]"), visible.map { it.occurrencePath })
    }

    @Test
    fun `an occurrence change authorizes only the Requirements addressed by the given occurrence paths`()
    {
        val fixture = Fixture()
        val inScope = fixture.requirement("items[0]")
        val outOfScope = fixture.requirement("items[1]")
        whenever(fixture.requirementRepository.findForRequest(fixture.request.id))
            .thenReturn(listOf(inScope, outOfScope))
        whenever(fixture.authorizationService.authorize(any(), any(), any(), any())).thenReturn(Decision.Allow())

        fixture.service.authorizeOccurrenceChange(fixture.access, fixture.request, setOf("items[0]"), emptyList())

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
    fun `an occurrence change refuses when any addressed Requirement denies the actor`()
    {
        val fixture = Fixture()
        val inScope = fixture.requirement("items[0]")
        whenever(fixture.requirementRepository.findForRequest(fixture.request.id)).thenReturn(listOf(inScope))
        whenever(fixture.authorizationService.authorize(any(), any(), any(), any()))
            .thenReturn(Decision.Deny(InformationRequestErrorCatalog.PARTY_NOT_ASSIGNED, "denied"))

        assertThrows<ForbiddenException> {
            fixture.service.authorizeOccurrenceChange(fixture.access, fixture.request, setOf("items[0]"), emptyList())
        }
    }

    @Test
    fun `an occurrence change leaves out the Requirements of a removed nested occurrence`() {
        val fixture = Fixture()
        val parent = fixture.requirement("items[0]")
        val removedChild = fixture.requirement("items[0]/entries[0]")
        fixture.removedPaths += removedChild.occurrencePath
        whenever(fixture.requirementRepository.findForRequest(fixture.request.id)).thenReturn(
            listOf(
                parent,
                removedChild
            )
        )
        whenever(
            fixture.authorizationService.authorize(
                any(),
                any(),
                eq(ResourceRef.informationRequestRequirement(removedChild.id)),
                any(),
            ),
        ).thenReturn(Decision.Deny(InformationRequestErrorCatalog.GROUP_OCCURRENCE_REMOVED, "removed"))
        whenever(
            fixture.authorizationService.authorize(
                any(),
                any(),
                eq(ResourceRef.informationRequestRequirement(parent.id)),
                any(),
            ),
        ).thenReturn(Decision.Allow())

        fixture.service.authorizeOccurrenceChange(fixture.access, fixture.request, setOf("items[0]"), emptyList())

        verify(fixture.authorizationService, never()).authorize(
            any(),
            any(),
            eq(ResourceRef.informationRequestRequirement(removedChild.id)),
            any(),
        )
    }

    @Test
    fun `group creation evaluates authored creation policy even when the binding already exists`()
    {
        val fixture = Fixture()
        val existing = fixture.requirement("items[0]", bindingId = fixture.binding.id)
        whenever(fixture.requirementRepository.findForRequest(fixture.request.id)).thenReturn(listOf(existing))
        whenever(fixture.authorizationService.authorize(any(), any(), any(), any()))
            .thenReturn(Decision.Deny(InformationRequestErrorCatalog.PARTY_NOT_ASSIGNED, "denied"))
        fixture.assignParty(InformationRequestShareRoleKey.PREPARER, fixture.access.principal)

        fixture.service.authorizeGroupCreation(fixture.access, fixture.request, listOf(fixture.binding), emptyList())

        verify(fixture.authorizationService, never()).authorize(any(), any(), any(), any())
    }

    @Test
    fun `group creation refuses an exact Requirement delegate when creating a new occurrence`()
    {
        val fixture = Fixture()
        val existing = fixture.requirement("items[0]", bindingId = fixture.binding.id)
        whenever(fixture.requirementRepository.findForRequest(fixture.request.id)).thenReturn(listOf(existing))
        whenever(fixture.authorizationService.authorize(any(), any(), any(), any())).thenReturn(Decision.Allow())
        fixture.assignParty(InformationRequestShareRoleKey.PREPARER, PrincipalRef.user(UUID.randomUUID()))

        assertThrows<ForbiddenException> {
            fixture.service.authorizeGroupCreation(
                fixture.access,
                fixture.request,
                listOf(fixture.binding),
                emptyList()
            )
        }

        verify(fixture.authorizationService, never()).authorize(any(), any(), any(), any())
    }

    @Test
    fun `group creation permits the assigned party to recreate a removed zero minimum occurrence`()
    {
        val fixture = Fixture()
        val removed = fixture.requirement("items[0]", bindingId = fixture.binding.id)
        whenever(fixture.requirementRepository.findForRequest(fixture.request.id)).thenReturn(listOf(removed))
        whenever(fixture.authorizationService.authorize(any(), any(), any(), any()))
            .thenReturn(Decision.Deny(InformationRequestErrorCatalog.GROUP_OCCURRENCE_REMOVED, "removed"))
        fixture.assignParty(InformationRequestShareRoleKey.PREPARER, fixture.access.principal)

        fixture.service.authorizeGroupCreation(fixture.access, fixture.request, listOf(fixture.binding), emptyList())

        verify(fixture.authorizationService, never()).authorize(any(), any(), any(), any())
    }

    @Test
    fun `group creation permits the assigned party for a binding with no materialized occurrence yet`()
    {
        val fixture = Fixture()
        whenever(fixture.requirementRepository.findForRequest(fixture.request.id)).thenReturn(emptyList())
        fixture.assignParty(InformationRequestShareRoleKey.PREPARER, fixture.access.principal)

        fixture.service.authorizeGroupCreation(fixture.access, fixture.request, listOf(fixture.binding), emptyList())

        verify(fixture.authorizationService, never()).authorize(any(), any(), any(), any())
    }

    @Test
    fun `group creation refuses an actor not assigned to the binding's authored party role`()
    {
        val fixture = Fixture()
        whenever(fixture.requirementRepository.findForRequest(fixture.request.id)).thenReturn(emptyList())
        fixture.assignParty(InformationRequestShareRoleKey.PREPARER, PrincipalRef.user(UUID.randomUUID()))

        val failure = assertThrows<ForbiddenException> {
            fixture.service.authorizeGroupCreation(
                fixture.access,
                fixture.request,
                listOf(fixture.binding),
                emptyList()
            )
        }

        assertEquals("Access denied to change Information Request group occurrences", failure.message)
    }

    @Test
    fun `group creation refuses a not disclosed binding even for its own assigned party`()
    {
        val fixture = Fixture()
        fixture.binding.responseMode = InformationRequestResponseMode.NOT_DISCLOSED
        whenever(fixture.requirementRepository.findForRequest(fixture.request.id)).thenReturn(emptyList())
        fixture.assignParty(InformationRequestShareRoleKey.PREPARER, fixture.access.principal)

        assertThrows<ForbiddenException> {
            fixture.service.authorizeGroupCreation(
                fixture.access,
                fixture.request,
                listOf(fixture.binding),
                emptyList()
            )
        }
    }

    @Test
    fun `group creation refuses a protected binding without a stepped up session`()
    {
        val fixture = Fixture()
        fixture.binding.confidentialityCompartmentKey = "restricted-response"
        whenever(fixture.requirementRepository.findForRequest(fixture.request.id)).thenReturn(emptyList())
        fixture.assignParty(InformationRequestShareRoleKey.PREPARER, fixture.access.principal)

        assertThrows<ForbiddenException> {
            fixture.service.authorizeGroupCreation(
                fixture.access,
                fixture.request,
                listOf(fixture.binding),
                emptyList()
            )
        }
    }

    @Test
    fun `group creation permits a protected binding once the session is stepped up`()
    {
        val fixture = Fixture(mfaSatisfied = true)
        fixture.binding.confidentialityCompartmentKey = "restricted-response"
        whenever(fixture.requirementRepository.findForRequest(fixture.request.id)).thenReturn(emptyList())
        fixture.assignParty(InformationRequestShareRoleKey.PREPARER, fixture.access.principal)

        fixture.service.authorizeGroupCreation(fixture.access, fixture.request, listOf(fixture.binding), emptyList())
    }

    @Test
    fun `group creation fails closed when the authored policy context cannot be resolved`()
    {
        val fixture = Fixture()
        whenever(fixture.requirementRepository.findForRequest(fixture.request.id)).thenReturn(emptyList())
        whenever(fixture.parentState.snapshot(fixture.request.exchangeId)).thenReturn(null)

        assertThrows<ForbiddenException> {
            fixture.service.authorizeGroupCreation(
                fixture.access,
                fixture.request,
                listOf(fixture.binding),
                emptyList()
            )
        }
    }

    @Test
    fun `group creation is refused when the group's scope holds no binding at all`() {
        val fixture = Fixture()
        whenever(fixture.requirementRepository.findForRequest(fixture.request.id)).thenReturn(emptyList())
        fixture.assignParty(InformationRequestShareRoleKey.PREPARER, fixture.access.principal)

        assertThrows<ForbiddenException> {
            fixture.service.authorizeGroupCreation(fixture.access, fixture.request, emptyList(), emptyList())
        }
    }

    @Test
    fun `group creation with nothing materialized falls back to every binding authored beneath the group`() {
        val fixture = Fixture()
        whenever(fixture.requirementRepository.findForRequest(fixture.request.id)).thenReturn(emptyList())
        fixture.assignParty(InformationRequestShareRoleKey.PREPARER, PrincipalRef.user(UUID.randomUUID()))

        assertThrows<ForbiddenException> {
            fixture.service.authorizeGroupCreation(
                fixture.access,
                fixture.request,
                emptyList(),
                listOf(fixture.binding)
            )
        }
    }

    @Test
    fun `group creation through optional child groups is permitted to the party assigned to those bindings`() {
        val fixture = Fixture()
        whenever(fixture.requirementRepository.findForRequest(fixture.request.id)).thenReturn(emptyList())
        fixture.assignParty(InformationRequestShareRoleKey.PREPARER, fixture.access.principal)

        fixture.service.authorizeGroupCreation(fixture.access, fixture.request, emptyList(), listOf(fixture.binding))
    }

    @Test
    fun `an occurrence change authorizes the Requirements beneath every changed occurrence`() {
        val fixture = Fixture()
        val nested = fixture.requirement("items[1]/entries[0]", bindingId = fixture.binding.id)
        whenever(fixture.requirementRepository.findForRequest(fixture.request.id)).thenReturn(listOf(nested))
        fixture.assignParty(InformationRequestShareRoleKey.PREPARER, fixture.access.principal)
        whenever(fixture.authorizationService.authorize(any(), any(), any(), any()))
            .thenReturn(Decision.Deny(InformationRequestErrorCatalog.PARTY_NOT_ASSIGNED, "denied"))

        assertThrows<ForbiddenException> {
            fixture.service.authorizeOccurrenceChange(
                fixture.access,
                fixture.request,
                setOf("items[1]"),
                listOf(fixture.binding)
            )
        }
    }

    @Test
    fun `an occurrence change is refused when nothing at or beneath it and nothing authored for its group is in scope`() {
        val fixture = Fixture()
        whenever(fixture.requirementRepository.findForRequest(fixture.request.id)).thenReturn(emptyList())
        whenever(fixture.authorizationService.authorize(any(), any(), any(), any())).thenReturn(Decision.Allow())

        assertThrows<ForbiddenException> {
            fixture.service.authorizeOccurrenceChange(fixture.access, fixture.request, setOf("items[0]"), emptyList())
        }
    }

    @Test
    fun `an occurrence change also needs the authored creation policy of the group's bindings`() {
        val fixture = Fixture()
        val existing = fixture.requirement("items[0]", bindingId = fixture.binding.id)
        whenever(fixture.requirementRepository.findForRequest(fixture.request.id)).thenReturn(listOf(existing))
        whenever(fixture.authorizationService.authorize(any(), any(), any(), any())).thenReturn(Decision.Allow())
        fixture.assignParty(InformationRequestShareRoleKey.PREPARER, PrincipalRef.user(UUID.randomUUID()))

        assertThrows<ForbiddenException> {
            fixture.service.authorizeOccurrenceChange(
                fixture.access,
                fixture.request,
                setOf("items[0]"),
                listOf(fixture.binding)
            )
        }
    }

    @Test
    fun `an occurrence change is permitted when every Requirement beneath it and every authored binding allow the actor`() {
        val fixture = Fixture()
        val existing = fixture.requirement("items[0]", bindingId = fixture.binding.id)
        val nested = fixture.requirement("items[0]/entries[0]", bindingId = fixture.binding.id)
        val sibling = fixture.requirement("items[10]", bindingId = fixture.binding.id)
        whenever(fixture.requirementRepository.findForRequest(fixture.request.id)).thenReturn(
            listOf(
                existing,
                nested,
                sibling
            )
        )
        whenever(fixture.authorizationService.authorize(any(), any(), any(), any())).thenReturn(Decision.Allow())
        fixture.assignParty(InformationRequestShareRoleKey.PREPARER, fixture.access.principal)

        fixture.service.authorizeOccurrenceChange(
            fixture.access,
            fixture.request,
            setOf("items[0]"),
            listOf(fixture.binding)
        )

        listOf(existing, nested).forEach { requirement ->
            verify(fixture.authorizationService).authorize(
                fixture.access.principal,
                Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND,
                ResourceRef.informationRequestRequirement(requirement.id),
                fixture.access.authorization,
            )
        }
        verify(fixture.authorizationService, never()).authorize(
            any(), any(), eq(ResourceRef.informationRequestRequirement(sibling.id)), any(),
        )
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
        val removedPaths = mutableSetOf<String>()
        val occurrenceRepository = mock<InformationRequestGroupOccurrenceRepository>().also { repository ->
            whenever(repository.findForRequest(any())).thenAnswer { invocation ->
                requirementRepository.findForRequest(invocation.arguments[0] as UUID)
                    .map { it.occurrencePath }
                    .filter { it != InformationRequestOccurrencePath.ROOT && it !in removedPaths }
                    .distinct()
                    .map { path ->
                        InformationRequestGroupOccurrence().apply {
                            informationRequestId = request.id
                            sourceTemplateGroupId = UUID.randomUUID()
                            occurrencePath = path
                        }
                    }
            }
        }
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
            principalGroups = mock<PrincipalGroupService>().also {
                whenever(it.activeMembers(any())).thenReturn(emptyList())
            },
            attestationPolicies = mock<InformationRequestAttestationPolicyLoader>(),
            lockService = mock<InformationRequestSubmissionLockService>().also {
                whenever(it.correctionScopeOf(any(), any())).thenReturn(InformationRequestRequirementCorrectionScope.NORMAL_RESPONSE)
            },
            shareService = mock<ShareService>(),
        )
        private val requirementPolicyEvaluator = InformationRequestRequirementPolicyEvaluator()
        val service = InformationRequestGroupAuthorizationService(
            authorizationService = authorizationService,
            requirementRepository = requirementRepository,
            occurrenceRepository = occurrenceRepository,
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
